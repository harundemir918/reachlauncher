# Reach Privacy Policy

**Effective date: 2026-11-15**

Reach is an Android home screen (launcher). This page says, in plain language, what it
reads on your phone and what it does with it.

## The short version

- **Nothing leaves your device.** Reach does not have the Android `INTERNET` permission, so
  it cannot open a network connection at all. It cannot upload, sync or "phone home".
- **No analytics, no crash-reporting service, no advertising, no accounts, no sign-in.**
- **No cloud backup.** Reach's data is excluded from Google backup and from device-to-device
  transfer, so it is not copied anywhere.
- Everything optional is **off until you switch it on**, and Reach works without it.

## What Reach reads, why, and where it stays

| Data | Why Reach reads it | Where it stays | How to turn it off |
|---|---|---|---|
| **Installed apps** (names and launcher icons' labels, through Android's launcher API) | To show your apps, sort them into the six intent words and open them. This is what a launcher is. | A cache in Reach's private on-device database so Home opens instantly. | Uninstalling Reach removes it. |
| **Launch history** (which app you opened, and when) | To order apps by how often you use them and to suggest apps in the "Now" row. | Reach's private on-device database. Used only for these predictions. | Clear Reach's storage in Android Settings > Apps > Reach > Storage. |
| **Notifications** (optional, *Notification access*) | To show how many unread notifications each app has ("3 NEW") and to find the music that is playing. | **Counts per app, in memory only.** Reach never reads or stores a notification's title, text, sender or any other content, and never writes the counts to disk. They disappear when access is turned off or Reach stops. | Settings > Notifications > Notification access (or Device & app notifications) > Reach > off. |
| **Media session** (optional, uses the same Notification access) | To show the track title and app that is playing, and to give you play, pause, next and previous. | In memory only, while it is playing. Not stored. | Same switch as above. |
| **Contacts** (optional, *Contacts* permission) | So that typing a name in Search can find a contact and offer Call, Message or Contact card. | Looked up **only while you type**, for that query. Never stored, never copied into Reach's database. | Android Settings > Apps > Reach > Permissions > Contacts. |
| **Calendar** (optional, *Calendar* permission) | To show the next event of today or tomorrow under the clock. | The next event only, in memory. Not stored. | Android Settings > Apps > Reach > Permissions > Calendar. |
| **Usage access** (optional, special access) | To learn which apps you open at which hour of the day, so the "Now" row guesses better. | Counts are computed on the device from Android's own usage log and are not stored by Reach or sent anywhere. | Settings > Apps > Special app access > Usage access > Reach > off. |

| **Private space** (Android 15+, only if you use one) | To show a "Private space" section in All Apps with Lock / Unlock. | Nothing is stored. Reach shows the space's apps **only while Android says it is unlocked**; locked, or if Reach can't tell, they are never shown (not in Home, Search, All Apps or suggestions). | Lock the space, or make another app your home screen. |
| **Accessibility service** (optional, *Reach screen actions*) | To lock the screen on a double-tap and to open the notification shade with a two-finger swipe down. | It **reads nothing**. It cannot see your screen, windows or what you type, subscribes to no accessibility events, and can only press "lock screen" and "open notifications" when you ask on Home. No data is collected or stored. | Settings > Accessibility > Reach screen actions > off. |

Reach also stores your own settings (theme, hidden apps, which tips you dismissed, the
"Double-tap to lock" choice) in a small private file on the device.

## Permissions

| Permission | Required? | Used for |
|---|---|---|
| Notification access (special) | Optional | Unread counts and media controls |
| `READ_CONTACTS` | Optional | Contacts in Search |
| `READ_CALENDAR` | Optional | Next event line |
| Usage access (`PACKAGE_USAGE_STATS`, special) | Optional | Better Now-row suggestions |
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`) | Optional | Lock screen and notification shade gestures. You switch it on yourself in Android's Accessibility settings; Reach never turns it on. |
| `ACCESS_HIDDEN_PROFILES` | Install-time | Lets Reach, when it is your home screen, see that a private space exists so it can offer Lock / Unlock. It does not unlock it or read anything inside. |
| `EXPAND_STATUS_BAR` | Install-time | Only a fallback for opening the notification shade; it cannot read notifications. |
| `REQUEST_DELETE_PACKAGES` | Install-time | Lets Reach show Android's own "Uninstall this app?" question. Android always asks you; Reach cannot uninstall anything by itself. |

Reach has **no** `INTERNET`, `QUERY_ALL_PACKAGES`, location, camera, microphone, SMS or
phone-call permission.

## If you say no

Every optional permission can be denied or switched off and Reach still works fully:

- **No notification access:** no "NEW" badges and no media row or Now Playing card.
- **No contacts:** Search shows apps, shortcuts and commands, but no contacts.
- **No calendar:** the line under the clock is not shown.
- **No usage access:** the "Now" row uses only Reach's own launch history.
- **No accessibility service:** double-tap and two-finger swipe show a short hint instead;
  everything else is the same.

Reach asks for each of these once, on a small card that you can dismiss with "Not now".
A dismissed or denied card is not shown again.

## Children

Reach collects no personal information and does not target children.

## Changes

If this policy changes, the new version will be published with a new effective date.

## Questions

Contact: `[CONTACT EMAIL]`
