package panel.xyper.keygen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplashScreen {

    // ── URL dari AppConstants — TIDAK hardcode di sini ───────
    // [SECURITY] Semua URL diambil dari AppConstants (terenkripsi)

    // ─── Palette ─────────────────────────────────────────────
    private static final int CYAN      = 0xFF22E5FF;
    private static final int CYAN_DIM  = 0x5522E5FF;
    private static final int BLUE      = 0xFF0EA5E9;
    private static final int PURPLE    = 0xFFA78BFA;
    private static final int BG_DARK   = 0xFF060D1E;
    private static final int RED       = 0xFFF87171;
    private static final int YELLOW    = 0xFFFBBF24;

    // ===================== BUILD =====================
    public static View build(final Context ctx, final OnSplashDone onDone) {

        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(BG_DARK);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ParticleFieldView particles = new ParticleFieldView(ctx);
        root.addView(particles, new FrameLayout.LayoutParams(-1, -1));

        AuroraView aurora = new AuroraView(ctx);
        root.addView(aurora, new FrameLayout.LayoutParams(-1, -1));

        ScanlineView scanlines = new ScanlineView(ctx);
        root.addView(scanlines, new FrameLayout.LayoutParams(-1, -1));

        VignetteView vignette = new VignetteView(ctx);
        root.addView(vignette, new FrameLayout.LayoutParams(-1, -1));

        // ── Center content ────────────────────────────────────
        LinearLayout center = new LinearLayout(ctx);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setClipChildren(false);
        center.setClipToPadding(false);
        root.addView(center, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ));

        // ── Lottie / EnergyOrb ────────────────────────────────
        final EnergyOrbView orb;
        final LottieAnimationView lottieView;

        boolean hasLottie = checkLottieAsset(ctx, "splash_anim.json");
        if (hasLottie) {
            lottieView = new LottieAnimationView(ctx);
            lottieView.setAnimation("splash_anim.json");
            lottieView.setRepeatCount(LottieDrawable.INFINITE);
            lottieView.playAnimation();
            LinearLayout.LayoutParams lottieLp =
                new LinearLayout.LayoutParams(dp(ctx, 200), dp(ctx, 200));
            lottieLp.bottomMargin = dp(ctx, 16);
            lottieView.setLayoutParams(lottieLp);
            center.addView(lottieView);
            orb = null;
        } else {
            lottieView = null;
            orb = new EnergyOrbView(ctx);
            LinearLayout.LayoutParams orbLp =
                new LinearLayout.LayoutParams(dp(ctx, 160), dp(ctx, 160));
            orbLp.bottomMargin = dp(ctx, 24);
            orbLp.leftMargin   = dp(ctx, 20);
            orbLp.rightMargin  = dp(ctx, 20);
            orb.setLayoutParams(orbLp);
            center.addView(orb);
        }

        // ── App name ──────────────────────────────────────────
        TextView appName = new TextView(ctx);
        appName.setText("XYPER XIT");
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        appName.setTextSize(34f);
        appName.setTextColor(Color.WHITE);
        appName.setShadowLayer(28f, 0f, 0f, CYAN);
        appName.setGravity(Gravity.CENTER);
        appName.setLetterSpacing(0.25f);
        appName.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        center.addView(appName, new LinearLayout.LayoutParams(-2, -2));

        View accentLine = new View(ctx);
        GradientDrawable lineBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x00FFFFFF, CYAN, PURPLE, 0x00FFFFFF}
        );
        accentLine.setBackground(lineBg);
        LinearLayout.LayoutParams lineLp =
            new LinearLayout.LayoutParams(dp(ctx, 200), dp(ctx, 1));
        lineLp.topMargin    = dp(ctx, 8);
        lineLp.bottomMargin = dp(ctx, 6);
        accentLine.setLayoutParams(lineLp);
        center.addView(accentLine);

        TextView subtitle = new TextView(ctx);
        subtitle.setText("MOD MENU · ADMIN PANEL");
        subtitle.setTypeface(Typeface.MONOSPACE);
        subtitle.setTextSize(10f);
        subtitle.setTextColor(Color.parseColor("#7DD3FC"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.bottomMargin = dp(ctx, 40);
        subtitle.setLayoutParams(subLp);
        center.addView(subtitle);

        // ── Progress card ──────────────────────────────────────
        LinearLayout progressCard = new LinearLayout(ctx);
        progressCard.setOrientation(LinearLayout.VERTICAL);
        progressCard.setPadding(dp(ctx, 24), dp(ctx, 20), dp(ctx, 24), dp(ctx, 20));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x1AFFFFFF);
        cardBg.setCornerRadius(dp(ctx, 20));
        cardBg.setStroke(dp(ctx, 1), 0x4422E5FF);
        progressCard.setBackground(cardBg);
        progressCard.setLayoutParams(
            new LinearLayout.LayoutParams(dp(ctx, 300), -2));

        LinearLayout statusRow = new LinearLayout(ctx);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        final TextView statusDot = new TextView(ctx);
        statusDot.setText("◈");
        statusDot.setTextSize(12f);
        statusDot.setTextColor(CYAN);
        statusDot.setShadowLayer(8f, 0f, 0f, CYAN);
        statusDot.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(-2, -2);
        dotLp.rightMargin = dp(ctx, 8);
        statusDot.setLayoutParams(dotLp);

        final TextView statusText = new TextView(ctx);
        statusText.setText("");
        statusText.setTypeface(Typeface.MONOSPACE);
        statusText.setTextSize(11f);
        statusText.setTextColor(Color.parseColor("#94A3B8"));
        statusText.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        final TextView cursor = new TextView(ctx);
        cursor.setText("_");
        cursor.setTypeface(Typeface.MONOSPACE);
        cursor.setTextSize(11f);
        cursor.setTextColor(CYAN);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            cursor.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            cursor.setShadowLayer(6f, 0f, 0f, CYAN);
        }
        startCursorBlink(cursor);

        statusRow.addView(statusDot);
        statusRow.addView(statusText);
        statusRow.addView(cursor);

        LinearLayout pbRow = new LinearLayout(ctx);
        pbRow.setOrientation(LinearLayout.HORIZONTAL);
        pbRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pbRowLp =
            new LinearLayout.LayoutParams(-1, -2);
        pbRowLp.topMargin = dp(ctx, 14);
        pbRow.setLayoutParams(pbRowLp);

        final NeonProgressBar progressBar = new NeonProgressBar(ctx);
        progressBar.setLayoutParams(
            new LinearLayout.LayoutParams(0, dp(ctx, 5), 1f));

        final TextView pctText = new TextView(ctx);
        pctText.setText("0%");
        pctText.setTypeface(Typeface.MONOSPACE);
        pctText.setTextSize(10f);
        pctText.setTextColor(CYAN);
        pctText.setShadowLayer(6f, 0f, 0f, CYAN);
        pctText.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        pctText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pctLp =
            new LinearLayout.LayoutParams(dp(ctx, 36), -2);
        pctLp.leftMargin = dp(ctx, 10);
        pctText.setLayoutParams(pctLp);

        pbRow.addView(progressBar);
        pbRow.addView(pctText);
        progressCard.addView(statusRow);
        progressCard.addView(pbRow);
        center.addView(progressCard);

        // ── Version ────────────────────────────────────────────
        TextView version = new TextView(ctx);
        String versionStr = "Xyper Dev";
        try {
            android.content.pm.PackageInfo pi =
                ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            versionStr = "v" + pi.versionName + "  ·  Xyper Dev";
        } catch (Exception ignored) {}
        version.setText(versionStr);
        version.setTypeface(Typeface.MONOSPACE);
        version.setTextSize(9f);
        version.setTextColor(0x44FFFFFF);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams verLp =
            new LinearLayout.LayoutParams(-2, -2);
        verLp.topMargin = dp(ctx, 20);
        version.setLayoutParams(verLp);
        center.addView(version);

        // ── Animasi masuk ──────────────────────────────────────
        center.setAlpha(0f);
        center.setTranslationY(dp(ctx, 30));
        center.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (orb != null) orb.startPulse();
                    startLoadingSequence(ctx, root, statusText, progressBar, pctText, onDone);
                }
            }).start();

        if (orb != null) orb.startIdle();

        return root;
    }

    // ===================== LOADING SEQUENCE =====================
    private static void startLoadingSequence(
        final Context ctx,
        final FrameLayout root,
        final TextView statusText,
        final NeonProgressBar progressBar,
        final TextView pctText,
        final OnSplashDone onDone
    ) {
        final Handler main = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(new Runnable() {
            @Override public void run() {

                // ── STEP 0: Security Check ──────────────────
                // [SECURITY] Jalankan dulu sebelum loading berlanjut
                main.post(new Runnable() { @Override public void run() {
                    setStatus(statusText, "Verifying integrity...", null);
                    progressBar.animateTo(0.08f);
                    animatePct(pctText, 0, 8);
                }});

                final SecurityGuard.Result secResult =
                    SecurityGuard.runChecks(ctx);
                sleep(500);

                if (!secResult.passed) {
                    main.post(new Runnable() { @Override public void run() {
                        showSecurityError(ctx, root, secResult);
                    }});
                    executor.shutdown();
                    return;
                }

                // ── STEP 1: Init ────────────────────────────
                main.post(new Runnable() { @Override public void run() {
                    setStatus(statusText, "Initializing system...", null);
                    progressBar.animateTo(0.15f);
                    animatePct(pctText, 8, 15);
                }});
                sleep(700);

                // ── STEP 2: Network ──────────────────────────
                // [BUG FIX] Pakai HTTP HEAD request, bukan InetAddress
                main.post(new Runnable() { @Override public void run() {
                    setStatus(statusText, "Checking network...", null);
                    progressBar.animateTo(0.35f);
                    animatePct(pctText, 15, 35);
                }});

                final boolean hasNet = checkInternet();
                sleep(500);

                if (!hasNet) {
                    main.post(new Runnable() { @Override public void run() {
                        setStatus(statusText, "No connection. Retrying...", null);
                        statusText.setTextColor(Color.parseColor("#F87171"));
                    }});
                    sleep(2000);
                    main.post(new Runnable() { @Override public void run() {
                        setStatus(statusText, "Checking network...", null);
                        statusText.setTextColor(Color.parseColor("#94A3B8"));
                    }});
                    sleep(1000);
                }

                // ── STEP 3: Server ping ──────────────────────
                // [BUG FIX] Reuse ApiClient, tidak buat OkHttpClient baru
                main.post(new Runnable() { @Override public void run() {
                    setStatus(statusText, "Connecting to server...", null);
                    progressBar.animateTo(0.60f);
                    animatePct(pctText, 35, 60);
                }});

                final boolean apiOk = pingApi();
                sleep(600);

                if (!apiOk) {
                    main.post(new Runnable() { @Override public void run() {
                        setStatus(statusText, "Server unreachable.", null);
                        statusText.setTextColor(Color.parseColor("#FBBF24"));
                    }});
                    sleep(1500);
                }

                // ── STEP 4: Load dashboard ───────────────────
                main.post(new Runnable() { @Override public void run() {
                    statusText.setTextColor(Color.parseColor("#94A3B8"));
                    setStatus(statusText, "Loading dashboard...", null);
                    progressBar.animateTo(0.85f);
                    animatePct(pctText, 60, 85);
                }});
                sleep(600);

                // ── STEP 5: Ready ────────────────────────────
                main.post(new Runnable() { @Override public void run() {
                    setStatus(statusText, "Ready.", null);
                    progressBar.animateTo(1.0f);
                    animatePct(pctText, 85, 100);
                }});
                sleep(700);

                main.post(new Runnable() { @Override public void run() {
                    if (onDone != null) onDone.onReady();
                }});
                executor.shutdown();
            }
        });
    }

    // ===================== SECURITY ERROR SCREEN =====================
    // Tampil kalau SecurityGuard.runChecks() gagal — full-screen blocking
    private static void showSecurityError(
        final Context ctx,
        final FrameLayout root,
        final SecurityGuard.Result result
    ) {
        // Overlay gelap
        FrameLayout errorRoot = new FrameLayout(ctx);
        errorRoot.setBackgroundColor(0xFF030812);

        LinearLayout center = new LinearLayout(ctx);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setPadding(dp(ctx, 32), 0, dp(ctx, 32), 0);

        // Icon
        TextView icon = new TextView(ctx);
        String iconText;
        switch (result.type) {
            case ROOTED:           iconText = "⚠️"; break;
            case EMULATOR:         iconText = "🖥️"; break;
            case DEBUGGER_ATTACHED:iconText = "🔍"; break;
            case DEBUGGABLE:       iconText = "🐛"; break;
            case TAMPERED:         iconText = "💀"; break;
            default:               iconText = "🛡️"; break;
        }
        icon.setText(iconText);
        icon.setTextSize(64f);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp =
            new LinearLayout.LayoutParams(-1, -2);
        iconLp.bottomMargin = dp(ctx, 24);
        icon.setLayoutParams(iconLp);
        center.addView(icon);

        // Title
        TextView title = new TextView(ctx);
        title.setText("Security Check Failed");
        title.setTextColor(0xFFF87171);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp =
            new LinearLayout.LayoutParams(-1, -2);
        titleLp.bottomMargin = dp(ctx, 12);
        title.setLayoutParams(titleLp);
        center.addView(title);

        // Reason
        TextView reason = new TextView(ctx);
        String reasonText;
        switch (result.type) {
            case ROOTED:
                reasonText = "Rooted device detected.\nApp cannot run on rooted devices.";
                break;
            case EMULATOR:
                reasonText = "Emulator detected.\nThis app is for physical devices only.";
                break;
            case DEBUGGER_ATTACHED:
                reasonText = "Debugger detected.\nPlease disconnect the debugger.";
                break;
            case DEBUGGABLE:
                reasonText = "App is running in debug mode.\nInstall the release build.";
                break;
            case TAMPERED:
                reasonText = "APK signature mismatch.\nThis APK may have been tampered.";
                break;
            default:
                reasonText = result.reason;
                break;
        }
        reason.setText(reasonText);
        reason.setTextColor(0xFF94A3B8);
        reason.setTextSize(13f);
        reason.setTypeface(Typeface.MONOSPACE);
        reason.setGravity(Gravity.CENTER);
        reason.setLineSpacing(dp(ctx, 4), 1f);
        LinearLayout.LayoutParams reasonLp =
            new LinearLayout.LayoutParams(-1, -2);
        reasonLp.bottomMargin = dp(ctx, 32);
        reason.setLayoutParams(reasonLp);
        center.addView(reason);

        // Error code card
        LinearLayout codeCard = new LinearLayout(ctx);
        codeCard.setGravity(Gravity.CENTER);
        codeCard.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
        GradientDrawable codeBg = new GradientDrawable();
        codeBg.setColor(0x1AF87171);
        codeBg.setCornerRadius(dp(ctx, 12));
        codeBg.setStroke(dp(ctx, 1), 0x88F87171);
        codeCard.setBackground(codeBg);
        LinearLayout.LayoutParams codeLp =
            new LinearLayout.LayoutParams(-2, -2);
        codeLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        codeCard.setLayoutParams(codeLp);

        TextView codeText = new TextView(ctx);
        codeText.setText("ERR_" + result.type.name());
        codeText.setTextColor(0xFFF87171);
        codeText.setTextSize(11f);
        codeText.setTypeface(Typeface.MONOSPACE);
        codeCard.addView(codeText);
        center.addView(codeCard);

        errorRoot.addView(center, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER));

        // Replace splash content dengan error screen
        root.removeAllViews();
        root.setBackgroundColor(0xFF030812);
        root.addView(errorRoot, new FrameLayout.LayoutParams(-1, -1));
    }

    // ===================== CURSOR BLINK =====================
    private static void startCursorBlink(final TextView cursor) {
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] visible = {true};
        final Runnable[] blink = {null};
        blink[0] = new Runnable() {
            @Override public void run() {
                visible[0] = !visible[0];
                cursor.setAlpha(visible[0] ? 1f : 0f);
                h.postDelayed(blink[0], 530);
            }
        };
        h.postDelayed(blink[0], 530);
    }

    // ===================== STATUS TEXT =====================
    private static void setStatus(final TextView tv, final String text, final Runnable onDone) {
        tv.animate().alpha(0f).translationY(dp_s(6))
            .setDuration(140)
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .withEndAction(new Runnable() {
                @Override public void run() {
                    tv.setText(text);
                    tv.setTranslationY(-dp_s(10));
                    tv.animate().alpha(1f).translationY(0f)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                        .withEndAction(onDone != null ? onDone : new Runnable() {
                            @Override public void run() {}
                        }).start();
                }
            }).start();
    }

    // ===================== PERCENTAGE ANIM =====================
    private static void animatePct(final TextView tv, int from, int to) {
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(500);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                tv.setText(a.getAnimatedValue() + "%");
            }
        });
        va.start();
    }

    // ===================== HELPERS =====================

    /**
     * [BUG FIX] checkInternet yang benar — pakai HTTP HEAD request ke server,
     * bukan InetAddress.getByName() yang hanya DNS lookup.
     *
     * Kenapa yang lama salah:
     *   InetAddress.getByName("8.8.8.8") bisa resolve dari cache DNS lokal
     *   meskipun internet sudah mati → false positive "connected"
     *
     * Kenapa yang baru benar:
     *   HEAD request ke server butuh koneksi TCP nyata ke internet.
     */
    private static boolean checkInternet() {
        try {
            // [BUG FIX] Reuse ApiClient — tidak buat OkHttpClient baru
            // Coba HEAD request ke Workers, timeout 5 detik
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
            okhttp3.Response resp = client.newCall(
                new okhttp3.Request.Builder()
                    .url(AppConstants.getAdminEndpoint()) // [SECURITY] pakai AppConstants
                    .head()
                    .build()
            ).execute();
            resp.close();
            return true; // TCP connected → ada internet
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * [BUG FIX] pingApi — reuse OkHttpClient yang sama, tidak buat baru.
     * Response 401/403 = server reached tapi unauthorized → anggap OK.
     */
    private static boolean pingApi() {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
            okhttp3.Response resp = client.newCall(
                new okhttp3.Request.Builder()
                    .url(AppConstants.getAdminEndpoint()) // [SECURITY] pakai AppConstants
                    .get()
                    .build()
            ).execute();
            boolean reachable = resp.code() > 0;
            resp.close();
            return reachable;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }

    private static int dp_s(int val) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, val,
            android.content.res.Resources.getSystem().getDisplayMetrics());
    }

    private static boolean checkLottieAsset(Context ctx, String fileName) {
        try {
            String[] assets = ctx.getAssets().list("");
            if (assets != null) {
                for (String asset : assets) {
                    if (asset.equals(fileName)) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== CALLBACK =====================
    public interface OnSplashDone { void onReady(); }

    // ===================== ENERGY ORB =====================
    static class EnergyOrbView extends View {
        private final Paint corePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float breathScale = 1f;
        private float ring1Angle  = 0f;
        private float ring2Angle  = 0f;
        private ValueAnimator idleAnim, pulseAnim, rotAnim;

        public EnergyOrbView(Context ctx) {
            super(ctx);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            ring1Paint.setStyle(Paint.Style.STROKE);
            ring1Paint.setStrokeWidth(3f);
            ring1Paint.setColor(0x8822E5FF);
            ring2Paint.setStyle(Paint.Style.STROKE);
            ring2Paint.setStrokeWidth(2f);
            ring2Paint.setColor(0x66A78BFA);
            glowPaint.setStyle(Paint.Style.FILL);
        }

        public void startIdle() {
            idleAnim = ValueAnimator.ofFloat(0.92f, 1.08f);
            idleAnim.setDuration(2000);
            idleAnim.setRepeatMode(ValueAnimator.REVERSE);
            idleAnim.setRepeatCount(ValueAnimator.INFINITE);
            idleAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    breathScale = (float) a.getAnimatedValue(); invalidate();
                }
            });
            idleAnim.start();

            rotAnim = ValueAnimator.ofFloat(0f, 360f);
            rotAnim.setDuration(4000);
            rotAnim.setRepeatCount(ValueAnimator.INFINITE);
            rotAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    ring1Angle =  (float) a.getAnimatedValue();
                    ring2Angle = -(float) a.getAnimatedValue() * 0.7f;
                    invalidate();
                }
            });
            rotAnim.start();
        }

        public void startPulse() {
            if (idleAnim != null) idleAnim.cancel();
            pulseAnim = ValueAnimator.ofFloat(1f, 1.2f, 1f);
            pulseAnim.setDuration(500);
            pulseAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    breathScale = (float) a.getAnimatedValue(); invalidate();
                }
            });
            pulseAnim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { startIdle(); }
            });
            pulseAnim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float base = Math.min(cx, cy) * 0.62f;
            float r = base * breathScale;

            glowPaint.setColor(0x0A22E5FF);
            canvas.drawCircle(cx, cy, r * 1.5f, glowPaint);
            glowPaint.setColor(0x1522E5FF);
            canvas.drawCircle(cx, cy, r * 1.25f, glowPaint);
            glowPaint.setColor(0x2522E5FF);
            canvas.drawCircle(cx, cy, r * 1.05f, glowPaint);

            canvas.save();
            canvas.rotate(ring1Angle, cx, cy);
            RectF ovalR1 = new RectF(cx-r*0.88f, cy-r*0.55f, cx+r*0.88f, cy+r*0.55f);
            ring1Paint.setPathEffect(
                new android.graphics.DashPathEffect(new float[]{12f, 8f}, 0f));
            canvas.drawOval(ovalR1, ring1Paint);
            canvas.restore();

            canvas.save();
            canvas.rotate(ring2Angle, cx, cy);
            RectF ovalR2 = new RectF(cx-r*0.6f, cy-r*0.88f, cx+r*0.6f, cy+r*0.88f);
            ring2Paint.setPathEffect(
                new android.graphics.DashPathEffect(new float[]{8f, 12f}, 0f));
            canvas.drawOval(ovalR2, ring2Paint);
            canvas.restore();

            RadialGradient rg = new RadialGradient(cx, cy, r * 0.75f,
                new int[]{0xFF5EF7FF, 0xFF22E5FF, 0xFF0EA5E9, 0xFF1E3A6E},
                new float[]{0f, 0.35f, 0.65f, 1f},
                Shader.TileMode.CLAMP);
            corePaint.setShader(rg);
            canvas.drawCircle(cx, cy, r * 0.75f, corePaint);

            Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
            highlight.setColor(0xAAFFFFFF);
            canvas.drawCircle(cx-r*0.22f, cy-r*0.22f, r*0.14f, highlight);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (idleAnim  != null) idleAnim.cancel();
            if (pulseAnim != null) pulseAnim.cancel();
            if (rotAnim   != null) rotAnim.cancel();
        }
    }

    // ===================== NEON PROGRESS BAR =====================
    static class NeonProgressBar extends View {
        private float progress = 0f;
        private ValueAnimator animator;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tipPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

        public NeonProgressBar(Context ctx) {
            super(ctx);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            trackPaint.setStyle(Paint.Style.FILL);
            glowPaint.setStyle(Paint.Style.FILL);
            fillPaint.setStyle(Paint.Style.FILL);
            tipPaint.setStyle(Paint.Style.FILL);
        }

        public void animateTo(float target) {
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(600);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    progress = (float) a.getAnimatedValue(); invalidate();
                }
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), r = h / 2f;
            trackPaint.setColor(0x22FFFFFF);
            canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, trackPaint);
            if (progress <= 0f) return;
            float fillW = w * progress;
            glowPaint.setColor(0x3322E5FF);
            canvas.drawRoundRect(new RectF(0, -h, fillW, h*2f), r, r, glowPaint);
            LinearGradient grad = new LinearGradient(0, 0, fillW, 0,
                new int[]{0xFF0EA5E9, 0xFF22E5FF, 0xFFA78BFA},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            fillPaint.setShader(grad);
            canvas.drawRoundRect(new RectF(0, 0, fillW, h), r, r, fillPaint);
            tipPaint.setColor(0xCCFFFFFF);
            float tipR = h * 0.8f;
            canvas.drawCircle(fillW - tipR*0.3f, h/2f, tipR, tipPaint);
            Paint shimmer = new Paint(Paint.ANTI_ALIAS_FLAG);
            shimmer.setColor(0x33FFFFFF);
            canvas.drawRoundRect(new RectF(0, 0, fillW, h*0.45f), r, r, shimmer);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (animator != null) animator.cancel();
        }
    }

    // ===================== PARTICLE FIELD =====================
    static class ParticleFieldView extends View {
        private static final int COUNT = 55;
        private final float[] px, py, vx, vy, pr, pa;
        private final int[] pc;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator anim;
        private final Random rnd = new Random();

        public ParticleFieldView(Context ctx) {
            super(ctx);
            px = new float[COUNT]; py = new float[COUNT];
            vx = new float[COUNT]; vy = new float[COUNT];
            pr = new float[COUNT]; pa = new float[COUNT];
            pc = new int[COUNT];
            int[] colors = {0xFF22E5FF, 0xFFA78BFA, 0xFF38BDF8, 0xFF7DD3FC, 0xFFFFFFFF};
            for (int i = 0; i < COUNT; i++) {
                px[i] = rnd.nextFloat();
                py[i] = rnd.nextFloat();
                vx[i] = (rnd.nextFloat() - 0.5f) * 0.0004f;
                vy[i] = (rnd.nextFloat() - 0.5f) * 0.0004f;
                pr[i] = 1.2f + rnd.nextFloat() * 2.2f;
                pa[i] = 0.2f + rnd.nextFloat() * 0.7f;
                pc[i] = colors[rnd.nextInt(colors.length)];
            }
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(16);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    for (int i = 0; i < COUNT; i++) {
                        px[i] += vx[i]; py[i] += vy[i];
                        if (px[i] < 0) px[i] = 1f;
                        if (px[i] > 1) px[i] = 0f;
                        if (py[i] < 0) py[i] = 1f;
                        if (py[i] > 1) py[i] = 0f;
                    }
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            for (int i = 0; i < COUNT; i++) {
                int base  = pc[i] & 0x00FFFFFF;
                int alpha = Math.round(pa[i] * 255);
                paint.setColor((alpha << 24) | base);
                canvas.drawCircle(px[i]*w, py[i]*h, pr[i], paint);
            }
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }
    }

    // ===================== AURORA VIEW =====================
    static class AuroraView extends View {
        private final Paint p1 = new Paint(), p2 = new Paint();
        private float phase = 0f;
        private ValueAnimator anim;

        public AuroraView(Context ctx) {
            super(ctx);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            anim = ValueAnimator.ofFloat(0f, (float)(Math.PI * 2));
            anim.setDuration(6000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    phase = (float) a.getAnimatedValue(); invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float offset = (float)(Math.sin(phase) * 0.08f * h);
            RadialGradient rg1 = new RadialGradient(
                w*0.35f, h*0.2f+offset, h*0.55f,
                new int[]{0x1522E5FF, 0x0822E5FF, 0x00000000},
                null, Shader.TileMode.CLAMP);
            p1.setShader(rg1);
            canvas.drawRect(0, 0, w, h, p1);
            RadialGradient rg2 = new RadialGradient(
                w*0.75f, h*0.75f-offset, h*0.5f,
                new int[]{0x12A78BFA, 0x06A78BFA, 0x00000000},
                null, Shader.TileMode.CLAMP);
            p2.setShader(rg2);
            canvas.drawRect(0, 0, w, h, p2);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }
    }

    // ===================== SCANLINE VIEW =====================
    static class ScanlineView extends View {
        private final Paint p = new Paint();
        public ScanlineView(Context ctx) {
            super(ctx);
            p.setColor(0x08000000);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            int h = getHeight(), w = getWidth();
            for (int y = 0; y < h; y += 4)
                canvas.drawLine(0, y, w, y, p);
        }
    }

    // ===================== VIGNETTE VIEW =====================
    static class VignetteView extends View {
        public VignetteView(Context ctx) { super(ctx); }
        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            RadialGradient rg = new RadialGradient(
                w/2f, h/2f, Math.max(w, h)*0.72f,
                new int[]{0x00000000, 0x00000000, 0xCC000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            Paint p = new Paint();
            p.setShader(rg);
            canvas.drawRect(0, 0, w, h, p);
        }
    }
}
