# Domonap (intercom / домофон)

Read from public sources on 2026-08-15. The tablet itself was inspected the same day — see
[Recorded on the tablet](#recorded-on-the-tablet-2026-08-15), which supersedes the guesses further
down wherever the two disagree. **No Domonap endpoint has been called.** Everything below about the
*backend* comes from a third-party Home Assistant integration, not from Domonap. Do not invent
endpoints on top of it.

## Recorded on the tablet (2026-08-15)

Read off the actual panel tablet — Samsung SM-T875 (`gts7l`), Android 13 / API 33 — with the app
installed and logged in. Everything in this section is observed output, not inference; the two
inferences are labelled. Reproduce with:

```bash
adb shell dumpsys notification --noredact     # channels, and the live record during a call
adb shell dumpsys telecom                     # phone accounts, call state
adb shell dumpsys package com.domonap.app     # permissions, components
adb pull "$(adb shell pm path com.domonap.app | head -1 | cut -d: -f2)" base.apk
aapt2 dump xmltree base.apk --file AndroidManifest.xml
```

**Package.** `com.domonap.app`, versionName/versionCode `9850`, `minSdk=24`, **`targetSdk=36`**.

**Notification channels** — four, all created by the app, names in Russian:

| id | name | importance | sound |
|---|---|---|---|
| `telecom_incoming_channel3` | Входящие звонки | **5 (MAX)** | `null` |
| `telecom_silent_channel3` | Тихий канал | 2 (LOW) | `null` |
| `telecom_ongoing_channel3` | Исходящие звонки | 3 (DEFAULT) | default |
| `telecom_missed_channel3` | Уведомление | 3 (DEFAULT) | default |

The incoming channel is `mImportance=5`, `mBypassDnd=false`, `mSound=null`, `mAudioAttributes=null`.
Sound being null means **the ringtone is not the channel's** — it is played by the app/Telecom. So
the "maximum volume" part of the takeover is already Domonap's, and the panel must not touch audio
streams to try to help.

**The call is a real Telecom call, not just a notification.** `dumpsys telecom` shows a registered
self-managed phone account:

```
PhoneAccount: ComponentInfo{com.domonap.app/androidx.core.telecom.internal.JetpackConnectionService},
  Capabilities: SelfManaged   Audio Routes: BESW   Schemes: tel   Extras: [isCoreTelecomAccount=true]
```

So Domonap drives the call through **Jetpack `androidx.core.telecom`**, and the system gives it
call-grade audio focus and lifecycle. This was not known before and is the most consequential fact
here: it means there is a second, system-level signal for "a call is up" besides the notification.

**The call screen** is a declared activity:

```
com.domonap.telephony.presentation.activity.incoming.IncomingDomofonCallActivity
  exported=true  launchMode=singleTop  noHistory=true
  showOnLockScreen=true  showWhenLocked=true  turnScreenOn=true
  configChanges=0x0d80  (orientation|screenLayout|screenSize|smallestScreenSize)
```

`showWhenLocked` + `turnScreenOn` mean **the platform already puts this screen on top and wakes the
tablet** — our panel does not have to launch anything, only get out of its way.

Supporting components: `com.domonap.telephony.presentation.service.TelephonyService`
(`foregroundServiceType=0x4`, i.e. `phoneCall`, `directBootAware=true`),
`com.domonap.telephony.presentation.broadcast.NotificationIntentReceiver` (the accept/decline
actions), `com.domonap.platform.gms.FirebaseMessagingService` and
`com.domonap.app.services.push.signalR.SignalRService` — so pushes arrive over **both FCM and
SignalR**. There is also `com.domonap.app.ui.widgets.WidgetProvider`, a Glance AppWidget.

**Permissions requested** (relevant subset): `USE_FULL_SCREEN_INTENT`, `MANAGE_OWN_CALLS`,
`FOREGROUND_SERVICE_PHONE_CALL`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`.

**Orientation: the app does not force landscape.** `screenOrientation` occurs **zero times** in the
whole manifest, and there is no `resizeableActivity`, `minAspectRatio` or `maxAspectRatio` either;
`IncomingDomofonCallActivity` declares `configChanges` including `orientation`, i.e. it handles
rotation itself. The panel display is 1600×2560 at density 340 → **smallest width ≈ 753 dp**, and
`wm get-ignore-orientation-request` reads `false` for display 0. This kills case 2 in the orientation
section below: if the call screen ever appears landscape, that is the tablet's rotation lock, not
Domonap.

### Still missing — the notification record itself

**No incoming call has been captured yet**, so the posted notification's `extras` — title, text,
`android.title`/`android.text`, which field carries the caller or the door, whether a
`fullScreenIntent` and `CATEGORY_CALL` are set, the actions, the `tag` and `key` — are **still
unrecorded**. The channels above prove the app *has* an incoming-call channel; they do not prove
what it puts in the notification. A capture needs the intercom rung once while
`dumpsys notification --noredact` is sampled. Until that exists, nothing in the panel should key on
any extras field.

An attempt was made on 2026-08-15 and could not be completed — the calling device was out of
service, so nothing rang. Re-arm with a poll of `dumpsys notification --noredact` filtered to
`pkg=com.domonap.app`, keep it running, and ring once.

**What the panel keys on meanwhile** (`DomonapCalls`): package `com.domonap.app` plus channel id
`telecom_incoming_channel3` or `telecom_ongoing_channel3`, and nothing else. Consequences to revisit
when a capture lands:

- The caller's name, flat and photo are **not shown**, because no field is known to carry them.
- `telecom_silent_channel3` is treated as *not* a call. If the capture shows the `phoneCall`
  foreground service posts there for the duration of a call, that is the channel that should extend
  the call, and it must still never be the thing that *starts* one — it is at importance 2 and could
  plausibly sit there idle.
- Whether the ringing notification is replaced (new key) or updated (same key) when the call is
  answered is unknown; `DomonapCalls` handles both, and the capture will say which happens.

## Verified — as facts about the public record, not about the API

**Official developer API: none.** No developer portal, no API docs, no published terms. Domonap
is a resident-facing service (door release, video call from the panel, guest keys, building chat,
news); everything runs through the mobile app. There is no web дверь-open either.

**Auth model, as far as anyone outside can tell:** by phone number registered in the app. There is
no client registration, no OAuth, no key to obtain — which also means no cost, and no permission.

**What the community integration `svmironov/domonap_intercom` says** (HA custom component,
installed via HACS; author states it is "никак не связано и не одобрено ООО «ДОМОНАП»"):

- Hosts: `https://api.domonap.ru` (API), `https://hls.domonap.ru` (video), `https://s3-api.domonap.ru`
  (photos, avatars).
- One endpoint is named explicitly: `client-api/CallLog/GetCallLogs` — the call history, which is
  also where the caller's `photoUrl` comes from ("получает исходный photoUrl так же, как приложение
  Domonap").
- Video: HLS at `https://hls.domonap.ru/{doorId}/index.m3u8`, and a WebRTC/WHEP session. The
  integration runs a **local WHEP proxy inside HA** — `/api/domonap/webrtc_proxy/<secret>/<camera_id>/whep`
  — which "сам обновляет токен и проксирует WebRTC-сессию", so go2rtc can connect without
  authenticating to Domonap. Note those proxy paths are Home Assistant's, not Domonap's.
- It raises a `domonap_incoming_call` event carrying door id, address and video URLs.
- Stated limitation: **"Существует ограничение на одновременное использование одного номера
  телефона в приложении Domonap и интеграции HA"** — one phone number cannot be used in the app and
  in a second client at the same time. This is the single most consequential fact for us, see below.

**The Android app** is `com.domonap.app` (RuStore listing), minimum Android 7.

## Inferred / explicitly unverified

- **How the HA integration detects a call is unknown.** The README does not say. Secondary
  write-ups claim it polls `GetCallLogs`, which fits `GetCallLogs` being the only endpoint named —
  but polling a call *log* means the call is noticed after it has started ringing, and for a
  takeover screen that latency is the whole feature. Not our path, but worth knowing if we ever
  reconsider.
- The real app almost certainly gets an FCM push for the call. We cannot subscribe to it — FCM
  goes to the app registered with Domonap's sender — but we do not need to: the notification the
  app raises in response is readable, and arrives at the same moment.
- Door release endpoint: **not documented anywhere I found.** The integration opens doors; the path
  is not published. Do not guess it.
- Token lifetime, refresh mechanism, SMS-code step: unknown.
- Legality/ToS: replaying a private mobile API is likely against the user agreement. For a resident
  controlling their own door in their own flat this is a personal risk decision, not a technical
  one. Asking Domonap support whether an API exists costs one email and is the only path that ends
  with something supportable.

### How the panel handles a call

**The Domonap app owns the call screen.** It stays installed and logged in; when someone rings, its
own full-screen UI takes over with video, accept/decline and door release. Our panel does not call
`api.domonap.ru` at all, so none of the unverified endpoints above become load-bearing, there is no
ToS question, and the one-phone-number limitation never bites — the app remains the only client.

Our side is a **notification listener**. With notification access granted, the panel sees Domonap's
incoming-call notification and can use it to yield the screen, dim, log the event, and restore the
panel afterwards; the caller's name and photo may also be readable from the notification's own
extras. Plus a launcher tile for opening the app on purpose.

This rests on one unverified assumption: **that Domonap posts a notification we can see**, rather
than only firing a full-screen intent. Partly answered since: the app has a dedicated
`telecom_incoming_channel3` at importance MAX and holds `USE_FULL_SCREEN_INTENT`, so it does both —
but the record's contents are still uncaptured. Ring the intercom once and watch:

```bash
adb shell dumpsys notification --noredact
```

If nothing shows up, the call takeover is Domonap's alone and the panel simply gets out of the way —
still a working outcome, just a blinder one.

Two caveats: notification access is a user-granted toggle in Settings plus a manifest service, both
"ask first" under AGENTS.md; and notification listeners are known to need rebinding after some
reboots, which matters on a tablet that reboots unattended.

Verified on the tablet on 2026-08-15: with `android:exported="true"` guarded by
`android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, the service resolves
(`cmd package query-services -a android.service.notification.NotificationListenerService`) and, once
the toggle is granted, the system binds it —

```
Live notification listeners:
  ComponentInfo{ru.domovoy/ru.domovoy.integrations.domonap.DomonapCallListener} (user 0)
```

The process stays up with no exception, so `onListenerConnected` and its `activeNotifications`
replay are good on this device. Rebinding after reboot is handled by `requestRebind` in
`onListenerDisconnected` and is **still untested** — it needs an actual reboot.

Rejected: our panel logging in and rendering the call itself. It would fix the orientation problem
below, but it rests entirely on an unverified private API, and if the one-session-per-number limit
is real it would force the Domonap app off the tablet.

## Orientation: the app is landscape, the tablet hangs portrait

### Diagnose before fixing — these are two different problems

1. **The tablet is rotation-locked to landscape.** Then it is a device setting and nothing to do
   with Domonap.
2. **The app declares `android:screenOrientation="landscape"`.** Then no amount of rotation-locking
   helps — the app forces the display around. **Ruled out on 2026-08-15:** the manifest contains no
   `screenOrientation` anywhere. Only case 1 remains possible.

Tell them apart: unlock auto-rotate, hold the tablet portrait, open another app (portrait, fine) and
then Domonap (still landscape → case 2). Or read the manifest: `adb shell pm path com.domonap.app`,
pull the APK, and dump `AndroidManifest.xml` with `aapt2`.

### Fixes, best first

**A. Make the display ignore orientation requests.** A display-level switch:

```bash
adb shell wm set-ignore-orientation-request true
```

(`-d 0` targets the built-in display; `adb shell wm get-ignore-orientation-request` reads it back.)
The app then gets **letterboxed** — a landscape-shaped window drawn upright inside the portrait
screen, with bars above and below. Readable while the tablet hangs vertically, which is what we
want; the app just doesn't fill the glass. Verified from the Android docs that this per-display
"ignore orientation request" behaviour is what device manufacturers use on large screens; the exact
`wm` command is from a community adb reference, so confirm it on the actual tablet.
Caveats: it affects **every** app on that display — harmless here, since our panel is portrait by
design — and it very likely does **not survive a reboot**, which AGENTS.md says will happen
unattended. Re-applying it needs adb-over-TCP at boot or a rooted ROM.

**B. Per-app compat override.** Android documents exactly two relevant ones:

```bash
adb shell am compat enable OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION com.domonap.app
adb shell am compat enable OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED com.domonap.app
```

Verified from the Android docs, including that "the commands only temporarily apply or remove the
override" — so this too resets. Widely reported (not in the docs) to be refused for non-debuggable
release apps on retail builds. Try it, but expect A to be the one that works.

**C. Wait for the platform, or check the tablet's own settings.** Verified from the Android docs:
Android 16 (API 36) already ignores orientation restrictions by default on displays with smallest
width ≥ 600dp, and Android 17 (API 37) ignores `android:screenOrientation`, `resizableActivity`,
`minAspectRatio`, `maxAspectRatio`, `setRequestedOrientation()` and `getRequestedOrientation()`
outright on sw > 600dp — but **only for apps targeting that SDK**. Domonap in fact targets SDK 36
and this display is sw ≈ 753 dp, so both conditions are already met on the *app* side — what is
missing is the platform: the tablet runs Android 13. This becomes a free fix the day the tablet is
upgraded, and is not one today. What *is* worth two minutes: many large-screen
builds expose a per-app **aspect ratio / full screen** item under Settings → Apps → Domonap. If this
tablet has it, that is fix A with no adb at all.

**D. Rotation lock, for case 1 only.** `settings put system accelerometer_rotation 0` then
`settings put system user_rotation 0` (0 = portrait). Second-hand, and useless if the app forces
landscape.

**Recommendation:** try the per-app aspect-ratio setting first, then A, and accept letterboxed
landscape for the duration of a call. Rendering the call ourselves would sidestep this entirely,
and is rejected above for reasons that have nothing to do with layout.

## Open questions

- **What the incoming-call notification actually contains** — extras, caller field, full-screen
  intent, category, tag. The channel exists; the record has not been captured. Everything the
  listener keys on depends on this, and it needs the intercom rung once.
- Is the notification or the Telecom call state the earlier / more reliable signal? Both exist now;
  neither has been timed.
- Does the call screen actually come up portrait on this tablet? The app no longer looks like the
  reason it would not — only the tablet's own rotation lock is left to check.
- Does Domonap have any official/partner API? (One email to support.)

## Sources

- [svmironov/domonap_intercom](https://github.com/svmironov/domonap_intercom) — unofficial HA integration
- [Domonap](https://domonap.ru/) — service description
- [Domonap in RuStore](https://www.rustore.ru/catalog/app/com.domonap.app) — package name, min Android
- [Device compatibility mode](https://developer.android.com/guide/practices/device-compatibility-mode) — compat overrides
- [Android 17: restrictions on orientation and resizability are ignored](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored)
- [Behavior changes: apps targeting Android 16+](https://developer.android.com/about/versions/16/behavior-changes-16)

## Recorded responses

No HTTP response — the panel calls nothing. What has been read off the device is in
[Recorded on the tablet](#recorded-on-the-tablet-2026-08-15). The incoming-call notification record
is still to be captured.
