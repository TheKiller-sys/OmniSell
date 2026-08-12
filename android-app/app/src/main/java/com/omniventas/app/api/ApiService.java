package com.omniventas.app.api;

import com.omniventas.app.models.LoginResponse;
import com.omniventas.app.models.VendorLoginRequest;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/login-vendedor")
    Call<LoginResponse> loginVendor(@Body VendorLoginRequest request);

    @POST("api/send-log")
    Call<Void> sendLog(@Body JsonObject logData);
}
