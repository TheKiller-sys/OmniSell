package com.omniventas.app.models;

public class LoginResponse {
    private boolean success;
    private String token;
    private String message;
    private User user;

    public static class User {
        private int id;
        private String username;
        private String role;
        private String business_name;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getBusinessName() { return business_name; }
        public void setBusinessName(String business_name) { this.business_name = business_name; }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
