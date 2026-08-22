package com.omniventas.app.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.omniventas.app.R;
import com.omniventas.app.adapters.InventarioAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.local.ProductoEntity;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.RespuestaProductos;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventarioFragment extends Fragment {
    private static final String TAG = "InventarioFragment";

    private RecyclerView rvInventario;
    private TextView tvStatsProducts, tvUpdatedNow, tvInventarioVacio;
    private EditText etSearchInventario;
    private LinearLayout llFiltrosCategoria;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;
    private InventarioAdapter adapter;
    private List<Producto> productos = new ArrayList<>();
    private List<Producto> filteredProducts = new ArrayList<>();
    private String filtroActual = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView iniciado");
        
        try {
            View view = inflater.inflate(R.layout.fragment_inventario, container, false);
            Log.d(TAG, "Layout inflado correctamente");

            rvInventario = view.findViewById(R.id.rv_inventario);
            tvStatsProducts = view.findViewById(R.id.tv_stats_products);
            tvUpdatedNow = view.findViewById(R.id.tv_updated_now);
            tvInventarioVacio = view.findViewById(R.id.tv_inventario_vacio);
            etSearchInventario = view.findViewById(R.id.et_search_inventario);
            llFiltrosCategoria = view.findViewById(R.id.ll_filtros_categoria);

            sessionManager = new SessionManager(getContext());
            logger = TelegramLogger.getInstance(getContext());
            repository = new OmniVentasRepository(getContext());

            adapter = new InventarioAdapter();
            rvInventario.setLayoutManager(new GridLayoutManager(getContext(), 2));
            rvInventario.setAdapter(adapter);

            // Búsqueda
            etSearchInventario.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    filterProducts(filtroActual, s.toString().trim());
                }
            });

            cargarInventario();
            Log.d(TAG, "✅ onCreateView completado");
            return view;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreateView: " + e.getMessage());
            e.printStackTrace();
            
            TextView errorView = new TextView(getContext());
            errorView.setText("Error cargando Inventario\n\n" + e.getMessage());
            errorView.setPadding(20, 20, 20, 20);
            return errorView;
        }
    }

    private void cargarInventario() {
        new CargarInventarioTask().execute();
    }

    private class CargarInventarioTask extends AsyncTask<Void, Void, List<Producto>> {
        @Override
        protected List<Producto> doInBackground(Void... voids) {
            try {
                List<Producto> resultado = new ArrayList<>();
                List<ProductoEntity> locales = repository.getProductosLocal();
                
                for (ProductoEntity entity : locales) {
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
            filteredProducts = new ArrayList<>(productos);
            adapter.setProductos(filteredProducts);
            updateStats();
            crearFiltrosCategoria();
            tvUpdatedNow.setText("Datos locales");
            
            sincronizarDesdeServidor();
        }
    }

    private void crearFiltrosCategoria() {
        llFiltrosCategoria.removeAllViews();
        
        // Obtener categorías únicas
        Set<String> categorias = new HashSet<>();
        for (Producto p : productos) {
            if (p.getSeccion() != null && !p.getSeccion().isEmpty()) {
                categorias.add(p.getSeccion());
            }
        }

        // Chip "Todos"
        Chip chipAll = new Chip(getContext());
        chipAll.setText("Todos");
        chipAll.setChipBackgroundColorResource(R.color.primary);
        chipAll.setTextColor(getResources().getColor(R.color.white));
        chipAll.setOnClickListener(v -> {
            filtroActual = "all";
            filterProducts("all", etSearchInventario.getText().toString().trim());
            actualizarChips();
        });
        llFiltrosCategoria.addView(chipAll);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) chipAll.getLayoutParams();
        params.setMarginEnd(8);
        chipAll.setLayoutParams(params);

        // Chips por categoría
        for (String categoria : categorias) {
            Chip chip = new Chip(getContext());
            chip.setText(categoria);
            chip.setChipBackgroundColorResource(R.color.light_gray);
            chip.setTextColor(getResources().getColor(R.color.dark));
            chip.setOnClickListener(v -> {
                filtroActual = categoria;
                filterProducts(categoria, etSearchInventario.getText().toString().trim());
                actualizarChips();
            });
            llFiltrosCategoria.addView(chip);
            LinearLayout.LayoutParams chipParams = (LinearLayout.LayoutParams) chip.getLayoutParams();
            chipParams.setMarginEnd(8);
            chip.setLayoutParams(chipParams);
        }
    }

    private void actualizarChips() {
        for (int i = 0; i < llFiltrosCategoria.getChildCount(); i++) {
            View child = llFiltrosCategoria.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                String texto = chip.getText().toString();
                boolean isSelected = filtroActual.equals("all") ? 
                    texto.equals("Todos") : texto.equals(filtroActual);
                
                if (isSelected) {
                    chip.setChipBackgroundColorResource(R.color.primary);
                    chip.setTextColor(getResources().getColor(R.color.white));
                } else {
                    chip.setChipBackgroundColorResource(R.color.light_gray);
                    chip.setTextColor(getResources().getColor(R.color.dark));
                }
            }
        }
    }

    private void filterProducts(String categoria, String query) {
        filteredProducts.clear();
        
        for (Producto p : productos) {
            boolean matchCategoria = categoria.equals("all") || 
                (p.getSeccion() != null && p.getSeccion().equals(categoria));
            
            boolean matchQuery = query.isEmpty() || 
                p.getNombre().toLowerCase().contains(query.toLowerCase());
            
            if (matchCategoria && matchQuery) {
                filteredProducts.add(p);
            }
        }
        
        adapter.setProductos(filteredProducts);
        updateStats();
    }

    private void sincronizarDesdeServidor() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getProductos("Bearer " + token).enqueue(new Callback<RespuestaProductos>() {
            @Override
            public void onResponse(Call<RespuestaProductos> call, Response<RespuestaProductos> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    productos = response.body().getProductos();
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            filteredProducts = new ArrayList<>(productos);
                            adapter.setProductos(filteredProducts);
                            updateStats();
                            tvUpdatedNow.setText("Actualizado ahora");
                            crearFiltrosCategoria();
                        });
                    }
                    
                    repository.syncProductosFromServer();
                }
            }

            @Override
            public void onFailure(Call<RespuestaProductos> call, Throwable t) {
                logger.networkError(t);
                tvUpdatedNow.setText("Datos locales");
            }
        });
    }

    private void updateStats() {
        tvStatsProducts.setText(filteredProducts.size() + " Productos");
        if (filteredProducts.isEmpty()) {
            tvInventarioVacio.setVisibility(View.VISIBLE);
            rvInventario.setVisibility(View.GONE);
        } else {
            tvInventarioVacio.setVisibility(View.GONE);
            rvInventario.setVisibility(View.VISIBLE);
        }
    }
            }
