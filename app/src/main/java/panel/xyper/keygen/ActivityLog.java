package panel.xyper.keygen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ActivityLog — sistem log lokal aksi admin
 *
 * Menyimpan riwayat aksi (create_key, ban_key, dll) ke SharedPreferences.
 * Dipakai oleh:
 *   - AccountPanel  → Recent Activity section
 *   - KeyViewer     → log setiap action (opsional)
 *   - KeyManager    → log saat create key
 *
 * Format entry di SharedPreferences:
 *   key = "activity_log"
 *   value = JSONArray of LogEntry
 *
 * Max 100 log tersimpan (oldest dihapus otomatis).
 */
public class ActivityLog {

    private static final String PREF_NAME  = "xyper_activity_log";
    private static final String PREF_KEY   = "activity_log";
    private static final int    MAX_LOGS   = 100;

    // ===================== LOG ENTRY =====================
    public static class LogEntry {
        public String action;
        public String target;
        public long   timestamp;

        public LogEntry(String action, String target, long timestamp) {
            this.action    = action;
            this.target    = target;
            this.timestamp = timestamp;
        }

        // Icon berdasarkan action name
        public String getIcon() {
            if (action == null) return "📋";
            if (action.contains("create"))   return "✦";
            if (action.contains("delete"))   return "🗑";
            if (action.contains("ban"))      return "⛔";
            if (action.contains("unban"))    return "✅";
            if (action.contains("extend"))   return "⏩";
            if (action.contains("edit"))     return "✏";
            if (action.contains("revoke"))   return "📵";
            if (action.contains("reset"))    return "🔄";
            if (action.contains("bulk"))     return "🗂";
            if (action.contains("config"))   return "⚙️";
            if (action.contains("login"))    return "🔐";
            if (action.contains("logout"))   return "⏻";
            return "📋";
        }

        public String getDisplayAction() {
            if (action == null) return "—";
            switch (action) {
                case "create_key":       return "Buat key baru";
                case "delete_key":       return "Hapus key";
                case "bulk_delete":      return "Bulk delete";
                case "edit_key":         return "Edit key";
                case "extend_key":       return "Extend key";
                case "ban_key":          return "Ban key";
                case "unban_key":        return "Unban key";
                case "ban_device":       return "Ban device";
                case "unban_device":     return "Unban device";
                case "revoke_devices":   return "Revoke devices";
                case "reset_violations": return "Reset violations";
                case "set_config":       return "Update config";
                case "login":            return "Login";
                case "logout":           return "Logout";
                default:                 return action;
            }
        }

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("action",    action    != null ? action    : "");
                o.put("target",    target    != null ? target    : "");
                o.put("timestamp", timestamp);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static LogEntry fromJson(JSONObject o) {
            if (o == null) return null;
            return new LogEntry(
                o.optString("action",    ""),
                o.optString("target",    ""),
                o.optLong  ("timestamp", 0L)
            );
        }
    }

    // ===================== PUBLIC API =====================

    /**
     * Catat satu aksi ke log lokal.
     *
     * @param ctx    Context
     * @param action nama action, contoh: "create_key", "ban_key"
     * @param target target aksi, biasanya plain key name atau encoded key
     */
    public static void log(Context ctx, String action, String target) {
        if (ctx == null || action == null) return;
        try {
            List<LogEntry> logs = getLogs(ctx);
            logs.add(0, new LogEntry(action, target, System.currentTimeMillis()));

            // Trim ke MAX_LOGS
            while (logs.size() > MAX_LOGS) {
                logs.remove(logs.size() - 1);
            }

            saveLogs(ctx, logs);
        } catch (Exception ignored) {}
    }

    /**
     * Ambil N log terbaru (urutan terbaru di atas).
     *
     * @param ctx   Context
     * @param limit jumlah maksimal log yang dikembalikan
     * @return List of LogEntry, bisa kosong tapi tidak null
     */
    public static List<LogEntry> getRecentLogs(Context ctx, int limit) {
        List<LogEntry> all = getLogs(ctx);
        if (all.size() <= limit) return all;
        return all.subList(0, limit);
    }

    /**
     * Ambil semua log.
     */
    public static List<LogEntry> getAllLogs(Context ctx) {
        return getLogs(ctx);
    }

    /**
     * Hapus semua log.
     */
    public static void clearAll(Context ctx) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_KEY).apply();
    }

    /**
     * Jumlah log tersimpan.
     */
    public static int count(Context ctx) {
        return getLogs(ctx).size();
    }

    // ===================== PRIVATE HELPERS =====================

    private static List<LogEntry> getLogs(Context ctx) {
        List<LogEntry> result = new ArrayList<LogEntry>();
        if (ctx == null) return result;
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(PREF_KEY, null);
            if (raw == null || raw.isEmpty()) return result;

            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                LogEntry entry = LogEntry.fromJson(arr.optJSONObject(i));
                if (entry != null) result.add(entry);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static void saveLogs(Context ctx, List<LogEntry> logs) {
        if (ctx == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (LogEntry e : logs) arr.put(e.toJson());
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
