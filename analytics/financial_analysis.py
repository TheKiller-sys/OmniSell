# analytics/financial_analysis.py - Análisis financieros
import pandas as pd
from datetime import datetime, timedelta
import numpy as np
from scipy.stats import linregress
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import seaborn as sns
import io
import logging

logger = logging.getLogger(__name__)

class FinancialAnalyzer:
    def __init__(self, db_manager):
        self.db = db_manager

    def analisis_inversiones(self):
        """Analizar rentabilidad de inversiones"""
        query = """
        SELECT 
            i.id,
            p.nombre as producto,
            i.cantidad,
            i.costo_total,
            i.fecha,
            COALESCE(SUM(v.cantidad), 0) as vendido,
            COALESCE(SUM(v.cantidad * p.precio_venta), 0) as ingresos,
            (COALESCE(SUM(v.cantidad * p.precio_venta), 0) - i.costo_total) as ganancia
        FROM inversiones i
        JOIN productos p ON i.producto_id = p.id
        LEFT JOIN ventas v ON v.inversion_id = i.id
        GROUP BY i.id
        ORDER BY i.fecha DESC
        """
        
        df = self.db.get_dataframe(query)
        
        if df.empty:
            return {"error": "No hay datos disponibles"}
        
        # Calcular ROI
        df['roi'] = (df['ganancia'] / df['costo_total']) * 100
        df['porcentaje_vendido'] = (df['vendido'] / df['cantidad']) * 100
        
        return df.to_dict(orient='records')
    
    def generar_reporte_ventas(self, periodo='mensual'):
        """Generar reporte de ventas con análisis"""
        hoy = datetime.now()
        if periodo == 'diario':
            fecha_inicio = hoy
            fecha_fin = hoy
            group_by = "DATE(fecha)"
        elif periodo == 'semanal':
            fecha_inicio = hoy - timedelta(days=hoy.weekday())
            fecha_fin = hoy
            group_by = "DATE(fecha)"
        elif periodo == 'mensual':
            fecha_inicio = hoy.replace(day=1)
            fecha_fin = hoy
            group_by = "strftime('%Y-%m', fecha)"
        else:  # trimestral
            quarter_start = (hoy.month - 1) // 3 * 3 + 1
            fecha_inicio = hoy.replace(month=quarter_start, day=1)
            fecha_fin = hoy
            group_by = "strftime('%Y-%m', fecha)"
        
        # Detectar si estamos en PostgreSQL o SQLite
        is_postgres = 'RENDER' in os.environ
        if is_postgres:
            group_by = "TO_CHAR(fecha, 'YYYY-MM')"
            date_format = "%s"
        else:
            group_by = "strftime('%Y-%m', fecha)"
            date_format = "?"
        
        query = f"""
        SELECT 
            {group_by} as periodo,
            SUM(v.cantidad) as total_ventas,
            SUM(v.cantidad * p.precio_venta) as ingresos,
            SUM(v.cantidad * (p.precio_compra + p.costo_transporte)) as costos,
            SUM(v.cantidad * (p.precio_venta - p.precio_compra - p.costo_transporte)) as ganancia
        FROM ventas v
        JOIN productos p ON v.producto_id = p.id
        WHERE fecha BETWEEN {date_format} AND {date_format}
        GROUP BY periodo
        ORDER BY periodo
        """
        
        df = self.db.get_dataframe(query, (fecha_inicio, fecha_fin))
        
        if df.empty:
            return {"error": "No hay datos disponibles"}
        
        # Análisis adicional
        df['margen'] = (df['ganancia'] / df['ingresos']) * 100
        df['costo_unitario'] = df['costos'] / df['total_ventas']
        
        # Top productos
        if is_postgres:
            top_query = """
            SELECT p.nombre, SUM(v.cantidad) as ventas
            FROM ventas v
            JOIN productos p ON v.producto_id = p.id
            WHERE fecha BETWEEN %s AND %s
            GROUP BY p.nombre
            ORDER BY ventas DESC
            LIMIT 5
            """
        else:
            top_query = """
            SELECT p.nombre, SUM(v.cantidad) as ventas
            FROM ventas v
            JOIN productos p ON v.producto_id = p.id
            WHERE fecha BETWEEN ? AND ?
            GROUP BY p.nombre
            ORDER BY ventas DESC
            LIMIT 5
            """
        top_productos = self.db.execute_query(top_query, (fecha_inicio, fecha_fin))
        
        return {
            "periodo": periodo,
            "fecha_inicio": fecha_inicio.strftime("%Y-%m-%d"),
            "fecha_fin": fecha_fin.strftime("%Y-%m-%d"),
            "resumen": df.to_dict(orient='records'),
            "total_ventas": int(df['total_ventas'].sum()),
            "total_ingresos": float(df['ingresos'].sum()),
            "total_ganancia": float(df['ganancia'].sum()),
            "margen_promedio": float(df['margen'].mean()),
            "top_productos": top_productos
        }
    
    def generar_grafico_ventas(self, periodo='mensual'):
        """Generar gráfico de ventas para el panel web"""
        reporte = self.generar_reporte_ventas(periodo)
        if 'error' in reporte:
            return None
        
        df = pd.DataFrame(reporte['resumen'])
        plt.figure(figsize=(10, 6))
        
        # Gráfico de barras apiladas
        if periodo == 'diario':
            df['periodo'] = pd.to_datetime(df['periodo']).dt.strftime('%H:%M')
            x = df['periodo']
            plt.bar(x, df['ingresos'], label='Ingresos')
            plt.bar(x, df['costos'], bottom=df['ingresos'], label='Costos')
            plt.xticks(rotation=45)
            plt.xlabel('Hora del día')
        else:
            x = df['periodo']
            plt.plot(x, df['ingresos'], 'o-', label='Ingresos')
            plt.plot(x, df['costos'], 'o-', label='Costos')
            plt.plot(x, df['ganancia'], 'o-', label='Ganancia')
            plt.xlabel('Periodo')
        
        plt.title(f"Análisis de Ventas - {periodo.capitalize()}")
        plt.ylabel('Monto ($)')
        plt.legend()
        plt.grid(True, linestyle='--', alpha=0.7)
        
        # Guardar en buffer
        buf = io.BytesIO()
        plt.savefig(buf, format='png', bbox_inches='tight')
        buf.seek(0)
        plt.close()
        return buf
    
    def analisis_rentabilidad_productos(self):
        """Analizar rentabilidad de productos"""
        query = """
        SELECT 
            p.nombre,
            SUM(v.cantidad) as unidades_vendidas,
            SUM(v.cantidad * p.precio_venta) as ingresos,
            SUM(v.cantidad * (p.precio_compra + p.costo_transporte)) as costos,
            (SUM(v.cantidad * p.precio_venta) - SUM(v.cantidad * (p.precio_compra + p.costo_transporte))) as ganancia,
            AVG(p.margen_ganancia) as margen_promedio
        FROM ventas v
        JOIN productos p ON v.producto_id = p.id
        GROUP BY p.nombre
        ORDER BY ganancia DESC
        """
        
        df = self.db.get_dataframe(query)
        
        if df.empty:
            return {"error": "No hay datos disponibles"}
        
        # Cálculos adicionales
        total_ganancia = df['ganancia'].sum()
        df['contribucion'] = (df['ganancia'] / total_ganancia) * 100
        df['rentabilidad'] = df['ganancia'] / df['costos']
        
        # Clasificación ABC
        df = df.sort_values('ganancia', ascending=False)
        df['cumulative'] = df['ganancia'].cumsum()
        df['porcentaje_acumulado'] = (df['cumulative'] / total_ganancia) * 100
        
        df['clasificacion'] = 'C'
        df.loc[df['porcentaje_acumulado'] <= 80, 'clasificacion'] = 'A'
        df.loc[(df['porcentaje_acumulado'] > 80) & (df['porcentaje_acumulado'] <= 95), 'clasificacion'] = 'B'
        
        return df.to_dict(orient='records')
    
    def generar_grafico_rentabilidad(self):
        """Generar gráfico de rentabilidad por producto"""
        data = self.analisis_rentabilidad_productos()
        if 'error' in data:
            return None
        
        df = pd.DataFrame(data)
        plt.figure(figsize=(12, 8))
        
        # Gráfico de barras apiladas
        sns.barplot(
            x='nombre', 
            y='ganancia', 
            data=df, 
            hue='clasificacion',
            palette={'A': 'green', 'B': 'orange', 'C': 'red'},
            dodge=False
        )
        
        plt.title('Rentabilidad por Producto (Clasificación ABC)')
        plt.xlabel('Producto')
        plt.ylabel('Ganancia ($)')
        plt.xticks(rotation=45, ha='right')
        plt.grid(axis='y', linestyle='--', alpha=0.7)
        
        # Guardar en buffer
        buf = io.BytesIO()
        plt.savefig(buf, format='png', bbox_inches='tight')
        buf.seek(0)
        plt.close()
        return buf