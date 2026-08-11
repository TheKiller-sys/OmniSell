package com.omniventas.app.models;

public class VentaRequest {
    private int producto_id;
    private int cantidad;
    private double precio_unitario;

    public VentaRequest(int producto_id, int cantidad, double precio_unitario) {
        this.producto_id = producto_id;
        this.cantidad = cantidad;
        this.precio_unitario = precio_unitario;
    }

    public int getProducto_id() { return producto_id; }
    public void setProducto_id(int producto_id) { this.producto_id = producto_id; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public double getPrecio_unitario() { return precio_unitario; }
    public void setPrecio_unitario(double precio_unitario) { this.precio_unitario = precio_unitario; }
}
