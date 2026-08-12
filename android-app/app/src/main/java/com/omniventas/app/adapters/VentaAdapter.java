package com.omniventas.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.omniventas.app.R;
import com.omniventas.app.models.Venta;
import java.util.ArrayList;
import java.util.List;

public class VentaAdapter extends RecyclerView.Adapter<VentaAdapter.ViewHolder> {
    private List<Venta> ventas = new ArrayList<>();

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
        holder.tvProducto.setText(v.getProducto());
        holder.tvFecha.setText(v.getFecha());
        holder.tvCantidad.setText(v.getCantidad() + "x");
        holder.tvTotal.setText("$" + String.format("%.2f", v.getTotal()));
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
