package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText etVendorId;
    private Button btnLogin;
    private CardView cardLogin;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);

        // Verificar si ya hay sesión activa
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);

        // Animación de entrada de la tarjeta
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .start();

        // Poner el ID de prueba por defecto para facilitar pruebas
        etVendorId.setText("AAAA0000");

        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void performLogin() {
        String vendorId = etVendorId.getText().toString().trim().toUpperCase();

        if (vendorId.isEmpty()) {
            etVendorId.setError("Ingresa tu ID de vendedor");
            etVendorId.requestFocus();
            shakeView(etVendorId);
            return;
        }

        if (vendorId.length() != 8) {
            etVendorId.setError("El ID debe tener 8 caracteres");
            etVendorId.requestFocus();
            shakeView(etVendorId);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Verificando...");

        Log.d(TAG, "Intentando login con ID: " + vendorId);

        VendorLoginRequest request = new VendorLoginRequest(vendorId);
        ApiService apiService = RetrofitClient.getInstance(this).getApiService();

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Ingresar");

                Log.d(TAG, "Response code: " + response.code());
                Log.d(TAG, "Response isSuccessful: " + response.isSuccessful());

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse data = response.body();
                    Log.d(TAG, "Login success: " + data.isSuccess());
                    Log.d(TAG, "Token: " + (data.getToken() != null ? "PRESENTE" : "NULL"));
                    Log.d(TAG, "Vendor ID: " + (data.getVendor() != null ? data.getVendor().getId() : "NULL"));
                    Log.d(TAG, "Vendor Name: " + (data.getVendor() != null ? data.getVendor().getName() : "NULL"));

                    if (data.isSuccess() && data.getToken() != null && data.getVendor() != null) {
                        // Guardar sesión
                        sessionManager.saveUser(
                            data.getToken(),
                            data.getVendor().getId(),
                            data.getVendor().getName(),
                            data.getVendor().getBusinessName()
                        );

                        Toast.makeText(LoginActivity.this,
                            "✅ ¡Bienvenido " + data.getVendor().getName() + "!",
                            Toast.LENGTH_LONG).show();

                        Log.d(TAG, "Sesión guardada correctamente");
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        String msg = data.getMessage() != null ? data.getMessage() : "Error de autenticación";
                        Log.e(TAG, "Error en login: " + msg);
                        Toast.makeText(LoginActivity.this, "❌ " + msg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    String errorMsg = "Error de autenticación";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            Log.e(TAG, "Error body: " + errorBody);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error body: " + e.getMessage());
                    }
                    Toast.makeText(LoginActivity.this, "❌ " + errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Ingresar");
                Log.e(TAG, "Network error: " + t.getMessage(), t);
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
