package com.omniventas.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;
import com.omniventas.app.adapters.VentaRecienteAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.local.VentaEntity;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.Venta;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {
    private static final String TAG = "DashboardFragment";
    private static final int PAGE_SIZE = 10;

    private TextView tvGreeting, tvLiveRevenue, tvPendingOrders, tvConversionRate, tvConversionTrend;
    private TextView tvPageInfo, tvSinVentas;
    private Button btnPrevPage, btnNextPage;
    private RecyclerView rvVentasDiarias;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;
    private VentaRecienteAdapter ventasAdapter;
    private List<Venta> todasLasVentas = new ArrayList<>();
    private List<Venta> ventasPagina = new ArrayList<>();
    private int paginaActual = 0;
    private int totalPaginas = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable actualizacionAutomatica;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView iniciado");
        
        try {
            View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
            Log.d(TAG, "Layout inflado correctamente");

            // Inicializar vistas
            tvGreeting = view.findViewById(R.id.tv_greeting);
            tvLiveRevenue = view.findViewById(R.id.tv_live_revenue);
            tvPendingOrders = view.findViewById(R.id.tv_pending_orders);
            tvConversionRate = view.findViewById(R.id.tv_conversion_rate);
            tvConversionTrend = view.findViewById(R.id.tv_conversion_trend);
            tvPageInfo = view.findViewById(R.id.tv_page_info);
            tvSinVentas = view.findViewById(R.id.tv_sin_ventas);
            btnPrevPage = view.findViewById(R.id.btn_prev_page);
            btnNextPage = view.findViewById(R.id.btn_next_page);
            rvVentasDiarias = view.findViewById(R.id.rv_ventas_diarias);
            swipeRefresh = view.findViewById(R.id.swipe_refresh);

            sessionManager = new SessionManager(getContext());
            logger = TelegramLogger.getInstance(getContext());
            repository = new OmniVentasRepository(getContext());

            // Configurar RecyclerView
            ventasAdapter = new VentaRecienteAdapter(new ArrayList<>());
            rvVentasDiarias.setLayoutManager(new LinearLayoutManager(getContext()));
            rvVentasDiarias.setAdapter(ventasAdapter);

            // Configurar saludo en español
            String vendorName = sessionManager.getVendorName();
            String greeting = "Buenos días";
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour >= 12 && hour < 18) greeting = "Buenas tardes";
            else if (hour >= 18) greeting = "Buenas noches";
            
            if (tvGreeting != null) {
                tvGreeting.setText(greeting + ", " + (vendorName != null ? vendorName : "Vendedor"));
            }

            // Configurar SwipeRefreshLayout
            if (swipeRefresh != null) {
                swipeRefresh.setOnRefreshListener(() -> {
                    cargarDashboardCompleto();
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            // Paginación
            btnPrevPage.setOnClickListener(v -> {
                if (paginaActual > 0) {
                    paginaActual--;
                    mostrarPagina();
                }
            });

            btnNextPage.setOnClickListener(v -> {
                if (paginaActual < totalPaginas - 1) {
                    paginaActual++;
                    mostrarPagina();
                }
            });

            // Actualización automática cada 30 segundos
            actualizacionAutomatica = new Runnable() {
                @Override
                public void run() {
                    if (isAdded()) {
                        cargarDashboardCompleto();
                        handler.postDelayed(this, 30000);
                    }
                }
            };
            handler.postDelayed(actualizacionAutomatica, 30000);

            // Cargar datos
            cargarDashboardCompleto();

            Log.d(TAG, "✅ onCreateView completado");
            return view;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreateView: " + e.getMessage());
            e.printStackTrace();
            
            TextView errorView = new TextView(getContext());
            errorView.setText("Error cargando Dashboard\n\n" + e.getMessage());
            errorView.setPadding(20, 20, 20, 20);
            return errorView;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(actualizacionAutomatica);
    }

    // 🔥 MÉTODO PÚBLICO PARA ACTUALIZAR DESDE VENTAS
    public void actualizarDesdeVenta() {
        Log.d(TAG, "actualizarDesdeVenta - Actualizando dashboard");
        if (isAdded()) {
            cargarDashboardCompleto();
        }
    }

    private void cargarDashboardCompleto() {
        cargarDatosLocales();
        cargarDashboardDesdeServidor();
    }

    private void cargarDatosLocales() {
        try {
            int pendientes = repository.getVentasPendientesCount();
            if (tvPendingOrders != null) {
                tvPendingOrders.setText(String.valueOf(pendientes));
            }
            
            // Cargar ventas locales recientes si hay datos del servidor
            List<VentaEntity> ventasLocal = repository.getVentasSincronizadas();
            if (!ventasLocal.isEmpty() && todasLasVentas.isEmpty()) {
                // Convertir ventas locales a formato Venta
                List<Venta> ventasConvertidas = new ArrayList<>();
                for (VentaEntity entity : ventasLocal) {
                    Venta v = new Venta();
                    v.setProducto(entity.getProductoNombre());
                    v.setCantidad(entity.getCantidad());
                    v.setTotal(entity.getTotal());
                    v.setFecha(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(entity.getFecha())));
                    ventasConvertidas.add(v);
                }
                todasLasVentas = ventasConvertidas;
                totalPaginas = (int) Math.ceil((double) todasLasVentas.size() / PAGE_SIZE);
                paginaActual = 0;
                mostrarPagina();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cargando datos locales: " + e.getMessage());
        }
    }

    private void cargarDashboardDesdeServidor() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "No hay token, mostrando datos locales");
            return;
        }

        Log.d(TAG, "Cargando dashboard desde servidor");
        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getDashboard("Bearer " + token).enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                Log.d(TAG, "Dashboard - onResponse recibido");
                try {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        DashboardResponse.DashboardData data = response.body().getDashboard();
                        Log.d(TAG, "Dashboard - Datos recibidos correctamente");
                        
                        // Actualizar ingresos
                        if (tvLiveRevenue != null) {
                            double ingresosHoy = data.getIngresosHoy();
                            tvLiveRevenue.setText("$" + String.format("%,.0f", ingresosHoy));
                        }

                        // Actualizar pedidos pendientes (productos bajo stock)
                        if (tvPendingOrders != null) {
                            tvPendingOrders.setText(String.valueOf(data.getProductosBajoStock()));
                        }

                        // Actualizar tasa de conversión
                        if (tvConversionRate != null && tvConversionTrend != null) {
                            double conversion = 0.0;
                            if (data.getVentasMes() > 0) {
                                conversion = (double) data.getVentasHoy() / data.getVentasMes() * 100;
                            }
                            tvConversionRate.setText(String.format("%.1f%%", conversion));
                            tvConversionTrend.setText("↑ " + String.format("%.1f%%", conversion));
                        }

                        // Actualizar historial de ventas
                        if (data.getVentasRecientes() != null) {
                            todasLasVentas = data.getVentasRecientes();
                            totalPaginas = (int) Math.ceil((double) todasLasVentas.size() / PAGE_SIZE);
                            paginaActual = 0;
                            mostrarPagina();
                            
                            if (todasLasVentas.isEmpty()) {
                                tvSinVentas.setVisibility(View.VISIBLE);
                                rvVentasDiarias.setVisibility(View.GONE);
                            } else {
                                tvSinVentas.setVisibility(View.GONE);
                                rvVentasDiarias.setVisibility(View.VISIBLE);
                            }
                        }

                        // Sincronizar productos en segundo plano
                        repository.syncProductosFromServer();
                        
                    } else {
                        Log.e(TAG, "Dashboard - Respuesta no exitosa");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error procesando dashboard: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> call, Throwable t) {
                Log.e(TAG, "❌ Dashboard - onFailure: " + t.getMessage());
                logger.networkError(t);
                
                // Si hay ventas locales, mostrarlas como fallback
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!todasLasVentas.isEmpty()) {
                            mostrarPagina();
                        }
                    });
                }
            }
        });
    }

    private void mostrarPagina() {
        int inicio = paginaActual * PAGE_SIZE;
        int fin = Math.min(inicio + PAGE_SIZE, todasLasVentas.size());
        
        ventasPagina.clear();
        if (inicio < todasLasVentas.size()) {
            ventasPagina.addAll(todasLasVentas.subList(inicio, fin));
        }
        
        ventasAdapter.updateData(ventasPagina);
        
        // Actualizar información de página
        tvPageInfo.setText("Página " + (paginaActual + 1) + " de " + Math.max(1, totalPaginas));
        
        btnPrevPage.setVisibility(paginaActual > 0 ? View.VISIBLE : View.GONE);
        btnNextPage.setVisibility(paginaActual < totalPaginas - 1 ? View.VISIBLE : View.GONE);
    }
}
