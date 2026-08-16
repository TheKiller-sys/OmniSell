package com.omniventas.app.models;

public class LoginResponse {
    private boolean success;
    private String token;
    private String message;
    private Vendor vendor;

    public static class Vendor {
        private String id;
        private String name;
        private String business_id;
        private String business_name;
        private String role;
        private int user_id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBusinessId() { return business_id; }
        public void setBusinessId(String business_id) { this.business_id = business_id; }
        public String getBusinessName() { return business_name; }
        public void setBusinessName(String business_name) { this.business_name = business_name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public int getUserId() { return user_id; }
        public void setUserId(int user_id) { this.user_id = user_id; }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
}
