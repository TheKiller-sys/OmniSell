package com.omniventas.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.LoginActivity;
import com.omniventas.app.R;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.sync.SyncManager;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;

public class UsuarioFragment extends Fragment {

    private TextView tvVendorName, tvVendorId;
    private TextView tvTotalVentas, tvProductosVendidos, tvVentasHoy, tvVentasMes;
    private Button btnCerrarSesion, btnSyncManual;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usuario, container, false);

        tvVendorName = view.findViewById(R.id.tv_vendor_name);
        tvVendorId = view.findViewById(R.id.tv_vendor_id);
        tvTotalVentas = view.findViewById(R.id.tv_total_ventas_vendedor);
        tvProductosVendidos = view.findViewById(R.id.tv_productos_vendidos);
        tvVentasHoy = view.findViewById(R.id.tv_ventas_hoy_vendedor);
        tvVentasMes = view.findViewById(R.id.tv_ventas_mes_vendedor);
        btnCerrarSesion = view.findViewById(R.id.btn_cerrar_sesion);
        btnSyncManual = view.findViewById(R.id.btn_sync_manual);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());
        repository = new OmniVentasRepository(getContext());

        String nombre = sessionManager.getVendorName();
        String id = sessionManager.getVendorId();

        if (nombre != null) {
            tvVendorName.setText(nombre);
        }
        if (id != null) {
            tvVendorId.setText("ID: " + id);
        }

        // Estadísticas locales
        int pendientes = repository.getVentasPendientesCount();
        tvVentasHoy.setText(String.valueOf(pendientes));
        
        int totalProductos = repository.getTotalProductos();
        tvTotalVentas.setText(String.valueOf(pendientes));
        tvProductosVendidos.setText(String.valueOf(totalProductos));

        btnSyncManual.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Sincronizando...", Toast.LENGTH_SHORT).show();
            SyncManager.syncNow(getContext());
            Toast.makeText(getContext(), "Sincronización iniciada", Toast.LENGTH_SHORT).show();
        });

        btnCerrarSesion.setOnClickListener(v -> {
            sessionManager.clearSession();
            logger.info("Sesión cerrada por el usuario");
            Toast.makeText(getContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        });

        swipeRefresh.setOnRefreshListener(() -> {
            SyncManager.syncNow(getContext());
            swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "Sincronización iniciada", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}
