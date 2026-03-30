package panel.xyper.keygen;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalApplication extends Application {

    private static Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        ApiClient.init(this);
        CrashHandler.getInstance().registerGlobal(this);
        CrashHandler.getInstance().registerPart(this);
    }

    public static void write(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024 * 8];
        int len;
        while ((len = input.read(buf)) != -1) {
            output.write(buf, 0, len);
        }
    }

    public static void write(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        ByteArrayInputStream input = new ByteArrayInputStream(data);
        FileOutputStream output = new FileOutputStream(file);
        try {
            write(input, output);
        } finally {
            closeIO(input, output);
        }
    }

    public static String toString(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(input, output);
        try {
            return output.toString("UTF-8");
        } finally {
            closeIO(input, output);
        }
    }

    public static void closeIO(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            try {
                if (closeable != null) closeable.close();
            } catch (IOException ignored) {}
        }
    }

    public static class CrashHandler {

        public static final UncaughtExceptionHandler DEFAULT_UNCAUGHT_EXCEPTION_HANDLER = Thread.getDefaultUncaughtExceptionHandler();

        private static CrashHandler sInstance;

        private PartCrashHandler mPartCrashHandler;

        public static CrashHandler getInstance() {
            if (sInstance == null) {
                sInstance = new CrashHandler();
            }
            return sInstance;
        }

        public void registerGlobal(Context context) {
            registerGlobal(context, null);
        }

        public void registerGlobal(Context context, String crashDir) {
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandlerImpl(context.getApplicationContext(), crashDir));
        }

        public void unregister() {
            Thread.setDefaultUncaughtExceptionHandler(DEFAULT_UNCAUGHT_EXCEPTION_HANDLER);
        }

        public void registerPart(Context context) {
            unregisterPart(context);
            mPartCrashHandler = new PartCrashHandler(context.getApplicationContext());
            MAIN_HANDLER.postAtFrontOfQueue(mPartCrashHandler);
        }

        public void unregisterPart(Context context) {
            if (mPartCrashHandler != null) {
                mPartCrashHandler.isRunning.set(false);
                mPartCrashHandler = null;
            }
        }

        private static class PartCrashHandler implements Runnable {

            private final Context mContext;

            public AtomicBoolean isRunning = new AtomicBoolean(true);

            public PartCrashHandler(Context context) {
                this.mContext = context;
            }

            @Override
            public void run() {
                while (isRunning.get()) {
                    try {
                        Looper.loop();
                    } catch (final Throwable e) {
                        e.printStackTrace();
                        if (isRunning.get()) {
                            MAIN_HANDLER.post(new Runnable(){

                                    @Override
                                    public void run() {
                                        try {
                                            final String fullMsg = Log.getStackTraceString(e);
                                            android.app.AlertDialog.Builder builder =
                                                new android.app.AlertDialog.Builder(mContext);
                                            builder.setTitle("⚠ Runtime Error");

                                            android.widget.ScrollView sv = new android.widget.ScrollView(mContext);
                                            android.widget.TextView tv = new android.widget.TextView(mContext);
                                            tv.setText(fullMsg);
                                            tv.setTextSize(11f);
                                            tv.setTextIsSelectable(true);
                                            tv.setPadding(32, 24, 32, 24);
                                            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                                            sv.addView(tv);

                                            builder.setView(sv);
                                            builder.setPositiveButton("Copy", new android.content.DialogInterface.OnClickListener() {
													@Override
													public void onClick(android.content.DialogInterface d, int w) {
														android.content.ClipboardManager cm = (android.content.ClipboardManager)
															mContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
														cm.setPrimaryClip(android.content.ClipData.newPlainText("error", fullMsg));
														Toast.makeText(mContext, "Copied!", Toast.LENGTH_SHORT).show();
													}
												});
                                            builder.setNegativeButton("Dismiss", null);
                                            android.app.AlertDialog dialog = builder.create();
                                            dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                                            dialog.show();
                                        } catch (Throwable ignored) {
                                            Toast.makeText(mContext, e.toString(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                        } else {
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException)e;
                            } else {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }

        private static class UncaughtExceptionHandlerImpl implements UncaughtExceptionHandler {

            private static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss");

            private final Context mContext;

            private final File mCrashDir;

            public UncaughtExceptionHandlerImpl(Context context, String crashDir) {
                this.mContext = context;
                this.mCrashDir = TextUtils.isEmpty(crashDir) ? new File(mContext.getExternalCacheDir(), "crash") : new File(crashDir);
            }

            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {

                    String log = buildLog(throwable);
                    writeLog(log);

                    try {
                        Intent intent = new Intent(mContext, CrashActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra(Intent.EXTRA_TEXT, log);
                        mContext.startActivity(intent);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        writeLog(e.toString());
                    }

                    throwable.printStackTrace();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);

                } catch (Throwable e) {
                    if (DEFAULT_UNCAUGHT_EXCEPTION_HANDLER != null) DEFAULT_UNCAUGHT_EXCEPTION_HANDLER.uncaughtException(thread, throwable);
                }
            }

            private String buildLog(Throwable throwable) {
                String time = DATE_FORMAT.format(new Date());

                String versionName = "unknown";
                long versionCode = 0;
                try {
                    PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                    versionName = packageInfo.versionName;
                    versionCode = Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
                } catch (Throwable ignored) {}

                LinkedHashMap<String, String> head = new LinkedHashMap<String, String>();
                head.put("Time Of Crash", time);
                head.put("Device", String.format("%s, %s", Build.MANUFACTURER, Build.MODEL));
                head.put("Android Version", String.format("%s (%d)", Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
                head.put("App Version", String.format("%s (%d)", versionName, versionCode));
                head.put("Kernel", getKernel());
                head.put("Support Abis", Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS != null ? Arrays.toString(Build.SUPPORTED_ABIS): "unknown");
                head.put("Fingerprint", Build.FINGERPRINT);

                StringBuilder builder = new StringBuilder();

                for (String key : head.keySet()) {
                    if (builder.length() != 0) builder.append("\n");
                    builder.append(key);
                    builder.append(" :    ");
                    builder.append(head.get(key));
                }

                builder.append("\n\n");
                builder.append(Log.getStackTraceString(throwable));

                return builder.toString(); 
            }

            private void writeLog(String log) {
                String time = DATE_FORMAT.format(new Date());
                File file = new File(mCrashDir, "crash_" + time + ".txt");
                try {
                    write(file, log.getBytes("UTF-8"));
                } catch (Throwable e) {
                    e.printStackTrace();
                } 
            }

            private static String getKernel() {
                try {
                    return GlobalApplication.toString(new FileInputStream("/proc/version")).trim();
                } catch (Throwable e) {
                    return e.getMessage();
                }
            }
        }
    }

    public static final class CrashActivity extends Activity {

        private String mLog;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLog = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (mLog == null) mLog = "No crash info available.";

            // ── Root layout (dark) ──
            android.widget.LinearLayout root = new android.widget.LinearLayout(this);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            root.setBackgroundColor(0xFF060D1E);
            setContentView(root);

            // ── Header ──
            android.widget.LinearLayout header = new android.widget.LinearLayout(this);
            header.setOrientation(android.widget.LinearLayout.VERTICAL);
            header.setBackgroundColor(0xFF0A1628);
            header.setPadding(dp2px(20), dp2px(20), dp2px(20), dp2px(16));
            android.widget.TextView tvTitle = new android.widget.TextView(this);
            tvTitle.setText("💥  App Crashed");
            tvTitle.setTextColor(0xFFF87171);
            tvTitle.setTextSize(20f);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(tvTitle);
            android.widget.TextView tvSub = new android.widget.TextView(this);
            tvSub.setText("Copy the error below and send it for debugging");
            tvSub.setTextColor(0xFF64748B);
            tvSub.setTextSize(12f);
            tvSub.setTypeface(Typeface.MONOSPACE);
            android.widget.LinearLayout.LayoutParams subLp = new android.widget.LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp2px(4);
            tvSub.setLayoutParams(subLp);
            header.addView(tvSub);
            root.addView(header, new android.widget.LinearLayout.LayoutParams(-1, -2));

            // ── Divider ──
            android.view.View div = new android.view.View(this);
            div.setBackgroundColor(0xFFF87171);
            root.addView(div, new android.widget.LinearLayout.LayoutParams(-1, dp2px(2)));

            // ── Scrollable log ──
            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(0xFF060D1E);
            sv.setVerticalScrollBarEnabled(true);
            android.widget.TextView tvLog = new android.widget.TextView(this);
            tvLog.setText(mLog);
            tvLog.setTextColor(0xFFCBD5E1);
            tvLog.setTextSize(11f);
            tvLog.setTypeface(Typeface.MONOSPACE);
            tvLog.setTextIsSelectable(true);
            tvLog.setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16));
            tvLog.setLineSpacing(dp2px(2), 1f);
            sv.addView(tvLog);
            android.widget.LinearLayout.LayoutParams svLp = new android.widget.LinearLayout.LayoutParams(-1, 0, 1f);
            root.addView(sv, svLp);

            // ── Bottom buttons ──
            android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
            btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            btnRow.setBackgroundColor(0xFF0A1628);
            btnRow.setPadding(dp2px(16), dp2px(12), dp2px(16), dp2px(12));

            // Copy button
            android.widget.TextView btnCopy = new android.widget.TextView(this);
            btnCopy.setText("📋  Copy Error");
            btnCopy.setTextColor(0xFF22E5FF);
            btnCopy.setTextSize(13f);
            btnCopy.setTypeface(Typeface.DEFAULT_BOLD);
            btnCopy.setGravity(android.view.Gravity.CENTER);
            btnCopy.setPadding(dp2px(16), dp2px(12), dp2px(16), dp2px(12));
            android.graphics.drawable.GradientDrawable copyBg = new android.graphics.drawable.GradientDrawable();
            copyBg.setColor(0x2222E5FF);
            copyBg.setCornerRadius(dp2px(10));
            copyBg.setStroke(dp2px(1), 0x8822E5FF);
            btnCopy.setBackground(copyBg);
            android.widget.LinearLayout.LayoutParams copyLp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            copyLp.rightMargin = dp2px(8);
            btnCopy.setLayoutParams(copyLp);
            final String logFinal = mLog;
            btnCopy.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("crash_log", logFinal));
                    Toast.makeText(CrashActivity.this, "✅ Copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
            btnRow.addView(btnCopy);

            // Save to Downloads
            android.widget.TextView btnSave = new android.widget.TextView(this);
            btnSave.setText("💾  Save Log");
            btnSave.setTextColor(0xFFFBBF24);
            btnSave.setTextSize(13f);
            btnSave.setTypeface(Typeface.DEFAULT_BOLD);
            btnSave.setGravity(android.view.Gravity.CENTER);
            btnSave.setPadding(dp2px(16), dp2px(12), dp2px(16), dp2px(12));
            android.graphics.drawable.GradientDrawable saveBg = new android.graphics.drawable.GradientDrawable();
            saveBg.setColor(0x22FBBF24);
            saveBg.setCornerRadius(dp2px(10));
            saveBg.setStroke(dp2px(1), 0x88FBBF24);
            btnSave.setBackground(saveBg);
            android.widget.LinearLayout.LayoutParams saveLp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            saveLp.rightMargin = dp2px(8);
            btnSave.setLayoutParams(saveLp);
            btnSave.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    try {
                        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                            java.util.Locale.getDefault()).format(new java.util.Date());
                        java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS);
                        java.io.File file = new java.io.File(dir, "xyper_crash_" + ts + ".txt");
                        java.io.FileWriter fw = new java.io.FileWriter(file);
                        fw.write(logFinal);
                        fw.close();
                        Toast.makeText(CrashActivity.this,
                            "✅ Saved to Downloads/xyper_crash_" + ts + ".txt",
                            Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(CrashActivity.this,
                            "❌ Failed to save: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    }
                }
            });
            btnRow.addView(btnSave);

            // Restart button
            android.widget.TextView btnRestart = new android.widget.TextView(this);
            btnRestart.setText("↺  Restart");
            btnRestart.setTextColor(0xFF34D399);
            btnRestart.setTextSize(13f);
            btnRestart.setTypeface(Typeface.DEFAULT_BOLD);
            btnRestart.setGravity(android.view.Gravity.CENTER);
            btnRestart.setPadding(dp2px(16), dp2px(12), dp2px(16), dp2px(12));
            android.graphics.drawable.GradientDrawable restartBg = new android.graphics.drawable.GradientDrawable();
            restartBg.setColor(0x2234D399);
            restartBg.setCornerRadius(dp2px(10));
            restartBg.setStroke(dp2px(1), 0x8834D399);
            btnRestart.setBackground(restartBg);
            btnRestart.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
            btnRestart.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) { restart(); }
            });
            btnRow.addView(btnRestart);

            root.addView(btnRow, new android.widget.LinearLayout.LayoutParams(-1, -2));
        }

        private void restart() {
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }

        private int dp2px(float dpValue) {
            return (int) (dpValue * getResources().getDisplayMetrics().density + 0.5f);
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            menu.add(0, android.R.id.copy, 0, "Copy Log")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            return super.onCreateOptionsMenu(menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.copy) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(getPackageName(), mLog));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onBackPressed() { restart(); }
    }
}

