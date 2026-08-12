package com.omniventas.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static final String TAG = "RetrofitClient";
    private static RetrofitClient instance;
    private ApiService apiService;
    private static String API_URL = "https://prueba-1-omni.onrender.com/";
    private Context context;

    private RetrofitClient(Context context) {
        this.context = context.getApplicationContext();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // 🔥 Interceptor para agregar token a TODAS las llamadas
        Interceptor authInterceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();
                String url = original.url().toString();

                Log.d(TAG, "📡 URL: " + url);

                // Obtener token de SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences("OmniVentasSession", Context.MODE_PRIVATE);
                String token = prefs.getString("token", null);

                Log.d(TAG, "🔑 Token desde SharedPreferences: " + (token != null ? "PRESENTE (" + token.substring(0, Math.min(10, token.length())) + "...)" : "NULL"));

                Request request;
                if (token != null && !token.isEmpty()) {
                    // 🔥 IMPORTANTE: El token debe ir con el prefijo "Bearer "
                    String authHeader = "Bearer " + token;
                    Log.d(TAG, "🔐 Cabecera Authorization: " + authHeader.substring(0, Math.min(20, authHeader.length())) + "...");

                    request = original.newBuilder()
                            .header("Authorization", authHeader)
                            .build();
                } else {
                    Log.d(TAG, "⚠️ Sin token, continuando sin autenticación");
                    request = original;
                }

                // Log de la petición
                Log.d(TAG, "📤 Request - Method: " + request.method());
                Log.d(TAG, "📤 Request - Headers: " + request.headers());

                return chain.proceed(request);
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
        Log.d(TAG, "✅ RetrofitClient inicializado con URL: " + API_URL);
    }

    public static synchronized RetrofitClient getInstance(Context context) {
        if (instance == null) {
            instance = new RetrofitClient(context);
            Log.d(TAG, "🆕 Nueva instancia creada");
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }

    public static void setApiUrl(String url) {
        API_URL = url;
        instance = null;
        Log.d(TAG, "URL cambiada a: " + url);
    }

    public static String getApiUrl() {
        return API_URL;
    }
}
