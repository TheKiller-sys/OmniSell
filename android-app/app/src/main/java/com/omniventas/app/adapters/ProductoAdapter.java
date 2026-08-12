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
    private List<Producto> productos = new ArrayList<>();
    private OnProductoClickListener listener;

    public interface OnProductoClickListener {
        void onProductoClick(Producto producto);
    }

    public ProductoAdapter(OnProductoClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_producto_busqueda, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Producto p = productos.get(position);
        holder.tvNombre.setText(p.getNombre());
        holder.tvSeccion.setText(p.getSeccion() != null ? p.getSeccion() : "Sin categoría");
        holder.tvPrecio.setText("$" + String.format("%.2f", p.getPrecio()));
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProductoClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return productos.size();
    }

    public void setProductos(List<Producto> productos) {
        this.productos = productos != null ? productos : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void filter(String query) {
        // El filtrado se maneja desde el fragmento
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvSeccion, tvPrecio;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tv_nombre_busqueda);
            tvSeccion = itemView.findViewById(R.id.tv_seccion_busqueda);
            tvPrecio = itemView.findViewById(R.id.tv_precio_busqueda);
        }
    }
}
