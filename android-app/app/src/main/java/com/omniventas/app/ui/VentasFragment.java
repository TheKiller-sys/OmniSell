package com.omniventas.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import com.omniventas.app.models.RespuestaProductos;
import com.omniventas.app.models.Venta;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VentasFragment extends Fragment {

    private RecyclerView rvVentasHoy;
    private SwipeRefreshLayout swipeRefresh;
    private Button btnRegistrarVenta;
    private TextView tvSinVentas;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private VentaAdapter ventaAdapter;
    private List<Venta> ventasHoy = new ArrayList<>();
    private List<Producto> productosGlobales = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable actualizacionAutomatica;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ventas, container, false);

        rvVentasHoy = view.findViewById(R.id.rv_ventas_hoy);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        btnRegistrarVenta = view.findViewById(R.id.btn_registrar_venta);
        tvSinVentas = view.findViewById(R.id.tv_sin_ventas);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());

        ventaAdapter = new VentaAdapter();
        rvVentasHoy.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVentasHoy.setAdapter(ventaAdapter);

        swipeRefresh.setOnRefreshListener(this::cargarVentasHoy);
        btnRegistrarVenta.setOnClickListener(v -> mostrarDialogoRegistrarVenta());

        actualizacionAutomatica = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    cargarVentasHoy();
                    handler.postDelayed(this, 5000);
                }
            }
        };
        handler.postDelayed(actualizacionAutomatica, 5000);

        cargarVentasHoy();
        cargarProductos();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(actualizacionAutomatica);
    }

    private void cargarVentasHoy() {
        ventasHoy.clear();
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

    private void cargarProductos() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getProductos("Bearer " + token).enqueue(new Callback<RespuestaProductos>() {
            @Override
            public void onResponse(Call<RespuestaProductos> call, Response<RespuestaProductos> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    productosGlobales = response.body().getProductos();
                    Collections.sort(productosGlobales, (p1, p2) -> p1.getNombre().compareToIgnoreCase(p2.getNombre()));
                }
            }
            @Override
            public void onFailure(Call<RespuestaProductos> call, Throwable t) {
                logger.networkError(t);
            }
        });
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

        if (productosGlobales.isEmpty()) {
            cargarProductos();
        }

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
        productoAdapter.setProductos(productosGlobales);

        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase().trim();
                List<Producto> filtrados = new ArrayList<>();
                for (Producto p : productosGlobales) {
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

        btnRegistrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object tag = tvTotalVenta.getTag();
                if (tag instanceof Producto) {
                    final Producto productoSeleccionado = (Producto) tag;
                    int cantidad = 1;
                    try {
                        cantidad = Integer.parseInt(etCantidad.getText().toString());
                    } catch (NumberFormatException e) {}
                    final int cantidadFinal = cantidad;

                    String token = sessionManager.getToken();
                    if (token != null && !token.isEmpty()) {
                        VentaRequest request = new VentaRequest(
                            productoSeleccionado.getId(),
                            cantidadFinal,
                            productoSeleccionado.getPrecio()
                        );
                        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
                        apiService.registrarVenta("Bearer " + token, request).enqueue(new Callback<VentaResponse>() {
                            @Override
                            public void onResponse(Call<VentaResponse> call, Response<VentaResponse> response) {
                                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                    Toast.makeText(getContext(), "✅ Venta registrada: " + productoSeleccionado.getNombre() + " x" + cantidadFinal, Toast.LENGTH_SHORT).show();
                                    logger.success("Venta registrada: " + productoSeleccionado.getNombre() + " x" + cantidadFinal);
                                    dialog.dismiss();
                                    cargarVentasHoy();
                                    if (getActivity() != null) {
                                        DashboardFragment dashboard = (DashboardFragment) getActivity()
                                            .getSupportFragmentManager()
                                            .findFragmentByTag("dashboard");
                                        if (dashboard != null) {
                                            dashboard.cargarDashboard();
                                        }
                                    }
                                } else {
                                    String errorMsg = "Error al registrar venta";
                                    try {
                                        if (response.errorBody() != null) {
                                            String errorBody = response.errorBody().string();
                                            Log.e("VentasFragment", "❌ Error body: " + errorBody);
                                            
                                            // Verificar si el error es HTML
                                            if (errorBody.trim().startsWith("<!DOCTYPE") || errorBody.trim().startsWith("<html")) {
                                                errorMsg = "Error del servidor (HTML)";
                                            } else {
                                                try {
                                                    JSONObject jsonError = new JSONObject(errorBody);
                                                    errorMsg = jsonError.optString("message", errorMsg);
                                                } catch (Exception e) {
                                                    errorMsg = errorBody;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e("VentasFragment", "❌ Error leyendo errorBody", e);
                                        errorMsg = "Error al procesar la respuesta del servidor";
                                    }
                                    Toast.makeText(getContext(), "❌ " + errorMsg, Toast.LENGTH_LONG).show();
                                    logger.error("Error en venta: " + errorMsg);
                                }
                            }

                            @Override
                            public void onFailure(Call<VentaResponse> call, Throwable t) {
                                String errorMsg = "Error de conexión";
                                if (t.getMessage() != null) {
                                    errorMsg = t.getMessage();
                                    // Verificar si el error es de JSON
                                    if (errorMsg.contains("BEGIN_OBJECT")) {
                                        errorMsg = "El servidor devolvió texto plano en lugar de JSON";
                                    }
                                    if (errorMsg.length() > 100) {
                                        errorMsg = errorMsg.substring(0, 100) + "...";
                                    }
                                }
                                Toast.makeText(getContext(), "❌ " + errorMsg, Toast.LENGTH_LONG).show();
                                logger.error("Error en venta: " + errorMsg);
                            }
                        });
                    } else {
                        Toast.makeText(getContext(), "Sesión expirada", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Selecciona un producto", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();
    }
}
