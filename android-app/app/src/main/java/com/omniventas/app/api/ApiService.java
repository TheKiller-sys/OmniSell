package com.omniventas.app.api;

import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.LoginRequest;
import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.models.Producto;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;

import java.util.List;  // ← ESTE IMPORT FALTABA

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {
    
    @POST("api/login-vendedor")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("api/productos")
    Call<List<Producto>> getProductos(@Header("Authorization") String token);

    @POST("api/registrar-venta-app")
    Call<VentaResponse> registrarVenta(@Header("Authorization") String token, @Body VentaRequest request);

    @GET("api/dashboard-app")
    Call<DashboardResponse> getDashboard(@Header("Authorization") String token);
}
