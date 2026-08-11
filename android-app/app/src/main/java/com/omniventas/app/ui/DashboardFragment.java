package com.omniventas.app.ui;

import android.os.Bundle;
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
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.utils.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private TextView tvVentasHoy, tvIngresosHoy, tvVentasMes, tvIngresosMes, tvBajoStock;
    private RecyclerView rvVentasRecientes;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;

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

        rvVentasRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
        sessionManager = new SessionManager(requireContext());

        // Configurar token en Retrofit
        String token = sessionManager.getToken();
        if (token != null) {
            RetrofitClient.getInstance().setAuthToken(token);
        }

        swipeRefresh.setOnRefreshListener(this::cargarDashboard);
        cargarDashboard();

        return view;
    }

    private void cargarDashboard() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        RetrofitClient.getInstance().getApiService().getDashboard(
            "Bearer " + sessionManager.getToken()
        ).enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    DashboardResponse.DashboardData data = response.body().getDashboard();
                    actualizarUI(data);
                } else {
                    Toast.makeText(getContext(), "Error al cargar dashboard", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> call, Throwable t) {
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
                Toast.makeText(getContext(), "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void actualizarUI(DashboardResponse.DashboardData data) {
        tvVentasHoy.setText(String.valueOf(data.getVentasHoy()));
        tvIngresosHoy.setText("$" + data.getIngresosHoy());
        tvVentasMes.setText(String.valueOf(data.getVentasMes()));
        tvIngresosMes.setText("$" + data.getIngresosMes());
        tvBajoStock.setText(String.valueOf(data.getProductosBajoStock()));
        
        // Aquí podrías llenar el RecyclerView con data.getVentasRecientes()
    }
}
