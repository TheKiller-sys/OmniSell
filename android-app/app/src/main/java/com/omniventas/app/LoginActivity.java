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
        Log.d(TAG, "onCreate iniciado");
        
        try {
            setContentView(R.layout.activity_login);
            Log.d(TAG, "setContentView completado");

            sessionManager = new SessionManager(this);
            logger = TelegramLogger.getInstance(this);

            if (sessionManager.isLoggedIn()) {
                Log.d(TAG, "Usuario ya logueado, redirigiendo al Dashboard");
                irAlDashboard();
                return;
            }

            etVendorId = findViewById(R.id.et_vendor_id);
            btnLogin = findViewById(R.id.btn_login);
            cardLogin = findViewById(R.id.card_login);
            progressBar = findViewById(R.id.progressBar);
            tvError = findViewById(R.id.tv_error);

            if (etVendorId != null) {
                etVendorId.setText("");
            }

            if (cardLogin != null) {
                cardLogin.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up));
            }

            btnLogin.setOnClickListener(v -> realizarLogin());

            if (etVendorId != null) {
                etVendorId.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        realizarLogin();
                        return true;
                    }
                    return false;
                });
            }
            
            Log.d(TAG, "onCreate completado correctamente");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreate: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error al iniciar la app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void realizarLogin() {
        Log.d(TAG, "realizarLogin iniciado");
        
        String vendorId = etVendorId.getText().toString().trim().toUpperCase();
        Log.d(TAG, "Vendor ID ingresado: " + vendorId);

        if (vendorId.isEmpty()) {
            mostrarError("Ingresa tu ID de vendedor");
            shakeView(etVendorId);
            return;
        }

        if (vendorId.length() != 8) {
            mostrarError("El ID debe tener 8 caracteres");
            shakeView(etVendorId);
            return;
        }

        if (!vendorId.matches("[A-Z0-9]+")) {
            mostrarError("Solo letras y números");
            shakeView(etVendorId);
            return;
        }

        setLoading(true);
        ocultarError();

        ApiService apiService = RetrofitClient.getInstance(this).getApiService();
        VendorLoginRequest request = new VendorLoginRequest(vendorId);
        
        Log.d(TAG, "Enviando petición de login...");

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                Log.d(TAG, "Login - onResponse recibido");
                setLoading(false);
                
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse loginResponse = response.body();
                        Log.d(TAG, "Login - success: " + loginResponse.isSuccess());
                        Log.d(TAG, "Login - message: " + loginResponse.getMessage());
                        
                        if (loginResponse.isSuccess()) {
                            String token = loginResponse.getToken();
                            LoginResponse.Vendor vendor = loginResponse.getVendor();
                            
                            if (vendor != null && token != null) {
                                Log.d(TAG, "Login - Vendor ID: " + vendor.getId());
                                Log.d(TAG, "Login - Vendor Name: " + vendor.getName());
                                
                                sessionManager.saveUser(
                                    token,
                                    vendor.getId(),
                                    vendor.getName(),
                                    vendor.getBusinessName(),
                                    vendor.getUserId()
                                );
                                
                                logger.success("Login exitoso: " + vendor.getName());
                                
                                SyncManager.scheduleSync(getApplicationContext());
                                
                                Log.d(TAG, "Login exitoso, redirigiendo al Dashboard");
                                irAlDashboard();
                            } else {
                                Log.e(TAG, "Login - Vendor o token son null");
                                mostrarError("Error en la respuesta del servidor");
                            }
                        } else {
                            String msg = loginResponse.getMessage() != null ? loginResponse.getMessage() : "ID inválido";
                            Log.e(TAG, "Login fallido: " + msg);
                            mostrarError(msg);
                            logger.warning("Login fallido: " + msg);
                        }
                    } else {
                        Log.e(TAG, "Login - Response no exitosa. Código: " + response.code());
                        mostrarError("Error del servidor. Código: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error procesando login: " + e.getMessage());
                    e.printStackTrace();
                    mostrarError("Error al procesar la respuesta: " + e.getMessage());
                    logger.error("Error en login: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Log.e(TAG, "❌ Login - onFailure: " + t.getMessage());
                t.printStackTrace();
                setLoading(false);
                mostrarError("Error de conexión: " + t.getMessage());
                logger.networkError(t);
            }
        });
    }

    private void irAlDashboard() {
        Log.d(TAG, "irAlDashboard iniciado");
        try {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            Log.d(TAG, "✅ Dashboard iniciado correctamente");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error al ir al Dashboard: " + e.getMessage());
            e.printStackTrace();
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
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - Verificando sesión");
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Sesión activa, redirigiendo al Dashboard");
            irAlDashboard();
        }
    }
}
