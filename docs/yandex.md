# Yandex Smart Home

**Scope:** air conditioner, curtains, bulbs — full integration, and the reference one for the rest
of the panel. The smart speaker is not a tile: it appears in the API, but the IoT API cannot make
it speak or play. (It can, it turns out, set the light ring — see "What every type of device can be
driven with", which counts what each of the thirteen types in this account actually exposes.)

Read from the public docs on 2026-08-15, and **`/v1.0/user/info` called for real on 2026-08-15**
from a laptop on the flat's Wi-Fi — 41 devices came back. What that call actually returned is under
"Recorded responses"; the redacted body is the fixture at
`app/src/test/resources/yandex/user_info.json`. **`POST /v1.0/devices/actions` has now been called
for real too**, from the panel on the tablet on 2026-08-15 — see "Run on the tablet". No response
body was captured from that call, so there is no fixture for it and the open questions about what
the response *means* are still open.

## Verified in the docs

**Official third-party API: yes.** Yandex documents two separate things; we want the first one.

1. *Управление устройствами по API* — control the devices already in a user's Yandex account.
   This is our case.
2. *Навык умного дома* — publish **our** devices into Alice. We are not doing this. It requires
   running an OAuth provider and a public HTTPS endpoint.

**Host:** `https://api.iot.yandex.net`

| Method | Path | Purpose |
| --- | --- | --- |
| `GET`  | `/v1.0/user/info` | everything: rooms, groups, devices, scenarios, households |
| `GET`  | `/v1.0/devices/{device_id}` | state of one device |
| `POST` | `/v1.0/devices/actions` | send actions to one or more devices |
| `POST` | `/v1.0/groups/{group_id}/actions` | send actions to a group |
| `POST` | `/v1.0/scenarios/{scenario_id}/actions` | run a scenario — no request body |

There is **no** `/v1.0/user/devices`. The device list only comes out of `/v1.0/user/info`.

**Auth:** `Authorization: Bearer <oauth_token>`. Token comes from Yandex OAuth
(`https://oauth.yandex.ru/`), app registered at `https://oauth.yandex.ru/client/new/`,
scopes `iot:view` and `iot:control`. Both are in the same permission group (`iot`), which matters
because an app of the "authorize users" type may hold at most 3 permission groups. For our use the
app type is *«Для доступа к API или отладки»*.

**Cost:** nothing. Registering the OAuth app is free and self-serve; no company details, no review
queue documented for this app type.

**Response shape** of `/v1.0/user/info`:

```json
{ "status": "ok", "request_id": "…", "rooms": [], "groups": [], "devices": [],
  "scenarios": [], "households": [] }
```

`/v1.0/devices/{id}` returns `id`, `name`, `type`, `state` (`"online"` / `"offline"`), `room`,
and `capabilities[]`, each capability carrying `last_updated` as a float unix timestamp.

**What a device inside `/v1.0/user/info` actually carries** (observed, 41 devices):
`id`, `name`, `aliases`, `type`, `external_id`, `skill_id`, `household_id`, `room`, `groups`,
`capabilities`, `properties`, `device_info` (`manufacturer`, `model`), and `quasar_info` on Yandex
hardware. Each capability has **both** `last_updated` **and `state_changed_at`** — the latter is
undocumented in the pages above and is the one that answers "when did this actually change".

Action body:

```json
{ "devices": [ { "id": "lamp-id-1",
  "actions": [ { "type": "devices.capabilities.on_off",
                 "state": { "instance": "on", "value": true } } ] } ] }
```

Success carries `action_result` with status `DONE`; unknown device ids give `404`.

Every response carries `request_id`; the docs tell you to log it — it is the only thing Yandex
support can act on.

**Push:** not documented. No webhook, subscription or streaming endpoint appears anywhere in the
device-control half of the docs. (The `X-Request-Id` header and callbacks that do exist belong to
the *provider* half — us serving Yandex, not Yandex notifying us.)

**Rate limits:** not published. The docs give no numbers at all.

## Inferred / not verified

- **Poll-only.** Because no push mechanism is documented, the panel has to poll. `/v1.0/user/info`
  is one call for the whole house, so poll that on a timer and use `/v1.0/devices/{id}` only when a
  tile is opened. A tile that was just tapped should be re-read sooner than the timer, otherwise the
  UI shows the old state for up to one interval.
- **No local path.** Nothing in the docs describes talking to a Yandex hub or station over the LAN.
  Assume every action leaves the flat and comes back. If the internet is down, this integration is
  down — that is the "degrade this tile only" case from AGENTS.md.
- **Rate limits:** unknown, so treat 429 as expected and back off. Yandex's other APIs sit in the
  10–300 rps range, which is far above anything a wall panel does; a poll every 5–15 s is almost
  certainly fine, but it is a guess, not a documented allowance.
- **Token lifetime:** the OAuth page shows per-scope token lifetimes; I did not confirm what `iot:*`
  gets. Assume it expires and store a refresh token. AGENTS.md already assumes expiry.
- ~~**Coverage:**~~ **confirmed, and bigger than expected** — see "Coverage, measured" below.

## Coverage, measured

One `/v1.0/user/info` call returned **41 devices, 21 rooms, 3 groups, 3 scenarios — across four
households**. Two consequences, both bigger than the "one flat, a few tiles" the panel assumes.

**The account is not one home.** Devices split 29 / 7 / 4 / 1 across four `household_id`s: the flat
the panel hangs in, two other homes and a dacha. Nothing in a device says "this is the panel's
home" except `household_id`, so **the panel must filter by household or the dacha's lights land on
the hallway wall**.

Decided: **the panel shows one household — the flat with 29 devices — and nothing else.** Not a
default that can be overridden in the UI; the other three homes are simply not the panel's
business. Its id is an apartment identifier, so it lives in `local.properties` as
`yandex.household.id` (gitignored) and never in a fixture, a doc or a commit. In
`user_info.json` that household is `household-flat`; the other three are kept in the fixture on
purpose, so a test can prove they get filtered out.

**Xiaomi is already here.** Seven devices arrive through one third-party skill: a roborock vacuum,
a deerma vacuum, two deerma humidifiers and a dmaker fan — i.e. the vacuum and the humidifier that
`vendor-comparison.md` assigned to a hosted Mi Home widget "because no credentials can be had".
Through Yandex they are ordinary devices with `on_off`, `mode` and `range` capabilities and no Mi
credentials at all. That does not make the hosted-widget decision wrong — Yandex exposes what the
skill chose to publish, not everything Mi Home shows — but it did mean the decision had to be
re-taken against this list rather than inherited. **It was, on 2026-08-15: Mi Home stays.** Battery,
cleaning status, consumables and the map beat an on/off and a mode list for a device whose useful
state is not a switch. See `xiaomi.md`. The Tuya recuperators are **not** in here; that plan is
unchanged.

Also observed, and relevant to how tiles are built:

- **No `state` field.** Not one of the 41 devices carries `state` — the `"online"`/`"offline"` the
  docs describe is on `/v1.0/devices/{id}`, not on the list. From the poll alone the panel cannot
  say a device is unreachable, only how old its reading is.
- **`last_updated` is `0.0` for 33 of 116 capabilities**, and for every capability on 5 devices.
  That is "never reported", not 1970 — a tile that formats it as a date shows *1 Jan 1970*. The
  staleness display needs a "never" case before it needs anything else.
- **Capabilities**: `on_off` 36, `range` 24, `color_setting` 20, `mode` 14, `toggle` 12,
  `zigbee_node` 10. **Properties** (a second array the docs above never mention): 23 `float`
  (`signal_level`, `temperature`, `battery_level`, `humidity`, `voltage`, `power`, `amperage`) and
  11 `event` (`voice_activity`, `button`, `motion`, `noise`).
- **`skill_id` is how you tell vendors apart**: `YANDEX_IO` for Zigbee through a Yandex hub (the
  LUMI curtain among them), plus one skill id per linked vendor account.

### Getting the token, in practice

The first call returned a bare non-JSON `Forbidden`. Cause: the token was issued before `iot:view`
and `iot:control` were added to the OAuth app, and Yandex reuses an existing grant — so
re-authorizing silently returns the *old* scope set. Fix: add the scopes, revoke the app at
`https://id.yandex.ru/security/apps`, then authorize again and accept the new consent screen.
An API-level error would have been JSON; a plain-text `Forbidden` means it never reached the API.

### How the token gets in

The panel reads the token from `EncryptedSharedPreferences` (file `domovoy-secrets`, keys
`AES256_SIV`, values `AES256_GCM`, master key in the AndroidKeyStore) on **every call**, not once
at startup — so a token written while the panel is running is used by the next poll, with no
restart. `local.properties` still seeds it:

1. **Fresh install.** The store is empty, so `yandex.oauth.token` — carried into the APK as
   `BuildConfig.YANDEX_OAUTH_TOKEN` — is written into it on first launch. That is the only way a
   token reaches the tablet today.
2. **Every launch after that.** The store already holds a token, so the build-time value is
   ignored. Reinstalling the same APK over a store that has a fresher token does not put the stale
   one back. *Uninstalling* wipes the store, and the next install seeds again from the APK.
3. **Nothing stored.** No request is sent — an empty `Bearer` would come back as the same
   plain-text `Forbidden` above and blame the scopes. The poll fails with *«no Yandex token stored —
   set yandex.oauth.token in local.properties and reinstall»*, **and that sentence is now in `Log`
   rather than on the wall**: every throwable reaching a tile goes through `reason`, which has four
   words in it (`docs/ui.md`, "Why a poll failed"). What the panel shows is
   `Лампы: not updating: failed`. That is a real loss and it is tracked —
   `docs/design/panel-redesign.md` item 8 is the commit that would give the sentence the 753 dp
   group-failure line it actually fits on.
4. **Store will not open.** A keystore lost across a restored backup or a wipe fails the poll rather
   than taking the panel down; *«secure storage unavailable: …»* is in `Log`, on 3's rule.

**Still true, and the reason this is only half the fix:** the seed rides in the APK, and there is
no way to type a token into the panel. So an expired token today still means edit
`local.properties`, rebuild, reinstall. What the move buys is that the *runtime* source of truth is
now the store, so anything that can write to it fixes the panel in place.

`androidx.security:security-crypto` is deprecated upstream — 1.1.0 is its last release. Kept
because AGENTS.md names `EncryptedSharedPreferences` and it is still the shortest keystore-backed
path; the replacement, if it ever matters, is AES-GCM against the AndroidKeyStore by hand, behind
the same `TokenStore`.

### What an OAuth refresh flow would need

Not built, and not designed here. What it would have to answer first:

- **Does the current app type even issue a refresh token?** Unknown. The app is registered as
  *«Для доступа к API или отладки»* and the token was obtained by hand; `iot:*` token lifetime was
  never confirmed (see "Inferred / not verified"). If the answer is no, the app has to be
  re-registered as an "authorize users" type with a redirect URI before any of this is possible.
- **Somewhere to keep the refresh token** — `TokenStore`, the same store, another key.
- **A trigger.** `YandexClient` currently treats `401` and `403` as ordinary failures. Refresh
  means recognising them, exchanging the refresh token, storing the new one and re-polling once —
  and not looping when the exchange itself fails.
- **A first authorization that a wall tablet can do.** The consent screen is a browser flow; the
  panel has no place to run one today, so the first token would still arrive from a laptop.

## Ecosystem health

Alive and first-party. Docs are current, in Russian, versioned under `yandex.ru/dev`, and this is
the same platform Alice itself runs on, so it is not a side project that can quietly rot. Russian
availability is not a question the way it is for Aqara/Tuya regions.

## What every type of device can be driven with

The panel drives four device types and five capability types. The account holds **thirteen device
types and six capability types**. This section is the gap between those numbers: what each type in
this account actually exposes, and what an action for it would have to look like.

**Two footings, deliberately not mixed.** What each device *carries* comes from the recorded
`GET /v1.0/user/info` of 2026-08-15 — a real call against this account, fixture at
`app/src/test/resources/yandex/user_info.json` — so it is fact about these devices, not about the
type in general. What an action for a capability *looks like* comes from Yandex's capability pages.
`on_off`, `range`, AC `mode`/`toggle`, and RGB/scene `color_setting` have now been sent to this
flat and read back (see "Live capability verification"). Nothing here is a guessed endpoint.

### Every type in the account

`flat` is the household the panel hangs in; everything outside it is filtered out in `YandexClient`
and can never reach a tile. It is listed anyway, because a type with more capabilities elsewhere in
the account is the best evidence of what that type looks like when fully equipped.

| Type | acct | flat | capabilities — `type/instance` | properties | tile today |
| --- | --- | --- | --- | --- | --- |
| `light` | 21 | 18 | `on_off/on`, `range/brightness`, `color_setting/temperature_k`\|`rgb` | `float/signal_level` | Bulb — on/off; brightness; RGB/scenes when advertised; Kelvin shown |
| `light.strip` | 2 | 2 | `on_off/on`, `range/brightness`, `color_setting/temperature_k` | `float/signal_level` | Strip — on/off + brightness, colour shown |
| `openable.curtain` | 1 | 1 | `on_off/on`, `range/open` | `event/button`, `float/signal_level` | Curtain — position |
| `thermostat.ac` | 3 | 3 | `on_off/on`, `range/temperature`, `mode/thermostat`, `mode/fan_speed`, `mode/swing`, `toggle/ionization`, `toggle/keep_warm`, `toggle/backlight` | `float/temperature` | AC — on/off, setpoint, modes and toggles |
| `vacuum_cleaner` | 2 | 1 | `on_off/on`, `toggle/pause`, `mode/cleanup_mode` | `float/battery_level` | none — Mi Home widget |
| `switch` | 3 | 2 | in the flat: **none** | `event/button`, `float/battery_level`, `float/signal_level` | none |
| `media_device.tv.yandex.magritte` | 1 | 1 | `on_off/on` | `event/voice_activity` | none |
| `smart_speaker.yandex.station.cucumber` | 2 | 1 | `color_setting` — scenes only | `event/motion`, `event/noise`, `event/voice_activity` | none |
| `humidifier` | 2 | 0 | `on_off/on`, `mode/fan_speed` | `float/humidity`, `float/temperature` | none |
| `ventilation.fan` | 1 | 0 | `on_off/on`, `mode/fan_speed`, `toggle/oscillation` | — | none |
| `socket` | 1 | 0 | `on_off/on` | `float/voltage`, `float/power`, `float/amperage` | none |
| `media_device.tv` | 1 | 0 | `on_off/on`, `range/volume`, `range/channel`, `toggle/mute` | — | none |
| `smart_speaker.yandex.station` | 1 | 0 | **none at all** | `event/voice_activity` | none |

A seventh capability type, `zigbee_node`, sits on 10 devices. It is not drivable and not a function:
its `state.value` is an object (`signal_quality`, `channel`, `is_active`), it is `retrievable: false`,
and it describes the mesh, not the device.

### The flat, type by type

**`devices.types.light` — one type, four different devices.** The 18 flat bulbs do not have one
surface, and the bulb tile's on/off is the *whole* of it for only some of them:

| Model (skill) | n | `on_off` | `range/brightness` | `color_setting` |
| --- | --- | --- | --- | --- |
| `_TZ3000_g92baclx/TS0001` (`skill-04`, Zigbee relay) | 5 | yes | **absent** | **absent** |
| `SMART LIFE/T-01` (`skill-02`) | 11 | yes | `0..100`, precision 1, `unit.percent` | `temperature_k`, `2700..6500` |
| `GLEDOPTO/GL-C-009P` — Трек в коридоре (`skill-04`) | 1 | yes | `1..100` | `temperature_k` bounds `1996..6369`, **`state: null`** |
| `Aqara/HM1S-G02` — Акара в коридоре (`skill-06`) | 1 | yes | `0..100` | `rgb`, `color_model: rgb`, 4 `color_scene`s |

- **Five of the eighteen are on/off relays and nothing else.** For those the tile is already complete;
  no slider is being withheld, there is nothing to slide.
- **Thirteen carry brightness and a colour.** Their sheets expose brightness, show Kelvin as a
  reading, and offer RGB swatches/scenes only when the device advertises them. That is the same trio
  the strips carry — the type string is the only thing separating a bulb from a strip in this
  response, which is the open question at the bottom of this file.
- **The brightness floor is not the same across them**: `0` on the T-01s and on the Aqara, `1` on the
  GLEDOPTO. `Bounds.snap` already reads it from the device, which is why this costs nothing; a
  constant `0..100` would have sent the GLEDOPTO a value it never offered.
- **The Aqara's `color_setting` has no `temperature_k` bounds at all** — `color_model: rgb` plus a
  `color_scene` list (`candle`, `rest`, `movie`, `sunrise`). So "a bulb's colour" is not one control:
  a Kelvin slider fits 12 of the 13 and is meaningless on this one.
- Two relays report `on_off` as `false`, not null — so "off" here is a reading, not a gap.
  `light-11`'s `zigbee_node` says `bad` (`lqi: 6`, `rssi: -97`) and `light-10`'s says `ok`; with no
  `state` field on this call, that mesh quality is the closest thing to a reachability signal the
  response has, and nothing reads it.

**`devices.types.light.strip`** — as recorded in "The light strips, as recorded" above. The colour
bounds are `1996..6369` on both, and on `light-strip-02` the `color_setting` has no state, hence no
instance either.

**`devices.types.openable.curtain`** — `on_off/on` and `range/open` (`0..100`, `unit.percent`,
`random_access: true`), both drivable and the position already driven by the tile. It also carries an
`event/button` property with `click` / `double_click` / `long_press` and a `float/signal_level`,
neither with any state and neither read by the panel.

**`devices.types.thermostat.ac`** — the widest surface in the flat: eight capabilities. Beyond the
`on_off` and `range/temperature` the tile drives, each unit lists, *from the device itself*:

| Capability | Values the device listed |
| --- | --- |
| `mode/thermostat` | `fan_only`, `heat`, `cool`, `dry`, `auto` |
| `mode/fan_speed` | `turbo`, `high`, `medium`, `low`, `quiet`, `auto` |
| `mode/swing` | `stationary`, `vertical`, `horizontal`, `auto` |
| `toggle/ionization`, `toggle/keep_warm`, `toggle/backlight` | boolean; `backlight` is absent on `ac-03` |

All six are parsed into `Device.modes` / `Device.toggles` and are presented in the AC's sheet;
`setMode` and `setToggle` send only values this particular device advertised. Note `quiet` in
`fan_speed`: Yandex's own documented list for
that instance is `auto, high, low, medium, turbo`, so **the device advertises a value the platform's
reference list does not contain**. Reading `parameters.modes` off the device rather than off a table
is not defensive style here, it is the only thing that would have worked.

**`devices.types.vacuum_cleaner`** — the flat's deerma: `on_off/on`, `toggle/pause`,
`mode/cleanup_mode` (`dry_cleaning`, `wet_cleaning`, `mixed_cleaning`, currently `mixed_cleaning`),
and `float/battery_level` at 100. **The mode instance is per device, not per type**: the roborock in
another household has `mode/work_speed` (`quiet`, `normal`, `fast`, `turbo`) and no `cleanup_mode` at
all. Anything keyed off "the vacuum's mode" would be right for one of the two. This is the device
`xiaomi.md` gives a Mi Home widget instead of a tile, and this list is the comparison that decision
rests on: start/stop, pause, one mode — against battery, consumables, cleaning status and the map.

**`devices.types.switch` — in this flat it is an input, not an output.** Both flat switches are
Yandex buttons (`YNDX-00534`): `switch-01` carries a `zigbee_node`, `float/battery_level` (100),
`event/button` (`click`) and a stateless `float/signal_level`; `switch-03` carries one `event/button`
and an **empty `capabilities` array** — the only device in the response with one. **On neither is
there anything to control.** The one `switch` in the account that behaves like a relay (`WS-EUK01`,
"Люстра") is in another household. So the type string does not tell you whether a `switch` can be
switched; only the capability list does.

**`devices.types.media_device.tv.yandex.magritte`** — the ТВ Станция exposes `on_off/on` and nothing
else: no volume, no channel, no input source, no mute. The ordinary TV in another household (Samsung
`QE55Q70RAUXRU`, through a different skill) carries `range/volume` (`0..100`), `range/channel` (no
bounds, `unit: ""`) and `toggle/mute` — so the missing controls are Yandex's own hardware being
thinner over this API than a third-party TV, not a limit of the type.

**`devices.types.smart_speaker.yandex.station.cucumber` — the one genuine surprise.** The Станция
Миди has no `on_off`, no volume and no playback, exactly as `vendor-comparison.md` says. What it does
have is a **`color_setting` carrying a `color_scene` list — `lava_lamp`, `inactive`, `night`,
`candle`** — i.e. the light ring is addressable through this API. It is `retrievable: false` **and**
`reportable: false`, which is a combination nothing else in this response has: a control that can be
written and never read back. A tile for it could not show state, only send. It also carries three
event properties — `motion` (`detected`), `noise` (twelve values including `break_glass`, `dog_bark`,
`child_cry`, `alarm`) and `voice_activity` — all stateless in the list call, so they are usable only
inside Yandex's own scenarios, not by a panel that polls.

### The types outside the flat, for the record

`humidifier` (`on_off` + `mode/fan_speed`, and `float/humidity` + `float/temperature` that both do
report), `ventilation.fan` (`on_off` + `mode/fan_speed` + `toggle/oscillation`), `socket` (`on_off`,
plus live `voltage` / `power` / `amperage`) and the plain `station` speaker (**no capabilities at
all**, one event property). None can reach the panel; the humidifier is the second device
`xiaomi.md` covers with a widget, and its Yandex surface is on/off plus four fan speeds.

### The action body, per capability

Every action goes to the one endpoint, `POST /v1.0/devices/actions`, with the body already in
"Verified in the docs". Only the `state` object differs:

| Capability | `instance` | `value` | Footing |
| --- | --- | --- | --- |
| `on_off` | `on` | boolean | **Verified on the tablet** — a bulb and `ac-03`, 2026-08-15 |
| `range` | `brightness`, `open`, `temperature`, `volume`, `channel` | number | **Verified** for AC `temperature` and curtain `open`; brightness accepted but the tested offline lights did not reflect it |
| `mode` | `thermostat`, `fan_speed`, `swing`, `cleanup_mode`, `work_speed` | string, one of `parameters.modes` | **Verified** for the AC's `thermostat`, `fan_speed`, and `swing`, 2026-08-30 |
| `toggle` | `ionization`, `keep_warm`, `backlight`, `pause`, `mute`, `oscillation`, `controls_locked` | boolean | **Verified** for AC `ionization`, 2026-08-30 |
| `color_setting` | `temperature_k` | integer Kelvin | Accepted, but the tested offline lights did not reflect it; not exposed as a writable control |
| `color_setting` | `rgb` | integer, `0..16777215` packed `0xRRGGBB` | **Verified** on `light-21`, 2026-08-30 |
| `color_setting` | `hsv` | object `{h: 0..360, s: 0..100, v: 0..100}` | Docs only — **never sent**; no device here reports it |
| `color_setting` | `scene` | string, one of `parameters.color_scene.scenes[].id` | **Verified** on `light-21` with `candle`, 2026-08-30 |

### Live capability verification — 2026-08-30

All tests below were reversible: the original value was read first, one change was sent, the house
was re-read, and the original value was restored and confirmed. Device ids and credentials stayed
in temporary files and are not reproduced here.

- **AC fan mode:** `auto → low → auto`, reflected on re-read.
- **AC thermostat mode:** `cool → dry → cool`, reflected on re-read while the unit was off.
- **AC swing:** `horizontal → vertical` was accepted; the re-read reported `auto`, and restoring
  `horizontal` reflected. The panel therefore never paints the requested value optimistically.
- **AC ionization:** `false → true → false`, reflected on re-read.
- **RGB bulb:** packed RGB `16777200 → 16711680 → 16777200`, reflected and restored.
- **RGB scene:** `candle` reflected; restoring the original RGB value reflected.
- **Curtain:** `open: 0 → 20 → 0`, reflected and restored. For this device, zero is closed and a
  larger value is more open.
- **Brightness and Kelvin:** the action endpoint answered `ok`, but Lamp 8 remained at brightness
  `83` and `2700 K`; the strip behaved the same way. `GET /v1.0/devices/{id}` reported these lights
  offline. This confirms the payload was accepted, not that the physical capability changed, so
  Kelvin remains read-only and every range write is repainted only from a fresh poll.

Two things the docs add that nothing in the panel uses yet:

- **`"relative": true` next to `value` on a `range` action** makes the value a delta rather than a
  position. That is what a `random_access: false` range accepts *instead of* an absolute value — a TV
  volume behind an IR blaster is the docs' own example. Every range in this account is
  `random_access: true`, so the panel has never needed it.
- **The documented instance lists are wider than this account uses.** `mode` documents twelve
  instances (`cleanup_mode`, `coffee_mode`, `dishwashing`, `fan_speed`, `heat`, `input_source`,
  `program`, `swing`, `tea_mode`, `thermostat`, `ventilation_mode`, `work_speed`) and `range` six
  (`brightness`, `channel`, `humidity`, `open`, `temperature`, `volume`). They are worth knowing and
  worth **not** hardcoding: the AC's `quiet` fan speed above is already outside the documented set.

### Two control surfaces the panel does not touch at all

- **Groups.** `POST /v1.0/groups/{group_id}/actions`, already in the endpoint table. The flat has two
  light groups — `group-01` "Трек в спальне" over 7 bulbs and `group-03` "Трек в зале" over 4 — so
  "all the track lights to 40%" is one call rather than seven. **A group is shaped like a device**:
  it carries `household_id` (so the panel's filter works on it unchanged) and its own `capabilities`
  array, `on_off/on` + `range/brightness` + `color_setting/temperature_k` on `group-01`, complete
  with `parameters`, bounds and a `state` value. What it does **not** carry is `last_updated` or
  `state_changed_at` on any of them — no group capability in this response has a timestamp of any
  kind. So a tile driven off a group could show a value and could not say how old it is, which is
  the one thing AGENTS.md says a tile must always be able to do. Reading state per device and
  *writing* per group is the shape that keeps both.
- **Scenarios.** `POST /v1.0/scenarios/{scenario_id}/actions`, **with no body at all** — the token
  header is the whole request, and the response is `{request_id, status}`; an unknown id gives `404`
  with `"status": "error"`. This is the cheapest write in the whole API and the panel ignores it.
  **The catch is that a scenario has no `household_id`.** Of the three in the account, two act on
  `light-10` in the flat and `Счастливый фермер` acts on `light-14` and `socket-01`, neither of which
  is the panel's household — so the filter that keeps the dacha off the wall has nothing to filter
  on. What a scenario *does* carry is `steps[].parameters.items[]` naming the devices it touches, so
  a scenario's household can be worked out from its steps. Anything that puts a scenario button on
  the wall has to do that first. (`is_active` is also per scenario — `Счастливый фермер` is `false` —
  and it carries `triggers`, e.g. a `scenario.trigger.timetable` with a solar condition.)

### What is read-only whatever we do

`properties[]` is a second array beside `capabilities[]`, and **no action type exists for a
property** — there is no way to write one. Two shapes, both present here:

- `devices.properties.float`: `signal_level`, `battery_level`, `temperature`, `humidity`, `voltage`,
  `power`, `amperage`.
- `devices.properties.event`: `button` (`click`/`double_click`/`long_press`), `motion` (`detected`),
  `noise` (twelve values), `voice_activity` (`speech_finished`).

**The panel now parses the `float` ones** into `Device.properties`, which is how the AC's measured
room temperature reaches the detail sheet — `room 28.0 °C`, seen on the tablet on 2026-08-31. It is
the first property on the wall, and it sits next to the setpoint, which is the thing a thermostat
tile most obviously wants. Still unread: `battery_level`, which reports 100 on the vacuum and on
`switch-01` — `switch-03` has none, so a battery line has to cope with a button that never mentions
one. The event properties are a
different matter: they are `reportable` and stateless, so a poll can only ever catch the value that
happens to be sitting there — `switch-01`'s `button` says `click` with a `last_updated` and
`switch-03`'s says `click` with `0.0`. Without push, a button press is not something this panel can
see. That is the same wall `vendor-comparison.md` records for every vendor.

## What the bulb tile actually does with this

Built against the fixture; first run against the live API on the tablet on 2026-08-15, see
"Run on the tablet" below.

- **`devices.types.light` only.** Exact match, so `devices.types.light.strip` is *not* a bulb tile.
  18 of the flat's 29 devices qualify. The two strips now have a tile of their own — see "The light
  strips, as recorded" below — and the match here is unchanged: they are a separate `kind`, not a
  bulb with extra capabilities.
- **Filtering happens in `YandexClient`**, not downstream: 23 of the 41 devices returned belong to
  the other three households and never reach the shared model. `household_id` comes from
  `local.properties` as `yandex.household.id` via a `BuildConfig` constant.
- **Both timestamps are kept** on the model (`OnOff.lastUpdated`, `OnOff.stateChangedAt`). The tile
  prints the age of `last_updated` — that is literally "how old is this reading". Which of the two
  a tile *should* show is still the open question below; keeping both means answering it later
  costs nothing.
- **`last_updated: 0.0` is `Reading.Never`**, rendered "never read". A `Double` at this magnitude
  resolves only ~0.2 µs, so sub-second parts survive approximately — irrelevant for an age display,
  worth knowing before anyone compares two timestamps for equality.
- **A failed or timed-out poll keeps the tiles on screen** with their last values and adds one of
  four words — `unreachable`, `timed out`, `refused`, `failed` — on the tile's second line. One `/v1.0/user/info` call is the whole house, so the failure belongs to the
  bulb group rather than to one tile. Every call carries a 10 s call timeout.
- **A toggle is not trusted.** `POST /v1.0/devices/actions` succeeding only means Yandex accepted
  it, so the tile is repainted from a fresh `/v1.0/user/info`, never from the action result — see
  the first open question below, still unanswered. The tablet run shows the round trip works; it
  does not show that the response could have been trusted.
- **The OAuth token** is read at runtime from `EncryptedSharedPreferences`, not from `BuildConfig`
  — see "How the token gets in" below. No token stored is a visible failure on every tile, not an
  empty poll; what it is *not* any more is a sentence naming the token, which is in `Log`.

## What the curtain tile actually does with this

Built against the fixture only. **Nothing in this section has been run against the real curtain** —
see the warning at the end of it.

The flat has one `devices.types.openable.curtain`, a LUMI `lumi.curtain` through the Yandex hub
(`skill-04`). What it carries, verbatim from the recorded response:

```json
{ "type": "devices.capabilities.range",
  "state_changed_at": 0.0, "last_updated": 1786667879.388499,
  "parameters": { "instance": "open", "unit": "unit.percent", "random_access": true,
                  "looped": false, "range": { "min": 0, "max": 100, "precision": 1 } },
  "state": { "instance": "open", "value": 0 } }
```

plus an `on_off` whose value is `false` and whose **both** timestamps are `0.0`, and a
`zigbee_node` whose `state.value` is an *object* (`signal_quality`, `channel`, `is_active`) rather
than a scalar.

- **The tile shows the `range` timestamps, not the `on_off` ones.** The position is what the tile
  prints, so its age is the age of the position — and on this device `on_off` has never reported at
  all, so hanging the age on it would have said "never read" next to a position read minutes ago.
- **`state_changed_at: 0.0` with a real `last_updated`** is the reverse of the bulbs' case: the
  capability *has* been read, it just has no recorded change. Both are kept, `Reading.Never` covers
  the `0.0`, and the tile prints the age of `last_updated` — the same choice as the bulbs, and the
  same open question below.
- **A `range` with no `state` at all reads as unknown, never as 0.** A curtain at 0% is shut; one
  that has never reported is not, and a tile that prints "0% open" for it says the opposite of the
  truth. The recorded response does carry a stateless range — on the TV's `channel` — so the shape
  is real even though this curtain happens to have a value.
- **`random_access: true`** is what makes an absolute position a legal action at all; on a range
  without it, only relative moves would be. Every range in scope has it, so the panel does not
  store the flag — the first `false` one is the moment to start.
- **The value is snapped before it is sent**: clamped to `min`..`max` and rounded to `precision`,
  from the bounds *that device reported*, not from constants. A slider hands over anything, and an
  action Yandex rejects reaches the wall as "not updating" for a reason that was ours.
- **Whole numbers go out as whole numbers.** Yandex reports the position as `0`, not `0.0`, and
  precision is 1, so the action sends `"value": 70`.

### The curtain never reports where it is — read live, 2026-08-31

The tile said `0% open` at display size in front of a curtain standing fully open. **Nothing was
wrong with the read.** Both endpoints agreed, thirteen hours after the panel last drove the curtain:

| Capability | flags | `state` | `last_updated` |
| --- | --- | --- | --- |
| `range/open` | retrievable, reportable | `0` | 2026-08-30 19:26:01 (`state_changed_at` 19:25:58) |
| `on_off/on` | retrievable, reportable | `false` | `0.0` — **never reported** |
| `zigbee_node` | reportable | `excellent`, `is_active: true` | 2026-08-31 08:25:37, and hourly |

`GET /v1.0/devices/{id}` answered `"state": "online"` and the same `open: 0`, so this is neither a
cached list response nor an unreachable device. And 19:26:01 the previous evening is the exact minute
the capability run above restored `open: 0`: **the newest thing Yandex knows about this curtain is
the panel's own last write.**

**It is not that Yandex missed a move it could not see.** The curtain was opened that morning through
a Yandex station — a command Yandex itself issued, on its own hub (`skill_id: YANDEX_IO`, the curtain
is a Zigbee node on it). An hour later neither capability had moved: `range/open` still held our
write, and `on_off` still had never reported a value at all, both flags notwithstanding.

The hub is not the problem, and neither is the account. On the same skill, in the same response,
`Свет в гардеробе` reported `on_off: false` at 10:08 — 23 minutes before the poll. State callbacks
arrive from this hub within minutes. **The curtain is the one device on it that does not send them.**

So on this device `range/open` is not a position, it is *the last position the panel drove it to*.
Nothing in the response tells "shut" apart from "not moved since we shut it", and there is no second
source to cross-check against: `on_off` is silent and the `event/button` property is stateless.

What the panel does about it: the position is a fact for an hour — `WORTH_SAYING`, the same line the
tile prints an age at — and a memory after that, which reads on the wall exactly as a position nobody
has ever read does. See docs/ui.md, "A curtain position expires".

**It is not the panel's account, and it is not the API.** The Yandex app shows the same stale record
after a voice command, so whatever the panel reads is what Yandex itself holds.

**Why, most likely.** This motor is not a device the hub officially supports: Yandex's own list for
the hub carries exactly two curtain tracks, both `Яндекс`-branded (`YNDX-00591`, `YNDX-00592`), and
no Aqara or Xiaomi motor at all. A `lumi.curtain` (ZNCLDJ11LM) paired to it is a standard Zigbee
window covering the hub can *command* without having an implementation that reads it back — which is
the shape of what is observed, and matches the common report for partially-supported curtain motors:
open/close works, the position report never arrives. Two things follow from it, neither confirmed:

- **Calibration.** On this model calibration is mandatory for position reporting and for intermediate
  positions at all; an uncalibrated motor is a documented cause of a position that never moves.
- **State the hub stores versus state it reads.** Every timestamp this capability has ever carried
  came from a write — ours at 19:26. Nothing suggests the hub ever *reads* the motor: `on_off` is
  declared `retrievable` and has never held a value.

### Watched across two station commands — 2026-08-31

Two spoken commands, the API polled within a minute of each. This is the whole behaviour:

| Spoken | Capability it drives | `range/open` after | `on_off/on` after |
| --- | --- | --- | --- |
| "открой шторы на 50 процентов" (10:51) | `range` | **`50`**, `lu` 10:51:34, `sca` 10:51:28 | untouched |
| "закрой шторы" (10:53) | `on_off` | **still `50`**, `lu` still 10:51:34 | `false`, `lu` 10:53:57, `sca` still never |

The curtain physically went to about half on the first and shut on the second, both confirmed by eye.

- **A percentage command writes the position.** The record follows it and the timestamps are real, so
  a position is trustworthy immediately after *anything* drives this curtain by percent — the panel
  included, since `setOpen` is the only way it moves a curtain.
- **An open/close command does not.** `range/open` kept `50` in front of a shut curtain. All the
  command left behind was `on_off.last_updated`; the value stayed `false` and `state_changed_at` is
  *still* `0.0`, so the on/off is not a readout either — one write bumped its clock without ever
  recording a value that changed.
- **So the record is not a position, it is the last percentage somebody commanded**, and it goes
  wrong the moment a voice open/close moves the curtain past it. That is what the panel showed on the
  wall this morning, and what the Yandex app shows too — it is one record, and both read it.

A third command settled the last of it. **"открой шторы" at 10:57 wrote `on_off: true`, with
`last_updated` *and* `state_changed_at` both stamped** — the value changed, so both moved, which is
the pair of timestamps Yandex documents. The close at 10:53 had moved only `last_updated`, because
`false` was already `false`. So the value is written on every command and not merely on a change,
and the clock says which command was last.

**What the panel does with it: the newer of the two commands is the position.** Neither capability is
a sensor, so there is nothing to prefer the percentage for except that it carries a number — and an
open or close is a position too, at the end of travel *this device reported*. It is right about all
three commands above within one poll, where waiting on the percentage was wrong for two of them.

The morning's "открой шторы" remains the odd one out: it left no trace on either capability, which
neither this rule nor any other can catch. That is what the hour expiry is still for.

**The workaround needs no repair.** A scenario in Дом с Алисой on a phrase of your own, whose action
sets the curtain to a percentage rather than on/off, keeps the record — and therefore the app, and
this panel — correct.

### The shared model, and why it grew

`range` is the first non-boolean state in the panel's device model — the bulbs only ever needed a
`Boolean`. Rather than a curtain-shaped field, `Device` now carries **every** range the poll
returned, keyed by instance (`open`, `brightness`, `temperature`), alongside a `kind` that says
which tile group the device belongs to. One `/v1.0/user/info` call is the whole house, so the AC's
`range/temperature` is already parsed and waiting.

`Mode` and `Toggle` now sit alongside `Range`, added the same way: `Device` carries **every** mode
and **every** toggle the poll returned, keyed by instance, not an AC-shaped field. A `Mode` holds
`current: String?` and `available: List<String>` — the values the device itself listed — and a
`Toggle` holds `isOn: Boolean?`. Both carry the same two timestamps as everything else.

`YandexClient.devices()` now returns every kind the panel has a tile for, told apart by `kind`,
instead of bulbs only. The tile groups filter it. Each group polled separately for a while, which
made the panel send three identical `/v1.0/user/info` calls per interval where it had made one —
a real cost against a rate limit nobody has published. **That is now one call: `YandexPoll` reads
the house and hands the same device list to all three groups**, and the re-read after a toggle or
a set goes through it too, so an action costs one read rather than one per group.

What is shared is the fetch only. Each group still holds its own tiles, its own error and its own
ages: the bulb, the curtain and the AC were last read days apart and the strips never at all, so
one "last read" for the panel would be a lie about most of it. A failed poll is one failure that
reaches every group at once, each keeping the values and ages it already had. Adding the strips as
a fourth group cost no fourth call, which is the whole point of the shape.

> ⚠️ **`POST /v1.0/devices/actions` with `devices.capabilities.range` has never been sent to the
> real curtain.** The body shape is from the capabilities docs and the code is tested against the
> fixture and a loopback socket; nothing here proves the curtain moves, that `open` means "percent
> open" rather than "percent closed", or how long it takes to report the new position back. The
> `on_off` path was verified on the tablet on 2026-08-15 — this one has not been.
>
> **Update, same day:** a `range` action *has* now been accepted by Yandex for real — `temperature`
> on the air conditioner, see "Run on the tablet … the AC tile". So the request shape is no longer
> in doubt. The three curtain-specific questions above still are: nothing has been sent to the
> curtain itself.

### The air conditioners, as recorded

Three of them, all `devices.types.thermostat.ac`, all `household-flat`, all the same
Hisense `AS-13UW4RXVQH01(B)`: `ac-01` (Детская), `ac-02` (Спальня), `ac-03` (Зал). Each carries
eight capabilities — one `range/temperature` (`16..32`, precision `1`,
`unit.temperature.celsius`, `random_access: true`), one `on_off`, three `mode`
(`thermostat`, `fan_speed`, `swing`) and up to three `toggle` (`ionization`, `keep_warm`,
`backlight`) — plus a `properties` entry, a `devices.properties.float` for the *measured* room
temperature whose `state` is `null` on all three and which nothing reads yet.

**What actually carries state is not the same on the three units**, and the difference is the whole
reason `null` had to be modelled rather than defaulted:

| | `range/temperature` | `on_off` | the three `mode`s | the `toggle`s |
| --- | --- | --- | --- | --- |
| `ac-01` | `18`, read 1778169164 | `false`, read 1785174334 | **all `"state": null`** | all three present, **all `null`** |
| `ac-02` | `20` | `false` | `cool` / `turbo` / `horizontal`, `last_updated` `0.0` | `ionization` `false`, `keep_warm` `false`, `backlight` **`null`** |
| `ac-03` | `16` | `false` | `cool` / `auto` / `horizontal` | `ionization` `false`, `keep_warm` `false` — **no `backlight` capability at all** |

- **A `mode` with `"state": null` lists what the device accepts without saying what is running.**
  `ac-01`'s `thermostat` names `fan_only, heat, cool, dry, auto` and reports none of them. The
  panel reads that as unknown; taking the first of the list would put "fan_only" on the wall for a
  unit nobody has asked to blow. The available values come from `parameters.modes` on the device,
  never from a constant here.
- **A capability that is absent is not a capability that reported nothing.** `ac-03` has no
  `backlight`; `ac-01` has one that has never reported. `Device.toggles` distinguishes them: a
  missing key versus a key whose `isOn` is `null`.
- **`ac-02`'s modes have real values with `last_updated: 0.0`** — the same "read, but no recorded
  time" shape the curtain showed on `state_changed_at`, and another reason `0.0` is `Reading.Never`
  rather than the epoch.
- **The two readings on one tile are 81 days apart.** On `ac-01` the on/off was read 2026-07-27
  and the target temperature on 2026-05-07. One age for the tile would have to lie about one of the
  two values, so the tile prints two: `off · 2 h ago · 18 °C · 81 d ago`. (`ac-03` is the opposite
  case — every one of its capabilities carries the same 2026-08-15 00:56 timestamp.)
- **`°C` is printed only when the device named the unit.** All three do; a range that names none
  gets the bare number, which is the shape the TV's volume range already has in this same response.

The tile keeps only the most useful secondary shortcut — fan speed — while the sheet shows every
advertised mode and toggle. Live checks confirmed thermostat, fan speed, swing and ionization action
bodies; a mode that has never reported remains visibly unselected rather than defaulting to the
first advertised value.

### Run on the tablet, 2026-08-15 19:55–19:58 — the AC tile

Installed on the wall tablet (SM-T875) and driven from the panel's own UI, on the flat's Wi-Fi.
Live `/v1.0/user/info` matched the fixture value for value, which is the first confirmation that
the redaction is faithful.

**Observed, as rendered:**

| tile | status line |
| --- | --- |
| Residential air conditioner | `off · 18 d ago · 18 °C · 100 d ago` |
| Кондиционер в спальне | `off · 7 d ago · 20 °C · 27 d ago` |
| Кондиционер в зале | `off · 15 h ago · 16 °C · 15 h ago` |

The 81-day gap between the two readings on the first unit is real and now visible on the wall: 18 d
against 100 d. Each slider sat at the reported target, not at the bottom of its range.

Then, on **Кондиционер в зале** only, through the tile: setpoint 16 → 24, on, off, setpoint back to
16. It ended exactly as it started.

So, as recorded fact:

- **`POST /v1.0/devices/actions` works end to end for `devices.capabilities.range`.** This closes
  the ⚠️ that stood over the curtain change: the body shape written from the docs is accepted by
  Yandex for `instance: temperature`, with `"value": 24` as a whole number. The tile repainted to
  `off · just now · 24 °C · just now`.
- **A range action is accepted while the unit is off.** That was an open question above; it is
  answered for this Hisense. The setpoint changed with nothing starting up.
- **`on_off` works for the AC too**, and the tile repainted to `on · just now`.
- **An action on one capability refreshes `last_updated` on the device's *other* capabilities.**
  The setpoint change alone moved the on/off's age from `15 h ago` to `just now`, though no
  `on_off` action had been sent. Mechanism unknown — Yandex may re-read the whole device — but it
  means the 81-day gap on a tile collapses after any write to that device.

**Not observed, and therefore not claimed.** The action response body was still not captured — the
client parses `status` and `request_id` but logs neither, so `DONE`-on-accept versus
`DONE`-on-applied is *still* open. Nobody was in the room, so whether the compressor physically
started when the tile said `on` is unconfirmed; only the cloud's own report was seen. Nothing here
measures 429 behaviour, the three-poll cost, or hours of running.

### The light strips, as recorded

Two of them, both `devices.types.light.strip`, both `household-flat`, both GLEDOPTO `GL-C-009P`
through the Yandex hub (`skill-04`): `light-strip-01` (Зал, "Подсветка в зале") and
`light-strip-02` (Детская, "Подсветка в детской"). Built against the fixture only — **nothing in
this section has been run against a real strip.**

Each carries four capabilities: `on_off`, `range/brightness` (`1..100`, precision `1`,
`unit.percent`, `random_access: true`), `color_setting`, and a `zigbee_node` nothing reads.

| | `on_off` | `range/brightness` | `color_setting` |
| --- | --- | --- | --- |
| `light-strip-01` | `true` | `26` | `temperature_k` = `2700` |
| `light-strip-02` | `true` | `100` | **`"state": null`** |

- **`devices.types.light.strip` is a separate type, not a sub-type.** The bulb tile's exact match
  meant both strips were dropped in `YandexClient` and rendered as nothing at all. They are now
  their own `DeviceKind`, mapped in the same `KINDS` table.
- **Every capability on both strips carries `last_updated: 0.0`** — brightness, on/off and colour
  alike. So both tiles read `on · 26% · never read`, values present and read times absent — one age
  for the tile, which is the oldest of the three and here is the only one there is. These are the first tiles where that is the whole story rather than one field of it, and
  the reason `Reading.Never` existed before the strips did.
- **Brightness is the existing `Range`,** the same model and the same `Bounds.snap` the curtain's
  `open` percent uses; the action is the same `POST /v1.0/devices/actions` with
  `devices.capabilities.range` and `instance: brightness`. Nothing new was added for it.
- **The range is `1..100`, not `0..100`.** The bottom of a strip's brightness is dim, not off, so a
  slider dragged to the bottom hands over a `0` the device never offered. Snapping turns that into
  `1` before it is sent, exactly as the AC's `40` becomes `32`.
- **`color_setting` is parsed and modelled.** A Kelvin strip still prints
  `2700 K · not controllable`, its age folded into the one the status line prints; the client can
  send colour actions, but the sheet exposes them only for a bulb that advertises RGB/scenes. The
  capability differs from `range`/`mode`/`toggle` in one way that mattered: it names its instance
  only inside `state`, never in `parameters`. `light-strip-02`'s `state` is `null`, so it has
  neither instance nor value, and dropping such a capability would tell the tile the strip has no
  colour when what it has is a colour that has never reported. It is kept, with a null instance.
- **Two colour shapes are in the recorded response**, both handled: `temperature_k` (a Kelvin
  number, on the strips and most bulbs) and `rgb` (a packed `0xRRGGBB`, on `light-21` — `16777200`,
  printed `#FFFFF0`). The `parameters` also carry `temperature_k: {min, max}` (`1996..6369` on the
  strips) and, on `light-21`, a `color_scene` list of four scenes. The panel preserves both:
  temperature bounds explain the read-only Kelvin surface, while the RGB bulb's sheet presents the
  four advertised scenes.

**Left out on purpose:** Kelvin control for the bulbs and strips that the per-device endpoint
reported offline during the live check. RGB and scene control are enabled only on the capable bulb
whose writes reflected in the next read.

## Run on the tablet

2026-08-15, panel installed on the wall tablet, on the flat's Wi-Fi. First time any of this ran
outside tests.

**Observed:** the panel came up as a list of devices, each with an on/off switch. One bulb was
toggled from its switch and the physical bulb followed.

So, as recorded fact:

- **`POST /v1.0/devices/actions` works end to end for `devices.capabilities.on_off`.** The action
  body above is accepted as written, the OAuth token from `local.properties` carries `iot:control`
  as well as `iot:view`, and the action reaches a real bulb through Yandex's cloud. Until now the
  whole write path existed only against the fixture.
- **`GET /v1.0/user/info` works from the tablet**, not just from a laptop — same Wi-Fi, same token,
  and enough of the response parsed for tiles to render.

**Not observed, and therefore not claimed here.** This was a "does it turn the light on" check, not
a measurement. Nothing below was reported, so nothing below is written down as true: the response
body of the action call, how long the repaint took or whether the tile briefly showed the old state,
whether staleness/"never read" rendered correctly, what happens on a failed poll, whether the other
three households were correctly filtered out, and how the panel behaves over hours rather than one
tap.

### Run on the tablet, 2026-08-31 08:27–08:30 — the capability controls, rendered

`installDebug` onto the wall tablet (SM-T875) with the capability-control work in the tree, on the
flat's Wi-Fi. **This was a rendering check only: not one action was sent.** The writes themselves
were verified the day before against live devices — see "Live capability verification — 2026-08-30".
Every value below is a live poll, not the fixture.

**The AC's detail sheet, `Кондиционер в зале`:**

| Row | Value | Age |
| --- | --- | --- |
| power | `off` | 1 h ago |
| target | `26 °C` | 1 h ago |
| room | **`28.0 °C`** | 1 h ago |

- **`room` is a `devices.properties.float` on the wall for the first time.** Nothing read a property
  before this build.
- **`Quiet` is on the Fan row** — `Turbo / High / Medium / Low / Quiet / Auto`, with `Auto`
  selected. This is the value Yandex's own documented `fan_speed` list does not contain, rendered
  from `parameters.modes`. Had the list been hardcoded from the docs, this chip would not exist.
- **There is no `Backlight` chip**, because `ac-03` has no `backlight` capability — while `ac-01`
  has one that has never reported. The "absent capability versus capability that reported nothing"
  distinction survives all the way to the screen.
- Thermostat showed `Cool`, Swing showed `Auto`. All three ages equal, consistent with the
  2026-08-15 finding that a write to any capability refreshes `last_updated` on all of them.

**The bulbs, and the point that they are not one device.** With the `7 lamps` group expanded, the
four shapes in the table above are visibly four different tiles:

| Tile | Rendered |
| --- | --- |
| `Споты в коридоре` (TS0001 relay) | `on · never read` — **no slider at all** |
| `Трек в коридоре` (GLEDOPTO) | `on · 100% · never read` + `unknown` colour |
| `Акара в коридоре` (Aqara, `rgb`) | `off · 1% · 16 h ago` + **`#FFFFF0`** |
| `Лампа 4` (T-01) | `on · 5% · 4500 K`; Лампы 1–3 at `2700 K` |

`#FFFFF0` is `16777200` — the packed value in the recorded response, printed as a hex colour. The
Aqara's sheet carries four RGB swatches and the four scene buttons `Candle / Rest / Movie /
Sunrise`, which are its `color_scene` ids and nothing else's, and **three separate ages** — power
13 h, brightness 16 h, colour 13 h. One age per tile would have had to lie about two of them.

**Also seen, live:** `Residential air conditioner` at `off · 18 °C · 115 d ago` — the stale-reading
case, now 115 days rather than the fixture's 81. `Подсветка в детской` at `on · 100% · never read`
with colour `unknown · not controllable`, which is `light-strip-02`'s permanently-null
`color_setting` on the wall, showing "unknown" rather than inventing a Kelvin. The five Tuya
recuperators rendered with their own per-device ages (1 h, 12 h, 18 d, 23 h) alongside temperature
and humidity.

**Not observed, and therefore not claimed.** No action of any kind was sent during this run, so
nothing here adds to or subtracts from the footings in "The action body, per capability". Nothing
was measured — not repaint latency, not 429 behaviour, not hours of running. `logcat` carried no
exception from `ru.domovoy` across install, launch, three sheets and a poll cycle, which is the
whole of what the log is being claimed for.

## Open questions before writing code

- ~~What does `/v1.0/user/info` actually return for this account?~~ Answered: see "Coverage,
  measured". Fixture at `app/src/test/resources/yandex/user_info.json`.
- Does a `devices.capabilities.*` action come back `DONE` before the device has physically changed
  state? If yes, the tile must not report success on `DONE` alone. **Still unknown.** The tablet run
  proved the action arrives at the bulb, but the response body was not captured and the ordering was
  not timed — one tap that eventually works cannot distinguish `DONE`-on-accept from
  `DONE`-on-applied. Answering it needs the action response logged next to the moment the bulb
  visibly changes. Until then the tile keeps repainting from a fresh `/v1.0/user/info`. The AC run
  did not settle it either: nobody was in the room to see the unit start, and the response body is
  still not logged.
- Actual 429 behaviour under a 5 s poll. The three-calls-per-interval part of this is gone — the
  panel is back to one `/v1.0/user/info` per interval, see "The shared model, and why it grew" —
  but what Yandex allows for that one call is still unmeasured and still unpublished.
- Why every `mode` and `toggle` on `ac-01` reports `null` while `ac-03` reports all of them. Is it
  the unit, the skill, or a state Yandex simply loses? The panel shows unknown either way, but the
  answer decides whether a mode is ever worth putting on the tile.
- ~~What a `devices.capabilities.mode` / `toggle` / `color_setting` action body looks like, and
  whether this flat accepts it~~ — answered for AC modes, ionization, RGB, and scenes in "Live
  capability verification". Kelvin and brightness reached the endpoint while the tested lights
  were offline but did not reflect, so their physical behavior remains unconfirmed.
- Whether `light-strip-02`'s permanently `null` colour state is the device, the skill, or the same
  thing that leaves every `mode` on `ac-01` null.
- **Can the Станция Миди's light ring actually be set?** Its `color_setting` is `retrievable: false`
  *and* `reportable: false` — write-only, unique in this response. Sending one `scene` settles both
  whether it works and what a tile for a control that can never be read back would even show.
- **Is `devices.types.switch` worth a tile at all here?** Both of the flat's are buttons, one with an
  empty `capabilities` array, and their `button` event is stateless — so a poll cannot see a press.
  If the answer is "battery only", it belongs on some other tile rather than one of its own.
- Whether the group and scenario endpoints work for this account — neither has been called. "All
  track lights off" is one group call against seven device calls, and a scenario is a bodyless POST.
  Both come with a question attached: a group capability carries no timestamp at all, and a scenario
  carries no `household_id`, so neither can be put on the wall the way a device tile is.
- Whether `devices.types.light.strip` and `devices.types.light` should stay two tile types at all.
  In this response they are one shape: 13 of the 18 flat bulbs carry the same `on_off` +
  `range/brightness` + `color_setting` trio as the strips, and `light-21` even carries the `rgb`
  colour. Nothing separates them but the type string; their different art and quick actions are a
  panel design decision rather than a capability-model limitation.
- ~~Does `open` on the curtain mean percent *open* or percent *closed*, and does 0 mean shut?~~
  Answered on the real curtain: 0 is closed and 20 is partly open.
- **Can a move of the curtain be seen at all — even one Yandex made itself?** Not so far. A station
  opened it and an hour later `range/open` still held the panel's own last write and `on_off` had
  still never reported, while a light on the same hub reported an on/off within minutes (read live
  2026-08-31, above). The next step is watching the API across a station command, `on_off` and
  `range` separately. Until then the panel cannot know where this curtain is between its own writes,
  and says so rather than guessing.
- ~~Does `/v1.0/devices/{id}` carry `state` (`online`/`offline`) when the list call does not?~~ Yes;
  it reported every tested Kelvin bulb and strip offline. The panel does not spend an extra call per
  tile during polling, so this remains diagnostic information rather than a tile field.
- What is `state_changed_at` when `last_updated` is `0.0`, and which of the two should a tile show?
  Partly narrowed by the AC run: a write to *any* capability appears to refresh `last_updated` on
  all of that device's capabilities, so the two diverge only while a device is left alone.

The curtain direction and per-device online-state questions were closed by the 2026-08-30 run.

## Sources

- [Управление устройствами по API](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-quickstart)
- [Получение полной информации об умном доме](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-user-info)
- [Получение информации о состоянии устройства](https://yandex.ru/dev/dialogs/smart-home/doc/concepts/platform-device-info.html)
- [Управление умениями устройств](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-capabilities)
- [Управление запуском сценария](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-scenario)
- [Регистрация приложения — OAuth для Яндекс ID](https://yandex.ru/dev/id/doc/ru/register-client)

Read for "What every type of device can be driven with", 2026-08-30 — the per-capability pages and,
where one exists, its list of instances. These pages are written from the *provider* side, so their
own examples wrap the state in a `payload`/`capabilities` envelope the control API does not use; what
carries over is the `state` object, which is identical in both directions.

- [`on_off`](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/on_off) ·
  [`range`](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/range) ·
  [instances](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/range-instance)
- [`mode`](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/mode) ·
  [instances and their values](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/mode-instance)
- [`toggle`](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/toggle) ·
  [instances](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/toggle-instance)
- [`color_setting`](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/color_setting)

## Recorded responses

`GET /v1.0/user/info`, 2026-08-15, `200`. Full redacted body:
[`app/src/test/resources/yandex/user_info.json`](../app/src/test/resources/yandex/user_info.json).

Redaction is mechanical and structure-preserving: every device / room / group / scenario /
household / skill id replaced with a readable stand-in (`light-01`, `room-08`, `household-flat`),
`external_id` and `quasar_info.device_id` regenerated, `aliases` emptied, and the four household
names — which were place names — replaced. Timestamps, capability parameters, ranges, colour
temperatures and the nesting are untouched, because those are the parts a parser has to survive.

One light, abridged:

```json
{ "id": "light-01", "name": "Лампа 4", "type": "devices.types.light",
  "external_id": "ext-light-01", "skill_id": "skill-02",
  "household_id": "household-flat", "room": "room-08", "groups": ["group-03"],
  "capabilities": [
    { "type": "devices.capabilities.color_setting", "retrievable": true, "reportable": true,
      "state_changed_at": 1762006164.6363142, "last_updated": 1784883564.0,
      "parameters": { "temperature_k": { "min": 2700, "max": 6500 } },
      "state": { "instance": "temperature_k", "value": 4500,
                 "internal_state": { "color_id": "white" } } },
    { "type": "devices.capabilities.on_off", "parameters": { "split": false },
      "state_changed_at": 1778228459.0, "last_updated": 1784883564.0,
      "state": { "instance": "on", "value": true } }
  ] }
```

Note `internal_state` inside a capability `state` — also undocumented, also present.
