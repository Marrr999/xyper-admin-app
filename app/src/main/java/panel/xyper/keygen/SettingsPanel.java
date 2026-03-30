package panel.xyper.keygen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * SettingsPanel — App preferences
 * Style identical to other panels (animated bg, neon glow, card animations)
 */
public class SettingsPanel {

    // ── Preference keys ──────────────────────────────────
    public static final String PREFS            = "xyper_settings";
    public static final String KEY_HAPTIC       = "haptic_enabled";
    public static final String KEY_AUTO_REFRESH = "auto_refresh_enabled";
    public static final String KEY_CONFIRM_DEL  = "confirm_delete";
    public static final String KEY_SHOW_DEVICES = "show_device_details";
    public static final String KEY_DARK_DEFAULT = "dark_mode_default";
    public static final String KEY_NOTIF_EXPIRY = "notif_expiry_enabled";
    public static final String KEY_NOTIF_DAYS   = "notif_expiry_days";
    public static final String KEY_NOTIF_CUSTOM = "notif_custom_days";

    // ── Colors ───────────────────────────────────────────
    private static final String CYAN   = "#22E5FF";
    private static final String PURPLE = "#A78BFA";
    private static final String GREEN  = "#34D399";
    private static final String RED    = "#F87171";
    private static final String YELLOW = "#FBBF24";
    private static final String MUTED  = "#475569";
    private static final String CARD   = "#0B1426";

    // ── Getters ──────────────────────────────────────────
    public static boolean isHapticEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HAPTIC, true);
    }
    public static boolean isAutoRefreshEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_REFRESH, true);
    }
    public static boolean isConfirmDelete(Context ctx) {
        return prefs(ctx).getBoolean(KEY_CONFIRM_DEL, true);
    }
    public static boolean isShowDeviceDetails(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SHOW_DEVICES, true);
    }
    public static boolean isDarkDefault(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DARK_DEFAULT, true);
    }
    public static boolean isNotifExpiryEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NOTIF_EXPIRY, false);
    }
    public static String getNotifDays(Context ctx) {
        return prefs(ctx).getString(KEY_NOTIF_DAYS, "1,3,7");
    }
    public static int getNotifCustomDays(Context ctx) {
        return prefs(ctx).getInt(KEY_NOTIF_CUSTOM, 0);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ══════════════════════════════════════════════════════
    // BUILD
    // ══════════════════════════════════════════════════════
    public static View build(final Context ctx) {
        // ── Root: FrameLayout with animated background (same as other panels) ──
        FrameLayout root = new FrameLayout(ctx);

        // Animated background
        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        // Dark overlay
        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x55000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // Scroll content
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx, 16), dp(ctx, 20), dp(ctx, 16), dp(ctx, 80));
        scroll.addView(content, new LinearLayout.LayoutParams(-1, -2));

        // ── Header ──────────────────────────────────────
        content.addView(buildHeader(ctx));

        int delay = 0;

        // ══════════════════════════════════════════════
        // SECTION: GENERAL — fieldset card
        // ══════════════════════════════════════════════
        FrameLayout generalSection = buildFieldsetCard(ctx, "🎮  GENERAL", CYAN);
        LinearLayout generalInner = (LinearLayout) generalSection.getTag();

        final View hapticRow = buildToggleRow(ctx,
            "Haptic Feedback",
            "Vibrate on ban, delete, and bulk actions",
            CYAN, KEY_HAPTIC, true, null);
        generalInner.addView(hapticRow);

        final View autoRow = buildToggleRow(ctx,
            "Auto-refresh Dashboard",
            "Refresh stats every 5 minutes automatically",
            GREEN, KEY_AUTO_REFRESH, true, null);
        LinearLayout.LayoutParams rowLp1 = new LinearLayout.LayoutParams(-1, -2);
        rowLp1.topMargin = dp(ctx, 8);
        autoRow.setLayoutParams(rowLp1);
        generalInner.addView(autoRow);

        final View confirmRow = buildToggleRow(ctx,
            "Confirm before Delete",
            "Show confirmation dialog before deleting keys",
            RED, KEY_CONFIRM_DEL, true, null);
        LinearLayout.LayoutParams rowLp2 = new LinearLayout.LayoutParams(-1, -2);
        rowLp2.topMargin = dp(ctx, 8);
        confirmRow.setLayoutParams(rowLp2);
        generalInner.addView(confirmRow);

        final View devRow = buildToggleRow(ctx,
            "Show Device Details",
            "Expand device info cards in Key Viewer",
            PURPLE, KEY_SHOW_DEVICES, true, null);
        LinearLayout.LayoutParams rowLp3 = new LinearLayout.LayoutParams(-1, -2);
        rowLp3.topMargin = dp(ctx, 8);
        devRow.setLayoutParams(rowLp3);
        generalInner.addView(devRow);

        final View darkRow = buildToggleRow(ctx,
            "Dark Mode by Default",
            "Always open Dashboard in dark mode",
            YELLOW, KEY_DARK_DEFAULT, true, null);
        LinearLayout.LayoutParams rowLp4 = new LinearLayout.LayoutParams(-1, -2);
        rowLp4.topMargin = dp(ctx, 8);
        darkRow.setLayoutParams(rowLp4);
        generalInner.addView(darkRow);

        animateIn(generalSection, ctx, delay += 40);
        content.addView(generalSection);

        // ══════════════════════════════════════════════
        // SECTION: NOTIFICATIONS — fieldset card
        // ══════════════════════════════════════════════
        LinearLayout.LayoutParams nSecLp = new LinearLayout.LayoutParams(-1, -2);
        nSecLp.topMargin = dp(ctx, 20);

        FrameLayout notifSection = buildFieldsetCard(ctx, "🔔  NOTIFICATIONS", YELLOW);
        notifSection.setLayoutParams(nSecLp);
        LinearLayout notifInner = (LinearLayout) notifSection.getTag();

        // Notif days card — hidden until toggle on
        final LinearLayout notifDaysCard = new LinearLayout(ctx);
        notifDaysCard.setOrientation(LinearLayout.VERTICAL);
        buildNotifDaysCard(ctx, notifDaysCard);
        notifDaysCard.setVisibility(isNotifExpiryEnabled(ctx) ? View.VISIBLE : View.GONE);

        final View notifRow = buildToggleRow(ctx,
            "Expiry Alert",
            "Notify when keys are about to expire",
            YELLOW, KEY_NOTIF_EXPIRY, false,
            new OnToggleChange() {
                @Override public void onChange(boolean on) {
                    if (on) {
                        notifDaysCard.setVisibility(View.VISIBLE);
                        notifDaysCard.setAlpha(0f);
                        notifDaysCard.animate().alpha(1f).setDuration(300).start();
                        ExpiryNotificationWorker.schedule(ctx);
                    } else {
                        notifDaysCard.animate().alpha(0f).setDuration(200)
                            .withEndAction(new Runnable() {
                                @Override public void run() {
                                    notifDaysCard.setVisibility(View.GONE);
                                }
                            }).start();
                        ExpiryNotificationWorker.cancel(ctx);
                    }
                }
            });
        notifInner.addView(notifRow);

        LinearLayout.LayoutParams daysLp = new LinearLayout.LayoutParams(-1, -2);
        daysLp.topMargin = dp(ctx, 8);
        notifDaysCard.setLayoutParams(daysLp);
        notifInner.addView(notifDaysCard);

        animateIn(notifSection, ctx, delay += 80);
        content.addView(notifSection);

        // ══════════════════════════════════════════════
        // SECTION: ABOUT — fieldset card
        // ══════════════════════════════════════════════
        LinearLayout.LayoutParams aSecLp = new LinearLayout.LayoutParams(-1, -2);
        aSecLp.topMargin = dp(ctx, 20);

        FrameLayout aboutSection = buildFieldsetCard(ctx, "ℹ️  ABOUT", "#64748B");
        aboutSection.setLayoutParams(aSecLp);
        LinearLayout aboutInner = (LinearLayout) aboutSection.getTag();

        TextView aboutTv = new TextView(ctx);
        aboutTv.setText("Xyper Admin Panel" + "\n" + "Version 1.0.0" + "\n\n"
            + "Built for Xyper Key Manager system." + "\n"
            + "All settings are stored locally on device.");
        aboutTv.setTextColor(Color.parseColor("#64748B"));
        aboutTv.setTextSize(12f);
        aboutTv.setTypeface(Typeface.MONOSPACE);
        aboutTv.setLineSpacing(dp(ctx, 2), 1f);
        aboutInner.addView(aboutTv);

        animateIn(aboutSection, ctx, delay += 80);
        content.addView(aboutSection);

        return root;
    }

    // ══════════════════════════════════════════════════════
    // FIELDSET CARD — neon border with title cutting through top
    // Returns FrameLayout; inner LinearLayout stored in .getTag()
    // ══════════════════════════════════════════════════════
    private static FrameLayout buildFieldsetCard(final Context ctx,
            final String titleText, final String accentColor) {

        // ── Outer FrameLayout (allows title to overlap border) ──
        final FrameLayout outer = new FrameLayout(ctx);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(-1, -2);
        outerLp.bottomMargin = dp(ctx, 4);
        outer.setLayoutParams(outerLp);

        // ── Border card (transparent bg + neon stroke) ──
        final LinearLayout borderCard = new LinearLayout(ctx);
        borderCard.setOrientation(LinearLayout.VERTICAL);
        // Top padding = 22dp so content doesn't go behind the title label
        borderCard.setPadding(dp(ctx, 14), dp(ctx, 22), dp(ctx, 14), dp(ctx, 16));

        final GradientDrawable borderBg = new GradientDrawable();
        borderBg.setColor(0x0D22E5FF); // very subtle fill
        borderBg.setCornerRadius(dp(ctx, 20));
        try {
            int ac = Color.parseColor(accentColor);
            borderBg.setStroke(dp(ctx, 1),
                Color.argb(120, Color.red(ac), Color.green(ac), Color.blue(ac)));
        } catch (Exception e) {
            borderBg.setStroke(dp(ctx, 1), Color.parseColor("#22455A"));
        }
        borderCard.setBackground(borderBg);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(ctx, 12); // leave room for title label above
        borderCard.setLayoutParams(cardLp);
        outer.addView(borderCard);

        // ── Title label — sits on top of border, overlapping ──
        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(ctx, 6), dp(ctx, 3), dp(ctx, 10), dp(ctx, 3));

        // Title background — gradient transparent sides so it blends with any background
        GradientDrawable titleBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x00000000, 0xDD050D1A, 0xDD050D1A, 0x00000000});
        titleRow.setBackground(titleBg);

        // Accent dot
        View dot = new View(ctx);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        try { dotBg.setColor(Color.parseColor(accentColor)); }
        catch (Exception e) { dotBg.setColor(Color.parseColor(CYAN)); }
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 6), dp(ctx, 6));
        dotLp.rightMargin = dp(ctx, 6);
        dot.setLayoutParams(dotLp);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            try {
                dot.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            } catch (Exception ignored) {}
        }
        titleRow.addView(dot);

        // Title text
        TextView titleTv = new TextView(ctx);
        titleTv.setText(titleText);
        titleTv.setTextSize(11f);
        titleTv.setTypeface(Typeface.MONOSPACE);
        titleTv.setLetterSpacing(0.08f);
        try { titleTv.setTextColor(Color.parseColor(accentColor)); }
        catch (Exception e) { titleTv.setTextColor(Color.parseColor(CYAN)); }
        if (android.os.Build.VERSION.SDK_INT < 31) {
            try {
                titleTv.setShadowLayer(6f, 0f, 0f, Color.parseColor(accentColor));
                titleTv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            } catch (Exception ignored) {}
        }
        titleRow.addView(titleTv);

        // Position title at top-left of the card, overlapping the border
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(-2, -2);
        titleLp.leftMargin = dp(ctx, 16);
        titleLp.topMargin = dp(ctx, 4); // sits on top edge of border
        titleRow.setLayoutParams(titleLp);
        outer.addView(titleRow);

        // Animate neon border breathing glow
        ValueAnimator glow = ValueAnimator.ofFloat(60f, 140f);
        glow.setDuration(2500);
        glow.setRepeatMode(ValueAnimator.REVERSE);
        glow.setRepeatCount(ValueAnimator.INFINITE);
        glow.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float alpha = (float) a.getAnimatedValue();
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0x0D22E5FF);
                bg.setCornerRadius(dp(ctx, 20));
                try {
                    int ac = Color.parseColor(accentColor);
                    bg.setStroke(dp(ctx, 1),
                        Color.argb((int) alpha,
                            Color.red(ac), Color.green(ac), Color.blue(ac)));
                } catch (Exception ignored) {}
                borderCard.setBackground(bg);
            }
        });
        glow.start();

        // Store inner container in tag so caller can add children
        outer.setTag(borderCard);
        return outer;
    }

    // ══════════════════════════════════════════════════════
    // HEADER — same style as KeyViewer/ServerLogPanel
    // ══════════════════════════════════════════════════════
    private static View buildHeader(final Context ctx) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
        hLp.bottomMargin = dp(ctx, 20);
        header.setLayoutParams(hLp);

        // Title row
        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(ctx);
        title.setText("Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            title.setShadowLayer(18f, 0f, 0f, Color.parseColor(CYAN));
            title.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        TextView sub = new TextView(ctx);
        sub.setText("Xyper Xit  \u00b7  Preferences");
        sub.setTextSize(11f);
        sub.setTextColor(Color.parseColor(CYAN));
        sub.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(ctx, 2);
        sub.setLayoutParams(subLp);

        textWrap.addView(title);
        textWrap.addView(sub);
        titleRow.addView(textWrap);

        header.addView(titleRow);

        // Neon divider line under header
        View divider = new View(ctx);
        GradientDrawable divBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.parseColor(CYAN), 0x0022E5FF});
        divider.setBackground(divBg);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        divLp.topMargin = dp(ctx, 10);
        divider.setLayoutParams(divLp);
        header.addView(divider);

        // Animate header slide in from top
        header.setAlpha(0f);
        header.setTranslationY(-dp(ctx, 20));
        header.postDelayed(new Runnable() {
            @Override public void run() {
                header.animate().alpha(1f).translationY(0f)
                    .setDuration(400).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        }, 50);

        return header;
    }

    // ══════════════════════════════════════════════════════
    // SECTION LABEL — with neon dot accent
    // ══════════════════════════════════════════════════════
    private static View buildSectionLabel(Context ctx, String text) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 12);
        row.setLayoutParams(lp);

        // Neon accent line
        View accent = new View(ctx);
        GradientDrawable acBg = new GradientDrawable();
        acBg.setColor(Color.parseColor(CYAN));
        acBg.setCornerRadius(dp(ctx, 2));
        accent.setBackground(acBg);
        LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(dp(ctx, 3), dp(ctx, 16));
        acLp.rightMargin = dp(ctx, 10);
        accent.setLayoutParams(acLp);
        row.addView(accent);

        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(CYAN));
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setLetterSpacing(0.08f);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            tv.setShadowLayer(6f, 0f, 0f, Color.parseColor(CYAN));
            tv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        row.addView(tv);

        return row;
    }

    // ══════════════════════════════════════════════════════
    // TOGGLE ROW — with animated pill + press scale
    // ══════════════════════════════════════════════════════
    interface OnToggleChange {
        void onChange(boolean on);
    }

    private static View buildToggleRow(final Context ctx, final String title,
            final String desc, final String color, final String prefKey,
            final boolean defaultVal, final OnToggleChange listener) {

        final LinearLayout card = buildCard(ctx, 0x0A000000, "#1E3A50");
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16));

        // Text column
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);

        if (desc != null && !desc.isEmpty()) {
            TextView tvDesc = new TextView(ctx);
            tvDesc.setText(desc);
            tvDesc.setTextColor(Color.parseColor(MUTED));
            tvDesc.setTextSize(11f);
            tvDesc.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, -2);
            dLp.topMargin = dp(ctx, 3);
            tvDesc.setLayoutParams(dLp);
            textCol.addView(tvDesc);
        }
        card.addView(textCol);

        // Toggle pill
        final boolean[] isOn = {prefs(ctx).getBoolean(prefKey, defaultVal)};
        final LinearLayout pill = buildTogglePill(ctx, isOn[0], color);
        // wrapper wraps the FrameLayout pill — set margin here
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
        pillLp.leftMargin = dp(ctx, 12);
        pill.setLayoutParams(pillLp);
        card.addView(pill);

        // Border glow on card matches toggle color when ON
        updateCardBorder(card, isOn[0], color, ctx);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                isOn[0] = !isOn[0];
                prefs(ctx).edit().putBoolean(prefKey, isOn[0]).apply();
                animateToggle(pill, isOn[0], color, ctx);
                updateCardBorder(card, isOn[0], color, ctx);
                if (isOn[0] && isHapticEnabled(ctx)) haptic(ctx);
                if (listener != null) listener.onChange(isOn[0]);
            }
        });
        attachPressScale(card);

        return card;
    }

    private static LinearLayout buildTogglePill(Context ctx, boolean on, String color) {
        // Use LinearLayout as outer container for LayoutParams compatibility
        // but put FrameLayout inside so knob uses absolute translationX only — no Gravity conflict
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);

        // Pill track = FrameLayout
        FrameLayout pill = new FrameLayout(ctx);
        int pw = dp(ctx, 56), ph = dp(ctx, 28);
        pill.setLayoutParams(new LinearLayout.LayoutParams(pw, ph));
        pill.setTag("pill"); // tag so animateToggle can find it

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(on ? Color.parseColor(color) : Color.parseColor("#2D3748"));
        pillBg.setCornerRadius(dp(ctx, 14));
        pillBg.setStroke(dp(ctx, 1), on ? Color.parseColor(color) : Color.parseColor("#4A5568"));
        pill.setBackground(pillBg);

        // Knob: 20dp, centered vertically, X position = translationX only
        View knob = new View(ctx);
        GradientDrawable kBg = new GradientDrawable();
        kBg.setShape(GradientDrawable.OVAL);
        kBg.setColor(on ? Color.WHITE : Color.parseColor("#A0AEC0"));
        knob.setBackground(kBg);
        FrameLayout.LayoutParams knobLp = new FrameLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20));
        knobLp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        knobLp.leftMargin = dp(ctx, 4);
        knob.setLayoutParams(knobLp);
        // Set initial position
        float knobX = on ? dp(ctx, 56) - dp(ctx, 20) - dp(ctx, 4) - dp(ctx, 4) : 0f;
        knob.setTranslationX(knobX);
        pill.addView(knob);
        wrapper.addView(pill);

        return wrapper;
    }

    private static void animateToggle(final LinearLayout wrapper, final boolean on,
            final String color, final Context ctx) {
        // wrapper → pill (FrameLayout) → knob (View)
        if (wrapper.getChildCount() == 0) return;
        final FrameLayout pill = (FrameLayout) wrapper.getChildAt(0);
        if (pill == null || pill.getChildCount() == 0) return;
        final View knob = pill.getChildAt(0);
        if (knob == null) return;

        int pillW  = dp(ctx, 56);
        int knobW  = dp(ctx, 20);
        int padEnd = dp(ctx, 4);
        // ON: knob slides to right end, OFF: knob at left start
        float toX = on ? (pillW - knobW - padEnd - padEnd) : 0f;
        knob.animate().translationX(toX).setDuration(220)
            .setInterpolator(new DecelerateInterpolator()).start();

        // Knob color
        GradientDrawable kBg = new GradientDrawable();
        kBg.setShape(GradientDrawable.OVAL);
        kBg.setColor(on ? Color.WHITE : Color.parseColor("#A0AEC0"));
        knob.setBackground(kBg);

        // Pill background color animation
        int fromColor = on ? Color.parseColor("#2D3748") : Color.parseColor(color);
        int toColor   = on ? Color.parseColor(color)    : Color.parseColor("#2D3748");
        final int strokeOff = Color.parseColor("#4A5568");
        final int strokeOn  = Color.parseColor(color);

        ValueAnimator colorAnim = ValueAnimator.ofArgb(fromColor, toColor);
        colorAnim.setDuration(220);
        colorAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor((int) a.getAnimatedValue());
                bg.setCornerRadius(dp(ctx, 14));
                bg.setStroke(dp(ctx, 1), on ? strokeOn : strokeOff);
                pill.setBackground(bg);
            }
        });
        colorAnim.start();
    }

    private static void updateCardBorder(LinearLayout card, boolean on,
            String color, Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(CARD));
        bg.setCornerRadius(dp(ctx, 16));
        if (on) {
            bg.setStroke(dp(ctx, 1), Color.parseColor(color) & 0x88FFFFFF | (Color.parseColor(color) & 0xFF000000));
            // Semi-transparent stroke
            int c = Color.parseColor(color);
            bg.setStroke(dp(ctx, 1), Color.argb(100,
                Color.red(c), Color.green(c), Color.blue(c)));
        } else {
            bg.setStroke(dp(ctx, 1), Color.parseColor("#1E3A50"));
        }
        card.setBackground(bg);
    }

    // ══════════════════════════════════════════════════════
    // NOTIF DAYS CARD
    // ══════════════════════════════════════════════════════
    private static void buildNotifDaysCard(final Context ctx,
            final LinearLayout container) {
        LinearLayout card = buildCard(ctx, 0x0A22E5FF, "#22455A");
        card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, -2);
        cLp.topMargin = dp(ctx, 6);
        card.setLayoutParams(cLp);

        // Title
        TextView lbl = new TextView(ctx);
        lbl.setText("Alert me before expiry:");
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(13f);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(-1, -2);
        lblLp.bottomMargin = dp(ctx, 12);
        lbl.setLayoutParams(lblLp);
        card.addView(lbl);

        // Preset chips
        LinearLayout chipRow = new LinearLayout(ctx);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(-1, -2);
        crLp.bottomMargin = dp(ctx, 14);
        chipRow.setLayoutParams(crLp);

        String savedDays = prefs(ctx).getString(KEY_NOTIF_DAYS, "1,3,7");
        final int[] presetDays = {1, 3, 7, 14};
        final String[] presetLabels = {"1 Day", "3 Days", "7 Days", "14 Days"};

        for (int i = 0; i < presetDays.length; i++) {
            final int day = presetDays[i];
            final String label = presetLabels[i];
            boolean selected = isSelectedDay(savedDays, day);
            final TextView chip = makeChip(ctx, label, selected);
            LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(-2, -2);
            cpLp.rightMargin = dp(ctx, 8);
            chip.setLayoutParams(cpLp);

            final boolean[] isOn = {selected};
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    isOn[0] = !isOn[0];
                    // Animate chip
                    chip.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                chip.animate().scaleX(1f).scaleY(1f).setDuration(120)
                                    .setInterpolator(new OvershootInterpolator(2f)).start();
                                updateChipStyle(chip, isOn[0], ctx);
                            }
                        }).start();
                    String current = prefs(ctx).getString(KEY_NOTIF_DAYS, "1,3,7");
                    String updated = isOn[0] ? addDay(current, day) : removeDay(current, day);
                    prefs(ctx).edit().putString(KEY_NOTIF_DAYS, updated).apply();
                    if (isNotifExpiryEnabled(ctx)) ExpiryNotificationWorker.schedule(ctx);
                    if (isHapticEnabled(ctx)) haptic(ctx);
                }
            });
            chipRow.addView(chip);
        }
        card.addView(chipRow);

        // Divider
        View div = new View(ctx);
        GradientDrawable dBg = new GradientDrawable();
        dBg.setColor(0x1522E5FF);
        div.setBackground(dBg);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        dLp.bottomMargin = dp(ctx, 12);
        div.setLayoutParams(dLp);
        card.addView(div);

        // Custom days
        TextView customLbl = new TextView(ctx);
        customLbl.setText("Custom reminder (days):");
        customLbl.setTextColor(Color.parseColor("#64748B"));
        customLbl.setTextSize(12f);
        customLbl.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(-1, -2);
        clLp.bottomMargin = dp(ctx, 8);
        customLbl.setLayoutParams(clLp);
        card.addView(customLbl);

        LinearLayout customRow = new LinearLayout(ctx);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crLp2 = new LinearLayout.LayoutParams(-1, -2);
        customRow.setLayoutParams(crLp2);

        final EditText etCustom = new EditText(ctx);
        int savedCustom = prefs(ctx).getInt(KEY_NOTIF_CUSTOM, 0);
        etCustom.setHint("e.g. 30");
        if (savedCustom > 0) etCustom.setText(String.valueOf(savedCustom));
        etCustom.setTextColor(Color.WHITE);
        etCustom.setHintTextColor(0x6622E5FF);
        etCustom.setTextSize(13f);
        etCustom.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCustom.setTypeface(Typeface.MONOSPACE);
        etCustom.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0x1022E5FF);
        etBg.setCornerRadius(dp(ctx, 12));
        etBg.setStroke(dp(ctx, 1), 0x5522E5FF);
        etCustom.setBackground(etBg);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, -2, 1f);
        etLp.rightMargin = dp(ctx, 10);
        etCustom.setLayoutParams(etLp);
        customRow.addView(etCustom);

        // Save button — neon style
        TextView btnSave = new TextView(ctx);
        btnSave.setText("Save");
        btnSave.setTextColor(Color.parseColor(CYAN));
        btnSave.setTextSize(13f);
        btnSave.setTypeface(Typeface.DEFAULT_BOLD);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 18), dp(ctx, 10));
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(0x1522E5FF);
        saveBg.setCornerRadius(dp(ctx, 12));
        saveBg.setStroke(dp(ctx, 1), 0x8822E5FF);
        btnSave.setBackground(saveBg);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            btnSave.setShadowLayer(8f, 0f, 0f, Color.parseColor(CYAN));
            btnSave.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        attachPressScale(btnSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    int days = Integer.parseInt(etCustom.getText().toString().trim());
                    if (days > 0 && days <= 365) {
                        prefs(ctx).edit().putInt(KEY_NOTIF_CUSTOM, days).apply();
                        if (isNotifExpiryEnabled(ctx)) ExpiryNotificationWorker.schedule(ctx);
                        android.widget.Toast.makeText(ctx,
                            "Reminder set: " + days + " days before expiry",
                            android.widget.Toast.LENGTH_SHORT).show();
                        if (isHapticEnabled(ctx)) haptic(ctx);
                    }
                } catch (Exception ignored) {}
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etCustom.getWindowToken(), 0);
            }
        });
        customRow.addView(btnSave);
        card.addView(customRow);
        container.addView(card);
    }

    // ══════════════════════════════════════════════════════
    // UI HELPERS
    // ══════════════════════════════════════════════════════
    private static TextView makeChip(Context ctx, String label, boolean selected) {
        TextView chip = new TextView(ctx);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        updateChipStyle(chip, selected, ctx);
        return chip;
    }

    private static void updateChipStyle(TextView chip, boolean selected, Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        if (selected) {
            bg.setColor(0x3322E5FF);
            bg.setStroke(dp(ctx, 2), Color.parseColor(CYAN));
            chip.setTextColor(Color.parseColor(CYAN));
            if (android.os.Build.VERSION.SDK_INT < 31) {
                chip.setShadowLayer(6f, 0f, 0f, Color.parseColor(CYAN));
                chip.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
        } else {
            bg.setColor(Color.parseColor(CARD));
            bg.setStroke(dp(ctx, 1), Color.parseColor("#1E3A50"));
            chip.setTextColor(Color.parseColor(MUTED));
            chip.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        bg.setCornerRadius(dp(ctx, 20));
        chip.setBackground(bg);
    }

    private static LinearLayout buildCard(Context ctx, int fillColor, String strokeHex) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor != 0 ? fillColor : Color.parseColor(CARD));
        bg.setCornerRadius(dp(ctx, 16));
        try { bg.setStroke(dp(ctx, 1), Color.parseColor(strokeHex)); }
        catch (Exception e) { bg.setStroke(dp(ctx, 1), Color.parseColor("#1E3A50")); }
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 8);
        lp.topMargin = dp(ctx, 2);
        card.setLayoutParams(lp);
        return card;
    }

    // ── animateIn: slide from right (same as ConfigPanel) ──
    private static void animateIn(final View v, final Context ctx, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(ctx, 12));
        v.postDelayed(new Runnable() {
            @Override public void run() {
                v.animate().alpha(1f).translationY(0f)
                    .setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
            }
        }, delayMs);
    }

    // ── Press scale animation ──
    private static void attachPressScale(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
                } else if (e.getAction() == android.view.MotionEvent.ACTION_UP
                        || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(new OvershootInterpolator(2f)).start();
                }
                return false;
            }
        });
    }

    // ── Day helpers ──
    private static boolean isSelectedDay(String saved, int day) {
        for (String s : saved.split(",")) {
            try { if (Integer.parseInt(s.trim()) == day) return true; } catch (Exception ignored) {}
        }
        return false;
    }
    private static String addDay(String current, int day) {
        if (isSelectedDay(current, day)) return current;
        return current.isEmpty() ? String.valueOf(day) : current + "," + day;
    }
    private static String removeDay(String current, int day) {
        StringBuilder sb = new StringBuilder();
        for (String s : current.split(",")) {
            try {
                int d = Integer.parseInt(s.trim());
                if (d != day) { if (sb.length() > 0) sb.append(","); sb.append(d); }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    // ── Haptic ──
    private static void haptic(Context ctx) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.os.VibratorManager vm = (android.os.VibratorManager)
                    ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vm.getDefaultVibrator().vibrate(
                    android.os.VibrationEffect.createOneShot(20,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.os.Vibrator vb = (android.os.Vibrator)
                    ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vb != null) vb.vibrate(
                    android.os.VibrationEffect.createOneShot(20,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                android.os.Vibrator vb = (android.os.Vibrator)
                    ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (vb != null) vb.vibrate(20);
            }
        } catch (Exception ignored) {}
    }

    private static int dp(Context ctx, int val) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }
}
