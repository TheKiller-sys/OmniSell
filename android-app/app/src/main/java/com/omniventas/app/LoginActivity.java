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

        // Limpiar sesión anterior para pruebas
        sessionManager.clearSession();

        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);

        // Poner el ID de prueba por defecto
        etVendorId.setText("AAAA0000");

        // Animación de entrada
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .start();

        // ====================================================
        // 🔥 BOTÓN DE LOGIN - SOLO ESTO LLEVA AL DASHBOARD
        // ====================================================
        btnLogin.setOnClickListener(v -> {
            Log.d(TAG, "Botón Login presionado");
            irAlDashboard();
        });
    }

    /**
     * Método que lleva al Dashboard
     * Guarda una sesión de prueba y navega a MainActivity
     */
    private void irAlDashboard() {
        Log.d(TAG, "irAlDashboard() - INICIANDO");
        
        // Guardar sesión de prueba
        sessionManager.saveUser(
            "token_de_prueba_123456",
            "AAAA0000",
            "Vendedor Prueba",
            "Tienda de Prueba"
        );
        
        Log.d(TAG, "Sesión guardada");
        
        // Navegar a MainActivity
        try {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Log.d(TAG, "✅ MainActivity iniciada correctamente");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error iniciando MainActivity: " + e.getMessage());
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }
}
