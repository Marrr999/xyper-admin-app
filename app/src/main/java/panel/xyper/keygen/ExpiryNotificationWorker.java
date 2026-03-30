package panel.xyper.keygen;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ExpiryNotificationWorker
 *
 * Upgrade dari versi sebelumnya:
 *   1. [SECURITY] URL diambil dari AppConstants, tidak hardcode
 *   2. [BUG FIX]  Tidak buat OkHttpClient sendiri — pakai ApiClient yang sudah ada
 *   3. [BUG FIX]  Auth headers diambil via ApiAuthHelper (sama seperti request lain)
 */
public class ExpiryNotificationWorker extends Worker {

    private static final String WORK_NAME    = "xyper_expiry_check";
    private static final String CHANNEL_ID   = "xyper_expiry";
    private static final String CHANNEL_NAME = "Key Expiry Alerts";
    private static final int    NOTIF_ID     = 1001;

    public ExpiryNotificationWorker(@NonNull Context context,
                                    @NonNull WorkerParameters params) {
        super(context, params);
    }

    // ── Schedule / Cancel ─────────────────────────────────────
    public static void schedule(Context ctx) {
        if (!SettingsPanel.isNotifExpiryEnabled(ctx)) return;

        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
            ExpiryNotificationWorker.class, 12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build();

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            work
        );
    }

    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME);
    }

    // ── Worker logic ──────────────────────────────────────────
    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!SettingsPanel.isNotifExpiryEnabled(ctx)) return Result.success();
        if (!ApiAuthHelper.isLoggedIn(ctx)) return Result.success();

        try {
            // [BUG FIX] Gunakan ApiClient.getAdminService() — tidak buat OkHttpClient baru
            // [SECURITY] URL diambil dari AppConstants via ApiClient (tidak hardcode)
            // Auth headers (X-Api-Key, X-Timestamp, X-Device-Id) di-inject otomatis
            // oleh ApiClient interceptor — tidak perlu set manual lagi

            retrofit2.Response<String> response =
                ApiClient.getAdminService().getAllKeys().execute();

            if (!response.isSuccessful() || response.body() == null) {
                return Result.retry();
            }

            String body = response.body();
            JSONArray keys;
            try {
                keys = new JSONArray(body);
            } catch (Exception e) {
                return Result.retry();
            }

            // ── Alert days dari Settings ───────────────────
            String savedDays = SettingsPanel.getNotifDays(ctx);
            int customDays   = SettingsPanel.getNotifCustomDays(ctx);
            List<Integer> alertDays = parseDays(savedDays, customDays);
            if (alertDays.isEmpty()) return Result.success();

            long now = System.currentTimeMillis();
            List<String> expiringKeys = new ArrayList<>();
            List<String> expiredKeys  = new ArrayList<>();

            for (int i = 0; i < keys.length(); i++) {
                JSONObject k = keys.optJSONObject(i); // [BUG FIX] pakai optJSONObject
                if (k == null) continue;

                long exp         = k.optLong("expired", 0);
                long bannedUntil = k.optLong("banned_until", 0);
                String role      = k.optString("role", "");

                String raw  = k.optString("key", "");
                String name = raw;
                try {
                    byte[] dec = android.util.Base64.decode(raw, android.util.Base64.DEFAULT);
                    name = new String(dec, "UTF-8");
                } catch (Exception ignored) {}

                if (bannedUntil > now && !"Developer".equals(role)) continue;
                if (exp == 0) continue;

                if (exp < now) {
                    if ((now - exp) < 24 * 3600000L) expiredKeys.add(name);
                    continue;
                }

                long msLeft   = exp - now;
                long daysLeft = msLeft / 86400000L;

                for (int alertDay : alertDays) {
                    if (Math.abs(msLeft - alertDay * 86400000L) < 43200000L) {
                        expiringKeys.add(name + " (" + daysLeft + "d left)");
                        break;
                    }
                }
            }

            if (!expiringKeys.isEmpty()) {
                fireNotification(ctx, "⏰  Keys Expiring Soon", expiringKeys, NOTIF_ID);
            }
            if (!expiredKeys.isEmpty()) {
                fireNotification(ctx, "🔴  Keys Just Expired", expiredKeys, NOTIF_ID + 1);
            }

        } catch (Exception e) {
            return Result.retry();
        }

        return Result.success();
    }

    // ── Notification ──────────────────────────────────────────
    private void fireNotification(Context ctx, String title,
                                  List<String> keys, int notifId) {
        NotificationManager nm = (NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        createChannel(nm);

        StringBuilder bodyBuilder = new StringBuilder();
        int max = Math.min(keys.size(), 5);
        for (int i = 0; i < max; i++) {
            bodyBuilder.append("• ").append(keys.get(i));
            if (i < max - 1) bodyBuilder.append("\n");
        }
        if (keys.size() > 5) {
            bodyBuilder.append("\n...and ").append(keys.size() - 5).append(" more");
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
            Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(keys.size() == 1
                    ? bodyBuilder.toString()
                    : keys.size() + " keys need attention")
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(bodyBuilder.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(0xFF22E5FF);

        nm.notify(notifId, builder.build());
    }

    private void createChannel(NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Alerts for keys about to expire");
            nm.createNotificationChannel(ch);
        }
    }

    private List<Integer> parseDays(String saved, int custom) {
        List<Integer> days = new ArrayList<>();
        for (String s : saved.split(",")) {
            try { days.add(Integer.parseInt(s.trim())); }
            catch (Exception ignored) {}
        }
        if (custom > 0 && !days.contains(custom)) days.add(custom);
        return days;
    }
}
