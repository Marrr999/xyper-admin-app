# ==============================================================
# XYPER ADMIN PANEL — ProGuard / R8 Rules (Aggressive)
# ==============================================================

# ── Entry points wajib di-keep ────────────────────────────────
-keep class panel.xyper.keygen.MainActivity { *; }
-keep class panel.xyper.keygen.GlobalApplication { *; }
-keep class panel.xyper.keygen.GlobalApplication$CrashActivity { *; }
-keep class panel.xyper.keygen.ExpiryNotificationWorker { *; }
-keep class panel.xyper.keygen.SecurityGuard { *; }
-keep class panel.xyper.keygen.SecurityGuard$Result { *; }
-keep class panel.xyper.keygen.SecurityGuard$Type { *; }
-keep class panel.xyper.keygen.AppConstants { *; }

# ── Retrofit ──────────────────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Interface API wajib di-keep agar Retrofit bisa proxy-nya
-keep interface panel.xyper.keygen.AdminApiService { *; }

# ── OkHttp ────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ── Gson / JSON ───────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── WorkManager ───────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Lottie ────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── AndroidX Notification ─────────────────────────────────────
-keep class androidx.core.app.NotificationCompat { *; }
-keep class androidx.core.app.NotificationCompat$Builder { *; }
-keep class androidx.core.app.NotificationCompat$BigTextStyle { *; }

# ── Hapus semua logging di release ────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}

# ── Hapus printStackTrace di release ──────────────────────────
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# ── Aggressive Obfuscation ────────────────────────────────────
-optimizationpasses 5
-dontusemixedcaseclassnames
-repackageclasses 'x'
-allowaccessmodification
-overloadaggressively

# ── Source file info ──────────────────────────────────────────
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ── Optimisasi tambahan ───────────────────────────────────────
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# ── Suppress common warnings ──────────────────────────────────
-dontwarn java.lang.invoke.**
-dontwarn **$$Lambda$*
-dontwarn kotlin.**
