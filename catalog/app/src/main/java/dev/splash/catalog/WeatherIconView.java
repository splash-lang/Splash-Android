package dev.splash.catalog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A faithful Android port of octos-one's `WeatherIcon` (aichat/widgets/src/weather_icon.rs).
 *
 * The original is an SDF pixel shader driven by `draw_pass.time`. Every primitive
 * it uses — `sdf.circle`, `sdf.box(x,y,w,h,r)`, `sdf.rotate(a,cx,cy)`,
 * `sdf.move_to/line_to/close_path`, `sdf.stroke` — has an exact Canvas
 * counterpart, so the geometry, the colours and the animation are ported
 * one-for-one rather than approximated. `cond` selects the condition:
 *
 *   0 sunny  1 partly cloudy  2 cloudy  3 rain
 *   4 thunderstorm  5 snow  6 wind  7 fog
 */
public class WeatherIconView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final long t0 = System.nanoTime();
    private int cond;

    public WeatherIconView(Context c, int cond) {
        super(c);
        this.cond = cond;
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setCond(int c) { cond = c; invalidate(); }

    /** Seconds since attach — the shader's `self.draw_pass.time`. */
    private float time() { return (System.nanoTime() - t0) / 1_000_000_000f; }

    private static float fract(float v) { return v - (float) Math.floor(v); }

    private void box(Canvas c, float x, float y, float w, float h, float rad) {
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, rad, rad, p);
    }

    @Override protected void onDraw(Canvas c) {
        final float w = getWidth(), h = getHeight(), t = time();
        p.setStyle(Paint.Style.FILL);

        switch (cond) {
            case 0: sunny(c, w, h, t); break;
            case 1: partly(c, w, h, t); break;
            case 2: cloudy(c, w, h, t); break;
            case 3: rain(c, w, h, t); break;
            case 4: thunder(c, w, h, t); break;
            case 5: snow(c, w, h, t); break;
            case 6: wind(c, w, h, t); break;
            default: fog(c, w, h, t); break;
        }
        postInvalidateOnAnimation();   // the shader's continuous ~60fps pump
    }

    // 0 — rotating rays + two-tone disc
    private void sunny(Canvas c, float w, float h, float t) {
        float cx = w * 0.5f, cy = h * 0.5f, rr = w * 0.12f;
        c.save();
        c.rotate((float) Math.toDegrees(t * 0.5f), cx, cy);
        p.setColor(0xFFFFD36B);
        for (int i = 0; i < 8; i++) {
            box(c, cx - w * 0.012f, cy - rr * 3f, w * 0.024f, rr * 1.1f, w * 0.012f);
            c.rotate(45f, cx, cy);           // 0.7854 rad
        }
        c.restore();
        p.setColor(0xFFFFB63C); c.drawCircle(cx, cy, rr * 1.5f, p);
        p.setColor(0xFFFFD06A); c.drawCircle(cx, cy, rr * 1.1f, p);
    }

    /** The shared cloud puff: three circles plus a base bar. */
    private void cloud(Canvas c, float cx, float cy, float rr,
                       int cLeft, int cTop, int cRight, int cBar, boolean thirdCircle) {
        p.setColor(cLeft);  c.drawCircle(cx - rr * 1.1f, cy + rr * 0.2f, rr * 0.9f, p);
        p.setColor(cTop);   c.drawCircle(cx + rr * 0.2f, cy - rr * 0.5f, rr * 1.15f, p);
        if (thirdCircle) { p.setColor(cRight); c.drawCircle(cx + rr * 1.2f, cy + rr * 0.15f, rr * 0.85f, p); }
        p.setColor(cBar);   box(c, cx - rr * 1.9f, cy + rr * 0.1f, rr * 3.8f, rr * 1.0f, rr * 0.5f);
    }

    // 1 — small sun + cloud
    private void partly(Canvas c, float w, float h, float t) {
        float sx = w * 0.36f, sy = h * 0.34f, sr = w * 0.10f;
        c.save();
        c.rotate((float) Math.toDegrees(t * 0.4f), sx, sy);
        p.setColor(0xFFFFD36B);
        for (int i = 0; i < 5; i++) {
            box(c, sx - w * 0.01f, sy - sr * 2.6f, w * 0.02f, sr * 1.0f, w * 0.01f);
            c.rotate(71.96f, sx, sy);        // 1.256 rad
        }
        c.restore();
        p.setColor(0xFFFFC247); c.drawCircle(sx, sy, sr * 1.4f, p);
        cloud(c, w * 0.56f, h * 0.56f, w * 0.13f, 0xFFC4CEDE, 0xFFDBE6F5, 0xFFC4CEDE, 0xFFD4E0F0, true);
    }

    // 2 — two drifting clouds
    private void cloudy(Canvas c, float w, float h, float t) {
        float d = (float) Math.sin(t * 0.9f) * w * 0.04f;
        cloud(c, w * 0.40f + d, h * 0.42f, w * 0.12f, 0xFFAAB6C8, 0xFFBCC8DA, 0, 0xFFB2BECF, false);
        cloud(c, w * 0.58f - d, h * 0.56f, w * 0.14f, 0xFFC4CEDE, 0xFFDBE6F5, 0xFFC4CEDE, 0xFFD4E0F0, true);
    }

    // 3 — cloud + falling drops
    private void rain(Canvas c, float w, float h, float t) {
        float cx = w * 0.5f, cy = h * 0.34f, rr = w * 0.16f;
        cloud(c, cx, cy, rr, 0xFFC4CEDE, 0xFFDBE6F5, 0xFFC4CEDE, 0xFFD4E0F0, true);
        float base = cy + rr * 1.2f, span = h - base + 24f;
        p.setColor(0xFF6DB6FF);
        drop(c, cx - w * 0.22f, base + fract(t * 0.95f) * span, 15f);
        drop(c, cx - w * 0.06f, base + fract(t * 1.15f + 0.35f) * span, 15f);
        drop(c, cx + w * 0.10f, base + fract(t * 0.85f + 0.62f) * span, 15f);
        drop(c, cx + w * 0.22f, base + fract(t * 1.05f + 0.20f) * span, 15f);
    }

    private void drop(Canvas c, float x, float y, float len) { box(c, x, y, 3f, len, 1.5f); }

    // 4 — dark cloud + lightning flash + drops
    private void thunder(Canvas c, float w, float h, float t) {
        float cx = w * 0.5f, cy = h * 0.3f, rr = w * 0.15f;
        cloud(c, cx, cy, rr, 0xFF9AA6B8, 0xFFB0BCCD, 0xFF9AA6B8, 0xFFA6B2C3, true);
        float base = cy + rr * 1.2f;
        if (fract(t * 0.7f) < 0.14f) {
            path.reset();
            path.moveTo(cx - w * 0.02f, base);
            path.lineTo(cx - w * 0.09f, base + h * 0.22f);
            path.lineTo(cx - w * 0.01f, base + h * 0.22f);
            path.lineTo(cx - w * 0.07f, base + h * 0.46f);
            path.lineTo(cx + w * 0.08f, base + h * 0.16f);
            path.lineTo(cx, base + h * 0.16f);
            path.lineTo(cx + w * 0.06f, base);
            path.close();
            p.setColor(0xFFFFD23C); c.drawPath(path, p);
        }
        float span = h - base + 20f;
        p.setColor(0xFF6DB6FF);
        drop(c, cx - w * 0.20f, base + fract(t * 1.1f) * span, 13f);
        drop(c, cx + w * 0.16f, base + fract(t * 1.3f + 0.5f) * span, 13f);
    }

    // 5 — cloud + drifting flakes
    private void snow(Canvas c, float w, float h, float t) {
        float cx = w * 0.5f, cy = h * 0.3f, rr = w * 0.15f;
        cloud(c, cx, cy, rr, 0xFFC4CEDE, 0xFFDBE6F5, 0xFFC4CEDE, 0xFFD4E0F0, true);
        float base = cy + rr * 1.3f, span = h - base + 18f;
        p.setColor(Color.WHITE);
        flake(c, cx - w * 0.18f, base, span, fract(t * 0.45f), w);
        flake(c, cx + w * 0.02f, base, span, fract(t * 0.38f + 0.4f), w);
        flake(c, cx + w * 0.17f, base, span, fract(t * 0.5f + 0.7f), w);
    }

    private void flake(Canvas c, float x, float base, float span, float f, float w) {
        c.drawCircle(x + (float) Math.sin(f * 6.28f) * w * 0.03f, base + f * span, 4.5f, p);
    }

    // 6 — drifting gust lines
    private void wind(Canvas c, float w, float h, float t) {
        float sp = w + 80f;
        float x0 = fract(t * 0.35f) * sp - 40f;
        p.setColor(0xFFDFE9F5);
        box(c, x0 - w * 0.22f, h * 0.34f, w * 0.4f, 6f, 3f);
        ring(c, x0 + w * 0.18f, h * 0.34f + 3f, 9f, 0xFFDFE9F5);
        float x1 = fract(t * 0.5f + 0.4f) * sp - 40f;
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFC6D6EA);
        box(c, x1 - w * 0.26f, h * 0.52f, w * 0.46f, 6f, 3f);
        ring(c, x1 + w * 0.20f, h * 0.52f + 3f, 9f, 0xFFC6D6EA);
        float x2 = fract(t * 0.42f + 0.7f) * sp - 40f;
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFD2E0F0);
        box(c, x2 - w * 0.18f, h * 0.68f, w * 0.34f, 6f, 3f);
    }

    private void ring(Canvas c, float cx, float cy, float rad, int colour) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5f); p.setColor(colour);
        c.drawCircle(cx, cy, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    // 7 — cloud + drifting fog bars
    private void fog(Canvas c, float w, float h, float t) {
        float cx = w * 0.5f, cy = h * 0.28f, rr = w * 0.14f;
        cloud(c, cx, cy, rr, 0xFFB6BFCC, 0xFFC8D2DF, 0, 0xFFBFC9D6, false);
        float b = cy + rr * 1.5f;
        p.setColor(0xFFDBE6F5);
        box(c, cx - w * 0.28f + (float) Math.sin(t * 0.9f) * w * 0.05f, b, w * 0.5f, 6f, 3f);
        p.setColor(0xFFC6D2E2);
        box(c, cx - w * 0.22f + (float) Math.sin(t * 0.9f + 1.2f) * w * 0.05f, b + h * 0.13f, w * 0.44f, 6f, 3f);
        p.setColor(0xFFD0DCEC);
        box(c, cx - w * 0.26f + (float) Math.sin(t * 0.9f + 2.3f) * w * 0.05f, b + h * 0.26f, w * 0.48f, 6f, 3f);
    }
}
