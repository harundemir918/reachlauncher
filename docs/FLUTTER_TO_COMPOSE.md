# Flutter → Compose (and Android) for Reach

**v1 (M0) — extended each milestone.**

## How to read this

You already know how to build apps; this file maps what you know from Flutter/Dart
(clean architecture, BLoC, get_it/injectable, Dio) onto what Reach uses: Kotlin, Jetpack
Compose, coroutines/Flow, Hilt, Room and DataStore.

- The **quick map** gives you the one-line equivalent of each concept.
- Each **section** then shows a short Kotlin example (and Dart where it helps).
- Names in `code` that exist in this repo today are **real** — you can jump to them.
  Examples marked *(illustrative, M1+)* show code that will exist later; they use the real
  interface names but the implementation isn't written yet.
- Kotlin syntax primer in one breath: `val` = `final`, `var` = mutable, `fun` = function,
  types after names (`label: String`), `data class` ≈ a freezed/equatable class with
  `copy()`, `object` = a singleton, trailing lambdas: `Column { … }` is `Column(content = { … })`.

---

## Quick map

| Flutter / Dart | Compose / Android in Reach |
|---|---|
| `Widget` | `@Composable` function |
| `StatelessWidget` / `StatefulWidget` | composable function + `remember` / `rememberSaveable` |
| `setState` | `mutableStateOf` (writes trigger recomposition) |
| BLoC / Cubit | `ViewModel` + `StateFlow<UiState>` |
| `BlocBuilder` / `BlocListener` | `collectAsStateWithLifecycle()` / collecting an event `Flow` in `LaunchedEffect` |
| `Stream` / `BehaviorSubject` | `Flow` (cold) / `StateFlow` (hot, has a current value) |
| `Future` / `async`/`await` | `suspend fun` + coroutines; `viewModelScope` ≈ BLoC lifetime |
| get_it / injectable | Hilt (`@Inject constructor`, `@Module @InstallIn`, `@Binds`, `@HiltViewModel`) |
| `shared_preferences` | DataStore |
| sqflite / drift | Room (`@Entity`, `@Dao`, `@Database`); KSP ≈ build_runner |
| `Theme.of(context)` / `ThemeExtension` | `CompositionLocal` — `ReachTheme.colors` reads `LocalReachColors` |
| `Column` / `Row` / `Stack` | `Column` / `Row` / `Box` |
| `Padding` / `SizedBox` / `Container` | `Modifier` chains (order matters) |
| `ListView.builder` | `LazyColumn` |
| `GestureDetector` | `Modifier.clickable`, `Modifier.pointerInput` |
| `Navigator` | not used — a state machine in `HomeViewModel` |
| `MethodChannel` | not needed — call Android APIs directly |
| `pubspec.yaml` | `gradle/libs.versions.toml` + each module's `build.gradle.kts` |
| `flutter run` / `test` / `analyze` | `./gradlew installDebug` / `test` / `lint`, or Android Studio Run |
| `AnimatedContainer`, implicit animations | `animate*AsState`, `AnimatedContent`, `AnimatedVisibility` |
| hot reload | Live Edit + Compose Preview (`@Preview`) |
| `BuildContext` | none — composition locals, e.g. `LocalContext.current` |
| `main()` / `runApp()` | `MainActivity.setContent { … }` |
| `MediaQuery.padding` / `SafeArea` | `WindowInsets`, `Modifier.safeDrawingPadding()`, edge-to-edge |
| `intl` / ARB files | `res/values/strings.xml` + `res/values-tr/strings.xml` |
| `const` widgets, avoiding rebuilds | recomposition skipping, `@Immutable` / `@Stable` |

---

## UI building blocks

### Widget ↔ `@Composable`

A composable is a **function** that emits UI; there is no widget object you construct and
keep. Name it in PascalCase like a widget (ktlint is configured to allow that).

```kotlin
// app/.../MainActivity.kt (real)
@Composable
private fun FoundationPlaceholder() {
    val colors = ReachTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Text(text = stringResource(R.string.foundation_title), style = ReachTheme.typography.clock, color = colors.fg)
    }
}
```

```dart
class FoundationPlaceholder extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Column(children: [Text('Reach')]);
}
```

### StatelessWidget / StatefulWidget ↔ composables + `remember` / `rememberSaveable`

There's no Stateless/Stateful split. A composable that needs local state asks for it with
`remember` (survives recomposition) or `rememberSaveable` (also survives configuration
changes and process death, like `RestorationMixin`).

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableIntStateOf(0) }          // ≈ int count = 0 in State<T>
    var query by rememberSaveable { mutableStateOf("") }    // ≈ a restorable field
    Text("$count", Modifier.clickable { count++ })
}
```

Reach's rule: **screen state lives in ViewModels**, `remember` is for purely visual,
local state (a pressed highlight, a scroll offset).

### `setState` ↔ `mutableStateOf`

Writing to a `MutableState` *is* the `setState` call: Compose tracks which composables
read it and re-runs only those.

```kotlin
var expanded by remember { mutableStateOf(false) }
Text(if (expanded) "Less" else "More", Modifier.clickable { expanded = !expanded })
```

```dart
setState(() => expanded = !expanded);
```

### Column / Row / Stack ↔ Column / Row / Box

```kotlin
Column(verticalArrangement = Arrangement.Bottom) { /* ≈ MainAxisAlignment.end */ }
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { }
Box(contentAlignment = Alignment.BottomStart) {     // ≈ Stack(alignment: …)
    Image(/* … */)
    Text("on top", Modifier.align(Alignment.TopEnd)) // ≈ Positioned / Align
}
Spacer(Modifier.weight(1f))                          // ≈ Spacer() / Expanded inside a Column/Row
```

### Padding / SizedBox / Container ↔ `Modifier` chains (order matters)

Flutter wraps widgets in widgets; Compose decorates one composable with an ordered chain
of modifiers. **Each modifier wraps everything after it**, so order changes the result:

```kotlin
// Background fills the padded area (padding is *inside* the colour) — like
// Container(color: …, padding: EdgeInsets.all(16))
Modifier.background(colors.surface).padding(16.dp)

// Padding first: a 16dp transparent margin, then the colour — like
// Padding(padding: 16, child: ColoredBox(color: …))
Modifier.padding(16.dp).background(colors.surface)

// Same for taps: clip → clickable gives a ripple clipped to the pill shape;
// clickable → clip lets the ripple spill outside it.
Modifier.clip(CircleShape).clickable { }.padding(12.dp)
```

`SizedBox(width: 42, height: 42)` ↔ `Modifier.size(42.dp)`;
`ConstrainedBox(minHeight: 48)` ↔ `Modifier.heightIn(min = 48.dp)`;
`Expanded` ↔ `Modifier.weight(1f)` (only inside `Row`/`Column`).

Real example from `MainActivity`: background → system-bar insets → gutter padding.

```kotlin
Modifier
    .fillMaxSize()
    .background(colors.bg)              // paint edge to edge, behind the system bars
    .safeDrawingPadding()               // then keep content out of bars/cutout/IME
    .padding(ReachTheme.dimens.gutter)  // then the 24dp gutter
```

### `ListView.builder` ↔ `LazyColumn`

```kotlin
LazyColumn {
    items(apps, key = { it.key }) { app ->   // key ≈ ValueKey: keeps item state stable
        Text(app.label)
    }
}
```

`LazyRow` ↔ horizontal `ListView`; `LazyVerticalGrid` ↔ `GridView.builder`. Don't put a
`LazyColumn` inside a vertically scrolling `Column` (same "unbounded height" trap as
nesting `ListView` in `Column` without `shrinkWrap`).

### GestureDetector ↔ `Modifier.clickable` / `Modifier.pointerInput`

Simple taps: `Modifier.clickable { }` / `Modifier.combinedClickable(onLongClick = …) { }`
— these also give semantics for TalkBack. Anything custom goes in `pointerInput`:

```kotlin
Modifier.pointerInput(Unit) {
    detectTapGestures(onDoubleTap = { /* lock */ }, onLongPress = { /* appearance */ })
}

// Low level (press-slide-release in M2 is built like this):
Modifier.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        // …loop over awaitPointerEvent(), track position, decide, consume…
    }
}
```

**Gesture arena vs. consumption.** Flutter resolves competing recognisers in a *gesture
arena* that declares one winner. Compose has no arena: pointer events travel through the
modifier tree, each handler sees them in order, and a handler **consumes** a change
(`change.consume()`) to tell others "this is mine". Others check `isConsumed` and back
off. The full step-by-step write-up comes in `docs/GESTURES.md` (M2).

### `AnimatedContainer` / implicit animations ↔ `animate*AsState` / `AnimatedContent`

```kotlin
val bg by animateColorAsState(if (selected) colors.accent else colors.surface, label = "rowBg")
val height by animateDpAsState(if (expanded) 120.dp else 58.dp, label = "rowHeight")
Box(Modifier.height(height).background(bg))

AnimatedContent(targetState = surface, label = "surface") { s ->  // ≈ AnimatedSwitcher
    when (s) { /* Home, IntentDetail, Search… */ }
}
AnimatedVisibility(visible = isPlaying) { /* play/pause button */ }
```

The `label` parameter only names the animation in Android Studio's animation inspector.

---

## State and architecture

### BLoC / Cubit ↔ `ViewModel` + `StateFlow`

*(illustrative, M1+ — `HomeViewModel` doesn't exist yet; the repository interfaces do)*

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    catalog: AppCatalogRepository,                      // real interface in :core:data
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =               // ≈ the Cubit's state stream
        catalog.apps
            .map { apps -> HomeUiState(apps = apps.filterNot { it.isHidden }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()                 // one-off events (each delivered once)

    fun onIntentTapped(intent: ReachIntent) { /* ≈ add(IntentTapped(intent)) / emit(...) */ }
}
```

In a composable:

```kotlin
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()   // ≈ BlocBuilder
    LaunchedEffect(Unit) {                                          // ≈ BlocListener
        viewModel.events.collect { event -> /* show a toast, open settings… */ }
    }
    HomeScreen(state, onIntentTapped = viewModel::onIntentTapped)
}
```

- `collectAsStateWithLifecycle()` (from `lifecycle-runtime-compose`) stops collecting when
  the app is in the background — prefer it over plain `collectAsState()`.
- `hiltViewModel()` comes from `androidx.hilt.lifecycle.viewmodel.compose` (≈
  `BlocProvider(create: (_) => getIt<HomeCubit>())`).
- A ViewModel survives configuration changes (rotation, dark mode) — like a BLoC provided
  above the `MaterialApp`, not inside the route.

### Streams ↔ `Flow`

| Dart | Kotlin |
|---|---|
| single-subscription `Stream` (e.g. `async*`) | cold `Flow` — runs its producer per collector |
| `BehaviorSubject` / `ValueStream` (rxdart) | `StateFlow` — hot, always has `.value`, replays latest, skips equal values |
| `PublishSubject` / broadcast stream | `SharedFlow` |
| `stream.listen(…)` | `flow.collect { … }` (a `suspend` call, runs in a coroutine) |
| `StreamController` | `MutableStateFlow` / `MutableSharedFlow` / `Channel` |

Real contract and fake:

```kotlin
// :core:data — the catalog is a never-ending stream of the current app list
interface AppCatalogRepository {
    val apps: Flow<List<CatalogApp>>
    suspend fun refresh()
}

// :core:data fake — a MutableStateFlow exposed as Flow (≈ a BehaviorSubject behind a Stream getter)
class FakeAppCatalogRepository(initial: List<CatalogApp> = emptyList()) : AppCatalogRepository {
    val state = MutableStateFlow(initial)
    override val apps: Flow<List<CatalogApp>> = state
    // …
}
```

Operators read like rxdart: `map`, `filter`, `combine` (≈ `Rx.combineLatest`),
`flatMapLatest` (≈ `switchMap`), `distinctUntilChanged`, `debounce`.

### `Future` / `async`/`await` ↔ `suspend` + coroutines

```kotlin
suspend fun refresh()                // ≈ Future<void> refresh()
viewModelScope.launch {              // ≈ fire-and-forget inside a BLoC; cancelled in onCleared()
    catalog.refresh()                // no `await` keyword — suspend calls await implicitly
}
val a = async { loadA() }            // ≈ Future.wait([...])
val b = async { loadB() }
use(a.await(), b.await())
```

- `viewModelScope` ≈ the BLoC's lifetime: everything launched in it is cancelled when the
  ViewModel is cleared (no manual `StreamSubscription.cancel()`).
- Composables use `LaunchedEffect` / `rememberCoroutineScope()` for their own coroutines.
- Cancellation is cooperative and structured: cancelling a scope cancels its children.

### get_it / injectable ↔ Hilt

| injectable | Hilt |
|---|---|
| `configureDependencies()` in `main()` | `@HiltAndroidApp` on `ReachApplication` (real) |
| `@injectable` class | `class X @Inject constructor(…)` |
| `@module abstract class` | `@Module @InstallIn(SingletonComponent::class)` |
| `@LazySingleton(as: Repo)` | `@Binds @Singleton abstract fun bind(impl: Impl): Repo` |
| `getIt<HomeCubit>()` | `@HiltViewModel` + `hiltViewModel()` |
| — | `@AndroidEntryPoint` on `MainActivity` (real): lets Hilt inject into the Activity |
| build_runner | KSP (`ksp(libs.hilt.compiler)`) generates the graph at compile time |

```kotlin
// app/.../ReachApplication.kt (real)
@HiltAndroidApp
class ReachApplication : Application()

// (illustrative, M1+) binding the real interface to a future implementation
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds @Singleton
    abstract fun bindCatalog(impl: RoomAppCatalogRepository): AppCatalogRepository
}
```

Unlike get_it, a missing binding is a **compile error**, not a runtime crash.

### `shared_preferences` ↔ DataStore

DataStore is async and Flow-based (no synchronous `getString`). It will back
`SettingsRepository` in M4. *(illustrative)*

```kotlin
private val Context.settingsStore by preferencesDataStore(name = "settings")
private val PALETTE = stringPreferencesKey("palette")

val palette: Flow<PaletteId> =
    context.settingsStore.data.map { prefs -> PaletteId.fromStorageKey(prefs[PALETTE]) }

suspend fun setPalette(p: PaletteId) {
    context.settingsStore.edit { it[PALETTE] = p.storageKey }   // stored by name, never colours
}
```

### sqflite / drift ↔ Room

Room ≈ drift: annotated classes, generated code, queries checked at compile time,
`Flow` results that re-emit when tables change (≈ drift's `watch()`). Code generation runs
through **KSP** (≈ `build_runner`, but automatic on every build). Reach uses Room in M1 for
launch history and intent assignments. *(illustrative)*

```kotlin
@Entity(tableName = "launch_events")
data class LaunchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // The three parts of AppKey: an app is package + activity + profile, not just a package.
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
    val epochMillis: Long,
)

@Dao
interface LaunchEventDao {
    @Insert suspend fun insert(e: LaunchEventEntity)
    @Query("SELECT * FROM launch_events WHERE epochMillis >= :since ORDER BY epochMillis")
    fun since(since: Long): Flow<List<LaunchEventEntity>>   // ≈ drift .watch()
}

@Database(entities = [LaunchEventEntity::class], version = 1)
abstract class ReachDatabase : RoomDatabase() { abstract fun launchEvents(): LaunchEventDao }
```

Schemas are exported to `core/data/schemas` (see `room { schemaDirectory(…) }` in
`core/data/build.gradle.kts`) so migrations can be tested.

### `Theme.of(context)` / `ThemeExtension` ↔ `CompositionLocal`

A `CompositionLocal` is Compose's `InheritedWidget`. `CompositionLocalProvider` puts a value
into the tree; `LocalX.current` reads the nearest one. Reach's tokens (`ReachColors`) are
the equivalent of a `ThemeExtension`.

```kotlin
// core/designsystem/.../ReachTheme.kt (real, abridged)
val LocalReachColors = staticCompositionLocalOf { ReachColors.of(PaletteId.DEFAULT, isDark = true) }

@Composable
fun ReachTheme(colors: ReachColors, typography: ReachTypography = ReachTypography.Default, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = /* mapped from colors */) {
        CompositionLocalProvider(LocalReachColors provides colors, LocalReachTypography provides typography, content = content)
    }
}

object ReachTheme {
    val colors: ReachColors @Composable @ReadOnlyComposable get() = LocalReachColors.current
}

// Usage anywhere below ReachTheme { }:
Text("Talk", color = ReachTheme.colors.fg)        // ≈ Theme.of(context).extension<ReachColors>()!.fg
```

**`staticCompositionLocalOf` vs `compositionLocalOf`:**
- `compositionLocalOf` tracks *who reads it*; changing the value recomposes only those readers.
  Good for values that change often and are read in a few places.
- `staticCompositionLocalOf` doesn't track readers; changing the value recomposes the
  **whole** content of the provider. Reads are cheaper. Reach uses it for colours and type:
  they're read almost everywhere and change rarely (palette/theme switch), when a full
  recomposition is what we want anyway.

Material 3 is used only as infrastructure; `ReachTheme` maps Reach's tokens onto
`MaterialTheme.colorScheme` so text fields and ripples already look like Reach.

### `Navigator` ↔ a state machine in `HomeViewModel`

Reach deliberately has **no Navigation Compose**. Home, IntentDetail, Search, AllApps and
the Appearance sheet are states of one surface (see [PLAN.md §3](PLAN.md#3-surface-state-machine-in-homeviewmodel)):

```kotlin
// (illustrative, M1)
sealed interface HomeSurface {
    data object Home : HomeSurface
    data class IntentDetail(val intent: ReachIntent) : HomeSurface
    data object Search : HomeSurface
    data object AllApps : HomeSurface
    data object Appearance : HomeSurface
}

when (val s = state.surface) {          // ≈ a BlocBuilder switching on a sealed state
    HomeSurface.Home -> HomeScreen(…)
    is HomeSurface.IntentDetail -> IntentDetailScreen(s.intent, …)
    …
}
BackHandler(enabled = state.surface != HomeSurface.Home) { viewModel.onBack() }  // ≈ PopScope
```

Why: a launcher has a single surface, the **Home button must always return to Home**
(`MainActivity.onNewIntent` → `HomeViewModel.onHomePressed()`), Back steps one level and
does nothing on Home. That is a five-state machine, easier to test as plain ViewModel
logic than a back stack.

### `MethodChannel` ↔ not needed

You're already native. Android APIs are plain Kotlin calls — no channel, no codec, no
platform thread hop:

```kotlin
// (illustrative, M1 — :core:system)
val launcherApps = context.getSystemService(LauncherApps::class.java)
val activities = launcherApps.getActivityList(null, userHandle)
launcherApps.startMainActivity(component, userHandle, sourceBounds, options)
```

In Reach those calls live only in `:core:system`, behind the interfaces in
`:core:data/source` (e.g. `InstalledAppsSource`, `AppLauncher`) — the same role a
platform-interface package plays for a Flutter plugin.

---

## App entry, context and platform

### `main()` / `runApp()` ↔ `MainActivity.setContent`

```kotlin
// app/.../MainActivity.kt (real)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {                 // ≈ runApp(...)
            ReachTheme { FoundationPlaceholder() }
        }
    }
}
```

`FlutterActivity` normally hides this from you. The Activity is declared in
`AndroidManifest.xml` with the HOME intent filter — that declaration, not code, is what
makes Reach a launcher.

### `BuildContext` ↔ there is none

Composables don't receive a context parameter. Anything "from above" is a composition
local: `LocalContext.current` (the Android `Context`, for system services and resources),
`LocalConfiguration.current`, `LocalDensity.current`, `ReachTheme.colors`.

```kotlin
val context = LocalContext.current
val label = stringResource(R.string.app_name)   // resources without passing context around
```

Don't store a `Context` in a ViewModel (it leaks the Activity); inject
`@ApplicationContext context: Context` with Hilt where it's really needed.

### `MediaQuery.padding` / `SafeArea` ↔ `WindowInsets`, edge-to-edge

`enableEdgeToEdge()` lets Reach draw behind the status and navigation bars (mandatory
behaviour when targeting API 35+). Each composable then decides how to avoid them:

```kotlin
Modifier.safeDrawingPadding()                      // ≈ SafeArea: bars + cutout + keyboard
Modifier.windowInsetsPadding(WindowInsets.systemBars)
Modifier.imePadding()                              // ≈ MediaQuery.viewInsets.bottom
```

The manifest's `windowSoftInputMode="adjustResize"` plus these modifiers is how search
results will sit just above the keyboard (M2).

### `intl` / ARB ↔ `strings.xml` + `values-tr/`

```xml
<!-- app/src/main/res/values/strings.xml (real) -->
<string name="foundation_subtitle">Foundation build. Home arrives in M1.</string>
<!-- app/src/main/res/values-tr/strings.xml (real) -->
<string name="foundation_subtitle">Temel sürüm. Ana ekran M1\'de geliyor.</string>
```

```kotlin
Text(stringResource(R.string.foundation_subtitle))   // ≈ AppLocalizations.of(context)!.foundationSubtitle
```

The system picks the folder by device locale — no delegate setup. Apostrophes must be
escaped (`\'`). Plurals use `<plurals>` + `pluralStringResource`. Locale-aware sorting
uses `java.text.Collator`; Turkish-aware matching uses `TextFolding` in `:core:model`.

### `const` widgets / rebuild optimisation ↔ recomposition skipping, `@Immutable` / `@Stable`

Compose **skips** a composable when its parameters are equal to last time — the equivalent
of a `const` widget or `shouldRebuild == false`, but automatic. It can only trust equality
for *stable* types: primitives, `String`, and classes marked `@Immutable`/`@Stable` or
inferred stable.

```kotlin
// core/designsystem/.../ReachColors.kt (real)
@Immutable
data class ReachColors(val isDark: Boolean, val bg: Color, val fg: Color, /* … */)
```

With the Kotlin 2.x Compose compiler, *strong skipping* is on by default, so unstable
parameters are compared by instance (`===`) instead of disabling skipping. Still: pass
`List`s that don't change identity needlessly, hoist lambdas with `remember` when they
capture changing values, and read fast-changing state (scroll, drag offsets) as late as
possible — e.g. in `Modifier.offset { }` lambdas or `drawBehind`.

---

## Tooling

### `pubspec.yaml` ↔ `libs.versions.toml` + `build.gradle.kts`

```toml
# gradle/libs.versions.toml (real) — the "dependencies:" section, versions in one place
[versions]
room = "2.8.5"
[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
```

```kotlin
// core/data/build.gradle.kts (real) — each module picks what it uses
dependencies {
    api(project(":core:model"))                 // ≈ path: ../model  (and re-exported)
    implementation(libs.androidx.room.runtime)  // ≈ a normal dependency
    ksp(libs.androidx.room.compiler)            // ≈ dev_dependency on a build_runner generator
    testImplementation(libs.turbine)            // ≈ dev_dependencies for tests
}
```

`settings.gradle.kts` lists the modules (≈ a melos/pub workspace). `api` vs
`implementation`: `api` leaks the dependency to consumers (like exporting a package),
`implementation` keeps it private.

### `flutter run` ↔ Gradle tasks / Android Studio run configs

| Flutter | Here |
|---|---|
| `flutter run` | Android Studio ▶ Run `app`, or `./gradlew installDebug` + `adb shell am start -n com.baklavapp.reachlauncher/.MainActivity` |
| `flutter test` | `./gradlew test` (or `:core:designsystem:test` for one module) |
| `flutter analyze` | `./gradlew lint` + `./gradlew ktlintCheck` |
| `dart format` | `./gradlew ktlintFormat` |
| `flutter build apk` | `./gradlew assembleDebug` / `assembleRelease` |

See the README for the full table and the milestone gate `./gradlew build test lint`.

### Hot reload ↔ Live Edit / Compose Preview

There's no full hot reload. **Live Edit** (Android Studio) pushes changes to composable
bodies to a running app; structural changes need a rebuild. **`@Preview`** renders a
composable inside the IDE without a device — closest to iterating on a widget in
isolation:

```kotlin
// app/.../MainActivity.kt (real)
@Preview
@Composable
private fun FoundationPlaceholderPreview() {
    ReachTheme { FoundationPlaceholder() }
}
```

Previews can't touch real system services, which is one reason the fakes in
`core.data.fake` live in `src/main` ([DECISIONS D4](DECISIONS.md)).

### Testing

| Flutter | Here |
|---|---|
| `test()` / `expect()` | JUnit 4 `@Test` + Truth `assertThat(x).isEqualTo(y)` |
| `bloc_test` / `expectLater(stream, emitsInOrder(…))` | Turbine: `flow.test { assertThat(awaitItem()).isEqualTo(…) }` |
| `fake_async` | `kotlinx-coroutines-test` `runTest { }` (virtual time) |
| hand-written fakes | `FakeAppCatalogRepository`, `FakeSettingsRepository`, … in `core.data.fake` |
| widget tests | Compose UI tests (`createComposeRule()`), run on the JVM via Robolectric |

```kotlin
// core/designsystem/src/test/.../ColorMathTest.kt (real style)
@Test
fun `contrast of black and white is 21`() {
    assertThat(ColorMath.contrastRatio(Color.Black, Color.White)).isWithin(0.01).of(21.0)
}
```

Backtick names are just Kotlin identifiers with spaces — handy for test names.

---

## Mental model differences that bite Flutter devs

1. **Recomposition isn't rebuilding a tree you hold.** You never get a widget object back
   or keep a reference to one. A composable may run many times, in any order, or be
   skipped; treat its body like `build()` *without* the guarantee it runs at all.
2. **Side effects never go in the composable body.** Anything that starts work, registers a
   listener or logs belongs in an effect: `LaunchedEffect(key) { … }` (≈ `initState` +
   `didUpdateWidget` for coroutines; restarts when `key` changes), `DisposableEffect(key) { …
   onDispose { … } }` (≈ `initState`/`dispose` pairs), `SideEffect { }` for every
   successful recomposition.
3. **Modifier order is semantics.** `padding().background()` ≠ `background().padding()`;
   `clickable` before or after `padding` changes the tap area. Read a chain outside-in.
4. **The Activity lifecycle and configuration changes.** By default Android *destroys and
   recreates* the Activity on rotation, dark-mode toggle, font-scale or locale change.
   ViewModels survive that; `remember` doesn't, `rememberSaveable` does. Reach's
   `MainActivity` opts out for most of them via `android:configChanges` so Home is never
   torn down (no blank flash) — Compose just recomposes with the new configuration
   ([DECISIONS D8](DECISIONS.md#d8--launcher-manifest-flags-m0)).
5. **Process death is normal.** In the background the OS can kill the process and later
   restore the Activity from a saved Bundle — in most apps that's what `rememberSaveable` /
   `SavedStateHandle` are for. Reach is different: it declares `stateNotNeeded`, so after
   process death Home starts **fresh, with no saved state**. That's intended (Home always
   opens on the Home state), but it means anything a user would miss must be persisted in
   Room or DataStore. `rememberSaveable` still helps across configuration changes Reach
   doesn't handle itself.
6. **The main thread is yours to protect.** There's no separate UI isolate. Blocking I/O on
   `Dispatchers.Main` janks or ANRs the app. Room and DataStore already suspend off the
   main thread; for your own work use `withContext(Dispatchers.IO) { … }` (I/O) or
   `Dispatchers.Default` (CPU). Unlike Dart, coroutines on `Dispatchers.Default`/`IO` run
   **truly in parallel**, so shared mutable state needs care (prefer `StateFlow.update`).
7. **Kotlin null safety has a Java-shaped hole.** Kotlin is null-safe like Dart (`String?`,
   `?.`, `?:` ≈ `??`, `!!` ≈ `!`), but Android framework APIs are Java: their return types
   are *platform types* (`String!`) that the compiler won't check. Treat results of system
   calls (`getSystemService`, `getActivityList`, intent extras) as nullable unless the docs
   or annotations say otherwise.
8. **Equality and immutability are on you.** `data class` gives you `==`/`copy()` like
   freezed; plain classes compare by identity. Expose `List`/`Map` (read-only interfaces)
   from state, not `MutableList`, so Compose and `StateFlow` can see changes as new values.

---

## Where to look in this repo

| To see… | Open |
|---|---|
| The "runApp" | `app/src/main/kotlin/com/baklavapp/reachlauncher/MainActivity.kt` |
| The DI root | `app/src/main/kotlin/com/baklavapp/reachlauncher/ReachApplication.kt` |
| Why Reach is a launcher | `app/src/main/AndroidManifest.xml` (HOME filter, `singleTask`, `configChanges`, `<queries>`) |
| Theme ≈ `Theme.of` / `ThemeExtension` | `core/designsystem/.../theme/ReachTheme.kt`, `ReachColors.kt`, `ReachTypography.kt` |
| Palettes and colour maths | `core/designsystem/.../theme/Palettes.kt`, `ColorMath.kt` |
| Contrast tests | `core/designsystem/src/test/.../theme/PaletteContrastTest.kt`, `ColorMathTest.kt` |
| Domain types (≈ your `domain/entities`) | `core/model/src/main/kotlin/.../core/model/` (`App.kt`, `ReachIntent.kt`, `Appearance.kt`, `History.kt`, `SystemData.kt`) |
| Turkish-aware matching | `core/model/.../TextFolding.kt` + `TextFoldingTest.kt` |
| Repository contracts (≈ `domain/repositories`) | `core/data/.../repository/Repositories.kt` |
| System-source contracts (≈ plugin platform interfaces) | `core/data/.../source/SystemSources.kt` |
| Fakes for tests and previews | `core/data/.../fake/FakeRepositories.kt`, `FakeSystemSources.kt` |
| Dependency versions (≈ `pubspec.yaml`) | `gradle/libs.versions.toml`, `*/build.gradle.kts`, `settings.gradle.kts` |
| Lint/format rules (≈ `analysis_options.yaml`) | `.editorconfig`, the `lint { }` blocks in module build files |
| Why things are the way they are | `docs/DECISIONS.md`, `docs/PLAN.md` |

(`…` = `src/main/kotlin/com/baklavapp/reachlauncher`.)

Coming in later versions of this file: Room/DataStore and Hilt modules as they really are
(M1), gestures and `pointerInput` in depth (M2, with `GESTURES.md`), system services and
permissions (M3), and window themes/wallpaper (M4).
