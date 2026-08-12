package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.models.VendorLoginRequest;
import com.omniventas.app.utils.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etVendorId;
    private Button btnLogin;
    private CardView cardLogin;
    private ProgressBar progressBar;
    private TextView tvError;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar SessionManager
        sessionManager = new SessionManager(this);

        // Si ya está logueado, ir a MainActivity
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);
        progressBar = findViewById(R.id.progress_bar);
        tvError = findViewById(R.id.tv_error);

        // Animación de entrada
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .start();

        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void performLogin() {
        String vendorId = etVendorId.getText().toString().trim();

        // Validación: ID debe tener 8 caracteres (letras y números)
        if (vendorId.isEmpty()) {
            showError("Ingresa tu ID de vendedor");
            shakeView(etVendorId);
            return;
        }

        if (vendorId.length() != 8) {
            showError("El ID debe tener exactamente 8 caracteres");
            shakeView(etVendorId);
            return;
        }

        if (!vendorId.matches("^[a-zA-Z0-9]+$")) {
            showError("El ID solo debe contener letras y números");
            shakeView(etVendorId);
            return;
        }

        // Mostrar estado de carga
        setLoading(true);
        tvError.setVisibility(View.GONE);

        // Llamar a la API
        VendorLoginRequest request = new VendorLoginRequest(vendorId);
        Call<LoginResponse> call = RetrofitClient.getInstance()
            .getApiService()
            .loginVendor(request);

        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();

                    if (loginResponse.isSuccess()) {
                        // Guardar sesión
                        sessionManager.saveUser(
                            loginResponse.getToken(),
                            loginResponse.getVendor().getId(),
                            loginResponse.getVendor().getName(),
                            loginResponse.getVendor().getBusinessName()
                        );

                        Toast.makeText(LoginActivity.this, 
                            "✅ ¡Bienvenido " + loginResponse.getVendor().getName() + "!", 
                            Toast.LENGTH_LONG).show();

                        // Ir al dashboard
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showError(loginResponse.getMessage());
                    }
                } else {
                    showError("Error de conexión con el servidor");
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                showError("Error de red: " + t.getMessage());
            }
        });
    }

    private void setLoading(boolean loading) {
        if (loading) {
            btnLogin.setEnabled(false);
            btnLogin.setText("");
            progressBar.setVisibility(View.VISIBLE);
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("Ingresar");
            progressBar.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        shakeView(tvError);
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }
}
