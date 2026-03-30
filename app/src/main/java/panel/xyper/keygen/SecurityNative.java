package panel.xyper.keygen;

import android.content.Context;

/**
 * SecurityNative — JNI wrapper untuk native security checks.
 *
 * Checks yang dijalankan di native (.so):
 *   1. Frida Detection     — port scan + /proc/self/maps + unix socket
 *   2. Debugger Detection  — ptrace TRACEME + TracerPid + timing
 *   3. APK Signature       — SHA-256 via JNIEnv (lebih susah di-hook)
 */
public final class SecurityNative {

    static {
        System.loadLibrary("xyper_security");
    }

    private SecurityNative() {}

    /**
     * Deteksi Frida via port scan, /proc/self/maps, dan /proc/net/unix.
     * @return true jika Frida terdeteksi
     */
    public static native boolean isFridaPresent();

    /**
     * Deteksi debugger via ptrace, TracerPid, dan timing anomaly.
     * @return true jika debugger terdeteksi
     */
    public static native boolean isDebuggerPresent();

    /**
     * Verifikasi APK signature dari native layer.
     * @param ctx         Application context
     * @param expectedHex SHA-256 hex yang diharapkan (uppercase, no colon)
     * @return true jika signature cocok
     */
    public static native boolean isSignatureValid(Context ctx, String expectedHex);
}
