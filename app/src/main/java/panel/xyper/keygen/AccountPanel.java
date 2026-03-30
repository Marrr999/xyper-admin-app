package panel.xyper.keygen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

/**
 * AccountPanel — Info akun + Stats + Recent Activity + Logout
 */
public class AccountPanel {

    private static final String CYAN   = "#22E5FF";
    private static final String PURPLE = "#A78BFA";
    private static final String GREEN  = "#34D399";
    private static final String RED    = "#F87171";
    private static final String YELLOW = "#FBBF24";
    private static final String BG     = "#020617";
    private static final String CARD   = "#0B1426";

    private static Context ctxGlobal;

    public interface OnLogout {
        void onLogout();
    }

    // ===================== BUILD =====================
    public static View build(final Context ctx, final OnLogout onLogout) {
        ctxGlobal = ctx;

        FrameLayout root = new FrameLayout(ctx);
        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x44000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx, 16), dp(ctx, 20), dp(ctx, 16), dp(ctx, 80));
        scroll.addView(content, new LinearLayout.LayoutParams(-1, -2));

        boolean isDev   = ApiAuthHelper.isDeveloper(ctx);
        String role     = ApiAuthHelper.getRole(ctx);
        String apiKey   = ApiAuthHelper.getApiKey(ctx);
        String deviceId = ApiAuthHelper.getDeviceId(ctx);
        String keyName  = apiKey != null ? apiKey : "—";
        String roleColor = isDev ? CYAN : role.equalsIgnoreCase("Reseller") ? PURPLE : GREEN;

        // ── PROFILE CARD ──
        LinearLayout profileCard = buildCard(ctx,
            isDev ? 0x1522E5FF : role.equalsIgnoreCase("Reseller") ? 0x15A78BFA : 0x1534D399,
            roleColor);
        profileCard.setGravity(Gravity.CENTER_HORIZONTAL);
        profileCard.setPadding(dp(ctx, 20), dp(ctx, 28), dp(ctx, 20), dp(ctx, 24));

        // Avatar — gradient circle
        FrameLayout avatar = new FrameLayout(ctx);
        int avSize = dp(ctx, 80);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(avSize, avSize);
        avLp.gravity = Gravity.CENTER_HORIZONTAL;
        avLp.bottomMargin = dp(ctx, 16);
        avatar.setLayoutParams(avLp);

        // ✅ FIX: gradient avatar instead of flat color
        int[] gradColors = isDev
            ? new int[]{Color.parseColor("#0A1929"), Color.parseColor("#051540")}
            : role.equalsIgnoreCase("Reseller")
                ? new int[]{Color.parseColor("#150E2A"), Color.parseColor("#200A38")}
                : new int[]{Color.parseColor("#062A1A"), Color.parseColor("#03150D")};
        GradientDrawable avBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, gradColors);
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setStroke(dp(ctx, 2), Color.parseColor(roleColor));
        avatar.setBackground(avBg);

        // Glow ring animation on avatar
        final GradientDrawable avBgFinal = avBg;
        ValueAnimator avGlow = ValueAnimator.ofFloat(0.5f, 1.0f);
        avGlow.setDuration(1800);
        avGlow.setRepeatMode(ValueAnimator.REVERSE);
        avGlow.setRepeatCount(ValueAnimator.INFINITE);
        avGlow.setInterpolator(new AccelerateDecelerateInterpolator());
        avGlow.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                avBgFinal.setStroke(dp(ctx, 2), applyAlpha(Color.parseColor(
                    ctxGlobal != null && ApiAuthHelper.isDeveloper(ctxGlobal)
                        ? CYAN : PURPLE), (float) a.getAnimatedValue()));
            }
        });
        avGlow.start();

        TextView avIcon = new TextView(ctx);
        avIcon.setText(isDev ? "⚡" : role.equalsIgnoreCase("Reseller") ? "💼" : "🔑");
        avIcon.setTextSize(30f);
        avIcon.setGravity(Gravity.CENTER);
        avatar.addView(avIcon, new FrameLayout.LayoutParams(-1, -1));

        // Name
        TextView tvName = new TextView(ctx);
        tvName.setText(keyName);
        tvName.setTextColor(Color.parseColor("#E2E8F0"));
        tvName.setTextSize(18f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setGravity(Gravity.CENTER);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            tvName.setShadowLayer(12f, 0, 0, Color.parseColor(roleColor));
        }
        tvName.setMaxLines(1);
        tvName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.bottomMargin = dp(ctx, 8);
        tvName.setLayoutParams(nameLp);

        // Role badge
        TextView tvRole = new TextView(ctx);
        tvRole.setText("  " + role.toUpperCase() + "  ");
        tvRole.setTextColor(Color.parseColor(roleColor));
        tvRole.setTextSize(11f);
        tvRole.setTypeface(Typeface.DEFAULT_BOLD);
        tvRole.setLetterSpacing(0.12f);
        tvRole.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        tvRole.setLayoutParams(badgeLp);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(isDev ? Color.parseColor("#051520")
            : role.equalsIgnoreCase("Reseller") ? Color.parseColor("#100820")
            : Color.parseColor("#062A1A"));
        badgeBg.setCornerRadius(dp(ctx, 20));
        badgeBg.setStroke(dp(ctx, 1), Color.parseColor(roleColor));
        tvRole.setBackground(badgeBg);
        tvRole.setPadding(dp(ctx, 14), dp(ctx, 5), dp(ctx, 14), dp(ctx, 5));

        profileCard.addView(avatar);
        profileCard.addView(tvName);
        profileCard.addView(tvRole);
        content.addView(profileCard);
        animateCardIn(profileCard, ctx, 0);

        // ── STATS CARD (get_stats) ──
        content.addView(buildSectionTitle(ctx, "📊  STATISTIK"));

        final LinearLayout statsCard = buildCard(ctx, 0x0AFFFFFF, "#1E3A50");
        statsCard.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16));

        // Skeleton loading
        final LinearLayout statsLoading = new LinearLayout(ctx);
        statsLoading.setOrientation(LinearLayout.HORIZONTAL);
        statsLoading.setGravity(Gravity.CENTER);
        statsLoading.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));
        ProgressBar statsPb = new ProgressBar(ctx);
        TextView statsLoadTv = new TextView(ctx);
        statsLoadTv.setText("  Memuat stats...");
        statsLoadTv.setTextColor(Color.parseColor("#475569"));
        statsLoadTv.setTextSize(12f);
        statsLoadTv.setTypeface(Typeface.MONOSPACE);
        statsLoading.addView(statsPb);
        statsLoading.addView(statsLoadTv);
        statsCard.addView(statsLoading);

        content.addView(statsCard);
        animateCardIn(statsCard, ctx, 80);

        // ── SESSION INFO ──
        content.addView(buildSectionTitle(ctx, "🔐  SESSION INFO"));
        LinearLayout infoCard = buildCard(ctx, 0x0FFFFFFF, "#1E293B");
        infoCard.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        infoCard.addView(buildInfoRow(ctx, "🔑  Key", keyName, roleColor));
        infoCard.addView(buildDivider(ctx));
        infoCard.addView(buildInfoRow(ctx, "👤  Role", role, roleColor));
        infoCard.addView(buildDivider(ctx));
        infoCard.addView(buildInfoRow(ctx, "📱  Device ID",
            deviceId != null ? deviceId.substring(0, Math.min(deviceId.length(), 18)) + "..." : "—",
            "#64748B"));
        content.addView(infoCard);
        animateCardIn(infoCard, ctx, 160);

        // ── RECENT ACTIVITY ──
        content.addView(buildSectionTitle(ctx, "🕒  RECENT ACTIVITY"));

        final LinearLayout actCard = buildCard(ctx, 0x0AFFFFFF, "#1E3A50");
        actCard.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        content.addView(actCard);
        animateCardIn(actCard, ctx, 220);

        // Load recent activity dari ActivityLog (local)
        loadRecentActivity(ctx, actCard);

        // ── ACCOUNT ACTIONS ──
        content.addView(buildSectionTitle(ctx, "⚙️  AKUN"));

        TextView btnLogout = new TextView(ctx);
        btnLogout.setText("⏻   Logout");
        btnLogout.setTextColor(Color.parseColor(RED));
        btnLogout.setTextSize(14f);
        btnLogout.setTypeface(Typeface.DEFAULT_BOLD);
        btnLogout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(-1, dp(ctx, 52));
        logLp.topMargin = dp(ctx, 8);
        btnLogout.setLayoutParams(logLp);
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(Color.parseColor("#1A0808"));
        logBg.setCornerRadius(dp(ctx, 14));
        logBg.setStroke(dp(ctx, 1), Color.parseColor("#7F1D1D"));
        btnLogout.setBackground(logBg);
        attachPressScale(btnLogout);
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLogoutConfirm(ctx, onLogout); }
        });
        content.addView(btnLogout);
        animateCardIn(btnLogout, ctx, 300);

        // ✅ FIX: version text warna visible (#334155 instead of #1E293B yg invisible)
        TextView tvVersion = new TextView(ctx);
        tvVersion.setText("Xyper Key Manager  ·  v1.0");
        tvVersion.setTextColor(Color.parseColor("#334155"));
        tvVersion.setTextSize(10f);
        tvVersion.setGravity(Gravity.CENTER);
        tvVersion.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(-1, -2);
        verLp.topMargin = dp(ctx, 20);
        tvVersion.setLayoutParams(verLp);
        content.addView(tvVersion);

        // Load stats dari Workers
        loadStats(ctx, statsCard, statsLoading, isDev);

        return root;
    }

    // ===================== LOAD STATS (get_stats) =====================
    private static void loadStats(final Context ctx, final LinearLayout statsCard,
            final LinearLayout loadingView, final boolean isDev) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", "get_stats");

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (!response.isSuccessful()) return;
                        try {
                            final JSONObject stats = new JSONObject(response.body());
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    statsCard.removeAllViews();
                                    buildStatsUI(ctx, statsCard, stats, isDev);
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                    @Override
                    public void onFailure(Call<String> call, Throwable t) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override public void run() {
                                statsCard.removeAllViews();
                                TextView err = new TextView(ctx);
                                err.setText("Failed to load stats");
                                err.setTextColor(Color.parseColor("#475569"));
                                err.setTextSize(12f);
                                err.setGravity(Gravity.CENTER);
                                err.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));
                                statsCard.addView(err);
                            }
                        });
                    }
                });
        } catch (Exception ignored) {}
    }

    private static void buildStatsUI(Context ctx, LinearLayout card, JSONObject stats, boolean isDev) {
        int total   = stats.optInt("total", 0);
        int active  = stats.optInt("active", 0);
        int expired = stats.optInt("expired", 0);
        int banned  = stats.optInt("banned", 0);
        int violations = stats.optInt("violations", 0);

        JSONObject byRole = stats.optJSONObject("by_role");
        int devCount      = byRole != null ? byRole.optInt("developer", 0) : 0;
        int resellerCount = byRole != null ? byRole.optInt("reseller", 0) : 0;
        int clientCount   = byRole != null ? byRole.optInt("client", 0) : 0;

        // Stats grid 3 kolom: Total | Active | Expired
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(-1, -2);
        r1lp.bottomMargin = dp(ctx, 10);
        row1.setLayoutParams(r1lp);
        row1.addView(buildStatBox(ctx, String.valueOf(total), "Total", CYAN));
        row1.addView(buildStatBox(ctx, String.valueOf(active), "Active", GREEN));
        row1.addView(buildStatBox(ctx, String.valueOf(expired), "Expired", YELLOW));

        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        row2.addView(buildStatBox(ctx, String.valueOf(banned), "Banned", RED));
        row2.addView(buildStatBox(ctx, String.valueOf(violations), "Violations",
            violations > 0 ? RED : "#64748B"));
        if (isDev) {
            row2.addView(buildStatBox(ctx, devCount + "/" + resellerCount + "/" + clientCount,
                "Dev/Res/Cli", PURPLE));
        } else {
            row2.addView(buildStatBox(ctx, String.valueOf(clientCount), "Clients", PURPLE));
        }

        card.addView(row1);
        card.addView(row2);
    }

    private static LinearLayout buildStatBox(Context ctx, String value, String label, String colorHex) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(ctx, 8), dp(ctx, 12), dp(ctx, 8), dp(ctx, 12));
        GradientDrawable bg = new GradientDrawable();
        int c; try { c = Color.parseColor(colorHex); } catch (Exception e) { c = Color.parseColor(CYAN); }
        bg.setColor(Color.argb(20, Color.red(c), Color.green(c), Color.blue(c)));
        bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 1), Color.argb(60, Color.red(c), Color.green(c), Color.blue(c)));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = dp(ctx, 3);
        lp.rightMargin = dp(ctx, 3);
        box.setLayoutParams(lp);

        TextView tvVal = new TextView(ctx);
        tvVal.setText(value);
        tvVal.setTextColor(c);
        tvVal.setTextSize(20f);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setGravity(Gravity.CENTER);
        box.addView(tvVal);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#475569"));
        tvLabel.setTextSize(10f);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setTypeface(Typeface.MONOSPACE);
        box.addView(tvLabel);

        return box;
    }

    // ===================== RECENT ACTIVITY =====================
    private static void loadRecentActivity(final Context ctx, final LinearLayout card) {
        // Ambil dari ActivityLog (local storage)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                card.removeAllViews();
                List<ActivityLog.LogEntry> logs = ActivityLog.getRecentLogs(ctx, 5);
                if (logs == null || logs.isEmpty()) {
                    showActivityEmpty(ctx, card);
                    return;
                }
                for (ActivityLog.LogEntry entry : logs) {
                    card.addView(buildActivityRow(ctx, entry));
                }
            }
        }, 200);
    }

    private static void showActivityEmpty(Context ctx, LinearLayout card) {
        LinearLayout empty = new LinearLayout(ctx);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(ctx, 16), 0, dp(ctx, 16));

        TextView icon = new TextView(ctx);
        icon.setText("📋");
        icon.setTextSize(28f);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon);

        TextView tv = new TextView(ctx);
        tv.setText("No activity yet");
        tv.setTextColor(Color.parseColor("#334155"));
        tv.setTextSize(12f);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(ctx, 6);
        tv.setLayoutParams(lp);
        empty.addView(tv);

        card.addView(empty);
    }

    private static View buildActivityRow(Context ctx, ActivityLog.LogEntry entry) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 2);
        row.setLayoutParams(lp);

        // Dot indicator
        View dot = new View(ctx);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor(CYAN));
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 6), dp(ctx, 6));
        dotLp.rightMargin = dp(ctx, 10);
        dotLp.gravity = Gravity.CENTER_VERTICAL;
        dot.setLayoutParams(dotLp);
        row.addView(dot);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tvAction = new TextView(ctx);
        tvAction.setText(entry.action);
        tvAction.setTextColor(Color.parseColor("#CBD5E1"));
        tvAction.setTextSize(12f);
        tvAction.setTypeface(Typeface.DEFAULT_BOLD);
        tvAction.setMaxLines(1);
        tvAction.setEllipsize(TextUtils.TruncateAt.END);
        textWrap.addView(tvAction);

        if (entry.target != null && !entry.target.isEmpty()) {
            TextView tvTarget = new TextView(ctx);
            tvTarget.setText(entry.target);
            tvTarget.setTextColor(Color.parseColor("#475569"));
            tvTarget.setTextSize(11f);
            tvTarget.setTypeface(Typeface.MONOSPACE);
            tvTarget.setMaxLines(1);
            tvTarget.setEllipsize(TextUtils.TruncateAt.END);
            textWrap.addView(tvTarget);
        }
        row.addView(textWrap);

        TextView tvTime = new TextView(ctx);
        tvTime.setText(formatTimeShort(entry.timestamp));
        tvTime.setTextColor(Color.parseColor("#334155"));
        tvTime.setTextSize(10f);
        tvTime.setTypeface(Typeface.MONOSPACE);
        row.addView(tvTime);

        return row;
    }

    // ===================== LOGOUT CONFIRM =====================
    private static void showLogoutConfirm(final Context ctx, final OnLogout onLogout) {
        final android.app.Dialog dialog = new android.app.Dialog(ctx,
            android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout dimRoot = new FrameLayout(ctx);
        View dim = new View(ctx);
        dim.setBackgroundColor(0xAA000000);
        dim.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        dim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        dimRoot.addView(dim);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 24), dp(ctx, 24), dp(ctx, 24), dp(ctx, 24));
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(Color.parseColor("#0A1628"));
        cBg.setCornerRadius(dp(ctx, 18));
        cBg.setStroke(dp(ctx, 1), Color.parseColor("#7F1D1D"));
        card.setBackground(cBg);
        card.setElevation(30f);

        TextView tvIcon = new TextView(ctx);
        tvIcon.setText("⏻");
        tvIcon.setTextSize(36f);
        tvIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(-1, -2);
        iconLp.bottomMargin = dp(ctx, 10);
        tvIcon.setLayoutParams(iconLp);
        card.addView(tvIcon);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("Logout?");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(-1, -2);
        ttlp.bottomMargin = dp(ctx, 8);
        tvTitle.setLayoutParams(ttlp);
        card.addView(tvTitle);

        TextView tvMsg = new TextView(ctx);
        tvMsg.setText("Session will be cleared.\nYou will need to log in again.");
        tvMsg.setTextColor(Color.parseColor("#64748B"));
        tvMsg.setTextSize(13f);
        tvMsg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.bottomMargin = dp(ctx, 20);
        tvMsg.setLayoutParams(msgLp);
        card.addView(tvMsg);

        View divv = new View(ctx);
        divv.setBackgroundColor(0x3322E5FF);
        LinearLayout.LayoutParams dvLp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        dvLp.bottomMargin = dp(ctx, 16);
        divv.setLayoutParams(dvLp);
        card.addView(divv);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        TextView btnCancel = new TextView(ctx);
        btnCancel.setText("Batal");
        btnCancel.setTextColor(Color.parseColor("#64748B"));
        btnCancel.setTextSize(14f);
        btnCancel.setTypeface(Typeface.DEFAULT_BOLD);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding(dp(ctx, 12), dp(ctx, 14), dp(ctx, 12), dp(ctx, 14));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(0x0FFFFFFF);
        cancelBg.setCornerRadius(dp(ctx, 12));
        cancelBg.setStroke(dp(ctx, 1), Color.parseColor("#1E293B"));
        btnCancel.setBackground(cancelBg);
        btnCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        attachPressScale(btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        btnRow.addView(btnCancel);

        View sp = new View(ctx);
        sp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 10), 0));
        btnRow.addView(sp);

        TextView btnConfirm = new TextView(ctx);
        btnConfirm.setText("⏻  Logout");
        btnConfirm.setTextColor(Color.parseColor("#001A2E"));
        btnConfirm.setTextSize(14f);
        btnConfirm.setTypeface(Typeface.DEFAULT_BOLD);
        btnConfirm.setGravity(Gravity.CENTER);
        btnConfirm.setPadding(dp(ctx, 12), dp(ctx, 14), dp(ctx, 12), dp(ctx, 14));
        GradientDrawable confirmBg = new GradientDrawable();
        confirmBg.setColor(Color.parseColor(RED));
        confirmBg.setCornerRadius(dp(ctx, 12));
        btnConfirm.setBackground(confirmBg);
        btnConfirm.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        attachPressScale(btnConfirm);
        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                ActivityLog.log(ctx, "logout", ApiAuthHelper.getRole(ctx));
                ApiAuthHelper.clearSession(ctx);
                ApiClient.reset();
                if (onLogout != null) onLogout.onLogout();
            }
        });
        btnRow.addView(btnConfirm);
        card.addView(btnRow);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cardLp.leftMargin = dp(ctx, 28);
        cardLp.rightMargin = dp(ctx, 28);
        dimRoot.addView(card, cardLp);

        dialog.setContentView(dimRoot);
        card.setAlpha(0f);
        card.setScaleX(0.88f);
        card.setScaleY(0.88f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        dialog.show();
    }

    // ===================== UI HELPERS =====================
    private static LinearLayout buildCard(Context ctx, int fillColor, String strokeHex) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(ctx, 6);
        card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(ctx, 16));
        try { bg.setStroke(dp(ctx, 1), Color.parseColor(strokeHex)); }
        catch (Exception e) { bg.setStroke(dp(ctx, 1), Color.parseColor("#1E293B")); }
        card.setBackground(bg);
        return card;
    }

    private static View buildInfoRow(Context ctx, String label, String value, String valueColor) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(ctx, 5);
        lp.bottomMargin = dp(ctx, 5);
        row.setLayoutParams(lp);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#64748B"));
        tvLabel.setTextSize(12f);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(tvLabel);

        TextView tvValue = new TextView(ctx);
        tvValue.setText(value);
        try { tvValue.setTextColor(Color.parseColor(valueColor)); }
        catch (Exception e) { tvValue.setTextColor(Color.parseColor(CYAN)); }
        tvValue.setTextSize(12f);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
        tvValue.setMaxLines(1);
        tvValue.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tvValue);

        return row;
    }

    private static View buildDivider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Color.parseColor("#0F1E30"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        lp.topMargin = dp(ctx, 4);
        lp.bottomMargin = dp(ctx, 4);
        v.setLayoutParams(lp);
        return v;
    }

    private static TextView buildSectionTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#334155"));
        tv.setTextSize(10f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(ctx, 20);
        lp.bottomMargin = dp(ctx, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static void animateCardIn(final View v, Context ctx, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(ctx, 20));
        v.postDelayed(new Runnable() {
            @Override public void run() {
                v.animate().alpha(1f).translationY(0f)
                    .setDuration(350)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            }
        }, delayMs);
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

    private static String formatTimeShort(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60000) return "baru saja";
        if (diff < 3600000) return (diff / 60000) + " mnt lalu";
        if (diff < 86400000) return (diff / 3600000) + " jam lalu";
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(millis));
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }
}
