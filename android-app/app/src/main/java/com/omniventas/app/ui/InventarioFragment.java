package com.omniventas.app.ui;

import android.os.Bundle;
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
import com.omniventas.app.adapters.ProductoAdapter;
import com.omniventas.app.models.Producto;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.List;

public class InventarioFragment extends Fragment {

    private static final String TAG = "InventarioFragment";
    private RecyclerView rvInventario;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalProductos, tvBajoStock;
    private ProductoAdapter adapter;
    private TelegramLogger logger;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inventario, container, false);

        Log.d(TAG, "=== onCreateView InventarioFragment ===");

        logger = TelegramLogger.getInstance(getContext());
        logger.info("📦 InventarioFragment cargado (MODO PRUEBA)");

        rvInventario = view.findViewById(R.id.rv_inventario);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        tvTotalProductos = view.findViewById(R.id.tv_total_productos);
        tvBajoStock = view.findViewById(R.id.tv_bajo_stock);

        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));

        // ====================================================
        // 🔥 MODO PRUEBA: DATOS DE EJEMPLO
        // ====================================================
        List<Producto> productos = new ArrayList<>();
        
        Producto p1 = new Producto();
        p1.setId(1);
        p1.setNombre("Producto A");
        p1.setSeccion("Electrónicos");
        p1.setPrecio(10.00);
        p1.setStock(15);
        productos.add(p1);
        
        Producto p2 = new Producto();
        p2.setId(2);
        p2.setNombre("Producto B");
        p2.setSeccion("Ropa");
        p2.setPrecio(25.00);
        p2.setStock(3);
        productos.add(p2);
        
        Producto p3 = new Producto();
        p3.setId(3);
        p3.setNombre("Producto C");
        p3.setSeccion("Alimentos");
        p3.setPrecio(15.00);
        p3.setStock(8);
        productos.add(p3);

        adapter = new ProductoAdapter(productos);
        rvInventario.setAdapter(adapter);

        tvTotalProductos.setText("Total: " + productos.size() + " productos");
        tvBajoStock.setText("⚠️ Stock bajo: 1");

        swipeRefresh.setOnRefreshListener(() -> {
            Log.d(TAG, "SwipeRefresh en Inventario (MODO PRUEBA)");
            swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "🔄 Datos recargados (MODO PRUEBA)", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}
