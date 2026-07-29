package dev.splash.probe;

import java.nio.ByteBuffer;

public final class Native {
    static { System.loadLibrary("splash_android_probe"); }

    /** One JNI crossing: the whole UiNode tree as a direct ByteBuffer. */
    public static native ByteBuffer buildOps();

    /** Whether splash-render actually evaluated the DSL on device. */
    public static native String diag();
}
