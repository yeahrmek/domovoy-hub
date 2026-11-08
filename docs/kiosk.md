# Protected wall panel

The app supports a Device Owner kiosk on Android 11/API 30 or newer. The normal panel still
supports Android 8/API 26; older versions can use system PIN confirmation for manual app launches,
but cannot enable the kiosk. Installing an APK alone does not enable the kiosk or enrol an owner.

## Behaviour

- **Защита** in the fixed header opens the protection controls. Enabling protection and leaving the
  panel both require a fresh system fingerprint or screen-lock PIN/password confirmation.
- In kiosk mode, Home returns to the panel, Recents is unavailable, and other apps cannot launch.
  The panel is the persistent Home app, so Android launches it after reboot and device unlock.
- Xiaomi is temporarily allowed after authentication. Returning Home revokes that permission;
  restarting the panel also revokes a previous session. Each subsequent manual launch requires a
  new authentication, including the panel's Domonap shortcut.
- Domonap is permanently allowed while the kiosk is enabled. Its notifications remain available
  without PIN, including entry through incoming/ongoing call notifications. This is a package-wide
  exception, not an activity-level security boundary.
- A detected call closes protection controls and cancels pending authentication. The listener still
  only observes notifications: it never sends Domonap's full-screen PendingIntent, opens its screen,
  changes its notification, or changes audio. Panel polling keeps its existing call behaviour.
- Cancelling authentication, a late callback, a call, or a destroyed/background panel cannot grant
  access. No PIN or biometric data is stored by the app; Android verifies credentials and limits
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

On the tablet, configure a secure screen-lock PIN/password and optionally a fingerprint. Grant
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

1. Enable protection with PIN; repeat with a registered fingerprint. Cancel/failed checks must keep
   the panel protected. Check manual Xiaomi and Domonap launches.
2. Check Home, Recents, Back, notification settings links and vendor links. Home must return to the
   panel; Xiaomi must require authentication again after that return.
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

## Verified on 2026-11-04

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
