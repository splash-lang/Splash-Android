package dev.splash.catalog;

import java.nio.ByteBuffer;

/** JNI surface. Rust owns the DSL, the VM and the card state; Java owns Views. */
public final class Native {
    static { System.loadLibrary("splash_catalog"); }

    /** Evaluate `route` with current state; returns the node tree as a direct buffer. */
    public static native ByteBuffer render(String route);

    /**
     * Render a semantic PLAN — the typed JSON an LLM emits — straight to the node
     * tree. No Splash DSL is involved on this path.
     *
     * The same plan JSON is lowered by octos-one to makepad Splash DSL, so this is
     * where the "one plan, many native backends" claim is actually tested. A bad plan
     * renders a visible rejection rather than returning null: a blank screen cannot be
     * told apart from a crash.
     */
    public static native ByteBuffer renderPlan(String planJson);

    public static native void set(String key, String value);
    public static native String get(String key);
    public static native String diag();

    public static native int routeCount();
    /** "route|Title" */
    public static native String routeAt(int i);
}
