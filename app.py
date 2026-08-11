# app.py - Aplicación principal SIN Telegram, SOLO API REST
import os
from flask import Flask, g, jsonify, request, session
import logging
from flask_socketio import SocketIO
import time
import json
import jwt
import datetime
import bcrypt
from functools import wraps

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

# ==================== DECORADOR DE AUTENTICACIÓN ====================

def token_required(f):
    """Decorador para verificar token JWT en peticiones de la app"""
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        if not auth_header:
            return jsonify({'success': False, 'message': 'Token requerido'}), 401
        
        try:
            token = auth_header.split(' ')[1]
            payload = jwt.decode(token, os.environ.get('JWT_SECRET', 'secret-key'), algorithms=['HS256'])
            request.user_id = payload['user_id']
            request.business_id = payload['business_id']
            request.username = payload['username']
            request.role = payload.get('role', 'vendedor')
            return f(*args, **kwargs)
        except jwt.ExpiredSignatureError:
            return jsonify({'success': False, 'message': 'Token expirado'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'success': False, 'message': 'Token inválido'}), 401
        except Exception as e:
            logger.error(f"Error en token_required: {e}")
            return jsonify({'success': False, 'message': 'Error de autenticación'}), 401
    return decorated

# ==================== HEALTH CHECK ====================

@app.route('/health')
def health_check():
    """Endpoint para health checks y mantener el servicio activo"""
    return jsonify({
        'status': 'ok',
        'timestamp': time.time(),
        'service': 'OmniVentas API',
        'version': '2.0',
        'environment': 'production' if 'RENDER' in os.environ else 'development'
    }), 200

# ==================== API PARA LA APP ANDROID ====================

@app.route('/api/login-vendedor', methods=['POST'])
def login_vendedor():
    """Login para vendedores desde la app Android"""
    try:
        data = request.json
        username = data.get('username', '').strip()
        password = data.get('password', '').strip()
        business_id = data.get('business_id', '').strip()
        
        if not all([username, password, business_id]):
            return jsonify({'success': False, 'message': 'Faltan datos: usuario, contraseña y business_id son requeridos'}), 400
        
        # Verificar credenciales en la base de datos global
        from database.db_manager import DatabaseManager
        DatabaseManager.verify_and_fix_global_tables()
        conn = DatabaseManager.get_global_connection()
        if conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión a la base de datos'}), 500
        
        c = conn.cursor()
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        if is_postgres:
            c.execute("""
                SELECT u.id, u.business_id, u.username, u.password, u.role, b.name as business_name
                FROM users u
                JOIN businesses b ON u.business_id = b.id
                WHERE u.username = %s AND u.business_id = %s
            """, (username, business_id))
        else:
            c.execute("""
                SELECT u.id, u.business_id, u.username, u.password, u.role, b.name as business_name
                FROM users u
                JOIN businesses b ON u.business_id = b.id
                WHERE u.username = ? AND u.business_id = ?
            """, (username, business_id))
        
        user_data = c.fetchone()
        if not user_data:
            return jsonify({'success': False, 'message': 'Credenciales inválidas'}), 401
        
        # Verificar contraseña con bcrypt
        stored_password = user_data[3]
        if bcrypt.checkpw(password.encode('utf-8'), stored_password.encode('utf-8')):
            # Generar token JWT
            token = jwt.encode({
                'user_id': user_data[0],
                'business_id': user_data[1],
                'username': user_data[2],
                'role': user_data[4],
                'exp': datetime.datetime.utcnow() + datetime.timedelta(days=7)
            }, os.environ.get('JWT_SECRET', 'secret-key'), algorithm='HS256')
            
            return jsonify({
                'success': True,
                'token': token,
                'user': {
                    'id': user_data[0],
                    'username': user_data[2],
                    'role': user_data[4],
                    'business_name': user_data[5]
                }
            })
        else:
            return jsonify({'success': False, 'message': 'Credenciales inválidas'}), 401
            
    except Exception as e:
        logger.error(f"Error en login_vendedor: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/productos', methods=['GET'])
@token_required
def get_productos():
    """Obtener productos para la app Android"""
    try:
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
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
                    'stock': row[4],
                    'descripcion': row[5] if len(row) > 5 else ''
                })
        
        return jsonify({
            'success': True,
            'productos': productos,
            'total': len(productos)
        })
        
    except Exception as e:
        logger.error(f"Error en get_productos: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/registrar-venta-app', methods=['POST'])
@token_required
def registrar_venta_app():
    """Registrar venta desde la app Android"""
    try:
        data = request.json
        producto_id = data.get('producto_id')
        cantidad = data.get('cantidad')
        precio_unitario = data.get('precio_unitario')
        
        if not all([producto_id, cantidad, precio_unitario]):
            return jsonify({'success': False, 'message': 'Faltan datos: producto_id, cantidad y precio_unitario son requeridos'}), 400
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Verificar stock
        stock_query = "SELECT stock, nombre FROM productos WHERE id = %s" if is_postgres else "SELECT stock, nombre FROM productos WHERE id = ?"
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
        
        # Registrar venta
        insert_query = """
            INSERT INTO ventas (producto_id, cantidad, usuario_id) 
            VALUES (%s, %s, %s)
        """ if is_postgres else """
            INSERT INTO ventas (producto_id, cantidad, usuario_id) 
            VALUES (?, ?, ?)
        """
        db.execute_query(insert_query, (producto_id, cantidad, request.user_id))
        
        # Actualizar stock
        update_query = "UPDATE productos SET stock = stock - %s WHERE id = %s" if is_postgres else "UPDATE productos SET stock = stock - ? WHERE id = ?"
        db.execute_query(update_query, (cantidad, producto_id))
        
        total = cantidad * float(precio_unitario)
        
        return jsonify({
            'success': True,
            'message': f'Venta registrada: {cantidad} x {nombre_producto}',
            'venta': {
                'producto': nombre_producto,
                'producto_id': producto_id,
                'cantidad': cantidad,
                'precio_unitario': float(precio_unitario),
                'total': total
            },
            'stock_restante': stock_disponible - cantidad
        })
        
    except Exception as e:
        logger.error(f"Error en registrar_venta_app: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/dashboard-app', methods=['GET'])
@token_required
def dashboard_app():
    """Dashboard simplificado para la app Android"""
    try:
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Ventas de hoy
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
        
        # Productos con bajo stock (<= 5)
        if is_postgres:
            bajo_stock_query = """
                SELECT COUNT(*) FROM productos WHERE stock <= 5
            """
        else:
            bajo_stock_query = """
                SELECT COUNT(*) FROM productos WHERE stock <= 5
            """
        bajo_stock = db.execute_query(bajo_stock_query)
        productos_bajo_stock = bajo_stock[0][0] if bajo_stock else 0
        
        # Ventas del mes actual
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
        
        # Ventas recientes (últimas 5)
        if is_postgres:
            ventas_recientes_query = """
                SELECT p.nombre, v.cantidad, v.fecha, (v.cantidad * p.precio_venta) as total
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                ORDER BY v.fecha DESC
                LIMIT 5
            """
        else:
            ventas_recientes_query = """
                SELECT p.nombre, v.cantidad, v.fecha, (v.cantidad * p.precio_venta) as total
                FROM ventas v
                JOIN productos p ON v.producto_id = p.id
                ORDER BY v.fecha DESC
                LIMIT 5
            """
        
        ventas_recientes = db.execute_query(ventas_recientes_query)
        recientes = []
        if ventas_recientes:
            for row in ventas_recientes:
                recientes.append({
                    'producto': row[0],
                    'cantidad': row[1],
                    'fecha': row[2],
                    'total': float(row[3])
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
                'business_name': request.business_id
            }
        })
        
    except Exception as e:
        logger.error(f"Error en dashboard_app: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/ventas-app', methods=['GET'])
@token_required
def ventas_app():
    """Obtener historial de ventas para la app"""
    try:
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Obtener parámetros de paginación
        limite = request.args.get('limite', 50)
        offset = request.args.get('offset', 0)
        
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
                ORDER BY v.fecha DESC
                LIMIT %s OFFSET %s
            """
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
                ORDER BY v.fecha DESC
                LIMIT ? OFFSET ?
            """
        
        resultados = db.execute_query(query, (limite, offset))
        
        ventas = []
        if resultados:
            for row in resultados:
                ventas.append({
                    'id': row[0],
                    'producto': row[1],
                    'cantidad': row[2],
                    'precio_unitario': float(row[3]),
                    'total': float(row[4]),
                    'fecha': row[5]
                })
        
        # Contar total
        if is_postgres:
            count_query = "SELECT COUNT(*) FROM ventas"
        else:
            count_query = "SELECT COUNT(*) FROM ventas"
        
        count_result = db.execute_query(count_query)
        total = count_result[0][0] if count_result else 0
        
        return jsonify({
            'success': True,
            'ventas': ventas,
            'total': total,
            'limite': int(limite),
            'offset': int(offset)
        })
        
    except Exception as e:
        logger.error(f"Error en ventas_app: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/perfil-vendedor', methods=['GET'])
@token_required
def perfil_vendedor():
    """Obtener perfil del vendedor"""
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
                SELECT u.id, u.username, u.role, b.name as business_name, b.email
                FROM users u
                JOIN businesses b ON u.business_id = b.id
                WHERE u.id = %s
            """, (request.user_id,))
        else:
            c.execute("""
                SELECT u.id, u.username, u.role, b.name as business_name, b.email
                FROM users u
                JOIN businesses b ON u.business_id = b.id
                WHERE u.id = ?
            """, (request.user_id,))
        
        user_data = c.fetchone()
        if not user_data:
            return jsonify({'success': False, 'message': 'Usuario no encontrado'}), 404
        
        return jsonify({
            'success': True,
            'usuario': {
                'id': user_data[0],
                'username': user_data[1],
                'role': user_data[2],
                'business_name': user_data[3],
                'email': user_data[4] if len(user_data) > 4 else ''
            }
        })
        
    except Exception as e:
        logger.error(f"Error en perfil_vendedor: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

# ==================== LOGOUT ====================

@app.route('/logout')
def logout():
    session.clear()
    return jsonify({'success': True, 'message': 'Sesión cerrada'})

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
