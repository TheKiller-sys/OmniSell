package com.omniventas.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.omniventas.app.R;
import com.omniventas.app.models.Producto;
import java.util.ArrayList;
import java.util.List;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ViewHolder> {

    private List<Producto> productos;

    public ProductoAdapter(List<Producto> productos) {
        this.productos = productos != null ? productos : new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_producto, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Producto p = productos.get(position);
        holder.tvNombre.setText(p.getNombre());
        holder.tvSeccion.setText(p.getSeccion());
        holder.tvPrecio.setText("$" + String.format("%.2f", p.getPrecio()));
        holder.tvStock.setText("Stock: " + p.getStock());

        if (p.getStock() <= 3) {
            holder.tvStock.setTextColor(holder.itemView.getContext().getColor(R.color.danger));
        } else {
            holder.tvStock.setTextColor(holder.itemView.getContext().getColor(R.color.gray));
        }
    }

    @Override
    public int getItemCount() {
        return productos.size();
    }

    public void updateData(List<Producto> newData) {
        this.productos = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvSeccion, tvPrecio, tvStock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tv_nombre);
            tvSeccion = itemView.findViewById(R.id.tv_seccion);
            tvPrecio = itemView.findViewById(R.id.tv_precio);
            tvStock = itemView.findViewById(R.id.tv_stock);
        }
    }
}
