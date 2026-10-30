## Error Handling

### Clear User Messages
Show helpful, actionable messages without exposing internal details or security-sensitive information.

### Fail Fast
Validate inputs and check preconditions early; reject invalid data before it causes deeper issues.

### Typed Exceptions
Use specific exception types instead of generic ones to enable precise error handling.

### Centralized Handling
Catch and process errors at appropriate boundaries (controllers, API layers) rather than scattering try-catch throughout.

### Graceful Degradation
When non-critical services fail, continue operating with reduced functionality rather than crashing entirely.

### Retry with Backoff
Use exponential backoff for transient failures when calling external services.

### Resource Cleanup
Always release resources (file handles, connections) in finally blocks or equivalent cleanup mechanisms.

### Kotlin Result for Fallible Operations
Fallible data-layer suspend functions return `kotlin.Result<T>` instead of throwing. Producers wrap work in `runCatching` (or try/catch mapped to `Result.failure`), always rethrowing `CancellationException`. Consumers unwrap with `fold`/`onSuccess`/`onFailure`/`getOrElse` and map failures to the `Error` UiState variant with a user-facing message and a fallback such as `it.message ?: "Unknown error"`.

```kotlin
suspend fun graphql(query: String): Result<JsonObject> =
    withContext(Dispatchers.IO) { runCatching { /* ... */ } }

repository.fetchCatalogue().fold(
    onSuccess = { _uiState.value = UiState.Success(it) },
    onFailure = { e -> _uiState.value = UiState.Error(e.message ?: "Failed to load") }
)
```
