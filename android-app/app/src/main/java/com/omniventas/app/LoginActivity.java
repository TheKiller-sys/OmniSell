package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class LoginActivity extends AppCompatActivity {

    private EditText etVendorId;
    private Button btnLogin;
    private CardView cardLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

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

        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void performLogin() {
        String vendorId = etVendorId.getText().toString().trim();

        if (vendorId.isEmpty()) {
            etVendorId.setError("Ingresa tu ID de vendedor");
            etVendorId.requestFocus();
            shakeView(etVendorId);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Verificando...");

        // Simular verificación del ID
        btnLogin.postDelayed(() -> {
            Toast.makeText(LoginActivity.this, "✅ ¡Bienvenido Vendedor!", Toast.LENGTH_LONG).show();
            btnLogin.setEnabled(true);
            btnLogin.setText("Ingresar");
        }, 1500);
    }

    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0f, -20f, 20f, -20f, 20f, -10f, 10f, 0f);
        shake.setDuration(500);
        shake.start();
    }
}
