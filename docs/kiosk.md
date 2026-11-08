# Protected wall panel

The app supports a Device Owner kiosk on Android 11/API 30 or newer. The normal panel still
supports Android 8/API 26; older versions can open the allowed apps, but cannot enable the kiosk. Installing an APK alone does not enable the kiosk or enrol an owner.

## Behaviour

- **Защита** in the fixed header opens the allowed apps and protection controls. Enabling protection
  and leaving the panel both require a fresh screen-lock PIN/password confirmation; fingerprint
  authentication is not offered for these actions.
- In kiosk mode, Home returns to the panel, Recents is unavailable, and other apps cannot launch.
  The panel is the persistent Home app, so Android launches it after reboot and device unlock.
- Domonap, Mi Home, ChatGPT, Gemini, Samsung Camera, Amnezia VPN Family and Samsung Gallery
  are permanently allowed while protection is enabled. They open without authentication from
  **Защита**; returning Home, process restart and calls preserve the same package allowlist.
  Google is also allowed because Gemini runs inside its package on this tablet; this permits the
  entire Google app, not just the Gemini screen. Other applications remain blocked. Samsung
  package names and the installed Family VPN variant were checked on the physical tablet; this list does not discover or allow arbitrary apps.
- Domonap is included in that permanent allowlist. Its notifications remain available
  without PIN, including entry through incoming/ongoing call notifications. This is a package-wide
  exception, not an activity-level security boundary.
- A detected call closes protection controls and cancels pending authentication. The listener still
  only observes notifications: it never sends Domonap's full-screen PendingIntent, opens its screen,
  changes its notification, or changes audio. Panel polling keeps its existing call behaviour.
- Cancelling authentication, a late callback, a call, or a destroyed/background panel cannot grant
  access. No screen-lock credential is stored by the app; Android verifies credentials and limits
  failed attempts. A device screen-lock credential is required even when a fingerprint is enrolled.
- Authenticated **Выйти из панели** stops lock task, clears the managed Home preference and package
  allowlist, persists protection as disabled, and opens Android settings. The app remains Device
  Owner. Re-enable protection from the panel when maintenance is complete.

Notifications, the notification shade, the screen lock and power menu intentionally remain enabled.
Notification actions are subject to Android Lock Task restrictions: see the physical checks below.
This is an Android application kiosk, not protection against someone authorised for ADB or recovery.

## One-time enrolment

Use the repo-local toolchain and an explicitly selected tablet. Before changing ownership, inspect
existing owners and preserve accounts and app data. Do not reset a configured tablet automatically.
ADB enrolment may refuse devices with existing accounts or users, or devices already managed by
another owner. If it refuses, stop and assess provisioning separately; a factory reset erases data.

```bash
source scripts/env.sh
adb devices
# Inspect device policy locally; its output can contain private device information.
adb shell dumpsys device_policy
# Explicit setup step, after checking the target and provisioning eligibility:
adb shell dpm set-device-owner ru.domovoy/.kiosk.PanelDeviceAdmin
```

On the tablet, configure a secure screen-lock PIN/password. Grant
Domovoy Hub notification-listener access in Android settings, and keep Domonap installed, logged in,
and allowed to notify. Open **Защита → Включить защиту**, then authenticate. Without the owner,
credential, notification access or supported Android version, the UI explains the missing setup and
will not silently substitute screen pinning. Existing credentials are never removed or changed.

The HOME declaration makes the panel selectable as a launcher; the persistent managed preference
is applied only when protection is enabled. Exit clears that managed preference. Android may still
ask which launcher to use if the user had independently chosen the panel as their default launcher.

## Recovery and validation

Keep the authorised ADB connection available during the first test. If another allowed app is in
front, Home returns to the panel; the panel can also be brought forward explicitly:

```bash
source scripts/env.sh
adb shell am start -n ru.domovoy/.MainActivity
```

Then use **Защита → Выйти из панели** and authenticate. A release APK's owner is not necessarily
removable with `dpm remove-active-admin`; Android restricts that debugging command to test admins.
Do not rely on uninstalling or clearing app data as a kiosk recovery procedure.

Before leaving the wall tablet unattended, verify on the actual tablet:

1. Enable protection with PIN. Cancel/failed checks must keep the panel protected. Check every
   allowed app from the app list; none should ask for PIN.
2. Check Home, Recents, Back, notification settings links and vendor links. Home must return to the
   panel; the allowed apps must remain available without authentication after that return.
3. Reboot, unlock with the device PIN and verify the panel resumes in lock task. Android may require
   the primary credential after reboot; unattended bypass of the device screen lock is not provided.
4. Place a real Domonap call with the panel visible and again during an authentication prompt. Check
   ringing/banner, answer, video, door release through Domonap, decline/end and return to the panel.
   **This is essential:** Android documents restrictions on notification actions in lock task, and
   keeping the package and notifications allowed alone does not prove that all call actions work.
   Do not enable unattended kiosk use until this passes. Do not force-launch the full-screen intent
   as a workaround; it previously interrupted a physical call (see [domonap.md](domonap.md)).
5. Authenticate the exit, open other apps/settings, then enable protection again.

Automated tests cover policy/authorization behaviour with Robolectric and the Compose protection
controls. They do not prove Samsung system UI, fingerprint hardware, reboot or real-call behaviour.

## Physical tablet status on 2026-11-08

The connected Samsung SM-T875 runs Android 13/API 33. All seven requested apps are installed.
Notification-listener access is granted. Device Owner is not configured and lock task is inactive.
Existing accounts are present: do not remove them or reset the tablet as part of enrolment.
The expanded policy has not yet been activated on this tablet, and a real Domonap call in lock
task remains unverified. Launching Gemini's entry activity (`com.google.android.apps.bard`)
on the tablet opened `com.google.android.googlequicksearchbox`. Its package is
allowed as a package-wide dependency with the user's explicit approval.
Android 13+ shortcuts use `PackageManager.getLaunchIntentSenderForPackage` when a package is
not query-visible. The seven launcher packages are declared explicitly for earlier Android
versions as well; the five additions were authorised by the user.
See the [Android API reference](https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentSenderForPackage(java.lang.String)).

The updated current working-tree build was installed on this Samsung with `adb install -r`,
preserving app data. All seven app buttons were exercised from the new menu without entering
PIN; Gemini opened the Google package. The panel was returned to the foreground and `dpm
list-owners` still reported no owner. This checks app launching in ordinary mode, not lock-task
confinement or a real call. Scoped source changes were merged in
[PR #36](https://github.com/yeahrmek/domovoy-hub/pull/36); the installed working-tree build also
contains parallel local UI changes excluded from that PR. The JVM error-state captures are kept
in `app/build/outputs/kiosk/` as disposable build evidence and can be regenerated by recording
`KioskSettingsTest`.

## Full owner removal verified on 2026-11-08

**Снять управление планшетом** is separate from ordinary kiosk exit and is offered whenever
this app is Device Owner, including after protection has already been disabled. It requires a
fresh Android screen-lock credential. Cancellation, an incorrect PIN and a late result after
a call do not remove ownership.

After successful authentication the panel stops lock task, clears the managed Home override,
package allowlist, lock-task features and all user restrictions attributed to its administrator.
It persists protection as disabled before calling `clearDeviceOwnerApp`. Android finishes disabling
the active administrator asynchronously. There is no factory-reset, app-data clearing or PIN-changing
call. The API is deprecated and best-effort; see the
[Android reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#clearDeviceOwnerApp(java.lang.String)).

Android 13 can retain the panel as its ordinary HOME role holder even after the managed Home
policy and ownership are gone. The app therefore opens **Android Home settings** after removal:
choose the ordinary launcher there. This is a normal default-app choice, not a remaining Device
Owner restriction. Directly clearing the app's preferred activities does not reliably remove this
role. Notifications access and ordinary app permissions also remain ordinary user-controlled grants.

A new offline Android 13/API 33 ARM64 emulator passed the native flow with the real system PIN
screen and a regular admin (`testOnlyAdmin=false`), without `dpm remove-active-admin` or a wipe:

- PIN activation entered actual `LOCKED` mode; Android blocked Settings.
- Wrong PIN and cancellation preserved both ownership and `LOCKED` mode.
- Correct PIN removed the owner and, after the ordered broadcast completed, the active admin.
- Lock task became `NONE`, its package list was empty, and base/global/local/effective user
  restrictions were empty. The device was no longer managed; Backup Manager was activated again.
- Android's Home picker allowed Pixel Launcher to be selected. Settings, Home and Recents were
  available. The ordinary Home choice and unmanaged state survived a real emulator reboot.
- Android ID, a shared-storage sentinel and a private app-data marker were unchanged; the app
  remained installed, the original PIN still verified, and the persisted protection flag stayed false.

The isolated change passed the full `test`, `ktlintCheck`, Android `lint` and `assembleDebug`
checks. The separate removal action was also rendered and inspected with Compose/Robolectric.
The emulator APK has empty vendor seed fields. The physical Samsung was not modified or tested
with this feature; no Device Owner was enrolled on it.

Reproduction uses [the empty-credential build init script](../scripts/kiosk-emulator.init.gradle)
and [the emulator-only native verification runner](../scripts/verify-kiosk-owner-removal.py).
The runner refuses physical device serials, requires an offline emulator and a pre-enrolment
baseline, and uses only the emulator's disposable test PIN. It records native UI captures and
policy dumps; `--verify-after-reboot` resumes the final assertions if the system UI was briefly
unavailable during boot.

Evidence is retained at `/private/tmp/domovoy-kiosk/evidence/` (result JSON, before/after policy
and user dumps, screenshots and baseline). It may be removed after review. Disposable AVD,
isolated source worktree and build intermediates are removed after validation; installed system
images and shared toolchain caches are preserved.

## Historical verification on 2026-11-04

The checks below covered the earlier panel/Domonap-only baseline and authenticated Xiaomi
sessions. They do not verify the current expanded allowlist.

An isolated Android 13/API 33 ARM64 emulator, with networking disabled, passed actual system PIN
activation, authenticated exit to settings, cancellation, Home return, blocked Recents/settings,
and automatic kiosk restoration after reboot and initial PIN unlock. A disposable Xiaomi fixture
was allowed only after authentication; its attempt to open settings inside its own task was blocked,
and Home removed its temporary package permission. Native light/dark and narrow-screen captures
at font scale 1.3 were inspected. The added protection UI passed an independent visual review.
A separate disposable Domonap fixture posted the verified incoming-call channel while native PIN
confirmation was open: the actual notification listener cancelled that prompt, lock task stayed
active, the allowed fixture opened without PIN, and returning/ending the notification preserved the
baseline allowlist. This tests panel integration, not a real intercom call or its notification actions.

All 662 unit tests, `ktlintCheck`, Android `lint` and `assembleDebug` passed. The physical Samsung
was not connected; fingerprint hardware and a real Domonap call remain unverified.

## Android references

- [Lock Task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)
- [Persistent Home and provisioning](https://developer.android.com/work/dpc/dedicated-devices/cookbook)
- [BiometricPrompt](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.Builder)
