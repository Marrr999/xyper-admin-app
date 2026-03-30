package panel.xyper.keygen;

import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HmacHelper — Generate HMAC-SHA256 signature untuk setiap request ke Workers.
 *
 * Cara kerja:
 *   - Message = "{timestamp}.{body}"
 *   - Signature = HMAC-SHA256(message, HMAC_SECRET)
 *   - Header: X-Signature: {hex}
 *
 * HMAC_SECRET harus sama persis dengan env.HMAC_SECRET di Workers.
 * Simpan secret di local.properties, bukan hardcode di sini.
 *
 * Kalau HMAC_SECRET kosong ("") → fitur ini dinonaktifkan otomatis.
 * Workers-2-2 juga hanya enforce HMAC kalau env.HMAC_SECRET di-set.
 */
public class HmacHelper {

    // ── HMAC Secret ──────────────────────────────────────────
    // Isi dengan secret yang sama dengan Workers env.HMAC_SECRET
    // Kosongkan ("") untuk disable fitur ini
    private static final String HMAC_SECRET = BuildConfig.HMAC_SECRET;

    private HmacHelper() {}

    /**
     * Cek apakah HMAC signing aktif.
     * @return true kalau HMAC_SECRET tidak kosong
     */
    public static boolean isEnabled() {
        return HMAC_SECRET != null && !HMAC_SECRET.isEmpty();
    }

    /**
     * Sign request dengan menambahkan header X-Signature.
     * Hanya POST request yang di-sign (GET tidak punya body).
     *
     * @param original Request asli dari OkHttp chain
     * @return Request baru dengan X-Signature header ditambahkan
     */
    public static Request signRequest(Request original) {
        if (!isEnabled()) return original;

        // Hanya sign POST
        if (!"POST".equals(original.method())) return original;

        try {
            String timestamp = original.header("X-Timestamp");
            if (timestamp == null) {
                timestamp = String.valueOf(System.currentTimeMillis());
            }

            // Baca body
            String body = "";
            RequestBody rb = original.body();
            if (rb != null) {
                Buffer buffer = new Buffer();
                rb.writeTo(buffer);
                body = buffer.readUtf8();
            }

            // Compute HMAC
            String message   = timestamp + "." + body;
            String signature = computeHmac(message, HMAC_SECRET);

            // Tambah header X-Signature
            return original.newBuilder()
                .header("X-Signature", signature)
                .build();

        } catch (Exception e) {
            // Kalau gagal sign, lanjutkan tanpa signature
            return original;
        }
    }

    /**
     * Compute HMAC-SHA256 dan return sebagai hex string.
     */
    private static String computeHmac(String message, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(rawHmac);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
