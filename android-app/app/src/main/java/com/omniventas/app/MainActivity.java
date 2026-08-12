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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNav;
    private long backPressedTime = 0;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "=== 🚀 MAINACTIVITY INICIADA ===");
        Log.d(TAG, "✅ onCreate ejecutado correctamente");
        
        Toast.makeText(this, "✅ MainActivity cargada", Toast.LENGTH_SHORT).show();

        sessionManager = new SessionManager(this);
        
        // Verificar sesión
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "✅ Sesión activa encontrada");
            Log.d(TAG, "Vendor: " + sessionManager.getVendorName());
            Log.d(TAG, "Business: " + sessionManager.getBusinessName());
        } else {
            Log.w(TAG, "⚠️ Sin sesión activa - creando una de prueba");
            sessionManager.saveUser(
                "token_de_prueba_123456",
                "AAAA0000",
                "Vendedor Prueba",
                "Tienda de Prueba"
            );
        }

        bottomNav = findViewById(R.id.bottom_navigation);
        
        if (bottomNav == null) {
            Log.e(TAG, "❌ BottomNavigationView no encontrada!");
        } else {
            Log.d(TAG, "✅ BottomNavigationView encontrada");
            bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);
        }

        // Cargar fragmento inicial
        if (savedInstanceState == null) {
            Log.d(TAG, "Cargando DashboardFragment");
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DashboardFragment())
                .commit();
        }
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        int id = item.getItemId();

        Log.d(TAG, "Navegación: " + item.getTitle());

        if (id == R.id.nav_dashboard) {
            selectedFragment = new DashboardFragment();
        } else if (id == R.id.nav_ventas) {
            selectedFragment = new VentasFragment();
        } else if (id == R.id.nav_inventario) {
            selectedFragment = new InventarioFragment();
        } else if (id == R.id.nav_logout) {
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
        new SessionManager(this).clearSession();
        Toast.makeText(this, "Cerrando sesión...", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed();
            finish();
        } else {
            Toast.makeText(this, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show();
            backPressedTime = System.currentTimeMillis();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - MainActivity visible");
    }
}
