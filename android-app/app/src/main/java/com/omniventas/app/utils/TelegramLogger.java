package com.omniventas.app.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
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
    private String appVersion = "5.0.0";

    private TelegramLogger(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {}
    }

    public static synchronized TelegramLogger getInstance(Context context) {
        if (instance == null) {
            instance = new TelegramLogger(context);
        }
        return instance;
    }

    public void success(String message) { sendLog("SUCCESS", message); }
    public void warning(String message) { sendLog("WARNING", message); }
    public void error(String message) { sendLog("ERROR", message); }
    public void networkError(Throwable t) { sendLog("ERROR", "Error de red: " + t.getMessage()); }
    public void info(String message) { sendLog("INFO", message); }

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

            RetrofitClient.getInstance(context).getApiService().sendLog(jsonData)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) Log.d(TAG, "✅ Log enviado");
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "❌ Error enviando log: " + t.getMessage());
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en sendLog: " + e.getMessage());
        }
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
