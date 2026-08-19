package com.omniventas.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.Venta;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import java.util.Calendar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private TextView tvGreeting, tvLiveRevenue, tvDailyGoalPercent, tvDailyGoal;
    private TextView tvTopProductName, tvTopProductRevenue, tvTopProductQuantity;
    private TextView tvPendingOrders, tvConversionRate, tvConversionTrend;
    private TextView tvOfflineStatus;
    private ProgressBar progressDailyGoal;
    private SwipeRefreshLayout swipeRefresh;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable actualizacionAutomatica;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

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

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());

        String vendorName = sessionManager.getVendorName();
        String greeting = "Good morning";
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 12 && hour < 18) greeting = "Good afternoon";
        else if (hour >= 18) greeting = "Good evening";
        tvGreeting.setText(greeting + ", " + (vendorName != null ? vendorName : "Vendor"));

        swipeRefresh.setOnRefreshListener(() -> {
            cargarDashboard();
            swipeRefresh.setRefreshing(false);
        });

        actualizacionAutomatica = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    cargarDashboard();
                    handler.postDelayed(this, 10000);
                }
            }
        };
        handler.postDelayed(actualizacionAutomatica, 10000);

        cargarDashboard();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(actualizacionAutomatica);
    }

    private void cargarDashboard() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            tvOfflineStatus.setVisibility(View.VISIBLE);
            tvOfflineStatus.setText("📡 Sin conexión - Mostrando datos locales");
            return;
        }

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getDashboard("Bearer " + token).enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    DashboardResponse.DashboardData data = response.body().getDashboard();
                    tvOfflineStatus.setVisibility(View.GONE);

                    double ingresosHoy = data.getIngresosHoy();
                    tvLiveRevenue.setText("$" + String.format("%,.0f", ingresosHoy));

                    int ventasMes = data.getVentasMes();
                    double metaDiaria = 32000;
                    double porcentaje = (ingresosHoy / metaDiaria) * 100;
                    if (porcentaje > 100) porcentaje = 100;
                    tvDailyGoalPercent.setText(String.format("%.0f%%", porcentaje));
                    tvDailyGoal.setText("Daily Goal $" + String.format("%,.0f", metaDiaria));
                    progressDailyGoal.setProgress((int) porcentaje);

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

                    int pending = data.getProductosBajoStock();
                    tvPendingOrders.setText(String.valueOf(pending));

                    double conversion = 0.0;
                    if (data.getVentasMes() > 0) {
                        conversion = (double) data.getVentasHoy() / data.getVentasMes() * 100;
                    }
                    tvConversionRate.setText(String.format("%.1f%%", conversion));
                    tvConversionTrend.setText("↑ 0.0%");
                } else {
                    tvOfflineStatus.setVisibility(View.VISIBLE);
                    tvOfflineStatus.setText("📡 Mostrando datos locales");
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> call, Throwable t) {
                tvOfflineStatus.setVisibility(View.VISIBLE);
                tvOfflineStatus.setText("📡 Sin conexión - Mostrando datos locales");
                logger.networkError(t);
            }
        });
    }

    public void actualizarDashboard() {
        cargarDashboard();
    }
}
