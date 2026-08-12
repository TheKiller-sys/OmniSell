package com.omniventas.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
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
    private TextView tvError, tvStatusMessage;
    private LinearLayout llStatusContainer;
    private SessionManager sessionManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Estados de animación
    private enum LoginState { IDLE, LOADING, SUCCESS, ERROR }

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

        // Inicializar vistas
        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);
        progressBar = findViewById(R.id.progress_bar);
        tvError = findViewById(R.id.tv_error);
        tvStatusMessage = findViewById(R.id.tv_status_message);
        llStatusContainer = findViewById(R.id.ll_status_container);

        // Animación de entrada
        animateEntrance();

        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void animateEntrance() {
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void performLogin() {
        String vendorId = etVendorId.getText().toString().trim();

        // Validación: ID debe tener 8 caracteres (letras y números)
        if (vendorId.isEmpty()) {
            showError("Ingresa tu ID de vendedor", true);
            return;
        }

        if (vendorId.length() != 8) {
            showError("El ID debe tener exactamente 8 caracteres", true);
            return;
        }

        if (!vendorId.matches("^[a-zA-Z0-9]+$")) {
            showError("El ID solo debe contener letras y números", true);
            return;
        }

        // Mostrar estado de carga con animación
        setLoading(true);
        tvError.setVisibility(View.GONE);
        llStatusContainer.setVisibility(View.VISIBLE);
        tvStatusMessage.setText("🔍 Verificando ID...");
        tvStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.primary));

        // Llamar a la API
        VendorLoginRequest request = new VendorLoginRequest(vendorId);
        Call<LoginResponse> call = RetrofitClient.getInstance()
            .getApiService()
            .loginVendor(request);

        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();

                    if (loginResponse.isSuccess()) {
                        // Mostrar éxito con animación
                        showSuccess(loginResponse);
                    } else {
                        showError(loginResponse.getMessage(), false);
                    }
                } else {
                    showError("Error de conexión con el servidor", false);
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                showError("Error de red: " + t.getMessage(), false);
            }
        });
    }

    private void showSuccess(LoginResponse response) {
        setLoading(false);
        
        // Cambiar mensaje a éxito
        tvStatusMessage.setText("✅ ¡Bienvenido " + response.getVendor().getName() + "!");
        tvStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.success));
        
        // Animación de pulso en el botón
        ObjectAnimator pulse = ObjectAnimator.ofFloat(btnLogin, "scaleX", 1f, 1.05f, 1f);
        pulse.setDuration(300);
        pulse.setRepeatCount(2);
        pulse.start();
        
        // Guardar sesión después de un breve delay para que el usuario vea el mensaje
        handler.postDelayed(() -> {
            sessionManager.saveUser(
                response.getToken(),
                response.getVendor().getId(),
                response.getVendor().getName(),
                response.getVendor().getBusinessName()
            );

            Toast.makeText(LoginActivity.this, 
                "¡Bienvenido " + response.getVendor().getName() + "!", 
                Toast.LENGTH_SHORT).show();

            // Ir al dashboard con animación
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 1500);
    }

    private void showError(String message, boolean shakeField) {
        setLoading(false);
        
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        
        if (shakeField) {
            shakeView(etVendorId);
        } else {
            shakeView(tvError);
        }
        
        // Ocultar estado
        llStatusContainer.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        if (loading) {
            btnLogin.setEnabled(false);
            btnLogin.setText("");
            progressBar.setVisibility(View.VISIBLE);
            
            // Desvanecer el campo de entrada
            etVendorId.animate()
                .alpha(0.5f)
                .setDuration(200)
                .start();
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("Ingresar");
            progressBar.setVisibility(View.GONE);
            
            etVendorId.animate()
                .alpha(1f)
                .setDuration(200)
                .start();
        }
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 
            0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }
}
