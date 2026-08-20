package com.omniventas.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.omniventas.app.BuildConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class RetrofitClient {
    private static final String TAG = "RetrofitClient";
    private static RetrofitClient instance;
    private ApiService apiService;
    private String apiUrl;
    private Context context;

    private RetrofitClient(Context context) {
        this.context = context.getApplicationContext();
        
        // 🔥 OBTENER URL DESDE BUILDCONFIG
        this.apiUrl = BuildConfig.API_URL;
        
        // 🔥 SI LA URL ESTÁ VACÍA, USAR POR DEFECTO
        if (this.apiUrl == null || this.apiUrl.isEmpty()) {
            this.apiUrl = "https://omnisell-x19d.onrender.com/";
            Log.w(TAG, "⚠️ API_URL vacía, usando default: " + apiUrl);
        }
        
        // 🔥 ASEGURAR TRAILING SLASH
        if (!this.apiUrl.endsWith("/")) {
            this.apiUrl = this.apiUrl + "/";
        }
        
        Log.d(TAG, "🚀 Inicializando RetrofitClient");
        Log.d(TAG, "📍 URL: " + apiUrl);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);

        // Interceptor de autenticación
        Interceptor authInterceptor = chain -> {
            Request original = chain.request();
            SharedPreferences prefs = context.getSharedPreferences("OmniVentasSession", Context.MODE_PRIVATE);
            String token = prefs.getString("token", null);

            if (token != null && !token.isEmpty()) {
                Request request = original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
                return chain.proceed(request);
            }
            return chain.proceed(original);
        };

        // Interceptor de logging de respuestas
        Interceptor responseInterceptor = chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            
            Log.d(TAG, "📡 Código de respuesta: " + response.code());
            Log.d(TAG, "📡 URL: " + request.url());
            Log.d(TAG, "📡 Método: " + request.method());
            
            // 🔥 LOG DEL ERROR BODY SI EXISTE
            if (!response.isSuccessful()) {
                try {
                    String errorBody = response.body() != null ? response.body().string() : "null";
                    Log.e(TAG, "❌ Error Body: " + errorBody);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error leyendo errorBody: " + e.getMessage());
                }
            }
            
            return response;
        };

        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .addInterceptor(responseInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
            .setLenient()
            .create();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(apiUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build();

        apiService = retrofit.create(ApiService.class);
        Log.d(TAG, "✅ RetrofitClient inicializado correctamente");
        Log.d(TAG, "✅ URL final: " + apiUrl);
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

    public static String getApiUrl() {
        if (instance != null) {
            return instance.apiUrl;
        }
        return BuildConfig.API_URL != null ? BuildConfig.API_URL : "https://omnisell-x19d.onrender.com/";
    }
            }
