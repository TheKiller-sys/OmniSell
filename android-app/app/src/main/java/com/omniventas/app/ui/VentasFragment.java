package com.omniventas.app.ui;

import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.List;

public class VentasFragment extends Fragment {

    private Spinner spinnerProductos;
    private EditText etCantidad;
    private TextView tvPrecio, tvTotal;
    private Button btnRegistrarVenta;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ventas, container, false);

        spinnerProductos = view.findViewById(R.id.spinner_productos);
        etCantidad = view.findViewById(R.id.et_cantidad);
        tvPrecio = view.findViewById(R.id.tv_precio);
        tvTotal = view.findViewById(R.id.tv_total);
        btnRegistrarVenta = view.findViewById(R.id.btn_registrar_venta);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        List<String> productos = new ArrayList<>();
        productos.add("Seleccionar producto...");
        productos.add("Producto A - 0.00");
        productos.add("Producto B - 5.00");
        productos.add("Producto C - 0.00");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, productos);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProductos.setAdapter(adapter);

        spinnerProductos.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    tvPrecio.setText("0.00");
                    actualizarTotal();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        etCantidad.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { actualizarTotal(); }
        });

        btnRegistrarVenta.setOnClickListener(v -> {
            Toast.makeText(getContext(), "✅ Venta registrada!", Toast.LENGTH_SHORT).show();
        });

        tvPrecio.setText("/home/runner/work/_temp/27528a24-b2bb-4b09-97ad-f3ebf236557f.sh.00");
        tvTotal.setText("Total: /home/runner/work/_temp/27528a24-b2bb-4b09-97ad-f3ebf236557f.sh.00");

        return view;
    }

    private void actualizarTotal() {
        try {
            int cantidad = Integer.parseInt(etCantidad.getText().toString());
            double total = cantidad * 10.00;
            tvTotal.setText(String.format("Total: $%.2f", total));
        } catch (NumberFormatException e) {
            tvTotal.setText("Total: /home/runner/work/_temp/27528a24-b2bb-4b09-97ad-f3ebf236557f.sh.00");
        }
    }
}
