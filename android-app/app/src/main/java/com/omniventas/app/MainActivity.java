package com.omniventas.app;

import android.os.Bundle;
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
    private BottomNavigationView bottomNav;
    private long backPressedTime = 0;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private LinearLayout llOfflineIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        logger = TelegramLogger.getInstance(this);

        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        llOfflineIndicator = findViewById(R.id.ll_offline_indicator);
        
        checkConnectivity();

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DashboardFragment(), "dashboard")
                .commit();
        }

        SyncManager.scheduleSync(this);
    }

    private void checkConnectivity() {
        llOfflineIndicator.setVisibility(View.VISIBLE);
        SyncManager.syncNow(this);
        
        android.os.Handler handler = new android.os.Handler();
        handler.postDelayed(() -> {
            llOfflineIndicator.setVisibility(View.GONE);
        }, 3000);
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        String tag = "";
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
            return true;
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
