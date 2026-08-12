# app.py - Aplicación principal CON endpoint para vendedores
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
            request.user_id = payload.get('user_id')
            request.vendor_id = payload.get('vendor_id')
            request.business_id = payload['business_id']
            request.username = payload.get('username')
            request.vendor_name = payload.get('name')
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

# ==================== NUEVO ENDPOINT: LOGIN DE VENDEDOR ====================

@app.route('/api/login-vendedor', methods=['POST'])
def login_vendedor():
    """Login para vendedores con ID de 8 caracteres"""
    try:
        data = request.json
        vendor_id = data.get('vendor_id', '').strip()
        
        # Validar formato del ID (8 caracteres alfanuméricos)
        if not vendor_id:
            return jsonify({
                'success': False, 
                'message': 'ID de vendedor requerido'
            }), 400
        
        if len(vendor_id) != 8:
            return jsonify({
                'success': False, 
                'message': 'El ID debe tener exactamente 8 caracteres'
            }), 400
        
        if not vendor_id.isalnum():
            return jsonify({
                'success': False, 
                'message': 'El ID solo debe contener letras y números'
            }), 400
        
        from database.db_manager import DatabaseManager
        DatabaseManager.verify_and_fix_global_tables()
        conn = DatabaseManager.get_global_connection()
        
        if conn is None:
            return jsonify({'success': False, 'message': 'Error de conexión'}), 500
        
        c = conn.cursor()
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Buscar vendedor por ID
        if is_postgres:
            c.execute("""
                SELECT v.id, v.name, v.business_id, b.name as business_name, v.role, v.active
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                WHERE v.id = %s
            """, (vendor_id,))
        else:
            c.execute("""
                SELECT v.id, v.name, v.business_id, b.name as business_name, v.role, v.active
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                WHERE v.id = ?
            """, (vendor_id,))
        
        vendor_data = c.fetchone()
        
        if not vendor_data:
            return jsonify({
                'success': False, 
                'message': 'ID de vendedor no encontrado'
            }), 401
        
        # Verificar si el vendedor está activo
        if not vendor_data[5]:
            return jsonify({
                'success': False, 
                'message': 'Este vendedor está desactivado. Contacta al administrador.'
            }), 401
        
        # Generar token JWT
        token = jwt.encode({
            'vendor_id': vendor_data[0],
            'business_id': vendor_data[2],
            'name': vendor_data[1],
            'role': vendor_data[4],
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=7)
        }, os.environ.get('JWT_SECRET', 'secret-key'), algorithm='HS256')
        
        return jsonify({
            'success': True,
            'token': token,
            'vendor': {
                'id': vendor_data[0],
                'name': vendor_data[1],
                'business_id': vendor_data[2],
                'business_name': vendor_data[3],
                'role': vendor_data[4]
            }
        })
        
    except Exception as e:
        logger.error(f"Error en login_vendedor: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

# ==================== API PARA LA APP ANDROID ====================

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
        
        # Registrar venta (usar vendor_id si existe, sino user_id)
        usuario_id = request.vendor_id if hasattr(request, 'vendor_id') and request.vendor_id else request.user_id
        
        insert_query = """
            INSERT INTO ventas (producto_id, cantidad, usuario_id) 
            VALUES (%s, %s, %s)
        """ if is_postgres else """
            INSERT INTO ventas (producto_id, cantidad, usuario_id) 
            VALUES (?, ?, ?)
        """
        db.execute_query(insert_query, (producto_id, cantidad, usuario_id))
        
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
        bajo_stock_query = "SELECT COUNT(*) FROM productos WHERE stock <= 5"
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
                SELECT v.id, v.name, v.business_id, b.name as business_name, v.role
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                WHERE v.id = %s
            """, (request.vendor_id,))
        else:
            c.execute("""
                SELECT v.id, v.name, v.business_id, b.name as business_name, v.role
                FROM vendors v
                JOIN businesses b ON v.business_id = b.id
                WHERE v.id = ?
            """, (request.vendor_id,))
        
        vendor_data = c.fetchone()
        if not vendor_data:
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
        return jsonify({'success': False, 'message': str(e)}), 500

# ==================== ENDPOINTS PARA GESTIÓN DE VENDEDORES (ADMIN) ====================

@app.route('/api/vendedores', methods=['GET'])
@token_required
def get_vendedores():
    """Obtener lista de vendedores del negocio"""
    try:
        if request.role != 'admin':
            return jsonify({'success': False, 'message': 'Solo administradores pueden ver vendedores'}), 403
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
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
        
        resultados = db.execute_query(query, (request.business_id,))
        
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
        logger.error(f"Error en get_vendedores: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/vendedor', methods=['POST'])
@token_required
def crear_vendedor():
    """Crear un nuevo vendedor"""
    try:
        if request.role != 'admin':
            return jsonify({'success': False, 'message': 'Solo administradores pueden crear vendedores'}), 403
        
        data = request.json
        name = data.get('name', '').strip()
        
        if not name:
            return jsonify({'success': False, 'message': 'El nombre es requerido'}), 400
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        # Generar ID único de 8 caracteres
        import random
        import string
        def generate_vendor_id():
            characters = string.ascii_uppercase + string.digits
            return ''.join(random.choices(characters, k=8))
        
        vendor_id = generate_vendor_id()
        
        # Verificar que el ID no exista ya
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
        
        # Insertar vendedor
        if is_postgres:
            db.execute_query("""
                INSERT INTO vendors (id, name, business_id, role, active)
                VALUES (%s, %s, %s, %s, %s)
            """, (vendor_id, name, request.business_id, 'vendedor', True))
        else:
            db.execute_query("""
                INSERT INTO vendors (id, name, business_id, role, active)
                VALUES (?, ?, ?, ?, ?)
            """, (vendor_id, name, request.business_id, 'vendedor', 1))
        
        return jsonify({
            'success': True,
            'message': 'Vendedor creado correctamente',
            'vendor_id': vendor_id,
            'vendor_name': name
        })
        
    except Exception as e:
        logger.error(f"Error en crear_vendedor: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/vendedor/<vendor_id>', methods=['PUT'])
@token_required
def actualizar_vendedor(vendor_id):
    """Actualizar un vendedor (activar/desactivar)"""
    try:
        if request.role != 'admin':
            return jsonify({'success': False, 'message': 'Solo administradores pueden actualizar vendedores'}), 403
        
        data = request.json
        active = data.get('active')
        name = data.get('name')
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
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
        params.append(request.business_id)
        
        if is_postgres:
            query = f"UPDATE vendors SET {', '.join(updates)} WHERE id = %s AND business_id = %s"
        else:
            query = f"UPDATE vendors SET {', '.join(updates)} WHERE id = ? AND business_id = ?"
        
        db.execute_query(query, tuple(params))
        
        return jsonify({
            'success': True,
            'message': 'Vendedor actualizado correctamente'
        })
        
    except Exception as e:
        logger.error(f"Error en actualizar_vendedor: {e}")
        return jsonify({'success': False, 'message': str(e)}), 500

@app.route('/api/vendedor/<vendor_id>', methods=['DELETE'])
@token_required
def eliminar_vendedor(vendor_id):
    """Eliminar un vendedor"""
    try:
        if request.role != 'admin':
            return jsonify({'success': False, 'message': 'Solo administradores pueden eliminar vendedores'}), 403
        
        from database.db_manager import DatabaseManager
        db = DatabaseManager(request.business_id)
        is_postgres = 'RENDER' in os.environ and os.environ.get('DATABASE_URL')
        
        if is_postgres:
            db.execute_query("DELETE FROM vendors WHERE id = %s AND business_id = %s", (vendor_id, request.business_id))
        else:
            db.execute_query("DELETE FROM vendors WHERE id = ? AND business_id = ?", (vendor_id, request.business_id))
        
        return jsonify({
            'success': True,
            'message': 'Vendedor eliminado correctamente'
        })
        
    except Exception as e:
        logger.error(f"Error en eliminar_vendedor: {e}")
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
