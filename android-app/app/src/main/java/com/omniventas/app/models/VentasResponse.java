package com.omniventas.app.models;

import java.util.List;

public class VentasResponse {
    private boolean success;
    private List<Venta> ventas;
    private int total;
    private int limite;
    private int offset;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public List<Venta> getVentas() { return ventas; }
    public void setVentas(List<Venta> ventas) { this.ventas = ventas; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getLimite() { return limite; }
    public void setLimite(int limite) { this.limite = limite; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }
}
