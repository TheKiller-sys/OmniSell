/**
 * OmniVentas - Tutorial Profesional
 * Guía interactiva detallada de toda la plataforma
 * 
 * Características:
 * - Explicación detallada de cada funcionalidad
 * - Diseño premium con animaciones
 * - Navegación fluida entre pasos
 * - Persistencia del progreso
 * - Botón "Ver Tutorial" en todas las páginas
 * 
 * Uso:
 *   OmniTutorial.start()  - Iniciar el tutorial
 *   OmniTutorial.reset()  - Reiniciar el tutorial
 *   OmniTutorial.skip()   - Saltar el tutorial
 */

(function() {
    'use strict';

    // ============================================================
    // CONFIGURACIÓN DE PASOS POR PÁGINA - MUY DETALLADA
    // ============================================================
    const TUTORIAL_STEPS = {
        '/dashboard': [
            {
                target: '.section-header-wrapper',
                title: '🚀 Bienvenido al Centro de Control de tu Negocio',
                description: `
                    <strong>Este es tu Dashboard personalizado.</strong><br><br>
                    Aquí tienes una visión panorámica del estado de tu negocio en tiempo real. 
                    Todo lo que necesitas saber está a un vistazo.<br><br>
                    
                    <div class="info-box">
                        <span class="info-icon">💡</span>
                        <div>
                            <strong>¿Qué puedes hacer aquí?</strong><br>
                            • Monitorear ingresos y ganancias al instante<br>
                            • Ver el rendimiento de tus ventas<br>
                            • Controlar el estado de tu inventario<br>
                            • Tomar decisiones basadas en datos reales
                        </div>
                    </div>
                    
                    <div class="tip-box">
                        <span class="tip-icon">🎯</span>
                        <div>
                            <strong>Consejo Pro:</strong> Revisa este panel cada mañana para empezar el día 
                            con una visión clara de tu negocio.
                        </div>
                    </div>
                `,
                page: '/dashboard'
            },
            {
                target: '.stats-row',
                title: '📊 Tus Métricas Vitales - El Termómetro de tu Negocio',
                description: `
                    <strong>Estas 4 tarjetas son el corazón financiero de tu negocio.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">💰 Ingresos Totales:</span>
                            El dinero total que ha entrado en tu negocio. Es tu volumen de negocio.
                        </li>
                        <li>
                            <span class="label text-green">📈 Ganancia Neta:</span>
                            Lo que realmente ganas después de restar costos. ¡Tu verdadero beneficio!
                        </li>
                        <li>
                            <span class="label text-purple">📊 Margen Promedio:</span>
                            El porcentaje de ganancia que obtienes por cada venta. Ideal para fijar precios.
                        </li>
                        <li>
                            <span class="label text-orange">🛒 Total Ventas:</span>
                            La cantidad de transacciones realizadas. Mide el movimiento de tu negocio.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">📌</span>
                        <div>
                            <strong>Interpretación:</strong> Si tus ingresos suben pero tu ganancia baja, 
                            revisa tus costos. Si el margen es bajo, considera ajustar precios.
                        </div>
                    </div>
                `,
                page: '/dashboard'
            },
            {
                target: '#salesChart',
                title: '📊 Análisis Visual de Ventas - Toma Decisiones con Datos',
                description: `
                    <strong>Este gráfico es tu mejor aliado para entender el comportamiento de tu negocio.</strong><br><br>
                    
                    <strong>Filtros disponibles:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">📅 Mensual:</span>
                            Evolución mes a mes. Ideal para ver tendencias estacionales.
                        </li>
                        <li>
                            <span class="label">📅 Semanal:</span>
                            Detecta semanas buenas y malas. Perfecto para planificar promociones.
                        </li>
                        <li>
                            <span class="label">📅 Diario:</span>
                            Identifica qué días de la semana venden más. Optimiza tu personal.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">🔍</span>
                        <div>
                            <strong>¿Cómo usarlo?</strong><br>
                            Haz clic en los botones (Mensual/Semanal/Diario) para cambiar la vista. 
                            Encuentra patrones y anticipa tus movimientos.
                        </div>
                    </div>
                `,
                page: '/dashboard'
            },
            {
                target: '#inventoryTable',
                title: '📦 Inventario Inteligente - Controla tu Stock al Detalle',
                description: `
                    <strong>Gestiona tu inventario de forma profesional.</strong><br><br>
                    
                    <strong>Sistema de alertas automáticas:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="text-green">🟢 <span class="label">Óptimo:</span></span>
                            Stock saludable. No necesitas hacer nada.
                        </li>
                        <li>
                            <span class="text-orange">🟡 <span class="label">Atención:</span></span>
                            Quedan 3 o menos unidades. ¡Es momento de reabastecer!
                        </li>
                        <li>
                            <span class="text-red">🔴 <span class="label">Crítico:</span></span>
                            Producto agotado. Pérdida de ventas potencial.
                        </li>
                    </ul>
                    
                    <div class="warning-box">
                        <span class="warning-icon">⚠️</span>
                        <div>
                            <strong>¡Cuidado!</strong> Un producto en estado "Crítico" significa que 
                            estás perdiendo oportunidades de venta. Reabastece lo antes posible.
                        </div>
                    </div>
                `,
                page: '/dashboard'
            },
            {
                target: '.header-actions',
                title: '🎯 Navegación Principal - Accede a Todo tu Sistema',
                description: `
                    <strong>Desde aquí puedes acceder a todas las herramientas de gestión.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label">📊 Dashboard:</span>
                            El panel de control que estás viendo ahora.
                        </li>
                        <li>
                            <span class="label">🛒 Ventas:</span>
                            Registra y gestiona todas tus transacciones.
                        </li>
                        <li>
                            <span class="label">📦 Inventario:</span>
                            Controla tu stock, precios y margen de productos.
                        </li>
                        <li>
                            <span class="label">👥 Vendedores:</span>
                            Administra tu equipo de ventas.
                        </li>
                        <li>
                            <span class="label">💰 Finanzas:</span>
                            Analiza ingresos, gastos y rentabilidad.
                        </li>
                        <li>
                            <span class="label">🧠 Análisis:</span>
                            Descubre productos estrella y tendencias.
                        </li>
                        <li>
                            <span class="label">👤 Clientes:</span>
                            Gestiona tu cartera de clientes.
                        </li>
                        <li>
                            <span class="label">⚙️ Configuración:</span>
                            Personaliza tu negocio y seguridad.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">🚀</span>
                        <div>
                            <strong>Tip:</strong> Usa el menú lateral para moverte rápidamente entre 
                            secciones. Cada módulo está diseñado para una gestión eficiente.
                        </div>
                    </div>
                `,
                page: '/dashboard'
            }
        ],
        '/ventas': [
            {
                target: '.section-header-wrapper',
                title: '🛒 Gestión de Ventas - Tu Caja Registradora Digital',
                description: `
                    <strong>El corazón operativo de tu negocio.</strong><br><br>
                    Aquí registras, gestionas y analizas todas tus transacciones.<br><br>
                    
                    <strong>Funcionalidades clave:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">📝 Registrar Venta:</span>
                            Selecciona producto, cantidad y precio. El sistema valida stock.
                        </li>
                        <li>
                            <span class="label">📅 Filtros de Fecha:</span>
                            Control temporal preciso para ver períodos específicos.
                        </li>
                        <li>
                            <span class="label">📊 Exportar a Excel:</span>
                            Descarga tu historial completo para análisis externos.
                        </li>
                        <li>
                            <span class="label">🔍 Búsqueda de Productos:</span>
                            Encuentra rápidamente ventas específicas.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📌</span>
                        <div>
                            <strong>Sabías que...</strong> Cada venta registrada actualiza automáticamente 
                            tu inventario y finanzas. ¡Todo en tiempo real!
                        </div>
                    </div>
                `,
                page: '/ventas'
            },
            {
                target: '.filter-group',
                title: '🔍 Filtros Avanzados - Encuentra lo que Buscas',
                description: `
                    <strong>Localiza cualquier venta en segundos.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label">📅 Fecha Desde - Fecha Hasta:</span>
                            Filtra por rango de fechas. Útil para informes mensuales o trimestrales.
                        </li>
                        <li>
                            <span class="label">🔎 Buscar Producto:</span>
                            Escribe el nombre de un producto y verás todas sus ventas.
                        </li>
                        <li>
                            <span class="label">🧹 Limpiar Filtros:</span>
                            Reinicia todos los filtros con un solo clic.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">💡</span>
                        <div>
                            <strong>Uso recomendado:</strong> Si quieres saber cuánto vendiste de un producto 
                            específico en el último mes, usa el filtro de producto y las fechas.
                        </div>
                    </div>
                `,
                page: '/ventas'
            },
            {
                target: '.stat-card',
                title: '📊 Estadísticas de Ventas - Métricas Clave al Instante',
                description: `
                    <strong>Las 4 métricas que todo negocio debe monitorear.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">📊 Total Ventas:</span>
                            Número total de transacciones realizadas. Mide el volumen de tu negocio.
                        </li>
                        <li>
                            <span class="label text-green">💰 Ingresos:</span>
                            Dinero total facturado. El tamaño de tu negocio.
                        </li>
                        <li>
                            <span class="label text-purple">📈 Ganancia:</span>
                            Beneficio neto después de costos. Tu verdadera rentabilidad.
                        </li>
                        <li>
                            <span class="label text-orange">🎫 Ticket Promedio:</span>
                            Valor medio de cada venta. Ideal para estrategias de upselling.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📈</span>
                        <div>
                            <strong>Interpretación:</strong> Si tu ticket promedio es bajo, considera 
                            ofrecer combos o productos complementarios para aumentar el valor de cada venta.
                        </div>
                    </div>
                `,
                page: '/ventas'
            },
            {
                target: '#btn-registrar-venta',
                title: '➕ Registrar Nueva Venta - El Proceso Completo',
                description: `
                    <strong>Agrega ventas de forma rápida y profesional.</strong><br><br>
                    
                    <strong>Pasos para registrar una venta:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">1. Selecciona el Producto:</span>
                            Elige de tu catálogo. Verás el stock disponible.
                        </li>
                        <li>
                            <span class="label">2. Ingresa la Cantidad:</span>
                            El sistema te mostrará el stock disponible.
                        </li>
                        <li>
                            <span class="label">3. Confirma el Precio:</span>
                            Puedes ajustar el precio si es necesario.
                        </li>
                        <li>
                            <span class="label">4. ¡Listo!</span>
                            La venta se registra y el stock se actualiza automáticamente.
                        </li>
                    </ul>
                    
                    <div class="success-box">
                        <span class="success-icon">✅</span>
                        <div>
                            <strong>¡Importante!</strong> El sistema valida que haya stock suficiente 
                            antes de registrar la venta. ¡Sin errores!
                        </div>
                    </div>
                `,
                page: '/ventas'
            }
        ],
        '/inventario': [
            {
                target: '.section-header-wrapper',
                title: '📦 Gestión de Inventario - El Corazón de tu Negocio',
                description: `
                    <strong>Control total sobre tus productos y stock.</strong><br><br>
                    Tu inventario es el activo más importante de tu negocio. Aquí lo gestionas al detalle.<br><br>
                    
                    <strong>Acciones disponibles:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">➕ Agregar Producto:</span>
                            Añade nuevos productos a tu catálogo.
                        </li>
                        <li>
                            <span class="label text-orange">✏️ Editar Producto:</span>
                            Actualiza precios, stock y secciones.
                        </li>
                        <li>
                            <span class="label text-red">🗑️ Eliminar Producto:</span>
                            Da de baja productos que ya no vendes.
                        </li>
                        <li>
                            <span class="label text-green">📊 Ver Margen:</span>
                            Cada producto muestra su rentabilidad.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">📌</span>
                        <div>
                            <strong>Consejo:</strong> Revisa tu inventario semanalmente para identificar 
                            productos con bajo rendimiento y tomar decisiones a tiempo.
                        </div>
                    </div>
                `,
                page: '/inventario'
            },
            {
                target: '.stat-card',
                title: '📊 Estado del Inventario - Visión Panorámica',
                description: `
                    <strong>Métricas clave de tu stock.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">📦 Total Productos:</span>
                            El tamaño de tu catálogo completo.
                        </li>
                        <li>
                            <span class="label text-green">💰 Valor del Inventario:</span>
                            La inversión total en stock. ¡Sabes cuánto tienes invertido!
                        </li>
                        <li>
                            <span class="label text-orange">⚠️ Stock Bajo (≤3):</span>
                            Productos que necesitan reabastecimiento urgente.
                        </li>
                        <li>
                            <span class="label text-red">🚫 Sin Stock:</span>
                            Productos agotados. Estás perdiendo ventas.
                        </li>
                    </ul>
                    
                    <div class="warning-box">
                        <span class="warning-icon">🚨</span>
                        <div>
                            <strong>¡Alerta!</strong> Los productos en "Stock Bajo" y "Sin Stock" 
                            representan oportunidades de venta perdidas. ¡Reabastece cuanto antes!
                        </div>
                    </div>
                `,
                page: '/inventario'
            },
            {
                target: '#btn-agregar',
                title: '➕ Agregar Productos - Amplía tu Catálogo',
                description: `
                    <strong>Registra nuevos productos de forma sencilla.</strong><br><br>
                    
                    <strong>Datos que necesitas:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">📝 Nombre y Sección:</span>
                            Identifica y organiza tu producto. Ej: "Camiseta" en "Ropa".
                        </li>
                        <li>
                            <span class="label">💰 Precio de Venta:</span>
                            El precio al que vendes al cliente.
                        </li>
                        <li>
                            <span class="label">📉 Precio de Compra:</span>
                            El costo que pagas por el producto.
                        </li>
                        <li>
                            <span class="label">📦 Stock Inicial:</span>
                            La cantidad con la que empiezas.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📊</span>
                        <div>
                            <strong>Margen automático:</strong> El sistema calcula el margen de ganancia 
                            automáticamente con el precio de venta y compra. ¡Sin cálculos manuales!
                        </div>
                    </div>
                    
                    <div class="success-box">
                        <span class="success-icon">💡</span>
                        <div>
                            <strong>Tip Pro:</strong> Si tu margen es bajo (< 20%), considera 
                            renegociar con proveedores o ajustar tus precios.
                        </div>
                    </div>
                `,
                page: '/inventario'
            }
        ],
        '/finanzas': [
            {
                target: '.section-header-wrapper',
                title: '💰 Análisis Financiero - La Brújula de tu Negocio',
                description: `
                    <strong>Entiende la salud financiera de tu negocio.</strong><br><br>
                    Las finanzas son el termómetro de tu negocio. Aquí ves si realmente estás ganando dinero.<br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label">📊 Ingresos vs Gastos:</span>
                            Visualiza si estás ganando más de lo que gastas.
                        </li>
                        <li>
                            <span class="label">🥧 Distribución de Gastos:</span>
                            ¿En qué se va tu dinero? Identifica áreas de mejora.
                        </li>
                        <li>
                            <span class="label">📅 Resumen Mensual:</span>
                            Evolución mes a mes de tu rendimiento financiero.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📌</span>
                        <div>
                            <strong>La regla de oro:</strong> Si tus ingresos son mayores que tus gastos, 
                            tu negocio es rentable. Si no, es momento de hacer ajustes.
                        </div>
                    </div>
                `,
                page: '/finanzas'
            },
            {
                target: '.stat-card',
                title: '📊 Resumen Financiero - Tus Números Clave',
                description: `
                    <strong>Las 3 métricas financieras más importantes.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">📈 Ingresos Mensuales:</span>
                            Todo el dinero que ha entrado en el mes. Tu facturación.
                        </li>
                        <li>
                            <span class="label text-red">📉 Gastos Mensuales:</span>
                            Todo el dinero que ha salido. Tus costos operativos.
                        </li>
                        <li>
                            <span class="label text-green">💰 Beneficio Neto:</span>
                            Lo que realmente ganas. ¡La cifra más importante!
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">🎯</span>
                        <div>
                            <strong>Interpretación:</strong> Un beneficio neto positivo significa que 
                            tu negocio es rentable. Si es negativo, necesitas revisar tus gastos o aumentar ingresos.
                        </div>
                    </div>
                `,
                page: '/finanzas'
            },
            {
                target: '#finanzasChart',
                title: '📈 Evolución Financiera - Visualiza tu Progreso',
                description: `
                    <strong>Compara ingresos y gastos mes a mes.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">🔵 Barras Azules:</span>
                            Tus ingresos mensuales. La altura muestra cuánto ganaste.
                        </li>
                        <li>
                            <span class="label text-red">🔴 Barras Rojas:</span>
                            Tus gastos mensuales. La altura muestra cuánto gastaste.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📊</span>
                        <div>
                            <strong>Interpretación:</strong> Si las barras azules son más altas que las rojas, 
                            ¡estás ganando dinero! Si son más bajas, necesitas revisar tu estructura de costos.
                        </div>
                    </div>
                    
                    <div class="success-box">
                        <span class="success-icon">💡</span>
                        <div>
                            <strong>Tip:</strong> Usa este gráfico para identificar meses de alta y baja 
                            temporada. Planifica tus inversiones en consecuencia.
                        </div>
                    </div>
                `,
                page: '/finanzas'
            }
        ],
        '/analisis': [
            {
                target: '.section-header-wrapper',
                title: '🧠 Análisis Avanzado - Toma Decisiones Basadas en Datos',
                description: `
                    <strong>Descubre patrones y oportunidades en tu negocio.</strong><br><br>
                    El análisis avanzado te ayuda a entender qué funciona y qué no en tu negocio.<br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-orange">🏆 Top 10 Productos:</span>
                            Identifica tus "vacas sagradas". Los productos que más venden.
                        </li>
                        <li>
                            <span class="label text-blue">📈 Tendencia de Ventas:</span>
                            ¿Tu negocio está creciendo? Visualiza la evolución.
                        </li>
                        <li>
                            <span class="label text-purple">🏷️ Clasificación ABC:</span>
                            Prioriza estratégicamente tus productos.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">💡</span>
                        <div>
                            <strong>Consejo:</strong> Revisa este análisis semanalmente para 
                            identificar tendencias tempranas y tomar decisiones proactivas.
                        </div>
                    </div>
                `,
                page: '/analisis'
            },
            {
                target: '#topProductosChart',
                title: '🏆 Top 10 Productos Más Vendidos - Tus Estrellas',
                description: `
                    <strong>Identifica los productos que mueven tu negocio.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label">📊 Visualización Horizontal:</span>
                            Barras horizontales que muestran claramente la cantidad de ventas.
                        </li>
                        <li>
                            <span class="label">🔝 Ranking:</span>
                            Los productos están ordenados de mayor a menor ventas.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📌</span>
                        <div>
                            <strong>¿Por qué es importante?</strong> Estos productos son tu principal fuente 
                            de ingresos. Asegúrate de tener siempre stock de ellos.
                        </div>
                    </div>
                    
                    <div class="success-box">
                        <span class="success-icon">💡</span>
                        <div>
                            <strong>Estrategia:</strong> Promociona estos productos en redes sociales y 
                            ofrécelos como complementos en otras ventas.
                        </div>
                    </div>
                `,
                page: '/analisis'
            },
            {
                target: '#tendenciaChart',
                title: '📈 Tendencia de Ventas - ¿Estás Creciendo?',
                description: `
                    <strong>Visualiza la evolución de tus ventas en el tiempo.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label">📈 Línea de Tendencia:</span>
                            Muestra la evolución mes a mes de tus ventas.
                        </li>
                        <li>
                            <span class="label">📊 Picos y Caídas:</span>
                            Identifica meses de alta y baja temporada.
                        </li>
                        <li>
                            <span class="label">🔮 Proyección:</span>
                            Usa esta información para anticipar tendencias futuras.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">📌</span>
                        <div>
                            <strong>Interpretación:</strong> Si la línea sube, tu negocio está creciendo. 
                            Si baja, es momento de analizar qué está pasando y tomar acción.
                        </div>
                    </div>
                    
                    <div class="info-box">
                        <span class="info-icon">📊</span>
                        <div>
                            <strong>Uso estratégico:</strong> Identifica tus meses fuertes para planificar 
                            campañas de marketing y tus meses débiles para preparar promociones.
                        </div>
                    </div>
                `,
                page: '/analisis'
            }
        ],
        '/clientes': [
            {
                target: '.section-header-wrapper',
                title: '👤 Gestión de Clientes - Tu Base de Datos de Oro',
                description: `
                    <strong>Los clientes son el alma de tu negocio. Aquí los gestionas.</strong><br><br>
                    
                    <strong>Funcionalidades principales:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">➕ Agregar Cliente:</span>
                            Registra nuevos clientes con sus datos de contacto.
                        </li>
                        <li>
                            <span class="label text-green">📊 Top Clientes:</span>
                            Identifica a tus mejores clientes por volumen de compras.
                        </li>
                        <li>
                            <span class="label text-purple">📈 Nuevos Clientes:</span>
                            Visualiza el crecimiento de tu base de clientes.
                        </li>
                        <li>
                            <span class="label text-orange">📝 Historial de Compras:</span>
                            Cada cliente tiene su historial de transacciones.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">💡</span>
                        <div>
                            <strong>Consejo:</strong> Un cliente existente es más fácil de vender que 
                            conseguir uno nuevo. Mantén una relación cercana con tus mejores clientes.
                        </div>
                    </div>
                `,
                page: '/clientes'
            },
            {
                target: '#clientesChart',
                title: '📈 Nuevos Clientes por Mes - Mide tu Crecimiento',
                description: `
                    <strong>Visualiza cómo crece tu base de clientes.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label">📊 Línea de Crecimiento:</span>
                            Muestra cuántos clientes nuevos registras cada mes.
                        </li>
                        <li>
                            <span class="label">📈 Picos de Registro:</span>
                            Identifica meses con mayor captación de clientes.
                        </li>
                        <li>
                            <span class="label">🔮 Proyección de Crecimiento:</span>
                            Usa estos datos para planificar tu estrategia comercial.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📌</span>
                        <div>
                            <strong>Interpretación:</strong> Si ves meses con pocos registros, 
                            considera lanzar promociones o campañas de captación de clientes.
                        </div>
                    </div>
                `,
                page: '/clientes'
            },
            {
                target: '#btn-agregar-cliente',
                title: '➕ Agregar Cliente - Amplía tu Cartera',
                description: `
                    <strong>Registra nuevos clientes de forma rápida.</strong><br><br>
                    
                    <strong>Datos que puedes registrar:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">📝 Nombre Completo:</span>
                            Obligatorio. Identifica a tu cliente.
                        </li>
                        <li>
                            <span class="label">📧 Email:</span>
                            Para futuras comunicaciones y promociones.
                        </li>
                        <li>
                            <span class="label">📞 Teléfono:</span>
                            Contacto directo para seguimiento.
                        </li>
                        <li>
                            <span class="label">📍 Dirección:</span>
                            Para envíos o visitas a domicilio.
                        </li>
                    </ul>
                    
                    <div class="success-box">
                        <span class="success-icon">💡</span>
                        <div>
                            <strong>Tip Pro:</strong> Un cliente bien registrado es un cliente que 
                            volverá a comprar. Mantén tus datos actualizados.
                        </div>
                    </div>
                `,
                page: '/clientes'
            }
        ],
        '/vendedores': [
            {
                target: '.section-header-wrapper',
                title: '👥 Gestión de Vendedores - Administra tu Equipo',
                description: `
                    <strong>Controla y gestiona a tu fuerza de ventas.</strong><br><br>
                    
                    <strong>Funcionalidades clave:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">➕ Crear Vendedor:</span>
                            Genera un ID único de 8 caracteres para cada vendedor.
                        </li>
                        <li>
                            <span class="label text-orange">🔑 ID de Acceso:</span>
                            Cada vendedor usa su ID para iniciar sesión en la App Android.
                        </li>
                        <li>
                            <span class="label text-green">🔄 Cambiar Estado:</span>
                            Activa o desactiva vendedores según necesidad.
                        </li>
                        <li>
                            <span class="label text-red">🗑️ Eliminar Vendedor:</span>
                            Da de baja vendedores que ya no trabajan contigo.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">📌</span>
                        <div>
                            <strong>Importante:</strong> Cada vendedor tiene acceso independiente 
                            a la app. Pueden registrar ventas desde sus teléfonos.
                        </div>
                    </div>
                `,
                page: '/vendedores'
            },
            {
                target: '.stat-card',
                title: '📊 Resumen del Equipo - Visión Rápida de tu Fuerza de Ventas',
                description: `
                    <strong>Métricas clave de tu equipo.</strong><br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">👥 Total Vendedores:</span>
                            El tamaño completo de tu equipo de ventas.
                        </li>
                        <li>
                            <span class="label text-green">✅ Activos:</span>
                            Vendedores que están operativos y pueden registrar ventas.
                        </li>
                        <li>
                            <span class="label text-red">❌ Inactivos:</span>
                            Vendedores suspendidos. No pueden acceder a la app.
                        </li>
                    </ul>
                    
                    <div class="tip-box">
                        <span class="tip-icon">📌</span>
                        <div>
                            <strong>Interpretación:</strong> Mantén un equipo activo según tu 
                            volumen de ventas. Desactiva vendedores en temporada baja y actívalos 
                            en temporada alta.
                        </div>
                    </div>
                `,
                page: '/vendedores'
            },
            {
                target: '#btn-agregar-vendedor',
                title: '➕ Crear Vendedor - Amplía tu Equipo',
                description: `
                    <strong>Agrega nuevos vendedores en segundos.</strong><br><br>
                    
                    <strong>Proceso de creación:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">1. Ingresa el Nombre:</span>
                            El nombre completo del vendedor.
                        </li>
                        <li>
                            <span class="label">2. Sistema Genera ID:</span>
                            El ID es único de 8 caracteres alfanuméricos.
                        </li>
                        <li>
                            <span class="label">3. Comparte el ID:</span>
                            El vendedor usa este ID en la app para iniciar sesión.
                        </li>
                        <li>
                            <span class="label">4. ¡Listo!</span>
                            El vendedor ya puede comenzar a registrar ventas.
                        </li>
                    </ul>
                    
                    <div class="success-box">
                        <span class="success-icon">✅</span>
                        <div>
                            <strong>¡Importante!</strong> El ID generado es único y no se repite. 
                            Es como una huella digital para cada vendedor.
                        </div>
                    </div>
                `,
                page: '/vendedores'
            }
        ],
        '/configuracion': [
            {
                target: '.section-header-wrapper',
                title: '⚙️ Configuración - Personaliza tu Experiencia',
                description: `
                    <strong>Ajusta y personaliza tu sistema.</strong><br><br>
                    La configuración te permite adaptar OmniVentas a las necesidades específicas de tu negocio.<br><br>
                    
                    <ul class="feature-list">
                        <li>
                            <span class="label text-blue">📋 Datos del Negocio:</span>
                            Actualiza el nombre, email, teléfono y dirección de tu negocio.
                        </li>
                        <li>
                            <span class="label text-orange">🔐 Seguridad:</span>
                            Cambia tu contraseña de acceso para mantener tu cuenta segura.
                        </li>
                        <li>
                            <span class="label text-red">⚠️ Zona de Peligro:</span>
                            Acciones irreversibles como eliminar datos o la cuenta completa.
                        </li>
                    </ul>
                    
                    <div class="info-box">
                        <span class="info-icon">🔒</span>
                        <div>
                            <strong>Seguridad ante todo:</strong> Recomendamos cambiar tu contraseña 
                            cada 3 meses y usar una contraseña única y segura.
                        </div>
                    </div>
                `,
                page: '/configuracion'
            },
            {
                target: '#config-form',
                title: '📋 Datos del Negocio - Mantén tu Información Actualizada',
                description: `
                    <strong>Actualiza los datos de tu negocio.</strong><br><br>
                    
                    <strong>Campos que puedes editar:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">🏪 Nombre del Negocio:</span>
                            Aparece en el dashboard y en la app de vendedores.
                        </li>
                        <li>
                            <span class="label">📧 Email:</span>
                            Para recibir notificaciones importantes y comunicaciones.
                        </li>
                        <li>
                            <span class="label">📞 Teléfono:</span>
                            Contacto directo para clientes y proveedores.
                        </li>
                        <li>
                            <span class="label">📍 Dirección:</span>
                            Ubicación de tu negocio para referencias.
                        </li>
                    </ul>
                    
                    <div class="success-box">
                        <span class="success-icon">💡</span>
                        <div>
                            <strong>Consejo:</strong> Mantén estos datos siempre actualizados para 
                            que tus clientes y vendedores tengan la información correcta.
                        </div>
                    </div>
                `,
                page: '/configuracion'
            },
            {
                target: '#btn-cambiar-pass',
                title: '🔐 Seguridad - Cambia tu Contraseña',
                description: `
                    <strong>Mantén tu cuenta segura.</strong><br><br>
                    
                    <strong>Requisitos para una contraseña segura:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label">🔒 Mínimo 8 caracteres:</span>
                            Longitud suficiente para ser segura.
                        </li>
                        <li>
                            <span class="label">🔢 Incluye números:</span>
                            Al menos un número para mayor complejidad.
                        </li>
                        <li>
                            <span class="label">🔤 Mayúsculas y minúsculas:</span>
                            Combinación de ambos para mayor seguridad.
                        </li>
                        <li>
                            <span class="label">✨ Caracteres especiales:</span>
                            Idealmente incluye símbolos como !, @, #, $.
                        </li>
                    </ul>
                    
                    <div class="warning-box">
                        <span class="warning-icon">⚠️</span>
                        <div>
                            <strong>¡Importante!</strong> No compartas tu contraseña con nadie. 
                            Usa una contraseña diferente para OmniVentas que para otros servicios.
                        </div>
                    </div>
                `,
                page: '/configuracion'
            },
            {
                target: '.danger-zone',
                title: '⚠️ Zona de Peligro - Acciones que no se Pueden Deshacer',
                description: `
                    <strong>¡Cuidado! Estas acciones son irreversibles.</strong><br><br>
                    
                    <strong>Acciones disponibles:</strong><br>
                    <ul class="feature-list">
                        <li>
                            <span class="label text-orange">🗑️ Eliminar Todos los Datos:</span>
                            Borra productos, ventas, clientes y configuraciones. 
                            <strong>¡El negocio queda vacío!</strong>
                        </li>
                        <li>
                            <span class="label text-red">🚫 Eliminar Cuenta:</span>
                            Borra tu cuenta y todos los datos asociados. 
                            <strong>¡No hay vuelta atrás!</strong>
                        </li>
                    </ul>
                    
                    <div class="warning-box">
                        <span class="warning-icon">🚨</span>
                        <div>
                            <strong>¡Alerta Máxima!</strong> Estas acciones no se pueden deshacer. 
                            Asegúrate de tener una copia de seguridad antes de proceder.
                        </div>
                    </div>
                    
                    <div class="info-box">
                        <span class="info-icon">📌</span>
                        <div>
                            <strong>Recomendación:</strong> Si necesitas eliminar datos, primero 
                            exporta tu información. Siempre es mejor prevenir que lamentar.
                        </div>
                    </div>
                `,
                page: '/configuracion'
            }
        ]
    };

    // ============================================================
    // ESTADO DEL TUTORIAL
    // ============================================================
    let currentStepIndex = 0;
    let isRunning = false;
    let currentSteps = [];
    let currentPage = '';
    let totalSteps = 0;

    // ============================================================
    // CREAR ELEMENTOS DEL TUTORIAL
    // ============================================================
    function createTutorialElements() {
        // Overlay
        const overlay = document.createElement('div');
        overlay.id = 'tutorial-overlay';
        overlay.className = 'tutorial-overlay';
        document.body.appendChild(overlay);

        // Tarjeta del tutorial
        const card = document.createElement('div');
        card.id = 'tutorial-card';
        card.className = 'tutorial-card';
        card.innerHTML = `
            <div class="tutorial-header">
                <span class="tutorial-step" id="tutorial-step">Paso 1 / 10</span>
                <button class="tutorial-close" id="tutorial-close" title="Cerrar tutorial">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="tutorial-progress">
                <div class="tutorial-progress-bar">
                    <div class="tutorial-progress-fill" id="tutorial-progress-fill"></div>
                </div>
                <span class="tutorial-progress-text" id="tutorial-progress-text">0%</span>
            </div>
            <div class="tutorial-page-badge" id="tutorial-page-badge">Dashboard</div>
            <div class="tutorial-title" id="tutorial-title">Bienvenido</div>
            <div class="tutorial-description" id="tutorial-description">
                Descripción del paso
            </div>
            <div class="tutorial-nav">
                <div class="tutorial-nav-left">
                    <button class="tutorial-btn tutorial-btn-skip" id="tutorial-skip">
                        <i class="fas fa-times-circle"></i> Saltar
                    </button>
                    <button class="tutorial-btn tutorial-btn-prev" id="tutorial-prev" style="display:none;">
                        <i class="fas fa-arrow-left"></i> Anterior
                    </button>
                </div>
                <div class="tutorial-nav-right">
                    <div class="tutorial-dots" id="tutorial-dots"></div>
                    <button class="tutorial-btn tutorial-btn-next" id="tutorial-next">
                        Siguiente <i class="fas fa-arrow-right"></i>
                    </button>
                </div>
            </div>
        `;
        document.body.appendChild(card);
    }

    // ============================================================
    // FUNCIONES PRINCIPALES
    // ============================================================
    function getStepsForPage(path) {
        // Buscar coincidencia exacta
        if (TUTORIAL_STEPS[path]) {
            return TUTORIAL_STEPS[path];
        }
        // Buscar coincidencia parcial
        for (const [pagePath, steps] of Object.entries(TUTORIAL_STEPS)) {
            if (path.startsWith(pagePath)) {
                return steps;
            }
        }
        return null;
    }

    function getCurrentPageName(path) {
        const names = {
            '/dashboard': 'Dashboard',
            '/ventas': 'Ventas',
            '/inventario': 'Inventario',
            '/finanzas': 'Finanzas',
            '/analisis': 'Análisis',
            '/clientes': 'Clientes',
            '/vendedores': 'Vendedores',
            '/configuracion': 'Configuración'
        };
        for (const [pagePath, name] of Object.entries(names)) {
            if (path === pagePath || path.startsWith(pagePath)) {
                return name;
            }
        }
        return 'OmniVentas';
    }

    function startTutorial() {
        const path = window.location.pathname;
        const steps = getStepsForPage(path);
        
        if (!steps || steps.length === 0) {
            console.log('📖 No hay tutorial para esta página:', path);
            return;
        }

        currentSteps = steps;
        currentPage = path;
        currentStepIndex = 0;
        totalSteps = steps.length;

        const overlay = document.getElementById('tutorial-overlay');
        const card = document.getElementById('tutorial-card');

        if (!overlay || !card) {
            createTutorialElements();
        }

        isRunning = true;
        document.getElementById('tutorial-overlay').classList.add('active');
        document.getElementById('tutorial-card').classList.add('active');

        renderStep(0);
    }

    function renderStep(index) {
        const step = currentSteps[index];
        if (!step) return;

        currentStepIndex = index;

        // Actualizar elementos
        document.getElementById('tutorial-step').textContent = 
            `Paso ${index + 1} / ${totalSteps}`;
        document.getElementById('tutorial-page-badge').textContent = 
            getCurrentPageName(currentPage);
        document.getElementById('tutorial-title').innerHTML = step.title;
        document.getElementById('tutorial-description').innerHTML = step.description;

        // Progreso
        const progress = ((index + 1) / totalSteps) * 100;
        document.getElementById('tutorial-progress-fill').style.width = progress + '%';
        document.getElementById('tutorial-progress-text').textContent = Math.round(progress) + '%';

        // Dots
        const dotsContainer = document.getElementById('tutorial-dots');
        dotsContainer.innerHTML = '';
        currentSteps.forEach((_, i) => {
            const dot = document.createElement('span');
            dot.className = 'tutorial-dot';
            if (i === index) dot.classList.add('active');
            if (i < index) dot.classList.add('completed');
            dotsContainer.appendChild(dot);
        });

        // Botones
        document.getElementById('tutorial-prev').style.display = index === 0 ? 'none' : 'inline-flex';
        
        const nextBtn = document.getElementById('tutorial-next');
        if (index === totalSteps - 1) {
            nextBtn.innerHTML = '✅ ¡Entendido!';
            nextBtn.className = 'tutorial-btn tutorial-btn-next finish';
        } else {
            nextBtn.innerHTML = 'Siguiente <i class="fas fa-arrow-right"></i>';
            nextBtn.className = 'tutorial-btn tutorial-btn-next';
        }

        // Resaltar elemento
        highlightElement(step.target);
    }

    function highlightElement(selector) {
        document.querySelectorAll('.tutorial-highlight').forEach(el => {
            el.classList.remove('tutorial-highlight');
        });

        const element = document.querySelector(selector);
        if (element) {
            element.classList.add('tutorial-highlight');
            element.scrollIntoView({
                behavior: 'smooth',
                block: 'center',
                inline: 'center'
            });
        } else {
            console.warn('Elemento no encontrado:', selector);
            const fallback = document.querySelector('.section-header-wrapper, .card-premium, .main-content, body');
            if (fallback) {
                fallback.classList.add('tutorial-highlight');
                fallback.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
    }

    function nextStep() {
        if (currentStepIndex < totalSteps - 1) {
            renderStep(currentStepIndex + 1);
        } else {
            finishTutorial();
        }
    }

    function prevStep() {
        if (currentStepIndex > 0) {
            renderStep(currentStepIndex - 1);
        }
    }

    function finishTutorial() {
        isRunning = false;
        document.getElementById('tutorial-overlay').classList.remove('active');
        document.getElementById('tutorial-card').classList.remove('active');
        
        document.querySelectorAll('.tutorial-highlight').forEach(el => {
            el.classList.remove('tutorial-highlight');
        });

        const key = `omniventas_tutorial_${currentPage}`;
        localStorage.setItem(key, 'true');
        localStorage.setItem('omniventas_tutorial_complete', 'true');
        
        // Feedback
        console.log('✅ Tutorial completado para:', currentPage);
    }

    function skipTutorial() {
        finishTutorial();
    }

    function resetTutorial() {
        const path = window.location.pathname;
        const key = `omniventas_tutorial_${path}`;
        localStorage.removeItem(key);
        localStorage.removeItem('omniventas_tutorial_complete');
        startTutorial();
    }

    // ============================================================
    // INICIALIZAR EVENTOS
    // ============================================================
    function initTutorialEvents() {
        document.getElementById('tutorial-next')?.addEventListener('click', nextStep);
        document.getElementById('tutorial-prev')?.addEventListener('click', prevStep);
        document.getElementById('tutorial-skip')?.addEventListener('click', skipTutorial);
        document.getElementById('tutorial-close')?.addEventListener('click', skipTutorial);

        document.addEventListener('keydown', function(e) {
            if (!isRunning) return;
            if (e.key === 'ArrowRight' || e.key === 'Enter') {
                e.preventDefault();
                nextStep();
            }
            if (e.key === 'ArrowLeft') {
                e.preventDefault();
                prevStep();
            }
            if (e.key === 'Escape') {
                e.preventDefault();
                skipTutorial();
            }
        });

        document.getElementById('tutorial-overlay')?.addEventListener('click', function(e) {
            if (e.target === this) {
                skipTutorial();
            }
        });
    }

    // ============================================================
    // BOTÓN DE TUTORIAL EN HEADER
    // ============================================================
    function addTutorialButton() {
        if (document.querySelector('.btn-tutorial-header')) return;

        const headerActions = document.querySelector('.header-actions');
        if (!headerActions) return;

        const btn = document.createElement('button');
        btn.className = 'btn-tutorial-header';
        btn.innerHTML = `
            <i class="fas fa-play-circle"></i>
            <span>Ver Tutorial</span>
            <span class="badge-tutorial">?</span>
        `;
        btn.title = 'Ver tutorial interactivo de esta página';
        btn.onclick = function(e) {
            e.stopPropagation();
            resetTutorial();
        };
        headerActions.insertBefore(btn, headerActions.firstChild);
    }

    // ============================================================
    // INICIALIZACIÓN
    // ============================================================
    function init() {
        if (!document.getElementById('tutorial-overlay')) {
            createTutorialElements();
        }
        
        initTutorialEvents();
        addTutorialButton();

        // Verificar si el tutorial debe iniciar automáticamente
        const path = window.location.pathname;
        const key = `omniventas_tutorial_${path}`;
        const completed = localStorage.getItem(key) === 'true';
        const globalComplete = localStorage.getItem('omniventas_tutorial_complete') === 'true';

        // En dashboard, iniciar si no está completado globalmente
        if (path === '/dashboard' && !globalComplete) {
            setTimeout(startTutorial, 1200);
        } 
        // En otras páginas, iniciar si no tiene tutorial completado para esa página
        else if (!completed && getStepsForPage(path)) {
            setTimeout(startTutorial, 1200);
        }
    }

    // ============================================================
    // EXPONER API GLOBAL
    // ============================================================
    window.OmniTutorial = {
        start: startTutorial,
        finish: finishTutorial,
        next: nextStep,
        prev: prevStep,
        skip: skipTutorial,
        reset: resetTutorial,
        isRunning: () => isRunning,
        getCurrentStep: () => currentStepIndex,
        getTotalSteps: () => totalSteps
    };

    // ============================================================
    // EJECUTAR
    // ============================================================
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
