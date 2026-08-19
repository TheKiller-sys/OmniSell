package com.omniventas.app.models;

import java.util.List;

public class DashboardResponse {
    private boolean success;
    private DashboardData dashboard;

    public static class DashboardData {
        private int ventas_hoy;
        private double ingresos_hoy;
        private int ventas_mes;
        private double ingresos_mes;
        private int productos_bajo_stock;
        private List<Venta> ventas_recientes;
        private String fecha;
        private String business_name;

        public int getVentasHoy() { return ventas_hoy; }
        public void setVentasHoy(int ventas_hoy) { this.ventas_hoy = ventas_hoy; }
        public double getIngresosHoy() { return ingresos_hoy; }
        public void setIngresosHoy(double ingresos_hoy) { this.ingresos_hoy = ingresos_hoy; }
        public int getVentasMes() { return ventas_mes; }
        public void setVentasMes(int ventas_mes) { this.ventas_mes = ventas_mes; }
        public double getIngresosMes() { return ingresos_mes; }
        public void setIngresosMes(double ingresos_mes) { this.ingresos_mes = ingresos_mes; }
        public int getProductosBajoStock() { return productos_bajo_stock; }
        public void setProductosBajoStock(int productos_bajo_stock) { this.productos_bajo_stock = productos_bajo_stock; }
        public List<Venta> getVentasRecientes() { return ventas_recientes; }
        public void setVentasRecientes(List<Venta> ventas_recientes) { this.ventas_recientes = ventas_recientes; }
        public String getFecha() { return fecha; }
        public void setFecha(String fecha) { this.fecha = fecha; }
        public String getBusinessName() { return business_name; }
        public void setBusinessName(String business_name) { this.business_name = business_name; }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public DashboardData getDashboard() { return dashboard; }
    public void setDashboard(DashboardData dashboard) { this.dashboard = dashboard; }
}
