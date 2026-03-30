import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// ── Load local.properties ─────────────────────────────────────
// File ini TIDAK masuk Git (.gitignore)
// Isi local.properties:
//   ks.storePassword=isipasswordkamu
//   ks.keyPassword=isipasswordkamu
//   hmac.secret=isihmacsecretsama dengan workers
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "panel.xyper.keygen"
    compileSdk = 36

    defaultConfig {
        applicationId = "panel.xyper.keygen"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── HMAC Secret — di-inject ke BuildConfig ────────────
        // Di Java: BuildConfig.HMAC_SECRET
        // Kosong string = HMAC disable
        buildConfigField(
            "String",
            "HMAC_SECRET",
            "\"${localProps.getProperty("hmac.secret", "")}\""
        )
    }

    // ── Signing Config ────────────────────────────────────────
    // Password diambil dari local.properties, TIDAK hardcode
    signingConfigs {
        create("release") {
            val ksApp  = file("release-key.jks")
            val ksRoot = file("../release-key.jks")

            storeFile     = if (ksApp.exists()) ksApp else ksRoot
            storePassword = localProps.getProperty("ks.storePassword", "")
            keyAlias      = "Xyper-Panel-Key"
            keyPassword   = localProps.getProperty("ks.keyPassword", "")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding  = true
        buildConfig  = true   // aktifkan BuildConfig untuk HMAC_SECRET
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ── AndroidX (via version catalog) ───────────────────────
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // ── Retrofit + Converters ─────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-scalars:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    // ── OkHttp 5.x ────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    // ── Gson ──────────────────────────────────────────────────
    implementation("com.google.code.gson:gson:2.13.2")

    // ── Lottie ────────────────────────────────────────────────
    implementation("com.airbnb.android:lottie:6.7.1")

    // ── Coroutines ────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // ── WorkManager + Core ────────────────────────────────────
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.core:core:1.18.0")
}
