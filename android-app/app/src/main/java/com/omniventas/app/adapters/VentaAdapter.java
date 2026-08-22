package com.omniventas.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.omniventas.app.R;
import com.omniventas.app.models.Venta;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VentaAdapter extends RecyclerView.Adapter<VentaAdapter.ViewHolder> {
    private List<Venta> ventas = new ArrayList<>();
    private boolean showSyncStatus = false;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_venta, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Venta v = ventas.get(position);
        
        // Nombre del producto
        holder.tvProducto.setText(v.getProducto() != null ? v.getProducto() : "Producto sin nombre");
        
        // Fecha
        try {
            if (v.getFecha() != null && !v.getFecha().isEmpty()) {
                holder.tvFecha.setText(v.getFecha());
            } else {
                holder.tvFecha.setText("Fecha no disponible");
            }
        } catch (Exception e) {
            holder.tvFecha.setText("Fecha no disponible");
        }
        
        // Cantidad
        holder.tvCantidad.setText(v.getCantidad() + "x");
        
        // Total
        holder.tvTotal.setText("$" + String.format("%.2f", v.getTotal()));
        
        // Estado de sincronización
        if (showSyncStatus && v.isPendiente()) {
            holder.ivSyncStatus.setVisibility(View.VISIBLE);
        } else {
            holder.ivSyncStatus.setVisibility(View.GONE);
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

    public void setShowSyncStatus(boolean show) {
        this.showSyncStatus = show;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProducto, tvFecha, tvCantidad, tvTotal;
        ImageView ivSyncStatus;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProducto = itemView.findViewById(R.id.tv_producto);
            tvFecha = itemView.findViewById(R.id.tv_fecha);
            tvCantidad = itemView.findViewById(R.id.tv_cantidad);
            tvTotal = itemView.findViewById(R.id.tv_total);
            ivSyncStatus = itemView.findViewById(R.id.iv_sync_status);
        }
    }
                                  }
