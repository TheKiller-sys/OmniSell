package com.omniventas.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.omniventas.app.local.ProductoEntity;
import com.omniventas.app.local.VentaEntity;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.Venta;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.List;

public class VentasFragment extends Fragment {
    private RecyclerView rvVentasHoy;
    private SwipeRefreshLayout swipeRefresh;
    private Button btnRegistrarVenta;
    private TextView tvSinVentas, tvPendientes;
    private LinearLayout llPendientes;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private VentaAdapter ventaAdapter;
    private OmniVentasRepository repository;
    private List<Venta> ventasMostrar = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ventas, container, false);

        rvVentasHoy = view.findViewById(R.id.rv_ventas_hoy);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        btnRegistrarVenta = view.findViewById(R.id.btn_registrar_venta);
        tvSinVentas = view.findViewById(R.id.tv_sin_ventas);
        tvPendientes = view.findViewById(R.id.tv_pendientes);
        llPendientes = view.findViewById(R.id.ll_pendientes);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());
        repository = new OmniVentasRepository(getContext());

        ventaAdapter = new VentaAdapter();
        rvVentasHoy.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVentasHoy.setAdapter(ventaAdapter);

        swipeRefresh.setOnRefreshListener(() -> {
            repository.trySyncVentas();
            cargarVentasLocales();
            swipeRefresh.setRefreshing(false);
            Toast.makeText(getContext(), "Ventas actualizadas", Toast.LENGTH_SHORT).show();
        });

        btnRegistrarVenta.setOnClickListener(v -> mostrarDialogoRegistrarVenta());

        cargarVentasLocales();

        return view;
    }

    private void cargarVentasLocales() {
        List<VentaEntity> pendientes = repository.getVentasPendientes();
        ventasMostrar = new ArrayList<>();
        
        for (VentaEntity e : pendientes) {
            Venta v = new Venta();
            v.setProducto(e.getProductoNombre());
            v.setCantidad(e.getCantidad());
            v.setTotal(e.getTotal());
            v.setFecha(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(e.getFecha()));
            v.setPendiente(true);
            ventasMostrar.add(v);
        }

        int pendientesCount = repository.getVentasPendientesCount();
        if (pendientesCount > 0) {
            llPendientes.setVisibility(View.VISIBLE);
            tvPendientes.setText(pendientesCount + " ventas pendientes de sincronizar");
        } else {
            llPendientes.setVisibility(View.GONE);
        }

        actualizarUI();
    }

    private void actualizarUI() {
        if (ventasMostrar == null || ventasMostrar.isEmpty()) {
            tvSinVentas.setVisibility(View.VISIBLE);
            rvVentasHoy.setVisibility(View.GONE);
        } else {
            tvSinVentas.setVisibility(View.GONE);
            rvVentasHoy.setVisibility(View.VISIBLE);
            ventaAdapter.setShowSyncStatus(true);
            ventaAdapter.setVentas(ventasMostrar);
        }
    }

    private void mostrarDialogoRegistrarVenta() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_registrar_venta);

        EditText etBuscarProducto = dialog.findViewById(R.id.et_buscar_producto);
        RecyclerView rvProductosBusqueda = dialog.findViewById(R.id.rv_productos_busqueda);
        EditText etCantidad = dialog.findViewById(R.id.et_cantidad);
        TextView tvTotalVenta = dialog.findViewById(R.id.tv_total_venta);
        TextView tvProductoSeleccionado = dialog.findViewById(R.id.tv_producto_seleccionado);
        Button btnCancelar = dialog.findViewById(R.id.btn_cancelar_venta);
        Button btnRegistrar = dialog.findViewById(R.id.btn_registrar_venta_dialog);

        List<ProductoEntity> productosLocales = repository.getProductosLocal();

        ProductoAdapter productoAdapter = new ProductoAdapter(producto -> {
            tvProductoSeleccionado.setText("Producto seleccionado: " + producto.getNombre());
            tvProductoSeleccionado.setVisibility(View.VISIBLE);
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
        
        List<Producto> productos = new ArrayList<>();
        for (ProductoEntity entity : productosLocales) {
            Producto p = new Producto();
            p.setId(entity.getId());
            p.setNombre(entity.getNombre());
            p.setSeccion(entity.getSeccion());
            p.setPrecio(entity.getPrecio());
            p.setStock(entity.getStock());
            productos.add(p);
        }
        productoAdapter.setProductos(productos);

        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase().trim();
                List<Producto> filtrados = new ArrayList<>();
                for (Producto p : productos) {
                    if (p.getNombre().toLowerCase().contains(query) ||
                        (p.getSeccion() != null && p.getSeccion().toLowerCase().contains(query))) {
                        filtrados.add(p);
                    }
                }
                productoAdapter.setProductos(filtrados);
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
                final Producto productoSeleccionado = (Producto) tag;
                int cantidad = 1;
                try {
                    cantidad = Integer.parseInt(etCantidad.getText().toString());
                } catch (NumberFormatException e) {}

                ProductoEntity localProducto = repository.getProductosLocal().stream()
                    .filter(p -> p.getId() == productoSeleccionado.getId())
                    .findFirst()
                    .orElse(null);

                if (localProducto == null || localProducto.getStock() < cantidad) {
                    Toast.makeText(getContext(), "❌ Stock insuficiente", Toast.LENGTH_SHORT).show();
                    return;
                }

                repository.registrarVentaOffline(
                    productoSeleccionado.getId(),
                    productoSeleccionado.getNombre(),
                    cantidad,
                    productoSeleccionado.getPrecio()
                );

                Toast.makeText(getContext(), 
                    "✅ Venta registrada OFFLINE: " + productoSeleccionado.getNombre() + " x" + cantidad, 
                    Toast.LENGTH_LONG).show();

                dialog.dismiss();
                cargarVentasLocales();

                if (getActivity() != null) {
                    DashboardFragment dashboard = (DashboardFragment) getActivity()
                        .getSupportFragmentManager()
                        .findFragmentByTag("dashboard");
                    if (dashboard != null) {
                        dashboard.cargarDashboardLocal();
                    }
                }
            } else {
                Toast.makeText(getContext(), "Selecciona un producto", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
