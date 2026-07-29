package dev.splash.catalog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PaintDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

/** Splash node tree -> real Material Components views. */
public class Builder {

    public interface Env {
        /** A widget changed state: persist and (optionally) re-render. */
        void onState(String key, String value, boolean rerender);
        /** A tap that isn't a state change — dialogs, sheets, pickers, nav. */
        void onAction(Node n, View v);
    }

    final Context ctx;
    final Env env;
    final float d;

    public Builder(Context c, Env e) {
        ctx = c; env = e;
        d = c.getResources().getDisplayMetrics().density;
    }

    int dp(float v) { return Math.round(v * d); }

    // ------------------------------------------------------------ icons ----

    /// Only genuine synonyms remain: every icon the DSL names now exists as a
    /// real vector in res/drawable (the catalog's own set plus the Material
    /// Symbols authored for this app). No approximations.
    static final Map<String, String> ALIAS = new HashMap<>();
    static {
        ALIAS.put("person", "account_circle");
        ALIAS.put("mail", "mail_outline");
        ALIAS.put("inbox", "mail_outline");
        ALIAS.put("info", "info_outline");
        ALIAS.put("refresh", "refresh_black");
        ALIAS.put("attach_money", "payments");
        ALIAS.put("play_arrow", "play");
    }

    public Drawable icon(String name) {
        if (name == null || name.isEmpty()) return null;
        String n = ALIAS.containsKey(name) ? ALIAS.get(name) : name;
        int id = ctx.getResources().getIdentifier("ic_" + n, "drawable", ctx.getPackageName());
        if (id == 0) id = ctx.getResources().getIdentifier("ic_" + name, "drawable", ctx.getPackageName());
        return id == 0 ? null : ctx.getDrawable(id);
    }

    int attrColor(int attr) { return MaterialColors.getColor(ctx, attr, Color.MAGENTA); }

    int roleColor(String role) {
        if (role == null) return attrColor(R.attr.colorPrimary);
        switch (role) {
            case "primary": return attrColor(R.attr.colorPrimary);
            case "onPrimary": return attrColor(R.attr.colorOnPrimary);
            case "primaryContainer": return attrColor(R.attr.colorPrimaryContainer);
            case "onPrimaryContainer": return attrColor(R.attr.colorOnPrimaryContainer);
            case "secondary": return attrColor(R.attr.colorSecondary);
            case "onSecondary": return attrColor(R.attr.colorOnSecondary);
            case "secondaryContainer": return attrColor(R.attr.colorSecondaryContainer);
            case "onSecondaryContainer": return attrColor(R.attr.colorOnSecondaryContainer);
            case "tertiary": return attrColor(R.attr.colorTertiary);
            case "onTertiary": return attrColor(R.attr.colorOnTertiary);
            case "tertiaryContainer": return attrColor(R.attr.colorTertiaryContainer);
            case "onTertiaryContainer": return attrColor(R.attr.colorOnTertiaryContainer);
            case "error": return attrColor(R.attr.colorError);
            case "onError": return attrColor(R.attr.colorOnError);
            case "errorContainer": return attrColor(R.attr.colorErrorContainer);
            case "onErrorContainer": return attrColor(R.attr.colorOnErrorContainer);
            case "surface": return attrColor(R.attr.colorSurface);
            case "onSurface": return attrColor(R.attr.colorOnSurface);
            case "surfaceVariant": return attrColor(R.attr.colorSurfaceVariant);
            case "onSurfaceVariant": return attrColor(R.attr.colorOnSurfaceVariant);
            case "outline": return attrColor(R.attr.colorOutline);
        }
        return attrColor(R.attr.colorPrimary);
    }

    static int textAppearance(String v) {
        if (v == null) v = "bodyMedium";
        switch (v) {
            case "displayLarge":  return R.style.TextAppearance_Material3_DisplayLarge;
            case "displayMedium": return R.style.TextAppearance_Material3_DisplayMedium;
            case "displaySmall":  return R.style.TextAppearance_Material3_DisplaySmall;
            case "headlineLarge": return R.style.TextAppearance_Material3_HeadlineLarge;
            case "headlineMedium":return R.style.TextAppearance_Material3_HeadlineMedium;
            case "headlineSmall": return R.style.TextAppearance_Material3_HeadlineSmall;
            case "titleLarge":    return R.style.TextAppearance_Material3_TitleLarge;
            case "titleMedium":   return R.style.TextAppearance_Material3_TitleMedium;
            case "titleSmall":    return R.style.TextAppearance_Material3_TitleSmall;
            case "bodyLarge":     return R.style.TextAppearance_Material3_BodyLarge;
            case "bodySmall":     return R.style.TextAppearance_Material3_BodySmall;
            case "labelLarge":    return R.style.TextAppearance_Material3_LabelLarge;
            case "labelMedium":   return R.style.TextAppearance_Material3_LabelMedium;
            case "labelSmall":    return R.style.TextAppearance_Material3_LabelSmall;
            default:              return R.style.TextAppearance_Material3_BodyMedium;
        }
    }

    static String[] split(String s) { return (s == null || s.isEmpty()) ? new String[0] : s.split(";", -1); }
    static String[] csv(String s) { return (s == null || s.isEmpty()) ? new String[0] : s.split(",", -1); }

    // ------------------------------------------------------------ build ----

    public View build(Node n) {
        View v = create(n);
        if (v == null) return null;
        applyCommon(n, v);
        return v;
    }

    void addChildren(Node n, ViewGroup g, int spacingPx, boolean vertical) {
        for (int i = 0; i < n.children.size(); i++) {
            Node c = n.children.get(i);
            View cv = build(c);
            if (cv == null) continue;
            ViewGroup.LayoutParams lp = cv.getLayoutParams();
            if (g instanceof LinearLayout) {
                LinearLayout.LayoutParams l = lp instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) lp
                    : new LinearLayout.LayoutParams(
                        vertical ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (c.has("w")) l.width = c.i("w", 0) >= 999 ? ViewGroup.LayoutParams.MATCH_PARENT : dp(c.f("w", 0));
                if (c.has("h")) l.height = dp(c.f("h", 0));
                if (i > 0 && spacingPx > 0) { if (vertical) l.topMargin = spacingPx; else l.leftMargin = spacingPx; }
                if ("spacer".equals(c.kind)) {
                    // Take weight along the parent's axis only — a spacer that
                    // also stretched across the cross axis swallowed the rest of
                    // the card (verified on device: it grew to 682px tall).
                    l.weight = 1f;
                    if (vertical) { l.height = 0; l.width = ViewGroup.LayoutParams.MATCH_PARENT; }
                    else { l.width = 0; l.height = dp(1); }
                }
                cv.setLayoutParams(l);
            }
            g.addView(cv);
        }
    }

    void applyCommon(Node n, View v) {
        int px = n.has("padx") ? dp(n.f("padx", 0)) : (n.has("pad") ? dp(n.f("pad", 0)) : -1);
        int py = n.has("pady") ? dp(n.f("pady", 0)) : (n.has("pad") ? dp(n.f("pad", 0)) : -1);
        if (px >= 0 || py >= 0) {
            int l = px >= 0 ? px : v.getPaddingLeft();
            int t = py >= 0 ? py : v.getPaddingTop();
            v.setPadding(l, t, px >= 0 ? px : v.getPaddingRight(), py >= 0 ? py : v.getPaddingBottom());
        }
        if (n.has("enabled")) v.setEnabled(n.b("enabled", true));
    }

    @SuppressWarnings("deprecation")
    View create(Node n) {
        String k = n.kind;
        switch (k) {
            // ---------------------------------------------------- layout --
            case "col": {
                LinearLayout l = new LinearLayout(ctx);
                l.setOrientation(LinearLayout.VERTICAL);
                if (n.i("align", 0) == 1) l.setGravity(Gravity.CENTER_HORIZONTAL);
                addChildren(n, l, dp(n.f("spacing", 0)), true);
                return l;
            }
            case "row": {
                LinearLayout l = new LinearLayout(ctx);
                l.setOrientation(LinearLayout.HORIZONTAL);
                l.setGravity(n.i("align", 0) == 1 ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
                addChildren(n, l, dp(n.f("spacing", 0)), false);
                return l;
            }
            case "flow": {
                FlowLayout f = new FlowLayout(ctx, dp(n.f("spacing", 8)));
                for (Node c : n.children) { View cv = build(c); if (cv != null) f.addView(cv); }
                return f;
            }
            case "box": {
                FrameLayout f = new FrameLayout(ctx);
                for (Node c : n.children) { View cv = build(c); if (cv != null) f.addView(cv); }
                return f;
            }
            case "scroll": {
                ScrollView s = new ScrollView(ctx);
                s.setFillViewport(true);
                s.setClipToPadding(false);
                for (Node c : n.children) { View cv = build(c); if (cv != null) s.addView(cv); }
                return s;
            }
            case "spacer": return new View(ctx);

            // ------------------------------------------------------ text --
            case "text": {
                AppCompatTextView t = new AppCompatTextView(ctx);
                TextViewCompat.setTextAppearance(t, textAppearance(n.s("variant")));
                t.setText(n.s("text", ""));
                t.setTextColor(attrColor(n.has("color")
                    ? R.attr.colorOnSurface
                    : R.attr.colorOnSurface));
                String var = n.s("variant", "");
                if (var.startsWith("label") || var.startsWith("body") && var.endsWith("Small"))
                    t.setTextColor(attrColor(R.attr.colorOnSurfaceVariant));
                if (n.has("marginy") || n.has("pady")) {
                    int m = dp(n.f("marginy", n.f("pady", 0)));
                    t.setPadding(0, m, 0, m);
                }
                return t;
            }
            case "divider": {
                if ("vertical".equals(n.s("variant"))) {
                    MaterialDivider dv = new MaterialDivider(ctx);
                    dv.setLayoutParams(new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT));
                    return dv;
                }
                MaterialDivider dv = new MaterialDivider(ctx);
                if (n.has("marginx")) { dv.setDividerInsetStart(dp(n.f("marginx", 0))); dv.setDividerInsetEnd(dp(n.f("marginx", 0))); }
                return dv;
            }

            // --------------------------------------------------- buttons --
            case "button": {
                MaterialButton b = new MaterialButton(ctx, null, buttonStyleAttr(n.s("variant", "filled")));
                b.setText(n.s("label", n.s("text", "Button")));
                Drawable ic = icon(n.s("icon"));
                if (ic != null) { b.setIcon(ic); b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START); }
                b.setEnabled(n.b("enabled", true));
                b.setOnClickListener(v -> env.onAction(n, v));
                return b;
            }
            case "iconbutton": {
                MaterialButton b = new MaterialButton(ctx, null, iconButtonStyleAttr(n.s("variant", "standard")));
                b.setIcon(icon(n.s("icon")));
                b.setEnabled(n.b("enabled", true));
                if (n.b("checkable", false)) {
                    b.setCheckable(true);
                    b.setChecked(n.b("on", false));
                    b.addOnCheckedChangeListener((btn, checked) ->
                        env.onState(n.s("key", ""), checked ? "1" : "0", false));
                } else {
                    b.setOnClickListener(v -> env.onAction(n, v));
                }
                return b;
            }
            case "fab": {
                String var = n.s("variant", "regular");
                if ("extended".equals(var)) {
                    ExtendedFloatingActionButton f = new ExtendedFloatingActionButton(ctx);
                    f.setText(n.s("label", "Extended"));
                    Drawable ic = icon(n.s("icon"));
                    if (ic != null) f.setIcon(ic);
                    tintFab(f, n.s("group"));
                    f.setOnClickListener(v -> env.onAction(n, v));
                    return f;
                }
                FloatingActionButton f = new FloatingActionButton(ctx);
                f.setImageDrawable(icon(n.s("icon", "add")));
                if ("small".equals(var)) f.setSize(FloatingActionButton.SIZE_MINI);
                else f.setSize(FloatingActionButton.SIZE_NORMAL);
                if ("large".equals(var)) f.setCustomSize(dp(96));
                tintFab(f, n.s("group"));
                f.setOnClickListener(v -> env.onAction(n, v));
                return f;
            }
            case "segmented": {
                MaterialButtonToggleGroup g = new MaterialButtonToggleGroup(ctx);
                g.setSingleSelection(true);
                g.setSelectionRequired(true);
                String[] items = split(n.s("items"));
                int sel = n.i("selected", 0);
                for (int i = 0; i < items.length; i++) {
                    MaterialButton b = new MaterialButton(ctx, null,
                        R.attr.materialButtonOutlinedStyle);
                    b.setText(items[i]);
                    b.setId(View.generateViewId());
                    g.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    if (i == sel) g.check(b.getId());
                }
                g.addOnButtonCheckedListener((grp, id, checked) -> {
                    if (!checked) return;
                    for (int i = 0; i < grp.getChildCount(); i++)
                        if (grp.getChildAt(i).getId() == id) env.onState(n.s("key", ""), String.valueOf(i), true);
                });
                return g;
            }

            // ------------------------------------------------- selection --
            case "checkbox": {
                MaterialCheckBox c = new MaterialCheckBox(ctx);
                c.setText(n.s("text", ""));
                c.setEnabled(n.b("enabled", true));
                if (n.b("indeterminate", false)) {
                    c.setCheckedState(MaterialCheckBox.STATE_INDETERMINATE);
                } else {
                    c.setChecked(n.b("on", false));
                }
                if (n.has("error")) c.setErrorShown(true);
                c.setOnCheckedChangeListener((bv, ch) -> env.onState(n.s("key", ""), ch ? "1" : "0", false));
                return c;
            }
            case "radio": {
                MaterialRadioButton r = new MaterialRadioButton(ctx);
                r.setText(n.s("text", ""));
                r.setEnabled(n.b("enabled", true));
                r.setChecked(n.b("on", false));
                return r;
            }
            case "radiogroup": {
                RadioGroup rg = new RadioGroup(ctx);
                String[] items = split(n.s("items"));
                int sel = n.i("selected", 0);
                for (int i = 0; i < items.length; i++) {
                    MaterialRadioButton r = new MaterialRadioButton(ctx);
                    r.setText(items[i]);
                    r.setId(View.generateViewId());
                    rg.addView(r);
                    if (i == sel) rg.check(r.getId());
                }
                rg.setOnCheckedChangeListener((grp, id) -> {
                    for (int i = 0; i < grp.getChildCount(); i++)
                        if (grp.getChildAt(i).getId() == id) env.onState(n.s("key", ""), String.valueOf(i), false);
                });
                return rg;
            }
            case "switch": {
                MaterialSwitch s = new MaterialSwitch(ctx);
                s.setText(n.s("text", ""));
                s.setEnabled(n.b("enabled", true));
                s.setChecked(n.b("on", false));
                Drawable ic = icon(n.s("icon"));
                if (ic != null) s.setThumbIconDrawable(ic);
                s.setOnCheckedChangeListener((bv, ch) -> env.onState(n.s("key", ""), ch ? "1" : "0", false));
                return s;
            }
            case "chip": {
                Chip c = new Chip(ctx);
                String var = n.s("variant", "assist");
                c.setChipDrawable(com.google.android.material.chip.ChipDrawable.createFromAttributes(
                    ctx, null, 0, chipStyle(var)));
                c.setText(n.s("text", ""));
                c.setEnabled(n.b("enabled", true));
                Drawable ic = icon(n.s("icon"));
                if (ic != null) { c.setChipIcon(ic); c.setChipIconVisible(true); }
                if ("filter".equals(var)) {
                    c.setCheckable(true);
                    c.setChecked(n.b("on", false));
                    c.setOnCheckedChangeListener((bv, ch) -> env.onState(n.s("key", ""), ch ? "1" : "0", false));
                } else if (n.b("closeable", false)) {
                    c.setCloseIconVisible(true);
                    c.setOnCloseIconClickListener(v -> env.onAction(n, v));
                } else {
                    c.setOnClickListener(v -> env.onAction(n, v));
                }
                return c;
            }

            // ----------------------------------------------------- input --
            case "textfield": {
                boolean outlined = "outlined".equals(n.s("variant", "filled"));
                TextInputLayout til = new TextInputLayout(ctx, null, outlined
                    ? R.attr.textInputOutlinedStyle
                    : R.attr.textInputFilledStyle);
                til.setHint(n.s("hint", ""));
                if (n.has("helper")) { til.setHelperTextEnabled(true); til.setHelperText(n.s("helper")); }
                if (n.has("error")) { til.setErrorEnabled(true); til.setError(n.s("error")); }
                Drawable ic = icon(n.s("icon"));
                if (ic != null) til.setStartIconDrawable(ic);
                String act = n.s("action", "");
                if ("password".equals(act)) til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                TextInputEditText et = new TextInputEditText(til.getContext());
                if ("password".equals(act)) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                else if ("number".equals(act)) et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                int lines = n.i("lines", 1);
                if (lines > 1) { et.setSingleLine(false); et.setMinLines(lines); et.setGravity(Gravity.TOP | Gravity.START); }
                et.setText(n.s("text", ""));
                et.setEnabled(n.b("enabled", true));
                til.setEnabled(n.b("enabled", true));
                et.addTextChangedListener(new SimpleWatcher(s -> env.onState(n.s("key", ""), s, false)));
                til.addView(et);
                return til;
            }
            case "dropdown": {
                TextInputLayout til = new TextInputLayout(ctx, null,
                    "editable".equals(n.s("action", ""))
                        ? R.attr.textInputOutlinedExposedDropdownMenuStyle
                        : R.attr.textInputOutlinedExposedDropdownMenuStyle);
                til.setHint(n.s("hint", ""));
                MaterialAutoCompleteTextView av = new MaterialAutoCompleteTextView(til.getContext());
                String[] items = split(n.s("items"));
                av.setSimpleItems(items);
                if (!"editable".equals(n.s("action", ""))) { av.setInputType(0); av.setKeyListener(null); }
                av.setText(n.s("text", ""), false);
                av.setOnItemClickListener((p, vv, pos, id) ->
                    env.onState(n.s("key", ""), items.length > pos ? items[pos] : "", false));
                til.addView(av);
                return til;
            }
            case "slider": {
                Slider s = new Slider(ctx);
                float min = n.f("min", 0), max = n.f("max", 100);
                s.setValueFrom(min); s.setValueTo(max);
                if (n.has("step")) s.setStepSize(n.f("step", 1));
                float val = Math.max(min, Math.min(max, n.f("value", min)));
                s.setValue(val);
                s.setEnabled(n.b("enabled", true));
                s.addOnChangeListener((sl, value, fromUser) -> {
                    if (fromUser) env.onState(n.s("key", ""), String.valueOf((int) value), false);
                });
                s.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                    @Override public void onStartTrackingTouch(Slider sl) {}
                    @Override public void onStopTrackingTouch(Slider sl) {
                        env.onState(n.s("key", ""), String.valueOf((int) sl.getValue()), true);
                    }
                });
                return s;
            }
            case "rangeslider": {
                RangeSlider s = new RangeSlider(ctx);
                s.setValueFrom(n.f("min", 0));
                s.setValueTo(n.f("max", 100));
                s.setValues(n.f("value", 20), n.f("value2", 80));
                return s;
            }

            // -------------------------------------------------- progress --
            case "progress": {
                if ("circular".equals(n.s("variant", "linear"))) {
                    CircularProgressIndicator p = new CircularProgressIndicator(ctx);
                    if (n.b("indeterminate", false)) p.setIndeterminate(true);
                    else { p.setMax((int) n.f("total", 100)); p.setProgressCompat((int) n.f("value", 0), true); }
                    return p;
                }
                LinearProgressIndicator p = new LinearProgressIndicator(ctx);
                if (n.b("indeterminate", false)) p.setIndeterminate(true);
                else { p.setMax((int) n.f("total", 100)); p.setProgressCompat((int) n.f("value", 0), true); }
                return p;
            }
            case "loading": {
                CircularProgressIndicator p = new CircularProgressIndicator(ctx);
                p.setIndeterminate(true);
                if ("contained".equals(n.s("variant", ""))) {
                    p.setTrackThickness(dp(6));
                    p.setIndicatorSize(dp(48));
                }
                return p;
            }

            // ------------------------------------------------ containers --
            case "card": {
                MaterialCardView c = new MaterialCardView(ctx, null, cardStyle(n.s("variant", "elevated")));
                if (n.has("elevation")) c.setCardElevation(dp(n.f("elevation", 1)));
                if (n.b("checkable", false)) {
                    c.setCheckable(true);
                    c.setChecked(n.b("on", false));
                    c.setOnClickListener(v -> {
                        c.setChecked(!c.isChecked());
                        env.onState(n.s("key", ""), c.isChecked() ? "1" : "0", false);
                    });
                } else if (n.b("tap", false)) {
                    c.setClickable(true);
                    c.setOnClickListener(v -> env.onAction(n, v));
                }
                for (Node ch : n.children) { View cv = build(ch); if (cv != null) c.addView(cv); }
                return c;
            }
            case "listitem": return listItem(n);

            // ------------------------------------------------ navigation --
            case "tabs": {
                TabLayout t = new TabLayout(ctx);
                t.setTabMode(n.b("scrollable", false) ? TabLayout.MODE_SCROLLABLE : TabLayout.MODE_FIXED);
                String[] items = split(n.s("items"));
                String[] icons = csv(n.s("icon"));
                String[] badges = csv(n.s("badge"));
                for (int i = 0; i < items.length; i++) {
                    TabLayout.Tab tab = t.newTab().setText(items[i]);
                    if (i < icons.length && !icons[i].isEmpty()) tab.setIcon(icon(icons[i]));
                    t.addTab(tab);
                    if (i < badges.length && !badges[i].isEmpty()) {
                        try { tab.getOrCreateBadge().setNumber(Integer.parseInt(badges[i])); } catch (Exception ignored) {}
                    }
                }
                int sel = n.i("selected", 0);
                if (sel < t.getTabCount()) t.selectTab(t.getTabAt(sel));
                t.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                    @Override public void onTabSelected(TabLayout.Tab tab) {
                        env.onState(n.s("key", ""), String.valueOf(tab.getPosition()), true);
                    }
                    @Override public void onTabUnselected(TabLayout.Tab tab) {}
                    @Override public void onTabReselected(TabLayout.Tab tab) {}
                });
                return t;
            }
            case "navbar": {
                BottomNavigationView b = new BottomNavigationView(ctx);
                fillNav(b, n);
                return b;
            }
            case "navrail": {
                NavigationRailView r = new NavigationRailView(ctx);
                fillNav(r, n);
                r.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return r;
            }
            case "badgeicon": {
                // A real BadgeDrawable, attached once the host view is laid out.
                FrameLayout f = new FrameLayout(ctx);
                ImageView iv = new ImageView(ctx);
                iv.setImageDrawable(icon(n.s("icon", "star")));
                iv.setImageTintList(ColorStateList.valueOf(attrColor(R.attr.colorOnSurface)));
                int s = dp(32);
                f.addView(iv, new FrameLayout.LayoutParams(s, s));
                final String badge = n.s("badge", "");
                f.post(() -> {
                    try {
                        com.google.android.material.badge.BadgeDrawable bd =
                            com.google.android.material.badge.BadgeDrawable.create(ctx);
                        if (!badge.isEmpty()) {
                            bd.setNumber(Integer.parseInt(badge));
                            bd.setMaxCharacterCount(4);
                        }
                        com.google.android.material.badge.BadgeUtils
                            .attachBadgeDrawable(bd, iv, null);
                    } catch (Throwable ignored) {}
                });
                return f;
            }

            // ------------------------------------------------- demo hosts --
            case "carousel":      return carousel(n);
            case "searchbar":     return searchBar(n);
            case "appbardemo":    return appBarDemo(n);
            case "bottombardemo": return bottomBarDemo(n);
            case "toolbardemo":   return toolbarDemo(n);
            case "adaptivedemo":  return adaptiveDemo(n);
            case "transitionhost": return transitionHost(n);

            // -------------------------- octos-one's own makepad widgets ----
            // Ported to real Android views; see WeatherIconView / GlassPanelView
            // / NavMapView. These were previously documented as having "no
            // android.widget equivalent" — they do, and this is it.
            case "weathericon": {
                WeatherIconView v = new WeatherIconView(ctx, n.i("value", 0));
                int sz = dp(n.f("w", 96));
                v.setLayoutParams(new ViewGroup.LayoutParams(sz, sz));
                return v;
            }
            case "glasspanel": {
                GlassPanelView g = new GlassPanelView(ctx).variant(n.s("variant", "panel"));
                if (n.has("radius")) g.radius(n.f("radius", 10));
                g.setPadding(dp(16), dp(16), dp(16), dp(16));
                // GlassPanelView is a FrameLayout (it needs one for the blur
                // backdrop), so children go into a column rather than stacking.
                LinearLayout gcol = new LinearLayout(ctx);
                gcol.setOrientation(LinearLayout.VERTICAL);
                for (Node ch : n.children) { View cv = build(ch); if (cv != null) gcol.addView(cv); }
                g.addView(gcol, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return g;
            }
            case "navmap": {
                NavMapView m = new NavMapView(ctx, n.i("value", 1));
                m.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(n.f("h", 260))));
                return m;
            }

            // ----------------------------------------------------- media --
            case "image": {
                ShapeableImageView iv = new ShapeableImageView(ctx);
                int w = dp(n.f("w", 96)), h = dp(n.f("h", 96));
                iv.setLayoutParams(new ViewGroup.LayoutParams(w, h));
                iv.setImageDrawable(gradient(n.s("src", "grad1"), w, h));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                String var = n.s("variant", "rounded");
                ShapeAppearanceModel.Builder sm = ShapeAppearanceModel.builder();
                if ("circle".equals(var)) sm.setAllCorners(CornerFamily.ROUNDED, Math.min(w, h) / 2f);
                else if ("cut".equals(var)) sm.setAllCorners(CornerFamily.CUT, dp(16));
                else sm.setAllCorners(CornerFamily.ROUNDED, dp(16));
                iv.setShapeAppearanceModel(sm.build());
                return iv;
            }
            case "colorswatch": {
                LinearLayout l = new LinearLayout(ctx);
                l.setOrientation(LinearLayout.HORIZONTAL);
                l.setGravity(Gravity.CENTER_VERTICAL);
                int bg = roleColor(n.s("group"));
                l.setBackgroundColor(bg);
                l.setPadding(dp(16), dp(14), dp(16), dp(14));
                AppCompatTextView t = new AppCompatTextView(ctx);
                TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_BodyMedium);
                t.setText(n.s("text", ""));
                t.setTextColor(contrastOn(bg));
                l.addView(t);
                return l;
            }
            case "shapebox": {
                MaterialCardView c = new MaterialCardView(ctx, null,
                    R.attr.materialCardViewFilledStyle);
                float r = n.f("radius", 0);
                float rr = r >= 999 ? dp(28) * 2f : dp(r);
                ShapeAppearanceModel.Builder sm = ShapeAppearanceModel.builder();
                sm.setAllCorners("cut".equals(n.s("variant", "")) ? CornerFamily.CUT : CornerFamily.ROUNDED, rr);
                c.setShapeAppearanceModel(sm.build());
                AppCompatTextView t = new AppCompatTextView(ctx);
                TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_TitleSmall);
                t.setText(n.s("text", ""));
                t.setPadding(dp(20), dp(20), dp(20), dp(20));
                c.addView(t);
                return c;
            }
        }
        // Kinds handled by the host (dialog launchers, demo hosts) fall through
        // to a labelled placeholder so nothing silently disappears.
        return hostPlaceholder(n);
    }

    // ---------------------------------------------------------- helpers ----

    void fillNav(NavigationBarView b, Node n) {
        String[] items = split(n.s("items"));
        String[] icons = csv(n.s("icon"));
        String[] badges = csv(n.s("badge"));
        for (int i = 0; i < items.length && i < 5; i++) {
            android.view.MenuItem mi = b.getMenu().add(0, i, i, items[i]);
            if (i < icons.length && !icons[i].isEmpty()) mi.setIcon(icon(icons[i]));
            if (i < badges.length && !badges[i].isEmpty()) {
                try { b.getOrCreateBadge(i).setNumber(Integer.parseInt(badges[i])); } catch (Exception ignored) {}
            }
        }
        int sel = n.i("selected", 0);
        if (sel < items.length) b.setSelectedItemId(sel);
        b.setOnItemSelectedListener(mi -> { env.onState(n.s("key", ""), String.valueOf(mi.getItemId()), true); return true; });
    }

    View listItem(Node n) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(n.has("supporting") ? 72 : 56));
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        Drawable lead = icon(n.s("icon"));
        if (lead != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(lead);
            iv.setImageTintList(ColorStateList.valueOf(attrColor(R.attr.colorOnSurfaceVariant)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(24), dp(24));
            lp.rightMargin = dp(16);
            row.addView(iv, lp);
        }

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        AppCompatTextView h = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(h, R.style.TextAppearance_Material3_BodyLarge);
        h.setText(n.s("text", ""));
        texts.addView(h);
        if (n.has("supporting")) {
            AppCompatTextView sup = new AppCompatTextView(ctx);
            TextViewCompat.setTextAppearance(sup, R.style.TextAppearance_Material3_BodyMedium);
            sup.setTextColor(attrColor(R.attr.colorOnSurfaceVariant));
            sup.setText(n.s("supporting"));
            sup.setMaxLines(n.i("lines", 3) >= 3 ? 2 : 1);
            texts.addView(sup);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String act = n.s("action", "");
        if ("switch".equals(act)) {
            MaterialSwitch sw = new MaterialSwitch(ctx);
            sw.setChecked(n.b("on", false));
            sw.setOnCheckedChangeListener((b, c) -> env.onState(n.s("key", ""), c ? "1" : "0", false));
            row.addView(sw);
            row.setOnClickListener(v -> sw.toggle());
        } else if ("checkbox".equals(act)) {
            MaterialCheckBox cb = new MaterialCheckBox(ctx);
            cb.setChecked(n.b("on", false));
            cb.setOnCheckedChangeListener((b, c) -> env.onState(n.s("key", ""), c ? "1" : "0", false));
            row.addView(cb);
            row.setOnClickListener(v -> cb.toggle());
        } else {
            Drawable tr = icon(n.s("icon2"));
            if (tr != null) {
                ImageView iv = new ImageView(ctx);
                iv.setImageDrawable(tr);
                iv.setImageTintList(ColorStateList.valueOf(attrColor(R.attr.colorOnSurfaceVariant)));
                row.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
            }
            if (n.b("tap", false)) row.setOnClickListener(v -> env.onAction(n, v));
        }
        return row;
    }

    // ------------------------------------------------------ demo hosts ----

    /** A real Carousel: RecyclerView + CarouselLayoutManager + MaskableFrameLayout. */
    View carousel(Node n) {
        androidx.recyclerview.widget.RecyclerView rv = new androidx.recyclerview.widget.RecyclerView(ctx);
        String var = n.s("variant", "hero");
        com.google.android.material.carousel.CarouselLayoutManager lm =
            new com.google.android.material.carousel.CarouselLayoutManager();
        switch (var) {
            case "multibrowse":
                lm.setCarouselStrategy(new com.google.android.material.carousel.MultiBrowseCarouselStrategy());
                break;
            case "uncontained":
                lm.setCarouselStrategy(new com.google.android.material.carousel.UncontainedCarouselStrategy());
                break;
            case "fullscreen":
                lm.setCarouselStrategy(new com.google.android.material.carousel.FullScreenCarouselStrategy());
                lm.setOrientation(androidx.recyclerview.widget.RecyclerView.VERTICAL);
                break;
            default:
                lm.setCarouselStrategy(new com.google.android.material.carousel.HeroCarouselStrategy());
        }
        rv.setLayoutManager(lm);
        rv.setNestedScrollingEnabled(false);
        // Snapping is what makes a carousel feel like one rather than a list.
        new com.google.android.material.carousel.CarouselSnapHelper().attachToRecyclerView(rv);
        final int count = Math.max(1, n.i("count", 8));
        // The fullscreen strategy pages vertically, so it needs a page-sized item.
        final int itemH = dp("fullscreen".equals(var) ? 420 : 200);
        rv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemH));
        rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<CarouselVH>() {
            @Override public CarouselVH onCreateViewHolder(ViewGroup parent, int vt) {
                com.google.android.material.carousel.MaskableFrameLayout m =
                    new com.google.android.material.carousel.MaskableFrameLayout(ctx);
                boolean full = "fullscreen".equals(var);
                m.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                    full ? ViewGroup.LayoutParams.MATCH_PARENT : dp(220),
                    full ? dp(420) : ViewGroup.LayoutParams.MATCH_PARENT));
                m.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, dp(20)).build());
                ImageView iv = new ImageView(ctx);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                m.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                AppCompatTextView t = new AppCompatTextView(ctx);
                TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_TitleMedium);
                t.setTextColor(Color.WHITE);
                t.setPadding(dp(16), dp(16), dp(16), dp(16));
                FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.gravity = Gravity.BOTTOM | Gravity.START;
                m.addView(t, tlp);
                return new CarouselVH(m, iv, t);
            }
            @Override public void onBindViewHolder(CarouselVH h, int pos) {
                h.img.setImageDrawable(gradient("grad" + (pos % 3 + 1), dp(220), itemH));
                h.label.setText("Item " + (pos + 1));
            }
            @Override public int getItemCount() { return count; }
        });
        return rv;
    }

    static class CarouselVH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        final ImageView img; final AppCompatTextView label;
        CarouselVH(View v, ImageView i, AppCompatTextView t) { super(v); img = i; label = t; }
    }

    /** SearchBar + SearchView, wired as the real expand-to-full-screen pair. */
    View searchBar(Node n) {
        LinearLayout host = new LinearLayout(ctx);
        host.setOrientation(LinearLayout.VERTICAL);
        com.google.android.material.search.SearchBar sb =
            new com.google.android.material.search.SearchBar(ctx);
        sb.setHint(n.s("hint", "Search"));
        sb.setText(n.s("text", ""));
        host.addView(sb, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sb.setOnClickListener(v -> env.onAction(n, v));
        return host;
    }

    /** The four M3 top-app-bar sizes, each shown as a self-contained surface. */
    View appBarDemo(Node n) {
        String var = n.s("variant", "small");
        MaterialCardView card = new MaterialCardView(ctx, null, R.attr.materialCardViewOutlinedStyle);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(attrColor(R.attr.colorSurfaceContainer));

        com.google.android.material.appbar.MaterialToolbar tb =
            new com.google.android.material.appbar.MaterialToolbar(ctx);
        tb.setNavigationIcon(icon("drawer_menu"));
        tb.getMenu().add("Search").setIcon(icon("search"))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        tb.getMenu().add("More").setIcon(icon("drag_handle"))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        tb.setOnMenuItemClickListener(mi -> { env.onAction(n, tb); return true; });

        if ("center".equals(var)) { tb.setTitle(n.s("title", "Title")); tb.setTitleCentered(true); col.addView(tb); }
        else if ("small".equals(var)) { tb.setTitle(n.s("title", "Title")); col.addView(tb); }
        else {
            // medium / large: an expanded headline under a bare toolbar
            col.addView(tb);
            AppCompatTextView h = new AppCompatTextView(ctx);
            TextViewCompat.setTextAppearance(h, "large".equals(var)
                ? R.style.TextAppearance_Material3_HeadlineMedium
                : R.style.TextAppearance_Material3_HeadlineSmall);
            h.setText(n.s("title", "Title"));
            h.setPadding(dp(16), dp(4), dp(16), dp("large".equals(var) ? 28 : 20));
            col.addView(h);
        }
        card.addView(col);
        return card;
    }

    /** BottomAppBar with a cradled FAB. */
    View bottomBarDemo(Node n) {
        MaterialCardView card = new MaterialCardView(ctx, null, R.attr.materialCardViewOutlinedStyle);
        androidx.coordinatorlayout.widget.CoordinatorLayout co =
            new androidx.coordinatorlayout.widget.CoordinatorLayout(ctx);
        co.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));

        com.google.android.material.bottomappbar.BottomAppBar bab =
            new com.google.android.material.bottomappbar.BottomAppBar(ctx);
        bab.setFabAlignmentMode("end".equals(n.s("action", ""))
            ? com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_END
            : com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_CENTER);
        bab.setNavigationIcon(icon("drawer_menu"));
        bab.getMenu().add("Search").setIcon(icon("search"))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        bab.getMenu().add("More").setIcon(icon("drag_handle"))
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams blp =
            new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        co.addView(bab, blp);

        FloatingActionButton fab = new FloatingActionButton(ctx);
        fab.setImageDrawable(icon("add"));
        fab.setId(View.generateViewId());
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams flp =
            new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.setAnchorId(bab.getId() == View.NO_ID ? View.NO_ID : bab.getId());
        co.addView(fab, flp);
        bab.setFabAnchorMode(com.google.android.material.bottomappbar.BottomAppBar.FAB_ANCHOR_MODE_CRADLE);
        fab.setOnClickListener(v -> env.onAction(n, v));
        card.addView(co);
        return card;
    }

    /** Docked / floating / vertical toolbars, as icon-action surfaces. */
    View toolbarDemo(Node n) {
        String var = n.s("variant", "docked");
        boolean vertical = "vertical".equals(var);
        MaterialCardView card = new MaterialCardView(ctx, null,
            "docked".equals(var) ? R.attr.materialCardViewFilledStyle : R.attr.materialCardViewElevatedStyle);
        if (!"docked".equals(var)) {
            card.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, dp(28)).build());
        }
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        String[] icons = {"format_bold", "format_italic", "format_underlined", "format_align_center"};
        for (String ic : icons) {
            MaterialButton b = new MaterialButton(ctx, null, R.attr.materialIconButtonStyle);
            b.setIcon(icon(ic));
            b.setOnClickListener(v -> env.onAction(n, v));
            bar.addView(b);
        }
        if ("floatingfab".equals(var)) {
            FloatingActionButton f = new FloatingActionButton(ctx);
            f.setImageDrawable(icon("add"));
            f.setSize(FloatingActionButton.SIZE_MINI);
            f.setOnClickListener(v -> env.onAction(n, v));
            bar.addView(f);
        }
        card.addView(bar);
        return card;
    }

    /** List-detail / supporting-pane / feed, laid out by available width. */
    View adaptiveDemo(Node n) {
        String var = n.s("variant", "listdetail");
        boolean wide = ctx.getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        if ("feed".equals(var)) {
            GridLayout g = new GridLayout(ctx);
            g.setColumnCount(wide ? 3 : 2);
            for (int i = 0; i < 6; i++) {
                MaterialCardView c = new MaterialCardView(ctx, null, R.attr.materialCardViewFilledStyle);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0; lp.height = dp(96);
                lp.columnSpec = GridLayout.spec(i % (wide ? 3 : 2), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                c.setLayoutParams(lp);
                AppCompatTextView t = new AppCompatTextView(ctx);
                TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_TitleSmall);
                t.setText("Card " + (i + 1));
                t.setPadding(dp(12), dp(12), dp(12), dp(12));
                c.addView(t);
                g.addView(c);
            }
            return g;
        }

        MaterialCardView list = new MaterialCardView(ctx, null, R.attr.materialCardViewOutlinedStyle);
        LinearLayout lcol = new LinearLayout(ctx);
        lcol.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 3; i++) {
            Node li = new Node();
            li.kind = "listitem";
            li.attrs.put("text", "Item " + (i + 1));
            li.attrs.put("supporting", "Supporting text");
            li.attrs.put("tap", 1.0);
            lcol.addView(build(li));
        }
        list.addView(lcol);

        MaterialCardView detail = new MaterialCardView(ctx, null, R.attr.materialCardViewFilledStyle);
        AppCompatTextView dt = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(dt, R.style.TextAppearance_Material3_BodyMedium);
        dt.setText("supporting".equals(var)
            ? "Supporting pane — secondary content beside the main pane."
            : "Detail pane — the selected item's content.");
        dt.setPadding(dp(16), dp(16), dp(16), dp(16));
        detail.addView(dt);

        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(
            wide ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, wide ? 1f : 0f);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(
            wide ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, wide ? 1f : 0f);
        if (wide) b.leftMargin = dp(8); else b.topMargin = dp(8);
        wrap.addView(list, a);
        wrap.addView(detail, b);
        return wrap;
    }

    // ------------------------------------------------------ transitions ----

    /** Views the transition demos animate. Registered so the host can drive them. */
    public final Map<String, FrameLayout> transitionHosts = new HashMap<>();

    View transitionHost(Node n) {
        FrameLayout host = new FrameLayout(ctx);
        host.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(n.f("h", 200))));
        String var = n.s("variant", "stage");
        transitionHosts.put(var, host);

        if ("container".equals(var)) {
            // Hug the collapsed card; expanding grows the host with the transform
            // rather than leaving dead space under a small card.
            // `h` is the collapsed height (the parent's addChildren sets it);
            // `max` is what expanding animates to.
            host.setTag(R.id.splash_expanded_h, dp(n.f("max", 240)));
            host.setTag(R.id.splash_collapsed_h, dp(n.f("h", 96)));
            host.addView(containerCollapsed(host));
        } else {
            host.addView(stagePane(0));
        }
        return host;
    }

    /** The collapsed card of the container-transform demo. */
    View containerCollapsed(FrameLayout host) {
        MaterialCardView c = new MaterialCardView(ctx, null, R.attr.materialCardViewFilledStyle);
        c.setTag("collapsed");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        lp.gravity = Gravity.TOP;
        c.setLayoutParams(lp);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        ShapeableImageView iv = new ShapeableImageView(ctx);
        iv.setImageDrawable(gradient("grad1", dp(56), dp(56)));
        iv.setShapeAppearanceModel(ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, dp(12)).build());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(56), dp(56));
        ilp.rightMargin = dp(16);
        row.addView(iv, ilp);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        AppCompatTextView t = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_TitleMedium);
        t.setText("Container transform");
        AppCompatTextView s = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(s, R.style.TextAppearance_Material3_BodySmall);
        s.setTextColor(attrColor(R.attr.colorOnSurfaceVariant));
        s.setText("Tap to expand");
        col.addView(t); col.addView(s);
        row.addView(col);
        c.addView(row);
        c.setOnClickListener(v -> expandContainer(host, c));
        return c;
    }

    View containerExpanded(FrameLayout host) {
        MaterialCardView c = new MaterialCardView(ctx, null, R.attr.materialCardViewFilledStyle);
        c.setTag("expanded");
        c.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(16));
        ShapeableImageView iv = new ShapeableImageView(ctx);
        iv.setImageDrawable(gradient("grad1", dp(300), dp(96)));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setShapeAppearanceModel(ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, dp(12)).build());
        col.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        AppCompatTextView t = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_TitleLarge);
        t.setText("Expanded");
        t.setPadding(0, dp(12), 0, dp(4));
        AppCompatTextView s = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(s, R.style.TextAppearance_Material3_BodyMedium);
        s.setTextColor(attrColor(R.attr.colorOnSurfaceVariant));
        s.setText("The card grew into this surface. Tap to collapse.");
        col.addView(t); col.addView(s);
        c.addView(col);
        c.setOnClickListener(v -> collapseContainer(host, c));
        return c;
    }

    void expandContainer(FrameLayout host, View start) {
        Object tag = host.getTag(R.id.splash_expanded_h);
        animateHostHeight(host, tag instanceof Integer ? (Integer) tag : dp(220));
        View end = containerExpanded(host);
        com.google.android.material.transition.MaterialContainerTransform tr =
            new com.google.android.material.transition.MaterialContainerTransform();
        tr.setStartView(start);
        tr.setEndView(end);
        tr.addTarget(end);
        tr.setDuration(450);
        tr.setScrimColor(Color.TRANSPARENT);
        androidx.transition.TransitionManager.beginDelayedTransition(host, tr);
        host.removeAllViews();
        host.addView(end);
    }

    void collapseContainer(FrameLayout host, View start) {
        Object ct = host.getTag(R.id.splash_collapsed_h);
        animateHostHeight(host, ct instanceof Integer ? (Integer) ct : dp(96));
        View end = containerCollapsed(host);
        com.google.android.material.transition.MaterialContainerTransform tr =
            new com.google.android.material.transition.MaterialContainerTransform();
        tr.setStartView(start);
        tr.setEndView(end);
        tr.addTarget(end);
        tr.setDuration(400);
        tr.setScrimColor(Color.TRANSPARENT);
        androidx.transition.TransitionManager.beginDelayedTransition(host, tr);
        host.removeAllViews();
        host.addView(end);
    }

    void animateHostHeight(View host, int to) {
        int from = host.getHeight();
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(from, to);
        a.setDuration(450);
        a.setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator());
        a.addUpdateListener(v -> {
            host.getLayoutParams().height = (Integer) v.getAnimatedValue();
            host.requestLayout();
        });
        a.start();
    }

    /** One pane of the shared-axis / fade stage. */
    View stagePane(int i) {
        MaterialCardView c = new MaterialCardView(ctx, null, R.attr.materialCardViewFilledStyle);
        c.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(20), dp(20), dp(20), dp(20));
        AppCompatTextView t = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_HeadlineSmall);
        t.setText("Pane " + (i + 1));
        AppCompatTextView s = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(s, R.style.TextAppearance_Material3_BodyMedium);
        s.setTextColor(attrColor(R.attr.colorOnSurfaceVariant));
        s.setText("Press a motion button above");
        s.setPadding(0, dp(6), 0, 0);
        col.addView(t); col.addView(s);
        c.addView(col);
        return c;
    }

    int stageIndex = 0;

    /** Run a named Material transition on the stage pane. */
    public void runTransition(String kind) {
        FrameLayout host = transitionHosts.get("stage");
        if (host == null) return;
        androidx.transition.Transition tr;
        switch (kind) {
            case "axisx": tr = new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, true); break;
            case "axisy": tr = new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.Y, true); break;
            case "axisz": tr = new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.Z, true); break;
            case "fade":  tr = new com.google.android.material.transition.MaterialFade(); break;
            default:      tr = new com.google.android.material.transition.MaterialFadeThrough();
        }
        tr.setDuration(450);
        androidx.transition.TransitionManager.beginDelayedTransition(host, tr);
        stageIndex++;
        host.removeAllViews();
        host.addView(stagePane(stageIndex));
    }

    View hostPlaceholder(Node n) {
        // The host (MainActivity) replaces these by kind; if it doesn't, the
        // user sees what is missing rather than a blank gap.
        AppCompatTextView t = new AppCompatTextView(ctx);
        TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_BodySmall);
        t.setText("[" + n.kind + "]");
        t.setTextColor(attrColor(R.attr.colorOutline));
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    void tintFab(View f, String role) {
        if (role == null) return;
        int bg = roleColor(role.equals("surface") ? "surfaceVariant" : role + "Container");
        int fg = roleColor(role.equals("surface") ? "onSurfaceVariant" : "on" + cap(role) + "Container");
        if (f instanceof FloatingActionButton) {
            ((FloatingActionButton) f).setBackgroundTintList(ColorStateList.valueOf(bg));
            ((FloatingActionButton) f).setImageTintList(ColorStateList.valueOf(fg));
        } else if (f instanceof ExtendedFloatingActionButton) {
            ((ExtendedFloatingActionButton) f).setBackgroundTintList(ColorStateList.valueOf(bg));
            ((ExtendedFloatingActionButton) f).setIconTint(ColorStateList.valueOf(fg));
            ((ExtendedFloatingActionButton) f).setTextColor(fg);
        }
    }

    static String cap(String s) { return s.substring(0, 1).toUpperCase() + s.substring(1); }

    static int contrastOn(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return lum > 0.55 ? Color.BLACK : Color.WHITE;
    }

    Drawable gradient(String name, int w, int h) {
        int a, b;
        switch (name == null ? "grad1" : name) {
            case "grad2": a = 0xFF43C6AC; b = 0xFF191654; break;
            case "grad3": a = 0xFFFF9966; b = 0xFFFF5E62; break;
            default:      a = 0xFF7F7FD5; b = 0xFF86A8E7; break;
        }
        // GradientDrawable rather than PaintDrawable+ShaderFactory: the latter
        // has no intrinsic size, so inside a CENTER_CROP ImageView it never
        // painted (verified on device — the carousel drew empty cards).
        GradientDrawable g = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        g.setSize(Math.max(w, 1), Math.max(h, 1));
        return g;
    }

    static int buttonStyleAttr(String v) {
        switch (v) {
            case "elevated": return R.attr.materialButtonElevatedStyle;
            case "tonal":    return R.attr.materialButtonTonalStyle;
            case "outlined": return R.attr.materialButtonOutlinedStyle;
            case "text":     return R.attr.borderlessButtonStyle;
            default:         return R.attr.materialButtonStyle;
        }
    }

    static int iconButtonStyleAttr(String v) {
        switch (v) {
            case "filled":   return R.attr.materialIconButtonFilledStyle;
            case "tonal":    return R.attr.materialIconButtonFilledTonalStyle;
            case "outlined": return R.attr.materialIconButtonOutlinedStyle;
            default:         return R.attr.materialIconButtonStyle;
        }
    }

    static int chipStyle(String v) {
        switch (v) {
            case "filter":     return R.style.Widget_Material3_Chip_Filter;
            case "input":      return R.style.Widget_Material3_Chip_Input;
            case "suggestion": return R.style.Widget_Material3_Chip_Suggestion;
            default:           return R.style.Widget_Material3_Chip_Assist;
        }
    }

    static int cardStyle(String v) {
        switch (v) {
            case "filled":   return R.attr.materialCardViewFilledStyle;
            case "outlined": return R.attr.materialCardViewOutlinedStyle;
            default:         return R.attr.materialCardViewElevatedStyle;
        }
    }

    interface Sink { void accept(String s); }

    static class SimpleWatcher implements android.text.TextWatcher {
        final Sink sink;
        SimpleWatcher(Sink s) { sink = s; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(android.text.Editable e) { sink.accept(e.toString()); }
    }
}
