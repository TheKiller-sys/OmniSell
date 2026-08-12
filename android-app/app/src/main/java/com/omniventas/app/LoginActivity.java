package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.omniventas.app.utils.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText etVendorId;
    private Button btnLogin;
    private CardView cardLogin;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "=== onCreate ===");

        sessionManager = new SessionManager(this);
        sessionManager.clearSession();

        // Buscar vistas
        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);

        // Verificar que los elementos existen
        Log.d(TAG, "etVendorId: " + (etVendorId != null ? "ENCONTRADO" : "NULL"));
        Log.d(TAG, "btnLogin: " + (btnLogin != null ? "ENCONTRADO" : "NULL"));
        Log.d(TAG, "cardLogin: " + (cardLogin != null ? "ENCONTRADO" : "NULL"));

        if (btnLogin == null) {
            Log.e(TAG, "❌ btnLogin es NULL - Revisa el layout");
            Toast.makeText(this, "Error: Botón no encontrado", Toast.LENGTH_LONG).show();
            return;
        }

        // Poner el ID de prueba
        if (etVendorId != null) {
            etVendorId.setText("AAAA0000");
        }

        // Animación
        if (cardLogin != null) {
            cardLogin.setTranslationY(100f);
            cardLogin.setAlpha(0f);
            cardLogin.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .start();
        }

        // ====================================================
        // 🔥 BOTÓN DE LOGIN
        // ====================================================
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "🔥🔥🔥 BOTÓN PRESIONADO 🔥🔥🔥");
                Toast.makeText(LoginActivity.this, "🚀 Ingresando al Dashboard...", Toast.LENGTH_SHORT).show();
                irAlDashboard();
            }
        });

        // También probar con setOnTouchListener para ver si el botón recibe eventos
        btnLogin.setOnTouchListener((v, event) -> {
            Log.d(TAG, "Touch event en botón: " + event.getAction());
            return false;
        });

        Log.d(TAG, "=== onCreate FIN ===");
    }

    private void irAlDashboard() {
        Log.d(TAG, "🔥 irAlDashboard() - INICIANDO");
        
        try {
            // Guardar sesión de prueba
            sessionManager.saveUser(
                "token_de_prueba_123456",
                "AAAA0000",
                "Vendedor Prueba",
                "Tienda de Prueba"
            );
            Log.d(TAG, "✅ Sesión guardada");

            // Crear intent
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            Log.d(TAG, "🚀 Iniciando MainActivity...");
            startActivity(intent);
            
            Log.d(TAG, "✅ finish() llamando");
            finish();
            
            Log.d(TAG, "✅ Todo completado");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error: " + e.getMessage(), e);
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shakeView(View view) {
        if (view != null) {
            ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
            shake.setDuration(500);
            shake.start();
        }
    }
}
