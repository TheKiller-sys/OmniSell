# web/dashboard.py - Panel de administración web unificado
from flask import Flask, render_template, request, redirect, url_for, jsonify, g, session
from flask_login import LoginManager, UserMixin, login_user, login_required, logout_user, current_user
from flask_socketio import SocketIO
from database.db_manager import DatabaseManager
import os
from datetime import datetime, timedelta
import logging
import psycopg2
import requests
import random
import string
from slugify import slugify
import threading
import time
import bcrypt
from urllib.parse import urljoin

logger = logging.getLogger(__name__)

def create_app():
    app = Flask(__name__, template_folder='../templates', static_folder='../static')
    app.secret_key = os.environ.get('FLASK_SECRET_KEY', 'secret-key-default')
    socketio = SocketIO(app, async_mode='threading', cors_allowed_origins="*")

    # Configuración de Flask-Login
    login_manager = LoginManager()
    login_manager.init_app(app)
    login_manager.login_view = 'login'

    class User(UserMixin):
        def __init__(self, user_id, business_id, username):
            self.id = user_id
            self.business_id = business_id
            self.username = username

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
                return None
                
            c = conn.cursor()
            if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                c.execute("SELECT id, business_id, username FROM users WHERE id = %s", (user_id,))
            else:
                c.execute("SELECT id, business_id, username FROM users WHERE id = ?", (user_id,))
            user_data = c.fetchone()
            if user_data:
                return User(user_data[0], user_data[1], user_data[2])
        except Exception as e:
            logger.error(f"Error loading user: {e}")
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
                        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                            c.execute("SELECT name FROM businesses WHERE id = %s", (current_user.business_id,))
                        else:
                            c.execute("SELECT name FROM businesses WHERE id = ?", (current_user.business_id,))
                        business_data = c.fetchone()
                        if business_data:
                            session['business_name'] = business_data[0]
            except Exception as e:
                logger.error(f"Error in before_request: {e}")

    # Funciones auxiliares
    def generate_random_string(length=4):
        return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))

    def get_business_token(business_id):
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                return None
                
            c = conn.cursor()
            if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                c.execute("SELECT telegram_token FROM businesses WHERE id = %s", (business_id,))
            else:
                c.execute("SELECT telegram_token FROM businesses WHERE id = ?", (business_id,))
            token_data = c.fetchone()
            if token_data and token_data[0]:
                return token_data[0]
            return None
        except Exception as e:
            logger.error(f"Error getting business token: {e}")
            return None

    def is_bot_configured(business_id):
        """Verificar si el bot ya está configurado"""
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                return False
                
            c = conn.cursor()
            if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                c.execute("SELECT bot_configured, telegram_token FROM businesses WHERE id = %s", (business_id,))
            else:
                c.execute("SELECT bot_configured, telegram_token FROM businesses WHERE id = ?", (business_id,))
            result = c.fetchone()
            if result:
                bot_configured, telegram_token = result
                return bool(bot_configured) and telegram_token is not None and telegram_token != ''
            return False
        except Exception as e:
            logger.error(f"Error checking bot configuration: {e}")
            return False

    def get_webhook_url():
        """Obtener la URL base para webhooks"""
        if 'RENDER' in os.environ:
            return os.environ.get('RENDER_EXTERNAL_URL', '').rstrip('/')
        else:
            port = os.environ.get('PORT', 10000)
            return f"http://localhost:{port}"

    def send_telegram_message(bot_token, chat_id, text, parse_mode=None):
        try:
            if not bot_token:
                return False
                
            url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
            payload = {
                'chat_id': chat_id,
                'text': text
            }
            
            if parse_mode:
                payload['parse_mode'] = parse_mode
                
            response = requests.post(url, json=payload, timeout=10)
            return response.status_code == 200
        except Exception as e:
            logger.error(f"Error sending Telegram message: {e}")
            return False

    def get_last_insert_id(db, business_id):
        """Obtener el último ID insertado de forma compatible"""
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            result = db.execute_query("SELECT LASTVAL()")
        else:
            result = db.execute_query("SELECT last_insert_rowid()")
        
        if result and result[0]:
            return result[0][0]
        return None

    # ==================== RUTAS DE PÁGINAS ====================

    @app.route('/')
    def index():
        if current_user.is_authenticated:
            return redirect(url_for('dashboard'))
        return redirect(url_for('login'))

    @app.route('/signup', methods=['GET', 'POST'])
    def signup():
        if request.method == 'POST':
            try:
                DatabaseManager.verify_and_fix_global_tables()
                
                business_name = request.form['business_name']
                username = request.form['username']
                password = request.form['password']
                telegram_id = request.form['telegram_id']
                email = request.form['email']
            
                business_slug = slugify(business_name)
                business_id = f"{business_slug}_{generate_random_string(4)}"
            
                conn = DatabaseManager.get_global_connection()
                if conn is None:
                    return render_template('signup.html', error="Error de conexión a la base de datos")
                    
                c = conn.cursor()
                
                if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                    c.execute("SELECT id FROM users WHERE username = %s", (username,))
                else:
                    c.execute("SELECT id FROM users WHERE username = ?", (username,))
                
                if c.fetchone():
                    return render_template('signup.html', error="El usuario ya existe")
            
                try:
                    if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                        c.execute("SELECT id FROM businesses WHERE email = %s", (email,))
                    else:
                        c.execute("SELECT id FROM businesses WHERE email = ?", (email,))
                    
                    if c.fetchone():
                        return render_template('signup.html', error="El email ya está registrado")
                except Exception:
                    pass
            
                # Hash de la contraseña
                hashed_password = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt())
            
                if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                    c.execute('''
                        INSERT INTO businesses (id, name, admin_id, web_user, web_pass, email)
                        VALUES (%s, %s, %s, %s, %s, %s)
                    ''', (business_id, business_name, telegram_id, username, hashed_password.decode(), email))
                else:
                    c.execute('''
                        INSERT INTO businesses (id, name, admin_id, web_user, web_pass, email)
                        VALUES (?, ?, ?, ?, ?, ?)
                    ''', (business_id, business_name, telegram_id, username, hashed_password.decode(), email))
            
                if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                    c.execute('''
                        INSERT INTO users (business_id, username, password, role, telegram_id)
                        VALUES (%s, %s, %s, 'admin', %s)
                    ''', (business_id, username, hashed_password.decode(), telegram_id))
                else:
                    c.execute('''
                        INSERT INTO users (business_id, username, password, role, telegram_id)
                        VALUES (?, ?, ?, 'admin', ?)
                    ''', (business_id, username, hashed_password.decode(), telegram_id))
            
                conn.commit()
                
                try:
                    # Crear la base de datos del negocio
                    db = DatabaseManager(business_id)
                    db._create_tables()
                except Exception as e:
                    logger.error(f"Error creando BD del negocio: {e}")
            
                session['new_business_id'] = business_id
                session['business_id'] = business_id
                session['new_business_name'] = business_name
                session['new_username'] = username
                
                return redirect(url_for('initial_setup'))
            
            except Exception as e:
                logger.error(f"Error general en signup: {e}")
                return render_template('signup.html', error=f"Error interno del sistema: {str(e)}")
        
        return render_template('signup.html')

    @app.route('/login', methods=['GET', 'POST'])
    def login():
        message = request.args.get('message')
        
        if request.method == 'POST':
            DatabaseManager.verify_and_fix_global_tables()
            
            username = request.form['username']
            password = request.form['password']
            
            try:
                conn = DatabaseManager.get_global_connection()
                if conn is None:
                    return render_template('login.html', error="Error de conexión a la base de datos", message=message)
                    
                c = conn.cursor()
                if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                    c.execute('''SELECT u.id, b.id, b.name, u.password
                              FROM users u 
                              JOIN businesses b ON u.business_id = b.id 
                              WHERE u.username = %s''', (username,))
                else:
                    c.execute('''SELECT u.id, b.id, b.name, u.password
                              FROM users u 
                              JOIN businesses b ON u.business_id = b.id 
                              WHERE u.username = ?''', (username,))
                user_data = c.fetchone()
                
                if user_data:
                    user_id, business_id, business_name, stored_password = user_data
                    # Verificar contraseña con bcrypt
                    if bcrypt.checkpw(password.encode('utf-8'), stored_password.encode('utf-8')):
                        user_obj = User(user_id, business_id, username)
                        login_user(user_obj)
                        session['business_name'] = business_name
                        session['business_id'] = business_id
                        
                        # Verificar si el bot ya está configurado
                        if is_bot_configured(business_id):
                            return redirect(url_for('dashboard'))
                        else:
                            return redirect(url_for('initial_setup'))
                    else:
                        return render_template('login.html', error="Credenciales inválidas", message=message)
                else:
                    return render_template('login.html', error="Credenciales inválidas", message=message)
                        
            except Exception as e:
                logger.error(f"Error en login: {e}")
                return render_template('login.html', error="Error interno del sistema", message=message)
        
        return render_template('login.html', message=message)

    @app.route('/logout')
    @login_required
    def logout():
        logout_user()
        session.clear()
        return redirect(url_for('login'))

    @app.route('/dashboard')
    @login_required
    def dashboard():
        if not is_bot_configured(current_user.business_id):
            return redirect(url_for('initial_setup'))
        
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

    @app.route('/bot-diagnostic')
    @login_required
    def bot_diagnostic():
        """Página de diagnóstico del bot"""
        business_id = current_user.business_id
        business_name = session.get('business_name', 'Negocio')
        return render_template('bot_diagnostic.html', 
                             business_id=business_id, 
                             business_name=business_name)

    @app.route('/initial_setup')
    def initial_setup():
        DatabaseManager.verify_and_fix_global_tables()
        
        if current_user.is_authenticated:
            if is_bot_configured(current_user.business_id):
                return redirect(url_for('dashboard'))
            
            business_name = session.get('business_name', 'Negocio')
            business_id = current_user.business_id
            username = current_user.username
            
            session['business_id'] = business_id
            
            return render_template('initial_setup.html', 
                                 business_name=business_name,
                                 business_id=business_id,
                                 username=username)
        
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
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            data = request.json
            business_id = data.get('business_id')
            
            conn = DatabaseManager.get_global_connection()
            if conn is None:
                return jsonify({'success': False, 'message': 'Error de conexión a la base de datos'})
                
            c = conn.cursor()
            if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
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
            
            return jsonify({
                'success': True, 
                'message': 'Configuración completada exitosamente',
                'redirect': url_for('login', message='✅ Configuración completada. Ahora puedes iniciar sesión.')
            })
            
        except Exception as e:
            logger.error(f"Error finalizando configuración: {e}")
            return jsonify({'success': False, 'message': str(e)})

    @app.route('/api/test-bot', methods=['POST'])
    def test_bot():
        try:
            data = request.json
            token = data.get('token', '').strip()
            
            if not token:
                return jsonify({'success': False, 'message': 'Token requerido'})
            
            # Validar token con Telegram
            response = requests.get(f'https://api.telegram.org/bot{token}/getMe', timeout=10)
            
            if response.status_code == 200:
                bot_data = response.json()
                if bot_data.get('ok'):
                    return jsonify({
                        'success': True,
                        'username': bot_data['result'].get('username'),
                        'bot_data': bot_data['result']
                    })
                else:
                    return jsonify({
                        'success': False,
                        'message': bot_data.get('description', 'Error desconocido')
                    })
            else:
                return jsonify({
                    'success': False,
                    'message': f'Error de Telegram: {response.status_code}'
                })
            
        except Exception as e:
            logger.error(f"Error validando token: {e}")
            return jsonify({'success': False, 'message': f'Error: {str(e)}'})

    @app.route('/api/save-products', methods=['POST'])
    def save_products():
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            data = request.json
            products = data.get('products', [])
            business_id = data.get('business_id') or session.get('new_business_id') or (current_user.business_id if current_user.is_authenticated else None)
            
            if not business_id:
                return jsonify({'success': False, 'message': 'Business ID requerido'})
            
            db = get_business_db_connection(business_id)
            
            # Detectar si estamos en PostgreSQL
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            for product in products:
                seccion_nombre = product['category']
                
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
            
            return jsonify({
                'success': True, 
                'message': 'Productos guardados correctamente',
                'business_id': business_id
            })
            
        except Exception as e:
            logger.error(f"Error saving products: {e}")
            return jsonify({'success': False, 'message': str(e)})

    @app.route('/api/dashboard')
    @login_required
    def dashboard_data():
        try:
            hoy = datetime.now().strftime("%Y-%m-%d")
            mes_actual = datetime.now().strftime("%Y-%m")
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            # Ventas del mes actual
            if is_postgres:
                ventas_mes_query = """
                SELECT SUM(v.cantidad * p.precio_venta) 
                FROM ventas v 
                JOIN productos p ON v.producto_id = p.id 
                WHERE to_char(v.fecha, 'YYYY-MM') = %s
                """
            else:
                ventas_mes_query = """
                SELECT SUM(v.cantidad * p.precio_venta) 
                FROM ventas v 
                JOIN productos p ON v.producto_id = p.id 
                WHERE strftime('%%Y-%%m', v.fecha) = ?
                """
            
            ventas_mes = g.db.execute_query(ventas_mes_query, (mes_actual,))
            ventas_mes = float(ventas_mes[0][0]) if ventas_mes and ventas_mes[0][0] else 0.0
            
            # Ventas de hoy
            if is_postgres:
                ventas_hoy_query = """
                SELECT p.nombre, SUM(v.cantidad), SUM(v.cantidad * p.precio_venta) 
                FROM ventas v 
                JOIN productos p ON v.producto_id = p.id 
                WHERE DATE(v.fecha) = %s 
                GROUP BY p.nombre
                """
            else:
                ventas_hoy_query = """
                SELECT p.nombre, SUM(v.cantidad), SUM(v.cantidad * p.precio_venta) 
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
                        'producto': row[0],
                        'cantidad': row[1],
                        'total': float(row[2])
                    })
            
            # Inventario
            inventario = g.db.execute_query(
                "SELECT p.nombre, s.nombre, p.stock, p.precio_venta, p.precio_compra, "
                "ROUND((p.precio_venta - p.precio_compra) / p.precio_compra * 100, 2) as margen "
                "FROM productos p "
                "JOIN secciones s ON p.seccion_id = s.id "
                "ORDER BY p.stock ASC"
            )
            inventario_list = []
            if inventario:
                for row in inventario:
                    inventario_list.append({
                        'nombre': row[0],
                        'seccion': row[1],
                        'stock': row[2],
                        'precio_venta': float(row[3]),
                        'precio_compra': float(row[4]),
                        'margen': float(row[5]) if row[5] else 0.0
                    })
            
            # Calcular estadísticas adicionales
            total_ingresos = ventas_mes
            
            # Ganancia (estimada)
            if is_postgres:
                ganancia_query = """
                SELECT SUM(v.cantidad * (p.precio_venta - p.precio_compra - p.costo_transporte))
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE to_char(v.fecha, 'YYYY-MM') = %s
                """
            else:
                ganancia_query = """
                SELECT SUM(v.cantidad * (p.precio_venta - p.precio_compra - p.costo_transporte))
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE strftime('%%Y-%%m', v.fecha) = ?
                """
            
            ganancia = g.db.execute_query(ganancia_query, (mes_actual,))
            ganancia = float(ganancia[0][0]) if ganancia and ganancia[0][0] else 0.0
            
            # Margen promedio
            margen = (ganancia / ventas_mes * 100) if ventas_mes > 0 else 0
            
            # Total ventas (cantidad)
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
            
            # Datos para gráfico mensual
            if is_postgres:
                ventas_mensuales_query = """
                SELECT to_char(fecha, 'YYYY-MM') as mes, SUM(cantidad * precio_venta) as total
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                GROUP BY mes
                ORDER BY mes DESC
                LIMIT 6
                """
            else:
                ventas_mensuales_query = """
                SELECT strftime('%%Y-%%m', fecha) as mes, SUM(cantidad * precio_venta) as total
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                GROUP BY mes
                ORDER BY mes DESC
                LIMIT 6
                """
            
            ventas_mensuales = g.db.execute_query(ventas_mensuales_query)
            meses = []
            ventas = []
            if ventas_mensuales:
                for row in reversed(ventas_mensuales):
                    meses.append(row[0])
                    ventas.append(float(row[1]) if row[1] else 0.0)
            
            # Tendencias (simuladas)
            tendencias = {
                'ingresos': 12.5,
                'ganancia': 8.3,
                'margen': -2.1,
                'ventas': 15.7
            }
            
            return jsonify({
                'ingresos': total_ingresos,
                'ganancia': ganancia,
                'margen': round(margen, 2),
                'ventas': total_ventas,
                'tendencias': tendencias,
                'ventas_hoy': ventas_hoy_list,
                'inventario': inventario_list,
                'ventas_mensuales': {
                    'meses': meses,
                    'ventas': ventas
                }
            })
        
        except Exception as e:
            logger.error(f"Error en dashboard_data: {str(e)}")
            return jsonify({
                'error': 'Ocurrió un error al obtener los datos del dashboard',
                'details': str(e)
            }), 500

    @app.route('/api/sales')
    @login_required
    def sales_data():
        try:
            period = request.args.get('period', 'monthly')
            is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
            
            if period == 'monthly':
                if is_postgres:
                    query = """
                    SELECT to_char(fecha, 'YYYY-MM') as mes, SUM(cantidad * precio_venta) as total 
                    FROM ventas v 
                    JOIN productos p ON v.producto_id = p.id 
                    GROUP BY mes 
                    ORDER BY mes DESC 
                    LIMIT 6
                    """
                else:
                    query = """
                    SELECT strftime('%%Y-%%m', fecha) as mes, SUM(cantidad * precio_venta) as total 
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
                            "SELECT SUM(cantidad * precio_venta) "
                            "FROM ventas v "
                            "JOIN productos p ON v.producto_id = p.id "
                            "WHERE fecha BETWEEN %s AND %s", 
                            (inicio_semana, fin_semana)
                        )
                    else:
                        total = g.db.execute_query(
                            "SELECT SUM(cantidad * precio_venta) "
                            "FROM ventas v "
                            "JOIN productos p ON v.producto_id = p.id "
                            "WHERE fecha BETWEEN ? AND ?", 
                            (inicio_semana, fin_semana)
                        )
                    total = float(total[0][0]) if total and total[0][0] is not None else 0.0
                    
                    semanas.append(f"Sem {4-i}")
                    ventas.append(total)
                    
                return jsonify({'meses': semanas, 'ventas': ventas})
                
            else:
                dias = []
                ventas = []
                hoy = datetime.now()
                for i in range(7):
                    fecha = (hoy - timedelta(days=6-i)).strftime("%Y-%m-%d")
                    
                    if is_postgres:
                        total = g.db.execute_query(
                            "SELECT SUM(cantidad * precio_venta) "
                            "FROM ventas v "
                            "JOIN productos p ON v.producto_id = p.id "
                            "WHERE DATE(fecha) = %s", 
                            (fecha,)
                        )
                    else:
                        total = g.db.execute_query(
                            "SELECT SUM(cantidad * precio_venta) "
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
            return jsonify({
                'error': 'Ocurrió un error al obtener datos de ventas',
                'details': str(e)
            }), 500

    # ==================== API PARA VENTAS ====================

    @app.route('/api/ventas')
    @login_required
    def api_ventas():
        """Obtener datos de ventas para el panel"""
        try:
            db = get_business_db_connection(current_user.business_id)
            
            # Obtener filtros
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
                        v.cantidad * (p.precio_venta - p.precio_compra - p.costo_transporte) as ganancia
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
                        v.cantidad * (p.precio_venta - p.precio_compra - p.costo_transporte) as ganancia
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
                        'cantidad': row[2],
                        'precio_unitario': float(row[3]),
                        'total': float(row[4]),
                        'ganancia': float(row[5]) if row[5] else 0
                    })
                    total_ventas += 1
                    ingresos += float(row[4])
                    ganancia += float(row[5]) if row[5] else 0
            
            return jsonify({
                'ventas': ventas,
                'total_ventas': total_ventas,
                'ingresos': ingresos,
                'ganancia': ganancia,
                'ticket_promedio': ingresos / total_ventas if total_ventas > 0 else 0
            })
        except Exception as e:
            logger.error(f"Error en api_ventas: {str(e)}")
            return jsonify({'error': str(e)}), 500

    # ==================== API PARA INVENTARIO ====================

    @app.route('/api/inventario')
    @login_required
    def api_inventario():
        """Obtener datos del inventario completo"""
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
                        ROUND((p.precio_venta - p.precio_compra) / p.precio_compra * 100, 2) as margen
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
                        ROUND((p.precio_venta - p.precio_compra) / p.precio_compra * 100, 2) as margen
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
                    producto = {
                        'id': row[0],
                        'nombre': row[1],
                        'seccion': row[2],
                        'stock': row[3],
                        'precio_venta': float(row[4]),
                        'precio_compra': float(row[5]),
                        'margen': float(row[6]) if row[6] else 0
                    }
                    productos.append(producto)
                    total_valor += row[3] * float(row[5])
                    if row[3] <= 3:
                        stock_bajo += 1
                    if row[3] == 0:
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
            return jsonify({'error': str(e)}), 500

    # ==================== API PARA FINANZAS ====================

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
                    SELECT to_char(fecha, 'YYYY-MM') as mes, SUM(cantidad * precio_venta) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            else:
                query_ingresos = """
                    SELECT strftime('%Y-%m', fecha) as mes, SUM(cantidad * precio_venta) as total
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
                    SELECT to_char(fecha, 'YYYY-MM') as mes, SUM(cantidad * (precio_compra + costo_transporte)) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            else:
                query_gastos = """
                    SELECT strftime('%Y-%m', fecha) as mes, SUM(cantidad * (precio_compra + costo_transporte)) as total
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            
            gastos_mensuales = db.execute_query(query_gastos)
            
            if gastos_mensuales:
                gastos = [float(row[1]) if row[1] else 0 for row in reversed(gastos_mensuales)]
            
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
            return jsonify({'error': str(e)}), 500

    # ==================== API PARA ANÁLISIS ====================

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
                    SELECT p.nombre, SUM(v.cantidad) as ventas, SUM(v.cantidad * (p.precio_venta - p.precio_compra)) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY p.nombre
                    ORDER BY ventas DESC
                    LIMIT 10
                """
            else:
                top_query = """
                    SELECT p.nombre, SUM(v.cantidad) as ventas, SUM(v.cantidad * (p.precio_venta - p.precio_compra)) as ganancia
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
                        'ventas': row[1],
                        'ganancia': float(row[2]) if row[2] else 0
                    })
            
            # Tendencia
            if is_postgres:
                tendencia_query = """
                    SELECT to_char(fecha, 'YYYY-MM') as mes, SUM(cantidad) as total
                    FROM ventas
                    GROUP BY mes
                    ORDER BY mes DESC
                    LIMIT 6
                """
            else:
                tendencia_query = """
                    SELECT strftime('%Y-%m', fecha) as mes, SUM(cantidad) as total
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
                        SUM(v.cantidad) as ventas,
                        SUM(v.cantidad * (p.precio_venta - p.precio_compra)) as ganancia
                    FROM ventas v
                    JOIN productos p ON v.producto_id = p.id
                    GROUP BY p.nombre
                    ORDER BY ganancia DESC
                """
            else:
                abc_query = """
                    SELECT 
                        p.nombre,
                        SUM(v.cantidad) as ventas,
                        SUM(v.cantidad * (p.precio_venta - p.precio_compra)) as ganancia
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
                        'ventas': row[1],
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
            return jsonify({'error': str(e)}), 500

    # ==================== API PARA CLIENTES ====================

    @app.route('/api/clientes')
    @login_required
    def api_clientes():
        """Obtener datos de clientes"""
        try:
            # Por ahora, devolvemos datos de ejemplo
            # Puedes expandir esto con una tabla de clientes real
            return jsonify({
                'clientes': [],
                'top_clientes': [],
                'clientes_meses': ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun'],
                'clientes_por_mes': [0, 0, 0, 0, 0, 0]
            })
        except Exception as e:
            logger.error(f"Error en api_clientes: {str(e)}")
            return jsonify({'error': str(e)}), 500

    # Handlers de WebSocket
    @socketio.on('connect')
    def handle_connect():
        try:
            if 'business_id' in session:
                business_id = session['business_id']
                socketio.server.enter_room(request.sid, business_id)
                logger.info(f"Cliente conectado a sala de negocio: {business_id}")
        except Exception as e:
            logger.error(f"Error en handle_connect: {e}")

    @socketio.on('disconnect')
    def handle_disconnect():
        logger.info(f"Cliente desconectado: {request.sid}")

    # Rutas adicionales
    @app.route('/terms')
    def terms():
        return render_template('terms.html')

    @app.route('/privacy')
    def privacy():
        return render_template('privacy.html')

    return app
