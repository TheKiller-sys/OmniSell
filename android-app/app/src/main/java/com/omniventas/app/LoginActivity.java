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

        // Inicializar Logger de Telegram
        logger = TelegramLogger.getInstance(this);
        logger.setEnabled(true);
        logger.setVerbose(true);
        logger.info("📱 LoginActivity creada");

        sessionManager = new SessionManager(this);

        // Verificar si ya hay sesión activa
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Sesión activa encontrada");
            logger.info("Sesión activa encontrada, redirigiendo a MainActivity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);

        // Poner el ID de prueba por defecto
        etVendorId.setText("AAAA0000");
        Log.d(TAG, "ID de prueba precargado: AAAA0000");

        // Animación
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .start();

        btnLogin.setOnClickListener(v -> {
            Log.d(TAG, "Botón Login presionado");
            logger.info("Botón Login presionado");
            performLogin();
        });
        
        Log.d(TAG, "=== onCreate FIN ===");
    }

    private void performLogin() {
        String vendorId = etVendorId.getText().toString().trim().toUpperCase();
        
        Log.d(TAG, "=== performLogin ===");
        Log.d(TAG, "Vendor ID: " + vendorId);
        
        logger.info("Intentando login con ID: " + vendorId);

        if (vendorId.isEmpty()) {
            logger.warning("ID vacío");
            etVendorId.setError("Ingresa tu ID de vendedor");
            etVendorId.requestFocus();
            shakeView(etVendorId);
            return;
        }

        if (vendorId.length() != 8) {
            logger.warning("ID longitud incorrecta: " + vendorId.length());
            etVendorId.setError("El ID debe tener 8 caracteres");
            etVendorId.requestFocus();
            shakeView(etVendorId);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Verificando...");

        VendorLoginRequest request = new VendorLoginRequest(vendorId);
        ApiService apiService = RetrofitClient.getInstance(this).getApiService();

        logger.debug("Haciendo llamada a loginVendor");

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                Log.d(TAG, "=== onResponse ===");
                Log.d(TAG, "Código: " + response.code());
                Log.d(TAG, "isSuccessful: " + response.isSuccessful());
                
                btnLogin.setEnabled(true);
                btnLogin.setText("Ingresar");

                logger.info("Respuesta recibida - Código: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse data = response.body();
                    
                    Log.d(TAG, "LoginResponse - success: " + data.isSuccess());
                    Log.d(TAG, "LoginResponse - token: " + (data.getToken() != null));
                    Log.d(TAG, "LoginResponse - message: " + data.getMessage());
                    
                    if (data.getVendor() != null) {
                        Log.d(TAG, "Vendor ID: " + data.getVendor().getId());
                        Log.d(TAG, "Vendor Name: " + data.getVendor().getName());
                        Log.d(TAG, "Business: " + data.getVendor().getBusinessName());
                    }

                    if (data.isSuccess() && data.getToken() != null && data.getVendor() != null) {
                        Log.d(TAG, "✅ Login exitoso, guardando sesión");
                        logger.success("✅ Login exitoso: " + data.getVendor().getName());
                        
                        // Guardar sesión
                        sessionManager.saveUser(
                            data.getToken(),
                            data.getVendor().getId(),
                            data.getVendor().getName(),
                            data.getVendor().getBusinessName()
                        );
                        
                        Log.d(TAG, "Sesión guardada, verificando...");
                        boolean loggedIn = sessionManager.isLoggedIn();
                        Log.d(TAG, "isLoggedIn después de guardar: " + loggedIn);
                        
                        // Mostrar Toast de bienvenida
                        Toast.makeText(LoginActivity.this,
                            "✅ ¡Bienvenido " + data.getVendor().getName() + "!",
                            Toast.LENGTH_LONG).show();
                        
                        // NAVEGAR A MAINACTIVITY - USANDO HANDLER PARA ASEGURAR
                        handler.postDelayed(() -> {
                            Log.d(TAG, "🚀 INICIANDO MAINACTIVITY");
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                            Log.d(TAG, "✅ MainActivity iniciada correctamente");
                        }, 500);
                        
                    } else {
                        String msg = data.getMessage() != null ? data.getMessage() : "Error de autenticación";
                        Log.e(TAG, "Login falló: " + msg);
                        logger.error("Login falló: " + msg);
                        Toast.makeText(LoginActivity.this, "❌ " + msg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e(TAG, "Response no exitosa - Código: " + response.code());
                    logger.error("Response no exitosa - Código: " + response.code());
                    Toast.makeText(LoginActivity.this, "❌ Error de autenticación", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Log.e(TAG, "=== onFailure ===");
                Log.e(TAG, "Error: " + t.getMessage(), t);
                
                btnLogin.setEnabled(true);
                btnLogin.setText("Ingresar");
                logger.networkError(t);
                Toast.makeText(LoginActivity.this,
                    "❌ Error de conexión: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }
}
