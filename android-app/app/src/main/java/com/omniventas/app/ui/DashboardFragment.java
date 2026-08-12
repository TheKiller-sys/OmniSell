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
import com.omniventas.app.models.VentaReciente;
import com.omniventas.app.utils.SessionManager;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private TextView tvVentasHoy, tvIngresosHoy, tvVentasMes, tvIngresosMes, tvBajoStock;
    private RecyclerView rvVentasRecientes;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;
    private VentasRecientesAdapter adapter;

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
        
        rvVentasRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VentasRecientesAdapter();
        rvVentasRecientes.setAdapter(adapter);
        
        swipeRefresh.setOnRefreshListener(this::cargarDashboard);
        
        cargarDashboard();
        
        return view;
    }

    private void cargarDashboard() {
        String token = sessionManager.getToken();
        Call<DashboardResponse> call = RetrofitClient.getInstance().getApiService().getDashboard("Bearer " + token);
        
        call.enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                swipeRefresh.setRefreshing(false);
                
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    DashboardResponse.DashboardData data = response.body().getDashboard();
                    
                    tvVentasHoy.setText(String.valueOf(data.getVentasHoy()));
                    tvIngresosHoy.setText(String.format("$%.2f", data.getIngresosHoy()));
                    tvVentasMes.setText(String.valueOf(data.getVentasMes()));
                    tvIngresosMes.setText(String.format("$%.2f", data.getIngresosMes()));
                    tvBajoStock.setText(String.valueOf(data.getProductosBajoStock()));
                    
                    adapter.setVentas(data.getVentasRecientes());
                } else {
                    Toast.makeText(getContext(), "Error al cargar datos", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error de conexión: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Adapter para ventas recientes
    private static class VentasRecientesAdapter extends RecyclerView.Adapter<VentasRecientesAdapter.ViewHolder> {
        private List<VentaReciente> ventas;

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_venta_reciente, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VentaReciente venta = ventas.get(position);
            holder.tvProducto.setText(venta.getProducto());
            holder.tvCantidad.setText(venta.getCantidad() + "x");
            holder.tvTotal.setText(String.format("$%.2f", venta.getTotal()));
            holder.tvFecha.setText(venta.getFecha());
        }

        @Override
        public int getItemCount() {
            return ventas != null ? ventas.size() : 0;
        }

        public void setVentas(List<VentaReciente> ventas) {
            this.ventas = ventas;
            notifyDataSetChanged();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvProducto, tvCantidad, tvTotal, tvFecha;
            ViewHolder(View itemView) {
                super(itemView);
                tvProducto = itemView.findViewById(R.id.tv_producto);
                tvCantidad = itemView.findViewById(R.id.tv_cantidad);
                tvTotal = itemView.findViewById(R.id.tv_total);
                tvFecha = itemView.findViewById(R.id.tv_fecha);
            }
        }
    }
}
