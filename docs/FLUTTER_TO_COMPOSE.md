# Flutter → Compose (and Android) for Reach

**v5 (M4) — extended each milestone.** v1 (M0) introduced the concepts; v2 replaced the
illustrative examples with real code from the M1 launcher (events, lifecycle effects,
`callbackFlow`, nested scroll, semantics, UI tests); v3 adds what M2 brought: custom
gestures (`pointerInput` / `awaitEachGesture`, in depth in [GESTURES.md](GESTURES.md)),
`LazyColumn` + `FlowRow` in All Apps, the search text field and focus, runtime permission
requests, debounced flows, composable slots, the shared `LaunchApp` use case and haptics; v4 adds what M3 brought: Android `Service`s
(the notification listener), broadcast receivers, special accesses versus runtime
permissions, and re-checking on resume (see [the M3 sections](#system-features-m3-services-receivers-special-access-and-resume)); v5 adds what M4 brought: hand-built segmented controls, swatches and switch rows with proper semantics, `flatMapLatest` to start/stop a listener, a bounded `runBlocking` before the first frame, and window-level wallpaper/blur (see [the M4 section](#personalisation-m4-appearance-controls-and-the-window)).

## How to read this

You already know how to build apps; this file maps what you know from Flutter/Dart
(clean architecture, BLoC, get_it/injectable, Dio) onto what Reach uses: Kotlin, Jetpack
Compose, coroutines/Flow, Hilt, Room and DataStore.

- The **quick map** gives you the one-line equivalent of each concept.
- Each **section** then shows a short Kotlin example (and Dart where it helps).
- Example labels: **(real)** = copied from the repo; **(simplified from `<file>`)** = real
  code with imports, KDoc and unrelated parameters trimmed; **(illustrative)** = not in the
  repo (yet), shown only to explain the concept.
- Kotlin syntax primer in one breath: `val` = `final`, `var` = mutable, `fun` = function,
  types after names (`label: String`), `data class` ≈ a freezed/equatable class with
  `copy()`, `object` = a singleton, `sealed interface` ≈ a Dart `sealed class`, trailing
  lambdas: `Column { … }` is `Column(content = { … })`.
- For the big picture (modules, data flows, state machine) read
  [ARCHITECTURE.md](ARCHITECTURE.md); for system APIs, [ANDROID_APIS.md](ANDROID_APIS.md).

`…` in paths = the `src/main/kotlin/com/baklavapp/reachlauncher/<module>` part, e.g.
`core/data/…/db/Daos.kt` = `core/data/src/main/kotlin/com/baklavapp/reachlauncher/core/data/db/Daos.kt`
and `app/…/MainActivity.kt` = `app/src/main/kotlin/com/baklavapp/reachlauncher/MainActivity.kt`.

---

## Quick map

| Flutter / Dart | Compose / Android in Reach |
|---|---|
| `Widget` | `@Composable` function |
| `StatelessWidget` / `StatefulWidget` | composable function + `remember` / `rememberSaveable` |
| `setState` | `mutableStateOf` (writes trigger recomposition) |
| BLoC / Cubit | `ViewModel` + `StateFlow<UiState>` (`HomeViewModel`) |
| `BlocBuilder` | `collectAsStateWithLifecycle()` |
| `BlocListener` | collecting an event `Flow` (backed by a `Channel`) in `LaunchedEffect` + `repeatOnLifecycle` |
| `Stream` / `BehaviorSubject` | `Flow` (cold) / `StateFlow` (hot, has a current value) |
| `StreamController` wrapping a platform callback / `EventChannel` | `callbackFlow { register…; awaitClose { unregister… } }` |
| `Future` / `async`/`await` | `suspend fun` + coroutines; `viewModelScope` ≈ BLoC lifetime |
| get_it / injectable | Hilt (`@Inject constructor`, `@Module @InstallIn`, `@Binds`, `@Provides`, `@HiltViewModel`) |
| `shared_preferences` | DataStore (`SettingsRepositoryImpl`) |
| sqflite / drift | Room (`@Entity`, `@Dao`, `@Database`); KSP ≈ build_runner |
| Native background component the OS binds to (plugin's `Service`) | `Service` / `NotificationListenerService` (`ReachNotificationListenerService`); no direct Flutter equivalent |
| `EventChannel` of platform events | `BroadcastReceiver` registered at runtime, wrapped in `callbackFlow` |
| `permission_handler` for Settings-only grants | special access (notification listener, usage access): switch in Settings, no dialog |
| `didChangeAppLifecycleState(resumed)` | `LifecycleResumeEffect` / `AccessRechecks` on `onActivityResumed` |
| `Theme.of(context)` / `ThemeExtension` | `CompositionLocal` — `ReachTheme.colors` reads `LocalReachColors` |
| `Column` / `Row` / `Stack` / `Wrap` | `Column` / `Row` / `Box` / `FlowRow` |
| `Padding` / `SizedBox` / `Container` | `Modifier` chains (order matters) |
| `SingleChildScrollView` | `Modifier.verticalScroll(rememberScrollState())` |
| `ListView.builder` | `LazyColumn` (All Apps, Search results) |
| `ScrollController.jumpTo` / `animateTo` | `LazyListState.scrollToItem` / `animateScrollToItem` |
| `GestureDetector` | `Modifier.clickable`, `combinedClickable`, `detectTapGestures` |
| `RawGestureDetector` + custom `GestureRecognizer` | `Modifier.pointerInput { awaitEachGesture { … } }` ([GESTURES.md](GESTURES.md)) |
| winning the gesture arena | `PointerInputChange.consume()` (+ reading in `PointerEventPass.Initial`) |
| `NotificationListener<OverscrollNotification>` | `NestedScrollConnection` (Home's swipes) |
| `CustomMultiChildLayout` | `Layout { measurables, constraints -> … }` (`FanOverlay`) |
| `TextField` / `EditableText` | `BasicTextField` (Search input) |
| `FocusNode` + `SystemChannels.textInput` | `FocusRequester` + `LocalSoftwareKeyboardController` |
| `permission_handler` (`Permission.contacts.request()`) | `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` |
| rxdart `debounceTime` + `switchMap` | `Flow.debounce` + `mapLatest` |
| builder / `Widget child` parameters | composable slot parameters (`HomeRoute(searchContent = …)`) |
| use-case class (`LaunchApp.call()`) | `LaunchApp` in `:core:data` (`@Inject` class shared by ViewModels) |
| `HapticFeedback.selectionClick()` | `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.SegmentTick)` (`rememberHapticTick`) |
| `Semantics` / `CustomSemanticsAction` / `MergeSemantics` | `Modifier.semantics` / `CustomAccessibilityAction` / `clearAndSetSemantics` |
| `Navigator` | not used — a state machine in `HomeViewModel` (`HomeSurface`) |
| `PopScope` / `WillPopScope` | `BackHandler` |
| `AnimatedSwitcher` | `AnimatedContent` |
| `MethodChannel` | not needed — call Android APIs directly (in `:core:system`) |
| `initState` / `dispose` pair | `DisposableEffect(key) { …; onDispose { … } }` |
| `WidgetsBindingObserver.didChangeAppLifecycleState` | `LifecycleResumeEffect { …; onPauseOrDispose { } }` |
| `pubspec.yaml` | `gradle/libs.versions.toml` + each module's `build.gradle.kts` |
| `flutter run` / `test` / `analyze` | `./gradlew installDebug` / `test` / `lint`, or Android Studio Run |
| `AnimatedContainer`, implicit animations | `animate*AsState`, `AnimatedContent`, `AnimatedVisibility` |
| hot reload | Live Edit + Compose Preview (`@Preview`) |
| `BuildContext` | none — composition locals, e.g. `LocalContext.current` |
| `main()` / `runApp()` | `MainActivity.setContent { … }` |
| `MediaQuery.padding` / `SafeArea` | `WindowInsets`, `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`, edge-to-edge |
| `intl` / ARB files | `res/values/strings.xml` + `res/values-tr/strings.xml` |
| `const` widgets, avoiding rebuilds | recomposition skipping, `@Immutable` / `@Stable` |
| `testWidgets` + `WidgetTester` | `createComposeRule()` (on the JVM via Robolectric) |
| `bloc_test` / `expectLater(stream, emitsInOrder(…))` | Turbine: `flow.test { awaitItem() }` |

---

## UI building blocks

### Widget ↔ `@Composable`

A composable is a **function** that emits UI; there is no widget object you construct and
keep. Name it in PascalCase like a widget (ktlint is configured to allow that).

```kotlin
// (simplified from feature/home/…/ui/IntentDetailScreen.kt) — the accent dot + "Talk" title
@Composable
private fun DetailTitle(intent: ReachIntent, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(14.dp).background(ReachTheme.colors.accentInk, CircleShape))
        Text(text = intentWord(intent), style = ReachTheme.typography.intentTitle, color = ReachTheme.colors.fg,
             modifier = Modifier.semantics { heading() })
    }
}
```

```dart
class DetailTitle extends StatelessWidget {
  const DetailTitle({required this.intent});
  final ReachIntent intent;
  @override
  Widget build(BuildContext context) => Row(children: [
        Container(width: 14, height: 14, decoration: BoxDecoration(shape: BoxShape.circle, color: ...)),
        Semantics(header: true, child: Text(intentWord(intent))),
      ]);
}
```

Reach splits screens the Flutter way: **stateless composables** take state as parameters
and report interactions through callbacks (`HomeScreen(intents, now, thumbSide,
onIntentSelected, onLaunch, …)`), and one **route** (`HomeRoute`) connects them to the
ViewModel. Composables with a `@Composable () -> Unit` parameter (e.g. `IntentWordRow`'s
`extraContent`) take a *slot* — Flutter's `Widget child`.

### StatelessWidget / StatefulWidget ↔ composables + `remember` / `rememberSaveable`

There's no Stateless/Stateful split. A composable that needs local state asks for it with
`remember` (survives recomposition) or `rememberSaveable` (also survives configuration
changes and process death, like `RestorationMixin`).

```kotlin
// (simplified from feature/home/…/HomeRoute.kt) — local, visual-only state
var failedLabel by remember { mutableStateOf<String?>(null) }   // the "Couldn't open X" message
```

Reach's rule: **screen state lives in ViewModels**; `remember` is for purely visual, local
state (a message being shown, a scroll position, a remembered helper object such as
`LaunchOriginHolder`). Reach uses no `rememberSaveable` for the surface on purpose: after
process death Home must start on Home (`stateNotNeeded`, D28).

### `setState` ↔ `mutableStateOf`

Writing to a `MutableState` *is* the `setState` call: Compose tracks which composables
read it and re-runs only those.

```kotlin
// (simplified from feature/home/…/HomeRoute.kt)
LaunchedEffect(failedLabel) {          // restarts whenever failedLabel changes
    if (failedLabel != null) {
        delay(3_000)
        failedLabel = null              // ≈ setState(() => failedLabel = null)
    }
}
```

### Column / Row / Stack / Wrap ↔ Column / Row / Box / FlowRow

```kotlin
Column(verticalArrangement = Arrangement.Bottom) { /* ≈ MainAxisAlignment.end */ }
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { }
Box(contentAlignment = Alignment.BottomStart) {     // ≈ Stack(alignment: …)
    Text("on top", Modifier.align(Alignment.TopEnd)) // ≈ Positioned / Align
}
Spacer(Modifier.weight(1f))                          // ≈ Spacer() / Expanded inside a Column/Row
FlowRow { /* ≈ Wrap */ }
```

Real uses: `HomeRoute` stacks the surface and the failure message in a `Box`;
`HomeWithFan` stacks `HomeScreen` and `FanOverlay` (≈ `Stack(children: [home, overlay])`);
`IntentWordRow` is a `FlowRow` so the Listen row's inline player wraps under the word at
200 % font (D22); the Now row uses `Row(Modifier.height(IntrinsicSize.Min))` + tiles with
`fillMaxHeight()` ≈ `IntrinsicHeight` + `CrossAxisAlignment.stretch`.

`FlowRow` ↔ `Wrap` for real — the All Apps word wall: names flow left to right and wrap, so
200 % font needs no special layout, the wall just gets more lines.

```kotlin
// (simplified from feature/allapps/…/ui/AllAppsScreen.kt, WordWallGroup)
Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    MonoLabel(text = label, modifier = Modifier.semantics { heading() })   // "S"
    FlowRow(                                                              // ≈ Wrap(spacing: 10, runSpacing: 2)
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        apps.forEach { app -> key(app.key) { WordWallName(app, color, hiddenGroup, callbacks) } }
    }
}
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
```

`SizedBox(width: 42, height: 42)` ↔ `Modifier.size(42.dp)`;
`ConstrainedBox(minHeight: 48)` ↔ `Modifier.heightIn(min = 48.dp)`;
`Expanded` ↔ `Modifier.weight(1f)` (only inside `Row`/`Column`).

Real example — Home's layout: a `SingleChildScrollView` whose child is at least as tall as
the screen, with `spaceBetween`, so the words sit at the bottom in the thumb zone and
everything scrolls at 200 % font instead of clipping.

```kotlin
// (simplified from feature/home/…/ui/HomeScreen.kt)
BoxWithConstraints(                                  // ≈ LayoutBuilder: gives maxHeight
    Modifier.fillMaxSize()
        .nestedScroll(swipe)                         // see "Nested scroll" below
        .windowInsetsPadding(WindowInsets.safeDrawing),  // ≈ SafeArea
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState(), overscrollEffect = null) // ≈ SingleChildScrollView
            .heightIn(min = maxHeight)               // ≈ ConstrainedBox(minHeight: constraints.maxHeight)
            .padding(start = ReachTheme.dimens.gutter, end = ReachTheme.dimens.gutter),
        verticalArrangement = Arrangement.SpaceBetween,  // ≈ MainAxisAlignment.spaceBetween
    ) {
        Column { HomeDateLine(reading); HomeClock(reading, onOpenAppearance = onAppearance) /* + Now row */ }
        Column { IntentWords(…); SearchPill(onClick = onSearch) }
    }
}
```

### `ListView.builder` ↔ `LazyColumn`, `ScrollController` ↔ `LazyListState`

```kotlin
// (simplified from feature/allapps/…/ui/AllAppsScreen.kt)
val listState = rememberLazyListState()              // ≈ ScrollController (hoisted so tests can read it)
LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(18.dp)) {
    item(key = "header") { Header(state) }           // item 0
    items(state.groups, key = { "group:${it.letter}" }) { group ->   // key ≈ ValueKey
        WordWallGroup(label = group.letter, apps = group.apps, /* … */)
    }
}

// The letter rail asks for a jump; one group = one item, so "jump to S" = "scroll to item n".
LaunchedEffect(jump) {                               // restarts (and cancels a running scroll) per request
    val item = 1 + (jump?.groupIndex ?: return@LaunchedEffect)
    if (reducedMotion) listState.scrollToItem(item)  // ≈ controller.jumpTo
    else listState.animateScrollToItem(item)         // ≈ controller.animateTo / Scrollable.ensureVisible
}
```

Unlike `ScrollController.jumpTo(offset)`, `scrollToItem(index)` works in *items*, so you
never need item heights. Both are `suspend` functions: call them from a coroutine
(`LaunchedEffect` or `rememberCoroutineScope().launch { … }`). Search's results are a
`LazyColumn` too, keyed by `SearchResult.key`.

`LazyRow` ↔ horizontal `ListView`; `LazyVerticalGrid` ↔ `GridView.builder`. Don't put a
`LazyColumn` inside a vertically scrolling `Column` (same "unbounded height" trap as
nesting `ListView` in `Column` without `shrinkWrap`).

Intent detail deliberately uses **no** lazy list: an intent holds tens of apps, so it is a
plain `Column` inside the scroll (D33), which keeps the list and the intent chips anchored
at the bottom. In a non-lazy `forEach`, wrap each item in `key(item.app.key) { … }` (≈ a
`ValueKey`) so remembered per-item state follows the item when the list reorders — see
`AppList` and `NowRow`.

### GestureDetector / RawGestureDetector ↔ `clickable` / `combinedClickable` / `pointerInput` + `awaitEachGesture`

Simple taps: `Modifier.clickable { }` / `Modifier.combinedClickable(onLongClick = …) { }`
— these also give semantics for TalkBack. Reach wraps them in one recipe,
`Modifier.reachClickable` (D19, see [Accessibility](#accessibility)).
Anything custom goes in `pointerInput`:

```kotlin
// (simplified from feature/home/…/ui/HomeClock.kt) — ≈ GestureDetector(onLongPress: …)
Modifier.pointerInput(Unit) {
    detectTapGestures(
        onLongPress = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            open()                                     // → HomeViewModel.onAppearanceRequested()
        },
    )
}
```

The clock's 450 ms long-press is set by overriding `LocalViewConfiguration` around it
(never shorter than the system's own "touch & hold delay", D30) — Compose's equivalent of
`kLongPressTimeout`.

Low level — where Flutter makes you subclass `GestureRecognizer` for a `RawGestureDetector`,
Compose lets you write the recognizer as **one sequential coroutine**. The All Apps letter
rail is the shortest real example:

```kotlin
// (simplified from feature/allapps/…/ui/LetterRail.kt)
Modifier.pointerInput(Unit) {
    awaitEachGesture {                                 // once per gesture (≈ recognizer reset)
        val down = awaitFirstDown(requireUnconsumed = false)   // ≈ addAllowedPointer
        down.consume()                                 // ≈ winning the arena right away
        try {
            update(down.position.y)                    // → railLetterAt(...), tick, scroll
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break             // finger lifted
                update(change.position.y)              // ≈ handleEvent(PointerMoveEvent)
            }
        } finally {
            currentOnChange(null)                      // also runs on cancel / dispose
        }
    }
}
```

Press-slide-release (`feature/home/…/ui/PressSlideRelease.kt`) is the same shape with a hold
timer (`withTimeoutOrNull(hold)`, 300 ms by default) and a switch to `PointerEventPass.Initial` once armed.

**Gesture arena vs. consumption.** Flutter resolves competing recognisers in a *gesture
arena* that declares one winner. Compose has no arena: pointer events travel through the
modifier tree in three passes (Initial root→leaf, Main leaf→root, Final root→leaf), every
handler sees them, and a handler **consumes** a change (`change.consume()`) to tell others
"this is mine". Others check `isConsumed` and back off. Modifier order decides who is parent
and child. The full step-by-step write-up, with diagrams and the tests, is
[GESTURES.md](GESTURES.md).

### Nested scroll ↔ `NotificationListener<OverscrollNotification>`

Home *scrolls* (it must, at 200 % font), and pull-down → Search / swipe-up → All Apps must
not fight that scroll (D29). In Flutter you'd listen for `OverscrollNotification`s bubbling
up from the scroll view. Compose's equivalent is a `NestedScrollConnection` placed *above*
the scrollable with `Modifier.nestedScroll(connection)`: after the scroll consumed what it
could, `onPostScroll` receives the leftover (`available`).

```kotlin
// (simplified from feature/home/…/ui/HomeGestures.kt)
internal class VerticalSwipeConnection(/* distancePx, fling thresholds, callbacks */) : NestedScrollConnection {
    private var accumulated = 0f
    private var fired = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || fired) return Offset.Zero
        if (consumed.y != 0f) accumulated = 0f          // content scrolled: measure from the edge
        accumulated += available.y                       // +y = finger moving down
        when {
            accumulated >= distancePx -> fire(down = true)   // pull down → Search
            accumulated <= -distancePx -> fire(down = false) // swipe up → All Apps
        }
        return Offset.Zero                               // we only observe; consume nothing
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // finger lifted: a fast flick with a little travel also counts; then reset
        accumulated = 0f; fired = false
        return Velocity.Zero
    }
}
```

`rememberVerticalSwipeConnection` keeps the latest callbacks in `rememberUpdatedState` —
the Compose way to read "the current `widget.onX`" from a long-lived object.

### `HapticFeedback.selectionClick()` ↔ `LocalHapticFeedback`

```kotlin
// (real, core/designsystem/…/util/Motion.kt)
@Composable
fun rememberHapticTick(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) } }
}
```

`SegmentTick` is the semantic type for "stepped to the next discrete value" — one tick per new
fan pill and per new rail letter. The clock uses `HapticFeedbackType.LongPress`. Like Flutter's
`HapticFeedback`, it goes through the view (`View.performHapticFeedback`), so no `VIBRATE`
permission is needed.

### `CustomMultiChildLayout` ↔ `Layout { measurables, constraints -> … }`

The press-slide-release fan measures its pills, then places them from the finger upward,
stopping when the next one wouldn't fit under the top inset:

```kotlin
// (simplified from feature/home/…/ui/FanOverlay.kt)
Layout(content = { apps.forEachIndexed { i, app -> FanPill(app, highlighted = i == fan.highlighted, …) } }) { measurables, constraints ->
    val placeables = measurables.map { it.measure(Constraints.fixedWidth(pillWidth)) }   // ≈ layoutChild
    val positions = mutableListOf<Pair<Int, Int>>()
    var bottom = wordTop - gap
    for ((i, p) in placeables.withIndex()) {
        val y = bottom - p.height
        if (y < safeTop) break                                     // fewer pills at 200 % font
        positions += (pressX - leadIn + i * stagger) to y          // staggered away from the thumb
        bottom = y - gap
    }
    state.recordPills(/* window bounds of the placed pills, for hit-testing */)
    layout(constraints.maxWidth, constraints.maxHeight) {
        positions.forEachIndexed { i, (x, y) -> placeables[i].place(x, y) }   // ≈ positionChild
    }
}
```

### `AnimatedSwitcher` / implicit animations ↔ `AnimatedContent` / `animate*AsState`

```kotlin
// (simplified from feature/home/…/HomeRoute.kt) — ≈ AnimatedSwitcher keyed by the surface
val surface by viewModel.surface.collectAsStateWithLifecycle()   // which screen
val state by viewModel.uiState.collectAsStateWithLifecycle()     // what's on it

AnimatedContent(
    targetState = HomeFrame(surface, state),    // surface + data snapshot for one frame
    contentKey = { it.surface.contentKey() },   // ≈ the child's Key: same key → no transition
    transitionSpec = { surfaceTransition(reducedMotion) },
    label = "surface",
) { shown ->
    when (val s = shown.surface) {
        HomeSurface.Home, HomeSurface.Appearance -> HomeWithFan(/* HomeScreen + fan overlay */)
        is HomeSurface.IntentDetail -> IntentDetailScreen(detail = shown.state.detailFor(s.intent) /* … */)
        HomeSurface.Search -> searchContent(surfaceCallbacks)    // slot, default SearchRoute (see "Slots")
        HomeSurface.AllApps -> allAppsContent(surfaceCallbacks)  // slot, default AllAppsRoute
    }
}
AnimatedVisibility(visible = surface == HomeSurface.Appearance /* … */) {
    AppearancePlaceholderSheet(onClose = viewModel::onBack)    // the sheet slides in *over* Home (real sheet: M4)
}
```

`targetState` is the whole frame (surface + data), so a surface that's fading out keeps
rendering the snapshot it showed. With "Remove animations" on, `rememberReducedMotion()` (a
`ContentObserver` on `ANIMATOR_DURATION_SCALE`) turns the transition into a plain
cross-fade. Implicit property animations work as in Flutter:

```kotlin
// (illustrative)
val bg by animateColorAsState(if (selected) colors.accent else colors.surface, label = "rowBg")
```

The `label` parameter only names the animation in Android Studio's animation inspector.

---

## State and architecture

### BLoC / Cubit ↔ `ViewModel` + `StateFlow`

```kotlin
// (simplified from feature/home/…/HomeViewModel.kt)
@HiltViewModel                                            // ≈ @injectable + BlocProvider factory
class HomeViewModel @Inject constructor(
    private val catalogRepository: AppCatalogRepository,  // interfaces from :core:data only
    predictionRepository: PredictionRepository,
    settingsRepository: SettingsRepository,
    notificationCountSource: NotificationCountSource,
    shortcutSource: ShortcutSource,
    private val launchApp: LaunchApp,                     // the shared use case (see "Use-case classes")
    private val homeRoleSource: HomeRoleSource,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _surface = MutableStateFlow<HomeSurface>(HomeSurface.Home)
    val surface: StateFlow<HomeSurface> = _surface.asStateFlow()   // which screen — always current

    val uiState: StateFlow<HomeUiState> =                 // ≈ the Cubit's state stream (data only)
        combine(settingsRepository.settings, catalog, predictionRepository.predictions(3)) {
            settings, catalog, predictions -> buildUiState(settings, catalog, predictions)
        }.flowOn(defaultDispatcher)                       // build/sort off the main thread
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val theme: StateFlow<HomeTheme> = /* settings → (themeMode, palette), distinctUntilChanged */

    fun onIntentSelected(intent: ReachIntent) { _surface.value = HomeSurface.IntentDetail(intent) } // ≈ add(IntentTapped(intent))
    fun onBack() { _fan.value = null; _surface.value = HomeSurface.Home }

    // M2: a second small StateFlow for the press-slide-release fan (D50)
    private val _fan = MutableStateFlow<FanState?>(null)
    val fan: StateFlow<FanState?> = _fan.asStateFlow()
}
```

`SearchViewModel` and `AllAppsViewModel` follow the same recipe (`uiState` via
`combine(…).flowOn(defaultDispatcher).stateIn(WhileSubscribed(5_000))`, an event `Channel`).

- `combine` ≈ `Rx.combineLatest`: a new `HomeUiState` whenever any input emits.
- `stateIn(scope, WhileSubscribed(5_000), initial)` turns the cold pipeline into a hot
  `StateFlow` with a current value (≈ a `BehaviorSubject` seeded with `initial`). It runs
  while someone collects and **5 s longer** — a quick app switch doesn't restart it, but Home
  in the background stops working. The initial `HomeUiState()` is a valid first frame
  (`catalogLoaded = false`: intent rows without counts, no Now row), so Home never shows a
  spinner or a misleading "0".
- **Why `surface` is a separate `StateFlow`:** after those 5 s the `combine` is stopped, and a
  `stateIn` value only updates when its inputs re-emit. If the surface lived inside
  `uiState`, pressing Home after a while away would show the *previous* screen for a frame.
  A plain `MutableStateFlow` is always current (D39).
- The data is one immutable `@Immutable data class HomeUiState(catalogLoaded, thumbSide,
  intents, now, details)` (`HomeUiState.kt`) ≈ a freezed state class; `details` holds all six
  intent details precomputed, read with `detailFor(intent)`.
- A ViewModel survives configuration changes (rotation, dark mode) — like a BLoC provided
  above the `MaterialApp`, not inside the route. `MainActivity` obtains it with
  `by viewModels()` and passes it down (≈ one root `BlocProvider`).

### `BlocBuilder` / `BlocListener` ↔ `collectAsStateWithLifecycle` / an event `Channel`

State that should *replay* (what Home shows) goes into `uiState`. Things that must happen
**once** — hide the keyboard, show "Couldn't open X" — are events, sent through a `Channel`:

```kotlin
// (simplified from feature/home/…/HomeViewModel.kt)
// A Channel (not a SharedFlow) so an event sent while the UI isn't collecting (e.g.
// HideKeyboard from onNewIntent before onResume) is buffered and delivered once.
private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

fun onHomePressed() {
    val wasHome = _surface.value == HomeSurface.Home
    _fan.value = null
    _surface.value = HomeSurface.Home
    eventChannel.trySend(HomeEvent.HideKeyboard)
    if (wasHome) eventChannel.trySend(HomeEvent.ScrollToTop)
}
```

```kotlin
// (simplified from feature/home/…/HomeRoute.kt)
@Composable
fun HomeRoute(viewModel: HomeViewModel, modifier: Modifier = Modifier /* + searchContent, allAppsContent slots */) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()        // ≈ BlocBuilder
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {                          // ≈ BlocListener
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    HomeEvent.HideKeyboard -> { keyboard?.hide(); focusManager.clearFocus(force = true) }
                    is HomeEvent.LaunchFailed -> failedLabel = event.label
                }
            }
        }
    }
    BackHandler(enabled = true) { viewModel.onBack() }                  // ≈ PopScope, see below
    Box(modifier.fillMaxSize().background(ReachTheme.colors.bg)) { HomeSurfaces(state, viewModel) }
}
```

- `collectAsStateWithLifecycle()` (from `lifecycle-runtime-compose`) stops collecting when
  the app is in the background — prefer it over plain `collectAsState()`.
- `repeatOnLifecycle(STARTED)` does the same for the event collector.
- Why `Channel` and not `SharedFlow`: a `SharedFlow` without subscribers *drops* emissions;
  a buffered `Channel` keeps them until someone receives — each event exactly once, like a
  `BlocListener` that never misses a state.

### Streams ↔ `Flow`

| Dart | Kotlin |
|---|---|
| single-subscription `Stream` (e.g. `async*`) | cold `Flow` — runs its producer per collector |
| `BehaviorSubject` / `ValueStream` (rxdart) | `StateFlow` — hot, always has `.value`, replays latest, skips equal values |
| `PublishSubject` / broadcast stream | `SharedFlow` (`shareIn`) |
| `shareReplay(1)` | `shareIn(scope, WhileSubscribed(…), replay = 1)` (`PredictionRepositoryImpl`) |
| `stream.listen(…)` | `flow.collect { … }` (a `suspend` call, runs in a coroutine) |
| `StreamController` | `MutableStateFlow` / `MutableSharedFlow` / `Channel` / `callbackFlow` |
| `switchMap` / `combineLatest` / `distinct` | `flatMapLatest` / `combine` / `distinctUntilChanged` |
| `Stream.periodic` | a `flow { while (true) { emit(…); delay(…) } }` (`hourTicks`) |

Real contract and fake:

```kotlin
// (simplified from core/data/…/repository/Repositories.kt)
interface AppCatalogRepository {
    /** All apps including hidden ones, sorted by label (locale-aware). Never completes. */
    val apps: Flow<List<CatalogApp>>
    suspend fun refresh()
    suspend fun moveToIntent(key: AppKey, intent: ReachIntent)
    suspend fun setHidden(key: AppKey, hidden: Boolean)
}

// (simplified from core/data/…/fake/FakeRepositories.kt) — ≈ a BehaviorSubject behind a Stream getter
class FakeAppCatalogRepository(initial: List<CatalogApp> = emptyList()) : AppCatalogRepository {
    val state = MutableStateFlow(initial)          // tests push new lists to simulate installs
    override val apps: Flow<List<CatalogApp>> = state.map { it.sortedWith(/* Collator */) }
    // …
}
```

### `StreamController` around a platform callback ↔ `callbackFlow`

When a plugin wraps a native listener in an `EventChannel`, the Dart side is a stream
whose `onListen` registers the listener and `onCancel` unregisters it. `callbackFlow` is
exactly that, without the channel:

```kotlin
// (simplified from core/system/…/util/BroadcastFlow.kt)
internal fun Context.broadcasts(actions: Collection<String>): Flow<Intent> =
    callbackFlow {                                        // ≈ StreamController(onListen: …)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { trySend(intent) } // ≈ controller.add
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(this@broadcasts, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { unregisterReceiver(receiver) }       // ≈ onCancel
    }
```

`LauncherAppsInstalledAppsSource` does the same with `LauncherApps.registerCallback`; its
callback only *pokes* a `Channel(Channel.CONFLATED)` and a collector re-reads the full app
list on the IO dispatcher, so a burst of callbacks collapses into one or two reads.

### `Future` / `async`/`await` ↔ `suspend` + coroutines

```kotlin
// (real, feature/home/…/HomeViewModel.kt)
fun onResume() {
    homeRoleSource.recheck()
    viewModelScope.launch { bestEffort { catalogRepository.refresh() } }   // ≈ unawaited(repo.refresh()) inside a BLoC
}

// (simplified from core/data/…/repository/impl/AppCatalogRepositoryImpl.kt)
override suspend fun refresh() {                              // ≈ Future<void> refresh() async
    withContext(ioDispatcher) { safeSync(installedAppsSource.installedApps.first()) }  // ≈ await stream.first
}
```

- `viewModelScope` ≈ the BLoC's lifetime: everything launched in it is cancelled when the
  ViewModel is cleared (no manual `StreamSubscription.cancel()`).
- Work that must outlive every screen (keeping the app cache in sync) runs in an injected
  `@ApplicationScope CoroutineScope` instead (`core/data/…/di/DataModules.kt`).
- `withContext(Dispatchers.IO) { … }` ≈ `compute()`/an isolate hop for blocking work —
  but on a real thread pool, not a separate isolate.
- Composables use `LaunchedEffect` / `rememberCoroutineScope()` for their own coroutines.
- Cancellation is cooperative and structured: cancelling a scope cancels its children.

### rxdart `debounceTime` + `switchMap` ↔ `Flow.debounce` + `mapLatest`

Search ranks apps, shortcuts and commands on every keystroke (they're in memory), but the
contacts provider is another process, so its lookup waits until typing pauses and a lookup
overtaken by a newer query is cancelled:

```kotlin
// (simplified from feature/search/…/SearchViewModel.kt)
private val contacts: Flow<List<Contact>> =
    combine(query, contactsGranted) { q, granted -> ContactLookup(q.trim(), granted) }
        .distinctUntilChanged()                                       // ≈ .distinct()
        .debounce { if (it.shouldQuery) 150L else 0L }                // ≈ .debounceTime(150.ms), skipped when there's nothing to ask
        .mapLatest { if (it.shouldQuery) searchContacts(it.text) else emptyList() } // ≈ .switchMap(...)
        .onStart { emit(emptyList()) }                                // ≈ .startWith([]): first results never wait
```

`mapLatest` cancels the previous block's coroutine when a new value arrives — Kotlin's
built-in "switchMap for a suspend call". `debounce` takes a lambda here, so an empty query or
a missing permission passes straight through. Both need `@OptIn(FlowPreview::class,
ExperimentalCoroutinesApi::class)`.

### Use-case classes ↔ `LaunchApp`

```kotlin
// (simplified from core/data/…/domain/LaunchApp.kt)
class LaunchApp @Inject constructor(
    private val appLauncher: AppLauncher,
    private val shortcutSource: ShortcutSource,
    private val history: LaunchHistoryRepository,
    private val clock: Clock,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    fun app(key: AppKey, origin: LaunchOrigin?): LaunchResult {       // ≈ call()
        val result = appLauncher.launchApp(key, origin)
        if (result == LaunchResult.LAUNCHED) record(key)               // only real launches count
        return result
    }
    fun shortcut(shortcut: AppShortcut, origin: LaunchOrigin?, recordAs: AppKey?): LaunchResult { /* … */ }
    private fun record(key: AppKey) {
        val now = clock.millis()
        appScope.launch { /* history.recordLaunch(key, now), failures swallowed */ }  // outlives the ViewModel
    }
}
```

The clean-architecture use case you'd share between BLoCs: `HomeViewModel`,
`SearchViewModel` and `AllAppsViewModel` all inject it, so Home, the fan, Search and All Apps
record launches identically. No `@Module` needed — an `@Inject constructor` is enough for Hilt
(≈ `@injectable` on a concrete class).

### Builder / `Widget child` parameters ↔ composable slots

```kotlin
// (simplified from feature/home/…/HomeRoute.kt)
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    searchContent: @Composable (callbacks: SurfaceCallbacks) -> Unit = { SearchRoute(it.onClose, it.onOpenAppearance) },
    allAppsContent: @Composable (callbacks: SurfaceCallbacks) -> Unit = { AllAppsRoute(it.onClose, it.onOpenSearch) },
)

// (simplified from feature/home/src/test/…/HomeRouteTest.kt) — Hilt-free stand-ins
HomeRoute(viewModel = fixture.viewModel, searchContent = { Text(SEARCH_STUB) }, allAppsContent = { Text(ALL_APPS_STUB) })
```

A parameter of type `@Composable (T) -> Unit` is Compose's `Widget Function(BuildContext, T)
builder`. Defaults make the call site in `ReachApp` just `HomeRoute(viewModel)`, while tests
swap in stubs because the real routes call `hiltViewModel()` (D51). `SurfaceCallbacks` is the
small "what may a sub-surface ask Home to do" object — the feature modules never see
`HomeViewModel`.

### get_it / injectable ↔ Hilt

| injectable | Hilt |
|---|---|
| `configureDependencies()` in `main()` | `@HiltAndroidApp` on `ReachApplication` |
| `@injectable` / `@lazySingleton` class | `class X @Inject constructor(…)` (+ `@Singleton`) |
| `@module abstract class RegisterModule` | `@Module @InstallIn(SingletonComponent::class)` |
| `@LazySingleton(as: Repo)` | `@Binds abstract fun bind(impl: Impl): Repo` |
| a module getter that builds a third-party object | `@Provides fun provide…(): T` |
| `@Named('io')` | a `@Qualifier` annotation (`@IoDispatcher`, `@ApplicationScope`) |
| `getIt<HomeCubit>()` | `@HiltViewModel` + `by viewModels()` / `hiltViewModel()` |
| — | `@AndroidEntryPoint` on `MainActivity`: lets Hilt inject into the Activity |
| build_runner | KSP (`ksp(libs.hilt.compiler)`) generates the graph at compile time |

```kotlin
// (simplified from core/data/…/di/DataModules.kt)
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAppCatalogRepository(impl: AppCatalogRepositoryImpl): AppCatalogRepository
    @Binds abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    // …
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReachDatabase =
        Room.databaseBuilder(context, ReachDatabase::class.java, ReachDatabase.NAME).build()

    @Provides fun provideAppCacheDao(db: ReachDatabase): AppCacheDao = db.appCacheDao()
}

// (simplified from core/system/…/di/SystemModule.kt) — real implementations and stubs
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {
    @Binds @Singleton abstract fun bindAppLauncher(impl: SystemAppLauncher): AppLauncher
    @Binds @Singleton abstract fun bindContactsSource(impl: ContactsProviderSource): ContactsSource // M2: was a stub
    /** M3 placeholder. */
    @Binds @Singleton
    abstract fun bindNotificationCountSource(impl: UnavailableNotificationCountSource): NotificationCountSource
}

// (real, app/…/ReachApplication.kt) — field injection where there's no constructor to inject
@HiltAndroidApp
class ReachApplication : Application() {
    @Inject lateinit var activityTracker: ActivityTracker
    override fun onCreate() {
        super.onCreate() // Hilt injects the fields above here.
        registerActivityLifecycleCallbacks(activityTracker)
    }
}
```

The modules sit next to their code (`:core:data`, `:core:system`); `:app` depends on both, so
Hilt sees every `@InstallIn` module there and assembles one graph. Unlike get_it, a missing
binding is a **compile error**, not a runtime crash.

### `shared_preferences` ↔ DataStore

DataStore is async and Flow-based (no synchronous `getString`); every `edit` emits a new
snapshot, so the UI re-themes live with no "reload".

```kotlin
// (simplified from core/data/…/di/DataModules.kt) — exactly one instance per file, hence @Singleton
@Provides @Singleton
fun providePreferencesDataStore(@ApplicationContext context: Context, /* scope, io */): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },  // corrupt file → defaults
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

// (simplified from core/data/…/repository/impl/SettingsRepositoryImpl.kt)
override val settings: Flow<AppearanceSettings> =
    dataStore.data                                                  // ≈ a Stream of the whole prefs map
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs.toSettings() }                        // PaletteId.fromStorageKey(prefs[PALETTE]) …
        .distinctUntilChanged()

override suspend fun setPalette(palette: PaletteId) = put(Keys.PALETTE, palette.storageKey)  // by name, never colours

private object Keys {                                               // never rename: these strings are on disk
    val PALETTE = stringPreferencesKey("palette")
    val THUMB_SIDE = stringPreferencesKey("thumb_side")
}
```

In M1 the settings are only *read* (theme, palette, thumb side flow into `HomeUiState`); the
Appearance sheet that writes them arrives in M4.

### sqflite / drift ↔ Room

Room ≈ drift: annotated classes, generated code, queries checked at compile time,
`Flow` results that re-emit when tables change (≈ drift's `watch()`). Code generation runs
through **KSP** (≈ `build_runner`, but automatic on every build).

```kotlin
// (simplified from core/data/…/db/Entities.kt) — ≈ class LaunchEvents extends Table
@Entity(tableName = "launch_events", indices = [Index(value = ["epoch_millis"])])
data class LaunchEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    // The three parts of AppKey: an app is package + activity + profile, not just a package.
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "epoch_millis") val epochMillis: Long,
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int,     // captured at launch time (D27)
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
)

// (simplified from core/data/…/db/Daos.kt)
@Dao
abstract class LaunchEventDao {
    @Insert abstract suspend fun insert(event: LaunchEventEntity)                 // ≈ into(t).insert → Future

    @Query("SELECT * FROM launch_events WHERE epoch_millis >= :sinceEpochMillis ORDER BY epoch_millis ASC, id ASC")
    abstract fun observeSince(sinceEpochMillis: Long): Flow<List<LaunchEventEntity>>  // ≈ .watch()
}

@Dao
abstract class AppCacheDao {
    @Upsert abstract suspend fun upsert(rows: List<CachedAppEntity>)
    @Delete abstract suspend fun delete(rows: List<CachedAppEntity>)

    @Transaction                                       // ≈ db.transaction(() async { … })
    open suspend fun applyDiff(upserts: List<CachedAppEntity>, deletes: List<CachedAppEntity>) {
        if (upserts.isNotEmpty()) upsert(upserts)
        if (deletes.isNotEmpty()) delete(deletes)
    }
}

// (simplified from core/data/…/db/ReachDatabase.kt) — ≈ @DriftDatabase(tables: [...])
@Database(entities = [CachedAppEntity::class, AppAssignmentEntity::class, LaunchEventEntity::class], version = 1, exportSchema = true)
abstract class ReachDatabase : RoomDatabase() {
    abstract fun appCacheDao(): AppCacheDao
    abstract fun appAssignmentDao(): AppAssignmentDao
    abstract fun launchEventDao(): LaunchEventDao
}
```

- Column names are fixed with `@ColumnInfo`, and enums are stored by stable string keys
  (`ReachIntent.storageKey`), never by `name`/ordinal — renaming a Kotlin property or enum
  constant must never change what's on disk.
- Schemas are exported to `core/data/schemas` (see `room { schemaDirectory(…) }` in
  `core/data/build.gradle.kts`) and committed, so future migrations can be tested against the
  exact v1 schema (≈ drift's schema dumps).
- `abstract class` DAOs (not interfaces) because `@Transaction` methods with a body are
  simplest there.

### `Theme.of(context)` / `ThemeExtension` ↔ `CompositionLocal`

A `CompositionLocal` is Compose's `InheritedWidget`. `CompositionLocalProvider` puts a value
into the tree; `LocalX.current` reads the nearest one. Reach's tokens (`ReachColors`) are
the equivalent of a `ThemeExtension`.

```kotlin
// (simplified from core/designsystem/…/theme/ReachTheme.kt)
// No default: reading it outside ReachTheme throws, so a missing theme is caught at once (D17).
val LocalReachColors = staticCompositionLocalOf<ReachColors> { error("No ReachTheme: wrap content in ReachTheme { … }") }

@Composable
fun ReachTheme(palette: PaletteId = PaletteId.DEFAULT, themeMode: ThemeMode = ThemeMode.AUTO,
               wallpaperAccent: Color? = null, content: @Composable () -> Unit) {
    val isDark = when (themeMode) { ThemeMode.AUTO -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val colors = remember(palette, isDark, wallpaperAccent) { /* palette → derived tokens */ }
    ReachTheme(colors = colors, content = content)   // → MaterialTheme(mapped) + CompositionLocalProvider(LocalReachColors provides colors, …)
}

object ReachTheme {
    val colors: ReachColors @Composable @ReadOnlyComposable get() = LocalReachColors.current
}

// Usage anywhere below ReachTheme { }:
Text("Talk", color = ReachTheme.colors.fg)        // ≈ Theme.of(context).extension<ReachColors>()!.fg
```

`ReachApp` (`app/…/ReachApp.kt`) is the `MaterialApp(theme: …)`: it collects the small
`HomeViewModel.theme` flow (`themeMode` + `palette` only, `distinctUntilChanged`) and passes
it to `ReachTheme`, so a settings change re-themes everything live while catalog or
prediction updates don't recompose the theme root.

**`staticCompositionLocalOf` vs `compositionLocalOf`:**
- `compositionLocalOf` tracks *who reads it*; changing the value recomposes only those readers.
  Good for values that change often and are read in a few places.
- `staticCompositionLocalOf` doesn't track readers; changing the value recomposes the
  **whole** content of the provider. Reads are cheaper. Reach uses it for colours and type:
  they're read almost everywhere and change rarely (palette/theme switch), when a full
  recomposition is what we want anyway.

Material 3 is used only as infrastructure; `ReachTheme` maps *every* Reach token onto
`MaterialTheme.colorScheme` and the 15 type styles, so text fields, ripples and switches
never show stock purple or Roboto (D17).

### `Navigator` / `PopScope` ↔ a state machine + `BackHandler`

Reach deliberately has **no Navigation Compose** (D28). Home, IntentDetail, Search, AllApps
and the Appearance sheet are states of one surface:

```kotlin
// (simplified from feature/home/…/HomeSurface.kt)
@Immutable
sealed interface HomeSurface {
    data object Home : HomeSurface
    data class IntentDetail(val intent: ReachIntent) : HomeSurface
    data object Search : HomeSurface       // :feature:search's SearchRoute, via a slot
    data object AllApps : HomeSurface      // :feature:allapps's AllAppsRoute, via a slot
    data object Appearance : HomeSurface   // real sheet: :feature:appearance (M4)
}

// (real, feature/home/…/HomeRoute.kt) — ≈ PopScope(canPop: false, onPopInvokedWithResult: …)
BackHandler(enabled = true) { viewModel.onBack() }
```

`HomeRoute` switches on `viewModel.surface` inside `AnimatedContent` (see above). Back is
**always** consumed: sub-surfaces go to Home, Home ignores it (letting it through would
finish the launcher Activity). The Home button goes through `MainActivity.onNewIntent` →
`HomeViewModel.onHomePressed()`. Search and All Apps add no `BackHandler` of their own; All
Apps' long-press menu (`AppMenu`) registers one while it's open, and because the innermost
enabled handler wins, Back then closes the menu first — ≈ a nested `PopScope` (D42).
That's a five-state machine, easier to test as plain
ViewModel logic than a back stack — see the state diagram in
[ARCHITECTURE.md §4](ARCHITECTURE.md#4-the-home-surface-state-machine).

### `MethodChannel` ↔ not needed

You're already native. Android APIs are plain Kotlin calls — no channel, no codec, no
platform thread hop:

```kotlin
// (simplified from core/system/…/launch/SystemAppLauncher.kt)
override fun launchApp(key: AppKey, origin: LaunchOrigin?): Boolean {
    val user = profiles.userFor(key.userSerial) ?: return false        // stored serial → UserHandle
    val activity = tracker.resumedActivity                              // ActivityTracker, D11
    val (sourceBounds, options) = animationFor(activity, origin)        // makeClipRevealAnimation(decorView, …)
    return try {
        launcherApps.startMainActivity(ComponentName(key.packageName, key.activityName), user, sourceBounds, options)
        true
    } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
}
```

In Reach those calls live only in `:core:system`, behind the interfaces in
`:core:data/source` (e.g. `InstalledAppsSource`, `AppLauncher`) — the same role a
platform-interface package plays for a Flutter plugin. Note the Java-shaped error model:
Android reports failure with **exceptions** (`ActivityNotFoundException`,
`SecurityException`); Reach catches the documented ones and returns `false` so Home never crashes.

---

## App entry, context, lifecycle and platform

### `main()` / `runApp()` ↔ `MainActivity.setContent`

```kotlin
// (simplified from app/…/MainActivity.kt)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()     // ≈ the root BlocProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {                                         // ≈ runApp(...)
            ReachApp(viewModel = viewModel, onDarkSystemBars = ::applySystemBarStyle)
        }
    }

    // singleTask: pressing Home while Reach exists delivers the HOME intent here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onHomePressed()
    }
}
```

`FlutterActivity` normally hides this from you. The Activity is declared in
`AndroidManifest.xml` with the HOME intent filter — that declaration, not code, is what
makes Reach a launcher.

### `BuildContext` ↔ there is none

Composables don't receive a context parameter. Anything "from above" is a composition
local: `LocalContext.current` (the Android `Context`, for system services and resources),
`LocalConfiguration.current`, `LocalDensity.current`, `LocalLocale.current`,
`LocalSoftwareKeyboardController.current`, `ReachTheme.colors`.

```kotlin
val context = LocalContext.current
val label = stringResource(R.string.home_intent_talk)   // resources without passing context around
```

Don't store a `Context` in a ViewModel (it leaks the Activity); inject
`@ApplicationContext context: Context` with Hilt where it's really needed (every
`:core:system` class does). For the few things that need the *Activity* (launch animation,
role dialog), Reach uses `ActivityTracker`, a weak reference to the resumed Activity (D11).

### `initState`/`dispose` and app lifecycle ↔ `DisposableEffect` / `LifecycleResumeEffect`

```kotlin
// (simplified from feature/home/…/ui/HomeClock.kt) — ≈ initState registers, dispose unregisters
@Composable
internal fun rememberWallClock(): State<ClockReading> {
    val context = LocalContext.current
    val reading = remember(context) { mutableStateOf(readClock(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { reading.value = readClock(context) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LifecycleResumeEffect(context) {               // ≈ didChangeAppLifecycleState(resumed)
        reading.value = readClock(context)
        onPauseOrDispose { }
    }
    return reading
}
```

`ReachApp` uses the same `LifecycleResumeEffect` to call `HomeViewModel.onResume()` (re-check
the home role, refresh the catalog) every time the user comes back to Home.

### `MediaQuery.padding` / `SafeArea` ↔ `WindowInsets`, edge-to-edge

`enableEdgeToEdge()` lets Reach draw behind the status and navigation bars (mandatory
behaviour when targeting API 35+). Each surface then avoids them itself:

```kotlin
Modifier.windowInsetsPadding(WindowInsets.safeDrawing)  // ≈ SafeArea: bars + cutout + keyboard (Home, detail, Search, All Apps)
Modifier.imePadding()                                    // (illustrative) ≈ MediaQuery.viewInsets.bottom alone
WindowInsets.systemGestures                              // ≈ MediaQuery.systemGestureInsets (back-swipe edges)
```

`MainActivity.applySystemBarStyle` calls `enableEdgeToEdge(SystemBarStyle.dark/light(…))`
again whenever the *resolved* Reach theme changes, so status-bar icons stay visible when
the user forces Dark or Light. The manifest's `windowSoftInputMode="adjustResize"` plus
`safeDrawing` (which includes the IME) is how Search's input and results sit just above the
keyboard (`SearchScreen`) — ≈ `Scaffold(resizeToAvoidBottomInset: true)` + `SafeArea`.
All Apps keeps its letter rail off the back-gesture edge with
`WindowInsets.systemGestures.exclude(WindowInsets.safeDrawing)` padding plus
`Modifier.systemGestureExclusion()` (D41, [GESTURES.md §3](GESTURES.md#3-the-letter-rail-all-apps)).

### `TextField` / `EditableText` ↔ `BasicTextField`; `FocusNode` ↔ `FocusRequester`

Material's `TextField` brings its own underline/label chrome (Reach never shows stock
Material chrome), so Search uses the bare `BasicTextField` — Compose's `EditableText`:

```kotlin
// (simplified from feature/search/…/ui/SearchScreen.kt)
BasicTextField(
    value = query,                                  // text owned by the caller (UI state), see below
    onValueChange = onQueryChange,                  // ≈ onChanged
    modifier = Modifier.focusRequester(focusRequester),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go), // ≈ textInputAction
    keyboardActions = KeyboardActions(onGo = { onSubmit() }),   // ≈ onSubmitted; also hardware Enter
)

// (simplified from feature/search/…/SearchRoute.kt)
var query by rememberSaveable { mutableStateOf("") }   // survives Activity recreation
val focusRequester = remember { FocusRequester() }     // ≈ FocusNode()
val keyboard = LocalSoftwareKeyboardController.current
LaunchedEffect(focusRequester) {
    focusRequester.requestFocus()                       // ≈ focusNode.requestFocus()
    keyboard?.show()                                    // ≈ SystemChannels.textInput.invokeMethod('TextInput.show')
}
```

The text lives in the UI (`rememberSaveable`), and the ViewModel only gets a copy via
`onQueryChange` — so a slow ranking pass can never make the cursor jump (D46). That's the
Compose equivalent of keeping a `TextEditingController` in the widget rather than rebuilding
the field from BLoC state.

### `permission_handler` ↔ `rememberLauncherForActivityResult(RequestPermission())`

```kotlin
// (simplified from feature/search/…/SearchRoute.kt)
val contactsPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onContactsPermissionResult(granted)           // ≈ the PermissionStatus you'd await
    }
// …
onIncludeContacts = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) } // ≈ Permission.contacts.request()
```

Differences from `await Permission.contacts.request()`: the launcher must be **registered
during composition** (Android re-delivers the result if the Activity is recreated while the
dialog is up), so you can't create it inside the click handler; the answer comes back as a
callback, not a `Future`; and "permanently denied" isn't reported separately — the system
simply answers `false` at once, which Reach treats like any denial (hide the prompt for the
session, D45). Checking without asking is `ContextCompat.checkSelfPermission(…)` (≈
`Permission.contacts.status`), done in `ContactsProviderSource.isPermissionGranted()`.

### `intl` / ARB ↔ `strings.xml` + `values-tr/`

```xml
<!-- feature/home/src/main/res/values/strings.xml (real) -->
<string name="home_intent_make">Make</string>
<string name="home_launch_failed">Couldn\'t open %1$s</string>
<!-- feature/home/src/main/res/values-tr/strings.xml (real) -->
<string name="home_intent_make">Üret</string>
<string name="home_launch_failed">%1$s açılamadı</string>
```

```kotlin
Text(stringResource(R.string.home_launch_failed, label))   // ≈ AppLocalizations.of(context)!.homeLaunchFailed(label)
pluralStringResource(R.plurals.home_now_reason_unread, count, count)   // ≈ Intl.plural
```

The system picks the folder by device locale — no delegate setup. Each module has its own
`res/` and its own `R` class. Apostrophes must be escaped (`\'`). Locale-aware sorting uses
`java.text.Collator` (`CatalogAssembler`); Turkish-aware matching uses `TextFolding` in
`:core:model`. Labels are upper-cased at display time with `LocalLocale`, so Turkish "i"
becomes "İ" (D20).

### `const` widgets / rebuild optimisation ↔ recomposition skipping, `@Immutable` / `@Stable`

Compose **skips** a composable when its parameters are equal to last time — the equivalent
of a `const` widget or `shouldRebuild == false`, but automatic. It can only trust equality
for *stable* types: primitives, `String`, and classes marked `@Immutable`/`@Stable` or
inferred stable.

```kotlin
// (simplified from feature/home/…/HomeUiState.kt)
@Immutable
data class IntentSummary(val intent: ReachIntent, val appCount: Int, val unreadCount: Int)
```

Because `HomeUiState.intents` is a list of these, a new catalog emission only recomposes the
`IntentWordRow` whose summary actually changed. With the Kotlin 2.x Compose compiler,
*strong skipping* is on by default. Still: replace lists wholesale rather than mutating them,
use `rememberUpdatedState` for callbacks captured by long-lived objects, and keep
fast-changing values out of state when nothing needs to recompose — `LaunchOriginHolder`
stores the tapped element's bounds in a plain field, updated from `onGloballyPositioned`,
because it changes every scroll frame and is only read on tap.

---

## Accessibility

### `Semantics` / `CustomSemanticsAction` ↔ `Modifier.semantics` / `CustomAccessibilityAction`

TalkBack reads the **semantics tree**, Compose's equivalent of Flutter's semantics tree.

| Flutter | Compose |
|---|---|
| `Semantics(label: …)` | `Modifier.semantics { contentDescription = … }` |
| `Semantics(header: true)` | `Modifier.semantics { heading() }` |
| `MergeSemantics` + `ExcludeSemantics` | `clearAndSetSemantics { … }` (replace children's semantics with one description) |
| `CustomSemanticsAction` | `CustomAccessibilityAction(label) { …; true }` in `customActions` |
| `Semantics(liveRegion: true)` | `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` |
| `InkWell` + `Semantics(button: true)` | `clickable` / `combinedClickable` (role, click action, focus) |

Reach's one recipe is `Modifier.reachClickable` (`core/designsystem/…/component/ReachClickable.kt`,
D19):

```kotlin
// (simplified from core/designsystem/…/component/ReachClickable.kt)
val clickable = combinedClickable(onClick = onClick, onLongClick = onLongClick, role = role, onClickLabel = onClickLabel /* … */)
return if (description != null) {
    clickable.clearAndSetSemantics {        // after (inside) the clickable, so its click action survives
        contentDescription = description    // "Talk, 3 new notifications" as ONE sentence
        if (actions.isNotEmpty()) customActions = actions
    }
} else clickable
```

Real custom actions: the clock offers "Appearance" (long-press alternative), the search pill
offers "All apps" (swipe-up alternative) —

```kotlin
// (simplified from feature/home/…/ui/HomeScreen.kt)
SearchPill(
    onClick = onSearch,
    modifier = Modifier.semantics {
        customActions = listOf(CustomAccessibilityAction(allAppsLabel) { onAllApps(); true })
    },
)
```

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
// (simplified from core/data/build.gradle.kts) — each module picks what it uses
dependencies {
    api(project(":core:model"))                 // ≈ path: ../model  (and re-exported)
    implementation(libs.androidx.room.runtime)  // ≈ a normal dependency
    ksp(libs.androidx.room.compiler)            // ≈ dev_dependency on a build_runner generator
    testImplementation(libs.turbine)            // ≈ dev_dependencies for tests
}
```

`settings.gradle.kts` lists the modules (≈ a melos/pub workspace). `api` vs
`implementation`: `api` leaks the dependency to consumers (like exporting a package),
`implementation` keeps it private. The module graph is drawn in
[ARCHITECTURE.md §1](ARCHITECTURE.md#1-modules).

### `flutter run` ↔ Gradle tasks / Android Studio run configs

| Flutter | Here |
|---|---|
| `flutter run` | Android Studio ▶ Run `app`, or `./gradlew installDebug` + `adb shell am start -n com.baklavapp.reachlauncher/.MainActivity` |
| `flutter test` | `./gradlew test` (or `:feature:home:test` for one module) |
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
// (real, core/designsystem/…/component/IntentWordRow.kt)
@Preview(name = "Intent words · 200% font", widthDp = 390, fontScale = 2f)
@Composable
private fun IntentWordRowLargeFontPreview(
    @PreviewParameter(DarkLightProvider::class) isDark: Boolean,
) {
    PreviewSurface(isDark) { IntentWordsSample(ThumbSide.LEFT) }
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
| hand-written fakes / mocks | `FakeAppCatalogRepository`, `FakeAppLauncher`, … in `core.data.fake` |
| `testWidgets` + `WidgetTester` | `createComposeRule()`, run on the JVM via Robolectric |
| `find.text` / `find.bySemanticsLabel` | `onNodeWithText` / `onNodeWithContentDescription` |
| `tester.tap` / `tester.drag` | `performClick()` / `performTouchInput { swipeDown() }` |
| `tester.pumpAndSettle()` | automatic: Compose waits for idle before each assertion |

A ViewModel test (≈ `bloc_test`): build with fakes, act, expect. `Dispatchers.setMain`
swaps Android's main thread for a test dispatcher because `viewModelScope` runs on Main.

```kotlin
// (simplified from feature/home/src/test/…/HomeViewModelTest.kt)
@Test
fun `home pressed returns home from anywhere and hides the keyboard`() = runTest {
    val f = HomeFixture(sampleApps)                 // HomeViewModel built from fakes
    subscribe(f)                                    // keeps WhileSubscribed upstream alive
    f.viewModel.events.test {                       // Turbine ≈ expectLater(events, emitsInOrder([...]))
        f.viewModel.onSearchRequested()
        f.viewModel.onHomePressed()
        assertThat(awaitItem()).isEqualTo(HomeEvent.HideKeyboard)
        assertThat(f.surface).isEqualTo(HomeSurface.Home)
        // …
    }
}
```

A Compose UI test (≈ `testWidgets`), on the JVM thanks to Robolectric. Nodes are found
through the semantics tree, so these tests double as accessibility checks:

```kotlin
// (simplified from feature/home/src/test/…/HomeRouteTest.kt)
@RunWith(RobolectricTestRunner::class)
class HomeRouteTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun intentRowDescriptionIncludesTheBadge() {
        val fixture = HomeFixture(sampleApps)
        fixture.notifications.granted.value = true
        fixture.notifications.counts.value = mapOf(PackageUser("pkg.whatsapp", 0) to 3)
        composeRule.setContent {                                              // ≈ pumpWidget
            ReachTheme { HomeRoute(fixture.viewModel, searchContent = { Text(SEARCH_STUB) }, allAppsContent = { Text(ALL_APPS_STUB) }) }
        }
        composeRule.onNodeWithContentDescription("Talk, 3 new notifications").assertIsDisplayed()
    }
}
```

**Gestures in tests** ≈ `tester.startGesture` / `TestGesture`: `performTouchInput { down(center);
advanceEventTime(350); moveTo(…); up() }` injects real pointer events, and
`composeRule.mainClock.advanceTimeBy(350)` lets a hold timer fire while the finger rests
(≈ `tester.pump(duration)`). Full table and examples in
[GESTURES.md §6](GESTURES.md#6-testing-gestures) (`PressSlideReleaseTest`, `AllAppsScreenTest`).

Room tests use an in-memory database under Robolectric (`AppCatalogRepositoryImplTest`);
DataStore tests run on the plain JVM with a temporary file (`SettingsRepositoryImplTest`).
Backtick names are just Kotlin identifiers with spaces — handy for test names.

---

## System features (M3): services, receivers, special access and resume

### Android `Service` / `NotificationListenerService` ↔ (no direct equivalent)

A Flutter app has one process-wide Dart isolate you start yourself. Android has four kinds of
*components* the **OS** creates and calls: `Activity` (a screen), `Service` (no UI),
`BroadcastReceiver` (an event) and `ContentProvider` (data). They are declared in the
manifest, and Android can start them even when none of your UI exists. The nearest Flutter
thing is the *native background half of a plugin*: e.g. the Kotlin class in
`flutter_local_notifications` or `audio_service` that the OS calls, which then pushes events
to Dart through a channel.

```kotlin
// simplified from core/system/…/notifications/ReachNotificationListenerService.kt
@AndroidEntryPoint                       // lets Hilt inject fields into a class Android creates
class ReachNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var hub: NotificationListenerHub

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { hub.onPosted(entryOf(it)) }     // forward only; the hub counts
    }
    override fun onListenerDisconnected() { hub.onDisconnected() }
}
```

Because Android (not Hilt) creates the service, it cannot take constructor parameters. So it
does one thing: forward to a Hilt `@Singleton` hub, which the rest of the app uses like any
other `Flow` source. Think of the hub as the `StreamController` the plugin's native side
writes into. The manifest entry (`exported="true"`, `BIND_NOTIFICATION_LISTENER_SERVICE`) is
explained in [ANDROID_APIS.md](ANDROID_APIS.md#notification-listener-m3).

### `BroadcastReceiver` ↔ `EventChannel`-style platform events

A broadcast is a system-wide message ("time changed", "calendar provider changed"). You
register a receiver, convert it into a stream, and unregister when nobody listens. Exactly an
`EventChannel` stream with `onListen`/`onCancel`:

```kotlin
// simplified from core/system/…/calendar/CalendarProviderSource.kt
private fun providerChanges(): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { trySend(Unit) }
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    awaitClose { context.unregisterReceiver(receiver) }          // ≈ onCancel
}
```

Reach registers almost all receivers `RECEIVER_NOT_EXPORTED` ("only my app may send me this").
This one is `EXPORTED` because the Calendar app sends it, not the system (D56).

### Special accesses ↔ runtime permissions

| | Runtime permission (`READ_CONTACTS`, `READ_CALENDAR`) | Special access (notification listener, usage access) |
|---|---|---|
| Flutter | `permission_handler`'s `request()` | the plugin tells you "open Settings and flip a switch" |
| Android UI | a system dialog over your app | a full Settings screen you send the user to |
| Reach asks with | `rememberLauncherForActivityResult(RequestPermission())` | `AppLauncher.open(SystemScreen.NOTIFICATION_ACCESS)` |
| Check | `checkSelfPermission` | `isNotificationListenerAccessGranted(component)` / `AppOpsManager.checkOpNoThrow(…)` |
| Result delivered | callback | **nothing**; you re-check when the app is resumed |

### `LifecycleResumeEffect` / re-check on resume ↔ `didChangeAppLifecycleState(resumed)`

Since nothing tells Reach that the user flipped a switch in Settings, Reach re-reads on every
Activity resume. In Flutter you would bump a `ValueNotifier` from a `WidgetsBindingObserver`;
Reach does it once, in `:core:system`, for every source:

```kotlin
// simplified from core/system/…/access/AccessRechecks.kt and notifications/NotificationAccess.kt
@Singleton class AccessRechecks @Inject constructor() {
    private val counter = MutableStateFlow(0L)
    val ticks: StateFlow<Long> = counter.asStateFlow()
    fun recheck() = counter.update { it + 1 }        // called from ActivityTracker.onActivityResumed
}

val granted: Flow<Boolean> = combine(rechecks.ticks, hub.connected) { _, connected ->
    isGranted().also { granted -> if (granted && !connected) requestRebind() }
}.distinctUntilChanged()
```

Screens that need their own resume hook still use `LifecycleResumeEffect` (see
[the lifecycle section](#initstatedispose-and-app-lifecycle--disposableeffect--lifecycleresumeeffect)).
The onboarding card then simply disappears when the flow flips to `true`: UI = f(state).

---

## Personalisation (M4): appearance controls and the window

### Segmented control, swatches, switch row ↔ `CupertinoSegmentedControl`, `ChoiceChip`, `SwitchListTile`

Reach's Appearance sheet (`feature/appearance/…/ui/AppearanceSheet.kt`) draws these with its own
tokens instead of using Material widgets, but must still be accessible. In Flutter you'd wrap
a custom widget in `Semantics(inMutuallyExclusiveGroup: true, selected: …, button: true)`. In
Compose the same job is done by modifiers:

- `Modifier.selectableGroup()` on the container (≈ `inMutuallyExclusiveGroup`), and
  `Modifier.selectable(selected = …, role = Role.RadioButton, onClick = …)` on each option.
  TalkBack then says "Dark, radio button, selected, 1 of 3".
- The "Show wallpaper" row is one `Modifier.toggleable(value, role = Role.Switch, onValueChange)`
  on the whole row (≈ `SwitchListTile`: label and switch are one tap target), with the Material
  `Switch` inside it purely visual (`onCheckedChange = null`) so it isn't announced twice.
- Swatches use `minHeight`, not a fixed height, so palette names wrap at 200 % font size, and the
  card scrolls (≈ `ConstrainedBox(minHeight)` inside a `SingleChildScrollView`).
- There is no draft state or "Save" button: each tap calls a `SettingsRepository` setter and the
  UI re-reads the same DataStore flow (D60). ≈ a Riverpod `ref.watch` on a provider backed by
  `shared_preferences`, where the write *is* the state change.

### `flatMapLatest` ↔ `switchMap`: a listener that only exists while it's needed

`HomeViewModel.theme` maps settings to a `HomeTheme`, then
`flatMapLatest { if (it.showWallpaper) wallpaperColorSource.primaryColor.map { … } else flowOf(it) }`.
Whenever the setting changes, the previous inner flow is cancelled — which runs the `awaitClose`
of the `callbackFlow` and so unregisters the `WallpaperManager` listener. Same as RxDart's
`switchMap`; the benefit is that users who never turn wallpaper mode on never register a listener.
`@OptIn(ExperimentalCoroutinesApi::class)` just marks the operator as not yet stable.

### Nullable state as "not loaded yet", and `runBlocking` before the first frame

`theme` is `StateFlow<HomeTheme?>`: `null` means "DataStore hasn't answered yet" (≈ `AsyncValue.loading`).
Showing defaults for that moment would flash the wrong theme for users who forced Light or Dark,
so `MainActivity.onCreate` reads the settings once with `runBlocking { withTimeoutOrNull(250) { settings.first() } }`
and passes it to `ReachApp` as `initialTheme`. In Flutter you'd `await` in `main()` before `runApp`
(`WidgetsFlutterBinding.ensureInitialized(); final prefs = await SharedPreferences.getInstance();`).
Android's `onCreate` can't be `async`, so `runBlocking` is the equivalent. It's normally a smell
because it blocks the main thread; it is acceptable here because the read is a tiny local file,
happens once per process, and has a 250 ms cap that falls back to defaults (D63).

### Window flags ↔ the embedding's transparent mode

Showing the system wallpaper behind the app is a *window* property, not something Compose can
paint: `FLAG_SHOW_WALLPAPER` plus a transparent window background (`app/…/ReachWindow.kt`).
Flutter analogy: `FlutterActivity` `BackgroundMode.transparent` in the Android embedding. A
theme attribute (`windowShowWallpaper`) only applies on Activity creation, so Reach flips the flag
at run time to avoid recreating Home. On Android 12+ `Window.setBackgroundBlurRadius` blurs
what's behind the window (≈ `BackdropFilter`, but of other windows); it is only available when
`isCrossWindowBlurEnabled`, which can change at run time (battery saver), hence a listener.

### One shared menu in the design system

The long-press menu moved from `:feature:allapps` to `:core:designsystem` (`AppActionsMenu`) so
that All Apps and intent detail use one implementation. Features can't depend on each other
([ARCHITECTURE.md](ARCHITECTURE.md), dependency rule 1), so a widget used by two features goes in the shared UI package — the same
reason you'd move a widget from `lib/features/a/` to `lib/shared/widgets/` in Flutter.

## Mental model differences that bite Flutter devs

1. **Recomposition isn't rebuilding a tree you hold.** You never get a widget object back
   or keep a reference to one. A composable may run many times, in any order, or be
   skipped; treat its body like `build()` *without* the guarantee it runs at all.
2. **Side effects never go in the composable body.** Anything that starts work, registers a
   listener or logs belongs in an effect: `LaunchedEffect(key) { … }` (≈ `initState` +
   `didUpdateWidget` for coroutines; restarts when `key` changes), `DisposableEffect(key) { …
   onDispose { … } }` (≈ `initState`/`dispose` pairs — the clock's broadcast receiver),
   `LifecycleResumeEffect` (on every resume), `SideEffect { }` for every successful
   recomposition.
3. **Modifier order is semantics.** `padding().background()` ≠ `background().padding()`;
   `clickable` before or after `padding` changes the tap area; `clearAndSetSemantics` must
   come after the clickable. Read a chain outside-in.
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
   opens on the Home surface, D28), but it means anything a user would miss must be
   persisted in Room or DataStore.
6. **`singleTask` and `onNewIntent`.** A Flutter app rarely sees this: with
   `launchMode="singleTask"`, a second "start" of the Activity (the Home button) doesn't
   create a new one; Android calls `onNewIntent` on the existing instance. That's Reach's
   "Home pressed" signal.
7. **The main thread is yours to protect.** There's no separate UI isolate. Blocking I/O on
   `Dispatchers.Main` janks or ANRs the app. Room and DataStore already suspend off the
   main thread; for your own work use `withContext(Dispatchers.IO) { … }` (I/O) or
   `Dispatchers.Default` (CPU) — Reach injects them as `@IoDispatcher` / `@DefaultDispatcher`
   so tests can swap them. Unlike Dart, coroutines on `Dispatchers.Default`/`IO` run
   **truly in parallel**, so shared mutable state needs care (prefer `StateFlow.update`, or a
   `Mutex` as `AppCatalogRepositoryImpl.sync` does).
8. **Kotlin null safety has a Java-shaped hole.** Kotlin is null-safe like Dart (`String?`,
   `?.`, `?:` ≈ `??`, `!!` ≈ `!`), but Android framework APIs are Java: their return types
   are *platform types* (`String!`) that the compiler won't check. Treat results of system
   calls (`getSystemService`, `getActivityList`, intent extras) as nullable unless the docs
   or annotations say otherwise — and expect **exceptions** (`SecurityException`,
   `ActivityNotFoundException`) as the error channel.
9. **Equality and immutability are on you.** `data class` gives you `==`/`copy()` like
   freezed; plain classes compare by identity. Expose `List`/`Map` (read-only interfaces)
   from state, not `MutableList`, so Compose and `StateFlow` can see changes as new values.
   The flip side is used on purpose: `JumpRequest` (All Apps) and `LaunchFailure` (the
   routes) are *plain* classes so two equal-looking requests still restart a
   `LaunchedEffect(key)`.
10. **There's no gesture arena.** Every pointer detector sees every event; "winning" is
    consuming, and modifier order decides who is parent and child. Read
    [GESTURES.md §1](GESTURES.md#1-composes-pointer-model-for-a-flutter-developer) before
    writing a `pointerInput`.

---

## Where to look in this repo

| To see… | Open |
|---|---|
| The "runApp" and the Home-button hook | `app/…/MainActivity.kt` (`setContent`, `onNewIntent`) |
| The `MaterialApp` equivalent | `app/…/ReachApp.kt` |
| The DI root | `app/…/ReachApplication.kt` |
| Why Reach is a launcher | `app/src/main/AndroidManifest.xml` (HOME filter, `singleTask`, `configChanges`, `<queries>`) |
| The BLoC (state machine + data hub) | `feature/home/…/HomeViewModel.kt`, `HomeUiState.kt`, `HomeSurface.kt` |
| BlocBuilder/BlocListener/PopScope equivalents | `feature/home/…/HomeRoute.kt` |
| Stateless screens | `feature/home/…/ui/HomeScreen.kt`, `IntentDetailScreen.kt`, `HomeClock.kt` |
| Nested-scroll swipes | `feature/home/…/ui/HomeGestures.kt` |
| A custom gesture recognizer (≈ `RawGestureDetector`) | `feature/home/…/ui/PressSlideRelease.kt`, `feature/allapps/…/ui/LetterRail.kt` |
| A custom multi-child layout | `feature/home/…/ui/FanOverlay.kt` |
| Search: text field, focus, permission request, debounce | `feature/search/…/SearchRoute.kt`, `ui/SearchScreen.kt`, `SearchViewModel.kt` |
| Pure, unit-tested ranking | `feature/search/…/SearchRanker.kt` (+ `src/test/…/SearchRankerTest.kt`) |
| `LazyColumn` + `FlowRow` word wall, in-surface menu | `feature/allapps/…/ui/AllAppsScreen.kt`, `ui/AppMenu.kt` |
| Locale-aware letters (ICU) | `feature/allapps/…/LetterIndex.kt` |
| A shared use case | `core/data/…/domain/LaunchApp.kt` |
| Composable slots | `feature/home/…/HomeRoute.kt` (`searchContent`, `allAppsContent`, `SurfaceCallbacks`) |
| Theme ≈ `Theme.of` / `ThemeExtension` | `core/designsystem/…/theme/ReachTheme.kt`, `ReachColors.kt`, `ReachTypography.kt` |
| Components + the a11y recipe | `core/designsystem/…/component/` (`IntentWordRow.kt`, `ReachClickable.kt`, `NowTile.kt`, `AppRow.kt`) |
| Domain types (≈ `domain/entities`) | `core/model/…/` |
| Repository contracts (≈ `domain/repositories`) | `core/data/…/repository/Repositories.kt` |
| System-source contracts (≈ plugin platform interfaces) | `core/data/…/source/SystemSources.kt` |
| Repository implementations, Room, DataStore | `core/data/…/repository/impl/`, `…/db/` |
| Pure domain logic | `core/data/…/domain/` (`CatalogAssembler`, `IntentCategorizer`, `PredictionEngine`) |
| Hilt modules | `core/data/…/di/DataModules.kt`, `Qualifiers.kt`, `core/system/…/di/SystemModule.kt` |
| `callbackFlow` wrappers of Android callbacks | `core/system/…/apps/LauncherAppsInstalledAppsSource.kt`, `…/util/BroadcastFlow.kt` |
| Fakes for tests and previews | `core/data/…/fake/FakeRepositories.kt`, `FakeSystemSources.kt` |
| ViewModel and UI tests | `feature/home/src/test/…/HomeViewModelTest.kt`, `HomeRouteTest.kt`, `HomeTestFixtures.kt`, `PressSlideReleaseTest.kt`; `feature/search/src/test/…`, `feature/allapps/src/test/…` |
| Dependency versions (≈ `pubspec.yaml`) | `gradle/libs.versions.toml`, `*/build.gradle.kts`, `settings.gradle.kts` |
| Lint/format rules (≈ `analysis_options.yaml`) | `.editorconfig`, the `lint { }` blocks in module build files |
| Architecture, APIs, gestures, decisions | `docs/ARCHITECTURE.md`, `docs/ANDROID_APIS.md`, `docs/GESTURES.md`, `docs/DECISIONS.md`, `docs/PLAN.md` |

M3 additions: the system-feature code lives in `core/system/…/notifications/`, `…/media/`,
`…/calendar/`, `…/usage/`, `…/access/AccessRechecks.kt`; the cards in
`feature/home/…/ui/SystemCards.kt`, `HomeViewModel.kt` (`onboarding`, `system`) and
`MediaProgress.kt`.

Coming in later versions of this file: the accessibility service (M5).
