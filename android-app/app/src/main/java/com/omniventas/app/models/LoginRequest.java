package com.omniventas.app.models;

public class LoginRequest {
    private String username;
    private String password;
    private String business_id;

    public LoginRequest(String username, String password, String business_id) {
        this.username = username;
        this.password = password;
        this.business_id = business_id;
    }

    // Getters y Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getBusiness_id() { return business_id; }
    public void setBusiness_id(String business_id) { this.business_id = business_id; }
}
