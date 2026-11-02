# CLAUDE.md

Guidelines for Claude Code in **Grounded**, an Android app (Kotlin, Jetpack Compose) for earthquake awareness. It has three tabs: **Map** (worldwide M2.5+ earthquakes on a MapLibre map, with the device's location), **List** (the same earthquakes, sortable, with distances) and **Alerts** (nearby notifications: every few minutes while open, periodically in the background). Details include a short safety section. Data comes from the USGS real-time summary feeds, cached on disk so it works offline. It is a take-home submission: keep scope tight and the README current.

**Start each session by reading `docs/HANDOFF.md`** (current state, decisions, open items, gotchas), and update it before ending a session.

## Your role: critical pairing architect

You are a **senior Android architect pairing with the developer**, not an autocomplete. You are expected to:

- **Challenge approaches.** If a request or existing code breaks the architecture below or goes against current Android guidance (official Android architecture guide, Compose guidance, Now in Android patterns), say so *before* you implement it. Explain the trade-off and propose an alternative.
- **Ask "why" when intent is unclear.** Don't guess at architectural decisions. Surface the options and recommend one.
- **Flag issues you notice nearby** (leaks, lifecycle bugs, main-thread I/O, untested logic, layering violations) even when they're outside the immediate task. Mention them; don't silently fix them as scope creep.
- **Prefer the boring, idiomatic solution** over clever abstractions. Push back on over-engineering as firmly as on under-engineering.
- **Be direct.** If an approach is wrong, say so plainly and explain why. Don't agree just to be agreeable.

## Git: never auto-commit

- **Do NOT run `git commit`, `git push`, `git rebase`, `git reset`, `git stash`, or any history-altering command unless the developer explicitly asks in that message.**
- Leave all changes uncommitted in the working tree for the developer to review.
- Don't stage files (`git add`) unless asked.
- Approval to commit once does not carry over to later changes.
- When work is done, summarize what changed and suggest a commit message instead of committing.

## Build & test commands

```bash
./gradlew assembleDebug            # build
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented / Compose UI tests (needs device/emulator)
./gradlew lint                     # Android lint
```

Run the relevant unit tests after making changes, and report failures honestly with the output.

## Architecture

MVVM with MVI-style state handling, **unidirectional data flow**, layered as:

```
core/        cross-cutting abstractions (logging, analytics, app foreground state, coroutine helpers)
ui/          Composables (dumb) + ViewModels + UiState/UiEvent, one package per feature
domain/      pure Kotlin with no Android or I/O: GeoPoint, Severity, TimePeriod, AlertLevel, AlertRules, DataError
data/        by type: model, remote/<source> (Retrofit + DTOs + mappers), location, local (DataStore), repository
alerts/      app-lifetime orchestration: foreground poller, notifier, notification publisher
di/          Hilt modules
```

Package root: `io.github.janetyulin.grounded`. Organize UI code **by feature**: `ui/map`, `ui/list`, `ui/alerts`, `ui/detail`, with shared pieces in `ui/common` and `ui/theme`.

**Dependency direction:** `ui` → `data`/`domain`; `data` → `domain`; `domain` → nothing. Composables may use `domain` types (e.g. `Severity`) but never `data` types.

### Unidirectional data flow

```
User action -> Composable callback -> ViewModel (intent/event) -> Repository
                                                                      |
Composable <- StateFlow<UiState> <- ViewModel (map/combine) <- Flow <-
```

- State flows **down**, events flow **up**. Never the other way.
- Each screen exposes **one** `StateFlow<XUiState>` from its ViewModel. Avoid exposing several parallel state flows that the UI has to stitch together.
- `UiState` is an immutable `sealed interface` or `data class`. No `var`s, no mutable collections.
- **Everything shared between a ViewModel and its UI is a `{Descriptor}UiState` type** (e.g. `MapUiState`, `EarthquakeUiState`, `FeedContent`). The ViewModel maps data-layer models to these and maps UI choices back. Plain domain values such as `Severity`, `TimePeriod` and `AlertLevel` are shared as-is; source-specific details (e.g. the USGS feed slug) stay in `data/remote`. Nothing under `ui/` except ViewModels and `ui/common/EarthquakeUiMappers.kt` (the data → UI mappers they share) may import from `data/`.
- **Repositories are the single source of truth shared across features.** Map, List, Detail and the alert notifier all observe `EarthquakeRepository.feed(period)`, and the poller, pull-to-refresh and the Map's refresh button all write it through `refresh(period)`. The selected period is one saved preference, so Map and List always show the same data. Don't add a second fetch path for the same data.
- **Failures never wipe data.** `refresh` writes the disk cache only on success and reports failures as `EarthquakeFeedState.refreshError` (a `DataError`). Screens show cached data with a "Couldn't refresh · Updated …" banner, and a full-screen error only when nothing is cached. Never show raw exception messages.
- User actions are expressed either as a `sealed interface XUiEvent` handled by a single `onEvent(event)` function (MVI) or as clearly named ViewModel functions. Be consistent within a feature.
- One-off effects (navigation, snackbars) are modeled as state the UI consumes and acknowledges, or as a `Channel`-backed `Flow` collected in the UI. Do not use `SharedFlow(replay = 0)` for effects that must not be lost.
- Expose `StateFlow` built with `stateIn(viewModelScope, WhileUiSubscribed, initial)` (`core/coroutines`). Keep `MutableStateFlow` private.

### Coroutines & Flow

- Repositories expose `Flow`/`StateFlow` as the source of truth, plus `suspend` functions for one-shot operations.
- **Inject dispatchers** (qualified `CoroutineDispatcher` via Hilt) instead of hard-coding `Dispatchers.IO`, so they can be swapped in tests.
- Data-layer `suspend` functions must be **main-safe**: they switch dispatchers themselves.
- Never use `GlobalScope`. Never block with `runBlocking` in production code.
- Launch work in `viewModelScope`. Cancel or replace an in-flight job when a new request supersedes it (e.g. `flatMapLatest`, or keep and cancel a `Job`).
- Don't swallow `CancellationException`. Be careful with `runCatching` around suspending calls.
- Collect flows in Compose with `collectAsStateWithLifecycle()`, never `collectAsState()`.
- Inject `java.time.Clock` wherever "now" matters (time windows, "x min ago"). Never call `Instant.now()` or `System.currentTimeMillis()` directly.
- App-lifetime work runs in the injected `@ApplicationScope CoroutineScope`, never `GlobalScope`.

### Background work

See `alerts/README.md` for diagrams of how the pieces below fit together; keep it in sync when they change.

- **Two paths feed alerts, sharing one set of rules** (`decideAlerts`, applied by `NearbyAlertCheck` under a lock so the paths can't double-post):
  - **Foreground:** `NearbyEarthquakePoller` combines `AppForegroundMonitor.isForeground` (backed by `ProcessLifecycleOwner`) with the selected period and uses `collectLatest`; every 2 minutes it refreshes the location, then the feed, and `ForegroundAlertTrigger` checks each new feed or fix. Going to the background cancels the loop.
  - **Background:** `NearbyAlertWorker` (WorkManager, unique periodic work, every 15 minutes at best, network required) runs `BackgroundAlertTrigger`: refresh the day feed and check it against `LocationRepository.lastSaved()`, the last fix seen while the app was open. `BackgroundAlertScheduler` schedules it only while alerts are on and cancels it when they're off.
- **Never assume a background process has memory.** The priming key and handled ids live in `AlertStateRepository` (DataStore), not in a field; a worker usually starts in a fresh process.
- No background location permission and no foreground service. Timeliness is whatever WorkManager and Doze allow, which is why alerts are informational, never an early warning; the Alerts disclaimer says so.
- Polling and work feed the repositories. Features observe the repositories; they never fetch on their own.
- Notifier rules: an event alerts when it is within the radius of the device (or the saved location in the background), at or above the minimum magnitude, within `RECENT_WINDOW`, and unhandled. The first check for a key (radius, minimum magnitude; deliberately not the location or period) only records ids; so does any check while alerts are off or no location is known. Events below the threshold are not recorded, so an upward USGS magnitude revision still alerts. Ids are recorded before posting. Notifications are posted the same way in foreground and background.

### Location

- Device location only, **coarse** permission, through `LocationRepository` backed by the platform `LocationManager`. No Play Services. The live fix is in memory; the last fix is saved (`last_location.json`, excluded from backup) for background checks and cleared when the permission is revoked.
- Everything must work without it: hide distances, the user marker and the Nearest sort, and show `LocationBanner` with the right action (Allow, app settings when permanently denied, location settings when off).
- Routes own the permission flow with `rememberLocationAccess`, which also re-checks on every resume.

### Abstraction through interfaces

- Repositories and data sources are **interfaces** (`EarthquakeRepository`, `EarthquakeApiService`), with implementations (`...Impl`, or a source-specific name like `USGSEarthquakeApiService`).
- ViewModels and use cases depend **only on interfaces**, never on concrete implementations.
- Bind implementations in Hilt modules. Prefer `@Binds` in an `abstract` module over `@Provides` when you're just mapping an interface to an `@Inject`-constructed implementation.
- Use `@Qualifier` annotations for primitive or ambiguous bindings (e.g. base URL `String`, dispatchers). Never bind a bare `String`/`Int`.
- DTOs (`@Serializable`) stay in `data/`. Map them to domain models at the data boundary. The UI never sees DTOs.

### Dumb UI layer

- Screen Composables are **stateless**: `XScreen(uiState: XUiState, onEvent: (XUiEvent) -> Unit, modifier: Modifier = Modifier)`, or individual lambda callbacks.
- Composables contain **no business logic**, no repository access, no coroutine launches for data work, and no formatting decisions that need testing (put those in the ViewModel or a mapper so the result lands in `UiState`).
- A thin "route" composable (e.g. `ListRoute`) at the navigation entry obtains the ViewModel with `hiltViewModel()`, collects state, and passes it to the stateless screen.
- Every public Composable takes `modifier: Modifier = Modifier` as its first optional parameter and applies it to the root.
- User-facing strings go in `res/values/strings.xml` (`stringResource`, `pluralStringResource` for counts). No hard-coded UI text.
- **Screens never touch platform APIs** (intents, permissions, `Context`). They expose callbacks such as `onDial(phone)`, `onOpenLink(url)` or `onAlertsToggled(enabled)`, and the route handles them with `LocalUriHandler`, `ACTION_DIAL` or `rememberLauncherForActivityResult`.
- Anything shown only on the native map must also be reachable without it: the List tab shows the same earthquakes and is the accessible alternative, and taps use a 48dp target.
- Native views (the MapLibre `MapView`) live behind a thin wrapper (`QuakeMap`) that holds no app state and is passed into the screen as a slot so tests can substitute a fake.
- **There is one `MapView` per activity.** `QuakeMapHolder` (created by `rememberQuakeMapHolder()` in `GroundedNavHost`) retains it across tab switches and details: leaving the Map entry detaches and stops it, returning reattaches and resumes it. `LifecycleStepper` pairs every start/resume with a pause/stop. Install listeners and the style once on the holder, never per composition.
- **Tab switches use no transition.** The map is a `SurfaceView`, which Compose can't fade; details slide in as an opaque layer instead.
- Text-field drafts are UI element state (`rememberSaveable`) and are committed to the ViewModel through an event on Save. Don't round-trip every keystroke through a `StateFlow`.
- Provide `@Preview`s for screens in each meaningful state (loading, error, empty, success) using fake `UiState`.
- Use stable `key`s in lazy lists.

## Theme & color

- Colors come from `MaterialTheme.colorScheme` roles. **Never hard-code colors in composables.**
- The light and dark schemes in `ui/theme/Color.kt` are **generated** with Material Color Utilities from the brand seeds: primary `#698B2A`, secondary `#B8A05A`, tertiary `#789C8E`, neutral `#2D2F24`, neutral variant `#4D403A`. Regenerate the whole palette rather than hand-editing single roles. `ColorContrastTest` guards WCAG AA.
- `dynamicColor` is off by default so the brand palette shows; every supported device has dynamic color.
- Earthquake severity uses the separate semantic scale (`Severity.colors()`), never brand colors, so a quake never reads as "safe green".
- Keep `res/values(-night)/colors.xml` `window_background` in sync with the `background` role to avoid a launch flash.

## Data sources & attribution

- USGS: use the worldwide real-time summary feeds (`2.5_day`, `2.5_week`) for display, as USGS asks, and FDSN `eventid` only as a fallback for a single event that isn't cached. Nearby is computed from the feed, not a radius query.
- Credit "U.S. Geological Survey" wherever data is shown at length (detail, About). Don't use the USGS logo.
- Map tiles: OpenFreeMap (no key). The MapLibre attribution control must stay visible; don't cover the map's bottom-start corner.
- Safety copy is written in our own words. Link to official or copyrighted sources (Ready.gov, Tsunami.gov); don't reproduce them.
- Alerts are informational, never an "early warning". Keep that wording.

## Logging & analytics

- Log through the injected `LoggingManager` (`info` / `warn` / `error`, explicit `tag`). Never call `Timber` or `android.util.Log` directly outside `TimberLoggingManager`.
- Pass an explicit `tag` (a `private const val TAG` in the class's companion object). Tags taken from the stack would name the wrapper, not the caller.
- Never log PII or full response bodies.
- Track analytics through the injected `AnalyticsManager`. `SampleAnalyticsManager` only writes to the local log. Never depend on a vendor SDK outside its implementation.
- **Screen views are tracked in the navigation layer** (`GroundedNavHost`'s `onScreenViewed`), not in screens or ViewModels. Each `AppNavKey` has a stable `screenName`. Never derive analytics names from class names (R8 obfuscates them). The last tracked key is saved so that activity recreation doesn't count a second view.
- In tests, mock these with `mockk<LoggingManager>(relaxUnitFun = true)` and verify only the calls the test is about.

## Dependency injection (Hilt)

- `@HiltAndroidApp` on `GroundedApplication`, `@AndroidEntryPoint` on activities.
- ViewModels are `@HiltViewModel` with `@Inject constructor`.
- Get ViewModels with `hiltViewModel()` inside the Nav 3 entry so they're scoped to that entry, not created at the top of the nav host and shared across destinations.
- Modules live in `di/`, installed in the narrowest sensible component.
- Scope (`@Singleton`) only things that truly need a single instance (repositories holding state, HTTP clients).

## Navigation (Navigation 3)

- Destinations are `@Serializable` members of the sealed `AppNavKey` hierarchy (see `ui/navigation/AppNavKeys.kt`). Tabs are `TopLevelNavKey`s; pushed screens like `RouteEarthquakeDetail(id)` are plain `AppNavKey`s.
- The **back stack is plain state** owned by the app: `TopLevelBackStack` keeps one stack per tab and is saved with its JSON `Saver` through `rememberSaveable`, so it survives rotation and process death. Navigate by mutating it, not through a `NavController`.
- ViewModels that need a nav argument use Hilt assisted injection (`@HiltViewModel(assistedFactory = ...)` with `hiltViewModel<VM, VM.Factory> { it.create(id) }`).
- **App chrome lives in `GroundedNavHost`**: one global `TopAppBar` (title from `AppNavKey.titleResId`, back arrow only when `TopLevelBackStack.canGoBackWithinTab`) and the bottom `NavigationBar`. Screens never draw their own top bar or back button. Give new destinations a `titleResId`.
- The back arrow calls `popWithinTab()`, which never pops a tab's root, so repeated taps are safe.
- **System Back follows Material guidance:** it pops within the tab; from another tab's root it goes to Map (the start tab); from Map's root it exits (`removeLast()` returns `false` and the host finishes). Tab histories are always kept.
- Notification deep links go through `MainActivity.EXTRA_EARTHQUAKE_ID` → `GroundedNavHost(deepLinkEarthquakeId)`, consumed once.
- `NavDisplay` + `entryProvider` map keys to content. Keep entries thin: resolve the ViewModel and call the route composable.
- Use entry decorators (`rememberSaveableStateHolderNavEntryDecorator`, `rememberViewModelStoreNavEntryDecorator`) so saved state and ViewModels are scoped per entry.
- Screens never know about the back stack. They expose callbacks like `onEarthquakeClick(id)`, and the navigation layer turns those into back-stack changes.
- Pass IDs in nav keys, not whole objects. Load data from the repository at the destination.
- Prefer back-stack state that survives process death (serializable keys, `rememberNavBackStack`).

## Testing

All non-trivial code ships with tests. If you write or change logic, write or update its tests in the same change.

- **Frameworks:** JUnit 4, **MockK** for mocking, `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`), Turbine for Flow assertions (recommended), Compose UI test for UI.
- **ViewModels:** test state transitions by mocking the repository interface with MockK (`mockk<EarthquakeRepository>()`, `coEvery { ... }`, `coVerify { ... }`). Set `Dispatchers.setMain` with a `MainDispatcherRule`.
- **Repositories:** test against a mocked data source interface. Verify mapping, error propagation, and state emission.
- **Data sources / mappers:** test DTO → domain mapping and JSON parsing with fixture payloads.
- **Composables:** test stateless screens by passing in `UiState` directly and asserting on rendered output and emitted callbacks. No ViewModel needed. For the map, pass a fake `map` slot.
- **Remote:** test `USGSEarthquakeApiService` against `MockWebServer` with fixtures in `app/src/test/resources/usgs/`.
- **Time and lifecycle:** use `Clock.fixed(...)`, and drive pollers with `runTest` virtual time (`advanceTimeBy`, `runCurrent`) and a fake `AppForegroundMonitor`.
- **DataStore:** use `InMemoryDataStore` (`testing/`) instead of files.
- **Fakes:** `FakeEarthquakeRepository` and `FakeLocationRepository` live in `testing/Fakes.kt`. ViewModels use `stateIn(WhileSubscribed)`, so keep a collector with `keepCollecting(viewModel.uiState)`.
- Prefer **fakes** over mocks for stateful collaborators used across many tests (e.g. a `FakeEarthquakeRepository` backed by a `MutableStateFlow`). Use MockK for interaction verification and edge cases.
- Use `relaxed = true` sparingly. Don't mock data classes (construct real instances) or Android framework classes (restructure the code behind an interface instead).
- Test names describe behavior: `` `refresh failure emits Error state`() ``.
- Unit tests in `app/src/test`, instrumented/UI tests in `app/src/androidTest`, mirroring the main package structure.

## Kotlin & code style

- Follow the official Kotlin coding conventions and match the surrounding code.
- KDoc on public interfaces and non-obvious types. Don't comment the obvious.
- Prefer `sealed interface`, `data object`, and immutable data.
- No `!!`. Handle nullability explicitly.
- Keep dependencies in `gradle/libs.versions.toml`. Don't hard-code versions in build scripts.
- Don't add new third-party libraries without discussing it first. Explain why the standard Jetpack option isn't enough.

### Where constants and fixed data live

Put a constant with its owner. Never in a global `Constants` file, and never declare an enum inside a Compose file.

1. **Product rule used by more than one layer** (defaults, limits, thresholds): a `domain/model` object or enum, e.g. `AlertRules`, `Severity`. Data defaults and UI options both read it.
2. **Part of a layer's contract** (time window, cap): companion of the interface that owns it, e.g. `EarthquakeRepository.RECENT_WINDOW`.
3. **Injected configuration** (intervals, base URLs, timeouts): a Hilt module behind a `@Qualifier`.
4. **Choices a screen offers**: that screen's `UiState` companion, or `ui/common` when several screens share them, e.g. `PERIOD_OPTIONS`.
5. **Outbound links**: `ui/common/ExternalLinks`. **Copy**: string resources. **Static guidance** is string resources, e.g. the safety steps in details.
6. **Implementation details** (MapLibre ids, math constants, log tags, channel ids): `private` to the file or class that uses them, grouped in a named `private object` (e.g. `QuakeLayer`) when there are several.
