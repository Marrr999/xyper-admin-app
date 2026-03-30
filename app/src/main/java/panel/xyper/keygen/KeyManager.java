package panel.xyper.keygen;

import android.animation.ValueAnimator;
import android.app.Dialog;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Response;

public class KeyManager {

    private static Context ctxGlobal;

    public interface Callback {
        void onKeyAdded();
    }

    // ===================== BUILD =====================
    public static View build(final Context ctx, final Callback cb) {
        ctxGlobal = ctx;

        // ROOT
        FrameLayout root = new FrameLayout(ctx);

        // Animated BG
        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        // Overlay
        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x55000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // ScrollView biar ga kepotong di layar kecil
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout scrollContent = new LinearLayout(ctx);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setGravity(Gravity.CENTER_VERTICAL);
        scrollContent.setPadding(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 100));
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        scrollContent.setMinimumHeight(screenH);
        scroll.addView(scrollContent, new LinearLayout.LayoutParams(-1, -2));

        // ── GLASS CARD ──
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 24));

        GradientDrawable cardBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0x33FFFFFF, 0x15FFFFFF, 0x0AFFFFFF}
        );
        float r = dp(ctx, 24);
        cardBg.setCornerRadii(new float[]{r,r,r,r,r,r,r,r});
        cardBg.setStroke(dp(ctx, 1), 0x8822E5FF);
        card.setBackground(cardBg);
        card.setElevation(40f);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(cardLp);

        // Breathing border animation
        final GradientDrawable animCardBg = cardBg;
        ValueAnimator borderBreath = ValueAnimator.ofFloat(0.4f, 1.0f);
        borderBreath.setDuration(2200);
        borderBreath.setRepeatMode(ValueAnimator.REVERSE);
        borderBreath.setRepeatCount(ValueAnimator.INFINITE);
        borderBreath.setInterpolator(new AccelerateDecelerateInterpolator());
        borderBreath.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                float v = (float) anim.getAnimatedValue();
                animCardBg.setStroke(dp(ctx, 1), applyAlpha(0xFF22E5FF, v));
            }
        });
        borderBreath.start();

        // ── HEADER ──
        card.addView(buildHeader(ctx));
        card.addView(buildDivider(ctx));

        // ── SECTION: KEY INPUT ──
        card.addView(buildSectionLabel(ctx, "🔑  License Key"));

        final EditText keyInput = new EditText(ctx);
        keyInput.setHint("Enter key name...");
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(0x6622E5FF);
        keyInput.setTypeface(Typeface.MONOSPACE);
        keyInput.setGravity(Gravity.CENTER);
        keyInput.setTextSize(14f);
        styleEditText(ctx, keyInput);
        attachFocusBorder(ctx, keyInput);
        LinearLayout.LayoutParams kiLp = new LinearLayout.LayoutParams(-1, dp(ctx, 48));
        kiLp.bottomMargin = dp(ctx, 14);
        keyInput.setLayoutParams(kiLp);
        // Generate button row
        LinearLayout keyRow = new LinearLayout(ctx);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams krLp = new LinearLayout.LayoutParams(-1, -2);
        krLp.bottomMargin = dp(ctx, 14);
        keyRow.setLayoutParams(krLp);
        LinearLayout.LayoutParams kiLp2 = new LinearLayout.LayoutParams(0, dp(ctx, 48), 1f);
        kiLp2.rightMargin = dp(ctx, 8);
        keyInput.setLayoutParams(kiLp2);
        keyRow.addView(keyInput);

        TextView btnGen = new TextView(ctx);
        btnGen.setText("🎲");
        btnGen.setTextSize(20f);
        btnGen.setGravity(Gravity.CENTER);
        btnGen.setPadding(dp(ctx,2), 0, dp(ctx,2), 0);
        GradientDrawable genBg = new GradientDrawable();
        genBg.setColor(0x1522E5FF);
        genBg.setCornerRadius(dp(ctx, 12));
        genBg.setStroke(dp(ctx, 1), Color.parseColor("#22455A"));
        btnGen.setBackground(genBg);
        btnGen.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)));
        attachPressScale(btnGen);
        btnGen.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                keyInput.setText(generateKeyName());
                keyInput.setSelection(keyInput.getText().length());
            }
        });
        keyRow.addView(btnGen);
        card.addView(keyRow);

        // ── SECTION: ROLE ──
        card.addView(buildSectionLabel(ctx, "👤  Role"));

        final boolean isDev = ApiAuthHelper.isDeveloper(ctx);

        // ✅ FIX B1: "User" → "Client" sesuai workers.js
        final String[] roleOptions = isDev
            ? new String[]{"Developer", "Reseller", "Client"}
            : new String[]{"Client"};
        final String[] selectedRole = {roleOptions[roleOptions.length - 1]};
        final int[] selectedExpireIdx = {0};

        // ✅ FIX B2: selectedType dideklarasi di SINI (sebelum expire listener)
        // sehingga bisa diupdate dari dalam BottomSheet callback
        final String[] selectedType = new String[]{"1 Day"};

        final TextView rolePickerView = new TextView(ctx);
        rolePickerView.setText(selectedRole[0]);
        rolePickerView.setTextColor(Color.WHITE);
        rolePickerView.setTextSize(13f);
        rolePickerView.setTypeface(Typeface.MONOSPACE);
        rolePickerView.setGravity(Gravity.CENTER_VERTICAL);
        rolePickerView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout roleRow = new LinearLayout(ctx);
        roleRow.setOrientation(LinearLayout.HORIZONTAL);
        roleRow.setGravity(Gravity.CENTER_VERTICAL);
        roleRow.setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0);
        final GradientDrawable roleBg = new GradientDrawable();
        roleBg.setColor(Color.parseColor("#0D1B2A"));
        roleBg.setCornerRadius(dp(ctx, 14));
        roleBg.setStroke(dp(ctx, 1), Color.parseColor("#22455A"));
        roleRow.setBackground(roleBg);
        LinearLayout.LayoutParams rRowLp = new LinearLayout.LayoutParams(-1, dp(ctx, 48));
        rRowLp.bottomMargin = dp(ctx, 14);
        roleRow.setLayoutParams(rRowLp);

        TextView roleArrow = new TextView(ctx);
        roleArrow.setText("▾");
        roleArrow.setTextColor(Color.parseColor("#22E5FF"));
        roleArrow.setTextSize(14f);
        roleRow.addView(rolePickerView);
        roleRow.addView(roleArrow);

        attachPressScale(roleRow);
        roleRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showBottomSheet(ctx, "Select Role", roleOptions, selectedRole[0],
                    new BottomSheetCallback() {
                        @Override public void onSelected(String val, int idx) {
                            selectedRole[0] = val;
                            rolePickerView.setText(val);
                            // Warna border & text sesuai role
                            int roleColor = val.equals("Developer")
                                ? Color.parseColor("#A78BFA")
                                : val.equals("Reseller")
                                    ? Color.parseColor("#22E5FF")
                                    : Color.parseColor("#34D399");
                            roleBg.setStroke(dp(ctx, 1), roleColor);
                            rolePickerView.setTextColor(roleColor);
                        }
                    });
            }
        });
        card.addView(roleRow);

        // ── SECTION: DURASI ──
        card.addView(buildSectionLabel(ctx, "⏱  Duration"));

        final LinearLayout expireContainer = new LinearLayout(ctx);
        expireContainer.setOrientation(LinearLayout.HORIZONTAL);
        expireContainer.setGravity(Gravity.CENTER_VERTICAL);
        expireContainer.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
        final GradientDrawable exBg = new GradientDrawable();
        exBg.setColor(0x1AFFFFFF);
        exBg.setCornerRadius(dp(ctx, 14));
        exBg.setStroke(dp(ctx, 1), 0x5522E5FF);
        expireContainer.setBackground(exBg);
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(-1, dp(ctx, 48));
        exLp.bottomMargin = dp(ctx, 6);
        expireContainer.setLayoutParams(exLp);

        final TextView expireLabel = new TextView(ctx);
        expireLabel.setText("1 Day");
        expireLabel.setTextColor(Color.WHITE);
        expireLabel.setTextSize(13f);
        expireLabel.setTypeface(Typeface.MONOSPACE);
        expireLabel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView expireArrow = new TextView(ctx);
        expireArrow.setText("▾");
        expireArrow.setTextColor(Color.parseColor("#22E5FF"));
        expireArrow.setTextSize(14f);

        expireContainer.addView(expireLabel);
        expireContainer.addView(expireArrow);

        final String[] expireOptions = {"1 Day","3 Day","7 Day","14 Day","30 Day","Permanent","Custom"};

        // Custom days input — dideklarasi DULU biar bisa dipakai di BottomSheet callback
        final EditText customDaysInput = new EditText(ctx);
        customDaysInput.setHint("Number of days (e.g. 90)");
        customDaysInput.setTextColor(Color.WHITE);
        customDaysInput.setHintTextColor(0x6622E5FF);
        customDaysInput.setGravity(Gravity.CENTER);
        customDaysInput.setTextSize(13f);
        customDaysInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        styleEditText(ctx, customDaysInput);
        attachFocusBorder(ctx, customDaysInput);
        LinearLayout.LayoutParams cdLp = new LinearLayout.LayoutParams(-1, dp(ctx, 44));
        cdLp.topMargin = dp(ctx, 6);
        cdLp.bottomMargin = dp(ctx, 14);
        customDaysInput.setLayoutParams(cdLp);
        customDaysInput.setVisibility(View.GONE);

        attachPressScale(expireContainer);
        expireContainer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showBottomSheet(ctx, "Select Duration", expireOptions, expireOptions[selectedExpireIdx[0]],
                    new BottomSheetCallback() {
                        @Override public void onSelected(String val, int idx) {
                            selectedExpireIdx[0] = idx;
                            expireLabel.setText(val);
                            customDaysInput.setVisibility(idx == 6 ? View.VISIBLE : View.GONE);

                            // ✅ FIX B2: selectedType selalu sync dengan pilihan durasi user
                            selectedType[0] = val;

                            // Visual: border ungu kalau Permanent, cyan kalau lainnya
                            exBg.setStroke(dp(ctx, 1),
                                idx == 5
                                    ? Color.parseColor("#A78BFA")
                                    : 0x5522E5FF);
                        }
                    });
            }
        });
        card.addView(expireContainer);
        card.addView(customDaysInput);

        // ── SECTION: DEVICE LIMIT ──
        card.addView(buildSectionLabel(ctx, "📱  Device Limit"));

        LinearLayout limitRow = new LinearLayout(ctx);
        limitRow.setOrientation(LinearLayout.HORIZONTAL);
        limitRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable limitBg = new GradientDrawable();
        limitBg.setColor(0x1AFFFFFF);
        limitBg.setCornerRadius(dp(ctx, 14));
        limitBg.setStroke(dp(ctx, 1), 0x5522E5FF);
        limitRow.setBackground(limitBg);
        LinearLayout.LayoutParams lrLp = new LinearLayout.LayoutParams(-1, dp(ctx, 50));
        lrLp.bottomMargin = dp(ctx, 20);
        limitRow.setLayoutParams(lrLp);

        final TextView btnMinus = new TextView(ctx);
        btnMinus.setText("  −  ");
        btnMinus.setTextSize(22f);
        btnMinus.setTextColor(Color.parseColor("#22E5FF"));
        btnMinus.setGravity(Gravity.CENTER);
        btnMinus.setTypeface(Typeface.DEFAULT_BOLD);
        btnMinus.setPadding(dp(ctx, 16), 0, dp(ctx, 8), 0);

        final EditText limitInput = new EditText(ctx);
        limitInput.setText("1");
        limitInput.setGravity(Gravity.CENTER);
        limitInput.setTextColor(Color.WHITE);
        limitInput.setTextSize(16f);
        limitInput.setTypeface(Typeface.DEFAULT_BOLD);
        limitInput.setBackground(null);
        limitInput.setSingleLine(true);
        limitInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        limitInput.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        final TextView btnPlus = new TextView(ctx);
        btnPlus.setText("  +  ");
        btnPlus.setTextSize(22f);
        btnPlus.setTextColor(Color.parseColor("#22E5FF"));
        btnPlus.setGravity(Gravity.CENTER);
        btnPlus.setTypeface(Typeface.DEFAULT_BOLD);
        btnPlus.setPadding(dp(ctx, 8), 0, dp(ctx, 16), 0);

        attachPressScale(btnMinus);
        attachPressScale(btnPlus);

        limitRow.addView(btnMinus);
        limitRow.addView(limitInput);
        limitRow.addView(btnPlus);
        card.addView(limitRow);

        // ── ADD KEY BUTTON ──
        final TextView btnAdd = new TextView(ctx);
        btnAdd.setText("✦  ADD KEY");
        btnAdd.setTypeface(Typeface.DEFAULT_BOLD);
        btnAdd.setTextColor(Color.parseColor("#001A2E"));
        btnAdd.setTextSize(15f);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setLetterSpacing(0.1f);
        GradientDrawable btnBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.parseColor("#22E5FF"), Color.parseColor("#0EA5E9")}
        );
        btnBg.setCornerRadius(dp(ctx, 16));
        btnAdd.setBackground(btnBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(ctx, 52));
        btnLp.bottomMargin = dp(ctx, 10);
        btnAdd.setLayoutParams(btnLp);
        attachPressScale(btnAdd);

        // Glow animasi pada tombol
        final GradientDrawable animBtnBg = btnBg;
        ValueAnimator btnGlow = ValueAnimator.ofFloat(0.6f, 1.0f);
        btnGlow.setDuration(1500);
        btnGlow.setRepeatMode(ValueAnimator.REVERSE);
        btnGlow.setRepeatCount(ValueAnimator.INFINITE);
        btnGlow.setInterpolator(new AccelerateDecelerateInterpolator());
        btnGlow.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                float v = (float) anim.getAnimatedValue();
                animBtnBg.setStroke((int)(dp(ctx, 1) * v * 2),
                    applyAlpha(Color.parseColor("#22E5FF"), v));
            }
        });
        btnGlow.start();

        card.addView(btnAdd);

        // ── VIEW KEYS LINK ──
        TextView btnView = new TextView(ctx);
        btnView.setText("View all keys →");
        btnView.setTextSize(12f);
        btnView.setTextColor(Color.parseColor("#38BDF8"));
        btnView.setGravity(Gravity.CENTER);
        btnView.setPadding(0, dp(ctx, 4), 0, 0);
        attachPressScale(btnView);
        card.addView(btnView);

        scrollContent.addView(card);

        // ── LOADING DIALOG ──
        final Dialog loadingDialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout dialogRoot = new FrameLayout(ctx);
        dialogRoot.setBackgroundColor(0xCC000000);

        LinearLayout dialogBox = new LinearLayout(ctx);
        dialogBox.setOrientation(LinearLayout.VERTICAL);
        dialogBox.setGravity(Gravity.CENTER);
        dialogBox.setPadding(dp(ctx, 28), dp(ctx, 24), dp(ctx, 28), dp(ctx, 24));
        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(0xEE0B1120);
        dialogBg.setCornerRadius(dp(ctx, 20));
        dialogBg.setStroke(dp(ctx, 1), 0x8822E5FF);
        dialogBox.setBackground(dialogBg);
        dialogBox.setElevation(40f);

        ProgressBar dialogPb = new ProgressBar(ctx);
        TextView dialogTv = new TextView(ctx);
        dialogTv.setText("Menambahkan key...");
        dialogTv.setTextColor(Color.parseColor("#22E5FF"));
        dialogTv.setTextSize(14f);
        dialogTv.setTypeface(Typeface.MONOSPACE);
        dialogTv.setPadding(0, dp(ctx, 10), 0, 0);

        dialogBox.addView(dialogPb);
        dialogBox.addView(dialogTv);

        FrameLayout.LayoutParams dialogBoxLp = new FrameLayout.LayoutParams(
            dp(ctx, 240), -2, Gravity.CENTER);
        dialogRoot.addView(dialogBox, dialogBoxLp);
        loadingDialog.setContentView(dialogRoot);
        loadingDialog.setCancelable(false);

        // ===================== LOGIC =====================

        btnMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int val = Integer.parseInt(limitInput.getText().toString().trim());
                    if (val > 1) val--;
                    limitInput.setText(String.valueOf(val));
                } catch (Exception e) { limitInput.setText("1"); }
            }
        });

        btnPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int val = Integer.parseInt(limitInput.getText().toString().trim());
                    if (val < 99) val++;
                    limitInput.setText(String.valueOf(val));
                } catch (Exception e) { limitInput.setText("1"); }
            }
        });

        btnView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ctx instanceof MainActivity) {
                    ((MainActivity) ctx).showViewer();
                }
            }
        });

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String plainKey = keyInput.getText().toString().trim();
                if (plainKey.isEmpty()) {
                    showNeonToast(ctx, "Key name cannot be empty");
                    return;
                }

                int deviceLimit = 1;
                try {
                    String dl = limitInput.getText().toString().trim();
                    if (!dl.isEmpty()) {
                        deviceLimit = Integer.parseInt(dl);
                        if (deviceLimit < 1) deviceLimit = 1;
                    }
                } catch (Exception e) { deviceLimit = 1; }
                final int finalDeviceLimit = deviceLimit;

                int option = selectedExpireIdx[0];
                long days = 1;
                if      (option == 0) days = 1;
                else if (option == 1) days = 3;
                else if (option == 2) days = 7;
                else if (option == 3) days = 14;
                else if (option == 4) days = 30;
                else if (option == 5) days = 365L * 1000000L;
                else if (option == 6) {
                    String c = customDaysInput.getText().toString().trim();
                    if (c.isEmpty()) {
                        showNeonToast(ctx, "Please enter the number of days");
                        return;
                    }
                    try { days = Long.parseLong(c); }
                    catch (Exception e) {
                        showNeonToast(ctx, "Invalid number of days");
                        return;
                    }
                }

                // selectedType[0] sekarang selalu sync dengan pilihan durasi user
                final String typeForKey = selectedType[0];
                final String role       = selectedRole[0];
                final long finalDays    = days;

                loadingDialog.show();
                btnAdd.setEnabled(false);
                btnAdd.setText("⟳  Menambahkan...");

                addKeyToServer(plainKey, finalDays, typeForKey, finalDeviceLimit, role, new Callback() {
                    @Override
                    public void onKeyAdded() {
                        loadingDialog.dismiss();
                        btnAdd.setEnabled(true);
                        btnAdd.setText("✦  ADD KEY");
                        showNeonToast(ctx, "Key created successfully");
                        ActivityLog.log(ctx, "create_key", keyInput.getText().toString().trim());
                        if (cb != null) cb.onKeyAdded();
                    }
                });
            }
        });

        // Animasi card masuk (fade + slide up)
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 30));
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        return root;
    }

    // ── Key Name Generator ──────────────────────────────
    private static String generateKeyName() {
        // Format: XyperXit-[4 random alphanumeric uppercase]
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rng = new java.util.Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) code.append(chars.charAt(rng.nextInt(chars.length())));
        return "XyperXit-" + code.toString();
    }

    // ===================== API =====================
    private static void addKeyToServer(
        final String plainKey,
        final long days,
        final String type,
        final int deviceLimit,
        final String role,
        final Callback cb
    ) {
        try {
            long now       = System.currentTimeMillis();
            long expiresAt = (days >= 365L * 999999L) ? 0 : now + (days * 24L * 60L * 60L * 1000L);

            JSONObject keyData = new JSONObject();
            keyData.put("plain_key",       plainKey);
            keyData.put("type",            type);
            keyData.put("role",            role);
            keyData.put("expired",         expiresAt);
            keyData.put("created_at",      now);
            keyData.put("device_limit",    deviceLimit);
            keyData.put("violation_count", 0);
            keyData.put("banned_until",    0);
            keyData.put("devices",         new JSONArray());

            JSONObject payload = new JSONObject();
            payload.put("action", "create_key");
            payload.put("data",   keyData);

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (response.isSuccessful()) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    if (cb != null) cb.onKeyAdded();
                                }
                            });
                        } else {
                            postError("Failed to create key — HTTP " + response.code());
                        }
                    }
                    @Override
                    public void onFailure(Call<String> call, Throwable t) {
                        postError("An error occurred: " + t.getMessage());
                    }
                });
        } catch (final Exception e) {
            postError("An error occurred: " + e.getMessage());
        }
    }

    // ===================== UI HELPERS =====================
    private static View buildHeader(Context ctx) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 14);
        header.setLayoutParams(lp);

        TextView icon = new TextView(ctx);
        icon.setText("🔑");
        icon.setTextSize(28f);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(-2, -2);
        iconLp.rightMargin = dp(ctx, 12);
        icon.setLayoutParams(iconLp);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(ctx);
        title.setText("Add Key");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(20f);
        title.setTextColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            title.setShadowLayer(14f, 0f, 0f, Color.parseColor("#22E5FF"));
        }

        TextView sub = new TextView(ctx);
        sub.setText("Xyper Xit  ·  Admin Panel");
        sub.setTextSize(10f);
        sub.setTextColor(Color.parseColor("#38BDF8"));
        sub.setTypeface(Typeface.MONOSPACE);

        textWrap.addView(title);
        textWrap.addView(sub);
        header.addView(icon);
        header.addView(textWrap);
        return header;
    }

    private static View buildDivider(Context ctx) {
        View line = new View(ctx);
        line.setBackgroundColor(0x3322E5FF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        lp.bottomMargin = dp(ctx, 16);
        line.setLayoutParams(lp);
        return line;
    }

    private static TextView buildSectionLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#7DD3FC"));
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static void styleEditText(Context ctx, EditText et) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(dp(ctx, 14));
        bg.setStroke(dp(ctx, 1), 0x5522E5FF);
        et.setBackground(bg);
        et.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
    }

    /**
     * Neon focus border — border nyala lebih terang saat EditText aktif
     */
    private static void attachFocusBorder(final Context ctx, final EditText et) {
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(hasFocus ? 0x2522E5FF : 0x1AFFFFFF);
                bg.setCornerRadius(dp(ctx, 14));
                bg.setStroke(
                    hasFocus ? dp(ctx, 2) : dp(ctx, 1),
                    hasFocus ? 0xFF22E5FF : 0x5522E5FF
                );
                et.setBackground(bg);
                et.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
            }
        });
    }

    /**
     * Press scale — view sedikit mengecil saat ditekan, kembali normal saat dilepas
     */
    private static void attachPressScale(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.95f).scaleY(0.95f)
                            .setDuration(80)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(150)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                        break;
                }
                return false; // biar onClick tetap jalan
            }
        });
    }

    // ===================== BOTTOM SHEET =====================
    interface BottomSheetCallback {
        void onSelected(String val, int idx);
    }

    private static void showBottomSheet(final Context ctx, final String title,
            final String[] options, final String current, final BottomSheetCallback cb) {
        android.app.Dialog dialog = new android.app.Dialog(ctx,
            android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(ctx);
        final android.app.Dialog dialogFinal = dialog;

        View dim = new View(ctx);
        dim.setBackgroundColor(0xAA000000);
        dim.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        dim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialogFinal.dismiss(); }
        });
        root.addView(dim);

        LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(ctx, 16), dp(ctx, 20), dp(ctx, 16), dp(ctx, 32));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#0A1628"));
        sheetBg.setCornerRadii(new float[]{dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20),0,0,0,0});
        sheetBg.setStroke(dp(ctx, 1), Color.parseColor("#1A3A50"));
        sheet.setBackground(sheetBg);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(-1, -2);
        sheetLp.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(sheetLp);

        // Handle bar
        View handle = new View(ctx);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setColor(Color.parseColor("#22455A"));
        hBg.setCornerRadius(dp(ctx, 4));
        handle.setBackground(hBg);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 4));
        hLp.gravity = Gravity.CENTER_HORIZONTAL;
        hLp.bottomMargin = dp(ctx, 16);
        handle.setLayoutParams(hLp);
        sheet.addView(handle);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#22E5FF"));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.bottomMargin = dp(ctx, 12);
        tvTitle.setLayoutParams(tLp);
        sheet.addView(tvTitle);

        View divider = new View(ctx);
        divider.setBackgroundColor(Color.parseColor("#1A3A50"));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        dLp.bottomMargin = dp(ctx, 8);
        divider.setLayoutParams(dLp);
        sheet.addView(divider);

        for (int i = 0; i < options.length; i++) {
            final String opt = options[i];
            final int fi = i;
            boolean sel = opt.equals(current);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
            if (sel) {
                GradientDrawable rBg = new GradientDrawable();
                rBg.setColor(Color.parseColor("#0D2A3A"));
                rBg.setCornerRadius(dp(ctx, 10));
                rBg.setStroke(dp(ctx, 1), Color.parseColor("#22E5FF"));
                row.setBackground(rBg);
            }
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
            rLp.bottomMargin = dp(ctx, 4);
            row.setLayoutParams(rLp);

            TextView tvOpt = new TextView(ctx);
            tvOpt.setText(opt);
            tvOpt.setTextColor(sel ? Color.parseColor("#22E5FF") : Color.parseColor("#CBD5E1"));
            tvOpt.setTextSize(14f);
            tvOpt.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tvOpt.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(tvOpt);

            if (sel) {
                TextView chk = new TextView(ctx);
                chk.setText("\u2713");
                chk.setTextColor(Color.parseColor("#22E5FF"));
                chk.setTextSize(16f);
                row.addView(chk);
            }

            attachPressScale(row);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dialogFinal.dismiss();
                    cb.onSelected(opt, fi);
                }
            });
            sheet.addView(row);
        }

        root.addView(sheet);
        dialog.setContentView(root);

        // Animasi slide up
        sheet.setTranslationY(600f);
        sheet.animate().translationY(0).setDuration(300)
            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        dialog.show();
    }

    // ===================== TOAST =====================
    private static void showNeonToast(Context ctx, String msg) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14));
        layout.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE050816);
        bg.setCornerRadius(dp(ctx, 16));
        bg.setStroke(dp(ctx, 1), 0xBB22E5FF);
        layout.setBackground(bg);

        TextView tv = new TextView(ctx);
        tv.setText(msg);
        tv.setTextSize(13f);
        tv.setTextColor(Color.parseColor("#22E5FF"));
        if (android.os.Build.VERSION.SDK_INT < 31) {
            tv.setShadowLayer(6f, 0f, 0f, Color.parseColor("#0080FF"));
        }
        layout.addView(tv);

        android.widget.Toast toast = new android.widget.Toast(ctx);
        toast.setDuration(android.widget.Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(ctx, 100));
        toast.show();
    }

    private static void postError(final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                showNeonToast(ctxGlobal, "❌ " + msg);
            }
        });
    }

    // ===================== UTILS =====================
    private static int applyAlpha(int color, float alpha) {
        int a = Math.round(alpha * 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics()
        );
    }
}
