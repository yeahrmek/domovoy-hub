# Xiaomi (Mi Home / Xiaomi Home)

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

**The room: none.** The vacuum cleans every room and docks in one nobody has recorded, and the
humidifier the same app holds is somewhere else again. The tile lands in the panel's "Без комнаты"
section. That is an answer, not a gap — filling it in would mean picking a room the vacuum is not
in.

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
