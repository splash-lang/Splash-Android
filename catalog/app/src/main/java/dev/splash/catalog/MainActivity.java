package dev.splash.catalog;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.widget.TextViewCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.google.android.material.sidesheet.SideSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Builder.Env {

    static final String TAG = "SplashCatalog";

    DrawerLayout drawer;
    CoordinatorRoot root;
    MaterialToolbar toolbar;
    FrameLayout content;
    Builder builder;

    String route = "toc";
    final Deque<String> back = new ArrayDeque<>();
    final List<String[]> routes = new ArrayList<>();

    static class CoordinatorRoot extends androidx.coordinatorlayout.widget.CoordinatorLayout {
        CoordinatorRoot(android.content.Context c) { super(c); }
    }

    @Override protected void onCreate(Bundle b) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(b);
        builder = new Builder(this, this);

        for (int i = 0; i < Native.routeCount(); i++) {
            String s = Native.routeAt(i);
            int p = s.indexOf('|');
            if (p > 0) routes.add(new String[]{ s.substring(0, p), s.substring(p + 1) });
        }

        drawer = new DrawerLayout(this);
        root = new CoordinatorRoot(this);

        AppBarLayout bar = new AppBarLayout(this);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Material Catalog");
        bar.addView(toolbar, new AppBarLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams blp =
            new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(bar, blp);

        content = new FrameLayout(this);
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams clp =
            new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        root.addView(content, clp);

        drawer.addView(root, new DrawerLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        NavigationView nav = new NavigationView(this);
        nav.getMenu().add(0, 1, 0, "Inbox").setIcon(builder.icon("mail_outline"));
        nav.getMenu().add(0, 2, 1, "Starred").setIcon(builder.icon("star"));
        nav.getMenu().add(0, 3, 2, "Settings").setIcon(builder.icon("settings"));
        nav.setNavigationItemSelectedListener(mi -> {
            Native.set("drw_result", String.valueOf(mi.getTitle()));
            drawer.closeDrawer(GravityCompat.START);
            show(route);
            return true;
        });
        DrawerLayout.LayoutParams nlp = new DrawerLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        nlp.gravity = GravityCompat.START;
        drawer.addView(nav, nlp);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        setContentView(drawer);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> goBack());

        installBackHandling();
        publishWindowClass();
        String deep = getIntent() == null ? null : getIntent().getStringExtra("route");
        show(deep != null && !deep.isEmpty() ? deep : "toc");
    }

    /** Rotation changes the window size class, so re-render: the adaptive
     *  demos and the DSL's `win_class` both depend on it. */
    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        publishWindowClass();
        show(route);
    }

    void publishWindowClass() {
        int w = getResources().getConfiguration().screenWidthDp;
        String cls = w >= 840 ? "Expanded (" + w + "dp)"
                   : w >= 600 ? "Medium (" + w + "dp)"
                              : "Compact (" + w + "dp)";
        Native.set("win_class", cls);
    }

    /**
     * Back handling goes through the OnBackPressedDispatcher, not the
     * deprecated {@code onBackPressed()}. With targetSdk 35 the platform drives
     * back through OnBackInvokedCallback on Android 13+, and an override of
     * onBackPressed() is simply never called there — the button appears dead.
     */
    void installBackHandling() {
        getOnBackPressedDispatcher().addCallback(this,
            new androidx.activity.OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() { goBack(); }
            });
    }

    void goBack() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }
        if (!back.isEmpty()) { show(back.pop()); return; }
        // A demo reached directly (deep link, or a stack we already unwound)
        // returns to the catalog rather than dropping the user out of the app.
        if (!"toc".equals(route)) { show("toc"); return; }
        finish();
    }

    // -------------------------------------------------------- rendering ----

    void navigate(String r) { back.push(route); show(r); }

    void show(String r) {
        route = r;
        content.removeAllViews();
        boolean toc = "toc".equals(r);
        toolbar.setNavigationIcon(toc ? null : builder.icon("arrow_back"));
        drawer.setDrawerLockMode("navigationdrawer".equals(r)
            ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        if (toc) { toolbar.setTitle("Material Catalog"); content.addView(buildToc()); return; }

        for (String[] p : routes) if (p[0].equals(r)) toolbar.setTitle(p[1]);

        // A `plan/<name>` route renders a semantic PLAN instead of a DSL route: the
        // same typed JSON octos-one's LLM emits, lowered here straight to nodes. This
        // is where "one plan, many native backends" is actually exercised.
        //
        // Lowering a plan performs LIVE HTTP — the backend has no `sys.*` helpers, so
        // it resolves the data itself. That cannot run on the UI thread, so it happens
        // on a worker and the view build is posted back.
        if (r.startsWith("plan/")) {
            String json = readAsset("plans/" + r.substring(5) + ".json");
            AppCompatTextView loading = new AppCompatTextView(this);
            loading.setPadding(48, 48, 48, 48);
            loading.setText("Resolving live data\u2026");
            content.addView(loading);
            new Thread(() -> {
                ByteBuffer pb = Native.renderPlan(json);
                content.post(() -> {
                    content.removeAllViews();
                    if (pb == null) {
                        AppCompatTextView t = new AppCompatTextView(this);
                        t.setPadding(48, 48, 48, 48);
                        t.setText("plan render failed\n\n" + Native.diag());
                        content.addView(t);
                        return;
                    }
                    try {
                        builder.transitionHosts.clear();
                        View v = builder.build(Node.decode(pb));
                        if (v != null) {
                            ScrollView sv = new ScrollView(this);
                            sv.addView(v);
                            content.addView(sv, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                        }
                        String d = Native.diag();
                        if (d != null && d.startsWith("DEGRADED")) Log.i(TAG, d);
                    } catch (Throwable t) {
                        Log.e(TAG, "plan build failed", t);
                    }
                });
            }, "plan-lower").start();
            return;
        }

        ByteBuffer bb = Native.render(r);
        if (bb == null) {
            AppCompatTextView t = new AppCompatTextView(this);
            t.setPadding(48, 48, 48, 48);
            t.setText("render failed\n\n" + Native.diag());
            content.addView(t);
            Log.e(TAG, "render(" + r + ") null: " + Native.diag());
            return;
        }
        try {
            builder.transitionHosts.clear();
            Node n = Node.decode(bb);
            View v = builder.build(n);
            if (v != null) content.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) {
            Log.e(TAG, "build(" + r + ") failed", t);
            AppCompatTextView tv = new AppCompatTextView(this);
            tv.setPadding(48, 48, 48, 48);
            tv.setText("build failed: " + t);
            content.addView(tv);
        }
    }

    /** Read a bundled asset as UTF-8, or "" if it is missing. */
    String readAsset(String path) {
        try (java.io.InputStream in = getAssets().open(path)) {
            byte[] b = new byte[in.available()];
            int n = in.read(b);
            return n <= 0 ? "" : new String(b, 0, n, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Log.e(TAG, "asset " + path + " missing", t);
            return "";
        }
    }

    /**
     * Semantic-PLAN routes, listed first on the ToC.
     *
     * These render the SAME typed JSON octos-one's LLM emits — no Splash DSL involved.
     * Everything below them in the list is DSL-authored, so the two paths sit side by
     * side in one build.
     */
    static final String[][] PLAN_ROUTES = {
        {"plan/weather", "PLAN \u00b7 Weather (Kyoto)"},
        {"plan/weather-zh", "PLAN \u00b7 \u5929\u6c14 (\u4e0a\u6d77)"},
        {"plan/news", "PLAN \u00b7 News"},
    };

    View buildToc() {
        ScrollView sc = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(24));
        sc.addView(col);

        AppCompatTextView h = new AppCompatTextView(this);
        TextViewCompat.setTextAppearance(h, R.style.TextAppearance_Material3_BodyMedium);
        h.setText("Every screen below is authored in the Splash DSL, evaluated on device by the "
                + "makepad-script VM, and rendered as real Material Components views.");
        h.setPadding(0, dp(8), 0, dp(16));
        col.addView(h);

        MaterialCardView card = new MaterialCardView(this, null,
            R.attr.materialCardViewOutlinedStyle);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        card.addView(list);
        col.addView(card, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (String[] p : PLAN_ROUTES) {
            Node pf = new Node();
            pf.kind = "listitem";
            pf.attrs.put("text", p[1]);
            pf.attrs.put("icon2", "arrow_forward");
            // `tap` is what makes the built row clickable — omitting it produced a row
            // that looked identical and did nothing.
            pf.attrs.put("tap", 1.0);
            pf.attrs.put("route", p[0]);
            View row = builder.build(pf);
            if (row != null) {
                row.setOnClickListener(v -> navigate(p[0]));
                list.addView(row);
            }
        }
        list.addView(new MaterialDivider(this));

        for (int i = 0; i < routes.size(); i++) {
            String[] p = routes.get(i);
            Node fake = new Node();
            fake.kind = "listitem";
            fake.attrs.put("text", p[1]);
            fake.attrs.put("icon2", "arrow_forward");
            fake.attrs.put("tap", 1.0);
            fake.attrs.put("route", p[0]);
            View row = builder.build(fake);
            row.setOnClickListener(v -> navigate(p[0]));
            list.addView(row);
            if (i < routes.size() - 1) list.addView(new MaterialDivider(this));
        }
        return sc;
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------- Env ----

    @Override public void onState(String key, String value, boolean rerender) {
        if (key == null || key.isEmpty()) return;
        Native.set(key, value);
        if (rerender) content.post(() -> show(route));
    }

    @Override public void onAction(Node n, View v) {
        try { onAction0(n, v); }
        catch (Throwable t) { Log.e(TAG, "onAction failed", t);
            Snackbar.make(root, "action failed: " + t, Snackbar.LENGTH_LONG).show(); }
    }

    void onAction0(Node n, View v) {
        String key = n.s("key", "");
        Log.i(TAG, "action key=" + key + " act=" + n.s("action", "") + " kind=" + n.kind);
        String act = n.s("action", "");
        if (n.has("route")) { navigate(n.s("route")); return; }

        switch (key) {
            case "dlg": dialog(act); return;
            case "snk": snackbar(act, v); return;
            case "bs":  bottomSheet(act); return;
            case "ss":  sideSheet(act); return;
            case "dp":  datePicker(act); return;
            case "tp":  timePicker(act); return;
            case "menu": popupMenu(act, v); return;
            case "drw": drawer.openDrawer(GravityCompat.START); return;
            case "trx": builder.runTransition(act); return;
        }
        // Default feedback so every tap is observably live.
        Snackbar.make(root, n.s("label", n.s("text", n.kind)) + " tapped", Snackbar.LENGTH_SHORT).show();
    }

    // ----------------------------------------------------------- hosts ----

    void dialog(String kind) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this);
        switch (kind) {
            case "icon":
                b.setIcon(builder.icon("info_outline")).setTitle("Permission needed")
                 .setMessage("Allow the catalog to access your photos?")
                 .setPositiveButton("Allow", (d, w) -> result("dlg_result", "Allow"))
                 .setNegativeButton("Deny", (d, w) -> result("dlg_result", "Deny"));
                break;
            case "single": {
                String[] items = {"Never", "Daily", "Weekly", "Monthly"};
                b.setTitle("Sync frequency")
                 .setSingleChoiceItems(items, 1, (d, w) -> {})
                 .setPositiveButton("OK", (d, w) -> result("dlg_result", "single choice OK"))
                 .setNegativeButton("Cancel", null);
                break;
            }
            case "multi": {
                String[] items = {"Email", "SMS", "Push"};
                b.setTitle("Notify me by")
                 .setMultiChoiceItems(items, new boolean[]{true, false, true}, (d, w, c) -> {})
                 .setPositiveButton("OK", (d, w) -> result("dlg_result", "multi choice OK"))
                 .setNegativeButton("Cancel", null);
                break;
            }
            case "long": {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 24; i++) sb.append("Terms paragraph ").append(i + 1)
                    .append(": this dialog scrolls when its message exceeds the available height.\n\n");
                b.setTitle("Terms of service").setMessage(sb.toString())
                 .setPositiveButton("Accept", (d, w) -> result("dlg_result", "Accepted"))
                 .setNegativeButton("Decline", null);
                break;
            }
            case "full": {
                b.setTitle("Full-screen dialog")
                 .setView(dialogForm())
                 .setPositiveButton("Save", (d, w) -> result("dlg_result", "Saved"))
                 .setNegativeButton("Cancel", null);
                break;
            }
            default:
                b.setTitle("Reset settings?")
                 .setMessage("This will restore the catalog's defaults. This action cannot be undone.")
                 .setPositiveButton("Reset", (d, w) -> result("dlg_result", "Reset"))
                 .setNegativeButton("Cancel", (d, w) -> result("dlg_result", "Cancel"));
        }
        b.show();
    }

    View dialogForm() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(24), dp(8), dp(24), 0);
        TextInputLayout til = new TextInputLayout(this, null,
            R.attr.textInputOutlinedStyle);
        til.setHint("Name");
        TextInputEditText et = new TextInputEditText(til.getContext());
        til.addView(et);
        l.addView(til);
        return l;
    }

    void snackbar(String kind, View anchor) {
        Snackbar s;
        switch (kind) {
            case "long": s = Snackbar.make(root, "A longer message, shown for longer.", Snackbar.LENGTH_LONG); break;
            case "action":
                s = Snackbar.make(root, "Message archived", Snackbar.LENGTH_LONG)
                    .setAction("Undo", v -> result("snk_result", "Undone"));
                break;
            case "two":
                s = Snackbar.make(root, "This snackbar's message is long enough that it wraps onto a second line.",
                    Snackbar.LENGTH_LONG);
                break;
            case "indef":
                s = Snackbar.make(root, "Stays until dismissed", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Dismiss", v -> {});
                break;
            default: s = Snackbar.make(root, "Single-line snackbar", Snackbar.LENGTH_SHORT);
        }
        s.show();
    }

    void bottomSheet(String kind) {
        BottomSheetDialog d = new BottomSheetDialog(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(8), dp(16), dp(8), dp(24));

        AppCompatTextView t = new AppCompatTextView(this);
        TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_TitleMedium);
        t.setPadding(dp(16), 0, dp(16), dp(8));
        t.setText("list".equals(kind) ? "Share to" : "Bottom sheet");
        l.addView(t);

        int rows = "tall".equals(kind) ? 18 : ("list".equals(kind) ? 6 : 3);
        String[] names = {"Messages", "Mail", "Drive", "Photos", "Notes", "Calendar"};
        for (int i = 0; i < rows; i++) {
            Node n = new Node();
            n.kind = "listitem";
            n.attrs.put("text", names[i % names.length] + ("tall".equals(kind) ? " " + (i + 1) : ""));
            n.attrs.put("icon", "folder");
            n.attrs.put("tap", 1.0);
            View row = builder.build(n);
            row.setOnClickListener(v -> d.dismiss());
            l.addView(row);
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(l);
        d.setContentView(sc);
        d.show();
    }

    void sideSheet(String kind) {
        SideSheetDialog d = new SideSheetDialog(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(24), dp(16), dp(24));
        AppCompatTextView t = new AppCompatTextView(this);
        TextViewCompat.setTextAppearance(t, R.style.TextAppearance_Material3_TitleMedium);
        t.setText("Side sheet");
        l.addView(t);
        for (String s : new String[]{"Filters", "Sort", "Layout", "Density"}) {
            Node n = new Node();
            n.kind = "listitem";
            n.attrs.put("text", s);
            n.attrs.put("tap", 1.0);
            View row = builder.build(n);
            row.setOnClickListener(v -> d.dismiss());
            l.addView(row);
        }
        d.setContentView(l);
        if ("left".equals(kind)) d.setSheetEdge(Gravity.LEFT);
        d.show();
    }

    void datePicker(String kind) {
        if ("range".equals(kind)) {
            MaterialDatePicker<androidx.core.util.Pair<Long, Long>> p =
                MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select dates").build();
            p.addOnPositiveButtonClickListener(sel -> result("dp_result", p.getHeaderText()));
            p.show(getSupportFragmentManager(), "range");
            return;
        }
        MaterialDatePicker.Builder<Long> b = MaterialDatePicker.Builder.datePicker().setTitleText("Select date");
        if ("input".equals(kind)) b.setInputMode(MaterialDatePicker.INPUT_MODE_TEXT);
        MaterialDatePicker<Long> p = b.build();
        p.addOnPositiveButtonClickListener(sel -> result("dp_result", p.getHeaderText()));
        p.show(getSupportFragmentManager(), "date");
    }

    void timePicker(String kind) {
        MaterialTimePicker.Builder b = new MaterialTimePicker.Builder()
            .setTimeFormat("clock24".equals(kind) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
            .setHour(10).setMinute(30).setTitleText("Select time");
        if ("input".equals(kind)) b.setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD);
        MaterialTimePicker p = b.build();
        p.addOnPositiveButtonClickListener(v ->
            result("tp_result", String.format("%02d:%02d", p.getHour(), p.getMinute())));
        p.show(getSupportFragmentManager(), "time");
    }

    void popupMenu(String kind, View anchor) {
        androidx.appcompat.widget.PopupMenu m = new androidx.appcompat.widget.PopupMenu(this, anchor);
        m.getMenu().add("Refresh");
        m.getMenu().add("Settings");
        m.getMenu().add("Help");
        m.setOnMenuItemClickListener(mi -> { result("menu_result", String.valueOf(mi.getTitle())); return true; });
        m.show();
    }

    void result(String key, String value) {
        Native.set(key, value);
        show(route);
    }
}
