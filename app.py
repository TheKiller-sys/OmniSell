# app.py - Aplicación principal SIN bot de Telegram por negocio
import os
from flask import Flask, g, jsonify, request, session, send_file
import logging
from flask_socketio import SocketIO
import time
import json
import jwt
import datetime
import bcrypt
import requests
from functools import wraps
from flask_cors import CORS
import traceback
import sys
import random
import string

from flask_login import login_required, current_user

# Configuración básica de logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ==================== IMPORTAR DASHBOARD ====================
from web.dashboard import create_app, set_telegram_log_function

# ==================== IMPORTAR DatabaseManager ====================
from database.db_manager import DatabaseManager

# ==================== CREAR APP ====================
app = create_app()
socketio = SocketIO(app, async_mode='threading', cors_allowed_origins="*")

# ==================== CONFIGURACIÓN CORS ====================
CORS(app, resources={
    r"/api/*": {
        "origins": "*",
        "methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        "allow_headers": ["Content-Type", "Authorization", "Accept"]
    }
})

# ==================== CONFIGURACIÓN DE TELEGRAM SOLO PARA LOGS ====================
TELEGRAM_BOT_TOKEN = os.environ.get('TELEGRAM_BOT_TOKEN', '')
TELEGRAM_ADMIN_CHAT_ID = os.environ.get('TELEGRAM_ADMIN_CHAT_ID', '')

if TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID:
    logger.info("✅ Bot de Telegram configurado para LOGS del programador")
else:
    logger.warning("⚠️ Bot de Telegram NO configurado. Los logs no se enviarán.")

# ==================== FUNCIÓN DE LOG PARA TELEGRAM (SOLO PROGRAMADOR) ====================

def send_telegram_message(message, parse_mode=None):
    """Función interna para enviar mensajes a Telegram (SOLO PROGRAMADOR)"""
    try:
        if not TELEGRAM_BOT_TOKEN or not TELEGRAM_ADMIN_CHAT_ID:
            logger.warning("Telegram no configurado, mensaje no enviado")
            return False
        
        url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
        payload = {
            'chat_id': TELEGRAM_ADMIN_CHAT_ID,
            'text': message
        }
        
        if parse_mode and parse_mode in ['Markdown', 'HTML']:
            payload['parse_mode'] = parse_mode
        
        response = requests.post(url, json=payload, timeout=10)
        
        if response.status_code == 200:
            return True
        else:
            logger.error(f"Error enviando mensaje a Telegram: {response.status_code} - {response.text}")
            return False
            
    except Exception as e:
        logger.error(f"Error en send_telegram_message: {e}")
        return False


def log_to_telegram_web(level, message, data=None, user=None, business_id=None, request_info=None):
    """
    Función de log para el programador (SOLO LOGS)
    """
    try:
        if not TELEGRAM_BOT_TOKEN or not TELEGRAM_ADMIN_CHAT_ID:
            return False
        
        emoji = {
            'DEBUG': '🔍',
            'INFO': 'ℹ️',
            'WARNING': '⚠️',
            'ERROR': '❌',
            'SUCCESS': '✅',
            'CRITICAL': '🔥'
        }.get(level, '📱')
        
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        
        lines = [
            f"{emoji} [{level}] OMNIVENTAS LOG",
            "",
            f"⏰ Timestamp: {timestamp}",
        ]
        
        if user:
            user_info = f"Usuario: {user.username} (ID: {user.id}) - Rol: {user.role}"
            lines.append(f"👤 {user_info}")
        elif current_user and current_user.is_authenticated:
            user_info = f"Usuario: {current_user.username} (ID: {current_user.id}) - Rol: {current_user.role}"
            lines.append(f"👤 {user_info}")
        
        if business_id:
            lines.append(f"🏪 Business ID: {business_id}")
        elif session and session.get('business_id'):
            lines.append(f"🏪 Business ID: {session.get('business_id')}")
        
        if request_info:
            lines.append(f"📡 Método: {request_info.get('method', 'N/A')}")
            lines.append(f"🔗 Ruta: {request_info.get('path', 'N/A')}")
            lines.append(f"🌐 IP: {request_info.get('ip', 'N/A')}")
            if request_info.get('user_agent'):
                lines.append(f"📱 User-Agent: {request_info.get('user_agent', 'N/A')[:100]}")
        
        lines.append("")
        lines.append(f"📝 Mensaje: {message}")
        
        if data:
            try:
                if isinstance(data, dict):
                    data_str = json.dumps(data, indent=2, default=str, ensure_ascii=False)
                else:
                    data_str = str(data)
                
                if len(data_str) > 2000:
                    data_str = data_str[:2000] + "... (truncado)"
                
                lines.append("")
                lines.append("📊 Datos adicionales:")
                lines.append(data_str)
            except Exception as e:
                lines.append(f"📊 Datos: {str(data)}")
        
        full_message = "\n".join(lines)
        return send_telegram_message(full_message)
        
    except Exception as e:
        logger.error(f"Error en log_to_telegram_web: {e}")
        return False


# ==================== ¡CONECTAR LOGS CON DASHBOARD! ====================
set_telegram_log_function(log_to_telegram_web)


# ==================== FUNCIÓN DE LOG UNIFICADA ====================

def log_to_telegram(level, message, data=None, user=None, business_id=None, request_info=None):
    """Función unificada para enviar logs a Telegram (SOLO PROGRAMADOR)"""
    return log_to_telegram_web(level, message, data, user, business_id, request_info)


# ==================== DECORADOR PARA LOGS AUTOMÁTICOS ====================

def log_web_request(level='INFO'):
    """Decorador para loguear automáticamente peticiones web a Telegram"""
    def decorator(f):
        @wraps(f)
        def decorated(*args, **kwargs):
            request_info = {
                'method': request.method,
                'path': request.path,
                'ip': request.remote_addr,
                'user_agent': request.headers.get('User-Agent', 'N/A')
            }
            
            user = None
            try:
                if current_user and current_user.is_authenticated:
                    user = current_user
            except:
                pass
            
            business_id = session.get('business_id') if session else None
            
            try:
                response = f(*args, **kwargs)
                
                if '/health' not in request.path and '/api/test-log' not in request.path:
                    log_to_telegram(
                        level='SUCCESS' if level == 'INFO' else level,
                        message=f"Request exitosa: {request.method} {request.path}",
                        data={'status_code': getattr(response, 'status_code', 200) if response else 200},
                        user=user,
                        business_id=business_id,
                        request_info=request_info
                    )
                
                return response
                
            except Exception as e:
                error_data = {
                    'error': str(e),
                    'traceback': traceback.format_exc()
                }
                log_to_telegram(
                    level='ERROR',
                    message=f"❌ Error en {request.method} {request.path}: {str(e)}",
                    data=error_data,
                    user=user,
                    business_id=business_id,
                    request_info=request_info
                )
                raise
                
        return decorated
    return decorator


# ==================== MANEJADOR DE ERRORES GLOBAL CON LOGS ====================

@app.errorhandler(404)
def not_found(error):
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr,
        'user_agent': request.headers.get('User-Agent', 'N/A')
    }
    
    log_to_telegram(
        level='WARNING',
        message=f"404 - Endpoint no encontrado: {request.path}",
        data={'method': request.method},
        business_id=session.get('business_id') if session else None,
        request_info=request_info
    )
    
    response = jsonify({
        'success': False,
        'message': 'Endpoint no encontrado',
        'error': str(error)
    })
    response.status_code = 404
    response.headers.add('Access-Control-Allow-Origin', '*')
    return response


@app.errorhandler(500)
def internal_error(error):
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr,
        'user_agent': request.headers.get('User-Agent', 'N/A')
    }
    
    log_to_telegram(
        level='CRITICAL',
        message=f"🔥 500 - Error interno del servidor en {request.path}",
        data={
            'error': str(error),
            'traceback': traceback.format_exc()
        },
        business_id=session.get('business_id') if session else None,
        request_info=request_info
    )
    
    response = jsonify({
        'success': False,
        'message': 'Error interno del servidor',
        'error': str(error)
    })
    response.status_code = 500
    response.headers.add('Access-Control-Allow-Origin', '*')
    return response


@app.errorhandler(Exception)
def handle_exception(error):
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr,
        'user_agent': request.headers.get('User-Agent', 'N/A')
    }
    
    logger.error(f"Error no manejado: {error}")
    
    log_to_telegram(
        level='CRITICAL',
        message=f"🔥 Excepción no manejada en {request.path}: {str(error)}",
        data={
            'error': str(error),
            'traceback': traceback.format_exc()
        },
        business_id=session.get('business_id') if session else None,
        request_info=request_info
    )
    
    response = jsonify({
        'success': False,
        'message': 'Error interno del servidor',
        'error': str(error)
    })
    response.status_code = 500
    response.headers.add('Access-Control-Allow-Origin', '*')
    return response


# ==================== DECORADOR DE AUTENTICACIÓN ====================

def token_required(f):
    """Decorador para verificar token JWT en peticiones de la app Android"""
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        if not auth_header:
            return jsonify({'success': False, 'message': 'Token requerido'}), 401
        
        try:
            token = auth_header.split(' ')[1]
            payload = jwt.decode(token, os.environ.get('JWT_SECRET', 'secret-key'), algorithms=['HS256'])
            
            g.vendor_id = payload.get('vendor_id')
            g.user_id = payload.get('user_id')
            g.business_id = payload.get('business_id')
            g.vendor_name = payload.get('name')
            g.role = payload.get('role', 'vendedor')
            
            if not g.vendor_id:
                return jsonify({'success': False, 'message': 'Token inválido: falta vendor_id'}), 401
            
            if not g.user_id:
                return jsonify({'success': False, 'message': 'Token inválido: falta user_id'}), 401
            
            logger.debug(f"🔐 Token válido: vendor={g.vendor_id}, user_id={g.user_id}, business={g.business_id}")
            
            return f(*args, **kwargs)
            
        except jwt.ExpiredSignatureError:
            return jsonify({'success': False, 'message': 'Token expirado'}), 401
        except jwt.InvalidTokenError as e:
            return jsonify({'success': False, 'message': f'Token inválido: {str(e)}'}), 401
        except Exception as e:
            logger.error(f"Error en token_required: {e}")
            return jsonify({'success': False, 'message': f'Error de autenticación: {str(e)}'}), 401
    return decorated


# ==================== HEALTH CHECK ====================

@app.route('/health')
def health_check():
    status_data = {
        'status': 'ok',
        'timestamp': time.time(),
        'service': 'OmniVentas API',
        'version': '2.0',
        'environment': 'production' if 'RENDER' in os.environ else 'development',
        'telegram_logs': bool(TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID)
    }
    
    return jsonify(status_data), 200


# ==================== ENDPOINT: LOGS POR TELEGRAM (SOLO PROGRAMADOR) ====================

@app.route('/api/send-log', methods=['POST', 'OPTIONS'])
def send_log_to_telegram():
    """Endpoint para enviar logs desde la app Android (SOLO PROGRAMADOR)"""
    if request.method == 'OPTIONS':
        response = jsonify({'success': True})
        response.headers.add('Access-Control-Allow-Origin', '*')
        response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization,Accept')
        response.headers.add('Access-Control-Allow-Methods', 'POST,OPTIONS')
        return response
    
    try:
        if not TELEGRAM_BOT_TOKEN or not TELEGRAM_ADMIN_CHAT_ID:
            logger.warning("Telegram bot no configurado para enviar log")
            response = jsonify({'success': False, 'message': 'Bot not configured'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 500
        
        data = request.json
        
        if not data:
            response = jsonify({'success': False, 'message': 'No data received'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        log_level = data.get('level', 'INFO')
        log_message = data.get('message', '')
        log_data = data.get('data', {})
        timestamp = data.get('timestamp', datetime.datetime.now().isoformat())
        vendor_id = data.get('vendor_id', 'DESCONOCIDO')
        vendor_name = data.get('vendor_name', 'DESCONOCIDO')
        business_name = data.get('business_name', 'DESCONOCIDO')
        app_version = data.get('app_version', '1.0')
        device_model = data.get('device_model', 'DESCONOCIDO')
        android_version = data.get('android_version', 'DESCONOCIDO')
        
        if not log_message:
            response = jsonify({'success': False, 'message': 'message required'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        emoji = {
            'DEBUG': '🔍',
            'INFO': 'ℹ️',
            'WARNING': '⚠️',
            'ERROR': '❌',
            'SUCCESS': '✅',
            'CRITICAL': '🔥'
        }.get(log_level, '📱')
        
        message_lines = [
            f"{emoji} [{log_level}] LOG desde App Android",
            "",
            f"App: OmniVentas v{app_version}",
            f"Vendedor: {vendor_id} ({vendor_name})",
            f"Negocio: {business_name}",
            f"Dispositivo: {device_model} (Android {android_version})",
            f"Timestamp: {timestamp}",
            "",
            f"Mensaje: {log_message}"
        ]
        
        if log_data:
            try:
                if isinstance(log_data, dict):
                    message_lines.append(f"Data: {json.dumps(log_data, indent=2, default=str)}")
                else:
                    message_lines.append(f"Data: {str(log_data)}")
            except Exception as e:
                message_lines.append(f"Data: {str(log_data)}")
        
        message = "\n".join(message_lines)
        success = send_telegram_message(message)
        
        response = jsonify({
            'success': success,
            'message': 'Log sent to Telegram' if success else 'Failed to send Telegram message'
        })
        response.headers.add('Access-Control-Allow-Origin', '*')
        
        if success:
            logger.info(f"Log enviado a Telegram: {log_level} - {log_message[:50]}")
            return response
        else:
            return response, 500
            
    except Exception as e:
        logger.error(f"Error en send_log_to_telegram: {e}")
        response = jsonify({'success': False, 'message': str(e)})
        response.headers.add('Access-Control-Allow-Origin', '*')
        return response, 500


@app.route('/api/telegram-status', methods=['GET'])
def telegram_status():
    """Verificar estado del bot de Telegram (SOLO PROGRAMADOR)"""
    response = jsonify({
        'bot_configured': bool(TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID),
        'token_present': bool(TELEGRAM_BOT_TOKEN),
        'chat_id_present': bool(TELEGRAM_ADMIN_CHAT_ID),
        'token_preview': TELEGRAM_BOT_TOKEN[:10] + '...' if TELEGRAM_BOT_TOKEN else None,
        'chat_id_preview': TELEGRAM_ADMIN_CHAT_ID[:10] + '...' if TELEGRAM_ADMIN_CHAT_ID else None
    })
    response.headers.add('Access-Control-Allow-Origin', '*')
    return response


@app.route('/api/test-log', methods=['GET'])
def test_log_endpoint():
    """Endpoint para probar el envío de logs"""
    try:
        test_data = {
            'level': 'SUCCESS',
            'message': '🧪 Test de conexión desde el servidor',
            'vendor_id': 'TEST_SERVER',
            'vendor_name': 'Servidor',
            'business_name': 'OmniVentas Test',
            'app_version': '1.0',
            'device_model': 'Server',
            'android_version': 'N/A',
            'timestamp': datetime.datetime.now().isoformat(),
            'data': {'test': True, 'endpoint': '/api/test-log'}
        }
        
        with app.test_request_context(json=test_data):
            return send_log_to_telegram()
            
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500


# ==================== ENDPOINT: LOGIN DE VENDEDOR ====================

@app.route('/api/login-vendedor', methods=['POST'])
def login_vendedor():
    """Login para vendedores - FUNCIONA CON SQLite Y PostgreSQL"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        DatabaseManager.verify_and_fix_global_tables()
        
        data = request.json
        if not data:
            return jsonify({'success': False, 'message': 'No se recibieron datos'}), 400
        
        vendor_id = data.get('vendor_id', '').strip().upper()
        
        if not vendor_id:
            return jsonify({'success': False, 'message': 'ID de vendedor requerido'}), 400
        
        if len(vendor_id) != 8:
            return jsonify({'success': False, 'message': 'El ID debe tener exactamente 8 caracteres'}), 400
        
        if not vendor_id.isalnum():
            return jsonify({'success': False, 'message': 'El ID solo debe contener letras y números'}), 400
        
        conn = DatabaseManager.get_global_connection()
        if conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión al servidor'}), 500
        
        c = conn.cursor()
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        vendor_data = None
        business_id = None
        
        # Buscar en VENDORS
        try:
            if is_postgres:
                c.execute("SET search_path TO public")
                c.execute("""
                    SELECT id, name, business_id, role, active
                    FROM vendors
                    WHERE id = %s
                """, (vendor_id,))
            else:
                c.execute("""
                    SELECT id, name, business_id, role, active
                    FROM vendors
                    WHERE id = ?
                """, (vendor_id,))
            vendor_data = c.fetchone()
            
            if vendor_data:
                logger.info(f"✅ Vendor {vendor_id} encontrado en vendors")
                business_id = vendor_data[2]
        except Exception as e:
            logger.error(f"Error buscando en vendors: {e}")
        
        # Si no está en VENDORS, buscar en USERS
        if not vendor_data:
            logger.info(f"⚠️ Vendor {vendor_id} no encontrado en vendors, buscando en users...")
            try:
                if is_postgres:
                    c.execute("""
                        SELECT id, username as name, business_id, role, TRUE as active
                        FROM users
                        WHERE username = %s OR id::text = %s
                    """, (vendor_id, vendor_id))
                else:
                    c.execute("""
                        SELECT id, username as name, business_id, role, 1 as active
                        FROM users
                        WHERE username = ? OR id = ?
                    """, (vendor_id, vendor_id))
                user_data = c.fetchone()
                
                if user_data:
                    logger.info(f"✅ Vendor {vendor_id} encontrado en users, creando en vendors...")
                    if is_postgres:
                        c.execute("""
                            INSERT INTO vendors (id, name, business_id, role, active)
                            VALUES (%s, %s, %s, %s, %s)
                            ON CONFLICT (id) DO NOTHING
                        """, (vendor_id, user_data[1], user_data[2], user_data[3], True))
                    else:
                        c.execute("""
                            INSERT OR IGNORE INTO vendors (id, name, business_id, role, active)
                            VALUES (?, ?, ?, ?, ?)
                        """, (vendor_id, user_data[1], user_data[2], user_data[3], 1))
                    conn.commit()
                    vendor_data = user_data
                    business_id = user_data[2]
                    logger.info(f"✅ Vendor {vendor_id} creado en vendors automáticamente")
            except Exception as e:
                logger.error(f"Error buscando en users: {e}")
        
        if not vendor_data:
            return jsonify({'success': False, 'message': 'ID de vendedor no encontrado'}), 401
        
        # Verificar que esté activo
        active_value = vendor_data[4]
        is_active = False
        
        if is_postgres:
            is_active = active_value == True or active_value == 't' or active_value == 'true'
        else:
            is_active = active_value == 1 or active_value == 'True' or active_value == 't' or active_value == 'true'
        
        if not is_active:
            return jsonify({'success': False, 'message': 'El vendedor está inactivo'}), 401
        
        vendor_id_db = vendor_data[0]
        vendor_name = vendor_data[1]
        
        if not business_id:
            business_id = vendor_data[2]
        
        vendor_role = vendor_data[3] if len(vendor_data) > 3 else 'vendedor'
        
        # Obtener nombre del negocio
        try:
            if is_postgres:
                c.execute("SELECT name FROM businesses WHERE id = %s", (business_id,))
            else:
                c.execute("SELECT name FROM businesses WHERE id = ?", (business_id,))
            business_result = c.fetchone()
            business_name = business_result[0] if business_result else business_id
        except Exception as e:
            logger.error(f"Error obteniendo nombre del negocio: {e}")
            business_name = business_id
        
        # Obtener USER_ID
        user_id = None
        try:
            if is_postgres:
                c.execute("SELECT id FROM users WHERE business_id = %s LIMIT 1", (business_id,))
            else:
                c.execute("SELECT id FROM users WHERE business_id = ? LIMIT 1", (business_id,))
            user_result = c.fetchone()
            user_id = user_result[0] if user_result else None
        except Exception as e:
            logger.error(f"Error obteniendo user_id: {e}")
        
        # Si no hay usuario, crear uno automáticamente
        if not user_id:
            logger.warning(f"⚠️ Negocio {business_id} no tiene usuario. Creando usuario automático...")
            default_username = f"admin_{business_id[:6]}"
            default_password = bcrypt.hashpw('admin123'.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
            
            try:
                if is_postgres:
                    c.execute("""
                        INSERT INTO users (business_id, username, password, role)
                        VALUES (%s, %s, %s, 'admin')
                    """, (business_id, default_username, default_password))
                else:
                    c.execute("""
                        INSERT INTO users (business_id, username, password, role)
                        VALUES (?, ?, ?, 'admin')
                    """, (business_id, default_username, default_password))
                conn.commit()
                logger.info(f"✅ Usuario creado: {default_username} para negocio {business_id}")
                
                if is_postgres:
                    c.execute("SELECT id FROM users WHERE business_id = %s LIMIT 1", (business_id,))
                else:
                    c.execute("SELECT id FROM users WHERE business_id = ? LIMIT 1", (business_id,))
                user_result = c.fetchone()
                user_id = user_result[0] if user_result else None
            except Exception as e:
                logger.error(f"Error creando usuario automático: {e}")
        
        if not user_id:
            return jsonify({
                'success': False, 
                'message': 'No se pudo obtener o crear un usuario para este negocio'
            }), 401
        
        # Verificar que user_id sea numérico
        if not str(user_id).isdigit():
            logger.error(f"❌ user_id no es numérico: {user_id}")
            return jsonify({
                'success': False,
                'message': 'Error de configuración: user_id inválido. Contacta al administrador.'
            }), 500
        
        # Generar token
        token = jwt.encode({
            'vendor_id': vendor_id_db,
            'user_id': user_id,
            'business_id': business_id,
            'name': vendor_name,
            'role': vendor_role,
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=7)
        }, os.environ.get('JWT_SECRET', 'secret-key'), algorithm='HS256')
        
        log_to_telegram(
            level='SUCCESS',
            message=f"✅ Login exitoso desde app: {vendor_name}",
            data={
                'vendor_id': vendor_id_db,
                'business_id': business_id,
                'business_name': business_name,
                'role': vendor_role
            },
            business_id=business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'token': token,
            'vendor': {
                'id': vendor_id_db,
                'name': vendor_name,
                'business_id': business_id,
                'business_name': business_name,
                'role': vendor_role,
                'user_id': user_id
            }
        })
        
    except Exception as e:
        logger.error(f"❌ Error en login_vendedor: {e}")
        logger.error(traceback.format_exc())
        
        log_to_telegram(
            level='ERROR',
            message=f"Error en login_vendedor: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            request_info=request_info
        )
        
        return jsonify({'success': False, 'message': f'Error del servidor: {str(e)}'}), 500


# ==================== API PARA LA APP ANDROID ====================

@app.route('/api/productos', methods=['GET'])
@token_required
def get_productos():
    """Obtener productos para la app Android"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        if is_postgres:
            query = """
                SELECT p.id, p.nombre, s.nombre as seccion, p.precio_venta, p.stock, p.descripcion
                FROM productos p
                JOIN secciones s ON p.seccion_id = s.id
                ORDER BY p.nombre
            """
        else:
            query = """
                SELECT p.id, p.nombre, s.nombre as seccion, p.precio_venta, p.stock, p.descripcion
                FROM productos p
                JOIN secciones s ON p.seccion_id = s.id
                ORDER BY p.nombre
            """
        
        resultados = db.execute_query(query)
        productos = []
        if resultados:
            for row in resultados:
                productos.append({
                    'id': row[0],
                    'nombre': row[1],
                    'seccion': row[2],
                    'precio': float(row[3]),
                    'stock': row[4] if row[4] is not None else 0,
                    'descripcion': row[5] if len(row) > 5 and row[5] else ''
                })
        
        log_to_telegram(
            level='INFO',
            message=f"Productos consultados desde app Android",
            data={'total': len(productos)},
            business_id=g.business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'productos': productos,
            'total': len(productos)
        })
        
    except Exception as e:
        logger.error(f"Error en get_productos: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en get_productos: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


# ==================== ENDPOINT PARA REGISTRAR VENTAS DESDE APP ANDROID ====================
@app.route('/api/registrar-venta', methods=['POST', 'OPTIONS'])
@token_required
def registrar_venta_app():
    """Registrar venta DESDE LA APP ANDROID"""
    if request.method == 'OPTIONS':
        response = jsonify({'success': True})
        response.headers.add('Access-Control-Allow-Origin', '*')
        response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization,Accept')
        response.headers.add('Access-Control-Allow-Methods', 'POST,OPTIONS')
        return response
    
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        logger.info(f"📥 Solicitud POST a /api/registrar-venta (Android)")
        
        data = request.json
        if not data:
            return jsonify({'success': False, 'message': 'No se recibió JSON'}), 400
        
        producto_id = data.get('producto_id')
        cantidad = data.get('cantidad')
        precio_unitario = data.get('precio_unitario')
        
        if producto_id is None:
            return jsonify({'success': False, 'message': 'Campo producto_id requerido'}), 400
        if cantidad is None:
            return jsonify({'success': False, 'message': 'Campo cantidad requerido'}), 400
        if precio_unitario is None:
            return jsonify({'success': False, 'message': 'Campo precio_unitario requerido'}), 400
        
        try:
            producto_id = int(producto_id)
            cantidad = int(cantidad)
            precio_unitario = float(precio_unitario)
        except (ValueError, TypeError) as e:
            return jsonify({'success': False, 'message': f'Error en formato de datos: {str(e)}'}), 400
        
        if not hasattr(g, 'user_id') or not g.user_id:
            return jsonify({
                'success': False,
                'message': 'El token no contiene user_id. Contacta al administrador.'
            }), 401
        
        if not str(g.user_id).isdigit():
            return jsonify({
                'success': False,
                'message': 'user_id inválido en el token'
            }), 401
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Verificar stock
        if is_postgres:
            stock_query = "SELECT stock, nombre FROM productos WHERE id = %s"
        else:
            stock_query = "SELECT stock, nombre FROM productos WHERE id = ?"
        stock_result = db.execute_query(stock_query, (producto_id,))
        
        if not stock_result:
            return jsonify({'success': False, 'message': 'Producto no encontrado'}), 404
        
        stock_disponible = stock_result[0][0]
        nombre_producto = stock_result[0][1] if len(stock_result[0]) > 1 else 'Producto'
        
        if stock_disponible < cantidad:
            return jsonify({
                'success': False, 
                'message': f'Stock insuficiente. Disponible: {stock_disponible}',
                'stock_disponible': stock_disponible
            }), 400
        
        # Asegurar columna vendor_id
        if is_postgres:
            db._ensure_vendor_column(is_postgres)
        else:
            try:
                pragma_result = db.execute_query("PRAGMA table_info(ventas)")
                has_vendor_column = False
                if pragma_result and isinstance(pragma_result, list):
                    has_vendor_column = any(col[1] == 'vendor_id' for col in pragma_result)
                else:
                    try:
                        db.execute_query("SELECT vendor_id FROM ventas LIMIT 1")
                        has_vendor_column = True
                    except Exception:
                        has_vendor_column = False
                
                if not has_vendor_column:
                    logger.info("⚠️ Agregando columna vendor_id a ventas...")
                    db.execute_query("ALTER TABLE ventas ADD COLUMN vendor_id TEXT")
                    db.execute_query("CREATE INDEX IF NOT EXISTS idx_ventas_vendor_id ON ventas(vendor_id)")
                    logger.info("✅ Columna vendor_id agregada a ventas")
            except Exception as e:
                logger.error(f"Error verificando/agregando vendor_id: {e}")
                try:
                    db.execute_query("ALTER TABLE ventas ADD COLUMN vendor_id TEXT")
                    logger.info("✅ Columna vendor_id agregada a ventas (fallback)")
                except Exception as e2:
                    logger.error(f"Error agregando vendor_id (fallback): {e2}")
        
        # Registrar venta
        if is_postgres:
            insert_query = """
                INSERT INTO ventas (producto_id, cantidad, usuario_id, vendor_id) 
                VALUES (%s, %s, %s, %s)
            """
        else:
            insert_query = """
                INSERT INTO ventas (producto_id, cantidad, usuario_id, vendor_id) 
                VALUES (?, ?, ?, ?)
            """
        db.execute_query(insert_query, (
            producto_id, 
            cantidad, 
            g.user_id,
            g.vendor_id
        ))
        
        # Actualizar stock
        if is_postgres:
            update_query = "UPDATE productos SET stock = stock - %s WHERE id = %s"
        else:
            update_query = "UPDATE productos SET stock = stock - ? WHERE id = ?"
        db.execute_query(update_query, (cantidad, producto_id))
        
        total = cantidad * precio_unitario
        
        log_to_telegram(
            level='SUCCESS',
            message=f"✅ NUEVA VENTA desde App Android",
            data={
                'vendedor_id': g.vendor_id,
                'vendedor_nombre': g.vendor_name if hasattr(g, 'vendor_name') else 'N/A',
                'user_id': g.user_id,
                'producto': nombre_producto,
                'producto_id': producto_id,
                'cantidad': cantidad,
                'precio_unitario': precio_unitario,
                'total': total,
                'stock_restante': stock_disponible - cantidad
            },
            business_id=g.business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'message': f'Venta registrada: {cantidad} x {nombre_producto}',
            'venta': {
                'producto': nombre_producto,
                'producto_id': producto_id,
                'cantidad': cantidad,
                'precio_unitario': precio_unitario,
                'total': total
            },
            'stock_restante': stock_disponible - cantidad
        })
        
    except Exception as e:
        logger.error(f"Error en registrar_venta_app: {e}")
        logger.error(traceback.format_exc())
        
        log_to_telegram(
            level='ERROR',
            message=f"Error en registrar_venta_app: {str(e)}",
            data={
                'error': str(e),
                'traceback': traceback.format_exc()
            },
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/dashboard-app', methods=['GET'])
@token_required
def dashboard_app():
    """Dashboard simplificado para la app Android"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        hoy = time.strftime('%Y-%m-%d')
        if is_postgres:
            ventas_hoy_query = """
                SELECT COUNT(*), COALESCE(SUM(v.cantidad * p.precio_venta), 0)
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE DATE(v.fecha) = %s
                AND v.vendor_id = %s
            """
        else:
            ventas_hoy_query = """
                SELECT COUNT(*), COALESCE(SUM(v.cantidad * p.precio_venta), 0)
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE DATE(v.fecha) = ?
                AND v.vendor_id = ?
            """
        
        ventas_hoy = db.execute_query(ventas_hoy_query, (hoy, g.vendor_id))
        total_ventas = ventas_hoy[0][0] if ventas_hoy else 0
        total_ingresos = float(ventas_hoy[0][1]) if ventas_hoy and ventas_hoy[0][1] else 0
        
        bajo_stock_query = "SELECT COUNT(*) FROM productos WHERE stock <= 5"
        bajo_stock = db.execute_query(bajo_stock_query)
        productos_bajo_stock = bajo_stock[0][0] if bajo_stock else 0
        
        mes_actual = time.strftime('%Y-%m')
        if is_postgres:
            ventas_mes_query = """
                SELECT COUNT(*), COALESCE(SUM(v.cantidad * p.precio_venta), 0)
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE to_char(v.fecha, 'YYYY-MM') = %s
                AND v.vendor_id = %s
            """
        else:
            ventas_mes_query = """
                SELECT COUNT(*), COALESCE(SUM(v.cantidad * p.precio_venta), 0)
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE strftime('%%Y-%%m', v.fecha) = ?
                AND v.vendor_id = ?
            """
        
        ventas_mes = db.execute_query(ventas_mes_query, (mes_actual, g.vendor_id))
        ventas_mes_total = ventas_mes[0][0] if ventas_mes else 0
        ingresos_mes = float(ventas_mes[0][1]) if ventas_mes and ventas_mes[0][1] else 0
        
        if is_postgres:
            ventas_recientes_query = """
                SELECT p.nombre, v.cantidad, v.fecha, (v.cantidad * p.precio_venta) as total
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE v.vendor_id = %s
                ORDER BY v.fecha DESC
                LIMIT 5
            """
        else:
            ventas_recientes_query = """
                SELECT p.nombre, v.cantidad, v.fecha, (v.cantidad * p.precio_venta) as total
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE v.vendor_id = ?
                ORDER BY v.fecha DESC
                LIMIT 5
            """
        
        ventas_recientes = db.execute_query(ventas_recientes_query, (g.vendor_id,))
        recientes = []
        if ventas_recientes:
            for row in ventas_recientes:
                recientes.append({
                    'producto': row[0],
                    'cantidad': row[1] if row[1] is not None else 0,
                    'fecha': row[2],
                    'total': float(row[3]) if row[3] else 0
                })
        
        return jsonify({
            'success': True,
            'dashboard': {
                'ventas_hoy': total_ventas,
                'ingresos_hoy': total_ingresos,
                'ventas_mes': ventas_mes_total,
                'ingresos_mes': ingresos_mes,
                'productos_bajo_stock': productos_bajo_stock,
                'ventas_recientes': recientes,
                'fecha': hoy,
                'business_name': g.business_id
            }
        })
        
    except Exception as e:
        logger.error(f"Error en dashboard_app: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en dashboard_app: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/ventas-app', methods=['GET'])
@token_required
def ventas_app():
    """Obtener historial de ventas para la app"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        limite = request.args.get('limite', 50, type=int)
        offset = request.args.get('offset', 0, type=int)
        
        if is_postgres:
            query = """
                SELECT 
                    v.id,
                    p.nombre as producto,
                    v.cantidad,
                    p.precio_venta as precio_unitario,
                    (v.cantidad * p.precio_venta) as total,
                    v.fecha
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE v.vendor_id = %s
                ORDER BY v.fecha DESC
                LIMIT %s OFFSET %s
            """
            count_query = "SELECT COUNT(*) FROM ventas WHERE vendor_id = %s"
        else:
            query = """
                SELECT 
                    v.id,
                    p.nombre as producto,
                    v.cantidad,
                    p.precio_venta as precio_unitario,
                    (v.cantidad * p.precio_venta) as total,
                    v.fecha
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE v.vendor_id = ?
                ORDER BY v.fecha DESC
                LIMIT ? OFFSET ?
            """
            count_query = "SELECT COUNT(*) FROM ventas WHERE vendor_id = ?"
        
        resultados = db.execute_query(query, (g.vendor_id, limite, offset))
        
        ventas = []
        if resultados:
            for row in resultados:
                ventas.append({
                    'id': row[0],
                    'producto': row[1],
                    'cantidad': row[2] if row[2] is not None else 0,
                    'precio_unitario': float(row[3]) if row[3] else 0,
                    'total': float(row[4]) if row[4] else 0,
                    'fecha': row[5]
                })
        
        count_result = db.execute_query(count_query, (g.vendor_id,))
        total = count_result[0][0] if count_result else 0
        
        return jsonify({
            'success': True,
            'ventas': ventas,
            'total': total,
            'limite': limite,
            'offset': offset
        })
        
    except Exception as e:
        logger.error(f"Error en ventas_app: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en ventas_app: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/perfil-vendedor', methods=['GET'])
@token_required
def perfil_vendedor():
    """Obtener perfil del vendedor"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        from database.db_manager import DatabaseManager
        DatabaseManager.verify_and_fix_global_tables()
        conn = DatabaseManager.get_global_connection()
        
        if conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión'}), 500
        
        c = conn.cursor()
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        if is_postgres:
            c.execute("""
                SELECT v.id, v.name, v.business_id, b.name as business_name, v.role
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                WHERE v.id = %s
            """, (g.vendor_id,))
        else:
            c.execute("""
                SELECT v.id, v.name, v.business_id, b.name as business_name, v.role
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                WHERE v.id = ?
            """, (g.vendor_id,))
        
        vendor_data = c.fetchone()
        if not vendor_data:
            log_to_telegram(
                level='WARNING',
                message=f"Vendedor no encontrado: {g.vendor_id}",
                data={'vendor_id': g.vendor_id},
                business_id=g.business_id,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': 'Vendedor no encontrado'}), 404
        
        return jsonify({
            'success': True,
            'vendor': {
                'id': vendor_data[0],
                'name': vendor_data[1],
                'business_id': vendor_data[2],
                'business_name': vendor_data[3],
                'role': vendor_data[4]
            }
        })
        
    except Exception as e:
        logger.error(f"Error en perfil_vendedor: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en perfil_vendedor: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


# ==================== ENDPOINTS PARA GESTIÓN DE VENDEDORES (PANEL WEB) ====================

@app.route('/api/vendedores', methods=['GET'])
@login_required
def get_vendedores_web():
    """Obtener lista de vendedores del negocio (desde panel web)"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        if current_user.role != 'admin':
            log_to_telegram(
                level='WARNING',
                message=f"Intento de acceso no autorizado a vendedores por {current_user.username}",
                data={'role': current_user.role},
                business_id=current_user.business_id,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': 'Solo administradores pueden ver vendedores'}), 403
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(current_user.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        if is_postgres:
            query = """
                SELECT id, name, role, active, created_at
                FROM vendors
                WHERE business_id = %s
                ORDER BY created_at DESC
            """
        else:
            query = """
                SELECT id, name, role, active, created_at
                FROM vendors
                WHERE business_id = ?
                ORDER BY created_at DESC
            """
        
        resultados = db.execute_query(query, (current_user.business_id,))
        
        vendedores = []
        if resultados:
            for row in resultados:
                vendedores.append({
                    'id': row[0],
                    'name': row[1],
                    'role': row[2] if len(row) > 2 else 'vendedor',
                    'active': bool(row[3]) if row[3] is not None else True,
                    'created_at': row[4] if len(row) > 4 else None
                })
        
        log_to_telegram(
            level='INFO',
            message=f"Vendedores consultados desde panel web por {current_user.username}",
            data={'total': len(vendedores)},
            business_id=current_user.business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'vendedores': vendedores,
            'total': len(vendedores)
        })
        
    except Exception as e:
        logger.error(f"Error en get_vendedores_web: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en get_vendedores_web: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=current_user.business_id if current_user.is_authenticated else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/vendedor', methods=['POST'])
@login_required
def crear_vendedor_web():
    """Crear un nuevo vendedor (desde panel web)"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        if current_user.role != 'admin':
            log_to_telegram(
                level='WARNING',
                message=f"Intento de crear vendedor no autorizado por {current_user.username}",
                business_id=current_user.business_id,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': 'Solo administradores pueden crear vendedores'}), 403
        
        data = request.json
        name = data.get('name', '').strip()
        
        if not name:
            return jsonify({'success': False, 'message': 'El nombre es requerido'}), 400
        
        def generate_vendor_id():
            characters = string.ascii_uppercase + string.digits
            return ''.join(random.choices(characters, k=8))
        
        vendor_id = generate_vendor_id()
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # ✅ GUARDAR EN LA BASE DE DATOS DEL NEGOCIO
        from database.db_manager import DatabaseManager
        db = DatabaseManager(current_user.business_id)
        
        try:
            if is_postgres:
                db.execute_query("""
                    INSERT INTO vendors (id, name, business_id, role, active)
                    VALUES (%s, %s, %s, %s, %s)
                """, (vendor_id, name, current_user.business_id, 'vendedor', True))
            else:
                db.execute_query("""
                    INSERT INTO vendors (id, name, business_id, role, active)
                    VALUES (?, ?, ?, ?, ?)
                """, (vendor_id, name, current_user.business_id, 'vendedor', 1))
            logger.info(f"✅ Vendedor {vendor_id} creado en BD del negocio")
        except Exception as e:
            logger.error(f"Error guardando vendedor en BD del negocio: {e}")
            # Fallback: guardar en BD global
            try:
                conn = DatabaseManager.get_global_connection()
                if conn:
                    c = conn.cursor()
                    if is_postgres:
                        c.execute("SET search_path TO public")
                        c.execute("""
                            INSERT INTO vendors (id, name, business_id, role, active)
                            VALUES (%s, %s, %s, %s, %s)
                        """, (vendor_id, name, current_user.business_id, 'vendedor', True))
                    else:
                        c.execute("""
                            INSERT INTO vendors (id, name, business_id, role, active)
                            VALUES (?, ?, ?, ?, ?)
                        """, (vendor_id, name, current_user.business_id, 'vendedor', 1))
                    conn.commit()
                    logger.info(f"✅ Vendedor {vendor_id} creado en BD global (fallback)")
            except Exception as e2:
                logger.error(f"Error guardando vendedor en BD global: {e2}")
                return jsonify({'success': False, 'message': 'Error guardando vendedor: ' + str(e)}), 500
        
        log_to_telegram(
            level='SUCCESS',
            message=f"✅ Nuevo vendedor creado desde panel web",
            data={
                'vendor_id': vendor_id,
                'vendor_name': name,
                'business_id': current_user.business_id,
                'creado_por': current_user.username
            },
            business_id=current_user.business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'message': 'Vendedor creado correctamente',
            'vendor_id': vendor_id,
            'vendor_name': name
        })
        
    except Exception as e:
        logger.error(f"Error en crear_vendedor_web: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en crear_vendedor_web: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=current_user.business_id if current_user.is_authenticated else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/vendedor/<vendor_id>', methods=['PUT'])
@login_required
def actualizar_vendedor_web(vendor_id):
    """Actualizar un vendedor (desde panel web)"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        if current_user.role != 'admin':
            return jsonify({'success': False, 'message': 'Solo administradores pueden actualizar vendedores'}), 403
        
        data = request.json
        active = data.get('active')
        name = data.get('name')
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(current_user.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        updates = []
        params = []
        
        if active is not None:
            if is_postgres:
                updates.append("active = %s")
                params.append(active)
            else:
                updates.append("active = ?")
                params.append(1 if active else 0)
        
        if name:
            if is_postgres:
                updates.append("name = %s")
            else:
                updates.append("name = ?")
            params.append(name)
        
        if not updates:
            return jsonify({'success': False, 'message': 'No hay datos para actualizar'}), 400
        
        params.append(vendor_id)
        params.append(current_user.business_id)
        
        if is_postgres:
            query = f"UPDATE vendors SET {', '.join(updates)} WHERE id = %s AND business_id = %s"
        else:
            query = f"UPDATE vendors SET {', '.join(updates)} WHERE id = ? AND business_id = ?"
        
        db.execute_query(query, tuple(params))
        
        log_to_telegram(
            level='SUCCESS',
            message=f"Vendedor actualizado desde panel web",
            data={
                'vendor_id': vendor_id,
                'updated_by': current_user.username,
                'active': active,
                'name': name
            },
            business_id=current_user.business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'message': 'Vendedor actualizado correctamente'
        })
        
    except Exception as e:
        logger.error(f"Error en actualizar_vendedor_web: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en actualizar_vendedor_web: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=current_user.business_id if current_user.is_authenticated else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/vendedor/<vendor_id>', methods=['DELETE'])
@login_required
def eliminar_vendedor_web(vendor_id):
    """Eliminar un vendedor (desde panel web)"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        if current_user.role != 'admin':
            return jsonify({'success': False, 'message': 'Solo administradores pueden eliminar vendedores'}), 403
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(current_user.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Obtener nombre antes de eliminar
        if is_postgres:
            vendor_info = db.execute_query("SELECT name FROM vendors WHERE id = %s AND business_id = %s", (vendor_id, current_user.business_id))
        else:
            vendor_info = db.execute_query("SELECT name FROM vendors WHERE id = ? AND business_id = ?", (vendor_id, current_user.business_id))
        
        vendor_name = vendor_info[0][0] if vendor_info else 'DESCONOCIDO'
        
        # Eliminar de la BD del negocio
        if is_postgres:
            db.execute_query("DELETE FROM vendors WHERE id = %s AND business_id = %s", (vendor_id, current_user.business_id))
        else:
            db.execute_query("DELETE FROM vendors WHERE id = ? AND business_id = ?", (vendor_id, current_user.business_id))
        
        # También eliminar de la BD global si existe
        try:
            conn = DatabaseManager.get_global_connection()
            if conn:
                c = conn.cursor()
                if is_postgres:
                    c.execute("SET search_path TO public")
                    c.execute("DELETE FROM vendors WHERE id = %s AND business_id = %s", (vendor_id, current_user.business_id))
                else:
                    c.execute("DELETE FROM vendors WHERE id = ? AND business_id = ?", (vendor_id, current_user.business_id))
                conn.commit()
        except Exception as e:
            logger.warning(f"No se pudo eliminar de BD global: {e}")
        
        log_to_telegram(
            level='WARNING',
            message=f"Vendedor eliminado desde panel web",
            data={
                'vendor_id': vendor_id,
                'vendor_name': vendor_name,
                'deleted_by': current_user.username
            },
            business_id=current_user.business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'message': 'Vendedor eliminado correctamente'
        })
        
    except Exception as e:
        logger.error(f"Error en eliminar_vendedor_web: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en eliminar_vendedor_web: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=current_user.business_id if current_user.is_authenticated else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


# ==================== ENDPOINTS PARA GESTIÓN DE VENDEDORES (APP ANDROID) ====================

@app.route('/api/vendedores-app', methods=['GET'])
@token_required
def get_vendedores_app():
    """Obtener lista de vendedores del negocio (desde app Android)"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        if g.role != 'admin':
            log_to_telegram(
                level='WARNING',
                message=f"Intento de acceso no autorizado a vendedores desde app",
                data={'role': g.role, 'vendor_id': g.vendor_id},
                business_id=g.business_id,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': 'Solo administradores pueden ver vendedores'}), 403
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        if is_postgres:
            query = """
                SELECT id, name, role, active, created_at
                FROM vendors
                WHERE business_id = %s
                ORDER BY created_at DESC
            """
        else:
            query = """
                SELECT id, name, role, active, created_at
                FROM vendors
                WHERE business_id = ?
                ORDER BY created_at DESC
            """
        
        resultados = db.execute_query(query, (g.business_id,))
        
        vendedores = []
        if resultados:
            for row in resultados:
                vendedores.append({
                    'id': row[0],
                    'name': row[1],
                    'role': row[2] if len(row) > 2 else 'vendedor',
                    'active': bool(row[3]) if row[3] is not None else True,
                    'created_at': row[4] if len(row) > 4 else None
                })
        
        return jsonify({
            'success': True,
            'vendedores': vendedores,
            'total': len(vendedores)
        })
        
    except Exception as e:
        logger.error(f"Error en get_vendedores_app: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en get_vendedores_app: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/api/vendedor-app', methods=['POST'])
@token_required
def crear_vendedor_app():
    """Crear un nuevo vendedor (desde app Android)"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        if g.role != 'admin':
            log_to_telegram(
                level='WARNING',
                message=f"Intento de crear vendedor no autorizado desde app",
                data={'role': g.role, 'vendor_id': g.vendor_id},
                business_id=g.business_id,
                request_info=request_info
            )
            return jsonify({'success': False, 'message': 'Solo administradores pueden crear vendedores'}), 403
        
        data = request.json
        name = data.get('name', '').strip()
        
        if not name:
            return jsonify({'success': False, 'message': 'El nombre es requerido'}), 400
        
        def generate_vendor_id():
            characters = string.ascii_uppercase + string.digits
            return ''.join(random.choices(characters, k=8))
        
        vendor_id = generate_vendor_id()
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # ✅ GUARDAR EN LA BASE DE DATOS DEL NEGOCIO
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        
        try:
            if is_postgres:
                db.execute_query("""
                    INSERT INTO vendors (id, name, business_id, role, active)
                    VALUES (%s, %s, %s, %s, %s)
                """, (vendor_id, name, g.business_id, 'vendedor', True))
            else:
                db.execute_query("""
                    INSERT INTO vendors (id, name, business_id, role, active)
                    VALUES (?, ?, ?, ?, ?)
                """, (vendor_id, name, g.business_id, 'vendedor', 1))
            logger.info(f"✅ Vendedor {vendor_id} creado en BD del negocio")
        except Exception as e:
            logger.error(f"Error guardando vendedor en BD del negocio: {e}")
            # Fallback: guardar en BD global
            try:
                conn = DatabaseManager.get_global_connection()
                if conn:
                    c = conn.cursor()
                    if is_postgres:
                        c.execute("SET search_path TO public")
                        c.execute("""
                            INSERT INTO vendors (id, name, business_id, role, active)
                            VALUES (%s, %s, %s, %s, %s)
                        """, (vendor_id, name, g.business_id, 'vendedor', True))
                    else:
                        c.execute("""
                            INSERT INTO vendors (id, name, business_id, role, active)
                            VALUES (?, ?, ?, ?, ?)
                        """, (vendor_id, name, g.business_id, 'vendedor', 1))
                    conn.commit()
                    logger.info(f"✅ Vendedor {vendor_id} creado en BD global (fallback)")
            except Exception as e2:
                logger.error(f"Error guardando vendedor en BD global: {e2}")
                return jsonify({'success': False, 'message': 'Error guardando vendedor: ' + str(e)}), 500
        
        log_to_telegram(
            level='SUCCESS',
            message=f"✅ Nuevo vendedor creado desde App Android",
            data={
                'vendor_id': vendor_id,
                'vendor_name': name,
                'business_id': g.business_id,
                'creado_por': g.vendor_id,
                'creado_por_nombre': g.vendor_name
            },
            business_id=g.business_id,
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'message': 'Vendedor creado correctamente',
            'vendor_id': vendor_id,
            'vendor_name': name
        })
        
    except Exception as e:
        logger.error(f"Error en crear_vendedor_app: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en crear_vendedor_app: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


# ==================== DESCARGA DE APK ====================

@app.route('/download-apk')
def download_apk():
    try:
        apk_path = os.path.join(os.path.dirname(__file__), 'static', 'app-debug.apk')
        
        if not os.path.exists(apk_path):
            import glob
            apk_files = glob.glob(os.path.join(os.path.dirname(__file__), 'static', '*.apk'))
            if apk_files:
                apk_path = apk_files[0]
            else:
                return jsonify({'error': 'APK no encontrado'}), 404
        
        return send_file(apk_path, as_attachment=True, download_name='OmniVentas.apk')
        
    except Exception as e:
        logger.error(f"Error descargando APK: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/download-apk-public')
def download_apk_public():
    try:
        import glob
        apk_path = os.path.join(os.path.dirname(__file__), 'static', 'app-debug.apk')
        
        if not os.path.exists(apk_path):
            apk_files = glob.glob(os.path.join(os.path.dirname(__file__), 'static', '*.apk'))
            if apk_files:
                apk_path = apk_files[0]
            else:
                return "❌ APK no encontrado. Contacta al administrador.", 404
        
        return send_file(apk_path, as_attachment=True, download_name='OmniVentas.apk')
        
    except Exception as e:
        return f"❌ Error al descargar: {str(e)}", 500


@app.route('/api/apk-status')
def apk_status():
    try:
        import glob
        apk_path = os.path.join(os.path.dirname(__file__), 'static', 'app-debug.apk')
        
        if not os.path.exists(apk_path):
            apk_files = glob.glob(os.path.join(os.path.dirname(__file__), 'static', '*.apk'))
            exists = len(apk_files) > 0
        else:
            exists = True
        
        return jsonify({
            'exists': exists,
            'message': 'APK disponible' if exists else 'APK no disponible aún'
        })
    except Exception as e:
        return jsonify({'exists': False, 'error': str(e)})


# ==================== ENDPOINTS DE PRUEBA Y DIAGNÓSTICO ====================

@app.route('/api/test', methods=['GET'])
def test_endpoint():
    response = jsonify({
        'success': True,
        'message': 'Test endpoint working',
        'timestamp': datetime.datetime.now().isoformat()
    })
    response.headers.add('Access-Control-Allow-Origin', '*')
    return response


@app.route('/api/diagnostico', methods=['GET'])
def diagnostico():
    import platform
    import sys
    
    return jsonify({
        'success': True,
        'servidor': 'OmniVentas API',
        'version': '2.0',
        'python_version': sys.version,
        'platform': platform.platform(),
        'telegram_configured': bool(TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID),
        'timestamp': datetime.datetime.now().isoformat(),
        'endpoints_disponibles': [
            '/api/login-vendedor',
            '/api/productos',
            '/api/registrar-venta',
            '/api/dashboard-app',
            '/api/send-log',
            '/api/telegram-status',
            '/api/diagnostico',
            '/api/test'
        ]
    })


# ==================== LIMPIEZA DE CONEXIONES AL CIERRE ====================
import atexit

@atexit.register
def cleanup_database_connections():
    try:
        from database.db_manager import DatabaseManager
        DatabaseManager.cleanup_connections()
        logger.info("✅ Conexiones de base de datos limpiadas al cerrar")
        
        log_to_telegram(
            level='INFO',
            message="Aplicación cerrada - Conexiones limpiadas",
            business_id=None
        )
    except Exception as e:
        logger.error(f"Error limpiando conexiones: {e}")


# ==================== INICIO ====================

if __name__ == '__main__':
    try:
        port = int(os.environ.get('PORT', 10000))
        logger.info(f"Iniciando servidor en puerto {port}")
        
        log_to_telegram(
            level='SUCCESS',
            message=f"🚀 Servidor iniciado en puerto {port}",
            data={
                'port': port,
                'environment': 'production' if 'RENDER' in os.environ else 'development',
                'telegram_logs': bool(TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID)
            }
        )
        
        socketio.run(app, host='0.0.0.0', port=port, debug=False, use_reloader=False)
    except Exception as e:
        logger.error(f"Error al iniciar la aplicación: {e}")
        try:
            log_to_telegram(
                level='CRITICAL',
                message=f"🔥 Error al iniciar servidor: {str(e)}",
                data={'error': str(e), 'traceback': traceback.format_exc()}
            )
        except:
            pass
        raise
else:
    logger.info("✅ Aplicación cargada para Gunicorn")
    try:
        log_to_telegram(
            level='SUCCESS',
            message="🚀 Aplicación cargada para Gunicorn",
            data={'environment': 'production' if 'RENDER' in os.environ else 'development'}
        )
    except:
        pass
