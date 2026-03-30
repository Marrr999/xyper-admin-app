package panel.xyper.keygen;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;


public class KeyDashboard {

    private static final String PREFS_NAME       = "xyper_dashboard";
    private static final String KEY_TREND        = "trend_history";
    private static final String KEY_DARK_MODE    = "dark_mode";
    private static final int    AUTO_REFRESH_MS  = 5 * 60 * 1000;

    private static final String IC_KEY          = "\uE0DA";
    private static final String IC_CHECK        = "\uE86C";
    private static final String IC_ALARM        = "\uE855";
    private static final String IC_INFINITE     = "\uEB3D";
    private static final String IC_PHONE        = "\uE32C";
    private static final String IC_BLOCK        = "\uE14B";
    private static final String IC_REFRESH      = "\uE5D5";
    private static final String IC_SHIELD       = "\uE897";
    private static final String IC_WARNING      = "\uE002";
    private static final String IC_HISTORY      = "\uE889";
    private static final String IC_GROUP        = "\uE7EF";
    private static final String IC_TRENDING_UP   = "\uE8E5";
    private static final String IC_TRENDING_DOWN = "\uE8E3";
    private static final String IC_TRENDING_FLAT = "\uE8E4";
    private static final String IC_SEARCH       = "\uE8B6";
    private static final String IC_SORT         = "\uE164";
    private static final String IC_SHARE        = "\uE80D";
    private static final String IC_ANALYTICS    = "\uE88A";
    private static final String IC_DARK_MODE    = "\uE51C";
    private static final String IC_LOCATION     = "\uE0C8";
    private static final String IC_CLOSE        = "\uE5CD";

    // ─── THEME ───────────────────────────────────────────────────────────────
    static boolean isDark = true;
    private static AnimatedBackgroundView sBgView = null;
    private static View sScanlines = null;

    // ── Theme color system ──────────────────────────────────────────
    static int bgOverlay()        { return isDark ? 0xEE060D1E : 0xFFF1F5F9; }
    static int cardFill(int c)    { return isDark ? applyAlpha(c, 0.22f) : 0xFFFFFFFF; }
    static int cardStroke(int c)  { return isDark ? applyAlpha(c, 0.75f) : applyAlpha(c, 0.4f); }
    static int textPrimary()      { return isDark ? Color.WHITE : Color.parseColor("#0F172A"); }
    static int textSecondary()    { return isDark ? 0xAAFFFFFF : 0xFF475569; }
    static int textMuted()        { return isDark ? 0x55FFFFFF : 0xFF94A3B8; }
    static int sectionBg()        { return isDark ? 0x22FFFFFF : 0xFFFFFFFF; }
    static int sectionStroke()    { return isDark ? 0x33FFFFFF : 0xFFE2E8F0; }
    static int getSearchBg()      { return isDark ? 0x1AFFFFFF : 0xFFFFFFFF; }
    static int getSearchStroke()  { return isDark ? 0x33FFFFFF : 0xFFCBD5E1; }
    static int bannerFill(int c)  { return isDark ? applyAlpha(c, 0.15f) : applyAlpha(c, 0.08f); }
    static int glassCard1()       { return isDark ? 0x2AFFFFFF : 0xFFFFFFFF; }
    static int glassCard2()       { return isDark ? 0x44FFFFFF : 0xFFE2E8F0; }
    static int labelColor()       { return isDark ? 0xEEFFFFFF : Color.parseColor("#1E293B"); }
    static int subtextColor(int c){ return isDark ? applyAlpha(c,0.75f) : darkenForLight(c); }
    private static int darkenForLight(int c) {
        int r = Math.max(0, Math.round(((c>>16)&0xFF)*0.7f));
        int g = Math.max(0, Math.round(((c>>8)&0xFF)*0.7f));
        int b = Math.max(0, Math.round((c&0xFF)*0.7f));
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }

    private static int lastTotal   = -1;
    private static int lastAktif   = -1;
    private static int lastDevices = -1;

    private static Handler autoRefreshHandler   = null;
    private static Runnable autoRefreshRunnable = null;

    // ===================== BUILD =====================
    public static View build(final Context ctx) {
        final Typeface mi = loadMI(ctx);
        final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final boolean[] darkMode = {prefs.getBoolean(KEY_DARK_MODE, true)};
        isDark = darkMode[0];

        final FrameLayout root = new FrameLayout(ctx);
        sBgView = new AnimatedBackgroundView(ctx);
        // Light mode: sembunyikan animated bg, ganti solid background
        sBgView.setVisibility(darkMode[0] ? View.VISIBLE : View.GONE);
        root.addView(sBgView, new FrameLayout.LayoutParams(-1, -1));
        View vigV = new VignetteView(ctx);
        vigV.setVisibility(darkMode[0] ? View.VISIBLE : View.GONE);
        root.addView(vigV, new FrameLayout.LayoutParams(-1, -1));
        final View overlay = new View(ctx);
        overlay.setBackgroundColor(bgOverlay());
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        // Scanline hanya di dark mode
        sScanlines = new ScanlineView(ctx);
        sScanlines.setVisibility(darkMode[0] ? View.VISIBLE : View.GONE);
        root.addView(sScanlines, new FrameLayout.LayoutParams(-1, -1));

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        final LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx,16), dp(ctx,16), dp(ctx,16), dp(ctx,90));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new LinearLayout.LayoutParams(-1, -2));

        final TextView updatedTv = new TextView(ctx);
        updatedTv.setTextColor(textMuted());
        updatedTv.setTextSize(10f);
        updatedTv.setTypeface(Typeface.MONOSPACE);
        updatedTv.setText("Awaiting data sync...");
        LinearLayout.LayoutParams updLp = new LinearLayout.LayoutParams(-1, -2);
        updLp.bottomMargin = dp(ctx, 14);
        updatedTv.setLayoutParams(updLp);

        final LinearLayout skeletonContainer = new LinearLayout(ctx);
        skeletonContainer.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout statsContainer = new LinearLayout(ctx);
        statsContainer.setOrientation(LinearLayout.VERTICAL);
        statsContainer.setVisibility(View.GONE);
        statsContainer.setClipChildren(false);
        statsContainer.setClipToPadding(false);

        content.addView(buildHeader(ctx, mi, prefs, darkMode, overlay,
									updatedTv, skeletonContainer, statsContainer));
        content.addView(updatedTv);
        content.addView(skeletonContainer);
        content.addView(statsContainer);

        buildSkeleton(ctx, skeletonContainer);
        loadStats(ctx, mi, prefs, skeletonContainer, statsContainer, updatedTv);
        startAutoRefresh(ctx, mi, prefs, skeletonContainer, statsContainer, updatedTv);

        return root;
    }

    // ===================== HEADER =====================
    private static View buildHeader(final Context ctx, final Typeface mi,
									final SharedPreferences prefs, final boolean[] darkMode, final View overlay,
									final TextView updatedTv, final LinearLayout skeletonContainer,
									final LinearLayout statsContainer) {

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(ctx, 4);
        header.setLayoutParams(lp);

        AuroraView aurora = new AuroraView(ctx);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(-1, dp(ctx,4));
        aLp.bottomMargin = dp(ctx, 14);
        aurora.setLayoutParams(aLp);
        header.addView(aurora);

        int h = Integer.parseInt(new SimpleDateFormat("HH", Locale.getDefault()).format(new Date()));
        String greeting = h < 5 ? "🌙 Good Night" : h < 12 ? "🌅 Good Morning"
			: h < 17 ? "☀ Good Afternoon" : "🌆 Good Evening";
        TextView greetTv = new TextView(ctx);
        // Nama key langsung dari ApiAuthHelper — sudah plain text, tidak perlu decode
        String adminName = "Administrator";
        try {
            String keyName = ApiAuthHelper.getApiKey(ctx);
            if (keyName != null && !keyName.trim().isEmpty()) {
                adminName = keyName.trim();
            }
        } catch (Exception ignored) {}
        greetTv.setText(greeting + ", " + adminName);
        greetTv.setTypeface(Typeface.MONOSPACE);
        greetTv.setTextSize(11f);
        greetTv.setTextColor(isDark ? Color.parseColor("#38BDF8") : Color.parseColor("#0284C7"));
        header.addView(greetTv);

        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1,-2);
        trLp.topMargin = dp(ctx,4); trLp.bottomMargin = dp(ctx,2);
        titleRow.setLayoutParams(trLp);

        final TextView title = new GlitchTextView(ctx);
        title.setText("Dashboard");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(30f);
        title.setTextColor(Color.WHITE);
        title.setShadowLayer(22f, 0f, 0f, isDark ? Color.parseColor("#22E5FF") : Color.parseColor("#0EA5E9"));
            title.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        title.setTextColor(textPrimary());
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        titleRow.addView(title);

        final TextView modeBtn = new TextView(ctx);
        modeBtn.setText(darkMode[0] ? IC_DARK_MODE : "\uE518");
        if (mi != null) modeBtn.setTypeface(mi);
        modeBtn.setTextSize(22f);
        modeBtn.setTextColor(Color.parseColor("#FBBF24"));
        modeBtn.setPadding(dp(ctx,8),0,0,0);
        modeBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					vibrate(ctx, 15);
					// Bounce animasi icon
					modeBtn.animate().scaleX(0.75f).scaleY(0.75f).setDuration(90)
						.withEndAction(new Runnable() { @Override public void run() {
							modeBtn.animate().scaleX(1f).scaleY(1f)
								.setInterpolator(new android.view.animation.OvershootInterpolator(2f))
								.setDuration(180).start();
						}}).start();

					// Fade out content dulu
					statsContainer.animate().alpha(0f).setDuration(150)
						.withEndAction(new Runnable() { @Override public void run() {

							// Switch theme state
							darkMode[0] = !darkMode[0];
							isDark = darkMode[0];
							prefs.edit().putBoolean(KEY_DARK_MODE, darkMode[0]).apply();
							modeBtn.setText(isDark ? IC_DARK_MODE : "\uE518");
							updatedTv.setTextColor(textMuted());

							// Animate background crossfade
							int fromBg = isDark ? 0xFFF1F5F9 : 0xEE060D1E;
							int toBg   = bgOverlay();
							android.animation.ValueAnimator bgAnim =
								android.animation.ValueAnimator.ofArgb(fromBg, toBg);
							bgAnim.setDuration(350);
							bgAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
							bgAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
								@Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
									overlay.setBackgroundColor((int) a.getAnimatedValue());
								}
							});
							bgAnim.start();

							// Show/hide bg elements
							if (sBgView != null) {
								if (isDark) {
									sBgView.setAlpha(0f);
									sBgView.setVisibility(View.VISIBLE);
									sBgView.animate().alpha(1f).setDuration(400).start();
								} else {
									sBgView.animate().alpha(0f).setDuration(200)
										.withEndAction(new Runnable() { @Override public void run() {
											sBgView.setVisibility(View.GONE);
										}}).start();
								}
							}
							if (sScanlines != null) sScanlines.setVisibility(isDark ? View.VISIBLE : View.GONE);

							// Rebuild content
							statsContainer.removeAllViews();
							skeletonContainer.setVisibility(View.VISIBLE);
							buildSkeleton(ctx, skeletonContainer);
							updatedTv.setText("Applying theme...");
							loadStats(ctx, mi, prefs, skeletonContainer, statsContainer, updatedTv);

							// Fade in
							statsContainer.setAlpha(0f);
							statsContainer.setVisibility(View.VISIBLE);
							statsContainer.animate().alpha(1f).setDuration(300)
								.setStartDelay(100).start();
						}}).start();
				}
			});
        titleRow.addView(modeBtn);


        final TextView refreshBtn = new TextView(ctx);
        refreshBtn.setText(IC_REFRESH);
        if (mi != null) refreshBtn.setTypeface(mi);
        refreshBtn.setTextSize(26f);
        refreshBtn.setTextColor(Color.parseColor("#22E5FF"));
        refreshBtn.setShadowLayer(12f,0f,0f,Color.parseColor("#22E5FF"));
            refreshBtn.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        refreshBtn.setPadding(dp(ctx,10),0,0,0);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					ValueAnimator spin = ValueAnimator.ofFloat(0f, 360f);
					spin.setDuration(500);
					spin.setInterpolator(new AccelerateDecelerateInterpolator());
					spin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
							@Override public void onAnimationUpdate(ValueAnimator a) {
								refreshBtn.setRotation((float) a.getAnimatedValue());
							}
						});
					spin.start();
					vibrate(ctx, 20);
					statsContainer.setVisibility(View.GONE);
					statsContainer.removeAllViews();
					skeletonContainer.setVisibility(View.VISIBLE);
					buildSkeleton(ctx, skeletonContainer);
					updatedTv.setTextColor(textMuted());
					updatedTv.setText("Synchronizing...");
					loadStats(ctx, mi, prefs, skeletonContainer, statsContainer, updatedTv);
				}
			});
        titleRow.addView(refreshBtn);
        header.addView(titleRow);

        TextView subtitle = new TextView(ctx);
        subtitle.setText("Xyper Xit  ·  Management Console");
        subtitle.setTypeface(Typeface.MONOSPACE);
        subtitle.setTextSize(11f);
        subtitle.setTextColor(isDark ? Color.parseColor("#38BDF8") : Color.parseColor("#0369A1"));
        subtitle.setLetterSpacing(0.08f);
        header.addView(subtitle);
        return header;
    }

    // ===================== AUTO REFRESH =====================
    private static void startAutoRefresh(final Context ctx, final Typeface mi,
										 final SharedPreferences prefs, final LinearLayout skeleton,
										 final LinearLayout stats, final TextView updatedTv) {
        if (autoRefreshHandler != null && autoRefreshRunnable != null)
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        autoRefreshHandler = new Handler(Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override public void run() {
                stats.setVisibility(View.GONE);
                stats.removeAllViews();
                skeleton.setVisibility(View.VISIBLE);
                buildSkeleton(ctx, skeleton);
                updatedTv.setText("Auto-sync in progress...");
                loadStats(ctx, mi, prefs, skeleton, stats, updatedTv);
                autoRefreshHandler.postDelayed(this, AUTO_REFRESH_MS);
            }
        };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);
    }

    // ===================== SKELETON =====================
    private static void buildSkeleton(Context ctx, LinearLayout container) {
        container.removeAllViews();
        for (int row = 0; row < 3; row++) {
            LinearLayout r = new LinearLayout(ctx);
            r.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, dp(ctx,130));
            rLp.bottomMargin = dp(ctx,10);
            r.setLayoutParams(rLp);
            for (int col = 0; col < 2; col++) {
                ShimmerView sv = new ShimmerView(ctx);
                LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0,-1,1f);
                sLp.setMargins(dp(ctx,4),0,dp(ctx,4),0);
                sv.setLayoutParams(sLp);
                r.addView(sv);
            }
            container.addView(r);
        }
        ShimmerView s = new ShimmerView(ctx);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, dp(ctx,120));
        sLp.topMargin = dp(ctx,8);
        s.setLayoutParams(sLp);
        container.addView(s);
    }

    // ===================== LOAD DATA =====================
    private static void loadStats(final Context ctx, final Typeface mi,
								  final SharedPreferences prefs, final LinearLayout skeleton,
								  final LinearLayout stats, final TextView updatedTv) {
        ApiClient.getAdminService().getAllKeys().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, retrofit2.Response<String> response) {
                if (!response.isSuccessful()) {
                    showError(ctx, skeleton, "Server error: " + response.code());
                    return;
                }
                try {
                    final JSONArray array = new JSONArray(response.body());
                    final String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() {
                            skeleton.setVisibility(View.GONE);
                            buildStatsUI(ctx, mi, prefs, array, stats, updatedTv, timeStr);
                            stats.setAlpha(0f);
                            stats.setVisibility(View.VISIBLE);
                            ValueAnimator fadeIn = ValueAnimator.ofFloat(0f, 1f);
                            fadeIn.setDuration(500);
                            fadeIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override public void onAnimationUpdate(ValueAnimator a) {
                                    stats.setAlpha((float) a.getAnimatedValue());
                                }
                            });
                            fadeIn.start();
                            updatedTv.setText("Last sync · " + timeStr + "  ·  Auto-refresh in 5m");
                        }
                    });
                } catch (final Exception e) {
                    showError(ctx, skeleton, e.getMessage());
                }
            }
            @Override
            public void onFailure(Call<String> call, Throwable t) {
                showError(ctx, skeleton, t.getMessage());
            }
        });
    }

    private static void showError(final Context ctx, final LinearLayout skeleton, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override public void run() {
					skeleton.removeAllViews();
					LinearLayout card = buildGlassCard(ctx, 0x22F87171, 0x66F87171, dp(ctx,16));
					card.setGravity(Gravity.CENTER);
					card.setPadding(dp(ctx,20),dp(ctx,30),dp(ctx,20),dp(ctx,30));
					TextView err = new TextView(ctx);
					err.setText("⚠  Connection Failed\n" + msg);
					err.setTextColor(Color.parseColor("#F87171"));
					err.setTextSize(12f); err.setGravity(Gravity.CENTER);
					err.setTypeface(Typeface.MONOSPACE);
					card.addView(err);
					skeleton.addView(card);
				}
			});
    }

    // ===================== BUILD STATS UI =====================
    private static void buildStatsUI(final Context ctx, final Typeface mi,
									 final SharedPreferences prefs, final JSONArray array,
									 final LinearLayout container, final TextView updatedTv, final String timeStr) {

        container.removeAllViews();
        long now = System.currentTimeMillis();

        int total=0, aktif=0, expired=0, permanent=0;
        int roleDev=0, roleReseller=0, roleUser=0;
        int totalDevices=0, bannedKeys=0, bannedDevices=0, totalViolations=0;
        long newestCreatedAt=0; String newestKeyName=null;
        int expiring7Days=0;

        JSONArray recentKeys=new JSONArray(), expiringSoon=new JSONArray();
        java.util.LinkedHashMap<String, int[]> resellerStats = new java.util.LinkedHashMap<>();
        // int[0]=total, int[1]=active, int[2]=expired, int[3]=banned
        Map<String,Integer> countryMap = new HashMap<String,Integer>();
        List<JSONObject> violatorList  = new ArrayList<JSONObject>();
        List<JSONObject> suspiciousList= new ArrayList<JSONObject>();

        total = array.length();
        for (int i=0; i<array.length(); i++) {
            try {
                JSONObject obj   = array.getJSONObject(i);
                long exp         = obj.optLong("expired",0);
                String type      = obj.optString("type","");
                String role      = obj.optString("role","User");
                long bannedUntil = obj.optLong("banned_until",0);
                long createdAt   = obj.optLong("created_at",0);
                JSONArray devs   = obj.optJSONArray("devices");
                int vCount       = obj.optInt("violation_count",0);
                totalViolations += vCount;
                if (vCount > 0) violatorList.add(obj);

                if (devs != null) {
                    totalDevices += devs.length();
                    Set<String> ips = new HashSet<String>();
                    for (int d=0; d<devs.length(); d++) {
                        JSONObject dev = devs.getJSONObject(d);
                        if (dev.optLong("banned_until",0) > now) bannedDevices++;
                        String ip = dev.optString("ip","");
                        if (!ip.isEmpty()) ips.add(ip);
                        String loc = dev.optString("location_detail","");
                        if (!loc.isEmpty()) {
                            try {
                                JSONObject locObj = new JSONObject(loc);
                                String country = locObj.optString("country","Unknown");
                                if (!country.isEmpty()) {
                                    int cnt = countryMap.containsKey(country) ? countryMap.get(country) : 0;
                                    countryMap.put(country, cnt+1);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    if (ips.size() > 2) suspiciousList.add(obj);
                }

                if (bannedUntil > now) bannedKeys++;
                if (type.equals("Permanent")) { permanent++; aktif++; }
                else if (exp > now) {
                    aktif++;
                    long days = (exp-now)/(24L*3600L*1000L);
                    if (days < 3) expiringSoon.put(obj);
                    if (days < 7) expiring7Days++;
                } else { expired++; }

                if      (role.equals("Developer")) roleDev++;
                else if (role.equals("Reseller"))  roleReseller++;
                else                               roleUser++;

                // Per-reseller breakdown
                if (role.equals("Client")) {
                    String ownerKey = obj.optString("owner_key","unknown");
                    if (!resellerStats.containsKey(ownerKey)) resellerStats.put(ownerKey, new int[4]);
                    int[] rs = resellerStats.get(ownerKey);
                    rs[0]++; // total
                    if (bannedUntil > now) rs[3]++;
                    else if (exp > 0 && exp < now) rs[2]++;
                    else rs[1]++; // active
                }

                if (createdAt > newestCreatedAt) {
                    newestCreatedAt = createdAt;
                    newestKeyName = decodeKey(obj.optString("key","-"));
                }
                if (recentKeys.length() < 8) recentKeys.put(obj);
            } catch (Exception ignored) {}
        }

        Collections.sort(violatorList, new Comparator<JSONObject>() {
				@Override public int compare(JSONObject a, JSONObject b) {
					return b.optInt("violation_count",0) - a.optInt("violation_count",0);
				}
			});

        int aktifRate = total>0 ? Math.round((float)aktif/total*100) : 0;
        float avgDev  = total>0 ? (float)totalDevices/total : 0f;
        int banRate   = totalDevices>0 ? Math.round((float)bannedDevices/totalDevices*100) : 0;
        int secScore  = Math.max(0, 100 - (totalViolations*5) - (bannedKeys*10));

        saveTrend(prefs, total);
        int[] trendHistory = loadTrend(prefs);

        boolean hasExpiredToday = false;
        for (int i=0; i<array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                long exp = obj.optLong("expired",0);
                if (!obj.optString("type","").equals("Permanent")
                    && exp<now && (now-exp)<86400000L) { hasExpiredToday=true; break; }
            } catch (Exception ignored) {}
        }

        // ── BANNERS ──
        if (hasExpiredToday)
            container.addView(buildBanner(ctx,mi,IC_WARNING,
										  "License keys expired today — review required","#F59E0B"));
        if (expiring7Days>0)
            container.addView(buildBanner(ctx,mi,IC_WARNING,
										  expiring7Days+" license(s) expiring within 7 days","#F59E0B"));
        if (!suspiciousList.isEmpty())
            container.addView(buildBanner(ctx,mi,IC_SHIELD,
										  suspiciousList.size()+" key(s) with suspicious IP activity","#F87171"));

        // ── STAT CARDS ──
        int cardH = dp(ctx,140), delay=0;
        LinearLayout row1 = makeRow(ctx);
        row1.addView(buildStatCard(ctx,mi,IC_KEY,"Total Licenses",
								   total,"#22E5FF",cardH,delay,lastTotal,aktifRate+"% Active Rate",false));
        delay+=80;
        row1.addView(buildStatCard(ctx,mi,IC_CHECK,"Active",
								   aktif,"#34D399",cardH,delay,lastAktif,aktifRate+"% of Total",false));
        delay+=80;
        container.addView(row1);

        LinearLayout row2 = makeRow(ctx);
        row2.addView(buildStatCard(ctx,mi,IC_ALARM,"Expired",
								   expired,"#F87171",cardH,delay,-1,
								   expired>0 ? expired+" Require Renewal":"All Clear",expired>0));
        delay+=80;
        row2.addView(buildStatCard(ctx,mi,IC_INFINITE,"Permanent",
								   permanent,"#FBBF24",cardH,delay,-1,"No Expiry",false));
        delay+=80;
        container.addView(row2);

        LinearLayout row3 = makeRow(ctx);
        row3.addView(buildStatCard(ctx,mi,IC_PHONE,"Devices",
								   totalDevices,"#A78BFA",cardH,delay,lastDevices,
								   String.format(Locale.getDefault(),"Avg %.1f / License",avgDev),false));
        delay+=80;
        row3.addView(buildStatCard(ctx,mi,IC_BLOCK,"Violations",
								   bannedKeys,"#FB923C",cardH,delay,-1,banRate+"% Violation Rate",bannedKeys>0));
        container.addView(row3);

        lastTotal=total; lastAktif=aktif; lastDevices=totalDevices;

        // ── TREND CHART ──
        if (trendHistory!=null && trendHistory.length>1) {
            addSectionTitle(ctx,mi,container,IC_ANALYTICS,"License Trend");
            container.addView(buildTrendChart(ctx, trendHistory, "#22E5FF"));
        }

        // ── NEWEST LICENSE ──
        if (newestKeyName != null) {
            String newestTime = new SimpleDateFormat("dd MMM HH:mm",
													 Locale.getDefault()).format(new Date(newestCreatedAt));
            LinearLayout nCard = buildGlassCard(ctx,0x2222E5FF,0x5522E5FF,dp(ctx,14));
            LinearLayout.LayoutParams ncLp = new LinearLayout.LayoutParams(-1,-2);
            ncLp.bottomMargin = dp(ctx,10); nCard.setLayoutParams(ncLp);
            LinearLayout nRow = new LinearLayout(ctx);
            nRow.setOrientation(LinearLayout.HORIZONTAL);
            nRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView nIcon = new TextView(ctx);
            nIcon.setText("NEW"); nIcon.setTextSize(9f);
            nIcon.setTypeface(Typeface.DEFAULT_BOLD);
            nIcon.setTextColor(Color.parseColor("#22E5FF"));
            GradientDrawable newBg = new GradientDrawable();
            newBg.setColor(0x3322E5FF); newBg.setCornerRadius(dp(ctx,4));
            newBg.setStroke(1,0x8822E5FF); nIcon.setBackground(newBg);
            nIcon.setPadding(dp(ctx,4),dp(ctx,2),dp(ctx,4),dp(ctx,2));
            LinearLayout.LayoutParams niLp = new LinearLayout.LayoutParams(-2,-2);
            niLp.rightMargin = dp(ctx,10); nIcon.setLayoutParams(niLp);
            TextView nKey = new TextView(ctx);
            nKey.setText(newestKeyName);
            nKey.setTextColor(textPrimary());
            nKey.setMaxLines(1);
            nKey.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nKey.setTextSize(13f); nKey.setTypeface(Typeface.MONOSPACE);
            nKey.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView nTime = new TextView(ctx);
            nTime.setText(newestTime); nTime.setTextColor(textMuted());
            nTime.setTextSize(10f); nTime.setTypeface(Typeface.MONOSPACE);
            nRow.addView(nIcon); nRow.addView(nKey); nRow.addView(nTime);
            nCard.addView(nRow);
            container.addView(nCard);
        }

        // ── SECURITY SCORE ──
        addSectionTitle(ctx,mi,container,IC_SHIELD,"Security Health Score");
        container.addView(buildSecurityScore(ctx, secScore));

        // ── ROLE DISTRIBUTION ──
        addSectionTitle(ctx,mi,container,IC_GROUP,"Access Role Distribution");
        final LinearLayout roleCard = buildGlassCard(ctx,0x33FFFFFF,0x44FFFFFF,dp(ctx,18));
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(-1,-2);
        rcLp.bottomMargin = dp(ctx,12); roleCard.setLayoutParams(rcLp);
        roleCard.addView(buildRoleBar(ctx,"Developer",roleDev,total,"#22E5FF",0));
        roleCard.addView(buildRoleBar(ctx,"Reseller",roleReseller,total,"#A78BFA",120));
        roleCard.addView(buildRoleBar(ctx,"User",roleUser,total,"#34D399",240));
        final boolean[] collapsed = {false};
        roleCard.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					collapsed[0] = !collapsed[0];
					for (int i=0; i<roleCard.getChildCount(); i++)
						roleCard.getChildAt(i).setVisibility(collapsed[0]?View.GONE:View.VISIBLE);
					ValueAnimator a = ValueAnimator.ofFloat(roleCard.getScaleY(), collapsed[0]?0.05f:1f);
					a.setDuration(250);
					a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
							@Override public void onAnimationUpdate(ValueAnimator anim) {
								roleCard.setScaleY((float)anim.getAnimatedValue());
							}
						});
					a.start();
				}
			});
        container.addView(roleCard);

        // ── RESELLER BREAKDOWN ──
        if (!resellerStats.isEmpty()) {
            addSectionTitle(ctx,mi,container,IC_GROUP,"Reseller Breakdown");
            final LinearLayout resellerCard = buildGlassCard(ctx,0x33A78BFA,0x44A78BFA,dp(ctx,18));
            LinearLayout.LayoutParams rbcLp = new LinearLayout.LayoutParams(-1,-2);
            rbcLp.bottomMargin = dp(ctx,12); resellerCard.setLayoutParams(rbcLp);

            for (java.util.Map.Entry<String,int[]> entry : resellerStats.entrySet()) {
                String ownerEnc = entry.getKey();
                int[] rs = entry.getValue();
                String ownerName = ownerEnc;
                if (!"unknown".equals(ownerEnc)) {
                    try { byte[] d = android.util.Base64.decode(ownerEnc, android.util.Base64.DEFAULT);
                        ownerName = new String(d, "UTF-8"); } catch (Exception ignored) {}
                }

                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1,-2);
                rowLp.bottomMargin = dp(ctx,12); row.setLayoutParams(rowLp);

                android.widget.TextView tvIcon2 = new android.widget.TextView(ctx);
                tvIcon2.setText("");
                tvIcon2.setTextColor(android.graphics.Color.parseColor("#A78BFA"));
                tvIcon2.setTextSize(16f);
                if (loadMI(ctx) != null) tvIcon2.setTypeface(loadMI(ctx));
                android.widget.LinearLayout.LayoutParams icLp2 = new android.widget.LinearLayout.LayoutParams(-2,-2);
                icLp2.rightMargin = dp(ctx,8); tvIcon2.setLayoutParams(icLp2);
                row.addView(tvIcon2);
                android.widget.TextView tvName = new android.widget.TextView(ctx);
                tvName.setText(ownerName);
                tvName.setTextColor(android.graphics.Color.parseColor("#A78BFA"));
                tvName.setTextSize(13f);
                tvName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tvName.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                row.addView(tvName);

                // badges: total, active, expired, banned
                String[][] badges = {
                    {String.valueOf(rs[0])+" total","#94A3B8"},
                    {rs[1]+" active","#34D399"},
                    {rs[2]+" exp","#FBBF24"},
                    {rs[3]+" ban","#F87171"}
                };
                for (String[] b : badges) {
                    android.widget.TextView tvB = new android.widget.TextView(ctx);
                    tvB.setText(b[0]);
                    tvB.setTextColor(android.graphics.Color.parseColor(b[1]));
                    tvB.setTextSize(10f);
                    tvB.setTypeface(android.graphics.Typeface.MONOSPACE);
                    tvB.setPadding(dp(ctx,6),dp(ctx,2),dp(ctx,6),dp(ctx,2));
                    android.graphics.drawable.GradientDrawable bBg = new android.graphics.drawable.GradientDrawable();
                    int bc = android.graphics.Color.parseColor(b[1]);
                    bBg.setColor(android.graphics.Color.argb(30,android.graphics.Color.red(bc),android.graphics.Color.green(bc),android.graphics.Color.blue(bc)));
                    bBg.setCornerRadius(dp(ctx,4));
                    tvB.setBackground(bBg);
                    LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-2,-2);
                    bLp.leftMargin = dp(ctx,4); tvB.setLayoutParams(bLp);
                    row.addView(tvB);
                }
                resellerCard.addView(row);
            }
            container.addView(resellerCard);
        }

        // ── GEOGRAPHIC DISTRIBUTION ──
        if (!countryMap.isEmpty()) {
            addSectionTitle(ctx,mi,container,IC_LOCATION,"Geographic Distribution");
            container.addView(buildGeoDistribution(ctx, countryMap));
        }

        // ── TOP VIOLATORS ──
        if (!violatorList.isEmpty()) {
            addSectionTitle(ctx,mi,container,IC_WARNING,"Top Violators");
            int maxShow = Math.min(3, violatorList.size());
            for (int i=0; i<maxShow; i++)
                container.addView(buildViolatorRow(ctx, violatorList.get(i)));
        }

        // ── SUSPICIOUS ACTIVITY ──
        if (!suspiciousList.isEmpty()) {
            addSectionTitle(ctx,mi,container,IC_SHIELD,"Suspicious Activity");
            for (int i=0; i<Math.min(3,suspiciousList.size()); i++)
                container.addView(buildSuspiciousRow(ctx, suspiciousList.get(i)));
        }

        // ── EXPIRING SOON ──
        if (expiringSoon.length()>0) {
            addSectionTitle(ctx,mi,container,IC_ALARM,"Expiring Soon");
            for (int i=0; i<expiringSoon.length(); i++) {
                try { container.addView(buildWarningRow(ctx,expiringSoon.getJSONObject(i),now)); }
                catch (Exception ignored) {}
            }
        }

        // ── EXPORT BUTTON ──
        container.addView(buildExportButton(ctx,mi,total,aktif,expired,
											permanent,totalDevices,bannedKeys,secScore,timeStr));

        // ── RECENT LICENSE ACTIVITY ──
        addSectionTitle(ctx,mi,container,IC_HISTORY,"Recent License Activity");
        container.addView(buildRecentKeysWithSearch(ctx, mi, array, now));
    }

    // ===================== TREND CHART =====================
    private static View buildTrendChart(Context ctx, int[] data, String color) {
        LinearLayout card = buildGlassCard(ctx,0x2222E5FF,0x4422E5FF,dp(ctx,16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,12); card.setLayoutParams(lp);
        TrendChartView chart = new TrendChartView(ctx, data, Color.parseColor(color));
        card.addView(chart, new LinearLayout.LayoutParams(-1, dp(ctx,110)));
        TextView label = new TextView(ctx);
        label.setText("Total licenses over last "+data.length+" syncs");
        label.setTextColor(0x66FFFFFF); label.setTextSize(10f);
        label.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1,-2);
        llp.topMargin = dp(ctx,6); label.setLayoutParams(llp);
        card.addView(label);
        return card;
    }

    // ===================== GEO DISTRIBUTION =====================
    private static View buildGeoDistribution(final Context ctx, Map<String,Integer> countryMap) {
        LinearLayout card = buildGlassCard(ctx,0x2234D399,0x4434D399,dp(ctx,16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,12); card.setLayoutParams(lp);

        List<Map.Entry<String,Integer>> entries =
            new ArrayList<Map.Entry<String,Integer>>(countryMap.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String,Integer>>() {
				@Override public int compare(Map.Entry<String,Integer> a, Map.Entry<String,Integer> b) {
					return b.getValue()-a.getValue();
				}
			});
        int total=0;
        for (Map.Entry<String,Integer> e : entries) total += e.getValue();
        final int tot = total;

        int maxShow = Math.min(5, entries.size());
        for (int i=0; i<maxShow; i++) {
            Map.Entry<String,Integer> e = entries.get(i);
            int pct = tot>0 ? Math.round((float)e.getValue()/tot*100) : 0;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1,-2);
            rLp.bottomMargin = dp(ctx,8); row.setLayoutParams(rLp);

            TextView country = new TextView(ctx);
            country.setText(e.getKey());
            country.setTextColor(isDark ? Color.parseColor("#34D399") : Color.parseColor("#059669"));
            country.setTextSize(12f); country.setTypeface(Typeface.DEFAULT_BOLD);
            country.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx,100),-2));

            final FrameLayout barFrame = new FrameLayout(ctx);
            barFrame.setLayoutParams(new LinearLayout.LayoutParams(0,dp(ctx,6),1f));
            View track = new View(ctx);
            GradientDrawable tb = new GradientDrawable();
            tb.setColor(0x33FFFFFF); tb.setCornerRadius(3f);
            track.setBackground(tb);
            barFrame.addView(track, new FrameLayout.LayoutParams(-1,-1));
            final View fill = new View(ctx);
            GradientDrawable fb = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x8834D399,0xFF34D399});
            fb.setCornerRadius(3f); fill.setBackground(fb);
            fill.setLayoutParams(new FrameLayout.LayoutParams(0,-1));
            barFrame.addView(fill);
            final float pctF = tot>0 ? (float)e.getValue()/tot : 0f;
            barFrame.post(new Runnable() {
					@Override public void run() {
						ValueAnimator a = ValueAnimator.ofInt(0,(int)(barFrame.getWidth()*pctF));
						a.setDuration(800); a.setInterpolator(new DecelerateInterpolator());
						a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
								@Override public void onAnimationUpdate(ValueAnimator anim) {
									fill.getLayoutParams().width = (int)anim.getAnimatedValue();
									fill.requestLayout();
								}
							});
						a.start();
					}
				});

            TextView pctTv = new TextView(ctx);
            pctTv.setText("  "+pct+"%"); pctTv.setTextColor(isDark ? 0x99FFFFFF : 0xFF475569);
            pctTv.setTextSize(11f); pctTv.setTypeface(Typeface.MONOSPACE);
            row.addView(country); row.addView(barFrame); row.addView(pctTv);
            card.addView(row);
        }
        return card;
    }

    // ===================== TOP VIOLATOR ROW =====================
    private static View buildViolatorRow(Context ctx, JSONObject obj) {
        LinearLayout card = buildGlassCard(ctx,0x22F87171,0x55F87171,dp(ctx,14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,8); card.setLayoutParams(lp);
        try {
            String decoded = decodeKey(obj.optString("key","-"));
            int vCount = obj.optInt("violation_count",0);
            String role = obj.optString("role","User");
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView keyTv = new TextView(ctx);
            keyTv.setText("🔑  "+decoded); keyTv.setTextColor(isDark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626"));
            keyTv.setTextSize(12f); keyTv.setTypeface(Typeface.MONOSPACE);
            keyTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            String roleColor = role.equals("Developer")?"#22E5FF":role.equals("Reseller")?"#A78BFA":"#34D399";
            TextView roleBadge = makeBadge(ctx, role, roleColor);
            LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(-2,-2);
            rbLp.rightMargin = dp(ctx,6); roleBadge.setLayoutParams(rbLp);
            TextView vTv = makeBadge(ctx, vCount+" violations", "#F87171");
            row.addView(keyTv); row.addView(roleBadge); row.addView(vTv);
            card.addView(row);
        } catch (Exception ignored) {}
        return card;
    }

    // ===================== SUSPICIOUS ROW =====================
    private static View buildSuspiciousRow(Context ctx, JSONObject obj) {
        LinearLayout card = buildGlassCard(ctx,0x22FBBF24,0x55FBBF24,dp(ctx,14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,8); card.setLayoutParams(lp);
        try {
            String decoded = decodeKey(obj.optString("key","-"));
            JSONArray devs = obj.optJSONArray("devices");
            Set<String> ips = new HashSet<String>();
            if (devs!=null) for (int i=0; i<devs.length(); i++)
					ips.add(devs.getJSONObject(i).optString("ip",""));
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView keyTv = new TextView(ctx);
            keyTv.setText("⚠  "+decoded); keyTv.setTextColor(isDark ? Color.parseColor("#FBBF24") : Color.parseColor("#D97706"));
            keyTv.setTextSize(12f); keyTv.setTypeface(Typeface.MONOSPACE);
            keyTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView ipTv = makeBadge(ctx, ips.size()+" unique IPs", "#FBBF24");
            row.addView(keyTv); row.addView(ipTv);
            card.addView(row);
        } catch (Exception ignored) {}
        return card;
    }

    // ===================== EXPORT BUTTON =====================
    private static View buildExportButton(final Context ctx, final Typeface mi,
										  final int total, final int aktif, final int expired, final int permanent,
										  final int devices, final int banned, final int score, final String time) {
        // Export row: two buttons — Summary TXT + CSV
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1,-2);
        rowLp.topMargin = dp(ctx,8); rowLp.bottomMargin = dp(ctx,8);
        row.setLayoutParams(rowLp);

        // ── Button 1: Summary TXT (original) ──
        LinearLayout btnTxt = new LinearLayout(ctx);
        btnTxt.setOrientation(LinearLayout.HORIZONTAL);
        btnTxt.setGravity(Gravity.CENTER);
        btnTxt.setPadding(dp(ctx,14),dp(ctx,12),dp(ctx,14),dp(ctx,12));
        GradientDrawable bg1 = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x3322E5FF,0x3334D399});
        bg1.setCornerRadius(dp(ctx,14)); bg1.setStroke(dp(ctx,1),0x7722E5FF);
        btnTxt.setBackground(bg1);
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0,-2,1f);
        b1Lp.rightMargin = dp(ctx,6); btnTxt.setLayoutParams(b1Lp);
        TextView lbl1 = new TextView(ctx);
        lbl1.setText("📋  Summary");
        lbl1.setTextColor(Color.parseColor("#22E5FF"));
        lbl1.setTextSize(13f); lbl1.setTypeface(Typeface.DEFAULT_BOLD);
        btnTxt.addView(lbl1);
        btnTxt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String summary = "╔══════════════════════════╗\n"
                    +"║   XYPER XIT — DASHBOARD  ║\n"
                    +"╚══════════════════════════╝\n"
                    +"Generated  : "+time+"\n\n"
                    +"LICENSES\n"
                    +"  Total      : "+total+"\n"
                    +"  Active     : "+aktif+"\n"
                    +"  Expired    : "+expired+"\n"
                    +"  Permanent  : "+permanent+"\n\n"
                    +"DEVICES\n"
                    +"  Total      : "+devices+"\n"
                    +"  Violations : "+banned+"\n\n"
                    +"SECURITY\n"
                    +"  Health Score : "+score+"/100\n";
                try {
                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                        java.util.Locale.getDefault()).format(new java.util.Date());
                    java.io.File file = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS),
                        "xyper_summary_"+ts+".txt");
                    java.io.FileWriter fw = new java.io.FileWriter(file);
                    fw.write(summary); fw.close();
                    showMiniToast(ctx,"📋 Saved to Downloads/xyper_summary_"+ts+".txt");
                } catch (Exception e) {
                    ClipboardManager cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("report",summary));
                    showMiniToast(ctx,"📋 Summary copied to clipboard!");
                }
                vibrate(ctx,30);
            }
        });
        row.addView(btnTxt);

        // ── Button 2: Export CSV ──
        LinearLayout btnCsv = new LinearLayout(ctx);
        btnCsv.setOrientation(LinearLayout.HORIZONTAL);
        btnCsv.setGravity(Gravity.CENTER);
        btnCsv.setPadding(dp(ctx,14),dp(ctx,12),dp(ctx,14),dp(ctx,12));
        GradientDrawable bg2 = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x33A78BFA,0x3322E5FF});
        bg2.setCornerRadius(dp(ctx,14)); bg2.setStroke(dp(ctx,1),0x77A78BFA);
        btnCsv.setBackground(bg2);
        btnCsv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView lbl2 = new TextView(ctx);
        lbl2.setText("📊  Export CSV");
        lbl2.setTextColor(Color.parseColor("#A78BFA"));
        lbl2.setTextSize(13f); lbl2.setTypeface(Typeface.DEFAULT_BOLD);
        btnCsv.addView(lbl2);
        btnCsv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                exportCsvKeys(ctx);
                vibrate(ctx,30);
            }
        });
        row.addView(btnCsv);

        return row;
    }

    // ── CSV Export ──────────────────────────────────────
    private static void exportCsvKeys(Context ctx) {
        // allKeysCache is the raw JSON from server
        // We grab it via a fresh fetch or use cached
        ApiClient.getAdminService().getAllKeys().enqueue(new retrofit2.Callback<String>() {
            @Override public void onResponse(retrofit2.Call<String> call, retrofit2.Response<String> response) {
                if (!response.isSuccessful()) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){
                        @Override public void run() { showMiniToast(ctx,"Failed to fetch keys for CSV"); }
                    });
                    return;
                }
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(response.body());
                    StringBuilder csv = new StringBuilder();
                    csv.append("Key Name,Role,Owner,Type,Status,Expires,Devices,Device Limit,Violations,Banned Until,Created At\n");
                    long now = System.currentTimeMillis();
                    for (int i=0; i<arr.length(); i++) {
                        try {
                            org.json.JSONObject obj = arr.getJSONObject(i);
                            String keyName = decodeKey(obj.optString("key",""));
                            String role    = obj.optString("role","—");
                            String ownerEnc = obj.optString("owner_key","");
                            String owner   = "—";
                            if (!ownerEnc.isEmpty()) {
                                try { byte[] d = android.util.Base64.decode(ownerEnc, android.util.Base64.DEFAULT);
                                    owner = new String(d, "UTF-8"); } catch (Exception ignored) {}
                            }
                            String type    = obj.optString("type","—");
                            long exp       = obj.optLong("expired",0);
                            long banned    = obj.optLong("banned_until",0);
                            int devCount   = obj.optJSONArray("devices") != null ? obj.optJSONArray("devices").length() : 0;
                            int devLimit   = obj.optInt("device_limit",1);
                            int viol       = obj.optInt("violation_count",0);
                            long createdAt = obj.optLong("created_at",0);
                            String status  = banned>now ? "Banned" : exp>0&&exp<now ? "Expired" : "Active";
                            String expiresStr = exp==0 ? "Permanent" : new java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date(exp));
                            String bannedStr  = banned>now ? new java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date(banned)) : "—";
                            String createdStr = createdAt>0 ? new java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date(createdAt)) : "—";
                            // Escape commas in key name
                            if (keyName.contains(",")) keyName = "\"" + keyName + "\"";
                            csv.append(keyName+","+role+","+owner+","+type+","+status+","+expiresStr+","+devCount+","+devLimit+","+viol+","+bannedStr+","+createdStr+"\n");
                        } catch(Exception ignored){}
                    }
                    final String csvContent = csv.toString();
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){
                        @Override public void run() {
                            try {
                                String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                                    java.util.Locale.getDefault()).format(new java.util.Date());
                                String fileName = "xyper_keys_"+ts+".csv";
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
                                        cv.clear();
                                        cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                                        ctx.getContentResolver().update(uri, cv, null, null);
                                        showMiniToast(ctx,"📊 CSV saved to Downloads/"+fileName);
                                    }
                                } else {
                                    java.io.File file = new java.io.File(
                                        android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS), fileName);
                                    java.io.FileWriter fw = new java.io.FileWriter(file);
                                    fw.write(csvContent); fw.close();
                                    showMiniToast(ctx,"📊 CSV saved to Downloads/"+fileName);
                                }
                            } catch (Exception e) {
                                showMiniToast(ctx,"Failed to save CSV: "+e.getMessage());
                            }
                        }
                    });
                } catch(Exception e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){
                        @Override public void run() { showMiniToast(ctx,"CSV parse error: "+e.getMessage()); }
                    });
                }
            }
            @Override public void onFailure(retrofit2.Call<String> call, Throwable t) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){
                    @Override public void run() { showMiniToast(ctx,"Failed: "+t.getMessage()); }
                });
            }
        });
    }

    // ===================== RECENT KEYS WITH SEARCH =====================
    private static View buildRecentKeysWithSearch(final Context ctx, final Typeface mi,
												  final JSONArray allKeys, final long now) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        // Search bar
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(-1,-2);
        srLp.bottomMargin = dp(ctx,8); searchRow.setLayoutParams(srLp);

        final EditText searchEt = new EditText(ctx);
        searchEt.setHint("Search licenses...");
        searchEt.setHintTextColor(0x55FFFFFF);
        searchEt.setTextColor(Color.WHITE); searchEt.setTextSize(12f);
        searchEt.setTypeface(Typeface.MONOSPACE); searchEt.setBackground(null);
        searchEt.setSingleLine(true);
        searchEt.setPadding(dp(ctx,12),dp(ctx,10),dp(ctx,12),dp(ctx,10));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(getSearchBg()); searchBg.setCornerRadius(dp(ctx,20));
        searchBg.setStroke(dp(ctx,1),getSearchStroke()); searchEt.setBackground(searchBg);
        searchEt.setTextColor(textPrimary()); searchEt.setHintTextColor(textMuted());
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0,-2,1f);
        etLp.rightMargin = dp(ctx,8); searchEt.setLayoutParams(etLp);
        searchRow.addView(searchEt);

        final TextView sortBtn = new TextView(ctx);
        sortBtn.setText(IC_SORT);
        if (mi!=null) sortBtn.setTypeface(mi);
        sortBtn.setTextSize(20f); sortBtn.setTextColor(Color.parseColor("#38BDF8"));
        sortBtn.setPadding(dp(ctx,8),dp(ctx,8),dp(ctx,8),dp(ctx,8));
        GradientDrawable sortBg = new GradientDrawable();
        sortBg.setColor(getSearchBg()); sortBg.setCornerRadius(dp(ctx,20));
        sortBg.setStroke(dp(ctx,1),getSearchStroke()); sortBtn.setBackground(sortBg);
        searchRow.addView(sortBtn);
        wrapper.addView(searchRow);

        // Tabs
        LinearLayout tabBar = new LinearLayout(ctx);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(-1,-2);
        tbLp.bottomMargin = dp(ctx,10); tabBar.setLayoutParams(tbLp);

        final String[] tabs = {"All","Active","Expired"};
        final TextView[] tabViews = new TextView[3];
        final LinearLayout keyList = new LinearLayout(ctx);
        keyList.setOrientation(LinearLayout.VERTICAL);
        final String[] currentFilter = {"All"};
        final String[] currentSearch = {""};
        final boolean[] sortAsc = {false};

        for (int i=0; i<tabs.length; i++) {
            final int idx = i;
            TextView tab = new TextView(ctx);
            tab.setText(tabs[i]); tab.setTextSize(11f); tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(ctx,16),dp(ctx,8),dp(ctx,16),dp(ctx,8));
            tab.setTypeface(i==0?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
            tabViews[i] = tab;
            GradientDrawable tabBg = new GradientDrawable();
            tabBg.setCornerRadius(dp(ctx,20));
            if (i==0) { tabBg.setColor(isDark?0x3322E5FF:0xFFE0F2FE); tabBg.setStroke(dp(ctx,1),isDark?0x8822E5FF:0xFF0EA5E9);
                tab.setTextColor(isDark?Color.parseColor("#22E5FF"):Color.parseColor("#0284C7")); }
            else { tabBg.setColor(isDark?0x11FFFFFF:0xFFF1F5F9); tabBg.setStroke(dp(ctx,1),isDark?0x33FFFFFF:0xFFCBD5E1);
                tab.setTextColor(isDark?0x77FFFFFF:0xFF64748B); }
            tab.setBackground(tabBg);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-2,-2);
            tLp.rightMargin = dp(ctx,8); tab.setLayoutParams(tLp);
            tab.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						currentFilter[0] = tabs[idx];
						for (int j=0; j<tabViews.length; j++) {
							GradientDrawable bg = new GradientDrawable();
							bg.setCornerRadius(dp(ctx,20));
							if (j==idx) { bg.setColor(isDark?0x3322E5FF:0xFFE0F2FE); bg.setStroke(dp(ctx,1),isDark?0x8822E5FF:0xFF0EA5E9);
								tabViews[j].setTextColor(isDark?Color.parseColor("#22E5FF"):Color.parseColor("#0284C7"));
								tabViews[j].setTypeface(Typeface.DEFAULT_BOLD); }
							else { bg.setColor(isDark?0x11FFFFFF:0xFFF1F5F9); bg.setStroke(dp(ctx,1),isDark?0x33FFFFFF:0xFFCBD5E1);
								tabViews[j].setTextColor(isDark?0x77FFFFFF:0xFF64748B);
								tabViews[j].setTypeface(Typeface.DEFAULT); }
							tabViews[j].setBackground(bg);
						}
						buildFilteredKeyList(ctx,mi,keyList,allKeys,now,
											 currentFilter[0],currentSearch[0],sortAsc[0]);
					}
				});
            tabBar.addView(tab);
        }
        sortBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					sortAsc[0] = !sortAsc[0]; vibrate(ctx,15);
					buildFilteredKeyList(ctx,mi,keyList,allKeys,now,
										 currentFilter[0],currentSearch[0],sortAsc[0]);
				}
			});
        searchEt.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
				@Override public void onTextChanged(CharSequence s,int st,int b,int c) {
					currentSearch[0] = s.toString().toLowerCase();
					buildFilteredKeyList(ctx,mi,keyList,allKeys,now,
										 currentFilter[0],currentSearch[0],sortAsc[0]);
				}
				@Override public void afterTextChanged(Editable s) {}
			});
        wrapper.addView(tabBar);
        wrapper.addView(keyList);
        buildFilteredKeyList(ctx,mi,keyList,allKeys,now,"All","",false);
        return wrapper;
    }

    private static void buildFilteredKeyList(Context ctx, Typeface mi,
											 LinearLayout container, JSONArray keys, long now,
											 String filter, String search, final boolean sortAsc) {
        container.removeAllViews();
        List<JSONObject> list = new ArrayList<JSONObject>();
        for (int i=0; i<keys.length(); i++) {
            try {
                JSONObject obj = keys.getJSONObject(i);
                String type = obj.optString("type","-");
                long exp = obj.optLong("expired",0);
                boolean isExpired = exp<now && !type.equals("Permanent");
                if (filter.equals("Active") && isExpired) continue;
                if (filter.equals("Expired") && !isExpired) continue;
                String decoded = decodeKey(obj.optString("key","")).toLowerCase();
                if (!search.isEmpty() && !decoded.contains(search)) continue;
                list.add(obj);
            } catch (Exception ignored) {}
        }
        Collections.sort(list, new Comparator<JSONObject>() {
				@Override public int compare(JSONObject a, JSONObject b) {
					long ca=a.optLong("created_at",0), cb=b.optLong("created_at",0);
					return sortAsc ? Long.compare(ca,cb) : Long.compare(cb,ca);
				}
			});
        if (list.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("No licenses found");
            empty.setTextColor(0x55FFFFFF); empty.setTextSize(12f);
            empty.setTypeface(Typeface.MONOSPACE); empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,dp(ctx,20),0,dp(ctx,20));
            container.addView(empty); return;
        }
        for (JSONObject obj : list)
            container.addView(buildRecentRow(ctx,mi,obj,now));
    }

    // ===================== RECENT ROW =====================
    private static View buildRecentRow(final Context ctx, final Typeface mi,
									   final JSONObject obj, final long now) {
        LinearLayout card = buildGlassCard(ctx,0x2AFFFFFF,0x44FFFFFF,dp(ctx,14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,8); card.setLayoutParams(lp);
        try {
            final String decoded = decodeKey(obj.optString("key","-"));
            final String role    = obj.optString("role","User");
            final String type    = obj.optString("type","-");
            final long exp       = obj.optLong("expired",0);
            final long createdAt = obj.optLong("created_at",0);
            boolean isExpired    = exp<now && !type.equals("Permanent");
            String roleColor     = role.equals("Developer")?"#22E5FF":
				role.equals("Reseller") ?"#A78BFA":"#34D399";
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView keyTv = new TextView(ctx);
            keyTv.setText("🔑  "+decoded);
            keyTv.setTextColor(isDark ? 0xFFFFFFFF : 0xFF0F172A);
            keyTv.setTextSize(12f); keyTv.setTypeface(Typeface.MONOSPACE);
            keyTv.setMaxLines(1);
            keyTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            keyTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView roleBadge = makeBadge(ctx, role, roleColor);
            LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(-2,-2);
            rbLp.rightMargin = dp(ctx,6); roleBadge.setLayoutParams(rbLp);
            TextView statusBadge = makeBadge(ctx,isExpired?"EXPIRED":"ACTIVE",
											 isExpired?"#F87171":"#34D399");
            row.addView(keyTv); row.addView(roleBadge); row.addView(statusBadge);
            card.addView(row);
            card.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						showKeyDetail(ctx,mi,obj,decoded,role,type,exp,createdAt,now);
					}
				});
            card.setOnLongClickListener(new View.OnLongClickListener() {
					@Override public boolean onLongClick(View v) {
						ClipboardManager cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
						if (cm!=null) {
							cm.setPrimaryClip(ClipData.newPlainText("key",decoded));
							showMiniToast(ctx,"📋 License key copied to clipboard");
							vibrate(ctx,40);
						}
						return true;
					}
				});
        } catch (Exception ignored) {}
        return card;
    }

    // ===================== KEY DETAIL BOTTOM SHEET =====================
    private static void showKeyDetail(final Context ctx, final Typeface mi,
									  final JSONObject obj, String decoded, String role, String type,
									  long exp, long createdAt, long now) {
        final FrameLayout overlay = new FrameLayout(ctx);
        overlay.setBackgroundColor(0xAA000000);
        overlay.setClickable(true);
        android.app.Activity act = (android.app.Activity) ctx;
        final FrameLayout rootView = act.findViewById(android.R.id.content);
        rootView.addView(overlay, new FrameLayout.LayoutParams(-1,-1));

        final LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,30));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.parseColor("#0D1B2A"));
        sheetBg.setCornerRadii(new float[]{dp(ctx,20),dp(ctx,20),dp(ctx,20),dp(ctx,20),0,0,0,0});
        sheetBg.setStroke(dp(ctx,1),0x5522E5FF);
        sheet.setBackground(sheetBg);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(-1,-2);
        sheetLp.gravity = Gravity.BOTTOM;
        overlay.addView(sheet, sheetLp);

        // Drag handle
        View handle = new View(ctx);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setColor(0x55FFFFFF); hBg.setCornerRadius(dp(ctx,3));
        handle.setBackground(hBg);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(dp(ctx,40),dp(ctx,4));
        hLp.gravity = Gravity.CENTER_HORIZONTAL; hLp.bottomMargin = dp(ctx,16);
        handle.setLayoutParams(hLp);
        sheet.addView(handle);

        // Header
        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(-1,-2);
        hrLp.bottomMargin = dp(ctx,16); headerRow.setLayoutParams(hrLp);
        TextView titleTv = new TextView(ctx);
        titleTv.setText("License Details"); titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setTextSize(16f); titleTv.setTextColor(Color.WHITE);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        headerRow.addView(titleTv);
        final TextView closeBtn = new TextView(ctx);
        if (mi!=null) { closeBtn.setText(IC_CLOSE); closeBtn.setTypeface(mi); }
        else closeBtn.setText("✕");
        closeBtn.setTextSize(20f); closeBtn.setTextColor(0x99FFFFFF);
        closeBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					sheet.animate().translationY(sheet.getHeight()).setDuration(250).start();
					overlay.postDelayed(new Runnable() {
							@Override public void run() { rootView.removeView(overlay); }
						}, 260);
				}
			});
        headerRow.addView(closeBtn);
        sheet.addView(headerRow);

        addDetailRow(ctx,sheet,"🔑  License Key",decoded,Color.parseColor("#22E5FF"));

        // Role + Type badges
        LinearLayout badgeRow = new LinearLayout(ctx);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1,-2);
        brLp.bottomMargin = dp(ctx,10); badgeRow.setLayoutParams(brLp);
        String roleColor = role.equals("Developer")?"#22E5FF":role.equals("Reseller")?"#A78BFA":"#34D399";
        TextView roleBadge = makeBadge(ctx,role,roleColor);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(-2,-2);
        rbLp.rightMargin = dp(ctx,8); roleBadge.setLayoutParams(rbLp);
        badgeRow.addView(roleBadge);
        badgeRow.addView(makeBadge(ctx,type,"#FBBF24"));
        sheet.addView(badgeRow);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        addDetailRow(ctx,sheet,"📅  Created",
					 createdAt>0?sdf.format(new Date(createdAt)):"—",Color.WHITE);

        if (!type.equals("Permanent")) {
            boolean isExp = exp<now;
            String expStr;
            if (isExp) {
                long ago = (now-exp)/(3600L*1000L);
                expStr = sdf.format(new Date(exp))+"  (expired "+ago+"h ago)";
            } else {
                long left = (exp-now)/(3600L*1000L);
                expStr = sdf.format(new Date(exp))+"  ("+left+"h remaining)";
            }
            addDetailRow(ctx,sheet,"⏰  Expiry",expStr,
						 isExp?Color.parseColor("#F87171"):Color.parseColor("#34D399"));
        } else {
            addDetailRow(ctx,sheet,"⏰  Expiry","No Expiry — Permanent",Color.parseColor("#FBBF24"));
        }

        try {
            JSONArray devs = obj.optJSONArray("devices");
            if (devs!=null && devs.length()>0) {
                addDetailRow(ctx,sheet,"📱  Devices",devs.length()+" registered",
							 Color.parseColor("#A78BFA"));
                int maxD = Math.min(3,devs.length());
                for (int i=0; i<maxD; i++) {
                    JSONObject dev = devs.getJSONObject(i);
                    String name = dev.optString("device_name","Unknown");
                    String model = dev.optString("model","");
                    String ip = dev.optString("ip","");
                    boolean devBanned = dev.optLong("banned_until",0)>now;
                    addDetailRow(ctx,sheet,
								 "   "+(i+1)+".  "+name+(model.isEmpty()?"":" · "+model),
								 (ip.isEmpty()?"":ip)+(devBanned?"  ⛔ Banned":""),
								 devBanned?Color.parseColor("#F87171"):0xAAFFFFFF);
                }
                if (devs.length()>3) {
                    TextView more = new TextView(ctx);
                    more.setText("   + "+(devs.length()-3)+" more devices");
                    more.setTextColor(0x66FFFFFF); more.setTextSize(11f);
                    more.setTypeface(Typeface.MONOSPACE);
                    sheet.addView(more);
                }
            }
        } catch (Exception ignored) {}

        int vCount = obj.optInt("violation_count",0);
        if (vCount>0)
            addDetailRow(ctx,sheet,"⚠  Violations",vCount+" recorded",
						 Color.parseColor("#F87171"));

        sheet.setTranslationY(1000f);
        sheet.post(new Runnable() {
				@Override public void run() {
					sheet.animate().translationY(0).setDuration(300)
						.setInterpolator(new DecelerateInterpolator()).start();
				}
			});
        overlay.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (v==overlay) {
						sheet.animate().translationY(sheet.getHeight()).setDuration(250).start();
						overlay.postDelayed(new Runnable() {
								@Override public void run() { rootView.removeView(overlay); }
							}, 260);
					}
				}
			});
    }

    private static void addDetailRow(Context ctx, LinearLayout parent,
									 String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,10); row.setLayoutParams(lp);
        TextView labelTv = new TextView(ctx);
        labelTv.setText(label); labelTv.setTextColor(isDark ? 0x77FFFFFF : 0xFF64748B);
        labelTv.setTextSize(10f); labelTv.setTypeface(Typeface.MONOSPACE);
        row.addView(labelTv);
        TextView valueTv = new TextView(ctx);
        valueTv.setText(value); valueTv.setTextColor(valueColor);
        valueTv.setTextSize(13f); valueTv.setTypeface(Typeface.MONOSPACE);
        row.addView(valueTv);
        parent.addView(row);
    }

    // ===================== STAT CARD =====================
    private static View buildStatCard(final Context ctx, final Typeface mi,
									  final String iconCode, final String label, final int value,
									  final String color, int fixedHeight, int animDelay,
									  int prevValue, final String subtext, boolean isPulsing) {

        final int pc = Color.parseColor(color);
        final GlowCardView outer = new GlowCardView(ctx, pc, dp(ctx,20));
        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(0, fixedHeight, 1f);
        outerLp.setMargins(dp(ctx,10),dp(ctx,10),dp(ctx,10),dp(ctx,10));
        outer.setLayoutParams(outerLp);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setClipChildren(false); card.setClipToPadding(false);
        if (android.os.Build.VERSION.SDK_INT < 31) card.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        card.setPadding(dp(ctx,8),dp(ctx,12),dp(ctx,8),dp(ctx,12));

        View highlight = new View(ctx);
        GradientDrawable hlBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x00FFFFFF,0x44FFFFFF,0x00FFFFFF});
        hlBg.setCornerRadius(dp(ctx,20));
        highlight.setBackground(hlBg);
        FrameLayout.LayoutParams hlLp = new FrameLayout.LayoutParams(-1, dp(ctx,2));
        hlLp.gravity = Gravity.TOP;
        hlLp.topMargin = dp(ctx,8); hlLp.leftMargin = dp(ctx,12); hlLp.rightMargin = dp(ctx,12);

        TextView iconTv = new TextView(ctx);
        iconTv.setText(iconCode);
        if (mi!=null) iconTv.setTypeface(mi);
        iconTv.setTextSize(30f); iconTv.setTextColor(isDark ? applyAlpha(pc,0.9f) : pc);
        iconTv.setShadowLayer(isDark?20f:0f,0f,0f,pc); iconTv.setGravity(Gravity.CENTER);
        iconTv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        final TextView valueTv = new GradientTextView(ctx, pc);
        valueTv.setText("0"); valueTv.setTypeface(Typeface.DEFAULT_BOLD);
        valueTv.setTextSize(40f); valueTv.setTextColor(pc);
        valueTv.setShadowLayer(isDark?30f:0f,0f,0f,pc); valueTv.setGravity(Gravity.CENTER);

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label); labelTv.setTypeface(Typeface.MONOSPACE);
        labelTv.setTextSize(11f); labelTv.setTextColor(labelColor());
        labelTv.setShadowLayer(0f,0f,0f,0);
        labelTv.setGravity(Gravity.CENTER);

        TextView subTv = new TextView(ctx);
        subTv.setText(subtext); subTv.setTypeface(Typeface.MONOSPACE);
        subTv.setTextSize(9f); subTv.setTextColor(subtextColor(pc));
        subTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2,-2);
        subLp.topMargin = dp(ctx,2); subTv.setLayoutParams(subLp);

        card.addView(iconTv); card.addView(valueTv); card.addView(labelTv); card.addView(subTv);
        outer.addView(card, new FrameLayout.LayoutParams(-1,-1));
        outer.addView(highlight, hlLp);

        if (prevValue>=0 && mi!=null) {
            final TextView trendOv = new TextView(ctx);
            trendOv.setTypeface(mi);
            if (android.os.Build.VERSION.SDK_INT < 31) trendOv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            if (value>prevValue) {
                trendOv.setText(IC_TRENDING_UP);
                trendOv.setTextColor(Color.parseColor("#34D399"));
                trendOv.setShadowLayer(8f,0f,0f,Color.parseColor("#34D399"));
            } else if (value<prevValue) {
                trendOv.setText(IC_TRENDING_DOWN);
                trendOv.setTextColor(Color.parseColor("#F87171"));
                trendOv.setShadowLayer(8f,0f,0f,Color.parseColor("#F87171"));
            } else {
                trendOv.setText(IC_TRENDING_FLAT);
                trendOv.setTextColor(0x66FFFFFF);
            }
            trendOv.setTextSize(14f);
            trendOv.setPadding(0,dp(ctx,8),dp(ctx,10),0);
            FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(-2,-2);
            tLp.gravity = Gravity.TOP|Gravity.END;
            outer.addView(trendOv, tLp);
        }

        final ValueAnimator breathe = ValueAnimator.ofFloat(0.35f,1.0f);
        breathe.setDuration(1600+(animDelay*8));
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setInterpolator(new AccelerateDecelerateInterpolator());
        breathe.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override public void onAnimationUpdate(ValueAnimator anim) {
					outer.setStrokeAlpha((float)anim.getAnimatedValue());
				}
			});
        breathe.start();

        final ValueAnimator[] pulseHolder = {null};
        if (isPulsing && value>0) {
            ValueAnimator pulse = ValueAnimator.ofFloat(1f,1.04f);
            pulse.setDuration(700); pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator());
            pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
					@Override public void onAnimationUpdate(ValueAnimator anim) {
						float s=(float)anim.getAnimatedValue();
						outer.setScaleX(s); outer.setScaleY(s);
					}
				});
            pulseHolder[0] = pulse;
            pulse.start();
        }

        outer.setAlpha(0f); outer.setTranslationY(dp(ctx,30));
        outer.postDelayed(new Runnable() {
				@Override public void run() {
					outer.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
					outer.animate().alpha(1f).translationY(0f).setDuration(450)
						.setInterpolator(new OvershootInterpolator(0.8f))
						.withEndAction(new Runnable() {
							@Override public void run() {
								outer.setLayerType(android.view.View.LAYER_TYPE_NONE, null);
							}
						}).start();
					ValueAnimator cu = ValueAnimator.ofInt(0, value);
					cu.setDuration(900); cu.setInterpolator(new DecelerateInterpolator());
					cu.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
							@Override public void onAnimationUpdate(ValueAnimator a) {
								valueTv.setText(String.valueOf(a.getAnimatedValue()));
							}
						});
					cu.start();
				}
			}, animDelay);


        return outer;
    }

    // ===================== SECURITY SCORE =====================
    private static View buildSecurityScore(final Context ctx, final int score) {
        String sc = score>=80?"#34D399":score>=50?"#FBBF24":"#F87171";
        String sl = score>=80?"Excellent":score>=50?"Good":"At Risk";
        final int scp = Color.parseColor(sc);
        LinearLayout card = buildGlassCard(ctx,applyAlpha(scp,0.15f),applyAlpha(scp,0.5f),dp(ctx,16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,12); card.setLayoutParams(lp);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView scoreTv = new TextView(ctx);
        scoreTv.setText(String.valueOf(score));
        scoreTv.setTypeface(Typeface.DEFAULT_BOLD); scoreTv.setTextSize(38f);
        scoreTv.setTextColor(scp); scoreTv.setShadowLayer(16f, 0f, 0f, scp);
        scoreTv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2,-2);
        slp.rightMargin = dp(ctx,16); scoreTv.setLayoutParams(slp);
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView labelTv = new TextView(ctx);
        labelTv.setText(sl); labelTv.setTextColor(scp); labelTv.setTextSize(15f);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD); labelTv.setShadowLayer(8f, 0f, 0f, scp);
        labelTv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        TextView descTv = new TextView(ctx);
        descTv.setText("Based on violations & banned licenses");
        descTv.setTextColor(textMuted()); descTv.setTextSize(10f);
        descTv.setTypeface(Typeface.MONOSPACE);
        final FrameLayout barFrame = new FrameLayout(ctx);
        LinearLayout.LayoutParams bfLp = new LinearLayout.LayoutParams(-1,dp(ctx,7));
        bfLp.topMargin = dp(ctx,8); barFrame.setLayoutParams(bfLp);
        View track = new View(ctx);
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(0x33FFFFFF); tb.setCornerRadius(dp(ctx,4));
        track.setBackground(tb);
        barFrame.addView(track, new FrameLayout.LayoutParams(-1,-1));
        final View fill = new View(ctx);
        GradientDrawable fb = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{applyAlpha(scp,0.5f),scp});
        fb.setCornerRadius(dp(ctx,4)); fill.setBackground(fb);
        fill.setLayoutParams(new FrameLayout.LayoutParams(0,-1));
        barFrame.addView(fill);
        final float pct = score/100f;
        barFrame.post(new Runnable() {
				@Override public void run() {
					ValueAnimator a = ValueAnimator.ofInt(0,(int)(barFrame.getWidth()*pct));
					a.setDuration(1000); a.setInterpolator(new DecelerateInterpolator());
					a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
							@Override public void onAnimationUpdate(ValueAnimator anim) {
								fill.getLayoutParams().width = (int)anim.getAnimatedValue();
								fill.requestLayout();
							}
						});
					a.start();
				}
			});
        info.addView(labelTv); info.addView(descTv); info.addView(barFrame);
        row.addView(scoreTv); row.addView(info); card.addView(row);
        return card;
    }

    // ===================== ROLE BAR =====================
    private static View buildRoleBar(Context ctx, String role, int count,
									 int total, String color, int barDelay) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1,-2);
        rLp.bottomMargin = dp(ctx,14); row.setLayoutParams(rLp);
        LinearLayout labelRow = new LinearLayout(ctx);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView roleTv = new TextView(ctx);
        roleTv.setText(role); roleTv.setTextColor(Color.parseColor(color));
        roleTv.setTextSize(13f); roleTv.setTypeface(Typeface.DEFAULT_BOLD);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            roleTv.setShadowLayer(8f,0f,0f,Color.parseColor(color));
            roleTv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        roleTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        int pct = total>0?Math.round((float)count/total*100):0;
        TextView cTv = new TextView(ctx);
        cTv.setText(count+" licenses  "+pct+"%");
        cTv.setTextColor(isDark ? 0x99FFFFFF : 0xFF475569); cTv.setTextSize(11f);
        cTv.setTypeface(Typeface.MONOSPACE);
        labelRow.addView(roleTv); labelRow.addView(cTv);
        row.addView(labelRow);
        final FrameLayout barFrame = new FrameLayout(ctx);
        LinearLayout.LayoutParams bfLp = new LinearLayout.LayoutParams(-1,dp(ctx,8));
        bfLp.topMargin = dp(ctx,6); barFrame.setLayoutParams(bfLp);
        View track = new View(ctx);
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(0x33FFFFFF); tb.setCornerRadius(dp(ctx,4));
        track.setBackground(tb);
        barFrame.addView(track, new FrameLayout.LayoutParams(-1,-1));
        final float fp = total>0?(float)count/total:0f;
        final View fill = new View(ctx);
        final int pc = Color.parseColor(color);
        GradientDrawable fb = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{applyAlpha(pc,0.5f),pc});
        fb.setCornerRadius(dp(ctx,4)); fill.setBackground(fb);
        fill.setLayoutParams(new FrameLayout.LayoutParams(0,-1));
        barFrame.addView(fill);
        final int delay = barDelay;
        barFrame.post(new Runnable() {
				@Override public void run() {
					barFrame.postDelayed(new Runnable() {
							@Override public void run() {
								ValueAnimator a = ValueAnimator.ofInt(0,(int)(barFrame.getWidth()*fp));
								a.setDuration(900); a.setInterpolator(new DecelerateInterpolator(1.5f));
								a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
										@Override public void onAnimationUpdate(ValueAnimator anim) {
											fill.getLayoutParams().width = (int)anim.getAnimatedValue();
											fill.requestLayout();
										}
									});
								a.start();
							}
						}, delay);
				}
			});
        row.addView(barFrame);
        return row;
    }

    // ===================== BANNER =====================
    private static View buildBanner(Context ctx, Typeface mi, String iconCode,
									String msg, String color) {
        LinearLayout banner = new LinearLayout(ctx);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(ctx,14),dp(ctx,12),dp(ctx,14),dp(ctx,12));
        int c = Color.parseColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(applyAlpha(c,0.15f)); bg.setCornerRadius(dp(ctx,12));
        bg.setStroke(dp(ctx,1),applyAlpha(c,0.7f));
        banner.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,10); banner.setLayoutParams(lp);
        final TextView iconTv = new TextView(ctx);
        iconTv.setText(iconCode);
        if (mi!=null) iconTv.setTypeface(mi);
        iconTv.setTextSize(18f); iconTv.setTextColor(Color.parseColor(color));
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(-2,-2);
        iLp.rightMargin = dp(ctx,10); iconTv.setLayoutParams(iLp);
        ValueAnimator bounce = ValueAnimator.ofFloat(0f,(float)-dp(ctx,4),0f);
        bounce.setDuration(700); bounce.setRepeatCount(ValueAnimator.INFINITE);
        bounce.setRepeatMode(ValueAnimator.RESTART);
        bounce.setInterpolator(new AccelerateDecelerateInterpolator());
        bounce.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override public void onAnimationUpdate(ValueAnimator anim) {
					iconTv.setTranslationY((float)anim.getAnimatedValue());
				}
			});
        bounce.start();
        TextView tv = new TextView(ctx);
        tv.setText(msg); tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(12f); tv.setTypeface(Typeface.MONOSPACE);
        banner.addView(iconTv); banner.addView(tv);
        return banner;
    }

    // ===================== WARNING ROW =====================
    private static View buildWarningRow(Context ctx, JSONObject obj, long now) {
        LinearLayout card = buildGlassCard(ctx,0x25F59E0B,0x88F59E0B,dp(ctx,14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(ctx,8); card.setLayoutParams(lp);
        try {
            String decoded = decodeKey(obj.optString("key","-"));
            long hours = (obj.optLong("expired",0)-now)/(3600L*1000L);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView keyTv = new TextView(ctx);
            keyTv.setText("🔑  "+decoded); keyTv.setTextColor(isDark?Color.parseColor("#FBBF24"):Color.parseColor("#B45309"));
            keyTv.setTextSize(12f); keyTv.setTypeface(Typeface.MONOSPACE);
            keyTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView timeTv = new TextView(ctx);
            timeTv.setText(hours+"h remaining");
            timeTv.setTextColor(Color.parseColor("#FDE68A"));
            timeTv.setTextSize(11f); timeTv.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(keyTv); row.addView(timeTv); card.addView(row);
        } catch (Exception ignored) {}
        return card;
    }

    // ===================== TREND DATA =====================
    private static void saveTrend(SharedPreferences prefs, int total) {
        String raw = prefs.getString(KEY_TREND,"");
        String[] parts = raw.isEmpty()?new String[0]:raw.split(",");
        List<String> list = new ArrayList<String>();
        for (String p:parts) if (!p.isEmpty()) list.add(p);
        list.add(String.valueOf(total));
        if (list.size()>14) list = list.subList(list.size()-14,list.size());
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<list.size();i++) { if(i>0)sb.append(","); sb.append(list.get(i)); }
        prefs.edit().putString(KEY_TREND,sb.toString()).apply();
    }

    private static int[] loadTrend(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_TREND,"");
        if (raw.isEmpty()) return null;
        String[] parts = raw.split(",");
        int[] data = new int[parts.length];
        for (int i=0;i<parts.length;i++) {
            try { data[i]=Integer.parseInt(parts[i].trim()); }
            catch (Exception e) { data[i]=0; }
        }
        return data;
    }

    // ===================== HELPERS =====================
    private static LinearLayout makeRow(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        row.setClipChildren(false); row.setClipToPadding(false);
        return row;
    }

    private static LinearLayout buildGlassCard(Context ctx,int fill,int stroke,int radius) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx,16),dp(ctx,14),dp(ctx,16),dp(ctx,14));
        GradientDrawable bg = new GradientDrawable();
        if (isDark) {
            bg.setColor(fill);
            bg.setStroke(dp(ctx,1), stroke);
        } else {
            bg.setColor(0xFFFFFFFF);
            bg.setStroke(dp(ctx,1), 0xFFE2E8F0);
        }
        bg.setCornerRadius(radius); card.setBackground(bg);
        return card;
    }

    private static void addSectionTitle(Context ctx, Typeface mi,
										LinearLayout container, String iconCode, String title) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(ctx,16); lp.bottomMargin = dp(ctx,8);
        row.setLayoutParams(lp);
        if (mi!=null) {
            TextView iconTv = new TextView(ctx);
            iconTv.setText(iconCode); iconTv.setTypeface(mi);
            iconTv.setTextSize(16f); iconTv.setTextColor(Color.parseColor("#38BDF8"));
            iconTv.setShadowLayer(6f,0f,0f,Color.parseColor("#0EA5E9"));
            if (android.os.Build.VERSION.SDK_INT < 31) iconTv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(-2,-2);
            iLp.rightMargin = dp(ctx,6); iconTv.setLayoutParams(iLp);
            row.addView(iconTv);
        }
        TextView tv = new TextView(ctx);
        tv.setText(title); tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(13f); tv.setTextColor(Color.parseColor("#38BDF8"));
        tv.setShadowLayer(8f,0f,0f,Color.parseColor("#0EA5E9"));
        if (android.os.Build.VERSION.SDK_INT < 31) tv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        row.addView(tv); container.addView(row);
    }

    private static TextView makeBadge(Context ctx, String text, String color) {
        TextView tv = new TextView(ctx);
        tv.setText(" "+text+" "); tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(10f); tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setShadowLayer(6f,0f,0f,Color.parseColor(color));
        if (android.os.Build.VERSION.SDK_INT < 31) tv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(applyAlpha(Color.parseColor(color),0.15f));
        bg.setCornerRadius(dp(ctx,6));
        bg.setStroke(1,applyAlpha(Color.parseColor(color),0.5f));
        tv.setBackground(bg);
        return tv;
    }

    private static Typeface loadMI(Context ctx) {
        try { return Typeface.createFromAsset(ctx.getAssets(),"MaterialIcons-Regular.ttf"); }
        catch (Exception e) { return null; }
    }

    private static String decodeKey(String encoded) {
        try {
            byte[] b = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
            return new String(b,"UTF-8");
        } catch (Exception e) { return encoded; }
    }

    private static void vibrate(Context ctx, int ms) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                // Android 12+ — VibratorManager (deprecated Vibrator replaced)
                android.os.VibratorManager vm = (android.os.VibratorManager)
                    ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    android.os.Vibrator v = vm.getDefaultVibrator();
                    if (v.hasVibrator())
                        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else if (android.os.Build.VERSION.SDK_INT >= 26) {
                // Android 8-11 — VibrationEffect
                Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator())
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // Android < 8 — legacy
                Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) v.vibrate(ms);
            }
        } catch(Exception ignored) {}
    }

    private static void showMiniToast(Context ctx, String msg) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setPadding(dp(ctx,18),dp(ctx,12),dp(ctx,18),dp(ctx,12));
        layout.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE050816); bg.setCornerRadius(dp(ctx,14));
        bg.setStroke(dp(ctx,1),0xBB22E5FF); layout.setBackground(bg);
        TextView tv = new TextView(ctx);
        tv.setText(msg); tv.setTextSize(12f);
        tv.setTextColor(Color.parseColor("#22E5FF"));
        layout.addView(tv);
        Toast toast = new Toast(ctx);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(ctx,100));
        toast.show();
    }

    private static int applyAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha*255) << 24);
    }

    private static int dp(Context ctx, int val) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val,
											  ctx.getResources().getDisplayMetrics());
    }

    // ===================== CUSTOM VIEWS =====================
    static class AuroraView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float offset = 0f;
        private final Handler h = new Handler(Looper.getMainLooper());
        public AuroraView(Context ctx) { super(ctx); startLoop(); }
        private void startLoop() {
            h.postDelayed(new Runnable() {
					@Override public void run() { offset=(offset+0.006f)%1f; invalidate(); startLoop(); }
				}, 30);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (getWidth()==0) return;
            float w=getWidth(), shift=offset*w;
            LinearGradient lg = new LinearGradient(shift-w,0,shift,0,
												   new int[]{0x0022E5FF,0xFF22E5FF,0xFFA78BFA,0xFF34D399,0xFF22E5FF,0x0022E5FF},
												   null, Shader.TileMode.MIRROR);
            paint.setShader(lg);
            paint.setMaskFilter(new android.graphics.BlurMaskFilter(5f,android.graphics.BlurMaskFilter.Blur.NORMAL));
            canvas.drawRect(0,0,w,getHeight(),paint);
        }
    }

    static class VignetteView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public VignetteView(Context ctx) { super(ctx); }
        @Override protected void onDraw(Canvas canvas) {
            float w=getWidth(),h=getHeight();
            paint.setShader(new android.graphics.RadialGradient(w/2f,h/2f,Math.max(w,h)*0.75f,
																new int[]{0x00000000,0x99000000},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);
        }
    }

    static class ScanlineView extends View {
        private final Paint paint = new Paint();
        public ScanlineView(Context ctx) { super(ctx); paint.setColor(0x07000000); }
        @Override protected void onDraw(Canvas canvas) {
            for (int y=0; y<getHeight(); y+=3)
                canvas.drawLine(0,y,getWidth(),y,paint);
        }
    }

    static class ShimmerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float pos = -0.5f;
        private final Handler h = new Handler(Looper.getMainLooper());
        public ShimmerView(Context ctx) {
            super(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(isDark ? 0x22FFFFFF : 0xFFDDE3EA); bg.setCornerRadius(20f);
            setBackground(bg); startLoop();
        }
        private void startLoop() {
            h.postDelayed(new Runnable() {
					@Override public void run() {
						pos+=0.018f; if(pos>1.5f)pos=-0.5f; invalidate(); startLoop();
					}
				}, 24);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (getWidth()==0) return;
            float cx=pos*getWidth();
            paint.setShader(new LinearGradient(cx-200,0,cx+200,0,
											   new int[]{0x00FFFFFF,0x33FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(0,0,getWidth(),getHeight()),20f,20f,paint);
        }
    }

    static class GlitchTextView extends TextView {
        private final Handler h = new Handler(Looper.getMainLooper());
        private boolean glitching = false;
        private static final String CHARS = "!@#$%^&*<>?/|\\";
        private static final Random RNG = new Random();
        public GlitchTextView(Context ctx) {
            super(ctx);
            postDelayed(new Runnable() { @Override public void run() { doGlitch(); } }, 1000);
        }
        private void doGlitch() {
            if (glitching) return;
            glitching = true;
            final String orig = getText().toString();
            final int[] count = {0};
            h.post(new Runnable() {
					@Override public void run() {
						if (count[0]<7) {
							char[] c = orig.toCharArray();
							for (int i=0;i<2;i++)
								c[RNG.nextInt(c.length)]=CHARS.charAt(RNG.nextInt(CHARS.length()));
							setText(new String(c));
							setTextColor(count[0]%2==0?Color.parseColor("#FF22E5FF"):Color.WHITE);
							count[0]++;
							h.postDelayed(this,55);
						} else { setText(orig); setTextColor(Color.WHITE); glitching=false; }
					}
				});
        }
    }

    static class GradientTextView extends TextView {
        private final int color;
        public GradientTextView(Context ctx, int color) {
            super(ctx); this.color=color;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (getWidth()>0) {
                // Dark: putih->warna; Light: warna lebih gelap->warna agar kontras di bg terang
                int topColor = isDark ? Color.WHITE : darken(color, 0.7f);
                getPaint().setShader(new LinearGradient(0,0,0,getHeight(),
														new int[]{topColor, color}, null, Shader.TileMode.CLAMP));
            }
            super.onDraw(canvas);
        }
        private int darken(int c, float factor) {
            int r = Math.round(((c>>16)&0xFF) * factor);
            int g = Math.round(((c>>8)&0xFF)  * factor);
            int b = Math.round((c&0xFF)        * factor);
            return 0xFF000000 | (r<<16) | (g<<8) | b;
        }
    }

    static class GlowCardView extends FrameLayout {
        private final Paint bgPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int cardColor;
        private float strokeAlpha = 0.75f;
        private final float radius;

        public GlowCardView(Context ctx, int color, float cornerRadius) {
            super(ctx); this.cardColor=color; this.radius=cornerRadius;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClipChildren(false); setClipToPadding(false); setWillNotDraw(false);
        }
        public void setStrokeAlpha(float alpha) { this.strokeAlpha=alpha; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float w=getWidth(), h=getHeight();
            if (w==0||h==0) return;
            RectF r = new RectF(0,0,w,h);
            bgPaint.setMaskFilter(null); bgPaint.setStyle(Paint.Style.FILL);

            if (isDark) {
                // Dark: neon gradient fill + glow border
                int[] bgColors = new int[]{applyAlpha(cardColor,0.32f), applyAlpha(cardColor,0.08f)};
                bgPaint.setShader(new android.graphics.LinearGradient(
                    0,0,w,h, bgColors, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, radius, radius, bgPaint);
                strokePaint.setShader(null); strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(3.5f); strokePaint.setColor(applyAlpha(cardColor, strokeAlpha));
                strokePaint.setMaskFilter(new android.graphics.BlurMaskFilter(
                    8f, android.graphics.BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(r, radius, radius, strokePaint);
            } else {
                // Light: clean white card + drop shadow + thin left accent bar
                // 1. Drop shadow
                Paint shadowP = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowP.setStyle(Paint.Style.FILL);
                shadowP.setColor(0x14000000);
                shadowP.setMaskFilter(new android.graphics.BlurMaskFilter(
                    10f, android.graphics.BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(new RectF(2, 4, w-2, h+2), radius, radius, shadowP);

                // 2. White card fill
                bgPaint.setShader(null);
                bgPaint.setColor(0xFFFFFFFF);
                canvas.drawRoundRect(r, radius, radius, bgPaint);

                // 3. Thin left accent bar (4dp wide, flat right edge)
                float barW = 4f * getResources().getDisplayMetrics().density;
                Paint accentBar = new Paint(Paint.ANTI_ALIAS_FLAG);
                accentBar.setStyle(Paint.Style.FILL);
                accentBar.setColor(applyAlpha(cardColor, 0.9f));
                accentBar.setMaskFilter(null);
                // Save clip so right side of bar is flat (not rounded)
                canvas.save();
                canvas.clipRect(0, 0, barW, h);
                canvas.drawRoundRect(r, radius, radius, accentBar);
                canvas.restore();

                // 4. Hairline border
                strokePaint.setShader(null); strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(1f);
                strokePaint.setColor(0x18000000);
                strokePaint.setMaskFilter(null);
                canvas.drawRoundRect(new RectF(0.5f, 0.5f, w-0.5f, h-0.5f), radius, radius, strokePaint);
            }
            super.onDraw(canvas);
        }
        private float dp(float v) {
            return v * getContext().getResources().getDisplayMetrics().density;
        }
        private int applyAlpha(int color, float alpha) {
            return (color&0x00FFFFFF)|(Math.round(alpha*255)<<24);
        }
    }

    static class TrendChartView extends View {
        private final int[] data;
        private final int color;
        private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TrendChartView(Context ctx, int[] data, int color) {
            super(ctx); this.data=data; this.color=color;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            linePaint.setColor(color); linePaint.setStrokeWidth(3f);
            linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeCap(Paint.Cap.ROUND);
            fillPaint.setColor(isDark ? applyAlpha(color,0.15f) : applyAlpha(color,0.08f)); fillPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(color); dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setShadowLayer(8f,0f,0f,color);
            labelPaint.setColor(isDark ? 0x88FFFFFF : 0xFF64748B); labelPaint.setTextSize(20f);
            labelPaint.setTypeface(Typeface.MONOSPACE);
            labelPaint.setTextAlign(Paint.Align.CENTER);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (data.length<2||getWidth()==0) return;
            float w=getWidth(),h=getHeight();
            int max=1; for (int v:data) if(v>max) max=v;
            float stepX=w/(data.length-1), padY=28f;
            Path linePath=new Path(), fillPath=new Path();
            for (int i=0;i<data.length;i++) {
                float x=i*stepX, y=h-padY-(data[i]/(float)max)*(h-2*padY);
                if (i==0) { linePath.moveTo(x,y); fillPath.moveTo(x,h); fillPath.lineTo(x,y); }
                else { linePath.lineTo(x,y); fillPath.lineTo(x,y); }
            }
            fillPath.lineTo((data.length-1)*stepX,h); fillPath.close();
            canvas.drawPath(fillPath,fillPaint);
            canvas.drawPath(linePath,linePaint);
            for (int i : new int[]{0,data.length-1}) {
                float x=i*stepX, y=h-padY-(data[i]/(float)max)*(h-2*padY);
                canvas.drawCircle(x,y,5f,dotPaint);
                canvas.drawText(String.valueOf(data[i]),x,y-12f,labelPaint);
            }
        }
        private int applyAlpha(int color, float alpha) {
            return (color&0x00FFFFFF)|(Math.round(alpha*255)<<24);
        }
    }
}

