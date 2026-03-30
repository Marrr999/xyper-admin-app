package panel.xyper.keygen;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * ApiAuthHelper — generate auth headers untuk semua request ke /admin
 * Headers: X-Api-Key, X-Timestamp, X-Device-Id
 */
public class ApiAuthHelper {

    private static final String PREFS_NAME  = "xyper_auth";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_ROLE    = "role";
    private static final String KEY_SETUP   = "setup_done";

    // ── Simpan API Key setelah login ─────────────────────
    public static void saveSession(Context ctx, String apiKey, String role) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_ROLE, role)
            .putBoolean(KEY_SETUP, true)
            .apply();
    }

    // ── Hapus session (logout) ────────────────────────────
    public static void clearSession(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply();
    }

    // ── Cek apakah sudah login ────────────────────────────
    public static boolean isLoggedIn(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP, false);
    }

    // ── Ambil API Key tersimpan ───────────────────────────
    public static String getApiKey(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "");
    }

    // ── Ambil Role tersimpan ──────────────────────────────
    public static String getRole(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, "");
    }

    // ── Ambil Device ID unik ──────────────────────────────
    public static String getDeviceId(Context ctx) {
        return Settings.Secure.getString(
            ctx.getContentResolver(),
            Settings.Secure.ANDROID_ID
        );
    }

    // ── Generate semua auth headers untuk OkHttp ─────────
    public static void applyHeaders(Context ctx, okhttp3.Request.Builder builder) {
        builder.header("X-Api-Key",    getApiKey(ctx));
        builder.header("X-Timestamp",  String.valueOf(System.currentTimeMillis()));
        builder.header("X-Device-Id",  getDeviceId(ctx));
    }

    // ── Cek apakah Developer ──────────────────────────────
    public static boolean isDeveloper(Context ctx) {
        return "Developer".equals(getRole(ctx));
    }

    // ── Cek apakah Reseller ───────────────────────────────
    public static boolean isReseller(Context ctx) {
        return "Reseller".equals(getRole(ctx));
    }
}
