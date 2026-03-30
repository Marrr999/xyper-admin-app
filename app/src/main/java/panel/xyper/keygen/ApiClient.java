package panel.xyper.keygen;

import android.content.Context;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import java.io.IOException;
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
 *   5. [NEW]      Handle HTTP 429 — auto retry dengan retry_after dari server
 *   6. [NEW]      HMAC signature header — dikirim kalau HMAC_SECRET di-set di local.properties
 */
public class ApiClient {

    private static final boolean DEBUG = false;

    // ── Retry config ─────────────────────────────────────────
    private static final int MAX_RETRY_COUNT    = 3;       // max berapa kali retry
    private static final long DEFAULT_RETRY_MS  = 5_000L;  // default wait kalau tidak ada retry_after

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
            })

            // ── HMAC Signature interceptor (opsional) ────────
            // Aktif kalau HmacHelper.isEnabled() = true
            // (bergantung apakah HMAC_SECRET ada di BuildConfig/local.properties)
            .addInterceptor(chain -> {
                Request original = chain.request();
                if (!HmacHelper.isEnabled()) {
                    return chain.proceed(original);
                }
                Request signed = HmacHelper.signRequest(original);
                return chain.proceed(signed);
            })

            // ── HTTP 429 Retry interceptor ───────────────────
            .addInterceptor(chain -> {
                Request request = chain.request();
                Response response = null;
                int retryCount = 0;

                while (retryCount < MAX_RETRY_COUNT) {
                    // Tutup response sebelumnya kalau ada
                    if (response != null) response.close();

                    response = chain.proceed(request);

                    // Bukan 429 → lanjutkan normal
                    if (response.code() != 429) break;

                    retryCount++;
                    if (retryCount >= MAX_RETRY_COUNT) break;

                    // Ambil retry_after dari response body
                    long waitMs = DEFAULT_RETRY_MS;
                    try {
                        String bodyStr = response.peekBody(512).string();
                        org.json.JSONObject json = new org.json.JSONObject(bodyStr);
                        int retryAfterSec = json.optInt("retry_after", 0);
                        if (retryAfterSec > 0) {
                            waitMs = retryAfterSec * 1000L;
                        }
                    } catch (Exception ignored) {}

                    // Tutup response sebelum sleep
                    response.close();

                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                return response;
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
