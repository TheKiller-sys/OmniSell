package com.omniventas.app.sync;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Response;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private SessionManager sessionManager;
    private AppDatabase database;
    private TelegramLogger logger;
    private ExecutorService executor;
    private boolean isSyncing = false;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        sessionManager = new SessionManager(context);
        database = AppDatabase.getInstance(context);
        logger = TelegramLogger.getInstance(context);
        executor = Executors.newSingleThreadExecutor();
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "Usuario no logueado, cancelando sincronización");
            return Result.success();
        }

        // Evitar sincronización simultánea
        if (isSyncing) {
            Log.d(TAG, "Sincronización ya en curso, omitiendo");
            return Result.success();
        }

        isSyncing = true;
        Log.d(TAG, "Iniciando sincronización en segundo plano...");

        try {
            // 1. Sincronizar productos
            syncProductos();

            // 2. Enviar ventas pendientes
            syncVentasPendientes();

        } finally {
            isSyncing = false;
        }

        return Result.success();
    }

    private void syncProductos() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) return;

        try {
            Call<RespuestaProductos> call = RetrofitClient.getInstance(getApplicationContext())
                .getApiService().getProductos("Bearer " + token);
            
            Response<RespuestaProductos> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                List<Producto> productos = response.body().getProductos();
                
                executor.execute(() -> {
                    try {
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
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error guardando productos en SyncWorker: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sincronizando productos: " + e.getMessage());
        }
    }

    private void syncVentasPendientes() {
        List<VentaEntity> pendientes = database.ventaDao().getPendientes();
        
        if (pendientes.isEmpty()) {
            Log.d(TAG, "No hay ventas pendientes");
            return;
        }

        Log.d(TAG, "Enviando " + pendientes.size() + " ventas pendientes...");

        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.d(TAG, "No hay token, ventas pendientes no enviadas");
            return;
        }

        for (VentaEntity venta : pendientes) {
            try {
                // 🔥 IMPORTANTE: Verificar que la venta no haya sido ya sincronizada
                VentaEntity ventaActualizada = database.ventaDao().getById(venta.getId());
                if (ventaActualizada == null || ventaActualizada.isSincronizado()) {
                    Log.d(TAG, "Venta ya sincronizada, omitiendo: " + venta.getId());
                    continue;
                }

                VentaRequest request = new VentaRequest(
                    venta.getProductoId(),
                    venta.getCantidad(),
                    venta.getPrecioUnitario()
                );

                Call<VentaResponse> call = RetrofitClient.getInstance(getApplicationContext())
                    .getApiService().registrarVenta("Bearer " + token, request);
                
                Response<VentaResponse> response = call.execute();

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    // ✅ Marcar como sincronizada
                    executor.execute(() -> {
                        try {
                            database.ventaDao().marcarSincronizada(venta.getId());
                            Log.d(TAG, "✅ Venta sincronizada: " + venta.getProductoNombre());
                            logger.success("Venta sincronizada offline: " + venta.getProductoNombre());
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error marcando venta como sincronizada: " + e.getMessage());
                        }
                    });
                } else {
                    String errorMsg = "Error al sincronizar venta";
                    if (response.errorBody() != null) {
                        errorMsg = response.errorBody().string();
                    }
                    final String finalError = errorMsg;
                    executor.execute(() -> {
                        try {
                            database.ventaDao().setError(venta.getId(), finalError);
                            Log.e(TAG, "❌ Error sincronizando venta: " + finalError);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error guardando error: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error enviando venta: " + e.getMessage());
                final String error = e.getMessage();
                executor.execute(() -> {
                    try {
                        database.ventaDao().setError(venta.getId(), error);
                    } catch (Exception e2) {
                        Log.e(TAG, "❌ Error guardando error: " + e2.getMessage());
                    }
                });
            }
        }

        executor.execute(() -> {
            try {
                database.ventaDao().deleteSincronizadas();
                Log.d(TAG, "✅ Ventas sincronizadas eliminadas");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error eliminando ventas sincronizadas: " + e.getMessage());
            }
        });
    }
                }
