package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar Logger de Telegram
        logger = TelegramLogger.getInstance(this);
        logger.setEnabled(true);
        logger.setVerbose(true); // Envía logs DEBUG también
        logger.appStarted();

        sessionManager = new SessionManager(this);

        // Verificar si ya hay sesión activa
        if (sessionManager.isLoggedIn()) {
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
        logger.debug("ID de prueba precargado: AAAA0000");

        // Animación
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .start();

        btnLogin.setOnClickListener(v -> {
            logger.debug("Botón Login presionado");
            performLogin();
        });
    }

    private void performLogin() {
        String vendorId = etVendorId.getText().toString().trim().toUpperCase();
        
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
                btnLogin.setEnabled(true);
                btnLogin.setText("Ingresar");

                logger.info("Respuesta recibida - Código: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse data = response.body();
                    
                    logger.debug("Login success: " + data.isSuccess());
                    logger.debug("Token presente: " + (data.getToken() != null));
                    
                    if (data.getVendor() != null) {
                        logger.debug("Vendor ID: " + data.getVendor().getId());
                        logger.debug("Vendor Name: " + data.getVendor().getName());
                        logger.debug("Business: " + data.getVendor().getBusinessName());
                    }

                    if (data.isSuccess() && data.getToken() != null && data.getVendor() != null) {
                        logger.success("Login exitoso: " + data.getVendor().getName());
                        
                        sessionManager.saveUser(
                            data.getToken(),
                            data.getVendor().getId(),
                            data.getVendor().getName(),
                            data.getVendor().getBusinessName()
                        );

                        Toast.makeText(LoginActivity.this,
                            "✅ ¡Bienvenido " + data.getVendor().getName() + "!",
                            Toast.LENGTH_LONG).show();

                        logger.info("Iniciando MainActivity");
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        String msg = data.getMessage() != null ? data.getMessage() : "Error de autenticación";
                        logger.error("Login falló: " + msg);
                        Toast.makeText(LoginActivity.this, "❌ " + msg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    logger.error("Response no exitosa - Código: " + response.code());
                    Toast.makeText(LoginActivity.this, "❌ Error de autenticación", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
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
