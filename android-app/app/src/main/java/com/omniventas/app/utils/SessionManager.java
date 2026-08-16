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
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private SharedPreferences sharedPreferences;

    public SessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveUser(String token, String vendorId, String vendorName, String businessName, int userId) {
        sharedPreferences.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_VENDOR_ID, vendorId)
            .putString(KEY_VENDOR_NAME, vendorName)
            .putString(KEY_BUSINESS_NAME, businessName)
            .putInt(KEY_USER_ID, userId)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply();
        Log.d(TAG, "✅ Sesión guardada");
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

    public int getUserId() {
        return sharedPreferences.getInt(KEY_USER_ID, 0);
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false) && getToken() != null;
    }

    public void clearSession() {
        sharedPreferences.edit().clear().apply();
        Log.d(TAG, "🧹 Sesión limpiada");
    }
}
