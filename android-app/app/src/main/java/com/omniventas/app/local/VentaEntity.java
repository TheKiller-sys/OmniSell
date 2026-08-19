package com.omniventas.app.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ventas_pendientes")
public class VentaEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private int productoId;
    private String productoNombre;
    private int cantidad;
    private double precioUnitario;
    private double total;
    private long fecha;
    private String vendorId;
    private boolean sincronizado;
    private String error;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getProductoId() { return productoId; }
    public void setProductoId(int productoId) { this.productoId = productoId; }
    public String getProductoNombre() { return productoNombre; }
    public void setProductoNombre(String productoNombre) { this.productoNombre = productoNombre; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(double precioUnitario) { this.precioUnitario = precioUnitario; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public long getFecha() { return fecha; }
    public void setFecha(long fecha) { this.fecha = fecha; }
    public String getVendorId() { return vendorId; }
    public void setVendorId(String vendorId) { this.vendorId = vendorId; }
    public boolean isSincronizado() { return sincronizado; }
    public void setSincronizado(boolean sincronizado) { this.sincronizado = sincronizado; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
