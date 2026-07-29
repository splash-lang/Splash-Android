package dev.splash.catalog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.widget.FrameLayout;

/**
 * An Android port of octos-one's `glass.Panel` family
 * (aichat/widgets/src/glass_panel.rs — `AppleGlassRoundedView`).
 *
 * The makepad original is one MPSL shader with these uniforms; each maps onto a
 * Canvas pass here:
 *
 * | uniform | Android |
 * |---|---|
 * | `blur_level` | `RenderEffect.createBlurEffect` on API 31+, backdrop tint below |
 * | `tint_color` / `tint_alpha` / `surface_alpha` | the fill pass |
 * | `lensing_effect` / `_strength` / `_width` | the edge-refraction ring |
 * | `specular_strength` | the top-edge highlight sweep |
 * | `border_alpha` / `border_width` | the hairline stroke |
 * | `shadow_color` / `_radius` / `_offset` | `Paint.setShadowLayer` |
 * | `diffraction_strength` | the chromatic edge tint |
 *
 * Blur is the one uniform that needs API 31; everything else renders identically
 * on the SDK-30 test device, which is why the panel still reads as glass there.
 */
public class GlassPanelView extends FrameLayout {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float radius = 10f;
    private int tint = 0xFFF8FBFF;
    private float tintAlpha = 0.10f;      // the shader's tint_alpha is over a blurred backdrop
    private float borderAlpha = 0.72f;
    private float borderWidth = 1f;
    private float specular = 0.22f;
    private float lensStrength = 28f;
    private float lensWidth = 20f;
    private float diffraction = 4.4f;
    private int fallback = 0xFF334156;
    private float blurLevel = 5.2f;

    public GlassPanelView(Context c) {
        super(c);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** `variant`: panel | clear | nav | group | card | lens */
    public GlassPanelView variant(String v) {
        if (v == null) v = "panel";
        switch (v) {
            case "clear":                                   // glass.ClearPanel
                blurLevel = 5.4f; lensStrength = 34f; lensWidth = 18f;
                tintAlpha = 0.07f; borderAlpha = 0.84f; specular = 0.28f;
                fallback = 0xFF263242; diffraction = 5.4f; break;
            case "nav":                                     // glass.NavBar
                blurLevel = 5.4f; radius = 26f; tintAlpha = 0.08f;
                borderAlpha = 0.8f; specular = 0.26f; fallback = 0xFF263242; break;
            case "card":                                    // glass.Card
                radius = 18f; tintAlpha = 0.12f; specular = 0.20f; break;
            case "lens":                                    // glass.LensSurface
                radius = 999f; lensStrength = 36f; lensWidth = 22f;
                specular = 0.34f; diffraction = 6f; break;
            default: break;                                  // glass.Panel
        }
        applyBlur();
        return this;
    }

    public GlassPanelView radius(float dp) { radius = dp; return this; }

    private void applyBlur() {
        if (Build.VERSION.SDK_INT >= 31) {
            // The one uniform that needs a modern API. `blur_level` is a radius
            // in the shader's own units; scaled to px here.
            setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                blurLevel * 3f, blurLevel * 3f, Shader.TileMode.CLAMP));
        }
    }

    @Override protected void onDraw(Canvas c) {
        final float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        final float d = getResources().getDisplayMetrics().density;
        final float r = Math.min(radius * d, Math.min(w, h) * 0.5f);
        rect.set(0, 0, w, h);

        // --- surface: tinted glass over a fallback body ------------------
        fill.setShadowLayer(13f * d, 0, 5f * d, 0x77000000);   // shadow_* uniforms
        fill.setColor(blend(fallback, tint, tintAlpha));
        fill.setAlpha(Build.VERSION.SDK_INT >= 31 ? 150 : 225);
        c.drawRoundRect(rect, r, r, fill);
        fill.clearShadowLayer();

        // --- lensing: light bends at the rim, brightest near the edges ---
        lens.setStyle(Paint.Style.STROKE);
        lens.setStrokeWidth(lensWidth * 0.5f);
        lens.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.6f,
            new int[]{ 0x00FFFFFF, argb(lensStrength / 255f * 0.9f, 0xFFFFFF) },
            new float[]{ 0.72f, 1f }, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, lens);
        lens.setShader(null);

        // --- specular: a highlight sweep down from the top edge ----------
        spec.setShader(new LinearGradient(0, 0, 0, h * 0.55f,
            new int[]{ argb(specular, 0xFFFFFF), 0x00FFFFFF },
            null, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, spec);
        spec.setShader(null);

        // --- diffraction: a faint chromatic cast along the top rim -------
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(borderWidth * d * 2f);
        edge.setShader(new LinearGradient(0, 0, w, h,
            new int[]{ argb(diffraction / 40f, 0x8FD3FF),
                       argb(borderAlpha * 0.5f, 0xFFFFFF),
                       argb(diffraction / 40f, 0xFFC0E7) },
            null, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, edge);
        edge.setShader(null);

        // --- border hairline ---------------------------------------------
        edge.setStrokeWidth(borderWidth * d);
        edge.setColor(argb(borderAlpha * 0.55f, 0xFFFFFF));
        c.drawRoundRect(rect, r, r, edge);
    }

    private static int argb(float a, int rgb) {
        int A = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return (A << 24) | (rgb & 0xFFFFFF);
    }

    private static int blend(int base, int over, float amount) {
        float m = Math.max(0f, Math.min(1f, amount * 8f)); // tint_alpha is tiny by design
        return Color.rgb(
            Math.round(Color.red(base)   * (1 - m) + Color.red(over)   * m),
            Math.round(Color.green(base) * (1 - m) + Color.green(over) * m),
            Math.round(Color.blue(base)  * (1 - m) + Color.blue(over)  * m));
    }
}
