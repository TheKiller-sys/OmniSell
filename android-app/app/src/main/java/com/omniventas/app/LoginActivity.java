package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
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
import com.omniventas.app.sync.SyncManager;
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
        Log.d(TAG, "🚀 onCreate iniciado");
        
        try {
            setContentView(R.layout.activity_login);
            Log.d(TAG, "✅ setContentView completado");

            sessionManager = new SessionManager(this);
            logger = TelegramLogger.getInstance(this);

            // Verificar sesión activa
            if (sessionManager.isLoggedIn()) {
                Log.d(TAG, "🔐 Usuario ya logueado, redirigiendo al Dashboard");
                irAlDashboard();
                return;
            }

            // Inicializar vistas
            etVendorId = findViewById(R.id.et_vendor_id);
            btnLogin = findViewById(R.id.btn_login);
            cardLogin = findViewById(R.id.card_login);
            progressBar = findViewById(R.id.progressBar);
            tvError = findViewById(R.id.tv_error);

            // ✅ CORREGIDO: Forzar que el EditText pueda borrar
            if (etVendorId != null) {
                etVendorId.setText("");
                etVendorId.setSelectAllOnFocus(true);
                etVendorId.requestFocus();
            }

            // Animación de entrada
            if (cardLogin != null) {
                cardLogin.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up));
            }

            // Click listener
            btnLogin.setOnClickListener(v -> realizarLogin());

            // Enter key
            if (etVendorId != null) {
                etVendorId.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        realizarLogin();
                        return true;
                    }
                    return false;
                });
            }
            
            Log.d(TAG, "✅ onCreate completado correctamente");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error al iniciar la app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void realizarLogin() {
        Log.d(TAG, "🔐 realizarLogin iniciado");
        
        String vendorId = etVendorId.getText().toString().trim().toUpperCase();
        Log.d(TAG, "📝 Vendor ID ingresado: " + vendorId);

        // Validaciones
        if (vendorId.isEmpty()) {
            mostrarError(getString(R.string.login_error_id_vacio));
            shakeView(etVendorId);
            return;
        }

        if (vendorId.length() != 8) {
            mostrarError(getString(R.string.login_error_id_longitud));
            shakeView(etVendorId);
            return;
        }

        if (!vendorId.matches("[A-Z0-9]+")) {
            mostrarError(getString(R.string.login_error_id_formato));
            shakeView(etVendorId);
            return;
        }

        setLoading(true);
        ocultarError();

        String apiUrl = RetrofitClient.getApiUrl();
        Log.d(TAG, "🔗 URL de API: " + apiUrl);

        ApiService apiService = RetrofitClient.getInstance(this).getApiService();
        VendorLoginRequest request = new VendorLoginRequest(vendorId);
        
        Log.d(TAG, "📤 Enviando petición de login...");

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                Log.d(TAG, "📥 Login - onResponse recibido");
                setLoading(false);
                
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "❌ Código de error: " + response.code());
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            Log.e(TAG, "❌ Cuerpo de error: " + errorBody);
                        }
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse loginResponse = response.body();
                        Log.d(TAG, "✅ Login - success: " + loginResponse.isSuccess());
                        
                        if (loginResponse.isSuccess()) {
                            String token = loginResponse.getToken();
                            LoginResponse.Vendor vendor = loginResponse.getVendor();
                            
                            if (vendor != null && token != null) {
                                Log.d(TAG, "✅ Vendor ID: " + vendor.getId());
                                Log.d(TAG, "✅ Vendor Name: " + vendor.getName());
                                
                                sessionManager.saveUser(
                                    token,
                                    vendor.getId(),
                                    vendor.getName(),
                                    vendor.getBusinessName(),
                                    vendor.getUserId()
                                );
                                
                                logger.success("Login exitoso: " + vendor.getName());
                                SyncManager.scheduleSync(getApplicationContext());
                                
                                Log.d(TAG, "🚀 Login exitoso, redirigiendo al Dashboard");
                                irAlDashboard();
                            } else {
                                Log.e(TAG, "❌ Vendor o token son null");
                                mostrarError("Error en la respuesta del servidor");
                            }
                        } else {
                            String msg = loginResponse.getMessage() != null ? 
                                loginResponse.getMessage() : "ID inválido";
                            Log.e(TAG, "❌ Login fallido: " + msg);
                            mostrarError(msg);
                            logger.warning("Login fallido: " + msg);
                        }
                    } else {
                        String errorMsg = "Error del servidor";
                        if (response.code() == 404) {
                            errorMsg = "Error 404: Servidor no encontrado";
                            logger.error("Error 404 - URL incorrecta: " + RetrofitClient.getApiUrl());
                        } else if (response.code() == 500) {
                            errorMsg = "Error 500: Error interno del servidor";
                        } else if (response.code() == 401) {
                            errorMsg = "Error 401: No autorizado";
                        } else {
                            errorMsg = "Error " + response.code() + ": " + response.message();
                        }
                        Log.e(TAG, "❌ " + errorMsg);
                        mostrarError(errorMsg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error procesando login: " + e.getMessage(), e);
                    mostrarError("Error al procesar la respuesta");
                    logger.error("Error en login: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Log.e(TAG, "❌ Login - onFailure: " + t.getMessage(), t);
                setLoading(false);
                mostrarError("Error de conexión: " + t.getMessage());
                logger.networkError(t);
            }
        });
    }

    private void irAlDashboard() {
        Log.d(TAG, "🚀 irAlDashboard iniciado");
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Log.d(TAG, "✅ Dashboard iniciado correctamente");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error al ir al Dashboard: " + e.getMessage(), e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Verificando..." : "Ingresar");
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void mostrarError(String mensaje) {
        tvError.setText(mensaje);
        tvError.setVisibility(View.VISIBLE);
    }

    private void ocultarError() {
        tvError.setVisibility(View.GONE);
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 
            0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "📱 onResume - Verificando sesión");
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "🔐 Sesión activa, redirigiendo al Dashboard");
            irAlDashboard();
        }
    }
}
