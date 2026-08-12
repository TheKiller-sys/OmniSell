package com.omniventas.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "OmniVentasSession";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_VENDOR_ID = "vendor_id";
    private static final String KEY_VENDOR_NAME = "vendor_name";
    private static final String KEY_BUSINESS_NAME = "business_name";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private SharedPreferences sharedPreferences;

    public SessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveUser(String token, String vendorId, String vendorName, String businessName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_VENDOR_ID, vendorId);
        editor.putString(KEY_VENDOR_NAME, vendorName);
        editor.putString(KEY_BUSINESS_NAME, businessName);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
        Log.d(TAG, "✅ Sesion guardada");
    }

    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    public String getVendorId() {
        return sharedPreferences.getString(KEY_VENDOR_ID, null);
    }

    public String getVendorName() {
        return sharedPreferences.getString(KEY_VENDOR_NAME, null);
    }

    public String getBusinessName() {
        return sharedPreferences.getString(KEY_BUSINESS_NAME, null);
    }

    public boolean isLoggedIn() {
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        String token = getToken();
        return isLoggedIn && token != null && !token.isEmpty();
    }

    public void clearSession() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "🧹 Sesion limpiada");
    }
}
