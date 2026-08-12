package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.models.VendorLoginRequest;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText etVendorId;
    private Button btnLogin;
    private CardView cardLogin;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "=== onCreate INICIO ===");
        
        // Limpiar sesión anterior para pruebas
        sessionManager = new SessionManager(this);
        sessionManager.clearSession();

        // Inicializar Logger de Telegram
        logger = TelegramLogger.getInstance(this);
        logger.setEnabled(true);
        logger.setVerbose(true);
        logger.info("📱 LoginActivity creada (MODO PRUEBA)");

        // ====================================================
        // 🔥 MODO PRUEBA: REDIRECCIÓN AUTOMÁTICA
        // ====================================================
        // La app saltará directamente al Dashboard después de 1 segundo
        // sin necesidad de ingresar ningún ID.
        // ====================================================
        
        handler.postDelayed(() -> {
            Log.d(TAG, "🔥 MODO PRUEBA: Redirigiendo automáticamente al Dashboard");
            Toast.makeText(LoginActivity.this, "🚀 MODO PRUEBA: Entrando al Dashboard", Toast.LENGTH_SHORT).show();
            
            // Guardar sesión de prueba
            sessionManager.saveUser(
                "token_de_prueba_123456",
                "AAAA0000",
                "Vendedor Prueba",
                "Tienda de Prueba"
            );
            
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 1000);

        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);

        // Poner el ID de prueba por defecto y deshabilitar el campo
        etVendorId.setText("AAAA0000");
        etVendorId.setEnabled(false);
        
        // Cambiar texto del botón
        btnLogin.setText("⏳ Entrando automáticamente...");
        btnLogin.setEnabled(false);

        // Animación
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .start();

        Log.d(TAG, "=== onCreate FIN ===");
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }
}
