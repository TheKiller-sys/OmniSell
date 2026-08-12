package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
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
    private ProgressBar progressBar;
    private TextView tvError;
    private SessionManager sessionManager;
    private TelegramLogger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "=== onCreate ===");

        // Inicializar utilidades
        sessionManager = new SessionManager(this);
        logger = TelegramLogger.getInstance(this);
        
        // Limpiar sesión anterior (para pruebas)
        sessionManager.clearSession();

        // Buscar vistas
        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tv_error);

        // Verificar que los elementos existen
        if (btnLogin == null) {
            Log.e(TAG, "❌ btnLogin es NULL");
            Toast.makeText(this, "Error: Botón no encontrado", Toast.LENGTH_LONG).show();
            return;
        }

        // Poner el ID de prueba (AAAA0000 es el vendedor de prueba)
        if (etVendorId != null) {
            etVendorId.setText("AAAA0000");
        }

        // Ocultar error inicialmente
        if (tvError != null) {
            tvError.setVisibility(View.GONE);
        }

        // Animación de entrada
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
        // 🔥 BOTÓN DE LOGIN - CONECTADO A LA API
        // ====================================================
        btnLogin.setOnClickListener(v -> {
            Log.d(TAG, "🔥 Botón de login presionado");
            realizarLogin();
        });

        // También permitir login con Enter
        if (etVendorId != null) {
            etVendorId.setOnEditorActionListener((v, actionId, event) -> {
                realizarLogin();
                return true;
            });
        }
    }

    private void realizarLogin() {
        String vendorId = etVendorId.getText().toString().trim().toUpperCase();

        // Validar
        if (vendorId.isEmpty()) {
            mostrarError("Por favor ingresa tu ID de vendedor");
            shakeView(etVendorId);
            return;
        }

        if (vendorId.length() != 8) {
            mostrarError("El ID debe tener exactamente 8 caracteres");
            shakeView(etVendorId);
            return;
        }

        if (!vendorId.matches("[A-Z0-9]+")) {
            mostrarError("El ID solo debe contener letras y números");
            shakeView(etVendorId);
            return;
        }

        // Mostrar loading
        setLoading(true);
        ocultarError();

        Log.d(TAG, "🔄 Intentando login con vendorId: " + vendorId);

        // 📡 Llamada a la API
        ApiService apiService = RetrofitClient.getInstance(this).getApiService();
        VendorLoginRequest request = new VendorLoginRequest(vendorId);

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);

                Log.d(TAG, "📡 Respuesta recibida - Código: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();

                    if (loginResponse.isSuccess()) {
                        // ✅ Login exitoso
                        String token = loginResponse.getToken();
                        LoginResponse.Vendor vendor = loginResponse.getVendor();

                        Log.d(TAG, "✅ Login exitoso!");
                        Log.d(TAG, "   Token: " + (token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "NULL"));
                        Log.d(TAG, "   Vendor: " + vendor.getName());
                        Log.d(TAG, "   Business: " + vendor.getBusinessName());

                        // Guardar sesión
                        sessionManager.saveUser(
                            token,
                            vendor.getId(),
                            vendor.getName(),
                            vendor.getBusinessName()
                        );

                        // Log de éxito
                        logger.success("Login exitoso: " + vendor.getName() + " (" + vendor.getId() + ")");

                        // Ir al Dashboard
                        irAlDashboard();

                    } else {
                        // ❌ Error del servidor (vendedor no encontrado o inactivo)
                        String mensaje = loginResponse.getMessage() != null 
                            ? loginResponse.getMessage() 
                            : "ID de vendedor inválido";
                        mostrarError(mensaje);
                        logger.warning("Login fallido: " + mensaje);
                        shakeView(cardLogin);
                    }

                } else {
                    // ❌ Error HTTP (500, 404, etc)
                    String mensaje = "Error del servidor. Código: " + response.code();
                    mostrarError(mensaje);
                    logger.error("Error HTTP en login: " + response.code());
                    shakeView(cardLogin);
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                
                // ❌ Error de red
                Log.e(TAG, "❌ Error de red: " + t.getMessage(), t);
                mostrarError("Error de conexión. Verifica tu internet.");
                logger.networkError(t);
                shakeView(cardLogin);
            }
        });
    }

    private void irAlDashboard() {
        Log.d(TAG, "🚀 Navegando al Dashboard");
        try {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error navegando al Dashboard: " + e.getMessage(), e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==================== MÉTODOS DE UI ====================

    private void setLoading(boolean loading) {
        if (btnLogin != null) {
            btnLogin.setEnabled(!loading);
            btnLogin.setText(loading ? "Verificando..." : "Ingresar");
        }
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void mostrarError(String mensaje) {
        if (tvError != null) {
            tvError.setText(mensaje);
            tvError.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, "❌ " + mensaje, Toast.LENGTH_LONG).show();
        }
    }

    private void ocultarError() {
        if (tvError != null) {
            tvError.setVisibility(View.GONE);
        }
    }

    private void shakeView(View view) {
        if (view != null) {
            ObjectAnimator shake = ObjectAnimator.ofFloat(
                view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f
            );
            shake.setDuration(500);
            shake.start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si ya hay sesión, ir directamente al dashboard
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Sesión activa detectada, redirigiendo al Dashboard");
            irAlDashboard();
        }
    }
}
