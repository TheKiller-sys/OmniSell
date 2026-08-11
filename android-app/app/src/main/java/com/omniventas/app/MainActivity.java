package com.omniventas.app;

import android.content.Intent;
import android.os.Bundle;
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

    private BottomNavigationView bottomNav;
    private long backPressedTime = 0;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DashboardFragment())
                .commit();
        }
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            selectedFragment = new DashboardFragment();
        } else if (id == R.id.nav_ventas) {
            selectedFragment = new VentasFragment();
        } else if (id == R.id.nav_inventario) {
            selectedFragment = new InventarioFragment();
        } else if (id == R.id.nav_logout) {
            sessionManager.clearSession();
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
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
