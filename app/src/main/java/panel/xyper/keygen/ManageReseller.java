package panel.xyper.keygen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * ManageReseller — Developer panel untuk manage dan monitor semua Reseller
 * Menampilkan: list reseller, stats per reseller, key client mereka, aksi ban/unban/delete
 */
public class ManageReseller {

    private static final int ACCENT    = 0xFFA78BFA; // purple
    private static final int CYAN      = 0xFF22E5FF;
    private static final int GREEN     = 0xFF34D399;
    private static final int RED       = 0xFFF87171;
    private static final int YELLOW    = 0xFFFBBF24;
    private static final int BG_CARD   = 0xFF0B1426;
    private static final int BG_DARK   = 0xFF060D1E;

    private static JSONArray allKeysCache = null;
    private static LinearLayout listContainerRef = null;
    private static Handler autoRefreshHandler = null;
    private static Runnable autoRefreshRunnable = null;
    private static final int AUTO_REFRESH_MS = 5 * 60 * 1000;

    public static View build(final Context ctx) {
        allKeysCache = null;
        listContainerRef = null;

        FrameLayout root = new FrameLayout(ctx);
        AnimatedBackgroundView bgView = new AnimatedBackgroundView(ctx);
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(ctx);
        overlay.setBackgroundColor(0x55000000);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx,12), dp(ctx,12), dp(ctx,12), dp(ctx,80));
        scroll.addView(content, new LinearLayout.LayoutParams(-1, -2));

        // Header
        content.addView(buildHeader(ctx));

        // Skeleton
        final LinearLayout skeleton = buildSkeleton(ctx);
        content.addView(skeleton);

        // List container
        final LinearLayout listContainer = new LinearLayout(ctx);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainerRef = listContainer;
        content.addView(listContainer);

        loadData(ctx, skeleton, listContainer);

        // Auto-refresh
        if (autoRefreshHandler != null && autoRefreshRunnable != null)
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        autoRefreshHandler = new Handler(Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override public void run() {
                if (listContainerRef == null) return;
                allKeysCache = null;
                listContainerRef.removeAllViews();
                LinearLayout skel = buildSkeleton(ctx);
                listContainerRef.addView(skel);
                loadData(ctx, skel, listContainerRef);
                autoRefreshHandler.postDelayed(this, AUTO_REFRESH_MS);
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);

        return root;
    }

    // ── Header ─────────────────────────────────────────────────────
    private static View buildHeader(final Context ctx) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,14);
        header.setLayoutParams(lp);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

        TextView title = new TextView(ctx);
        title.setText("Manage Reseller");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setShadowLayer(14f, 0f, 0f, ACCENT);

        TextView sub = new TextView(ctx);
        sub.setText("Xyper Xit  ·  Reseller Control");
        sub.setTextSize(10f);
        sub.setTextColor(Color.parseColor("#A78BFA"));
        sub.setTypeface(Typeface.MONOSPACE);

        textWrap.addView(title);
        textWrap.addView(sub);
        header.addView(textWrap);

        // Refresh button
        TextView btnRefresh = new TextView(ctx);
        btnRefresh.setText("\uE5D5"); // Material refresh icon
        btnRefresh.setTextSize(20f);
        btnRefresh.setTextColor(ACCENT);
        btnRefresh.setGravity(Gravity.CENTER);
        try { btnRefresh.setTypeface(Typeface.createFromAsset(ctx.getAssets(), "MaterialIcons-Regular.ttf")); }
        catch (Exception ignored) {}
        btnRefresh.setText("↻  Refresh");
        btnRefresh.setTextSize(12f);
        btnRefresh.setTypeface(Typeface.DEFAULT_BOLD);
        btnRefresh.setPadding(dp(ctx,12), dp(ctx,6), dp(ctx,12), dp(ctx,6));
        GradientDrawable rbBg = new GradientDrawable();
        rbBg.setColor(0x1AA78BFA);
        rbBg.setCornerRadius(dp(ctx,10));
        rbBg.setStroke(dp(ctx,1), ACCENT);
        btnRefresh.setBackground(rbBg);
        // rightMargin to avoid overlap with FAB
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(-2, -2);
        rbLp.rightMargin = dp(ctx,54);
        btnRefresh.setLayoutParams(rbLp);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (listContainerRef == null) return;
                allKeysCache = null;
                listContainerRef.removeAllViews();
                LinearLayout skel = buildSkeleton(ctx);
                listContainerRef.addView(skel);
                loadData(ctx, skel, listContainerRef);
            }
        });
        header.addView(btnRefresh);
        return header;
    }

    // ── Load Data ──────────────────────────────────────────────────
    private static void loadData(final Context ctx, final LinearLayout skeleton, final LinearLayout list) {
        ApiClient.getAdminService().getAllKeys().enqueue(new retrofit2.Callback<String>() {
            @Override public void onResponse(Call<String> call, Response<String> response) {
                if (!response.isSuccessful()) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                        if (skeleton != null) skeleton.setVisibility(View.GONE);
                        showEmpty(ctx, list, "Failed to load data\nHTTP " + response.code());
                    }});
                    return;
                }
                try {
                    final JSONArray arr = new JSONArray(response.body());
                    allKeysCache = arr;
                    new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                        if (skeleton != null) skeleton.setVisibility(View.GONE);
                        buildResellerList(ctx, list, arr);
                    }});
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                        if (skeleton != null) skeleton.setVisibility(View.GONE);
                        showEmpty(ctx, list, "Failed to parse response");
                    }});
                }
            }
            @Override public void onFailure(Call<String> call, Throwable t) {
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                    if (skeleton != null) skeleton.setVisibility(View.GONE);
                    showEmpty(ctx, list, "No internet connection");
                }});
            }
        });
    }

    // ── Build Reseller List ────────────────────────────────────────
    private static void buildResellerList(final Context ctx, final LinearLayout container, JSONArray allKeys) {
        container.removeAllViews();
        long now = System.currentTimeMillis();

        // Pisahkan Reseller keys dan Client keys, grup by owner
        List<JSONObject> resellers = new ArrayList<>();
        LinkedHashMap<String, List<JSONObject>> clientsByOwner = new LinkedHashMap<>();

        for (int i = 0; i < allKeys.length(); i++) {
            try {
                JSONObject obj = allKeys.getJSONObject(i);
                String role = obj.optString("role", "");
                if ("Reseller".equals(role)) {
                    resellers.add(obj);
                } else if ("Client".equals(role)) {
                    String ownerKey = obj.optString("owner_key", "unknown");
                    if (!clientsByOwner.containsKey(ownerKey)) clientsByOwner.put(ownerKey, new ArrayList<>());
                    clientsByOwner.get(ownerKey).add(obj);
                }
            } catch (Exception ignored) {}
        }

        if (resellers.isEmpty()) {
            showEmpty(ctx, container, "No Resellers found\nCreate a Reseller key first");
            return;
        }

        // Summary card
        container.addView(buildSummaryCard(ctx, resellers.size(), clientsByOwner, now));

        // Per-reseller card
        for (int i = 0; i < resellers.size(); i++) {
            final JSONObject resellerObj = resellers.get(i);
            final String resellerEnc = resellerObj.optString("key", "");
            List<JSONObject> clients = clientsByOwner.containsKey(resellerEnc)
                ? clientsByOwner.get(resellerEnc) : new ArrayList<JSONObject>();

            final View card = buildResellerCard(ctx, resellerObj, clients, i + 1, now);
            container.addView(card);
            card.setAlpha(0f);
            card.setTranslationY(dp(ctx, 18));
            final int delay = i * 80;
            card.postDelayed(new Runnable() {
                @Override public void run() {
                    card.animate().alpha(1f).translationY(0f).setDuration(280)
                        .setInterpolator(new DecelerateInterpolator()).start();
                }
            }, delay);
        }
    }

    // ── Summary Card ───────────────────────────────────────────────
    private static View buildSummaryCard(Context ctx, int totalResellers,
            LinkedHashMap<String, List<JSONObject>> clientsByOwner, long now) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(ctx,14), dp(ctx,14), dp(ctx,14), dp(ctx,14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AA78BFA);
        bg.setCornerRadius(dp(ctx,16));
        bg.setStroke(dp(ctx,1), ACCENT);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,14);
        card.setLayoutParams(lp);

        int totalClients = 0;
        for (List<JSONObject> cl : clientsByOwner.values()) totalClients += cl.size();

        String[][] stats = {
            {String.valueOf(totalResellers), "Resellers", "#A78BFA"},
            {String.valueOf(totalClients), "Total Clients", "#22E5FF"},
        };
        for (String[] s : stats) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

            TextView tvNum = new TextView(ctx);
            tvNum.setText(s[0]);
            tvNum.setTextSize(28f);
            tvNum.setTypeface(Typeface.DEFAULT_BOLD);
            tvNum.setTextColor(Color.parseColor(s[2]));
            tvNum.setGravity(Gravity.CENTER);
            col.addView(tvNum);

            TextView tvLabel = new TextView(ctx);
            tvLabel.setText(s[1]);
            tvLabel.setTextSize(11f);
            tvLabel.setTextColor(0xAAFFFFFF);
            tvLabel.setTypeface(Typeface.MONOSPACE);
            tvLabel.setGravity(Gravity.CENTER);
            col.addView(tvLabel);

            card.addView(col);
        }
        return card;
    }

    // ── Reseller Card ──────────────────────────────────────────────
    private static View buildResellerCard(final Context ctx, final JSONObject obj,
            final List<JSONObject> clients, int index, final long now) {
        final String encoded = obj.optString("key", "");
        String plain = decode(encoded);
        final long bannedUntil = obj.optLong("banned_until", 0L);
        final boolean isBanned = bannedUntil > now;
        final long expired = obj.optLong("expired", 0L);
        final boolean isExpired = expired > 0 && expired < now;
        int devCount = obj.optJSONArray("devices") != null ? obj.optJSONArray("devices").length() : 0;

        // Per-client stats
        int cTotal = clients.size(), cActive = 0, cExpired = 0, cBanned = 0;
        for (JSONObject c : clients) {
            long cBan = c.optLong("banned_until", 0L);
            long cExp = c.optLong("expired", 0L);
            if (cBan > now) cBanned++;
            else if (cExp > 0 && cExp < now) cExpired++;
            else cActive++;
        }

        int statusColor = isBanned ? RED : isExpired ? YELLOW : GREEN;
        String statusLabel = isBanned ? "BANNED" : isExpired ? "EXPIRED" : "ACTIVE";

        final LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx,14), dp(ctx,14), dp(ctx,14), dp(ctx,14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(BG_CARD);
        cardBg.setCornerRadius(dp(ctx,16));
        cardBg.setStroke(dp(ctx,1), isBanned ? 0x80F87171 : 0x44A78BFA);
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2);
        cardLp.bottomMargin = dp(ctx,10);
        card.setLayoutParams(cardLp);

        // Top row: index + name + status badge
        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1,-2);
        trLp.bottomMargin = dp(ctx,10);
        topRow.setLayoutParams(trLp);

        TextView tvIdx = new TextView(ctx);
        tvIdx.setText("#" + index + "  ");
        tvIdx.setTextColor(0x55FFFFFF);
        tvIdx.setTextSize(12f);
        tvIdx.setTypeface(Typeface.MONOSPACE);
        topRow.addView(tvIdx);

        TextView tvName = new TextView(ctx);
        tvName.setText(plain);
        tvName.setTextColor(ACCENT);
        tvName.setTextSize(14f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        topRow.addView(tvName);

        TextView tvStatus = makeBadge(ctx, statusLabel, statusColor);
        topRow.addView(tvStatus);
        card.addView(topRow);

        // Divider
        card.addView(buildDivider(ctx));

        // Stats row
        LinearLayout statsRow = new LinearLayout(ctx);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(-1,-2);
        srLp.bottomMargin = dp(ctx,10);
        statsRow.setLayoutParams(srLp);

        String[][] statCols = {
            {String.valueOf(cTotal),   "Clients",  "#A78BFA"},
            {String.valueOf(cActive),  "Active",   "#34D399"},
            {String.valueOf(cExpired), "Expired",  "#FBBF24"},
            {String.valueOf(cBanned),  "Banned",   "#F87171"},
            {String.valueOf(devCount), "Devices",  "#94A3B8"},
        };
        for (String[] sc : statCols) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

            TextView n = new TextView(ctx);
            n.setText(sc[0]);
            n.setTextSize(18f);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            n.setTextColor(Color.parseColor(sc[2]));
            n.setGravity(Gravity.CENTER);
            col.addView(n);

            TextView l = new TextView(ctx);
            l.setText(sc[1]);
            l.setTextSize(9f);
            l.setTextColor(0x88FFFFFF);
            l.setTypeface(Typeface.MONOSPACE);
            l.setGravity(Gravity.CENTER);
            col.addView(l);

            statsRow.addView(col);
        }
        card.addView(statsRow);

        // Action row
        card.addView(buildDivider(ctx));
        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(-1,-2);
        arLp.bottomMargin = dp(ctx,8);
        actionRow.setLayoutParams(arLp);

        // Ban/Unban
        TextView btnBan = isBanned
            ? makeBtn(ctx, "✅ Unban", "#22E5FF", "#051520", "#0A3040")
            : makeBtn(ctx, "⛔ Ban", "#F87171", "#1A0808", "#7F1D1D");
        btnBan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isBanned) doAction(ctx, "unban_key", encoded, null);
                else doAction(ctx, "ban_key", encoded, null);
            }
        });
        LinearLayout.LayoutParams banLp = new LinearLayout.LayoutParams(0,-2,1f);
        banLp.rightMargin = dp(ctx,4); btnBan.setLayoutParams(banLp);
        actionRow.addView(btnBan);

        // Revoke devices
        TextView btnRevoke = makeBtn(ctx, "📵 Revoke", "#FBBF24", "#1A1208", "#78350F");
        btnRevoke.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                doAction(ctx, "revoke_devices", encoded, null);
            }
        });
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(0,-2,1f);
        rvLp.rightMargin = dp(ctx,4); btnRevoke.setLayoutParams(rvLp);
        actionRow.addView(btnRevoke);

        // Delete
        TextView btnDel = makeBtn(ctx, "🗑 Delete", "#F87171", "#1A0808", "#7F1D1D");
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doAction(ctx, "delete_key", encoded, null); }
        });
        btnDel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        actionRow.addView(btnDel);
        card.addView(actionRow);

        // Toggle client list
        if (!clients.isEmpty()) {
            final LinearLayout clientsContainer = new LinearLayout(ctx);
            clientsContainer.setOrientation(LinearLayout.VERTICAL);
            clientsContainer.setVisibility(View.GONE);

            final TextView btnToggle = makeBtn(ctx, "▶  Show " + cTotal + " Clients", "#A78BFA", "#0D0520", "#4C1D95");
            btnToggle.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
            btnToggle.setGravity(Gravity.CENTER);
            final boolean[] expanded = {false};
            btnToggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    expanded[0] = !expanded[0];
                    clientsContainer.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
                    btnToggle.setText(expanded[0]
                        ? "▼  Hide Clients"
                        : "▶  Show " + clients.size() + " Clients");
                    if (expanded[0] && clientsContainer.getChildCount() == 0) {
                        // Build client rows lazily
                        for (JSONObject c : clients) {
                            clientsContainer.addView(buildClientRow(ctx, c, now));
                        }
                    }
                }
            });
            card.addView(btnToggle);
            card.addView(clientsContainer);
        }

        return card;
    }

    // ── Client Row (inside reseller card) ─────────────────────────
    private static View buildClientRow(Context ctx, JSONObject obj, long now) {
        String enc = obj.optString("key","");
        String plain = decode(enc);
        long exp = obj.optLong("expired",0L);
        long ban = obj.optLong("banned_until",0L);
        boolean isBanned = ban > now;
        boolean isExpired = exp > 0 && exp < now;
        int statusColor = isBanned ? RED : isExpired ? YELLOW : GREEN;
        String statusLabel = isBanned ? "BAN" : isExpired ? "EXP" : "OK";
        int devCount = obj.optJSONArray("devices") != null ? obj.optJSONArray("devices").length() : 0;

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx,8), dp(ctx,8), dp(ctx,8), dp(ctx,8));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(0x1A22E5FF);
        rowBg.setCornerRadius(dp(ctx,10));
        row.setBackground(rowBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(ctx,6);
        row.setLayoutParams(lp);

        TextView tvKey = new TextView(ctx);
        tvKey.setText(plain);
        tvKey.setTextColor(0xCCFFFFFF);
        tvKey.setTextSize(11f);
        tvKey.setTypeface(Typeface.MONOSPACE);
        tvKey.setMaxLines(1);
        tvKey.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvKey.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        row.addView(tvKey);

        TextView tvDev = new TextView(ctx);
        tvDev.setText(devCount + " dev  ");
        tvDev.setTextColor(0x88FFFFFF);
        tvDev.setTextSize(10f);
        tvDev.setTypeface(Typeface.MONOSPACE);
        row.addView(tvDev);

        TextView tvStatus = makeBadge(ctx, statusLabel, statusColor);
        row.addView(tvStatus);
        return row;
    }

    // ── Action ────────────────────────────────────────────────────
    private static void doAction(final Context ctx, String action, String encoded, Runnable onDone) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("action", action);
            payload.put("target_encoded", encoded);
            ApiClient.getAdminService().postAction(payload.toString())
                .enqueue(new retrofit2.Callback<String>() {
                    @Override public void onResponse(Call<String> call, Response<String> resp) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                            String msg = resp.isSuccessful() ? "Done!" : "Failed: HTTP " + resp.code();
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                            // Refresh
                            if (listContainerRef != null) {
                                allKeysCache = null;
                                listContainerRef.removeAllViews();
                                LinearLayout skel = buildSkeleton(ctx);
                                listContainerRef.addView(skel);
                                loadData(ctx, skel, listContainerRef);
                            }
                        }});
                    }
                    @Override public void onFailure(Call<String> call, Throwable t) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                            Toast.makeText(ctx, "Connection failed", Toast.LENGTH_SHORT).show();
                        }});
                    }
                });
        } catch (Exception e) {
            Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private static String decode(String encoded) {
        try { return new String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charset.forName("UTF-8")); }
        catch (Exception e) { return encoded; }
    }

    private static View buildDivider(Context ctx) {
        View d = new View(ctx);
        d.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.topMargin = dp(ctx,8); lp.bottomMargin = dp(ctx,8);
        d.setLayoutParams(lp);
        return d;
    }

    private static TextView makeBadge(Context ctx, String text, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(9f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(ctx,6), dp(ctx,2), dp(ctx,6), dp(ctx,2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        bg.setCornerRadius(dp(ctx,6));
        bg.setStroke(dp(ctx,1), color);
        tv.setBackground(bg);
        return tv;
    }

    private static TextView makeBtn(Context ctx, String text, String textColor,
                                     String bgColor, String strokeColor) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(textColor));
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(ctx,10), dp(ctx,8), dp(ctx,10), dp(ctx,8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgColor));
        bg.setCornerRadius(dp(ctx,10));
        bg.setStroke(dp(ctx,1), Color.parseColor(strokeColor));
        tv.setBackground(bg);
        return tv;
    }

    private static LinearLayout buildSkeleton(Context ctx) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        for (int i = 0; i < 3; i++) {
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(ctx,14),dp(ctx,14),dp(ctx,14),dp(ctx,14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(BG_CARD);
            bg.setCornerRadius(dp(ctx,14));
            bg.setStroke(dp(ctx,1), 0x1EA78BFA);
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin = dp(ctx,10); card.setLayoutParams(lp);
            for (float r : new float[]{0.45f, 0.75f, 0.6f}) {
                View line = new View(ctx);
                GradientDrawable lb = new GradientDrawable();
                lb.setColor(0xFF1E293B); lb.setCornerRadius(dp(ctx,4));
                line.setBackground(lb);
                int w = (int)((ctx.getResources().getDisplayMetrics().widthPixels - dp(ctx,56)) * r);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(w, dp(ctx,10));
                llp.bottomMargin = dp(ctx,8); line.setLayoutParams(llp);
                card.addView(line);
            }
            final LinearLayout fc = card;
            android.animation.ValueAnimator sh = android.animation.ValueAnimator.ofFloat(0.3f, 0.7f);
            sh.setDuration(900); sh.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            sh.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            sh.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    fc.setAlpha((float) a.getAnimatedValue());
                }
            });
            sh.start();
            wrap.addView(card);
        }
        return wrap;
    }

    private static void showEmpty(Context ctx, LinearLayout container, String msg) {
        container.removeAllViews();
        LinearLayout empty = new LinearLayout(ctx);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(ctx,200)));
        TextView icon = new TextView(ctx);
        icon.setText("\uD83D\uDC65"); icon.setTextSize(40f); icon.setGravity(Gravity.CENTER);
        empty.addView(icon);
        TextView tv = new TextView(ctx);
        tv.setText(msg); tv.setTextColor(0xFF475569); tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER); tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1,-2);
        tlp.topMargin = dp(ctx,8); tv.setLayoutParams(tlp);
        empty.addView(tv);
        container.addView(empty);
    }

    private static int dp(Context ctx, int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
            ctx.getResources().getDisplayMetrics());
    }
}
