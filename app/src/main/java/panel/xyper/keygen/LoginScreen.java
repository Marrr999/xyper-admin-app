package panel.xyper.keygen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * LoginScreen — screen login Admin Panel
 * Flow:
 *   1. User input API Key
 *   2. GET /admin → verifikasi key + role
 *   3. Kalau pertama kali → register_device
 *   4. Simpan session → callback onLoginSuccess(role)
 */
public class LoginScreen {

    public interface OnLoginDone {
        void onSuccess(String role);
        void onFail(String reason);
    }

    private static final int CYAN   = Color.parseColor("#22E5FF");
    private static final int PURPLE = Color.parseColor("#A78BFA");
    private static final int RED    = Color.parseColor("#F87171");
    private static final int GREEN  = Color.parseColor("#34D399");

    // ===================== BUILD =====================
    public static View build(final Context ctx, final OnLoginDone callback) {

        // ── Root: FrameLayout biar AnimatedBG bisa di-stack ──
        FrameLayout root = new FrameLayout(ctx);

        // ✅ FIX: AnimatedBackgroundView sebagai background (bukan setBackgroundColor biasa)
        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        // Overlay gelap biar konten mudah dibaca
        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x88000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // ── Scroll ──
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(dp(ctx, 28), dp(ctx, 0), dp(ctx, 28), dp(ctx, 40));
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        container.setMinimumHeight(screenH);
        scroll.addView(container, new LinearLayout.LayoutParams(-1, -2));

        // Spacer atas
        View topSpacer = new View(ctx);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        container.addView(topSpacer);

        // ── LOGO / ICON ──
        FrameLayout logoWrap = new FrameLayout(ctx);
        LinearLayout.LayoutParams lwLp = new LinearLayout.LayoutParams(dp(ctx, 90), dp(ctx, 90));
        lwLp.gravity = Gravity.CENTER_HORIZONTAL;
        lwLp.bottomMargin = dp(ctx, 28);
        logoWrap.setLayoutParams(lwLp);

        // Glow ring behind icon
        View glowRing = new View(ctx);
        final GradientDrawable glowBg = new GradientDrawable();
        glowBg.setShape(GradientDrawable.OVAL);
        glowBg.setColor(Color.TRANSPARENT);
        glowBg.setStroke(dp(ctx, 3), CYAN);
        glowRing.setBackground(glowBg);
        glowRing.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        logoWrap.addView(glowRing);

        // Pulse animation on ring
        ValueAnimator pulse = ValueAnimator.ofFloat(0.5f, 1.0f);
        pulse.setDuration(1600);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                glowBg.setStroke(dp(ctx, 3), applyAlpha(CYAN, v));
            }
        });
        pulse.start();

        TextView logo = new TextView(ctx);
        logo.setText("🔐");
        logo.setTextSize(38f);
        logo.setGravity(Gravity.CENTER);
        logo.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        logo.setShadowLayer(30f, 0f, 0f, CYAN);
        logoWrap.addView(logo, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        container.addView(logoWrap);

        // ── TITLE ──
        TextView title = new TextView(ctx);
        title.setText("XYPER ADMIN");
        title.setTextSize(30f);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.2f);
        title.setShadowLayer(16f, 0f, 0f, CYAN);
        title.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.gravity = Gravity.CENTER_HORIZONTAL;
        titleLp.bottomMargin = dp(ctx, 6);
        container.addView(title, titleLp);

        // ── SUBTITLE ──
        TextView subtitle = new TextView(ctx);
        subtitle.setText("Enter your API Key to continue");
        subtitle.setTextSize(12f);
        subtitle.setTypeface(Typeface.MONOSPACE);
        subtitle.setTextColor(Color.parseColor("#475569"));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.gravity = Gravity.CENTER_HORIZONTAL;
        subLp.bottomMargin = dp(ctx, 40);
        container.addView(subtitle, subLp);

        // ── INPUT CARD ──
        LinearLayout inputCard = new LinearLayout(ctx);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        final GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#080F20"));
        cardBg.setCornerRadius(dp(ctx, 20));
        cardBg.setStroke(dp(ctx, 1), Color.parseColor("#1A3050"));
        inputCard.setBackground(cardBg);
        inputCard.setPadding(dp(ctx, 22), dp(ctx, 22), dp(ctx, 22), dp(ctx, 22));
        inputCard.setElevation(20f);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(ctx, 20);
        container.addView(inputCard, cardLp);

        // Breathing border on card
        ValueAnimator cardBreath = ValueAnimator.ofFloat(0.3f, 0.8f);
        cardBreath.setDuration(2400);
        cardBreath.setRepeatMode(ValueAnimator.REVERSE);
        cardBreath.setRepeatCount(ValueAnimator.INFINITE);
        cardBreath.setInterpolator(new AccelerateDecelerateInterpolator());
        cardBreath.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                cardBg.setStroke(dp(ctx, 1), applyAlpha(CYAN, (float) a.getAnimatedValue()));
            }
        });
        cardBreath.start();

        // Input label
        TextView inputLabel = new TextView(ctx);
        inputLabel.setText("🔑  API Key");
        inputLabel.setTextSize(11f);
        inputLabel.setTextColor(Color.parseColor("#22E5FF"));
        inputLabel.setTypeface(Typeface.DEFAULT_BOLD);
        inputLabel.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
        labelLp.bottomMargin = dp(ctx, 10);
        inputCard.addView(inputLabel, labelLp);

        // EditText neon-styled
        final EditText etApiKey = new EditText(ctx);
        etApiKey.setHint("Your-Api-Key-Here");
        etApiKey.setHintTextColor(Color.parseColor("#1E3A50"));
        etApiKey.setTextColor(Color.WHITE);
        etApiKey.setTextSize(14f);
        etApiKey.setTypeface(Typeface.MONOSPACE);
        etApiKey.setSingleLine(true);
        final GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#040A14"));
        inputBg.setCornerRadius(dp(ctx, 12));
        inputBg.setStroke(dp(ctx, 1), Color.parseColor("#22455A"));
        etApiKey.setBackground(inputBg);
        etApiKey.setPadding(dp(ctx, 16), dp(ctx, 13), dp(ctx, 16), dp(ctx, 13));
        inputCard.addView(etApiKey, new LinearLayout.LayoutParams(-1, dp(ctx, 50)));

        // Neon focus border effect on input
        etApiKey.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                inputBg.setColor(hasFocus
                    ? Color.parseColor("#06121E")
                    : Color.parseColor("#040A14"));
                inputBg.setStroke(
                    hasFocus ? dp(ctx, 2) : dp(ctx, 1),
                    hasFocus ? CYAN : Color.parseColor("#22455A")
                );
                // Also update card border color
                cardBg.setStroke(dp(ctx, 1),
                    hasFocus ? applyAlpha(CYAN, 0.9f) : applyAlpha(CYAN, 0.3f));
            }
        });

        // ── STATUS TEXT ──
        final TextView statusTv = new TextView(ctx);
        statusTv.setTextSize(12f);
        statusTv.setTypeface(Typeface.MONOSPACE);
        statusTv.setGravity(Gravity.CENTER);
        statusTv.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.bottomMargin = dp(ctx, 14);
        container.addView(statusTv, statusLp);

        // ── LOGIN BUTTON ──
        final TextView btnLogin = new TextView(ctx);
        btnLogin.setText("✦  CONNECT");
        btnLogin.setTextSize(15f);
        btnLogin.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnLogin.setTextColor(Color.parseColor("#001A2E"));
        btnLogin.setGravity(Gravity.CENTER);
        btnLogin.setLetterSpacing(0.12f);
        final GradientDrawable btnBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{CYAN, Color.parseColor("#0EA5E9")}
        );
        btnBg.setCornerRadius(dp(ctx, 16));
        btnLogin.setBackground(btnBg);
        btnLogin.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        btnLogin.setShadowLayer(20f, 0f, 0f, CYAN);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(ctx, 54));
        container.addView(btnLogin, btnLp);
        attachPressScale(btnLogin);

        // Glow pulse on button
        ValueAnimator btnGlow = ValueAnimator.ofFloat(0.5f, 1.0f);
        btnGlow.setDuration(1400);
        btnGlow.setRepeatMode(ValueAnimator.REVERSE);
        btnGlow.setRepeatCount(ValueAnimator.INFINITE);
        btnGlow.setInterpolator(new AccelerateDecelerateInterpolator());
        btnGlow.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                btnBg.setStroke((int)(dp(ctx, 1) * v * 2), applyAlpha(CYAN, v));
            }
        });
        btnGlow.start();

        // ── FOOTER ──
        TextView footer = new TextView(ctx);
        footer.setText("Xyper Key Manager  ·  Admin");
        footer.setTextSize(10f);
        footer.setTextColor(Color.parseColor("#1E3A50"));
        footer.setGravity(Gravity.CENTER);
        footer.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(ctx, 28);
        container.addView(footer, footerLp);

        // Spacer bawah
        View botSpacer = new View(ctx);
        botSpacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        container.addView(botSpacer);

        // ── Entrance animations ──
        logoWrap.setAlpha(0f);
        logoWrap.setScaleX(0.7f);
        logoWrap.setScaleY(0.7f);
        logoWrap.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(500).setInterpolator(new DecelerateInterpolator(1.5f)).start();

        title.setAlpha(0f);
        title.setTranslationY(dp(ctx, 20));
        title.animate().alpha(1f).translationY(0f)
            .setStartDelay(150).setDuration(400).setInterpolator(new DecelerateInterpolator()).start();

        subtitle.setAlpha(0f);
        subtitle.animate().alpha(1f)
            .setStartDelay(250).setDuration(400).start();

        inputCard.setAlpha(0f);
        inputCard.setTranslationY(dp(ctx, 30));
        inputCard.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        inputCard.animate().alpha(1f).translationY(0f)
            .setStartDelay(350).setDuration(450).setInterpolator(new DecelerateInterpolator())
            .withEndAction(new Runnable() {
                @Override public void run() {
                    inputCard.setLayerType(android.view.View.LAYER_TYPE_NONE, null);
                }
            }).start();

        btnLogin.setAlpha(0f);
        btnLogin.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        btnLogin.animate().alpha(1f)
            .setStartDelay(500).setDuration(350)
            .withEndAction(new Runnable() {
                @Override public void run() {
                    // Keep SOFTWARE for ongoing glow shadow
                    btnLogin.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
                }
            }).start();

        // ── CLICK HANDLER ──
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String apiKey = etApiKey.getText().toString().trim();
                if (apiKey.isEmpty()) {
                    showStatus(statusTv, "API Key cannot be empty", RED);
                    shakeView(inputCard);
                    return;
                }

                // Hide keyboard — adaptive API
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.view.WindowInsetsController wic = ((android.app.Activity) ctx)
                        .getWindow().getDecorView().getWindowInsetsController();
                    if (wic != null) wic.hide(android.view.WindowInsets.Type.ime());
                } else {
                    InputMethodManager imm = (InputMethodManager)
                        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(etApiKey.getWindowToken(), 0);
                }

                // Loading state
                btnLogin.setText("◈  Connecting...");
                btnLogin.setAlpha(0.65f);
                btnLogin.setClickable(false);
                showStatus(statusTv, "Verifying API Key...", CYAN);

                // Simpan sementara untuk auth headers
                ApiAuthHelper.saveSession(ctx, apiKey, "Unknown");

                // GET /admin → verifikasi key + role
                ApiClient.getAdminService().getAllKeys().enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (response.isSuccessful()) {
                            String role = response.headers().get("X-Role");
                            if (role == null) role = "Developer";
                            final String finalRole = role;
                            showStatus(statusTv, "Registering device...", CYAN);
                            registerDevice(ctx, apiKey, finalRole, statusTv, btnLogin, callback);
                        } else if (response.code() == 401) {
                            resetBtn(btnLogin);
                            String body = "";
                            try { body = response.errorBody().string(); } catch (Exception ignored) {}
                            showStatus(statusTv, "Authentication failed: " + body, RED);
                            ApiAuthHelper.clearSession(ctx);
                            shakeView(inputCard);
                        } else {
                            resetBtn(btnLogin);
                            showStatus(statusTv, "Server error — HTTP " + response.code(), RED);
                            ApiAuthHelper.clearSession(ctx);
                        }
                    }
                    @Override
                    public void onFailure(Call<String> call, Throwable t) {
                        resetBtn(btnLogin);
                        showStatus(statusTv, "Connection failed: " + t.getMessage(), RED);
                        ApiAuthHelper.clearSession(ctx);
                    }
                });
            }
        });

        return root;
    }

    // ===================== REGISTER DEVICE =====================
    private static void registerDevice(final Context ctx, final String apiKey,
            final String role, final TextView statusTv,
            final TextView btnLogin, final OnLoginDone callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", "register_device");
            payload.put("device_id", ApiAuthHelper.getDeviceId(ctx));

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        // 200 = berhasil register, 409 = sudah terdaftar (ok)
                        if (response.isSuccessful() || response.code() == 409) {
                            ApiAuthHelper.saveSession(ctx, apiKey, role);
                            showStatus(statusTv, "Authenticated as " + role, GREEN);
                            ActivityLog.log(ctx, "login", role);
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override public void run() { callback.onSuccess(role); }
                            }, 700);
                        } else {
                            resetBtn(btnLogin);
                            showStatus(statusTv, "Device registration failed", RED);
                            ApiAuthHelper.clearSession(ctx);
                        }
                    }
                    @Override
                    public void onFailure(Call<String> call, Throwable t) {
                        resetBtn(btnLogin);
                        showStatus(statusTv, "Connection failed: " + t.getMessage(), RED);
                        ApiAuthHelper.clearSession(ctx);
                    }
                });
        } catch (Exception e) {
            resetBtn(btnLogin);
            showStatus(statusTv, "An error occurred: " + e.getMessage(), RED);
        }
    }

    // ===================== HELPERS =====================
    private static void showStatus(final TextView tv, final String msg, final int color) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                tv.setText(msg);
                tv.setTextColor(color);
                tv.setVisibility(View.VISIBLE);
                // Fade in
                tv.setAlpha(0f);
                tv.animate().alpha(1f).setDuration(200).start();
            }
        });
    }

    private static void resetBtn(final TextView btn) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                btn.setText("✦  CONNECT");
                btn.setAlpha(1f);
                btn.setClickable(true);
            }
        });
    }

    // Shake animation untuk error feedback
    private static void shakeView(final View v) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                ValueAnimator shake = ValueAnimator.ofFloat(0f, 10f, -10f, 8f, -8f, 5f, -5f, 0f);
                shake.setDuration(400);
                shake.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
                        v.setTranslationX((float) a.getAnimatedValue());
                    }
                });
                shake.start();
            }
        });
    }

    private static void attachPressScale(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.96f).scaleY(0.96f)
                            .setDuration(80).setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
                        break;
                }
                return false;
            }
        });
    }

    private static int applyAlpha(int color, float alpha) {
        int a = Math.round(alpha * 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }
}
