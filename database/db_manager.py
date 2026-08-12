# database/db_manager.py - Gestor de base de datos con soporte PostgreSQL/SQLite
import sqlite3
import os
import psycopg2
import logging
from pathlib import Path
import time
import threading
import bcrypt  # ✅ Agregado para crear datos de prueba

logger = logging.getLogger(__name__)

class DatabaseManager:
    _global_conn = None
    _global_lock = threading.Lock()
    _connection_pool = {}
    _pool_lock = threading.Lock()
    
    @classmethod
    def get_global_connection(cls):
        """Conexión a la base de datos global (PostgreSQL en producción si DATABASE_URL está configurada, si no SQLite)"""
        with cls._global_lock:
            if cls._global_conn is None or (hasattr(cls._global_conn, 'closed') and cls._global_conn.closed):
                db_url = os.environ.get('DATABASE_URL')
                
                if db_url:
                    try:
                        max_retries = 5
                        for i in range(max_retries):
                            try:
                                cls._global_conn = psycopg2.connect(db_url, sslmode='require')
                                # Asegurar que usamos el esquema public para tablas globales
                                with cls._global_conn.cursor() as cur:
                                    cur.execute("SET search_path TO public")
                                logger.info("Conectado a PostgreSQL para base de datos global")
                                break
                            except psycopg2.OperationalError as e:
                                if i < max_retries - 1:
                                    wait_time = 2 ** i
                                    logger.warning(f"Error conectando a PostgreSQL, reintento {i+1}/{max_retries} en {wait_time}s: {e}")
                                    time.sleep(wait_time)
                                else:
                                    logger.error(f"No se pudo conectar a PostgreSQL después de {max_retries} intentos")
                                    raise
                    except Exception as e:
                        logger.error(f"Error conectando a PostgreSQL: {e}")
                        logger.info("Fallback a SQLite para base de datos global")
                        cls._global_conn = sqlite3.connect('global.db', check_same_thread=False)
                        cls._global_conn.execute("PRAGMA foreign_keys = ON")
                        logger.info("Conectado a SQLite para base de datos global (fallback)")
                else:
                    cls._global_conn = sqlite3.connect('global.db', check_same_thread=False)
                    cls._global_conn.execute("PRAGMA foreign_keys = ON")
                    logger.info("Conectado a SQLite para base de datos global")
            return cls._global_conn

    @classmethod
    def get_connection_for_business(cls, business_id):
        """Obtener conexión para un negocio específico con pooling"""
        with cls._pool_lock:
            if business_id not in cls._connection_pool:
                cls._connection_pool[business_id] = cls._create_business_connection(business_id)
            else:
                conn = cls._connection_pool[business_id]
                try:
                    if hasattr(conn, 'closed') and conn.closed:
                        cls._connection_pool[business_id] = cls._create_business_connection(business_id)
                except Exception:
                    cls._connection_pool[business_id] = cls._create_business_connection(business_id)
                    
            return cls._connection_pool[business_id]
    
    @classmethod
    def _create_business_connection(cls, business_id):
        """Crear una nueva conexión para un negocio"""
        try:
            db_url = os.environ.get('DATABASE_URL')
            
            if db_url:
                try:
                    max_retries = 5
                    for i in range(max_retries):
                        try:
                            conn = psycopg2.connect(db_url, sslmode='require')
                            with conn.cursor() as cur:
                                schema_name = f"business_{business_id.replace('-', '_').replace('.', '_')}"
                                cur.execute(f"CREATE SCHEMA IF NOT EXISTS {schema_name}")
                                cur.execute(f"SET search_path TO {schema_name}, public")
                            logger.info(f"Conexión creada para negocio: {business_id} (PostgreSQL)")
                            return conn
                        except psycopg2.OperationalError as e:
                            if i < max_retries - 1:
                                wait_time = 2 ** i
                                logger.warning(f"Error conectando a PostgreSQL para negocio {business_id}, reintento {i+1}/{max_retries}: {e}")
                                time.sleep(wait_time)
                            else:
                                logger.error(f"No se pudo conectar a PostgreSQL para negocio {business_id} después de {max_retries} intentos")
                                raise
                except Exception as e:
                    logger.error(f"Error conectando a PostgreSQL para negocio {business_id}: {e}")
                    logger.info(f"Fallback a SQLite para negocio {business_id}")
                    db_path = f"{business_id}.db"
                    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
                    conn = sqlite3.connect(db_path, timeout=30, check_same_thread=False)
                    conn.execute("PRAGMA foreign_keys = ON")
                    logger.info(f"Conexión creada para negocio: {business_id} (SQLite - fallback)")
                    return conn
            else:
                db_path = f"{business_id}.db"
                Path(db_path).parent.mkdir(parents=True, exist_ok=True)
                conn = sqlite3.connect(db_path, timeout=30, check_same_thread=False)
                conn.execute("PRAGMA foreign_keys = ON")
                logger.info(f"Conexión creada para negocio: {business_id} (SQLite)")
                return conn
                
        except Exception as e:
            logger.error(f"Error obteniendo conexión para negocio {business_id}: {e}")
            conn = sqlite3.connect(':memory:', check_same_thread=False)
            conn.execute("PRAGMA foreign_keys = ON")
            return conn

    @classmethod
    def verify_and_fix_global_tables(cls):
        """Verificar y corregir la estructura de las tablas globales automáticamente"""
        try:
            conn = cls.get_global_connection()
            if conn is None:
                logger.error("No se pudo obtener conexión a la base de datos")
                return
                
            c = conn.cursor()
            logger.info("Verificando estructura de tablas globales...")
            
            # Detectar si estamos en PostgreSQL o SQLite
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                c.execute("SET search_path TO public")
            
            # Verificar si la tabla businesses existe
            if is_postgres:
                c.execute("""
                    SELECT EXISTS (
                        SELECT FROM information_schema.tables 
                        WHERE table_name = 'businesses'
                    )
                """)
                businesses_exists = c.fetchone()[0]
            else:
                c.execute("""
                    SELECT name FROM sqlite_master 
                    WHERE type='table' AND name='businesses'
                """)
                businesses_exists = c.fetchone() is not None
            
            if not businesses_exists:
                logger.warning("Tabla businesses no existe, creándola...")
                if is_postgres:
                    c.execute('''
                        CREATE TABLE businesses (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            admin_id TEXT NOT NULL,
                            web_user TEXT UNIQUE NOT NULL,
                            web_pass TEXT NOT NULL,
                            telegram_token TEXT,
                            email TEXT,
                            bot_configured BOOLEAN DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    ''')
                else:
                    c.execute('''
                        CREATE TABLE businesses (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            admin_id TEXT NOT NULL,
                            web_user TEXT UNIQUE NOT NULL,
                            web_pass TEXT NOT NULL,
                            telegram_token TEXT,
                            email TEXT,
                            bot_configured BOOLEAN DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    ''')
                logger.info("Tabla businesses creada exitosamente")
            else:
                # Verificar si existe la columna bot_configured
                if is_postgres:
                    c.execute("""
                        SELECT column_name 
                        FROM information_schema.columns 
                        WHERE table_name = 'businesses' AND column_name = 'bot_configured'
                    """)
                    has_bot_configured = c.fetchone() is not None
                else:
                    c.execute("PRAGMA table_info(businesses)")
                    columns = [col[1] for col in c.fetchall()]
                    has_bot_configured = 'bot_configured' in columns
                
                if not has_bot_configured:
                    logger.warning("Columna bot_configured no existe, agregándola...")
                    if is_postgres:
                        c.execute("ALTER TABLE businesses ADD COLUMN bot_configured BOOLEAN DEFAULT FALSE")
                    else:
                        c.execute("ALTER TABLE businesses ADD COLUMN bot_configured BOOLEAN DEFAULT FALSE")
                    logger.info("Columna bot_configured agregada exitosamente")
            
            # Verificar tabla users
            if is_postgres:
                c.execute("""
                    SELECT EXISTS (
                        SELECT FROM information_schema.tables 
                        WHERE table_name = 'users'
                    )
                """)
                users_exists = c.fetchone()[0]
            else:
                c.execute("""
                    SELECT name FROM sqlite_master 
                    WHERE type='table' AND name='users'
                """)
                users_exists = c.fetchone() is not None
            
            if not users_exists:
                logger.warning("Tabla users no existe, creándola...")
                if is_postgres:
                    c.execute('''
                        CREATE TABLE users (
                            id SERIAL PRIMARY KEY,
                            business_id TEXT NOT NULL,
                            username TEXT UNIQUE NOT NULL,
                            password TEXT NOT NULL,
                            role TEXT DEFAULT 'admin',
                            telegram_id TEXT,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
                        )
                    ''')
                else:
                    c.execute('''
                        CREATE TABLE users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            business_id TEXT NOT NULL,
                            username TEXT UNIQUE NOT NULL,
                            password TEXT NOT NULL,
                            role TEXT DEFAULT 'admin',
                            telegram_id TEXT,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
                        )
                    ''')
                logger.info("Tabla users creada exitosamente")
            else:
                # Verificar si existe la columna role
                if is_postgres:
                    c.execute("""
                        SELECT column_name 
                        FROM information_schema.columns 
                        WHERE table_name = 'users' AND column_name = 'role'
                    """)
                    has_role = c.fetchone() is not None
                else:
                    c.execute("PRAGMA table_info(users)")
                    columns = [col[1] for col in c.fetchall()]
                    has_role = 'role' in columns
                
                if not has_role:
                    logger.warning("Columna role no existe en users, agregándola...")
                    if is_postgres:
                        c.execute("ALTER TABLE users ADD COLUMN role TEXT DEFAULT 'admin'")
                    else:
                        c.execute("ALTER TABLE users ADD COLUMN role TEXT DEFAULT 'admin'")
                    logger.info("Columna role agregada exitosamente")
            
            # ==================== NUEVA TABLA: VENDORS ====================
            if is_postgres:
                c.execute("""
                    SELECT EXISTS (
                        SELECT FROM information_schema.tables 
                        WHERE table_name = 'vendors'
                    )
                """)
                vendors_exists = c.fetchone()[0]
            else:
                c.execute("""
                    SELECT name FROM sqlite_master 
                    WHERE type='table' AND name='vendors'
                """)
                vendors_exists = c.fetchone() is not None
            
            if not vendors_exists:
                logger.warning("Tabla vendors no existe, creándola...")
                if is_postgres:
                    c.execute('''
                        CREATE TABLE vendors (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            business_id TEXT NOT NULL,
                            role TEXT DEFAULT 'vendedor',
                            active BOOLEAN DEFAULT TRUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
                        )
                    ''')
                else:
                    c.execute('''
                        CREATE TABLE vendors (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            business_id TEXT NOT NULL,
                            role TEXT DEFAULT 'vendedor',
                            active INTEGER DEFAULT 1,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE CASCADE
                        )
                    ''')
                logger.info("Tabla vendors creada exitosamente")
            
            conn.commit()
            logger.info("Estructura de tablas globales verificada y corregida correctamente")
            
        except Exception as e:
            logger.error(f"Error verificando estructura de tablas: {e}")
            if conn:
                try:
                    conn.rollback()
                except:
                    pass
            cls._global_conn = None

    def __init__(self, business_id):
        self.business_id = business_id
        self.conn = None
        self.c = None
        self._get_connection()
        self._create_tables()
        self._create_test_data()
        logger.info(f"Conexión establecida para negocio: {business_id}")

    def _get_connection(self):
        """Obtener conexión según entorno"""
        try:
            if self.conn and hasattr(self.conn, 'closed') and not self.conn.closed:
                return self.conn
                
            db_url = os.environ.get('DATABASE_URL')
            
            if db_url:
                try:
                    max_retries = 5
                    for i in range(max_retries):
                        try:
                            self.conn = psycopg2.connect(db_url, sslmode='require')
                            with self.conn.cursor() as cur:
                                schema_name = f"business_{self.business_id.replace('-', '_').replace('.', '_')}"
                                cur.execute(f"CREATE SCHEMA IF NOT EXISTS {schema_name}")
                                cur.execute(f"SET search_path TO {schema_name}, public")
                            self.c = self.conn.cursor()
                            logger.info(f"Conexión establecida para negocio: {self.business_id} (PostgreSQL)")
                            return self.conn
                        except psycopg2.OperationalError as e:
                            if i < max_retries - 1:
                                wait_time = 2 ** i
                                logger.warning(f"Error conectando a PostgreSQL para negocio {self.business_id}, reintento {i+1}/{max_retries}: {e}")
                                time.sleep(wait_time)
                            else:
                                logger.error(f"No se pudo conectar a PostgreSQL para negocio {self.business_id} después de {max_retries} intentos")
                                raise
                except Exception as e:
                    logger.error(f"Error conectando a PostgreSQL para negocio {self.business_id}: {e}")
                    logger.info(f"Fallback a SQLite para negocio {self.business_id}")
                    db_path = f"{self.business_id}.db"
                    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
                    self.conn = sqlite3.connect(db_path, timeout=30, check_same_thread=False)
                    self.conn.execute("PRAGMA foreign_keys = ON")
                    self.c = self.conn.cursor()
                    logger.info(f"Conexión creada para negocio: {self.business_id} (SQLite - fallback)")
                    return self.conn
            else:
                db_path = f"{self.business_id}.db"
                Path(db_path).parent.mkdir(parents=True, exist_ok=True)
                self.conn = sqlite3.connect(db_path, timeout=30, check_same_thread=False)
                self.conn.execute("PRAGMA foreign_keys = ON")
                self.c = self.conn.cursor()
                logger.info(f"Conexión creada para negocio: {self.business_id} (SQLite)")
                return self.conn
                
        except Exception as e:
            logger.error(f"Error obteniendo conexión para negocio {self.business_id}: {e}")
            self.conn = sqlite3.connect(':memory:', check_same_thread=False)
            self.conn.execute("PRAGMA foreign_keys = ON")
            self.c = self.conn.cursor()
            return self.conn

    def _create_tables(self):
        """Crear tablas si no existen con sintaxis compatible"""
        try:
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                # Configurar search_path para usar el esquema del negocio
                schema_name = f"business_{self.business_id.replace('-', '_').replace('.', '_')}"
                self.c.execute(f"SET search_path TO {schema_name}, public")
                
                # Verificar si las tablas ya existen en este esquema
                self.c.execute("""
                    SELECT EXISTS (
                        SELECT FROM information_schema.tables 
                        WHERE table_schema = %s AND table_name = 'secciones'
                    )
                """, (schema_name,))
                tables_exist = self.c.fetchone()[0]
                
                if tables_exist:
                    logger.info(f"Las tablas ya existen para el negocio {self.business_id}")
                    return
                
                serial_type = "SERIAL PRIMARY KEY"
                foreign_key = "REFERENCES"
                timestamp_type = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                boolean_type = "BOOLEAN DEFAULT FALSE"
            else:
                serial_type = "INTEGER PRIMARY KEY AUTOINCREMENT"
                foreign_key = "REFERENCES"
                timestamp_type = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                boolean_type = "BOOLEAN DEFAULT FALSE"
            
            # Tabla secciones
            self.c.execute(f'''
                CREATE TABLE IF NOT EXISTS secciones (
                    id {serial_type},
                    nombre TEXT NOT NULL UNIQUE
                )
            ''')
            
            # Tabla productos
            self.c.execute(f'''
                CREATE TABLE IF NOT EXISTS productos (
                    id {serial_type},
                    nombre TEXT NOT NULL UNIQUE,
                    precio_venta DECIMAL(10,2) NOT NULL,
                    precio_compra DECIMAL(10,2) NOT NULL,
                    costo_transporte DECIMAL(10,2) DEFAULT 0,
                    seccion_id INTEGER {foreign_key} secciones(id),
                    stock INTEGER NOT NULL,
                    margen_ganancia DECIMAL(5,2),
                    descripcion TEXT
                )
            ''')
            
            # Tabla ventas
            self.c.execute(f'''
                CREATE TABLE IF NOT EXISTS ventas (
                    id {serial_type},
                    producto_id INTEGER {foreign_key} productos(id),
                    cantidad INTEGER NOT NULL,
                    usuario_id INTEGER,
                    fecha {timestamp_type}
                )
            ''')
            
            # Tabla inversiones
            self.c.execute(f'''
                CREATE TABLE IF NOT EXISTS inversiones (
                    id {serial_type},
                    producto_id INTEGER {foreign_key} productos(id),
                    cantidad INTEGER NOT NULL,
                    costo_total DECIMAL(10,2) NOT NULL,
                    descripcion TEXT NOT NULL,
                    fecha {timestamp_type}
                )
            ''')
            
            # Tabla objetivos financieros
            self.c.execute(f'''
                CREATE TABLE IF NOT EXISTS objetivos_financieros (
                    id {serial_type},
                    descripcion TEXT NOT NULL,
                    monto_objetivo DECIMAL(10,2) NOT NULL,
                    fecha_limite DATE,
                    monto_actual DECIMAL(10,2) DEFAULT 0,
                    completado {boolean_type}
                )
            ''')
            
            # Tabla vendedores (NUEVA)
            if is_postgres:
                self.c.execute(f'''
                    CREATE TABLE IF NOT EXISTS vendors (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        business_id TEXT NOT NULL,
                        role TEXT DEFAULT 'vendedor',
                        active {boolean_type},
                        created_at {timestamp_type}
                    )
                ''')
            else:
                self.c.execute(f'''
                    CREATE TABLE IF NOT EXISTS vendors (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        business_id TEXT NOT NULL,
                        role TEXT DEFAULT 'vendedor',
                        active INTEGER DEFAULT 1,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                ''')
            
            # Crear índices para mejorar rendimiento
            if is_postgres:
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_productos_seccion ON productos(seccion_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_ventas_producto ON ventas(producto_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ventas(fecha)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_inversiones_producto ON inversiones(producto_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_vendors_business ON vendors(business_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_vendors_active ON vendors(active)")
            else:
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_productos_seccion ON productos(seccion_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_ventas_producto ON ventas(producto_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ventas(fecha)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_inversiones_producto ON inversiones(producto_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_vendors_business ON vendors(business_id)")
                self.c.execute("CREATE INDEX IF NOT EXISTS idx_vendors_active ON vendors(active)")
            
            self.conn.commit()
            logger.info(f"Tablas creadas/verificadas para el negocio {self.business_id}")
        except Exception as e:
            logger.error(f"Error al crear tablas para el negocio {self.business_id}: {e}")
            if self.conn:
                self.conn.rollback()

    # ==================== NUEVO MÉTODO: CREAR DATOS DE PRUEBA ====================
    def _create_test_data(self):
        """Crear datos de prueba para desarrollo (admin y vendedor)"""
        try:
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            logger.info("Verificando datos de prueba...")
            
            # Verificar si ya hay un admin en la base de datos GLOBAL
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                logger.error("No se pudo obtener conexión global para crear datos de prueba")
                return
            
            c = conn.cursor()
            
            if is_postgres:
                c.execute("SET search_path TO public")
            
            # Verificar si ya existe el admin
            if is_postgres:
                c.execute("SELECT COUNT(*) FROM users WHERE username = 'admin'")
            else:
                c.execute("SELECT COUNT(*) FROM users WHERE username = 'admin'")
            
            admin_exists = c.fetchone()[0] > 0
            
            if admin_exists:
                logger.info("⚠️ Los datos de prueba ya existen, omitiendo creación")
                return
            
            logger.info("Creando datos de prueba...")
            
            # 1. Crear business_id fijo para pruebas
            business_id = 'test_business_001'
            
            # Verificar si el business ya existe
            if is_postgres:
                c.execute("SELECT id FROM businesses WHERE id = %s", (business_id,))
            else:
                c.execute("SELECT id FROM businesses WHERE id = ?", (business_id,))
            
            business_exists = c.fetchone() is not None
            
            # Hash de contraseña (admin123)
            hashed_password = bcrypt.hashpw('admin123'.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
            
            if not business_exists:
                # Crear negocio de prueba
                if is_postgres:
                    c.execute("""
                        INSERT INTO businesses (id, name, admin_id, web_user, web_pass, email)
                        VALUES (%s, %s, %s, %s, %s, %s)
                    """, (business_id, 'Tienda de Prueba', '123456789', 'admin', hashed_password, 'admin@test.com'))
                else:
                    c.execute("""
                        INSERT INTO businesses (id, name, admin_id, web_user, web_pass, email)
                        VALUES (?, ?, ?, ?, ?, ?)
                    """, (business_id, 'Tienda de Prueba', '123456789', 'admin', hashed_password, 'admin@test.com'))
                logger.info(f"✅ Negocio de prueba creado: {business_id}")
            else:
                logger.info(f"⚠️ Negocio {business_id} ya existe, omitiendo creación")
            
            # 2. Crear usuario admin
            if is_postgres:
                c.execute("""
                    INSERT INTO users (business_id, username, password, role, telegram_id)
                    VALUES (%s, %s, %s, %s, %s)
                """, (business_id, 'admin', hashed_password, 'admin', '123456789'))
            else:
                c.execute("""
                    INSERT INTO users (business_id, username, password, role, telegram_id)
                    VALUES (?, ?, ?, ?, ?)
                """, (business_id, 'admin', hashed_password, 'admin', '123456789'))
            logger.info("✅ Usuario admin creado: admin / admin123")
            
            conn.commit()
            
            # 3. Crear vendedor de prueba en la base de datos del negocio
            vendor_id = 'AAAA0000'  # ✅ ID fijo de 8 caracteres como solicitaste
            
            # Verificar si el vendedor ya existe
            if is_postgres:
                vendor_check = self.execute_query("SELECT id FROM vendors WHERE id = %s", (vendor_id,))
            else:
                vendor_check = self.execute_query("SELECT id FROM vendors WHERE id = ?", (vendor_id,))
            
            if not vendor_check:
                if is_postgres:
                    self.execute_query("""
                        INSERT INTO vendors (id, name, business_id, role, active)
                        VALUES (%s, %s, %s, %s, %s)
                    """, (vendor_id, 'Vendedor Prueba', business_id, 'vendedor', True))
                else:
                    self.execute_query("""
                        INSERT INTO vendors (id, name, business_id, role, active)
                        VALUES (?, ?, ?, ?, ?)
                    """, (vendor_id, 'Vendedor Prueba', business_id, 'vendedor', 1))
                logger.info(f"✅ Vendedor de prueba creado: ID = {vendor_id}")
            else:
                logger.info(f"⚠️ Vendedor {vendor_id} ya existe, omitiendo creación")
            
            # 4. Crear sección y producto de prueba
            if is_postgres:
                seccion_check = self.execute_query("SELECT id FROM secciones WHERE nombre = 'Electrónicos'")
            else:
                seccion_check = self.execute_query("SELECT id FROM secciones WHERE nombre = 'Electrónicos'")
            
            if not seccion_check:
                if is_postgres:
                    seccion_id = self.execute_query("""
                        INSERT INTO secciones (nombre) VALUES (%s) RETURNING id
                    """, ('Electrónicos',))
                else:
                    self.execute_query("""
                        INSERT INTO secciones (nombre) VALUES (?)
                    """, ('Electrónicos',))
                    seccion_id = self.c.lastrowid
                
                if is_postgres:
                    self.execute_query("""
                        INSERT INTO productos (nombre, precio_venta, precio_compra, stock, seccion_id)
                        VALUES (%s, %s, %s, %s, %s)
                    """, ('Producto Test', 100.00, 80.00, 10, seccion_id))
                else:
                    self.execute_query("""
                        INSERT INTO productos (nombre, precio_venta, precio_compra, stock, seccion_id)
                        VALUES (?, ?, ?, ?, ?)
                    """, ('Producto Test', 100.00, 80.00, 10, seccion_id))
                logger.info("✅ Producto de prueba creado: Producto Test ($100.00)")
            else:
                logger.info("⚠️ Producto de prueba ya existe, omitiendo creación")
            
            logger.info("=" * 50)
            logger.info("📋 DATOS DE PRUEBA CREADOS EXITOSAMENTE:")
            logger.info(f"   🏪 Negocio: {business_id} (Tienda de Prueba)")
            logger.info(f"   👤 Admin: usuario='admin' contraseña='admin123'")
            logger.info(f"   🆔 Vendedor ID: {vendor_id} (Vendedor Prueba)")
            logger.info(f"   📦 Producto: Producto Test ($100.00, Stock: 10)")
            logger.info("=" * 50)
            
        except Exception as e:
            logger.error(f"Error creando datos de prueba: {e}")
            if self.conn:
                self.conn.rollback()

    def execute_query(self, query, params=()):
        """Ejecutar consulta segura con manejo de errores y compatibilidad PostgreSQL/SQLite"""
        try:
            if not self.conn or (hasattr(self.conn, 'closed') and self.conn.closed):
                self._get_connection()
                
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                # Asegurar que el search_path esté configurado
                schema_name = f"business_{self.business_id.replace('-', '_').replace('.', '_')}"
                self.c.execute(f"SET search_path TO {schema_name}, public")
                formatted_query = query
            else:
                formatted_query = query.replace('%s', '?')
                
            self.c.execute(formatted_query, params)
            
            if query.strip().upper().startswith('SELECT'):
                return self.c.fetchall()
            else:
                self.conn.commit()
                if query.strip().upper().startswith(('INSERT', 'RETURNING')):
                    if is_postgres:
                        self.c.execute("SELECT LASTVAL()")
                        return self.c.fetchone()[0]
                    else:
                        return self.c.lastrowid
                return True
        except Exception as e:
            logger.error(f"Database error: {e}\nQuery: {query}\nParams: {params}")
            if self.conn:
                self.conn.rollback()
            return None

    def get_dataframe(self, query, params=()):
        """Obtener datos como DataFrame (compatible con PostgreSQL y SQLite)"""
        try:
            import pandas as pd
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                conn = psycopg2.connect(os.environ.get('DATABASE_URL'), sslmode='require')
                with conn.cursor() as cur:
                    schema_name = f"business_{self.business_id.replace('-', '_').replace('.', '_')}"
                    cur.execute(f"SET search_path TO {schema_name}, public")
                return pd.read_sql_query(query, conn, params=params)
            else:
                return pd.read_sql_query(query, self.conn, params=params)
        except Exception as e:
            logger.error(f"DataFrame error: {e}")
            return None

    def close(self):
        """Cerrar conexión"""
        if self.conn:
            try:
                self.conn.close()
                logger.info(f"Conexión cerrada para negocio: {self.business_id}")
            except Exception as e:
                logger.error(f"Error cerrando conexión: {e}")

    def __del__(self):
        self.close()
