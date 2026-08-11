package com.omniventas.app.ui;

import android.os.Bundle;
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
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.models.Producto;
import com.omniventas.app.utils.SessionManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InventarioFragment extends Fragment {

    private RecyclerView rvInventario;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalProductos, tvBajoStock;
    private SessionManager sessionManager;
    private InventarioAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inventario, container, false);
        
        rvInventario = view.findViewById(R.id.rv_inventario);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        tvTotalProductos = view.findViewById(R.id.tv_total_productos);
        tvBajoStock = view.findViewById(R.id.tv_bajo_stock);
        
        sessionManager = new SessionManager(getContext());
        
        rvInventario.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InventarioAdapter();
        rvInventario.setAdapter(adapter);
        
        swipeRefresh.setOnRefreshListener(this::cargarInventario);
        
        cargarInventario();
        
        return view;
    }

    private void cargarInventario() {
        swipeRefresh.setRefreshing(true);
        String token = sessionManager.getToken();
        Call<List<Producto>> call = RetrofitClient.getInstance().getApiService().getProductos("Bearer " + token);
        
        call.enqueue(new Callback<List<Producto>>() {
            @Override
            public void onResponse(Call<List<Producto>> call, Response<List<Producto>> response) {
                swipeRefresh.setRefreshing(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    List<Producto> productos = response.body();
                    adapter.setProductos(productos);
                    
                    tvTotalProductos.setText("Total: " + productos.size() + " productos");
                    
                    int bajoStock = 0;
                    for (Producto p : productos) {
                        if (p.getStock() <= 5) bajoStock++;
                    }
                    tvBajoStock.setText("⚠️ Stock bajo: " + bajoStock);
                } else {
                    Toast.makeText(getContext(), "Error al cargar inventario", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Producto>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error de conexión: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Adapter para inventario
    private static class InventarioAdapter extends RecyclerView.Adapter<InventarioAdapter.ViewHolder> {
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
            holder.tvNombre.setText(p.getNombre());
            holder.tvSeccion.setText(p.getSeccion());
            holder.tvPrecio.setText(String.format("$%.2f", p.getPrecio()));
            holder.tvStock.setText("Stock: " + p.getStock());
            
            // Cambiar color según stock
            if (p.getStock() <= 0) {
                holder.tvStock.setTextColor(holder.itemView.getContext().getColor(android.R.color.holo_red_dark));
            } else if (p.getStock() <= 5) {
                holder.tvStock.setTextColor(holder.itemView.getContext().getColor(android.R.color.holo_orange_dark));
            } else {
                holder.tvStock.setTextColor(holder.itemView.getContext().getColor(android.R.color.holo_green_dark));
            }
        }

        @Override
        public int getItemCount() {
            return productos.size();
        }

        public void setProductos(List<Producto> productos) {
            this.productos = productos;
            notifyDataSetChanged();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNombre, tvSeccion, tvPrecio, tvStock;
            ViewHolder(View itemView) {
                super(itemView);
                tvNombre = itemView.findViewById(R.id.tv_nombre);
                tvSeccion = itemView.findViewById(R.id.tv_seccion);
                tvPrecio = itemView.findViewById(R.id.tv_precio);
                tvStock = itemView.findViewById(R.id.tv_stock);
            }
        }
    }
}
