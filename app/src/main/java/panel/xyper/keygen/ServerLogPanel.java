package panel.xyper.keygen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

/**
 * ServerLogPanel — panel audit log server-side
 * Fetch dari Workers action: get_logs (Developer only)
 * Menampilkan 50 log terakhir dengan search & filter by action type
 */
public class ServerLogPanel {

    private static final String CYAN   = "#22E5FF";
    private static final String PURPLE = "#A78BFA";
    private static final String GREEN  = "#34D399";
    private static final String RED    = "#F87171";
    private static final String YELLOW = "#FBBF24";

    private static Context ctxGlobal;
    private static JSONArray logsCache;
    private static String searchQuery  = "";
    private static String activeFilter = "All";
    private static LinearLayout listContainerRef;
    private static TextView countRef;

    // ===================== BUILD =====================
    public static View build(final Context ctx) {
        ctxGlobal    = ctx;
        searchQuery  = "";
        activeFilter = "All";
        logsCache    = null;

        FrameLayout root = new FrameLayout(ctx);
        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x44000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        ScrollView outerScroll = new ScrollView(ctx);
        outerScroll.setVerticalScrollBarEnabled(false);
        outerScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(outerScroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout outerContent = new LinearLayout(ctx);
        outerContent.setOrientation(LinearLayout.VERTICAL);
        outerContent.setPadding(dp(ctx, 12), dp(ctx, 14), dp(ctx, 12), dp(ctx, 80));
        outerScroll.addView(outerContent, new LinearLayout.LayoutParams(-1, -2));

        // ── HEADER ──
        outerContent.addView(buildHeader(ctx, outerContent));

        // ── SEARCH BAR ──
        final EditText etSearch = new EditText(ctx);
        etSearch.setHint("🔍  Search action, by, target...");
        etSearch.setTextColor(Color.WHITE);
        etSearch.setHintTextColor(0x6622E5FF);
        etSearch.setTextSize(13f);
        etSearch.setTypeface(Typeface.MONOSPACE);
        etSearch.setSingleLine(true);
        final GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(0x1A22E5FF);
        searchBg.setCornerRadius(dp(ctx, 14));
        searchBg.setStroke(dp(ctx, 1), 0x5522E5FF);
        etSearch.setBackground(searchBg);
        etSearch.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(ctx, 46));
        searchLp.bottomMargin = dp(ctx, 10);
        etSearch.setLayoutParams(searchLp);
        etSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                searchBg.setColor(hasFocus ? 0x2522E5FF : 0x1A22E5FF);
                searchBg.setStroke(hasFocus ? dp(ctx, 2) : dp(ctx, 1),
                    hasFocus ? 0xFF22E5FF : 0x5522E5FF);
                etSearch.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
            }
        });
        outerContent.addView(etSearch);

        // ── FILTER CHIPS ──
        outerContent.addView(buildFilterChips(ctx));

        // ── COUNT BAR ──
        LinearLayout countBar = new LinearLayout(ctx);
        countBar.setOrientation(LinearLayout.HORIZONTAL);
        countBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(-1, -2);
        cbLp.bottomMargin = dp(ctx, 8);
        countBar.setLayoutParams(cbLp);

        countRef = new TextView(ctx);
        countRef.setText("—");
        countRef.setTextColor(Color.parseColor("#334155"));
        countRef.setTextSize(11f);
        countRef.setTypeface(Typeface.MONOSPACE);
        countBar.addView(countRef);
        outerContent.addView(countBar);

        // ── SKELETON ──
        final LinearLayout skeleton = buildSkeleton(ctx);
        outerContent.addView(skeleton);

        // ── LIST ──
        final LinearLayout listContainer = new LinearLayout(ctx);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainerRef = listContainer;
        outerContent.addView(listContainer);

        // Load logs
        loadLogs(ctx, skeleton, listContainer);

        // Search listener
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().toLowerCase().trim();
                rebuildList(ctx, listContainer);
            }
        });

        return root;
    }

    // ── HEADER ──
    private static View buildHeader(final Context ctx, final LinearLayout content) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 14);
        header.setLayoutParams(lp);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(ctx);
        title.setText("Server Logs");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            title.setShadowLayer(14f, 0f, 0f, Color.parseColor(CYAN));
        }

        TextView sub = new TextView(ctx);
        sub.setText("Xyper Xit  ·  Audit Log");
        sub.setTextSize(10f);
        sub.setTextColor(Color.parseColor("#38BDF8"));
        sub.setTypeface(Typeface.MONOSPACE);

        textWrap.addView(title);
        textWrap.addView(sub);
        header.addView(textWrap);

        // Refresh button
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
                logsCache = null;
                listContainerRef.removeAllViews();
                LinearLayout skel = buildSkeleton(ctxGlobal);
                content.addView(skel);
                loadLogs(ctxGlobal, skel, listContainerRef);
            }
        });
        header.addView(btnRef);
        return header;
    }

    // ── FILTER CHIPS ──
    private static LinearLayout buildFilterChips(final Context ctx) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(-1, -2);
        wLp.bottomMargin = dp(ctx, 10);
        wrapper.setLayoutParams(wLp);

        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        final LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, 0, dp(ctx, 8), 0);

        final String[] filters = {"All", "create", "delete", "ban", "unban", "edit", "extend", "revoke", "config"};

        for (int i = 0; i < filters.length; i++) {
            final String f = filters[i];
            final TextView chip = new TextView(ctx);
            chip.setText(f.equals("All") ? "All" : f + "_*");
            chip.setTextSize(11f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
            applyChipStyle(ctx, chip, f.equals(activeFilter));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-2, -2);
            cLp.rightMargin = dp(ctx, 6);
            chip.setLayoutParams(cLp);
            attachPressScale(chip);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    activeFilter = f;
                    for (int j = 0; j < chips.getChildCount(); j++) {
                        View c = chips.getChildAt(j);
                        if (c instanceof TextView)
                            applyChipStyle(ctx, (TextView) c,
                                ((TextView) c).getText().toString()
                                    .replace("_*", "").equals(f));
                    }
                    if (logsCache != null && listContainerRef != null)
                        rebuildList(ctx, listContainerRef);
                }
            });
            chips.addView(chip);
        }
        hsv.addView(chips);
        wrapper.addView(hsv);
        return wrapper;
    }

    private static void applyChipStyle(Context ctx, TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        if (selected) {
            bg.setColor(Color.parseColor("#0D2A3A"));
            bg.setStroke(dp(ctx, 1), Color.parseColor(CYAN));
            chip.setTextColor(Color.parseColor(CYAN));
        } else {
            bg.setColor(0x0FFFFFFF);
            bg.setStroke(dp(ctx, 1), Color.parseColor("#1E3A50"));
            chip.setTextColor(Color.parseColor("#64748B"));
        }
        bg.setCornerRadius(dp(ctx, 20));
        chip.setBackground(bg);
    }

    // ── SKELETON ──
    private static LinearLayout buildSkeleton(Context ctx) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        for (int i = 0; i < 5; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(Color.parseColor("#0B1426"));
            rowBg.setCornerRadius(dp(ctx, 12));
            rowBg.setStroke(dp(ctx, 1), Color.parseColor("#1E293B"));
            row.setBackground(rowBg);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(ctx, 6);
            row.setLayoutParams(rowLp);

            // Dot skeleton
            View dot = new View(ctx);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(Color.parseColor("#1E293B"));
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8));
            dotLp.rightMargin = dp(ctx, 12);
            dotLp.gravity = Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(dotLp);
            row.addView(dot);

            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            textCol.addView(makeSkelLine(ctx, 0.5f));
            textCol.addView(makeSkelLine(ctx, 0.35f));
            row.addView(textCol);

            View timeSkel = makeSkelLine(ctx, 0.18f);
            row.addView(timeSkel);

            wrap.addView(row);
        }

        final LinearLayout wFinal = wrap;
        ValueAnimator sh = ValueAnimator.ofFloat(0.3f, 0.7f);
        sh.setDuration(800);
        sh.setRepeatMode(ValueAnimator.REVERSE);
        sh.setRepeatCount(ValueAnimator.INFINITE);
        sh.setInterpolator(new AccelerateDecelerateInterpolator());
        sh.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                wFinal.setAlpha((float) a.getAnimatedValue());
            }
        });
        sh.start();
        return wrap;
    }

    private static View makeSkelLine(Context ctx, float ratio) {
        View v = new View(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(ctx, 4));
        v.setBackground(bg);
        int w = (int) ((ctx.getResources().getDisplayMetrics().widthPixels - dp(ctx, 56)) * ratio);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, dp(ctx, 9));
        lp.bottomMargin = dp(ctx, 4);
        v.setLayoutParams(lp);
        return v;
    }

    // ===================== LOAD LOGS =====================
    private static void loadLogs(final Context ctx,
            final LinearLayout skeleton, final LinearLayout list) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", "get_logs");

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (!response.isSuccessful()) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    removeSkeleton(skeleton);
                                    showError(ctx, list, "Failed to load logs — HTTP " + response.code());
                                }
                            });
                            return;
                        }
                        try {
                            final JSONArray logs = new JSONArray(response.body());
                            logsCache = logs;
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    removeSkeleton(skeleton);
                                    rebuildList(ctx, list);
                                }
                            });
                        } catch (Exception e) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    removeSkeleton(skeleton);
                                    showError(ctx, list, "Failed to parse server response");
                                }
                            });
                        }
                    }
                    @Override public void onFailure(Call<String> call, Throwable t) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override public void run() {
                                removeSkeleton(skeleton);
                                showError(ctx, list, "No internet connection");
                            }
                        });
                    }
                });
        } catch (Exception e) {
            showNeonToast(ctx, "An error occurred: " + e.getMessage());
        }
    }

    private static void removeSkeleton(LinearLayout skeleton) {
        if (skeleton != null && skeleton.getParent() != null)
            ((android.view.ViewGroup) skeleton.getParent()).removeView(skeleton);
    }

    // ===================== REBUILD LIST =====================
    private static void rebuildList(Context ctx, LinearLayout container) {
        if (logsCache == null) return;
        container.removeAllViews();

        List<JSONObject> filtered = new ArrayList<JSONObject>();
        for (int i = 0; i < logsCache.length(); i++) {
            try {
                JSONObject log = logsCache.getJSONObject(i);
                String action = log.optString("action", "").toLowerCase();
                String by     = log.optString("by", "").toLowerCase();
                String target = log.optString("target", "").toLowerCase();

                // Filter by chip
                if (!activeFilter.equals("All") && !action.contains(activeFilter)) continue;

                // Filter by search
                if (!searchQuery.isEmpty()) {
                    if (!action.contains(searchQuery) &&
                        !by.contains(searchQuery) &&
                        !target.contains(searchQuery)) continue;
                }
                filtered.add(log);
            } catch (Exception ignored) {}
        }

        // Update count
        if (countRef != null) {
            countRef.setText(filtered.size() + " log" + (filtered.size() != 1 ? "s" : ""));
        }

        if (filtered.isEmpty()) {
            showEmpty(ctx, container, searchQuery.isEmpty()
                ? "No activity logs yet"
                : "🔍 Tidak ditemukan\n\"" + searchQuery + "\"");
            return;
        }

        for (int i = 0; i < filtered.size(); i++) {
            final View row = buildLogRow(ctx, filtered.get(i), i);
            container.addView(row);
            row.setAlpha(0f);
            row.setTranslationY(dp(ctx, 12));
            final int delay = i * 40;
            row.postDelayed(new Runnable() {
                @Override public void run() {
                    row.animate().alpha(1f).translationY(0f)
                        .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
                }
            }, delay);
        }
    }

    // ===================== LOG ROW =====================
    private static View buildLogRow(Context ctx, JSONObject log, int index) {
        String action    = log.optString("action", "—");
        String by        = log.optString("by", "?");
        String byKey     = log.optString("by_key", "");
        String target    = log.optString("target", "");
        long   timestamp = log.optLong("timestamp", 0L);

        // Color by action category
        int accentColor;
        if (action.contains("ban"))        accentColor = Color.parseColor(RED);
        else if (action.contains("unban")) accentColor = Color.parseColor(GREEN);
        else if (action.contains("delete"))accentColor = Color.parseColor(YELLOW);
        else if (action.contains("create"))accentColor = Color.parseColor(CYAN);
        else if (action.contains("extend"))accentColor = Color.parseColor(GREEN);
        else if (action.contains("edit"))  accentColor = Color.parseColor(PURPLE);
        else if (action.contains("config"))accentColor = Color.parseColor(YELLOW);
        else                               accentColor = Color.parseColor("#64748B");

        // Row container
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#0B1426"));
        rowBg.setCornerRadius(dp(ctx, 12));
        rowBg.setStroke(dp(ctx, 1),
            Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        row.setBackground(rowBg);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = dp(ctx, 6);
        row.setLayoutParams(rowLp);

        // ── Colored dot ──
        View dot = new View(ctx);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(accentColor);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8));
        dotLp.rightMargin = dp(ctx, 12);
        dotLp.gravity = Gravity.CENTER_VERTICAL;
        dot.setLayoutParams(dotLp);
        row.addView(dot);

        // ── Text column ──
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        // Action name + by role
        LinearLayout topLine = new LinearLayout(ctx);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(-1, -2);
        tlLp.bottomMargin = dp(ctx, 2);
        topLine.setLayoutParams(tlLp);

        TextView tvAction = new TextView(ctx);
        tvAction.setText(action);
        tvAction.setTextColor(accentColor);
        tvAction.setTextSize(12f);
        tvAction.setTypeface(Typeface.DEFAULT_BOLD);
        tvAction.setMaxLines(1);
        tvAction.setEllipsize(TextUtils.TruncateAt.END);
        tvAction.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        topLine.addView(tvAction);

        // Role badge inline
        String roleColor = by.equals("Developer") ? CYAN
            : by.equals("Reseller") ? PURPLE : GREEN;
        TextView tvBy = new TextView(ctx);
        tvBy.setText(by);
        tvBy.setTextSize(10f);
        tvBy.setTypeface(Typeface.DEFAULT_BOLD);
        try { tvBy.setTextColor(Color.parseColor(roleColor)); }
        catch (Exception e) { tvBy.setTextColor(Color.parseColor("#64748B")); }
        tvBy.setPadding(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 2));
        GradientDrawable byBg = new GradientDrawable();
        int rc; try { rc = Color.parseColor(roleColor); } catch (Exception e) { rc = Color.GRAY; }
        byBg.setColor(Color.argb(20, Color.red(rc), Color.green(rc), Color.blue(rc)));
        byBg.setCornerRadius(dp(ctx, 4));
        byBg.setStroke(dp(ctx, 1), Color.argb(60, Color.red(rc), Color.green(rc), Color.blue(rc)));
        tvBy.setBackground(byBg);
        LinearLayout.LayoutParams byLp = new LinearLayout.LayoutParams(-2, -2);
        byLp.leftMargin = dp(ctx, 6);
        tvBy.setLayoutParams(byLp);
        topLine.addView(tvBy);
        textCol.addView(topLine);

        // Target + key preview
        if (!target.isEmpty()) {
            TextView tvTarget = new TextView(ctx);
            String displayTarget = target.length() > 30 ? target.substring(0, 27) + "..." : target;
            tvTarget.setText(displayTarget);
            tvTarget.setTextColor(Color.parseColor("#475569"));
            tvTarget.setTextSize(11f);
            tvTarget.setTypeface(Typeface.MONOSPACE);
            tvTarget.setMaxLines(1);
            tvTarget.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(tvTarget);
        }

        if (!byKey.isEmpty()) {
            TextView tvKey = new TextView(ctx);
            tvKey.setText("by: " + byKey);
            tvKey.setTextColor(Color.parseColor("#334155"));
            tvKey.setTextSize(10f);
            tvKey.setTypeface(Typeface.MONOSPACE);
            textCol.addView(tvKey);
        }

        row.addView(textCol);

        // ── Timestamp ──
        TextView tvTime = new TextView(ctx);
        tvTime.setText(formatRelative(timestamp));
        tvTime.setTextColor(Color.parseColor("#334155"));
        tvTime.setTextSize(10f);
        tvTime.setTypeface(Typeface.MONOSPACE);
        tvTime.setGravity(Gravity.END);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(-2, -2);
        timeLp.leftMargin = dp(ctx, 8);
        tvTime.setLayoutParams(timeLp);
        row.addView(tvTime);

        // Long press → full timestamp tooltip
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showNeonToast(ctx, formatFull(timestamp));
                return true;
            }
        });

        return row;
    }

    // ── EMPTY & ERROR ──
    private static void showEmpty(Context ctx, LinearLayout container, String msg) {
        container.removeAllViews();
        LinearLayout empty = new LinearLayout(ctx);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(ctx, 200)));

        TextView icon = new TextView(ctx);
        icon.setText("📋");
        icon.setTextSize(40f);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon);

        TextView tv = new TextView(ctx);
        tv.setText(msg);
        tv.setTextColor(Color.parseColor("#475569"));
        tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(-1, -2);
        tvLp.topMargin = dp(ctx, 10);
        tv.setLayoutParams(tvLp);
        empty.addView(tv);
        container.addView(empty);
    }

    private static void showError(final Context ctx, final LinearLayout container, final String msg) {
        container.removeAllViews();
        LinearLayout err = new LinearLayout(ctx);
        err.setOrientation(LinearLayout.VERTICAL);
        err.setGravity(Gravity.CENTER);
        err.setPadding(dp(ctx, 20), dp(ctx, 40), dp(ctx, 20), dp(ctx, 40));
        GradientDrawable eBg = new GradientDrawable();
        eBg.setColor(Color.parseColor("#1A0808"));
        eBg.setCornerRadius(dp(ctx, 16));
        eBg.setStroke(dp(ctx, 1), Color.parseColor("#7F1D1D"));
        err.setBackground(eBg);
        err.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        TextView eIcon = new TextView(ctx);
        eIcon.setText("⚠️");
        eIcon.setTextSize(36f);
        eIcon.setGravity(Gravity.CENTER);
        err.addView(eIcon);

        TextView eMsg = new TextView(ctx);
        eMsg.setText(msg);
        eMsg.setTextColor(Color.parseColor(RED));
        eMsg.setTextSize(13f);
        eMsg.setGravity(Gravity.CENTER);
        eMsg.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(-1, -2);
        mLp.topMargin = dp(ctx, 10);
        mLp.bottomMargin = dp(ctx, 16);
        eMsg.setLayoutParams(mLp);
        err.addView(eMsg);

        TextView btnRetry = new TextView(ctx);
        btnRetry.setText("↻  Coba Lagi");
        btnRetry.setTextColor(Color.parseColor(CYAN));
        btnRetry.setTextSize(13f);
        btnRetry.setTypeface(Typeface.DEFAULT_BOLD);
        btnRetry.setGravity(Gravity.CENTER);
        btnRetry.setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10));
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(0x1522E5FF);
        rBg.setCornerRadius(dp(ctx, 10));
        rBg.setStroke(dp(ctx, 1), Color.parseColor("#22455A"));
        btnRetry.setBackground(rBg);
        attachPressScale(btnRetry);
        final LinearLayout listRef = container;
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                logsCache = null;
                container.removeAllViews();
                LinearLayout skel = buildSkeleton(ctx);
                // add skeleton into parent
                if (listRef.getParent() instanceof LinearLayout) {
                    LinearLayout parent = (LinearLayout) listRef.getParent();
                    int idx = parent.indexOfChild(listRef);
                    if (idx >= 0) parent.addView(skel, idx);
                }
                loadLogs(ctx, skel, listRef);
            }
        });
        err.addView(btnRetry);
        container.addView(err);
    }

    // ===================== UTILS =====================
    private static void attachPressScale(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.95f).scaleY(0.95f)
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

    private static void showNeonToast(final Context ctx, final String msg) {
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
                tv.setTextSize(12f);
                tv.setTextColor(Color.parseColor(CYAN));
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    tv.setShadowLayer(6f, 0f, 0f, Color.parseColor("#0080FF"));
                }
                layout.addView(tv);
                Toast toast = new Toast(ctx);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(layout);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(ctx, 100));
                toast.show();
            }
        });
    }

    private static String formatRelative(long millis) {
        if (millis <= 0) return "—";
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60000)       return "just now";
        if (diff < 3600000)     return (diff / 60000) + " min ago";
        if (diff < 86400000)    return (diff / 3600000) + " hr ago";
        if (diff < 2592000000L) return (diff / 86400000) + " days ago";
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(millis));
    }

    private static String formatFull(long millis) {
        if (millis <= 0) return "—";
        return new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
            .format(new Date(millis));
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }
}
