package panel.xyper.keygen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String IC_DASHBOARD = "\uE871";
    private static final String IC_KEYS      = "\uE0DA";
    private static final String IC_ADD       = "\uE147";
    private static final String IC_CONFIG    = "\uE8B8";
    private static final String IC_ACCOUNT   = "\uE7FD";
    private static final String IC_CLIENTS   = "\uE7EF";
    private static final String IC_MANAGE    = "\uE8D3"; // supervisor_account

    private static final int TAB_DASHBOARD = 0;
    private static final int TAB_KEYS      = 1;
    private static final int TAB_ADD       = 2;
    private static final int TAB_CONFIG    = 4;
    private static final int TAB_ACCOUNT   = 3;
    private static final int TAB_CLIENTS   = 10;
    private static final int TAB_LOGS      = 5;  // ServerLogPanel (sidebar)
    private static final int TAB_MANAGE_RESELLER = 7; // ManageReseller panel
    private static final int TAB_SETTINGS   = 6;  // SettingsPanel (sidebar)

    // ── Dimensi navbar ────────────────────────────────────────────
    // Bubble 55% di atas pill, 45% di bawah.
    // Bubble overflow ke atas menggunakan topMargin negatif.
    private static final int BUBBLE_D     = 54;  // dp diameter bubble
    private static final int PILL_H       = 58;  // dp tinggi pill
    private static final int BUBBLE_ABOVE = 30;  // dp bagian bubble di atas pill (55% × 54)
    private static final int GAP          = 5;   // dp gap antara bubble dan tepi notch

    private FrameLayout root;
    private FrameLayout contentFrame;
    private int         currentTab  = -1;
    private boolean     isDeveloper = false;
    private Typeface    materialIcons;

    private FrameLayout navWrapper;   // tinggi=PILL_H, clipChildren=false
    private NavPillView pillView;     // draws pill + animated concave notch
    private NavItem[]   navItems;
    private int[]       navTabs;

    // Sidebar & back press
    private FrameLayout sidebarOverlay;
    private long        lastBackPress = 0;

    private static class NavItem {
        FrameLayout bubble;
        TextView    icon;
        TextView    label;
        FrameLayout slot;
    }

    // ══════════════════════════════════════════════════════════════
    // NavPillView
    // Menggunakan canvas.saveLayer() untuk membuat compositing context
    // sendiri sehingga PorterDuff.CLEAR bisa "melubangi" fill pill
    // tanpa terpengaruh parent background.
    // ══════════════════════════════════════════════════════════════
    private class NavPillView extends View {
        private final Paint fillP   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint clearP  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcP    = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float         notchX    = -1f;
        private int           tabCount  = 1;
        private ValueAnimator notchAnim;

        NavPillView(int pillColor, int accentColor) {
            super(MainActivity.this);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // required for saveLayer + CLEAR

            fillP.setColor(pillColor);
            fillP.setStyle(Paint.Style.FILL);

            clearP.setStyle(Paint.Style.FILL);
            clearP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            strokeP.setColor(accentColor);
            strokeP.setStyle(Paint.Style.STROKE);
            strokeP.setStrokeWidth(dp(1.5f));

            arcP.setColor(accentColor);
            arcP.setStyle(Paint.Style.STROKE);
            arcP.setStrokeWidth(dp(1.5f));
        }

        void animateTo(int slotIdx, int count) {
            tabCount = count;
            if (getWidth() == 0) { post(() -> animateTo(slotIdx, count)); return; }
            float slotW  = getWidth() / (float) count;
            float dest   = slotW * slotIdx + slotW / 2f;
            if (notchX < 0) { notchX = dest; invalidate(); return; }
            if (notchAnim != null) notchAnim.cancel();
            notchAnim = ValueAnimator.ofFloat(notchX, dest);
            notchAnim.setDuration(320);
            notchAnim.setInterpolator(new OvershootInterpolator(1.3f));
            notchAnim.addUpdateListener(a -> {
                notchX = (float) a.getAnimatedValue();
                invalidate();
            });
            notchAnim.start();
        }

        private float clampNotchX(float x) { return x; } // kept for compat, no longer clamps

        void clearNotch() {
            if (notchAnim != null) notchAnim.cancel();
            notchX = -1f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float r      = h / 2f;
            float notchR = dp(BUBBLE_D) / 2f + dp(GAP);

            // ── 1 & 2: Fill pill + CLEAR notch (di dalam saveLayer) ──────
            canvas.saveLayer(0, 0, w, h, null);
            canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, fillP);
            if (notchX >= 0) {
                canvas.drawCircle(notchX, 0f, notchR, clearP);
            }
            canvas.restore();

            // ── 3: Border pill — digambar sebagai Path yang SKIP area notch ──
            // Tanpa notch: gambar border rounded rect biasa
            // Dengan notch: gambar border sebagai dua bagian:
            //   a) sisi kanan notch → corner kanan → bottom → corner kiri → sisi kiri notch
            //   b) arc bawah notch (masuk ke dalam pill)
            float ins = strokeP.getStrokeWidth() / 2f;

            if (notchX < 0) {
                // Tidak ada notch — border penuh
                canvas.drawRoundRect(new RectF(ins, ins, w - ins, h - ins),
                        r - ins, r - ins, strokeP);
            } else {
                // Titik pertemuan garis atas pill (y=ins) dengan lingkaran notch (cx=notchX, cy=0, r=notchR)
                // x = notchX ± sqrt(notchR² - ins²)
                double distSq = notchR * notchR - ins * ins;
                float  intX   = (distSq > 0) ? (float) Math.sqrt(distSq) : 0f;
                float  leftX  = notchX - intX;
                float  rightX = notchX + intX;

                // Clamp agar tidak melewati area corner pill
                leftX  = Math.max(ins + r, leftX);
                rightX = Math.min(w - ins - r, rightX);

                // ── Border path: mulai kanan notch → corner kanan → bottom → corner kiri → kiri notch ──
                android.graphics.Path borderPath = new android.graphics.Path();

                // Start: titik kanan pertemuan
                borderPath.moveTo(rightX, ins);

                // Garis atas kanan ke corner kanan atas
                if (rightX < w - ins - r) borderPath.lineTo(w - ins - r, ins);

                // Corner kanan atas (0° → 90°, clockwise → arc dari atas ke kanan)
                // RectF untuk arc = bounding box lingkaran corner
                float cr = r - ins; // corner radius yang di-inset
                borderPath.arcTo(new RectF(w - ins - 2*cr, ins, w - ins, ins + 2*cr),
                        270f, 90f, false);

                // Sisi kanan (turun)
                borderPath.lineTo(w - ins, h - ins - cr);

                // Corner kanan bawah (0° → 90°)
                borderPath.arcTo(new RectF(w - ins - 2*cr, h - ins - 2*cr, w - ins, h - ins),
                        0f, 90f, false);

                // Bottom (kiri)
                borderPath.lineTo(ins + cr, h - ins);

                // Corner kiri bawah (90° → 90°)
                borderPath.arcTo(new RectF(ins, h - ins - 2*cr, ins + 2*cr, h - ins),
                        90f, 90f, false);

                // Sisi kiri (naik)
                borderPath.lineTo(ins, ins + cr);

                // Corner kiri atas (180° → 90°)
                borderPath.arcTo(new RectF(ins, ins, ins + 2*cr, ins + 2*cr),
                        180f, 90f, false);

                // Garis atas kiri ke titik kiri notch
                if (leftX > ins + r) borderPath.lineTo(leftX, ins);

                canvas.drawPath(borderPath, strokeP);

                // ── Arc notch: dari titik kanan ke titik kiri, melewati BAWAH lingkaran ──
                // startAngle = sudut titik rightX di lingkaran notch (dari center notchX,0)
                // angle = acos((rightX - notchX) / notchR)  → sudut dari arah kanan (0°)
                float startAngle = (float) -Math.toDegrees(Math.acos(
                        Math.min(1.0, (rightX - notchX) / notchR)));
                // sweep dari startAngle ke 180-startAngle (melewati bawah = 180°)
                float sweepAngle = 360f - 2f * Math.abs(startAngle);

                // Clip ke dalam pill agar arc tidak meluber ke luar
                canvas.save();
                android.graphics.Path pillClip = new android.graphics.Path();
                pillClip.addRoundRect(new RectF(0, 0, w, h), r, r,
                        android.graphics.Path.Direction.CW);
                canvas.clipPath(pillClip);
                RectF arcRect = new RectF(notchX - notchR, -notchR, notchX + notchR, notchR);
                canvas.drawArc(arcRect, startAngle, sweepAngle, false, arcP);
                canvas.restore();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            super.onCreate(savedInstanceState);
            // ── Warna transparan dulu (aman sebelum setContentView) ──
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(false);
            }
            try { materialIcons = Typeface.createFromAsset(getAssets(), "MaterialIcons-Regular.ttf"); }
            catch (Exception e) { materialIcons = null; }
            root = new FrameLayout(this);
            root.setBackgroundColor(Color.TRANSPARENT);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            setContentView(root);
            // ── Hide status bar SETELAH setContentView (DecorView sudah ada) ──
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // API 30+ — WindowInsetsController
                android.view.WindowInsetsController wic = getWindow().getInsetsController();
                if (wic != null) {
                    wic.hide(android.view.WindowInsets.Type.statusBars());
                    wic.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                // API < 30 — SystemUiVisibility flags
                getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }
            requestAppPermissions();
            goSplash();
        } catch (final Throwable crash) {
            showOnCreateCrash(crash);
        }
    }

    // ── Runtime permission requests ──────────────────────
    private void requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // Android 13+ — POST_NOTIFICATIONS is runtime permission
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    101
                );
            }
        }
        if (android.os.Build.VERSION.SDK_INT <= 28) {
            // Android 9 ke bawah — WRITE_EXTERNAL_STORAGE untuk save CSV/TXT ke Downloads
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    102
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            // Notif permission result
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted — schedule if setting is on
                if (SettingsPanel.isNotifExpiryEnabled(this)) {
                    ExpiryNotificationWorker.schedule(this);
                }
            }
            // If denied — user can enable later from Settings
        }
        // WRITE_EXTERNAL_STORAGE (requestCode 102) — no action needed,
        // Android 10+ pakai MediaStore/Downloads tanpa permission
    }

        private void showOnCreateCrash(final Throwable t) {
        try {
            final String trace = android.util.Log.getStackTraceString(t);
            final String fullMsg = "=== CRASH IN MainActivity.onCreate() ===" + "\n"
                + "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n"
                + "Android: " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")" + "\n"
                + "========================================\n\n" + trace;
            try {
                String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                    java.util.Locale.getDefault()).format(new java.util.Date());
                java.io.File f2 = new java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS),
                    "xyper_crash_" + ts + ".txt");
                java.io.FileWriter fw = new java.io.FileWriter(f2);
                fw.write(fullMsg); fw.close();
            } catch (Exception ignored) {}
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setBackgroundColor(0xFF060D1E);
            android.widget.TextView hdr = new android.widget.TextView(this);
            hdr.setText("App Crashed — copy & report this error");
            hdr.setTextColor(0xFFF87171); hdr.setTextSize(14f);
            hdr.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            hdr.setBackgroundColor(0xFF0A1628); hdr.setPadding(40, 50, 40, 30);
            layout.addView(hdr, new android.widget.LinearLayout.LayoutParams(-1, -2));
            android.view.View div = new android.view.View(this);
            div.setBackgroundColor(0xFFF87171);
            layout.addView(div, new android.widget.LinearLayout.LayoutParams(-1, 4));
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            android.widget.TextView tvErr = new android.widget.TextView(this);
            tvErr.setText(fullMsg); tvErr.setTextColor(0xFFCBD5E1);
            tvErr.setTextSize(11f); tvErr.setTypeface(android.graphics.Typeface.MONOSPACE);
            tvErr.setTextIsSelectable(true); tvErr.setPadding(30, 30, 30, 30);
            sv.addView(tvErr);
            layout.addView(sv, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
            android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
            btnRow.setBackgroundColor(0xFF0A1628); btnRow.setPadding(24, 20, 24, 20);
            android.widget.TextView btnCopy = new android.widget.TextView(this);
            btnCopy.setText("Copy Error"); btnCopy.setTextColor(0xFF22E5FF);
            btnCopy.setTextSize(14f); btnCopy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            btnCopy.setGravity(android.view.Gravity.CENTER); btnCopy.setPadding(20, 24, 20, 24);
            android.graphics.drawable.GradientDrawable bg1 = new android.graphics.drawable.GradientDrawable();
            bg1.setColor(0x2222E5FF); bg1.setCornerRadius(20); bg1.setStroke(2, 0x8822E5FF);
            btnCopy.setBackground(bg1);
            android.widget.LinearLayout.LayoutParams lp1 = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            lp1.rightMargin = 16; btnCopy.setLayoutParams(lp1);
            btnCopy.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", fullMsg));
                    android.widget.Toast.makeText(MainActivity.this, "Copied!", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            btnRow.addView(btnCopy);
            android.widget.TextView btnSaved = new android.widget.TextView(this);
            btnSaved.setText("Saved to Downloads"); btnSaved.setTextColor(0xFF34D399);
            btnSaved.setTextSize(13f); btnSaved.setGravity(android.view.Gravity.CENTER);
            btnSaved.setPadding(20, 24, 20, 24);
            android.graphics.drawable.GradientDrawable bg2 = new android.graphics.drawable.GradientDrawable();
            bg2.setColor(0x2234D399); bg2.setCornerRadius(20); bg2.setStroke(2, 0x8834D399);
            btnSaved.setBackground(bg2);
            btnSaved.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
            btnRow.addView(btnSaved);
            layout.addView(btnRow, new android.widget.LinearLayout.LayoutParams(-1, -2));
            setContentView(layout);
        } catch (Throwable ignored) {
            android.widget.Toast.makeText(this,
                "FATAL: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void goSplash() {
        root.removeAllViews();
        View splash = SplashScreen.build(this, new SplashScreen.OnSplashDone() {
            @Override public void onReady() { goLoginOrMain(); }
        });
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
    }

    private void goLoginOrMain() {
        if (ApiAuthHelper.isLoggedIn(this)) goMain();
        else goLogin();
    }

    private void goLogin() {
        root.removeAllViews();
        currentTab  = -1;
        View loginView = LoginScreen.build(this, new LoginScreen.OnLoginDone() {
            @Override public void onSuccess(String role) { goMain(); }
            @Override public void onFail(String reason)  { }
        });
        root.addView(loginView, new FrameLayout.LayoutParams(-1, -1));
    }

    private void goMain() {
        root.removeAllViews();
        isDeveloper = ApiAuthHelper.isDeveloper(this);

        contentFrame = new FrameLayout(this);
        contentFrame.setBackgroundColor(Color.TRANSPARENT);
        contentFrame.setClipChildren(false);
        contentFrame.setClipToPadding(false);
        // FULL SCREEN — tidak ada padding, navbar floating di atas konten
        root.addView(contentFrame, new FrameLayout.LayoutParams(-1, -1));

        showTab(isDeveloper ? TAB_DASHBOARD : TAB_CLIENTS);

        // ── Floating sidebar button (Developer & Reseller) ──
        buildSidebarFab();
    }

    private void buildSidebarFab() {
        final int accentInt = isDeveloper
            ? Color.parseColor("#22E5FF")
            : Color.parseColor("#A78BFA");

        android.widget.TextView fab = new android.widget.TextView(this);
        fab.setText(""); // Material Icons: menu
        fab.setTextSize(20f);
        fab.setTextColor(accentInt);
        fab.setGravity(android.view.Gravity.CENTER);
        // Adaptive glow — GPU on Android 12+, CPU fallback
        fab.setShadowLayer(10f, 0, 0, accentInt);
        fab.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
        if (materialIcons != null) fab.setTypeface(materialIcons);

        android.graphics.drawable.GradientDrawable fabBg = new android.graphics.drawable.GradientDrawable();
        fabBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        fabBg.setColor(Color.parseColor("#0A1628"));
        fabBg.setStroke(dp(2), accentInt);
        fab.setBackground(fabBg);

        int fabSize = dp(42);
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(fabSize, fabSize);
        fabLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        fabLp.topMargin  = dp(36);
        fabLp.rightMargin = dp(16);
        fab.setLayoutParams(fabLp);

        // Breathing glow animation
        final android.graphics.drawable.GradientDrawable fabBgFinal = fabBg;
        android.animation.ValueAnimator glow = android.animation.ValueAnimator.ofFloat(0.4f, 1.0f);
        glow.setDuration(1800);
        glow.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        glow.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        glow.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        glow.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                int alpha = Math.round((float) a.getAnimatedValue() * 255);
                int color = (accentInt & 0x00FFFFFF) | (alpha << 24);
                fabBgFinal.setStroke(dp(2), color);
            }
        });
        glow.start();

        fab.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                showSidebar();
            }
        });

        // Draggable FAB — user can drag to reposition
        fab.setOnTouchListener(new android.view.View.OnTouchListener() {
            private float dX, dY;
            private boolean isDragging = false;
            private float startRawX, startRawY;
            private final float DRAG_THRESHOLD = 8f;

            @Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                switch (e.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        dX = v.getX() - e.getRawX();
                        dY = v.getY() - e.getRawY();
                        startRawX = e.getRawX();
                        startRawY = e.getRawY();
                        isDragging = false;
                        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start();
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float distX = Math.abs(e.getRawX() - startRawX);
                        float distY = Math.abs(e.getRawY() - startRawY);
                        if (!isDragging && (distX > DRAG_THRESHOLD || distY > DRAG_THRESHOLD)) {
                            isDragging = true;
                            v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(80).start();
                        }
                        if (isDragging) {
                            // Clamp within screen bounds
                            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                            float newX = e.getRawX() + dX;
                            float newY = e.getRawY() + dY;
                            newX = Math.max(0, Math.min(newX, dm.widthPixels  - v.getWidth()));
                            newY = Math.max(0, Math.min(newY, dm.heightPixels - v.getHeight()));
                            v.setX(newX);
                            v.setY(newY);
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        if (!isDragging) {
                            // Tap — open sidebar
                            showSidebar();
                        } else {
                            // Snap to nearest edge (left or right)
                            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                            float midScreen = dm.widthPixels / 2f;
                            float targetX = v.getX() < midScreen
                                ? dp(16)
                                : dm.widthPixels - v.getWidth() - dp(16);
                            v.animate().x(targetX).setDuration(220)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f)).start();
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        return true;
                }
                return false;
            }
        });

        root.addView(fab);
    }

    // ══════════════════════════════════════════════════════════════
    // BUILD NAVBAR
    //
    // Struktur view:
    //   root  (clipChildren=false)
    //   ├── contentFrame  (full screen, paddingBottom=PILL_H+20dp)
    //   └── navWrapper    (height=PILL_H, gravity=BOTTOM, clipChildren=false)
    //       ├── pillView  (MATCH_PARENT × MATCH_PARENT)  ← draws pill + notch
    //       ├── slotBar   (MATCH_PARENT × MATCH_PARENT)  ← transparent, holds slots
    //       └── bubble_i  (BUBBLE_D × BUBBLE_D, topMargin=-BUBBLE_ABOVE) ← overflow up!
    //
    // SOLUSI KOTAK HITAM:
    //   navWrapper hanya setinggi pill (tidak ada extra tinggi untuk bubble).
    //   Bubble di-posisi dengan topMargin NEGATIF sehingga overflow ke atas.
    //   Karena root.clipChildren=false, bubble tampil di atas contentFrame
    //   (yang punya background konten, bukan hitam kosong).
    // ══════════════════════════════════════════════════════════════
    private void buildNavBar() {
        final int accentInt = isDeveloper ? Color.parseColor("#22E5FF") : Color.parseColor("#A78BFA");
        final int pillInt   = Color.parseColor("#0A1628");

        String[] icons, labels;
        int[]    tabs;
        if (isDeveloper) {
            icons  = new String[]{IC_DASHBOARD, IC_KEYS, IC_ADD, IC_CONFIG, IC_MANAGE, IC_ACCOUNT};
            labels = new String[]{"Dashboard", "Keys", "Add", "Config", "Resellers", "Account"};
            tabs   = new int[]{TAB_DASHBOARD, TAB_KEYS, TAB_ADD, TAB_CONFIG, TAB_MANAGE_RESELLER, TAB_ACCOUNT};
        } else {
            icons  = new String[]{IC_CLIENTS, IC_ACCOUNT};
            labels = new String[]{"Clients", "Account"};
            tabs   = new int[]{TAB_CLIENTS, TAB_ACCOUNT};
        }
        navTabs  = tabs;
        navItems = new NavItem[tabs.length];

        // navWrapper: FULL WIDTH, transparan — hanya pill yang punya background
        navWrapper = new FrameLayout(this);
        navWrapper.setBackgroundColor(Color.TRANSPARENT);
        navWrapper.setClipChildren(false);
        navWrapper.setClipToPadding(false);

        // navWrapper: FULL WIDTH, tidak ada margin — agar tidak ada area hitam di luar pill
        // Margin 16dp dipindah ke dalam pillView dan slotBar
        FrameLayout.LayoutParams wLp = new FrameLayout.LayoutParams(-1, dp(PILL_H));
        wLp.gravity      = Gravity.BOTTOM;
        wLp.bottomMargin = dp(16);

        // Layer 1: pill background dengan concave notch — punya margin horizontal
        pillView = new NavPillView(pillInt, accentInt);
        FrameLayout.LayoutParams pvLp = new FrameLayout.LayoutParams(-1, -1);
        pvLp.leftMargin  = dp(16);
        pvLp.rightMargin = dp(16);
        navWrapper.addView(pillView, pvLp);

        // Layer 2: slot container — sama marginnya dengan pillView
        LinearLayout slotBar = new LinearLayout(this);
        slotBar.setOrientation(LinearLayout.HORIZONTAL);
        slotBar.setGravity(Gravity.CENTER_VERTICAL);
        slotBar.setClipChildren(false);
        slotBar.setClipToPadding(false);
        FrameLayout.LayoutParams sbLp = new FrameLayout.LayoutParams(-1, -1);
        sbLp.leftMargin  = dp(16);
        sbLp.rightMargin = dp(16);
        navWrapper.addView(slotBar, sbLp);

        // Slots
        for (int i = 0; i < tabs.length; i++) {
            final int tab  = tabs[i];
            NavItem   item = new NavItem();
            navItems[i]    = item;

            item.slot = new FrameLayout(this);
            item.slot.setClipChildren(false);
            slotBar.addView(item.slot, new LinearLayout.LayoutParams(0, dp(PILL_H), 1f));

            item.icon = new TextView(this);
            item.icon.setText(icons[i]);
            item.icon.setTextSize(18f);
            item.icon.setTextColor(Color.parseColor("#4A5568"));
            item.icon.setGravity(Gravity.CENTER);
            if (materialIcons != null) item.icon.setTypeface(materialIcons);
            FrameLayout.LayoutParams iLp = new FrameLayout.LayoutParams(-1, -1);
            iLp.gravity = Gravity.CENTER;
            item.slot.addView(item.icon, iLp);

            item.label = new TextView(this);
            item.label.setText(labels[i]);
            item.label.setTextSize(8f);
            item.label.setTextColor(accentInt);
            item.label.setTypeface(Typeface.DEFAULT_BOLD);
            item.label.setGravity(Gravity.CENTER);
            item.label.setAlpha(0f);
            FrameLayout.LayoutParams lLp = new FrameLayout.LayoutParams(-2, -2);
            lLp.gravity      = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lLp.bottomMargin = dp(7);
            item.slot.addView(item.label, lLp);

            item.slot.setOnClickListener(v -> showTab(tab));
        }

        root.addView(navWrapper, wLp);

        // Bubbles — dibuat setelah slotBar di-layout agar bisa hitung slotW
        final String[] iconsFinal = icons;
        final int      ac         = accentInt;
        final int      pc         = pillInt;

        slotBar.post(new Runnable() {
            @Override public void run() {
                if (slotBar.getWidth() == 0) { slotBar.post(this); return; }
                int n     = navItems.length;
                int slotW = slotBar.getWidth() / n;
                int bSz   = dp(BUBBLE_D);

                for (int i = 0; i < n; i++) {
                    NavItem item = navItems[i];

                    item.bubble = new FrameLayout(MainActivity.this);
                    item.bubble.setClipChildren(false);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.OVAL);
                    bg.setColor(pc);
                    bg.setStroke(dp(2), ac);
                    item.bubble.setBackground(bg);
                    item.bubble.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    item.bubble.setAlpha(0f);
                    item.bubble.setScaleX(0.3f);
                    item.bubble.setScaleY(0.3f);

                    TextView bi = new TextView(MainActivity.this);
                    bi.setText(iconsFinal[i]);
                    bi.setTextSize(20f);
                    bi.setTextColor(ac);
                    bi.setGravity(Gravity.CENTER);
                    bi.setShadowLayer(10f, 0, 0, ac);
                    if (materialIcons != null) bi.setTypeface(materialIcons);
                    item.bubble.addView(bi, new FrameLayout.LayoutParams(-1, -1));

                    // topMargin negatif = bubble overflow ke atas navWrapper
                    // leftMargin = offset pill (16dp) + center slot dalam pill
                    int pillOffset = dp(16);
                    int leftMargin = pillOffset + i * slotW + (slotW - bSz) / 2;
                    int topMargin  = -dp(BUBBLE_ABOVE); // NEGATIF!

                    FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(bSz, bSz);
                    bLp.leftMargin = leftMargin;
                    bLp.topMargin  = topMargin;
                    navWrapper.addView(item.bubble, bLp);
                }

                refreshNavHighlight();
            }
        });
    }

    private void refreshNavHighlight() {
        // Guard: navbar disabled (sidebar-only mode)
        if (navTabs == null || navItems == null) return;
        int activeIdx = -1;
        for (int i = 0; i < navTabs.length; i++) {
            if (navTabs[i] == currentTab) { activeIdx = i; break; }
        }
        if (pillView != null) {
            if (activeIdx >= 0) pillView.animateTo(activeIdx, navTabs.length);
            else pillView.clearNotch();
        }
        String acStr = isDeveloper ? "#22E5FF" : "#A78BFA";
        for (int i = 0; i < navItems.length; i++) {
            setNavActive(navItems[i], navTabs[i] == currentTab, acStr);
        }
    }

    private void setNavActive(NavItem item, boolean active, String activeColor) {
        if (item == null) return;
        if (active) {
            if (item.bubble != null)
                item.bubble.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
                item.bubble.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(300).setInterpolator(new OvershootInterpolator(1.8f))
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            item.bubble.setLayerType(android.view.View.LAYER_TYPE_NONE, null);
                        }
                    }).start();
            item.icon.animate().alpha(0f).setDuration(150).start();
            item.label.animate().alpha(1f).setDuration(200).setStartDelay(100).start();
        } else {
            if (item.bubble != null)
                item.bubble.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
                item.bubble.animate().scaleX(0.3f).scaleY(0.3f).alpha(0f)
                    .setDuration(160).setInterpolator(new AccelerateInterpolator())
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            item.bubble.setLayerType(android.view.View.LAYER_TYPE_NONE, null);
                        }
                    }).start();
            item.icon.animate().alpha(1f).setDuration(150).start();
            item.icon.setTextColor(Color.parseColor("#4A5568"));
            item.label.animate().alpha(0f).setDuration(100).start();
        }
    }

    // ══════════════════════════════════════════════════════════════
    private void showTab(final int tab) {
        if (currentTab == tab) return;
        final int prev = currentTab;
        currentTab = tab;
        refreshNavHighlight();

        final View newContent = buildTabContent(tab);
        if (newContent == null) return;

        if (prev == -1) {
            contentFrame.removeAllViews();
            contentFrame.addView(newContent, new FrameLayout.LayoutParams(-1, -1));
            return;
        }

        if (contentFrame.getChildCount() > 0) {
            final View old = contentFrame.getChildAt(0);
            ObjectAnimator out = ObjectAnimator.ofFloat(old, "alpha", 1f, 0f);
            out.setDuration(100);
            out.setInterpolator(new AccelerateInterpolator());
            out.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator anim) {
                    contentFrame.removeAllViews();
                    newContent.setAlpha(0f);
                    contentFrame.addView(newContent, new FrameLayout.LayoutParams(-1, -1));
                    ObjectAnimator.ofFloat(newContent, "alpha", 0f, 1f).setDuration(180).start();
                }
            });
            out.start();
        } else {
            newContent.setAlpha(0f);
            contentFrame.addView(newContent, new FrameLayout.LayoutParams(-1, -1));
            ObjectAnimator.ofFloat(newContent, "alpha", 0f, 1f).setDuration(180).start();
        }
    }

    private View buildTabContent(int tab) {
        switch (tab) {
            case TAB_DASHBOARD: return KeyDashboard.build(this);
            case TAB_KEYS:      return KeyViewer.build(this);
            case TAB_ADD:
                return KeyManager.build(this, new KeyManager.Callback() {
                    @Override public void onKeyAdded() { showTab(TAB_KEYS); }
                });
            case TAB_CONFIG:    return ConfigPanel.build(this);
            case TAB_CLIENTS:   return ResellerDashboard.build(this);
            case TAB_LOGS:      return ServerLogPanel.build(this);
            case TAB_MANAGE_RESELLER: return ManageReseller.build(this);
            case TAB_SETTINGS:  return SettingsPanel.build(this);
            case TAB_ACCOUNT:
                return AccountPanel.build(this, new AccountPanel.OnLogout() {
                    @Override public void onLogout() { goLogin(); }
                });
            default: return null;
        }
    }

    public void showKeyManager()  { showTab(TAB_ADD); }
    public void showViewer()      { showTab(TAB_KEYS); }
    public void showDashboard()   { showTab(TAB_DASHBOARD); }
    public void showServerLogs()  { showTab(TAB_LOGS); if (sidebarOverlay != null) hideSidebar(); }
    public void showSettings()     { showTab(TAB_SETTINGS); if (sidebarOverlay != null) hideSidebar(); }

    // ══════════════════════════════════════════════════════════════
    // BACK BUTTON — confirm exit
    // ══════════════════════════════════════════════════════════════
    @Override
    public void onBackPressed() {
        // Kalau sidebar terbuka, tutup dulu
        if (sidebarOverlay != null && sidebarOverlay.getParent() != null
                && sidebarOverlay.getAlpha() > 0.1f) {
            hideSidebar();
            return;
        }
        // Double-back untuk keluar
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
            super.onBackPressed();
        } else {
            lastBackPress = now;
            showExitToast();
        }
    }

    private void showExitToast() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(dp(20), dp(14), dp(20), dp(14));
        layout.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(android.graphics.Color.parseColor("#0A1628"));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), android.graphics.Color.parseColor("#22E5FF"));
        layout.setBackground(bg);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText("Tekan back lagi untuk keluar");
        tv.setTextSize(13f);
        tv.setTextColor(android.graphics.Color.parseColor("#22E5FF"));
        layout.addView(tv);
        android.widget.Toast toast = new android.widget.Toast(this);
        toast.setDuration(android.widget.Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, dp(100));
        toast.show();
    }

    // ══════════════════════════════════════════════════════════════
    // SIDEBAR — hanya untuk Developer, akses ke ServerLogPanel
    // ══════════════════════════════════════════════════════════════
    public void showSidebar() {
        if (sidebarOverlay != null && sidebarOverlay.getParent() != null) {
            hideSidebar();
            return;
        }

        final int accentInt = isDeveloper
            ? android.graphics.Color.parseColor("#22E5FF")
            : android.graphics.Color.parseColor("#A78BFA");
        final int strokeColor = isDeveloper
            ? android.graphics.Color.parseColor("#22455A")
            : android.graphics.Color.parseColor("#3D2D6B");
        final int dividerColor = isDeveloper ? 0x3322E5FF : 0x33A78BFA;

        // Dim overlay
        sidebarOverlay = new FrameLayout(this);
        sidebarOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        android.view.View dim = new android.view.View(this);
        dim.setBackgroundColor(0xAA000000);
        dim.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        sidebarOverlay.addView(dim);

        // Sidebar panel (slide dari kanan)
        final android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(50), dp(20), dp(40));
        android.graphics.drawable.GradientDrawable panelBg = new android.graphics.drawable.GradientDrawable();
        panelBg.setColor(android.graphics.Color.parseColor("#0A1628"));
        panelBg.setStroke(dp(1), strokeColor);
        panel.setBackground(panelBg);
        int panelW = (int)(getResources().getDisplayMetrics().widthPixels * 0.72f);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelW, -1);
        panelLp.gravity = android.view.Gravity.END;
        panel.setLayoutParams(panelLp);
        panel.setElevation(dp(12));

        // Header sidebar
        android.widget.TextView sideTitle = new android.widget.TextView(this);
        sideTitle.setText("Menu");
        sideTitle.setTextSize(22f);
        sideTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sideTitle.setTextColor(android.graphics.Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT < 31) {
            sideTitle.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
            sideTitle.setShadowLayer(12f, 0, 0, accentInt);
        }
        android.widget.LinearLayout.LayoutParams sTitleLp =
            new android.widget.LinearLayout.LayoutParams(-1, -2);
        sTitleLp.bottomMargin = dp(6);
        sideTitle.setLayoutParams(sTitleLp);
        panel.addView(sideTitle);

        android.widget.TextView sideSubtitle = new android.widget.TextView(this);
        String roleLabel = isDeveloper ? "Developer" : "Reseller";
        sideSubtitle.setText("Xyper Xit  ·  " + roleLabel);
        sideSubtitle.setTextSize(10f);
        sideSubtitle.setTypeface(android.graphics.Typeface.MONOSPACE);
        sideSubtitle.setTextColor(accentInt);
        android.widget.LinearLayout.LayoutParams sSubLp =
            new android.widget.LinearLayout.LayoutParams(-1, -2);
        sSubLp.bottomMargin = dp(24);
        sideSubtitle.setLayoutParams(sSubLp);
        panel.addView(sideSubtitle);

        // Divider
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(dividerColor);
        android.widget.LinearLayout.LayoutParams divLp =
            new android.widget.LinearLayout.LayoutParams(-1, dp(1));
        divLp.bottomMargin = dp(16);
        divider.setLayoutParams(divLp);
        panel.addView(divider);

        // Sidebar items — berbeda per role
        final String[][] items;
        final int[] tabIds;
        if (isDeveloper) {
            items = new String[][]{
                {"", "Dashboard",     "Overview & stats"},
                {"", "Key Viewer",    "Manage all keys"},
                {"", "Add Key",       "Create a new key"},
                {"", "Config",        "Server configuration"},
                {"", "Manage Reseller","Reseller control & stats"},
                {"", "Server Logs",   "Server-side audit log"},
                {"", "Settings",      "App preferences"},
                {"", "Account",       "Account info & logout"}
            };
            tabIds = new int[]{TAB_DASHBOARD, TAB_KEYS, TAB_ADD, TAB_CONFIG, TAB_MANAGE_RESELLER, TAB_LOGS, TAB_SETTINGS, TAB_ACCOUNT};
        } else {
            // Reseller: hanya Clients, Settings, Account
            items = new String[][]{
                {"", "My Clients",    "Manage your client keys"},
                {"", "Settings",      "App preferences"},
                {"", "Account",       "Account info & logout"}
            };
            tabIds = new int[]{TAB_CLIENTS, TAB_SETTINGS, TAB_ACCOUNT};
        }

        Typeface mi = null;
        try { mi = Typeface.createFromAsset(getAssets(), "MaterialIcons-Regular.ttf"); }
        catch (Exception ignored) {}
        final Typeface miFinal = mi;

        for (int i = 0; i < items.length; i++) {
            final int tabId = tabIds[i];
            final String[] item = items[i];
            final boolean isActive = (currentTab == tabId);

            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            android.widget.LinearLayout.LayoutParams rowLp =
                new android.widget.LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(4);
            row.setLayoutParams(rowLp);

            if (isActive) {
                android.graphics.drawable.GradientDrawable rowBg =
                    new android.graphics.drawable.GradientDrawable();
                rowBg.setColor(0x2022E5FF);
                rowBg.setCornerRadius(dp(12));
                rowBg.setStroke(dp(1), accentInt);
                row.setBackground(rowBg);
            }

            android.widget.TextView icTv = new android.widget.TextView(this);
            icTv.setText(item[0]);
            icTv.setTextSize(20f);
            icTv.setGravity(android.view.Gravity.CENTER);
            icTv.setTextColor(isActive
                ? accentInt
                : android.graphics.Color.parseColor("#475569"));
            if (miFinal != null) icTv.setTypeface(miFinal);
            android.widget.LinearLayout.LayoutParams icLp =
                new android.widget.LinearLayout.LayoutParams(dp(36), dp(36));
            icLp.rightMargin = dp(14);
            icTv.setLayoutParams(icLp);
            row.addView(icTv);

            android.widget.LinearLayout textCol = new android.widget.LinearLayout(this);
            textCol.setOrientation(android.widget.LinearLayout.VERTICAL);

            android.widget.TextView nameTv = new android.widget.TextView(this);
            nameTv.setText(item[1]);
            nameTv.setTextSize(14f);
            nameTv.setTypeface(isActive ? android.graphics.Typeface.DEFAULT_BOLD
                : android.graphics.Typeface.DEFAULT);
            nameTv.setTextColor(isActive
                ? android.graphics.Color.parseColor("#22E5FF")
                : android.graphics.Color.parseColor("#CBD5E1"));
            textCol.addView(nameTv);

            android.widget.TextView descTv = new android.widget.TextView(this);
            descTv.setText(item[2]);
            descTv.setTextSize(10f);
            descTv.setTypeface(android.graphics.Typeface.MONOSPACE);
            descTv.setTextColor(android.graphics.Color.parseColor("#334155"));
            textCol.addView(descTv);
            row.addView(textCol);

            // Special badge untuk Server Logs
            if (isDeveloper && tabId == TAB_LOGS) {
                android.widget.TextView badge = new android.widget.TextView(this);
                badge.setText("NEW");
                badge.setTextSize(9f);
                badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                badge.setTextColor(android.graphics.Color.parseColor("#22E5FF"));
                badge.setPadding(dp(5), dp(2), dp(5), dp(2));
                android.graphics.drawable.GradientDrawable badgeBg =
                    new android.graphics.drawable.GradientDrawable();
                badgeBg.setColor(0x3322E5FF);
                badgeBg.setCornerRadius(dp(4));
                badgeBg.setStroke(dp(1), 0x8822E5FF);
                badge.setBackground(badgeBg);
                android.widget.LinearLayout.LayoutParams badgeLp =
                    new android.widget.LinearLayout.LayoutParams(-2, -2);
                badgeLp.leftMargin = dp(8);
                badgeLp.gravity = android.view.Gravity.CENTER_VERTICAL;
                badge.setLayoutParams(badgeLp);
                row.addView(badge);
            }

            row.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    hideSidebar();
                    showTab(tabId);
                }
            });
            panel.addView(row);
        }

        sidebarOverlay.addView(panel);
        root.addView(sidebarOverlay);

        // Slide in dari kanan
        panel.setTranslationX(panelW);
        panel.animate().translationX(0).setDuration(280)
            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(280).start();

        // Tap dim → tutup sidebar
        dim.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { hideSidebar(); }
        });
    }

    private void hideSidebar() {
        if (sidebarOverlay == null || sidebarOverlay.getParent() == null) return;
        final FrameLayout overlay = sidebarOverlay;
        final android.view.View panel = overlay.getChildAt(1);
        if (panel != null) {
            int panelW = panel.getWidth();
            panel.animate().translationX(panelW).setDuration(220)
                .setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        }
        overlay.animate().alpha(0f).setDuration(220)
            .setListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    root.removeView(overlay);
                    sidebarOverlay = null;
                }
            }).start();
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
    private float dp(float val) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
}
