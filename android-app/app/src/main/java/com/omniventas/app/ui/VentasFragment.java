package com.omniventas.app.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.List;

public class VentasFragment extends Fragment {

    private static final String TAG = "VentasFragment";
    private Spinner spinnerProductos;
    private EditText etCantidad;
    private TextView tvPrecio, tvTotal;
    private Button btnRegistrarVenta;
    private SwipeRefreshLayout swipeRefresh;
    private TelegramLogger logger;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ventas, container, false);

        Log.d(TAG, "=== onCreateView VentasFragment ===");

        logger = TelegramLogger.getInstance(getContext());
        logger.info("🧾 VentasFragment cargado (MODO PRUEBA)");

        spinnerProductos = view.findViewById(R.id.spinner_productos);
        etCantidad = view.findViewById(R.id.et_cantidad);
        tvPrecio = view.findViewById(R.id.tv_precio);
        tvTotal = view.findViewById(R.id.tv_total);
        btnRegistrarVenta = view.findViewById(R.id.btn_registrar_venta);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        // ====================================================
        // 🔥 MODO PRUEBA: DATOS DE EJEMPLO
        // ====================================================
        List<String> productos = new ArrayList<>();
        productos.add("Producto A - $10.00");
        productos.add("Producto B - $25.00");
        productos.add("Producto C - $15.00");
        productos.add("Producto D - $30.00");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, productos);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProductos.setAdapter(adapter);

        spinnerProductos.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                String precioStr = selected.substring(selected.indexOf("$") + 1);
                tvPrecio.setText("$" + precioStr);
                actualizarTotal();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        etCantidad.setText("1");
        tvPrecio.setText("$10.00");
        tvTotal.setText("Total: $10.00");

        btnRegistrarVenta.setOnClickListener(v -> {
            Log.d(TAG, "Registrar venta (MODO PRUEBA)");
            Toast.makeText(getContext(), "✅ Venta registrada (MODO PRUEBA)", Toast.LENGTH_LONG).show();
            logger.success("Venta registrada en modo prueba");
        });

        swipeRefresh.setOnRefreshListener(() -> {
            Log.d(TAG, "SwipeRefresh en Ventas (MODO PRUEBA)");
            swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "🔄 Datos recargados (MODO PRUEBA)", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void actualizarTotal() {
        try {
            int cantidad = Integer.parseInt(etCantidad.getText().toString());
            String precioStr = tvPrecio.getText().toString().replace("$", "");
            double precio = Double.parseDouble(precioStr);
            double total = cantidad * precio;
            tvTotal.setText("Total: $" + String.format("%.2f", total));
        } catch (NumberFormatException e) {
            tvTotal.setText("Total: $0.00");
        }
    }
}
