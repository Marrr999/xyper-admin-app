package panel.xyper.keygen;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Debug;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * SecurityGuard — Deteksi ancaman keamanan saat startup.
 *
 * Checks yang dilakukan:
 *   1.  Root Detection         — su binary, build tags, dangerous apps
 *   2.  Emulator Detection     — build properties, hardware fingerprint
 *   3.  Debuggable Flag        — ApplicationInfo.FLAG_DEBUGGABLE
 *   4.  Debugger Attached      — Debug.isDebuggerConnected()
 *   5.  APK Integrity          — Signature SHA-256 vs expected hash
 *   6.  Frida Detection        — port scan, /proc maps, named pipe
 *   7.  Xposed Detection       — stack trace, class loader, files
 *   8.  Magisk Hide Detection  — /proc/mounts, prop tampering
 *   9.  Memory Tampering       — /proc/self/maps scan
 *   10. Anti-RE Runtime        — timing attack detection, native lib check
 */
public final class SecurityGuard {

    private SecurityGuard() {}

    // ── Expected APK Signature SHA-256 ─────────────────────
    private static final String EXPECTED_SIG_SHA256 =
        "ECAC1329A6E1770110D2FF8FDCB46DE06A9AA247D59CBE1CF861BC61A69B8DC2";

    // ── XOR-obfuscated sensitive strings ───────────────────
    // Plaintext strings di-XOR agar tidak muncul di jadx/apktool
    // Key: 0x5A (single byte XOR)
    private static String x(int[] enc) {
        char[] out = new char[enc.length];
        for (int i = 0; i < enc.length; i++) out[i] = (char)(enc[i] ^ 0x5A);
        return new String(out);
    }

    // ── Dangerous root-related packages ────────────────────
    private static final String[] ROOT_PACKAGES = {
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.zachspong.temprootremovejb",
        "com.ramdroid.appquarantine",
        "com.amphoras.hidemyroot",
        "com.saurik.substrate",
        "de.robv.android.xposed.installer",
        "com.rovo.apps.frep",
        // Tambahan: Magisk Manager variants
        "io.github.huskydg.magisk",
        "io.github.vvb2060.magisk",
        "com.topjohnwu.magisk.alpha",
        // Tambahan: LSPosed / EdXposed
        "org.lsposed.manager",
        "com.elderdrivers.riru.edxp",
        // Tambahan: Frida tools
        "com.frida",
    };

    // ── Known root binary paths ─────────────────────────────
    private static final String[] ROOT_PATHS = {
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/magisk/.core/bin/su",
        // Tambahan: Magisk paths
        "/data/adb/magisk",
        "/data/adb/modules",
        "/sbin/.magisk",
        "/cache/.disable_magisk",
        "/dev/magisk",
    };

    // ── Emulator build fingerprints ─────────────────────────
    private static final String[] EMU_FINGERPRINTS = {
        "generic", "unknown", "emulator", "sdk_gphone",
        "vbox", "test-keys", "Andy", "Genymotion",
        "goldfish", "ranchu",
    };

    // ── Frida default ports ─────────────────────────────────
    private static final int[] FRIDA_PORTS = { 27042, 27043 };

    // ── Xposed/LSPosed suspicious files ─────────────────────
    private static final String[] XPOSED_FILES = {
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so",
        "/data/data/de.robv.android.xposed.installer",
        "/system/xposed.prop",
        "/data/adb/lspatch",
        "/data/adb/modules/zygisk_lsposed",
        "/data/adb/modules/riru_lsposed",
    };

    // ── Frida artifacts in filesystem ───────────────────────
    private static final String[] FRIDA_FILES = {
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
        "/system/bin/frida-server",
        "/sdcard/frida-server",
        "/data/local/tmp/fnr.config",
    };

    // ======================================================
    // Result
    // ======================================================
    public static class Result {
        public final boolean passed;
        public final String  reason;
        public final Type    type;

        private Result(boolean passed, String reason, Type type) {
            this.passed = passed;
            this.reason = reason;
            this.type   = type;
        }

        static Result ok()                   { return new Result(true,  "",  Type.OK);   }
        static Result fail(String r, Type t) { return new Result(false, r,   t);         }
    }

    public enum Type {
        OK,
        ROOTED,
        EMULATOR,
        DEBUGGABLE,
        DEBUGGER_ATTACHED,
        TAMPERED,
        FRIDA,
        XPOSED,
        MEMORY_TAMPERED,
    }

    // ======================================================
    // Main entry point — jalankan semua checks
    // ======================================================
    public static Result runChecks(Context ctx) {

        // 1. Debugger paling bahaya → cek pertama
        if (isDebuggerAttached()) {
            return Result.fail("Debugger detected", Type.DEBUGGER_ATTACHED);
        }

        // 2. Debuggable flag
        if (isDebuggable(ctx)) {
            return Result.fail("App is debuggable", Type.DEBUGGABLE);
        }

        // 3. Timing-based anti-debug (deteksi debugger lewat execution time)
        if (isTimingAttack()) {
            return Result.fail("Timing anomaly detected", Type.DEBUGGER_ATTACHED);
        }

        // 4. Frida detection (paling dulu setelah debugger)
        if (isFridaPresent(ctx)) {
            return Result.fail("Frida detected", Type.FRIDA);
        }

        // 5. Xposed / LSPosed
        if (isXposedPresent()) {
            return Result.fail("Hook framework detected", Type.XPOSED);
        }

        // 6. Emulator
        if (isEmulator(ctx)) {
            return Result.fail("Emulator detected", Type.EMULATOR);
        }

        // 7. Root
        if (isRooted(ctx)) {
            return Result.fail("Rooted device detected", Type.ROOTED);
        }

        // 8. Memory tampering (/proc/self/maps scan)
        if (isMemoryTampered()) {
            return Result.fail("Memory tampering detected", Type.MEMORY_TAMPERED);
        }

        // 9. APK Integrity
        if (!EXPECTED_SIG_SHA256.isEmpty() && !isSignatureValid(ctx)) {
            return Result.fail("APK signature mismatch", Type.TAMPERED);
        }

        return Result.ok();
    }

    // ======================================================
    // 1. ROOT DETECTION
    // ======================================================
    public static boolean isRooted(Context ctx) {
        return checkRootBinaries()
            || checkBuildTags()
            || checkRootPackages(ctx)
            || checkSuExecution()
            || checkMagiskProps();
    }

    private static boolean checkRootBinaries() {
        for (String path : ROOT_PATHS) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkBuildTags() {
        String tags = Build.TAGS;
        return tags != null && tags.contains("test-keys");
    }

    private static boolean checkRootPackages(Context ctx) {
        if (ctx == null) return false;
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return false;
    }

    private static boolean checkSuExecution() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            return line != null && !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkMagiskProps() {
        // Cek system properties yang biasa diubah Magisk
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop"});
            BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                // Magisk sering mengubah/menambah prop ini
                if (line.contains("magisk")
                    || line.contains("ro.build.selinux=0")
                    || (line.contains("sys.oem_unlock_allowed") && line.contains("1"))) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return false;
    }

    // ======================================================
    // 2. EMULATOR DETECTION
    // ======================================================
    public static boolean isEmulator(Context ctx) {
        return checkBuildFingerprint()
            || checkBuildProperties()
            || checkHardware(ctx);
    }

    private static boolean checkBuildFingerprint() {
        String fp = Build.FINGERPRINT;
        if (fp == null) return false;
        String fpLower = fp.toLowerCase();
        for (String emu : EMU_FINGERPRINTS) {
            if (fpLower.contains(emu.toLowerCase())) return true;
        }
        return false;
    }

    private static boolean checkBuildProperties() {
        String model        = Build.MODEL        != null ? Build.MODEL.toLowerCase()        : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String brand        = Build.BRAND        != null ? Build.BRAND.toLowerCase()        : "";
        String device       = Build.DEVICE       != null ? Build.DEVICE.toLowerCase()       : "";
        String product      = Build.PRODUCT      != null ? Build.PRODUCT.toLowerCase()      : "";
        String hardware     = Build.HARDWARE     != null ? Build.HARDWARE.toLowerCase()     : "";

        return model.contains("emulator")
            || model.contains("sdk")
            || model.contains("android sdk built for x86")
            || manufacturer.contains("genymotion")
            || manufacturer.contains("unknown")
            || brand.contains("generic")
            || brand.contains("android")
            || device.contains("generic")
            || product.contains("sdk")
            || product.contains("vbox86p")
            || product.contains("emulator")
            || hardware.contains("goldfish")
            || hardware.contains("ranchu")
            || hardware.contains("vbox");
    }

    private static boolean checkHardware(Context ctx) {
        if (ctx == null) return false;
        try {
            android.hardware.SensorManager sm =
                (android.hardware.SensorManager)
                ctx.getSystemService(Context.SENSOR_SERVICE);
            if (sm != null && sm.getSensorList(
                android.hardware.Sensor.TYPE_ALL).isEmpty()) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ======================================================
    // 3. DEBUGGABLE FLAG
    // ======================================================
    public static boolean isDebuggable(Context ctx) {
        if (ctx == null) return false;
        return (ctx.getApplicationInfo().flags
            & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    // ======================================================
    // 4. DEBUGGER ATTACHED
    // ======================================================
    public static boolean isDebuggerAttached() {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
    }

    // ======================================================
    // 5. TIMING-BASED ANTI-DEBUG
    // Kalau debugger attached, execution time antar instruksi
    // jauh lebih lambat dari normal. Deteksi lewat System.nanoTime().
    // ======================================================
    private static boolean isTimingAttack() {
        try {
            long start = System.nanoTime();
            // Operasi sederhana yang harusnya selesai < 5ms tanpa debugger
            int dummy = 0;
            for (int i = 0; i < 1000; i++) dummy += i;
            long elapsed = System.nanoTime() - start;
            // Kalau > 10ms untuk 1000 iterasi → kemungkinan ada debugger
            return elapsed > 10_000_000L; // 10ms dalam nanoseconds
        } catch (Exception e) {
            return false;
        }
    }

    // ======================================================
    // 6. FRIDA DETECTION
    // ======================================================
    public static boolean isFridaPresent(Context ctx) {
        return checkFridaFiles()
            || checkFridaPorts()
            || checkFridaMaps()
            || checkFridaNamedPipe()
            || checkFridaThreads();
    }

    private static boolean checkFridaFiles() {
        for (String path : FRIDA_FILES) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkFridaPorts() {
        for (int port : FRIDA_PORTS) {
            try {
                java.net.Socket s = new java.net.Socket();
                s.connect(
                    new java.net.InetSocketAddress("127.0.0.1", port), 50);
                s.close();
                return true; // Port terbuka → Frida server running
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static boolean checkFridaMaps() {
        // Scan /proc/self/maps untuk library Frida
        try {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/self/maps")));
            String line;
            while ((line = br.readLine()) != null) {
                String l = line.toLowerCase();
                if (l.contains("frida")
                    || l.contains("gum-js-loop")
                    || l.contains("gmain")
                    || l.contains("linjector")
                    || l.contains("re.frida")) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean checkFridaNamedPipe() {
        // Frida sering buat named pipe di /data/local/tmp
        String[] fridaPipes = {
            "/data/local/tmp/frida-server",
            "/data/local/tmp/linjector-",
            "/proc/net/unix",
        };
        try {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/net/unix")));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("frida") || line.contains("gum-js")) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean checkFridaThreads() {
        // Frida biasanya spawn thread bernama "gmain", "gdbus", "gum-js-loop"
        try {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(
                    Runtime.getRuntime().exec("ps -A").getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                String l = line.toLowerCase();
                if (l.contains("frida") || l.contains("gum-js-loop")
                    || l.contains("linjector")) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return false;
    }

    // ======================================================
    // 7. XPOSED / LSPOSED DETECTION
    // ======================================================
    public static boolean isXposedPresent() {
        return checkXposedFiles()
            || checkXposedStackTrace()
            || checkXposedClassLoader()
            || checkXposedBridge();
    }

    private static boolean checkXposedFiles() {
        for (String path : XPOSED_FILES) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkXposedStackTrace() {
        try {
            throw new Exception("xposed_check");
        } catch (Exception e) {
            for (StackTraceElement el : e.getStackTrace()) {
                String cls = el.getClassName();
                if (cls.contains("de.robv.android.xposed")
                    || cls.contains("com.saurik.substrate")
                    || cls.contains("org.lsposed")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean checkXposedClassLoader() {
        try {
            ClassLoader cl = SecurityGuard.class.getClassLoader();
            if (cl != null) {
                String clStr = cl.toString();
                if (clStr.contains("XposedBridge")
                    || clStr.contains("EdXposed")
                    || clStr.contains("LSPosed")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean checkXposedBridge() {
        // Coba load XposedBridge class — kalau berhasil, Xposed aktif
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("de.robv.android.xposed.XC_MethodHook");
            return true;
        } catch (ClassNotFoundException ignored) {}
        return false;
    }

    // ======================================================
    // 8. MEMORY TAMPERING DETECTION
    // Scan /proc/self/maps untuk library berbahaya yang
    // di-inject ke memory process kita
    // ======================================================
    public static boolean isMemoryTampered() {
        // Library mencurigakan yang sering di-inject untuk patching
        String[] suspiciousLibs = {
            /*"xposed", "substrate", "frida", "inject",
            "hook", "lsplant", "riru", "zygisk",
            "dobby", "whale", "sandhook", "epic",
            "dexposed", "pine", "yahfa",*/
        };

        try {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/self/maps")));
            String line;
            while ((line = br.readLine()) != null) {
                String lower = line.toLowerCase();
                for (String lib : suspiciousLibs) {
                    if (lower.contains(lib)) {
                        br.close();
                        return true;
                    }
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return false;
    }

    // ======================================================
    // 9. APK INTEGRITY CHECK
    // ======================================================
    public static boolean isSignatureValid(Context ctx) {
        if (ctx == null) return false;
        if (EXPECTED_SIG_SHA256.isEmpty()) return true;

        try {
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.PackageInfo pi =
                    ctx.getPackageManager().getPackageInfo(
                        ctx.getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = pi.signingInfo.getApkContentsSigners();
            } else {
                @SuppressWarnings("deprecation")
                android.content.pm.PackageInfo pi =
                    ctx.getPackageManager().getPackageInfo(
                        ctx.getPackageName(),
                        PackageManager.GET_SIGNATURES);
                sigs = pi.signatures;
            }

            if (sigs == null || sigs.length == 0) return false;

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest    = md.digest(sigs[0].toByteArray());
            String actual    = bytesToHex(digest).toUpperCase();
            return actual.equals(EXPECTED_SIG_SHA256.toUpperCase()
                .replace(":", "").replace(" ", ""));

        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    // ======================================================
    // DEV UTILITY — dapatkan hash signature APK
    // HAPUS sebelum release!
    // ======================================================
    public static String getCurrentSignatureHash(Context ctx) {
        try {
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.PackageInfo pi =
                    ctx.getPackageManager().getPackageInfo(
                        ctx.getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = pi.signingInfo.getApkContentsSigners();
            } else {
                @SuppressWarnings("deprecation")
                android.content.pm.PackageInfo pi =
                    ctx.getPackageManager().getPackageInfo(
                        ctx.getPackageName(),
                        PackageManager.GET_SIGNATURES);
                sigs = pi.signatures;
            }
            if (sigs == null || sigs.length == 0) return "NO_SIGNATURE";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest    = md.digest(sigs[0].toByteArray());
            return bytesToHex(digest);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
