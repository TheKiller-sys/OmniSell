package com.omniventas.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import com.omniventas.app.models.RespuestaProductos;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventarioFragment extends Fragment {

    private RecyclerView rvInventario;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalProductos, tvStockBajo, tvSinStock, tvInventarioVacio;
    private EditText etBuscarProducto;
    private Button btnLimpiarFiltro;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private InventarioAdapter inventarioAdapter;
    private List<Producto> productosOriginales = new ArrayList<>();
    private List<Producto> productosFiltrados = new ArrayList<>();

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
        etBuscarProducto = view.findViewById(R.id.et_buscar_producto);
        btnLimpiarFiltro = view.findViewById(R.id.btn_limpiar_filtro);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());

        inventarioAdapter = new InventarioAdapter();
        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));
        rvInventario.setAdapter(inventarioAdapter);

        swipeRefresh.setOnRefreshListener(this::cargarInventario);

        btnLimpiarFiltro.setOnClickListener(v -> {
            etBuscarProducto.setText("");
            aplicarFiltros();
        });

        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { aplicarFiltros(); }
        });

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
        apiService.getProductos("Bearer " + token).enqueue(new Callback<RespuestaProductos>() {
            @Override
            public void onResponse(Call<RespuestaProductos> call, Response<RespuestaProductos> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    productosOriginales = response.body().getProductos();
                    Collections.sort(productosOriginales, (p1, p2) -> p1.getNombre().compareToIgnoreCase(p2.getNombre()));
                    productosFiltrados = new ArrayList<>(productosOriginales);
                    actualizarUI();
                } else {
                    Toast.makeText(getContext(), "Error al cargar inventario", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RespuestaProductos> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error de conexión", Toast.LENGTH_SHORT).show();
                logger.networkError(t);
            }
        });
    }

    private void aplicarFiltros() {
        String query = etBuscarProducto.getText().toString().toLowerCase().trim();

        productosFiltrados = new ArrayList<>();
        for (Producto p : productosOriginales) {
            if (query.isEmpty()) {
                productosFiltrados.add(p);
            } else {
                if (p.getNombre().toLowerCase().contains(query) ||
                    (p.getSeccion() != null && p.getSeccion().toLowerCase().contains(query))) {
                    productosFiltrados.add(p);
                }
            }
        }

        actualizarUI();
    }

    private void actualizarUI() {
        Collections.sort(productosFiltrados, (p1, p2) -> p1.getNombre().compareToIgnoreCase(p2.getNombre()));
        inventarioAdapter.setProductos(productosFiltrados);

        int total = productosFiltrados.size();
        int bajo = 0, sinStock = 0;
        for (Producto p : productosFiltrados) {
            if (p.getStock() == 0) sinStock++;
            else if (p.getStock() <= 3) bajo++;
        }

        tvTotalProductos.setText(String.valueOf(total));
        tvStockBajo.setText(String.valueOf(bajo));
        tvSinStock.setText(String.valueOf(sinStock));

        if (productosFiltrados.isEmpty()) {
            tvInventarioVacio.setVisibility(View.VISIBLE);
            rvInventario.setVisibility(View.GONE);
        } else {
            tvInventarioVacio.setVisibility(View.GONE);
            rvInventario.setVisibility(View.VISIBLE);
        }
    }
}
