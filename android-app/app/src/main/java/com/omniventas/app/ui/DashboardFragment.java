package com.omniventas.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.omniventas.app.adapters.VentaAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.Venta;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private TextView tvVentasHoy, tvIngresosHoy, tvVentasMes, tvIngresosMes, tvBajoStock;
    private RecyclerView rvVentasRecientes;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private VentaAdapter ventaAdapter;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable actualizacionAutomatica;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        tvVentasHoy = view.findViewById(R.id.tv_ventas_hoy);
        tvIngresosHoy = view.findViewById(R.id.tv_ingresos_hoy);
        tvVentasMes = view.findViewById(R.id.tv_ventas_mes);
        tvIngresosMes = view.findViewById(R.id.tv_ingresos_mes);
        tvBajoStock = view.findViewById(R.id.tv_bajo_stock);
        rvVentasRecientes = view.findViewById(R.id.rv_ventas_recientes);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());

        ventaAdapter = new VentaAdapter();
        rvVentasRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVentasRecientes.setAdapter(ventaAdapter);

        swipeRefresh.setOnRefreshListener(this::cargarDashboard);

        actualizacionAutomatica = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    cargarDashboard();
                    handler.postDelayed(this, 10000);
                }
            }
        };
        handler.postDelayed(actualizacionAutomatica, 10000);

        cargarDashboard();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(actualizacionAutomatica);
    }

    public void cargarDashboard() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(getContext(), "Sesión expirada", Toast.LENGTH_SHORT).show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getDashboard("Bearer " + token).enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    DashboardResponse.DashboardData data = response.body().getDashboard();
                    tvVentasHoy.setText(String.valueOf(data.getVentasHoy()));
                    tvIngresosHoy.setText("$" + String.format("%.2f", data.getIngresosHoy()));
                    tvVentasMes.setText(String.valueOf(data.getVentasMes()));
                    tvIngresosMes.setText("$" + String.format("%.2f", data.getIngresosMes()));
                    tvBajoStock.setText(String.valueOf(data.getProductosBajoStock()));

                    List<Venta> ventas = data.getVentasRecientes();
                    if (ventas != null) {
                        if (ventas.size() > 5) {
                            ventas = ventas.subList(0, 5);
                        }
                        ventaAdapter.setVentas(ventas);
                    }
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                logger.networkError(t);
            }
        });
    }
}
