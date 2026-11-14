# Reach — build plan

This is the working plan for building Reach. The product/design spec lives in
[`reach-launcher-prompt.md`](reach-launcher-prompt.md) and is the source of truth;
this file says **how** we get there: modules, contracts, milestones and who owns what.
Visual reference: the "Android Launcher Concept" design canvas (claude.ai artifact).

---

## 1. Module layout

```
:app                  MainActivity (HOME), Application (@HiltAndroidApp), manifest, and
                      (from M1) the top-level ReachApp composable wiring features together.
:core:model           Pure Kotlin (JVM, no Android). Domain types shared by everyone:
                      ReachIntent, AppKey, CatalogApp, LaunchEvent, Prediction,
                      AppearanceSettings, PaletteId, MediaState, AppShortcut, …
                      plus pure helpers (TextFolding for Turkish-aware matching).
:core:designsystem    ReachTheme, palettes, derived tokens, colour maths, typography,
                      bundled fonts, shared components (Monogram, pills, rows…).
:core:data            CONTRACTS: repository interfaces + system-source interfaces.
                      IMPLEMENTATIONS: Room (launch history, intent assignments),
                      DataStore (settings), the catalog repository, prediction engine,
                      intent categoriser.
:core:system          Implementations of the system-source interfaces declared in
                      :core:data (LauncherApps, RoleManager, NotificationListenerService,
                      MediaSessionManager, shortcuts, usage stats, calendar, contacts,
                      wallpaper colours, accessibility service).
:feature:home         HomeViewModel (the surface state machine), Home screen, intent
                      detail, press-slide-release gesture. Hosts the other features.
:feature:search       Type-to-launch screen, SearchViewModel, ranking, commands.
:feature:allapps      A–Z word wall + letter rail.
:feature:appearance   Appearance bottom sheet.
```

### Dependency rules

```
app ──► feature:* ──► core:data ──► core:model
 │         │    └───► core:designsystem ──► core:model
 │         └ feature:home ──► feature:search / feature:allapps / feature:appearance
 └──► core:system ──► core:data
```

- **Features never import `:core:system`.** They talk to Android only through the
  interfaces in `:core:data` (`…data.source.*`). Hilt bindings in `:core:system` provide
  the implementations; `:app` is the only module that sees both sides. This keeps
  features testable with fakes and makes every system integration optional.
- `:core:model` has no Android dependency, so its tests are plain, fast JVM tests.
- Feature modules don't depend on each other, except `:feature:home`, which hosts the
  others (Search/AllApps/Appearance are *states* of the Home surface).

## 2. Contracts (written by the orchestrator before delegation)

| Contract | Module / package | Implemented by |
|---|---|---|
| Domain types | `:core:model` `com.baklavapp.reachlauncher.core.model` | — |
| `AppCatalogRepository`, `LaunchHistoryRepository`, `PredictionRepository`, `SettingsRepository` | `:core:data` `…core.data.repository` | `:core:data` (catalog-agent, appearance-agent) |
| `InstalledAppsSource`, `ProfileSource`, `AppLauncher`, `DefaultAppsSource`, `HomeRoleSource`, `NotificationCountSource`, `MediaSessionSource`, `ShortcutSource`, `UsageStatsSource`, `CalendarSource`, `ContactsSource`, `WallpaperColorSource`, `ScreenActionsSource` | `:core:data` `…core.data.source` | `:core:system` (catalog-agent, system-agent) |
| `ReachTheme`, `ReachColors`, `ReachTypography`, palettes, colour maths | `:core:designsystem` | orchestrator (M0), designsystem-agent (components) |

Hilt in M0 is the root container only (`@HiltAndroidApp`, `@AndroidEntryPoint`); the
`@Module`/`@Binds` bindings for these contracts arrive with their implementations in M1+.

Contract changes after M0 go through the orchestrator: an agent that needs a new
method proposes it in its report; the orchestrator updates the interface and fakes.

Every interface in `:core:data` ships with a **fake** in `:core:data`'s
`testFixtures`-style package `…core.data.fake` (in `src/main`, used by feature tests),
so feature agents can test without Android.

## 3. Surface state machine (in `HomeViewModel`)

```
            ┌──────────── Home ◄──────────────┐  (Home button from anywhere → Home)
   tap word │   pull down / pill │ swipe up   │ long-press clock / search command
            ▼                    ▼            ▼
      IntentDetail(intent)    Search       AllApps        Appearance (sheet over Home)
            │ Back → Home        │ Back → Home │ Back → Home   │ Back/Done → Home
```

`HomeSurface` is a sealed interface: `Home`, `IntentDetail(intent)`, `Search`,
`AllApps`, `Appearance`. System Back pops one level; on `Home` it does nothing.
`MainActivity.onNewIntent` (Home pressed) → `HomeViewModel.onHomePressed()` → `Home`,
which also closes the keyboard.

## 4. Milestones and owners

Each milestone ends with: `./gradlew build test lint` green at the root, docs updated
(docs-agent), an independent review (reviewer-agent), fixes, and one commit.

| Milestone | Waves (parallel agents in isolated worktrees) |
|---|---|
| **M0 Foundation** | Orchestrator: Gradle, version catalog, modules, Hilt, `:core:model`, `:core:data` contracts, design tokens + `ReachTheme` + contrast tests, fonts, HOME `MainActivity`, PLAN, DECISIONS. docs-agent: README skeleton, `FLUTTER_TO_COMPOSE.md` v1. reviewer-agent. |
| **M1 Core launcher** | Wave 1: **catalog-agent** (`:core:data` impl: Room, categoriser, catalog repo, launch history, predictions; `:core:system`: LauncherApps source + callback, launcher with clip-reveal, default dialer/SMS roles) ∥ **designsystem-agent** (components: Monogram, AppRow, Pill, IntentWordRow, SectionLabel, NowTile). Wave 2: **home-agent** (HomeViewModel state machine, Home, intent detail, Home/Back behaviour). Then docs-agent, reviewer-agent. |
| **M2 Speed** | **search-agent** (`:feature:search`, plus the real `ShortcutSource` and `ContactsSource` in `:core:system`, which search needs) ∥ **allapps-agent** (`:feature:allapps`) ∥ **home-agent** (press-slide-release, haptics). docs-agent (GESTURES.md), reviewer. |
| **M3 System features** | Wave 1: **system-agent** (notification listener, media sessions, usage stats, calendar, default-home role, permission state). Wave 2: **home-agent** (badges, Listen row + Now Playing card, shortcut pills, onboarding, permissions UX). docs-agent (ANDROID_APIS.md), reviewer. |
| **M4 Personalisation** | **appearance-agent** (`:feature:appearance`, DataStore settings, Auto theme, thumb side, move/hide UI) ∥ **system-agent** (wallpaper colours, window theme/blur). docs, reviewer. |
| **M5 Polish** | **system-agent** (accessibility service, work profile, private space) ∥ **a11y pass agent** (TalkBack, 200% font, reduced motion, Turkish strings) ∥ **perf agent** (Baseline Profile, release config). Final docs pass, final review, final report. |

### Carried-over notes (from the M0 review)
- **M1:** `MainActivity.onNewIntent` → `HomeViewModel.onHomePressed()` and a `BackHandler`
  (§4); the catalog excludes Reach's own LAUNCHER entry; add `<queries>` for
  `WEB_SEARCH`, `DIAL` and `SENDTO` intents before `AppLauncher.perform` resolves them;
  the `AppLauncher` implementation tracks the resumed Activity (D11).
- ~~M4: `SystemBarStyle` from the resolved Reach theme~~ — done in M1 (`MainActivity`).
- **M2:** replace `SearchPlaceholder` / `AllAppsPlaceholder` in `feature/home/.../ui/PlaceholderSurfaces.kt`;
  press-slide-release attaches via `HomeScreen(wordModifier = …)` and must consume pointer
  moves once the fan opens (Home scroll/swipe detection, D29) and avoid
  `WindowInsets.systemGestures`; launch origins via `rememberLaunchOrigin()` /
  `Modifier.trackLaunchOrigin()`.
- **M3:** Home context line slot (calendar) and the Now Playing card slot are marked in
  `HomeScreen` / `IntentDetailScreen`.
- **M4:** replace `AppearancePlaceholderSheet`; app-row long-press "Move to… / Hide" (TODO in
  `IntentDetailScreen`); avoid the one-frame theme flash for users who forced Light/Dark
  (settings arrive from DataStore a few ms after the first frame — needs a synchronous
  settings snapshot); consider a `LaunchResult` type (D32).
- **M5:** verify private-space APIs/permissions (D13); revisit R8 `packageScope` (D16);
  the owner reviews all Turkish strings (agents' wording kept until then, e.g. "amaç"
  for intent, "Yazıp aç" for Type to launch).

### Rules for every sub-agent prompt
1. Exact files/modules it owns; files it must not touch (always: `gradle/libs.versions.toml`
   — ask the orchestrator via its report — `:core:model`, `:core:data` contracts,
   `ReachTheme` tokens, other agents' modules).
2. The relevant spec sections copied in verbatim.
3. Interfaces to implement/consume (paths).
4. Definition of done: compiles; unit tests; KDoc on all public API with *why*;
   "Flutter analogy" header on important files; `./gradlew :<module>:check` passes;
   a short report: what was built, deviations, contract change requests.

## 5. Docs (§8 of the spec)

| Doc | First written | Owner |
|---|---|---|
| `README.md` | M0 skeleton, completed M5 | docs-agent |
| `docs/FLUTTER_TO_COMPOSE.md` | M0 v1, extended each milestone | docs-agent |
| `docs/DECISIONS.md` | M0, appended continuously | orchestrator + agents (via reports) |
| `docs/ARCHITECTURE.md` | M1 | docs-agent |
| `docs/GESTURES.md` | M2 | docs-agent |
| `docs/ANDROID_APIS.md` | M1 (catalog), completed M3–M5 | docs-agent |
| `PRIVACY.md` | M3 | docs-agent |
