# app.py - Aplicación principal con webhooks para Telegram
import os
from flask import Flask, g, jsonify, request, session
import threading
from database.db_manager import DatabaseManager
import logging
from flask_socketio import SocketIO
import time
import requests
from slugify import slugify
import random
import string
from urllib.parse import urljoin
import json

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

# Función para generar string aleatorio
def generate_random_string(length=8):
    """Generar string aleatorio para business_id"""
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))

def init_global_db():
    """Inicializar base de datos global de negocios"""
    try:
        # Verificar y corregir la estructura de la base de datos
        DatabaseManager.verify_and_fix_global_tables()
        logger.info("Base de datos global inicializada correctamente")
    except Exception as e:
        logger.error(f"Error al crear tablas globales: {e}")
        # Reintentar después de un tiempo
        time.sleep(2)
        init_global_db()

def get_active_businesses():
    """Obtener todos los negocios registrados con tokens activos"""
    try:
        DatabaseManager.verify_and_fix_global_tables()
        
        conn = DatabaseManager.get_global_connection()
        if conn is None:
            logger.error("No se pudo obtener conexión a la base de datos")
            return []
            
        c = conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute("SELECT id, telegram_token, admin_id, bot_configured FROM businesses WHERE telegram_token IS NOT NULL AND telegram_token != ''")
        else:
            c.execute("SELECT id, telegram_token, admin_id, bot_configured FROM businesses WHERE telegram_token IS NOT NULL AND telegram_token != ''")
        
        businesses = []
        for row in c.fetchall():
            businesses.append({
                'id': row[0],
                'token': row[1],
                'admin_id': row[2],
                'bot_configured': row[3] if len(row) > 3 else False
            })
        return businesses
    except Exception as e:
        logger.error(f"Error al obtener negocios: {e}")
        return []

def get_webhook_url():
    """Obtener la URL base para webhooks"""
    if 'RENDER' in os.environ:
        # En Render, usa la variable de entorno proporcionada
        return os.environ.get('RENDER_EXTERNAL_URL', '').rstrip('/')
    else:
        # En desarrollo, usa localhost con el puerto correcto
        port = os.environ.get('PORT', 10000)
        return f"http://localhost:{port}"

def setup_webhook(business_id, bot_token):
    """Configurar webhook para un bot de Telegram"""
    try:
        webhook_url = urljoin(get_webhook_url(), f"/webhook/{business_id}")
        
        # Validar que la URL sea HTTPS en producción
        if 'RENDER' in os.environ and not webhook_url.startswith('https://'):
            logger.warning(f"La URL del webhook no es HTTPS: {webhook_url}. Telegram requiere HTTPS en producción.")
            # Forzar HTTPS si está en Render
            webhook_url = webhook_url.replace('http://', 'https://')
        
        logger.info(f"Configurando webhook para {business_id} en: {webhook_url}")
        
        # Configurar el webhook
        response = requests.post(
            f"https://api.telegram.org/bot{bot_token}/setWebhook",
            json={"url": webhook_url},
            timeout=10
        )
        
        result = response.json()
        if result.get('ok'):
            logger.info(f"Webhook configurado para business {business_id}: {result.get('description')}")
            return True
        else:
            logger.error(f"Error configurando webhook para {business_id}: {result.get('description')}")
            return False
            
    except Exception as e:
        logger.error(f"Excepción al configurar webhook para {business_id}: {str(e)}")
        return False

def remove_webhook(bot_token):
    """Eliminar webhook de un bot de Telegram"""
    try:
        response = requests.post(
            f"https://api.telegram.org/bot{bot_token}/deleteWebhook",
            timeout=10
        )
        
        result = response.json()
        if result.get('ok'):
            logger.info(f"Webhook eliminado correctamente")
            return True
        else:
            logger.error(f"Error eliminando webhook: {result.get('description')}")
            return False
            
    except Exception as e:
        logger.error(f"Excepción al eliminar webhook: {str(e)}")
        return False

def send_telegram_message(bot_token, chat_id, text, parse_mode=None):
    """Enviar mensaje a través de la API de Telegram"""
    try:
        if not bot_token or not chat_id:
            logger.error("Token o chat_id vacío")
            return False
            
        # Asegurar que chat_id es string y sin espacios
        chat_id = str(chat_id).strip()
        
        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        payload = {
            'chat_id': chat_id,
            'text': text
        }
        
        if parse_mode:
            payload['parse_mode'] = parse_mode
            
        response = requests.post(url, json=payload, timeout=10)
        
        if response.status_code == 200:
            return True
        else:
            logger.error(f"Error enviando mensaje: {response.status_code} - {response.text}")
            return False
        
    except Exception as e:
        logger.error(f"Error enviando mensaje de Telegram: {str(e)}")
        return False

def setup_bot_webhook(business_id, token, admin_id):
    """Configurar webhook para un bot en lugar de usar polling"""
    logger.info(f"Configurando webhook para negocio: {business_id}")
    
    # Validación básica del token
    if not token or token.strip() == '' or ':' not in token:
        logger.error(f"Token inválido para negocio {business_id}")
        return
        
    try:
        # Primero verificar que el token sea válido
        test_response = requests.get(f'https://api.telegram.org/bot{token}/getMe', timeout=10)
        if test_response.status_code != 200:
            logger.error(f"Token inválido para negocio {business_id}: {test_response.status_code}")
            return
            
        bot_info = test_response.json()
        if bot_info.get('ok'):
            logger.info(f"Token válido para bot @{bot_info['result']['username']}")
        else:
            logger.error(f"Token inválido: {bot_info.get('description')}")
            return
        
        # Configurar el webhook
        if setup_webhook(business_id, token):
            # Enviar mensaje de confirmación al administrador
            try:
                message_text = f"✅ Bot @{bot_info['result']['username']} configurado exitosamente para tu negocio\n\nAhora puedes usar los comandos de administración. Escribe /start para comenzar."
                send_telegram_message(token, admin_id, message_text)
                logger.info(f"Mensaje de confirmación enviado al admin {admin_id}")
            except Exception as e:
                logger.warning(f"No se pudo enviar mensaje de confirmación: {e}")
                
            # Actualizar estado en la base de datos
            try:
                global_conn = DatabaseManager.get_global_connection()
                if global_conn is None:
                    logger.error("No se pudo obtener conexión a la base de datos para actualizar bot_configured")
                    return
                    
                c = global_conn.cursor()
                if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                    c.execute("UPDATE businesses SET bot_configured = TRUE WHERE id = %s", (business_id,))
                else:
                    c.execute("UPDATE businesses SET bot_configured = TRUE WHERE id = ?", (business_id,))
                global_conn.commit()
                logger.info(f"Estado bot_configured actualizado para {business_id}")
            except Exception as e:
                logger.error(f"Error actualizando bot_configured: {e}")
            
            logger.info(f"Webhook configurado exitosamente para business {business_id}")
        else:
            logger.error(f"Fallo al configurar webhook para business {business_id}")
            
    except Exception as e:
        logger.error(f"Error configurando webhook para business {business_id}: {str(e)}")

def start_background_services():
    """Iniciar todos los servicios en segundo plano"""
    logger.info("Iniciando servicios en segundo plano...")
    
    # Inicializar base de datos global
    init_global_db()
    
    # Esperar un poco para que la base de datos global esté lista
    time.sleep(3)
    
    # Configurar webhooks para cada negocio
    businesses = get_active_businesses()
    logger.info(f"Negocios activos encontrados: {len(businesses)}")
    
    for business in businesses:
        # Solo configurar si el bot está marcado como configurado
        if business.get('bot_configured', False) and business.get('token'):
            thread = threading.Thread(
                target=setup_bot_webhook,
                args=(business['id'], business['token'], business['admin_id']),
                daemon=True
            )
            thread.start()
            logger.info(f"Webhook configurado para negocio: {business['id']}")
        else:
            logger.info(f"Bot no configurado para negocio: {business['id']} - omitiendo webhook")

# Ruta para manejar webhooks
@app.route('/webhook/<business_id>', methods=['POST'])
def handle_webhook(business_id):
    """Manejar webhooks de Telegram para un business específico"""
    try:
        # Obtener el update de Telegram
        update = request.get_json()
        logger.info(f"Webhook recibido para business {business_id}")
        
        if not update:
            logger.warning("Webhook recibido sin datos")
            return jsonify({'status': 'error', 'message': 'No data'}), 400
        
        # Obtener el token del bot para este business
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            logger.error("No se pudo obtener conexión a la base de datos")
            return jsonify({'status': 'error', 'message': 'Database connection failed'}), 500
            
        c = global_conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = %s", (business_id,))
        else:
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = ?", (business_id,))
        result = c.fetchone()
        
        if not result or not result[0]:
            logger.error(f"Business {business_id} no encontrado o sin token configurado")
            return jsonify({'status': 'error', 'message': 'Business not found'}), 404
            
        bot_token, admin_id = result
        
        # Procesar el update en un hilo separado para no bloquear la respuesta
        thread = threading.Thread(
            target=process_telegram_update,
            args=(business_id, bot_token, admin_id, update),
            daemon=True
        )
        thread.start()
        
        return jsonify({'status': 'ok'})
        
    except Exception as e:
        logger.error(f"Error procesando webhook para {business_id}: {str(e)}")
        return jsonify({'status': 'error', 'message': str(e)}), 500

def process_telegram_update(business_id, bot_token, admin_id, update):
    """Procesar un update de Telegram"""
    try:
        if 'message' in update:
            message = update['message']
            chat_id = message['chat']['id']
            text = message.get('text', '')
            
            logger.info(f"Mensaje recibido de {chat_id}: {text}")
            
            # Procesar el mensaje según el comando
            if text.startswith('/'):
                command = text.split(' ')[0].lower()
                
                if command == '/start':
                    response = f"¡Hola! 👋 Soy tu asistente de OmniVentas.\n\n" \
                              f"Business ID: {business_id}\n" \
                              f"Escribe /menu para ver las opciones disponibles."
                    send_telegram_message(bot_token, chat_id, response)
                    
                elif command == '/menu':
                    response = "📋 <b>Menú Principal</b>\n\n" \
                              "/ventas - Registrar una venta\n" \
                              "/inventario - Ver estado del inventario\n" \
                              "/estadisticas - Ver estadísticas\n" \
                              "/ayuda - Obtener ayuda"
                    send_telegram_message(bot_token, chat_id, response, parse_mode='HTML')
                    
                elif command == '/ventas':
                    handle_ventas_command(business_id, bot_token, chat_id, message)
                    
                elif command == '/inventario':
                    handle_inventario_command(business_id, bot_token, chat_id)
                    
                elif command == '/estadisticas':
                    handle_estadisticas_command(business_id, bot_token, chat_id)
                    
                elif command == '/ayuda':
                    help_text = "🤖 <b>Ayuda de OmniVentas</b>\n\n" \
                               "Soy tu asistente para gestionar tu negocio.\n\n" \
                               "<b>Comandos disponibles:</b>\n" \
                               "/start - Iniciar el bot\n" \
                               "/menu - Ver menú principal\n" \
                               "/ventas - Registrar ventas\n" \
                               "/inventario - Consultar inventario\n" \
                               "/estadisticas - Ver estadísticas\n" \
                               "/ayuda - Mostrar esta ayuda"
                    send_telegram_message(bot_token, chat_id, help_text, parse_mode='HTML')
                
                else:
                    send_telegram_message(bot_token, chat_id, "❌ Comando no reconocido. Usa /ayuda para ver los comandos disponibles.")
            
            else:
                # Mensaje de texto regular
                send_telegram_message(bot_token, chat_id, "🤖 Escribe /menu para ver las opciones disponibles.")
    
    except Exception as e:
        logger.error(f"Error procesando update para {business_id}: {str(e)}")
        # Notificar al administrador sobre el error
        try:
            error_msg = f"❌ Error en bot para {business_id}:\n{str(e)}"
            send_telegram_message(bot_token, admin_id, error_msg)
        except:
            pass

def handle_ventas_command(business_id, bot_token, chat_id, message):
    """Manejar comando de ventas"""
    try:
        db = DatabaseManager(business_id)
        
        # Parsear el mensaje: /ventas producto cantidad precio
        parts = message.get('text', '').split(' ')
        
        if len(parts) >= 4:
            producto_nombre = parts[1]
            cantidad = int(parts[2])
            precio = float(parts[3])
            
            # Buscar el producto
            productos = db.execute_query(
                "SELECT id, precio_venta, stock FROM productos WHERE nombre LIKE ?",
                (f"%{producto_nombre}%",)
            )
            
            if productos and productos[0]:
                producto_id, precio_venta, stock = productos[0]
                
                if stock >= cantidad:
                    # Registrar la venta
                    db.execute_query(
                        "INSERT INTO ventas (producto_id, cantidad, usuario_id) VALUES (?, ?, ?)",
                        (producto_id, cantidad, 1)
                    )
                    
                    # Actualizar stock
                    db.execute_query(
                        "UPDATE productos SET stock = stock - ? WHERE id = ?",
                        (cantidad, producto_id)
                    )
                    
                    total = cantidad * precio_venta
                    response = f"✅ <b>Venta registrada exitosamente!</b>\n\n" \
                              f"Producto: {producto_nombre}\n" \
                              f"Cantidad: {cantidad}\n" \
                              f"Precio unitario: ${precio_venta:.2f}\n" \
                              f"Total: ${total:.2f}\n" \
                              f"Stock restante: {stock - cantidad}"
                    
                    send_telegram_message(bot_token, chat_id, response, parse_mode='HTML')
                else:
                    response = f"❌ <b>Stock insuficiente</b>\n\n" \
                              f"Producto: {producto_nombre}\n" \
                              f"Stock disponible: {stock}\n" \
                              f"Stock solicitado: {cantidad}"
                    send_telegram_message(bot_token, chat_id, response, parse_mode='HTML')
            else:
                response = f"❌ Producto no encontrado: {producto_nombre}\n\n" \
                          f"Usa /inventario para ver los productos disponibles."
                send_telegram_message(bot_token, chat_id, response)
        else:
            response = "📊 <b>Módulo de Ventas</b>\n\n" \
                      "Para registrar una venta, usa el formato:\n" \
                      "<code>/ventas producto cantidad precio</code>\n\n" \
                      "Ejemplo:\n" \
                      "<code>/ventas camiseta 2 25.99</code>"
            send_telegram_message(bot_token, chat_id, response, parse_mode='HTML')
        
    except Exception as e:
        error_msg = f"❌ Error al procesar ventas: {str(e)}"
        send_telegram_message(bot_token, chat_id, error_msg)
        logger.error(f"Error en ventas para {business_id}: {str(e)}")

def handle_inventario_command(business_id, bot_token, chat_id):
    """Manejar comando de inventario"""
    try:
        db = DatabaseManager(business_id)
        
        # Obtener inventario desde la base de datos
        inventory = db.execute_query(
            "SELECT p.nombre, p.stock, s.nombre as seccion " \
            "FROM productos p " \
            "JOIN secciones s ON p.seccion_id = s.id " \
            "ORDER BY p.stock ASC LIMIT 10"
        )
        
        if inventory and len(inventory) > 0:
            response = "📦 <b>Inventario - Top 10 productos</b>\n\n"
            for product in inventory:
                nombre, stock, seccion = product
                status = "🟢" if stock > 10 else "🟡" if stock > 3 else "🔴"
                response += f"{status} <b>{nombre}</b> - {stock} unidades ({seccion})\n"
                
            response += "\nUsa /inventario_completo para ver todo el inventario."
        else:
            response = "📦 No hay productos en el inventario.\n\n" \
                      "Agrega productos desde el panel web."
                      
        send_telegram_message(bot_token, chat_id, response, parse_mode='HTML')
        
    except Exception as e:
        error_msg = f"❌ Error al consultar inventario: {str(e)}"
        send_telegram_message(bot_token, chat_id, error_msg)
        logger.error(f"Error en inventario para {business_id}: {str(e)}")

def handle_estadisticas_command(business_id, bot_token, chat_id):
    """Manejar comando de estadísticas"""
    try:
        db = DatabaseManager(business_id)
        
        # Obtener estadísticas básicas
        stats = db.execute_query(
            "SELECT " \
            "(SELECT SUM(v.cantidad * p.precio_venta) FROM ventas v JOIN productos p ON v.producto_id = p.id) as total_ventas, " \
            "(SELECT COUNT(*) FROM ventas) as total_transacciones, " \
            "(SELECT COUNT(*) FROM productos) as total_productos"
        )
        
        if stats and stats[0]:
            total_ventas, total_transacciones, total_productos = stats[0]
            total_ventas = total_ventas or 0
            
            response = "📈 <b>Estadísticas de tu Negocio</b>\n\n" \
                      f"💰 <b>Ventas totales:</b> ${total_ventas:.2f}\n" \
                      f"🧾 <b>Transacciones:</b> {total_transacciones}\n" \
                      f"📦 <b>Productos registrados:</b> {total_productos}\n\n" \
                      "Para análisis detallados, visita el panel web."
        else:
            response = "📈 Aún no hay estadísticas disponibles.\n\n" \
                      "Después de registrar ventas, podrás verlas aquí."
                      
        send_telegram_message(bot_token, chat_id, response, parse_mode='HTML')
        
    except Exception as e:
        error_msg = f"❌ Error al obtener estadísticas: {str(e)}"
        send_telegram_message(bot_token, chat_id, error_msg)
        logger.error(f"Error en estadísticas para {business_id}: {str(e)}")

# ==================== ENDPOINTS DE DIAGNÓSTICO Y MANTENIMIENTO ====================

# Health check para mantener el servicio activo
@app.route('/health')
def health_check():
    """Endpoint para health checks y mantener el servicio activo"""
    return jsonify({
        'status': 'ok',
        'timestamp': time.time(),
        'service': 'OmniVentas',
        'environment': 'production' if 'RENDER' in os.environ else 'development'
    }), 200

# Debug del webhook
@app.route('/api/webhook-debug/<business_id>')
def webhook_debug(business_id):
    """Endpoint para debuggear el webhook del bot"""
    try:
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            return jsonify({'status': 'error', 'message': 'Database connection failed'})
            
        c = global_conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute("SELECT telegram_token, bot_configured FROM businesses WHERE id = %s", (business_id,))
        else:
            c.execute("SELECT telegram_token, bot_configured FROM businesses WHERE id = ?", (business_id,))
        result = c.fetchone()
        
        if not result or not result[0]:
            return jsonify({'status': 'error', 'message': 'Token no configurado'})
        
        bot_token = result[0]
        
        # Verificar estado del webhook con Telegram
        response = requests.get(f'https://api.telegram.org/bot{bot_token}/getWebhookInfo', timeout=10)
        webhook_info = response.json()
        
        webhook_url = f"https://{os.environ.get('RENDER_EXTERNAL_URL', 'localhost:10000')}/webhook/{business_id}"
        
        return jsonify({
            'status': 'success',
            'webhook_info': webhook_info,
            'server_time': time.time(),
            'webhook_url': webhook_url,
            'is_webhook_active': webhook_info.get('ok') and webhook_info.get('result', {}).get('url') == webhook_url
        })
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)})

# Forzar reconexión del webhook
@app.route('/api/webhook-refresh/<business_id>', methods=['POST'])
def webhook_refresh(business_id):
    """Forzar la reconexión del webhook"""
    try:
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            return jsonify({'status': 'error', 'message': 'Database connection failed'})
            
        c = global_conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = %s", (business_id,))
        else:
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = ?", (business_id,))
        result = c.fetchone()
        
        if not result or not result[0]:
            return jsonify({'success': False, 'message': 'Token no configurado'})
        
        bot_token, admin_id = result
        
        # Eliminar webhook existente
        remove_webhook(bot_token)
        
        # Configurar nuevo webhook
        success = setup_webhook(business_id, bot_token)
        
        if success:
            # Enviar mensaje de confirmación
            message = "🔄 <b>Webhook refrescado correctamente</b>\n\n" \
                     "El webhook ha sido reconectado exitosamente."
            send_telegram_message(bot_token, admin_id, message, parse_mode='HTML')
            
            return jsonify({
                'success': True,
                'message': 'Webhook refrescado correctamente'
            })
        else:
            return jsonify({
                'success': False,
                'message': 'Error configurando webhook'
            })
            
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)})

# ==================== FIN DE ENDPOINTS DE DIAGNÓSTICO ====================

# Función de diagnóstico del bot
@app.route('/api/bot-diagnostic/token')
def get_bot_token_diagnostic():
    """Obtener el token del bot para diagnóstico"""
    business_id = request.args.get('business_id')
    
    if not business_id:
        return jsonify({'success': False, 'message': 'Business ID requerido'})
    
    try:
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión a la base de datos'})
            
        c = global_conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = %s", (business_id,))
        else:
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = ?", (business_id,))
        result = c.fetchone()
        
        if result and result[0]:
            return jsonify({
                'success': True,
                'token': result[0],
                'admin_id': result[1]
            })
        else:
            return jsonify({
                'success': False,
                'message': 'Token no configurado'
            })
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'Error obteniendo token: {str(e)}'
        })

@app.route('/api/bot-diagnostic/validate-token', methods=['POST'])
def validate_bot_token():
    """Validar el token con la API de Telegram"""
    data = request.json
    token = data.get('token')
    
    if not token:
        return jsonify({'success': False, 'message': 'Token requerido'})
    
    try:
        # Verificar el token con la API de Telegram
        response = requests.get(f'https://api.telegram.org/bot{token}/getMe', timeout=10)
        if response.status_code == 200:
            bot_data = response.json()
            if bot_data.get('ok'):
                return jsonify({
                    'success': True,
                    'username': bot_data['result']['username'],
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
        return jsonify({
            'success': False,
            'message': f'Error validando token: {str(e)}'
        })

@app.route('/api/bot-diagnostic/webhook', methods=['POST'])
def check_webhook_status():
    """Verificar el estado del webhook en Telegram"""
    data = request.json
    token = data.get('token')
    business_id = data.get('business_id')
    
    if not token:
        return jsonify({'success': False, 'message': 'Token requerido'})
    
    try:
        # Obtener información del webhook actual
        response = requests.get(f'https://api.telegram.org/bot{token}/getWebhookInfo', timeout=10)
        webhook_info = response.json()
        
        if webhook_info.get('ok'):
            webhook_data = webhook_info['result']
            
            # Si no tiene webhook configurado, configurarlo
            if not webhook_data.get('url'):
                if business_id:
                    # Configurar webhook
                    webhook_url = urljoin(get_webhook_url(), f"/webhook/{business_id}")
                    
                    # Forzar HTTPS en producción
                    if 'RENDER' in os.environ and not webhook_url.startswith('https://'):
                        webhook_url = webhook_url.replace('http://', 'https://')
                    
                    set_response = requests.post(
                        f"https://api.telegram.org/bot{token}/setWebhook",
                        json={"url": webhook_url},
                        timeout=10
                    )
                    set_result = set_response.json()
                    if set_result.get('ok'):
                        return jsonify({
                            'success': True,
                            'message': 'Webhook configurado correctamente',
                            'webhook_url': webhook_url,
                            'info': webhook_data
                        })
                    else:
                        return jsonify({
                            'success': False,
                            'message': f'Error configurando webhook: {set_result.get("description")}',
                            'info': webhook_data
                        })
                else:
                    return jsonify({
                        'success': False,
                        'message': 'Business ID requerido para configurar webhook',
                        'info': webhook_data
                    })
            else:
                return jsonify({
                    'success': True,
                    'message': f'Webhook configurado: {webhook_data["url"]}',
                    'webhook_url': webhook_data.get('url'),
                    'info': webhook_data
                })
        else:
            return jsonify({
                'success': False,
                'message': webhook_info.get('description', 'Error obteniendo información del webhook')
            })
            
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'Error verificando webhook: {str(e)}'
        })

@app.route('/api/bot-diagnostic/test-message', methods=['POST'])
def test_webhook_message():
    """Enviar mensaje de prueba al administrador"""
    data = request.json
    token = data.get('token')
    business_id = data.get('business_id')
    admin_id = data.get('admin_id')
    
    if not token:
        return jsonify({'success': False, 'message': 'Token requerido'})
    
    if not admin_id:
        return jsonify({'success': False, 'message': 'Admin ID requerido'})
    
    try:
        message_text = "✅ <b>Mensaje de prueba desde OmniVentas</b>\n\n" \
                      "Tu bot está configurado correctamente y funcionando con webhooks.\n\n" \
                      "Fecha: {}\n" \
                      "Business ID: {}".format(
                          time.strftime('%Y-%m-%d %H:%M:%S'),
                          business_id or 'No especificado'
                      )
        
        success = send_telegram_message(token, admin_id, message_text, parse_mode='HTML')
        
        if success:
            return jsonify({
                'success': True,
                'message': 'Mensaje de prueba enviado correctamente'
            })
        else:
            return jsonify({
                'success': False,
                'message': 'Error enviando mensaje. Verifica el token y el admin_id.'
            })
            
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'Error enviando mensaje de prueba: {str(e)}'
        })

@app.route('/api/bot-diagnostic/restart-bot', methods=['POST'])
def restart_bot():
    """Reiniciar la configuración del bot"""
    data = request.json
    business_id = data.get('business_id')
    
    if not business_id:
        return jsonify({'success': False, 'message': 'Business ID requerido'})
    
    try:
        # Obtener el token actual
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión a la base de datos'})
            
        c = global_conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = %s", (business_id,))
        else:
            c.execute("SELECT telegram_token, admin_id FROM businesses WHERE id = ?", (business_id,))
        result = c.fetchone()
        
        if not result or not result[0]:
            return jsonify({'success': False, 'message': 'Token no configurado'})
        
        token, admin_id = result
        
        # Eliminar webhook existente
        remove_webhook(token)
        
        # Configurar nuevo webhook
        success = setup_webhook(business_id, token)
        
        if success:
            # Enviar mensaje de confirmación
            message = "🔄 <b>Bot reiniciado correctamente</b>\n\n" \
                     "El bot ha sido reiniciado y el webhook ha sido reconfigurado."
            send_telegram_message(token, admin_id, message, parse_mode='HTML')
            
            return jsonify({
                'success': True,
                'message': 'Bot reiniciado correctamente'
            })
        else:
            return jsonify({
                'success': False,
                'message': 'Error configurando webhook después del reinicio'
            })
            
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'Error reiniciando bot: {str(e)}'
        })

@app.route('/api/bot-diagnostic/update-webhook', methods=['POST'])
def update_webhook():
    """Forzar actualización del webhook"""
    data = request.json
    token = data.get('token')
    business_id = data.get('business_id')
    
    if not token:
        return jsonify({'success': False, 'message': 'Token requerido'})
    
    if not business_id:
        return jsonify({'success': False, 'message': 'Business ID requerido'})
    
    try:
        webhook_url = urljoin(get_webhook_url(), f"/webhook/{business_id}")
        
        # Forzar HTTPS en producción
        if 'RENDER' in os.environ and not webhook_url.startswith('https://'):
            webhook_url = webhook_url.replace('http://', 'https://')
        
        response = requests.post(
            f"https://api.telegram.org/bot{token}/setWebhook",
            json={"url": webhook_url},
            timeout=10
        )
        
        result = response.json()
        if result.get('ok'):
            return jsonify({
                'success': True,
                'message': f'Webhook actualizado: {webhook_url}',
                'webhook_url': webhook_url
            })
        else:
            return jsonify({
                'success': False,
                'message': result.get('description', 'Error actualizando webhook')
            })
            
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'Error actualizando webhook: {str(e)}'
        })

@app.route('/api/bot-diagnostic/reset-token', methods=['POST'])
def reset_bot_token():
    """Regenerar token del bot (eliminar token actual)"""
    data = request.json
    business_id = data.get('business_id')
    
    if not business_id:
        return jsonify({'success': False, 'message': 'Business ID requerido'})
    
    try:
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión a la base de datos'})
            
        c = global_conn.cursor()
        # Eliminar el token y marcar como no configurado
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute(
                "UPDATE businesses SET telegram_token = NULL, bot_configured = FALSE WHERE id = %s",
                (business_id,)
            )
        else:
            c.execute(
                "UPDATE businesses SET telegram_token = NULL, bot_configured = FALSE WHERE id = ?",
                (business_id,)
            )
        global_conn.commit()
        
        return jsonify({
            'success': True,
            'message': 'Token eliminado correctamente. Configura uno nuevo desde el panel.'
        })
            
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'Error eliminando token: {str(e)}'
        })

# Endpoint para obtener el estado del webhook del bot
@app.route('/api/bot/webhook-status/<business_id>')
def get_webhook_status_api(business_id):
    """Obtener estado del webhook para un business"""
    try:
        # Verificar en la base de datos global
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            return jsonify({'status': 'error', 'message': 'Error de conexión a la base de datos'})
            
        c = global_conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute("SELECT telegram_token, bot_configured FROM businesses WHERE id = %s", (business_id,))
        else:
            c.execute("SELECT telegram_token, bot_configured FROM businesses WHERE id = ?", (business_id,))
        result = c.fetchone()
        
        if not result or not result[0]:
            return jsonify({'status': 'error', 'message': 'Token no configurado'})
        
        bot_token, bot_configured = result
        
        # Verificar estado del webhook con Telegram
        response = requests.get(f'https://api.telegram.org/bot{bot_token}/getWebhookInfo', timeout=10)
        webhook_info = response.json()
        
        if webhook_info.get('ok'):
            return jsonify({
                'status': 'success',
                'bot_configured': bot_configured,
                'webhook_info': webhook_info['result']
            })
        else:
            return jsonify({
                'status': 'error',
                'message': webhook_info.get('description', 'Error desconocido')
            })
                
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)})

# Endpoint para conectar el bot (SOLO UNA VEZ)
@app.route('/api/connect-bot', methods=['POST'])
def connect_bot():
    """Conectar el bot de Telegram y configurar webhook"""
    try:
        data = request.json
        token = data.get('token', '').strip()
        business_id = data.get('business_id') or session.get('business_id')
        
        if not token:
            return jsonify({'success': False, 'message': 'Token requerido'})
        
        if not business_id:
            return jsonify({'success': False, 'message': 'Business ID requerido'})
        
        # 1. VALIDAR EL TOKEN CON TELEGRAM
        test_response = requests.get(f'https://api.telegram.org/bot{token}/getMe', timeout=10)
        if test_response.status_code != 200:
            return jsonify({'success': False, 'message': 'Token inválido o revocado'})
        
        bot_info = test_response.json()
        if not bot_info.get('ok'):
            return jsonify({'success': False, 'message': bot_info.get('description', 'Token inválido')})
        
        bot_username = bot_info['result'].get('username', 'desconocido')
        logger.info(f"Token válido para bot @{bot_username}")
        
        # 2. GUARDAR TOKEN EN BD
        global_conn = DatabaseManager.get_global_connection()
        if global_conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión a la base de datos'})
            
        c = global_conn.cursor()
        if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
            c.execute(
                "UPDATE businesses SET telegram_token = %s, bot_configured = TRUE WHERE id = %s",
                (token, business_id)
            )
        else:
            c.execute(
                "UPDATE businesses SET telegram_token = ?, bot_configured = TRUE WHERE id = ?",
                (token, business_id)
            )
        global_conn.commit()
        
        # 3. CONFIGURAR WEBHOOK
        webhook_url = urljoin(get_webhook_url(), f"/webhook/{business_id}")
        
        # Forzar HTTPS en producción
        if 'RENDER' in os.environ and not webhook_url.startswith('https://'):
            webhook_url = webhook_url.replace('http://', 'https://')
        
        logger.info(f"Configurando webhook para {business_id} en: {webhook_url}")
        
        webhook_response = requests.post(
            f"https://api.telegram.org/bot{token}/setWebhook",
            json={"url": webhook_url},
            timeout=10
        )
        webhook_result = webhook_response.json()
        
        if not webhook_result.get('ok'):
            return jsonify({
                'success': False,
                'message': f'Error configurando webhook: {webhook_result.get("description")}',
                'webhook_url': webhook_url
            })
        
        # 4. ENVIAR MENSAJE DE CONFIRMACIÓN AL ADMIN
        try:
            # Obtener admin_id si no vino en la petición
            admin_id = data.get('admin_id')
            if not admin_id:
                if global_conn is None:
                    raise Exception("No se pudo obtener conexión a la base de datos")
                c2 = global_conn.cursor()
                if 'RENDER' in os.environ and os.environ.get('DATABASE_URL'):
                    c2.execute("SELECT admin_id FROM businesses WHERE id = %s", (business_id,))
                else:
                    c2.execute("SELECT admin_id FROM businesses WHERE id = ?", (business_id,))
                result = c2.fetchone()
                admin_id = result[0] if result else None
            
            if admin_id:
                message_text = f"✅ Bot @{bot_username} conectado correctamente\n\n" \
                              f"📡 Webhook: {webhook_url}\n" \
                              f"🤖 Bot: @{bot_username}\n\n" \
                              f"Ahora puedes usar los comandos de administración.\n" \
                              f"Escribe /start para comenzar."
                send_telegram_message(token, admin_id, message_text)
        except Exception as e:
            logger.warning(f"No se pudo enviar mensaje de confirmación: {e}")
        
        return jsonify({
            'success': True,
            'message': f'Bot @{bot_username} conectado y webhook configurado',
            'webhook_url': webhook_url,
            'bot_username': bot_username
        })
        
    except Exception as e:
        logger.error(f"Error en connect_bot: {str(e)}")
        return jsonify({'success': False, 'message': str(e)})

# Iniciar servicios al arrancar
if __name__ == '__main__':
    try:
        start_background_services()
        port = int(os.environ.get('PORT', 10000))
        logger.info(f"Iniciando servidor en puerto {port}")
        socketio.run(app, host='0.0.0.0', port=port, debug=False, use_reloader=False)
    except Exception as e:
        logger.error(f"Error al iniciar la aplicación: {e}")
        raise
else:
    # Para ejecución con Gunicorn
    start_background_services()
