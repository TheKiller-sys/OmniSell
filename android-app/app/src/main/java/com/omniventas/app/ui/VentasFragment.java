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
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.omniventas.app.utils.SessionManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VentasFragment extends Fragment {

    private Spinner spinnerProductos;
    private EditText etCantidad;
    private TextView tvPrecio, tvTotal;
    private Button btnRegistrarVenta;
    private SwipeRefreshLayout swipeRefresh;
    private List<Producto> productos = new ArrayList<>();
    private SessionManager sessionManager;

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
        
        sessionManager = new SessionManager(getContext());
        
        cargarProductos();
        
        spinnerProductos.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    Producto p = productos.get(position - 1);
                    tvPrecio.setText(String.format("$%.2f", p.getPrecio()));
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
        
        btnRegistrarVenta.setOnClickListener(v -> registrarVenta());
        
        swipeRefresh.setOnRefreshListener(this::cargarProductos);
        
        return view;
    }

    private void cargarProductos() {
        swipeRefresh.setRefreshing(true);
        String token = sessionManager.getToken();
        Call<List<Producto>> call = RetrofitClient.getInstance().getApiService().getProductos("Bearer " + token);
        
        call.enqueue(new Callback<List<Producto>>() {
            @Override
            public void onResponse(Call<List<Producto>> call, Response<List<Producto>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    productos.clear();
                    productos.addAll(response.body());
                    
                    List<String> nombres = new ArrayList<>();
                    nombres.add("Seleccionar producto...");
                    for (Producto p : productos) {
                        nombres.add(p.getNombre() + " (Stock: " + p.getStock() + ")");
                    }
                    
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, nombres);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerProductos.setAdapter(adapter);
                } else {
                    Toast.makeText(getContext(), "Error al cargar productos", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Producto>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error de conexión: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void actualizarTotal() {
        try {
            int cantidad = Integer.parseInt(etCantidad.getText().toString());
            int position = spinnerProductos.getSelectedItemPosition();
            if (position > 0) {
                Producto p = productos.get(position - 1);
                double total = cantidad * p.getPrecio();
                tvTotal.setText(String.format("Total: $%.2f", total));
            }
        } catch (NumberFormatException e) {
            tvTotal.setText("Total: $0.00");
        }
    }

    private void registrarVenta() {
        int position = spinnerProductos.getSelectedItemPosition();
        if (position <= 0) {
            Toast.makeText(getContext(), "Selecciona un producto", Toast.LENGTH_SHORT).show();
            return;
        }

        Producto producto = productos.get(position - 1);
        int cantidad;
        try {
            cantidad = Integer.parseInt(etCantidad.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Ingresa una cantidad válida", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cantidad <= 0) {
            Toast.makeText(getContext(), "La cantidad debe ser mayor a 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cantidad > producto.getStock()) {
            Toast.makeText(getContext(), "Stock insuficiente. Disponible: " + producto.getStock(), Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegistrarVenta.setEnabled(false);
        btnRegistrarVenta.setText("Registrando...");

        String token = sessionManager.getToken();
        VentaRequest request = new VentaRequest(producto.getId(), cantidad, producto.getPrecio());
        
        Call<VentaResponse> call = RetrofitClient.getInstance().getApiService().registrarVenta("Bearer " + token, request);
        call.enqueue(new Callback<VentaResponse>() {
            @Override
            public void onResponse(Call<VentaResponse> call, Response<VentaResponse> response) {
                btnRegistrarVenta.setEnabled(true);
                btnRegistrarVenta.setText("Registrar Venta");
                
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(getContext(), "✅ " + response.body().getMessage(), Toast.LENGTH_LONG).show();
                    etCantidad.setText("1");
                    cargarProductos(); // Recargar para actualizar stock
                } else {
                    Toast.makeText(getContext(), "Error: " + (response.body() != null ? response.body().getMessage() : "Desconocido"), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<VentaResponse> call, Throwable t) {
                btnRegistrarVenta.setEnabled(true);
                btnRegistrarVenta.setText("Registrar Venta");
                Toast.makeText(getContext(), "Error de conexión: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
