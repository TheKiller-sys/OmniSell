package com.omniventas.app.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TelegramLogger {
    private static final String TAG = "TelegramLogger";
    private static TelegramLogger instance;
    private Context context;
    private SessionManager sessionManager;
    private String appVersion = "8.0.4";

    private TelegramLogger(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {}
    }

    public static synchronized TelegramLogger getInstance(Context context) {
        if (instance == null) {
            instance = new TelegramLogger(context);
        }
        return instance;
    }

    public void success(String message) { sendLog("SUCCESS", message); }
    public void warning(String message) { sendLog("WARNING", message); }
    public void error(String message) { sendLog("ERROR", message); }
    public void networkError(Throwable t) { sendLog("ERROR", "Error de red: " + t.getMessage()); }
    public void info(String message) { sendLog("INFO", message); }

    private void sendLog(String level, String message) {
        try {
            String vendorId = sessionManager.isLoggedIn() ? sessionManager.getVendorId() : "DESCONOCIDO";
            String vendorName = sessionManager.isLoggedIn() ? sessionManager.getVendorName() : "DESCONOCIDO";
            String businessName = sessionManager.isLoggedIn() ? sessionManager.getBusinessName() : "DESCONOCIDO";

            JsonObject jsonData = new JsonObject();
            jsonData.addProperty("level", level);
            jsonData.addProperty("message", message);
            jsonData.addProperty("timestamp", getCurrentTimestamp());
            jsonData.addProperty("vendor_id", vendorId);
            jsonData.addProperty("vendor_name", vendorName);
            jsonData.addProperty("business_name", businessName);
            jsonData.addProperty("app_version", appVersion);
            jsonData.addProperty("device_model", Build.MANUFACTURER + " " + Build.MODEL);
            jsonData.addProperty("android_version", Build.VERSION.RELEASE);

            RetrofitClient.getInstance(context).getApiService().sendLog(jsonData)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) Log.d(TAG, "✅ Log enviado");
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "❌ Error enviando log: " + t.getMessage());
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error en sendLog: " + e.getMessage());
        }
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
EOF    

- name: Create UI Fragments - Dashboard
  run: |
    cd android-app

    cat > app/src/main/java/com/omniventas/app/ui/DashboardFragment.java << 'EOF'
    package com.omniventas.app.ui;

    import android.os.Bundle;
    import android.os.Handler;
    import android.os.Looper;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.TextView;
    import android.widget.Toast;
    import androidx.annotation.NonNull;
    import androidx.annotation.Nullable;
    import androidx.fragment.app.Fragment;
    import androidx.recyclerview.widget.LinearLayoutManager;
    import androidx.recyclerview.widget.RecyclerView;
    import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
    import com.omniventas.app.R;
    import com.omniventas.app.adapters.VentaAdapter;
    import com.omniventas.app.api.ApiService;
    import com.omniventas.app.api.RetrofitClient;
    import com.omniventas.app.models.DashboardResponse;
    import com.omniventas.app.models.Venta;
    import com.omniventas.app.utils.SessionManager;
    import com.omniventas.app.utils.TelegramLogger;
    import java.util.List;
    import retrofit2.Call;
    import retrofit2.Callback;
    import retrofit2.Response;

    public class DashboardFragment extends Fragment {

        private TextView tvVentasHoy, tvIngresosHoy, tvVentasMes, tvIngresosMes, tvBajoStock;
        private RecyclerView rvVentasRecientes;
        private SwipeRefreshLayout swipeRefresh;
        private SessionManager sessionManager;
        private TelegramLogger logger;
        private VentaAdapter ventaAdapter;
        private Handler handler = new Handler(Looper.getMainLooper());
        private Runnable actualizacionAutomatica;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

            tvVentasHoy = view.findViewById(R.id.tv_ventas_hoy);
            tvIngresosHoy = view.findViewById(R.id.tv_ingresos_hoy);
            tvVentasMes = view.findViewById(R.id.tv_ventas_mes);
            tvIngresosMes = view.findViewById(R.id.tv_ingresos_mes);
            tvBajoStock = view.findViewById(R.id.tv_bajo_stock);
            rvVentasRecientes = view.findViewById(R.id.rv_ventas_recientes);
            swipeRefresh = view.findViewById(R.id.swipe_refresh);

            sessionManager = new SessionManager(getContext());
            logger = TelegramLogger.getInstance(getContext());

            ventaAdapter = new VentaAdapter();
            rvVentasRecientes.setLayoutManager(new LinearLayoutManager(getContext()));
            rvVentasRecientes.setAdapter(ventaAdapter);

            swipeRefresh.setOnRefreshListener(this::cargarDashboard);

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

        public void cargarDashboard() {
            String token = sessionManager.getToken();
            if (token == null || token.isEmpty()) {
                Toast.makeText(getContext(), "Sesión expirada", Toast.LENGTH_SHORT).show();
                swipeRefresh.setRefreshing(false);
                return;
            }

            ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
            apiService.getDashboard("Bearer " + token).enqueue(new Callback<DashboardResponse>() {
                @Override
                public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                    swipeRefresh.setRefreshing(false);
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        DashboardResponse.DashboardData data = response.body().getDashboard();
                        tvVentasHoy.setText(String.valueOf(data.getVentasHoy()));
                        tvIngresosHoy.setText("$" + String.format("%.2f", data.getIngresosHoy()));
                        tvVentasMes.setText(String.valueOf(data.getVentasMes()));
                        tvIngresosMes.setText("$" + String.format("%.2f", data.getIngresosMes()));
                        tvBajoStock.setText(String.valueOf(data.getProductosBajoStock()));

                        List<Venta> ventas = data.getVentasRecientes();
                        if (ventas != null) {
                            if (ventas.size() > 5) {
                                ventas = ventas.subList(0, 5);
                            }
                            ventaAdapter.setVentas(ventas);
                        }
                    }
                }

                @Override
                public void onFailure(Call<DashboardResponse> call, Throwable t) {
                    swipeRefresh.setRefreshing(false);
                    logger.networkError(t);
                }
            });
        }
    }
    EOF
