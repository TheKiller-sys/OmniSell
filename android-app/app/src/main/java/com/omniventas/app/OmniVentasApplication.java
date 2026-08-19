package com.omniventas.app;

import android.app.Application;
import android.util.Log;

import com.omniventas.app.utils.CrashHandler;

public class OmniVentasApplication extends Application {
    private static final String TAG = "OmniVentasApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 Aplicación iniciada");
        
        // Registrar CrashHandler para capturar errores
        CrashHandler.register(this);
        
        Log.d(TAG, "✅ CrashHandler registrado correctamente");
    }
}
