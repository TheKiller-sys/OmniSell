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

        sessionManager = new SessionManager(this);
        logger = TelegramLogger.getInstance(this);
        sessionManager.clearSession();

        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tv_error);

        if (etVendorId != null) {
            etVendorId.setText("AAAA0000");
        }

        // Animación de entrada
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
    }

    private void realizarLogin() {
        String vendorId = etVendorId.getText().toString().trim().toUpperCase();

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

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();

                    if (loginResponse.isSuccess()) {
                        String token = loginResponse.getToken();
                        LoginResponse.Vendor vendor = loginResponse.getVendor();

                        if (vendor != null && token != null) {
                            sessionManager.saveUser(
                                token,
                                vendor.getId(),
                                vendor.getName(),
                                vendor.getBusinessName()
                            );

                            logger.success("Login exitoso: " + vendor.getName());
                            irAlDashboard();
                        }
                    } else {
                        String msg = loginResponse.getMessage() != null ? loginResponse.getMessage() : "ID inválido";
                        mostrarError(msg);
                        logger.warning("Login fallido: " + msg);
                    }
                } else {
                    mostrarError("Error del servidor. Código: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                mostrarError("Error de conexión");
                logger.networkError(t);
            }
        });
    }

    private void irAlDashboard() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
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
        ObjectAnimator shake = ObjectAnimator.ofFloat(
            view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f
        );
        shake.setDuration(500);
        shake.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager.isLoggedIn()) {
            irAlDashboard();
        }
    }
}
