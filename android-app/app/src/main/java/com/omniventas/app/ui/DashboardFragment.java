package com.omniventas.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.omniventas.app.R;

public class DashboardFragment extends Fragment {

    private TextView tvVentasHoy, tvIngresosHoy, tvVentasMes, tvIngresosMes, tvBajoStock;
    private RecyclerView rvVentasRecientes;
    private SwipeRefreshLayout swipeRefresh;

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

        rvVentasRecientes.setLayoutManager(new LinearLayoutManager(getContext()));

        tvVentasHoy.setText("5");
        tvIngresosHoy.setText("$125.50");
        tvVentasMes.setText("42");
        tvIngresosMes.setText("$1,250.00");
        tvBajoStock.setText("3");

        return view;
    }
}
