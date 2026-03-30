package panel.xyper.keygen;

/**
 * AppConstants — Single source of truth untuk semua konstanta sensitif.
 *
 * Anti Reverse Engineering improvements:
 *   1. URL di-XOR encrypt dengan key 5 byte (tidak muncul sebagai plaintext)
 *   2. XOR key dipecah jadi 5 konstanta terpisah (susah dilacak di jadx)
 *   3. Semua string sensitif dikembalikan lewat method, bukan field publik
 *   4. Tambahan: fake/decoy strings untuk confuse decompiler
 *   5. Host string juga di-encrypt (bukan hardcode plaintext)
 */
public final class AppConstants {

    private AppConstants() {}

    // ── XOR Key — dipecah jadi 5 konstanta terpisah ────────
    private static final byte K0 = 0x58; // 'X'
    private static final byte K1 = 0x79; // 'y'
    private static final byte K2 = 0x70; // 'p'
    private static final byte K3 = 0x65; // 'e'
    private static final byte K4 = 0x72; // 'r'

    // ── Encrypted BASE URL ──────────────────────────────────
    // Plaintext : "https://xyper-api.djangkapjaya.workers.dev"
    // Key       : "Xyper" (repeating XOR)
    private static final byte[] ENC_BASE_URL = {
        0x30, 0x0D, 0x04, 0x15, 0x01, 0x62, 0x56, 0x5F,
        0x1D, 0x0B, 0x28, 0x1C, 0x02, 0x48, 0x13, 0x28,
        0x10, 0x5E, 0x01, 0x18, 0x39, 0x17, 0x17, 0x0E,
        0x13, 0x28, 0x13, 0x11, 0x1C, 0x13, 0x76, 0x0E,
        0x1F, 0x17, 0x19, 0x3D, 0x0B, 0x03, 0x4B, 0x16,
        0x3D, 0x0F
    };

    // ── Encrypted HOST ──────────────────────────────────────
    // Plaintext : "xyper-api.djangkapjaya.workers.dev"
    // (dipecah dari base url, tanpa "https://")
    private static final byte[] ENC_HOST = {
        0x20, 0x00, 0x00, 0x00, 0x00, 0x75, 0x18, 0x00,
        0x0C, 0x5C, 0x3C, 0x13, 0x11, 0x0B, 0x15, 0x33,
        0x18, 0x00, 0x0F, 0x13, 0x21, 0x18, 0x5E, 0x12,
        0x1D, 0x2A, 0x12, 0x05, 0x17, 0x01, 0x76, 0x1D,
        0x15, 0x13
    };

    // ── DECOY strings — confuse decompiler / analyst ────────
    // Ini string palsu yang tidak dipakai. Analyst yang lihat di jadx
    // akan kebingungan mana yang real.
    @SuppressWarnings("unused")
    private static final String _D0 = "https://api.xyper-backup.workers.dev";
    @SuppressWarnings("unused")
    private static final String _D1 = "https://xyper-panel.ngrok.io";
    @SuppressWarnings("unused")
    private static final String _D2 = "http://192.168.1.100:8080";

    // ── Public getters ──────────────────────────────────────

    /** Base URL untuk Retrofit */
    public static String getBaseUrl() {
        return xorDecrypt(ENC_BASE_URL) + "/";
    }

    /** Admin endpoint */
    public static String getAdminEndpoint() {
        return xorDecrypt(ENC_BASE_URL) + "/admin";
    }

    /** Host only — untuk cert pinning */
    public static String getHost() {
        // Host juga di-decrypt runtime, tidak hardcode plaintext
        return xorDecrypt(ENC_HOST);
    }

    // ── XOR Decrypt ─────────────────────────────────────────
    private static String xorDecrypt(byte[] data) {
        byte[] key    = { K0, K1, K2, K3, K4 };
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte)(data[i] ^ key[i % key.length]);
        }
        try {
            return new String(result, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    // ── Runtime string builder — untuk string sensitif lainnya
    // Gunakan ini kalau mau tambah konstanta baru yang perlu disembunyikan
    // Contoh: buildString(new int[]{0x58, 0x79, ...})
    static String buildString(int[] xorData) {
        return xorDecrypt(toBytes(xorData));
    }

    private static byte[] toBytes(int[] data) {
        byte[] b = new byte[data.length];
        for (int i = 0; i < data.length; i++) b[i] = (byte) data[i];
        return b;
    }
}
