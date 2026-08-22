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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;
import com.omniventas.app.adapters.DashboardVentaAdapter;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.Venta;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {
    private static final String TAG = "DashboardFragment";
    private static final int PAGE_SIZE = 10;

    private TextView tvGreeting, tvLiveRevenue, tvFechaHoy, tvPendingOrders, tvConversionRate, tvConversionTrend;
    private TextView tvPageInfo, tvSinVentas;
    private Button btnPrevPage, btnNextPage;
    private RecyclerView rvVentasDiarias;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;
    private DashboardVentaAdapter ventasAdapter;
    private List<Venta> ventasDelDia = new ArrayList<>();
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
            tvFechaHoy = view.findViewById(R.id.tv_fecha_hoy);
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

            // Mostrar fecha actual en "En Vivo"
            String fechaActual = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
            if (tvFechaHoy != null) {
                tvFechaHoy.setText(fechaActual);
            }

            // Configurar RecyclerView
            ventasAdapter = new DashboardVentaAdapter();
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
                        
                        // ========================================
                        // FILTRAR VENTAS DEL DÍA DE HOY
                        // ========================================
                        List<Venta> todasLasVentas = data.getVentasRecientes();
                        List<Venta> ventasDeHoy = new ArrayList<>();
                        double totalIngresosHoy = 0.0;
                        
                        // Obtener fecha de hoy en formato para comparar
                        String fechaHoyStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        
                        if (todasLasVentas != null) {
                            for (Venta v : todasLasVentas) {
                                String fechaVenta = v.getFecha();
                                if (fechaVenta != null && fechaVenta.contains(fechaHoyStr)) {
                                    ventasDeHoy.add(v);
                                    totalIngresosHoy += v.getTotal();
                                }
                            }
                        }
                        
                        Log.d(TAG, "📊 Ventas de hoy: " + ventasDeHoy.size() + " - Total: $" + totalIngresosHoy);
                        
                        // ========================================
                        // ACTUALIZAR "EN VIVO" CON VENTAS DEL DÍA
                        // ========================================
                        if (tvLiveRevenue != null) {
                            tvLiveRevenue.setText("$" + String.format("%,.0f", totalIngresosHoy));
                        }

                        // ========================================
                        // ACTUALIZAR "VENTAS DEL DÍA"
                        // ========================================
                        ventasDelDia.clear();
                        ventasDelDia.addAll(ventasDeHoy);
                        
                        totalPaginas = (int) Math.ceil((double) ventasDelDia.size() / PAGE_SIZE);
                        if (totalPaginas == 0) totalPaginas = 1;
                        paginaActual = 0;
                        mostrarPagina();
                        
                        if (ventasDelDia.isEmpty()) {
                            tvSinVentas.setVisibility(View.VISIBLE);
                            rvVentasDiarias.setVisibility(View.GONE);
                        } else {
                            tvSinVentas.setVisibility(View.GONE);
                            rvVentasDiarias.setVisibility(View.VISIBLE);
                        }

                        // Actualizar pedidos pendientes (productos bajo stock)
                        if (tvPendingOrders != null) {
                            tvPendingOrders.setText(String.valueOf(data.getProductosBajoStock()));
                        }

                        // Actualizar tasa de conversión
                        if (tvConversionRate != null && tvConversionTrend != null) {
                            double conversion = 0.0;
                            if (data.getVentasMes() > 0) {
                                conversion = (double) ventasDeHoy.size() / data.getVentasMes() * 100;
                            }
                            tvConversionRate.setText(String.format("%.1f%%", conversion));
                            tvConversionTrend.setText("↑ " + String.format("%.1f%%", conversion));
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
            }
        });
    }

    private void mostrarPagina() {
        int inicio = paginaActual * PAGE_SIZE;
        int fin = Math.min(inicio + PAGE_SIZE, ventasDelDia.size());
        
        ventasPagina.clear();
        if (inicio < ventasDelDia.size()) {
            ventasPagina.addAll(ventasDelDia.subList(inicio, fin));
        }
        
        ventasAdapter.setVentas(ventasPagina);
        
        // Actualizar información de página
        tvPageInfo.setText("Página " + (paginaActual + 1) + " de " + Math.max(1, totalPaginas));
        
        btnPrevPage.setVisibility(paginaActual > 0 ? View.VISIBLE : View.GONE);
        btnNextPage.setVisibility(paginaActual < totalPaginas - 1 ? View.VISIBLE : View.GONE);
    }

    public void actualizarDesdeVenta() {
        Log.d(TAG, "actualizarDesdeVenta - Actualizando dashboard desde venta");
        if (isAdded()) {
            cargarDashboardCompleto();
        }
    }
}
