package com.omniventas.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
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
    private static final String TAG = "RetrofitClient";
    private static RetrofitClient instance;
    private ApiService apiService;
    private static String API_URL = "https://prueba-1-omni.onrender.com/";
    private Context context;

    private RetrofitClient(Context context) {
        this.context = context.getApplicationContext();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Interceptor para agregar token SOLO si no es el endpoint de logs
        Interceptor authInterceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();
                String url = original.url().toString();
                
                Log.d(TAG, "📡 URL: " + url);

                // ❌ NO agregar token a /api/send-log (es público)
                if (url.contains("/api/send-log")) {
                    Log.d(TAG, "🔓 Endpoint público (send-log), sin autenticación");
                    return chain.proceed(original);
                }

                // ✅ Agregar token a las demás llamadas
                SharedPreferences prefs = context.getSharedPreferences("OmniVentasSession", Context.MODE_PRIVATE);
                String token = prefs.getString("token", null);

                if (token != null && !token.isEmpty()) {
                    Request request = original.newBuilder()
                        .header("Authorization", token)
                        .build();
                    Log.d(TAG, "🔐 Token agregado a la cabecera");
                    return chain.proceed(request);
                } else {
                    Log.d(TAG, "⚠️ Sin token, continuando sin autenticación");
                    return chain.proceed(original);
                }
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
}
