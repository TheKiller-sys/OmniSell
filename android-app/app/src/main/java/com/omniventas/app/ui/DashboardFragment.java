package com.omniventas.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;
import com.omniventas.app.adapters.VentaRecienteAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.VentaReciente;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";
    private TextView tvVentasHoy, tvIngresosHoy, tvVentasMes, tvIngresosMes, tvBajoStock;
    private RecyclerView rvVentasRecientes;
    private SwipeRefreshLayout swipeRefresh;
    private VentaRecienteAdapter adapter;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private Handler handler = new Handler();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        Log.d(TAG, "=== onCreateView DashboardFragment ===");
        
        logger = TelegramLogger.getInstance(getContext());
        logger.info("📊 DashboardFragment cargado (MODO PRUEBA)");

        tvVentasHoy = view.findViewById(R.id.tv_ventas_hoy);
        tvIngresosHoy = view.findViewById(R.id.tv_ingresos_hoy);
        tvVentasMes = view.findViewById(R.id.tv_ventas_mes);
        tvIngresosMes = view.findViewById(R.id.tv_ingresos_mes);
        tvBajoStock = view.findViewById(R.id.tv_bajo_stock);
        rvVentasRecientes = view.findViewById(R.id.rv_ventas_recientes);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        sessionManager = new SessionManager(getContext());

        rvVentasRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VentaRecienteAdapter(new ArrayList<>());
        rvVentasRecientes.setAdapter(adapter);

        // ====================================================
        // 🔥 MODO PRUEBA: DATOS DE EJEMPLO
        // ====================================================
        Log.d(TAG, "🔥 MODO PRUEBA: Mostrando datos de ejemplo");
        
        tvVentasHoy.setText("5");
        tvIngresosHoy.setText("$125.50");
        tvVentasMes.setText("42");
        tvIngresosMes.setText("$1,250.00");
        tvBajoStock.setText("3");
        
        // Datos de ejemplo para ventas recientes
        List<VentaReciente> ventasEjemplo = new ArrayList<>();
        VentaReciente v1 = new VentaReciente();
        v1.setProducto("Producto A");
        v1.setCantidad(2);
        v1.setTotal(50.00);
        v1.setFecha("2024-08-12 10:30");
        ventasEjemplo.add(v1);
        
        VentaReciente v2 = new VentaReciente();
        v2.setProducto("Producto B");
        v2.setCantidad(1);
        v2.setTotal(25.00);
        v2.setFecha("2024-08-12 09:15");
        ventasEjemplo.add(v2);
        
        VentaReciente v3 = new VentaReciente();
        v3.setProducto("Producto C");
        v3.setCantidad(3);
        v3.setTotal(75.00);
        v3.setFecha("2024-08-11 16:45");
        ventasEjemplo.add(v3);
        
        adapter.updateData(ventasEjemplo);

        swipeRefresh.setOnRefreshListener(() -> {
            Log.d(TAG, "SwipeRefresh activado (MODO PRUEBA)");
            swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "🔄 Datos de ejemplo recargados", Toast.LENGTH_SHORT).show();
        });

        Toast.makeText(getContext(), "📊 Dashboard cargado (MODO PRUEBA)", Toast.LENGTH_SHORT).show();

        return view;
    }
}
