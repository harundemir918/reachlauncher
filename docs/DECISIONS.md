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
