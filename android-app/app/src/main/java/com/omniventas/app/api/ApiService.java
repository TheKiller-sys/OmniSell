package com.omniventas.app.api;

import com.omniventas.app.models.DashboardResponse;
import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.models.RespuestaProductos;
import com.omniventas.app.models.VentaRequest;
import com.omniventas.app.models.VentaResponse;
import com.omniventas.app.models.VendorLoginRequest;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/login-vendedor")
    Call<LoginResponse> loginVendor(@Body VendorLoginRequest request);

    @GET("api/productos")
    Call<RespuestaProductos> getProductos(@Header("Authorization") String token);

    @POST("api/registrar-venta")
    Call<VentaResponse> registrarVenta(@Header("Authorization") String token, @Body VentaRequest request);

    @GET("api/dashboard-app")
    Call<DashboardResponse> getDashboard(@Header("Authorization") String token);

    @POST("api/send-log")
    Call<Void> sendLog(@Body JsonObject logData);
}
