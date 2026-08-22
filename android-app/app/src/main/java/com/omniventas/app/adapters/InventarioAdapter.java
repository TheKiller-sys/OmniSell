package com.omniventas.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.omniventas.app.R;
import com.omniventas.app.models.Producto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventarioAdapter extends RecyclerView.Adapter<InventarioAdapter.ViewHolder> {
    private List<Producto> productos = new ArrayList<>();

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
        
        // NOMBRE (arriba, legible, sin dividir)
        holder.tvNombre.setText(p.getNombre());
        
        // SECCIÓN/CATEGORÍA
        holder.tvSeccion.setText(p.getSeccion() != null ? p.getSeccion() : "Sin categoría");
        
        // PRECIO (abajo izquierda)
        holder.tvPrecio.setText("$" + String.format("%.2f", p.getPrecio()));
        
        // STOCK (abajo derecha)
        holder.tvStock.setText(String.valueOf(p.getStock()));

        // Colores según stock (solo el texto del stock)
        if (p.getStock() == 0) {
            holder.tvStock.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.danger));
        } else if (p.getStock() <= 3) {
            holder.tvStock.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.warning));
        } else {
            holder.tvStock.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
        }
        
        // Fondo de la tarjeta siempre blanco
        holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
    }

    @Override
    public int getItemCount() {
        return productos.size();
    }

    public void setProductos(List<Producto> productos) {
        this.productos = productos != null ? productos : new ArrayList<>();
        Collections.sort(this.productos, (p1, p2) -> p1.getNombre().compareToIgnoreCase(p2.getNombre()));
        notifyDataSetChanged();
    }

    public List<Producto> getProductos() {
        return productos;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvSeccion, tvPrecio, tvStock;
        CardView cardView;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tv_nombre);
            tvSeccion = itemView.findViewById(R.id.tv_seccion);
            tvPrecio = itemView.findViewById(R.id.tv_precio);
            tvStock = itemView.findViewById(R.id.tv_stock);
            cardView = (CardView) itemView;
        }
    }
}
