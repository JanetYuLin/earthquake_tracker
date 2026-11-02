# Grounded — session handoff

Notes to start the next session quickly. The **rules** live in `CLAUDE.md`; this file records **state, decisions, open work and lessons**. Update it at the end of each session.

_Last updated: 2026-11-02_

## Current state

- **This is a take-home submission** ("Brick Earthquakes Tracker"). On 2026-11-02 the app was realigned with the brief: device location instead of a typed address; one persisted feed shared by Map, List, details and alerts; stale-data handling; the Safety tab and the Profile extras (name, notes, contacts, address) were cut. `README.md` is the reviewer-facing summary.
- **Nothing is committed.** The repo has only the initial commit, and everything else is untracked. Ask before committing (see `CLAUDE.md`).
- **Build health:** 107 JVM unit tests and 21 instrumented/Compose UI tests pass (UI tests use the v2 `createComposeRule`); `ktlintCheck` is clean; no Kotlin compiler warnings; lint has 0 errors. The remaining lint warnings are deliberate version holds: OkHttp/MockWebServer stay on 4.12 (Retrofit 3.0 targets OkHttp 4; 5.x is a major upgrade) and `kotlinx-coroutines-test` stays on 1.10.2 to match the coroutines runtime the app resolves.
- **Verified on the `Medium_Phone_API_37.0` emulator:** Map centered on the device with a "you are here" marker; List with distances and Recent/Nearest/Strongest; airplane mode plus a process kill still shows the cached feed with a "Couldn't refresh · Updated …" banner; details open offline; with location revoked the map shows the world, distances and Nearest disappear, and an Allow banner shows.
- **ktlint** (`org.jlleitschuh.gradle.ktlint` + `io.nlopez.compose.rules`) was added on 2026-11-02 and the codebase reformatted; `ktlintCheck` is clean. Style is `android_studio` with `max_line_length = 120` (the code was already written to 120) and `compose:parameter-naming` off because past-tense callbacks like `onAlertsToggled` are the project convention. Config is in `.editorconfig`.
- **applicationId is now `io.github.janetyulin.grounded`** (was `…Grounded`). The emulator may still have the old package installed; it's a separate app and can be uninstalled.

### What the app does
| Tab    | Behavior                                                                                                                               | Data                                |
|--------|----------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| Map    | Selected period's M2.5+ worldwide quakes on MapLibre, user marker, refresh, stale banner; tap → bottom sheet (with distance) → details | `EarthquakeRepository.feed(period)` |
| List   | Same quakes; sort Recent/Nearest/Strongest; pull-to-refresh; tsunami chip                                                              | same                                |
| Alerts | Enable toggle (notification permission), min magnitude, radius, disclaimer, attribution                                                | `UserPreferences` (DataStore)       |
| Detail | Pushed on the current tab; distance from you, local time, "If you felt it" safety section                                              | Cached feeds, then FDSN `eventid`   |

The period (Past day / Past week) is one saved preference, so Map and List always show the same data.

## Where things are

```
alerts/   NearbyEarthquakePoller (foreground), ForegroundAlertTrigger (foreground), NearbyAlertWorker + BackgroundAlertTrigger
          + BackgroundAlertScheduler (background), decideAlerts + NearbyAlertCheck (shared rules), NotificationPublisher
core/     LoggingManager(Timber), AnalyticsManager(Sample), AppForegroundMonitor, resultOf(), WhileUiSubscribed
data/     model (Earthquake, EarthquakeFeed/CachedFeeds/EarthquakeFeedState, UserPreferences, UserLocation),
          remote/earthquake (EarthquakeApiService, USGSEarthquakeApiService → DataError, UsgsApi, UsgsDtos, UsgsMapper),
          location (LocationProvider, AndroidLocationProvider), local (AppDataStores: files + factory, JsonSerializer),
          repository (Earthquake, UserPreferences, Location, AlertState)
domain/   model (GeoPoint, Severity, TimePeriod, AlertLevel, AlertRules, DataError)
di/       CoreModule, ManagerModule, NetworkModule, DataModule (repositories, DataStore singletons, location), AlertsModule, Qualifiers
ui/       navigation, map (QuakeMap = MapLibre wrapper), list, alerts, detail (+SafetySection),
          common (EarthquakeUiState/FeedContent, EarthquakeUiMappers, FeedComponents, LocationAccess, ExternalLinks), theme
test/     testing/{Fakes (FakeEarthquakeRepository, FakeLocationRepository, keepCollecting), InMemoryDataStore, Fixtures}
```

## Decision log and why

The alerts pipeline (poller, triggers, shared check, publisher) is diagrammed in `app/src/main/java/io/github/janetyulin/grounded/alerts/README.md`.


**Data**
- **One persisted feed is the source of truth.** `EarthquakeRepository.feed(period)` reads `earthquake_feeds.json` (DataStore + kotlinx JSON). `refresh(period)` writes only on success; failures are kept in memory per period and surfaced as `EarthquakeFeedState.refreshError`. So a failed poll never wipes data, and the banner covers poller failures as well as pull-to-refresh.
- **DataStore over Room**: two small JSON documents, no queries. Room would be justified with filtering across periods or large histories.
- **Worldwide summary feeds** (`2.5_day`, `2.5_week`), as USGS asks for display. The FDSN radius search and the `UsRegions` US filter were removed; nearby is computed from the feed.
- **Typed errors**: `DataError` (Network/Server/Unknown, in `domain`) is mapped to string resources; raw exception messages never reach the UI.

**Location**
- **Coarse permission, platform `LocationManager.getCurrentLocation`** (FUSED if present, else NETWORK) with a 10 s timeout and a last-known fallback. No Play Services dependency. Kept in memory only; never persisted.
- **Denied/unavailable**: everything still works; distances, the user marker and the Nearest sort are hidden; a banner offers Allow, or Settings once permanently denied or when location is off. Alerts need a location and say so.
- Checked on every resume of Map/List/Alerts (`rememberLocationAccess`) and on every poll tick.

**Alerts**
- **Background checks (2026-11-02, product decision reversing foreground-only):** WorkManager periodic work (15 min, network) while alerts are on, measured from the last foreground fix (no background location permission). Notifications look the same in foreground and background. Rules are shared (`decideAlerts`); priming key and handled ids are persisted in `alert_state.json` because a worker starts in a fresh process.
- Notifier filters the shared feed: within radius of the device, ≥ min magnitude, within `RECENT_WINDOW` (5 h), unseen.
- **Priming key is (radius, min magnitude), not the location or period**, otherwise every slightly different fix would re-prime and nothing would ever alert. Without a location, ids are recorded (like "disabled") so the backlog doesn't replay when it arrives.
- Below-threshold quakes aren't recorded, so a USGS upward revision still alerts. Ids are recorded before posting.
- Enabling alerts while notifications are blocked keeps them enabled and shows a warning (the user can fix it in Settings), rather than silently refusing.

**Navigation and UI**
- Tabs Map · List · Alerts; start tab Map; Back pops within a tab, then returns to Map, then exits. Notification deep links open details on the List tab.
- **Only ViewModels and `ui/common/EarthquakeUiMappers.kt` import `data/`**; that file holds the data → UI mappers Map, List and Detail share.
- MapLibre Native `android-sdk-opengl` in a thin `AndroidView` wrapper (maplibre-compose is Beta). OpenFreeMap tiles, attribution kept visible bottom-start.
- Backup: preferences are backed up; `earthquake_feeds.json` is excluded (regenerable, quickly stale). No PII is stored any more.

## Open items, highest value first

1. **Release build is unverified.** Do an R8 build and check the kotlinx-serialization nav keys, `Saver` and DataStore models, Retrofit interfaces/DTOs, and MapLibre consumer rules.
2. **Map performance on a real device is unmeasured.** One `MapView` is now retained for the activity (`QuakeMapHolder`): returning to Map keeps the camera, style and tiles and shows no spinner; only the `SurfaceView` surface is recreated (a blank ~300 ms on the software-rendered emulator). Emulator frame stats are noisy (28–38% janky across runs), so judge on hardware. `textureMode` would remove the blank frame at a per-frame GPU cost.
3. **Review items still open:**
   - `QuakeMap` doesn't forward `onSaveInstanceState`; the camera survives rotation through `MapViewModel` instead.
   - `UsgsMapper` falls back to hard-coded English (`"Earthquake"`, `"Unknown location"`).
   - The map stroke color is hard-coded.
   - No marker clustering; the week feed (~300–500 events) is fine, a month feed would not be.
4. **Test gaps:**
   - No Hilt nav-host test (tab switching, deep link, `onScreenViewed`).
   - Route platform handling (permission results, settings intents) is untested; `AndroidLocationProvider` is untested beyond manual runs.
   - A real alert notification hasn't been seen on a device.
5. **Nice to have:** connectivity callback to refresh when the network returns; server push for timely alerts; minSdk could likely drop to 30 now that the async Geocoder is gone.
6. **Background run on a real device is unobserved over hours** (Doze, standby buckets). On the emulator the worker runs, refreshes and reports SUCCESS, and a JobScheduler-started fresh process initializes Hilt correctly.

## Gotchas learned the hard way

**Shell (zsh)**
- **Always quote heredocs (`<<'EOF'`) when the content has `$`.** An unquoted heredoc once turned `%2$d` into `%2` in strings.xml and crashed the app. Lint catches it (`StringFormatInvalid`).
- **zsh doesn't word-split `$VAR`**, so `sed … $FILES` fails with "File name too long". Pipe through `xargs` instead. BSD `sed -E` has no `\b`; use `[[:>:]]`.
- **zsh special variables:** a loop variable named `path` clobbers `PATH`.

**Testing and the emulator**
- **`connectedDebugAndroidTest` uninstalls the app** afterwards, which wipes both DataStores and the location grant.
- **`stateIn(WhileSubscribed)` needs a collector in tests**: use `keepCollecting(viewModel.uiState)` from `testing/Fakes.kt`.
- **The emulator's default fix is Mountain View**, and `adb emu geo fix` may not apply until an app requests updates. `adb shell pm grant io.github.janetyulin.grounded android.permission.ACCESS_COARSE_LOCATION` skips the dialog; `pm revoke` kills the app.
- **The emulator renders in software** (SwiftShader), so frame timings are pessimistic.
- **Compose semantics:** when two `semantics { contentDescription }` modifiers sit on the same node, the outer one wins. Find test fakes with `testTag`.
- **Posting a notification is asynchronous.** Poll `activeNotifications` in tests.

**MapLibre**
- **The map is a `SurfaceView`** (`GLSurfaceView`; `textureMode` is off). Compose can't fade or alpha it, so any crossfade leaves the map fully visible until its entry leaves. Tab switches therefore use no transition; details slide in as an opaque layer over it. Screens have no background of their own, so the detail entry sets `colorScheme.background` in `GroundedNavHost`; without it the tab below shows through during the slide. `textureMode(true)` would animate correctly but costs a GPU copy per frame.
- **Place a new map's start camera inside `getMapAsync` before adding the camera-idle listener.** Otherwise the default (0, 0) view reports idle first and overwrites the camera `MapViewModel` restores after rotation.
- **A retained `MapView` must be removed from its old parent** before `AndroidView` reattaches it (`QuakeMapHolder.obtainView`).
- **Don't `animateCamera` from the world view to the user**: it loads tiles at every zoom on the way. Start at the user when known, otherwise `moveCamera`.
- `Expression.match` must use the typed form, `match(input, default, *stops)`. The varargs form silently colored every circle black.

**USGS**
- FDSN `eventid` lookups are only a fallback now; summary feeds are the main source. FDSN returns **204 with no body** for no matches.

**WorkManager**
- Force a run with `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler io.github.janetyulin.grounded <jobId>` (Android 14+ needs the namespace). WorkManager skips a forced run inside a period that already ran; toggle alerts off and on for a fresh immediate run.
- The default `WorkManagerInitializer` is removed in the manifest so `GroundedApplication`'s Hilt worker factory is used.

**Lint**
- `MissingPermission` can't see a check in the caller: annotate private helpers with `@RequiresPermission`.

## Useful commands

```bash
./gradlew testDebugUnitTest lint ktlintCheck          # fast checks; ktlintFormat auto-fixes style
./gradlew connectedDebugAndroidTest                   # UI tests (uninstalls app afterwards!)
./gradlew installDebug && adb shell am start -n io.github.janetyulin.grounded/.MainActivity
adb shell cmd connectivity airplane-mode enable       # offline check; "disable" to restore
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -no-window -no-audio -no-snapshot-save -no-boot-anim
adb logcat -s 'NearbyPoller:*' 'EarthquakeRepository:*' 'NearbyAlertCheck:*' 'BackgroundAlertTrigger:*' 'BackgroundAlerts:*' 'WM-WorkerWrapper:*'
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml   # find tap coordinates
```

Bottom-bar tap coordinates on the portrait 1080×2400 emulator: Map `172 2232`, List `540 2232`, Alerts `906 2232`.

### Regenerating the theme
Use Node with `@material/material-color-utilities@0.3.0`. Build a `DynamicScheme` with `TonalPalette.fromInt(seed)` for each role, `variant: TONAL_SPOT` (import `Variant` from `./node_modules/@material/material-color-utilities/dynamiccolor/variant.js`, since the package exports don't expose it), and `contrastLevel: 0`. Emit `MaterialDynamicColors[role].getArgb(scheme)` for every Compose `ColorScheme` role, light and dark. Then run `ColorContrastTest`.

## Suggested next steps
1. Review the working tree and commit in logical chunks, then push to GitHub for submission.
2. Do an R8 release build check.
3. Run on a real device: map performance and a real alert notification.
