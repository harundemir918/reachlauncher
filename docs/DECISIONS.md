# Decisions

Short ADR-style entries for every non-obvious choice. Newest last. Each entry: context,
decision, consequences. Deviations from the spec are marked **[deviation]**.

---

## D1 — Keep the Android Studio template's toolchain (M0)
**Context.** The repo started from an Android Studio "Empty Compose Activity" template:
AGP 9.4.1, Gradle 9.6.0, compileSdk/targetSdk 37, minSdk 28, package
`com.baklavapp.reachlauncher`.
**Decision.** Keep package, Gradle wrapper and SDK levels; bump libraries to the latest
stable releases found on Google Maven / Maven Central on 2026-11-14 (AGP 9.5.1, Kotlin
2.4.21, KSP 2.3.14, Hilt 2.60.1, Compose BOM 2026.09.00, Room 2.8.5, DataStore 1.3.0…).
Google Play currently requires new apps to target API 36; 37 satisfies "or newer".
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
`WeakReference` to the resumed Activity (registered from `MainActivity`), uses its decor
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
