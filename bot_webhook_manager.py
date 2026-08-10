# bot_webhook_manager.py - Gestor centralizado de webhooks para bots de Telegram
import logging
import requests
import os
from database.db_manager import DatabaseManager
from flask import jsonify
import time
from urllib.parse import urljoin

logger = logging.getLogger(__name__)

class WebhookManager:
    """Gestor centralizado de webhooks para todos los bots de Telegram"""
    
    def __init__(self):
        self.active_bots = {}
        self.lock = None  # Para futura implementación de threading
        self._initialized = False
    
    def initialize(self):
        """Inicializar el gestor cargando los bots activos desde la base de datos"""
        if self._initialized:
            return
        
        try:
            businesses = self._get_active_businesses()
            for business in businesses:
                if business.get('bot_configured', False) and business.get('token'):
                    self.active_bots[business['id']] = {
                        'token': business['token'],
                        'admin_id': business['admin_id']
                    }
            
            self._initialized = True
            logger.info(f"WebhookManager inicializado con {len(self.active_bots)} bots activos")
            
        except Exception as e:
            logger.error(f"Error inicializando WebhookManager: {str(e)}")
    
    def _get_active_businesses(self):
        """Obtener negocios activos desde la base de datos"""
        try:
            DatabaseManager.verify_and_fix_global_tables()
            
            with DatabaseManager.get_global_connection() as conn:
                c = conn.cursor()
                if 'RENDER' in os.environ:
                    c.execute("""
                        SELECT id, telegram_token, admin_id, bot_configured 
                        FROM businesses 
                        WHERE telegram_token IS NOT NULL AND telegram_token != ''
                    """)
                else:
                    c.execute("""
                        SELECT id, telegram_token, admin_id, bot_configured 
                        FROM businesses 
                        WHERE telegram_token IS NOT NULL AND telegram_token != ''
                    """)
                
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
            logger.error(f"Error obteniendo negocios activos: {str(e)}")
            return []
    
    def get_webhook_url(self):
        """Obtener la URL base para webhooks"""
        if 'RENDER' in os.environ:
            return os.environ.get('RENDER_EXTERNAL_URL', '').rstrip('/')
        else:
            port = os.environ.get('PORT', 10000)
            return f"http://localhost:{port}"
    
    def setup_webhook(self, business_id, bot_token):
        """Configurar webhook para un bot específico"""
        try:
            webhook_url = urljoin(self.get_webhook_url(), f"/webhook/{business_id}")
            
            # Configurar el webhook
            response = requests.post(
                f"https://api.telegram.org/bot{bot_token}/setWebhook",
                json={"url": webhook_url},
                timeout=10
            )
            
            result = response.json()
            if result.get('ok'):
                logger.info(f"Webhook configurado para business {business_id}: {result.get('description')}")
                self.active_bots[business_id] = {
                    'token': bot_token,
                    'webhook_url': webhook_url
                }
                return True
            else:
                logger.error(f"Error configurando webhook para {business_id}: {result.get('description')}")
                return False
                
        except Exception as e:
            logger.error(f"Excepción al configurar webhook para {business_id}: {str(e)}")
            return False
    
    def remove_webhook(self, bot_token):
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
    
    def get_webhook_info(self, bot_token):
        """Obtener información del webhook actual"""
        try:
            response = requests.get(
                f"https://api.telegram.org/bot{bot_token}/getWebhookInfo",
                timeout=10
            )
            
            result = response.json()
            if result.get('ok'):
                return result['result']
            else:
                logger.error(f"Error obteniendo información del webhook: {result.get('description')}")
                return None
                
        except Exception as e:
            logger.error(f"Excepción al obtener información del webhook: {str(e)}")
            return None
    
    def setup_webhook_for_business(self, business_id, bot_token, webhook_url=None):
        """Configurar webhook para un business específico"""
        try:
            if not webhook_url:
                webhook_url = urljoin(self.get_webhook_url(), f"/webhook/{business_id}")
            
            # Eliminar webhook existente primero
            self.remove_webhook(bot_token)
            
            # Configurar nuevo webhook
            response = requests.post(
                f"https://api.telegram.org/bot{bot_token}/setWebhook",
                json={"url": webhook_url},
                timeout=10
            )
            
            result = response.json()
            if result.get('ok'):
                logger.info(f"Webhook configurado para {business_id}: {result.get('description')}")
                self.active_bots[business_id] = {
                    'token': bot_token,
                    'webhook_url': webhook_url
                }
                return True
            else:
                logger.error(f"Error configurando webhook para {business_id}: {result.get('description')}")
                return False
                
        except Exception as e:
            logger.error(f"Excepción al configurar webhook para {business_id}: {str(e)}")
            return False
    
    def remove_webhook_for_business(self, business_id):
        """Eliminar webhook para un business"""
        if business_id in self.active_bots:
            try:
                bot_token = self.active_bots[business_id]['token']
                response = requests.post(
                    f"https://api.telegram.org/bot{bot_token}/deleteWebhook",
                    timeout=10
                )
                
                result = response.json()
                if result.get('ok'):
                    logger.info(f"Webhook eliminado para {business_id}")
                    del self.active_bots[business_id]
                    return True
                else:
                    logger.error(f"Error eliminando webhook para {business_id}: {result.get('description')}")
                    return False
                    
            except Exception as e:
                logger.error(f"Excepción al eliminar webhook para {business_id}: {str(e)}")
                return False
        return True
    
    def get_bot_token(self, business_id):
        """Obtener token de un bot desde la base de datos"""
        try:
            with DatabaseManager.get_global_connection() as conn:
                c = conn.cursor()
                if 'RENDER' in os.environ:
                    c.execute("SELECT telegram_token FROM businesses WHERE id = %s", (business_id,))
                else:
                    c.execute("SELECT telegram_token FROM businesses WHERE id = ?", (business_id,))
                result = c.fetchone()
                if result and result[0]:
                    return result[0]
                return None
        except Exception as e:
            logger.error(f"Error obteniendo token para {business_id}: {str(e)}")
            return None
    
    def send_telegram_message(self, bot_token, chat_id, text, parse_mode=None):
        """Enviar mensaje a través de la API de Telegram"""
        try:
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
            logger.error(f"Error enviando mensaje de Telegram: {str(e)}")
            return False
    
    def refresh_bots(self):
        """Refrescar la lista de bots activos desde la base de datos"""
        self._initialized = False
        self.active_bots = {}
        self.initialize()
        logger.info(f"Bots refrescados. Activos: {len(self.active_bots)}")

# Instancia global del webhook manager
webhook_manager = WebhookManager()

# Inicializar el gestor al importar
webhook_manager.initialize()