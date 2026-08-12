package com.omniventas.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import java.io.IOException;
import java.security.GeneralSecurityException;

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
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            sharedPreferences = EncryptedSharedPreferences.create(
                PREF_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            Log.d(TAG, "EncryptedSharedPreferences inicializado correctamente");
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error inicializando EncryptedSharedPreferences, usando SharedPreferences normal: " + e.getMessage());
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    // Guardar usuario vendedor
    public void saveUser(String token, String vendorId, String vendorName, String businessName) {
        Log.d(TAG, "Guardando sesión - Token: " + (token != null ? "PRESENTE" : "NULL"));
        Log.d(TAG, "Vendor ID: " + vendorId);
        Log.d(TAG, "Vendor Name: " + vendorName);
        Log.d(TAG, "Business Name: " + businessName);
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_VENDOR_ID, vendorId);
        editor.putString(KEY_VENDOR_NAME, vendorName);
        editor.putString(KEY_BUSINESS_NAME, businessName);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        boolean success = editor.commit();
        Log.d(TAG, "Sesión guardada: " + (success ? "ÉXITO" : "FALLÓ"));
    }

    public String getToken() {
        String token = sharedPreferences.getString(KEY_TOKEN, null);
        Log.d(TAG, "getToken: " + (token != null ? "PRESENTE" : "NULL"));
        return token;
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
        boolean result = isLoggedIn && token != null;
        Log.d(TAG, "isLoggedIn: " + result + " (isLoggedIn=" + isLoggedIn + ", token=" + (token != null ? "PRESENTE" : "NULL") + ")");
        return result;
    }

    public void clearSession() {
        Log.d(TAG, "Limpiando sesión");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
}
