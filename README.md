# Reach

**A thumb-first Android home screen.** Reach replaces the icon grid with six big *intent*
words in the thumb zone — **Talk, Listen, Go, Make, Play, Tools**. Tap a word to see its
apps, or **press, slide and release** to launch one in a single gesture. A **"Now" row**
suggests three apps for this moment, and **type to launch** finds apps, shortcuts,
contacts and commands in a couple of keystrokes. The look is editorial rather than stock
Android: large type, letter monograms instead of app icons, one accent colour.

Built with Kotlin and Jetpack Compose. The owner comes from Flutter, so the docs explain
the Android side with Flutter analogies — start with
[`docs/FLUTTER_TO_COMPOSE.md`](docs/FLUTTER_TO_COMPOSE.md).

> This README is a **skeleton** (milestone M0). It is completed in M5.

---

## Status: M0 — Foundation (in progress)

| | Milestone | Scope |
|---|---|---|
| ◐ | **M0 — Foundation** | Gradle + version catalog, modules, Hilt, `ReachTheme` with all palettes, derived tokens and contrast tests, bundled fonts, a placeholder `MainActivity` registered as HOME, PLAN/DECISIONS, this README, `FLUTTER_TO_COMPOSE.md` v1 |
| ☐ | M1 — Core launcher | App catalog (LauncherApps, live updates, cache), intent auto-assignment, Home, intent detail, launching with animation, launch history, the Now row, Home/Back behaviour |
| ☐ | M2 — Speed | Search with ranking and commands, All Apps with the letter rail, press-slide-release, haptics |
| ☐ | M3 — System features | Notification counts, media controls, app shortcuts, default-home onboarding, permissions UX |
| ☐ | M4 — Personalisation | Appearance sheet, DataStore persistence, Auto theme, thumb side, wallpaper mode, move/hide apps |
| ☐ | M5 — Polish | Accessibility pass, optional accessibility service, work profile + private space, Turkish strings, Baseline Profile, release config |

What actually exists in M0: the nine Gradle modules, the domain types in `:core:model`,
the repository and system-source **interfaces** in `:core:data` with in-memory **fakes**,
the design system (palettes, derived tokens, colour maths, typography, `ReachTheme`) with
unit tests, and a `MainActivity` that shows a placeholder screen. `:core:system` and the
`:feature:*` modules are empty shells. **Reach cannot launch apps yet.**

---

## Privacy

Reach has **no analytics, no network access and sends no data off the device.**

- The manifest declares **no `INTERNET` permission**, so the app cannot open a network
  connection at all.
- **Cloud backup is disabled** (`android:allowBackup="false"`), so launch history and
  settings are not copied to Google Drive backups either. See
  [DECISIONS D9](docs/DECISIONS.md#d9--no-cloud-backup-m0).
- A Play-listing-ready `PRIVACY.md` arrives in M3, together with the optional
  permissions (notification access, usage access, contacts, calendar).

(The *build* downloads Gradle, libraries and possibly a JDK — that's your machine, not the app.)

---

## Requirements

| Tool | Version |
|---|---|
| Android Studio | current stable |
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
| `./gradlew test` | All JVM unit tests in every module | `flutter test` |
| `./gradlew :core:designsystem:test` | Unit tests of one module (also `:core:model:test`, `:core:data:test`, …) | `flutter test test/some_dir` |
| `./gradlew lint` | Android Lint (warnings are errors) | `flutter analyze` |
| `./gradlew ktlintCheck` | Kotlin style check (ktlint, `ktlint_official` style) | `dart format --set-exit-if-changed .` |
| `./gradlew ktlintFormat` | Auto-fixes style | `dart format .` |
| `./gradlew build test lint` | **Milestone gate** — must be green before every milestone commit | `flutter analyze && flutter test && flutter build` |

`build` already includes `check`, which runs unit tests, Lint and ktlint for every
module; the explicit `test lint` in the gate just makes the intent obvious.

After `installDebug`, start Reach without Android Studio:

```sh
adb shell am start -n com.baklavapp.reachlauncher/.MainActivity
```

---

## Set Reach as the default home app

`MainActivity` declares the `MAIN` + `HOME` + `DEFAULT` intent filter, which is what makes
an app eligible as a home screen. It *also* declares a normal `LAUNCHER` filter, so you can
open Reach from your current launcher (and run it from Android Studio) without making it
the default ([DECISIONS D8](docs/DECISIONS.md#d8--launcher-manifest-flags-m0)).

> **In M0, Reach is only a placeholder screen — it can't open other apps.** Setting it as
> home is useful to check the HOME wiring, but switch back afterwards (see below).

### On an emulator or device, via Settings

1. Install it (`./gradlew installDebug` or Run in Android Studio).
2. Open **Settings → Apps → Default apps → Home app** and choose **Reach**.
   (Samsung: *Settings → Apps → Choose default apps → Home app*. Names vary slightly by
   manufacturer.) Shortcut: `adb shell am start -a android.settings.HOME_SETTINGS`.
3. Press Home.

On some devices, pressing Home right after installing a second home app shows a
"Select a home app" chooser — pick Reach and *Always*.

From M3 on, Reach itself will offer this on first run via
`RoleManager.createRequestRoleIntent(ROLE_HOME)`.

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

### Getting back to the stock launcher

- **Settings → Apps → Default apps → Home app** → pick the original launcher. In M0 Reach
  can't open Settings for you: pull down the notification shade and tap the ⚙ gear.
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
:app                  MainActivity (HOME), ReachApplication (@HiltAndroidApp), manifest
:core:model           Pure Kotlin domain types (ReachIntent, AppKey, CatalogApp, PaletteId, …) + TextFolding
:core:designsystem    ReachTheme, palettes, derived tokens, colour maths, typography, bundled fonts
:core:data            Repository + system-source interfaces and their fakes; Room/DataStore impls from M1
:core:system          Android system integrations (LauncherApps, notifications, media, …) — empty until M1
:feature:home         Home surface + the HomeViewModel state machine — empty until M1
:feature:search       Type to launch — empty until M2
:feature:allapps      A–Z word wall + letter rail — empty until M2
:feature:appearance   Appearance sheet — empty until M4
```

Features talk to Android only through the interfaces in `:core:data`; `:core:system`
implements them, and `:app` wires both sides together with Hilt.

| Doc | What's in it |
|---|---|
| [`docs/PLAN.md`](docs/PLAN.md) | Module layout, dependency rules, contracts, surface state machine, milestones |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | ADR-style log of non-obvious choices and spec deviations |
| [`docs/FLUTTER_TO_COMPOSE.md`](docs/FLUTTER_TO_COMPOSE.md) | Flutter → Compose/Android mapping with code examples |
| [`docs/reach-launcher-prompt.md`](docs/reach-launcher-prompt.md) | The full product and design spec (source of truth) |
| `docs/ARCHITECTURE.md` | *M1* — module diagram, data flow from system event to pixel |
| `docs/ANDROID_APIS.md` | *M1–M5* — every Android API and permission, and why |
| `docs/GESTURES.md` | *M2* — press-slide-release and the letter rail, step by step |
| `PRIVACY.md` | *M3* — privacy policy for the Play listing |

---

## Troubleshooting

**Gradle sync fails with a JDK / "Unsupported class file major version" / toolchain error.**
The Gradle daemon must run on Java 25 (`gradle/gradle-daemon-jvm.properties`). In Android
Studio open *Settings → Build, Execution, Deployment → Build Tools → Gradle* and make sure
the Gradle JDK follows the project's daemon-JVM criteria or points at a JDK 25 (the
bundled JBR if it is 25+). On the command line, Gradle provisions a JDK 25 automatically
the first time (needs network). Also check `JAVA_HOME` isn't forcing an older JDK.

**"Default Activity not found" when pressing Run.** Android Studio looks for an activity
with `MAIN` + `LAUNCHER`. Reach declares one in addition to its HOME filter, so this
shouldn't happen. If you see it, the manifest merge failed (check *Build* output) or the
run configuration was edited — set *Run → Edit Configurations → app → Launch* back to
"Default Activity".

**Stuck with Reach as home while developing** (e.g. it crashes on start, or the M0
placeholder can't open anything). Use the adb role command or `adb uninstall` above. Quick
Settings (pull down the shade) still gives you the Settings gear. A crash-looping home
app usually makes Android offer the other launcher after a few attempts.

**Odd build errors after editing `build.gradle.kts` or the version catalog.**
The configuration cache is on (`gradle.properties`). Try once without it:
`./gradlew build --no-configuration-cache`. If that fixes it, a build script is doing
something the cache can't store — report it rather than disabling the cache for good.

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
