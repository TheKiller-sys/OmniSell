package com.omniventas.app.models;

public class VentaReciente {
    private String producto;
    private int cantidad;
    private double total;
    private String fecha;

    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}
