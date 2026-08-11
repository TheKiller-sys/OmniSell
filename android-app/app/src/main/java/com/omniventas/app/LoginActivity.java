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
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.LoginRequest;
import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.utils.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etVendorId, etUsername, etPassword;
    private Button btnLogin;
    private CardView cardLogin;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);

        // Si ya está logueado, ir directo a MainActivity
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        etVendorId = findViewById(R.id.et_vendor_id);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);

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
        String businessId = etVendorId.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (businessId.isEmpty()) {
            etVendorId.setError("Ingresa el ID del negocio");
            etVendorId.requestFocus();
            shakeView(etVendorId);
            return;
        }

        if (username.isEmpty()) {
            etUsername.setError("Ingresa tu usuario");
            etUsername.requestFocus();
            shakeView(etUsername);
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Ingresa tu contraseña");
            etPassword.requestFocus();
            shakeView(etPassword);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Verificando...");

        LoginRequest request = new LoginRequest(username, password, businessId);

        RetrofitClient.getInstance().getApiService().login(request)
            .enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Ingresar");

                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse loginResponse = response.body();
                        if (loginResponse.isSuccess()) {
                            // Guardar sesión
                            sessionManager.saveUser(
                                loginResponse.getToken(),
                                username,
                                loginResponse.getUser().getBusinessName()
                            );

                            Toast.makeText(LoginActivity.this, 
                                "✅ ¡Bienvenido " + username + "!", 
                                Toast.LENGTH_LONG).show();

                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, 
                                "❌ " + loginResponse.getMessage(), 
                                Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, 
                            "❌ Error de conexión con el servidor", 
                            Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Ingresar");
                    Toast.makeText(LoginActivity.this, 
                        "❌ Error de red: " + t.getMessage(), 
                        Toast.LENGTH_LONG).show();
                }
            });
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 
            0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }
}
