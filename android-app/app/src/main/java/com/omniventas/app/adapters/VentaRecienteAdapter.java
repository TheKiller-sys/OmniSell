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

public class VentaRecienteAdapter extends RecyclerView.Adapter<VentaRecienteAdapter.ViewHolder> {
    private List<Venta> ventas = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public VentaRecienteAdapter(List<Venta> ventas) {
        this.ventas = ventas != null ? ventas : new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_venta_reciente, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Venta v = ventas.get(position);
        
        // Nombre del producto
        holder.tvProducto.setText(v.getProducto() != null ? v.getProducto() : "Producto sin nombre");
        
        // Fecha formateada
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
    }

    @Override
    public int getItemCount() {
        return ventas.size();
    }

    public void updateData(List<Venta> newData) {
        this.ventas = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProducto, tvFecha, tvCantidad, tvTotal;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProducto = itemView.findViewById(R.id.tv_producto);
            tvFecha = itemView.findViewById(R.id.tv_fecha);
            tvCantidad = itemView.findViewById(R.id.tv_cantidad);
            tvTotal = itemView.findViewById(R.id.tv_total);
        }
    }
                                  }
