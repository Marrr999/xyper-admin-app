package panel.xyper.keygen;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * AdminApiService — Retrofit interface untuk /admin endpoint
 * Base URL: https://xyper-api.djangkapjaya.workers.dev
 * Auth headers di-inject otomatis oleh ApiClient interceptor
 */
public interface AdminApiService {

    // GET /admin → load semua keys (filtered by role di Workers)
    @GET("/admin")
    Call<String> getAllKeys();

    // POST /admin → semua actions (create, delete, ban, dll)
    @POST("/admin")
    Call<String> postAction(@Body String jsonBody);
}
