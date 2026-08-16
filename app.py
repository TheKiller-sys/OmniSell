# app.py - Aplicación principal CON endpoint para vendedores y logs por Telegram
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

from flask_login import login_required, current_user

# Configuración básica de logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Importar y crear la aplicación Flask desde dashboard
from web.dashboard import create_app
app = create_app()
socketio = SocketIO(app, async_mode='threading', cors_allowed_origins="*")

# ==================== CONFIGURACIÓN CORS ====================
# Permitir CORS para todas las rutas API
CORS(app, resources={
    r"/api/*": {
        "origins": "*",
        "methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        "allow_headers": ["Content-Type", "Authorization", "Accept"]
    }
})

# ==================== CONFIGURACIÓN DEL BOT DE TELEGRAM PARA LOGS ====================
TELEGRAM_BOT_TOKEN = os.environ.get('TELEGRAM_BOT_TOKEN', '')
TELEGRAM_ADMIN_CHAT_ID = os.environ.get('TELEGRAM_ADMIN_CHAT_ID', '')

if TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID:
    logger.info("✅ Bot de Telegram configurado correctamente para logs")
else:
    logger.warning("⚠️ Bot de Telegram NO configurado. Los logs no se enviarán.")

# ==================== FUNCIÓN DE LOG PARA TELEGRAM (WEB) ====================

def send_telegram_message(message, parse_mode=None):
    """Función interna para enviar mensajes a Telegram"""
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


def log_to_telegram(level, message, data=None, user=None, business_id=None, request_info=None):
    """
    Función unificada para enviar logs detallados a Telegram desde la web
    
    Args:
        level: INFO, WARNING, ERROR, SUCCESS, CRITICAL
        message: Mensaje principal
        data: Datos adicionales (dict)
        user: Usuario actual (current_user)
        business_id: ID del negocio
        request_info: Información de la petición (método, ruta, IP)
    """
    try:
        if not TELEGRAM_BOT_TOKEN or not TELEGRAM_ADMIN_CHAT_ID:
            return False
        
        # Emojis por nivel
        emoji = {
            'DEBUG': '🔍',
            'INFO': 'ℹ️',
            'WARNING': '⚠️',
            'ERROR': '❌',
            'SUCCESS': '✅',
            'CRITICAL': '🔥'
        }.get(level, '📱')
        
        # Timestamp
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        
        # Construir mensaje
        lines = [
            f"{emoji} [{level}] LOG WEB - OmniVentas",
            "",
            f"⏰ Timestamp: {timestamp}",
        ]
        
        # Información de usuario
        if user:
            user_info = f"Usuario: {user.username} (ID: {user.id}) - Rol: {user.role}"
            lines.append(f"👤 {user_info}")
        elif current_user and current_user.is_authenticated:
            user_info = f"Usuario: {current_user.username} (ID: {current_user.id}) - Rol: {current_user.role}"
            lines.append(f"👤 {user_info}")
        
        # Business ID
        if business_id:
            lines.append(f"🏪 Business ID: {business_id}")
        elif session and session.get('business_id'):
            lines.append(f"🏪 Business ID: {session.get('business_id')}")
        
        # Información de la petición
        if request_info:
            lines.append(f"📡 Método: {request_info.get('method', 'N/A')}")
            lines.append(f"🔗 Ruta: {request_info.get('path', 'N/A')}")
            lines.append(f"🌐 IP: {request_info.get('ip', 'N/A')}")
            if request_info.get('user_agent'):
                lines.append(f"📱 User-Agent: {request_info.get('user_agent', 'N/A')[:100]}")
        
        # Mensaje principal
        lines.append("")
        lines.append(f"📝 Mensaje: {message}")
        
        # Datos adicionales
        if data:
            try:
                if isinstance(data, dict):
                    data_str = json.dumps(data, indent=2, default=str, ensure_ascii=False)
                else:
                    data_str = str(data)
                
                # Limitar longitud
                if len(data_str) > 2000:
                    data_str = data_str[:2000] + "... (truncado)"
                
                lines.append("")
                lines.append("📊 Datos adicionales:")
                lines.append(data_str)
            except Exception as e:
                lines.append(f"📊 Datos: {str(data)}")
        
        # Enviar
        full_message = "\n".join(lines)
        return send_telegram_message(full_message)
        
    except Exception as e:
        logger.error(f"Error en log_to_telegram: {e}")
        return False


# ==================== DECORADOR PARA LOGS AUTOMÁTICOS EN RUTAS ====================

def log_web_request(level='INFO'):
    """
    Decorador para loguear automáticamente peticiones web a Telegram
    """
    def decorator(f):
        @wraps(f)
        def decorated(*args, **kwargs):
            # Obtener información de la petición
            request_info = {
                'method': request.method,
                'path': request.path,
                'ip': request.remote_addr,
                'user_agent': request.headers.get('User-Agent', 'N/A')
            }
            
            # Obtener usuario
            user = None
            try:
                if current_user and current_user.is_authenticated:
                    user = current_user
            except:
                pass
            
            # Business ID
            business_id = session.get('business_id') if session else None
            
            try:
                # Ejecutar la función original
                response = f(*args, **kwargs)
                
                # Log de éxito
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
                # Log de error
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
    """Manejar errores 404 devolviendo JSON y log a Telegram"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr,
        'user_agent': request.headers.get('User-Agent', 'N/A')
    }
    
    # Log a Telegram
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
    """Manejar errores 500 devolviendo JSON y log a Telegram"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr,
        'user_agent': request.headers.get('User-Agent', 'N/A')
    }
    
    # Log a Telegram con traceback completo
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
    """Manejar cualquier excepción devolviendo JSON y log a Telegram"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr,
        'user_agent': request.headers.get('User-Agent', 'N/A')
    }
    
    logger.error(f"Error no manejado: {error}")
    
    # Log a Telegram con traceback completo
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
    """
    Decorador para verificar token JWT en peticiones de la app Android.
    ✅ Usa flask.g para almacenar los datos del token de forma segura.
    ✅ Valida que user_id sea numérico.
    """
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        if not auth_header:
            # Log de intento sin token
            log_to_telegram(
                level='WARNING',
                message=f"Intento de acceso sin token a {request.path}",
                request_info={
                    'method': request.method,
                    'path': request.path,
                    'ip': request.remote_addr
                }
            )
            return jsonify({'success': False, 'message': 'Token requerido'}), 401
        
        try:
            token = auth_header.split(' ')[1]
            payload = jwt.decode(token, os.environ.get('JWT_SECRET', 'secret-key'), algorithms=['HS256'])
            
            # ✅ Usar flask.g para almacenar datos del token
            g.vendor_id = payload.get('vendor_id')
            g.user_id = payload.get('user_id')
            g.business_id = payload.get('business_id')
            g.vendor_name = payload.get('name')
            g.role = payload.get('role', 'vendedor')
            
            # ✅ Validar que user_id sea numérico y > 0
            if not g.user_id:
                log_to_telegram(
                    level='WARNING',
                    message=f"Token sin user_id: vendor_id={g.vendor_id}",
                    data={'vendor_id': g.vendor_id},
                    business_id=g.business_id
                )
                return jsonify({
                    'success': False, 
                    'message': 'Token inválido: user_id requerido. Contacta al administrador.'
                }), 401
            
            if not str(g.user_id).isdigit():
                log_to_telegram(
                    level='WARNING',
                    message=f"user_id no numérico: {g.user_id}",
                    data={'user_id': g.user_id},
                    business_id=g.business_id
                )
                return jsonify({
                    'success': False, 
                    'message': 'Token inválido: user_id debe ser numérico'
                }), 401
            
            logger.debug(f"🔐 Token válido: vendor={g.vendor_id}, user_id={g.user_id}, business={g.business_id}")
            
            return f(*args, **kwargs)
            
        except jwt.ExpiredSignatureError:
            log_to_telegram(
                level='WARNING',
                message=f"Token expirado en {request.path}",
                request_info={
                    'method': request.method,
                    'path': request.path,
                    'ip': request.remote_addr
                }
            )
            return jsonify({'success': False, 'message': 'Token expirado'}), 401
        except jwt.InvalidTokenError as e:
            log_to_telegram(
                level='WARNING',
                message=f"Token inválido en {request.path}: {str(e)}",
                request_info={
                    'method': request.method,
                    'path': request.path,
                    'ip': request.remote_addr
                }
            )
            return jsonify({'success': False, 'message': 'Token inválido'}), 401
        except Exception as e:
            log_to_telegram(
                level='ERROR',
                message=f"Error en token_required: {str(e)}",
                data={'traceback': traceback.format_exc()},
                request_info={
                    'method': request.method,
                    'path': request.path,
                    'ip': request.remote_addr
                }
            )
            return jsonify({'success': False, 'message': 'Error de autenticación'}), 401
    return decorated


# ==================== HEALTH CHECK ====================

@app.route('/health')
def health_check():
    """Endpoint para health checks y mantener el servicio activo"""
    status_data = {
        'status': 'ok',
        'timestamp': time.time(),
        'service': 'OmniVentas API',
        'version': '2.0',
        'environment': 'production' if 'RENDER' in os.environ else 'development',
        'telegram_logs': bool(TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID)
    }
    
    log_to_telegram(
        level='INFO',
        message="Health check realizado",
        data={'status': 'ok'},
        business_id=None
    )
    
    return jsonify(status_data), 200


# ==================== ENDPOINT: LOGS POR TELEGRAM (BOT ÚNICO) ====================

@app.route('/api/send-log', methods=['POST', 'OPTIONS'])
def send_log_to_telegram():
    """
    Recibe logs desde la app Android y los envía al bot de Telegram del programador.
    NO requiere autenticación, es un endpoint público para logs.
    """
    # Manejar preflight CORS
    if request.method == 'OPTIONS':
        response = jsonify({'success': True})
        response.headers.add('Access-Control-Allow-Origin', '*')
        response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization,Accept')
        response.headers.add('Access-Control-Allow-Methods', 'POST,OPTIONS')
        return response
    
    try:
        # Verificar que el bot esté configurado
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
        
        # Emojis por nivel
        emoji = {
            'DEBUG': '🔍',
            'INFO': 'ℹ️',
            'WARNING': '⚠️',
            'ERROR': '❌',
            'SUCCESS': '✅',
            'CRITICAL': '🔥'
        }.get(log_level, '📱')
        
        # Construir mensaje formateado
        message_lines = [
            f"{emoji} [{log_level}] Log desde App Android",
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
        
        # Enviar mensaje a Telegram
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
    """Verificar el estado del bot de Telegram"""
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
    """Endpoint para probar el envío de logs (sin autenticación)"""
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
        
        # Reutilizar la función de envío
        with app.test_request_context(json=test_data):
            return send_log_to_telegram()
            
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)}), 500

# ==================== ENDPOINT: LOGIN DE VENDEDOR ====================

@app.route('/api/login-vendedor', methods=['POST'])
def login_vendedor():
    """Login para vendedores con ID de 8 caracteres"""
    request_info = {
        'method': request.method,
        'path': request.path,
        'ip': request.remote_addr
    }
    
    try:
        data = request.json
        vendor_id = data.get('vendor_id', '').strip().upper()
        
        # Validar formato del ID (8 caracteres alfanuméricos)
        if not vendor_id:
            log_to_telegram(
                level='WARNING',
                message=f"Intento de login sin ID de vendedor",
                request_info=request_info
            )
            return jsonify({
                'success': False, 
                'message': 'ID de vendedor requerido'
            }), 400
        
        if len(vendor_id) != 8:
            log_to_telegram(
                level='WARNING',
                message=f"Intento de login con ID inválido: {vendor_id} (longitud incorrecta)",
                data={'vendor_id': vendor_id},
                request_info=request_info
            )
            return jsonify({
                'success': False, 
                'message': 'El ID debe tener exactamente 8 caracteres'
            }), 400
        
        if not vendor_id.isalnum():
            log_to_telegram(
                level='WARNING',
                message=f"Intento de login con ID inválido: {vendor_id} (caracteres no permitidos)",
                data={'vendor_id': vendor_id},
                request_info=request_info
            )
            return jsonify({
                'success': False, 
                'message': 'El ID solo debe contener letras y números'
            }), 400
        
        from database.db_manager import DatabaseManager
        DatabaseManager.verify_and_fix_global_tables()
        conn = DatabaseManager.get_global_connection()
        
        if conn is None:
            log_to_telegram(
                level='ERROR',
                message="Error de conexión a la base de datos en login_vendedor",
                request_info=request_info
            )
            return jsonify({'success': False, 'message': 'Error de conexión'}), 500
        
        c = conn.cursor()
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # ✅ Buscar vendedor por ID y obtener el user_id real
        if is_postgres:
            c.execute("""
                SELECT 
                    v.id as vendor_id, 
                    v.name, 
                    v.business_id, 
                    b.name as business_name, 
                    v.role, 
                    v.active,
                    COALESCE(u.id, (SELECT id FROM users WHERE business_id = b.id ORDER BY id LIMIT 1)) as user_id
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                LEFT JOIN users u ON u.business_id = b.id AND u.role = 'admin'
                WHERE v.id = %s
            """, (vendor_id,))
        else:
            c.execute("""
                SELECT 
                    v.id as vendor_id, 
                    v.name, 
                    v.business_id, 
                    b.name as business_name, 
                    v.role, 
                    v.active,
                    COALESCE(u.id, (SELECT id FROM users WHERE business_id = b.id ORDER BY id LIMIT 1)) as user_id
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                LEFT JOIN users u ON u.business_id = b.id AND u.role = 'admin'
                WHERE v.id = ?
            """, (vendor_id,))
        
        vendor_data = c.fetchone()
        
        if not vendor_data:
            log_to_telegram(
                level='WARNING',
                message=f"Login fallido: Vendor ID no encontrado: {vendor_id}",
                data={'vendor_id': vendor_id},
                request_info=request_info
            )
            return jsonify({
                'success': False, 
                'message': 'ID de vendedor no encontrado'
            }), 401
        
        # Verificar si el vendedor está activo
        if not vendor_data[5]:
            log_to_telegram(
                level='WARNING',
                message=f"Login fallido: Vendedor desactivado: {vendor_id}",
                data={
                    'vendor_id': vendor_id,
                    'vendor_name': vendor_data[1],
                    'business_id': vendor_data[2]
                },
                request_info=request_info
            )
            return jsonify({
                'success': False, 
                'message': 'Este vendedor está desactivado. Contacta al administrador.'
            }), 401
        
        # ✅ Obtener el user_id real
        user_id = vendor_data[6]
        if not user_id:
            # Fallback: buscar cualquier usuario del negocio
            if is_postgres:
                c.execute("SELECT id FROM users WHERE business_id = %s LIMIT 1", (vendor_data[2],))
            else:
                c.execute("SELECT id FROM users WHERE business_id = ? LIMIT 1", (vendor_data[2],))
            user_fallback = c.fetchone()
            if user_fallback:
                user_id = user_fallback[0]
            else:
                log_to_telegram(
                    level='ERROR',
                    message=f"No hay usuarios para el negocio {vendor_data[2]}",
                    data={'business_id': vendor_data[2]},
                    request_info=request_info
                )
                return jsonify({
                    'success': False, 
                    'message': 'Error de configuración: no hay usuarios en el negocio'
                }), 500
        
        # ✅ Generar token JWT
        token = jwt.encode({
            'vendor_id': vendor_data[0],
            'user_id': user_id,
            'business_id': vendor_data[2],
            'name': vendor_data[1],
            'role': vendor_data[4],
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=7)
        }, os.environ.get('JWT_SECRET', 'secret-key'), algorithm='HS256')
        
        # Log de login exitoso
        log_to_telegram(
            level='SUCCESS',
            message=f"✅ Login exitoso desde App Android",
            data={
                'vendor_id': vendor_data[0],
                'vendor_name': vendor_data[1],
                'business_id': vendor_data[2],
                'business_name': vendor_data[3],
                'user_id': user_id
            },
            request_info=request_info
        )
        
        return jsonify({
            'success': True,
            'token': token,
            'vendor': {
                'id': vendor_data[0],
                'name': vendor_data[1],
                'business_id': vendor_data[2],
                'business_name': vendor_data[3],
                'role': vendor_data[4],
                'user_id': user_id
            }
        })
        
    except Exception as e:
        logger.error(f"Error en login_vendedor: {e}")
        log_to_telegram(
            level='ERROR',
            message=f"Error en login_vendedor: {str(e)}",
            data={'error': str(e), 'traceback': traceback.format_exc()},
            request_info=request_info
        )
        return jsonify({'success': False, 'message': str(e)}), 500


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
    """
    Registrar venta DESDE LA APP ANDROID.
    ✅ Usa g.user_id del JWT (numérico) para la columna usuario_id
    ✅ También guarda vendor_id para trazabilidad
    ✅ Valida que user_id sea numérico
    """
    # Manejar preflight CORS
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
        logger.info(f"📥 Headers: {dict(request.headers)}")
        
        raw_data = request.get_data(as_text=True)
        logger.info(f"📥 Raw data: {raw_data}")
        
        try:
            data = request.json
        except Exception as e:
            logger.error(f"Error parseando JSON: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error parseando JSON en registrar_venta: {str(e)}",
                data={'raw_data': raw_data[:200]},
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({'success': False, 'message': f'Error parseando JSON: {str(e)}'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        if not data:
            log_to_telegram(
                level='WARNING',
                message="No se recibió JSON en registrar_venta",
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({'success': False, 'message': 'No se recibió JSON'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        producto_id = data.get('producto_id')
        cantidad = data.get('cantidad')
        precio_unitario = data.get('precio_unitario')
        
        logger.info(f"📥 producto_id: {producto_id}, cantidad: {cantidad}, precio_unitario: {precio_unitario}")
        
        # Validar campos
        if producto_id is None:
            log_to_telegram(
                level='WARNING',
                message="Campo producto_id no enviado en registrar_venta",
                data={'data': data},
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({'success': False, 'message': 'Campo producto_id no enviado'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        if cantidad is None:
            log_to_telegram(
                level='WARNING',
                message="Campo cantidad no enviado en registrar_venta",
                data={'data': data},
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({'success': False, 'message': 'Campo cantidad no enviado'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        if precio_unitario is None:
            log_to_telegram(
                level='WARNING',
                message="Campo precio_unitario no enviado en registrar_venta",
                data={'data': data},
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({'success': False, 'message': 'Campo precio_unitario no enviado'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        if producto_id == '':
            response = jsonify({'success': False, 'message': 'producto_id vacío'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        if cantidad == '':
            response = jsonify({'success': False, 'message': 'cantidad vacía'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        if precio_unitario == '':
            response = jsonify({'success': False, 'message': 'precio_unitario vacío'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        try:
            producto_id = int(producto_id)
            cantidad = int(cantidad)
            precio_unitario = float(precio_unitario)
        except (ValueError, TypeError) as e:
            logger.error(f"Error de conversión de tipos: {e}")
            log_to_telegram(
                level='ERROR',
                message=f"Error de conversión de tipos en registrar_venta: {str(e)}",
                data={'producto_id': producto_id, 'cantidad': cantidad, 'precio_unitario': precio_unitario},
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({'success': False, 'message': f'Error en formato de datos: {str(e)}'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        if not hasattr(g, 'user_id') or not g.user_id:
            log_to_telegram(
                level='ERROR',
                message="Token no contiene user_id en registrar_venta",
                data={'vendor_id': g.vendor_id if hasattr(g, 'vendor_id') else None},
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({
                'success': False,
                'message': 'El token no contiene user_id. Contacta al administrador.'
            })
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 401
        
        if not str(g.user_id).isdigit():
            log_to_telegram(
                level='ERROR',
                message=f"user_id inválido en token: {g.user_id}",
                data={'user_id': g.user_id},
                business_id=g.business_id if hasattr(g, 'business_id') else None,
                request_info=request_info
            )
            response = jsonify({
                'success': False,
                'message': 'user_id inválido en el token'
            })
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 401
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Verificar stock
        stock_query = "SELECT stock, nombre FROM productos WHERE id = %s" if is_postgres else "SELECT stock, nombre FROM productos WHERE id = ?"
        stock_result = db.execute_query(stock_query, (producto_id,))
        
        if not stock_result:
            log_to_telegram(
                level='WARNING',
                message=f"Producto no encontrado: ID {producto_id}",
                data={'producto_id': producto_id},
                business_id=g.business_id,
                request_info=request_info
            )
            response = jsonify({'success': False, 'message': 'Producto no encontrado'})
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 404
        
        stock_disponible = stock_result[0][0]
        nombre_producto = stock_result[0][1] if len(stock_result[0]) > 1 else 'Producto'
        
        if stock_disponible < cantidad:
            log_to_telegram(
                level='WARNING',
                message=f"Stock insuficiente: {nombre_producto} (ID: {producto_id})",
                data={
                    'producto': nombre_producto,
                    'stock_disponible': stock_disponible,
                    'cantidad_solicitada': cantidad
                },
                business_id=g.business_id,
                request_info=request_info
            )
            response = jsonify({
                'success': False, 
                'message': f'Stock insuficiente. Disponible: {stock_disponible}',
                'stock_disponible': stock_disponible
            })
            response.headers.add('Access-Control-Allow-Origin', '*')
            return response, 400
        
        # ✅ Verificar columna vendor_id
        if is_postgres:
            db._ensure_vendor_column(is_postgres)
        else:
            check_column = db.execute_query("PRAGMA table_info(ventas)")
            has_vendor_column = any(col[1] == 'vendor_id' for col in check_column) if check_column else False
            if not has_vendor_column:
                db.execute_query("ALTER TABLE ventas ADD COLUMN vendor_id TEXT")
                db.execute_query("CREATE INDEX IF NOT EXISTS idx_ventas_vendor_id ON ventas(vendor_id)")
        
        # ✅ INSERTAR VENTA
        insert_query = """
            INSERT INTO ventas (producto_id, cantidad, usuario_id, vendor_id) 
            VALUES (%s, %s, %s, %s)
        """ if is_postgres else """
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
        update_query = "UPDATE productos SET stock = stock - %s WHERE id = %s" if is_postgres else "UPDATE productos SET stock = stock - ? WHERE id = ?"
        db.execute_query(update_query, (cantidad, producto_id))
        
        total = cantidad * precio_unitario
        
        # Log de venta registrada
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
        
        response = jsonify({
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
        response.headers.add('Access-Control-Allow-Origin', '*')
        return response
        
    except Exception as e:
        logger.error(f"Error en registrar_venta_app: {e}")
        logger.error(traceback.format_exc())
        
        log_to_telegram(
            level='ERROR',
            message=f"Error en registrar_venta_app: {str(e)}",
            data={
                'error': str(e),
                'traceback': traceback.format_exc(),
                'vendor_id': g.vendor_id if hasattr(g, 'vendor_id') else 'DESCONOCIDO',
                'user_id': g.user_id if hasattr(g, 'user_id') else 'DESCONOCIDO',
                'business_id': g.business_id if hasattr(g, 'business_id') else 'DESCONOCIDO'
            },
            business_id=g.business_id if hasattr(g, 'business_id') else None,
            request_info=request_info
        )
        
        response = jsonify({'success': False, 'message': str(e)})
        response.headers.add('Access-Control-Allow-Origin', '*')
        return response, 500


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
            """
        else:
            ventas_hoy_query = """
                SELECT COUNT(*), COALESCE(SUM(v.cantidad * p.precio_venta), 0)
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE DATE(v.fecha) = ?
            """
        
        ventas_hoy = db.execute_query(ventas_hoy_query, (hoy,))
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
            """
        else:
            ventas_mes_query = """
                SELECT COUNT(*), COALESCE(SUM(v.cantidad * p.precio_venta), 0)
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                WHERE strftime('%%Y-%%m', v.fecha) = ?
            """
        
        ventas_mes = db.execute_query(ventas_mes_query, (mes_actual,))
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
    """
    Obtener historial de ventas para la app.
    ✅ FILTRA POR vendor_id para que cada vendedor vea solo sus ventas.
    """
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
                    'role': row[2],
                    'active': bool(row[3]) if row[3] is not None else True,
                    'created_at': row[4]
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
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(current_user.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        import random
        import string
        def generate_vendor_id():
            characters = string.ascii_uppercase + string.digits
            return ''.join(random.choices(characters, k=8))
        
        vendor_id = generate_vendor_id()
        
        if is_postgres:
            existing = db.execute_query("SELECT id FROM vendors WHERE id = %s", (vendor_id,))
        else:
            existing = db.execute_query("SELECT id FROM vendors WHERE id = ?", (vendor_id,))
        
        while existing and existing[0]:
            vendor_id = generate_vendor_id()
            if is_postgres:
                existing = db.execute_query("SELECT id FROM vendors WHERE id = %s", (vendor_id,))
            else:
                existing = db.execute_query("SELECT id FROM vendors WHERE id = ?", (vendor_id,))
        
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
        
        # Obtener nombre del vendedor antes de eliminar
        if is_postgres:
            vendor_info = db.execute_query("SELECT name FROM vendors WHERE id = %s AND business_id = %s", (vendor_id, current_user.business_id))
        else:
            vendor_info = db.execute_query("SELECT name FROM vendors WHERE id = ? AND business_id = ?", (vendor_id, current_user.business_id))
        
        vendor_name = vendor_info[0][0] if vendor_info else 'DESCONOCIDO'
        
        if is_postgres:
            db.execute_query("DELETE FROM vendors WHERE id = %s AND business_id = %s", (vendor_id, current_user.business_id))
        else:
            db.execute_query("DELETE FROM vendors WHERE id = ? AND business_id = ?", (vendor_id, current_user.business_id))
        
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
                    'role': row[2],
                    'active': bool(row[3]) if row[3] is not None else True,
                    'created_at': row[4]
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
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(g.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        import random
        import string
        def generate_vendor_id():
            characters = string.ascii_uppercase + string.digits
            return ''.join(random.choices(characters, k=8))
        
        vendor_id = generate_vendor_id()
        
        if is_postgres:
            existing = db.execute_query("SELECT id FROM vendors WHERE id = %s", (vendor_id,))
        else:
            existing = db.execute_query("SELECT id FROM vendors WHERE id = ?", (vendor_id,))
        
        while existing and existing[0]:
            vendor_id = generate_vendor_id()
            if is_postgres:
                existing = db.execute_query("SELECT id FROM vendors WHERE id = %s", (vendor_id,))
            else:
                existing = db.execute_query("SELECT id FROM vendors WHERE id = ?", (vendor_id,))
        
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
    """Servir el archivo APK para descarga"""
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
    """Servir el archivo APK sin requerir login"""
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
    """Verificar si el APK está disponible"""
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
    """Endpoint de prueba para verificar que el servidor responde JSON"""
    response = jsonify({
        'success': True,
        'message': 'Test endpoint working',
        'timestamp': datetime.datetime.now().isoformat()
    })
    response.headers.add('Access-Control-Allow-Origin', '*')
    return response

@app.route('/api/venta-diagnostico', methods=['GET'])
def venta_diagnostico():
    """Endpoint para diagnosticar problemas de ventas"""
    return jsonify({
        'success': True,
        'message': 'Endpoint de diagnóstico funcionando',
        'telegram_configured': bool(TELEGRAM_BOT_TOKEN and TELEGRAM_ADMIN_CHAT_ID),
        'telegram_token': TELEGRAM_BOT_TOKEN[:10] + '...' if TELEGRAM_BOT_TOKEN else None,
        'telegram_chat_id': TELEGRAM_ADMIN_CHAT_ID[:10] + '...' if TELEGRAM_ADMIN_CHAT_ID else None,
        'timestamp': datetime.datetime.now().isoformat()
    })

@app.route('/api/diagnostico', methods=['GET'])
def diagnostico():
    """Endpoint de diagnóstico completo"""
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
            '/api/test',
            '/api/venta-diagnostico'
        ]
    })

# ==================== LIMPIEZA DE CONEXIONES AL CIERRE ====================
import atexit
@atexit.register
def cleanup_database_connections():
    """Limpiar conexiones de base de datos al cerrar la aplicación"""
    try:
        from database.db_manager import DatabaseManager
        DatabaseManager.cleanup_connections()
        logger.info("✅ Conexiones de base de datos limpiadas al cerrar")
    except Exception as e:
        logger.error(f"Error limpiando conexiones: {e}")

# ==================== INICIO ====================

if __name__ == '__main__':
    try:
        port = int(os.environ.get('PORT', 10000))
        logger.info(f"Iniciando servidor en puerto {port}")
        socketio.run(app, host='0.0.0.0', port=port, debug=False, use_reloader=False)
    except Exception as e:
        logger.error(f"Error al iniciar la aplicación: {e}")
        raise
else:
    # Para ejecución con Gunicorn
    pass
