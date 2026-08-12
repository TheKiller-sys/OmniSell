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

        // Inicializar
        sessionManager = new SessionManager(this);
        logger = TelegramLogger.getInstance(this);

        // Limpiar sesión anterior
        sessionManager.clearSession();
        Log.d(TAG, "✅ Sesión limpiada");

        // Buscar vistas
        etVendorId = findViewById(R.id.et_vendor_id);
        btnLogin = findViewById(R.id.btn_login);
        cardLogin = findViewById(R.id.card_login);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tv_error);

        if (etVendorId != null) {
            etVendorId.setText("AAAA0000");
            Log.d(TAG, "✅ ID de prueba: AAAA0000");
        }

        if (tvError != null) tvError.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        // Animación
        if (cardLogin != null) {
            cardLogin.setTranslationY(100f);
            cardLogin.setAlpha(0f);
            cardLogin.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(600)
                    .start();
        }

        // 🔥 Botón de login CON descarga de logs
        btnLogin.setOnClickListener(v -> {
            Log.d(TAG, "🔥🔥🔥 BOTÓN PRESIONADO 🔥🔥🔥");
            iniciarCapturaLogs();
            realizarLogin();
        });

        // Login con Enter
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

        Log.d(TAG, "=== onCreate FIN ===");
    }

    // ==================== MÉTODO PARA CAPTURAR LOGS ====================

    private void iniciarCapturaLogs() {
        logBuilder = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        logBuilder.append("========================================\n");
        logBuilder.append("📱 OMNIVENTAS - LOG DE DEPURACIÓN\n");
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

    /**
     * 🔥 Guarda el archivo de logs en la carpeta DOWNLOAD del dispositivo
     */
    private void guardarYDescargarLogs() {
        try {
            agregarLog("💾 Guardando archivo de logs en Download...");

            // Obtener la carpeta Download
            File downloadDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ usa Environment.getExternalStoragePublicDirectory
                downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            } else {
                downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            }

            // Crear directorio si no existe
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            // Nombre del archivo con timestamp
            String fileName = "omniventas_login_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            File logFile = new File(downloadDir, fileName);

            // Guardar logs
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile));
            writer.write(logBuilder.toString());
            writer.close();

            agregarLog("✅ Archivo guardado en Download: " + logFile.getAbsolutePath());
            agregarLog("📏 Tamaño: " + logFile.length() + " bytes");

            // Mostrar mensaje con la ubicación
            String mensaje = "✅ Logs guardados en:\n" + logFile.getAbsolutePath();
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();

            // Opcional: Abrir el archivo con un visor de texto
            abrirArchivoLogs(logFile);

        } catch (IOException e) {
            Log.e(TAG, "❌ Error guardando logs: " + e.getMessage());
            Toast.makeText(this, "❌ Error guardando logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Abre el archivo de logs con el visor de texto del sistema
     */
    private void abrirArchivoLogs(File logFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                    FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", logFile),
                    "text/plain"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "📄 Abrir archivo de logs con..."));
            agregarLog("📄 Abriendo archivo de logs...");
        } catch (Exception e) {
            Log.e(TAG, "Error abriendo archivo: " + e.getMessage());
            // No mostrar Toast para no saturar
        }
    }

    // ==================== MÉTODO DE LOGIN ====================

    private void realizarLogin() {
        agregarLog("=== Iniciando login ===");

        String vendorId = etVendorId.getText().toString().trim().toUpperCase();
        agregarLog("📝 Vendor ID ingresado: '" + vendorId + "'");

        // Validar
        if (vendorId.isEmpty()) {
            agregarLog("❌ Error: ID vacío");
            mostrarError("Ingresa tu ID de vendedor");
            shakeView(etVendorId);
            guardarYDescargarLogs();
            return;
        }

        if (vendorId.length() != 8) {
            agregarLog("❌ Error: ID tiene " + vendorId.length() + " caracteres (debe tener 8)");
            mostrarError("El ID debe tener 8 caracteres");
            shakeView(etVendorId);
            guardarYDescargarLogs();
            return;
        }

        if (!vendorId.matches("[A-Z0-9]+")) {
            agregarLog("❌ Error: ID contiene caracteres inválidos");
            mostrarError("Solo letras y números");
            shakeView(etVendorId);
            guardarYDescargarLogs();
            return;
        }

        agregarLog("✅ Validaciones pasadas");

        // Mostrar loading
        setLoading(true);
        ocultarError();

        agregarLog("🔄 Llamando a la API: " + RetrofitClient.getApiUrl());

        // 📡 Llamada a la API
        ApiService apiService = RetrofitClient.getInstance(this).getApiService();
        VendorLoginRequest request = new VendorLoginRequest(vendorId);

        apiService.loginVendor(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);

                agregarLog("📡 Respuesta recibida");
                agregarLog("   Código HTTP: " + response.code());
                agregarLog("   isSuccessful: " + response.isSuccessful());

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    agregarLog("   loginResponse.success: " + loginResponse.isSuccess());
                    agregarLog("   loginResponse.message: " + loginResponse.getMessage());

                    if (loginResponse.isSuccess()) {
                        String token = loginResponse.getToken();
                        LoginResponse.Vendor vendor = loginResponse.getVendor();

                        agregarLog("✅ LOGIN EXITOSO!");
                        agregarLog("   Token: " + (token != null ? "PRESENTE (" + token.substring(0, Math.min(15, token.length())) + "...)" : "NULL"));
                        agregarLog("   Vendor ID: " + (vendor != null ? vendor.getId() : "NULL"));
                        agregarLog("   Vendor Name: " + (vendor != null ? vendor.getName() : "NULL"));
                        agregarLog("   Business: " + (vendor != null ? vendor.getBusinessName() : "NULL"));

                        if (vendor != null && token != null && !token.isEmpty()) {
                            // Guardar sesión
                            sessionManager.saveUser(
                                    token,
                                    vendor.getId(),
                                    vendor.getName(),
                                    vendor.getBusinessName()
                            );
                            agregarLog("✅ Sesión guardada");

                            // Verificar token guardado
                            String savedToken = sessionManager.getToken();
                            agregarLog("🔑 Token guardado: " + (savedToken != null ? "✅ PRESENTE" : "❌ NULL"));

                            // Log de éxito
                            logger.success("Login exitoso: " + vendor.getName());

                            // 🔥 GUARDAR LOGS Y NAVEGAR
                            agregarLog("🚀 Navegando al Dashboard...");

                            // Guardar logs en Download
                            guardarYDescargarLogs();

                            // Ir al Dashboard
                            irAlDashboard();
                        } else {
                            agregarLog("❌ Error: Token o Vendor es NULL");
                            mostrarError("Error: datos de login incompletos");
                            guardarYDescargarLogs();
                        }

                    } else {
                        String mensaje = loginResponse.getMessage() != null ? loginResponse.getMessage() : "ID inválido";
                        agregarLog("❌ Login fallido: " + mensaje);
                        mostrarError(mensaje);
                        shakeView(cardLogin);
                        guardarYDescargarLogs();
                    }

                } else {
                    String mensaje = "Error del servidor. Código: " + response.code();
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
                mostrarError("Error de conexión. Verifica tu internet.");
                shakeView(cardLogin);
                guardarYDescargarLogs();
            }
        });
    }

    private void irAlDashboard() {
        agregarLog("🚀🚀🚀 NAVEGANDO AL DASHBOARD 🚀🚀🚀");
        try {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            agregarLog("✅ Dashboard iniciado correctamente");
        } catch (Exception e) {
            agregarLog("❌ Error navegando: " + e.getMessage());
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==================== MÉTODOS UI ====================

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
            Log.d(TAG, "🔄 Sesión activa, redirigiendo al Dashboard");
            irAlDashboard();
        }
    }
}
