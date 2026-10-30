# Jetpack Compose + Android TV Standards

### Declarative UI via Jetpack Compose
All screens and UI components must be built with Jetpack Compose. No XML layout files for UI. All Compose library versions are managed via the Compose BOM (`2024.09.00`) — add Compose dependencies without explicit version strings.

```kotlin
// ✓ Compose-only UI
@Composable
fun HomeScreen() { ... }

// ✗ No XML layouts, no View inflation
setContentView(R.layout.activity_home)
```

### Use androidx.tv Material 3 Components
Use `androidx.tv:tv-foundation` and `androidx.tv:tv-material` components for all TV UI elements. These provide built-in D-pad focus semantics. Do not use standard `androidx.compose.material3` components where a TV equivalent exists.

```kotlin
// ✓ TV Material 3
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

// ✗ Standard Material3 where TV equivalent exists
import androidx.compose.material3.Surface
```

### @OptIn(ExperimentalTvMaterial3Api::class) Required
Every function, composable, or file that calls any `androidx.tv.material3` API must be annotated with `@OptIn(ExperimentalTvMaterial3Api::class)`. All TV Material 3 APIs are experimental (alpha07).

Apply at the narrowest scope that covers all usages — typically the function or class level.

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
override fun onCreate(savedInstanceState: Bundle?) { ... }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeCard() { Surface { ... } }

@OptIn(ExperimentalTvMaterial3Api::class)
val Typography = Typography(...)
```

### SeanimeTVTheme Wraps All Content
All Compose content trees must be wrapped in `SeanimeTVTheme { }`. This applies to the activity's `setContent {}` and to every `@Preview` composable.

```kotlin
// Activity
setContent { SeanimeTVTheme { NavHost(...) } }

// Preview
@Preview
@Composable
fun LibraryScreenPreview() {
    SeanimeTVTheme { LibraryScreen() }
}
```

### Modifier as Last Parameter with Default
`@Composable` functions that accept a modifier declare it as the last non-content parameter with `Modifier` as the default value. This follows official Compose API guidelines.

```kotlin
@Composable
fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,  // last non-trailing-lambda parameter
) { ... }
```

### @Preview on Dedicated Preview Composable
`@Preview` annotations go on a separate composable suffixed with `Preview` that wraps the real composable inside `SeanimeTVTheme`. Do not annotate the original composable with `@Preview`.

```kotlin
@Preview(showBackground = true)
@Composable
fun AnimeCardPreview() {
    SeanimeTVTheme {
        AnimeCard(anime = fakeAnime, onClick = {})
    }
}
```

### Named Parameters in Composable Calls
Use named parameters for all non-trivial composable arguments (anything beyond a single positional arg). Trailing lambda content blocks may be passed without a name.

```kotlin
Surface(
    modifier = Modifier.fillMaxSize(),
    shape = RectangleShape,
) {
    AnimeCard(anime = item, onClick = { onSelect(item) })
}
```

### Theme Color Naming
The app is dark-theme-only TV UI. The palette is defined as top-level `val` constants named `Seanime<Role>` in `ui/theme/Color.kt` (e.g. `SeanimeBackground`, `SeanimeSurface`, `SeanimePrimary`, `SeanimeOnSurface`, `SeanimeOnSurfaceMuted`, `SeanimeBorder`). Components may import these constants directly rather than routing through `MaterialTheme.colorScheme`. New colors must follow the `Seanime<Role>` naming and live in `ui/theme/Color.kt`.

### Theme Files in `ui.theme` Subpackage
Color, Typography, and Theme files are organized under the `ui.theme` subpackage (`com.malina.seanimetvport.ui.theme`). New theme-related files belong in this package.
