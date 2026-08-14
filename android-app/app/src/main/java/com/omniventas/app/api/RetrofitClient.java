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
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class RetrofitClient {
    private static final String TAG = "RetrofitClient";
    private static RetrofitClient instance;
    private ApiService apiService;
    private static final String API_URL = "https://prueba-1-omni.onrender.com/";
    private Context context;

    private RetrofitClient(Context context) {
        this.context = context.getApplicationContext();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

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

        // ✅ Interceptor para loguear respuestas y depurar
        Interceptor responseInterceptor = chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            
            Log.d(TAG, "📡 Código de respuesta: " + response.code());
            Log.d(TAG, "📡 Mensaje: " + response.message());
            
            try {
                String bodyString = response.body().string();
                Log.d(TAG, "📡 Cuerpo de la respuesta: " + bodyString);
                
                okhttp3.MediaType contentType = response.body().contentType();
                okhttp3.ResponseBody newBody = okhttp3.ResponseBody.create(contentType, bodyString);
                
                return response.newBuilder()
                    .body(newBody)
                    .build();
            } catch (Exception e) {
                Log.e(TAG, "❌ Error leyendo respuesta: " + e.getMessage());
                return response;
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .addInterceptor(responseInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        // ✅ CREAR GSON CON LENIENTE ACTIVADO
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
            .setLenient()
            .create();

        // ✅ RETROFIT CON SCALARS PRIMERO (para texto plano) y GSON después (para JSON)
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(API_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build();

        apiService = retrofit.create(ApiService.class);
        Log.d(TAG, "✅ API URL: " + API_URL);
        Log.d(TAG, "✅ Gson con setLenient(true) activado");
        Log.d(TAG, "✅ ScalarsConverterFactory activado para manejar texto plano");
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
        return API_URL;
    }
}
