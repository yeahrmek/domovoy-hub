# Domonap (intercom / домофон)

Read from public sources on 2026-08-15. The tablet itself was inspected the same day, and a real
incoming call was captured on 2026-08-16 — see [Recorded on the tablet](#recorded-on-the-tablet-2026-08-15)
and [a real incoming call](#recorded-on-the-tablet--a-real-incoming-call-2026-08-16), which supersede
the guesses further down wherever they disagree. **No Domonap endpoint has been called.** Everything
below about the *backend* comes from a third-party Home Assistant integration, not from Domonap. Do
not invent endpoints on top of it.

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

`showWhenLocked` + `turnScreenOn` were read here as meaning **the platform already puts this screen
on top and wakes the tablet**. **Half of that is wrong, and the capture of 2026-08-16 says so:** the
tablet is woken (by the app's own `SCREEN_BRIGHT_WAKE_LOCK`, not by this activity), but the activity
**never launches** — not when awake, not over the keyguard. These manifest flags say what the screen
*may* do once started; they were never evidence that anything starts it. See
[the call screen did not come up](#the-call-screen-did-not-come-up--in-either-state).

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

## Recorded on the tablet — a real incoming call (2026-08-16)

**The intercom was rung once at 22:02 and the whole event captured.** This supersedes the "still
missing" note that stood here: the notification record is no longer a guess. Method: three
independent pollers against the tablet — `dumpsys notification --noredact` every ~200 ms,
`dumpsys telecom` every ~100 ms, screen/lock state every 1 s — plus `adb logcat -v threadtime`,
all armed before the ring. Device clock ran 262 ms ahead of the capture host; **all times below are
the tablet's own**, taken from `when=`/`mCreationTimeMs` and from logcat, not from the poller.

Conditions, because they turn out to decide the outcome: the tablet was **awake and unlocked**
(`mWakefulness=Awake`, `isKeyguardShowing=false`) for the whole call, and the foreground app was the
Samsung launcher — not our panel. Notification access had been granted at 22:00:30 and the listener
was bound.

| device time | event |
|---|---|
| 22:02:22.656 | `ActivityManager: Background started FGS: Allowed` for `com.domonap.app` |
| 22:02:22.668 | notification **id=200** posted on `telecom_incoming_channel3`, `flags=0x42` (ONGOING\|FOREGROUND_SERVICE) — **extras empty**: `android.title=null`, `android.text=null`, no actions, `fullscreenIntent=null` |
| 22:02:22.693 | **same key updated** — `flags=0xc2`, CallStyle, 3 actions, `fullscreenIntent` present |
| 22:02:22.702 | `NotificationManager: com.domonap.app: notify(200, null, …)` |
| 22:02:22.71–23.00 | the app starts the ringtone **itself**, `MediaPlayer` on the *system default* ringtone (`Over the Horizon`), `setVolume(1.0, 1.0)` |
| 22:02:23.047 | SystemUI `mHeadsUpShowing: false -> true` — shown as a **heads-up banner** |
| 22:02:23.554 | Telecom `CREATED (com.domonap.app;null, INCOMING, false)` |
| 22:02:23.575, .580 | two further `notify(200)`, flags settle at `0xe2` |
| 22:02:23.579 | Telecom `SET_RINGING (successful incoming call)` |
| 22:02:23.665 | `PHONE_STATE` broadcast; keyguard sees `IDLE => RINGING` |
| 22:02:35.500, .511 | `notify(1002)` on `telecom_missed_channel3` |
| 22:02:35.520 | Telecom `SET_DISCONNECTED`, cause **`REJECTED`** |
| 22:02:35.525 | FGS count → 0; id=200 gone, **no explicit `cancel(200)` logged** |

**The notification arrives first, by 886 ms.** Posted 22:02:22.668, Telecom `CREATED` 22:02:23.554
(`SET_RINGING` 911 ms after the post). The notification is the earlier signal on this device, and by
a margin far larger than any measurement error here.

### The record

```
key=0|com.domonap.app|200|null|10130   id=200  tag=null  importance=5 (mImportance=HIGH)
channel=telecom_incoming_channel3      category=call     vis=PUBLIC   actions=3
flags: 0x42 → 0xc2 → 0xe2   (originalFlags=0xc2; the system adds NO_CLEAR)
fullscreenIntent=PendingIntent{… com.domonap.app startActivity …}
contentIntent=null   deleteIntent=null   contentView=null   sound=null   vibrate=null
extras:
  android.title            = <дом/подъезд/этаж/квартиры>   ← the DOOR, not a person
  android.text             = "Входящий вызов"              ← constant
  android.template         = android.app.Notification$CallStyle
  androidx.core.app.extra.COMPAT_TEMPLATE = androidx.core.app.NotificationCompat$CallStyle
  android.callType         = 1        android.callIsVideo = false
  android.callPerson       = Person (opaque in dumpsys — prints as android.app.Person@…)
  android.answerIntent     = PendingIntent (startActivity)
  android.declineIntent    = PendingIntent (broadcastIntent)
  android.hangUpIntent     = null     android.largeIcon = null   android.subText = null
actions:
  [0] "Отклонить"     -> broadcastIntent
  [1] "Открыть дверь" -> startActivity
  [2] "Ответить"      -> startActivity
```

`android.title` carries the **door address — building, entrance, floor and the flat range** — which
AGENTS.md forbids committing, so only its shape is recorded here. It is not a caller name: nothing
in the record names a person, and `android.callPerson` is a `Person` that `dumpsys` prints only as
an object hash, so **whether it carries a name or photo is still unconfirmed** and needs the
listener to read it, not `dumpsys`.

Note `android.callIsVideo=false` on a video intercom, and `handle=tel:…` — Telecom carries a phone
number (redacted here and partly redacted by `dumpsys` itself). The `Открыть дверь` action means
**door release is reachable straight from the notification**. The panel does not use it and this
note is not a proposal that it should.

### Updated in place, never replaced

Four posts, one key: `0|com.domonap.app|200|null|10130` throughout, `tag=null`, `id=200`. The
ringing notification is **updated (same key)**, and the answered case did not arise (below). The
first post — the one the `phoneCall` foreground service attaches — is on the incoming channel but
**carries no extras at all**; the populated update follows **25 ms** later. Anything that reads
`android.title`/`android.text` on the first `onNotificationPosted` gets nulls.

The call ending posts a **separate** notification: `id=1002`, key
`0|com.domonap.app|1002|null|10130`, channel `telecom_missed_channel3`, `flags=0x10` (AUTO_CANCEL),
`vis=PRIVATE`, **no `category`**, `fullscreenIntent=null`, `android.title="Уведомление"`,
`android.text="Пропущенный звонок от <door>"`.

**Channels never seen in this capture:** `telecom_ongoing_channel3` and `telecom_silent_channel3` —
neither was posted to at any point. The silent-channel worry recorded earlier did not materialise
here, and the ongoing channel remains untested because the call was never answered on the tablet.

### The call screen did not come up — in either state

**`IncomingDomofonCallActivity` never launched.** Zero occurrences across both captures' logcat: no
`ActivityTaskManager` start, no window, no `Displayed`. The full-screen intent sits on the record
and is *not used*. This was checked in both of the states that could plausibly differ:

| | call 1, 22:02 | call 2, 22:23 |
|---|---|---|
| screen at ring | **Awake, unlocked** | **Dozing, keyguard showing** |
| `IncomingDomofonCallActivity` | never launched | never launched |
| `mKeyguardOccluded` | n/a (unlocked) | **`false` throughout** |
| how it surfaced | heads-up banner over the launcher | screen woken, notification over the lock screen |
| ended by | answered on another phone | **rang out unanswered, ~18.3 s** |

The awake case could be explained away as ordinary platform behaviour — Android prefers a heads-up
over firing a full-screen intent when the device is awake and unlocked. **The locked case removes
that explanation.** A dozing, keyguarded tablet is exactly the condition under which the platform
*does* launch a full-screen intent, and it still did not: `mKeyguardOccluded` stayed `false` for the
whole ring, and no full-screen-intent launch appears anywhere in the log.

**What Domonap does instead is wake the screen and leave the notification on the lock screen:**

```
22:23:44.624  PowerManagerService: acquire WakeLock SCREEN_BRIGHT_WAKE_LOCK 'app:call'
                                   ACQUIRE_CAUSES_WAKEUP (uid=10130)
22:23:45.127  PowerManagerService: acquire WakeLock SCREEN_BRIGHT_WAKE_LOCK 'EDGELIGHTING:app:call'
22:23:45.127  PowerGroup: Waking up power group from Dozing (reason=application,
                          details=EDGELIGHTING:app:call)
22:23:45.133  WindowManager: Started waking up... (why=ON_BECAUSE_OF_APPLICATION)
```

So the tablet does light up — but by the **app's own wake lock**, not by `turnScreenOn` on an
activity that never starts. The keyguard is never occluded, so **the call screen is reached only by
tapping the notification** (`android.answerIntent` is a `startActivity`).

This overturns the reading recorded on 2026-08-15 that "the platform already puts this screen on top
and wakes the tablet". The waking half is right; **the putting-on-top half is wrong**. Whatever the
panel is showing stays on screen, with a notification over it.

### Second capture — locked and unanswered (2026-08-16, 22:23)

Same structure as the first call, which is the point: it reproduces.

- Same key `0|com.domonap.app|200|null|10130`, same flag progression `0x42 → 0xc2 → 0xe2`, same
  three `notify(200)` calls. **Updated in place, twice out of twice.**
- **The notification still arrives first, but the margin is not stable:** `notify(200)` at
  22:23:44.630 against Telecom `CREATED` at 22:23:44.805 — **175 ms**, against 886 ms on the first
  call. The ordering held both times; the size of the lead should not be relied on.
- **Ring-out is ~18.3 s** (22:23:44.6 → `SET_DISCONNECTED` 22:24:02.973) with nobody answering.
- The missed-call notification is posted on the **same key** as the previous call's
  (`0|com.domonap.app|1002|null|10130`) — after two calls there is still exactly **one** record.
  Missed calls do not accumulate; the newest replaces the last.
- Samsung's `NotificationService` logs `Category call notification, so make not work edgelighting`
  while still taking an `EDGELIGHTING:app:call` wake lock — noted only because it explains the
  wake-lock name; nothing here depends on it.
- The screen returned to `Dozing` on its own after the call.

### Sending the full-screen intent ourselves — tried, and withdrawn (2026-08-16, 22:41)

Because the platform does not launch the call screen, the panel was made to: on the first post of a
ringing call carrying a `fullScreenIntent`, `DomonapCallListener` sent that intent and nothing else
— never `android.answerIntent`, never `Открыть дверь`. Built, installed on the tablet and rung once,
screen locked.

**It worked, and it killed the call.**

| device time | event |
|---|---|
| 22:41:39.981 | Domonap posts `notify(200)` |
| **22:41:40.200** | `START u0 … IncomingDomofonCallActivity … from uid 10130` — our send |
| 22:41:40.244 | Telecom `CREATED` — **44 ms after we had already launched the screen** |
| 22:41:40.325 | `SET_RINGING` |
| 22:41:40.519 | call screen resumed, `mKeyguardOccluded=true`, rendering, `keepScreenOn=true` |
| **22:41:40.616** | `SET_DISCONNECTED`, cause **`LOCAL`** |
| 22:41:52.9 | the screen was dismissed by hand — 12 s *after* the call was already dead |

The mechanical part succeeded: the activity launched **over the keyguard**, exactly once, and the
background-activity-start was allowed because a `PendingIntent` runs as its creator (`uid 10130`,
Domonap). No send failure was logged.

Then the call ended 416 ms later with `LOCAL` — the local side hanging up. No one had touched the
tablet: the only input lines in that window are the touchscreen powering on with the display.

**An A/B the same evening confirms the association.** The launch was removed, the reverted build
installed, and the intercom rung again under the same locked-screen conditions:

| | with the launch, 22:41 | reverted, 22:49 | baselines, 22:02 and 22:23 |
|---|---|---|---|
| call screen launched | yes, once | **no** | no |
| disconnect cause | **`LOCAL`** | `REJECTED` | `REJECTED`, `REJECTED` |
| time to disconnect | **0.42 s** | 4.6 s | 12.5 s, 18.3 s |

`LOCAL` appears in exactly the run that launched the screen, and in none of the three that did not.
One trial per condition, so this is a strong association and not a proven mechanism.

**The likely mechanism, untested:** we launch off the *notification*, which the earlier captures
measured as arriving **175–886 ms ahead of the Telecom call** — here 44 ms ahead of `CREATED`. So
the screen came up before the call it belongs to existed, and Domonap, finding no call session,
hung up cleanly. If that is right, the fix is to trigger on the Telecom call rather than the
notification, or to wait for the call to register. Both are guesses until watched.

The `fullscreenIntent`/`android.answerIntent` shared `PendingIntentRecord` (`d42d046`) is *not*
implicated: the call went `LOCAL`-disconnected, never `SET_ACTIVE`, so nothing answered it.

**Where this leaves the panel:** the listener is read-only again, exactly as before. `DomonapCalls`
still works out when a call screen *would* go up — once per call, on the post that carries the
intent, ringing channel only, all of it recorded above and unit-tested — and nothing acts on it.
A panel that shows nothing while the intercom rings is a poor wall panel; one that hangs up on
whoever is at the door is a broken intercom, and that trade is not close.

### What the panel did during the captures — unanswered, and why

Nothing observable. The listener bound at 22:00:30 and stayed bound (no disconnect, no rebind), but
**the panel emits no log line on this path**, and its process logged nothing during the call. The
panel was also not in the foreground — the launcher was — so there was no yield to see and no tab to
come back to. Whether polling paused and whether the panel restores its tab are **still
unconfirmed**; this run could not answer them, and confirming them needs either logging on that path
or a call raised while the panel is the foreground app.

### `REJECTED` says nothing about what happened

The first call was answered on another household phone; the second rang out with nobody answering.
**Both produced `DisconnectCause: REJECTED`** on the tablet, and both posted a missed-call
notification. So answered-elsewhere, declined-here and nobody-answered are **indistinguishable** on
this evidence. Nothing should be built on the disconnect cause, and "missed" on this panel does not
mean the door went unanswered.

**What the panel keys on** (`DomonapCalls`): package `com.domonap.app` plus channel id
`telecom_incoming_channel3` or `telecom_ongoing_channel3`. The capture says this is right for
detecting the ring — the very first post is already on `telecom_incoming_channel3` — and that the
missed-call notification correctly does not trigger it. The ongoing-channel branch stays untested.

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

**The Domonap app owns the call screen.** It stays installed and logged in. Our panel does not call
`api.domonap.ru` at all, so none of the unverified endpoints above become load-bearing, there is no
ToS question, and the one-phone-number limitation never bites — the app remains the only client.

**Corrected by the 2026-08-16 capture:** "its own full-screen UI takes over" is not what happens on
an awake, unlocked tablet. The call screen did not launch at all; the call arrived as a heads-up
banner over the foreground app, with the full-screen intent present on the notification and unused.
Whether it takes over when the tablet is locked is still unverified. See
[a real incoming call](#recorded-on-the-tablet--a-real-incoming-call-2026-08-16).

Our side is a **notification listener**. With notification access granted, the panel sees Domonap's
incoming-call notification and can use it to yield the screen, dim, log the event, and restore the
panel afterwards. Plus a launcher tile for opening the app on purpose.

What the extras actually carry, now that they are recorded: the **door** (building, entrance, floor,
flat range) in `android.title`, a constant `"Входящий вызов"` in `android.text`, and an
`android.callPerson` whose contents `dumpsys` will not show. There is **no caller name** in the
record; a photo is unconfirmed.

### The launcher tile (shipped)

"Домофон", in the **Коридор** — the intercom is answered at the front door, which is the room the
panel hangs in, so it is the first section on the wall. It opens `com.domonap.app` and nothing
else: no `api.domonap.ru` call, no endpoint from the unverified list above becomes load-bearing.

The tile is the *deliberate* direction — looking at the call log, or letting someone in before they
ring. It is not the call path: the takeover is Domonap's own screen, arrives on its own and is
untouched by this.

It shows **no state and no age**, unlike every other tile on the panel. Nothing polls it, nothing
about the intercom is read on it, so there is nothing to be stale; the line under the name says
`opens the app · no state to read`. If the app is not installed the tile refuses the tap and says
`not installed · com.domonap.app` instead of swallowing it.

The package is verified — read off the tablet, above. The manifest declares
`<queries><package android:name="com.domonap.app" /></queries>`: targeting API 30+, a package we do
not name is invisible to `getLaunchIntentForPackage` and the tile would read "not installed" on a
tablet that plainly has the app.

**Verified end to end on the tablet, 2026-08-15.** `versionCode=9850` unchanged, the launcher
intent resolves to `com.domonap.app/.ui.main.MainActivity`, the panel renders the tile in the
Коридор section after that room's lights —

```
Коридор
Споты в коридоре      on · never read
Трек в коридоре       on · never read
Акара в коридоре      on · 91 d ago
Домофон               opens the app · no state to read
```

— and tapping it brings Domonap to the foreground.

What came up was `com.domonap.authorization.AuthorizationActivity` — because the account was signed
out by hand at
the time of the check, not because a session had expired. **The app stays authorized on this
tablet**, which is what the call path above assumes.

One property that launch happens to demonstrate, and it is a real one: a Domonap that is not signed
in posts no incoming-call notification, and the panel cannot tell that apart from an intercom
nobody has rung. That is inherent to keying on notifications rather than anything to fix here — it
is only worth knowing when the takeover is next tested and nothing happens.

This rested on one unverified assumption: **that Domonap posts a notification we can see**, rather
than only firing a full-screen intent. **Answered on 2026-08-16: it posts one, and it is the earlier
signal** — on `telecom_incoming_channel3`, 886 ms before Telecom registers the call. The full-screen
intent is on the record but went unused on an awake tablet. The assumption the call path rests on
holds.

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
replay are good on this device.

**Reboot, measured on 2026-08-15 — the listener comes back on its own, but only after unlock.**

| time | state |
|---|---|
| 16:48:42 | `adb reboot` |
| 16:50:13 | `sys.boot_completed=1` |
| still locked | `strongAuthRequired=0x1` (required after boot), our process **not running**, listener **not bound**, grant intact |
| 16:50:51 | tablet unlocked by hand |
| ≤ 7 s later | process started and listener **bound**, no app launch, no user action beyond the unlock |

So nobody has to open the panel after a reboot — but **the credential lock screen gates it**. This
tablet requires a PIN, so between an unattended reboot and someone physically unlocking it, the
intercom takeover is dead: no listener, no yielding, and the call screen is Domonap's problem alone.
For a panel whose whole premise is that the tablet "reboots unattended", that is the gap to close.

Two candidate fixes, **neither verified**: drop the credential lock so storage unlocks at boot (a
security decision, not a technical one), or try `android:directBootAware="true"` on the listener —
Domonap's own `TelephonyService` sets it. Whether the framework will bind a notification listener
before user unlock at all is unknown and needs testing before anyone relies on it.

What the reboot did *not* exercise is `requestRebind` itself: the process died with the reboot, so
`onListenerDisconnected` never ran. That path was seen once, when notification access was revoked
by hand — the listener disconnected and correctly stayed down, `requestRebind` not being able to
override a withdrawn grant, which is the behaviour we want.

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

Closed by the two captures of 2026-08-16: what the notification contains; that it is updated in
place, same key, both times; that it is the earlier signal, both times; that the call screen does
**not** take over, awake or locked; and that the disconnect cause is uninformative. Still open:

- **The answered-on-the-tablet path.** `telecom_ongoing_channel3` was never posted to in either
  capture — one call was answered elsewhere, the other rang out. Whether that channel is used at
  all, and what the notification does on answer, needs someone to tap `Ответить` **on the tablet**.
- **What `android.callPerson` carries.** `dumpsys` prints it as an object hash. Reading it needs the
  listener, not adb — and it is the only place a caller name or photo could still be hiding.
- **Does the panel yield?** Not observable in either run: the panel emits no log line on this path,
  and it was not the foreground app. Answering it needs logging on that path, or a call raised while
  the panel is foreground. Note the panel is **not** the HOME app on this tablet (`Role: ru.domovoy
  not qualified for android.app.role.HOME`), so there is nothing that returns to it automatically —
  including after the call screen the panel now opens is dismissed.
- **Why does launching the call screen ourselves end the call?** It does — measured, and A/B'd
  against a reverted build the same evening. The timing hypothesis (the screen goes up before
  Telecom has registered the call) is untested. Until somebody tests it, the panel does not launch
  anything; see [tried, and withdrawn](#sending-the-full-screen-intent-ourselves--tried-and-withdrawn-2026-08-16-2241).
- **Is there any signal that fires *after* the call is registered?** Telecom's own call state is the
  obvious candidate and the panel cannot see it without becoming an `InCallService`, which is a much
  larger commitment than a notification listener. Worth pricing before another attempt.
- Does the call screen come up portrait? Still untested — it has never come up. It can be reached by
  tapping the notification, which would answer both this and the orientation section below.
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
[Recorded on the tablet](#recorded-on-the-tablet-2026-08-15), and the incoming-call notification
record — captured 2026-08-16 — is in
[a real incoming call](#recorded-on-the-tablet--a-real-incoming-call-2026-08-16). The door address
and the phone handle it contains are redacted there, per AGENTS.md.
