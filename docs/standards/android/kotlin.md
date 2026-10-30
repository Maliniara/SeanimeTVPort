# Kotlin Language Standards

### Kotlin Only
All application code must be written in Kotlin. No Java source files in `app/src/`. The current Kotlin compiler version is **2.2.10** — use Kotlin 2.x features freely; do not introduce constructs requiring a newer compiler.

### Kotlin Coroutines for Async
Use Kotlin Coroutines for all asynchronous operations. Repository and API layer methods must be `suspend` functions. Do not use callbacks, `AsyncTask`, or `RxJava`.

```kotlin
// Preferred
suspend fun fetchAnimeLibrary(): List<Anime>

// Avoid
fun fetchAnimeLibrary(callback: (List<Anime>) -> Unit)
```

### Sealed Classes for UI State
Model UI state using sealed classes. Each screen ViewModel file declares its own `sealed class <Feature>UiState` at the top of the same file, with an `object Loading` variant, a `data class Success(...)` (or `Loaded`/`Ready`) variant carrying the screen data, and a `data class Error(val message: String)` variant. Keep the UiState co-located with its ViewModel, one per screen.

```kotlin
sealed class LibraryUiState {
    object Loading : LibraryUiState()
    data class Success(val items: List<Anime>) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}
```

### StateFlow + collectAsState for State
ViewModel state is held in a `private val _x = MutableStateFlow(...)` (leading-underscore name) and exposed read-only as `val x: StateFlow<T> = _x.asStateFlow()`; the public property drops the underscore. Composables consume state only via `collectAsState()`. Never expose a `MutableStateFlow` publicly.

```kotlin
// ViewModel
private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

// Composable
val uiState by viewModel.uiState.collectAsState()
```

### Dispatchers.IO at the Data-Layer Edge
All async work starts from `viewModelScope.launch` in ViewModels. Blocking network/serialization work is shifted with `withContext(Dispatchers.IO)` inside data-layer clients only — never in ViewModels or composables. Derived reactive state uses `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)`.

### Official Kotlin Code Style
Kotlin code follows the official JetBrains code style (4-space indentation, official formatting conventions), declared via `kotlin.code.style=official` in `gradle.properties`. No ktlint/detekt/lint.xml/.editorconfig are in use — this property is the sole formatting configuration.
