package com.omniventas.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.Venta;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.Calendar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {
    private static final String TAG = "DashboardFragment";

    private TextView tvGreeting, tvLiveRevenue, tvDailyGoalPercent, tvDailyGoal;
    private TextView tvTopProductName, tvTopProductRevenue, tvTopProductQuantity;
    private TextView tvPendingOrders, tvConversionRate, tvConversionTrend;
    private TextView tvOfflineStatus;
    private ProgressBar progressDailyGoal;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable actualizacionAutomatica;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView iniciado");
        
        try {
            View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
            Log.d(TAG, "Layout inflado correctamente");

            // Inicializar vistas con manejo de null
            tvGreeting = view.findViewById(R.id.tv_greeting);
            tvLiveRevenue = view.findViewById(R.id.tv_live_revenue);
            tvDailyGoalPercent = view.findViewById(R.id.tv_daily_goal_percent);
            tvDailyGoal = view.findViewById(R.id.tv_daily_goal);
            progressDailyGoal = view.findViewById(R.id.progress_daily_goal);
            tvTopProductName = view.findViewById(R.id.tv_top_product_name);
            tvTopProductRevenue = view.findViewById(R.id.tv_top_product_revenue);
            tvTopProductQuantity = view.findViewById(R.id.tv_top_product_quantity);
            tvPendingOrders = view.findViewById(R.id.tv_pending_orders);
            tvConversionRate = view.findViewById(R.id.tv_conversion_rate);
            tvConversionTrend = view.findViewById(R.id.tv_conversion_trend);
            tvOfflineStatus = view.findViewById(R.id.tv_offline_status);
            swipeRefresh = view.findViewById(R.id.swipe_refresh);

            // Verificar que los views importantes no sean null
            if (swipeRefresh == null) {
                Log.e(TAG, "❌ swipe_refresh es NULL - verificar layout");
                Toast.makeText(getContext(), "Error: swipe_refresh no encontrado", Toast.LENGTH_SHORT).show();
            }

            sessionManager = new SessionManager(getContext());
            logger = TelegramLogger.getInstance(getContext());
            repository = new OmniVentasRepository(getContext());

            // Configurar saludo
            String vendorName = sessionManager.getVendorName();
            String greeting = "Good morning";
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour >= 12 && hour < 18) greeting = "Good afternoon";
            else if (hour >= 18) greeting = "Good evening";
            
            if (tvGreeting != null) {
                tvGreeting.setText(greeting + ", " + (vendorName != null ? vendorName : "Vendor"));
            }

            // Configurar SwipeRefreshLayout
            if (swipeRefresh != null) {
                swipeRefresh.setOnRefreshListener(() -> {
                    cargarDashboard();
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            // Actualización automática
            actualizacionAutomatica = new Runnable() {
                @Override
                public void run() {
                    if (isAdded()) {
                        cargarDashboardLocal();
                        handler.postDelayed(this, 15000);
                    }
                }
            };
            handler.postDelayed(actualizacionAutomatica, 15000);

            // Cargar datos
            cargarDashboardLocal();
            cargarDashboard();

            Log.d(TAG, "✅ onCreateView completado");
            return view;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error en onCreateView: " + e.getMessage());
            e.printStackTrace();
            
            // Mostrar mensaje de error
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error cargando Dashboard: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            
            // Devolver un layout simple para evitar crash
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

    public void cargarDashboardLocal() {
        try {
            Log.d(TAG, "cargarDashboardLocal - Cargando datos locales");
            int ventasPendientes = repository.getVentasPendientesCount();
            int totalProductos = repository.getTotalProductos();
            int stockBajo = repository.getStockBajo();
            
            if (tvLiveRevenue != null) {
                tvLiveRevenue.setText("$" + String.format("%,.0f", (double) ventasPendientes * 100));
            }
            if (tvPendingOrders != null) {
                tvPendingOrders.setText(String.valueOf(ventasPendientes));
            }
            
            if (tvOfflineStatus != null) {
                tvOfflineStatus.setVisibility(View.VISIBLE);
                tvOfflineStatus.setText("📡 Modo Offline - " + ventasPendientes + " pendientes");
            }
            
            Log.d(TAG, "✅ Datos locales cargados: " + ventasPendientes + " pendientes");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en cargarDashboardLocal: " + e.getMessage());
        }
    }

    private void cargarDashboard() {
        try {
            String token = sessionManager.getToken();
            if (token == null || token.isEmpty()) {
                Log.w(TAG, "No hay token, mostrando datos locales");
                if (tvOfflineStatus != null) {
                    tvOfflineStatus.setVisibility(View.VISIBLE);
                    tvOfflineStatus.setText("📡 Sin conexión - Mostrando datos locales");
                }
                return;
            }

            Log.d(TAG, "cargarDashboard - Solicitando datos al servidor");
            ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
            apiService.getDashboard("Bearer " + token).enqueue(new Callback<DashboardResponse>() {
                @Override
                public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                    Log.d(TAG, "Dashboard - onResponse recibido");
                    try {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            DashboardResponse.DashboardData data = response.body().getDashboard();
                            Log.d(TAG, "Dashboard - Datos recibidos correctamente");
                            
                            if (tvOfflineStatus != null) {
                                tvOfflineStatus.setVisibility(View.GONE);
                            }

                            if (tvLiveRevenue != null) {
                                double ingresosHoy = data.getIngresosHoy();
                                tvLiveRevenue.setText("$" + String.format("%,.0f", ingresosHoy));
                            }

                            if (tvDailyGoalPercent != null && tvDailyGoal != null && progressDailyGoal != null) {
                                double ingresosHoy = data.getIngresosHoy();
                                double metaDiaria = 32000;
                                double porcentaje = (ingresosHoy / metaDiaria) * 100;
                                if (porcentaje > 100) porcentaje = 100;
                                tvDailyGoalPercent.setText(String.format("%.0f%%", porcentaje));
                                tvDailyGoal.setText("Daily Goal $" + String.format("%,.0f", metaDiaria));
                                progressDailyGoal.setProgress((int) porcentaje);
                            }

                            if (tvTopProductName != null && tvTopProductRevenue != null && tvTopProductQuantity != null) {
                                if (data.getVentasRecientes() != null && !data.getVentasRecientes().isEmpty()) {
                                    Venta topVenta = data.getVentasRecientes().get(0);
                                    tvTopProductName.setText(topVenta.getProducto());
                                    tvTopProductRevenue.setText("$" + String.format("%,.2f", topVenta.getTotal()));
                                    tvTopProductQuantity.setText(topVenta.getCantidad() + " sold");
                                } else {
                                    tvTopProductName.setText("No sales yet");
                                    tvTopProductRevenue.setText("$0");
                                    tvTopProductQuantity.setText("0 sold");
                                }
                            }

                            if (tvPendingOrders != null) {
                                tvPendingOrders.setText(String.valueOf(data.getProductosBajoStock()));
                            }

                            if (tvConversionRate != null && tvConversionTrend != null) {
                                double conversion = 0.0;
                                if (data.getVentasMes() > 0) {
                                    conversion = (double) data.getVentasHoy() / data.getVentasMes() * 100;
                                }
                                tvConversionRate.setText(String.format("%.1f%%", conversion));
                                tvConversionTrend.setText("↑ 0.0%");
                            }
                            
                            // Sincronizar productos en segundo plano
                            repository.syncProductosFromServer();
                            
                        } else {
                            Log.e(TAG, "Dashboard - Respuesta no exitosa");
                            if (tvOfflineStatus != null) {
                                tvOfflineStatus.setVisibility(View.VISIBLE);
                                tvOfflineStatus.setText("📡 Mostrando datos locales");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error procesando dashboard: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(Call<DashboardResponse> call, Throwable t) {
                    Log.e(TAG, "❌ Dashboard - onFailure: " + t.getMessage());
                    if (tvOfflineStatus != null) {
                        tvOfflineStatus.setVisibility(View.VISIBLE);
                        tvOfflineStatus.setText("📡 Sin conexión - Mostrando datos locales");
                    }
                    logger.networkError(t);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en cargarDashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void actualizarDashboard() {
        Log.d(TAG, "actualizarDashboard - Actualizando dashboard");
        cargarDashboard();
    }
}
