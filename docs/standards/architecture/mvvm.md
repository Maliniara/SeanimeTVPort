# MVVM Architecture Standards

### MVVM Pattern
The application follows Model-View-ViewModel architecture:
- **View**: `@Composable` functions — display state, emit events, no business logic
- **ViewModel**: holds `StateFlow<UiState>`, processes events, calls repositories
- **Model**: data classes and repository interfaces live in the owning domain package, e.g. `data/anilist/model/` and `data/anilist/repository/` (shared cross-domain models in `data/model/`)

```kotlin
class LibraryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun loadLibrary() = viewModelScope.launch {
        _uiState.value = repository.getLibrary().fold(
            onSuccess = { LibraryUiState.Success(it) },
            onFailure = { LibraryUiState.Error(it.message.orEmpty()) }
        )
    }
}
```

### Feature-Sliced Directory Structure
Code is organized by feature. Each feature directory owns its screens, ViewModel, and local state. No cross-feature imports except through shared `data/` or `components/`.

```
app/src/main/java/com/malina/seanimetvport/
├── MainActivity.kt ← NavHost and route constants
├── di/             ← AppContainer, ViewModelFactory
├── feature/
│   ├── home/       ← HomeScreen, HomeViewModel, library/browse/search/schedule content
│   ├── series/
│   ├── player/
│   ├── extensions/
│   ├── pluginstore/
│   ├── settings/
│   ├── login/
│   └── account/
├── data/
│   ├── anilist/    ← client, model/, repository/
│   ├── plugin/
│   ├── extension/
│   ├── metadata/
│   └── ...         ← see data-layer.md
├── components/     ← shared reusable composables
└── ui/
    └── theme/
```

### Single Application Module
The project is a single-module Android app (`:app` only). Do not split into Gradle multi-module without explicit architectural planning. New features go under `app/src/main/java/com/malina/seanimetvport/feature/`.
