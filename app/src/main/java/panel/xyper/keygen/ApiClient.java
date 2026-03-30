package panel.xyper.keygen;

import android.content.Context;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import java.util.concurrent.TimeUnit;

/**
 * ApiClient — Singleton Retrofit instance
 *
 *   1. [SECURITY] URL diambil dari AppConstants (terenkripsi), tidak hardcode
 *   2. [SECURITY] Certificate Pinning dengan pin ASLI (bukan placeholder)
 *       - Pin 1 (Leaf)            : 9+O+dAfORDWv44N0IULe72LBnW2TWuMqLL6Zyna63wE=
 *       - Pin 2 (Intermediate CA) : kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=
 *   3. [SECURITY] Request timeout ketat (10s connect, 15s read/write)
 *   4. [BUG FIX]  Thread-safe singleton dengan double-checked locking
 */
public class ApiClient {

    private static final boolean DEBUG = false;

    // ── URL dari AppConstants (terenkripsi) ──────────────────
    private static String getBaseUrl() {
        return AppConstants.getBaseUrl();
    }

    // ── Singleton ────────────────────────────────────────────
    private static volatile AdminApiService adminService;
    private static volatile Context appContext;

    // ── Init context ─────────────────────────────────────────
    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    // ── Certificate Pinner ───────────────────────────────────
    //
    // Pin diperoleh dengan:
    //   openssl s_client -showcerts -connect xyper-api.djangkapjaya.workers.dev:443
    //   | ... | openssl dgst -sha256 -binary | base64
    //
    // Pin 1 — Leaf certificate (Cloudflare edge cert, rotate ~90 hari)
    // Pin 2 — Intermediate CA  (Cloudflare Inc ECC CA-3, lebih stabil)
    //
    // PENTING: Selalu ada 2 pin. Kalau leaf rotate dan hanya ada 1 pin → app mati.
    // Kalau leaf rotate, update Pin 1. Pin 2 (intermediate) jarang berubah.
    private static CertificatePinner buildCertPinner() {
        return new CertificatePinner.Builder()
            .add(
                AppConstants.getHost(),
                "sha256/9+O+dAfORDWv44N0IULe72LBnW2TWuMqLL6Zyna63wE="   // Leaf cert
            )
            .add(
                AppConstants.getHost(),
                "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="   // Intermediate CA
            )
            .build();
    }

    // ── OkHttp client ────────────────────────────────────────
    private static OkHttpClient buildHttpClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(
            DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE
        );

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            // ── Auth header interceptor ──────────────────────
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder reqBuilder = original.newBuilder();
                if (appContext != null && ApiAuthHelper.isLoggedIn(appContext)) {
                    ApiAuthHelper.applyHeaders(appContext, reqBuilder);
                }
                return chain.proceed(reqBuilder.build());
            });

        // Certificate Pinning — aktif di release
        if (!DEBUG) {
            builder.certificatePinner(buildCertPinner());
        }

        return builder.build();
    }

    // ── Admin API Service — Thread-safe Singleton ─────────────
    public static AdminApiService getAdminService() {
        if (adminService == null) {
            synchronized (ApiClient.class) {
                if (adminService == null) {
                    adminService = new Retrofit.Builder()
                        .baseUrl(getBaseUrl())
                        .client(buildHttpClient())
                        .addConverterFactory(ScalarsConverterFactory.create())
                        .build()
                        .create(AdminApiService.class);
                }
            }
        }
        return adminService;
    }

    // ── Reset — dipanggil saat logout ────────────────────────
    public static void reset() {
        synchronized (ApiClient.class) {
            adminService = null;
        }
    }
}
