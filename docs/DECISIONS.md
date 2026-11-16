# Decisions

Short ADR-style entries for every non-obvious choice. Newest last. Each entry: context,
decision, consequences. Deviations from the spec are marked **[deviation]**.

---

## D1 — Keep the Android Studio template's toolchain (M0)
**Context.** The repo started from an Android Studio "Empty Compose Activity" template:
AGP 9.4.1, Gradle 9.6.0, compileSdk/targetSdk 37, minSdk 28, package
`com.baklavapp.reachlauncher`.
**Decision.** Keep package, Gradle wrapper and SDK levels; bump libraries to the latest
stable releases found on Google Maven / Maven Central on 2026-11-14 (Kotlin 2.4.21,
KSP 2.3.14, Hilt 2.60.1, Compose BOM 2026.09.00, Room 2.8.5, DataStore 1.3.0…).
Google Play currently requires new apps to target API 36; 37 satisfies "or newer".
**AGP stays on the 9.4 line (9.4.1).** AGP 9.5.1 is the newest on Google Maven, but
Android Studio refuses to sync a project whose AGP is newer than the IDE supports
("incompatible version (AGP 9.5.1)… Latest supported version is AGP 9.4.0"). AGP is
capped by the owner's Android Studio, not by Maven: bump it only after updating Studio.
**Consequences.** Version bumps are a deliberate task: Lint's `GradleDependency` /
`NewerVersionAvailable` checks are disabled so a new library release doesn't break the build.

## D2 — AGP 9 built-in Kotlin, explicit per-module build files (M0)
**Context.** AGP 9 compiles Kotlin itself; the `org.jetbrains.kotlin.android` plugin is
no longer applied. Big projects often add a `build-logic` folder of convention plugins.
**Decision.** No convention plugins. Each module has a short, explicit
`build.gradle.kts`. The Kotlin plugins are declared in the root with `apply false` to pin
the compiler version AGP uses. `:core:model` is a pure Kotlin/JVM module.
**Consequences.** ~40 lines of repeated `android {}` config across modules, but every
module's build is readable on its own — friendlier for a developer new to Gradle. Revisit
if the module count grows.

## D3 — Contracts in `:core:data`, implementations of system sources in `:core:system` (M0)
**Context.** The spec asks for every system integration behind an interface, and for
features that work when any permission is denied.
**Decision.** All interfaces (repositories and system sources) live in `:core:data`.
`:core:system` implements the system sources and depends on `:core:data`; features
depend only on `:core:data`. `:app` sees both and Hilt wires them.
**Consequences.** Features can't accidentally call Android system APIs; their tests use
the fakes in `core.data.fake`.

## D4 — Fakes ship in `src/main` of `:core:data` (M0)
**Context.** Feature tests *and* Compose `@Preview`s need fake data sources.
**Decision.** Put the fakes in `src/main` (`…core.data.fake`) rather than a test-fixtures
source set, so previews can use them too.
**Consequences.** R8 strips them from release builds because nothing references them.

## D5 — Colour `mix` is a linear sRGB-channel blend (M0)
**Context.** §5.3 defines derived tokens with `mix(a, b, t)` = "linear RGB blend".
Compose's own `lerp(Color, Color, Float)` interpolates in Oklab.
**Decision.** `ColorMath.mix` blends the sRGB channel values linearly, the same as CSS
`color-mix(in srgb, …)` used by the design mockups. Results are quantised to 8-bit per
channel (Compose stores sRGB colours that way).
**Consequences.** Derived tokens differ slightly from the mockup's hand-picked values
(e.g. Lime dark surface is `#1F2022` vs. the mockup's `#1B1C20`). The spec formula wins.

## D6 — Wallpaper accent: also nudge the fill when no on-colour reaches 4.5:1 **[deviation]** (M0)
**Context.** §6.9: `onAccent` = whichever of bg/fg contrasts more with the wallpaper
accent. For mid-luminance accents — greys around `#6A`–`#7E`, e.g. `#757575` on
Paper & Ink dark (4.25:1 against bg, 4.04:1 against fg) — *neither* reaches 4.5:1, so
text on accent pills would be unreadable. (`#808080` just passes: 4.95:1 on Paper & Ink's bg.)
**Decision.** After choosing `onAccent`, if it is still below 4.5:1, move the accent fill
in small steps towards whichever of white/black contrasts more with `onAccent` until it
passes. `accentInk` is then derived from that accent as specified.
**Consequences.** The fill may be a little lighter/darker than the wallpaper colour for
greyish wallpapers. Covered by `PaletteContrastTest` (`#757575` is in the hard-colour
set) and `WallpaperAccentTest`.

## D7 — Bricolage Grotesque optical size per role (M0)
**Context.** The fonts are bundled as variable fonts. Browsers set the `opsz` axis from
the font size automatically; Android doesn't.
**Decision.** Each Bricolage type role gets its own `Font` instance with `opsz` = its
size (clamped to the axis range 12–96), so the 112sp clock uses display-optimised shapes.
Inter roles do the same on Inter's own opsz axis (14–32).
**Consequences.** A few more `Font` objects; no visible cost.

## D8 — Launcher manifest flags (M0)
**Decision.** `MainActivity` declares `MAIN/HOME/DEFAULT` and, additionally,
`MAIN/LAUNCHER`, so Reach can be opened from another launcher (and run from Android
Studio) before it becomes the default home. The catalog will exclude Reach's own entry.
It also handles most `configChanges` itself, so Home is never recreated (no blank flash)
when dark mode, font scale, locale or orientation change; Compose recomposes instead.
`<queries>` for `MAIN/LAUNCHER` replaces `QUERY_ALL_PACKAGES` (see §4 and Play's
package-visibility policy; a launcher only needs launchable apps).
Other launcher flags, as AOSP Launcher3 uses them: `stateNotNeeded` (Home can restart
without saved state), `clearTaskOnLaunch` (Home's task always shows Home itself),
`resumeWhilePausing` (Home appears while the closing app is still pausing).
**Trade-off.** Because `uiMode` is handled in-process, the *window* background
(`values-night/themes.xml`) doesn't switch when dark mode is toggled while Reach is
running. Compose paints the real background on every frame, so this only matters for
the very first frame after a cold start.

## D9 — No cloud backup (M0)
**Decision.** `android:allowBackup="false"`.
**Why.** "No data leaves the device" (§7). Launch history would otherwise be uploaded
to the user's Google Drive backup.
**Consequences.** Moving to a new phone starts with empty history and default settings.

## D10 — ktlint (not detekt) for static checks (M0)
**Context.** §3 allows ktlint or detekt. detekt's Kotlin 2.4-compatible line (2.0) is
still alpha; 1.23 targets older Kotlin compilers.
**Decision.** ktlint via `org.jlleitschuh.gradle.ktlint` 14.2.0, `ktlint_official` style,
wired into every module's `check` task. Android Lint runs with `warningsAsErrors`.

## D11 — No Android types in contracts; an Activity tracker inside `:core:system` (M0, after review)
**Context.** The first contracts returned `android.content.Intent` (settings screens,
role request) and `AppLauncher` had no way to reach a `View`. On the plain JVM,
constructing an `Intent` throws ("Method … not mocked"), and
`ActivityOptions.makeClipRevealAnimation` / `startActivityForResult` (needed by
`RoleManager.createRequestRoleIntent`) both need the current Activity.
**Decision.** Contracts use domain types only: `SystemScreen` (notification access, usage
access, accessibility, home role) and `ExternalAction` (web search, dial, message, contact
card). `AppLauncher.open(screen)` / `perform(action)` replace the per-source
`…Intent()` methods. The `:core:system` implementation is a singleton that keeps a
`WeakReference` to the resumed Activity (registered in `ReachApplication.onCreate`), uses its decor
view for the clip-reveal animation (`LaunchOrigin` is in window pixels, i.e. decor-view
coordinates) and converts to screen coordinates for `startMainActivity`'s `sourceBounds`.
Without a resumed Activity it launches without animation.
**Consequences.** ViewModel tests are plain JVM tests with the fakes. Runtime-permission
requests (contacts, calendar) still need an `ActivityResultLauncher` in the UI layer (M3).

## D12 — Notification counts keyed by package *and profile* **[deviation]** (M0, after review)
**Context.** §7.1 says `Flow<Map<packageName, Int>>`. The same package can be installed in
the personal and work profiles; a package-only key merges their badges.
**Decision.** `NotificationCountSource.countsByApp: Flow<Map<PackageUser, Int>>`, where
`PackageUser` = package name + user serial.

## D13 — Profiles and private space in the contracts (M0, after review)
**Decision.** `ProfileSource` exposes `ProfileState(userSerial, type, isQuietModeEnabled)`
and `setQuietMode()`. Quiet mode covers both a paused work profile (apps shown dimmed via
`CatalogApp.isProfilePaused`) and a locked private space (apps never emitted).
`AppCatalogRepository` filters **every** emission, including the instant cached one,
against current profile state, so private-space apps cached from an unlocked session can
never flash onto Home, predictions or search. The exact Android 15+ private-space APIs and
permissions (`LauncherApps.getLauncherUserInfo`, `ACCESS_HIDDEN_PROFILES`) are verified in M5.

## D14 — Shortcuts as a stream; nullable shortcut activity (M0, after review)
**Decision.** `ShortcutSource.shortcuts: Flow<List<AppShortcut>>` re-emits on
`LauncherApps.Callback.onShortcutsChanged` and permission changes, so pills and search
never go stale. `AppShortcut` carries `PackageUser` + a nullable `activityName`, because
pinned shortcuts may have no activity.

## D15 — Unknown stored intent keys mean "no user choice" (M0, after review)
**Decision.** `ReachIntent.fromStorageKey` returns null for unknown keys (e.g. a row written
by a newer build). The catalog then falls back to auto-assignment instead of silently
recording "Tools" as the user's choice.

## D16 — Lint and ktlint configuration notes (M0)
- `ObsoleteLintCustomCheck` is disabled: some AndroidX artifacts ship lint checks built
  against a different lint API; lint skips them and warns, and `warningsAsErrors` would
  turn that into a build failure we can't fix.
- The ktlint *engine* is pinned (`ktlintEngine` in the catalog, applied from the root
  build file); the Gradle plugin's default engine is older than our Kotlin.
- Modules without tests yet set `failOnNoDiscoveredTests = false` (Gradle 9 otherwise
  fails). Remove it from a module once it has tests.
- `release { optimization { packageScope = … } }` is the template's AGP 9 default and is
  kept until M5, when the release build configuration (R8, keep rules, signing) is done
  properly and its scope re-evaluated.

## D17 — Theme: full Material 3 mapping, no silent defaults (M0, after review)
**Decision.** `ReachTheme` maps *every* Material 3 colour role and all 15 type styles onto
Reach tokens and provides `LocalTextStyle`, so M3 infrastructure (switches, segmented
buttons, text fields) never shows stock purple or Roboto. `LocalReachColors` /
`LocalReachTypography` have no default and throw outside `ReachTheme`, so a missing theme
is caught immediately instead of rendering in Lime dark.

## D18 — Derived text tokens `soft`, `groupDim`, `dim` (M1)
**Context.** The mockups use three colours the spec's token list doesn't name: secondary
body text, non-active All Apps groups, and intent words dimmed while the gesture fan is open.
**Decision.** Derive them like the other tokens: `soft = mix(bg, fg, 0.84)`,
`groupDim = mix(bg, fg, 0.62)`, `dim = mix(bg, fg, 0.20)`. `groupDim` uses 0.62 rather than
the mockup's ≈0.57 because 0.57 gives only ≈4.0:1 on light Lime. `PaletteContrastTest`
requires `soft` and `groupDim` ≥ 4.5:1 on bg and surface for every palette × theme.
**[deviation]** `dim` is ≈1.5–1.9:1 and is **not text-safe**: it is only for the label of a
control that is inactive while the fan is open, which WCAG 1.4.3 exempts. A test keeps
`dim < groupDim < soft` and `dim ≥ 1.4:1` so it stays visible.

## D19 — One click/accessibility recipe: `Modifier.reachClickable` (M1)
**Decision.** Every tappable component uses `reachClickable`: a `combinedClickable` plus,
when a description is given, `clearAndSetSemantics` (applied after the clickable) so
TalkBack reads one sentence ("Talk, 3 new notifications"). A long-press is exposed both as
the long-click action and as a named custom accessibility action (§6.10).

## D20 — Labels are upper-cased at display time with `LocalLocale` (M1)
**Decision.** Components take natural-case strings and upper-case them for display with
the composition's locale (Turkish "i" → "İ"); TalkBack reads the natural case. Lint flags
`Locale.getDefault()` inside composables (`NonObservableLocale`), so composables use
`LocalLocale`; pure helpers (`monogramFor`, `toLabelCase`) default to `Locale.getDefault()`.

## D21 — Mockup sizes below 48dp are raised to 48dp; `ReachComponentDimens` (M1)
**Decision.** Back, close, chips and "Done" are 48dp (the mockups use 44dp; §5.4's 48dp
touch-target rule wins). The inline play button *looks* 44dp but has a 48dp touch target.
Component paddings and gaps from the mockups live in `ReachComponentDimens`; they may be
folded into `ReachDimens` later. Pill corners are a fixed 24dp so pills that grow taller at
200% font become rounded rectangles instead of blobs.

## D22 — Intent rows: physical thumb side and reflow (M1)
**Decision.** `IntentWordRow` aligns with absolute left/right (the thumb side is physical,
so it must not flip in right-to-left locales) and lays out as a `FlowRow`, so at 200% font
the Listen row's inline player wraps under the word instead of clipping.

## D23 — Default dialer/SMS without `RoleManager.getRoleHolders` **[deviation]** (M1)
**Context.** §6.1 says to find the default dialer and SMS apps "via RoleManager".
`RoleManager.getRoleHolders` is a `@SystemApi`; third-party apps can only ask whether
they hold a role themselves.
**Decision.** `TelephonyDefaultAppsSource` uses `TelecomManager.getDefaultDialerPackage()`
and `Telephony.Sms.getDefaultSmsPackage()` — public, no permission, same answer. Re-read on
every catalog sync/refresh.

## D24 — Catalog cache sync and privacy defaults (M1)
**Decision.** `AppCatalogRepository.apps` reads only Room (instant); an application-scoped
coroutine writes the *diff* between `LauncherApps` and the cache, so unchanged data never
re-emits. User assignments live in their own table and survive app removal, so a reinstall
keeps the user's choice. Privacy defaults fail closed: private-space apps are emitted only
while their profile is known *and* unlocked; a refused `getLauncherUserInfo` lookup counts
as PRIVATE; an unknown stored profile key reads as PRIVATE. Until M5 adds
`ACCESS_HIDDEN_PROFILES`, Android 15+ hides the private space from launchers entirely.

## D25 — Prediction scoring constants (M1)
**Decision.** Per launch: `0.5^(age / 14 days) × (hourWindow × dayType + 0.1)`, where
hourWindow = 1 (same hour) / 0.5 (±1h, circular) / 0, dayType = 1 (same weekday/weekend
type) / 0.25. Events older than 60 days are ignored. With usage access, add
`0.3 · ln(1 + launches in window)` (personal profile only — usage stats cover only the
user's own profile). Unread multiplies the score by `1 + 0.15 · min(n, 4)` (≤ ×1.6): it
boosts, never dominates, and never creates a prediction alone. Reason: Unread if n > 0;
"usually around now" if the window carries ≥ 50 % of the score from ≥ 2 same-day-type
launches; otherwise Recent. Re-ranked on catalog/history/unread/usage changes and every
local hour change (ticker sleeps to the next hour, capped at 5 min because `delay` doesn't
advance in deep sleep). Launch history isn't pruned yet (≈18k rows/year); revisit later.

## D26 — `REQUEST_DELETE_PACKAGES` for "Uninstall" (M1)
**Decision.** Uninstall opens `ACTION_DELETE` with `package:<pkg>` + `EXTRA_USER`
(`ACTION_UNINSTALL_PACKAGE` is deprecated since API 29). Apps targeting API 28+ need the
*normal* (install-time) permission `REQUEST_DELETE_PACKAGES`, declared in `:core:system`.
The system always shows its own confirmation.

## D27 — Clock follows the current time zone; paused-profile launch (M1)
**Decision.** Hilt provides `SystemZoneClock`, which reads `ZoneId.systemDefault()` on every
call (`Clock.systemDefaultZone()` would freeze the zone at creation). Launch hour/weekday
are stored at record time and never recomputed. Launching an app of a paused work profile
asks the system to resume the profile and returns without launching; the user taps again.
Profile-change broadcasts are registered per API level (`ACTION_PROFILE_AVAILABLE/UNAVAILABLE`
exist from API 35, not 31).

## D28 — Surface state machine instead of navigation; surface not saved (M1)
**Decision.** `HomeViewModel` owns a `MutableStateFlow<HomeSurface>`; `HomeRoute` switches on
it with `AnimatedContent` (Home and Appearance share a content key so the sheet opens over
Home; chip switches update the detail in place). Back is always consumed (`BackHandler`):
sub-surfaces → Home, Home → no-op. `onNewIntent` (singleTask HOME) → `onHomePressed()` →
Home + hide keyboard. The surface is not persisted (`stateNotNeeded`): after process death
Home is always correct. After a successful launch the surface returns to Home.

## D29 — Home swipes from nested-scroll leftovers (M1)
**Decision.** Home scrolls (200 % font must reflow, §6.10), so pull-down/swipe-up are
detected from the drag the scroll *couldn't* use, via a `NestedScrollConnection` (72dp past
the edge, or a ≥ 1200dp/s flick with ≥ 16dp travel) — not a second, competing drag
detector. Overscroll stretch is off on Home. TalkBack users get "All apps" as a custom
action on the search pill (swipe-up has no tap alternative otherwise).

## D30 — Clock ticks and long-press timing (M1)
**Decision.** The clock re-reads the time on `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED` (also
fired by the 12/24h toggle), `ACTION_TIMEZONE_CHANGED` and every resume — no timers.
Long-press uses `max(450ms, system long-press timeout)` via a `LocalViewConfiguration`
override, so the accessibility "touch & hold delay" is never shortened. The clock
auto-sizes (min 40sp) instead of wrapping at large font scales.

## D31 — Unread totals per package-in-profile (M1)
**Decision.** An intent's badge = sum of `countsByApp` over the *distinct* `PackageUser`s
of its visible apps (an app with two launcher activities counts once; personal and work
copies count separately). Hidden apps don't contribute.

## D32 — Paused-profile tap doesn't count as a launch (M1)
**Decision.** For a paused work-profile app, `launchApp` only requests the profile to resume
(D27); `HomeViewModel` records no history and keeps the current surface so the user can
tap again. (A future `LaunchResult` type — Launched / ResumingProfile / Failed — would make
this explicit instead of checking `isProfilePaused` in the ViewModel.)

## D33 — Small M1 wording/UX choices (M1)
- Turkish intent words: Konuş, Dinle, Git, **Üret** (create/produce — "Yap" is a vague
  "do"), Oyna, Araçlar. Pending the owner's review of all Turkish strings (M5).
- The Now label is just "NOW" (spec) rather than the mockup's "NOW · LEARNS YOUR ROUTINE".
- An empty intent detail shows "No apps here yet."
- The intent detail list is a plain `Column` in a scroll (tens of apps at most), which keeps
  the list and the intent chips anchored at the bottom in the thumb zone.

## D34 — `LaunchResult` instead of the ViewModel's paused check (M1, after review)
**Context.** D32 kept the paused-profile decision in `HomeViewModel` (`CatalogApp.isProfilePaused`),
a catalog snapshot that can be seconds stale: a just-resumed profile still looked paused (the
tap did nothing), a just-paused one looked running (the tap was recorded as a launch).
**Decision.** `AppLauncher.launchApp` returns `LaunchResult` (`LAUNCHED`, `RESUMING_PROFILE`,
`FAILED`), decided by `SystemAppLauncher` from *live* `UserManager` state. `HomeViewModel`
reacts only to the result: `LAUNCHED` → record + Home; `RESUMING_PROFILE` → stay, no history;
`FAILED` → "Couldn't open X". Shortcut launches (still `Boolean`) map to `LAUNCHED`/`FAILED`.
This supersedes the "future `LaunchResult`" note in D32; D32's behaviour is unchanged.

## D35 — Catalog: really cache-first, one shared pipeline, re-sort on resume (M1, after review)
**Decision.** `AppCatalogRepositoryImpl.apps` combines `ProfileSource.profiles` starting from
an empty list (`onStart { emit(emptyList()) }`), so the first emission depends on Room alone
(`UserManagerProfileSource` reads the system before its first emission). With no profile data,
the privacy filter fails closed — private-space apps hidden — and personal/work apps show,
not marked paused (D34 makes a missing "paused" flag harmless). The assembled list is shared
(`shareIn(appScope, WhileSubscribed(5 s, replayExpirationMillis = 0), replay = 1)`) by Home and
predictions; the zero replay expiry means a list built while the private space was unlocked is
never replayed to a later subscriber. The sort locale is a flow updated by `refresh()` (every
resume), because Reach handles `locale` config changes itself (D8) and nothing else would
re-sort after a language change.
**Not done.** `refresh()` still reads once via `installedApps.first()` (registering and
unregistering the `LauncherApps` callback per resume): a cheaper one-shot read needs a new
method on the `InstalledAppsSource` contract — an orchestrator decision.

## D36 — Database and system failures never crash Home (M1, after review)
**Context.** A launcher that crashes on start crash-loops on the home screen (e.g. disk full).
**Decision.** Catalog syncs catch `SQLiteException` (full, can't open, locked, corrupt),
`IllegalStateException` (Room's own checks) and `SecurityException`, log, and keep serving the
cache; a failing *observed* catalog query is retried with capped back-off on `SQLiteException`
only (anything else is a bug and should surface). `refresh()` swallows a failing system read.
`HomeViewModel` runs `refresh()` and `recordLaunch` "best effort" (everything but
`CancellationException` is dropped — the launch already happened). The application scope gets
a logging `CoroutineExceptionHandler` as a backstop, not as the primary handling.

## D37 — Room: destructive fallback on downgrade only (M1, after review)
**Decision.** `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`, so installing
an older dev build over a newer one wipes the cache/history instead of crashing on open.
**Consequences.** Upgrades are *not* covered: every schema version bump needs a real
`Migration` (tested against the exported schemas in `core/data/schemas/`), or users lose their
history and "Move to…" choices.

## D38 — `AppKey` includes the activity name (M1, after review)
**Context.** One package can have several launcher activities (e.g. a "Contacts" and a
"Phone" entry), and each is its own row on Home, so the key must be package + activity + user.
**Decision / trade-off.** Accepted: an app update that renames its launcher activity looks like
a removal + a new app, so it loses its "Move to…" choice, hidden flag and launch history.
Possible mitigation later: when a key disappears and a new one appears for the same package and
user in the same sync, carry the assignment over (or fall back to a package + user match).

## D39 — The surface is its own `StateFlow`; details precomputed; `catalogLoaded` (M1, after review)
**Context.** With the surface inside `uiState`'s `combine` + `stateIn(WhileSubscribed(5 s))`,
pressing Home after Reach had been in the background > 5 s updated the surface, but the first
resumed frame still showed the old intent detail/search until every input re-emitted.
**Decision.** `HomeViewModel.surface` is a plain `StateFlow` that `HomeRoute` reads directly;
`uiState` holds only data and precomputes the detail of all six intents (tens of apps, cheap),
so neither a Home press nor a chip switch waits for a recombine. `buildUiState` runs on the
default dispatcher. `HomeUiState.catalogLoaded` is false until the first catalog emission: intent
rows show no trailing count (instead of "0") and the Now block stays hidden. The theme root reads
a separate, `distinctUntilChanged` `HomeViewModel.theme`.

## D40 — All Apps letters from ICU `AlphabeticIndex` (M2)
**Decision.** Buckets come from `android.icu.text.AlphabeticIndex(locale)` plus English
labels, so Latin app names always get a letter (e.g. W in Turkish). Underflow, overflow and
inflow buckets merge into one "#" group at the end. Names keep the catalog's Collator order.
The rail shows every index letter; letters without apps are drawn in `dim` (an inactive
control, D18), skipped while scrubbing, and have no TalkBack action. The locale is pushed in
from the composition (`LocalLocale`), because a ViewModel survives language changes.
Turkish İ, I, Ç, Ş, Ö, Ü, Ğ are their own letters; English folds accents.

## D41 — Letter rail placement and accessibility (M2)
**Decision.** The rail sits inside `WindowInsets.systemGestures` on the thumb side, plus
`systemGestureExclusion()` as a backup for devices whose real gesture zone is wider than
reported. The home activity is exempt from Android's 200dp exclusion cap, but insetting keeps
Back working along the whole edge and doesn't depend on Reach being the default home.
TalkBack sees the rail as one "Letter index" control with "Jump to X" custom actions; group
labels are headings. At large font sizes only every k-th letter is drawn (the rest become
dots) while the slot mapping keeps every letter reachable. The bubble glyph is fixed at 36dp
so it never overflows its 72dp bubble.

## D42 — All Apps long-press menu as an in-surface overlay (M2)
**Decision.** The menu (App info, Move to intent…, Hide/Unhide, Uninstall) is an overlay —
scrim + bottom card with 28dp corners, `BackHandler`, `paneTitle`, content behind hidden
from TalkBack — not a `Dialog`, to fit the single-surface model (D28). Every menu item is
also a direct custom accessibility action on the app name. Hidden apps appear in a final
dimmed "Hidden" group (not on the rail) so they can be un-hidden. Only
`LaunchResult.LAUNCHED` closes All Apps.

## D43 — Search ranking (M2)
**Decision.** Matching is folded (`TextFolding`) and punctuation-collapsed. Tiers: label
prefix 2.25 > word prefix 2.0 > substring 1.0. Launch frequency adds at most 0.5
(`0.5 · min(1, ln(1+n) / ln 51)`, saturating at 50 launches), so it reorders within a tier
but never lifts a substring match above a word-prefix match. Ties: apps > shortcuts >
contacts > commands > alphabetical. At most 6 rows (≤ 3 shortcuts, ≤ 3 contacts), then
"Search the web for '<q>'" always last. Empty query: 4 predictions.

## D44 — Commands: ≥ 2 letters, bilingual keywords in code **[deviation]** (M2)
**Context.** §6.6 only requires ≥ 2 typed letters for palette commands.
**Decision.** *All* commands need ≥ 2 letters and match word prefixes, so a single "a" doesn't
flood the list with commands. Keywords live in code (`CommandKeywords`) in English **and**
Turkish ("theme"/"tema", "appearance"/"görünüm", "palette"/"palet", "dark"/"koyu",
"light"/"açık") and match whatever the UI language — resources only expose the current
language. Display text is localised from resources. Palette display names live in
`:core:designsystem` `PaletteNames.kt` (reused by Appearance in M4); the keyword list must stay
in sync with them.

## D45 — Contacts in search (M2)
**Decision.** `READ_CONTACTS` (runtime) is requested only from an "Include contacts" row shown
after ≥ 2 typed letters, with a one-line rationale. Any denial hides the row for the session
(not persisted; it may reappear once after a process restart — a persisted flag would need a
new setting). Lookups use `Contacts.CONTENT_FILTER_URI` with a limit, then one Phone query
ordered `IS_SUPER_PRIMARY DESC, IS_PRIMARY DESC`; the ViewModel debounces 150 ms, keeps only
the latest lookup (`mapLatest`) and re-matches names with `TextFolding`. Tapping a contact
opens inline Call / Message / Contact-card pills. Work-profile contacts are left for M5.

## D46 — Search query lives in the UI (M2)
**Decision.** The text field's text is UI state (`rememberSaveable`) mirrored to the
Activity-scoped `SearchViewModel`, so typing never waits on ranking. `onOpened` / `onClosed`
reset per-opening state. Enter re-ranks the current query directly if the state lags a
keystroke behind. Search adds no `BackHandler`; Home's closes it.

## D47 — Shortcut source re-reads (M2)
**Decision.** `LauncherAppsShortcutSource` re-reads everything on collection start,
`onShortcutsChanged`, package callbacks and profile broadcasts. It skips locked private
spaces, returns nothing for locked/paused profiles (the system throws
`IllegalStateException`), drops disabled or unlabelled shortcuts, and prefers the short
label. There is no broadcast when Reach becomes default home, so the permission change is
noticed on the next collection or callback. Turkish palette names ("Kâğıt ve Mürekkep"…)
await the owner's review.

## D48 — Press-slide-release hold time (M2)
**Decision.** The fan opens after 300 ms (§6.5) — unless the system long-press timeout is
> 500 ms. Platform defaults are 400 ms (500 ms before API 31), so anything longer means the
user chose a longer "Touch & hold delay"; then that delay is used, so the fan never steals
their taps (same rule as D30). The hold is abandoned on moving past touch slop, lifting, a
second finger, or another detector consuming the event; until it fires nothing is consumed,
so a tap still opens the intent detail.

## D49 — Fan gesture: consumption and cancel detection (M2)
**Decision.** The detector is attached via `wordModifier`, which `IntentWordRow` applies
*before* its click, so it is the click's parent and a child of Home's scroll. Once armed it
reads events in `PointerEventPass.Initial` and consumes every change, so the word's click,
Home's scroll and the nested-scroll swipes (D29) all stand down. A system cancel arrives as a
synthetic "up" whose changes are already consumed; a real lift arrives unconsumed in Initial
— only a real lift over a pill launches. Hit-testing uses forgiving horizontal bands (pill
height ± half the gap, fan width ± 24dp, because thumbs drift); the held word is the cancel
zone. TalkBack's double-tap-and-hold can open the fan, but lifting without sliding cancels
and removes it (tested). Possible later refinement: disable the gesture while touch
exploration is on.

## D50 — Fan contents and layout (M2)
**Decision.** The fan shows the intent's visible apps in detail order (launch count, then
alphabetical), capped at 6, most-used nearest the finger, staggered 16dp away from the thumb
edge per step (mirrored for the right thumb). All pills are ≥ 52dp (the mockup uses 48dp +
52dp highlighted; a single height keeps hit bands stable). Pills stay inside
`safeDrawing ∪ systemGestures` + 8dp, and only those that fit above the word are placed
(fewer at 200 % font). The halo is a 6dp accent ring at 32 % alpha in both themes (the light
mockup's near-black glow contradicts §6.5's "accent halo"). Fan *state* lives in
`HomeViewModel.fan`; geometry and hit-testing live in the UI. Release goes through `LaunchApp`.

## D51 — Sub-surfaces are slots of `HomeRoute` (M2)
**Decision.** `HomeRoute(viewModel, searchContent, allAppsContent)` takes Search and All Apps
as composable slots defaulting to `SearchRoute` / `AllAppsRoute`, with a small
`SurfaceCallbacks` (close, open Appearance, open Search). Feature modules never see
`HomeViewModel`, and Home's Robolectric tests render stand-ins because the real routes need
Hilt (`hiltViewModel()`). Home's `BackHandler` closes both surfaces; only All Apps' open
long-press menu claims Back for itself.

## D52 — Lighter process from M2 on: orchestrator review instead of a reviewer agent **[deviation]** (M2)
**Context.** §1.5 asks for a separate reviewer sub-agent after every milestone. Each sub-agent
re-reads the spec, plan, decisions and code from scratch (≈ 200–280k tokens each), and M2's
agents were repeatedly stopped by usage limits. The owner asked to cut the token use.
**Decision.** M2 was finished and reviewed by the orchestrator directly: the docs-agent's
updates were checked against the code, and a focused review covered the riskiest parts —
the press-slide-release detector (consumption, cancel detection, cleanup), the contacts
query (parameterised, fails closed), the shortcut source's callback lifecycle, that every
launch goes through `LaunchApp`, and the merged release manifest's permissions
(`READ_CONTACTS`, `REQUEST_DELETE_PACKAGES`; no `INTERNET`). The process for M3–M5 will be
agreed with the owner before M3 starts.

## D53 — Access re-check on resume via `AccessRechecks` (M3)
**Decision.** Special accesses (notification listener, usage access) and runtime permissions
are granted in Settings, outside the app. `AccessRechecks` is a singleton `StateFlow` counter
bumped by `ActivityTracker.onActivityResumed`; access-gated sources map it to a fresh read. No
contract needs a `recheck()` and no screen can forget to call it (`HomeRoleSource.recheck()`
stays as before). Additive contract changes in M3: `CalendarSource.isPermissionGranted:
Flow<Boolean>` and `SettingsRepository.dismissTip(key)` + `AppearanceSettings.dismissedTips`.

## D54 — Unread counting (M3)
**Decision.** A notification counts if it is not ongoing (incl. foreground-service ones), not a
group summary and not Reach's own. Counts are keyed by `StatusBarNotification.key` (an update
never counts twice) and grouped per `PackageUser` via the notification's user serial. The hub
clears on listener disconnect, so badges are never stale; `requestRebind` when access is
granted but the listener isn't connected. Access is checked with
`NotificationManager.isNotificationListenerAccessGranted(component)` (exact component).

## D55 — Which media session shows (M3)
**Decision.** Only sessions with a title that are playing (incl. buffering/connecting/
seeking/skipping) or paused qualify; playing beats paused, then the system's priority order.
Progress is extrapolated from `positionUpdatedAtMillis × speed`, ticking once a second only
while playing and Home is STARTED; with reduced motion the bar steps instead of gliding. The
Listen row's inline player is hidden while the press-slide-release fan is open.

## D56 — Next calendar event (M3)
**Decision.** Declined and untitled events are skipped. Order: today's next timed event →
today's all-day event → tomorrow's first timed event → tomorrow's all-day event. All-day events
are dated in UTC (as the provider stores them). Re-read on calendar `ACTION_PROVIDER_CHANGED`
(registered *exported*: the provider's app sends it, not the system; a spoofed broadcast only
triggers one cheap query), on time/date/zone changes and every minute while collected.

## D57 — Usage-stats buckets (M3)
**Decision.** Launches come from `UsageStatsManager.queryEvents` `ACTIVITY_RESUMED`
(`MOVE_TO_FOREGROUND` on API 28); consecutive resumes of the same app count once; Reach's own
resumes are ignored; hours are local. Access is checked through `AppOpsManager.checkOpNoThrow(
OPSTR_GET_USAGE_STATS)` (falling back to the permission for `MODE_DEFAULT`).
`PACKAGE_USAGE_STATS` is declared with `tools:ignore="ProtectedPermissions"` — it is granted
only by the user in Settings.

## D58 — Onboarding and permission cards (M3)
**Decision.** One compact, dismissible card at a time on Home, in order: default home →
notification access (badges + media) → calendar → usage access. Each has a one-line reason, a
primary action and "Not now". "Not now", and a denied calendar permission, are stored
permanently (`dismissedTips`) — Reach never nags (§7). If a settings screen can't be opened,
the card dismisses itself. Cards disappear once the access is granted.

## D59 — M3 review fixes (M3)
Reviewed by a Sonnet reviewer agent; fixed by the orchestrator (process per D52).
- **Multi-day all-day events** now show on every day they cover (`Instances.END`, end date
  exclusive, as the provider stores it), not only on their first day. Tested, including
  UTC−8 and UTC+13 zones where the local date differs from the event's UTC date.
- **Media listener crash-proofing:** every entry point of `MediaSessionsSource` that runs on
  the main Handler (active-sessions listener, controller callbacks, initial read) is wrapped
  in `guarded {}` — a session dying or access being revoked mid-callback shows nothing
  instead of crashing Home.
- **Rebind rate limit:** `NotificationAccess.granted` is cold and collected by several
  screens; `requestRebind` now fires at most once per 10 s.
- **Usage stats:** `RuntimeException`s from `queryEvents` / `getNextEvent` on some OEM builds
  are caught; usage data is an optional blend.
- New `NotificationListenerHubTest` (connect → post/update → ongoing → remove → disconnect,
  reconnect replaces stale state, Reach's own notifications never count).

## D60 — Appearance sheet: own module, live writes, hand-built controls (M4)
**Decision.** `:feature:appearance` exposes `AppearanceRoute(onClose)`; Home hosts it in an
`appearanceContent` slot (like Search/All Apps) and draws it over Home inside the existing
`AnimatedVisibility` (fade only with "Remove animations"). `AppearanceViewModel` keeps no
draft: every control calls the `SettingsRepository` setter and the sheet's state comes back
from the same settings flow, so DataStore is the single source of truth and Home re-themes
behind the sheet live. No contract change was needed (the setters existed since M0). The
segmented controls, swatches and switch row are drawn with Reach tokens but carry Material's
semantics: `selectable(role = RadioButton)` in a `selectableGroup()`, and one
`toggleable(role = Switch)` row with a visual-only M3 `Switch`. Swatches are painted in each
palette's real colours for the *current* dark/light theme; min-height (not fixed) so names
wrap at 200 % font; the card scrolls.

## D61 — Wallpaper colour source and the scrim (M4)
**Decision.** `WallpaperManagerColorSource` reads `getWallpaperColors(FLAG_SYSTEM).primaryColor`
(no permission, never the bitmap) and listens with `addOnColorsChangedListener` inside a
`callbackFlow`; unsupported wallpaper, null colours, `SecurityException` and OEM
`RuntimeException`s all mean "unknown" (null → palette accent). `HomeViewModel.theme` collects
it only while wallpaper mode is on (`flatMapLatest`), so the listener isn't registered
otherwise. `ReachColors` gained `wallpaperMode` plus two computed tokens: `root` (bg, or the
82 %/86 % scrim) painted once by `HomeRoute`, and `backdrop` (bg, or transparent) painted by
full-screen surfaces above it (Search, All Apps), so the scrim is never stacked. With
wallpaper mode off both equal `bg`: behaviour unchanged. The date line shows "ACCENT FROM
WALLPAPER" with an accent dot in wallpaper mode (§6.3). `WallpaperAccent` (D6) was already
complete; its tests now also assert, for pure white/yellow, mid-grey and near-black in every
palette × theme, that `onAccent` is the stronger of bg/fg.

## D62 — Wallpaper window: runtime flag, not a second theme (M4)
**Decision.** `ReachWindow` (in `:app`) toggles `FLAG_SHOW_WALLPAPER` and sets a transparent
window background at run time, instead of switching to a theme with
`windowShowWallpaper=true`. **Why:** a theme only applies when the Activity is recreated, and
Reach never recreates Home (it would flash and lose the surface state; see `configChanges`).
The theme attribute is just that flag, which a window may change at any time, so the switch
applies live. On Android 12+ `Window.setBackgroundBlurRadius(40dp)` blurs the wallpaper while
`WindowManager.addCrossWindowBlurEnabledListener` reports blur enabled (battery saver or the
developer option turn it off); otherwise radius 0 and the scrim alone keeps text readable.
In opaque mode the window background is the palette bg (not the static Lime colour from
`themes.xml`).

## D63 — First-frame theme: bounded synchronous DataStore read (M4)
**Decision.** `MainActivity.onCreate` reads `settings.first()` with `runBlocking` +
`withTimeoutOrNull(250 ms)` before `setContent`, applies the window (wallpaper flag or palette
bg, system-bar icons) and passes the snapshot to `ReachApp` as `initialTheme`, used until
`HomeViewModel.theme` (now `StateFlow<HomeTheme?>`, null = not read yet) answers.
**Alternatives:** a SharedPreferences mirror written on every change. **Why this one:** it's
the same small file read Home needs a few milliseconds later anyway (it warms the DataStore
singleton), it can't drift from DataStore, and it happens once per process (the Activity is
never recreated). The timeout falls back to defaults rather than delay Home on a broken disk.
Known gap: the wallpaper *accent* still arrives a frame or two later (the colour is a binder
call); only the palette accent shows for that moment.

## D64 — One app menu for All Apps and intent detail (M4)
**Decision.** The long-press menu moved from `:feature:allapps` to `:core:designsystem`
(`AppActionsMenu`, `AppMenuActions`, `appMenuAccessibilityActions`, `appMenuOpenLabel`), with
its strings as `ds_app_menu_*` (EN + TR, removed from All Apps). Intent detail's app rows now
offer the same menu: App info, Move to intent…, Hide, Uninstall — on long-press, and each as a
direct TalkBack custom action (plus "App options" for the menu itself). The ViewModel side is
four one-line calls on `HomeViewModel` (`openAppInfo`, `moveToIntent`, `hideApp`,
`uninstall`) on the same repository/launcher APIs All Apps uses; the precomputed details
update by themselves when the catalog re-emits. Detail lists only visible apps, so it offers
"Hide" only; unhiding stays in All Apps' "Hidden" group. `:core:designsystem` gained an
`implementation` dependency on `activity-compose` (already in the catalog) for the menu's
`BackHandler`; `:feature:allapps` dropped it.

## D65 — M4 review fixes: wallpaper-safe text, no overlapping fades, modal sheet (M4)
The M4 review found that the scrim (82 % dark / 86 % light) still lets 14–18 % of the
wallpaper through, so contrast measured against the opaque `bg` overstated it.
- **Text tokens over the wallpaper.** In wallpaper mode `ReachColors.from` re-checks `fg`,
  `muted`, `accentInk`, `soft` and `groupDim` against the worst case
  (`wallpaperWorstBackground`: the scrim composited over pure white for dark themes, pure
  black for light ones) and nudges them with `ensureContrast`. Any real wallpaper lands
  between that and `bg`, so 4.5:1 holds over every photo, blurred or not. Text on accent
  fills and opaque surfaces is unaffected. Tested for every palette, theme and the hard
  accent colours.
- **No overlapping fades.** Surfaces paint a transparent `backdrop` in wallpaper mode, so a
  cross-fade would draw two screens' text on top of each other. In wallpaper mode the
  entering surface waits until the exiting one has faded out.
- **Modal sheet for TalkBack.** While the Appearance sheet is open, Home behind it is
  removed from the accessibility tree (`clearAndSetSemantics`).
- **Robustness.** The first-frame settings read falls back to defaults on any exception
  (a crash in a launcher's `onCreate` is a crash loop); the wallpaper source's fallback
  re-read runs on the IO dispatcher instead of the main thread.

## D66 — Optional accessibility service: lock and notification shade (M5)
**Decision.** `ReachAccessibilityService` (`:core:system/screen`) is used for exactly two
global actions: `GLOBAL_ACTION_LOCK_SCREEN` (double-tap on empty Home) and
`GLOBAL_ACTION_NOTIFICATIONS` (two-finger swipe down on Home). Its config subscribes to **no**
event types (default mask 0), limits events to Reach's package, sets
`canRetrieveWindowContent="false"`, no `can*` capabilities and `isAccessibilityTool="false"`.
Device-admin `lockNow()` is never used (it forces PIN/password instead of biometrics).
- **Hub, not injection into the service's callers.** The system creates/binds the service;
  it registers a `GlobalActionPerformer` in the singleton `ScreenActionsHub` on connect and
  clears it on unbind/destroy, so the source never holds the service.
- **Enabled = listed && connected.** `AccessibilityScreenActionsSource.isServiceEnabled`
  combines `AccessibilityManager.getEnabledAccessibilityServiceList` (re-read on every resume
  via `AccessRechecks`, D53) with the hub's live connection: each alone is briefly wrong right
  after the user toggles the service.
- **Opt-in lives in the Appearance sheet only** (new "Gestures" section: a "Double-tap to
  lock" switch, and while the service is off one line of why + "Turn on in Accessibility
  settings"). No Home card: it is a convenience few users need, and an unprompted request for
  an accessibility service reads as a red flag. Turning the switch on only stores the choice;
  Reach never opens Settings by itself.
- **Without the service nothing destructive happens.** Double-tap (only listened for while
  the switch is on, so ordinary taps are never delayed) and the two-finger swipe show a 3 s
  inline hint pointing to Appearance (`HomeEvent.ScreenActionUnavailable`).
- **Hidden-API shade fallback.** `StatusBarManager.expandNotificationsPanel()` via reflection,
  with the normal `EXPAND_STATUS_BAR` permission, only when `hiddenApiShadeFallback` is true
  (default off, no UI: a dormant feature flag). Any failure (method gone, non-SDK access
  blocked, SecurityException) returns false → the hint.
- **Gesture conflicts.** Double-tap sits on Home's outer box, after its children, so taps on
  words/tiles/pill/clock are consumed by them; two fingers are watched in the *Initial* pass
  and consumed only once a second finger is down, so every one-finger gesture (pull-down,
  swipe-up, press-slide-release, clock long-press) is untouched and two fingers can't also
  open Search. The pure rule `isTwoFingerSwipeDown` (both fingers ≥ 48dp down, within 45° of
  vertical) is unit-tested.
- **TalkBack.** With TalkBack on, double-tap means "activate", so the search pill carries
  "Lock screen" / "Open notifications" custom actions — only while they would work.

## D67 — Work profile and private space, verified (M5)
**Verified against the local SDK** (`platforms/android-35/36/37.0/android.jar` via `javap`,
`android-37.0/android-stubs-src.jar`, javadoc in `sources/android-36`):
- `LauncherApps.getProfiles()` / `getLauncherUserInfo(UserHandle)` (API 35;
  `LauncherUserInfo.getUserType()`, `getUserSerialNumber()`): the private profile
  (`UserManager.USER_TYPE_PROFILE_PRIVATE` = `"android.os.usertype.profile.PRIVATE"`) is only
  returned to a caller with *"normal `ACCESS_HIDDEN_PROFILES` permission and the
  `RoleManager.ROLE_HOME` role"* (javadoc wording); system apps may use
  `ACCESS_HIDDEN_PROFILES_FULL` instead. So a third-party launcher can get it, but only while
  it is the default home app. Reach now declares `ACCESS_HIDDEN_PROFILES` (`:core:system`).
- `UserManager.requestQuietModeEnabled(enable, user)`: allowed for "the foreground default
  launcher" (or `MANAGE_USERS`/`MODIFY_QUIET_MODE`); returns false when the user's credential
  must be confirmed first (the system shows that screen). Throws `SecurityException` /
  `IllegalArgumentException` → we return false.
- `Intent.ACTION_PROFILE_AVAILABLE/UNAVAILABLE` (API 35) are sent for *every* profile type
  (managed profiles additionally get the `MANAGED_PROFILE_*` ones), registered receivers
  only — which is what `Profiles.profileChangeActions()` already registers.
- `LauncherApps.getPrivateSpaceSettingsIntent()` exists from **API 36** only (not 35); not
  used yet (a "Private space settings" entry would need a new `SystemScreen`).

**Changes.** Work apps already carried the "WORK" tag (`AppRow`, All Apps, Search). Private
space: All Apps gets its own **"Private space" section** at the end of the wall (never mixed
into letter groups, as Android recommends), with a Lock/Unlock pill
(`ProfileSource.setQuietMode`); locked, it shows no names and no count. `PrivateSpaceUi.of`
lists apps only while the private profile is known *and* unlocked (fail closed);
`Profiles.isQuietMode` now treats a refused answer as "quiet". A null `LauncherUserInfo`
(javadoc: "user is inaccessible") is classified *not* private: the profile came from
`getProfiles()`, which only lists a private space to callers that may read its info, and
treating null as private would hide a paused work profile's apps and break tap-to-resume
(a Robolectric test caught exactly that). Unlocked private apps still appear in their intents,
Search and predictions (spec only requires hiding when locked).

**Needs a device (Android 15+, Reach as default home, a private space set up):** that the
private profile appears in `getProfiles()`; lock/unlock from All Apps (with the system
credential screen); that apps vanish within a frame of locking from the system's own UI;
that `ACCESS_HIDDEN_PROFILES` is granted at install on release images; paused work profile
dimming and tap-to-resume.

## D68 — Baseline Profile (M5)
**Decision.** New `:baselineprofile` module (`com.android.test`, `androidx.baselineprofile`
plugin **1.5.0**, `benchmark-macro-junit4` 1.5.0, `uiautomator` 2.4.1 — resolved from Google
Maven and building with AGP 9.4.1; AGP unchanged). `BaselineProfileGenerator` records cold
start → Home, an intent's detail, Search with typing, and All Apps with a scroll, finding
elements by their TalkBack descriptions (no test tags in production code). `:app` applies the
consumer plugin, depends on `baselineProfile(project(":baselineprofile"))` and keeps
`profileinstaller`. Generation is manual (needs a device, API 33+ or rooted 28+):
`./gradlew :app:generateBaselineProfile` → `app/src/release/generated/baselineProfiles/`
(commit the result). `useConnectedDevices = true`: no Gradle-managed emulator downloads.
Until a profile is generated the release ships only the libraries' own profiles (Compose etc.).

## D69 — Release build: whole-app R8, resource shrinking, signing from outside the repo (M5)
**Decision.** D16's `packageScope = androidx/kotlin/kotlinx` is removed: R8 now optimizes and
obfuscates Reach's own code too, and `isShrinkResources = true`. No extra keep rules were
needed: manifest components (activity, application, both services) are kept by AAPT's rules;
Hilt, Room, DataStore and Compose ship consumer rules and generate code rather than reflect
on ours; the shade fallback reflects on a framework class R8 never sees.
`src/main/keepRules/rules.keep` keeps line numbers for readable crash traces. Signing reads
`keystore.properties` (git-ignored; `keystore.properties.example` committed) or
`REACH_KEYSTORE_FILE/_PASSWORD`, `REACH_KEY_ALIAS`, `REACH_KEY_PASSWORD`; without either the
release is signed with the debug key so it installs for testing and profile generation —
Play rejects debug-signed uploads. `.gitignore` adds `keystore.properties`, `*.jks`, `*.keystore`.

## D70 — M5 cleanup: shared intent words (M5)
The six intent words moved to `:core:designsystem` (`ds_intent_*`, `intentWord()` /
`ReachIntent.wordRes()` in `util/IntentWords.kt`); `home_intent_*` and `allapps_intent_*`
are gone (Turkish wording copied unchanged). The shared clip-reveal helper for
`SystemAppLauncher` / `LauncherAppsShortcutSource` was **not** extracted: the two differ in
how they get the Activity, and the gain is a few lines — left for later.

## D71 — M5 review fixes: private apps off Home, two-finger swipe only when useful (M5)
- **Private space stays out of Home.** D67 showed unlocked private-space apps in Home's
  intents and the Now row. Android's guidance for launchers keeps them in their own
  container, and Home is the screen bystanders see. They now appear only in All Apps'
  "Private space" section and in Search (tagged), and never as predictions. Their launch
  history is kept (so nothing is lost on lock/unlock) but is never ranked.
- **Two-finger swipe.** The detector is installed only while it can do something (service on,
  or the hidden-API fallback flag) and never while a press-slide-release fan is open, so a
  stray second finger can't break the fan. Once a second finger touches, the detector keeps
  the gesture until every finger is up, so the finger left behind can't open Search.
- **TalkBack.** "Open notifications" is offered only when the service is on; the hidden-API
  fallback can fail silently, and actions are offered only when they will work.
- **Kept on purpose.** `EXPAND_STATUS_BAR` stays for the spec's best-effort fallback (§7.4),
  which is off by default; drop it if Play review asks. The release build still falls back
  to unsigned/debug signing without `keystore.properties` (local builds must work); the
  Play upload is a manual, signed step.

## D72 — Fixes from testing on a real device (Android 13) and a Pixel 10 Pro emulator
Found by driving the release-candidate build on devices; none of these showed up in
Robolectric.
- **The fan never appeared (regression from D71).** D71 removed the two-finger detector from
  Home's modifier chain while a fan was open. Changing an ancestor's pointer-input chain under
  a held finger cancels that finger's gesture, so the fan closed the instant it opened. The
  detector now stays installed and reads a `suspended` state (fan open) instead.
  `PressSlideReleaseTest.theFanStaysOpenWhileTheTwoFingerSwipeIsAvailable` covers it.
- **Wallpaper mode only worked if Reach started with it on.** A window whose surface is created
  opaque stays opaque: turning `FLAG_SHOW_WALLPAPER` on later made Reach the wallpaper target
  but nothing showed through. `ReachWindow.start()` now always requests a translucent format
  (`PixelFormat.TRANSLUCENT`); in opaque mode the palette-coloured window background makes
  it look the same.
- **Home didn't fit a 360×800dp phone.** The search pill was below the fold. Below 780dp of
  viewport Home uses a compact layout (84sp clock, smaller gaps).
- **Mid-word line breaks.** Turkish "SON KULLANILAN" broke inside the word in a Now tile; at
  200% font "Instagram", "Otomatik", "Mürekkep" and the search pill's "Yazıp aç" did too.
  Fixes: the TR label became "son açılan"; Now tiles stack as full-width rows above 1.3×
  font scale; the pill's "OR PULL DOWN" and the top search result's "ENTER" hint give way
  above 1.3×; segmented-control labels are one auto-sized line; palette swatches may widen
  with the font scale (84dp at 1×, as specified).
- **Display-type tracking is in em, not sp.** The clock auto-sizes down at 200%, but a fixed
  −10sp tracking (−5sp × 2) made its digits overlap. Converting each role's tracking to em
  keeps 1× identical and scales with the glyphs.
- **Smaller fixes.** Switch rows in the Appearance sheet no longer clip their first letters
  (the pill-shaped clip cut the corners of a two-line row). The TR fan hint is shorter so it
  fits on one line. While the fan is open, the date, clock and Now row fade to 25%: a tall fan
  overlaps them. Intent chips in intent detail are pinned below the scrolling app list, so
  they stay in thumb reach for intents with many apps.

## D73 — Well-known apps override their declared category
On real devices the spec's category table (§6.1) put Chrome, Clock, Gmail, Google and the
Play Store into Make (they declare `PRODUCTIVITY`) and Samsung's Gallery into Tools (no
category). `IntentCategorizer` now checks a short table of well-known packages (Google, AOSP
and Samsung system apps, common messengers, browsers and media apps) after the dialer/SMS
roles and before the category: browsers, clock, calculator, store, files and settings →
Tools; mail and messengers → Talk; camera, gallery and notes → Make; and so on. Only the
automatic intent changes (it is recomputed on every catalog sync); a user's "Move to…" still
wins. Deviation from spec §6.1, which lists the category table only.
- **Follow-up on the phone.** Now-tile app names are one auto-sized line (at 1× on a 360dp
  phone "Instagram" and "VakıfBank" still broke mid-word); the theme's three options stack
  vertically above 1.3× font scale (the smallest auto-size step still clipped "Otomatik");
  the selected intent chip is scrolled into view (the six chips are wider than a 360dp
  screen, so "Tools" opened out of sight); Samsung Calendar joins Google Calendar in Make.

## D74 — Home with a calendar event and a media player (real device)
Tested with a long calendar title and a paused YouTube Music session on a 360×800dp phone.
- **The search pill is pinned** below Home's scrolling content (like the intent chips, D72):
  a context line plus the media row pushed it below the fold. A zero-range `verticalScroll`
  around the pill passes drags on it to the same swipe connection, so swiping up from the
  pill still opens All Apps.
- **"Tight" mode.** When even the compact layout overflows, the clock (72sp) and the intent
  words (40sp) shrink. It is decided after measuring (`scroll.maxValue > 0`), re-evaluated when
  what Home shows changes, and never applied at large font scales, where Home scrolls.
- **The context line** is at most two lines (a long title took five); TalkBack reads it all.
- **Media player notifications never count as unread.** A paused player is no longer
  ongoing, so it showed "1 NEW" on Listen and "1 unread" in the Now row.
  `isPlayerNotification` (media session extra or `CATEGORY_TRANSPORT`) now counts as ongoing.
- **The Now Playing card** says "Paused · <app>" while paused instead of "Now playing".
