# Splash-Android

Splash DSL rendered to **native Android widgets** from Rust — the Android peer of
[Splash-OH](https://github.com/ymote/Splash-OH), which does the same against
OpenHarmony's ArkUI.

```
probe/      the feasibility probe   — splash-render -> android.widget.*, framework widgets only
catalog/    the Material catalog    — 42 screens of Splash DSL -> com.google.android.material.*
```

Both run on device (OnePlus 6T, Android 11 / SDK 30).

## The shape

```
.splash  ──►  makepad-script VM  ──►  node tree  ──►  flat buffer  ──►  Java builder  ──►  Views
              (via splash-render;        (Rust)      ONE JNI call      (owns every View)
               no makepad renderer)
```

**Java owns every `View`; Rust owns integer ids and the card state.** No `jobject`
is ever held in Rust, so ART's 512-local-reference abort and the `FindClass`
classloader trap are structurally unreachable.

There is no makepad renderer in the process — no `makepad-platform`, no
`makepad-draw`, no `makepad-widgets`, no GL surface. The only makepad code linked
in is `makepad-script`, the language VM, whose own dependencies are
`error_log`, `math`, `live_id`, `script-derive`, `smallvec`, `regex`, `html`.

## Why Android is not a port of Splash-OH

OpenHarmony ships `arkui/native_node.h` — a C NDK for widget construction.
**Android has no equivalent**: 62 headers in the NDK's `android/` directory,
none of them a widget API. Every `android.widget.*` object must be constructed
through JNI into ART, and unlike ArkUI there is no native tier beneath the
managed object. So Splash-OH's 2.5–3× construction win does not transfer, and the
design goal here is *minimising boundary crossings*, not avoiding managed-language
objects.

See `docs/` in octos-one (`SPLASH-ANDROID-NATIVE-WIDGETS.md`) for the full
analysis.

## catalog/

A reproduction of
[material-components-android](https://github.com/material-components/material-components-android)'s
catalog: **42 screens**, every one authored in the Splash DSL and evaluated on
device. Includes real `MaterialAlertDialogBuilder` / `Snackbar` /
`MaterialDatePicker` / `MaterialTimePicker` / `BottomSheetDialog` /
`SideSheetDialog` / `PopupMenu` / `DrawerLayout`, a real Carousel, and live
Material motion (`MaterialContainerTransform`, `MaterialSharedAxis`,
`MaterialFadeThrough`).

It also carries **octos-one's own makepad widgets ported to Android views** —
`WeatherIconView`, `NavMapView`, `GlassPanelView` — the ones previously written
off as having no `android.widget` equivalent.

See `catalog/README.md`.

## Build

Both need the Android NDK and a JDK. The catalog uses Gradle (which is what makes
androidx and Material available at all); the probe builds an APK by hand with
`aapt2`/`javac`/`d8`.

```sh
# catalog
cd catalog/rust && cargo build --release --target aarch64-linux-android
cp target/aarch64-linux-android/release/libsplash_catalog.so ../app/src/main/jniLibs/arm64-v8a/
cd .. && gradle assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# probe
cd probe && ./build.sh
```

`catalog/rust` also has a host-side check that needs no device:

```sh
cd catalog/rust && cargo run --release --example probe   # evaluates all 42 routes
```

## Status

- ✅ feasibility probe — framework widgets, real IME, full accessibility tree
- ✅ Material catalog — 42 screens, 0 placeholders, 0 exceptions on device
- ✅ octos-one widget ports — WeatherIcon (8 conditions), MapView (3 nav modes), glass panels
- ⏳ the `UiNode` delta/event contract — construction is done; incremental updates are not
- ⏳ `splash-render` upstreaming: a `Native`/`Custom` node kind, a `key` attribute
  for reconciliation, and `Serialize` derives

## Licence

MIT OR Apache-2.0, matching makepad.
