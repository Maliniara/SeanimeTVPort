## Test Writing

### Test Behavior
Focus on what code does, not how it does it, to allow safe refactoring.

### Clear Names
Use descriptive names explaining what's tested and expected (`shouldReturnErrorWhenUserNotFound`).

### Mock External Dependencies
Isolate tests by mocking databases, APIs, and external services.

### Fast Execution
Keep unit tests fast (milliseconds) so developers run them frequently.

### Risk-Based Testing
Prioritize testing based on business criticality and likelihood of bugs.

### Balance Coverage and Velocity
Adjust test coverage based on project needs and team workflow.

### Critical Path Focus
Ensure core user workflows and critical business logic are well-tested.

### Appropriate Depth
Match edge case testing to the risk profile of the code.

## Android-Specific Testing

### Test Naming
Test methods use camelCase segments joined by underscores describing subject, condition, and expectation (`subject_condition_expectation` or `shouldX_whenY`), e.g. `logout_clearsToken`, `shouldShowError_whenGetViewerFails`. Backtick function names are not used. Each test carries a short comment stating the behavior verified. This refines the single-camelCase example above — underscore-segmented is the project convention.

### Compose UI Tests via ui-test-junit4
Instrumented UI tests use `androidx.compose.ui:ui-test-junit4`. Debug builds include `ui-test-manifest` for test activity support. Test classes use `@RunWith(AndroidJUnit4::class)` with `@get:Rule val composeTestRule = createComposeRule()`. `setContent` always wraps the component under test in `SeanimeTVTheme`. Assertions target semantics via `onNodeWithText`/`onNodeWithTag`; production code adds `Modifier.testTag` for non-text nodes.

```kotlin
composeTestRule.setContent { SeanimeTVTheme { AnimeCard(/* ... */) } }
composeTestRule.onNodeWithTag("progress_indicator").assertIsDisplayed()
```

```kotlin
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun continueWatchingRowDisplayed_whenEpisodesExist() {
        composeTestRule.setContent {
            SeanimeTVTheme { HomeScreen(viewModel = fakeViewModel) }
        }
        composeTestRule.onNodeWithText("Continue Watching").assertIsDisplayed()
    }
}
```

### Mock the Seanime API Client in Unit Tests
The `SeanimeApiClient` must be mocked or faked in ViewModel unit tests. Do not make real HTTP calls in unit tests. Use a fake implementation of the repository interface.

```kotlin
class LibraryViewModelTest {
    private val fakeRepository = FakeLibraryRepository()
    private val viewModel = LibraryViewModel(fakeRepository)

    @Test
    fun uiState_isSuccess_whenLibraryLoads() = runTest {
        viewModel.loadLibrary()
        assertIs<LibraryUiState.Success>(viewModel.uiState.value)
    }
}
```

### Hand-Written Fake Test Doubles
ViewModel unit tests inject hand-written fakes named `Fake<Interface>` that implement the repository interface with constructor-configurable `Result` values and public call-recording lists. No mocking library (Mockito/MockK) is used or permitted. Fakes live in the test tree mirroring the interface's package.

```kotlin
class FakeAniListRepository(
    private val viewerResult: Result<AniListUser> = Result.success(defaultUser)
) : AniListRepository {
    val mediaListCallArgs = mutableListOf<Pair<Int, MediaListStatus>>()
    // ...
}
```

### Coroutine Test Harness
ViewModel tests are annotated `@OptIn(ExperimentalCoroutinesApi::class)`, hold a test dispatcher (`StandardTestDispatcher` or `UnconfinedTestDispatcher`) as a field, call `Dispatchers.setMain` in `@Before` and `Dispatchers.resetMain` in `@After`, run each test body with `runTest`, and drive pending coroutines with `advanceUntilIdle()` before asserting on `uiState.value`.

### Test Dependency Stack
Unit tests use JUnit 4 with kotlinx-coroutines-test and OkHttp MockWebServer — HTTP is always mocked, never real. Instrumented UI tests use `androidx.compose.ui:ui-test-junit4` with `androidx.test.ext:junit`; `ui-test-manifest` and `ui-tooling` are `debugImplementation` only.
