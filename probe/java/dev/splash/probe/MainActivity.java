package dev.splash.probe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Splash UiNode -> real android.widget.* views.
 *
 * Validates docs/SPLASH-ANDROID-NATIVE-WIDGETS.md 7c:
 *  - Java owns every View (this SparseArray). Rust never holds a jobject.
 *  - Rust owns integer ids only.
 *  - One JNI crossing delivers the whole tree as a direct ByteBuffer.
 */
public class MainActivity extends Activity {

    static final String TAG = "SplashProbe";

    // Java owns the Views. This is the whole ownership model.
    private final SparseArray<View> views = new SparseArray<>();

    // kind codes, matching kind_code() in lib.rs
    static final int K_COLUMN=0, K_ROW=1, K_STACK=2, K_SCROLL=3, K_LIST=4, K_GRID=5,
        K_WATERFLOW=6, K_REFRESH=7, K_SWIPER=8, K_TEXT=9, K_IMAGE=10, K_BUTTON=11,
        K_TOGGLE=12, K_CHECKBOX=13, K_RADIO=14, K_SLIDER=15, K_PROGRESS=16,
        K_LOADING=17, K_INPUT=18, K_TEXTAREA=19, K_DATEPICKER=20, K_TIMEPICKER=21,
        K_TEXTPICKER=22;

    static final int A_TEXT=1, A_LABEL=2, A_PLACEHOLDER=3, A_W=4, A_H=5, A_SIZE=6,
        A_WEIGHT=7, A_COLOR=8, A_BG=9, A_PAD=10, A_VALUE=11, A_TOTAL=12, A_ON=13,
        A_TAP=14, A_SPACING=15;

    static final int T_F32=0, T_U32=1, T_I32=2, T_STR=3;

    float density;
    TextView status;
    final StringBuilder report = new StringBuilder();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        setContentView(root);

        String diag;
        try { diag = Native.diag(); } catch (Throwable t) { diag = "JNI diag failed: " + t; }
        Log.i(TAG, "diag: " + diag);
        report.append(diag).append('\n');

        ByteBuffer buf = null;
        try { buf = Native.buildOps(); } catch (Throwable t) {
            Log.e(TAG, "buildOps threw", t);
            report.append("buildOps threw: ").append(t).append('\n');
        }

        if (buf == null) {
            TextView tv = new TextView(this);
            tv.setText("buildOps returned null\n" + report);
            tv.setTextColor(Color.RED);
            root.addView(tv);
            return;
        }

        buf.order(ByteOrder.LITTLE_ENDIAN);
        try {
            View built = build(buf);
            if (built != null) root.addView(built,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                             ViewGroup.LayoutParams.MATCH_PARENT));
            Log.i(TAG, "REPORT\n" + report);
        } catch (Throwable t) {
            Log.e(TAG, "build failed", t);
            TextView tv = new TextView(this);
            tv.setText("build failed: " + t + "\n" + report);
            tv.setTextColor(Color.RED);
            root.addView(tv);
        }
    }

    int dp(float v) { return Math.round(v * density); }

    static class Attr { int id, ty; int a, b; }

    View build(ByteBuffer bb) {
        int magic = bb.getInt();
        int count = bb.getInt();
        int blobLen = bb.getInt();
        if (magic != 0x53504C31) throw new IllegalStateException("bad magic " + Integer.toHexString(magic));

        int nodesStart = bb.position();
        // string blob sits after the nodes; find it by scanning nodes once.
        // Simpler: blob is the LAST blobLen bytes of the buffer.
        int blobStart = bb.limit() - blobLen;
        byte[] blob = new byte[blobLen];
        int save = bb.position();
        bb.position(blobStart);
        bb.get(blob);
        bb.position(save);

        report.append("nodes=").append(count).append(" blob=").append(blobLen).append('\n');

        View root = null;
        int ok = 0, failed = 0;
        for (int i = 0; i < count; i++) {
            int id = bb.getInt();
            int parent = bb.getInt();
            int kind = bb.get() & 0xFF;
            int nattr = bb.get() & 0xFF;
            bb.getShort();

            Attr[] attrs = new Attr[nattr];
            for (int j = 0; j < nattr; j++) {
                Attr at = new Attr();
                at.id = bb.get() & 0xFF;
                at.ty = bb.get() & 0xFF;
                bb.getShort();
                at.a = bb.getInt();
                at.b = bb.getInt();
                attrs[j] = at;
            }

            View v;
            try {
                v = create(kind, attrs, blob);
            } catch (Throwable t) {
                report.append("  FAIL kind=").append(kind).append(' ').append(t).append('\n');
                failed++;
                continue;
            }
            if (v == null) { report.append("  UNMAPPED kind=").append(kind).append('\n'); failed++; continue; }
            ok++;
            views.put(id, v);

            ViewGroup.LayoutParams lp = layoutFor(kind, attrs, parent);
            v.setLayoutParams(lp);

            if (parent == -1) {
                root = v;
            } else {
                View p = views.get(parent);
                if (p instanceof ViewGroup) ((ViewGroup) p).addView(v);
                else report.append("  ORPHAN id=").append(id).append('\n');
            }
        }
        report.append("built ok=").append(ok).append(" failed=").append(failed).append('\n');
        return root;
    }

    ViewGroup.LayoutParams layoutFor(int kind, Attr[] attrs, int parent) {
        Float h = f(attrs, A_H);
        int hh = (h != null) ? dp(h) : ViewGroup.LayoutParams.WRAP_CONTENT;
        // containers that must fill
        if (kind == K_SCROLL) return new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, hh);
        Float sp = f(attrs, A_SPACING);
        return lp;
    }

    String str(Attr[] a, int id, byte[] blob) {
        for (Attr x : a) if (x.id == id && x.ty == T_STR) {
            try { return new String(blob, x.a, x.b, "UTF-8"); } catch (Exception e) { return ""; }
        }
        return null;
    }
    Float f(Attr[] a, int id) {
        for (Attr x : a) if (x.id == id && x.ty == T_F32) return Float.intBitsToFloat(x.a);
        return null;
    }
    Integer i32(Attr[] a, int id) {
        for (Attr x : a) if (x.id == id && x.ty == T_I32) return x.a;
        return null;
    }
    Integer u32(Attr[] a, int id) {
        for (Attr x : a) if (x.id == id && x.ty == T_U32) return x.a;
        return null;
    }

    View create(int kind, Attr[] a, byte[] blob) {
        String text = str(a, A_TEXT, blob);
        String label = str(a, A_LABEL, blob);
        String ph = str(a, A_PLACEHOLDER, blob);
        Float size = f(a, A_SIZE);
        Float pad = f(a, A_PAD);
        Float value = f(a, A_VALUE);
        Float total = f(a, A_TOTAL);
        Integer color = u32(a, A_COLOR);
        Integer bg = u32(a, A_BG);
        Integer on = i32(a, A_ON);
        Integer weight = i32(a, A_WEIGHT);

        View v = null;
        switch (kind) {
            case K_COLUMN: case K_LIST: {
                LinearLayout l = new LinearLayout(this);
                l.setOrientation(LinearLayout.VERTICAL);
                Float sp = f(a, A_SPACING);
                if (sp != null) l.setDividerPadding(dp(sp));
                v = l; break;
            }
            case K_ROW: {
                LinearLayout l = new LinearLayout(this);
                l.setOrientation(LinearLayout.HORIZONTAL);
                l.setGravity(Gravity.CENTER_VERTICAL);
                v = l; break;
            }
            case K_STACK: v = new FrameLayout(this); break;
            case K_SCROLL: v = new ScrollView(this); break;
            case K_GRID: v = new GridLayout(this); break;
            case K_TEXT: {
                TextView t = new TextView(this);
                if (text != null) t.setText(text);
                if (size != null) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
                if (weight != null && weight >= 6) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
                v = t; break;
            }
            case K_IMAGE: v = new ImageView(this); break;
            case K_BUTTON: {
                Button btn = new Button(this);
                btn.setText(label != null ? label : (text != null ? text : "Button"));
                btn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View x) { Log.i(TAG, "click -> would post to Rust"); }
                });
                v = btn; break;
            }
            case K_TOGGLE: {
                Switch s = new Switch(this);
                if (text != null) s.setText(text);
                if (on != null) s.setChecked(on != 0);
                v = s; break;
            }
            case K_CHECKBOX: {
                CheckBox c = new CheckBox(this);
                if (text != null) c.setText(text);
                if (on != null) c.setChecked(on != 0);
                v = c; break;
            }
            case K_RADIO: {
                RadioButton r = new RadioButton(this);
                if (text != null) r.setText(text);
                if (on != null) r.setChecked(on != 0);
                v = r; break;
            }
            case K_SLIDER: {
                SeekBar s = new SeekBar(this);
                if (total != null) s.setMax(Math.round(total));
                if (value != null) s.setProgress(Math.round(value));
                v = s; break;
            }
            case K_PROGRESS: {
                ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                p.setIndeterminate(false);
                if (total != null) p.setMax(Math.round(total));
                if (value != null) p.setProgress(Math.round(value));
                v = p; break;
            }
            case K_LOADING: {
                ProgressBar p = new ProgressBar(this);
                p.setIndeterminate(true);
                v = p; break;
            }
            case K_INPUT: case K_TEXTAREA: {
                EditText e = new EditText(this);
                if (ph != null) e.setHint(ph);
                if (kind == K_TEXTAREA) { e.setSingleLine(false); e.setLines(3); }
                v = e; break;
            }
            case K_DATEPICKER: v = new DatePicker(this); break;
            case K_TIMEPICKER: v = new TimePicker(this); break;
            case K_TEXTPICKER: {
                NumberPicker n = new NumberPicker(this);
                n.setMinValue(0); n.setMaxValue(4);
                n.setDisplayedValues(new String[]{"Alpha","Bravo","Charlie","Delta","Echo"});
                v = n; break;
            }
            default: return null; // androidx-only kinds land here
        }

        if (bg != null) v.setBackgroundColor(bg);
        if (color != null && v instanceof TextView) ((TextView) v).setTextColor(color);
        if (pad != null) { int p = dp(pad); v.setPadding(p, p, p, p); }
        return v;
    }
}
