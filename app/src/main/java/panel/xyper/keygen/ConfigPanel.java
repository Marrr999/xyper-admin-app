package panel.xyper.keygen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
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
import android.widget.Toast;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Response;

/**
 * ConfigPanel — UI untuk Developer mengatur config server
 * Fields: motd, server_status, version_code, version_name,
 *         update_url, force_update, ban_threshold
 */
public class ConfigPanel {

    private static final String CYAN   = "#22E5FF";
    private static final String RED    = "#F87171";
    private static final String GREEN  = "#34D399";
    private static final String YELLOW = "#FBBF24";
    private static final String PURPLE = "#A78BFA";
    private static final String BG     = "#020617";

    // State config
    private static String  curMotd         = "";
    private static String  curServerStatus = "ONLINE";
    private static int     curVersionCode  = 1;
    private static String  curVersionName  = "1.0";
    private static String  curUpdateUrl    = "";
    private static boolean curForceUpdate  = false;
    private static int     curBanThreshold = 0;

    // Value TextViews untuk live update
    private static TextView tvMotdVal;
    private static TextView tvStatusVal;
    private static android.widget.LinearLayout motdPreviewRef = null;
    private static android.widget.TextView motdPreviewTextRef = null;
    private static TextView tvVerCodeVal;
    private static TextView tvVerNameVal;
    private static TextView tvUpdateUrlVal;
    private static TextView tvForceUpdateVal;
    private static TextView tvBanThresholdVal;

    private static LinearLayout contentLayout;
    private static LinearLayout skeletonLayout;
    private static Context ctxGlobal;

    // ===================== BUILD =====================
    public static View build(final Context ctx) {
        ctxGlobal = ctx;

        FrameLayout root = new FrameLayout(ctx);

        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x44000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // ✅ FIX: full-width bukan fixed 320dp
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(ctx, 16), dp(ctx, 20), dp(ctx, 16), dp(ctx, 80));
        scroll.addView(outer, new LinearLayout.LayoutParams(-1, -2));

        // ── HEADER ──
        outer.addView(buildHeader(ctx));

        // ── SKELETON (loading placeholder) ──
        skeletonLayout = buildSkeleton(ctx);
        outer.addView(skeletonLayout);

        // ── CONTENT (hidden until loaded) ──
        contentLayout = new LinearLayout(ctx);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setVisibility(View.GONE);
        outer.addView(contentLayout);

        loadConfig(ctx);
        return root;
    }

    // ── HEADER ──
    private static View buildHeader(Context ctx) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 16);
        header.setLayoutParams(lp);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(ctx);
        title.setText("Server Config");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            title.setShadowLayer(14f, 0, 0, Color.parseColor(CYAN));
        }

        TextView sub = new TextView(ctx);
        sub.setText("Developer Only  ·  Xyper Xit");
        sub.setTextSize(10f);
        sub.setTextColor(Color.parseColor("#38BDF8"));
        sub.setTypeface(Typeface.MONOSPACE);

        textWrap.addView(title);
        textWrap.addView(sub);
        header.addView(textWrap);

        // Refresh button di header
        TextView btnRef = new TextView(ctx);
        btnRef.setText("↻");
        btnRef.setTextSize(20f);
        btnRef.setTextColor(Color.parseColor(CYAN));
        btnRef.setGravity(Gravity.CENTER);
        btnRef.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
        GradientDrawable refBg = new GradientDrawable();
        refBg.setColor(0x1522E5FF);
        refBg.setCornerRadius(dp(ctx, 10));
        refBg.setStroke(dp(ctx, 1), Color.parseColor("#22455A"));
        btnRef.setBackground(refBg);
        attachPressScale(btnRef);
        btnRef.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (skeletonLayout != null) skeletonLayout.setVisibility(View.VISIBLE);
                if (contentLayout != null) contentLayout.setVisibility(View.GONE);
                loadConfig(ctxGlobal);
            }
        });
        header.addView(btnRef);
        return header;
    }

    // ── SKELETON ──
    private static LinearLayout buildSkeleton(Context ctx) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        String[] sections = {"🌐  SERVER STATUS", "💬  MOTD", "📦  VERSION", "🔄  UPDATE", "🛡️  AUTO-BAN"};
        int[] rowsPerSection = {1, 1, 2, 2, 1};

        for (int s = 0; s < sections.length; s++) {
            // Section label skeleton
            View secSkel = new View(ctx);
            GradientDrawable secBg = new GradientDrawable();
            secBg.setColor(Color.parseColor("#1E293B"));
            secBg.setCornerRadius(dp(ctx, 4));
            secSkel.setBackground(secBg);
            LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(dp(ctx, 120), dp(ctx, 10));
            secLp.topMargin = dp(ctx, 18);
            secLp.bottomMargin = dp(ctx, 8);
            secSkel.setLayoutParams(secLp);
            wrap.addView(secSkel);

            for (int r = 0; r < rowsPerSection[s]; r++) {
                LinearLayout rowSkel = new LinearLayout(ctx);
                rowSkel.setOrientation(LinearLayout.HORIZONTAL);
                rowSkel.setGravity(Gravity.CENTER_VERTICAL);
                rowSkel.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setColor(Color.parseColor("#0B1426"));
                rowBg.setCornerRadius(dp(ctx, 12));
                rowBg.setStroke(dp(ctx, 1), Color.parseColor("#1E293B"));
                rowSkel.setBackground(rowBg);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = dp(ctx, 6);
                rowSkel.setLayoutParams(rowLp);

                View l1 = makeSkelLine(ctx, 0.35f);
                View l2 = makeSkelLine(ctx, 0.3f);
                rowSkel.addView(l1);
                View sp = new View(ctx);
                sp.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
                rowSkel.addView(sp);
                rowSkel.addView(l2);
                wrap.addView(rowSkel);
            }
        }

        // Shimmer animation on whole skeleton
        final LinearLayout wrapFinal = wrap;
        android.animation.ValueAnimator shimmer = android.animation.ValueAnimator.ofFloat(0.4f, 0.8f);
        shimmer.setDuration(800);
        shimmer.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        shimmer.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        shimmer.setInterpolator(new AccelerateDecelerateInterpolator());
        shimmer.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                wrapFinal.setAlpha((float) a.getAnimatedValue());
            }
        });
        shimmer.start();
        return wrap;
    }

    private static View makeSkelLine(Context ctx, float ratio) {
        View v = new View(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(ctx, 4));
        v.setBackground(bg);
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int w = (int) ((screenW - dp(ctx, 56)) * ratio);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, dp(ctx, 10)));
        return v;
    }

    // ===================== LOAD CONFIG =====================
    private static void loadConfig(final Context ctx) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", "get_config");

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (!response.isSuccessful()) {
                            showToast(ctx, "Failed to load configuration — HTTP " + response.code());
                            showErrorState(ctx, "Failed to load configuration\nHTTP " + response.code());
                            return;
                        }
                        try {
                            JSONObject cfg = new JSONObject(response.body());
                            curMotd         = cfg.optString("motd", "");
                            curServerStatus = cfg.optString("server_status", "ONLINE");
                            curVersionCode  = cfg.optInt("version_code", 1);
                            curVersionName  = cfg.optString("version_name", "1.0");
                            curUpdateUrl    = cfg.optString("update_url", "");
                            curForceUpdate  = cfg.optBoolean("force_update", false);
                            curBanThreshold = cfg.optInt("ban_threshold", 0);

                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    if (skeletonLayout != null) skeletonLayout.setVisibility(View.GONE);
                                    buildContent(ctx);
                                }
                            });
                        } catch (Exception e) {
                            showToast(ctx, "Failed to parse server response");
                            showErrorState(ctx, "Parse error");
                        }
                    }
                    @Override
                    public void onFailure(Call<String> call, Throwable t) {
                        showToast(ctx, "An error occurred: " + t.getMessage());
                        showErrorState(ctx, "No internet connection\nPlease check your network");
                    }
                });
        } catch (Exception e) {
            showToast(ctx, "An error occurred: " + e.getMessage());
        }
    }

    private static void showErrorState(final Context ctx, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                if (skeletonLayout != null) skeletonLayout.setVisibility(View.GONE);
                if (contentLayout == null) return;
                contentLayout.removeAllViews();
                contentLayout.setVisibility(View.VISIBLE);

                LinearLayout errCard = new LinearLayout(ctx);
                errCard.setOrientation(LinearLayout.VERTICAL);
                errCard.setGravity(Gravity.CENTER);
                errCard.setPadding(dp(ctx, 20), dp(ctx, 32), dp(ctx, 20), dp(ctx, 32));
                GradientDrawable errBg = new GradientDrawable();
                errBg.setColor(Color.parseColor("#1A0808"));
                errBg.setCornerRadius(dp(ctx, 16));
                errBg.setStroke(dp(ctx, 1), Color.parseColor("#7F1D1D"));
                errCard.setBackground(errBg);
                errCard.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

                TextView errIcon = new TextView(ctx);
                errIcon.setText("⚠️");
                errIcon.setTextSize(36f);
                errIcon.setGravity(Gravity.CENTER);
                errCard.addView(errIcon);

                TextView errTv = new TextView(ctx);
                errTv.setText(msg);
                errTv.setTextColor(Color.parseColor("#F87171"));
                errTv.setTextSize(13f);
                errTv.setGravity(Gravity.CENTER);
                errTv.setTypeface(Typeface.MONOSPACE);
                LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(-1, -2);
                errLp.topMargin = dp(ctx, 10);
                errLp.bottomMargin = dp(ctx, 16);
                errTv.setLayoutParams(errLp);
                errCard.addView(errTv);

                TextView btnRetry = new TextView(ctx);
                btnRetry.setText("↻  Coba Lagi");
                btnRetry.setTextColor(Color.parseColor(CYAN));
                btnRetry.setTextSize(13f);
                btnRetry.setTypeface(Typeface.DEFAULT_BOLD);
                btnRetry.setGravity(Gravity.CENTER);
                btnRetry.setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10));
                GradientDrawable retryBg = new GradientDrawable();
                retryBg.setColor(0x1522E5FF);
                retryBg.setCornerRadius(dp(ctx, 10));
                retryBg.setStroke(dp(ctx, 1), Color.parseColor("#22455A"));
                btnRetry.setBackground(retryBg);
                attachPressScale(btnRetry);
                btnRetry.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        contentLayout.setVisibility(View.GONE);
                        if (skeletonLayout != null) skeletonLayout.setVisibility(View.VISIBLE);
                        loadConfig(ctx);
                    }
                });
                errCard.addView(btnRetry);
                contentLayout.addView(errCard);
            }
        });
    }

    // ===================== BUILD CONTENT =====================
    private static void buildContent(final Context ctx) {
        if (contentLayout == null) return;
        contentLayout.removeAllViews();
        contentLayout.setVisibility(View.VISIBLE);

        int delay = 0;

        // ── STATUS SERVER ──
        contentLayout.addView(buildSectionTitle(ctx, "🌐  SERVER STATUS"));

        LinearLayout statusRow = buildSettingRow(ctx, "Status", curServerStatus,
            getStatusColor(curServerStatus),
            new View.OnClickListener() {
                @Override public void onClick(View v) { showStatusSheet(ctx); }
            });
        tvStatusVal = (TextView) statusRow.getTag();
        contentLayout.addView(statusRow);
        animateIn(statusRow, ctx, delay += 40);

        // ── MOTD ──
        contentLayout.addView(buildSectionTitle(ctx, "💬  MESSAGE OF THE DAY"));

        LinearLayout motdRow = buildSettingRow(ctx, "MOTD",
            curMotd.isEmpty() ? "(empty)" : curMotd, "#94A3B8",
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showMotdEditSheet(ctx);
                }
            });
        tvMotdVal = (TextView) motdRow.getTag();
        contentLayout.addView(motdRow);
        animateIn(motdRow, ctx, delay += 40);

        // ── MOTD Preview Box ──
        final LinearLayout motdPreview = new LinearLayout(ctx);
        motdPreview.setOrientation(LinearLayout.VERTICAL);
        motdPreview.setPadding(dp(ctx,14), dp(ctx,12), dp(ctx,14), dp(ctx,12));
        GradientDrawable prevBg = new GradientDrawable();
        prevBg.setColor(0x1522E5FF);
        prevBg.setCornerRadius(dp(ctx,12));
        prevBg.setStroke(dp(ctx,1), 0x5522E5FF);
        motdPreview.setBackground(prevBg);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(-1,-2);
        prevLp.bottomMargin = dp(ctx,8); motdPreview.setLayoutParams(prevLp);
        motdPreview.setVisibility(curMotd.isEmpty() ? View.GONE : View.VISIBLE);

        TextView prevLabel = new TextView(ctx);
        prevLabel.setText("👁  Preview (as seen by users)");
        prevLabel.setTextColor(0x8822E5FF);
        prevLabel.setTextSize(10f); prevLabel.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams plLp = new LinearLayout.LayoutParams(-1,-2);
        plLp.bottomMargin = dp(ctx,6); prevLabel.setLayoutParams(plLp);
        motdPreview.addView(prevLabel);

        final TextView prevText = new TextView(ctx);
        prevText.setText(curMotd.isEmpty() ? "" : curMotd);
        prevText.setTextColor(Color.WHITE);
        prevText.setTextSize(13f); prevText.setTypeface(Typeface.DEFAULT);
        prevText.setLineSpacing(dp(ctx,2), 1f);
        motdPreview.addView(prevText);

        motdPreviewRef = motdPreview;
        motdPreviewTextRef = prevText;
        contentLayout.addView(motdPreview);
        animateIn(motdPreview, ctx, delay += 40);

        // ── VERSION ──
        contentLayout.addView(buildSectionTitle(ctx, "📦  VERSION"));

        LinearLayout verCodeRow = buildSettingRow(ctx, "Version Code",
            String.valueOf(curVersionCode), YELLOW,
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showTextInputSheet(ctx, "Version Code", "contoh: 12",
                        String.valueOf(curVersionCode), true,
                        new OnInputDone() {
                            @Override public void onDone(String val) {
                                try { saveConfig(ctx, "version_code", null, null, false,
                                    Integer.parseInt(val)); } catch (Exception ignored) {}
                            }
                        });
                }
            });
        tvVerCodeVal = (TextView) verCodeRow.getTag();
        contentLayout.addView(verCodeRow);
        animateIn(verCodeRow, ctx, delay += 40);

        LinearLayout verNameRow = buildSettingRow(ctx, "Version Name", curVersionName, YELLOW,
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showTextInputSheet(ctx, "Version Name", "contoh: 1.2.3", curVersionName, false,
                        new OnInputDone() {
                            @Override public void onDone(String val) {
                                saveConfig(ctx, "version_name", val, null, false, 0);
                            }
                        });
                }
            });
        tvVerNameVal = (TextView) verNameRow.getTag();
        contentLayout.addView(verNameRow);
        animateIn(verNameRow, ctx, delay += 40);

        // ── UPDATE ──
        contentLayout.addView(buildSectionTitle(ctx, "🔄  UPDATE"));

        LinearLayout forceRow = buildSettingRow(ctx, "Force Update",
            curForceUpdate ? "ON" : "OFF", curForceUpdate ? RED : GREEN,
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean newVal = !curForceUpdate;
                    saveConfig(ctx, "force_update", null, newVal, true, 0);
                }
            });
        tvForceUpdateVal = (TextView) forceRow.getTag();
        contentLayout.addView(forceRow);
        animateIn(forceRow, ctx, delay += 40);

        LinearLayout urlRow = buildSettingRow(ctx, "Update URL",
            curUpdateUrl.isEmpty() ? "(empty)" : shortenUrl(curUpdateUrl), "#64748B",
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showTextInputSheet(ctx, "Update URL", "https://...", curUpdateUrl, false,
                        new OnInputDone() {
                            @Override public void onDone(String val) {
                                saveConfig(ctx, "update_url", val, null, false, 0);
                            }
                        });
                }
            });
        tvUpdateUrlVal = (TextView) urlRow.getTag();
        contentLayout.addView(urlRow);
        animateIn(urlRow, ctx, delay += 40);

        // ── AUTO-BAN ──
        contentLayout.addView(buildSectionTitle(ctx, "🛡️  AUTO-BAN"));

        LinearLayout banRow = buildSettingRow(ctx, "Ban Threshold",
            curBanThreshold == 0 ? "Disabled" : curBanThreshold + " violations",
            curBanThreshold == 0 ? "#64748B" : RED,
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showTextInputSheet(ctx, "Ban Threshold", "0 = disabled, e.g. 5",
                        String.valueOf(curBanThreshold), true,
                        new OnInputDone() {
                            @Override public void onDone(String val) {
                                try { saveConfig(ctx, "ban_threshold", null, null, false,
                                    Integer.parseInt(val)); } catch (Exception ignored) {}
                            }
                        });
                }
            });
        tvBanThresholdVal = (TextView) banRow.getTag();
        contentLayout.addView(banRow);
        animateIn(banRow, ctx, delay += 40);
    }

    // ===================== SAVE CONFIG =====================
    private static void showMotdEditSheet(final Context ctx) {
        showTextInputSheet(ctx, "Edit MOTD", "Server message...", curMotd, false,
            new OnInputDone() {
                @Override public void onDone(String val) {
                    curMotd = val;
                    saveConfig(ctx, "motd", val, null, false, 0);
                    // Update preview
                    if (motdPreviewRef != null) {
                        if (val.isEmpty()) {
                            motdPreviewRef.setVisibility(android.view.View.GONE);
                        } else {
                            motdPreviewRef.setVisibility(android.view.View.VISIBLE);
                            if (motdPreviewTextRef != null) motdPreviewTextRef.setText(val);
                        }
                    }
                }
            });
    }

        private static void saveConfig(final Context ctx, final String field,
            final String strVal, final Boolean boolVal,
            final boolean isBool, final int intVal) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", "set_config");

            if (isBool && boolVal != null) payload.put(field, (boolean) boolVal);
            else if (strVal != null)        payload.put(field, strVal);
            else                            payload.put(field, intVal);

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (response.isSuccessful()) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    applyLocalUpdate(ctx, field, strVal, boolVal, isBool, intVal);
                                    showToast(ctx, field + " saved successfully");
                                }
                            });
                        } else {
                            showToast(ctx, "Failed to save — HTTP " + response.code());
                        }
                    }
                    @Override
                    public void onFailure(Call<String> call, Throwable t) {
                        showToast(ctx, "An error occurred: " + t.getMessage());
                    }
                });
        } catch (Exception e) {
            showToast(ctx, "An error occurred: " + e.getMessage());
        }
    }

    private static void applyLocalUpdate(Context ctx, String field, String strVal,
            Boolean boolVal, boolean isBool, int intVal) {
        switch (field) {
            case "motd":
                curMotd = strVal;
                if (tvMotdVal != null) tvMotdVal.setText(curMotd.isEmpty() ? "(empty)" : curMotd);
                break;
            case "server_status":
                curServerStatus = strVal;
                if (tvStatusVal != null) {
                    tvStatusVal.setText(curServerStatus);
                    tvStatusVal.setTextColor(Color.parseColor(getStatusColor(curServerStatus)));
                }
                break;
            case "version_code":
                curVersionCode = intVal;
                if (tvVerCodeVal != null) tvVerCodeVal.setText(String.valueOf(curVersionCode));
                break;
            case "version_name":
                curVersionName = strVal;
                if (tvVerNameVal != null) tvVerNameVal.setText(curVersionName);
                break;
            case "update_url":
                curUpdateUrl = strVal;
                if (tvUpdateUrlVal != null)
                    tvUpdateUrlVal.setText(curUpdateUrl.isEmpty() ? "(empty)" : shortenUrl(curUpdateUrl));
                break;
            case "force_update":
                curForceUpdate = isBool && boolVal != null && boolVal;
                if (tvForceUpdateVal != null) {
                    tvForceUpdateVal.setText(curForceUpdate ? "ON" : "OFF");
                    tvForceUpdateVal.setTextColor(Color.parseColor(curForceUpdate ? RED : GREEN));
                }
                break;
            case "ban_threshold":
                curBanThreshold = intVal;
                if (tvBanThresholdVal != null) {
                    tvBanThresholdVal.setText(curBanThreshold == 0 ? "Disabled" : curBanThreshold + " violations");
                    tvBanThresholdVal.setTextColor(Color.parseColor(curBanThreshold == 0 ? "#64748B" : RED));
                }
                break;
        }
    }

    // ===================== DIALOGS & SHEETS =====================

    // Status picker — bottom sheet style
    private static void showStatusSheet(final Context ctx) {
        final String[] opts   = {"ONLINE", "OFFLINE", "MAINTENANCE"};
        final String[] colors = {GREEN,    RED,       YELLOW};
        final String[] icons  = {"🟢",    "🔴",      "🟡"};

        android.app.Dialog dialog = new android.app.Dialog(ctx,
            android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(ctx);
        final android.app.Dialog df = dialog;

        View dim = new View(ctx);
        dim.setBackgroundColor(0xAA000000);
        dim.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        dim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { df.dismiss(); }
        });
        root.addView(dim);

        LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(ctx, 16), dp(ctx, 20), dp(ctx, 16), dp(ctx, 32));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.parseColor("#0A1628"));
        sBg.setCornerRadii(new float[]{dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20),0,0,0,0});
        sBg.setStroke(dp(ctx, 1), Color.parseColor("#1A3A50"));
        sheet.setBackground(sBg);
        FrameLayout.LayoutParams shLp = new FrameLayout.LayoutParams(-1, -2);
        shLp.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(shLp);

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
        tvTitle.setText("Server Status");
        tvTitle.setTextColor(Color.parseColor(CYAN));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(-1, -2);
        ttLp.bottomMargin = dp(ctx, 14);
        tvTitle.setLayoutParams(ttLp);
        sheet.addView(tvTitle);

        for (int i = 0; i < opts.length; i++) {
            final String opt = opts[i];
            final String col = colors[i];
            final String ico = icons[i];
            boolean sel = opt.equals(curServerStatus);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
            if (sel) {
                GradientDrawable rBg = new GradientDrawable();
                int c; try { c = Color.parseColor(col); } catch (Exception e) { c = Color.parseColor(CYAN); }
                rBg.setColor(Color.argb(20, Color.red(c), Color.green(c), Color.blue(c)));
                rBg.setCornerRadius(dp(ctx, 10));
                rBg.setStroke(dp(ctx, 1), c);
                row.setBackground(rBg);
            }
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
            rLp.bottomMargin = dp(ctx, 4);
            row.setLayoutParams(rLp);

            TextView tvIco = new TextView(ctx);
            tvIco.setText(ico + "  ");
            tvIco.setTextSize(16f);
            row.addView(tvIco);

            TextView tvOpt = new TextView(ctx);
            tvOpt.setText(opt);
            try { tvOpt.setTextColor(Color.parseColor(col)); }
            catch (Exception e) { tvOpt.setTextColor(Color.WHITE); }
            tvOpt.setTextSize(14f);
            tvOpt.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tvOpt.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(tvOpt);

            if (sel) {
                TextView chk = new TextView(ctx);
                chk.setText("✓");
                try { chk.setTextColor(Color.parseColor(col)); }
                catch (Exception e) { chk.setTextColor(Color.WHITE); }
                chk.setTextSize(16f);
                row.addView(chk);
            }

            attachPressScale(row);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    df.dismiss();
                    saveConfig(ctx, "server_status", opt, null, false, 0);
                }
            });
            sheet.addView(row);
        }

        root.addView(sheet);
        dialog.setContentView(root);
        sheet.setTranslationY(500f);
        sheet.animate().translationY(0).setDuration(280)
            .setInterpolator(new DecelerateInterpolator(2f)).start();
        dialog.show();
    }

    // Text input — bottom sheet style
    private static void showTextInputSheet(final Context ctx, final String title,
            final String hint, final String current, final boolean numeric,
            final OnInputDone callback) {

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

        LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(ctx, 16), dp(ctx, 20), dp(ctx, 16), dp(ctx, 32));
        GradientDrawable shBg = new GradientDrawable();
        shBg.setColor(Color.parseColor("#0A1628"));
        shBg.setCornerRadii(new float[]{dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20),0,0,0,0});
        shBg.setStroke(dp(ctx, 1), Color.parseColor("#1A3A50"));
        sheet.setBackground(shBg);
        FrameLayout.LayoutParams shLp = new FrameLayout.LayoutParams(-1, -2);
        shLp.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(shLp);

        // Handle
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
        tvTitle.setTextColor(Color.parseColor(CYAN));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(-1, -2);
        ttLp.bottomMargin = dp(ctx, 14);
        tvTitle.setLayoutParams(ttLp);
        sheet.addView(tvTitle);

        final EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setText(current);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0x6622E5FF);
        et.setTextSize(14f);
        et.setTypeface(Typeface.MONOSPACE);
        if (numeric) et.setInputType(InputType.TYPE_CLASS_NUMBER);
        else et.setSingleLine(!title.equals("Edit MOTD"));
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0x1A22E5FF);
        etBg.setCornerRadius(dp(ctx, 12));
        etBg.setStroke(dp(ctx, 1), 0x5522E5FF);
        et.setBackground(etBg);
        et.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        // Focus border
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(hasFocus ? 0x2522E5FF : 0x1A22E5FF);
                bg.setCornerRadius(dp(ctx, 12));
                bg.setStroke(hasFocus ? dp(ctx, 2) : dp(ctx, 1),
                    hasFocus ? 0xFF22E5FF : 0x5522E5FF);
                et.setBackground(bg);
                et.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
            }
        });
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(-1, -2);
        etLp.bottomMargin = dp(ctx, 14);
        et.setLayoutParams(etLp);
        sheet.addView(et);

        // Buttons row
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnCancel = buildSheetBtn(ctx, "Cancel", "#64748B", 0x0FFFFFFF, "#1E293B");
        btnCancel.setLayoutParams(new LinearLayout.LayoutParams(0, dp(ctx, 48), 1f));
        attachPressScale(btnCancel);
        final android.app.Dialog df = dialog;
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { df.dismiss(); }
        });
        btnRow.addView(btnCancel);

        View sp = new View(ctx);
        sp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 10), 0));
        btnRow.addView(sp);

        TextView btnSave = buildSheetBtn(ctx, "✅ Save", "#001A2E",
            Color.parseColor(CYAN), CYAN);
        btnSave.setLayoutParams(new LinearLayout.LayoutParams(0, dp(ctx, 48), 1f));
        attachPressScale(btnSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String val = et.getText().toString().trim();
                if (!val.isEmpty()) {
                    df.dismiss();
                    callback.onDone(val);
                }
            }
        });
        btnRow.addView(btnSave);
        sheet.addView(btnRow);

        dimRoot.addView(sheet);
        dialog.setContentView(dimRoot);
        sheet.setTranslationY(500f);
        sheet.animate().translationY(0).setDuration(280)
            .setInterpolator(new DecelerateInterpolator(2f)).start();
        dialog.show();

        // Auto-focus keyboard
        et.post(new Runnable() {
            @Override public void run() {
                et.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) ctx
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private static TextView buildSheetBtn(Context ctx, String text, String textColor,
            int bgColor, String borderColor) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextSize(14f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        try { btn.setTextColor(Color.parseColor(textColor)); }
        catch (Exception e) { btn.setTextColor(Color.WHITE); }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(ctx, 12));
        try { bg.setStroke(dp(ctx, 1), Color.parseColor(borderColor)); }
        catch (Exception e) { bg.setStroke(dp(ctx, 1), Color.parseColor(CYAN)); }
        btn.setBackground(bg);
        return btn;
    }

    // ===================== UI HELPERS =====================
    private static LinearLayout buildSettingRow(Context ctx, String label, String value,
            String valueColor, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 14), dp(ctx, 13), dp(ctx, 14), dp(ctx, 13));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = dp(ctx, 6);
        row.setLayoutParams(rowLp);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#0B1426"));
        rowBg.setCornerRadius(dp(ctx, 12));
        rowBg.setStroke(dp(ctx, 1), Color.parseColor("#1E3A50"));
        row.setBackground(rowBg);

        attachPressScale(row);

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#CBD5E1"));
        tvLabel.setTextSize(13f);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(tvLabel);

        TextView tvValue = new TextView(ctx);
        tvValue.setText(value);
        try { tvValue.setTextColor(Color.parseColor(valueColor)); }
        catch (Exception e) { tvValue.setTextColor(Color.parseColor("#94A3B8")); }
        tvValue.setTextSize(13f);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
        tvValue.setMaxLines(1);
        tvValue.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tvValue);

        TextView tvArrow = new TextView(ctx);
        tvArrow.setText("  ›");
        tvArrow.setTextColor(Color.parseColor("#334155"));
        tvArrow.setTextSize(18f);
        row.addView(tvArrow);

        row.setOnClickListener(onClick);
        row.setTag(tvValue); // so caller can update value later
        return row;
    }

    private static TextView buildSectionTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#475569"));
        tv.setTextSize(10f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(ctx, 18);
        lp.bottomMargin = dp(ctx, 6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static void animateIn(final View v, Context ctx, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationX(dp(ctx, 16));
        v.postDelayed(new Runnable() {
            @Override public void run() {
                v.animate().alpha(1f).translationX(0f)
                    .setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
            }
        }, delayMs);
    }

    private static void attachPressScale(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.97f).scaleY(0.97f)
                            .setDuration(70).setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(130).setInterpolator(new DecelerateInterpolator()).start();
                        break;
                }
                return false;
            }
        });
    }

    private static String getStatusColor(String status) {
        if ("ONLINE".equals(status))      return GREEN;
        if ("OFFLINE".equals(status))     return RED;
        if ("MAINTENANCE".equals(status)) return YELLOW;
        return "#64748B";
    }

    private static String shortenUrl(String url) {
        if (url.length() <= 32) return url;
        return url.substring(0, 29) + "...";
    }

    private static void showToast(final Context ctx, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                LinearLayout layout = new LinearLayout(ctx);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14));
                layout.setGravity(Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#050816"), Color.parseColor("#0B1020")}
                );
                bg.setCornerRadius(dp(ctx, 16));
                bg.setStroke(dp(ctx, 2), Color.parseColor("#00FFF7"));
                layout.setBackground(bg);
                TextView tv = new TextView(ctx);
                tv.setText(msg);
                tv.setTextSize(13f);
                tv.setTextColor(Color.parseColor(CYAN));
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    tv.setShadowLayer(6f, 0f, 0f, Color.parseColor("#0080FF"));
                }
                layout.addView(tv);
                Toast toast = new Toast(ctx);
                toast.setDuration(Toast.LENGTH_SHORT);
                toast.setView(layout);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(ctx, 100));
                toast.show();
            }
        });
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }

    interface OnInputDone {
        void onDone(String val);
    }
}
