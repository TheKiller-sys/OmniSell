package com.omniventas.app.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventarioFragment extends Fragment {
    private static final String TAG = "InventarioFragment";

    private RecyclerView rvInventario;
    private TextView tvStatsProducts, tvUpdatedNow, tvInventarioVacio;
    private ImageView btnScan;
    private Chip chipAll, chipLowStock, chipElectronics, chipClothing;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;
    private InventarioAdapter adapter;
    private List<Producto> productos = new ArrayList<>();
    private List<Producto> filteredProducts = new ArrayList<>();

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
            btnScan = view.findViewById(R.id.btn_scan);
            chipAll = view.findViewById(R.id.chip_all);
            chipLowStock = view.findViewById(R.id.chip_low_stock);
            chipElectronics = view.findViewById(R.id.chip_electronics);
            chipClothing = view.findViewById(R.id.chip_clothing);

            sessionManager = new SessionManager(getContext());
            logger = TelegramLogger.getInstance(getContext());
            repository = new OmniVentasRepository(getContext());

            adapter = new InventarioAdapter();
            rvInventario.setLayoutManager(new GridLayoutManager(getContext(), 2));
            rvInventario.setAdapter(adapter);

            btnScan.setOnClickListener(v -> 
                Toast.makeText(getContext(), "Escáner disponible en próxima versión", Toast.LENGTH_SHORT).show()
            );

            chipAll.setOnClickListener(v -> filterProducts("all"));
            chipLowStock.setOnClickListener(v -> filterProducts("low_stock"));
            chipElectronics.setOnClickListener(v -> filterProducts("electronics"));
            chipClothing.setOnClickListener(v -> filterProducts("clothing"));

            // ✅ CORREGIDO: Cargar inventario en segundo plano
            cargarInventario();

            Log.d(TAG, "✅ onCreateView completado");
            return view;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreateView: " + e.getMessage());
            e.printStackTrace();
            
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            
            TextView errorView = new TextView(getContext());
            errorView.setText("Error cargando Inventario\n\n" + e.getMessage());
            errorView.setPadding(20, 20, 20, 20);
            return errorView;
        }
    }

    // ✅ CORREGIDO: Cargar inventario en segundo plano con AsyncTask
    private void cargarInventario() {
        new CargarInventarioTask().execute();
    }

    // ✅ AsyncTask para cargar inventario en segundo plano
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
                
                Log.d(TAG, "✅ Productos locales cargados: " + resultado.size());
                return resultado;
            } catch (Exception e) {
                Log.e(TAG, "❌ Error cargando productos locales: " + e.getMessage());
                return new ArrayList<>();
            }
        }

        @Override
        protected void onPostExecute(List<Producto> result) {
            try {
                productos = result;
                filteredProducts = new ArrayList<>(productos);
                adapter.setProductos(filteredProducts);
                updateStats();
                tvUpdatedNow.setText("Datos locales");
                
                // Luego sincronizar desde servidor
                sincronizarDesdeServidor();
            } catch (Exception e) {
                Log.e(TAG, "❌ Error actualizando UI: " + e.getMessage());
            }
        }
    }

    // ✅ Sincronizar desde servidor en segundo plano
    private void sincronizarDesdeServidor() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getProductos("Bearer " + token).enqueue(new Callback<RespuestaProductos>() {
            @Override
            public void onResponse(Call<RespuestaProductos> call, Response<RespuestaProductos> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    productos = response.body().getProductos();
                    
                    // ✅ Actualizar UI en el hilo principal
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            filteredProducts = new ArrayList<>(productos);
                            adapter.setProductos(filteredProducts);
                            updateStats();
                            tvUpdatedNow.setText("Actualizado ahora");
                        });
                    }
                    
                    // Guardar en Room (ya usa AsyncTask internamente)
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

    private void filterProducts(String filter) {
        filteredProducts.clear();
        for (Producto p : productos) {
            switch (filter) {
                case "all":
                    filteredProducts.add(p);
                    break;
                case "low_stock":
                    if (p.getStock() <= 3 && p.getStock() > 0) filteredProducts.add(p);
                    break;
                case "electronics":
                    if (p.getSeccion() != null && p.getSeccion().toLowerCase().contains("electron")) {
                        filteredProducts.add(p);
                    }
                    break;
                case "clothing":
                    if (p.getSeccion() != null && p.getSeccion().toLowerCase().contains("cloth")) {
                        filteredProducts.add(p);
                    }
                    break;
            }
        }
        adapter.setProductos(filteredProducts);
        updateStats();
        
        chipAll.setChipBackgroundColorResource(filter.equals("all") ? R.color.primary : R.color.light_gray);
        chipAll.setTextColor(getResources().getColor(filter.equals("all") ? R.color.white : R.color.dark));
        chipLowStock.setChipBackgroundColorResource(filter.equals("low_stock") ? R.color.primary : R.color.light_gray);
        chipLowStock.setTextColor(getResources().getColor(filter.equals("low_stock") ? R.color.white : R.color.dark));
        chipElectronics.setChipBackgroundColorResource(filter.equals("electronics") ? R.color.primary : R.color.light_gray);
        chipElectronics.setTextColor(getResources().getColor(filter.equals("electronics") ? R.color.white : R.color.dark));
        chipClothing.setChipBackgroundColorResource(filter.equals("clothing") ? R.color.primary : R.color.light_gray);
        chipClothing.setTextColor(getResources().getColor(filter.equals("clothing") ? R.color.white : R.color.dark));
    }

    private void updateStats() {
        tvStatsProducts.setText(filteredProducts.size() + " Productos ·");
        if (filteredProducts.isEmpty()) {
            tvInventarioVacio.setVisibility(View.VISIBLE);
            rvInventario.setVisibility(View.GONE);
        } else {
            tvInventarioVacio.setVisibility(View.GONE);
            rvInventario.setVisibility(View.VISIBLE);
        }
    }
                                             }
