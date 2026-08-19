package com.omniventas.app.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.google.gson.JsonObject;
import com.omniventas.app.api.RetrofitClient;

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
    private String appVersion = "9.0.0";

    private TelegramLogger(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error obteniendo versión: " + e.getMessage());
        }
    }

    public static synchronized TelegramLogger getInstance(Context context) {
        if (instance == null) {
            instance = new TelegramLogger(context);
        }
        return instance;
    }

    public void success(String message) {
        sendLog("✅ SUCCESS", message);
    }

    public void warning(String message) {
        sendLog("⚠️ WARNING", message);
    }

    public void error(String message) {
        sendLog("❌ ERROR", message);
    }

    public void networkError(Throwable t) {
        String errorMsg = "🔴 Error de red: ";
        if (t.getMessage() != null) {
            errorMsg += t.getMessage();
            if (errorMsg.length() > 200) {
                errorMsg = errorMsg.substring(0, 200) + "...";
            }
        } else {
            errorMsg += "Desconocido";
        }
        sendLog("🚨 NETWORK_ERROR", errorMsg);
    }

    public void info(String message) {
        sendLog("ℹ️ INFO", message);
    }

    private void sendLog(String level, String message) {
        try {
            String vendorId = sessionManager.isLoggedIn() ? sessionManager.getVendorId() : "DESCONOCIDO";
            String vendorName = sessionManager.isLoggedIn() ? sessionManager.getVendorName() : "DESCONOCIDO";
            String businessName = sessionManager.isLoggedIn() ? sessionManager.getBusinessName() : "DESCONOCIDO";

            JsonObject jsonData = new JsonObject();
            jsonData.addProperty("level", level);
            jsonData.addProperty("message", message);
            jsonData.addProperty("timestamp", getCurrentTimestamp());
            jsonData.addProperty("vendor_id", vendorId);
            jsonData.addProperty("vendor_name", vendorName);
            jsonData.addProperty("business_name", businessName);
            jsonData.addProperty("app_version", appVersion);
            jsonData.addProperty("device_model", Build.MANUFACTURER + " " + Build.MODEL);
            jsonData.addProperty("android_version", Build.VERSION.RELEASE);
            jsonData.addProperty("api_url", RetrofitClient.getApiUrl());

            Log.d(TAG, "📤 Enviando log a Telegram: " + level + " - " + message);

            RetrofitClient.getInstance(context).getApiService().sendLog(jsonData)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "✅ Log enviado correctamente a Telegram");
                        } else {
                            Log.w(TAG, "⚠️ Log enviado con código: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "❌ Error enviando log a Telegram: " + t.getMessage());
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en sendLog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
