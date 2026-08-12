package com.omniventas.app.models;

public class VendorLoginRequest {
    private String vendor_id;

    public VendorLoginRequest(String vendor_id) {
        this.vendor_id = vendor_id;
    }

    public String getVendorId() { return vendor_id; }
    public void setVendorId(String vendor_id) { this.vendor_id = vendor_id; }
}
