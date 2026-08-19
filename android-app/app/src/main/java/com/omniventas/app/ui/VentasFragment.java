package com.omniventas.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import com.omniventas.app.R;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import com.omniventas.app.sync.SyncManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VentasFragment extends Fragment {

    private EditText etSearchProduct;
    private TextView tvLiveTotal, tvLivePercent, tvProductName, tvProductDescription;
    private TextView tvUnitPrice, tvQuantity, tvDiscount, tvSubtotal, tvPendientesCount;
    private ImageView btnDecreaseQty, btnIncreaseQty;
    private Button btnConfirmSale;
    private Switch switchHaptic;
    private Chip chipBlueJeans, chipRedTshirt, chipSneakers, chipHoodie;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;
    private Producto selectedProduct = null;
    private int quantity = 1;
    private List<Producto> productos = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ventas, container, false);

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
        switchHaptic = view.findViewById(R.id.switch_haptic);
        chipBlueJeans = view.findViewById(R.id.chip_blue_jeans);
        chipRedTshirt = view.findViewById(R.id.chip_red_tshirt);
        chipSneakers = view.findViewById(R.id.chip_sneakers);
        chipHoodie = view.findViewById(R.id.chip_hoodie);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());
        repository = new OmniVentasRepository(getContext());

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Mostrar ventas pendientes
        int pendientes = repository.getVentasPendientesCount();
        if (pendientes > 0) {
            tvPendientesCount.setVisibility(View.VISIBLE);
            tvPendientesCount.setText(pendientes + " pendientes");
        } else {
            tvPendientesCount.setVisibility(View.GONE);
        }

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
                Toast.makeText(getContext(), "Select a product first", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Not enough stock", Toast.LENGTH_SHORT).show();
            }
        });

        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase().trim();
                // Aquí se podrían filtrar productos de la lista
            }
        });

        chipBlueJeans.setOnClickListener(v -> selectProduct("Blue Jeans", "Regular Fit", 42.00, 20, 1));
        chipRedTshirt.setOnClickListener(v -> selectProduct("Red T-Shirt", "Cotton", 25.00, 15, 2));
        chipSneakers.setOnClickListener(v -> selectProduct("Sneakers", "Running", 89.00, 10, 3));
        chipHoodie.setOnClickListener(v -> selectProduct("Hoodie", "Warm", 55.00, 8, 4));

        btnConfirmSale.setOnClickListener(v -> confirmSale());

        updateUI();

        return view;
    }

    private void selectProduct(String name, String desc, double price, int stock, int id) {
        selectedProduct = new Producto();
        selectedProduct.setNombre(name);
        selectedProduct.setDescripcion(desc);
        selectedProduct.setPrecio(price);
        selectedProduct.setStock(stock);
        selectedProduct.setId(id);
        quantity = 1;
        updateUI();
    }

    private void updateUI() {
        if (selectedProduct != null) {
            tvProductName.setText(selectedProduct.getNombre());
            tvProductDescription.setText(selectedProduct.getDescripcion());
            tvUnitPrice.setText("$" + String.format("%.2f", selectedProduct.getPrecio()));
            tvQuantity.setText(String.valueOf(quantity));
            updateQuantityAndPrice();
        } else {
            tvProductName.setText("Select a product");
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
        if (selectedProduct == null) {
            Toast.makeText(getContext(), "Select a product first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (quantity > selectedProduct.getStock()) {
            Toast.makeText(getContext(), "Not enough stock", Toast.LENGTH_SHORT).show();
            return;
        }

        if (switchHaptic.isChecked()) {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(50);
            }
        }

        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            // Modo offline: guardar localmente
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
            
            int pendientes = repository.getVentasPendientesCount();
            if (pendientes > 0) {
                tvPendientesCount.setVisibility(View.VISIBLE);
                tvPendientesCount.setText(pendientes + " pendientes");
            }
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
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(getContext(), "✅ Sale confirmed!", Toast.LENGTH_SHORT).show();
                    logger.success("Venta registrada: " + selectedProduct.getNombre() + " x" + quantity);
                    
                    selectedProduct.setStock(selectedProduct.getStock() - quantity);
                    quantity = 1;
                    updateUI();
                    
                    // Actualizar Dashboard
                    if (getActivity() != null) {
                        DashboardFragment dashboard = (DashboardFragment) getActivity()
                            .getSupportFragmentManager()
                            .findFragmentByTag("dashboard");
                        if (dashboard != null) {
                            dashboard.actualizarDesdeVenta();
                        }
                    }
                } else {
                    // Fallback a modo offline
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
                }
            }

            @Override
            public void onFailure(Call<VentaResponse> call, Throwable t) {
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
                logger.networkError(t);
            }
        });
    }
}
