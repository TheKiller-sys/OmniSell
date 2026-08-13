package com.omniventas.app.models;

import java.util.List;

public class RespuestaProductos {
    private boolean success;
    private List<Producto> productos;
    private int total;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public List<Producto> getProductos() { return productos; }
    public void setProductos(List<Producto> productos) { this.productos = productos; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}
