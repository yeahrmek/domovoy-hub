# Domonap (intercom / домофон)

Read from public sources on 2026-08-15. **Nothing has been called, and nothing has been tried on the
tablet.** Everything below about the backend comes from a third-party Home Assistant integration,
not from Domonap. Do not invent endpoints on top of it.

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
than only firing a full-screen intent. Ring the intercom once and watch:

```bash
adb shell dumpsys notification --noredact
```

If nothing shows up, the call takeover is Domonap's alone and the panel simply gets out of the way —
still a working outcome, just a blinder one.

Two caveats: notification access is a user-granted toggle in Settings plus a manifest service, both
"ask first" under AGENTS.md; and notification listeners are known to need rebinding after some
reboots, which matters on a tablet that reboots unattended.

Rejected: our panel logging in and rendering the call itself. It would fix the orientation problem
below, but it rests entirely on an unverified private API, and if the one-session-per-number limit
is real it would force the Domonap app off the tablet.

## Orientation: the app is landscape, the tablet hangs portrait

### Diagnose before fixing — these are two different problems

1. **The tablet is rotation-locked to landscape.** Then it is a device setting and nothing to do
   with Domonap.
2. **The app declares `android:screenOrientation="landscape"`.** Then no amount of rotation-locking
   helps — the app forces the display around.

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
outright on sw > 600dp — but **only for apps targeting that SDK**, and Domonap supports Android 7,
so it targets something much lower. Not a fix today. What *is* worth two minutes: many large-screen
builds expose a per-app **aspect ratio / full screen** item under Settings → Apps → Domonap. If this
tablet has it, that is fix A with no adb at all.

**D. Rotation lock, for case 1 only.** `settings put system accelerometer_rotation 0` then
`settings put system user_rotation 0` (0 = portrait). Second-hand, and useless if the app forces
landscape.

**Recommendation:** try the per-app aspect-ratio setting first, then A, and accept letterboxed
landscape for the duration of a call. Rendering the call ourselves would sidestep this entirely,
and is rejected above for reasons that have nothing to do with layout.

## Open questions

- Does an incoming call post a notification the panel can see, and how early? Everything else here
  depends on it.
- Can the landscape call screen be letterboxed on this tablet, and does the fix survive a reboot?
- Does Domonap have any official/partner API? (One email to support.)

## Sources

- [svmironov/domonap_intercom](https://github.com/svmironov/domonap_intercom) — unofficial HA integration
- [Domonap](https://domonap.ru/) — service description
- [Domonap in RuStore](https://www.rustore.ru/catalog/app/com.domonap.app) — package name, min Android
- [Device compatibility mode](https://developer.android.com/guide/practices/device-compatibility-mode) — compat overrides
- [Android 17: restrictions on orientation and resizability are ignored](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored)
- [Behavior changes: apps targeting Android 16+](https://developer.android.com/about/versions/16/behavior-changes-16)

## Recorded responses

_None yet._
