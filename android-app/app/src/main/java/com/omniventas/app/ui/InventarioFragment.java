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
import com.omniventas.app.adapters.ProductoAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.Producto;
import com.omniventas.app.utils.SessionManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventarioFragment extends Fragment {

    private RecyclerView rvInventario;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalProductos, tvBajoStock;
    private ProductoAdapter adapter;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inventario, container, false);

        rvInventario = view.findViewById(R.id.rv_inventario);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        tvTotalProductos = view.findViewById(R.id.tv_total_productos);
        tvBajoStock = view.findViewById(R.id.tv_bajo_stock);

        sessionManager = new SessionManager(getContext());

        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProductoAdapter(new ArrayList<>());
        rvInventario.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::cargarInventario);

        cargarInventario();

        return view;
    }

    private void cargarInventario() {
        swipeRefresh.setRefreshing(true);

        String token = sessionManager.getToken();
        if (token == null) {
            swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "Sesión expirada", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();

        apiService.getProductos(token).enqueue(new Callback<List<Producto>>() {
            @Override
            public void onResponse(Call<List<Producto>> call, Response<List<Producto>> response) {
                swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<Producto> productos = response.body();
                    adapter.updateData(productos);

                    int total = productos.size();
                    int bajoStock = 0;
                    for (Producto p : productos) {
                        if (p.getStock() <= 3) bajoStock++;
                    }

                    tvTotalProductos.setText("Total: " + total + " productos");
                    tvBajoStock.setText("⚠️ Stock bajo: " + bajoStock);
                } else {
                    Toast.makeText(getContext(), "Error al cargar inventario", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Producto>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error de conexión: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
