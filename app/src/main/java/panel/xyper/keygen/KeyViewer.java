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
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Response;

public class KeyViewer {

    private static Context ctxGlobal;
    private static String searchQuery = "";
    private static final java.util.List<String> searchHistory = new java.util.ArrayList<String>();
    private static final int MAX_HISTORY = 5;
    private static String activeFilter = "All";
    private static String sortBy = "default"; // default, name, expires, violations
    private static boolean bulkMode = false;
    private static final Set<String> selectedKeys = new HashSet<String>();
    private static LinearLayout listContainerRef;
    private static Handler autoRefreshHandler = null;
    private static Runnable autoRefreshRunnable = null;
    private static final int AUTO_REFRESH_MS = 5 * 60 * 1000;
    private static LinearLayout filterChipsRef;
    private static JSONArray allKeysCache;
    private static TextView bulkCountRef;
    private static LinearLayout bulkBarRef;

    public static View build(final Context ctx) {
        ctxGlobal = ctx;
        searchQuery = "";
        sortBy = "default";
        bulkMode = false;
        selectedKeys.clear();
        allKeysCache = null;
        listContainerRef = null;
        filterChipsRef = null;
        if (activeFilter.startsWith("owner:")) activeFilter = "All";
        else activeFilter = "All";

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
        outerContent.setPadding(dp(ctx,12), dp(ctx,12), dp(ctx,12), dp(ctx,80));
        outerScroll.addView(outerContent, new LinearLayout.LayoutParams(-1,-2));

        outerContent.addView(buildHeader(ctx));

        final EditText etSearch = new EditText(ctx);
        etSearch.setHint("🔍  Search key, role, type...");
        etSearch.setTextColor(Color.WHITE);
        etSearch.setHintTextColor(0x6622E5FF);
        etSearch.setTextSize(13f);
        etSearch.setTypeface(Typeface.MONOSPACE);
        etSearch.setSingleLine(true);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(0x1A22E5FF);
        searchBg.setCornerRadius(dp(ctx,14));
        searchBg.setStroke(dp(ctx,1), 0x5522E5FF);
        etSearch.setBackground(searchBg);
        etSearch.setPadding(dp(ctx,14), 0, dp(ctx,14), 0);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(ctx,46));
        searchLp.bottomMargin = dp(ctx,10);
        etSearch.setLayoutParams(searchLp);
        etSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(hasFocus ? 0x2522E5FF : 0x1A22E5FF);
                bg.setCornerRadius(dp(ctx,14));
                bg.setStroke(hasFocus ? dp(ctx,2) : dp(ctx,1),
                    hasFocus ? 0xFF22E5FF : 0x5522E5FF);
                etSearch.setBackground(bg);
                etSearch.setPadding(dp(ctx,14), 0, dp(ctx,14), 0);
            }
        });
        outerContent.addView(etSearch);

        LinearLayout filterChipsWrap = buildFilterChips(ctx);
        filterChipsRef = filterChipsWrap;
        outerContent.addView(filterChipsWrap);

        final LinearLayout bulkBar = buildBulkBar(ctx);
        bulkBarRef = bulkBar;
        outerContent.addView(bulkBar);

        final LinearLayout skeletonWrap = buildSkeleton(ctx);
        outerContent.addView(skeletonWrap);

        final LinearLayout listContainer = new LinearLayout(ctx);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainerRef = listContainer;
        outerContent.addView(listContainer);

        // Pull-to-refresh: swipe down on list
        outerScroll.setOnScrollChangeListener(new android.view.View.OnScrollChangeListener() {
            @Override public void onScrollChange(android.view.View v, int x, int y, int ox, int oy) {
                if (y == 0 && oy > 0 && v instanceof android.widget.ScrollView) {
                    // reached top while scrolling up — show refresh hint
                }
            }
        });
        final TextView pullHint = new TextView(ctx);
        pullHint.setText("↓  Pull to refresh");
        pullHint.setTextColor(0x4422E5FF);
        pullHint.setTextSize(11f);
        pullHint.setTypeface(Typeface.MONOSPACE);
        pullHint.setGravity(Gravity.CENTER);
        pullHint.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams phLp = new LinearLayout.LayoutParams(-1, -2);
        phLp.bottomMargin = dp(ctx,4);
        pullHint.setLayoutParams(phLp);
        outerContent.addView(pullHint, 0);

        // Overscroll refresh detector
        outerScroll.setOnTouchListener(new android.view.View.OnTouchListener() {
            private float startY = 0;
            @Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) { startY = e.getRawY(); }
                else if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dist = e.getRawY() - startY;
                    if (dist > dp(ctx, 80) && outerScroll.getScrollY() == 0) {
                        haptic(ctx);
                        allKeysCache = null;
                        listContainerRef.removeAllViews();
                        LinearLayout newSkel = buildSkeleton(ctx);
                        outerContent.addView(newSkel, 1);
                        loadKeys(ctx, newSkel, listContainerRef);
                    }
                }
                return false;
            }
        });

        loadKeys(ctx, skeletonWrap, listContainer);

        // ── Auto-refresh tiap 5 menit ──
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
                loadKeys(ctx, skel, listContainerRef);
                autoRefreshHandler.postDelayed(this, AUTO_REFRESH_MS);
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);

        etSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && !searchHistory.isEmpty() && etSearch.getText().toString().isEmpty()) {
                    showSearchHistoryBelow(ctx, etSearch, listContainer);
                }
            }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                if (!searchQuery.isEmpty()) saveSearchHistory(searchQuery);
                rebuildList(ctx, listContainer);
            }
        });

        return root;
    }

    // ── Search History ──────────────────────────────────────────
    private static void saveSearchHistory(String query) {
        if (query == null || query.isEmpty()) return;
        searchHistory.remove(query); // avoid duplicates
        searchHistory.add(0, query);
        while (searchHistory.size() > MAX_HISTORY) searchHistory.remove(searchHistory.size()-1);
    }

    private static void showSearchHistoryBelow(final Context ctx, final EditText etSearch,
            final LinearLayout container) {
        // Remove any existing history view
        for (int i = container.getChildCount()-1; i >= 0; i--) {
            if (container.getChildAt(i).getTag() instanceof String
                    && "historyRow".equals(container.getChildAt(i).getTag())) {
                container.removeViewAt(i);
            }
        }
        if (searchHistory.isEmpty()) return;
        LinearLayout histRow = new LinearLayout(ctx);
        histRow.setOrientation(LinearLayout.HORIZONTAL);
        histRow.setTag("historyRow");
        histRow.setPadding(0, dp(ctx,4), 0, dp(ctx,8));

        TextView histLabel = new TextView(ctx);
        histLabel.setText("Recent: ");
        histLabel.setTextColor(Color.parseColor("#475569"));
        histLabel.setTextSize(11f);
        histLabel.setTypeface(Typeface.MONOSPACE);
        histLabel.setGravity(Gravity.CENTER_VERTICAL);
        histRow.addView(histLabel);

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);

        for (final String h : searchHistory) {
            TextView chip = new TextView(ctx);
            chip.setText(h);
            chip.setTextColor(Color.parseColor("#22E5FF"));
            chip.setTextSize(11f);
            chip.setPadding(dp(ctx,10), dp(ctx,4), dp(ctx,10), dp(ctx,4));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x1522E5FF); bg.setCornerRadius(dp(ctx,12));
            bg.setStroke(dp(ctx,1), 0x5522E5FF);
            chip.setBackground(bg);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-2,-2);
            cLp.rightMargin = dp(ctx,6); chip.setLayoutParams(cLp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    etSearch.setText(h);
                    etSearch.setSelection(h.length());
                }
            });
            chips.addView(chip);
        }
        hsv.addView(chips);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        histRow.addView(hsv);

        // Insert at position 0 of container
        container.addView(histRow, 0);
    }

    // ── Sort helpers ─────────────────────────────────────────────
    private static java.util.List<JSONObject> applySortToList(java.util.List<JSONObject> list) {
        if ("name".equals(sortBy)) {
            java.util.Collections.sort(list, new java.util.Comparator<JSONObject>() {
                @Override public int compare(JSONObject a, JSONObject b) {
                    String ka = decodeKeyStr(a.optString("key",""));
                    String kb = decodeKeyStr(b.optString("key",""));
                    return ka.compareToIgnoreCase(kb);
                }
            });
        } else if ("expires".equals(sortBy)) {
            java.util.Collections.sort(list, new java.util.Comparator<JSONObject>() {
                @Override public int compare(JSONObject a, JSONObject b) {
                    long ea = a.optLong("expired",0); long eb = b.optLong("expired",0);
                    if (ea == 0) ea = Long.MAX_VALUE;
                    if (eb == 0) eb = Long.MAX_VALUE;
                    return Long.compare(ea, eb);
                }
            });
        } else if ("violations".equals(sortBy)) {
            java.util.Collections.sort(list, new java.util.Comparator<JSONObject>() {
                @Override public int compare(JSONObject a, JSONObject b) {
                    return b.optInt("violation_count",0) - a.optInt("violation_count",0);
                }
            });
        }
        return list;
    }

    private static String decodeKeyStr(String encoded) {
        try { return new String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), "UTF-8"); }
        catch (Exception e) { return encoded; }
    }

        private static void showSortSheet(final Context ctx) {
        final String[] opts = {"Default", "Name (A-Z)", "Expires (Soonest)", "Violations (Most)"};
        final String[] keys = {"default", "name", "expires", "violations"};
        showBanSheet(ctx, "Sort Keys By", opts, opts[0], new SheetCallback() {
            @Override public void onSelected(String val, int idx) {
                sortBy = keys[idx];
                if (listContainerRef != null) rebuildList(ctx, listContainerRef);
            }
        });
    }

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
        title.setText("Key Viewer");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            title.setShadowLayer(14f,0f,0f,Color.parseColor("#22E5FF"));
        }

        TextView sub = new TextView(ctx);
        sub.setText("Xyper Xit  ·  All Keys");
        sub.setTextSize(10f);
        sub.setTextColor(Color.parseColor("#38BDF8"));
        sub.setTypeface(Typeface.MONOSPACE);

        textWrap.addView(title);
        textWrap.addView(sub);
        header.addView(textWrap);

        TextView btnBulk = new TextView(ctx);
        btnBulk.setText("☑ Select");
        btnBulk.setTextSize(12f);
        btnBulk.setTextColor(Color.parseColor("#22E5FF"));
        btnBulk.setTypeface(Typeface.DEFAULT_BOLD);
        btnBulk.setGravity(Gravity.CENTER);
        btnBulk.setPadding(dp(ctx,12), dp(ctx,7), dp(ctx,12), dp(ctx,7));
        GradientDrawable bulkBtnBg = new GradientDrawable();
        bulkBtnBg.setColor(0x1522E5FF);
        bulkBtnBg.setCornerRadius(dp(ctx,10));
        bulkBtnBg.setStroke(dp(ctx,1), Color.parseColor("#22455A"));
        btnBulk.setBackground(bulkBtnBg);
        attachPressScale(btnBulk);
        btnBulk.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                bulkMode = !bulkMode;
                selectedKeys.clear();
                if (!bulkMode) {
                    if (bulkBarRef != null) bulkBarRef.setVisibility(View.GONE);
                } else {
                    if (bulkBarRef != null) bulkBarRef.setVisibility(View.VISIBLE);
                    updateBulkCount();
                }
                if (allKeysCache != null && listContainerRef != null)
                    rebuildList(ctxGlobal, listContainerRef);
            }
        });
        header.addView(btnBulk);
        return header;
    }

    private static LinearLayout buildFilterChips(final Context ctx) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(-1,-2);
        wLp.bottomMargin = dp(ctx,10);
        wrapper.setLayoutParams(wLp);

        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        final LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, 0, dp(ctx,8), 0);

        final String[] filters = {"All","Developer","Reseller","Client","Active","Banned"};
        for (int i = 0; i < filters.length; i++) {
            final String f = filters[i];
            final TextView chip = new TextView(ctx);
            chip.setText(f);
            chip.setTextSize(12f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(ctx,14), dp(ctx,7), dp(ctx,14), dp(ctx,7));
            applyChipStyle(ctx, chip, f.equals(activeFilter));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2,-2);
            chipLp.rightMargin = dp(ctx,6);
            chip.setLayoutParams(chipLp);
            attachPressScale(chip);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    activeFilter = f;
                    for (int j = 0; j < chips.getChildCount(); j++) {
                        View c = chips.getChildAt(j);
                        if (c instanceof TextView)
                            applyChipStyle(ctx,(TextView)c,((TextView)c).getText().toString().equals(f));
                    }
                    if (allKeysCache != null && listContainerRef != null)
                        rebuildList(ctx, listContainerRef);
                }
            });
            chips.addView(chip);
        }
        hsv.addView(chips);
        wrapper.addView(hsv);
        return wrapper;
    }

    private static void applyChipStyle(Context ctx, TextView chip, boolean sel) {
        GradientDrawable bg = new GradientDrawable();
        if (sel) {
            bg.setColor(Color.parseColor("#0D2A3A"));
            bg.setStroke(dp(ctx,1), Color.parseColor("#22E5FF"));
            chip.setTextColor(Color.parseColor("#22E5FF"));
        } else {
            bg.setColor(0x0FFFFFFF);
            bg.setStroke(dp(ctx,1), Color.parseColor("#1E3A50"));
            chip.setTextColor(Color.parseColor("#64748B"));
        }
        bg.setCornerRadius(dp(ctx,20));
        chip.setBackground(bg);
    }

    private static LinearLayout buildBulkBar(final Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(ctx,12), dp(ctx,10), dp(ctx,12), dp(ctx,10));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.parseColor("#0A1628"));
        barBg.setCornerRadius(dp(ctx,14));
        barBg.setStroke(dp(ctx,1), Color.parseColor("#F87171"));
        bar.setBackground(barBg);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1,-2);
        barLp.bottomMargin = dp(ctx,10);
        bar.setLayoutParams(barLp);
        bar.setVisibility(View.GONE);

        // ── Row 1: count + Select All ──
        LinearLayout bulkRow1 = new LinearLayout(ctx);
        bulkRow1.setOrientation(LinearLayout.HORIZONTAL);
        bulkRow1.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams br1lp = new LinearLayout.LayoutParams(-1,-2);
        br1lp.bottomMargin = dp(ctx,8);
        bulkRow1.setLayoutParams(br1lp);

        TextView countTv = new TextView(ctx);
        countTv.setText("0 selected");
        countTv.setTextColor(Color.parseColor("#94A3B8"));
        countTv.setTextSize(12f);
        countTv.setTypeface(Typeface.MONOSPACE);
        countTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        bulkCountRef = countTv;
        bulkRow1.addView(countTv);

        TextView btnAll = makeActionBtn(ctx,"☑ All","#22E5FF","#051520","#22455A");
        attachPressScale(btnAll);
        btnAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (allKeysCache==null) return;
                for (int i=0;i<allKeysCache.length();i++) {
                    try { String k=allKeysCache.getJSONObject(i).optString("key","");
                        if(!k.isEmpty()) selectedKeys.add(k); } catch(Exception e){}
                }
                updateBulkCount();
                rebuildList(ctx,listContainerRef);
            }
        });
        bulkRow1.addView(btnAll);
        bar.addView(bulkRow1);

        // ── Row 2: 4 actions equal width ──
        LinearLayout bulkRow2 = new LinearLayout(ctx);
        bulkRow2.setOrientation(LinearLayout.HORIZONTAL);
        bulkRow2.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));

        TextView btnBan = makeActionBtn(ctx,"⛔ Ban","#F87171","#1A0808","#7F1D1D");
        attachPressScale(btnBan);
        btnBan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (selectedKeys.isEmpty()) { showNeonToast(ctx,"No keys selected"); return; }
                showBanSheet(ctx,"Bulk Ban Duration",
                    new String[]{"1 Day","3 Days","7 Days","30 Days","Permanent"},
                    "1 Day", new SheetCallback() {
                        @Override public void onSelected(String val, int idx) {
                            long[] ms = {86400000L,3*86400000L,7*86400000L,30*86400000L,Long.MAX_VALUE/2};
                            long bu = System.currentTimeMillis()+ms[idx];
                            for (String enc : selectedKeys.toArray(new String[0])) {
                                try { JSONObject ex=new JSONObject();
                                    ex.put("target_encoded",enc); ex.put("banned_until",bu);
                                    sendAdminAction("ban_key",ex);
                                } catch(Exception ignored){}
                            }
                            showNeonToast(ctx, selectedKeys.size()+" keys banned");
                            exitBulkMode(ctx);
                        }
                    });
            }
        });
        LinearLayout.LayoutParams banLp = new LinearLayout.LayoutParams(0,-2,1f);
        banLp.rightMargin = dp(ctx,4); btnBan.setLayoutParams(banLp);
        bulkRow2.addView(btnBan);

        TextView btnUnban = makeActionBtn(ctx,"✅ Unban","#34D399","#051A0E","#064E3B");
        attachPressScale(btnUnban);
        btnUnban.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (selectedKeys.isEmpty()) { showNeonToast(ctx,"No keys selected"); return; }
                for (String enc : selectedKeys.toArray(new String[0])) {
                    try { JSONObject ex=new JSONObject(); ex.put("target_encoded",enc);
                        sendAdminAction("unban_key",ex);
                    } catch(Exception ignored){}
                }
                showNeonToast(ctx, selectedKeys.size()+" keys unbanned");
                exitBulkMode(ctx);
            }
        });
        LinearLayout.LayoutParams unbanLp = new LinearLayout.LayoutParams(0,-2,1f);
        unbanLp.rightMargin = dp(ctx,4); btnUnban.setLayoutParams(unbanLp);
        bulkRow2.addView(btnUnban);

        TextView btnExt = makeActionBtn(ctx,"⏩ Ext","#A78BFA","#0D0520","#4C1D95");
        attachPressScale(btnExt);
        btnExt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (selectedKeys.isEmpty()) { showNeonToast(ctx,"No keys selected"); return; }
                showBanSheet(ctx,"Bulk Extend",
                    new String[]{"+1 Day","+7 Days","+30 Days","+90 Days"},
                    "+7 Days", new SheetCallback() {
                        @Override public void onSelected(String val, int idx) {
                            long[] ms = {86400000L,7*86400000L,30*86400000L,90*86400000L};
                            for (String enc : selectedKeys.toArray(new String[0])) {
                                try { JSONObject ex=new JSONObject();
                                    ex.put("target_encoded",enc); ex.put("extend_ms",ms[idx]);
                                    sendAdminAction("extend_key",ex);
                                } catch(Exception ignored){}
                            }
                            showNeonToast(ctx, selectedKeys.size()+" keys extended");
                            exitBulkMode(ctx);
                        }
                    });
            }
        });
        LinearLayout.LayoutParams extLp = new LinearLayout.LayoutParams(0,-2,1f);
        extLp.rightMargin = dp(ctx,4); btnExt.setLayoutParams(extLp);
        bulkRow2.addView(btnExt);

        TextView btnDel = makeActionBtn(ctx,"🗑 Del","#F87171","#1A0808","#7F1D1D");
        attachPressScale(btnDel);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (selectedKeys.isEmpty()) { showNeonToast(ctx,"No keys selected"); return; }
                haptic(ctx);
                confirmBulkDelete(ctx);
            }
        });
        btnDel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        bulkRow2.addView(btnDel);

        bar.addView(bulkRow2);
        return bar;
    }

    private static void exitBulkMode(Context ctx) {
        bulkMode = false;
        selectedKeys.clear();
        if (listContainerRef != null) rebuildList(ctx, listContainerRef);
        if (bulkBarRef != null) bulkBarRef.setVisibility(View.GONE);
    }

    private static void updateBulkCount() {
        if (bulkCountRef!=null) bulkCountRef.setText(selectedKeys.size()+" selected");
    }

    private static LinearLayout buildSkeleton(Context ctx) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        for (int i=0;i<3;i++) {
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(ctx,14),dp(ctx,14),dp(ctx,14),dp(ctx,14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#0F172A"));
            bg.setCornerRadius(dp(ctx,14));
            bg.setStroke(dp(ctx,1), Color.parseColor("#1E293B"));
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin = dp(ctx,8);
            card.setLayoutParams(lp);
            card.addView(buildSkeletonLine(ctx,0.5f));
            card.addView(buildSkeletonLine(ctx,0.8f));
            card.addView(buildSkeletonLine(ctx,0.65f));
            wrap.addView(card);
            final LinearLayout fc = card;
            ValueAnimator sh = ValueAnimator.ofFloat(0.3f,0.7f);
            sh.setDuration(900); sh.setRepeatMode(ValueAnimator.REVERSE); sh.setRepeatCount(ValueAnimator.INFINITE);
            sh.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { fc.setAlpha((float)a.getAnimatedValue()); }
            });
            sh.start();
        }
        return wrap;
    }

    private static View buildSkeletonLine(Context ctx, float ratio) {
        View line = new View(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(ctx,4));
        line.setBackground(bg);
        int w=(int)((ctx.getResources().getDisplayMetrics().widthPixels-dp(ctx,56))*ratio);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w,dp(ctx,10));
        lp.bottomMargin = dp(ctx,8);
        line.setLayoutParams(lp);
        return line;
    }

    private static void loadKeys(final Context ctx, final LinearLayout skeleton, final LinearLayout list) {
        ApiClient.getAdminService().getAllKeys().enqueue(new retrofit2.Callback<String>() {
            @Override public void onResponse(Call<String> call, Response<String> response) {
                if (!response.isSuccessful()) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                        if(skeleton!=null) skeleton.setVisibility(View.GONE);
                        showEmptyState(ctx,list,"Failed to load data\nPlease try again");
                    }});
                    return;
                }
                try {
                    final JSONArray arr = new JSONArray(response.body());
                    allKeysCache = arr;
                    new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                        if(skeleton!=null) skeleton.setVisibility(View.GONE);
                        rebuildResellerChips(ctx);
                        rebuildList(ctx, list);
                    }});
                } catch(Exception e) { postError("Failed to parse server response"); }
            }
            @Override public void onFailure(Call<String> call, Throwable t) {
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                    if(skeleton!=null) skeleton.setVisibility(View.GONE);
                    showEmptyState(ctx,list,"No internet connection\nPlease check your network");
                }});
            }
        });
    }

    private static void rebuildList(Context ctx, LinearLayout container) {
        if (allKeysCache==null) return;
        container.removeAllViews();
        List<JSONObject> filtered = new ArrayList<JSONObject>();
        long now = System.currentTimeMillis();
        for (int i=0;i<allKeysCache.length();i++) {
            try {
                JSONObject obj = allKeysCache.getJSONObject(i);
                String role = obj.optString("role","").toLowerCase();
                long banned = obj.optLong("banned_until",0L);
                long exp = obj.optLong("expired",0L);
                boolean isBanned = banned > now;
                boolean isExpired = exp > 0 && exp < now;
                if (!activeFilter.equals("All")) {
                    if (activeFilter.equals("Banned") && !isBanned) continue;
                    if (activeFilter.equals("Expired") && !isExpired) continue;
                    if (activeFilter.equals("Active") && (isBanned || isExpired)) continue;
                    if (activeFilter.equals("Developer") && !role.equals("developer")) continue;
                    if (activeFilter.equals("Reseller") && !role.equals("reseller")) continue;
                    if (activeFilter.equals("Client") && !role.equals("client")) continue;
                    // Filter by owner (reseller)
                    if (activeFilter.startsWith("owner:")) {
                        String ownerFilter = activeFilter.substring(6);
                        String ownerKey = obj.optString("owner_key","");
                        if (!ownerKey.equals(ownerFilter)) continue;
                    }
                }
                if (!searchQuery.isEmpty()) {
                    String sq = searchQuery.toLowerCase();
                    String key = obj.optString("key","").toLowerCase();
                    String type = obj.optString("type","").toLowerCase();
                    try { byte[] dec=android.util.Base64.decode(key,android.util.Base64.DEFAULT);
                        key=new String(dec,Charset.forName("UTF-8")).toLowerCase(); } catch(Exception ignored){}
                    if (!key.contains(sq)&&!role.contains(sq)&&!type.contains(sq)) continue;
                }
                filtered.add(obj);
            } catch(Exception ignored){}
        }
        if (filtered.isEmpty()) {
            String msg = searchQuery.isEmpty()
                ? "No keys found\nCreate a new key from the Add Key page"
                : "No results for \""+searchQuery+"\"";
            showEmptyState(ctx, container, msg);
            return;
        }
        // Apply sort
        applySortToList(filtered);

        for (int i=0;i<filtered.size();i++) {
            final View card = buildKeyCard(ctx, filtered.get(i), i+1);
            container.addView(card);
            card.setAlpha(0f);
            card.setTranslationY(dp(ctx,18));
            final int delay = i*60;
            card.postDelayed(new Runnable() {
                @Override public void run() {
                    card.animate().alpha(1f).translationY(0f).setDuration(280)
                        .setInterpolator(new DecelerateInterpolator()).start();
                }
            }, delay);
        }
    }

    // ── Rebuild filter chips dengan reseller dinamis ──────────────
    private static void rebuildResellerChips(Context ctx) {
        if (filterChipsRef == null || allKeysCache == null) return;
        // Ambil HorizontalScrollView → LinearLayout chips
        if (!(filterChipsRef.getChildAt(0) instanceof android.widget.HorizontalScrollView)) return;
        android.widget.HorizontalScrollView hsv =
            (android.widget.HorizontalScrollView) filterChipsRef.getChildAt(0);
        if (!(hsv.getChildAt(0) instanceof LinearLayout)) return;
        final LinearLayout chips = (LinearLayout) hsv.getChildAt(0);

        // Hapus chip reseller lama (index >= 6 = setelah All,Dev,Reseller,Client,Active,Expired,Banned)
        while (chips.getChildCount() > 7) chips.removeViewAt(7);

        // Kumpulkan semua Reseller unik dari cache
        java.util.LinkedHashMap<String,String> resellerMap = new java.util.LinkedHashMap<>();
        for (int i = 0; i < allKeysCache.length(); i++) {
            try {
                org.json.JSONObject obj = allKeysCache.getJSONObject(i);
                if ("Client".equals(obj.optString("role",""))) {
                    String enc = obj.optString("owner_key","");
                    if (!enc.isEmpty() && !resellerMap.containsKey(enc)) {
                        try { byte[] d = android.util.Base64.decode(enc, android.util.Base64.DEFAULT);
                            resellerMap.put(enc, new String(d, java.nio.charset.Charset.forName("UTF-8")));
                        } catch (Exception ignored) { resellerMap.put(enc, enc); }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (resellerMap.isEmpty()) return;

        // Tambah separator
        android.view.View sep = new android.view.View(ctx);
        sep.setBackgroundColor(0x33A78BFA);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(ctx,1), dp(ctx,22));
        sepLp.rightMargin = dp(ctx,6);
        sepLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        sep.setLayoutParams(sepLp);
        chips.addView(sep);

        // Tambah chip per Reseller
        for (final java.util.Map.Entry<String,String> entry : resellerMap.entrySet()) {
            final String filterKey = "owner:" + entry.getKey();
            final String label = "👤 " + entry.getValue();
            final android.widget.TextView chip = new android.widget.TextView(ctx);
            chip.setText(label);
            chip.setTextSize(12f);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(dp(ctx,14), dp(ctx,7), dp(ctx,14), dp(ctx,7));
            applyChipStyle(ctx, chip, filterKey.equals(activeFilter));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2,-2);
            chipLp.rightMargin = dp(ctx,6);
            chip.setLayoutParams(chipLp);
            attachPressScale(chip);
            chip.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    activeFilter = filterKey;
                    for (int j = 0; j < chips.getChildCount(); j++) {
                        android.view.View c = chips.getChildAt(j);
                        if (c instanceof android.widget.TextView)
                            applyChipStyle(ctx, (android.widget.TextView)c,
                                ((android.widget.TextView)c).getTag() != null
                                    ? ((android.widget.TextView)c).getTag().equals(filterKey)
                                    : ((android.widget.TextView)c).getText().toString().equals(filterKey));
                    }
                    chip.setTag(filterKey);
                    if (allKeysCache != null && listContainerRef != null)
                        rebuildList(ctx, listContainerRef);
                }
            });
            chip.setTag(filterKey);
            chips.addView(chip);
        }
    }

    private static void showEmptyState(Context ctx, LinearLayout container, String msg) {
        container.removeAllViews();
        LinearLayout empty = new LinearLayout(ctx);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(ctx,200)));
        TextView icon = new TextView(ctx);
        icon.setText("🗝️"); icon.setTextSize(40f); icon.setGravity(Gravity.CENTER);
        empty.addView(icon);
        TextView tv = new TextView(ctx);
        tv.setText(msg); tv.setTextColor(Color.parseColor("#475569")); tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER); tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1,-2);
        tlp.topMargin = dp(ctx,8); tv.setLayoutParams(tlp);
        empty.addView(tv);
        container.addView(empty);
    }

    // Haptic — respects SettingsPanel preference
    private static void haptic(Context ctx) {
        if (!SettingsPanel.isHapticEnabled(ctx)) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.os.VibratorManager vm = (android.os.VibratorManager)
                    ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vm.getDefaultVibrator().vibrate(
                    android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.os.Vibrator v = (android.os.Vibrator) ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                android.os.Vibrator v = (android.os.Vibrator) ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(30);
            }
        } catch (Exception ignored) {}
    }

        private static void showKeyDetailSheet(final Context ctx, final JSONObject obj, final String plainKey) {
        // Full screen bottom sheet with all key info + copy buttons
        final android.app.Dialog dialog = new android.app.Dialog(ctx,
            android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setCancelable(true);

        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(0xCC000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        // Sheet card
        LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(Color.parseColor("#0B1426"));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#0B1426"));
        sheetBg.setCornerRadii(new float[]{dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20),0,0,0,0});
        sheet.setBackground(sheetBg);
        sheet.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { /* consume */ }
        });

        ScrollView sv = new ScrollView(ctx);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx,20), dp(ctx,8), dp(ctx,20), dp(ctx,24));
        sv.addView(content);

        // Drag handle
        View handle = new View(ctx);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setColor(0xFF2D3A4A); hBg.setCornerRadius(dp(ctx,3));
        handle.setBackground(hBg);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(dp(ctx,40), dp(ctx,4));
        hLp.gravity = Gravity.CENTER_HORIZONTAL; hLp.topMargin = dp(ctx,12); hLp.bottomMargin = dp(ctx,16);
        handle.setLayoutParams(hLp);
        sheet.addView(handle);
        sheet.addView(sv);

        // Title
        TextView title = new TextView(ctx);
        title.setText("🔑  " + plainKey);
        title.setTextColor(Color.parseColor("#22E5FF"));
        title.setTextSize(18f); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setShadowLayer(8f, 0, 0, Color.parseColor("#22E5FF"));
        title.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1,-2);
        tLp.bottomMargin = dp(ctx,16); title.setLayoutParams(tLp);
        content.addView(title);

        // All fields
        long now = System.currentTimeMillis();
        String role = obj.optString("role", "—");
        String type = obj.optString("type", "—");
        long exp = obj.optLong("expired", 0);
        long banned = obj.optLong("banned_until", 0);
        long created = obj.optLong("created_at", 0);
        int devLimit = obj.optInt("device_limit", 1);
        int violations = obj.optInt("violation_count", 0);
        JSONArray devs = obj.optJSONArray("devices");
        if (devs == null) devs = new JSONArray();

        String status = banned > now ? "BANNED" : exp > 0 && exp < now ? "EXPIRED" : "ACTIVE";
        String expStr = exp == 0 ? "∞ Permanent"
            : new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(exp));
        String createdStr = created > 0
            ? new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(created))
            : "—";
        String bannedStr = banned > now
            ? new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(banned))
            : "—";

        // Info section
        content.addView(buildDetailSection(ctx, "KEY INFO", new String[][]{
            {"Key Name",    plainKey},
            {"Role",        role},
            {"Type",        type},
            {"Status",      status},
            {"Expires",     expStr},
            {"Created",     createdStr},
            {"Device Limit",String.valueOf(devLimit)},
            {"Devices Used",devs.length() + "/" + devLimit},
            {"Violations",  String.valueOf(violations)},
            {"Banned Until",bannedStr},
        }, ctx));

        // Devices section
        if (devs.length() > 0) {
            content.addView(buildDetailSectionTitle(ctx, "📱  DEVICES (" + devs.length() + ")"));
            for (int i = 0; i < devs.length(); i++) {
                try {
                    JSONObject d = devs.getJSONObject(i);
                    String model = d.optString("device_name", d.optString("device_id", "Unknown"));
                    String os    = "Android " + d.optString("android_version", "?");
                    String ip    = d.optString("ip", "?");
                    String lastLogin = d.optString("last_login", "");
                    content.addView(buildDetailSection(ctx, "Device " + (i+1), new String[][]{
                        {"Model",      model},
                        {"OS",         os},
                        {"IP",         ip},
                        {"Last Login", lastLogin},
                    }, ctx));
                } catch (Exception ignored) {}
            }
        }

        // Copy buttons
        LinearLayout copyRow = new LinearLayout(ctx);
        copyRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(-1,-2);
        crLp.topMargin = dp(ctx,16); copyRow.setLayoutParams(crLp);

        final JSONArray devsFinal = devs;
        final String expStrF = expStr, createdStrF = createdStr;

        // Copy Key
        TextView btnCopyKey = makeDetailBtn(ctx, "📋 Copy Key", "#22E5FF");
        btnCopyKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) { cm.setPrimaryClip(ClipData.newPlainText("key", plainKey)); }
                showNeonToast(ctx, "Key copied!"); haptic(ctx);
            }
        });
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0,-2,1f);
        b1Lp.rightMargin = dp(ctx,8); btnCopyKey.setLayoutParams(b1Lp);
        copyRow.addView(btnCopyKey);

        // Copy All Info
        TextView btnCopyAll = makeDetailBtn(ctx, "📄 Copy All Info", "#A78BFA");
        btnCopyAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String info = "Key: "+plainKey+"\n"+"Role: "+obj.optString("role","?")+"\n"+"Type: "+obj.optString("type","?")+"\n"+"Status: "+(obj.optLong("banned_until",0)>System.currentTimeMillis()?"BANNED":obj.optLong("expired",0)>0&&obj.optLong("expired",0)<System.currentTimeMillis()?"EXPIRED":"ACTIVE")+"\n"+"Expires: "+expStrF+"\n"+"Created: "+createdStrF+"\n"+"Devices: "+devsFinal.length()+"/"+obj.optInt("device_limit",1)+"\n"+"Violations: "+obj.optInt("violation_count",0);
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) { cm.setPrimaryClip(ClipData.newPlainText("key_info", info)); }
                showNeonToast(ctx, "All info copied!"); haptic(ctx);
            }
        });
        btnCopyAll.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        copyRow.addView(btnCopyAll);
        content.addView(copyRow);

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(-1, -2);
        sheetLp.gravity = Gravity.BOTTOM;
        root.addView(sheet, sheetLp);

        // Animate up
        sheet.setTranslationY(1000f);
        dialog.setContentView(root);
        dialog.show();
        sheet.animate().translationY(0f).setDuration(320)
            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    private static LinearLayout buildDetailSection(Context ctx, String title, String[][] rows, Context c) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx,14), dp(ctx,12), dp(ctx,14), dp(ctx,12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0D1F38"));
        bg.setCornerRadius(dp(ctx,12));
        bg.setStroke(dp(ctx,1), Color.parseColor("#1E3A50"));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,8); card.setLayoutParams(lp);

        TextView sTitle = new TextView(ctx);
        sTitle.setText(title);
        sTitle.setTextColor(0x8894A3B8);
        sTitle.setTextSize(10f); sTitle.setTypeface(Typeface.MONOSPACE);
        sTitle.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1,-2);
        stLp.bottomMargin = dp(ctx,8); sTitle.setLayoutParams(stLp);
        card.addView(sTitle);

        for (String[] row : rows) {
            if (row[1].equals("—") || row[1].isEmpty()) continue;
            LinearLayout rowView = new LinearLayout(ctx);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1,-2);
            rLp.bottomMargin = dp(ctx,4); rowView.setLayoutParams(rLp);

            TextView label = new TextView(ctx);
            label.setText(row[0]); label.setTextColor(0xFF475569);
            label.setTextSize(12f); label.setTypeface(Typeface.MONOSPACE);
            label.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx,100),-2));
            rowView.addView(label);

            TextView value = new TextView(ctx);
            value.setText(row[1]); value.setTextColor(Color.WHITE);
            value.setTextSize(12f); value.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            rowView.addView(value);
            card.addView(rowView);
        }
        return card;
    }

    private static TextView buildDetailSectionTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text); tv.setTextColor(Color.parseColor("#22E5FF"));
        tv.setTextSize(12f); tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(ctx,8); lp.bottomMargin = dp(ctx,4); tv.setLayoutParams(lp);
        return tv;
    }

    private static TextView makeDetailBtn(Context ctx, String text, String color) {
        TextView tv = new TextView(ctx);
        tv.setText(text); tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(13f); tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(ctx,12), dp(ctx,12), dp(ctx,12), dp(ctx,12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x15000000); bg.setCornerRadius(dp(ctx,12));
        bg.setStroke(dp(ctx,1), Color.parseColor(color + "88".substring(0,
            Math.min(2, color.length()))));
        try {
            bg.setStroke(dp(ctx,1), android.graphics.Color.parseColor(color) & 0x88FFFFFF);
        } catch (Exception ignored) {}
        tv.setBackground(bg);
        return tv;
    }

        private static View buildKeyCard(final Context ctx, final JSONObject obj, int index) {
        final String encoded  = obj.optString("key","");
        final String type     = obj.optString("type","Unknown");
        final String role     = obj.optString("role","-");
        final String ownerEncoded = obj.optString("owner_key","");
        String ownerDecoded = "";
        if (!ownerEncoded.isEmpty()) {
            try { byte[] od = android.util.Base64.decode(ownerEncoded, android.util.Base64.DEFAULT);
                ownerDecoded = new String(od, Charset.forName("UTF-8")); } catch (Exception ignored) {}
        }
        final String ownerFinal = ownerDecoded;
        final long expired    = obj.optLong("expired",0L);
        final long created    = obj.optLong("created_at",0L);
        final int devLimit    = obj.optInt("device_limit",1);
        final int violation   = obj.optInt("violation_count",0);
        final long bannedUntil= obj.optLong("banned_until",0L);
        JSONArray devs = obj.optJSONArray("devices");
        if (devs==null) devs=new JSONArray();
        final JSONArray devsFinal = devs;

        String plain = encoded;
        try { byte[] d=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);
            plain=new String(d,Charset.forName("UTF-8")); } catch(Exception ignored){}
        final String plainFinal = plain;

        long now = System.currentTimeMillis();
        final boolean isBanned  = bannedUntil > now;
        final boolean isExpired = expired > 0 && expired < now;

        int roleColor = role.equals("Developer") ? Color.parseColor("#A78BFA")
            : role.equals("Reseller") ? Color.parseColor("#22E5FF")
            : Color.parseColor("#34D399");
        int statusColor = isBanned ? Color.parseColor("#F87171")
            : isExpired ? Color.parseColor("#FBBF24") : Color.parseColor("#34D399");
        String statusLabel = isBanned ? "BANNED" : isExpired ? "EXPIRED" : "ACTIVE";

        final LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx,14),dp(ctx,14),dp(ctx,14),dp(ctx,14));
        final GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#0B1426"));
        cardBg.setCornerRadius(dp(ctx,16));
        cardBg.setStroke(dp(ctx,1), bulkMode && selectedKeys.contains(encoded)
            ? Color.parseColor("#22E5FF") : Color.parseColor("#1E3A50"));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1,-2);
        cardLp.bottomMargin = dp(ctx,10);
        card.setLayoutParams(cardLp);

        // Top row
        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1,-2);
        trLp.bottomMargin = dp(ctx,10);
        topRow.setLayoutParams(trLp);

        // FIX: simpan referensi chkBox sebagai final supaya bisa di-update dari click listener
        final TextView chkBox;
        if (bulkMode) {
            chkBox = new TextView(ctx);
            chkBox.setText(selectedKeys.contains(encoded) ? "\u2611" : "\u2610");
            chkBox.setTextColor(selectedKeys.contains(encoded)
                ? Color.parseColor("#22E5FF") : Color.parseColor("#334155"));
            chkBox.setTextSize(18f);
            LinearLayout.LayoutParams chkLp = new LinearLayout.LayoutParams(-2,-2);
            chkLp.rightMargin = dp(ctx,8);
            chkBox.setLayoutParams(chkLp);
            topRow.addView(chkBox);
        } else {
            chkBox = null;
        }

        TextView tvNum = new TextView(ctx);
        tvNum.setText("#"+index+"  ");
        tvNum.setTextColor(Color.parseColor("#334155"));
        tvNum.setTextSize(12f);
        tvNum.setTypeface(Typeface.MONOSPACE);
        topRow.addView(tvNum);

        TextView tvKey = new TextView(ctx);
        tvKey.setText(plain);
        tvKey.setTextColor(Color.parseColor("#22E5FF"));
        tvKey.setTextSize(13f);
        tvKey.setTypeface(Typeface.MONOSPACE);
        tvKey.setMaxLines(1);
        tvKey.setEllipsize(TextUtils.TruncateAt.END);
        tvKey.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        // Long press → copy key to clipboard
        final String copyKey = plainFinal;
        tvKey.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                haptic(ctx);
                showKeyDetailSheet(ctx, obj, copyKey);
                return true;
            }
        });
        topRow.addView(tvKey);

        TextView badge = new TextView(ctx);
        badge.setText(statusLabel);
        badge.setTextColor(statusColor);
        badge.setTextSize(10f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(ctx,8),dp(ctx,3),dp(ctx,8),dp(ctx,3));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.argb(30,Color.red(statusColor),Color.green(statusColor),Color.blue(statusColor)));
        badgeBg.setCornerRadius(dp(ctx,6));
        badgeBg.setStroke(dp(ctx,1),statusColor);
        badge.setBackground(badgeBg);
        topRow.addView(badge);
        card.addView(topRow);

        card.addView(buildDivider(ctx));

        // Info grid
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        LinearLayout col1 = new LinearLayout(ctx);
        col1.setOrientation(LinearLayout.VERTICAL);
        col1.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout col2 = new LinearLayout(ctx);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

        String roleHex = role.equals("Developer")?"#A78BFA":role.equals("Reseller")?"#22E5FF":"#34D399";
        col1.addView(buildInfoRow(ctx,"Role",role,roleHex));
        String displayType = type;
        if (!type.equals("Permanent") && expired > 0 && created > 0) {
            long actual = expired - created;
            long expected = 0;
            String tl = type.toLowerCase();
            if (tl.contains("1 day"))        expected = 86400000L;
            else if (tl.contains("3 day"))   expected = 3*86400000L;
            else if (tl.contains("7 day"))   expected = 7*86400000L;
            else if (tl.contains("14 day"))  expected = 14*86400000L;
            else if (tl.contains("30 day"))  expected = 30*86400000L;
            else if (tl.contains("90 day"))  expected = 90*86400000L;
            if (expected > 0 && actual > expected * 1.05) {
                long extDays = Math.round((double)(actual - expected) / 86400000L);
                displayType = type + " +" + extDays + "d (Extended)";
            }
        }
        col1.addView(buildInfoRow(ctx,"Type",displayType,"#94A3B8"));
        col1.addView(buildInfoRow(ctx,"Limit",String.valueOf(devLimit),"#94A3B8"));
        String expStr = expired==0 ? "∞ Permanent" : isExpired ? "⚠ "+formatTime(expired) : formatTime(expired);
        col2.addView(buildInfoRow(ctx,"Expired",expStr,isExpired?"#FBBF24":"#94A3B8"));
        col2.addView(buildInfoRow(ctx,"Devices",devsFinal.length()+"/"+devLimit,"#94A3B8"));
        col2.addView(buildInfoRow(ctx,"Violation",String.valueOf(violation),violation>0?"#F87171":"#94A3B8"));
        // Owner row — hanya untuk Client key yang punya owner
        if (role.equals("Client") && !ownerFinal.isEmpty()) {
            col1.addView(buildInfoRow(ctx,"Owner",ownerFinal,"#FBBF24"));
        }
        grid.addView(col1); grid.addView(col2);
        card.addView(grid);

        if (isBanned) {
            TextView tvBan = new TextView(ctx);
            tvBan.setText("⛔ Banned until: "+formatTime(bannedUntil));
            tvBan.setTextColor(Color.parseColor("#F87171"));
            tvBan.setTextSize(11f);
            tvBan.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams banLp = new LinearLayout.LayoutParams(-1,-2);
            banLp.topMargin = dp(ctx,6);
            tvBan.setLayoutParams(banLp);
            card.addView(tvBan);
        }

        card.addView(buildDivider(ctx));

        // Action row 1
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(-1,-2);
        r1lp.bottomMargin = dp(ctx,6);
        row1.setLayoutParams(r1lp);

        TextView btnBan = isBanned
            ? makeActionBtn(ctx,"✅ Unban","#22E5FF","#051520","#0A3040")
            : makeActionBtn(ctx,"⛔ Ban","#F87171","#1A0808","#7F1D1D");
        attachPressScale(btnBan);
        btnBan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                haptic(ctx);
                if(isBanned) unbanKey(ctx,encoded); else showBanDialogForKey(ctx,encoded);
            }
        });
        row1.addView(btnBan); addSpacer(row1,ctx);

        TextView btnEdit = makeActionBtn(ctx,"✏ Edit","#A78BFA","#150E2A","#4C1D95");
        attachPressScale(btnEdit);
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showEditDialog(ctx,encoded,type,devLimit,expired); }
        });
        row1.addView(btnEdit); addSpacer(row1,ctx);

        TextView btnExtend = makeActionBtn(ctx,"⏩ Extend","#34D399","#062A1A","#065F46");
        attachPressScale(btnExtend);
        btnExtend.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showExtendSheet(ctx,encoded); }
        });
        row1.addView(btnExtend); addSpacer(row1,ctx);

        TextView btnDel = makeActionBtn(ctx,"🗑","#FBBF24","#1A1208","#78350F");
        attachPressScale(btnDel);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmDeleteKey(ctx,encoded,plainFinal); }
        });
        row1.addView(btnDel);
        card.addView(row1);

        // Action row 2
        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));

        TextView btnRevoke = makeActionBtn(ctx,"📵 Revoke Devices","#64748B","#0C111A","#1E293B");
        attachPressScale(btnRevoke);
        btnRevoke.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmRevokeDevices(ctx,encoded,plainFinal); }
        });
        row2.addView(btnRevoke); addSpacer(row2,ctx);

        TextView btnReset = makeActionBtn(ctx,"🔄 Reset Violations","#64748B","#0C111A","#1E293B");
        attachPressScale(btnReset);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmResetViolations(ctx,encoded,plainFinal); }
        });
        row2.addView(btnReset);
        card.addView(row2);

        // Devices
        if (devsFinal.length()>0) {
            TextView devLabel = new TextView(ctx);
            devLabel.setText("📱  "+devsFinal.length()+" Device(s)");
            devLabel.setTextColor(Color.parseColor("#7DD3FC"));
            devLabel.setTextSize(10f);
            devLabel.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1,-2);
            dlp.topMargin=dp(ctx,10); dlp.bottomMargin=dp(ctx,4);
            devLabel.setLayoutParams(dlp);
            card.addView(devLabel);
            for (int i=0;i<devsFinal.length();i++) {
                try { card.addView(buildDeviceRow(ctx,devsFinal.getJSONObject(i),i+1,encoded)); }
                catch(Exception ignored){}
            }
        }

        if (bulkMode) {
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if(selectedKeys.contains(encoded)) {
                        selectedKeys.remove(encoded);
                        cardBg.setStroke(dp(ctx,1),Color.parseColor("#1E3A50"));
                        // FIX: update checkbox visual langsung tanpa rebuild list
                        if (chkBox != null) {
                            chkBox.setText("\u2610");
                            chkBox.setTextColor(Color.parseColor("#334155"));
                        }
                    } else {
                        selectedKeys.add(encoded);
                        cardBg.setStroke(dp(ctx,1),Color.parseColor("#22E5FF"));
                        // FIX: update checkbox visual langsung
                        if (chkBox != null) {
                            chkBox.setText("\u2611");
                            chkBox.setTextColor(Color.parseColor("#22E5FF"));
                        }
                    }
                    updateBulkCount();
                }
            });
        }
        return card;
    }

    private static LinearLayout buildInfoRow(Context ctx, String label, String value, String valColor) {
        LinearLayout row = new LinearLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,3);
        row.setLayoutParams(lp);
        TextView l = new TextView(ctx);
        l.setText(label+": "); l.setTextColor(Color.parseColor("#475569")); l.setTextSize(11f); l.setTypeface(Typeface.MONOSPACE);
        row.addView(l);
        TextView v = new TextView(ctx);
        try { v.setTextColor(Color.parseColor(valColor)); } catch(Exception e){ v.setTextColor(Color.parseColor("#94A3B8")); }
        v.setText(value); v.setTextSize(11f); v.setTypeface(Typeface.MONOSPACE);
        v.setMaxLines(1); v.setEllipsize(TextUtils.TruncateAt.END);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        row.addView(v);
        return row;
    }

    private static View buildDeviceRow(Context ctx, JSONObject d, int idx, final String encodedKey) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(ctx,6);
        row.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#060E1C"));
        bg.setCornerRadius(dp(ctx,10));
        bg.setStroke(dp(ctx,1),Color.parseColor("#1E293B"));
        row.setBackground(bg);
        row.setPadding(dp(ctx,12),dp(ctx,10),dp(ctx,12),dp(ctx,10));

        String mac      = d.optString("mac","-");
        String devId    = d.optString("device_id",mac);
        String name     = d.optString("device_name",d.optString("model","Unknown Device"));
        String osVer    = d.optString("os_version","-");
        String appVer   = d.optString("app_version","?");
        long lastUsed   = d.optLong("last_used",0L);
        long banned     = d.optLong("banned_until",0L);
        JSONObject loc  = d.optJSONObject("location_detail");
        JSONObject spec = d.optJSONObject("specs_detail");
        JSONObject sec  = d.optJSONObject("security_detail");
        boolean isBannedDev = banned>0 && banned>System.currentTimeMillis();

        LinearLayout hdr = new LinearLayout(ctx);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1,-2);
        hLp.bottomMargin = dp(ctx,6);
        hdr.setLayoutParams(hLp);
        TextView tName = new TextView(ctx);
        tName.setText("📱 "+name);
        tName.setTypeface(Typeface.DEFAULT_BOLD);
        tName.setTextColor(Color.parseColor("#38BDF8"));
        tName.setTextSize(12f);
        tName.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        hdr.addView(tName);
        TextView tStat = new TextView(ctx);
        tStat.setText(isBannedDev?"BANNED":"Active");
        tStat.setTextColor(isBannedDev?Color.parseColor("#F87171"):Color.parseColor("#34D399"));
        tStat.setTextSize(10f); tStat.setTypeface(Typeface.DEFAULT_BOLD);
        hdr.addView(tStat);
        row.addView(hdr);

        row.addView(buildSmallLine(ctx,"OS",osVer+" (app v"+appVer+")"));
        row.addView(buildSmallLine(ctx,"Last Login",lastUsed>0?formatTime(lastUsed):"Never"));
        if (loc!=null) {
            row.addView(buildSmallLine(ctx,"Location",loc.optString("city","?")+", "+loc.optString("country","?")));
            row.addView(buildSmallLine(ctx,"IP",loc.optString("ip",d.optString("ip","-"))));
        }
        if (spec!=null) {
            String bat=spec.optString("battery_level","-");
            row.addView(buildSmallLine(ctx,"Battery",bat+(spec.optBoolean("charging",false)?" ⚡":"")));
        }
        if (sec!=null) {
            boolean rooted=sec.optBoolean("is_rooted",false);
            boolean emu=sec.optBoolean("is_emulator",false);
            boolean hook=sec.optBoolean("has_frida",false)||sec.optBoolean("has_xposed",false);
            if(rooted||emu||hook) {
                String t=(rooted?"ROOT ":"")+(emu?"EMU ":"")+(hook?"HOOK":"");
                row.addView(buildSecLine(ctx,"⚠ Threats",true,t.trim()));
            } else row.addView(buildSecLine(ctx,"Security",false,"Clean"));
        }
        if (isBannedDev) row.addView(buildSmallLine(ctx,"⛔ Banned Until",formatTime(banned)));

        LinearLayout acts = new LinearLayout(ctx);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setGravity(Gravity.END);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(-1,-2);
        aLp.topMargin = dp(ctx,8);
        acts.setLayoutParams(aLp);
        final String tid = devId;
        if (isBannedDev) {
            TextView btnU = makeActionBtn(ctx,"UNBAN DEVICE","#22E5FF","#051520","#0A3040");
            attachPressScale(btnU);
            btnU.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { unbanDevice(ctx,tid,encodedKey); }
            });
            acts.addView(btnU);
        } else {
            TextView btnB = makeActionBtn(ctx,"BAN DEVICE","#F87171","#1A0808","#7F1D1D");
            attachPressScale(btnB);
            btnB.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showBanDialogForDevice(ctx,tid,encodedKey); }
            });
            acts.addView(btnB);
        }
        row.addView(acts);
        return row;
    }

    // ===================== NEW ACTIONS =====================
    private static void showEditDialog(final Context ctx, final String encoded,
            final String curType, final int curLimit, final long curExp) {
        final String[] typeOpts = {"1 Day","3 Day","7 Day","14 Day","30 Day","Permanent","Custom"};
        final String[] selType = {curType};

        final android.app.Dialog dialog = new android.app.Dialog(ctx,android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCanceledOnTouchOutside(true);
        FrameLayout dimRoot = new FrameLayout(ctx);
        View dim = new View(ctx); dim.setBackgroundColor(0xAA000000);
        dim.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        dim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v){dialog.dismiss();} });
        dimRoot.addView(dim);

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(ctx,16),dp(ctx,16),dp(ctx,16),dp(ctx,16));
        GradientDrawable wBg = new GradientDrawable();
        wBg.setColor(Color.parseColor("#0A1628")); wBg.setCornerRadius(dp(ctx,16));
        wBg.setStroke(dp(ctx,1),Color.parseColor("#22E5FF"));
        wrap.setBackground(wBg);

        TextView editTitle = new TextView(ctx);
        editTitle.setText("✏  Edit Key");
        editTitle.setTextColor(Color.WHITE); editTitle.setTextSize(15f); editTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams etLp2 = new LinearLayout.LayoutParams(-1,-2); etLp2.bottomMargin=dp(ctx,12);
        editTitle.setLayoutParams(etLp2);
        wrap.addView(editTitle);

        // Type picker
        final LinearLayout typeRow = new LinearLayout(ctx);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        typeRow.setPadding(dp(ctx,12),0,dp(ctx,12),0);
        GradientDrawable trBg = new GradientDrawable();
        trBg.setColor(0x1AFFFFFF); trBg.setCornerRadius(dp(ctx,10)); trBg.setStroke(dp(ctx,1),0x5522E5FF);
        typeRow.setBackground(trBg);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1,dp(ctx,44)); trLp.bottomMargin=dp(ctx,12);
        typeRow.setLayoutParams(trLp);
        final TextView typeLabel = new TextView(ctx);
        typeLabel.setText("Type: "+curType);
        typeLabel.setTextColor(Color.WHITE); typeLabel.setTextSize(13f); typeLabel.setTypeface(Typeface.MONOSPACE);
        typeLabel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView typeArrow = new TextView(ctx); typeArrow.setText("▾"); typeArrow.setTextColor(Color.parseColor("#22E5FF"));
        typeRow.addView(typeLabel); typeRow.addView(typeArrow);
        wrap.addView(typeRow);

        TextView lLabel = new TextView(ctx);
        lLabel.setText("Device Limit:"); lLabel.setTextColor(Color.parseColor("#7DD3FC")); lLabel.setTextSize(11f);
        lLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp2 = new LinearLayout.LayoutParams(-1,-2); llp2.bottomMargin=dp(ctx,4);
        lLabel.setLayoutParams(llp2);
        wrap.addView(lLabel);

        final EditText etLimit = new EditText(ctx);
        etLimit.setText(String.valueOf(curLimit));
        etLimit.setTextColor(Color.WHITE); etLimit.setTextSize(13f); etLimit.setGravity(Gravity.CENTER);
        etLimit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        GradientDrawable etBg2 = new GradientDrawable();
        etBg2.setColor(0x1AFFFFFF); etBg2.setCornerRadius(dp(ctx,10)); etBg2.setStroke(dp(ctx,1),0x5522E5FF);
        etLimit.setBackground(etBg2); etLimit.setPadding(dp(ctx,12),dp(ctx,8),dp(ctx,12),dp(ctx,8));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1,dp(ctx,44)); elp.bottomMargin=dp(ctx,4);
        etLimit.setLayoutParams(elp);
        wrap.addView(etLimit);

        LinearLayout btnRow = new LinearLayout(ctx);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1,-2); brLp.topMargin=dp(ctx,14);
        btnRow.setLayoutParams(brLp);
        TextView btnCancel = makeActionBtn(ctx,"Cancel","#64748B","#0C111A","#1E293B");
        btnCancel.setLayoutParams(new LinearLayout.LayoutParams(0,dp(ctx,44),1f));
        attachPressScale(btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v){dialog.dismiss();} });
        btnRow.addView(btnCancel);
        View sp = new View(ctx); sp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx,8),0)); btnRow.addView(sp);
        final String[] sType = selType;
        TextView btnSave = makeActionBtn(ctx,"✅ Save","#22E5FF","#051520","#0A3040");
        btnSave.setLayoutParams(new LinearLayout.LayoutParams(0,dp(ctx,44),1f));
        attachPressScale(btnSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                try {
                    int lim = Integer.parseInt(etLimit.getText().toString().trim());
                    JSONObject ex = new JSONObject();
                    ex.put("target_encoded",encoded); ex.put("type",sType[0]); ex.put("device_limit",lim);
                    sendAdminAction("edit_key",ex);
                } catch(Exception e){ postError("An error occurred: "+e.getMessage()); }
            }
        });
        btnRow.addView(btnSave);
        wrap.addView(btnRow);

        typeRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showBanSheet(ctx,"Select Type",typeOpts,sType[0],new SheetCallback() {
                    @Override public void onSelected(String val, int i) { sType[0]=val; typeLabel.setText("Type: "+val); }
                });
            }
        });

        FrameLayout.LayoutParams wLp = new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        wLp.leftMargin=dp(ctx,20); wLp.rightMargin=dp(ctx,20);
        dimRoot.addView(wrap,wLp);
        dialog.setContentView(dimRoot);
        wrap.setAlpha(0f); wrap.setScaleX(0.9f); wrap.setScaleY(0.9f);
        wrap.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        dialog.show();
    }

    private static void showExtendSheet(final Context ctx, final String encoded) {
        final String[] opts = {"+ 1 Day","+ 3 Day","+ 7 Day","+ 14 Day","+ 30 Day","+ 90 Day","+ 1 Year"};
        final long[] ms = {86400000L,3*86400000L,7*86400000L,14*86400000L,30*86400000L,90*86400000L,365*86400000L};
        showBanSheet(ctx,"Extend Duration",opts,opts[2],new SheetCallback() {
            @Override public void onSelected(String val, int i) {
                try {
                    JSONObject ex = new JSONObject();
                    ex.put("target_encoded",encoded); ex.put("extend_ms",ms[i]);
                    sendAdminAction("extend_key",ex);
                } catch(Exception e){ postError("An error occurred: "+e.getMessage()); }
            }
        });
    }

    private static void confirmRevokeDevices(final Context ctx, final String enc, String pk) {
        showConfirmDialog(ctx,"Revoke Devices","All devices from key \""+pk+"\" will be removed. The key remains active.","#F87171",
            new Runnable() { @Override public void run() {
                try { JSONObject ex=new JSONObject(); ex.put("target_encoded",enc); sendAdminAction("revoke_devices",ex); }
                catch(Exception e){ postError(e.getMessage()); }
            }});
    }

    private static void confirmResetViolations(final Context ctx, final String enc, String pk) {
        showConfirmDialog(ctx,"Reset Violations","Reset violation count for key \""+pk+"\" to 0?","#34D399",
            new Runnable() { @Override public void run() {
                try { JSONObject ex=new JSONObject(); ex.put("target_encoded",enc); sendAdminAction("reset_violations",ex); }
                catch(Exception e){ postError(e.getMessage()); }
            }});
    }

    private static void confirmDeleteKey(final Context ctx, final String enc, String pk) {
        showConfirmDialog(ctx,"Delete Key","Key \""+pk+"\" will be permanently deleted. This cannot be undone!","#F87171",
            new Runnable() { @Override public void run() {
                try { JSONObject ex=new JSONObject(); ex.put("target_encoded",enc); sendAdminAction("delete_key",ex); }
                catch(Exception e){ postError(e.getMessage()); }
            }});
    }

    private static void confirmBulkDelete(final Context ctx) {
        int cnt = selectedKeys.size();
        showConfirmDialog(ctx,"Bulk Delete",cnt+" keys will be permanently deleted. This cannot be undone!","#F87171",
            new Runnable() { @Override public void run() {
                try {
                    JSONArray tgts = new JSONArray();
                    for(String k:selectedKeys) tgts.put(k);
                    JSONObject pl = new JSONObject();
                    pl.put("targets",tgts);
                    sendAdminActionRaw("bulk_delete",pl);
                    selectedKeys.clear(); bulkMode=false;
                    if(bulkBarRef!=null) bulkBarRef.setVisibility(View.GONE);
                    updateBulkCount();
                } catch(Exception e){ postError(e.getMessage()); }
            }});
    }

    private static void showConfirmDialog(final Context ctx, String title, String msg,
            String accentColor, final Runnable onConfirm) {
        final android.app.Dialog dialog = new android.app.Dialog(ctx,android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCanceledOnTouchOutside(true);
        FrameLayout dimRoot = new FrameLayout(ctx);
        View dim = new View(ctx); dim.setBackgroundColor(0xAA000000);
        dim.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        dim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v){dialog.dismiss();} });
        dimRoot.addView(dim);

        int ac; try{ ac=Color.parseColor(accentColor); }catch(Exception e){ ac=Color.parseColor("#22E5FF"); }
        final int acF=ac;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20));
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(Color.parseColor("#0A1628")); cBg.setCornerRadius(dp(ctx,16)); cBg.setStroke(dp(ctx,1),ac);
        card.setBackground(cBg);

        TextView tvT = new TextView(ctx); tvT.setText(title); tvT.setTextColor(Color.WHITE);
        tvT.setTextSize(16f); tvT.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(-1,-2); ttLp.bottomMargin=dp(ctx,10);
        tvT.setLayoutParams(ttLp); card.addView(tvT);

        View divv = new View(ctx); divv.setBackgroundColor(0x3322E5FF);
        LinearLayout.LayoutParams dvLp = new LinearLayout.LayoutParams(-1,dp(ctx,1)); dvLp.bottomMargin=dp(ctx,12);
        divv.setLayoutParams(dvLp); card.addView(divv);

        TextView tvM = new TextView(ctx); tvM.setText(msg); tvM.setTextColor(Color.parseColor("#94A3B8"));
        tvM.setTextSize(13f);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(-1,-2); mLp.bottomMargin=dp(ctx,16);
        tvM.setLayoutParams(mLp); card.addView(tvM);

        LinearLayout bRow = new LinearLayout(ctx);
        TextView bCancel = makeActionBtn(ctx,"Cancel","#64748B","#0C111A","#1E293B");
        bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,dp(ctx,44),1f));
        attachPressScale(bCancel);
        bCancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v){dialog.dismiss();} });
        bRow.addView(bCancel);
        View bSp = new View(ctx); bSp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx,8),0)); bRow.addView(bSp);
        TextView bOk = new TextView(ctx); bOk.setText("✅ Confirm");
        bOk.setTextColor(Color.parseColor("#001A2E")); bOk.setTextSize(13f); bOk.setTypeface(Typeface.DEFAULT_BOLD);
        bOk.setGravity(Gravity.CENTER); bOk.setPadding(dp(ctx,12),dp(ctx,8),dp(ctx,12),dp(ctx,8));
        GradientDrawable okBg = new GradientDrawable(); okBg.setColor(acF); okBg.setCornerRadius(dp(ctx,10));
        bOk.setBackground(okBg); bOk.setLayoutParams(new LinearLayout.LayoutParams(0,dp(ctx,44),1f));
        attachPressScale(bOk);
        bOk.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); if(onConfirm!=null) onConfirm.run(); }
        });
        bRow.addView(bOk); card.addView(bRow);

        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        cLp.leftMargin=dp(ctx,20); cLp.rightMargin=dp(ctx,20);
        dimRoot.addView(card,cLp);
        dialog.setContentView(dimRoot);
        card.setAlpha(0f); card.setScaleX(0.9f); card.setScaleY(0.9f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        dialog.show();
    }

    interface SheetCallback { void onSelected(String val, int idx); }

    private static void showBanSheet(final Context ctx, final String title,
            final String[] opts, final String current, final SheetCallback cb) {
        android.app.Dialog dialog = new android.app.Dialog(ctx,android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCanceledOnTouchOutside(true);
        FrameLayout root = new FrameLayout(ctx);
        final android.app.Dialog df = dialog;
        View dim = new View(ctx); dim.setBackgroundColor(0xAA000000);
        dim.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        dim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v){df.dismiss();} });
        root.addView(dim);

        LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(ctx,16),dp(ctx,20),dp(ctx,16),dp(ctx,32));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.parseColor("#0A1628"));
        sBg.setCornerRadii(new float[]{dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20),0,0,0,0});
        sBg.setStroke(dp(ctx,1),Color.parseColor("#1A3A50"));
        sheet.setBackground(sBg);
        FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(-1,-2); sLp.gravity=Gravity.BOTTOM;
        sheet.setLayoutParams(sLp);

        View handle = new View(ctx);
        GradientDrawable hBg = new GradientDrawable(); hBg.setColor(Color.parseColor("#22455A")); hBg.setCornerRadius(dp(ctx,4));
        handle.setBackground(hBg);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(dp(ctx,40),dp(ctx,4));
        hLp.gravity=Gravity.CENTER_HORIZONTAL; hLp.bottomMargin=dp(ctx,16); handle.setLayoutParams(hLp);
        sheet.addView(handle);

        TextView tvT = new TextView(ctx); tvT.setText(title); tvT.setTextColor(Color.parseColor("#22E5FF"));
        tvT.setTextSize(14f); tvT.setTypeface(Typeface.DEFAULT_BOLD); tvT.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(-1,-2); ttlp.bottomMargin=dp(ctx,12);
        tvT.setLayoutParams(ttlp); sheet.addView(tvT);

        View div = new View(ctx); div.setBackgroundColor(Color.parseColor("#1A3A50"));
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(-1,dp(ctx,1)); dlp2.bottomMargin=dp(ctx,8);
        div.setLayoutParams(dlp2); sheet.addView(div);

        for (int i=0;i<opts.length;i++) {
            final String opt=opts[i]; final int fi=i;
            boolean sel = opt.equals(current);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx,16),dp(ctx,13),dp(ctx,16),dp(ctx,13));
            if(sel){ GradientDrawable rb=new GradientDrawable(); rb.setColor(Color.parseColor("#0D2A3A"));
                rb.setCornerRadius(dp(ctx,10)); rb.setStroke(dp(ctx,1),Color.parseColor("#22E5FF")); row.setBackground(rb); }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1,-2); rlp.bottomMargin=dp(ctx,4);
            row.setLayoutParams(rlp);
            TextView tvO = new TextView(ctx); tvO.setText(opt);
            tvO.setTextColor(sel?Color.parseColor("#22E5FF"):Color.parseColor("#CBD5E1"));
            tvO.setTextSize(14f); tvO.setTypeface(sel?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
            tvO.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); row.addView(tvO);
            if(sel){ TextView chk=new TextView(ctx); chk.setText("✓"); chk.setTextColor(Color.parseColor("#22E5FF")); chk.setTextSize(16f); row.addView(chk); }
            attachPressScale(row);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { df.dismiss(); if(cb!=null) cb.onSelected(opt,fi); }
            });
            sheet.addView(row);
        }
        root.addView(sheet);
        dialog.setContentView(root);
        sheet.setTranslationY(600f);
        sheet.animate().translationY(0).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).start();
        dialog.show();
    }

    private static void showBanDialogForKey(final Context ctx, final String enc) {
        final String[] opts={"1 day","3 days","7 days","14 days","30 days","Permanent","Custom"};
        final long[] days={1,3,7,14,30,365L*100L,1};
        showBanSheet(ctx,"Ban Duration",opts,opts[0],new SheetCallback() {
            @Override public void onSelected(String v, int i) {
                if(i==6) showCustomBanInput(ctx,enc,true,null); else banKey(ctx,enc,days[i]);
            }
        });
    }

    private static void showBanDialogForDevice(final Context ctx, final String mac, final String enc) {
        final String[] opts={"1 day","3 days","7 days","14 days","30 days","Permanent","Custom"};
        final long[] days={1,3,7,14,30,365L*100L,1};
        showBanSheet(ctx,"Device Ban Duration",opts,opts[0],new SheetCallback() {
            @Override public void onSelected(String v, int i) {
                if(i==6) showCustomBanInput(ctx,mac,false,enc); else banDevice(ctx,mac,days[i],enc);
            }
        });
    }

    private static void showCustomBanInput(final Context ctx, final String target,
            final boolean isKey, final String encOrNull) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(ctx,12); wrap.setPadding(pad,pad,pad,pad);
        final EditText input = new EditText(ctx);
        input.setHint("Number of days (e.g. 90)");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.parseColor("#E0F2FE")); input.setHintTextColor(Color.parseColor("#64748B")); input.setSingleLine(true);
        GradientDrawable etBg=new GradientDrawable(); etBg.setColor(Color.parseColor("#020617"));
        etBg.setCornerRadius(dp(ctx,10)); etBg.setStroke(dp(ctx,1),Color.parseColor("#22E5FF"));
        input.setBackground(etBg); input.setPadding(dp(ctx,10),dp(ctx,8),dp(ctx,10),dp(ctx,8));
        wrap.addView(input,new LinearLayout.LayoutParams(-1,-2));
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
        b.setTitle("Custom ban duration (days)"); b.setView(wrap);
        b.setPositiveButton("OK",new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                String txt=input.getText().toString().trim(); if(txt.isEmpty()) return;
                try { long dd=Long.parseLong(txt); if(isKey) banKey(ctx,target,dd); else banDevice(ctx,target,dd,encOrNull); }
                catch(Exception ignored){}
            }
        });
        b.setNegativeButton("Cancel",null);
        final android.app.AlertDialog dialog=b.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override public void onShow(android.content.DialogInterface di) {
                android.app.AlertDialog dlg=(android.app.AlertDialog)di;
                android.view.Window win=dlg.getWindow();
                if(win!=null) {
                    android.view.View cont=win.getDecorView().findViewById(android.R.id.content);
                    if(cont!=null){ GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.parseColor("#0A1628"));
                        bg.setCornerRadius(dp(ctx,16)); bg.setStroke(dp(ctx,1),Color.parseColor("#22E5FF"));
                        cont.setBackground(bg); cont.setPadding(dp(ctx,12),dp(ctx,12),dp(ctx,12),dp(ctx,12)); }
                }
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#22E5FF"));
                dlg.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#F87171"));
                TextView tt=dlg.findViewById(ctx.getResources().getIdentifier("alertTitle","id","android"));
                if(tt!=null) tt.setTextColor(Color.parseColor("#E0F2FE"));
            }
        });
        dialog.show();
    }

    private static void sendAdminAction(final String action, final JSONObject extras) {
        try {
            JSONObject pl = new JSONObject(); pl.put("action",action);
            if(extras!=null){ JSONArray names=extras.names();
                if(names!=null) for(int i=0;i<names.length();i++) { String k=names.getString(i); pl.put(k,extras.get(k)); } }
            ApiClient.getAdminService().postAction(pl.toString()).enqueue(new retrofit2.Callback<String>() {
                @Override public void onResponse(Call<String> call, Response<String> r) {
                    if(r.isSuccessful()) {
                        postSuccess(action + " completed successfully");
                        ActivityLog.log(ctxGlobal, action, extras!=null?extras.optString("target_encoded",""):"-");
                        postRefresh();
                    } else postError("Failed to execute " + action + " — HTTP " + r.code());
                }
                @Override public void onFailure(Call<String> call, Throwable t) { postError("An error occurred: "+t.getMessage()); }
            });
        } catch(Exception e){ postError("An error occurred: "+e.getMessage()); }
    }

    private static void sendAdminActionRaw(final String action, final JSONObject payload) {
        try {
            payload.put("action",action);
            ApiClient.getAdminService().postAction(payload.toString()).enqueue(new retrofit2.Callback<String>() {
                @Override public void onResponse(Call<String> call, Response<String> r) {
                    if(r.isSuccessful()) {
                        postSuccess(action + " completed successfully");
                        ActivityLog.log(ctxGlobal, action, selectedKeys.size()+" keys");
                        postRefresh();
                    } else postError("Request failed — HTTP " + r.code());
                }
                @Override public void onFailure(Call<String> call, Throwable t) { postError("An error occurred: "+t.getMessage()); }
            });
        } catch(Exception e){ postError("An error occurred: "+e.getMessage()); }
    }

    private static void banKey(Context ctx, String enc, long days) {
        try { long bu=System.currentTimeMillis()+days*24L*60L*60L*1000L;
            JSONObject ex=new JSONObject(); ex.put("target_encoded",enc); ex.put("banned_until",bu);
            sendAdminAction("ban_key",ex); } catch(Exception ignored){}
    }
    private static void unbanKey(Context ctx, String enc) {
        try { JSONObject ex=new JSONObject(); ex.put("target_encoded",enc); sendAdminAction("unban_key",ex); }
        catch(Exception ignored){}
    }
    private static void banDevice(Context ctx, String id, long days, String enc) {
        try { long bu=System.currentTimeMillis()+days*24L*60L*60L*1000L;
            JSONObject ex=new JSONObject(); ex.put("target_encoded",enc); ex.put("target_id",id); ex.put("banned_until",bu);
            sendAdminAction("ban_device",ex); } catch(Exception ignored){}
    }
    private static void unbanDevice(Context ctx, String id, String enc) {
        try { JSONObject ex=new JSONObject(); ex.put("target_encoded",enc); ex.put("target_id",id);
            sendAdminAction("unban_device",ex); } catch(Exception ignored){}
    }

    private static void postRefresh() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                if(ctxGlobal instanceof MainActivity) ((MainActivity)ctxGlobal).showViewer();
            }
        });
    }

    private static View buildDivider(Context ctx) {
        View l=new View(ctx); l.setBackgroundColor(0x1F22E5FF);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(ctx,1));
        lp.topMargin=dp(ctx,8); lp.bottomMargin=dp(ctx,8); l.setLayoutParams(lp); return l;
    }
    private static TextView buildSmallLine(Context ctx, String label, String val) {
        TextView tv=new TextView(ctx); tv.setText(label+": "+val);
        tv.setTextColor(Color.parseColor("#64748B")); tv.setTextSize(11f); tv.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(ctx,2); tv.setLayoutParams(lp);
        return tv;
    }
    private static TextView buildSecLine(Context ctx, String label, boolean risk, String detail) {
        TextView tv=new TextView(ctx); tv.setText(label+": "+detail);
        tv.setTextColor(risk?Color.parseColor("#F87171"):Color.parseColor("#34D399"));
        tv.setTextSize(11f); tv.setTypeface(risk?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(ctx,2); tv.setLayoutParams(lp);
        return tv;
    }
    private static TextView makeActionBtn(Context ctx, String label, String tc, String bc, String brc) {
        TextView btn=new TextView(ctx); btn.setText(label); btn.setTextSize(11f);
        btn.setTextColor(Color.parseColor(tc)); btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER); btn.setPadding(dp(ctx,10),dp(ctx,7),dp(ctx,10),dp(ctx,7));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.parseColor(bc));
        bg.setCornerRadius(dp(ctx,8)); bg.setStroke(dp(ctx,1),Color.parseColor(brc)); btn.setBackground(bg);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-2,-2)); return btn;
    }
    private static void addSpacer(LinearLayout row, Context ctx) {
        View sp=new View(ctx); sp.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx,6),0)); row.addView(sp);
    }
    private static void attachPressScale(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch(e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).setInterpolator(new DecelerateInterpolator()).start(); break;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(130).setInterpolator(new DecelerateInterpolator()).start(); break;
                }
                return false;
            }
        });
    }
    private static void showNeonToast(Context ctx, String msg) {
        LinearLayout layout=new LinearLayout(ctx); layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(ctx,20),dp(ctx,14),dp(ctx,20),dp(ctx,14)); layout.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.parseColor("#050816"),Color.parseColor("#0B1020")});
        bg.setCornerRadius(dp(ctx,16)); bg.setStroke(dp(ctx,2),Color.parseColor("#00FFF7")); layout.setBackground(bg);
        TextView tv=new TextView(ctx); tv.setText(msg); tv.setTextSize(13f);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            tv.setTextColor(Color.parseColor("#22E5FF")); tv.setShadowLayer(6f,0f,0f,Color.parseColor("#0080FF"));
        }
        layout.addView(tv);
        Toast toast=new Toast(ctx); toast.setDuration(Toast.LENGTH_SHORT); toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(ctx,100)); toast.show();
    }
    private static void postError(final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                if (ctxGlobal == null) return;
                // Red border toast for errors
                android.widget.LinearLayout layout = new android.widget.LinearLayout(ctxGlobal);
                layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                layout.setPadding(dp(ctxGlobal,20), dp(ctxGlobal,14), dp(ctxGlobal,20), dp(ctxGlobal,14));
                layout.setGravity(android.view.Gravity.CENTER);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{android.graphics.Color.parseColor("#050816"), android.graphics.Color.parseColor("#0B1020")}
                );
                bg.setCornerRadius(dp(ctxGlobal,16));
                bg.setStroke(dp(ctxGlobal,2), android.graphics.Color.parseColor("#F87171"));
                layout.setBackground(bg);
                android.widget.TextView tv = new android.widget.TextView(ctxGlobal);
                tv.setText(msg); tv.setTextSize(13f);
                tv.setTextColor(android.graphics.Color.parseColor("#F87171"));
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    tv.setShadowLayer(6f,0f,0f,android.graphics.Color.parseColor("#7F1D1D"));
                }
                layout.addView(tv);
                android.widget.Toast toast = new android.widget.Toast(ctxGlobal);
                toast.setDuration(android.widget.Toast.LENGTH_SHORT);
                toast.setView(layout);
                toast.setGravity(android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL,0,dp(ctxGlobal,100));
                toast.show();
            }
        });
    }

    private static void postSuccess(final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                if (ctxGlobal == null) return;
                // Cyan border toast for success
                android.widget.LinearLayout layout = new android.widget.LinearLayout(ctxGlobal);
                layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                layout.setPadding(dp(ctxGlobal,20), dp(ctxGlobal,14), dp(ctxGlobal,20), dp(ctxGlobal,14));
                layout.setGravity(android.view.Gravity.CENTER);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{android.graphics.Color.parseColor("#050816"), android.graphics.Color.parseColor("#0B1020")}
                );
                bg.setCornerRadius(dp(ctxGlobal,16));
                bg.setStroke(dp(ctxGlobal,2), android.graphics.Color.parseColor("#22E5FF"));
                layout.setBackground(bg);
                android.widget.TextView tv = new android.widget.TextView(ctxGlobal);
                tv.setText(msg); tv.setTextSize(13f);
                tv.setTextColor(android.graphics.Color.parseColor("#22E5FF"));
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    tv.setShadowLayer(6f,0f,0f,android.graphics.Color.parseColor("#0080FF"));
                }
                layout.addView(tv);
                android.widget.Toast toast = new android.widget.Toast(ctxGlobal);
                toast.setDuration(android.widget.Toast.LENGTH_SHORT);
                toast.setView(layout);
                toast.setGravity(android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL,0,dp(ctxGlobal,100));
                toast.show();
            }
        });
    }
    private static String formatTime(long ms) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.getDefault()).format(new Date(ms));
    }
    private static int dp(Context ctx, int val) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,val,ctx.getResources().getDisplayMetrics());
    }
}
