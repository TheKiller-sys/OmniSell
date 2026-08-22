package com.omniventas.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.omniventas.app.R;
import com.omniventas.app.models.Venta;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardVentaAdapter extends RecyclerView.Adapter<DashboardVentaAdapter.ViewHolder> {
    private List<Venta> ventas = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public DashboardVentaAdapter() {
        // Constructor vacío
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_dashboard_venta, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Venta v = ventas.get(position);
        
        // Posición (número de venta)
        holder.tvPosicion.setText("#" + (position + 1));
        
        // Nombre del producto
        holder.tvProducto.setText(v.getProducto() != null ? v.getProducto() : "Producto");
        
        // Cantidad
        holder.tvCantidad.setText("×" + v.getCantidad());
        
        // Total
        holder.tvTotal.setText("$" + String.format("%.2f", v.getTotal()));
        
        // Fecha
        try {
            if (v.getFecha() != null && !v.getFecha().isEmpty()) {
                holder.tvFecha.setText(v.getFecha());
            } else {
                holder.tvFecha.setText("--:--");
            }
        } catch (Exception e) {
            holder.tvFecha.setText("--:--");
        }
    }

    @Override
    public int getItemCount() {
        return ventas.size();
    }

    public void setVentas(List<Venta> ventas) {
        this.ventas = ventas != null ? ventas : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPosicion, tvProducto, tvCantidad, tvTotal, tvFecha;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPosicion = itemView.findViewById(R.id.tv_posicion);
            tvProducto = itemView.findViewById(R.id.tv_producto);
            tvCantidad = itemView.findViewById(R.id.tv_cantidad);
            tvTotal = itemView.findViewById(R.id.tv_total);
            tvFecha = itemView.findViewById(R.id.tv_fecha);
        }
    }
}
