package com.omniventas.app.models;

public class VentaResponse {
    private boolean success;
    private String message;
    private VentaData venta;
    private int stock_restante;

    public static class VentaData {
        private String producto;
        private int producto_id;
        private int cantidad;
        private double precio_unitario;
        private double total;

        public String getProducto() { return producto; }
        public void setProducto(String producto) { this.producto = producto; }
        public int getProducto_id() { return producto_id; }
        public void setProducto_id(int producto_id) { this.producto_id = producto_id; }
        public int getCantidad() { return cantidad; }
        public void setCantidad(int cantidad) { this.cantidad = cantidad; }
        public double getPrecio_unitario() { return precio_unitario; }
        public void setPrecio_unitario(double precio_unitario) { this.precio_unitario = precio_unitario; }
        public double getTotal() { return total; }
        public void setTotal(double total) { this.total = total; }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public VentaData getVenta() { return venta; }
    public void setVenta(VentaData venta) { this.venta = venta; }
    public int getStock_restante() { return stock_restante; }
    public void setStock_restante(int stock_restante) { this.stock_restante = stock_restante; }
}
