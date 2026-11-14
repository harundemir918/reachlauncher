# Android APIs and permissions used by Reach

**v1 (M1).** Every Android API and permission the code uses *today*: what it does, why
Reach needs it, how the user grants it (if at all), what happens without it, and Play
policy notes. M3–M5 extend this file as notification access, media, shortcuts, usage
stats, calendar, contacts, wallpaper colours and the accessibility service arrive (see
[the last section](#coming-in-m3m5)).

Where it helps, an API is tagged with the Android version (API level) that introduced it.
Reach's `minSdk` is **28** (Android 9); anything newer is behind a `Build.VERSION.SDK_INT`
check in the code. In paths, `…` = the `src/main/kotlin/com/baklavapp/reachlauncher/<module>`
part, e.g. `core/system/…/launch/` = `core/system/src/main/kotlin/com/baklavapp/reachlauncher/core/system/launch/`.

For a Flutter developer: everything below is called **directly from Kotlin** — no
MethodChannel, no plugin. In Reach, these calls live only in `:core:system` (and a few UI
APIs in `:feature:home` / `:app`), behind the interfaces in `:core:data` (see
[ARCHITECTURE.md](ARCHITECTURE.md)).

---

## Permissions

| Permission | Type | Declared in | Why | Without it |
|---|---|---|---|---|
| `REQUEST_DELETE_PACKAGES` | **normal** (granted at install, no dialog) | `core/system/src/main/AndroidManifest.xml` | Lets Reach open the system's "Uninstall this app?" confirmation (`ACTION_DELETE`) for apps targeting API 28+ (D26). It does **not** let Reach uninstall anything silently: the system always asks. | The uninstall request would be refused. (In M1, `AppLauncher.requestUninstall` exists but no UI calls it yet; the All Apps long-press menu arrives in M2.) |

That is the **only** permission Reach declares in M1. The merged manifest additionally
contains `com.baklavapp.reachlauncher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, a
*signature* permission that AndroidX Core defines for Reach itself so
`ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)` can emulate non-exported runtime
receivers on Android versions before 13. It grants nothing to other apps.

### Deliberately **not** requested

| Not requested | Why not |
|---|---|
| `INTERNET` | Reach has no network features in v1 and sends no data off the device (spec §7). Without this permission the app cannot open a socket at all — that's a stronger guarantee than "we don't call the network". None of the libraries adds it (checked in the merged debug manifest). |
| `QUERY_ALL_PACKAGES` | Restricted by Google Play (needs a Console declaration and a qualifying use case). A launcher only needs apps that *have a launcher activity*, which the `<queries>` entry for `MAIN/LAUNCHER` makes visible (D8, [package visibility](#package-visibility-queries)). |
| `ACCESS_HIDDEN_PROFILES` | Android 15+: needed (together with the home role) to see the **private space**. Until it's added and verified in **M5** (D13, D24), Android hides the private space from Reach entirely — the privacy-safe default. |
| `MODIFY_QUIET_MODE` | Not needed: `UserManager.requestQuietModeEnabled` is allowed for the foreground default launcher. |
| `VIBRATE` | Not needed: haptics go through `View.performHapticFeedback` (Compose `LocalHapticFeedback`), which needs no permission. |
| Runtime permissions (`READ_CONTACTS`, `READ_CALENDAR`), special accesses (notification listener, usage access, accessibility service) | Not yet: their sources are stubs in M1 (see [Coming in M3–M5](#coming-in-m3m5)). |

Library components in the merged manifest (none needs a permission from the user):
`androidx.startup.InitializationProvider` (not exported), Room's
`MultiInstanceInvalidationService` (not exported), `androidx.profileinstaller.ProfileInstallReceiver`
(exported but protected by `android.permission.DUMP`, i.e. only adb/the shell can send to
it; it installs Baseline Profiles). Debug builds also contain Compose's `PreviewActivity`
and a test `ComponentActivity` (from `ui-tooling` / `ui-test-manifest`, `debugImplementation`
only).

---

## App enumeration and profiles

### `LauncherApps` — the launcher's view of installed apps
Used in `core/system/…/apps/LauncherAppsInstalledAppsSource.kt` and `…/profile/Profiles.kt`.

| API | What it does | Why Reach needs it |
|---|---|---|
| `LauncherApps.getProfiles()` (API 26) | The user profiles a launcher may show: personal, work, and (Android 15+, with `ACCESS_HIDDEN_PROFILES`) the private space. | The catalog lists apps of *all* profiles (spec §6.1). On `SecurityException` Reach falls back to its own user (`Process.myUserHandle()`). |
| `LauncherApps.getActivityList(null, user)` | All launchable (`MAIN/LAUNCHER`) activities of one profile, with label and `ApplicationInfo`. | The app catalog itself. Reach's own entry is filtered out. Unlike `PackageManager.queryIntentActivities`, it works across profiles. |
| `LauncherApps.registerCallback(callback, Handler)` / `unregisterCallback` | Notifies about packages added, removed, changed, (un)available (SD card) and (un)suspended (parental controls, Digital Wellbeing), per profile. | Live catalog updates (spec §4). Registered while the `installedApps` flow is collected (`callbackFlow` + `awaitClose`); each callback only triggers a full re-read. Delivered on the main looper. |
| `LauncherApps.getLauncherUserInfo(user).userType == UserManager.USER_TYPE_PROFILE_PRIVATE` (API 35) | Tells a launcher whether a profile is the private space. | Private-space apps must disappear while it is locked (D13). If the lookup throws `SecurityException` (it does for hidden profiles without `ACCESS_HIDDEN_PROFILES`), Reach treats the profile as **PRIVATE** (`classifyProfile`, D24). |
| `LauncherApps.startMainActivity(component, user, sourceBounds, options)` | Starts an app's launcher activity in a given profile. | Every app launch ([Launching](#launching)). |
| `LauncherApps.startAppDetailsActivity(component, user, null, null)` | Opens the system "App info" screen for an app in a given profile. | The future long-press menu (M2/M4). Implemented in `SystemAppLauncher.openAppInfo`; no UI calls it in M1. |

**How the user grants it:** nothing to grant — `LauncherApps` is available to every app;
results are filtered by [package visibility](#package-visibility-queries).
**Without it:** n/a. If a call fails (`SecurityException` for a profile that became
inaccessible), that profile contributes no apps and the catalog keeps serving the Room cache.
**Play policy:** none specific; this is the API Play expects launchers to use instead of
`QUERY_ALL_PACKAGES`.

### `ApplicationInfo.category` (API 26)
Mapped in `core/system/…/apps/AppCategories.kt` (`appCategoryOf`) to the Android-free
`AppCategory`, then to an intent by `IntentCategorizer` (`core/data/…/domain/`): SOCIAL →
Talk; AUDIO → Listen; MAPS → Go; IMAGE, PRODUCTIVITY → Make; GAME, VIDEO, NEWS → Play;
anything else (including ACCESSIBILITY, API 31, spelled out as the constant `8`) → Tools.
No permission. Apps that declare no category land in Tools until the user moves them (M4).

### Default dialer and SMS apps (D23)
`core/system/…/roles/RoleSources.kt` (`TelephonyDefaultAppsSource`).

| API | Why |
|---|---|
| `TelecomManager.getDefaultDialerPackage()` | The default Phone app goes into Talk whatever its category (spec §6.1). |
| `Telephony.Sms.getDefaultSmsPackage(context)` | Same for the default Messages app. |

**Deviation:** the spec says "via `RoleManager`", but `RoleManager.getRoleHolders` is a
`@SystemApi` (needs `MANAGE_ROLE_HOLDERS`); third-party apps can only ask about roles they
hold themselves. These two public calls need no permission and report the same role holders.
**Without them** (tablets without telephony may lack the services or throw): no override,
the apps are placed by category. Re-read on every catalog sync.

### `UserManager` — profile identity and quiet mode
`core/system/…/profile/Profiles.kt`.

| API | What / why |
|---|---|
| `UserManager.getSerialNumberForUser(user)` / `getUserForSerialNumber(serial)` | A `UserHandle` is an in-memory token; the **serial number** is stable and safe to store. Room stores serials (`AppKey.userSerial`) and Reach converts back before calling the system. `-1` / `null` = the profile was removed. |
| `UserManager.isQuietModeEnabled(user)` | Quiet mode = a **paused work profile** (apps shown dimmed, `CatalogApp.isProfilePaused`) or a **locked private space** (apps never shown). |
| `UserManager.requestQuietModeEnabled(enabled, user)` (API 28) | Tapping an app of a paused work profile asks the system to resume the profile (D27, D32); the system may show its own confirmation or credential screen, and the user taps the app again. Allowed for the **foreground default launcher** (or holders of `MODIFY_QUIET_MODE`); a `SecurityException` returns `false`. A locked private space is never unlocked by a launch tap. |

### Profile broadcasts
`Profiles.profileChangeActions()`, collected by `UserManagerProfileSource` and
`LauncherAppsInstalledAppsSource` via `Context.broadcasts(…)` (`core/system/…/util/BroadcastFlow.kt`).
Registered **at runtime** with `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)`
— these are protected system broadcasts, which the system still delivers to non-exported
receivers, and not exporting means no other app can fake one. Each broadcast just triggers a
full re-read of profile state (never trusting the extras).

| Actions | Available from |
|---|---|
| `ACTION_MANAGED_PROFILE_ADDED`, `_REMOVED`, `_AVAILABLE`, `_UNAVAILABLE`, `_UNLOCKED` | every supported version (≤ API 28) |
| `ACTION_PROFILE_ACCESSIBLE`, `ACTION_PROFILE_INACCESSIBLE` | API 31 |
| `ACTION_PROFILE_ADDED`, `ACTION_PROFILE_REMOVED` | API 34 |
| `ACTION_PROFILE_AVAILABLE`, `ACTION_PROFILE_UNAVAILABLE` (also cover the private space) | API 35 (D27: not 31) |

Covered by `ProfileMappingTest` ("profile broadcasts on Android 15 include private-space
events", "… on Android 9 are the managed-profile ones only").

---

## Launching

`core/system/…/launch/SystemAppLauncher.kt`, `core/system/…/ActivityTracker.kt`.

| API | What it does | Why Reach needs it |
|---|---|---|
| `ActivityOptions.makeClipRevealAnimation(view, x, y, w, h)` (API 23) | The opening app's window grows out of a rectangle given relative to `view`. | The launch animation (spec §4). Reach passes the decor view and the tapped element's `boundsInWindow()` (`LaunchOrigin`) unchanged — the decor view *is* the window's root. |
| `View.getLocationOnScreen(int[])` | The view's position on the physical screen. | `startMainActivity`'s `sourceBounds` must be in **screen** coordinates; the decor view's offset is non-zero in split-screen / freeform windows. |
| `Application.ActivityLifecycleCallbacks` | Callbacks for every Activity's lifecycle. | `ActivityTracker` keeps a `WeakReference` to the **resumed** Activity (cleared on pause/destroy), registered in `ReachApplication.onCreate()` (D11). The singleton launcher uses it for the animation and the role dialog; ViewModels never hold an Android object. |
| `Context.startActivity` + `Intent.FLAG_ACTIVITY_NEW_TASK` | Starting an activity from a non-Activity context requires a new task. | Fallback when no Activity is resumed: launch without animation. |

**Errors:** `ActivityNotFoundException` (app gone) and `SecurityException` (disabled or
unexported) return `false`; `HomeViewModel` then shows "Couldn't open X" and records no launch.
**Permissions:** none. **Play policy:** none.

Not used, on purpose: the "Opening X…" overlay from the mockups (spec §4) and any
`ACTION_MAIN` intent built by hand — `LauncherApps` handles profiles for us.

---

## Default home app and the HOME role

| API | Where | What / why |
|---|---|---|
| Manifest: `MAIN` + `HOME` + `DEFAULT` intent filter on `MainActivity` | `app/src/main/AndroidManifest.xml` | Makes Reach *eligible* as a home app. A second `MAIN/LAUNCHER` filter lets you open Reach from another launcher or Android Studio before it's the default (D8). |
| `launchMode="singleTask"` + `Activity.onNewIntent` | `MainActivity` | Only one Home ever exists. Pressing Home while Reach exists delivers the HOME intent to `onNewIntent` → `HomeViewModel.onHomePressed()` (spec §4). |
| `excludeFromRecents`, `stateNotNeeded`, `clearTaskOnLaunch`, `resumeWhilePausing`, `configChanges=…` | `MainActivity` | Launcher flags as AOSP Launcher3 uses them (D8): no Recents card, restart without saved state, Home's task shows Home, appear while the closing app pauses, never recreate Home on rotation/dark mode/font scale/locale. |
| `RoleManager.isRoleHeld(ROLE_HOME)` (API 29) | `RoleHomeRoleSource` | "Is Reach the default home?" — about our own role, no permission. Re-read on every resume (`HomeRoleSource.recheck()`), because there's no callback when the user changes it in Settings. |
| `PackageManager.resolveActivity(HOME intent, MATCH_DEFAULT_ONLY)` | `RoleHomeRoleSource` | Same question on API 28, which has no roles: compare the default HOME activity's package with ours. No `<queries>` needed (API 28 has no package-visibility rules). |
| `RoleManager.isRoleAvailable(ROLE_HOME)` + `createRequestRoleIntent(ROLE_HOME)` + `Activity.startActivityForResult` (API 29) | `SystemAppLauncher.requestHomeRole` | The system "Set Reach as your default home app?" dialog. Needs a calling Activity (from `ActivityTracker`); the result is ignored because `recheck()` runs on resume. |
| `Settings.ACTION_HOME_SETTINGS` | `SystemIntents.settingsScreen` | Fallback when the dialog can't be shown (API 28, no resumed Activity, role unavailable). |

**How the user grants it:** by choosing Reach in the system dialog or in *Settings → Apps →
Default apps → Home app*. **Without it:** Reach still runs as a normal app (via its LAUNCHER
entry) and can launch apps; pressing Home opens the other launcher. Later features that need
the role: app shortcuts (`hasShortcutHostPermission`, M3), resuming a paused work profile,
the private space (M5).
**M1 status:** the role state is read on every resume, and `AppLauncher.open(SystemScreen.HOME_ROLE)`
is implemented and tested (`SystemAppLauncherTest`, `HomeRoleSourceTest`), but no UI shows
the onboarding card yet — that's **M3**. Until then, set the default via Settings or adb
(see the README).

---

## Settings deep links and hand-offs to other apps

Implemented in `core/system/…/launch/SystemIntents.kt`, exposed through
`AppLauncher.open(SystemScreen)` and `AppLauncher.perform(ExternalAction)`. **No UI calls
them in M1**; they're ready for onboarding/permissions UX (M3) and search (M2). Covered by
`SystemAppLauncherTest`. None needs a permission.

| Domain request | Android intent | Used for |
|---|---|---|
| `SystemScreen.NOTIFICATION_ACCESS` | `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` | Granting notification access (M3). The per-app detail screen (API 30) needs Reach's listener component, which arrives in M3. |
| `SystemScreen.USAGE_ACCESS` | `Settings.ACTION_USAGE_ACCESS_SETTINGS` | Granting usage access (optional prediction blend). |
| `SystemScreen.ACCESSIBILITY_SERVICE` | `Settings.ACTION_ACCESSIBILITY_SETTINGS` | Enabling the optional accessibility service. |
| `SystemScreen.HOME_ROLE` | role dialog, else `Settings.ACTION_HOME_SETTINGS` | See above. |
| `ExternalAction.WebSearch(query)` | `ACTION_WEB_SEARCH` + extra `SearchManager.QUERY` | Search's "Search the web for '…'" fallback (M2). |
| `ExternalAction.Dial(number)` | `ACTION_DIAL`, `tel:` URI | Contact result → call. **Pre-fills** the dialer; doesn't call, so no `CALL_PHONE`. |
| `ExternalAction.Message(number)` | `ACTION_SENDTO`, `smsto:` URI | Contact result → message. No `SEND_SMS`. |
| `ExternalAction.ViewContact(id, lookupKey)` | `ACTION_VIEW`, `ContactsContract.Contacts.getLookupUri(…)` | Open the contact card. |
| `AppLauncher.requestUninstall(key)` | `ACTION_DELETE`, `package:<pkg>` URI + `Intent.EXTRA_USER` | "Uninstall" in the long-press menu (M2). `EXTRA_USER` targets the work copy instead of the personal one. `ACTION_UNINSTALL_PACKAGE` is deprecated since API 29 (D26). Requires `REQUEST_DELETE_PACKAGES`. |

All are started from the resumed Activity when there is one (else with `FLAG_ACTIVITY_NEW_TASK`);
"nothing handles this" (`ActivityNotFoundException`) returns `false`.

---

## Package visibility (`<queries>`)

Since Android 11 (API 30), an app only sees other apps it declares an interest in.

| `<queries>` entry | Declared in | Why |
|---|---|---|
| `MAIN` + `LAUNCHER` | `app/src/main/AndroidManifest.xml` | Makes every app with a launcher activity visible to `LauncherApps` — exactly what a launcher shows. Replaces `QUERY_ALL_PACKAGES`. |
| `WEB_SEARCH` | `core/system/src/main/AndroidManifest.xml` | Resolve a web-search handler before offering the row (M2). |
| `DIAL` with `tel:` | same | Resolve a dialer. |
| `SENDTO` with `smsto:` | same | Resolve a messaging app. |
| `VIEW` with MIME `vnd.android.cursor.item/contact` | same | Resolve a contacts app. |

Starting an implicit intent works without `<queries>`; *resolving* it (to hide a row that
would do nothing) needs them. Library manifests are merged into the app's at build time, so
`:core:system`'s entries live next to the code that uses them.

---

## Clock, locale and reduced motion (Home UI)

| API | Where | What / why |
|---|---|---|
| `Intent.ACTION_TIME_TICK` | `feature/home/…/ui/HomeClock.kt` (`rememberWallClock`) | System broadcast at every minute boundary, **only to receivers registered in code** and only while the screen is on — exactly when the clock must update. No timers, no drift (D30). |
| `Intent.ACTION_TIME_CHANGED` | same | Manual time change; also fired by the 12/24-hour toggle. |
| `Intent.ACTION_TIMEZONE_CHANGED` | same | Time-zone change. |
| `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)` | same, and `BroadcastFlow.kt` | Android 14+ (when targeting 34+) requires an explicit exported/not-exported flag for runtime receivers; `ContextCompat` handles older versions. Registered in a `DisposableEffect`, unregistered on dispose. |
| `android.text.format.DateFormat.is24HourFormat(context)` | same | Follows the user's 12/24-hour setting. The time is also re-read on every resume (`LifecycleResumeEffect`). |
| `ViewConfiguration.longPressTimeoutMillis` (Compose `LocalViewConfiguration`) | `HomeClock` | Long-press on the clock = `max(450 ms, system long-press timeout)`, so the accessibility "Touch & hold delay" is never shortened (D30). |
| `Settings.Global.ANIMATOR_DURATION_SCALE` + `ContentObserver` | `core/designsystem/…/util/Motion.kt` (`rememberReducedMotion`) | "Remove animations" (spec §6.10): surface transitions fall back to a plain cross-fade. Read-only, no permission. |
| `LocalHapticFeedback` → `View.performHapticFeedback(LONG_PRESS)` | `HomeClock` | Haptic tick on the clock long-press. No `VIBRATE` permission needed. |
| `java.time.ZoneId.systemDefault()` | `core/data/…/time/SystemZoneClock.kt` | Launch hour/weekday are stored in the zone current *at launch time* (D27). |
| `java.text.Collator.getInstance(locale)` | `CatalogAssembler` | Locale-aware alphabetical order (Turkish İ/ı, ç, ş…), spec §3. |

None of these needs a permission.

---

## Edge-to-edge, system bars and the keyboard

| API | Where | What / why |
|---|---|---|
| `enableEdgeToEdge()` | `MainActivity.onCreate` | Draw behind the status and navigation bars. Edge-to-edge is enforced for apps targeting API 35+; Reach targets 37. |
| `enableEdgeToEdge(SystemBarStyle.dark/light(Color.TRANSPARENT…))` | `MainActivity.applySystemBarStyle` | Re-styles bar icons for the *resolved* Reach theme (a forced Dark/Light theme would otherwise leave dark icons on a dark background). Called from `ReachApp` whenever the resolved theme changes. |
| `WindowInsets.safeDrawing` + `windowInsetsPadding` | `HomeScreen`, `IntentDetailScreen`, `HomeRoute` | Keeps content out of the status bar, navigation bar, display cutout and IME. |
| `windowSoftInputMode="adjustResize"` | manifest | The window resizes for the keyboard; Compose applies IME insets (Search, M2). |
| `LocalSoftwareKeyboardController.hide()` + `LocalFocusManager.clearFocus(force = true)` | `HomeRoute` | "Pressing Home closes the keyboard" (spec §4), on `HomeEvent.HideKeyboard`. |
| `BackHandler` (AndroidX `OnBackPressedDispatcher`) | `HomeRoute` | Intercepts system Back (button and gesture): sub-surface → Home, Home → nothing (D28). |
| `android:windowBackground` in the window theme | `app/src/main/res/values*/themes.xml` | Default palette bg before Compose's first frame — no white flash (D8). |

Not yet: keeping the press-slide-release area away from `WindowInsets.systemGestures` (M2),
wallpaper window theme / blur (M4).

---

## Data storage (for completeness)

Room (`reach.db`, `core/data/…/db/`) and Preferences DataStore (`settings`, built in
`DataModules.kt`) write only to the app's private storage. No permission, nothing shared
with other apps, excluded from cloud backup and device transfer (D9).

---

## Coming in M3–M5

Each of these is already behind an interface in `core/data/…/source/SystemSources.kt` and
bound to an `Unavailable…` stub in `core/system/…/di/SystemModule.kt`, which reports exactly
what the real source reports with the permission denied. Milestones as stated in the stub's
KDoc (`core/system/…/unavailable/UnavailableSources.kt`):

- **Notification counts** — `NotificationListenerService`, enabled by the user in system settings; feeds the "3 NEW" badges (M3).
- **Media sessions** — `MediaSessionManager.getActiveSessions(listenerComponent)`, reuses notification access; Listen row + Now Playing card (M3).
- **App shortcuts** — `LauncherApps.getShortcuts` / `startShortcut` with `hasShortcutHostPermission()` (default launcher only) (M2, with Search; intent-detail pills in M3).
- **Default-home onboarding card** — UI for the existing `HomeRoleSource` / `SystemScreen.HOME_ROLE` (M3).
- **Calendar** — next event from `CalendarContract`, runtime permission `READ_CALENDAR` (M3).
- **Contacts** — `ContactsContract` search, runtime permission `READ_CONTACTS` (M2, with Search).
- **Usage stats** — `UsageStatsManager`, special access `PACKAGE_USAGE_STATS` via usage-access settings; optional prediction blend (M3).
- **Wallpaper colours** — `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` for the wallpaper-mode accent (M4).
- **Accessibility service** — opt-in, `GLOBAL_ACTION_LOCK_SCREEN` / `GLOBAL_ACTION_NOTIFICATIONS`, with the Play accessibility-API disclosure text (M5).
- **Private space** — `ACCESS_HIDDEN_PROFILES` and the exact Android 15+ behaviour, verified in M5 (D13).
