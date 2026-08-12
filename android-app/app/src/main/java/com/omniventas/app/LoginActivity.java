package com.omniventas.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.models.VendorLoginRequest;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "=== onCreate ===");

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

        if (tvError != null) tvError.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        if (cardLogin != null) {
            cardLogin.setTranslationY(100f);
            cardLogin.setAlpha(0f);
            cardLogin.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(600)
                    .start();
        }

        btnLogin.setOnClickListener(v -> {
            Log.d(TAG, "🔥 Boton presionado");
            iniciarCapturaLogs();
            realizarLogin();
        });

        if (etVendorId != null) {
            etVendorId.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    iniciarCapturaLogs();
                    realizarLogin();
                    return true;
                }
                return false;
            });
        }
    }

    private void iniciarCapturaLogs() {
        logBuilder = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        logBuilder.append("========================================\n");
        logBuilder.append("📱 OMNIVENTAS - LOG DE DEPURACION\n");
        logBuilder.append("========================================\n");
        logBuilder.append("📅 Fecha: ").append(timestamp).append("\n");
        logBuilder.append("📱 Dispositivo: ").append(Build.MANUFACTURER)
                .append(" ").append(Build.MODEL).append("\n");
        logBuilder.append("🤖 Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        logBuilder.append("📦 App Version: ").append(getAppVersion()).append("\n");
        logBuilder.append("========================================\n\n");

        agregarLog("🚀 Iniciando proceso de login...");
        String vendorId = etVendorId != null ? etVendorId.getText().toString().trim().toUpperCase() : "DESCONOCIDO";
        agregarLog("📝 Vendor ID: " + vendorId);
    }

    private void agregarLog(String mensaje) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        logBuilder.append("[").append(timestamp).append("] ").append(mensaje).append("\n");
        Log.d(TAG, mensaje);
    }

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    private void guardarYDescargarLogs() {
        try {
            agregarLog("💾 Guardando archivo de logs en Download...");

            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            String fileName = "omniventas_login_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            File logFile = new File(downloadDir, fileName);

            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile));
            writer.write(logBuilder.toString());
            writer.close();

            agregarLog("✅ Archivo guardado en Download: " + logFile.getAbsolutePath());
            Toast.makeText(this, "✅ Logs guardados en Download", Toast.LENGTH_LONG).show();

            abrirArchivoLogs(logFile);

        } catch (IOException e) {
            Log.e(TAG, "❌ Error guardando logs: " + e.getMessage());
            Toast.makeText(this, "❌ Error guardando logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void abrirArchivoLogs(File logFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                    FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", logFile),
                    "text/plain"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "📄 Abrir logs con..."));
        } catch (Exception e) {
            Log.e(TAG, "Error abriendo archivo: " + e.getMessage());
        }
    }

    private void realizarLogin() {
        agregarLog("=== Iniciando login ===");

        String vendorId = etVendorId.getText().toString().trim().toUpperCase();
        agregarLog("📝 Vendor ID ingresado: '" + vendorId + "'");

        if (vendorId.isEmpty()) {
            agregarLog("❌ Error: ID vacio");
            mostrarError("Ingresa tu ID de vendedor");
            shakeView(etVendorId);
            guardarYDescargarLogs();
            return;
        }

        if (vendorId.length() != 8) {
            agregarLog("❌ Error: ID tiene " + vendorId.length() + " caracteres");
            mostrarError("El ID debe tener 8 caracteres");
            shakeView(etVendorId);
            guardarYDescargarLogs();
            return;
        }

        if (!vendorId.matches("[A-Z0-9]+")) {
            agregarLog("❌ Error: ID contiene caracteres invalidos");
            mostrarError("Solo letras y numeros");
            shakeView(etVendorId);
            guardarYDescargarLogs();
            return;
        }

        agregarLog("✅ Validaciones pasadas");
        setLoading(true);
        ocultarError();

        agregarLog("🔄 Llamando a la API: " + RetrofitClient.getApiUrl());

        ApiService apiService = RetrofitClient.getInstance(this).getApiService();
        VendorLoginRequest request = new VendorLoginRequest(vendorId);

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);
                agregarLog("📡 Respuesta recibida - Codigo: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    agregarLog("   loginResponse.success: " + loginResponse.isSuccess());

                    if (loginResponse.isSuccess()) {
                        String token = loginResponse.getToken();
                        LoginResponse.Vendor vendor = loginResponse.getVendor();

                        agregarLog("✅ LOGIN EXITOSO!");
                        agregarLog("   Token: " + (token != null ? "PRESENTE" : "NULL"));
                        agregarLog("   Vendor: " + (vendor != null ? vendor.getName() : "NULL"));

                        if (vendor != null && token != null && !token.isEmpty()) {
                            sessionManager.saveUser(token, vendor.getId(), vendor.getName(), vendor.getBusinessName());
                            agregarLog("✅ Sesion guardada");

                            logger.success("Login exitoso: " + vendor.getName());
                            guardarYDescargarLogs();
                            irAlDashboard();
                        } else {
                            agregarLog("❌ Error: Token o Vendor es NULL");
                            mostrarError("Error: datos de login incompletos");
                            guardarYDescargarLogs();
                        }

                    } else {
                        String mensaje = loginResponse.getMessage() != null ? loginResponse.getMessage() : "ID invalido";
                        agregarLog("❌ Login fallido: " + mensaje);
                        mostrarError(mensaje);
                        shakeView(cardLogin);
                        guardarYDescargarLogs();
                    }

                } else {
                    String mensaje = "Error del servidor. Codigo: " + response.code();
                    agregarLog("❌ " + mensaje);
                    mostrarError(mensaje);
                    shakeView(cardLogin);
                    guardarYDescargarLogs();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                agregarLog("❌ ERROR DE RED: " + t.getMessage());
                mostrarError("Error de conexion. Verifica tu internet.");
                shakeView(cardLogin);
                guardarYDescargarLogs();
            }
        });
    }

    private void irAlDashboard() {
        agregarLog("🚀 Navegando al Dashboard");
        try {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            agregarLog("✅ Dashboard iniciado");
        } catch (Exception e) {
            agregarLog("❌ Error navegando: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

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
        if (sessionManager.isLoggedIn()) {
            irAlDashboard();
        }
    }
}
