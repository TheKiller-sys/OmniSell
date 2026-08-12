package com.omniventas.app.api;

import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.VendorLoginRequest;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.google.gson.JsonObject;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {
    
    // Login con ID de vendedor
    @POST("api/login-vendedor")
    Call<LoginResponse> loginVendor(@Body VendorLoginRequest request);

    @GET("api/productos")
    Call<List<Producto>> getProductos(@Header("Authorization") String token);

    @POST("api/registrar-venta-app")
    Call<VentaResponse> registrarVenta(@Header("Authorization") String token, @Body VentaRequest request);

    @GET("api/dashboard-app")
    Call<DashboardResponse> getDashboard(@Header("Authorization") String token);
    
    // ===== NUEVO: Envío de logs a Telegram (SIN autenticación) =====
    @POST("api/send-log")
    Call<Void> sendLog(@Body JsonObject logData);
}
