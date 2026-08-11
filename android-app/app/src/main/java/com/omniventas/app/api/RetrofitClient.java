package com.omniventas.app.api;

import android.content.Context;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RetrofitClient {
    private static RetrofitClient instance;
    private ApiService apiService;
    private String apiUrl;
    private String authToken;
    private OkHttpClient client;

    private RetrofitClient() {
        // Por defecto, usar localhost para desarrollo
        this.apiUrl = "http://10.0.2.2:10000/"; // Emulador Android
        // Para dispositivo real: "https://tu-api.ondigitalocean.app/"
        buildClient();
    }

    private void buildClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        Interceptor authInterceptor = chain -> {
            Request original = chain.request();
            Request.Builder builder = original.newBuilder();
            
            if (authToken != null && !authToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + authToken);
            }
            
            return chain.proceed(builder.build());
        };

        client = new OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(apiUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build();

        apiService = retrofit.create(ApiService.class);
    }

    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        buildClient(); // Reconstruir cliente con nuevo token
    }

    public void setApiUrl(String url) {
        this.apiUrl = url.endsWith("/") ? url : url + "/";
        buildClient();
    }

    public static void setApiUrlStatic(String url) {
        getInstance().setApiUrl(url);
    }
}
