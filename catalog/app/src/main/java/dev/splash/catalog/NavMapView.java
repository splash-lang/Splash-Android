package dev.splash.catalog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/**
 * An Android port of octos-one's `MapView` nav rendering
 * (aichat/widgets/src/map/view.rs).
 *
 * The makepad original projects ground-plane geometry in a vertex shader. This
 * is the SAME projection, evaluated on the CPU per vertex — a true pinhole
 * ground-plane camera, transcribed from the shader:
 *
 * <pre>
 *   rel   = world - anchor
 *   ahead = rel.x*rot.x - rel.y*rot.y      // heading-up frame
 *   cross = rel.x*rot.y + rel.y*rot.x
 *   a     = ahead + forwardOffset
 *   z_cam = a*cosP + h*sinP                // nav_cam = (h, sinP, cosP, tan(hfov/2))
 *   y_cam = a*sinP - h*cosP
 *   zc    = max(z_cam, h*0.6)              // near plane below nearest ground
 *   ndc   = (cross/(zc*tanH), y_cam/(zc*tanV))
 * </pre>
 *
 * Because x and y are rational functions of the shared depth, a ground-plane
 * segment maps to a straight screen segment — the property the original relies
 * on to avoid densification. Atmospheric haze uses the shader's own
 * {@code pow(clamp(a/far,0,1), 2.6) * 0.9}.
 *
 * `nav_mode`: 0 flat, 1 3D chase, 2 heading-up 2D — all three implemented.
 */
public class NavMapView extends View {

    // nav_cam / nav_misc equivalents
    // The horizon sits at ndc_y = tan(pitch)/tan(vfov/2); it is only ON SCREEN
    // while tan(pitch) < tan(vfov/2). A steeper pitch than the half-FOV pushes
    // it off the top and the ground appears to radiate from the bottom edge —
    // which is exactly what a first attempt at 58 deg / 32 deg produced.
    private float camH = 22f;                 // camera height, world units
    private float pitch = (float) Math.toRadians(22);
    private float tanH = (float) Math.tan(Math.toRadians(38));
    private float tanV = (float) Math.tan(Math.toRadians(30));
    private float forward = 30f;              // nav_misc.x
    private float far = 420f;                 // nav_misc.y — haze falloff
    private int mode = 1;

    private float bearing = 0f;               // radians; drives nav_rot
    private float travelled = 0f;             // metres along the route

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final long t0 = System.nanoTime();

    // --- the world -------------------------------------------------------
    private static final int GRID = 9;
    private static final float BLOCK = 120f;
    private final float[] route;              // x,y pairs
    private final float[][] pins;

    public NavMapView(Context c, int mode) {
        super(c);
        this.mode = mode;
        route = buildRoute();
        pins = new float[][]{ {route[0], route[1]}, {route[route.length-2], route[route.length-1]} };
    }

    private static float[] buildRoute() {
        // A drivable polyline that turns through the grid, like a real leg.
        float[] pts = {
            0,    -60,
            0,    120,
            120,  120,
            120,  360,
            360,  360,
            360,  600,
            600,  600,
        };
        return pts;
    }

    private float time() { return (System.nanoTime() - t0) / 1_000_000_000f; }

    // --- projection ------------------------------------------------------

    /** World -> heading-up frame (ahead, cross). */
    private void toFrame(float wx, float wy, float ax, float ay, float sinB, float cosB, float[] f) {
        float rx = wx - ax, ry = wy - ay;
        f[0] = rx * cosB - ry * sinB;   // ahead
        f[1] = rx * sinB + ry * cosB;   // cross
    }

    /** Depth for a frame-space `ahead` — linear, so it can be solved for the near plane. */
    private float zCamOf(float ahead) {
        return (ahead + forward) * (float) Math.cos(pitch) + camH * (float) Math.sin(pitch);
    }

    /** World (x,y) -> screen. Returns false when the vertex is behind the camera. */
    private boolean project(float wx, float wy, float ax, float ay, float sinB, float cosB, float[] out) {
        float rx = wx - ax, ry = wy - ay;
        float ahead = rx * cosB - ry * sinB;
        float cross = rx * sinB + ry * cosB;

        if (mode == 2) {                       // heading-up 2D
            out[0] = getWidth() * 0.5f + cross;
            out[1] = getHeight() * 0.62f - ahead;
            out[2] = 0f;
            return true;
        }
        if (mode == 0) {                       // flat, north-up
            out[0] = getWidth() * 0.5f + rx;
            out[1] = getHeight() * 0.5f - ry;
            out[2] = 0f;
            return true;
        }

        float sinP = (float) Math.sin(pitch), cosP = (float) Math.cos(pitch);
        float a = ahead + forward;
        float zCam = a * cosP + camH * sinP;
        float yCam = a * sinP - camH * cosP;
        float near3 = camH * 0.6f;
        boolean behind = zCam < near3;
        float zc = Math.max(zCam, near3);
        float ndcX = cross / (zc * tanH);
        float ndcY = yCam / (zc * tanV);
        if (behind) {
            out[0] = getWidth() * 0.5f + cross * 0.30f;
            out[1] = getHeight() * 1.6f;       // clean below-screen curtain
        } else {
            out[0] = getWidth() * 0.5f * (1f + ndcX);
            out[1] = getHeight() * 0.5f * (1f - ndcY);
        }
        float ht = Math.min(Math.max(a / far, 0f), 1f);
        out[2] = (float) Math.pow(ht, 2.6) * 0.9f;   // haze
        return !behind;
    }

    /** Project an already-framed point. */
    private void projectFrame(float ahead, float cross, float[] out) {
        float sinP = (float) Math.sin(pitch), cosP = (float) Math.cos(pitch);
        float a = ahead + forward;
        float zc = Math.max(a * cosP + camH * sinP, camH * 0.6f);
        float yCam = a * sinP - camH * cosP;
        out[0] = getWidth() * 0.5f * (1f + cross / (zc * tanH));
        out[1] = getHeight() * 0.5f * (1f - yCam / (zc * tanV));
        float ht = Math.min(Math.max(a / far, 0f), 1f);
        out[2] = (float) Math.pow(ht, 2.6) * 0.9f;
    }

    private static int haze(int colour, float t, int horizon) {
        float m = Math.min(Math.max(t, 0f), 1f);
        return Color.rgb(
            Math.round(Color.red(colour)   * (1 - m) + Color.red(horizon)   * m),
            Math.round(Color.green(colour) * (1 - m) + Color.green(horizon) * m),
            Math.round(Color.blue(colour)  * (1 - m) + Color.blue(horizon)  * m));
    }

    // --- drawing ---------------------------------------------------------

    private final float[] pa = new float[3], pb = new float[3];

    @Override protected void onDraw(Canvas c) {
        final int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float t = time();
        travelled = (t * 26f) % routeLength();
        float[] pos = along(travelled);
        float[] nxt = along(Math.min(travelled + 8f, routeLength() - 0.01f));
        bearing = (float) Math.atan2(nxt[0] - pos[0], nxt[1] - pos[1]);
        float sinB = (float) Math.sin(bearing), cosB = (float) Math.cos(bearing);

        final int GROUND = 0xFF12141A, HORIZON = 0xFF2A3550, ROAD = 0xFF3A4152;

        // The vanishing line: ndc_y -> tan(pitch)/tan(vfov/2) as distance -> inf.
        float horizonY = mode == 1
            ? h * 0.5f * (1f - (float) Math.tan(pitch) / tanV)
            : -1f;

        p.setStyle(Paint.Style.FILL);
        if (mode == 1) {
            p.setShader(new LinearGradient(0, 0, 0, horizonY,
                new int[]{ 0xFF0B1020, HORIZON }, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, horizonY, p);
            p.setShader(null);
        }
        p.setColor(GROUND);
        c.drawRect(0, mode == 1 ? horizonY : 0, w, h, p);
        if (mode == 1) {                      // haze band hugging the vanishing line
            p.setShader(new LinearGradient(0, horizonY, 0, horizonY + h * 0.22f,
                new int[]{ HORIZON, 0x0012141A }, null, Shader.TileMode.CLAMP));
            c.drawRect(0, horizonY, w, horizonY + h * 0.22f, p);
            p.setShader(null);
        }

        // --- street grid, projected ---
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = -GRID; i <= GRID; i++) {
            float o = i * BLOCK;
            strokeWorld(c, o, -GRID * BLOCK, o, GRID * BLOCK, pos[0], pos[1], sinB, cosB, ROAD, HORIZON, 9f);
            strokeWorld(c, -GRID * BLOCK, o, GRID * BLOCK, o, pos[0], pos[1], sinB, cosB, ROAD, HORIZON, 9f);
        }

        // --- route ribbon (world-space, so it lies on the ground) ---
        drawRibbon(c, pos[0], pos[1], sinB, cosB, HORIZON);

        // --- standing pins ---
        for (float[] pin : pins) {
            if (project(pin[0], pin[1], pos[0], pos[1], sinB, cosB, pa)) drawPin(c, pa[0], pa[1], pa[2]);
        }

        // --- vehicle puck: screen space, always crisp ---
        drawPuck(c, w * 0.5f, mode == 1 ? h * 0.72f : h * 0.62f);

        postInvalidateOnAnimation();
    }

    private final float[] fa = new float[2], fb = new float[2];

    /**
     * Draw a ground segment with TRUE near-plane clipping.
     *
     * Snapping a behind-camera vertex to an off-screen "curtain" (what the
     * shader does, because a GPU cannot drop a vertex) streaks a line right
     * across the horizon when only one end is behind. On the CPU the segment can
     * simply be cut at z_cam = near, which is what a straight ground line
     * actually looks like.
     */
    private void strokeWorld(Canvas c, float x0, float y0, float x1, float y1,
                             float ax, float ay, float sinB, float cosB,
                             int colour, int horizon, float width) {
        if (mode != 1) {                       // 2D paths need no clipping
            boolean va = project(x0, y0, ax, ay, sinB, cosB, pa);
            boolean vb = project(x1, y1, ax, ay, sinB, cosB, pb);
            if (!va && !vb) return;
            p.setColor(colour);
            p.setStrokeWidth(width);
            c.drawLine(pa[0], pa[1], pb[0], pb[1], p);
            return;
        }
        toFrame(x0, y0, ax, ay, sinB, cosB, fa);
        toFrame(x1, y1, ax, ay, sinB, cosB, fb);
        float near = camH * 0.62f;
        float za = zCamOf(fa[0]), zb = zCamOf(fb[0]);
        if (za < near && zb < near) return;                 // wholly behind
        if (za < near || zb < near) {                        // crossing: cut it
            float t = (near - za) / (zb - za);
            float ah = fa[0] + (fb[0] - fa[0]) * t;
            float cr = fa[1] + (fb[1] - fa[1]) * t;
            if (za < near) { fa[0] = ah; fa[1] = cr; } else { fb[0] = ah; fb[1] = cr; }
        }
        projectFrame(fa[0], fa[1], pa);
        projectFrame(fb[0], fb[1], pb);
        float ht = (pa[2] + pb[2]) * 0.5f;
        if (ht > 0.97f) return;
        p.setColor(haze(colour, ht, horizon));
        p.setStrokeWidth(Math.max(1f, width * (1f - ht * 0.75f)));
        c.drawLine(pa[0], pa[1], pb[0], pb[1], p);
    }

    private void drawRibbon(Canvas c, float ax, float ay, float sinB, float cosB, int horizon) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (int pass = 0; pass < 2; pass++) {           // casing, then fill
            path.reset();
            boolean started = false;
            float lastHaze = 0f;
            float near = camH * 0.62f;
            for (int i = 0; i + 1 < route.length; i += 2) {
                toFrame(route[i], route[i + 1], ax, ay, sinB, cosB, fa);
                if (mode == 1 && zCamOf(fa[0]) < near) { started = false; continue; }
                if (mode == 1) projectFrame(fa[0], fa[1], pa);
                else project(route[i], route[i + 1], ax, ay, sinB, cosB, pa);
                lastHaze = pa[2];
                if (!started) { path.moveTo(pa[0], pa[1]); started = true; }
                else path.lineTo(pa[0], pa[1]);
            }
            p.setColor(pass == 0 ? haze(0xFF0E2A55, lastHaze, horizon)
                                 : haze(0xFF4C8DFF, lastHaze, horizon));
            p.setStrokeWidth(pass == 0 ? 34f : 24f);
            c.drawPath(path, p);
        }
    }

    private void drawPin(Canvas c, float x, float y, float ht) {
        if (ht > 0.95f) return;
        float s = 1f - ht * 0.6f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawOval(x - 9 * s, y - 4 * s, x + 9 * s, y + 4 * s, p);   // ground shadow
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x - 11 * s, y - 26 * s);
        path.lineTo(x + 11 * s, y - 26 * s);
        path.close();
        p.setColor(0xFFE0453B);
        c.drawPath(path, p);
        c.drawCircle(x, y - 32 * s, 13 * s, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x, y - 32 * s, 5.5f * s, p);
    }

    private void drawPuck(Canvas c, float x, float y) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x334C8DFF);
        c.drawCircle(x, y, 34f, p);                      // accuracy halo
        p.setColor(0xFF1A73E8);
        c.drawCircle(x, y, 17f, p);
        p.setColor(0xFFFFFFFF);
        path.reset();                                    // heading chevron
        path.moveTo(x, y - 11);
        path.lineTo(x - 7.5f, y + 7);
        path.lineTo(x, y + 3);
        path.lineTo(x + 7.5f, y + 7);
        path.close();
        c.drawPath(path, p);
    }

    // --- route helpers ---------------------------------------------------

    private float routeLength() {
        float d = 0;
        for (int i = 0; i + 3 < route.length; i += 2) d += seg(i);
        return d;
    }

    private float seg(int i) {
        float dx = route[i + 2] - route[i], dy = route[i + 3] - route[i + 1];
        return (float) Math.hypot(dx, dy);
    }

    /** Point at distance `d` along the route. */
    private float[] along(float d) {
        for (int i = 0; i + 3 < route.length; i += 2) {
            float l = seg(i);
            if (d <= l) {
                float f = l == 0 ? 0 : d / l;
                return new float[]{ route[i] + (route[i + 2] - route[i]) * f,
                                    route[i + 1] + (route[i + 3] - route[i + 1]) * f };
            }
            d -= l;
        }
        return new float[]{ route[route.length - 2], route[route.length - 1] };
    }
}
