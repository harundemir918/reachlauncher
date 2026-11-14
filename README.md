# Reach

**A thumb-first Android home screen.** Reach replaces the icon grid with six big *intent*
words in the thumb zone — **Talk, Listen, Go, Make, Play, Tools**. Tap a word to see its
apps, or **press, slide and release** to launch one in a single gesture. A **"Now" row**
suggests three apps for this moment, and **type to launch** finds apps, shortcuts,
contacts and commands in a couple of keystrokes. The look is editorial rather than stock
Android: large type, letter monograms instead of app icons, one accent colour.

Built with Kotlin and Jetpack Compose. The owner comes from Flutter, so the docs explain
the Android side with Flutter analogies — start with
[`docs/FLUTTER_TO_COMPOSE.md`](docs/FLUTTER_TO_COMPOSE.md), then
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

> This README grows with each milestone and is completed in M5.

---

## Status: M1 — Core launcher (done)

| | Milestone | Scope |
|---|---|---|
| ● | **M0 — Foundation** | Gradle + version catalog, modules, Hilt, `ReachTheme` with all palettes, derived tokens and contrast tests, bundled fonts, `MainActivity` registered as HOME, PLAN/DECISIONS, README, `FLUTTER_TO_COMPOSE.md` v1 |
| ◐ | **M1 — Core launcher** | App catalog (LauncherApps, live updates, cache), intent auto-assignment, Home, intent detail, launching with animation, launch history, the Now row, Home/Back behaviour — *implemented, awaiting the independent review* |
| ☐ | M2 — Speed | Search with ranking and commands, All Apps with the letter rail, press-slide-release, haptics |
| ☐ | M3 — System features | Notification counts, media controls, app shortcuts, default-home onboarding, permissions UX |
| ☐ | M4 — Personalisation | Appearance sheet, DataStore persistence, Auto theme, thumb side, wallpaper mode, move/hide apps |
| ☐ | M5 — Polish | Accessibility pass, optional accessibility service, work profile + private space, Turkish strings, Baseline Profile, release config |

### What works now (M1)

- **Home**: the date line ("SAT 14 NOV") and the big clock, updated on every minute tick,
  time/time-zone change and resume; the **NOW** row with up to three predicted apps and a
  reason ("usually around now", "recent"); the six **intent words** with their app counts;
  the "Type to launch" pill. Layout reflows/scrolls at large font sizes.
- **App catalog**: every launchable app of the personal and work profiles via
  `LauncherApps`, cached in Room so Home renders instantly, updated live on install,
  removal and update. Apps are auto-assigned to an intent from their Play category; the
  default Phone and SMS apps go to Talk. Sorted alphabetically for the current locale.
- **Intent detail**: tap a word → back button, "INTENT · N APPS", the intent title, the app
  rows (most launched first, then A–Z, with a WORK tag for work apps) and chips to switch
  between the six intents in one tap.
- **Launching** with a clip-reveal animation from the tapped row/tile, through
  `LauncherApps.startMainActivity`. Apps of a paused work profile ask Android to resume the
  profile first. A failed launch shows "Couldn't open X".
- **Launch history** in Room (no permission needed) drives the Now row and the detail order.
- **Home / Back**: pressing Home returns to the Home state from anywhere and closes the
  keyboard; Back steps back one level and does nothing on Home.
- **Accessibility basics**: every control is a labelled button; intent rows read as one
  sentence ("Talk, 5 apps"); the clock long-press and the swipe-up have TalkBack custom
  actions ("Appearance", "All apps"); "Remove animations" reduces transitions to a fade.

### Placeholders (so you know what you're looking at)

- **Pull down** / tap the pill → a "Type to launch arrives in M2" placeholder (Search, **M2**).
- **Swipe up** → a placeholder for the A–Z list (All Apps, **M2**).
- **Long-press the clock** (~450 ms) → a placeholder sheet (Appearance, **M4**). The theme
  follows Android's dark mode; palette and thumb side are fixed to the defaults until M4.
- No "3 NEW" badges, media controls, app shortcuts, calendar line or default-home
  onboarding card yet (**M3**). No "Move to… / Hide" yet (**M4**). No press-slide-release
  gesture yet (**M2**).

---

## Privacy

Reach has **no analytics, no network access and sends no data off the device.**

- No manifest in the project declares the **`INTERNET` permission**, so the app cannot
  open a network connection at all.
- **Cloud backup and device-to-device transfer are disabled** (`android:allowBackup="false"`
  plus backup/data-extraction rules that exclude everything), so launch history and
  settings are not copied anywhere. See [DECISIONS D9](docs/DECISIONS.md#d9--no-cloud-backup-m0).
- The only permission in M1 is `REQUEST_DELETE_PACKAGES` (normal, install-time), for the
  system's own uninstall confirmation. Details and everything that is *not* requested:
  [`docs/ANDROID_APIS.md`](docs/ANDROID_APIS.md).
- A Play-listing-ready `PRIVACY.md` arrives in M3, together with the optional
  permissions (notification access, usage access, contacts, calendar).

(The *build* downloads Gradle, libraries and possibly a JDK — that's your machine, not the app.)

---

## Requirements

| Tool | Version |
|---|---|
| Android Studio | current stable (it must support AGP 9.4 — see [DECISIONS D1](docs/DECISIONS.md)) |
| JDK for Gradle | **Java 25** — the Gradle daemon is pinned to it by [`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties) (`toolchainVersion=25`). If no JDK 25 is installed, Gradle downloads one via the Foojay toolchain resolver on the first build. In Android Studio, use the bundled JBR / the daemon-JVM criteria rather than a random system JDK. |
| Gradle | 9.6.0 via the wrapper (`./gradlew`) — don't install Gradle yourself |
| Android SDK | **API 37** platform (`compileSdk`/`targetSdk` 37), plus platform-tools (`adb`) |
| Device / emulator | **Android 9 (API 28)** or newer (`minSdk 28`). An emulator on the latest API level is the main target. |

Toolchain versions (AGP, Kotlin, Compose BOM, Hilt, Room, …) live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml); why they were chosen is in
[DECISIONS D1](docs/DECISIONS.md).

---

## Build, run, test

All commands run from the repo root.

| Gradle command | What it does | Flutter equivalent |
|---|---|---|
| `./gradlew assembleDebug` | Builds `app/build/outputs/apk/debug/app-debug.apk` | `flutter build apk --debug` |
| `./gradlew installDebug` | Builds and installs on the connected device/emulator (does not start it) | `flutter install` (roughly) |
| Android Studio ▶ Run `app` | Build + install + launch, with debugger/Logcat | `flutter run` |
| `./gradlew test` | All JVM unit tests in every module (incl. Robolectric Room and Compose UI tests) | `flutter test` |
| `./gradlew :feature:home:test` | Unit tests of one module (also `:core:model:test`, `:core:data:test`, `:core:system:test`, `:core:designsystem:test`) | `flutter test test/some_dir` |
| `./gradlew lint` | Android Lint (warnings are errors) | `flutter analyze` |
| `./gradlew ktlintCheck` | Kotlin style check (ktlint, `ktlint_official` style) | `dart format --set-exit-if-changed .` |
| `./gradlew ktlintFormat` | Auto-fixes style | `dart format .` |
| `./gradlew build test lint` | **Milestone gate** — must be green before every milestone commit | `flutter analyze && flutter test && flutter build` |

`build` already includes `check`, which runs unit tests, Lint and ktlint for every
module; the explicit `test lint` in the gate just makes the intent obvious.

What the tests cover in M1: the Home state machine, unread sums, launch/failed-launch/paused
profile paths and Home UI flows (`feature/home/src/test`); the catalog cache, privacy filter,
Turkish collation, launch history, predictions and settings (`core/data/src/test`); intents,
the home-role fallback and profile mapping (`core/system/src/test`); palettes, contrast and
monograms (`core/designsystem/src/test`); storage keys and text folding (`core/model/src/test`).

After `installDebug`, start Reach without Android Studio:

```sh
adb shell am start -n com.baklavapp.reachlauncher/.MainActivity
```

---

## Set Reach as the default home app and try it

`MainActivity` declares the `MAIN` + `HOME` + `DEFAULT` intent filter, which is what makes
an app eligible as a home screen. It *also* declares a normal `LAUNCHER` filter, so you can
open Reach from your current launcher (and run it from Android Studio) without making it
the default ([DECISIONS D8](docs/DECISIONS.md#d8--launcher-manifest-flags-m0)).

### On an emulator or device, via Settings

1. Install it (`./gradlew installDebug` or Run in Android Studio).
2. Open **Settings → Apps → Default apps → Home app** and choose **Reach**.
   (Samsung: *Settings → Apps → Choose default apps → Home app*. Names vary slightly by
   manufacturer.) Shortcut: `adb shell am start -a android.settings.HOME_SETTINGS`.
3. Press Home.

On some devices, pressing Home right after installing a second home app shows a
"Select a home app" chooser — pick Reach and *Always*.

From M3 on, Reach itself will offer this on first run via
`RoleManager.createRequestRoleIntent(ROLE_HOME)` (the code exists already; the onboarding
card that calls it doesn't).

### Via adb (developer convenience)

On Android 10 (API 29) and newer, home is a *role* managed by `RoleManager`, and the shell
can assign it directly:

```sh
# Make Reach the home app
adb shell cmd role add-role-holder android.app.role.HOME com.baklavapp.reachlauncher

# Check who holds the role
adb shell cmd role get-role-holders android.app.role.HOME
```

HOME is an exclusive role, so adding Reach replaces the previous holder. This is a dev
shortcut only — real users go through Settings or Reach's own onboarding. On Android 9
(API 28) the `role` command doesn't exist; use
`adb shell cmd package set-home-activity com.baklavapp.reachlauncher/.MainActivity` or Settings.

### What you should see

1. Home: the date and the clock at the top, the six intent words at the bottom (left
   side), each followed by its number of apps, and the "Type to launch" pill below them.
   The **NOW** row is *missing* at first — see Troubleshooting.
2. Tap **Tools** (apps without a Play category, often including Settings, land there) → the app list. Tap an app:
   it opens with a reveal animation from the row. Press Home: you're back on Home, not in the
   list.
3. Use a few apps through Reach, then come back: the **NOW** row appears, and the intent
   lists put your most-launched apps first.
4. Install or uninstall an app (e.g. `adb install some.apk` / `adb uninstall <pkg>`): the
   intent's count changes within a moment, without restarting Reach.
5. Back on Home does nothing; Back in a sub-screen returns to Home.

### Getting back to the stock launcher

- **Settings → Apps → Default apps → Home app** → pick the original launcher. In M1, Reach
  has no settings shortcut of its own yet: open the **Settings** app from Reach's intent lists
  (usually under Tools), or pull down the notification shade and tap the ⚙ gear.
- Or via adb, give the role back (find the package with
  `adb shell pm query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`;
  on Google emulators/Pixels it is usually `com.google.android.apps.nexuslauncher`,
  on AOSP images `com.android.launcher3`):

  ```sh
  adb shell cmd role add-role-holder android.app.role.HOME com.google.android.apps.nexuslauncher
  ```

- Or simply uninstall Reach: `adb uninstall com.baklavapp.reachlauncher` — Android falls
  back to the remaining launcher.

---

## Project layout

```
:app                  MainActivity (HOME, onNewIntent → Home), ReachApp (root composable), ReachApplication (@HiltAndroidApp)
:core:model           Pure Kotlin domain types (ReachIntent, AppKey, CatalogApp, Prediction, PaletteId, …) + TextFolding
:core:designsystem    ReachTheme, palettes, derived tokens, colour maths, typography, fonts, components (IntentWordRow, NowTile, AppRow, …)
:core:data            Repository + system-source interfaces, Room/DataStore implementations, catalog/prediction logic, fakes
:core:system          Android integrations: LauncherApps catalog, profiles, app launcher, default dialer/SMS, home role; stubs for M2–M5
:feature:home         HomeViewModel (surface state machine), Home, intent detail, placeholders for Search/All Apps/Appearance
:feature:search       Type to launch — empty until M2
:feature:allapps      A–Z word wall + letter rail — empty until M2
:feature:appearance   Appearance sheet — empty until M4
```

Features talk to Android only through the interfaces in `:core:data`; `:core:system`
implements them, and `:app` wires both sides together with Hilt. The module diagram and
data flows are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

| Doc | What's in it |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Module diagram, layers, data flows from system event to pixel, the Home state machine, startup, privacy, where to add a feature |
| [`docs/ANDROID_APIS.md`](docs/ANDROID_APIS.md) | Every Android API and permission used, why, how it's granted, what happens without it, Play notes (v1; extended M3–M5) |
| [`docs/FLUTTER_TO_COMPOSE.md`](docs/FLUTTER_TO_COMPOSE.md) | Flutter → Compose/Android mapping with real code from the repo (v2) |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | ADR-style log of non-obvious choices and spec deviations (D1–D33) |
| [`docs/PLAN.md`](docs/PLAN.md) | Module layout, dependency rules, contracts, milestones, carried-over notes |
| [`docs/reach-launcher-prompt.md`](docs/reach-launcher-prompt.md) | The full product and design spec (source of truth) |
| `docs/GESTURES.md` | *M2* — press-slide-release and the letter rail, step by step |
| `PRIVACY.md` | *M3* — privacy policy for the Play listing |

---

## Troubleshooting

**The NOW row is empty / missing.** Expected on a fresh install. Predictions come from
Reach's *own* launch history (no permission needed), scored by time of day and weekday
([DECISIONS D25](docs/DECISIONS.md)). Until you've launched a few apps *through Reach*,
there's nothing to predict and the whole NOW block stays hidden. Launches from other
launchers, notifications or Recents don't count. (Optional usage-access blending arrives
later.)

**An app is in the "wrong" intent.** Auto-assignment uses the app's Play category
(`ApplicationInfo.category`); many apps declare none and land in **Tools**. "Move to…"
arrives in M4.

**A new app doesn't show up.** Package changes arrive through `LauncherApps` callbacks and
a refresh on every resume. If it's still missing, check it has a launcher activity
(`adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER <pkg>`).
Apps of an Android 15+ **private space** are intentionally invisible to Reach until M5.

**Tapping a work app does nothing the first time.** Its work profile is paused: Reach asks
Android to resume it (you may see a system prompt). Tap the app again once it's on
([DECISIONS D27](docs/DECISIONS.md)).

**No launch animation.** Expected when *Accessibility → Remove animations* is on, or the
window/transition animation scales are 0 in Developer options — the clip-reveal is a system
window animation.

**Gradle sync fails with a JDK / "Unsupported class file major version" / toolchain error.**
The Gradle daemon must run on Java 25 (`gradle/gradle-daemon-jvm.properties`). In Android
Studio open *Settings → Build, Execution, Deployment → Build Tools → Gradle* and make sure
the Gradle JDK follows the project's daemon-JVM criteria or points at a JDK 25 (the
bundled JBR if it is 25+). On the command line, Gradle provisions a JDK 25 automatically
the first time (needs network). Also check `JAVA_HOME` isn't forcing an older JDK.

**Android Studio refuses to sync ("incompatible version (AGP …)").** The project pins AGP
to what current stable Studio supports (9.4.1). Update Studio before bumping AGP
([DECISIONS D1](docs/DECISIONS.md)).

**"Default Activity not found" when pressing Run.** Android Studio looks for an activity
with `MAIN` + `LAUNCHER`. Reach declares one in addition to its HOME filter, so this
shouldn't happen. If you see it, the manifest merge failed (check *Build* output) or the
run configuration was edited — set *Run → Edit Configurations → app → Launch* back to
"Default Activity".

**Stuck with Reach as home while developing** (e.g. it crashes on start). Use the adb role
command or `adb uninstall` above. Quick Settings (pull down the shade) still gives you the
Settings gear. A crash-looping home app usually makes Android offer the other launcher
after a few attempts.

**Odd build errors after editing `build.gradle.kts` or the version catalog.**
The configuration cache is on (`gradle.properties`). Try once without it:
`./gradlew build --no-configuration-cache`. If that fixes it, a build script is doing
something the cache can't store — report it rather than disabling the cache for good.

**Room schema / KSP errors after changing an entity.** Room checks queries at compile time
and exports the schema to `core/data/schemas/`. Changing an `@Entity` needs a database
version bump and a migration (none exist yet; the database is at version 1).

**Fonts look wrong / where are the fonts and licences?** The three variable fonts live in
`core/designsystem/src/main/res/font/` (`bricolage_grotesque.ttf`, `inter.ttf`,
`jetbrains_mono.ttf`) and are wired up in `ReachTypography.kt`. Their licences are in
`core/designsystem/src/main/assets/licenses/fonts/`.

**ktlint fails on a file you didn't touch.** Run `./gradlew ktlintFormat`; the rules are
in `.editorconfig` (`ktlint_official`, max line length 140, `@Composable` functions may
be PascalCase).

---

## Licences

Reach bundles three fonts from Google Fonts, all under the **SIL Open Font License 1.1**:

- **Bricolage Grotesque** — display words (clock, intents, titles)
- **Inter** — app names and body text
- **JetBrains Mono** — labels, counts, monograms

The licence texts are in
[`core/designsystem/src/main/assets/licenses/fonts/`](core/designsystem/src/main/assets/licenses/fonts/)
and are packaged with the app.
