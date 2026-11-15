# Android APIs and permissions used by Reach

**v4 (M4).** Every Android API and permission the code uses *today*: what it does, why
Reach needs it, how the user grants it (if at all), what happens without it, and Play
policy notes. M2 added contacts, app shortcuts, ICU letter buckets, gesture-zone handling and
haptics; M3 added the notification listener, media sessions, calendar, usage access and the
resume re-check. M4 added wallpaper colours, the wallpaper window flag and background blur; M5 will add the
accessibility service (see [the last section](#coming-in-m5)).

Where it helps, an API is tagged with the Android version (API level) that introduced it.
Reach's `minSdk` is **28** (Android 9); anything newer is behind a `Build.VERSION.SDK_INT`
check in the code. In paths, `…` = the `src/main/kotlin/com/baklavapp/reachlauncher/<module>`
part, e.g. `core/system/…/launch/` = `core/system/src/main/kotlin/com/baklavapp/reachlauncher/core/system/launch/`.

For a Flutter developer: everything below is called **directly from Kotlin** — no
MethodChannel, no plugin. In Reach, these calls live only in `:core:system` (and a few UI
APIs in the feature modules / `:app`), behind the interfaces in `:core:data` (see
[ARCHITECTURE.md](ARCHITECTURE.md)).

---

## Permissions

| Permission | Type | Declared in | Why | Without it |
|---|---|---|---|---|
| `REQUEST_DELETE_PACKAGES` | **normal** (granted at install, no dialog) | `core/system/src/main/AndroidManifest.xml` | Lets Reach open the system's "Uninstall this app?" confirmation (`ACTION_DELETE`) for apps targeting API 28+ (D26). It does **not** let Reach uninstall anything silently: the system always asks. Used by "Uninstall" in the All Apps long-press menu (M2). | The uninstall request would be refused. |
| `READ_CONTACTS` | **runtime / "dangerous"** (system dialog), optional | `core/system/src/main/AndroidManifest.xml` | Search finds contacts by name and offers Call / Message / Contact card (spec §6.6, D45). See [Contacts](#contacts-contactscontract-m2). | No contact rows; everything else in Search works. |
| `READ_CALENDAR` | **runtime / "dangerous"**, optional | `core/system/src/main/AndroidManifest.xml` | The "next event" line under the clock (D56). See [Calendar](#calendar-calendarcontract-m3). | No line; nothing else changes. |
| `PACKAGE_USAGE_STATS` | **special access** (user flips a switch in Settings; `tools:ignore="ProtectedPermissions"`), optional | `core/system/src/main/AndroidManifest.xml` | Hour-of-day launch counts for the Now row (D57). See [Usage access](#usage-access-usagestatsmanager-m3). | Predictions use Reach's own launch history only. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | **service permission** (held only by the system); the *user* grants "Notification access" in Settings, optional | `<service>` in `core/system/src/main/AndroidManifest.xml` | Unread counts and media controls. See [Notification listener](#notification-listener-m3) and [Media sessions](#media-sessions-m3). | No badges, no media row. |
| *Shortcut host access* (not a manifest permission) | granted by **being the default home app** | — | `LauncherApps.getShortcuts` / `startShortcut` for app shortcuts in Search and intent detail. See [App shortcuts](#app-shortcuts-launcherapps-m2). | No shortcut rows or pills. |

Those are the **only** permissions Reach declares as of M4 (M4 added none: wallpaper colours need no permission) (the last four are all optional). The merged manifest additionally
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
| `VIBRATE` | Not needed: haptics go through `View.performHapticFeedback` (Compose `LocalHapticFeedback`), which needs no permission ([Haptics](#haptics-m2)). |
| `CALL_PHONE`, `SEND_SMS` | Not needed: a contact's Call / Message only *pre-fill* the dialer / messaging app (`ACTION_DIAL`, `ACTION_SENDTO`); the user presses call/send there. |
| Accessibility service | Not yet: still a stub until M5 (see [Coming in M5](#coming-in-m5)). |

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
| `LauncherApps.startAppDetailsActivity(component, user, null, null)` | Opens the system "App info" screen for an app in a given profile. | "App info" in the All Apps long-press menu (M2), via `SystemAppLauncher.openAppInfo`. |

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

All screens reach this through the shared `LaunchApp` use case (`core/data/…/domain/LaunchApp.kt`),
which records only real launches ([ARCHITECTURE.md §3e](ARCHITECTURE.md)).

---

## App shortcuts (`LauncherApps`, M2)

`core/system/…/shortcuts/LauncherAppsShortcutSource.kt` (D47). Shown as rows in Search
(≤ 3, `SearchRanker.MAX_SHORTCUTS`) and as pills in intent detail (≤ 4).

| API | What it does | Why Reach needs it |
|---|---|---|
| `LauncherApps.hasShortcutHostPermission()` (API 25) | `true` exactly while the caller is the **default home app**. | Checked before every read and launch; without it every shortcut call throws `SecurityException`, so Reach reports `hasHostPermission = false` and an empty list instead. |
| `LauncherApps.getShortcuts(ShortcutQuery, user)` with `setQueryFlags(FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST or FLAG_MATCH_PINNED)` | An app's manifest shortcuts, the ones it publishes at run time (recent chats…), and those pinned *to Reach*. | Read per visible profile. Disabled shortcuts (can't be started) and unlabelled ones are dropped; the short label is preferred over the long one. A paused work profile throws `IllegalStateException` → that profile contributes nothing. A locked private space is never queried. |
| `LauncherApps.startShortcut(packageName, id, sourceBounds, options, user)` | Starts a shortcut in a given profile. | Tapping a shortcut row/pill, with the same clip-reveal animation as an app launch. `ActivityNotFoundException` (gone/disabled), `IllegalStateException` (profile locked/not running) and `SecurityException` (permission lost) return `false` → "Couldn't open X". |
| `LauncherApps.Callback.onShortcutsChanged(packageName, shortcuts, user)` | Fired when an app publishes, updates or removes shortcuts. | Like the package callbacks (also registered), it only *pokes* a full re-read. |
| `LauncherApps.getApplicationInfo(packageName, 0, user)` (API 26) | Package info in a given profile. | The source app's label for rows like "MESSAGE AYŞE · WHATSAPP". |

**How the user grants it:** by making Reach the default home app — there is no dialog of its
own. **Without it:** no shortcut rows in Search, no shortcut pills in intent detail; nothing
else changes. There's no broadcast when Reach becomes default home, so the permission change
is noticed on the next collection start (e.g. opening Search) or callback (D47).
**Play policy:** none specific.

---

## Contacts (`ContactsContract`, M2)

`core/system/…/contacts/ContactsProviderSource.kt`, used by `SearchViewModel` (D45).

**Background for a Flutter developer.** Contacts live in a system *content provider* — a
database owned by another process. You query it with `ContentResolver.query(uri, columns,
selection, args, sortOrder)` and read the rows with a `Cursor` (what a `flutter_contacts`-style
plugin does behind its `MethodChannel`). It's a blocking IPC call, so it runs on the IO
dispatcher.

| API | What / why |
|---|---|
| `ContactsContract.Contacts.CONTENT_FILTER_URI` + `Uri.encode(text)` + `LIMIT_PARAM_KEY` | The provider's own name lookup: matches the start of any name word, case- and accent-insensitive, capped at 12 rows (`CONTACT_FETCH_LIMIT`; the ranker shows ≤ 3). Columns `_ID`, `LOOKUP_KEY`, `DISPLAY_NAME_PRIMARY`, `HAS_PHONE_NUMBER`. |
| `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`, `CONTACT_ID IN (…)`, sorted `IS_SUPER_PRIMARY DESC, IS_PRIMARY DESC, _ID` | One number per contact: the default number first. Only for contacts with a phone. |
| `ContactsContract.Contacts.getLookupUri(id, lookupKey)` | Opens the contact card (`ExternalAction.ViewContact`). The lookup key survives contact merges/syncs better than the row id. |
| `ContextCompat.checkSelfPermission(context, READ_CONTACTS)` | Checked before every query; re-checked on every `ON_RESUME` of Search (the user may grant it in Settings). |

**How it's requested.** Never at startup. After at least two typed letters, Search shows one
"Include contacts" row with a one-line rationale (only while not granted and not declined
this session). Tapping it runs
`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` →
`launch(Manifest.permission.READ_CONTACTS)` in `SearchRoute` (≈ permission_handler's
`Permission.contacts.request()`); the answer goes to
`SearchViewModel.onContactsPermissionResult(granted)`. A denial hides the row for the rest of
the process's life (not persisted, D45); a permanently denied permission ends there too,
because the system then answers "denied" at once.

**Without it:** contacts are never queried; Search shows apps, shortcuts, commands and the web
row. Any provider failure (`SecurityException` if revoked mid-query, a crashed or missing
provider) also yields an empty list.

**Privacy / Play notes.** Contacts are personal data. Reach reads them **on demand, for the
current query only**: nothing is stored (no Room table, no DataStore key), and with no
`INTERNET` permission nothing can leave the device. The Play listing's Data safety form and
`PRIVACY.md` (**M3**) will still need to state that contacts are accessed and processed on the
device only, never collected or shared. Work-profile contacts (`ENTERPRISE_CONTENT_FILTER_URI`) are not read yet
(**M5**).

---

## Letter buckets: `android.icu.text.AlphabeticIndex` (API 24, M2)

`feature/allapps/…/LetterIndex.kt` (D40). ICU's `AlphabeticIndex(locale)` — the class the
system Contacts app uses for its fast-scroll letters — knows per locale which letters an
alphabet has and which characters fold into them: in English "Ödeme" files under O; in
Turkish Ç, I, İ, Ö, Ş, Ü, Ğ are letters of their own. Reach builds
`AlphabeticIndex<Any>(locale).addLabels(Locale.ENGLISH).buildImmutableIndex()` (English labels
added so Latin names like "WhatsApp" get a W even in Turkish), uses `getBucketIndex(name)` /
`getBucket(…)`, and merges the underflow/overflow/inflow buckets into one "#" group. The built
`ImmutableIndex` is thread-safe; it's rebuilt only when the composition's locale
(`LocalLocale`) changes, off the main thread. No permission. (Flutter has no equivalent in
the SDK; packages like `azlistview` approximate it with `name[0].toUpperCase()`.)

---

## Haptics (M2)

| API | Where | What / why |
|---|---|---|
| `LocalHapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)` | `core/designsystem/…/util/Motion.kt` (`rememberHapticTick`), used by `PressSlideRelease.kt` and `LetterRail.kt` | One light tick per new fan pill / rail letter (spec §6.5, §6.7) — Compose's semantic type for stepping through discrete values (≈ `HapticFeedback.selectionClick()`). |
| `LocalHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)` | `feature/home/…/ui/HomeClock.kt` | The clock long-press. |

Both go through `View.performHapticFeedback`, which **needs no `VIBRATE` permission** (there is
none in any manifest) and follows the user's system touch-feedback setting.

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
entry) and can launch apps; pressing Home opens the other launcher. Features that need the
role: app shortcuts (`hasShortcutHostPermission`, [M2](#app-shortcuts-launcherapps-m2)),
resuming a paused work profile, the private space (M5).
**M2 status:** the role state is read on every resume, and `AppLauncher.open(SystemScreen.HOME_ROLE)`
is implemented and tested (`SystemAppLauncherTest`, `HomeRoleSourceTest`), but no UI shows
the onboarding card yet — that's **M3**. Until then, set the default via Settings or adb
(see the README).

---

## Settings deep links and hand-offs to other apps

Implemented in `core/system/…/launch/SystemIntents.kt`, exposed through
`AppLauncher.open(SystemScreen)` and `AppLauncher.perform(ExternalAction)`. Since M2,
**Search uses the `ExternalAction`s** (web fallback, contact Call / Message / Card) and All
Apps' menu uses uninstall; the `SystemScreen` deep links still wait for the onboarding /
permissions UX (M3). Covered by `SystemAppLauncherTest`. None needs a permission.

| Domain request | Android intent | Used for |
|---|---|---|
| `SystemScreen.NOTIFICATION_ACCESS` | `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` | Granting notification access (M3). The per-app detail screen (API 30) needs Reach's listener component, which arrives in M3. |
| `SystemScreen.USAGE_ACCESS` | `Settings.ACTION_USAGE_ACCESS_SETTINGS` | Granting usage access (optional prediction blend). |
| `SystemScreen.ACCESSIBILITY_SERVICE` | `Settings.ACTION_ACCESSIBILITY_SETTINGS` | Enabling the optional accessibility service. |
| `SystemScreen.HOME_ROLE` | role dialog, else `Settings.ACTION_HOME_SETTINGS` | See above. |
| `ExternalAction.WebSearch(query)` | `ACTION_WEB_SEARCH` + extra `SearchManager.QUERY` | Search's always-last "Search the web for '…'" row (and Enter when it's the top row). |
| `ExternalAction.Dial(number)` | `ACTION_DIAL`, `tel:` URI | Contact → Call pill. **Pre-fills** the dialer; doesn't call, so no `CALL_PHONE`. |
| `ExternalAction.Message(number)` | `ACTION_SENDTO`, `smsto:` URI | Contact → Message pill. No `SEND_SMS`. |
| `ExternalAction.ViewContact(id, lookupKey)` | `ACTION_VIEW`, `ContactsContract.Contacts.getLookupUri(…)` | Contact → Card pill. |
| `AppLauncher.requestUninstall(key)` | `ACTION_DELETE`, `package:<pkg>` URI + `Intent.EXTRA_USER` | "Uninstall" in the All Apps long-press menu. `EXTRA_USER` targets the work copy instead of the personal one. `ACTION_UNINSTALL_PACKAGE` is deprecated since API 29 (D26). Requires `REQUEST_DELETE_PACKAGES`. |

All are started from the resumed Activity when there is one (else with `FLAG_ACTIVITY_NEW_TASK`);
"nothing handles this" (`ActivityNotFoundException`) returns `false`. Search then shows a short
"no app can do this" line (`SearchEvent.NoHandler`) instead of closing; the web row itself is
always offered (Reach doesn't resolve a handler first).

---

## Package visibility (`<queries>`)

Since Android 11 (API 30), an app only sees other apps it declares an interest in.

| `<queries>` entry | Declared in | Why |
|---|---|---|
| `MAIN` + `LAUNCHER` | `app/src/main/AndroidManifest.xml` | Makes every app with a launcher activity visible to `LauncherApps` — exactly what a launcher shows. Replaces `QUERY_ALL_PACKAGES`. |
| `WEB_SEARCH` | `core/system/src/main/AndroidManifest.xml` | Lets Reach resolve a web-search handler. Declared ahead of use: in M2 the web row is always shown and a missing handler is reported after the tap. |
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
| `ViewConfiguration.longPressTimeoutMillis` (Compose `LocalViewConfiguration`, `PointerInputScope.viewConfiguration`) | `HomeClock`, `PressSlideRelease.kt` | Long-press on the clock = `max(450 ms, system long-press timeout)` (D30); the fan's hold = 300 ms unless the system timeout is > 500 ms, then the user's value (D48). Either way the accessibility "Touch & hold delay" is never shortened. |
| `ViewConfiguration.touchSlop` | `PressSlideRelease.kt` | Moving further than this during the hold abandons the fan (≈ `kTouchSlop`). |
| `Settings.Global.ANIMATOR_DURATION_SCALE` + `ContentObserver` | `core/designsystem/…/util/Motion.kt` (`rememberReducedMotion`) | "Remove animations" (spec §6.10): surface transitions fall back to a plain cross-fade, the fan's entrance and the rail's animated scroll are skipped. Read-only, no permission. |
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
| `windowSoftInputMode="adjustResize"` | manifest | The window resizes for the keyboard; `SearchScreen` pads itself by `WindowInsets.safeDrawing` (which includes the IME inset), so results sit right above the keyboard. |
| `FocusRequester.requestFocus()` + `LocalSoftwareKeyboardController.show()` | `SearchRoute` | Search opens with the keyboard up (spec §6.6). |
| `LocalSoftwareKeyboardController.hide()` + `LocalFocusManager.clearFocus(force = true)` | `HomeRoute`, `SearchRoute` | "Pressing Home closes the keyboard" (spec §4), on `HomeEvent.HideKeyboard`; Search also hides it when it closes. |
| `BackHandler` (AndroidX `OnBackPressedDispatcher`) | `HomeRoute`, `AppMenu` | Intercepts system Back (button and gesture): sub-surface → Home, Home → nothing (D28); while All Apps' long-press menu is open, Back closes the menu (D42). |
| `WindowInsets.systemGestures` | `AllAppsScreen`, `FanOverlay` | The zones where a swipe is a system gesture (back from the left/right edge, home from the bottom). The letter rail is inset by `systemGestures.exclude(safeDrawing)` on its side, and fan pills stay inside `safeDrawing ∪ systemGestures` (D41, D50). |
| `Modifier.systemGestureExclusion()` (→ `View.setSystemGestureExclusionRects`, API 29) | `LetterRail` | Asks the system not to treat swipes on the rail strip as Back — a backup for devices whose real zone is wider than the reported inset. Android caps exclusions at **200dp per edge**, except for the current home activity; Reach insets the rail anyway, so Back keeps working along the whole edge and nothing depends on being default home (D41). |
| `android:windowBackground` in the window theme | `app/src/main/res/values*/themes.xml` | Default palette bg before Compose's first frame — no white flash (D8). |

Not yet: wallpaper window theme / blur (M4).

---

## Data storage (for completeness)

Room (`reach.db`, `core/data/…/db/`) and Preferences DataStore (`settings`, built in
`DataModules.kt`) write only to the app's private storage. No permission, nothing shared
with other apps, excluded from cloud backup and device transfer (D9).

---

## Access that is granted outside Reach, and the resume re-check (M3, D53)

**What.** Notification access and usage access are *special accesses*: no runtime dialog, the
user flips a switch on a Settings screen. The calendar and contacts permissions are normal
runtime permissions, but their dialog also happens outside Reach's own UI. Android sends **no
broadcast** when any of these changes.

**How Reach notices.** To grant anything, the user leaves Reach's Activity and comes back, so
the Activity *resumes*. `ActivityTracker.onActivityResumed` calls `AccessRechecks.recheck()`
(`core/system/…/access/AccessRechecks.kt`), a singleton `StateFlow<Long>` counter. Each
access-gated source maps it to a fresh read:

```kotlin
// simplified from core/system/…/access/AccessRechecks.kt (KDoc)
override val isAccessGranted = rechecks.ticks.map { readAccess() }.distinctUntilChanged()
```

No contract needs a `recheck()` method, and no screen can forget to call it. Flutter: a
`ValueNotifier<int>` bumped from `didChangeAppLifecycleState(resumed)`.

---

## Notification listener (M3)

**What.** `ReachNotificationListenerService` (`core/system/…/notifications/`) extends
`NotificationListenerService`. Once the user enables it, the *system* binds it and calls
`onListenerConnected`, `onNotificationPosted` and `onNotificationRemoved`. It forwards only
the notification `key`, the package + profile (`PackageUser`, D12) and two flags (ongoing,
group summary) to `NotificationListenerHub`. It **never reads titles or text** and stores
nothing (D54).

**Manifest.**

```xml
<!-- simplified from core/system/src/main/AndroidManifest.xml -->
<service android:name=".notifications.ReachNotificationListenerService"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
    <meta-data android:name="android.service.notification.default_filter_types"
        android:value="conversations|alerting|silent" />
</service>
```

- `exported="true"` because the system binds it from outside Reach's process;
  `BIND_NOTIFICATION_LISTENER_SERVICE` means only the system (which holds it) can bind, so
  no other app can.
- `default_filter_types` (API 31+) says which kinds of notifications Reach wants delivered:
  not the *ongoing* ones (music, navigation), which Reach would not count anyway. The user
  can change it in Settings.

**Why.** The "3 NEW" badges (spec §7.1), and it is the key to media sessions (next section).

**How granted.** Home shows a card (below). Its button opens
`Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with
`EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` on **API 30+** (the switch for Reach alone), or
`ACTION_NOTIFICATION_LISTENER_SETTINGS` (the list of all listeners) before that
(`SystemIntents.kt`). Android 13+ may grey the switch for apps not installed from a store
("Restricted setting": Settings > Apps > Reach > ⋮ > *Allow restricted settings*).

**Access check and rebind.** `NotificationAccess.isGranted()` calls
`NotificationManager.isNotificationListenerAccessGranted(component)` (API 27+, no permission
needed, exact component). It is re-read on every resume ([above](#access-that-is-granted-outside-reach-and-the-resume-re-check-m3-d53))
and whenever the listener connects or disconnects. If access is granted but the hub says
"not connected" (after an app update, or an OEM killing the process), Reach calls
`NotificationListenerService.requestRebind(component)` (API 24+, a no-op if already bound) so
badges do not silently freeze. The hub is cleared on disconnect, so counts are never stale.

**Counting rules (D54).** Not ongoing (including foreground-service notifications), not a
group summary, not Reach's own; keyed by `StatusBarNotification.key` so an update never
counts twice; grouped per `PackageUser`.

**Without it.** `NotificationCountSource` reports `isAccessGranted = false` and an empty map
-> no badges, and `MediaSessionSource` reports nothing.

**Play policy.** Notification access is a *sensitive* permission. Reach (1) uses it only for
the user-visible features above, (2) processes on device only (no `INTERNET`), (3) never
reads content and keeps counts in memory, (4) shows an in-app disclosure *before* sending the
user to Settings (card text: "Notification access shows “3 NEW” on intents and what’s
playing. Nothing leaves your phone.", D58), and (5) documents it in [PRIVACY.md](../PRIVACY.md).

---

## Media sessions (M3)

**What.** `MediaSessionsSource` (`core/system/…/media/`) uses `MediaSessionManager`:

```kotlin
// simplified from core/system/…/media/MediaSessionsSource.kt
manager.addOnActiveSessionsChangedListener(listener, access.listenerComponent, handler)
track(manager.getActiveSessions(access.listenerComponent))   // throws SecurityException without access
```

Every music/podcast/video app publishes a *session*; reading and controlling other apps'
sessions needs an enabled notification listener, which is why `getActiveSessions` takes
Reach's `ComponentName`. Reach registers a `MediaController.Callback` on each session
(`onPlaybackStateChanged`, `onMetadataChanged`, `onSessionDestroyed`) and re-chooses the
session to show on every change (`MediaSessionChooser`, D55: has a title, playing beats
paused, then the system's order).

**Commands.** `controller.transportControls.play() / pause() / skipToNext() /
skipToPrevious()` on the shown session's controller; with no session they do nothing.

**Progress.** Sessions report position only on state changes, so the UI extrapolates
(`MediaState.positionAt`: position + elapsed × speed) once a second, only while playing and
Home is STARTED; with "Remove animations" the bar steps instead of gliding.

**Why / how granted / without it.** The Listen row's inline player and the Now Playing card
(spec §7.2). Granted by the same notification-access switch. Without it: `mediaState`
emits `null`, nothing is shown. **Play policy:** same as above; title and app label are
held in memory only.

---

## Calendar (`CalendarContract`, M3)

**What.** `CalendarProviderSource` (`core/system/…/calendar/`) queries
`CalendarContract.Instances` (occurrences, so recurring events just work) for local today +
tomorrow, only *visible* calendars, and shows the next one (D56: declined and untitled
events skipped; today's next timed event -> today's all-day -> tomorrow's first timed ->
tomorrow's all-day).

**Permission.** `READ_CALENDAR`, runtime, asked from a dismissible Home card with
`ActivityResultContracts.RequestPermission`. Denied -> the card is dismissed for good.

**When it re-reads** (only while Home collects it): on resume, on calendar
`ACTION_PROVIDER_CHANGED`, on `ACTION_TIME_TICK` and time/date/zone changes.

**Why an exported receiver.** The provider's *app* sends `ACTION_PROVIDER_CHANGED`, not the
system, so a `RECEIVER_NOT_EXPORTED` receiver would never get it. Reach registers it
`RECEIVER_EXPORTED`, filtered to the `content://com.android.calendar` authority. A spoofed
broadcast can only make Reach run one cheap, permission-checked query. (All of Reach's
other receivers are not exported.)

```kotlin
// simplified from core/system/…/calendar/CalendarProviderSource.kt
val filter = IntentFilter(Intent.ACTION_PROVIDER_CHANGED).apply {
    addDataScheme("content"); addDataAuthority(CalendarContract.AUTHORITY, null)
}
ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
```

**Without it.** `nextEvent` is `null`; the line is hidden. **Play policy:** a dangerous
permission with a clear user-facing feature; the data is read on demand, kept in memory and
never stored or sent.

---

## Usage access (`UsageStatsManager`, M3)

**What.** `UsageStatsManagerSource` (`core/system/…/usage/`) calls
`UsageStatsManager.queryEvents(begin, end)` for the last N days and counts launches per hour
and app (D57): `ACTIVITY_RESUMED` events (`MOVE_TO_FOREGROUND` on API 28); consecutive resumes
of the same app count once; Reach's own resumes are ignored; hours are local. It only sees
the current user's data. Nothing is stored. The Now row blends these counts into its
predictions.

**Permission.** `PACKAGE_USAGE_STATS`, declared with `tools:ignore="ProtectedPermissions"`:
lint calls it "system only" because a normal install never grants it. Declaring it grants
nothing; the user turns on *Usage access* for Reach (`Settings.ACTION_USAGE_ACCESS_SETTINGS`).

**Access check.** Not `checkSelfPermission` (it says "denied" even when allowed). Instead:

```kotlin
// simplified from core/system/…/usage/UsageStatsManagerSource.kt
val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
// MODE_ALLOWED -> granted; MODE_DEFAULT -> fall back to the permission check
```

Re-read on every resume (D53).

**Without it.** `isAccessGranted = false`, empty buckets; predictions use only Reach's own
launch history (which needs no permission).

**Play policy.** Usage access is *sensitive*. Reach asks only from an optional card ("Optional.
Usage access tells Reach which apps you open at what hour. It stays on your phone.", D58),
uses it only for on-device predictions, and lists it in [PRIVACY.md](../PRIVACY.md).

---

## In-app disclosure cards (D58)

Before Reach sends the user to any of these screens, Home shows **one compact, dismissible
card at a time**, in this order: default home -> notification access -> calendar -> usage
access. Each has a one-line reason (the in-app disclosure Play expects for sensitive
permissions), a primary action and "Not now". "Not now" and a denied calendar permission are
stored permanently in DataStore (`dismissedTips`): Reach never nags. If a Settings screen
cannot be opened on the device, the card dismisses itself. A card disappears as soon as the
access is on. Code: `HomeViewModel.onboarding` / `OnboardingCard.next`,
`feature/home/…/ui/SystemCards.kt`.

---

## Wallpaper colours and the wallpaper window (M4, D61–D62)

No permission is needed for any of this: Reach reads the wallpaper's *colours*, never its bitmap
(`READ_EXTERNAL_STORAGE`/`READ_MEDIA_*` are not involved).

| API | Where | Level | Notes |
|---|---|---|---|
| `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` → `WallpaperColors.primaryColor` | `core/system/…/wallpaper/WallpaperManagerColorSource.kt` | Android 8.1 (27) | Below Reach's `minSdk` 28, so no version check. Returns null for some wallpapers/OEMs; null (also from `SecurityException` or OEM `RuntimeException`) means "unknown" and the palette accent is used. It's a binder call, so it runs on `Dispatchers.IO`. |
| `WallpaperManager.addOnColorsChangedListener(listener, handler)` / `removeOnColorsChangedListener` | same, inside a `callbackFlow` | 27 | Re-emits when the system wallpaper changes; only `FLAG_SYSTEM` changes are used; null colours in the callback trigger a re-read. Registered only while wallpaper mode is on (`flatMapLatest` in `HomeViewModel.theme`). |
| `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER` via `Window.addFlags` / `clearFlags` | `app/…/ReachWindow.kt` | all | Same as the theme attribute `windowShowWallpaper`, but switchable at run time without recreating the Activity (which Reach never does). Paired with a transparent window background. |
| `Window.setBackgroundBlurRadius(px)` | `ReachWindow.applyBlur` | **Android 12 (31)** | 40dp (converted to px), only while wallpaper mode is on and blur is enabled; 0 otherwise. |
| `WindowManager.addCrossWindowBlurEnabledListener(executor, consumer)` / `remove…` | `ReachWindow` | 31 | Blur can be off (battery saver, developer option, some GPUs); the radius follows the listener. Without blur, the 82 %/86 % scrim alone keeps text readable. |

Below Android 12 the wallpaper is shown unblurred under the scrim. Accent contrast rules
(`WallpaperAccent`): `accentInk` ≥ 4.5:1 on bg, and `onAccent` is the more contrasting of bg/fg.

## Coming in M5

Notification counts, media sessions, the default-home card, calendar, usage stats and wallpaper
colours are done. **Only this remains stubbed**, bound to an `Unavailable…` class in
`core/system/…/di/SystemModule.kt` (`core/system/…/unavailable/UnavailableSources.kt`):

- **Accessibility service** — opt-in, `GLOBAL_ACTION_LOCK_SCREEN` / `GLOBAL_ACTION_NOTIFICATIONS`, with the Play accessibility-API disclosure text (M5).

Also planned (not behind a stub): **work-profile contacts** via
`ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI`, subject to the admin's
cross-profile contacts policy (M5), and the **private space** via `ACCESS_HIDDEN_PROFILES`
with the exact Android 15+ behaviour verified in M5 (D13).
