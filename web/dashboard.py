# web/dashboard.py - Panel de administración web con LOGS A TELEGRAM
from flask import Flask, render_template, request, redirect, url_for, jsonify, g, session, flash, send_from_directory
from flask_login import LoginManager, UserMixin, login_user, login_required, logout_user, current_user
from flask_socketio import SocketIO
from database.db_manager import DatabaseManager
import os
from datetime import datetime, timedelta
import logging
import psycopg2
import random
import string
from slugify import slugify
import threading
import time
import bcrypt
import jwt
import json
import traceback

logger = logging.getLogger(__name__)

# Variable global para la función de log de Telegram
_telegram_log_func = None

def set_telegram_log_function(func):
    """Establecer la función de log de Telegram desde app.py"""
    global _telegram_log_func
    _telegram_log_func = func

def log_to_telegram(level, message, data=None, user=None, business_id=None, request_info=None):
    """Wrapper para la función de log de Telegram"""
    if _telegram_log_func:
        return _telegram_log_func(level, message, data, user, business_id, request_info)
    return False

def create_app():
    app = Flask(__name__, template_folder='../templates', static_folder='../static')
    app.secret_key = os.environ.get('FLASK_SECRET_KEY', 'secret-key-default')
    socketio = SocketIO(app, async_mode='threading', cors_allowed_origins="*")

    # Configuración de Flask-Login
    login_manager = LoginManager()
    login_manager.init_app(app)
    login_manager.login_view = 'login'

    class User(UserMixin):
        def __init__(self, user_id, business_id, username, role='admin'):
            self.id = user_id
            self.business_id = business_id
            self.username = username
            self.role = role

    # Variable global para almacenar conexiones de base de datos por negocio
    business_db_connections = {}
    business_db_lock = threading.Lock()

    def get_business_db_connection(business_id):
        """Obtener o crear una conexión de base de datos para un negocio específico"""
        with business_db_lock:
            if business_id not in business_db_connections:
                business_db_connections[business_id] = DatabaseManager(business_id)
            return business_db_connections[business_id]

    @login_manager.user_loader
    def load_user(user_id):
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                logger.error("No se pudo obtener conexión a la base de datos")
                log_to_telegram(
                    level='ERROR',
                    message="Error cargando usuario: No se pudo obtener conexión a la base de datos",
                    data={'user_id': user_id}
                )
                return None
                
            c = conn.cursor()
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                c.execute("SELECT id, business_id, username, role FROM users WHERE id = %s", (user_id,))
            else:
                c.execute("SELECT id, business_id, username, role FROM users WHERE id = ?", (user_id,))
            user_data = c.fetchone()
            if user_data:
                user = User(user_data[0], user_data[1], user_data[2], user_data[3] if len(user_data) > 3 else 'admin')
                
                log_to_telegram(
                    level='SUCCESS',
                    message=f"✅ Usuario cargado en sesión: {user.username}",
                    data={'user_id': user.id, 'role': user.role, 'business_id': user.business_id},
                    user=user,
                    business_id=user.business_id
                )
                return user
            else:
                log_to_telegram(
                    level='WARNING',
                    message=f"Usuario no encontrado: ID {user_id}",
                    data={'user_id': user_id}
                )
        except Exception as e:
            logger.error(f"Error loading user: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error cargando usuario: {str(e)}",
                data={'user_id': user_id, 'error': str(e), 'traceback': traceback.format_exc()}
            )
        return None

    @app.before_request
    def before_request():
        if current_user.is_authenticated:
            try:
                DatabaseManager.verify_and_fix_global_tables()
                
                g.db = get_business_db_connection(current_user.business_id)
                session['business_id'] = current_user.business_id
                
                if 'business_name' not in session:
                    conn = DatabaseManager.get_global_connection()
                    if conn is not None:
                        c = conn.cursor()
                        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
                        if is_postgres:
                            c.execute("SELECT name FROM businesses WHERE id = %s", (current_user.business_id,))
                        else:
                            c.execute("SELECT name FROM businesses WHERE id = ?", (current_user.business_id,))
                        business_data = c.fetchone()
                        if business_data:
                            session['business_name'] = business_data[0]
            except Exception as e:
                logger.error(f"Error in before_request: {e}")
                log_to_telegram(
                    level='ERROR',
                    message=f"Error en before_request: {str(e)}",
                    data={'error': str(e), 'traceback': traceback.format_exc()},
                    user=current_user if current_user.is_authenticated else None,
                    business_id=current_user.business_id if current_user.is_authenticated else None
                )

    # Funciones auxiliares
    def generate_random_string(length=4):
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))

    def get_last_insert_id(db, business_id):
        """Obtener el último ID insertado de forma compatible"""
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        if is_postgres:
            result = db.execute_query("SELECT LASTVAL()")
        else:
            result = db.execute_query("SELECT last_insert_rowid()")
        
        if result and result[0]:
            return result[0][0]
        return None

    # ==================== RUTA DE LANDING PAGE ====================
    @app.route('/')
    def index():
        """
        Landing page de OmniVentas.
        Si el usuario ya está autenticado, redirige al dashboard.
        """
        try:
            # Verificar si el usuario ya está logueado
            if current_user and current_user.is_authenticated:
                log_to_telegram(
                    level='INFO',
                    message=f"Usuario autenticado redirigido al dashboard desde landing",
                    data={'username': current_user.username},
                    user=current_user,
                    business_id=current_user.business_id
                )
                return redirect(url_for('dashboard'))
        
            # Si hay una sesión activa de configuración
            if session and session.get('business_id'):
                return redirect(url_for('dashboard'))
        
            # Renderizar la landing page
            return render_template('index.html')
        
        except Exception as e:
            logger.error(f"Error en landing page: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en landing page: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()}
            )
            # En caso de error, mostrar la landing sin funcionalidades extras
            return render_template('index.html')

    # ==================== RUTA DEL FAVICON ====================
    @app.route('/favicon.ico')
    def favicon():
        """Servir el favicon"""
        try:
            return send_from_directory(
                os.path.join(app.root_path, 'static'),
                'favicon.svg',
                mimetype='image/svg+xml'
            )
        except:
            return '', 404

    # ==================== SIGNUP CORREGIDO (SIN telegram_id) ====================
    @app.route('/signup', methods=['GET', 'POST'])
    def signup():
        if request.method == 'POST':
            request_info = {
                'method': request.method,
                'path': request.path,
                'ip': request.remote_addr,
                'user_agent': request.headers.get('User-Agent', 'N/A')
            }
            
            try:
                DatabaseManager.verify_and_fix_global_tables()
                
                business_name = request.form.get('business_name', '').strip()
                username = request.form.get('username', '').strip()
                password = request.form.get('password', '').strip()
                email = request.form.get('email', '').strip()
                
                if not all([business_name, username, password]):
                    log_to_telegram(
                        level='WARNING',
                        message="Intento de registro con campos faltantes",
                        data={'business_name': business_name, 'username': username},
                        request_info=request_info
                    )
                    return render_template('signup.html', error="Todos los campos son obligatorios")
                
                if len(password) < 8:
                    return render_template('signup.html', error="La contraseña debe tener al menos 8 caracteres")
            
                business_slug = slugify(business_name)
                business_id = f"{business_slug}_{generate_random_string(4)}"
            
                conn = DatabaseManager.get_global_connection()
                if conn is None:
                    return render_template('signup.html', error="Error de conexión a la base de datos")
                    
                c = conn.cursor()
                is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
                
                if is_postgres:
                    c.execute("SELECT id FROM users WHERE username = %s", (username,))
                else:
                    c.execute("SELECT id FROM users WHERE username = ?", (username,))
                
                if c.fetchone():
                    log_to_telegram(
                        level='WARNING',
                        message=f"Intento de registro con usuario existente: {username}",
                        data={'username': username},
                        request_info=request_info
                    )
                    return render_template('signup.html', error="El usuario ya existe")
            
                try:
                    if is_postgres:
                        c.execute("SELECT id FROM businesses WHERE email = %s", (email,))
                    else:
                        c.execute("SELECT id FROM businesses WHERE email = ?", (email,))
                    
                    if c.fetchone():
                        return render_template('signup.html', error="El email ya está registrado")
                except Exception:
                    pass
            
                # Hash de la contraseña
                hashed_password = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
            
                # INSERT en businesses SIN telegram_id
                if is_postgres:
                    c.execute('''
                        INSERT INTO businesses (id, name, admin_id, web_user, web_pass, email)
                        VALUES (%s, %s, %s, %s, %s, %s)
                    ''', (business_id, business_name, '123456789', username, hashed_password, email))
                else:
                    c.execute('''
                        INSERT INTO businesses (id, name, admin_id, web_user, web_pass, email)
                        VALUES (?, ?, ?, ?, ?, ?)
                    ''', (business_id, business_name, '123456789', username, hashed_password, email))
            
                # INSERT en users SIN telegram_id
                if is_postgres:
                    c.execute('''
                        INSERT INTO users (business_id, username, password, role)
                        VALUES (%s, %s, %s, 'admin')
                    ''', (business_id, username, hashed_password))
                else:
                    c.execute('''
                        INSERT INTO users (business_id, username, password, role)
                        VALUES (?, ?, ?, 'admin')
                    ''', (business_id, username, hashed_password))
            
                conn.commit()
                
                try:
                    # Crear la base de datos del negocio
                    db = DatabaseManager(business_id)
                    db._create_tables()
                except Exception as e:
                    logger.error(f"Error creando BD del negocio: {e}")
                    log_to_telegram(
                        level='ERROR',
                        message=f"Error creando BD del negocio: {str(e)}",
                        data={'business_id': business_id, 'error': str(e), 'traceback': traceback.format_exc()},
                        request_info=request_info
                    )
            
                session['new_business_id'] = business_id
                session['business_id'] = business_id
                session['new_business_name'] = business_name
                session['new_username'] = username
                
                log_to_telegram(
                    level='SUCCESS',
                    message=f"✅ Nuevo negocio registrado: {business_name}",
                    data={
                        'business_id': business_id,
                        'business_name': business_name,
                        'username': username,
                        'email': email
                    },
                    request_info=request_info
                )
                
                return redirect(url_for('initial_setup'))
            
            except Exception as e:
                logger.error(f"Error general en signup: {e}")
                log_to_telegram(
                    level='ERROR',
                    message=f"Error en signup: {str(e)}",
                    data={'error': str(e), 'traceback': traceback.format_exc()},
                    request_info=request_info
                )
                return render_template('signup.html', error=f"Error interno del sistema: {str(e)}")
        
        return render_template('signup.html')

    # ==================== LOGIN ====================
    @app.route('/login', methods=['GET', 'POST'])
    def login():
        message = request.args.get('message')
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr,
            'user_agent': request.headers.get('User-Agent', 'N/A')
        }
        
        if request.method == 'POST':
            try:
                DatabaseManager.verify_and_fix_global_tables()
                
                username = request.form.get('username', '').strip()
                password = request.form.get('password', '').strip()
                
                if not username or not password:
                    log_to_telegram(
                        level='WARNING',
                        message="Intento de login con campos faltantes",
                        data={'username': username if username else 'N/A'},
                        request_info=request_info
                    )
                    return render_template('login.html', error="Usuario y contraseña son requeridos", message=message)
                
                conn = DatabaseManager.get_global_connection()
                if conn is None:
                    logger.error("❌ No se pudo obtener conexión a la base de datos")
                    return render_template('login.html', error="Error de conexión a la base de datos", message=message)
                    
                c = conn.cursor()
                is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
                
                # Buscar usuario
                if is_postgres:
                    c.execute('''SELECT u.id, b.id, b.name, u.password, u.role
                              FROM users u 
                              JOIN businesses b ON u.business_id = b.id 
                              WHERE u.username = %s''', (username,))
                else:
                    c.execute('''SELECT u.id, b.id, b.name, u.password, u.role
                              FROM users u 
                              JOIN businesses b ON u.business_id = b.id 
                              WHERE u.username = ?''', (username,))
                user_data = c.fetchone()
                
                if user_data:
                    user_id, business_id, business_name, stored_password, role = user_data
                    
                    # Verificar contraseña con bcrypt
                    try:
                        if stored_password and stored_password.startswith('$2b$'):
                            if bcrypt.checkpw(password.encode('utf-8'), stored_password.encode('utf-8')):
                                user_obj = User(user_id, business_id, username, role)
                                login_user(user_obj)
                                session['business_name'] = business_name
                                session['business_id'] = business_id
                                session['role'] = role
                                
                                log_to_telegram(
                                    level='SUCCESS',
                                    message=f"✅ Login exitoso: {username}",
                                    data={
                                        'user_id': user_id,
                                        'business_id': business_id,
                                        'business_name': business_name,
                                        'role': role
                                    },
                                    user=user_obj,
                                    business_id=business_id,
                                    request_info=request_info
                                )
                                
                                return redirect(url_for('dashboard'))
                            else:
                                log_to_telegram(
                                    level='WARNING',
                                    message=f"Intento de login fallido: contraseña incorrecta para {username}",
                                    data={'username': username},
                                    request_info=request_info
                                )
                                return render_template('login.html', error="Contraseña incorrecta", message=message)
                        else:
                            # Si la contraseña no es bcrypt, intentar comparación directa (fallback)
                            logger.warning(f"⚠️ Contraseña almacenada no es bcrypt para {username}")
                            if password == stored_password:
                                # Re-hashear la contraseña con bcrypt
                                new_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
                                if is_postgres:
                                    c.execute("UPDATE users SET password = %s WHERE id = %s", (new_hash, user_id))
                                else:
                                    c.execute("UPDATE users SET password = ? WHERE id = ?", (new_hash, user_id))
                                conn.commit()
                                
                                user_obj = User(user_id, business_id, username, role)
                                login_user(user_obj)
                                session['business_name'] = business_name
                                session['business_id'] = business_id
                                session['role'] = role
                                
                                log_to_telegram(
                                    level='SUCCESS',
                                    message=f"✅ Login exitoso (contraseña migrada): {username}",
                                    data={
                                        'user_id': user_id,
                                        'business_id': business_id,
                                        'business_name': business_name,
                                        'role': role
                                    },
                                    user=user_obj,
                                    business_id=business_id,
                                    request_info=request_info
                                )
                                return redirect(url_for('dashboard'))
                            else:
                                return render_template('login.html', error="Contraseña incorrecta", message=message)
                    except ValueError as e:
                        logger.error(f"❌ Error verificando contraseña: {e}")
                        return render_template('login.html', error="Error de autenticación, contacta al administrador", message=message)
                else:
                    log_to_telegram(
                        level='WARNING',
                        message=f"Intento de login fallido: usuario no encontrado: {username}",
                        data={'username': username},
                        request_info=request_info
                    )
                    return render_template('login.html', error="Usuario no encontrado", message=message)
                        
            except Exception as e:
                logger.error(f"❌ Error en login: {e}")
                import traceback
                logger.error(traceback.format_exc())
                log_to_telegram(
                    level='ERROR',
                    message=f"Error en login: {str(e)}",
                    data={'error': str(e), 'traceback': traceback.format_exc()},
                    request_info=request_info
                )
                return render_template('login.html', error="Error interno del sistema. Contacta al administrador.", message=message)
        
        return render_template('login.html', message=message)

    @app.route('/logout')
    @login_required
    def logout():
        username = current_user.username
        business_id = current_user.business_id
        
        log_to_telegram(
            level='INFO',
            message=f"Usuario cerró sesión: {username}",
            data={'user_id': current_user.id},
            user=current_user,
            business_id=business_id
        )
        
        logout_user()
        session.clear()
        return redirect(url_for('login'))

    @app.route('/dashboard')
    @login_required
    def dashboard():
        business_name = session.get('business_name', 'Negocio')
        return render_template('dashboard.html', business_name=business_name)

    @app.route('/ventas')
    @login_required
    def ventas_page():
        business_name = session.get('business_name', 'Negocio')
        return render_template('ventas.html', business_name=business_name)

    @app.route('/inventario')
    @login_required
    def inventario_page():
        business_name = session.get('business_name', 'Negocio')
        return render_template('inventario.html', business_name=business_name)

    @app.route('/finanzas')
    @login_required
    def finanzas_page():
        business_name = session.get('business_name', 'Negocio')
        return render_template('finanzas.html', business_name=business_name)

    @app.route('/analisis')
    @login_required
    def analisis_page():
        business_name = session.get('business_name', 'Negocio')
        return render_template('analisis.html', business_name=business_name)

    @app.route('/clientes')
    @login_required
    def clientes_page():
        business_name = session.get('business_name', 'Negocio')
        return render_template('clientes.html', business_name=business_name)

    @app.route('/configuracion')
    @login_required
    def configuracion_page():
        business_name = session.get('business_name', 'Negocio')
        business_id = session.get('business_id')
        return render_template('configuracion.html', business_name=business_name, business_id=business_id)

    @app.route('/vendedores')
    @login_required
    def vendedores_page():
        """Página de gestión de vendedores"""
        if current_user.role != 'admin':
            flash('No tienes permisos para acceder a esta página', 'danger')
            log_to_telegram(
                level='WARNING',
                message=f"Intento de acceso no autorizado a vendedores_page por {current_user.username}",
                data={'role': current_user.role},
                user=current_user,
                business_id=current_user.business_id
            )
            return redirect(url_for('dashboard'))
        
        business_name = session.get('business_name', 'Negocio')
        return render_template('vendedores.html', business_name=business_name)

    @app.route('/initial_setup')
    def initial_setup():
        DatabaseManager.verify_and_fix_global_tables()
        
        if current_user.is_authenticated:
            business_name = session.get('business_name', 'Negocio')
            business_id = current_user.business_id
            username = current_user.username
            
            session['business_id'] = business_id
            
            try:
                db = get_business_db_connection(business_id)
                productos = db.execute_query("SELECT COUNT(*) FROM productos")
                hay_productos = productos and productos[0][0] > 0
            except:
                hay_productos = False
            
            return render_template('initial_setup.html', 
                                 business_name=business_name,
                                 business_id=business_id,
                                 username=username,
                                 hay_productos=hay_productos)
        
        elif 'new_business_id' in session:
            business_id = session.get('new_business_id')
            business_name = session.get('new_business_name', 'Negocio')
            username = session.get('new_username')
            
            session['business_id'] = business_id
            
            return render_template('initial_setup.html', 
                                 business_name=business_name,
                                 business_id=business_id,
                                 username=username)
        else:
            return redirect(url_for('signup'))

    # ==================== API ENDPOINTS ====================

    @app.route('/api/finish-setup', methods=['POST'])
    def finish_setup():
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            data = request.json
            business_id = data.get('business_id')
            
            if not business_id:
                return jsonify({'success': False, 'message': 'Business ID requerido'})
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                return jsonify({'success': False, 'message': 'Error de conexión a la base de datos'})
                
            c = conn.cursor()
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                c.execute(
                    "UPDATE businesses SET bot_configured = TRUE WHERE id = %s",
                    (business_id,)
                )
            else:
                c.execute(
                    "UPDATE businesses SET bot_configured = TRUE WHERE id = ?",
                    (business_id,)
                )
            conn.commit()
            
            session.pop('new_business_id', None)
            session.pop('new_business_name', None)
            session.pop('new_username', None)
            
            log_to_telegram(
                level='SUCCESS',
                message=f"✅ Configuración finalizada para negocio: {business_id}",
                data={'business_id': business_id},
                business_id=business_id,
                request_info=request_info
            )
            
            return jsonify({
                'success': True, 
                'message': 'Configuración completada exitosamente',
                'redirect': url_for('login', message='✅ Configuración completada. Ahora puedes iniciar sesión.')
            })
            
        except Exception as e:
            logger.error(f"Error finalizando configuración: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en finish_setup: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)})

    @app.route('/api/save-products', methods=['POST'])
    def save_products():
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            data = request.json
            products = data.get('products', [])
            business_id = data.get('business_id') or session.get('new_business_id') or (current_user.business_id if current_user.is_authenticated else None)
            
            if not business_id:
                return jsonify({'success': False, 'message': 'Business ID requerido'})
            
            db = get_business_db_connection(business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            productos_guardados = 0
            for product in products:
                seccion_nombre = product.get('category', 'General').strip()
                
                if not seccion_nombre:
                    seccion_nombre = 'General'
                
                if is_postgres:
                    seccion = db.execute_query(
                        "SELECT id FROM secciones WHERE nombre = %s", (seccion_nombre,)
                    )
                else:
                    seccion = db.execute_query(
                        "SELECT id FROM secciones WHERE nombre = ?", (seccion_nombre,)
                    )
                
                if seccion and seccion[0]:
                    seccion_id = seccion[0][0]
                else:
                    if is_postgres:
                        db.execute_query(
                            "INSERT INTO secciones (nombre) VALUES (%s)", (seccion_nombre,)
                        )
                    else:
                        db.execute_query(
                            "INSERT INTO secciones (nombre) VALUES (?)", (seccion_nombre,)
                        )
                    seccion_id = get_last_insert_id(db, business_id)
                
                if is_postgres:
                    db.execute_query(
                        "INSERT INTO productos (nombre, precio_venta, precio_compra, stock, seccion_id) "
                        "VALUES (%s, %s, %s, %s, %s)",
                        (product['name'], product['price'], product['cost'], product['stock'], seccion_id)
                    )
                else:
                    db.execute_query(
                        "INSERT INTO productos (nombre, precio_venta, precio_compra, stock, seccion_id) "
                        "VALUES (?, ?, ?, ?, ?)",
                        (product['name'], product['price'], product['cost'], product['stock'], seccion_id)
                    )
                productos_guardados += 1
            
            log_to_telegram(
                level='SUCCESS',
                message=f"✅ {productos_guardados} productos guardados",
                data={'total': productos_guardados, 'business_id': business_id},
                user=current_user if current_user.is_authenticated else None,
                business_id=business_id,
                request_info=request_info
            )
            
            return jsonify({
                'success': True, 
                'message': f'{productos_guardados} productos guardados correctamente',
                'business_id': business_id,
                'total': productos_guardados
            })
            
        except Exception as e:
            logger.error(f"Error saving products: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error guardando productos: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)})

    # ============================================================
    # API: DASHBOARD
    # ============================================================
    @app.route('/api/dashboard')
    @login_required
    def dashboard_data():
        """Obtener datos para el dashboard con mejor manejo de errores"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            hoy = datetime.now().strftime("%Y-%m-%d")
            mes_actual = datetime.now().strftime("%Y-%m")
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # 1. VENTAS DEL MES ACTUAL
            try:
                if is_postgres:
                    ventas_mes_query = """
                    SELECT COALESCE(SUM(v.cantidad * p.precio_venta), 0) 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    WHERE to_char(v.fecha, 'YYYY-MM') = %s
                    """
                else:
                    ventas_mes_query = """
                    SELECT COALESCE(SUM(v.cantidad * p.precio_venta), 0) 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    WHERE strftime('%%Y-%%m', v.fecha) = ?
                    """
                
                ventas_mes = g.db.execute_query(ventas_mes_query, (mes_actual,))
                ventas_mes = float(ventas_mes[0][0]) if ventas_mes and ventas_mes[0][0] else 0.0
            except Exception as e:
                logger.error(f"Error calculando ventas del mes: {e}")
                ventas_mes = 0.0
            
            # 2. GANANCIA DEL MES
            try:
                if is_postgres:
                    ganancia_query = """
                    SELECT COALESCE(SUM(v.cantidad * (p.precio_venta - p.precio_compra - COALESCE(p.costo_transporte, 0))), 0)
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    WHERE to_char(v.fecha, 'YYYY-MM') = %s
                    """
                else:
                    ganancia_query = """
                    SELECT COALESCE(SUM(v.cantidad * (p.precio_venta - p.precio_compra - COALESCE(p.costo_transporte, 0))), 0)
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    WHERE strftime('%%Y-%%m', v.fecha) = ?
                    """
                
                ganancia = g.db.execute_query(ganancia_query, (mes_actual,))
                ganancia = float(ganancia[0][0]) if ganancia and ganancia[0][0] else 0.0
            except Exception as e:
                logger.error(f"Error calculando ganancia: {e}")
                ganancia = 0.0
            
            # 3. MARGEN PROMEDIO
            margen = (ganancia / ventas_mes * 100) if ventas_mes > 0 else 0
            
            # 4. TOTAL DE VENTAS
            try:
                if is_postgres:
                    total_ventas_query = """
                    SELECT COUNT(*) FROM ventas
                    WHERE to_char(fecha, 'YYYY-MM') = %s
                    """
                else:
                    total_ventas_query = """
                    SELECT COUNT(*) FROM ventas
                    WHERE strftime('%%Y-%%m', fecha) = ?
                    """
                
                total_ventas = g.db.execute_query(total_ventas_query, (mes_actual,))
                total_ventas = int(total_ventas[0][0]) if total_ventas and total_ventas[0][0] else 0
            except Exception as e:
                logger.error(f"Error calculando total de ventas: {e}")
                total_ventas = 0
            
            # 5. VENTAS DE HOY
            try:
                if is_postgres:
                    ventas_hoy_query = """
                    SELECT p.nombre, SUM(v.cantidad), COALESCE(SUM(v.cantidad * p.precio_venta), 0) 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    WHERE DATE(v.fecha) = %s 
                    GROUP BY p.nombre
                    """
                else:
                    ventas_hoy_query = """
                    SELECT p.nombre, SUM(v.cantidad), COALESCE(SUM(v.cantidad * p.precio_venta), 0) 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    WHERE DATE(v.fecha) = ? 
                    GROUP BY p.nombre
                    """
                
                ventas_hoy = g.db.execute_query(ventas_hoy_query, (hoy,))
                ventas_hoy_list = []
                if ventas_hoy:
                    for row in ventas_hoy:
                        ventas_hoy_list.append({
                            'producto': row[0] if row[0] else 'Producto',
                            'cantidad': int(row[1]) if row[1] else 0,
                            'total': float(row[2]) if row[2] else 0.0
                        })
            except Exception as e:
                logger.error(f"Error obteniendo ventas de hoy: {e}")
                ventas_hoy_list = []
            
            # 6. INVENTARIO
            try:
                if is_postgres:
                    inventario_query = """
                    SELECT p.nombre, s.nombre, p.stock, p.precio_venta, p.precio_compra, 
                    ROUND((p.precio_venta - p.precio_compra) / NULLIF(p.precio_compra, 0) * 100, 2) as margen
                    FROM productos p 
                    JOIN secciones s ON p.seccion_id = s.id 
                    ORDER BY p.stock ASC
                    """
                else:
                    inventario_query = """
                    SELECT p.nombre, s.nombre, p.stock, p.precio_venta, p.precio_compra, 
                    ROUND((p.precio_venta - p.precio_compra) / NULLIF(p.precio_compra, 0) * 100, 2) as margen
                    FROM productos p 
                    JOIN secciones s ON p.seccion_id = s.id 
                    ORDER BY p.stock ASC
                    """
                
                inventario = g.db.execute_query(inventario_query)
                inventario_list = []
                if inventario:
                    for row in inventario:
                        inventario_list.append({
                            'nombre': row[0] if row[0] else 'Sin nombre',
                            'seccion': row[1] if row[1] else 'Sin sección',
                            'stock': int(row[2]) if row[2] else 0,
                            'precio_venta': float(row[3]) if row[3] else 0.0,
                            'precio_compra': float(row[4]) if row[4] else 0.0,
                            'margen': float(row[5]) if row[5] else 0.0
                        })
            except Exception as e:
                logger.error(f"Error obteniendo inventario: {e}")
                inventario_list = []
            
            # 7. DATOS PARA GRÁFICO MENSUAL
            try:
                if is_postgres:
                    ventas_mensuales_query = """
                    SELECT to_char(fecha, 'YYYY-MM') as mes, COALESCE(SUM(cantidad * precio_venta), 0) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                    """
                else:
                    ventas_mensuales_query = """
                    SELECT strftime('%%Y-%%m', fecha) as mes, COALESCE(SUM(cantidad * precio_venta), 0) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                    """
                
                ventas_mensuales = g.db.execute_query(ventas_mensuales_query)
                meses = []
                ventas_mensuales_list = []
                if ventas_mensuales:
                    for row in reversed(ventas_mensuales):
                        meses.append(row[0] if row[0] else 'Sin mes')
                        ventas_mensuales_list.append(float(row[1]) if row[1] else 0.0)
            except Exception as e:
                logger.error(f"Error obteniendo datos mensuales: {e}")
                meses = []
                ventas_mensuales_list = []
            
            # 8. TENDENCIAS
            try:
                if is_postgres:
                    mes_anterior = (datetime.now() - timedelta(days=30)).strftime("%Y-%m")
                    ventas_mes_anterior_query = """
                    SELECT COALESCE(SUM(v.cantidad * p.precio_venta), 0) 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    WHERE to_char(v.fecha, 'YYYY-MM') = %s
                    """
                else:
                    mes_anterior = (datetime.now() - timedelta(days=30)).strftime("%Y-%m")
                    ventas_mes_anterior_query = """
                    SELECT COALESCE(SUM(v.cantidad * p.precio_venta), 0) 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    WHERE strftime('%%Y-%%m', v.fecha) = ?
                    """
                
                ventas_mes_anterior = g.db.execute_query(ventas_mes_anterior_query, (mes_anterior,))
                ventas_mes_anterior = float(ventas_mes_anterior[0][0]) if ventas_mes_anterior and ventas_mes_anterior[0][0] else 0
                
                tendencia_ingresos = ((ventas_mes - ventas_mes_anterior) / ventas_mes_anterior * 100) if ventas_mes_anterior > 0 else 0
                
                tendencias = {
                    'ingresos': round(tendencia_ingresos, 1),
                    'ganancia': round(tendencia_ingresos * 0.8, 1) if abs(tendencia_ingresos) < 100 else 0,
                    'margen': round(tendencia_ingresos * 0.5, 1) if abs(tendencia_ingresos) < 100 else 0,
                    'ventas': round(tendencia_ingresos * 1.2, 1) if abs(tendencia_ingresos) < 100 else 0
                }
            except Exception as e:
                logger.error(f"Error calculando tendencias: {e}")
                tendencias = {
                    'ingresos': 0,
                    'ganancia': 0,
                    'margen': 0,
                    'ventas': 0
                }
            
            return jsonify({
                'ingresos': round(ventas_mes, 2),
                'ganancia': round(ganancia, 2),
                'margen': round(margen, 2),
                'ventas': total_ventas,
                'tendencias': tendencias,
                'ventas_hoy': ventas_hoy_list,
                'inventario': inventario_list,
                'ventas_mensuales': {
                    'meses': meses,
                    'ventas': ventas_mensuales_list
                }
            })
            
        except Exception as e:
            logger.error(f"Error en dashboard_data: {str(e)}")
            logger.error(traceback.format_exc())
            
            log_to_telegram(
                level='ERROR',
                message=f"Error en dashboard_data: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            
            return jsonify({
                'ingresos': 0.0,
                'ganancia': 0.0,
                'margen': 0.0,
                'ventas': 0,
                'tendencias': {
                    'ingresos': 0,
                    'ganancia': 0,
                    'margen': 0,
                    'ventas': 0
                },
                'ventas_hoy': [],
                'inventario': [],
                'ventas_mensuales': {
                    'meses': [],
                    'ventas': []
                },
                'error': str(e)
            }), 500

    # ============================================================
    # API: SALES DATA
    # ============================================================
    @app.route('/api/sales')
    @login_required
    def sales_data():
        try:
            period = request.args.get('period', 'monthly')
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if period == 'monthly':
                if is_postgres:
                    query = """
                    SELECT to_char(fecha, 'YYYY-MM') as mes, COALESCE(SUM(cantidad * precio_venta), 0) as total 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    GROUP BY mes 
                    ORDER BY mes DESC 
                    LIMIT 6
                    """
                else:
                    query = """
                    SELECT strftime('%%Y-%%m', fecha) as mes, COALESCE(SUM(cantidad * precio_venta), 0) as total 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    GROUP BY mes 
                    ORDER BY mes DESC 
                    LIMIT 6
                    """
                
                data = g.db.execute_query(query)
                if data:
                    meses = []
                    ventas = []
                    for row in reversed(data):
                        meses.append(row[0])
                        ventas.append(float(row[1]) if row[1] else 0.0)
                    return jsonify({'meses': meses, 'ventas': ventas})
                else:
                    return jsonify({'meses': [], 'ventas': []})
                
            elif period == 'weekly':
                semanas = []
                ventas = []
                hoy = datetime.now()
                for i in range(4):
                    inicio_semana = (hoy - timedelta(days=hoy.weekday() + 7*i)).strftime("%Y-%m-%d")
                    fin_semana = (hoy - timedelta(days=hoy.weekday() - 6 + 7*i)).strftime("%Y-%m-%d")
                    
                    if is_postgres:
                        total = g.db.execute_query(
                            "SELECT COALESCE(SUM(cantidad * precio_venta), 0) "
                            "FROM ventas v "
                            "JOIN productos p ON v.producto_id = p.id "
                            "WHERE fecha BETWEEN %s AND %s", 
                            (inicio_semana, fin_semana)
                        )
                    else:
                        total = g.db.execute_query(
                            "SELECT COALESCE(SUM(cantidad * precio_venta), 0) "
                            "FROM ventas v "
                            "JOIN productos p ON v.producto_id = p.id "
                            "WHERE fecha BETWEEN ? AND ?", 
                            (inicio_semana, fin_semana)
                        )
                    total = float(total[0][0]) if total and total[0][0] is not None else 0.0
                    
                    semanas.append(f"Sem {4-i}")
                    ventas.append(total)
                    
                return jsonify({'meses': semanas, 'ventas': ventas})
                
            else:  # daily
                dias = []
                ventas = []
                hoy = datetime.now()
                for i in range(7):
                    fecha = (hoy - timedelta(days=6-i)).strftime("%Y-%m-%d")
                    
                    if is_postgres:
                        total = g.db.execute_query(
                            "SELECT COALESCE(SUM(cantidad * precio_venta), 0) "
                            "FROM ventas v "
                            "JOIN productos p ON v.producto_id = p.id "
                            "WHERE DATE(fecha) = %s", 
                            (fecha,)
                        )
                    else:
                        total = g.db.execute_query(
                            "SELECT COALESCE(SUM(cantidad * precio_venta), 0) "
                            "FROM ventas v "
                            "JOIN productos p ON v.producto_id = p.id "
                            "WHERE DATE(fecha) = ?", 
                            (fecha,)
                        )
                    total = float(total[0][0]) if total and total[0][0] is not None else 0.0
                    
                    dia_nombre = (hoy - timedelta(days=6-i)).strftime("%a")
                    dias.append(f"{dia_nombre} {fecha.split('-')[2]}")
                    ventas.append(total)
                    
                return jsonify({'meses': dias, 'ventas': ventas})
        
        except Exception as e:
            logger.error(f"Error en sales_data: {str(e)}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en sales_data: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None
            )
            return jsonify({
                'error': 'Ocurrió un error al obtener datos de ventas',
                'details': str(e)
            }), 500

    # ============================================================
    # API: VENTAS (TABLA HISTÓRICO)
    # ============================================================
    @app.route('/api/ventas')
    @login_required
    def api_ventas():
        """Obtener datos de ventas para el panel"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            db = get_business_db_connection(current_user.business_id)
            
            fecha_inicio = request.args.get('fecha_inicio')
            fecha_fin = request.args.get('fecha_fin')
            producto = request.args.get('producto')
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                query = """
                    SELECT 
                        v.fecha,
                        p.nombre as producto,
                        v.cantidad,
                        p.precio_venta as precio_unitario,
                        v.cantidad * p.precio_venta as total,
                        v.cantidad * (p.precio_venta - p.precio_compra - COALESCE(p.costo_transporte, 0)) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    WHERE 1=1
                """
            else:
                query = """
                    SELECT 
                        v.fecha,
                        p.nombre as producto,
                        v.cantidad,
                        p.precio_venta as precio_unitario,
                        v.cantidad * p.precio_venta as total,
                        v.cantidad * (p.precio_venta - p.precio_compra - COALESCE(p.costo_transporte, 0)) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    WHERE 1=1
                """
            params = []
            
            if fecha_inicio:
                if is_postgres:
                    query += " AND v.fecha >= %s"
                else:
                    query += " AND v.fecha >= ?"
                params.append(fecha_inicio)
            if fecha_fin:
                if is_postgres:
                    query += " AND v.fecha <= %s"
                else:
                    query += " AND v.fecha <= ?"
                params.append(fecha_fin)
            if producto:
                if is_postgres:
                    query += " AND p.nombre ILIKE %s"
                else:
                    query += " AND p.nombre LIKE ?"
                params.append(f"%{producto}%")
                
            if is_postgres:
                query += " ORDER BY v.fecha DESC LIMIT 100"
            else:
                query += " ORDER BY v.fecha DESC LIMIT 100"
            
            resultados = db.execute_query(query, tuple(params))
            
            ventas = []
            total_ventas = 0
            ingresos = 0
            ganancia = 0
            
            if resultados:
                for row in resultados:
                    ventas.append({
                        'fecha': row[0],
                        'producto': row[1],
                        'cantidad': row[2] or 0,
                        'precio_unitario': float(row[3]) if row[3] else 0,
                        'total': float(row[4]) if row[4] else 0,
                        'ganancia': float(row[5]) if row[5] else 0
                    })
                    total_ventas += 1
                    ingresos += float(row[4]) if row[4] else 0
                    ganancia += float(row[5]) if row[5] else 0
            
            log_to_telegram(
                level='INFO',
                message=f"Ventas consultadas desde panel web por {current_user.username}",
                data={'total_ventas': total_ventas, 'ingresos': ingresos},
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({
                'ventas': ventas,
                'total_ventas': total_ventas,
                'ingresos': ingresos,
                'ganancia': ganancia,
                'ticket_promedio': ingresos / total_ventas if total_ventas > 0 else 0
            })
        except Exception as e:
            logger.error(f"Error en api_ventas: {str(e)}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en api_ventas: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'error': str(e)}), 500

    @app.route('/api/inventario')
    @login_required
    def api_inventario():
        """Obtener datos del inventario completo"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            db = get_business_db_connection(current_user.business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                query = """
                    SELECT 
                        p.id,
                        p.nombre,
                        s.nombre as seccion,
                        p.stock,
                        p.precio_venta,
                        p.precio_compra,
                        ROUND((p.precio_venta - p.precio_compra) / NULLIF(p.precio_compra, 0) * 100, 2) as margen
                    FROM productos p
                    JOIN secciones s ON p.seccion_id = s.id
                    ORDER BY p.nombre
                """
            else:
                query = """
                    SELECT 
                        p.id,
                        p.nombre,
                        s.nombre as seccion,
                        p.stock,
                        p.precio_venta,
                        p.precio_compra,
                        ROUND((p.precio_venta - p.precio_compra) / NULLIF(p.precio_compra, 0) * 100, 2) as margen
                    FROM productos p
                    JOIN secciones s ON p.seccion_id = s.id
                    ORDER BY p.nombre
                """
            
            resultados = db.execute_query(query)
            
            productos = []
            total_valor = 0
            stock_bajo = 0
            sin_stock = 0
            
            if resultados:
                for row in resultados:
                    stock = row[3] or 0
                    precio_compra = float(row[5]) if row[5] else 0
                    producto = {
                        'id': row[0],
                        'nombre': row[1],
                        'seccion': row[2],
                        'stock': stock,
                        'precio_venta': float(row[4]) if row[4] else 0,
                        'precio_compra': precio_compra,
                        'margen': float(row[6]) if row[6] else 0
                    }
                    productos.append(producto)
                    total_valor += stock * precio_compra
                    if stock <= 3:
                        stock_bajo += 1
                    if stock == 0:
                        sin_stock += 1
            
            return jsonify({
                'productos': productos,
                'total': len(productos),
                'valor_total': total_valor,
                'stock_bajo': stock_bajo,
                'sin_stock': sin_stock
            })
        except Exception as e:
            logger.error(f"Error en api_inventario: {str(e)}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en api_inventario: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'error': str(e)}), 500

    @app.route('/api/producto', methods=['POST'])
    @login_required
    def agregar_producto():
        """Agregar un nuevo producto"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            data = request.json
            nombre = data.get('nombre', '').strip()
            seccion = data.get('seccion', '').strip()
            precio_venta = data.get('precio_venta')
            precio_compra = data.get('precio_compra')
            stock = data.get('stock')
            costo_transporte = data.get('costo_transporte', 0)
            
            if not all([nombre, seccion, precio_venta, precio_compra, stock is not None]):
                return jsonify({'success': False, 'message': 'Todos los campos son requeridos'}), 400
            
            db = get_business_db_connection(current_user.business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # Obtener o crear sección
            if is_postgres:
                seccion_result = db.execute_query("SELECT id FROM secciones WHERE nombre = %s", (seccion,))
            else:
                seccion_result = db.execute_query("SELECT id FROM secciones WHERE nombre = ?", (seccion,))
            
            if seccion_result and seccion_result[0]:
                seccion_id = seccion_result[0][0]
            else:
                if is_postgres:
                    db.execute_query("INSERT INTO secciones (nombre) VALUES (%s)", (seccion,))
                else:
                    db.execute_query("INSERT INTO secciones (nombre) VALUES (?)", (seccion,))
                seccion_id = get_last_insert_id(db, current_user.business_id)
            
            # Insertar producto
            if is_postgres:
                db.execute_query("""
                    INSERT INTO productos (nombre, precio_venta, precio_compra, stock, seccion_id, costo_transporte)
                    VALUES (%s, %s, %s, %s, %s, %s)
                """, (nombre, precio_venta, precio_compra, stock, seccion_id, costo_transporte))
            else:
                db.execute_query("""
                    INSERT INTO productos (nombre, precio_venta, precio_compra, stock, seccion_id, costo_transporte)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (nombre, precio_venta, precio_compra, stock, seccion_id, costo_transporte))
            
            log_to_telegram(
                level='SUCCESS',
                message=f"✅ Nuevo producto agregado: {nombre}",
                data={
                    'nombre': nombre,
                    'seccion': seccion,
                    'precio_venta': precio_venta,
                    'stock': stock
                },
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({'success': True, 'message': 'Producto agregado correctamente'})
            
        except Exception as e:
            logger.error(f"Error en agregar_producto: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en agregar_producto: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    @app.route('/api/producto/<int:producto_id>', methods=['PUT'])
    @login_required
    def actualizar_producto(producto_id):
        """Actualizar un producto existente"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            data = request.json
            nombre = data.get('nombre', '').strip()
            seccion = data.get('seccion', '').strip()
            precio_venta = data.get('precio_venta')
            precio_compra = data.get('precio_compra')
            stock = data.get('stock')
            costo_transporte = data.get('costo_transporte', 0)
            
            if not all([nombre, seccion, precio_venta, precio_compra, stock is not None]):
                return jsonify({'success': False, 'message': 'Todos los campos son requeridos'}), 400
            
            db = get_business_db_connection(current_user.business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # Obtener o crear sección
            if is_postgres:
                seccion_result = db.execute_query("SELECT id FROM secciones WHERE nombre = %s", (seccion,))
            else:
                seccion_result = db.execute_query("SELECT id FROM secciones WHERE nombre = ?", (seccion,))
            
            if seccion_result and seccion_result[0]:
                seccion_id = seccion_result[0][0]
            else:
                if is_postgres:
                    db.execute_query("INSERT INTO secciones (nombre) VALUES (%s)", (seccion,))
                else:
                    db.execute_query("INSERT INTO secciones (nombre) VALUES (?)", (seccion,))
                seccion_id = get_last_insert_id(db, current_user.business_id)
            
            # Actualizar producto
            if is_postgres:
                db.execute_query("""
                    UPDATE productos 
                    SET nombre = %s, precio_venta = %s, precio_compra = %s, stock = %s, 
                        seccion_id = %s, costo_transporte = %s
                    WHERE id = %s
                """, (nombre, precio_venta, precio_compra, stock, seccion_id, costo_transporte, producto_id))
            else:
                db.execute_query("""
                    UPDATE productos 
                    SET nombre = ?, precio_venta = ?, precio_compra = ?, stock = ?, 
                        seccion_id = ?, costo_transporte = ?
                    WHERE id = ?
                """, (nombre, precio_venta, precio_compra, stock, seccion_id, costo_transporte, producto_id))
            
            log_to_telegram(
                level='SUCCESS',
                message=f"✅ Producto actualizado: {nombre} (ID: {producto_id})",
                data={
                    'producto_id': producto_id,
                    'nombre': nombre,
                    'seccion': seccion,
                    'precio_venta': precio_venta,
                    'stock': stock
                },
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({'success': True, 'message': 'Producto actualizado correctamente'})
            
        except Exception as e:
            logger.error(f"Error en actualizar_producto: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en actualizar_producto: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    @app.route('/api/producto/<int:producto_id>', methods=['DELETE'])
    @login_required
    def eliminar_producto(producto_id):
        """Eliminar un producto"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            db = get_business_db_connection(current_user.business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # Obtener nombre del producto antes de eliminar
            if is_postgres:
                producto_info = db.execute_query("SELECT nombre FROM productos WHERE id = %s", (producto_id,))
            else:
                producto_info = db.execute_query("SELECT nombre FROM productos WHERE id = ?", (producto_id,))
            
            nombre_producto = producto_info[0][0] if producto_info else f"ID {producto_id}"
            
            if is_postgres:
                db.execute_query("DELETE FROM productos WHERE id = %s", (producto_id,))
            else:
                db.execute_query("DELETE FROM productos WHERE id = ?", (producto_id,))
            
            log_to_telegram(
                level='WARNING',
                message=f"Producto eliminado: {nombre_producto} (ID: {producto_id})",
                data={'producto_id': producto_id, 'nombre': nombre_producto},
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({'success': True, 'message': 'Producto eliminado correctamente'})
            
        except Exception as e:
            logger.error(f"Error en eliminar_producto: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en eliminar_producto: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    @app.route('/api/registrar-venta-web', methods=['POST'])
    @login_required
    def registrar_venta_web():
        """Registrar una venta desde el panel web"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            data = request.json
            producto_id = data.get('producto_id')
            cantidad = data.get('cantidad')
            precio_unitario = data.get('precio_unitario')
            
            if not all([producto_id, cantidad, precio_unitario]):
                return jsonify({'success': False, 'message': 'Faltan datos'}), 400
            
            db = get_business_db_connection(current_user.business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # Verificar stock
            stock_query = "SELECT stock, nombre FROM productos WHERE id = %s" if is_postgres else "SELECT stock, nombre FROM productos WHERE id = ?"
            stock_result = db.execute_query(stock_query, (producto_id,))
            
            if not stock_result:
                return jsonify({'success': False, 'message': 'Producto no encontrado'}), 404
            
            stock_disponible = stock_result[0][0]
            nombre_producto = stock_result[0][1] if len(stock_result[0]) > 1 else 'Producto'
            
            if stock_disponible < cantidad:
                log_to_telegram(
                    level='WARNING',
                    message=f"Intento de venta con stock insuficiente: {nombre_producto}",
                    data={
                        'producto': nombre_producto,
                        'stock_disponible': stock_disponible,
                        'cantidad_solicitada': cantidad
                    },
                    user=current_user,
                    business_id=current_user.business_id,
                    request_info=request_info
                )
                return jsonify({'success': False, 'message': f'Stock insuficiente. Disponible: {stock_disponible}'}), 400
            
            # Registrar venta
            insert_query = """
                INSERT INTO ventas (producto_id, cantidad, usuario_id) 
                VALUES (%s, %s, %s)
            """ if is_postgres else """
                INSERT INTO ventas (producto_id, cantidad, usuario_id) 
                VALUES (?, ?, ?)
            """
            db.execute_query(insert_query, (producto_id, cantidad, current_user.id))
            
            # Actualizar stock
            update_query = "UPDATE productos SET stock = stock - %s WHERE id = %s" if is_postgres else "UPDATE productos SET stock = stock - ? WHERE id = ?"
            db.execute_query(update_query, (cantidad, producto_id))
            
            total = cantidad * precio_unitario
            
            log_to_telegram(
                level='SUCCESS',
                message=f"✅ Venta registrada desde panel web",
                data={
                    'producto': nombre_producto,
                    'producto_id': producto_id,
                    'cantidad': cantidad,
                    'total': total,
                    'stock_restante': stock_disponible - cantidad
                },
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({'success': True, 'message': 'Venta registrada correctamente'})
            
        except Exception as e:
            logger.error(f"Error en registrar_venta: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en registrar_venta_web: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    @app.route('/api/finanzas')
    @login_required
    def api_finanzas():
        """Obtener datos financieros"""
        try:
            db = get_business_db_connection(current_user.business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # Ingresos mensuales
            if is_postgres:
                query_ingresos = """
                    SELECT to_char(fecha, 'YYYY-MM') as mes, COALESCE(SUM(cantidad * precio_venta), 0) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            else:
                query_ingresos = """
                    SELECT strftime('%Y-%m', fecha) as mes, COALESCE(SUM(cantidad * precio_venta), 0) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            
            ingresos_mensuales = db.execute_query(query_ingresos)
            
            meses = []
            ingresos = []
            gastos = []
            
            if ingresos_mensuales:
                for row in reversed(ingresos_mensuales):
                    meses.append(row[0])
                    ingresos.append(float(row[1]) if row[1] else 0)
            
            # Gastos (basados en costo de productos)
            if is_postgres:
                query_gastos = """
                    SELECT to_char(fecha, 'YYYY-MM') as mes, COALESCE(SUM(cantidad * (precio_compra + COALESCE(costo_transporte, 0))), 0) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            else:
                query_gastos = """
                    SELECT strftime('%Y-%m', fecha) as mes, COALESCE(SUM(cantidad * (precio_compra + COALESCE(costo_transporte, 0))), 0) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            
            gastos_mensuales = db.execute_query(query_gastos)
            
            if gastos_mensuales:
                gastos = [float(row[1]) if row[1] else 0 for row in reversed(gastos_mensuales)]
            
            # Asegurar que gastos tenga la misma longitud que ingresos
            while len(gastos) < len(ingresos):
                gastos.append(0)
            
            total_ingresos = sum(ingresos)
            total_gastos = sum(gastos)
            
            return jsonify({
                'ingresos': total_ingresos,
                'gastos': total_gastos,
                'beneficio': total_ingresos - total_gastos,
                'meses': meses,
                'ingresos_mensuales': ingresos,
                'gastos_mensuales': gastos,
                'categorias_gastos': ['Productos', 'Transporte', 'Publicidad', 'Otros'],
                'valores_gastos': [total_gastos * 0.6 if total_gastos > 0 else 0, 
                                  total_gastos * 0.2 if total_gastos > 0 else 0, 
                                  total_gastos * 0.15 if total_gastos > 0 else 0, 
                                  total_gastos * 0.05 if total_gastos > 0 else 0]
            })
        except Exception as e:
            logger.error(f"Error en api_finanzas: {str(e)}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en api_finanzas: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None
            )
            return jsonify({'error': str(e)}), 500

    @app.route('/api/analisis')
    @login_required
    def api_analisis():
        """Obtener datos de análisis avanzado"""
        try:
            db = get_business_db_connection(current_user.business_id)
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # Top productos
            if is_postgres:
                top_query = """
                    SELECT p.nombre, SUM(v.cantidad) as ventas, COALESCE(SUM(v.cantidad * (p.precio_venta - p.precio_compra)), 0) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY p.nombre
                    ORDER BY ventas DESC
                    LIMIT 10
                """
            else:
                top_query = """
                    SELECT p.nombre, SUM(v.cantidad) as ventas, COALESCE(SUM(v.cantidad * (p.precio_venta - p.precio_compra)), 0) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY p.nombre
                    ORDER BY ventas DESC
                    LIMIT 10
                """
            
            top_productos = db.execute_query(top_query)
            
            top = []
            if top_productos:
                for row in top_productos:
                    top.append({
                        'nombre': row[0],
                        'ventas': row[1] or 0,
                        'ganancia': float(row[2]) if row[2] else 0
                    })
            
            # Tendencia
            if is_postgres:
                tendencia_query = """
                    SELECT to_char(fecha, 'YYYY-MM') as mes, COALESCE(SUM(cantidad), 0) as total
                    FROM ventas
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            else:
                tendencia_query = """
                    SELECT strftime('%Y-%m', fecha) as mes, COALESCE(SUM(cantidad), 0) as total
                    FROM ventas
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            
            tendencia = db.execute_query(tendencia_query)
            
            tendencia_meses = []
            tendencia_valores = []
            if tendencia:
                for row in reversed(tendencia):
                    tendencia_meses.append(row[0])
                    tendencia_valores.append(row[1] or 0)
            
            # Clasificación ABC
            if is_postgres:
                abc_query = """
                    SELECT 
                        p.nombre,
                        COALESCE(SUM(v.cantidad), 0) as ventas,
                        COALESCE(SUM(v.cantidad * (p.precio_venta - p.precio_compra)), 0) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY p.nombre
                    ORDER BY ganancia DESC
                """
            else:
                abc_query = """
                    SELECT 
                        p.nombre,
                        COALESCE(SUM(v.cantidad), 0) as ventas,
                        COALESCE(SUM(v.cantidad * (p.precio_venta - p.precio_compra)), 0) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY p.nombre
                    ORDER BY ganancia DESC
                """
            
            abc_data = db.execute_query(abc_query)
            
            abc = []
            total_ganancia = 0
            temp = []
            
            if abc_data:
                for row in abc_data:
                    ganancia = float(row[2]) if row[2] else 0
                    temp.append({
                        'producto': row[0],
                        'ventas': row[1] or 0,
                        'ganancia': ganancia
                    })
                    total_ganancia += ganancia
                
                acumulado = 0
                for item in temp:
                    acumulado += item['ganancia']
                    contribucion = (item['ganancia'] / total_ganancia * 100) if total_ganancia > 0 else 0
                    porcentaje_acumulado = (acumulado / total_ganancia * 100) if total_ganancia > 0 else 0
                    clasificacion = 'A' if porcentaje_acumulado <= 80 else 'B' if porcentaje_acumulado <= 95 else 'C'
                    abc.append({
                        'producto': item['producto'],
                        'ventas': item['ventas'],
                        'ganancia': item['ganancia'],
                        'contribucion': contribucion,
                        'clasificacion': clasificacion
                    })
            
            return jsonify({
                'top_productos': top,
                'tendencia_meses': tendencia_meses,
                'tendencia_valores': tendencia_valores,
                'abc': abc
            })
        except Exception as e:
            logger.error(f"Error en api_analisis: {str(e)}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en api_analisis: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None
            )
            return jsonify({'error': str(e)}), 500

    @app.route('/api/clientes')
    @login_required
    def api_clientes():
        """Obtener datos de clientes"""
        try:
            return jsonify({
                'clientes': [],
                'top_clientes': [],
                'clientes_meses': ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun'],
                'clientes_por_mes': [0, 0, 0, 0, 0, 0]
            })
        except Exception as e:
            logger.error(f"Error en api_clientes: {str(e)}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en api_clientes: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None
            )
            return jsonify({'error': str(e)}), 500

    @app.route('/api/cliente', methods=['POST'])
    @login_required
    def agregar_cliente():
        """Agregar un cliente"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        log_to_telegram(
            level='INFO',
            message="Funcionalidad de cliente en desarrollo (POST)",
            user=current_user,
            business_id=current_user.business_id,
            request_info=request_info
        )
        return jsonify({'success': True, 'message': 'Cliente agregado (funcionalidad en desarrollo)'})

    @app.route('/api/cliente/<int:cliente_id>', methods=['DELETE'])
    @login_required
    def eliminar_cliente(cliente_id):
        """Eliminar un cliente"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        log_to_telegram(
            level='INFO',
            message=f"Funcionalidad de cliente en desarrollo (DELETE ID: {cliente_id})",
            user=current_user,
            business_id=current_user.business_id,
            request_info=request_info
        )
        return jsonify({'success': True, 'message': 'Cliente eliminado (funcionalidad en desarrollo)'})

    @app.route('/api/configuracion', methods=['POST'])
    @login_required
    def guardar_configuracion():
        """Guardar configuración del negocio"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            data = request.json
            nombre = data.get('nombre', '').strip()
            email = data.get('email', '').strip()
            telefono = data.get('telefono', '').strip()
            direccion = data.get('direccion', '').strip()
            
            if not nombre:
                return jsonify({'success': False, 'message': 'El nombre es requerido'}), 400
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                return jsonify({'success': False, 'message': 'Error de conexión'}), 500
            
            c = conn.cursor()
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                c.execute("""
                    UPDATE businesses 
                    SET name = %s, email = %s 
                    WHERE id = %s
                """, (nombre, email, current_user.business_id))
            else:
                c.execute("""
                    UPDATE businesses 
                    SET name = ?, email = ? 
                    WHERE id = ?
                """, (nombre, email, current_user.business_id))
            conn.commit()
            
            session['business_name'] = nombre
            
            log_to_telegram(
                level='SUCCESS',
                message=f"Configuración actualizada: {nombre}",
                data={'nombre': nombre, 'email': email},
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({'success': True, 'message': 'Configuración guardada correctamente'})
            
        except Exception as e:
            logger.error(f"Error en guardar_configuracion: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en guardar_configuracion: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    @app.route('/api/cambiar-password', methods=['POST'])
    @login_required
    def cambiar_password():
        """Cambiar contraseña del usuario"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            data = request.json
            nueva_password = data.get('password', '').strip()
            
            if len(nueva_password) < 6:
                return jsonify({'success': False, 'message': 'La contraseña debe tener al menos 6 caracteres'}), 400
            
            hashed_password = bcrypt.hashpw(nueva_password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                return jsonify({'success': False, 'message': 'Error de conexión'}), 500
            
            c = conn.cursor()
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                c.execute("UPDATE users SET password = %s WHERE id = %s", (hashed_password, current_user.id))
                c.execute("UPDATE businesses SET web_pass = %s WHERE id = %s", (hashed_password, current_user.business_id))
            else:
                c.execute("UPDATE users SET password = ? WHERE id = ?", (hashed_password, current_user.id))
                c.execute("UPDATE businesses SET web_pass = ? WHERE id = ?", (hashed_password, current_user.business_id))
            conn.commit()
            
            log_to_telegram(
                level='SUCCESS',
                message=f"Contraseña cambiada para usuario: {current_user.username}",
                data={'user_id': current_user.id},
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({'success': True, 'message': 'Contraseña cambiada correctamente'})
            
        except Exception as e:
            logger.error(f"Error en cambiar_password: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en cambiar_password: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    @app.route('/api/eliminar-datos', methods=['POST'])
    @login_required
    def eliminar_datos():
        """Eliminar todos los datos del negocio"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            db = get_business_db_connection(current_user.business_id)
            
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                db.execute_query("DELETE FROM ventas")
                db.execute_query("DELETE FROM inversiones")
                db.execute_query("DELETE FROM objetivos_financieros")
                db.execute_query("DELETE FROM productos")
                db.execute_query("DELETE FROM secciones")
            else:
                db.execute_query("DELETE FROM ventas")
                db.execute_query("DELETE FROM inversiones")
                db.execute_query("DELETE FROM objetivos_financieros")
                db.execute_query("DELETE FROM productos")
                db.execute_query("DELETE FROM secciones")
            
            log_to_telegram(
                level='WARNING',
                message=f"⚠️ Todos los datos eliminados del negocio",
                data={'business_id': current_user.business_id},
                user=current_user,
                business_id=current_user.business_id,
                request_info=request_info
            )
            
            return jsonify({'success': True, 'message': 'Todos los datos eliminados correctamente'})
            
        except Exception as e:
            logger.error(f"Error en eliminar_datos: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en eliminar_datos: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    @app.route('/api/eliminar-cuenta', methods=['POST'])
    @login_required
    def eliminar_cuenta():
        """Eliminar la cuenta del negocio"""
        request_info = {
            'method': request.method,
            'path': request.path,
            'ip': request.remote_addr
        }
        
        try:
            business_id = current_user.business_id
            username = current_user.username
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                return jsonify({'success': False, 'message': 'Error de conexión'}), 500
            
            c = conn.cursor()
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if is_postgres:
                c.execute("DELETE FROM users WHERE id = %s", (current_user.id,))
                c.execute("DELETE FROM businesses WHERE id = %s", (business_id,))
            else:
                c.execute("DELETE FROM users WHERE id = ?", (current_user.id,))
                c.execute("DELETE FROM businesses WHERE id = ?", (business_id,))
            conn.commit()
            
            if not is_postgres:
                import os
                db_path = f"{business_id}.db"
                if os.path.exists(db_path):
                    os.remove(db_path)
            
            log_to_telegram(
                level='CRITICAL',
                message=f"🔥 Cuenta eliminada: {username} (Business: {business_id})",
                data={
                    'business_id': business_id,
                    'username': username,
                    'user_id': current_user.id
                },
                user=current_user,
                business_id=business_id,
                request_info=request_info
            )
            
            logout_user()
            session.clear()
            
            return jsonify({'success': True, 'message': 'Cuenta eliminada correctamente'})
            
        except Exception as e:
            logger.error(f"Error en eliminar_cuenta: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en eliminar_cuenta: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()},
                user=current_user if current_user.is_authenticated else None,
                business_id=current_user.business_id if current_user.is_authenticated else None,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': str(e)}), 500

    # ==================== RUTAS ADICIONALES ====================

    @app.route('/terms')
    def terms():
        return render_template('terms.html')

    @app.route('/privacy')
    def privacy():
        return render_template('privacy.html')

    # ============================================================
    # HANDLERS DE WEBSOCKET
    # ============================================================
    @socketio.on('connect')
    def handle_connect():
        try:
            if 'business_id' in session:
                business_id = session['business_id']
                socketio.server.enter_room(request.sid, business_id)
                logger.info(f"Cliente conectado a sala de negocio: {business_id}")
                
                log_to_telegram(
                    level='INFO',
                    message=f"WebSocket conectado",
                    data={'business_id': business_id, 'sid': request.sid},
                    business_id=business_id
                )
        except Exception as e:
            logger.error(f"Error en handle_connect: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error en WebSocket connect: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()}
            )

    @socketio.on('disconnect')
    def handle_disconnect():
        logger.info(f"Cliente desconectado: {request.sid}")
        log_to_telegram(
            level='INFO',
            message=f"WebSocket desconectado",
            data={'sid': request.sid}
        )

    return app
