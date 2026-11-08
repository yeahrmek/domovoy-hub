# Xiaomi (Mi Home / Xiaomi Home)

## Direct vacuum launch and Mi Home notifications, 2026-11-08

The vacuum tile opens the configured vacuum directly. The Mi Home button in **Защита** still
opens the app normally and provides a fallback if its device entry changes.

Verified on the Samsung SM-T875 (Android 13), using the installed Mi Home's own **Расширенные
настройки → Добавить на рабочий стол** shortcut. The panel has no shortcut-host permission, so
it uses the same explicit entry point rather than Android's `LauncherApps.startShortcut`:

```kotlin
Intent("com.xiaomi.smarthome.action.viewdevice")
    .setComponent(ComponentName("com.xiaomi.smarthome", "com.xiaomi.smarthome.device.utils.DeviceLauncher2"))
    .putExtra("device_id", configuredDeviceId)
    .putExtra("source_tag", "short_cut")
    .putExtra("user_model", "deerma.vacuum.a2506")
    .putExtra("timestamp", System.currentTimeMillis())
    .putExtra("from_main_activity", true)
```

`from_main_activity=true` is required by the installed handler. `device_mac` was omitted in the
successful physical test. Starting from the Samsung home screen opened the loaded vacuum screen
with its device name, charge, cleaning controls and station functions. No cleaning command was
sent. This is a verified private app entry point, not a published Xiaomi API; an Mi Home update
may change it. The identifier is kept in gitignored `local.properties` as
`xiaomi.vacuum.device.id` and seeded into encrypted preferences without replacing a stored value.
As with the existing vendor seeds, the value remains extractable from a locally built APK.

The existing notification listener also reads active Android notifications from
`com.xiaomi.smarthome`. A block above the panel's tiles displays the latest message, its original
title/text and posting time. Tapping expands up to five current messages. The source is labelled
**Mi Home**, because this app also reports other devices. Expanded notification text takes
precedence over the short preview. Group summaries and empty messages are omitted. The panel
never dismisses, modifies or opens the original notification and never derives a vacuum fault
state from its wording. Removal only removes the displayed message; it does not mean a fault was
resolved.

Notification access was already granted on the tablet, and Mi Home's system notifications were
allowed. No active Mi Home notification was present during the initial inspection. Messages seen
only inside Mi Home or delivered only to a phone are not available through this path. Access and
connection failures are shown separately; a failed reconnect preserves the previous messages as
potentially outdated. A successful reconnect reads the current Android notification snapshot.
Mi Home messages do not trigger the intercom-call state or launch another screen; the block is
hidden during a detected Domonap call.

Validation: all 778 JVM unit tests, `ktlintCheck`, Android `lint` and `assembleDebug` passed.
The debug APK was installed on the Samsung with `adb install -r`, preserving application data;
the installed APK hash matches the checked build. Tapping **Пылесос** in the updated panel
opened the loaded vacuum screen with the actual room map and cleaning/station controls.
Android reports the panel's notification
listener as granted and bound. Light/dark notification captures with font scale 1.3 are under
`app/build/outputs/mihome/`. Physical delivery of a new Mi Home push remains unverified.

Private device evidence is under `app/build/mihome-investigation/`; sanitized direct-launch
parameters are in `intent-probe/verified-intent.json`. The isolated verification outputs are under
`app/build/mihome-verification/` and `build/mihome-verification/`. These generated files are
disposable once this change has been reviewed and physical notification delivery has been
confirmed. The latest private tablet UI hierarchy is at
`/data/local/tmp/domovoy-mihome/window.xml`; it can be removed after the device checks finish.

## Direct humidifier LAN control, 2026-11-08

Both `deerma.humidifier.jsq5` units now have direct miIO/MIoT support in the panel. The existing
Yandex card IDs, names and rooms are retained, including favourites. Device tokens, Xiaomi IDs and
address hints are configured in gitignored `local.properties` as `xiaomi.local.devices`; the APK
seeds them into the existing encrypted preference store without overwriting stored credentials.
As with the other build-time credential seeds, they remain extractable from the APK.

The exact device specification is
`urn:miot-spec-v2:device:humidifier:0000A00E:deerma-jsq5:1`:

| MIoT property | Meaning | Values |
| --- | --- | --- |
| service 2, property 1 | Power | Boolean |
| service 2, property 2 | Fault | 0: normal; 1: insufficient water; 2: tank removed |
| service 2, property 5 | Intensity | 1: low; 2: medium; 3: high; 4: humidity control |
| service 2, property 6 | Target humidity | 40–80%, step 1% |
| service 3, property 1 | Measured humidity | Percent |
| service 3, property 7 | Measured temperature | Celsius |

Live authenticated `get_properties` reads returned mode 4 on both devices, with targets 45% and
60%. Thus Yandex's fourth intensity label, `turbo`, did not describe the native humidity-control
mode. Direct cards use `Авто`. Selecting a target writes it and selects this native mode when
needed; power is left unchanged. The target stays visible on the card, while intensity choices
remain under the chevron. Details expose the same target control.

Verified through a temporary USB-forwarded UDP relay on the wall tablet, with its VPN unchanged:
45 → 46 → 45% and 60 → 61 → 60%. Each new target and restoration was confirmed with a fresh
`get_properties` read, and power and mode were unchanged. Writes used `set_properties`, service 2,
property 6, and the actual Xiaomi device ID. A single property-5 write of the current mode also
returned code 0 on one unit. An earlier batch using placeholder IDs returned -4004 for the mode;
the client therefore sends the configured real Xiaomi ID and validates each acknowledgement.
Direct Mac UDP attempts failed; successful LAN checks were made from the tablet.

Redacted excerpt of a live read (device identifier fields omitted):

```json
{"id":1,"result":[{"siid":2,"piid":5,"code":0,"value":4},{"siid":2,"piid":6,"code":0,"value":45}]}
```

The client uses UDP port 54321, an miIO handshake, token-derived AES/MD5 framing, a three-second
request deadline and no automatic write retries. Each command is followed by a separate state
read. Xiaomi polling runs independently of Yandex, including before Yandex's first successful
read. A failed unit retains its last readings and gets its own error and polling timestamp.
Foreground/intercom lifecycle rules also apply to this poller. Saved IPv4 address hints currently
need updating if the router assigns different addresses; LAN rediscovery is not implemented.

The token inventory at `.toolchain/xiaomi/devices.json` is user-provided local credential material;
keep it for reconfiguration and remove it only when no longer needed. No Xiaomi account password
or token is included in source or recorded fixtures. The physical checks above verify LAN command
and readback behavior, not tablet APK installation, humidity convergence or reboot recovery.

Sources: [device specification](https://miot-spec.org/miot-spec-v2/instance?type=urn:miot-spec-v2:device:humidifier:0000A00E:deerma-jsq5:1),
[python-miio protocol](https://github.com/rytilahti/python-miio/blob/master/miio/protocol.py),
[model implementation](https://github.com/rytilahti/python-miio/blob/master/miio/integrations/deerma/humidifier/airhumidifier_jsqs.py).

Final validation: all 778 unit/UI tests, `ktlintCheck` and `assembleDebug` passed. Build outputs
were isolated from concurrent Gradle work through a temporary init script; no tests were excluded.
The APK is retained at `.toolchain/xiaomi/domovoy-xiaomi-debug.apk`, and JUnit XML evidence at
`.toolchain/xiaomi/verification-results.zip`. Keep the APK until this version is installed or
superseded, and the evidence until the next complete verification. Temporary probe files, the
USB relay/forwarding, and isolated build/cache directories were removed. This task did not install
an APK on the tablet.

The Yandex-only behavior and earlier integration decisions below predate this direct connection.

## Humidifier controls through Yandex, 2026-11-08

The panel now uses the existing Yandex integration for both discovered humidifiers. The recorded
`user_info.json` advertises power and `mode/fan_speed`: low, medium and high on one unit, plus turbo
on the other. Cards and details expose only each unit's advertised controls. Temperature and
humidity remain readings; neither unit advertises a writable humidity target. Commands use the
existing Yandex action endpoint and reread the inventory after success, retaining the previous
reading until confirmation. Pending commands disable that device's controls; command errors stay
local to it. Mock-server and Compose tests verify these paths; physical humidifier response has
not been tested for this change. The Mi Home vacuum launcher is unchanged.

The levels are collapsed by default behind a chevron on the card; temperature, humidity and
power remain visible. Expanding or collapsing the levels sends no device command, and the
device name still opens details.

A fresh read of `GET /v1.0/user/info` on 2026-11-08 returned `on_off/on` and `mode/fan_speed`
with low, medium, high and turbo for both humidifiers. Their properties contain humidity and
temperature. Neither advertises `range/humidity` or an automatic humidity mode, so the panel
cannot expose the device's own target-humidity control through this integration. Direct Mi Home
access is not configured; panel-side power automation has not been implemented.

The user confirmed that the target is available in Mi Home only. Yandex's
[Smart Climate documentation](https://alice.yandex.ru/support/ru/smart-home/smart-climate), checked
on 2026-11-08, describes temperature regulation with heating devices, not humidity regulation.

The earlier scope and integration investigation below predate these humidifier controls.

**Scope:** the vacuum and the humidifier, via a **hosted Mi Home app widget** — there is no Xiaomi
client in this project and no `integrations/xiaomi/` package. The widget shows real state because
the Mi Home app feeds it, and its buttons work; the cost is that it sits in Xiaomi's design
language and its values never reach our device model. A vacuum makes a poor tile anyway —
realistically start / dock / battery, not a map.

The rest of this note is why: the blocker is administrative, not technical.

Read from the public docs on 2026-08-15. **Nothing has been called yet**, and now nothing will be:
see "Decided: Mi Home, not the Yandex copy".

## Decided: Mi Home, not the Yandex copy

The Yandex probe found the vacuum and both humidifiers already reachable through a linked
third-party skill — `on_off`, `mode` and `range` capabilities, no Mi credentials involved (see
`yandex.md`, "Coverage, measured"). That reopened the question this file answers, because it meant
a vacuum tile could be built today with no Xiaomi integration at all.

**Exactly what that skill publishes has since been counted**, in `yandex.md` under "What every type
of device can be driven with": the flat's deerma vacuum is `on_off/on`, `toggle/pause`,
`mode/cleanup_mode` (`dry_cleaning` / `wet_cleaning` / `mixed_cleaning`) and a `float/battery_level`
— four things, one of them read-only. The humidifiers are `on_off/on` plus `mode/fan_speed`, with
humidity and temperature readable. Note also that the *other* household's roborock publishes
`mode/work_speed` instead of `cleanup_mode`: even the mode is per device, not per vacuum. That is
the list the decision below is measured against, and counting it did not change the decision.

**Decision, 2026-08-15: the panel keeps using Mi Home for the vacuum.** What Yandex exposes is what
the skill chose to publish — on/off and a mode list — while Mi Home shows battery, cleaning status,
consumables and the map. For a device whose useful state is mostly *not* a switch, the vendor's own
app is more informative than anything the shared device model could hold. The administrative
blocker below is therefore moot rather than binding: even with MIoT credentials, the tile would be
the poorer of the two.

What this settles, and what it does not: the vacuum will not get a Yandex-derived tile. **Whether
Mi Home is embedded as a hosted AppWidget or simply opened from a launcher tile is still open** —
it depends on whether Mi Home ships a widget for this vacuum, which is the first open question
below.

## Shipped: a launcher tile, pending the widget answer

The panel carries a launcher tile — "Пылесос" — that opens Mi Home and claims nothing else. It is
the fallback this note already named, and it needs nothing verified beyond a package name, so it
ships while the widget question stays open. If Mi Home turns out to ship a usable vacuum widget,
the hosted-widget tile replaces this one; if not, this is the answer.

**The package name is `com.xiaomi.smarthome`, verified on the tablet on 2026-08-15** — the app was
installed and signed in that day, and read off the device rather than off the public record:

```
package:com.xiaomi.smarthome                       # the only xiaomi package installed
versionName=11.7.622  versionCode=11070622  minSdk=24  targetSdk=35
com.xiaomi.smarthome/.SmartHomeMainActivity        # cmd package resolve-activity, MAIN/LAUNCHER
```

The launcher activity resolving is the part that matters: `getLaunchIntentForPackage` returns null
without one, and the tile would read "not installed" on a tablet that plainly has the app.

**Verified end to end on the tablet, same day.** With the debug build installed, the panel renders

```
Без комнаты
Пылесос
opens the app · no state to read
```

(The heading is **Коридор** as of 2026-08-30 — see "The room" below. The rest of this capture
stands.)

and tapping it brings `com.xiaomi.smarthome/.SmartHomeMainActivity` to the foreground — the same
activity resolved above. The `<queries>` block is what makes that work, and the system confirms it:

```
queriesPackages=[com.domonap.app, com.xiaomi.smarthome]     # dumpsys package ru.domovoy
queries via package name:                                    # dumpsys package queries
  ru.domovoy:
    com.xiaomi.smarthome
    com.domonap.app
```

**What the tile shows, and does not.** No state, no age, no polling: Mi Home's values never reach
our device model, so there is nothing here that could go stale and nothing to say an age about. The
line under the name says `opens the app · no state to read` rather than inventing a freshness.

**The room: Коридор, since 2026-08-30.** The vacuum docks in the hallway, which is where it is
fetched from and sent out from, and that is the room the tile is in.

It was **none** until then, and the reasoning was not wrong: the vacuum cleans every room, so no
single room owns it, and inventing one would have meant picking a room it is not in. What was
missing was the dock — nobody had recorded it, so "no room" was the honest answer to a question
nobody had answered. The cost showed on the wall: `roomSections` puts unplaced tiles last, so
"Пылесос" rendered under "Без комнаты" one scroll past all fourteen rooms, and Главная was the only
place it could be reached without scrolling the whole panel.

**The humidifier is not covered by this.** It is somewhere else again, and Mi Home is one launcher
tile named for the vacuum. A humidifier tile would get its own room, and it is not this one.

## Mi Home does ship widgets — five of them (2026-08-15)

Read off the tablet with `adb shell dumpsys appwidget`, which is the first open question below
half-answered: a widget exists, so the hosted-widget tile is not ruled out on availability.

| provider (`com.xiaomi.smarthome.miui.widget.*`) | min size |
|---|---|
| `MiJiaSingeDeviceWidgetProvider` | 110 × 110 dp |
| `MiJiaSmallEnvironmentWidgetProvider` | 110 × 110 dp |
| `MiJiaMiddleWidgetProvider` | 300 × 110 dp |
| `MiJiaMiddleEnvironmentWidgetProvider` | 300 × 110 dp |
| `MiJiaBigWidgetProvider` | 300 × 250 dp |

All five: `resizeMode=3` (resizable both ways), `widgetCategory=1` (home screen, not keyguard),
`updatePeriodMillis=10000`. Sizes are decoded from the raw dumpsys values — `28161` is `0x6E01`,
i.e. Android's `TypedValue` complex form with unit `1` (`COMPLEX_UNIT_DIP`) and mantissa `110`; the
same decode gives 300 and 250 for `76801` and `64001`, consistently across all five.

`MiJiaSingeDeviceWidgetProvider` — "single device", spelling Xiaomi's — is the one that matters
here: a 110 dp square showing one device is the right shape for a tile on our panel.

**What this does not settle, and none of it is inferable from a dumpsys:**

- **Whether it supports the vacuum**, and whether it shows battery and cleaning status or only a
  toggle. The provider list says nothing about which devices it accepts.
- **Whether it needs a configuration activity** to choose the device. `android:configure` lives in
  the `appwidget-provider` XML rather than in the manifest receiver, so this dump cannot show it;
  it needs the APK pulled and `aapt2 dump`. A single-device widget almost certainly has one, and a
  bind flow that must run a third-party configuration activity is a different job from binding a
  widget outright.
- **Whether these render on a non-MIUI tablet.** Every provider is namespaced `.miui.widget.` and
  registers a `miui.appwidget.action.APPWIDGET_UPDATE` alongside the standard one. The system here
  lists them as bindable providers, which is not the same as their populating with data on a
  Samsung. `updatePeriodMillis=10000` is also below the platform's 30-minute floor, so real
  refreshes come from the app's own broadcasts (`com.xiaomi.smarthome.miui.widget.refresh_widget_*`)
  — which is Mi Home's business, not something we could drive.

Binding one still needs a manifest change and a user-confirmed bind flow, "ask first" under
AGENTS.md. The launcher tile stands until somebody places `MiJiaSingeDeviceWidgetProvider` on this
tablet's home screen by hand and looks at what it actually shows for the vacuum — ten minutes, and
it decides the question.

## Verified in the docs

**Official third-party API for controlling a user's devices: not self-serve, and I could not find a
published one.** What actually exists:

- **Mi Account OAuth 2.0** (`dev.mi.com`, Mi Dev Platform). Real, documented, standard OAuth. But
  it is an *account/login* service: profile, contacts, identity. Getting a `client_id` requires
  registering as a developer and creating an app; **additional interface permissions are granted by
  manual review** — you pick the interfaces from a list and write a justification, and an operator
  decides. The callback carries `xmResult`, `_xmNonce`, `_xmSign`, `code`, `xmUserId`, and you are
  responsible for verifying `_xmSign`.
- **`XiaoMi/ha_xiaomi_home`** — the Home Assistant integration, published under Xiaomi's own GitHub
  org, i.e. first-party. This is the closest thing to a documented device-control path, and it
  states plainly how it works:
  - "Implements OAuth 2.0 login process, which does not keep your account password in the Home
    Assistant application."
  - Cloud control: "sends command messages to the devices via the HTTP interface of MIoT Cloud".
  - Local control, optional, two forms: the Xiaomi central hub gateway "contains a standard MQTT
    Broker", and LAN mode which "can only control IP devices (devices connected to the router via
    WiFi or ethernet cable) in the same local area network".
  - State updates are **pushed, not polled**: the integration "subscribes to the interested device
    messages on the MQTT Broker in MIoT Cloud", and the broker pushes on property change or event.
  - Regions: mainland China, Europe, India, **Russia**, Singapore, USA. Data is isolated per region.

**Cost:** no price is published. Developer registration is free; the gate is review, not money.

## Inferred / not verified — and this is most of the section

- **The OAuth client is the whole problem.** `ha_xiaomi_home` works because Xiaomi issued *that
  project* a client. Nothing says a third party can get one for an unrelated Android panel, and the
  documented route (Mi Dev Platform, per-interface review with a written justification) is not a
  route a hobby project walks through quickly. I could not find any page stating the terms.
  **Treat "can we even get credentials" as unanswered.**
- Commonly repeated but unconfirmed: `iot.mi.com` open-API access requires a company-verified
  account. I could not verify this on Xiaomi's own pages either way.
- The community route — `python-miio`, `micloud`, token extractors — reaches the Mi Cloud by
  replaying the app's own protocol, or talks the local miIO protocol using a per-device token
  scraped from the cloud. It works, people use it, and it is undocumented, unsanctioned and
  breakable. Under AGENTS.md's "never write code against an endpoint nobody has verified" this is
  out for the first cut.
- If credentials were obtainable, Xiaomi would be the *technically* best of the five: MQTT push
  from the cloud plus a genuine LAN path plus a local MQTT broker on the hub. That is the shape a
  wall panel wants. The blocker is purely commercial/administrative.

## Ecosystem health

The devices and the cloud are extremely alive; the *third-party developer surface* is the least open
of the five. Xiaomi has clearly chosen partnership-by-partnership integration (Home Assistant,
Alexa) over an open API. `ha_xiaomi_home` is actively maintained, so if we ever want to know what
MIoT Cloud really does, that repo is the reference — reading it is legitimate, copying its client
credentials is not.

## Open questions

- ~~Is `com.xiaomi.smarthome` the package on this tablet?~~ **Answered 2026-08-15: yes**, and the
  launcher tile is verified working on the device.
- Does Mi Home ship a widget for this vacuum, and does it show battery and status? **Half answered
  2026-08-15**: five widgets exist, one of them single-device — but whether it takes the vacuum,
  whether it needs a configuration activity, and whether a `.miui.` widget populates on a Samsung
  are all still open. Placing it on the home screen by hand answers all three.
- Widget binding needs a manifest change and a user-confirmed bind flow — "ask first" under
  AGENTS.md.
- Should a launcher tile whose app is missing offer to install it? Today it refuses the tap and
  names the package. A store deep link would need to know which store this tablet has — Domonap is
  distributed through RuStore, Mi Home through Google Play — and neither has been checked.
- Still worth ten minutes: is the vacuum already linked into Yandex via a skill? If so it appears
  in `/v1.0/user/info` and needs nothing from Xiaomi at all.
- Only if the scope changes: can an individual obtain a MIoT client_id, and on what terms? That is
  a support-ticket question, not a docs question.

## Sources

- [XiaoMi/ha_xiaomi_home](https://github.com/XiaoMi/ha_xiaomi_home)
- [Xiaomi Open API (passport)](https://dev.mi.com/docs/passport/en/open-api/)
- [XiaoMi Account Service Quick Access Guide](https://xiaomi-passport.github.io/)
- [python-miio](https://python-miio.readthedocs.io/) — community, for context only

## Recorded responses

_None yet._
