package com.omniventas.app.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TelegramLogger {
    
    private static final String TAG = "TelegramLogger";
    private static TelegramLogger instance;
    private Context context;
    private SessionManager sessionManager;
    private Gson gson;
    private boolean isEnabled = true;
    private boolean isVerbose = true;
    private String appVersion = "1.0";
    private int appCode = 1;
    
    // Niveles de log
    public static final String LEVEL_DEBUG = "DEBUG";
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARNING = "WARNING";
    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_SUCCESS = "SUCCESS";
    public static final String LEVEL_CRITICAL = "CRITICAL";
    
    private TelegramLogger(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
        this.gson = new Gson();
        
        // Obtener versión de la app desde PackageManager
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = pInfo.versionName;
            appCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error obteniendo versión de la app: " + e.getMessage());
        }
        
        Log.d(TAG, "TelegramLogger inicializado - App v" + appVersion);
    }
    
    public static synchronized TelegramLogger getInstance(Context context) {
        if (instance == null) {
            instance = new TelegramLogger(context);
        }
        return instance;
    }
    
    /**
     * Habilita o deshabilita el envío de logs
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        Log.d(TAG, "Logging " + (enabled ? "habilitado" : "deshabilitado"));
    }
    
    /**
     * Habilita el envío de logs DEBUG
     */
    public void setVerbose(boolean verbose) {
        this.isVerbose = verbose;
        Log.d(TAG, "Modo verbose " + (verbose ? "activado" : "desactivado"));
    }
    
    /**
     * Envía un log simple
     */
    public void log(String level, String message) {
        log(level, message, null);
    }
    
    /**
     * Envía un log con datos adicionales
     */
    public void log(String level, String message, Object data) {
        // Siempre mostrar en Logcat
        switch (level) {
            case LEVEL_ERROR:
            case LEVEL_CRITICAL:
                Log.e(TAG, message);
                break;
            case LEVEL_WARNING:
                Log.w(TAG, message);
                break;
            case LEVEL_DEBUG:
                if (isVerbose) Log.d(TAG, message);
                break;
            case LEVEL_SUCCESS:
                Log.i(TAG, "✅ " + message);
                break;
            default:
                Log.i(TAG, message);
                break;
        }
        
        // Si está deshabilitado, no enviar a Telegram
        if (!isEnabled) return;
        
        // No enviar logs DEBUG si no está en modo verbose
        if (level.equals(LEVEL_DEBUG) && !isVerbose) return;
        
        // Obtener información del vendedor (si existe)
        String vendorId = "DESCONOCIDO";
        String vendorName = "DESCONOCIDO";
        String businessName = "DESCONOCIDO";
        
        if (sessionManager.isLoggedIn()) {
            vendorId = sessionManager.getVendorId() != null ? sessionManager.getVendorId() : "DESCONOCIDO";
            vendorName = sessionManager.getVendorName() != null ? sessionManager.getVendorName() : "DESCONOCIDO";
            businessName = sessionManager.getBusinessName() != null ? sessionManager.getBusinessName() : "DESCONOCIDO";
        }
        
        // Preparar datos
        String timestamp = getCurrentTimestamp();
        String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;
        
        JsonObject jsonData = new JsonObject();
        jsonData.addProperty("level", level);
        jsonData.addProperty("message", message);
        jsonData.addProperty("timestamp", timestamp);
        jsonData.addProperty("vendor_id", vendorId);
        jsonData.addProperty("vendor_name", vendorName);
        jsonData.addProperty("business_name", businessName);
        jsonData.addProperty("app_version", appVersion);
        jsonData.addProperty("device_model", deviceModel);
        jsonData.addProperty("android_version", androidVersion);
        
        if (data != null) {
            try {
                String dataJson = gson.toJson(data);
                jsonData.addProperty("data", dataJson);
            } catch (Exception e) {
                jsonData.addProperty("data", data.toString());
            }
        }
        
        // Enviar al servidor
        sendToServer(jsonData);
    }
    
    /**
     * Envía un log de tipo DEBUG
     */
    public void debug(String message) {
        log(LEVEL_DEBUG, message);
    }
    
    public void debug(String message, Object data) {
        log(LEVEL_DEBUG, message, data);
    }
    
    /**
     * Envía un log de tipo INFO
     */
    public void info(String message) {
        log(LEVEL_INFO, message);
    }
    
    public void info(String message, Object data) {
        log(LEVEL_INFO, message, data);
    }
    
    /**
     * Envía un log de tipo WARNING
     */
    public void warning(String message) {
        log(LEVEL_WARNING, message);
    }
    
    public void warning(String message, Object data) {
        log(LEVEL_WARNING, message, data);
    }
    
    /**
     * Envía un log de tipo ERROR
     */
    public void error(String message) {
        log(LEVEL_ERROR, message);
    }
    
    public void error(String message, Object data) {
        log(LEVEL_ERROR, message, data);
    }
    
    public void error(String message, Throwable throwable) {
        log(LEVEL_ERROR, message + "\nStackTrace: " + throwable.getMessage(), throwable);
    }
    
    /**
     * Envía un log de tipo CRITICAL (error grave)
     */
    public void critical(String message) {
        log(LEVEL_CRITICAL, message);
    }
    
    public void critical(String message, Throwable throwable) {
        log(LEVEL_CRITICAL, message + "\nStackTrace: " + throwable.getMessage(), throwable);
    }
    
    /**
     * Envía un log de tipo SUCCESS
     */
    public void success(String message) {
        log(LEVEL_SUCCESS, message);
    }
    
    public void success(String message, Object data) {
        log(LEVEL_SUCCESS, message, data);
    }
    
    /**
     * Envía un log cuando hay un error de red
     */
    public void networkError(Throwable t) {
        error("Error de red: " + t.getMessage(), t);
    }
    
    /**
     * Envía un log cuando una llamada API falla
     */
    public void apiError(String endpoint, Throwable t) {
        error("API Error en " + endpoint + ": " + t.getMessage(), t);
    }
    
    /**
     * Envía un log al iniciar la app
     */
    public void appStarted() {
        info("🚀 App iniciada", getDeviceInfo());
    }
    
    /**
     * Envía un log al cerrar la app
     */
    public void appClosed() {
        info("🛑 App cerrada");
    }
    
    /**
     * Obtiene información del dispositivo
     */
    private JsonObject getDeviceInfo() {
        JsonObject info = new JsonObject();
        info.addProperty("device", Build.MANUFACTURER + " " + Build.MODEL);
        info.addProperty("android_version", Build.VERSION.RELEASE);
        info.addProperty("sdk_version", Build.VERSION.SDK_INT);
        info.addProperty("app_version", appVersion);
        info.addProperty("app_code", appCode);
        return info;
    }
    
    /**
     * Envía los datos al servidor
     */
    private void sendToServer(JsonObject data) {
        try {
            ApiService apiService = RetrofitClient.getInstance(context).getApiService();
            
            apiService.sendLog(data).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Log enviado correctamente a Telegram");
                    } else {
                        Log.e(TAG, "Error enviando log: " + response.code());
                    }
                }
                
                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Log.e(TAG, "Fallo al enviar log: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error preparando el envío del log: " + e.getMessage());
        }
    }
    
    /**
     * Obtiene el timestamp actual en formato ISO
     */
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
