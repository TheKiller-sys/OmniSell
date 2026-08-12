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
import com.omniventas.app.adapters.InventarioAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.Producto;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventarioFragment extends Fragment {

    private RecyclerView rvInventario;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalProductos, tvStockBajo, tvSinStock, tvInventarioVacio;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private InventarioAdapter inventarioAdapter;
    private List<Producto> productos = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inventario, container, false);

        rvInventario = view.findViewById(R.id.rv_inventario);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        tvTotalProductos = view.findViewById(R.id.tv_total_productos);
        tvStockBajo = view.findViewById(R.id.tv_stock_bajo);
        tvSinStock = view.findViewById(R.id.tv_sin_stock);
        tvInventarioVacio = view.findViewById(R.id.tv_inventario_vacio);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());

        inventarioAdapter = new InventarioAdapter();
        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));
        rvInventario.setAdapter(inventarioAdapter);

        swipeRefresh.setOnRefreshListener(this::cargarInventario);

        cargarInventario();

        return view;
    }

    private void cargarInventario() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(getContext(), "Sesión expirada", Toast.LENGTH_SHORT).show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getProductos("Bearer " + token).enqueue(new Callback<List<Producto>>() {
            @Override
            public void onResponse(Call<List<Producto>> call, Response<List<Producto>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    productos = response.body();
                    inventarioAdapter.setProductos(productos);

                    int total = productos.size();
                    int bajo = 0, sinStock = 0;
                    for (Producto p : productos) {
                        if (p.getStock() == 0) sinStock++;
                        else if (p.getStock() <= 3) bajo++;
                    }

                    tvTotalProductos.setText(String.valueOf(total));
                    tvStockBajo.setText(String.valueOf(bajo));
                    tvSinStock.setText(String.valueOf(sinStock));

                    if (productos.isEmpty()) {
                        tvInventarioVacio.setVisibility(View.VISIBLE);
                        rvInventario.setVisibility(View.GONE);
                    } else {
                        tvInventarioVacio.setVisibility(View.GONE);
                        rvInventario.setVisibility(View.VISIBLE);
                    }
                } else {
                    Toast.makeText(getContext(), "Error al cargar inventario", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Producto>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error de conexión", Toast.LENGTH_SHORT).show();
                logger.networkError(t);
            }
        });
    }
}
