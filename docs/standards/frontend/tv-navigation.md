# Android TV Navigation Standards

### Full D-pad Navigation Required
All screens must be fully navigable using a D-pad (up/down/left/right + select/back). Touch input is not the primary interaction model. Every interactive element must be reachable and activatable via remote control.

Use `androidx.tv` components (`Surface`, `Card`, `Button`) which have built-in D-pad focus semantics. Custom interactive elements must declare `Modifier.focusable()` and handle `KeyEvent` explicitly.

```kotlin
// Custom focusable element
Box(
    modifier = Modifier
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.key == Key.DirectionCenter) {
                onSelect(); true
            } else false
        }
)
```

### Touchscreen Not Required
The app declares `android.hardware.touchscreen` as `required="false"` in the manifest. This is intentional — do not add any interaction that requires touch (long-press, swipe gestures, pinch-to-zoom). All gestures must have a D-pad equivalent.

### Android TV Manifest — Dual Launcher
The activity declares both `android.intent.category.LAUNCHER` and `android.intent.category.LEANBACK_LAUNCHER` intent categories. This allows the app to appear in both the standard Android launcher and the TV leanback launcher.

### TV Manifest Declarations
In addition to `android.hardware.touchscreen` declared `required="false"` and the dual launcher categories, `android.software.leanback` is declared `required="false"` so the app installs on non-leanback devices. The `<application>` element declares a TV banner (`android:banner="@drawable/tv_banner"`).

### Focus State Visibility
All focusable elements must have a visible focus state (border, scale, or color change) so the user always knows which element has focus. Rely on TV Material 3 component focus defaults where possible; add custom focus indicators when building custom focusable components.

### InteractionSource-Driven Focus Visuals
Focusable TV elements create a remembered `MutableInteractionSource`, observe focus with `collectIsFocusedAsState()`, and render a visible focus state: animated scale of ~1.07–1.08 via `animateFloatAsState` with `tween(120–150ms)`, and/or a 2.dp `SeanimePrimary` border. Click and focus are wired with `.clickable(interactionSource, indication = null)` + `.focusable(interactionSource)`.

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isFocused by interactionSource.collectIsFocusedAsState()
val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, tween(150), label = "cardScale")
```

### AppCompat NoActionBar
The app theme is `Theme.AppCompat.DayNight.NoActionBar`. Do not use `supportActionBar`, `Toolbar`, or XML menus. All navigation chrome (top bars, navigation drawers) is implemented in Compose using `androidx.tv` layout components.

### OAuth Deep Link
`MainActivity` handles the custom deep link scheme `seanimetv://auth` (VIEW action with DEFAULT and BROWSABLE categories) for the AniList OAuth redirect flow. Auth redirects must use this scheme.
