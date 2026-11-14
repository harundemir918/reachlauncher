# Build "Reach" — a thumb-first Android launcher (Kotlin + Jetpack Compose)

You are the lead engineer and orchestrator for a new Android app: **Reach**, a home-screen launcher. Build it from scratch in an empty repository using **Kotlin and Jetpack Compose**. The full product and design spec is below; treat it as the source of truth.

The owner is an experienced **Flutter/Dart developer** (clean architecture, BLoC, Dio) who is new to native Android. **Documentation is a first-class deliverable**: everything you build must be explained so that a Flutter developer can understand how and why it works (see §8).

---

## 1. How you work: orchestrate with sub-agents

You coordinate. Most implementation goes to sub-agents (your Agent/Task tool), each with one well-bounded job.

1. **Plan first.** Before writing feature code, write `docs/PLAN.md`: the module layout, the interfaces/contracts between modules, the milestone breakdown (§9) and which sub-agent owns what. Then carry on; don't wait for approval unless a decision is hard to reverse.
2. **Contracts before delegation.** You write the shared pieces yourself first: Gradle setup, version catalog, the `:core:model` types, the repository interfaces in `:core:data`, and the design tokens in `:core:designsystem`. Sub-agents build *against* these, so their work snaps together.
3. **Delegate in waves.** Run independent sub-agents in parallel. Use isolated git worktrees if your tooling supports them, so parallel agents don't overwrite each other. Give each sub-agent:
   - the exact module(s) and files it owns, and the files it must NOT touch;
   - the relevant spec sections, copied into its prompt (sub-agents don't see this conversation);
   - the interfaces it must implement or consume;
   - its definition of done: compiles, has tests, has KDoc and "Flutter analogy" notes (§8), and passes `./gradlew :<module>:check`.
4. **Integrate after each wave.** Merge, then run `./gradlew build test lint` at the root and fix breakage before starting the next wave. Commit once per milestone with a clear message.
5. **Independent review.** After each milestone, spawn a *separate* reviewer sub-agent that did not write the code. It checks the work against this spec, accessibility (§6.9), permissions/privacy (§7) and documentation quality. Fix what it finds.
6. **Suggested sub-agents** (adapt in PLAN.md):
   - `designsystem-agent` — palettes, token derivation, typography, theme, contrast tests (§5)
   - `catalog-agent` — app list, intent categorisation, launch history, Room/DataStore persistence (§6.1, §6.2)
   - `home-agent` — Home screen, intent detail, press-slide-release gesture (§6.3–6.5)
   - `search-agent` — type-to-launch, ranking, commands (§6.6)
   - `allapps-agent` — A–Z list and letter rail (§6.7)
   - `system-agent` — notifications, media, shortcuts, usage stats, accessibility service, wallpaper, default-home role (§7)
   - `appearance-agent` — Appearance sheet, settings persistence, wallpaper mode (§6.8)
   - `docs-agent` — runs last in each milestone and writes/updates `docs/` from the code that exists (§8)
   - `reviewer-agent` — independent review, as above

Verify every Android API, library version and permission rule against current official documentation (developer.android.com, Google Play policy) before relying on it. Don't hard-code versions from memory: use the latest stable AGP, Kotlin, Compose BOM, Hilt, etc. at build time, kept in `gradle/libs.versions.toml`. Where this spec and current docs disagree, follow the docs and note the deviation in `docs/DECISIONS.md`.

---

## 2. Product in one paragraph

Reach replaces the icon grid with **intents**: six big words in the thumb zone — **Talk, Listen, Go, Make, Play, Tools**. Tap a word to see its apps (2 taps to any app), or **press, slide and release** to launch in one gesture. A **"Now" row** shows 3 apps predicted for this moment (1 tap). **Type to launch** finds apps, shortcuts, contacts and commands in a couple of keystrokes. There are no colourful icon grids and no stock Android or iOS look: the visual language is editorial — large type, letter monograms instead of app icons, one accent colour.

---

## 3. Technical baseline

- **Language/UI:** Kotlin, Jetpack Compose (Material 3 *only* as infrastructure — the visual design is fully custom; don't let it look like stock Material).
- **minSdk 28** (needed for the accessibility lock-screen action), **targetSdk/compileSdk:** whatever Google Play currently requires, or newer.
- **Architecture:** clean architecture with unidirectional data flow — ViewModel + `StateFlow<UiState>` + one-off events. Repositories behind interfaces. Coroutines/Flow.
- **DI:** Hilt. **Persistence:** DataStore (settings) + Room (launch history, intent assignments). **No networking** in v1.
- **Single Activity**, no Navigation Compose. The screens are states of one Home surface (Home / IntentDetail / Search / AllApps / Appearance sheet), driven by a state machine in the Home ViewModel.
- **Modules:** `:app`, `:core:model`, `:core:designsystem`, `:core:data`, `:core:system` (all Android system integrations), `:feature:home`, `:feature:search`, `:feature:allapps`, `:feature:appearance`. Keep it this light; this is an indie project.
- **Testing:** JUnit + Turbine for ViewModels/Flows, unit tests for search ranking, categorisation and colour maths, Compose UI tests for the main flows. Screenshot tests (Roborazzi or Paparazzi) are optional but welcome for the palettes.
- **Static checks:** ktlint or detekt, Android Lint, no warnings left unexplained.
- **Localisation:** all strings in `strings.xml`, English plus Turkish (`values-tr`). Alphabetical sorting must use `java.text.Collator` for the current locale (Turkish İ/ı must sort correctly).

---

## 4. Launcher fundamentals

- `MainActivity` declares the `MAIN` + `HOME` + `DEFAULT` intent filter, `launchMode="singleTask"`, `excludeFromRecents`, and handles `onNewIntent` so that **pressing Home while in any sub-state returns to the Home state** and closes the keyboard and sheets.
- System Back on the Home state does nothing; in sub-states it steps back one level.
- First run: if Reach is not the default home app, show a short onboarding card with a button that uses `RoleManager.createRequestRoleIntent(ROLE_HOME)` (fall back to the system Home settings screen).
- App enumeration via `LauncherApps` (all user profiles), with a `<queries>` entry for `MAIN/LAUNCHER` intents. Avoid `QUERY_ALL_PACKAGES` unless current Play policy and the docs say a launcher needs it; document the decision.
- React to package installs, removals and updates live (`LauncherApps.Callback`).
- Launch apps with `LauncherApps.startMainActivity` and a launch animation (`ActivityOptions.makeClipRevealAnimation` or scale-up from the tapped element). **Don't** build the "Opening X…" overlay from the mockup — it was prototype-only.
- Edge-to-edge, correct insets everywhere (status bar, navigation bar, IME, display cutout). Keep the press-and-slide gesture area away from the system back-gesture edges.
- **Performance:** Home must render immediately when Home is pressed. Load the app catalog from a cache first, then refresh in the background; no blocking work on the main thread; Baseline Profiles for startup.

---

## 5. Visual design system (`:core:designsystem`)

### 5.1 Typography (bundle the fonts in `res/font`; all three are Google Fonts, OFL licensed — include the licence files)
| Role | Font | Size / weight / tracking |
|---|---|---|
| Clock | Bricolage Grotesque | 112sp, 500, letter-spacing −5sp, line-height 0.9 |
| Intent word (Home) | Bricolage Grotesque | 46sp, 700, −1.5sp |
| Intent title (detail) | Bricolage Grotesque | 60sp, 700, −2.5sp |
| Sheet title / search input | Bricolage Grotesque | 30–40sp, 700, −1sp |
| Body / app names | Inter | 15–22sp, 400–600 |
| Labels, counts, hints | JetBrains Mono | 10–12sp, 500, UPPERCASE, +1.5sp tracking |

### 5.2 Palettes
Each palette defines six colours per theme: `bg, fg, muted, accent (fill), accentInk (accent used as text/outline), onAccent (text on accent fills)`.

| Palette | Theme | bg | fg | muted | accent | accentInk | onAccent |
|---|---|---|---|---|---|---|---|
| Lime (default) | dark | #101114 | #ECEAE3 | #A3A5AC | #C8F25A | #C8F25A | #101114 |
| | light | #F4F2EC | #141414 | #5E6066 | #C8F25A | #3E6A00 | #141414 |
| Paper & Ink | dark | #0C0C0C | #F2F0EA | #A6A39C | #F2F0EA | #F2F0EA | #0C0C0C |
| | light | #F7F5F0 | #111111 | #5F5D58 | #111111 | #111111 | #F7F5F0 |
| Night Tide | dark | #0B1220 | #E6EDF7 | #93A1B8 | #7FD4FF | #7FD4FF | #0B1220 |
| | light | #EEF3F8 | #0B1220 | #4E5C72 | #7FD4FF | #005C8A | #0B1220 |
| Terracotta | dark | #17120F | #F1E8DF | #B3A396 | #FF8A5C | #FF8A5C | #17120F |
| | light | #F6EFE7 | #1C1511 | #6B5A4D | #FF8A5C | #9A3B12 | #1C1511 |
| Forest | dark | #0F1512 | #E4EDE6 | #9DB0A3 | #8FE3A8 | #8FE3A8 | #0F1512 |
| | light | #EFF4EF | #0F1512 | #4F6356 | #8FE3A8 | #1F6B3A | #0F1512 |
| Dusk | dark | #120F18 | #ECE7F5 | #A79FB8 | #C6A8FF | #C6A8FF | #120F18 |
| | light | #F3F0F8 | #15111C | #5E5670 | #C6A8FF | #5B3AA8 | #15111C |
| High Contrast | dark | #000000 | #FFFFFF | #CCCCCC | #FFE14D | #FFE14D | #000000 |
| | light | #FFFFFF | #000000 | #3D3D3D | #FFE14D | #000000 | #000000 |

### 5.3 Derived tokens (computed, not hand-picked), with `mix(a, b, t)` = linear RGB blend
- `surface` = dark: mix(bg, fg, 0.07); light: mix(bg, #FFFFFF, 0.75)
- `line` = mix(bg, fg, 0.12); `line2` = mix(bg, fg, 0.20); `divider` = mix(bg, fg, 0.09)
- `ring` (monogram outline) = mix(bg, fg, 0.35); `faint` (decorative chevrons only) = mix(bg, fg, 0.45)

### 5.4 Rules
- Expose tokens through a `ReachTheme` composable and `CompositionLocal` (explain the analogy to Flutter's `Theme.of(context)` / `ThemeExtension`).
- **Store the palette by its name** (enum key), never as colour values, so palette tweaks reach existing users.
- **Contrast unit tests:** for every palette × theme, assert `muted`/bg, `muted`/surface, `accentInk`/bg and `onAccent`/accent are all ≥ 4.5:1 (WCAG relative luminance). The table above passes; the test protects future edits.
- App "icons" are **monograms**: the first letters of up to two words of the app name, in JetBrains Mono, inside a 42dp circle with a 1.5dp `ring` outline. No real icons on Home, intent lists or search. (A user setting to show real icons may come later; design the component so it's possible.)
- No gradients, no emoji, no drop-shadow-heavy cards. Shapes: pills and fully rounded rows; sheet top corners 28dp.
- Touch targets ≥ 48dp everywhere.

---

## 6. Features and screens

### 6.1 App catalog and intents
- Six fixed intents: **Talk, Listen, Go, Make, Play, Tools**.
- Auto-assign each app on first sight using `ApplicationInfo.category`: SOCIAL → Talk; AUDIO → Listen; MAPS → Go; IMAGE, PRODUCTIVITY → Make; GAME, VIDEO, NEWS → Play; everything else → Tools. Also put the default dialer and SMS apps (via `RoleManager`) into Talk.
- Users can **move an app to another intent** (long-press its row in the intent detail → "Move to…") and **hide an app**. Persist the assignments in Room; a user's choice always beats auto-assignment.
- Work-profile apps appear inside the same intents with a small "WORK" mono tag. Support Android 15+ **private space** the way current docs expect from third-party launchers (it locks and unlocks, and its apps stay hidden when locked). Verify the exact APIs and permissions.

### 6.2 Launch history and predictions ("Now" row)
- Reach records every launch it performs (package, time, weekday) in Room. **This needs no permission** and is the primary prediction source.
- Prediction: score apps by launches in the same hour-of-day window (±1h), with a weekday/weekend split and recency decay; show the top 3 with a short reason label ("usually around now", "2 unread").
- Optional enhancement: if the user grants usage access (`PACKAGE_USAGE_STATS`), blend in `UsageStatsManager` data. Explain the permission in onboarding; never nag.

### 6.3 Home screen (vertical layout, top to bottom)
1. Date line (mono, muted), e.g. "SUN 08 NOV". In wallpaper mode, add a small "ACCENT FROM WALLPAPER" tag with an accent dot.
2. **Clock**, 112sp. **Long-press (~450ms) opens the Appearance sheet** (§6.8). It's a real focusable element; TalkBack users get a custom action "Appearance".
3. Context line (muted, 15sp): next calendar event if calendar permission is granted, otherwise hidden. Weather is out of scope for v1.
4. **NOW** label + 3 prediction tiles (surface background, 18dp corners, a mono reason label above the app name). Tap launches.
5. Flexible spacer.
6. **Intent words**, stacked, 52dp+ rows, aligned to the user's thumb side (left by default, right if chosen). Each row shows the word, then either the app count (mono, muted) or an accent badge "3 NEW" when its apps have unread notifications (§7.1).
   - The **Listen** row also shows an inline **play/pause button** (44–48dp accent circle) and the current track title plus "PLAYING · <APP>" when a media session is active (§7.2). Hidden when nothing is playing.
7. **"Type to launch" pill** (56dp, outlined, search icon, "OR PULL DOWN" hint). Tap or pull down anywhere on Home opens Search.
- **Swipe up** anywhere opens All Apps. **Double-tap** empty space locks the screen (§7.4), if enabled.

### 6.4 Intent detail
- Back button + mono "INTENT · N APPS"; accent dot + the intent word at 60sp.
- **"APP SHORTCUTS"** section: up to 4 pill buttons from real `LauncherApps` shortcuts of this intent's most-used apps. Each pill shows the shortcut label and the source app in mono caps ("Message Ayşe · WHATSAPP"). Requires being the default launcher (`hasShortcutHostPermission`); hide the section otherwise.
- Listen only: a **Now Playing card** (source app, title, prev / play-pause / next, progress bar).
- App rows (58dp+): monogram, name, per-app unread count badge if any, arrow. Sorted by launch frequency, then alphabetically.
- At the bottom, a horizontally scrolling row of chips for all six intents (the current one filled with accent) to switch intent in one tap.

### 6.5 Press, slide, release (the signature gesture)
- Press and hold an intent word (~300ms; short enough to feel instant, long enough not to fight taps). Its apps **fan upward** in a staggered column beside the thumb, the nearest app closest to the finger.
- Slide: the app under the finger highlights (accent fill, onAccent text, soft accent halo) with a light haptic tick on each change.
- Release on an app → launch. Release elsewhere, or slide back onto the word → cancel, with a hint label "RELEASE TO OPEN · SLIDE OFF TO CANCEL".
- Implement with Compose `pointerInput` / `awaitEachGesture`; explain it carefully in docs (the analogy to Flutter's `GestureDetector` / `RawGestureDetector` and the gesture arena).
- Accessibility: the gesture is an *enhancement*; tapping always opens the intent detail, which TalkBack users rely on.

### 6.6 Search ("type to launch")
- Opens with the keyboard up; results sit **just above the keyboard** (thumb zone), the input below the results, with a mono label "APP, CONTACT OR ACTION".
- Empty query: 4 suggested apps from predictions.
- Ranking: word-prefix match > substring match, boosted by launch frequency, max ~6 results. Top result is highlighted (accent fill) with an "ENTER ↵" hint; **Enter launches the top result**.
- Sources: apps; app shortcuts; contacts (only if `READ_CONTACTS` is granted; tap → call/message chooser); **commands**: "Switch to <Palette>" for palette names (≥ 2 typed letters), "Switch to dark/light theme", "Appearance settings" (matches "appearance", "theme", "palette"); a final "Search the web for '<q>'" fallback via `ACTION_WEB_SEARCH`.
- Diacritic- and case-insensitive matching with Turkish rules ("i" matches "İ", "ş" matches "s").

### 6.7 All apps
- A heading "Everything" + mono count; the subtitle "Names, not icons — scan by reading."
- Apps grouped by first letter (locale-aware), each group a mono letter label followed by app names **flowing as wrapped text rows** (a "word wall"), 21sp, each name a 48dp tap target.
- An **A–Z letter rail** on the thumb side: scrub to jump, a large accent bubble shows the current letter, with a haptic tick per letter.
- Long-press an app name → small menu: App info, Move to intent…, Hide, Uninstall.

### 6.8 Appearance sheet (opened by long-pressing the clock, or via a search command)
A bottom sheet over Home (Home stays visible above it, with a dim scrim); **every change applies live, with no Save button**. Contents:
- Title "Appearance" + "Done" accent pill.
- **THEME**: segmented Auto / Dark / Light. Auto follows the system dark-mode setting; the label shows "· FOLLOWS ANDROID".
- **PALETTE**: a horizontal row of 7 swatch cards (84×96dp), each painted in that palette's real bg, with accent and fg dots and its name in its fg colour; the selected card has a 2dp `accentInk` border.
- **Show wallpaper**: a switch with the subtitle "Blurred behind Reach, accent picked from it". When on, the palette label reads "PALETTE · ACCENT FROM WALLPAPER".
- **Thumb side**: segmented Left / Right.
- Persist with DataStore; Home re-themes instantly via the theme state Flow.

### 6.9 Wallpaper mode
- When enabled, Home uses a window theme with `windowShowWallpaper=true` and a transparent window background.
- On Android 12+ where cross-window blur is enabled, blur the wallpaper behind the window; otherwise rely on the scrim alone.
- Scrim: the palette `bg` at **82% opacity (dark) / 86% (light)**, applied always, so 11–12sp labels stay readable over any photo.
- Base colours come from the selected palette (Paper & Ink is the recommended default for this mode); the **accent comes from `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)`** (primary colour), then gets adjusted: derive `accentInk` by darkening/lightening until it reaches ≥ 4.5:1 on bg, and pick `onAccent` as whichever of bg/fg contrasts more with the accent. Update when the wallpaper changes.
- Write a test that runs the adjustment over a set of hard colours (pure white, pure yellow, mid-grey, near-black) and asserts the contrast rules.

### 6.10 Accessibility (applies everywhere)
- Every control is a real semantic button/switch with a content description; icon-only buttons are labelled; badges are included in the row's description ("Talk, 3 new notifications").
- All gestures have tap alternatives; custom TalkBack actions for long-press features.
- Respect font scaling: test at 200% and make layouts scroll or reflow instead of clipping.
- Respect "remove animations".

---

## 7. System integrations (`:core:system`) — each behind an interface, each optional

The app must work fully, though with fewer features, when any permission is denied. Explain every permission in a short in-app rationale and in `docs/ANDROID_APIS.md`.

1. **Notification counts** — `NotificationListenerService` (the user enables it in system settings; deep-link there). Count active, non-ongoing notifications per package; sum them per intent; expose a `Flow<Map<packageName, Int>>`.
2. **Media controls** — `MediaSessionManager.getActiveSessions(listenerComponent)` (reuses the notification-listener access). Expose the active session's title, source app, state and progress; play/pause/next/prev through `MediaController.TransportControls`.
3. **App shortcuts** — `LauncherApps.getShortcuts` (dynamic + manifest + pinned) when `hasShortcutHostPermission()`; launch via `startShortcut`.
4. **Accessibility service** (opt-in, for two features only): double-tap to lock (`GLOBAL_ACTION_LOCK_SCREEN`) and a two-finger swipe down to open the notification shade (`GLOBAL_ACTION_NOTIFICATIONS`). Declare it minimally (no event types it doesn't need) and write the Play accessibility-API disclosure text into the docs. **Don't** use device-admin `lockNow` (it breaks biometric unlock). Opening the shade without the service (the hidden `StatusBarManager` API) is best-effort only, behind a feature flag, with a fallback.
5. **Usage stats** (optional blend, §6.2).
6. **Calendar** next event (optional, `READ_CALENDAR`).
7. **Wallpaper colours** (§6.9).
8. **Default home role** (§4).

Privacy: no analytics, no network, no data leaves the device. Say so in the README and in a `PRIVACY.md` suitable for the Play listing.

---

## 8. Documentation for a Flutter developer (mandatory, reviewed every milestone)

1. **KDoc on every public class and function**, explaining *why*, not just *what*.
2. **"Flutter analogy" notes** at the top of each important file, e.g.:
   ```kotlin
   /**
    * HomeViewModel — holds Home's UI state and handles user intents.
    *
    * Flutter analogy: this is your BLoC/Cubit. `StateFlow<HomeUiState>` ≈ the
    * BLoC's state stream; `onIntentTapped()` ≈ `add(IntentTapped())`. In
    * Compose, `collectAsStateWithLifecycle()` ≈ `BlocBuilder`.
    */
   ```
3. **`docs/FLUTTER_TO_COMPOSE.md`** — a mapping table with a short code example per row: Widget ↔ @Composable; StatelessWidget/StatefulWidget ↔ composables + `remember`/`rememberSaveable`; `setState` ↔ `mutableStateOf`; BLoC/Cubit ↔ ViewModel + StateFlow; Streams ↔ Flow; get_it/injectable ↔ Hilt; shared_preferences ↔ DataStore; sqflite/drift ↔ Room; `Theme.of(context)`/ThemeExtension ↔ CompositionLocal; Column/Row/Stack ↔ Column/Row/Box; `ListView.builder` ↔ `LazyColumn`; GestureDetector ↔ `Modifier.pointerInput`; `Navigator` ↔ (why we use a state machine instead); MethodChannel ↔ "not needed, you're already native"; pubspec.yaml ↔ `libs.versions.toml` + `build.gradle.kts`; `flutter run` ↔ Gradle tasks / Android Studio run configs; `AnimatedContainer`/implicit animations ↔ `animate*AsState`/`AnimatedContent`.
4. **`docs/ARCHITECTURE.md`** — the module diagram (Mermaid), how data flows from a system event (e.g. a new notification) to a pixel on Home, and where to add a new feature.
5. **`docs/ANDROID_APIS.md`** — every Android API and permission used: what it does, why Reach needs it, how the user grants it, what happens without it, and Play policy notes.
6. **`docs/GESTURES.md`** — how press-slide-release and the letter rail are implemented, step by step.
7. **`docs/DECISIONS.md`** — short ADR-style entries for each non-obvious choice.
8. **`README.md`** — what Reach is, how to build, run, test and set it as the default launcher on an emulator and a device, plus troubleshooting.
9. **Comments in code:** explain Android-specific lifecycle or API quirks where they occur. Don't comment the obvious.

---

## 9. Milestones (each ends with green `./gradlew build test lint`, updated docs, a review and a commit)

- **M0 — Foundation:** Gradle/version catalog, modules, Hilt, `ReachTheme` with all palettes + derived tokens + contrast tests, fonts, a `MainActivity` registered as HOME, `docs/PLAN.md`, the README skeleton, `FLUTTER_TO_COMPOSE.md` v1.
- **M1 — Core launcher:** app catalog (LauncherApps, live updates, cache), intent auto-assignment, Home (clock, intents, type-to-launch pill), intent detail, launching with animation, launch history, the Now row, Home-button/back behaviour.
- **M2 — Speed:** Search with ranking and commands, All Apps with the letter rail, the press-slide-release gesture, haptics.
- **M3 — System features:** notification counts, media controls (Home row + Listen card), app shortcuts, default-home onboarding, permissions UX.
- **M4 — Personalisation:** Appearance sheet, DataStore persistence, Auto theme, thumb side, wallpaper mode with blur/scrim and wallpaper-derived accent, move/hide apps.
- **M5 — Polish:** accessibility pass (TalkBack, 200% font, reduced motion), the optional accessibility service (lock, notification shade), work profile + private space, Turkish strings, Baseline Profile, a final docs pass, a release build configuration (R8, signing placeholders — never commit real keys).

---

## 10. Definition of done (whole project)

- Installs on an emulator (latest API level) and on a minSdk 28 emulator; can be set as the default home app; every flow in §6 works.
- With **every** optional permission denied, the app is still usable and never crashes.
- All tests pass; lint is clean or every suppression is explained.
- Every doc in §8 exists and matches the code.
- A final report from you (the orchestrator): what was built, what deviates from the spec and why, known issues, and suggested next steps.

Ask the owner only about decisions that are hard to reverse or genuinely ambiguous; otherwise make the sensible choice and record it in `docs/DECISIONS.md`.
