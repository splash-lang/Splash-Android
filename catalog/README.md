# Splash Catalog — the Material Components Android catalog, driven by Splash DSL

A reproduction of [material-components-android](https://github.com/material-components/material-components-android)'s
catalog app in which **every screen is authored in the Splash DSL, evaluated on
device by the makepad-script VM, and rendered as real
`com.google.android.material.*` views.**

No makepad renderer. No GL surface. No `Splash` widget. The only makepad code in
the process is the language VM.

```
41 .splash screens
   │
   ▼  makepad-script VM (via splash-render's re-export)   ── Rust
generic node tree  (kind + attr bag + children)
   │
   ▼  flat binary buffer, one direct ByteBuffer
   │  ── ONE JNI crossing per render ──
   ▼
Java builder → MaterialButton / Chip / TextInputLayout / Slider / …   ── Java owns every View
```

## Build

```sh
cd rust && cargo build --release --target aarch64-linux-android   # needs the NDK env
cp target/aarch64-linux-android/release/libsplash_catalog.so ../app/src/main/jniLibs/arm64-v8a/
cd .. && gradle assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Deep-link any screen: `adb shell am start -n dev.splash.catalog/.MainActivity --es route button`

Host-side check of every route without a device: `cd rust && cargo run --release --example probe`

## Coverage — 41 screens

All 41 routes verified on device (OnePlus 6T, Android 11 / SDK 30, Material 1.13.0):
rendered, no placeholders, no exceptions. Node counts 22–99 per screen.

`allcomponents` `adaptive` `badge` `bottomappbar` `bottomnav` `bottomsheet`
`button` `card` `carousel` `checkbox` `chip` `color` `datepicker` `dialog`
`divider` `dockedtoolbar` `elevation` `fab` `floatingtoolbar` `font` `imageview`
`listitem` `loadingindicator` `materialswitch` `menu` `musicplayer`
`navigationdrawer` `navigationrail` `preferences` `progressindicator`
`radiobutton` `search` `shapetheming` `sidesheet` `slider` `snackbar` `tabs`
`textfield` `timepicker` `topappbar` `transition`

### Interactions verified on device

| flow | evidence |
|---|---|
| `MaterialAlertDialogBuilder` — alert / icon / single / multi / long / full-screen | dialog renders with M3 shape + scrim |
| `Snackbar` with action | "Message archived" + "Undo" in the a11y tree |
| `MaterialDatePicker` — calendar / range / input | full calendar, correct date, Cancel/OK |
| `MaterialTimePicker` — 12h / 24h / keyboard | "Select time" |
| `BottomSheetDialog` — modal / list / tall | rounded sheet + list rows |
| `SideSheetDialog` — left / right / detached | "Side sheet" |
| `PopupMenu` | "Refresh" |
| `DrawerLayout` + `NavigationView` | Inbox / Starred / Settings |
| **state round-trip** | tab tap → `Content for tab 0` → `Content for tab 1`; slider drag → `Value: 50` → `Value: 97` |

The state round-trip is the one that matters: a Java widget event writes Rust
state, Rust **re-evaluates the DSL in the VM**, and the new tree rebuilds the
views. The DSL — not Java — decides what the screen says.

## VM constraints discovered (the hard-won part)

The makepad-script rev `e1c2164b` that `splash-render` pins has three shapes
that silently produce a wrong tree rather than an error. All were found by
host-side probing (`examples/probe.rs`) after they showed up as blank screens:

| shape | result | use instead |
|---|---|---|
| a top-level **function call** as the module result — `page([...])` | root evaluates without a `t` tag | end with a **literal object** |
| `let k = [ {…}, {…} ]` then `c: k` | the array arrives **empty** — children silently dropped | inline the array, or `let k = []` + `k.push(…)` |
| `st.missing_key` on a plain object | hard VM error, whole eval fails | a host function that returns a default — see `S()` / `N()` |

`S(key)` / `N(key, default)` are injected as VM globals (`set_injected_global`),
exactly as Splash-OH injects its network helpers. That is also why state reads
cannot fail: a missing key returns `""` / the default instead of killing the
evaluation.

Helper functions that **return objects** are fine — `section()`, `caption()`,
`group()` all do. Only the three shapes above are unsafe.

## Design notes

- **Java owns the Views; Rust owns ids and state.** No `jobject` is ever held in
  Rust, so the 512-local-ref abort and the `FindClass` classloader trap are
  structurally unreachable.
- **One JNI crossing per render.** The whole tree travels as a flat buffer in a
  direct `ByteBuffer`; strings live in a side blob addressed by (offset, len).
- **Generic attribute bag, not a fixed struct.** `splash-render`'s `Attrs` has ~30
  fixed fields; 43 Material components need far more, so this walker carries
  `Vec<(String, Val)>` against an explicit ~56-name vocabulary. LiveId keys are
  one-way hashes, so the vocabulary must be declared, not discovered.
- **Gradle, not cargo-makepad.** That is what makes androidx and Material
  available at all — the `-classpath android.jar` limitation in
  `cargo_makepad/src/android/compile.rs` is a property of that build path, not of
  Android.
- **Icons** are the catalog's own 112 vector drawables, lifted from the MDC repo.
  A small alias table maps DSL names onto the closest shipped icon where the set
  has no exact match.

## Motion, rotation, icons — all wired

**Material motion runs for real**, driven through `androidx.transition.TransitionManager`
on the view hierarchy (no fragment back stack needed):

| transition | verified |
|---|---|
| `MaterialContainerTransform` | card → detail surface: `Tap to expand` → `Expanded`, host height animates 96dp → 240dp with the transform |
| `MaterialSharedAxis` X / Y / Z | `Pane 1` → `Pane 2` |
| `MaterialFadeThrough` / `MaterialFade` | `Pane 2` → `Pane 3` |

**Rotation re-renders.** The Activity handles `orientation|screenSize|…` itself,
republishes the window size class into Rust state, and re-evaluates the DSL:
`Compact (384dp)` → `Medium (803dp)` on rotate. The adaptive demos relayout with it.

**Carousel** uses a `CarouselSnapHelper`; the `fullscreen` strategy pages
vertically with page-sized items.

**Icons are real vectors, with no approximations.** 122 drawables: the MDC
catalog's own set plus Material Symbols authored here for the ones it does not
ship (`share`, `shopping_cart`, `notifications`, `more_vert`, `shuffle`,
`repeat`, `place`, `call`, `album`, `chat`, `bookmark`, `payments`). The alias
table now holds only genuine synonyms (`mail`→`mail_outline`, `person`→
`account_circle`, …), not stand-ins.

## Bugs found by looking at the device

Worth recording, because none surfaced as an error:

- **`PaintDrawable` + `ShaderFactory` never painted** inside a `CENTER_CROP`
  `ImageView` — no intrinsic size. The carousel drew empty cards. Fixed with
  `GradientDrawable`.
- **A `spacer` took weight on both axes**, so the music player's time row grew to
  682px and swallowed the transport controls. A spacer now takes weight only
  along its parent's orientation.
- **The parent's `addChildren` overwrote a host's own height**, so the
  container-transform host stayed 220dp with the collapsed card floating in dead
  space. The DSL now states collapsed (`h`) and expanded (`max`) heights.
