package com.omniventas.app;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.omniventas.app.sync.SyncManager;
import com.omniventas.app.ui.DashboardFragment;
import com.omniventas.app.ui.VentasFragment;
import com.omniventas.app.ui.InventarioFragment;
import com.omniventas.app.ui.UsuarioFragment;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNav;
    private long backPressedTime = 0;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private LinearLayout llOfflineIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate iniciado");
        
        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "setContentView completado");

            sessionManager = new SessionManager(this);
            logger = TelegramLogger.getInstance(this);

            if (!sessionManager.isLoggedIn()) {
                Log.w(TAG, "Sesión expirada");
                Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            Log.d(TAG, "Usuario logueado: " + sessionManager.getVendorName());

            // Inicializar vistas con manejo de null
            llOfflineIndicator = findViewById(R.id.ll_offline_indicator);
            if (llOfflineIndicator == null) {
                Log.e(TAG, "⚠️ ll_offline_indicator no encontrado");
            }
            
            bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav == null) {
                Log.e(TAG, "❌ bottom_navigation no encontrado");
                Toast.makeText(this, "Error: bottom_navigation no encontrado", Toast.LENGTH_LONG).show();
                return;
            }
            
            bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);

            if (savedInstanceState == null) {
                Log.d(TAG, "Cargando DashboardFragment");
                try {
                    getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new DashboardFragment(), "dashboard")
                        .commit();
                    Log.d(TAG, "✅ DashboardFragment cargado");
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error cargando DashboardFragment: " + e.getMessage());
                    e.printStackTrace();
                    Toast.makeText(this, "Error al cargar el dashboard", Toast.LENGTH_SHORT).show();
                }
            }

            SyncManager.scheduleSync(this);
            Log.d(TAG, "✅ onCreate completado correctamente");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreate: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkConnectivity() {
        try {
            if (llOfflineIndicator != null) {
                llOfflineIndicator.setVisibility(View.VISIBLE);
            }
            SyncManager.syncNow(this);
            
            android.os.Handler handler = new android.os.Handler();
            handler.postDelayed(() -> {
                if (llOfflineIndicator != null) {
                    llOfflineIndicator.setVisibility(View.GONE);
                }
            }, 3000);
        } catch (Exception e) {
            Log.e(TAG, "Error en checkConnectivity: " + e.getMessage());
        }
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Log.d(TAG, "onNavigationItemSelected: " + item.getTitle());
        Fragment selectedFragment = null;
        String tag = "";
        
        try {
            if (item.getItemId() == R.id.nav_dashboard) {
                selectedFragment = new DashboardFragment();
                tag = "dashboard";
            } else if (item.getItemId() == R.id.nav_ventas) {
                selectedFragment = new VentasFragment();
                tag = "ventas";
            } else if (item.getItemId() == R.id.nav_inventario) {
                selectedFragment = new InventarioFragment();
                tag = "inventario";
            } else if (item.getItemId() == R.id.nav_usuario) {
                selectedFragment = new UsuarioFragment();
                tag = "usuario";
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment, tag)
                    .commit();
                Log.d(TAG, "✅ Fragmento " + tag + " cargado");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error al cargar fragmento: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error al cargar la sección", Toast.LENGTH_SHORT).show();
        }
        return false;
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
}
