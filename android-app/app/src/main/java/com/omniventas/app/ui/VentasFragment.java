package com.omniventas.app.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.omniventas.app.R;
import com.omniventas.app.adapters.ProductoAdapter;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VentasFragment extends Fragment {
    private static final String TAG = "VentasFragment";

    private EditText etSearchProduct;
    private TextView tvLiveTotal, tvLivePercent, tvProductName, tvProductDescription;
    private TextView tvUnitPrice, tvQuantity, tvDiscount, tvSubtotal, tvPendientesCount;
    private ImageView btnDecreaseQty, btnIncreaseQty;
    private Button btnConfirmSale;
    private RecyclerView rvProductosBusqueda;
    private LinearLayout llSugerencias;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;
    private Producto selectedProduct = null;
    private int quantity = 1;
    private List<Producto> productos = new ArrayList<>();
    private List<Producto> productosFiltrados = new ArrayList<>();
    private ProductoAdapter productoAdapter;
    private boolean isSearching = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView iniciado");
        
        try {
            View view = inflater.inflate(R.layout.fragment_ventas, container, false);
            Log.d(TAG, "Layout inflado correctamente");

            // Inicializar vistas
            etSearchProduct = view.findViewById(R.id.et_search_product);
            tvLiveTotal = view.findViewById(R.id.tv_live_total);
            tvLivePercent = view.findViewById(R.id.tv_live_percent);
            tvProductName = view.findViewById(R.id.tv_product_name);
            tvProductDescription = view.findViewById(R.id.tv_product_description);
            tvUnitPrice = view.findViewById(R.id.tv_unit_price);
            tvQuantity = view.findViewById(R.id.tv_quantity);
            tvDiscount = view.findViewById(R.id.tv_discount);
            tvSubtotal = view.findViewById(R.id.tv_subtotal);
            tvPendientesCount = view.findViewById(R.id.tv_pendientes_count);
            btnDecreaseQty = view.findViewById(R.id.btn_decrease_qty);
            btnIncreaseQty = view.findViewById(R.id.btn_increase_qty);
            btnConfirmSale = view.findViewById(R.id.btn_confirm_sale);
            rvProductosBusqueda = view.findViewById(R.id.rv_productos_busqueda);
            llSugerencias = view.findViewById(R.id.ll_sugerencias);

            sessionManager = new SessionManager(getContext());
            logger = TelegramLogger.getInstance(getContext());
            repository = new OmniVentasRepository(getContext());

            // Configurar RecyclerView para búsqueda
            productoAdapter = new ProductoAdapter(producto -> {
                selectedProduct = producto;
                quantity = 1;
                updateUI();
                rvProductosBusqueda.setVisibility(View.GONE);
                isSearching = false;
                etSearchProduct.setText("");
            });
            rvProductosBusqueda.setLayoutManager(new LinearLayoutManager(getContext()));
            rvProductosBusqueda.setAdapter(productoAdapter);

            // Cargar productos desde la base de datos
            cargarProductos();

            // Configurar búsqueda
            etSearchProduct.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String query = s.toString().trim();
                    if (query.length() >= 2) {
                        isSearching = true;
                        buscarProductos(query);
                    } else if (query.isEmpty()) {
                        isSearching = false;
                        rvProductosBusqueda.setVisibility(View.GONE);
                    }
                }
            });

            // Controles de cantidad
            btnDecreaseQty.setOnClickListener(v -> {
                if (quantity > 1) {
                    quantity--;
                    updateQuantityAndPrice();
                }
            });

            btnIncreaseQty.setOnClickListener(v -> {
                if (selectedProduct != null && quantity < selectedProduct.getStock()) {
                    quantity++;
                    updateQuantityAndPrice();
                } else if (selectedProduct == null) {
                    Toast.makeText(getContext(), "Selecciona un producto primero", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Stock insuficiente", Toast.LENGTH_SHORT).show();
                }
            });

            btnConfirmSale.setOnClickListener(v -> confirmSale());

            // Cargar ventas pendientes
            cargarVentasPendientes();

            updateUI();

            Log.d(TAG, "✅ onCreateView completado");
            return view;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreateView: " + e.getMessage());
            e.printStackTrace();
            
            TextView errorView = new TextView(getContext());
            errorView.setText("Error cargando Ventas\n\n" + e.getMessage());
            errorView.setPadding(20, 20, 20, 20);
            return errorView;
        }
    }

    private void cargarProductos() {
        new CargarProductosTask().execute();
    }

    private class CargarProductosTask extends AsyncTask<Void, Void, List<Producto>> {
        @Override
        protected List<Producto> doInBackground(Void... voids) {
            try {
                List<Producto> resultado = new ArrayList<>();
                List<com.omniventas.app.local.ProductoEntity> entities = repository.getProductosLocal();
                for (com.omniventas.app.local.ProductoEntity entity : entities) {
                    Producto p = new Producto();
                    p.setId(entity.getId());
                    p.setNombre(entity.getNombre());
                    p.setSeccion(entity.getSeccion());
                    p.setPrecio(entity.getPrecio());
                    p.setStock(entity.getStock());
                    p.setDescripcion(entity.getDescripcion());
                    resultado.add(p);
                }
                return resultado;
            } catch (Exception e) {
                Log.e(TAG, "Error cargando productos: " + e.getMessage());
                return new ArrayList<>();
            }
        }

        @Override
        protected void onPostExecute(List<Producto> result) {
            productos = result;
            productosFiltrados = new ArrayList<>(productos);
            crearSugerencias();
        }
    }

    private void crearSugerencias() {
        llSugerencias.removeAllViews();
        
        // Tomar los primeros 5 productos únicos como sugerencias
        List<Producto> sugerencias = new ArrayList<>();
        for (Producto p : productos) {
            if (sugerencias.size() < 5 && !sugerencias.contains(p)) {
                sugerencias.add(p);
            }
        }

        for (Producto p : sugerencias) {
            Chip chip = new Chip(getContext());
            chip.setText(p.getNombre());
            chip.setChipBackgroundColorResource(R.color.primary);
            chip.setTextColor(getResources().getColor(R.color.white));
            chip.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            chip.setOnClickListener(v -> {
                selectedProduct = p;
                quantity = 1;
                updateUI();
            });
            llSugerencias.addView(chip);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) chip.getLayoutParams();
            params.setMarginEnd(8);
            chip.setLayoutParams(params);
        }
    }

    private void buscarProductos(String query) {
        productosFiltrados.clear();
        for (Producto p : productos) {
            if (p.getNombre().toLowerCase().contains(query.toLowerCase())) {
                productosFiltrados.add(p);
            }
        }
        
        if (!productosFiltrados.isEmpty()) {
            productoAdapter.setProductos(productosFiltrados);
            rvProductosBusqueda.setVisibility(View.VISIBLE);
        } else {
            rvProductosBusqueda.setVisibility(View.GONE);
        }
    }

    private void cargarVentasPendientes() {
        new CargarVentasTask().execute();
    }

    private class CargarVentasTask extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... voids) {
            try {
                return repository.getVentasPendientesCount();
            } catch (Exception e) {
                return 0;
            }
        }

        @Override
        protected void onPostExecute(Integer pendientes) {
            if (pendientes > 0) {
                tvPendientesCount.setVisibility(View.VISIBLE);
                tvPendientesCount.setText(pendientes + " pendientes");
            } else {
                tvPendientesCount.setVisibility(View.GONE);
            }
        }
    }

    private void updateUI() {
        if (selectedProduct != null) {
            tvProductName.setText(selectedProduct.getNombre());
            tvProductDescription.setText(selectedProduct.getDescripcion() != null ? 
                selectedProduct.getDescripcion() : "Sin descripción");
            tvUnitPrice.setText("$" + String.format("%.2f", selectedProduct.getPrecio()));
            tvQuantity.setText(String.valueOf(quantity));
            updateQuantityAndPrice();
        } else {
            tvProductName.setText("Selecciona un producto");
            tvProductDescription.setText("");
            tvUnitPrice.setText("$0.00");
            tvQuantity.setText("1");
            tvDiscount.setText("$0.00");
            tvSubtotal.setText("$0.00");
            tvLiveTotal.setText("$0.00");
        }
    }

    private void updateQuantityAndPrice() {
        tvQuantity.setText(String.valueOf(quantity));
        if (selectedProduct != null) {
            double subtotal = selectedProduct.getPrecio() * quantity;
            tvDiscount.setText("$0.00");
            tvSubtotal.setText("$" + String.format("%.2f", subtotal));
            tvLiveTotal.setText("$" + String.format("%.2f", subtotal));
        }
    }

    private void confirmSale() {
        Log.d(TAG, "confirmSale - Iniciando");
        
        if (selectedProduct == null) {
            Toast.makeText(getContext(), "Selecciona un producto primero", Toast.LENGTH_SHORT).show();
            return;
        }

        if (quantity > selectedProduct.getStock()) {
            Toast.makeText(getContext(), "Stock insuficiente", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            // Modo offline
            repository.registrarVentaOffline(
                selectedProduct.getId(),
                selectedProduct.getNombre(),
                quantity,
                selectedProduct.getPrecio()
            );
            Toast.makeText(getContext(), "✅ Venta guardada OFFLINE: " + selectedProduct.getNombre() + " x" + quantity, Toast.LENGTH_LONG).show();
            
            selectedProduct.setStock(selectedProduct.getStock() - quantity);
            quantity = 1;
            updateUI();
            cargarVentasPendientes();
            return;
        }

        VentaRequest request = new VentaRequest(
            selectedProduct.getId(),
            quantity,
            selectedProduct.getPrecio()
        );

        com.omniventas.app.api.ApiService apiService = 
            com.omniventas.app.api.RetrofitClient.getInstance(getContext()).getApiService();
        
        apiService.registrarVenta("Bearer " + token, request).enqueue(new Callback<VentaResponse>() {
            @Override
            public void onResponse(Call<VentaResponse> call, Response<VentaResponse> response) {
                try {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        Toast.makeText(getContext(), "✅ Venta confirmada!", Toast.LENGTH_SHORT).show();
                        logger.success("Venta registrada: " + selectedProduct.getNombre() + " x" + quantity);
                        
                        selectedProduct.setStock(selectedProduct.getStock() - quantity);
                        quantity = 1;
                        updateUI();
                        cargarVentasPendientes();
                        
                        // Actualizar Dashboard
                        if (getActivity() != null) {
                            Fragment fragment = getActivity()
                                .getSupportFragmentManager()
                                .findFragmentByTag("dashboard");
                            
                            if (fragment instanceof DashboardFragment) {
                                DashboardFragment dashboard = (DashboardFragment) fragment;
                                dashboard.actualizarDesdeVenta();
                            }
                        }
                    } else {
                        // Fallback offline
                        repository.registrarVentaOffline(
                            selectedProduct.getId(),
                            selectedProduct.getNombre(),
                            quantity,
                            selectedProduct.getPrecio()
                        );
                        Toast.makeText(getContext(), "✅ Venta guardada OFFLINE", Toast.LENGTH_LONG).show();
                        selectedProduct.setStock(selectedProduct.getStock() - quantity);
                        quantity = 1;
                        updateUI();
                        cargarVentasPendientes();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error en onResponse: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<VentaResponse> call, Throwable t) {
                repository.registrarVentaOffline(
                    selectedProduct.getId(),
                    selectedProduct.getNombre(),
                    quantity,
                    selectedProduct.getPrecio()
                );
                Toast.makeText(getContext(), "✅ Venta guardada OFFLINE", Toast.LENGTH_LONG).show();
                selectedProduct.setStock(selectedProduct.getStock() - quantity);
                quantity = 1;
                updateUI();
                cargarVentasPendientes();
                logger.networkError(t);
            }
        });
    }
    }
