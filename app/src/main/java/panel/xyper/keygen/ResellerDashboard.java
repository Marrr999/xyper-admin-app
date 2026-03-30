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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

/**
 * ResellerDashboard — panel khusus Reseller
 * Hanya lihat & manage Client miliknya sendiri
 */
public class ResellerDashboard {

    private static final String PURPLE = "#A78BFA";
    private static final String CYAN   = "#22E5FF";
    private static final String GREEN  = "#34D399";
    private static final String RED    = "#F87171";
    private static final String YELLOW = "#FBBF24";
    private static final String BG     = "#020617";
    private static final String CARD   = "#0B1426";

    private static Context ctxGlobal;
    private static String rsSearchQuery  = "";
    private static String rsActiveFilter = "All";
    private static LinearLayout rsListRef;
    private static JSONArray rsKeysCache;

    // ===================== BUILD =====================
    public static View build(final Context ctx) {
        ctxGlobal = ctx;
        rsSearchQuery  = "";
        rsActiveFilter = "All";
        rsKeysCache    = null;

        FrameLayout root = new FrameLayout(ctx);
        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x44000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        final LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 80));
        scroll.addView(content, new LinearLayout.LayoutParams(-1, -2));

        // Pull-to-refresh
        scroll.setOnTouchListener(new View.OnTouchListener() {
            private float startY = 0;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    startY = e.getRawY();
                } else if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dist = e.getRawY() - startY;
                    if (dist > dp(ctx, 80) && scroll.getScrollY() == 0) {
                        content.removeAllViews();
                        rsKeysCache = null;
                        loadMyClients(ctx, content);
                    }
                }
                return false;
            }
        });

        loadMyClients(ctx, content);
        return root;
    }

    // ===================== LOAD DATA =====================
    private static void loadMyClients(final Context ctx, final LinearLayout content) {
        content.removeAllViews();
        content.addView(buildHeader(ctx, content));
        content.addView(buildSkeleton(ctx));

        ApiClient.getAdminService().getAllKeys().enqueue(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (!response.isSuccessful()) {
                    postToast(ctx, "Failed to load data — HTTP " + response.code());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() {
                            // remove skeleton, show error
                            if (content.getChildCount() > 1)
                                content.removeViewAt(content.getChildCount() - 1);
                            showErrorState(ctx, content);
                        }
                    });
                    return;
                }
                try {
                    final JSONArray all = new JSONArray(response.body());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() {
                            // remove skeleton
                            if (content.getChildCount() > 1)
                                content.removeViewAt(content.getChildCount() - 1);
                            rsKeysCache = all;
                            buildUI(ctx, content, all);
                        }
                    });
                } catch (Exception e) {
                    postToast(ctx, "Failed to parse server response");
                }
            }
            @Override
            public void onFailure(Call<String> call, Throwable t) {
                postToast(ctx, "An error occurred: " + t.getMessage());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (content.getChildCount() > 1)
                            content.removeViewAt(content.getChildCount() - 1);
                        showErrorState(ctx, content);
                    }
                });
            }
        });
    }

    // ===================== BUILD UI =====================
    private static void buildUI(final Context ctx, final LinearLayout content, final JSONArray keys) {
        int total = keys.length();
        int active = 0, expired = 0, banned = 0, totalDevices = 0;
        long now = System.currentTimeMillis();

        for (int i = 0; i < keys.length(); i++) {
            try {
                JSONObject k = keys.getJSONObject(i);
                long exp = k.optLong("expired", 0L);
                long ban = k.optLong("banned_until", 0L);
                JSONArray devs = k.optJSONArray("devices");
                if (ban > now) banned++;
                else if (exp > 0 && exp < now) expired++;
                else active++;
                if (devs != null) totalDevices += devs.length();
            } catch (Exception ignored) {}
        }

        // ── Banner notif expired/banned ──
        if (banned > 0) {
            content.addView(buildBanner(ctx,
                "⛔  " + banned + " client key" + (banned > 1 ? "s" : "") + " currently banned",
                "#F87171", "#2A0808", "#7F1D1D"));
        }
        if (expired > 0) {
            content.addView(buildBanner(ctx,
                "⚠️  " + expired + " client key" + (expired > 1 ? "s" : "") + " has expired — consider extending",
                "#FBBF24", "#2A1A00", "#78350F"));
        }

        // ── Stats grid ──
        content.addView(buildSectionTitle(ctx, "📊  MY CLIENTS"));

        LinearLayout statsGrid = new LinearLayout(ctx);
        statsGrid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sgLp = new LinearLayout.LayoutParams(-1, -2);
        sgLp.bottomMargin = dp(ctx, 4);
        statsGrid.setLayoutParams(sgLp);
        statsGrid.addView(buildStatBox(ctx, String.valueOf(total),        "Total",   PURPLE));
        statsGrid.addView(buildStatBox(ctx, String.valueOf(active),       "Active",  GREEN));
        statsGrid.addView(buildStatBox(ctx, String.valueOf(banned),       "Banned",  RED));
        statsGrid.addView(buildStatBox(ctx, String.valueOf(totalDevices), "Devices", CYAN));
        content.addView(statsGrid);
        animateIn(statsGrid, ctx, 0);

        // ── Mini Stats Chart ──
        if (total > 0) {
            content.addView(buildMiniChart(ctx, active, expired, banned, total));
        }

        // ── Add button ──
        TextView btnAdd = new TextView(ctx);
        btnAdd.setText("＋  Buat Key Client Baru");
        btnAdd.setTextColor(Color.parseColor(PURPLE));
        btnAdd.setTextSize(13f);
        btnAdd.setTypeface(Typeface.DEFAULT_BOLD);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setPadding(0, dp(ctx, 15), 0, dp(ctx, 15));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(Color.parseColor("#150E2A"));
        addBg.setCornerRadius(dp(ctx, 14));
        addBg.setStroke(dp(ctx, 1), Color.parseColor("#4C1D95"));
        btnAdd.setBackground(addBg);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, -2);
        addLp.topMargin = dp(ctx, 12);
        addLp.bottomMargin = dp(ctx, 4);
        btnAdd.setLayoutParams(addLp);
        attachPressScale(btnAdd);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCreateSheet(ctx, content); }
        });
        content.addView(btnAdd);
        animateIn(btnAdd, ctx, 60);

        // ── Export CSV button ──
        TextView btnExport = new TextView(ctx);
        btnExport.setText("📊  Export My Clients CSV");
        btnExport.setTextColor(Color.parseColor("#34D399"));
        btnExport.setTextSize(13f);
        btnExport.setTypeface(Typeface.DEFAULT_BOLD);
        btnExport.setGravity(Gravity.CENTER);
        btnExport.setPadding(dp(ctx,16), dp(ctx,14), dp(ctx,16), dp(ctx,14));
        GradientDrawable exportBg = new GradientDrawable();
        exportBg.setColor(Color.parseColor("#062A1A"));
        exportBg.setCornerRadius(dp(ctx,14));
        exportBg.setStroke(dp(ctx,1), Color.parseColor("#065F46"));
        btnExport.setBackground(exportBg);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(-1,-2);
        expLp.bottomMargin = dp(ctx,12);
        btnExport.setLayoutParams(expLp);
        attachPressScale(btnExport);
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportClientsCsv(ctx, keys); }
        });
        content.addView(btnExport);
        animateIn(btnExport, ctx, 80);

        // ── Client list ──
        if (total == 0) {
            showEmptyState(ctx, content);
            return;
        }

        // ── SEARCH BAR ──
        final EditText etSearch = new EditText(ctx);
        etSearch.setHint("🔍  Search clients...");
        etSearch.setTextColor(android.graphics.Color.WHITE);
        etSearch.setHintTextColor(0x66A78BFA);
        etSearch.setTextSize(13f);
        etSearch.setTypeface(android.graphics.Typeface.MONOSPACE);
        etSearch.setSingleLine(true);
        final android.graphics.drawable.GradientDrawable searchBg = new android.graphics.drawable.GradientDrawable();
        searchBg.setColor(0x1AFFFFFF);
        searchBg.setCornerRadius(dp(ctx,14));
        searchBg.setStroke(dp(ctx,1), 0x55A78BFA);
        etSearch.setBackground(searchBg);
        etSearch.setPadding(dp(ctx,14), 0, dp(ctx,14), 0);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(ctx,46));
        searchLp.bottomMargin = dp(ctx,10);
        etSearch.setLayoutParams(searchLp);
        etSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                searchBg.setColor(hasFocus ? 0x25A78BFA : 0x1AFFFFFF);
                searchBg.setStroke(hasFocus ? dp(ctx,2) : dp(ctx,1),
                    hasFocus ? android.graphics.Color.parseColor(PURPLE) : 0x55A78BFA);
                etSearch.setPadding(dp(ctx,14), 0, dp(ctx,14), 0);
            }
        });
        content.addView(etSearch);

        // ── FILTER CHIPS ──
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(-1,-2);
        hsvLp.bottomMargin = dp(ctx,10);
        hsv.setLayoutParams(hsvLp);
        final LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0,0,dp(ctx,8),0);
        final String[] filterOpts = {"All","Active","Banned","Expired"};
        for (int fi=0; fi<filterOpts.length; fi++) {
            final String fo = filterOpts[fi];
            final TextView chip = new TextView(ctx);
            chip.setText(fo);
            chip.setTextSize(12f);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(dp(ctx,14), dp(ctx,6), dp(ctx,14), dp(ctx,6));
            applyRsChipStyle(ctx, chip, fo.equals(rsActiveFilter));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-2,-2);
            cLp.rightMargin = dp(ctx,6);
            chip.setLayoutParams(cLp);
            attachPressScale(chip);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    rsActiveFilter = fo;
                    for (int j=0; j<chips.getChildCount(); j++) {
                        View c = chips.getChildAt(j);
                        if (c instanceof TextView)
                            applyRsChipStyle(ctx,(TextView)c,((TextView)c).getText().toString().equals(fo));
                    }
                    if (rsListRef != null) rebuildRsList(ctx, rsListRef, content);
                }
            });
            chips.addView(chip);
        }
        hsv.addView(chips);
        content.addView(hsv);

        // ── LIST CONTAINER ──
        content.addView(buildSectionTitle(ctx, "📋  CLIENT LIST"));
        final LinearLayout listContainer = new LinearLayout(ctx);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        rsListRef = listContainer;
        content.addView(listContainer);
        rebuildRsList(ctx, listContainer, content);

        // Search listener
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                rsSearchQuery = s.toString().toLowerCase().trim();
                rebuildRsList(ctx, listContainer, content);
            }
        });

        // ── Activity History ──
        java.util.List<ActivityLog.LogEntry> logs = ActivityLog.getAllLogs(ctx);
        if (!logs.isEmpty()) {
            content.addView(buildSectionTitle(ctx, "🕐  RECENT ACTIVITY"));
            LinearLayout histCard = new LinearLayout(ctx);
            histCard.setOrientation(LinearLayout.VERTICAL);
            histCard.setPadding(dp(ctx,14), dp(ctx,10), dp(ctx,14), dp(ctx,10));
            android.graphics.drawable.GradientDrawable histBg = new android.graphics.drawable.GradientDrawable();
            histBg.setColor(android.graphics.Color.parseColor("#080F1E"));
            histBg.setCornerRadius(dp(ctx,14));
            histBg.setStroke(dp(ctx,1), 0x33A78BFA);
            histCard.setBackground(histBg);
            LinearLayout.LayoutParams hcLp = new LinearLayout.LayoutParams(-1,-2);
            hcLp.bottomMargin = dp(ctx,8);
            histCard.setLayoutParams(hcLp);

            int showMax = Math.min(10, logs.size());
            for (int li = 0; li < showMax; li++) {
                ActivityLog.LogEntry entry = logs.get(li);
                histCard.addView(buildHistoryRow(ctx, entry));
            }
            if (logs.size() > 10) {
                TextView tvMore = new TextView(ctx);
                tvMore.setText("+ " + (logs.size() - 10) + " more actions...");
                tvMore.setTextColor(0x55A78BFA);
                tvMore.setTextSize(11f);
                tvMore.setTypeface(android.graphics.Typeface.MONOSPACE);
                tvMore.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(-1,-2);
                moreLp.topMargin = dp(ctx,6);
                tvMore.setLayoutParams(moreLp);
                histCard.addView(tvMore);
            }
            content.addView(histCard);
        }
    }

    private static View buildHistoryRow(Context ctx, ActivityLog.LogEntry entry) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,8);
        row.setLayoutParams(lp);

        // Icon
        TextView tvIcon = new TextView(ctx);
        tvIcon.setText(entry.getIcon());
        tvIcon.setTextSize(16f);
        tvIcon.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(ctx,32), dp(ctx,32));
        iLp.rightMargin = dp(ctx,10);
        tvIcon.setLayoutParams(iLp);
        row.addView(tvIcon);

        // Action + target
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

        TextView tvAction = new TextView(ctx);
        tvAction.setText(entry.getDisplayAction());
        tvAction.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
        tvAction.setTextSize(12f);
        tvAction.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        col.addView(tvAction);

        if (entry.target != null && !entry.target.isEmpty()) {
            String displayTarget = entry.target;
            // Only decode if target looks like base64 (no spaces, reasonable length)
            if (!displayTarget.contains(" ") && displayTarget.length() > 4) {
                try {
                    byte[] dec = android.util.Base64.decode(displayTarget, android.util.Base64.DEFAULT);
                    String decoded = new String(dec, java.nio.charset.Charset.forName("UTF-8"));
                    // Only use decoded if it's printable ASCII (no control chars)
                    boolean isPrintable = true;
                    for (char c : decoded.toCharArray()) {
                        if (c < 32 || c > 126) { isPrintable = false; break; }
                    }
                    if (isPrintable && !decoded.isEmpty()) displayTarget = decoded;
                } catch (Exception ignored) {}
            }
            if (displayTarget.length() > 24) displayTarget = displayTarget.substring(0, 24) + "...";
            TextView tvTarget = new TextView(ctx);
            tvTarget.setText(displayTarget);
            tvTarget.setTextColor(0x88FFFFFF);
            tvTarget.setTextSize(10f);
            tvTarget.setTypeface(android.graphics.Typeface.MONOSPACE);
            col.addView(tvTarget);
        }
        row.addView(col);

        // Timestamp
        TextView tvTime = new TextView(ctx);
        String timeStr = new java.text.SimpleDateFormat("dd MMM, HH:mm",
            java.util.Locale.getDefault()).format(new java.util.Date(entry.timestamp));
        tvTime.setText(timeStr);
        tvTime.setTextColor(0x55FFFFFF);
        tvTime.setTextSize(10f);
        tvTime.setTypeface(android.graphics.Typeface.MONOSPACE);
        row.addView(tvTime);

        return row;
    }

    private static void applyRsChipStyle(Context ctx, TextView chip, boolean selected) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        if (selected) {
            bg.setColor(android.graphics.Color.parseColor("#180A30"));
            bg.setStroke(dp(ctx,1), android.graphics.Color.parseColor(PURPLE));
            chip.setTextColor(android.graphics.Color.parseColor(PURPLE));
        } else {
            bg.setColor(0x0FFFFFFF);
            bg.setStroke(dp(ctx,1), android.graphics.Color.parseColor("#1E3A50"));
            chip.setTextColor(android.graphics.Color.parseColor("#64748B"));
        }
        bg.setCornerRadius(dp(ctx,20));
        chip.setBackground(bg);
    }

    private static void rebuildRsList(final Context ctx, final LinearLayout container, final LinearLayout content) {
        if (rsKeysCache == null) return;
        container.removeAllViews();
        long now = System.currentTimeMillis();
        java.util.List<JSONObject> filtered = new java.util.ArrayList<JSONObject>();
        for (int i=0; i<rsKeysCache.length(); i++) {
            try {
                JSONObject obj = rsKeysCache.getJSONObject(i);
                long banned  = obj.optLong("banned_until",0L);
                long expired = obj.optLong("expired",0L);
                boolean isBannedObj  = banned > now;
                boolean isExpiredObj = expired > 0 && expired < now;
                // Filter
                if (!rsActiveFilter.equals("All")) {
                    if (rsActiveFilter.equals("Active")  && (isBannedObj || isExpiredObj)) continue;
                    if (rsActiveFilter.equals("Banned")  && !isBannedObj) continue;
                    if (rsActiveFilter.equals("Expired") && !isExpiredObj) continue;
                }
                // Search
                if (!rsSearchQuery.isEmpty()) {
                    String key = obj.optString("key","").toLowerCase();
                    try { byte[] d=android.util.Base64.decode(key,android.util.Base64.DEFAULT);
                        key=new String(d,java.nio.charset.Charset.forName("UTF-8")).toLowerCase();
                    } catch(Exception ignored){}
                    if (!key.contains(rsSearchQuery)) continue;
                }
                filtered.add(obj);
            } catch(Exception ignored){}
        }
        if (filtered.isEmpty()) {
            LinearLayout empty = new LinearLayout(ctx);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(ctx,140)));
            TextView eIcon = new TextView(ctx); eIcon.setText("📭"); eIcon.setTextSize(36f);
            eIcon.setGravity(android.view.Gravity.CENTER); empty.addView(eIcon);
            TextView eTv = new TextView(ctx);
            eTv.setText(rsSearchQuery.isEmpty() ? "No clients found" : "No results for \"" + rsSearchQuery + "\"");
            eTv.setTextColor(android.graphics.Color.parseColor("#475569")); eTv.setTextSize(13f);
            eTv.setGravity(android.view.Gravity.CENTER); eTv.setTypeface(android.graphics.Typeface.MONOSPACE);
            LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(-1,-2); eLp.topMargin=dp(ctx,8);
            eTv.setLayoutParams(eLp); empty.addView(eTv);
            container.addView(empty);
            return;
        }
        for (int i=0; i<filtered.size(); i++) {
            try {
                final View card = buildClientCard(ctx, filtered.get(i), i+1, content);
                container.addView(card);
                final int delay = i * 50;
                card.setAlpha(0f); card.setTranslationY(dp(ctx,14));
                card.postDelayed(new Runnable() {
                    @Override public void run() {
                        card.animate().alpha(1f).translationY(0f)
                            .setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
                    }
                }, delay);
            } catch(Exception ignored){}
        }
    }

    // ===================== CLIENT CARD =====================
    private static View buildClientCard(final Context ctx, final JSONObject obj,
            int index, final LinearLayout content) {
        final String encoded  = obj.optString("key", "");
        String type           = obj.optString("type", "?");
        long expired          = obj.optLong("expired", 0L);
        final long bannedUntil= obj.optLong("banned_until", 0L);
        int violations        = obj.optInt("violation_count", 0);
        int devLimit          = obj.optInt("device_limit", 1);
        JSONArray devs        = obj.optJSONArray("devices");
        int devCount          = devs != null ? devs.length() : 0;

        long now = System.currentTimeMillis();
        final boolean isBanned  = bannedUntil > now;
        final boolean isExpired = expired > 0 && expired < now;

        String plainKey = encoded;
        try {
            byte[] dec = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
            plainKey = new String(dec, Charset.forName("UTF-8"));
        } catch (Exception ignored) {}
        final String plainFinal = plainKey;
        final String encodedFinal = encoded;

        String statusText  = isBanned ? "BANNED" : isExpired ? "EXPIRED" : "ACTIVE";
        int statusColor    = Color.parseColor(isBanned ? RED : isExpired ? YELLOW : GREEN);

        // Card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor(CARD));
        cardBg.setCornerRadius(dp(ctx, 16));
        cardBg.setStroke(dp(ctx, 1), isBanned
            ? Color.parseColor("#4A1010")
            : isExpired ? Color.parseColor("#3A3010")
            : Color.parseColor("#1A2A3A"));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(ctx, 8);
        card.setLayoutParams(cardLp);

        // ── Header row ──
        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(-1, -2);
        hrLp.bottomMargin = dp(ctx, 8);
        headerRow.setLayoutParams(hrLp);

        TextView tvNum = new TextView(ctx);
        tvNum.setText("#" + index + "  ");
        tvNum.setTextColor(Color.parseColor("#334155"));
        tvNum.setTextSize(11f);
        tvNum.setTypeface(Typeface.MONOSPACE);
        headerRow.addView(tvNum);

        TextView tvKey = new TextView(ctx);
        tvKey.setText(plainKey);
        tvKey.setTextColor(Color.parseColor(PURPLE));
        tvKey.setTypeface(Typeface.DEFAULT_BOLD);
        tvKey.setTextSize(13f);
        tvKey.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        tvKey.setMaxLines(1);
        tvKey.setEllipsize(TextUtils.TruncateAt.END);
        headerRow.addView(tvKey);

        // Status badge
        TextView tvStatus = new TextView(ctx);
        tvStatus.setText(statusText);
        tvStatus.setTextColor(statusColor);
        tvStatus.setTextSize(10f);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(dp(ctx, 7), dp(ctx, 3), dp(ctx, 7), dp(ctx, 3));
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(Color.argb(25, Color.red(statusColor),
            Color.green(statusColor), Color.blue(statusColor)));
        statusBg.setCornerRadius(dp(ctx, 6));
        statusBg.setStroke(dp(ctx, 1), statusColor);
        tvStatus.setBackground(statusBg);
        headerRow.addView(tvStatus);
        card.addView(headerRow);

        // ── Divider ──
        card.addView(buildDivider(ctx));

        // ── Info grid 2 kolom ──
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(-1, -2);
        gLp.bottomMargin = dp(ctx, 8);
        grid.setLayoutParams(gLp);

        LinearLayout col1 = new LinearLayout(ctx);
        col1.setOrientation(LinearLayout.VERTICAL);
        col1.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout col2 = new LinearLayout(ctx);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        col1.addView(buildInfoLine(ctx, "Type",     type,       "#CBD5E1"));
        col1.addView(buildInfoLine(ctx, "Devices",
            devCount + "/" + devLimit,                          devCount > 0 ? CYAN : "#475569"));
        if (violations > 0)
            col1.addView(buildInfoLine(ctx, "Violations", String.valueOf(violations), RED));

        String expStr = expired == 0 ? "∞ Permanent"
            : isExpired ? "⚠ " + formatTime(expired) : formatTime(expired);
        col2.addView(buildInfoLine(ctx, "Expired", expStr,
            isExpired ? YELLOW : "#94A3B8"));
        if (isBanned)
            col2.addView(buildInfoLine(ctx, "Banned Until", formatTime(bannedUntil), RED));

        grid.addView(col1);
        grid.addView(col2);
        card.addView(grid);

        // ── Action row 1: primary ──
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(-1, -2);
        r1lp.bottomMargin = dp(ctx, 6);
        row1.setLayoutParams(r1lp);

        // Ban / Unban
        TextView btnBan = isBanned
            ? makeBtn(ctx, "✅ Unban",  CYAN,   "#051520", "#0A3040")
            : makeBtn(ctx, "⛔ Ban",    RED,    "#1A0808", "#7F1D1D");
        attachPressScale(btnBan);
        btnBan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isBanned) doAction(ctx, "unban_key", encodedFinal, 0, content);
                else showBanSheet(ctx, encodedFinal, content);
            }
        });
        row1.addView(btnBan);
        addSpacer(row1, ctx);

        // Extend
        TextView btnExtend = makeBtn(ctx, "⏩ Extend", GREEN, "#062A1A", "#065F46");
        attachPressScale(btnExtend);
        btnExtend.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showExtendSheet(ctx, encodedFinal, content); }
        });
        row1.addView(btnExtend);
        addSpacer(row1, ctx);

        // Delete
        TextView btnDelete = makeBtn(ctx, "🗑 Delete", YELLOW, "#1A1208", "#78350F");
        attachPressScale(btnDelete);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showConfirmDialog(ctx,
                    "Delete Key",
                    "Key \"" + plainFinal + "\" will be permanently deleted.",
                    RED,
                    new Runnable() {
                        @Override public void run() {
                            doAction(ctx, "delete_key", encodedFinal, 0, content);
                        }
                    });
            }
        });
        row1.addView(btnDelete);
        card.addView(row1);

        // ── Action row 2: secondary ──
        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        if (devCount > 0) {
            TextView btnRevoke = makeBtn(ctx, "📵 Revoke Devices", "#64748B", "#0C111A", "#1E293B");
            attachPressScale(btnRevoke);
            btnRevoke.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showConfirmDialog(ctx,
                        "Revoke Devices",
                        "All devices from \"" + plainFinal + "\" will be removed.",
                        RED,
                        new Runnable() {
                            @Override public void run() {
                                doAction(ctx, "revoke_devices", encodedFinal, 0, content);
                            }
                        });
                }
            });
            row2.addView(btnRevoke);
            addSpacer(row2, ctx);
        }

        if (violations > 0) {
            TextView btnReset = makeBtn(ctx, "🔄 Reset Violations", "#64748B", "#0C111A", "#1E293B");
            attachPressScale(btnReset);
            btnReset.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showConfirmDialog(ctx,
                        "Reset Violations",
                        "Reset violation count key \"" + plainFinal + "\" ke 0?",
                        GREEN,
                        new Runnable() {
                            @Override public void run() {
                                doAction(ctx, "reset_violations", encodedFinal, 0, content);
                            }
                        });
                }
            });
            row2.addView(btnReset);
        }

        if (row2.getChildCount() > 0) card.addView(row2);

        // ── Device detail toggle ──
        final JSONArray devsFinal = devs;
        if (devCount > 0) {
            final LinearLayout devContainer = new LinearLayout(ctx);
            devContainer.setOrientation(LinearLayout.VERTICAL);
            devContainer.setVisibility(android.view.View.GONE);

            final TextView btnDevToggle = makeBtn(ctx,
                "📱  " + devCount + " Device(s)", CYAN, "#051520", "#0A2A3A");
            LinearLayout.LayoutParams dtLp = new LinearLayout.LayoutParams(-1,-2);
            dtLp.topMargin = dp(ctx,6);
            btnDevToggle.setLayoutParams(dtLp);
            btnDevToggle.setGravity(Gravity.CENTER);
            final boolean[] devExpanded = {false};
            btnDevToggle.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    devExpanded[0] = !devExpanded[0];
                    devContainer.setVisibility(devExpanded[0]
                        ? android.view.View.VISIBLE : android.view.View.GONE);
                    btnDevToggle.setText(devExpanded[0]
                        ? "▼  Hide Devices"
                        : "📱  " + devCount + " Device(s)");
                    if (devExpanded[0] && devContainer.getChildCount() == 0 && devsFinal != null) {
                        long now2 = System.currentTimeMillis();
                        for (int d = 0; d < devsFinal.length(); d++) {
                            try {
                                JSONObject dev = devsFinal.getJSONObject(d);
                                devContainer.addView(buildDeviceRow(ctx, dev, now2));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            });
            card.addView(btnDevToggle);
            card.addView(devContainer);
        }

        return card;
    }

    private static View buildDeviceRow(Context ctx, JSONObject dev, long now) {
        String devName   = dev.optString("device_name", "Unknown Device");
        String osVer     = dev.optString("os_version", "");
        String devId     = dev.optString("device_id", "");
        long bannedUntil = dev.optLong("banned_until", 0L);
        long lastUsed    = dev.optLong("last_used", 0L);
        boolean isBanned = bannedUntil > now;

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(ctx,10), dp(ctx,8), dp(ctx,10), dp(ctx,8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isBanned ? 0x201A0808 : 0x1A22E5FF);
        bg.setCornerRadius(dp(ctx,10));
        bg.setStroke(dp(ctx,1), isBanned ? 0x40F87171 : 0x2222E5FF);
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(ctx,6);
        row.setLayoutParams(lp);

        // Device name + status
        LinearLayout nameRow = new LinearLayout(ctx);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(ctx);
        tvName.setText("📱  " + devName);
        tvName.setTextColor(isBanned ? Color.parseColor("#F87171") : Color.WHITE);
        tvName.setTextSize(12f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        nameRow.addView(tvName);

        if (isBanned) {
            TextView tvBan = new TextView(ctx);
            tvBan.setText("BANNED");
            tvBan.setTextColor(Color.parseColor("#F87171"));
            tvBan.setTextSize(9f);
            tvBan.setPadding(dp(ctx,5),dp(ctx,2),dp(ctx,5),dp(ctx,2));
            GradientDrawable banBg = new GradientDrawable();
            banBg.setColor(0x20F87171); banBg.setCornerRadius(dp(ctx,4));
            banBg.setStroke(dp(ctx,1), 0x80F87171);
            tvBan.setBackground(banBg);
            nameRow.addView(tvBan);
        }
        row.addView(nameRow);

        // OS + last used
        if (!osVer.isEmpty()) {
            TextView tvOs = new TextView(ctx);
            tvOs.setText("OS: " + osVer);
            tvOs.setTextColor(0x88FFFFFF);
            tvOs.setTextSize(10f);
            tvOs.setTypeface(Typeface.MONOSPACE);
            row.addView(tvOs);
        }
        if (lastUsed > 0) {
            TextView tvLast = new TextView(ctx);
            tvLast.setText("Last Login: " + new java.text.SimpleDateFormat(
                "dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(
                new java.util.Date(lastUsed)));
            tvLast.setTextColor(0x66FFFFFF);
            tvLast.setTextSize(10f);
            tvLast.setTypeface(Typeface.MONOSPACE);
            row.addView(tvLast);
        }
        return row;
    }

    // ===================== SHEETS & DIALOGS =====================

    private static void showCreateSheet(final Context ctx, final LinearLayout content) {
        final String[] types  = {"7 Day","14 Day","30 Day","90 Day","Permanent"};
        final long[]   daysMs = {7,14,30,90,0};
        final String[] selType = {types[0]};

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
        shBg.setStroke(dp(ctx, 1), Color.parseColor("#2D1F5A"));
        sheet.setBackground(shBg);
        FrameLayout.LayoutParams shLp = new FrameLayout.LayoutParams(-1, -2);
        shLp.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(shLp);

        View handle = makeHandle(ctx);
        sheet.addView(handle);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("Create New Client Key");
        tvTitle.setTextColor(Color.parseColor(PURPLE));
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(-1, -2);
        ttLp.bottomMargin = dp(ctx, 14);
        tvTitle.setLayoutParams(ttLp);
        sheet.addView(tvTitle);

        // Key input
        final EditText etKey = new EditText(ctx);
        etKey.setHint("Key name (e.g. User123)");
        etKey.setTextColor(Color.WHITE);
        etKey.setHintTextColor(0x66A78BFA);
        etKey.setTextSize(13f);
        etKey.setTypeface(Typeface.MONOSPACE);
        etKey.setSingleLine(true);
        styleEditTextPurple(ctx, etKey);
        etKey.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(hasFocus ? 0x25A78BFA : 0x1AFFFFFF);
                bg.setCornerRadius(dp(ctx, 12));
                bg.setStroke(hasFocus ? dp(ctx, 2) : dp(ctx, 1),
                    hasFocus ? Color.parseColor(PURPLE) : Color.parseColor("#2D1F5A"));
                etKey.setBackground(bg);
                etKey.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
            }
        });
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(-1, dp(ctx, 50));
        etLp.bottomMargin = dp(ctx, 12);
        etKey.setLayoutParams(etLp);
        sheet.addView(etKey);

        // Type picker row
        final LinearLayout typeRow = new LinearLayout(ctx);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        typeRow.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
        GradientDrawable typeBg = new GradientDrawable();
        typeBg.setColor(0x1AFFFFFF);
        typeBg.setCornerRadius(dp(ctx, 12));
        typeBg.setStroke(dp(ctx, 1), Color.parseColor("#2D1F5A"));
        typeRow.setBackground(typeBg);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1, dp(ctx, 48));
        trLp.bottomMargin = dp(ctx, 14);
        typeRow.setLayoutParams(trLp);

        final TextView tvTypeLabel = new TextView(ctx);
        tvTypeLabel.setText("Duration: " + selType[0]);
        tvTypeLabel.setTextColor(Color.WHITE);
        tvTypeLabel.setTextSize(13f);
        tvTypeLabel.setTypeface(Typeface.MONOSPACE);
        tvTypeLabel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView typeArrow = new TextView(ctx);
        typeArrow.setText("▾");
        typeArrow.setTextColor(Color.parseColor(PURPLE));
        typeRow.addView(tvTypeLabel);
        typeRow.addView(typeArrow);
        attachPressScale(typeRow);
        typeRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showSimpleSheet(ctx, "Select Duration", types, selType[0], new SheetCb() {
                    @Override public void onSelected(String val, int idx) {
                        selType[0] = val;
                        tvTypeLabel.setText("Duration: " + val);
                    }
                });
            }
        });
        sheet.addView(typeRow);

        // Buttons
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        final android.app.Dialog df = dialog;
        TextView btnCancel = makeBtn(ctx, "Cancel", "#64748B", 0x0FFFFFFF, "#1E293B");
        btnCancel.setLayoutParams(new LinearLayout.LayoutParams(0, dp(ctx, 48), 1f));
        attachPressScale(btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { df.dismiss(); }
        });
        btnRow.addView(btnCancel);

        View sp = new View(ctx);
        sp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 10), 0));
        btnRow.addView(sp);

        TextView btnCreate = makeBtnSolid(ctx, "✦  Create Key", "#001A2E",
            Color.parseColor(PURPLE));
        btnCreate.setLayoutParams(new LinearLayout.LayoutParams(0, dp(ctx, 48), 1f));
        attachPressScale(btnCreate);
        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String keyName = etKey.getText().toString().trim();
                if (keyName.isEmpty()) { showNeonToast(ctx, "Key name cannot be empty"); return; }
                df.dismiss();
                String typeVal = selType[0];
                long expiredMs = 0;
                if (!typeVal.equals("Permanent")) {
                    int d = typeVal.equals("7 Day") ? 7 : typeVal.equals("14 Day") ? 14
                        : typeVal.equals("30 Day") ? 30 : 90;
                    expiredMs = System.currentTimeMillis() + (long) d * 24 * 60 * 60 * 1000L;
                }
                createClientKey(ctx, keyName, typeVal, expiredMs, content);
            }
        });
        btnRow.addView(btnCreate);
        sheet.addView(btnRow);

        dimRoot.addView(sheet);
        dialog.setContentView(dimRoot);
        sheet.setTranslationY(700f);
        sheet.animate().translationY(0).setDuration(300)
            .setInterpolator(new DecelerateInterpolator(2f)).start();
        dialog.show();

        etKey.post(new Runnable() {
            @Override public void run() {
                etKey.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) ctx
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.showSoftInput(etKey, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private static void showBanSheet(final Context ctx, final String enc, final LinearLayout content) {
        final String[] opts = {"1 Day","3 Days","7 Days","14 Days","30 Days","Permanent"};
        final long[] days   = {1, 3, 7, 14, 30, 365L * 100};

        showSimpleSheet(ctx, "Ban Duration", opts, opts[0], new SheetCb() {
            @Override public void onSelected(String val, int idx) {
                long bu = System.currentTimeMillis() + days[idx] * 24L * 60 * 60 * 1000L;
                doActionBan(ctx, enc, bu, content);
            }
        });
    }

    private static void showExtendSheet(final Context ctx, final String enc, final LinearLayout content) {
        final String[] opts = {"+ 1 Day","+ 3 Days","+ 7 Days","+ 14 Days","+ 30 Days","+ 90 Days"};
        final long[] ms     = {86400000L, 3*86400000L, 7*86400000L, 14*86400000L, 30*86400000L, 90*86400000L};

        showSimpleSheet(ctx, "Extend Duration", opts, opts[2], new SheetCb() {
            @Override public void onSelected(String val, int idx) {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("action", "extend_key");
                    payload.put("target_encoded", enc);
                    payload.put("extend_ms", ms[idx]);
                    ApiClient.getAdminService().postAction(payload.toString())
                        .enqueue(new retrofit2.Callback<String>() {
                            @Override public void onResponse(Call<String> call, Response<String> r) {
                                if (r.isSuccessful()) { showNeonToast(ctx, "Key duration extended successfully");
                                    ActivityLog.log(ctx, "extend_key", enc);
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override public void run() { loadMyClients(ctx, content); }
                                    });
                                } else postToast(ctx, "Request failed — HTTP " + r.code());
                            }
                            @Override public void onFailure(Call<String> call, Throwable t) {
                                postToast(ctx, "An error occurred: " + t.getMessage());
                            }
                        });
                } catch (Exception e) { postToast(ctx, "An error occurred: " + e.getMessage()); }
            }
        });
    }

    // Generic sheet picker
    interface SheetCb { void onSelected(String val, int idx); }

    private static void showSimpleSheet(final Context ctx, final String title,
            final String[] opts, final String current, final SheetCb cb) {
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
        GradientDrawable shBg = new GradientDrawable();
        shBg.setColor(Color.parseColor("#0A1628"));
        shBg.setCornerRadii(new float[]{dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20),0,0,0,0});
        shBg.setStroke(dp(ctx, 1), Color.parseColor("#2D1F5A"));
        sheet.setBackground(shBg);
        FrameLayout.LayoutParams shLp = new FrameLayout.LayoutParams(-1, -2);
        shLp.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(shLp);

        sheet.addView(makeHandle(ctx));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor(PURPLE));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(-1, -2);
        ttLp.bottomMargin = dp(ctx, 12);
        tvTitle.setLayoutParams(ttLp);
        sheet.addView(tvTitle);

        View divider = new View(ctx);
        divider.setBackgroundColor(Color.parseColor("#1A2A3A"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        divLp.bottomMargin = dp(ctx, 8);
        divider.setLayoutParams(divLp);
        sheet.addView(divider);

        for (int i = 0; i < opts.length; i++) {
            final String opt = opts[i];
            final int fi = i;
            boolean sel = opt.equals(current);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, 16), dp(ctx, 13), dp(ctx, 16), dp(ctx, 13));
            if (sel) {
                GradientDrawable rBg = new GradientDrawable();
                rBg.setColor(Color.parseColor("#180A30"));
                rBg.setCornerRadius(dp(ctx, 10));
                rBg.setStroke(dp(ctx, 1), Color.parseColor(PURPLE));
                row.setBackground(rBg);
            }
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
            rLp.bottomMargin = dp(ctx, 4);
            row.setLayoutParams(rLp);

            TextView tvOpt = new TextView(ctx);
            tvOpt.setText(opt);
            tvOpt.setTextColor(sel ? Color.parseColor(PURPLE) : Color.parseColor("#CBD5E1"));
            tvOpt.setTextSize(14f);
            tvOpt.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tvOpt.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(tvOpt);

            if (sel) {
                TextView chk = new TextView(ctx);
                chk.setText("✓");
                chk.setTextColor(Color.parseColor(PURPLE));
                chk.setTextSize(16f);
                row.addView(chk);
            }

            attachPressScale(row);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    df.dismiss();
                    if (cb != null) cb.onSelected(opt, fi);
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

    private static void showConfirmDialog(final Context ctx, String title, String msg,
            String accentHex, final Runnable onConfirm) {
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

        int ac; try { ac = Color.parseColor(accentHex); } catch (Exception e) { ac = Color.parseColor(PURPLE); }
        final int acF = ac;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 22), dp(ctx, 22), dp(ctx, 22), dp(ctx, 22));
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(Color.parseColor("#0A1628"));
        cBg.setCornerRadius(dp(ctx, 18));
        cBg.setStroke(dp(ctx, 1), ac);
        card.setBackground(cBg);

        TextView tvT = new TextView(ctx);
        tvT.setText(title);
        tvT.setTextColor(Color.WHITE);
        tvT.setTextSize(16f);
        tvT.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(-1, -2);
        ttLp.bottomMargin = dp(ctx, 10);
        tvT.setLayoutParams(ttLp);
        card.addView(tvT);

        View div = new View(ctx);
        div.setBackgroundColor(0x2222E5FF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        divLp.bottomMargin = dp(ctx, 12);
        div.setLayoutParams(divLp);
        card.addView(div);

        TextView tvM = new TextView(ctx);
        tvM.setText(msg);
        tvM.setTextColor(Color.parseColor("#94A3B8"));
        tvM.setTextSize(13f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.bottomMargin = dp(ctx, 18);
        tvM.setLayoutParams(msgLp);
        card.addView(tvM);

        LinearLayout bRow = new LinearLayout(ctx);
        final android.app.Dialog df = dialog;

        TextView bCancel = makeBtn(ctx, "Cancel", "#64748B", 0x0FFFFFFF, "#1E293B");
        bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, dp(ctx, 44), 1f));
        attachPressScale(bCancel);
        bCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { df.dismiss(); }
        });
        bRow.addView(bCancel);

        View sp = new View(ctx); sp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 10), 0));
        bRow.addView(sp);

        TextView bOk = new TextView(ctx);
        bOk.setText("✅ Confirm");
        bOk.setTextColor(Color.parseColor("#001A2E"));
        bOk.setTextSize(13f);
        bOk.setTypeface(Typeface.DEFAULT_BOLD);
        bOk.setGravity(Gravity.CENTER);
        bOk.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
        GradientDrawable okBg = new GradientDrawable();
        okBg.setColor(acF);
        okBg.setCornerRadius(dp(ctx, 10));
        bOk.setBackground(okBg);
        bOk.setLayoutParams(new LinearLayout.LayoutParams(0, dp(ctx, 44), 1f));
        attachPressScale(bOk);
        bOk.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                df.dismiss();
                if (onConfirm != null) onConfirm.run();
            }
        });
        bRow.addView(bOk);
        card.addView(bRow);

        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cLp.leftMargin = dp(ctx, 24); cLp.rightMargin = dp(ctx, 24);
        dimRoot.addView(card, cLp);
        dialog.setContentView(dimRoot);
        card.setAlpha(0f); card.setScaleX(0.9f); card.setScaleY(0.9f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        dialog.show();
    }

    // ===================== API CALLS =====================
    private static void createClientKey(final Context ctx, String keyName, String type,
            long expired, final LinearLayout content) {
        try {
            JSONObject data = new JSONObject();
            data.put("plain_key", keyName);
            data.put("type", type);
            data.put("expired", expired);
            data.put("device_limit", 1);
            data.put("role", "Client");

            JSONObject payload = new JSONObject();
            payload.put("action", "create_key");
            payload.put("data", data);

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (response.isSuccessful()) {
                            showNeonToast(ctx, "Client key created successfully");
                            ActivityLog.log(ctx, "create_key", keyName);
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() { loadMyClients(ctx, content); }
                            });
                        } else {
                            postToast(ctx, "Request failed — HTTP " + response.code());
                        }
                    }
                    @Override public void onFailure(Call<String> call, Throwable t) {
                        postToast(ctx, "An error occurred: " + t.getMessage());
                    }
                });
        } catch (Exception e) { postToast(ctx, "An error occurred: " + e.getMessage()); }
    }

    private static void doAction(final Context ctx, final String action,
            final String enc, final long extra, final LinearLayout content) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", action);
            payload.put("target_encoded", enc);

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (response.isSuccessful()) {
                            showNeonToast(ctx, action + " completed successfully");
                            ActivityLog.log(ctx, action, enc);
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() { loadMyClients(ctx, content); }
                            });
                        } else {
                            postToast(ctx, "Failed to execute " + action + " — HTTP " + response.code());
                        }
                    }
                    @Override public void onFailure(Call<String> call, Throwable t) {
                        postToast(ctx, "An error occurred: " + t.getMessage());
                    }
                });
        } catch (Exception e) { postToast(ctx, "An error occurred: " + e.getMessage()); }
    }

    private static void doActionBan(final Context ctx, final String enc,
            final long bannedUntil, final LinearLayout content) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", "ban_key");
            payload.put("target_encoded", enc);
            payload.put("banned_until", bannedUntil);

            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override
                    public void onResponse(Call<String> call, Response<String> response) {
                        if (response.isSuccessful()) {
                            showNeonToast(ctx, "Key has been suspended");
                            ActivityLog.log(ctx, "ban_key", enc);
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() { loadMyClients(ctx, content); }
                            });
                        } else postToast(ctx, "Request failed — HTTP " + response.code());
                    }
                    @Override public void onFailure(Call<String> call, Throwable t) {
                        postToast(ctx, "An error occurred: " + t.getMessage());
                    }
                });
        } catch (Exception e) { postToast(ctx, "An error occurred: " + e.getMessage()); }
    }

    // ===================== UI BUILDERS =====================
    // ── CSV Export per Reseller ─────────────────────────────────────
    private static void exportClientsCsv(final Context ctx, final JSONArray keys) {
        try {
            long now = System.currentTimeMillis();
            StringBuilder csv = new StringBuilder();
            csv.append("Key Name,Type,Status,Expires,Devices,Device Limit,Violations,Banned Until,Created At\n");
            for (int i = 0; i < keys.length(); i++) {
                try {
                    JSONObject obj = keys.getJSONObject(i);
                    String raw = obj.optString("key","");
                    String keyName = raw;
                    try { byte[] d = android.util.Base64.decode(raw, android.util.Base64.DEFAULT);
                        keyName = new String(d, java.nio.charset.Charset.forName("UTF-8")); } catch(Exception ignored){}
                    String type    = obj.optString("type","—");
                    long exp       = obj.optLong("expired",0);
                    long banned    = obj.optLong("banned_until",0);
                    int devCount   = obj.optJSONArray("devices")!=null?obj.optJSONArray("devices").length():0;
                    int devLimit   = obj.optInt("device_limit",1);
                    int viol       = obj.optInt("violation_count",0);
                    long createdAt = obj.optLong("created_at",0);
                    String status     = banned>now?"Banned":exp>0&&exp<now?"Expired":"Active";
                    String expiresStr = exp==0?"Permanent":new java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date(exp));
                    String bannedStr  = banned>now?new java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date(banned)):"—";
                    String createdStr = createdAt>0?new java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date(createdAt)):"—";
                    if (keyName.contains(",")) keyName = "\"" + keyName + "\"";
                    csv.append(keyName+","+type+","+status+","+expiresStr+","+devCount+","+devLimit+","+viol+","+bannedStr+","+createdStr+"\n");
                } catch(Exception ignored){}
            }
            final String csvContent = csv.toString();
            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",java.util.Locale.getDefault()).format(new java.util.Date());
            final String fileName = "xyper_my_clients_"+ts+".csv";
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv");
                cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                android.net.Uri uri = ctx.getContentResolver().insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try (java.io.OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                        if (os != null) os.write(csvContent.getBytes("UTF-8"));
                    }
                    cv.clear(); cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    ctx.getContentResolver().update(uri, cv, null, null);
                    showNeonToast(ctx, "📊 CSV saved to Downloads/" + fileName);
                }
            } else {
                java.io.File file = new java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), fileName);
                java.io.FileWriter fw = new java.io.FileWriter(file);
                fw.write(csvContent); fw.close();
                showNeonToast(ctx, "📊 CSV saved to Downloads/" + fileName);
            }
        } catch (Exception e) {
            showNeonToast(ctx, "Export failed: " + e.getMessage());
        }
    }

    private static View buildHeader(Context ctx, final LinearLayout content) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 14);
        header.setLayoutParams(lp);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        // Role badge
        TextView badge = new TextView(ctx);
        badge.setText("  RESELLER PANEL  ");
        badge.setTextColor(Color.parseColor(PURPLE));
        badge.setTextSize(10f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setLetterSpacing(0.12f);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#150E2A"));
        badgeBg.setCornerRadius(dp(ctx, 20));
        badgeBg.setStroke(dp(ctx, 1), Color.parseColor("#4C1D95"));
        badge.setBackground(badgeBg);
        badge.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.bottomMargin = dp(ctx, 4);
        badge.setLayoutParams(badgeLp);
        textWrap.addView(badge);

        String resellerKey = ApiAuthHelper.getApiKey(ctx);
        String displayKey = "Reseller";
        if (resellerKey != null) {
            try {
                byte[] dec = android.util.Base64.decode(resellerKey, android.util.Base64.DEFAULT);
                displayKey = new String(dec, Charset.forName("UTF-8"));
            } catch (Exception ignored) { displayKey = resellerKey; }
        }

        TextView tvName = new TextView(ctx);
        tvName.setText(displayKey);
        tvName.setTextColor(Color.parseColor("#E2E8F0"));
        tvName.setTextSize(18f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            tvName.setShadowLayer(10f, 0, 0, Color.parseColor("#7C3AED"));
        }
        textWrap.addView(tvName);

        TextView tvSub = new TextView(ctx);
        tvSub.setText("Manage your client keys");
        tvSub.setTextColor(Color.parseColor("#475569"));
        tvSub.setTextSize(11f);
        tvSub.setTypeface(Typeface.MONOSPACE);
        textWrap.addView(tvSub);

        header.addView(textWrap);

        // Refresh button — rightMargin to avoid overlap with FAB
        TextView btnRef = new TextView(ctx);
        btnRef.setText("↻");
        btnRef.setTextSize(20f);
        btnRef.setTextColor(Color.parseColor(PURPLE));
        btnRef.setGravity(Gravity.CENTER);
        btnRef.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
        GradientDrawable refBg = new GradientDrawable();
        refBg.setColor(0x15A78BFA);
        refBg.setCornerRadius(dp(ctx, 10));
        refBg.setStroke(dp(ctx, 1), Color.parseColor("#2D1F5A"));
        btnRef.setBackground(refBg);
        LinearLayout.LayoutParams refLp = new LinearLayout.LayoutParams(-2, -2);
        refLp.rightMargin = dp(ctx, 54);
        btnRef.setLayoutParams(refLp);
        attachPressScale(btnRef);
        btnRef.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadMyClients(ctxGlobal, content); }
        });
        header.addView(btnRef);
        return header;
    }

    private static LinearLayout buildSkeleton(Context ctx) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        // Stats skeleton
        LinearLayout statsRow = new LinearLayout(ctx);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(-1, dp(ctx, 80));
        srLp.bottomMargin = dp(ctx, 10);
        statsRow.setLayoutParams(srLp);
        for (int i = 0; i < 4; i++) {
            View box = new View(ctx);
            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setColor(Color.parseColor("#0B1426"));
            boxBg.setCornerRadius(dp(ctx, 14));
            boxBg.setStroke(dp(ctx, 1), Color.parseColor("#1E293B"));
            box.setBackground(boxBg);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0, -1, 1f);
            boxLp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3));
            box.setLayoutParams(boxLp);
            statsRow.addView(box);
        }
        wrap.addView(statsRow);

        // Card skeletons
        for (int i = 0; i < 2; i++) {
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.parseColor("#0B1426"));
            cBg.setCornerRadius(dp(ctx, 16));
            cBg.setStroke(dp(ctx, 1), Color.parseColor("#1E293B"));
            card.setBackground(cBg);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, -2);
            cLp.topMargin = dp(ctx, 8);
            card.setLayoutParams(cLp);
            card.addView(makeSkelLine(ctx, 0.55f));
            card.addView(makeSkelLine(ctx, 0.8f));
            card.addView(makeSkelLine(ctx, 0.65f));
            wrap.addView(card);
        }

        final LinearLayout wFinal = wrap;
        ValueAnimator sh = ValueAnimator.ofFloat(0.3f, 0.7f);
        sh.setDuration(900); sh.setRepeatMode(ValueAnimator.REVERSE);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, dp(ctx, 10));
        lp.bottomMargin = dp(ctx, 8);
        v.setLayoutParams(lp);
        return v;
    }

    private static View buildMiniChart(final Context ctx,
            final int active, final int expired, final int banned, final int total) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx,14), dp(ctx,12), dp(ctx,14), dp(ctx,12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#0B1426"));
        cardBg.setCornerRadius(dp(ctx,14));
        cardBg.setStroke(dp(ctx,1), Color.parseColor("#1E3A50"));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1,-2);
        cLp.bottomMargin = dp(ctx,10); card.setLayoutParams(cLp);

        // Title
        TextView title = new TextView(ctx);
        title.setText("📊  Client Distribution");
        title.setTextColor(Color.parseColor(CYAN));
        title.setTextSize(12f); title.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1,-2);
        tLp.bottomMargin = dp(ctx,10); title.setLayoutParams(tLp);
        card.addView(title);

        // Bar chart
        LinearLayout barRow = new LinearLayout(ctx);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(ctx,24)));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.parseColor("#0D1F38"));
        barBg.setCornerRadius(dp(ctx,6));
        barRow.setBackground(barBg);
        barRow.setClipToOutline(true);

        // Active bar
        if (active > 0) {
            View barActive = new View(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(GREEN));
            barActive.setBackground(bg);
            barRow.addView(barActive, new LinearLayout.LayoutParams(0, -1, (float)active/total));
        }
        // Expired bar
        if (expired > 0) {
            View barExp = new View(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(YELLOW));
            barExp.setBackground(bg);
            barRow.addView(barExp, new LinearLayout.LayoutParams(0, -1, (float)expired/total));
        }
        // Banned bar
        if (banned > 0) {
            View barBan = new View(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(RED));
            barBan.setBackground(bg);
            barRow.addView(barBan, new LinearLayout.LayoutParams(0, -1, (float)banned/total));
        }
        card.addView(barRow);

        // Legend row
        LinearLayout legend = new LinearLayout(ctx);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams legLp = new LinearLayout.LayoutParams(-1,-2);
        legLp.topMargin = dp(ctx,8); 
        legend.setLayoutParams(legLp);
        legend.addView(buildLegendDot(ctx, GREEN,  "Active "  +active));
        legend.addView(buildLegendDot(ctx, YELLOW, " Expired "+expired));
        legend.addView(buildLegendDot(ctx, RED,    " Banned " +banned));
        card.addView(legend);

        // Animate bars with ValueAnimator
        final View[] bars = new View[barRow.getChildCount()];
        for (int i = 0; i < barRow.getChildCount(); i++) bars[i] = barRow.getChildAt(i);
        for (final View bar : bars) {
            bar.setScaleX(0f);
            bar.setPivotX(0f);
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(800);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    bar.setScaleX((float)a.getAnimatedValue());
                }
            });
            anim.start();
        }

        return card;
    }

    private static View buildLegendDot(Context ctx, String color, String label) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color)); bg.setShape(GradientDrawable.OVAL);
        dot.setBackground(bg);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(ctx,8), dp(ctx,8));
        dLp.rightMargin = dp(ctx,4); dot.setLayoutParams(dLp);
        row.addView(dot);
        TextView tv = new TextView(ctx);
        tv.setText(label); tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(11f); tv.setTypeface(Typeface.MONOSPACE);
        row.addView(tv);
        return row;
    }

        private static LinearLayout buildStatBox(Context ctx, String val, String label, String colorHex) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(ctx, 6), dp(ctx, 14), dp(ctx, 6), dp(ctx, 14));
        int c; try { c = Color.parseColor(colorHex); } catch (Exception e) { c = Color.WHITE; }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(20, Color.red(c), Color.green(c), Color.blue(c)));
        bg.setCornerRadius(dp(ctx, 14));
        bg.setStroke(dp(ctx, 1), Color.argb(60, Color.red(c), Color.green(c), Color.blue(c)));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3));
        box.setLayoutParams(lp);

        TextView tvVal = new TextView(ctx);
        tvVal.setText(val);
        tvVal.setTextColor(c);
        tvVal.setTextSize(20f);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setGravity(Gravity.CENTER);
        box.addView(tvVal);

        TextView tvLbl = new TextView(ctx);
        tvLbl.setText(label);
        tvLbl.setTextColor(Color.parseColor("#475569"));
        tvLbl.setTextSize(10f);
        tvLbl.setGravity(Gravity.CENTER);
        tvLbl.setTypeface(Typeface.MONOSPACE);
        box.addView(tvLbl);
        return box;
    }

    private static void showEmptyState(Context ctx, LinearLayout content) {
        LinearLayout empty = new LinearLayout(ctx);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(-1, dp(ctx, 200));
        eLp.topMargin = dp(ctx, 20);
        empty.setLayoutParams(eLp);
        TextView icon = new TextView(ctx);
        icon.setText("📭"); icon.setTextSize(40f); icon.setGravity(Gravity.CENTER);
        empty.addView(icon);
        TextView tv = new TextView(ctx);
        tv.setText("No clients yet.\nTap ＋ to create a new key.");
        tv.setTextColor(Color.parseColor("#475569")); tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER); tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(-1, -2);
        tvLp.topMargin = dp(ctx, 8); tv.setLayoutParams(tvLp);
        empty.addView(tv);
        content.addView(empty);
    }

    private static void showErrorState(Context ctx, LinearLayout content) {
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
        TextView eIcon = new TextView(ctx); eIcon.setText("⚠️"); eIcon.setTextSize(36f);
        eIcon.setGravity(Gravity.CENTER); err.addView(eIcon);
        TextView eMsg = new TextView(ctx); eMsg.setText("Failed to load data");
        eMsg.setTextColor(Color.parseColor(RED)); eMsg.setTextSize(13f);
        eMsg.setGravity(Gravity.CENTER); eMsg.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams eMsgLp = new LinearLayout.LayoutParams(-1, -2);
        eMsgLp.topMargin = dp(ctx, 10); eMsgLp.bottomMargin = dp(ctx, 14);
        eMsg.setLayoutParams(eMsgLp); err.addView(eMsg);
        final LinearLayout contentFinal = content;
        TextView btnRetry = new TextView(ctx); btnRetry.setText("↻  Coba Lagi");
        btnRetry.setTextColor(Color.parseColor(PURPLE)); btnRetry.setTextSize(13f);
        btnRetry.setTypeface(Typeface.DEFAULT_BOLD); btnRetry.setGravity(Gravity.CENTER);
        btnRetry.setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10));
        GradientDrawable rBg = new GradientDrawable(); rBg.setColor(0x15A78BFA);
        rBg.setCornerRadius(dp(ctx, 10)); rBg.setStroke(dp(ctx, 1), Color.parseColor("#2D1F5A"));
        btnRetry.setBackground(rBg);
        attachPressScale(btnRetry);
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadMyClients(ctxGlobal, contentFinal); }
        });
        err.addView(btnRetry);
        content.addView(err);
    }

    private static View buildBanner(Context ctx, String msg, String textColor,
                                     String bgColor, String strokeColor) {
        TextView tv = new TextView(ctx);
        tv.setText(msg);
        tv.setTextColor(Color.parseColor(textColor));
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(ctx,14), dp(ctx,10), dp(ctx,14), dp(ctx,10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgColor));
        bg.setCornerRadius(dp(ctx,12));
        bg.setStroke(dp(ctx,1), Color.parseColor(strokeColor));
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static TextView buildSectionTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#334155"));
        tv.setTextSize(10f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(ctx, 16); lp.bottomMargin = dp(ctx, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static TextView buildInfoLine(Context ctx, String label, String value, String color) {
        TextView tv = new TextView(ctx);
        tv.setText(label + ": " + value);
        try { tv.setTextColor(Color.parseColor(color)); }
        catch (Exception e) { tv.setTextColor(Color.parseColor("#94A3B8")); }
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 2); tv.setLayoutParams(lp);
        return tv;
    }

    private static View buildDivider(Context ctx) {
        View v = new View(ctx); v.setBackgroundColor(0x1F22E5FF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(ctx, 1));
        lp.topMargin = dp(ctx, 6); lp.bottomMargin = dp(ctx, 8);
        v.setLayoutParams(lp); return v;
    }

    private static TextView makeBtn(Context ctx, String text, String tc, String bc, String brc) {
        TextView btn = new TextView(ctx); btn.setText(text); btn.setTextSize(11f);
        btn.setTextColor(Color.parseColor(tc)); btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER); btn.setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor(bc));
        bg.setCornerRadius(dp(ctx, 8)); bg.setStroke(dp(ctx, 1), Color.parseColor(brc));
        btn.setBackground(bg); btn.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return btn;
    }

    private static TextView makeBtn(Context ctx, String text, String tc, int bgColor, String brc) {
        TextView btn = new TextView(ctx); btn.setText(text); btn.setTextSize(11f);
        btn.setTextColor(Color.parseColor(tc)); btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER); btn.setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(bgColor);
        bg.setCornerRadius(dp(ctx, 8)); bg.setStroke(dp(ctx, 1), Color.parseColor(brc));
        btn.setBackground(bg); btn.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return btn;
    }

    private static TextView makeBtnSolid(Context ctx, String text, String tc, int bgColor) {
        TextView btn = new TextView(ctx); btn.setText(text); btn.setTextSize(13f);
        btn.setTextColor(Color.parseColor(tc)); btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER); btn.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(bgColor);
        bg.setCornerRadius(dp(ctx, 12)); btn.setBackground(bg);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return btn;
    }

    private static View makeHandle(Context ctx) {
        View h = new View(ctx);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setColor(Color.parseColor("#2D1F5A")); hBg.setCornerRadius(dp(ctx, 4));
        h.setBackground(hBg);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 4));
        hLp.gravity = Gravity.CENTER_HORIZONTAL; hLp.bottomMargin = dp(ctx, 16);
        h.setLayoutParams(hLp);
        return h;
    }

    private static void styleEditTextPurple(Context ctx, EditText et) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF); bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 1), Color.parseColor("#2D1F5A"));
        et.setBackground(bg); et.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
    }

    private static void addSpacer(LinearLayout row, Context ctx) {
        View sp = new View(ctx); sp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 6), 0));
        row.addView(sp);
    }

    private static void animateIn(final View v, Context ctx, int delayMs) {
        v.setAlpha(0f); v.setTranslationY(dp(ctx, 14));
        v.postDelayed(new Runnable() {
            @Override public void run() {
                v.animate().alpha(1f).translationY(0f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator()).start();
            }
        }, delayMs);
    }

    private static void attachPressScale(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.95f).scaleY(0.95f)
                            .setDuration(70).setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
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
                bg.setStroke(dp(ctx, 2), Color.parseColor("#7C3AED"));
                layout.setBackground(bg);
                TextView tv = new TextView(ctx); tv.setText(msg); tv.setTextSize(13f);
                tv.setTextColor(Color.parseColor(PURPLE));
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    tv.setShadowLayer(6f, 0f, 0f, Color.parseColor("#4C1D95"));
                }
                layout.addView(tv);
                Toast toast = new Toast(ctx);
                toast.setDuration(Toast.LENGTH_SHORT); toast.setView(layout);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(ctx, 100));
                toast.show();
            }
        });
    }

    private static void postToast(final Context ctx, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { showNeonToast(ctx, msg); }
        });
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            .format(new Date(millis));
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }
}
