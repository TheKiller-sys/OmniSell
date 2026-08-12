package com.omniventas.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RetrofitClient {
    private static RetrofitClient instance;
    private ApiService apiService;
    private static String API_URL = "https://prueba-1-omni.onrender.com";
    private Context context;

    private RetrofitClient(Context context) {
        this.context = context.getApplicationContext();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Interceptor para agregar token automáticamente
        Interceptor authInterceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();

                // Obtener token de SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences("OmniVentasSession", Context.MODE_PRIVATE);
                String token = prefs.getString("token", null);

                if (token != null) {
                    Request request = original.newBuilder()
                        .header("Authorization", token)
                        .build();
                    return chain.proceed(request);
                }
                return chain.proceed(original);
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build();

        apiService = retrofit.create(ApiService.class);
    }

    public static synchronized RetrofitClient getInstance(Context context) {
        if (instance == null) {
            instance = new RetrofitClient(context);
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }

    public static void setApiUrl(String url) {
        API_URL = url;
        instance = null;
    }
}
