package dev.splash.catalog;

import java.nio.ByteBuffer;

/** JNI surface. Rust owns the DSL, the VM and the card state; Java owns Views. */
public final class Native {
    static { System.loadLibrary("splash_catalog"); }

    /** Evaluate `route` with current state; returns the node tree as a direct buffer. */
    public static native ByteBuffer render(String route);

    public static native void set(String key, String value);
    public static native String get(String key);
    public static native String diag();

    public static native int routeCount();
    /** "route|Title" */
    public static native String routeAt(int i);
}
