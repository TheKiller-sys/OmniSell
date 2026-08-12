package com.omniventas.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.omniventas.app.ui.DashboardFragment;
import com.omniventas.app.ui.VentasFragment;
import com.omniventas.app.ui.InventarioFragment;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNav;
    private long backPressedTime = 0;
    private SessionManager sessionManager;
    private TelegramLogger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "=== onCreate MainActivity ===");
        Log.d(TAG, "✅ MainActivity INICIADA CORRECTAMENTE");
        
        logger = TelegramLogger.getInstance(this);
        logger.info("📱 MainActivity iniciada (MODO PRUEBA)");

        sessionManager = new SessionManager(this);

        // Verificar sesión activa - EN MODO PRUEBA SIEMPRE PASAMOS
        if (!sessionManager.isLoggedIn()) {
            Log.e(TAG, "⚠️ Sin sesión, pero en MODO PRUEBA la creamos");
            // Crear sesión de prueba si no existe
            sessionManager.saveUser(
                "token_de_prueba_123456",
                "AAAA0000",
                "Vendedor Prueba",
                "Tienda de Prueba"
            );
        }

        Log.d(TAG, "✅ Sesión activa (MODO PRUEBA):");
        Log.d(TAG, "Vendor ID: " + sessionManager.getVendorId());
        Log.d(TAG, "Vendor Name: " + sessionManager.getVendorName());
        Log.d(TAG, "Business: " + sessionManager.getBusinessName());

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);

        // Cargar fragmento inicial
        if (savedInstanceState == null) {
            Log.d(TAG, "Cargando DashboardFragment");
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DashboardFragment())
                .commit();
        }
        
        Toast.makeText(this, "✅ MODO PRUEBA: Dashboard cargado", Toast.LENGTH_SHORT).show();
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            Log.d(TAG, "Navegando a Dashboard");
            selectedFragment = new DashboardFragment();
        } else if (id == R.id.nav_ventas) {
            Log.d(TAG, "Navegando a Ventas");
            selectedFragment = new VentasFragment();
        } else if (id == R.id.nav_inventario) {
            Log.d(TAG, "Navegando a Inventario");
            selectedFragment = new InventarioFragment();
        } else if (id == R.id.nav_logout) {
            Log.d(TAG, "Cerrando sesión");
            logout();
            return true;
        }

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, selectedFragment)
                .commit();
            return true;
        }
        return false;
    }

    private void logout() {
        sessionManager.clearSession();
        Toast.makeText(this, "Cerrando sesión...", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            Log.d(TAG, "App cerrada por usuario");
            super.onBackPressed();
            finish();
        } else {
            Toast.makeText(this, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show();
            backPressedTime = System.currentTimeMillis();
        }
    }
}
