# Data Layer Standards

### Shared Data Layer for All External Communication
All network and persistence access (AniList, plugins, AniSkip/AniZip, DataStore) must go through the shared data layer. Direct API calls from composables or ViewModels are not allowed.

```
┌─────────────────────────────────┐
│   ViewModel (feature/)          │
│   calls repository interface    │
└─────────────────┬───────────────┘
                  ↓
┌─────────────────────────────────┐
│   Repository (data/<domain>/)   │
│   interface + *Impl             │
└─────────────────┬───────────────┘
                  ↓
┌─────────────────────────────────┐
│   Client / store                │
│   OkHttp, QuickJS, DataStore    │
└─────────────────────────────────┘
```

### Data Layer Directory Structure
The data layer is grouped by domain, each package owning its client, models and repository:
```
data/
├── anilist/    ← AniListApiClient, AccountStore, model/, repository/
├── plugin/     ← QuickJSEngine, JS bridges, PluginRepository, model/, store/
├── extension/  ← ExtensionRepository
├── metadata/   ← AniSkipClient, AniZipClient
├── net/        ← shared OkHttp client factories
├── history/ settings/ player/ search/ tvchannel/ model/
```

### Repository Pattern
- Define repository interfaces in the domain package (e.g. `data/anilist/repository/`) — ViewModels depend on the interface, not the implementation
- Implementations handle error mapping (API exceptions → domain errors)
- All methods are `suspend` functions

### Repository Naming and Shape
Data access is defined by a `<Name>Repository` interface whose methods are all suspend functions returning `Result<T>`; ViewModels depend on the interface, and the concrete class is named `<Name>RepositoryImpl` (or a purpose-named class such as `TokenDataStore` implementing the interface). Interface default methods return `Result.failure(UnsupportedOperationException())` so test fakes only implement what they need.

### API Response Models
API response models are `@Serializable` data classes grouped in `data/<domain>/model/` files (several related classes per file, e.g. `AniListModels.kt`). Optional fields are nullable with `= null` defaults or defaulted collections (`= emptyList()`). JSON parsers are built with `Json { ignoreUnknownKeys = true }` so server additions don't break parsing.

```kotlin
@Serializable
data class AniListMedia(
    val id: Int,
    val title: AniListTitle? = null,
    val genres: List<String> = emptyList(),
)
```

### HTTP Clients and Auth
Two OkHttp clients are provided by `AppContainer`. `streamingHttpClient` (plugins, AniSkip, AniZip) tolerates incomplete TLS chains and must never carry the AniList token. `authHttpClient` (token exchange) uses strict TLS in release builds. AniList calls attach the active account's token from `AccountStore`.
