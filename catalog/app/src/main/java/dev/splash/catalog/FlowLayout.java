package dev.splash.catalog;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Minimal wrapping row. Chips and button groups need it and Material has no public one. */
public class FlowLayout extends ViewGroup {
    private int gap;

    public FlowLayout(Context c, int gapPx) { super(c); this.gap = gapPx; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int width = MeasureSpec.getSize(wSpec);
        int pl = getPaddingLeft(), pr = getPaddingRight(), pt = getPaddingTop(), pb = getPaddingBottom();
        int avail = width - pl - pr;
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            measureChild(ch, MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                              MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
            if (x > 0 && x + cw > avail) { x = 0; y += lineH + gap; lineH = 0; }
            x += cw + gap;
            lineH = Math.max(lineH, chh);
        }
        setMeasuredDimension(width, pt + pb + y + lineH);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int pl = getPaddingLeft(), pt = getPaddingTop();
        int avail = r - l - pl - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
            if (x > 0 && x + cw > avail) { x = 0; y += lineH + gap; lineH = 0; }
            ch.layout(pl + x, pt + y, pl + x + cw, pt + y + chh);
            x += cw + gap;
            lineH = Math.max(lineH, chh);
        }
    }
}
