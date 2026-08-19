package com.omniventas.app.repository;

import android.content.Context;
import android.util.Log;
import com.omniventas.app.api.ApiService;
import com.omniventas.app.api.RetrofitClient;
import com.omniventas.app.local.AppDatabase;
import com.omniventas.app.local.ProductoEntity;
import com.omniventas.app.local.VentaEntity;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.RespuestaProductos;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.omniventas.app.utils.SessionManager;
import com.omniventas.app.utils.TelegramLogger;
import com.omniventas.app.sync.SyncManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OmniVentasRepository {
    private static final String TAG = "OmniVentasRepository";
    private final AppDatabase database;
    private final SessionManager sessionManager;
    private final TelegramLogger logger;
    private final Context context;

    public OmniVentasRepository(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        this.sessionManager = new SessionManager(context);
        this.logger = TelegramLogger.getInstance(context);
    }

    // PRODUCTOS
    public List<ProductoEntity> getProductosLocal() {
        return database.productoDao().getAll();
    }

    public List<ProductoEntity> buscarProductosLocal(String query) {
        if (query == null || query.isEmpty()) {
            return database.productoDao().getAll();
        }
        return database.productoDao().search(query);
    }

    public void syncProductosFromServer() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.d(TAG, "No hay token, usando datos locales");
            return;
        }

        ApiService api = RetrofitClient.getInstance(context).getApiService();
        api.getProductos("Bearer " + token).enqueue(new Callback<RespuestaProductos>() {
            @Override
            public void onResponse(Call<RespuestaProductos> call, Response<RespuestaProductos> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Producto> productos = response.body().getProductos();
                    List<ProductoEntity> entities = new ArrayList<>();
                    
                    for (Producto p : productos) {
                        ProductoEntity entity = new ProductoEntity();
                        entity.setId(p.getId());
                        entity.setNombre(p.getNombre());
                        entity.setSeccion(p.getSeccion());
                        entity.setPrecio(p.getPrecio());
                        entity.setStock(p.getStock());
                        entity.setDescripcion(p.getDescripcion() != null ? p.getDescripcion() : "");
                        entity.setLastSync(System.currentTimeMillis());
                        entity.setDeleted(false);
                        entities.add(entity);
                    }
                    
                    database.productoDao().insertAll(entities);
                    Log.d(TAG, "✅ Productos sincronizados: " + entities.size());
                    logger.success("Productos sincronizados desde servidor");
                }
            }

            @Override
            public void onFailure(Call<RespuestaProductos> call, Throwable t) {
                Log.e(TAG, "❌ Error sincronizando productos: " + t.getMessage());
            }
        });
    }

    // VENTAS OFFLINE
    public void registrarVentaOffline(int productoId, String productoNombre, int cantidad, double precioUnitario) {
        VentaEntity venta = new VentaEntity();
        venta.setProductoId(productoId);
        venta.setProductoNombre(productoNombre);
        venta.setCantidad(cantidad);
        venta.setPrecioUnitario(precioUnitario);
        venta.setTotal(cantidad * precioUnitario);
        venta.setFecha(System.currentTimeMillis());
        venta.setVendorId(sessionManager.getVendorId());
        venta.setSincronizado(false);
        venta.setError(null);

        long id = database.ventaDao().insert(venta);
        Log.d(TAG, "✅ Venta guardada localmente (ID: " + id + ")");
        logger.success("Venta registrada offline: " + productoNombre + " x" + cantidad);

        // Reducir stock local
        ProductoEntity producto = database.productoDao().getById(productoId);
        if (producto != null) {
            producto.setStock(producto.getStock() - cantidad);
            database.productoDao().update(producto);
        }

        // Intentar sincronizar inmediatamente si hay conexión
        SyncManager.syncNow(context);
    }

    public void trySyncVentas() {
        SyncManager.syncNow(context);
    }

    public List<VentaEntity> getVentasPendientes() {
        return database.ventaDao().getPendientes();
    }

    public List<VentaEntity> getVentasSincronizadas() {
        return database.ventaDao().getSincronizadas();
    }

    public int getVentasPendientesCount() {
        return database.ventaDao().getPendientesCount();
    }

    // ESTADÍSTICAS
    public int getTotalProductos() {
        return database.productoDao().getAll().size();
    }

    public int getStockBajo() {
        int count = 0;
        for (ProductoEntity p : database.productoDao().getAll()) {
            if (p.getStock() <= 3 && p.getStock() > 0) count++;
        }
        return count;
    }

    public int getSinStock() {
        int count = 0;
        for (ProductoEntity p : database.productoDao().getAll()) {
            if (p.getStock() == 0) count++;
        }
        return count;
    }
}
