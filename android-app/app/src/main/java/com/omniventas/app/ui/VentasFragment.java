package com.omniventas.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import com.omniventas.app.adapters.ProductoAdapter;
import com.omniventas.app.adapters.VentaAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.Venta;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VentasFragment extends Fragment {

    private static final String TAG = "VentasFragment";
    private RecyclerView rvVentasHoy;
    private SwipeRefreshLayout swipeRefresh;
    private Button btnRegistrarVenta;
    private TextView tvSinVentas, tvVentasHoyTitulo;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private VentaAdapter ventaAdapter;
    private List<Venta> ventasHoy = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ventas, container, false);

        rvVentasHoy = view.findViewById(R.id.rv_ventas_hoy);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        btnRegistrarVenta = view.findViewById(R.id.btn_registrar_venta);
        tvSinVentas = view.findViewById(R.id.tv_sin_ventas);
        tvVentasHoyTitulo = view.findViewById(R.id.tv_ventas_hoy_titulo);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());

        ventaAdapter = new VentaAdapter();
        rvVentasHoy.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVentasHoy.setAdapter(ventaAdapter);

        String fecha = new SimpleDateFormat("EEEE, d MMM", new Locale("es", "ES")).format(new Date());
        tvVentasHoyTitulo.setText("Ventas de Hoy - " + fecha);

        swipeRefresh.setOnRefreshListener(this::cargarVentasHoy);
        btnRegistrarVenta.setOnClickListener(v -> mostrarDialogoRegistrarVenta());

        cargarVentasHoy();

        return view;
    }

    private void cargarVentasHoy() {
        // Simulación de carga de ventas del día
        // En producción, se conectaría a la API
        ventasHoy.clear();
        // Aquí se cargarían las ventas reales desde la API
        actualizarUI();
        swipeRefresh.setRefreshing(false);
    }

    private void actualizarUI() {
        if (ventasHoy.isEmpty()) {
            tvSinVentas.setVisibility(View.VISIBLE);
            rvVentasHoy.setVisibility(View.GONE);
        } else {
            tvSinVentas.setVisibility(View.GONE);
            rvVentasHoy.setVisibility(View.VISIBLE);
            ventaAdapter.setVentas(ventasHoy);
        }
    }

    private void mostrarDialogoRegistrarVenta() {
        Dialog dialog = new Dialog(getContext(), R.style.DialogStyle);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_registrar_venta);

        EditText etBuscarProducto = dialog.findViewById(R.id.et_buscar_producto);
        RecyclerView rvProductosBusqueda = dialog.findViewById(R.id.rv_productos_busqueda);
        EditText etCantidad = dialog.findViewById(R.id.et_cantidad);
        TextView tvTotalVenta = dialog.findViewById(R.id.tv_total_venta);
        Button btnCancelar = dialog.findViewById(R.id.btn_cancelar_venta);
        Button btnRegistrar = dialog.findViewById(R.id.btn_registrar_venta_dialog);

        List<Producto> productos = new ArrayList<>();
        // Simulación de productos desde API
        // En producción, se cargarían desde la API
        ProductoAdapter productoAdapter = new ProductoAdapter(producto -> {
            // Seleccionar producto
            tvTotalVenta.setTag(producto);
            double precio = producto.getPrecio();
            int cantidad = 1;
            try {
                cantidad = Integer.parseInt(etCantidad.getText().toString());
            } catch (NumberFormatException e) {}
            tvTotalVenta.setText("Total: $" + String.format("%.2f", precio * cantidad));
        });
        rvProductosBusqueda.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProductosBusqueda.setAdapter(productoAdapter);

        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // Filtrar productos
                productoAdapter.setProductos(productos);
            }
        });

        etCantidad.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                Object tag = tvTotalVenta.getTag();
                if (tag instanceof Producto) {
                    Producto p = (Producto) tag;
                    int cantidad = 1;
                    try {
                        cantidad = Integer.parseInt(s.toString());
                    } catch (NumberFormatException e) {}
                    tvTotalVenta.setText("Total: $" + String.format("%.2f", p.getPrecio() * cantidad));
                }
            }
        });

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnRegistrar.setOnClickListener(v -> {
            Object tag = tvTotalVenta.getTag();
            if (tag instanceof Producto) {
                Producto p = (Producto) tag;
                int cantidad = 1;
                try {
                    cantidad = Integer.parseInt(etCantidad.getText().toString());
                } catch (NumberFormatException e) {}
                
                // Registrar venta
                Toast.makeText(getContext(), "✅ Venta registrada: " + p.getNombre() + " x" + cantidad, Toast.LENGTH_SHORT).show();
                logger.success("Venta registrada: " + p.getNombre() + " x" + cantidad);
                dialog.dismiss();
                cargarVentasHoy();
            } else {
                Toast.makeText(getContext(), "Selecciona un producto", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
