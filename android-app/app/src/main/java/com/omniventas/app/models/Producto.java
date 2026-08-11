package com.omniventas.app.models;

import com.google.gson.annotations.SerializedName;

public class Producto {
    @SerializedName("id") private int id;
    @SerializedName("nombre") private String nombre;
    @SerializedName("seccion") private String seccion;
    @SerializedName("precio") private double precio;
    @SerializedName("stock") private int stock;
    @SerializedName("descripcion") private String descripcion;

    // Getters y setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSeccion() { return seccion; }
    public void setSeccion(String seccion) { this.seccion = seccion; }
    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
