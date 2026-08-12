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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;

public class VentasFragment extends Fragment {

    private EditText etProducto, etCantidad, etPrecio;
    private TextView tvTotal;
    private Button btnRegistrarVenta;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ventas, container, false);

        etProducto = view.findViewById(R.id.et_producto);
        etCantidad = view.findViewById(R.id.et_cantidad);
        etPrecio = view.findViewById(R.id.et_precio);
        tvTotal = view.findViewById(R.id.tv_total);
        btnRegistrarVenta = view.findViewById(R.id.btn_registrar_venta);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { calcularTotal(); }
        };

        etCantidad.addTextChangedListener(watcher);
        etPrecio.addTextChangedListener(watcher);

        btnRegistrarVenta.setOnClickListener(v -> {
            Toast.makeText(getContext(), "✅ Venta registrada!", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void calcularTotal() {
        try {
            int cantidad = Integer.parseInt(etCantidad.getText().toString());
            double precio = Double.parseDouble(etPrecio.getText().toString());
            double total = cantidad * precio;
            tvTotal.setText("Total: $" + String.format("%.2f", total));
        } catch (NumberFormatException e) {
            tvTotal.setText("Total: $0.00");
        }
    }
}
