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
import com.omniventas.app.local.ProductoEntity;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.List;

public class InventarioFragment extends Fragment {
    private RecyclerView rvInventario;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalProductos, tvStockBajo, tvSinStock, tvInventarioVacio, tvOfflineIndicator;
    private EditText etBuscarProducto;
    private Button btnLimpiarFiltro;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private InventarioAdapter inventarioAdapter;
    private OmniVentasRepository repository;
    private List<ProductoEntity> productosFiltrados = new ArrayList<>();

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
        tvOfflineIndicator = view.findViewById(R.id.tv_offline_indicator);
        etBuscarProducto = view.findViewById(R.id.et_buscar_producto);
        btnLimpiarFiltro = view.findViewById(R.id.btn_limpiar_filtro);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());
        repository = new OmniVentasRepository(getContext());

        inventarioAdapter = new InventarioAdapter();
        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));
        rvInventario.setAdapter(inventarioAdapter);

        swipeRefresh.setOnRefreshListener(() -> {
            repository.syncProductosFromServer();
            cargarInventarioLocal();
            swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "Inventario actualizado", Toast.LENGTH_SHORT).show();
        });

        btnLimpiarFiltro.setOnClickListener(v -> {
            etBuscarProducto.setText("");
            aplicarFiltros();
        });

        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { aplicarFiltros(); }
        });

        cargarInventarioLocal();

        // Intentar sincronizar en segundo plano
        repository.syncProductosFromServer();

        return view;
    }

    private void cargarInventarioLocal() {
        List<ProductoEntity> productos = repository.getProductosLocal();
        productosFiltrados = new ArrayList<>(productos);
        actualizarUI();
        
        tvOfflineIndicator.setText("📡 Datos locales (" + productos.size() + " productos)");
    }

    private void aplicarFiltros() {
        String query = etBuscarProducto.getText().toString().toLowerCase().trim();
        productosFiltrados = repository.buscarProductosLocal(query);
        actualizarUI();
    }

    private void actualizarUI() {
        inventarioAdapter.setProductos(productosFiltrados);

        int total = productosFiltrados.size();
        int bajo = 0, sinStock = 0;
        for (ProductoEntity p : productosFiltrados) {
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
