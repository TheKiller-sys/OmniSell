package com.omniventas.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.omniventas.app.LoginActivity;
import com.omniventas.app.R;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.repository.OmniVentasRepository;
import com.omniventas.app.sync.SyncManager;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UsuarioFragment extends Fragment {

    private TextView tvVendorName, tvVendorCategory, tvMonthlyGoal;
    private ProgressBar progressMonthlyGoal;
    private Button btnCerrarSesion, btnSyncManual;
    private SessionManager sessionManager;
    private TelegramLogger logger;
    private OmniVentasRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usuario, container, false);

        tvVendorName = view.findViewById(R.id.tv_vendor_name);
        tvVendorCategory = view.findViewById(R.id.tv_vendor_category);
        tvMonthlyGoal = view.findViewById(R.id.tv_monthly_goal);
        progressMonthlyGoal = view.findViewById(R.id.progress_monthly_goal);
        btnCerrarSesion = view.findViewById(R.id.btn_cerrar_sesion);
        btnSyncManual = view.findViewById(R.id.btn_sync_manual);

        sessionManager = new SessionManager(getContext());
        logger = TelegramLogger.getInstance(getContext());
        repository = new OmniVentasRepository(getContext());

        String nombre = sessionManager.getVendorName();
        if (nombre != null) {
            tvVendorName.setText(nombre);
        }

        cargarDatosPerfil();

        btnSyncManual.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Sincronizando...", Toast.LENGTH_SHORT).show();
            SyncManager.syncNow(getContext());
            Toast.makeText(getContext(), "Sincronización iniciada", Toast.LENGTH_SHORT).show();
        });

        btnCerrarSesion.setOnClickListener(v -> {
            sessionManager.clearSession();
            logger.info("Sesión cerrada por el usuario");
            Toast.makeText(getContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().finish();
        });

        return view;
    }

    private void cargarDatosPerfil() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) return;

        ApiService apiService = RetrofitClient.getInstance(getContext()).getApiService();
        apiService.getDashboard("Bearer " + token).enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> call, Response<DashboardResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    DashboardResponse.DashboardData data = response.body().getDashboard();
                    
                    String businessName = data.getBusinessName();
                    if (businessName != null && !businessName.isEmpty()) {
                        tvVendorCategory.setText(businessName);
                    }

                    int ventasMes = data.getVentasMes();
                    int meta = 10000;
                    tvMonthlyGoal.setText("$" + ventasMes + "/$" + meta);
                    int progress = (int) ((double) ventasMes / meta * 100);
                    if (progress > 100) progress = 100;
                    progressMonthlyGoal.setProgress(progress);
                }
            }

            @Override
            public void onFailure(Call<DashboardResponse> call, Throwable t) {
                logger.networkError(t);
            }
        });
    }
}
