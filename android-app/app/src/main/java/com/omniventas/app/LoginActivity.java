package com.omniventas.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class LoginActivity extends AppCompatActivity {

    private EditText etVendorId;
    private Button btnLogin;
    private CardView cardLogin;
    private View logo, title, subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar vistas
        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);
        logo = findViewById(R.id.iv_logo);
        title = findViewById(R.id.tv_title);
        subtitle = findViewById(R.id.tv_subtitle);

        // Aplicar animaciones de entrada
        animateEntrance();

        // Configurar botón de login
        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void animateEntrance() {
        // Animación del logo
        ObjectAnimator fadeLogo = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f);
        fadeLogo.setDuration(600);
        fadeLogo.setStartDelay(200);
        fadeLogo.start();

        // Animación del título
        ObjectAnimator fadeTitle = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f);
        fadeTitle.setDuration(600);
        fadeTitle.setStartDelay(400);
        fadeTitle.start();

        // Animación del subtítulo
        ObjectAnimator fadeSubtitle = ObjectAnimator.ofFloat(subtitle, "alpha", 0f, 1f);
        fadeSubtitle.setDuration(600);
        fadeSubtitle.setStartDelay(600);
        fadeSubtitle.start();

        // Animación de la tarjeta (deslizamiento hacia arriba)
        cardLogin.setTranslationY(100f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(800)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void performLogin() {
        String vendorId = etVendorId.getText().toString().trim();

        if (vendorId.isEmpty()) {
            // Error shake animation
            etVendorId.setError("Ingresa tu ID de vendedor");
            etVendorId.requestFocus();
            shakeView(etVendorId);
            return;
        }

        // Mostrar estado de carga
        btnLogin.setEnabled(false);
        btnLogin.setText("Verificando...");

        // Simular verificación (aquí iría la llamada a la API)
        btnLogin.postDelayed(() -> {
            // Por ahora solo mostramos éxito
            Toast.makeText(LoginActivity.this, "✅ ¡Bienvenido Vendedor!", Toast.LENGTH_LONG).show();
            
            // Ir a MainActivity
            // startActivity(new Intent(LoginActivity.this, MainActivity.class));
            // finish();
            
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
