package com.omniventas.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private TelegramLogger logger;

    private CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.logger = TelegramLogger.getInstance(context);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        Log.e(TAG, "❌ App CRASH DETECTED!");
        Log.e(TAG, "Thread: " + thread.getName());
        Log.e(TAG, "Exception: " + throwable.getMessage());
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String stackTrace = sw.toString();
        Log.e(TAG, "Stack trace:\n" + stackTrace);

        // Enviar error a Telegram
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String errorMsg = "💥 **CRASH DETECTADO**\n\n" +
                         "📱 **App:** OmniVentas\n" +
                         "🕐 **Hora:** " + timestamp + "\n" +
                         "📝 **Error:** " + throwable.getMessage() + "\n\n" +
                         "📋 **Stack Trace:**\n```\n" + 
                         (stackTrace.length() > 1000 ? stackTrace.substring(0, 1000) + "\n... (truncado)" : stackTrace) + 
                         "\n```";
        
        // Enviar log a Telegram (en un hilo separado)
        new Thread(() -> {
            try {
                if (logger != null) {
                    logger.error(errorMsg);
                    Log.d(TAG, "✅ Error enviado a Telegram");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error enviando crash a Telegram: " + e.getMessage());
            }
        }).start();

        // Mostrar Toast en UI Thread
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context, "❌ Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                // Ignorar
            }
        });

        // Esperar un momento para enviar el log antes de cerrar
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignorar
        }

        // Si hay un manejador por defecto, usarlo
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    public static void register(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
        Log.d(TAG, "✅ CrashHandler registrado");
    }
}
