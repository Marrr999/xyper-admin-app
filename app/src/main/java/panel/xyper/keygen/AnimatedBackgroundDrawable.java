//ABSTRACT RAINBOW DOT LINE

package panel.xyper.keygen;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnimatedBackgroundDrawable extends Drawable {

    // ====== CONFIG ======
    private static final int   POINT_COUNT   = 100;    // sama seperti pts.resize(90)
    private static final float MAX_DIST      = 160f;  // maxDist
    private static final float CORE_RADIUS_MIN = 0.4f;
    private static final float CORE_RADIUS_VAR = 100f / 10f; // +rand/70
    private static final float VEL_SCALE     = 0.01f; // *0.04f

    private final Paint bgPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static class Pt {
        float x, y;
        float vx, vy;
        float r;
        float hue; // 0..360
    }

    private final List<Pt> pts = new ArrayList<>();
    private boolean initialized = false;
    private final Random rnd = new Random();

    // warna tema (ganti sesuai selera)
    private final int particleCore = Color.parseColor("#00F6FF");  // gTheme.particleCore
    private final int particleGlow = 0x5500F6FF;                    // glow
    private final int lineBase     = 0xFF00F6FF;                    // gTheme.lineBase

    public AnimatedBackgroundDrawable() {
        glowPaint.setStyle(Paint.Style.FILL);
        corePaint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.2f);
    }

    private void ensureInit(int w, int h) {
        if (initialized) return;
        pts.clear();
        for (int i = 0; i < POINT_COUNT; i++) {
            Pt p = new Pt();
            p.x = rnd.nextFloat() * w;
            p.y = rnd.nextFloat() * h;
            p.vx = ((rnd.nextInt(200) - 100) * VEL_SCALE);
            p.vy = ((rnd.nextInt(200) - 100) * VEL_SCALE);
            p.r = CORE_RADIUS_MIN + rnd.nextInt(100) / CORE_RADIUS_VAR;
            float baseHue = 140f;       // misal mulai di biru‑cyan 180
            float range   = 70f;       // main di 180..300 (cyan → ungu) 120
            p.hue = baseHue + rnd.nextFloat() * range;
            pts.add(p);
        }
        initialized = true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect r = getBounds();
        int w = r.width();
        int h = r.height();
        if (w <= 0 || h <= 0) return;

        ensureInit(w, h);

        // ===== 1. background gradient gelap =====
        LinearGradient lg = new LinearGradient(
            0, 0, 0, h,
            new int[]{
                Color.parseColor("#050814"),
                Color.parseColor("#0B1024"),
                Color.parseColor("#101833"),
                Color.parseColor("#050713")
            },
            new float[]{0f, 0.35f, 0.7f, 1f},
            Shader.TileMode.CLAMP
        );
        bgPaint.setShader(lg);
        canvas.drawRect(r, bgPaint);

        // ===== 2. update posisi & gambar titik =====
        /*glowPaint.setColor(particleGlow);
        corePaint.setColor(particleCore);

        for (Pt p : pts) {
            p.x += p.vx;
            p.y += p.vy;

            if (p.x < -10)      p.x = w + 10;
            if (p.y < -10)      p.y = h + 10;
            if (p.x > w + 10)   p.x = -10;
            if (p.y > h + 10)   p.y = -10;

            float cx = r.left + p.x;
            float cy = r.top + p.y;

            // glow
            canvas.drawCircle(cx, cy, p.r * 1.4f, glowPaint);
            // core
            canvas.drawCircle(cx, cy, p.r * 0.9f, corePaint);
        }*/ //Warna Biru
        
        for (Pt p : pts) {
            p.x += p.vx;
            p.y += p.vy;
            // wrap posisi sama seperti tadi...
            
            if (p.x < -10)      p.x = w + 10;
            if (p.y < -10)      p.y = h + 10;
            if (p.x > w + 10)   p.x = -10;
            if (p.y > h + 10)   p.y = -10;

            float cx = r.left + p.x;
            float cy = r.top + p.y;

            // update hue pelan biar rainbow jalan
            p.hue += 0.3f;   // dari 0.3f -> 0.08f
            if (p.hue > 360f) p.hue -= 360f;

            // konversi HSV -> ARGB
            float[] hsv = new float[]{p.hue, 0.9f, 1.0f};
            int coreColor = Color.HSVToColor(hsv);
            int glowColor = applyAlpha(coreColor, 0.35f); // atau pakai applyAlpha

            glowPaint.setColor(glowColor);
            corePaint.setColor(coreColor);

            canvas.drawCircle(cx, cy, p.r * 1.2f, glowPaint);
            canvas.drawCircle(cx, cy, p.r * 0.8f, corePaint);
        }

        // ===== 3. garis koneksi =====
        int lb = lineBase;
        int lr = (lb >> 16) & 0xFF;
        int lgc = (lb >> 8) & 0xFF;
        int lbk = lb & 0xFF;

        for (int i = 0; i < pts.size(); i++) {
            Pt a = pts.get(i);
            for (int j = i + 1; j < pts.size(); j++) {
                Pt b = pts.get(j);
                float dx = a.x - b.x;
                float dy = a.y - b.y;
                float dist2 = dx * dx + dy * dy;
                float maxDist2 = MAX_DIST * MAX_DIST;
                if (dist2 < maxDist2) {
                    float dist = (float) Math.sqrt(dist2);
                    float alphaFactor = 1f - (dist / MAX_DIST);
                    int alpha = (int) (alphaFactor * 255);

                    // hue garis = rata-rata hue dua titik
                    float hue = (a.hue + b.hue) * 0.5f;
                    if (Math.abs(a.hue - b.hue) > 180f) {
                        // handle wrap 360 -> 0
                        hue = ((a.hue + b.hue + 360f) * 0.5f) % 360f;
                    }
                    float[] hsvLine = new float[]{hue, 0.7f, 1.0f};
                    int rgbLine = Color.HSVToColor(hsvLine);
                    int lineColor = (rgbLine & 0x00FFFFFF) | (alpha << 24);

                    linePaint.setColor(lineColor);
                    canvas.drawLine(
                        r.left + a.x, r.top + a.y,
                        r.left + b.x, r.top + b.y,
                        linePaint
                    );
                }
            }
        }

        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        bgPaint.setAlpha(alpha);
        glowPaint.setAlpha(alpha);
        corePaint.setAlpha(alpha);
        linePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
    
    private static int applyAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
