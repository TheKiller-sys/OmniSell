# run.py - Script para ejecutar localmente

import os
from app import app, socketio

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 10000))
    print(f"🚀 Iniciando OmniVentas en http://localhost:{port}")
    print(f"📱 API disponible en http://localhost:{port}/api/")
    print(f"🔑 Endpoints de autenticación: /api/login-vendedor")
    socketio.run(app, host='0.0.0.0', port=port, debug=True, use_reloader=True)
