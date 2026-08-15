# Yandex Smart Home

**Scope:** air conditioner, curtains, bulbs — full integration, and the reference one for the rest
of the panel. The smart speaker is not a tile: it appears in the API, but the IoT API cannot make
it speak or play.

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
skill chose to publish, not everything Mi Home shows — but it does mean the decision should be
re-taken against this list rather than inherited. The Tuya recuperators are **not** in here; that
plan is unchanged.

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
   plain-text `Forbidden` above and blame the scopes. The bulb group shows *«no Yandex token
   stored — set yandex.oauth.token in local.properties and reinstall»*.
4. **Store will not open.** A keystore lost across a restored backup or a wipe fails the poll with
   *«secure storage unavailable: …»* on the tiles rather than taking the panel down.

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

## What the bulb tile actually does with this

Built against the fixture; first run against the live API on the tablet on 2026-08-15, see
"Run on the tablet" below.

- **`devices.types.light` only.** Exact match, so `devices.types.light.strip` (the GLEDOPTO strip
  in the зал) is *not* a bulb tile. 18 of the flat's 29 devices qualify.
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
- **A failed or timed-out poll keeps the tiles on screen** with their last values and adds
  "not updating: …". One `/v1.0/user/info` call is the whole house, so the failure belongs to the
  bulb group rather than to one tile. Every call carries a 10 s call timeout.
- **A toggle is not trusted.** `POST /v1.0/devices/actions` succeeding only means Yandex accepted
  it, so the tile is repainted from a fresh `/v1.0/user/info`, never from the action result — see
  the first open question below, still unanswered. The tablet run shows the round trip works; it
  does not show that the response could have been trusted.
- **The OAuth token** is read at runtime from `EncryptedSharedPreferences`, not from `BuildConfig`
  — see "How the token gets in" below. No token stored is a visible tile error, not an empty poll.

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
instead of bulbs only. The tile groups filter it. **Each group polls separately, so the panel makes
three `/v1.0/user/info` calls per interval where it made one.** That is a real cost with no
published rate limit to check it against. It was kept once because collapsing it means one object
owning the poll that every group reads from — and with the third group now added, that debt is
due: the next change to this integration should be the shared poll, not a fourth caller.

> ⚠️ **`POST /v1.0/devices/actions` with `devices.capabilities.range` has never been sent to the
> real curtain.** The body shape is from the capabilities docs and the code is tested against the
> fixture and a loopback socket; nothing here proves the curtain moves, that `open` means "percent
> open" rather than "percent closed", or how long it takes to report the new position back. The
> `on_off` path was verified on the tablet on 2026-08-15 — this one has not been.

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

> ⚠️ **Not sent to a real air conditioner.** `range/temperature` and `on_off` actions for these
> devices are tested against the fixture and a loopback socket only. Whether the unit accepts a
> target while it is off, and how long it takes to report the new target back, is unknown.

**Left out on purpose:** the modes and toggles are parsed and modelled but are neither shown on the
tile nor drivable — there is no `setMode`/`setToggle` on the client. Adding them means answering
what a `mode` action body looks like for this device and what the tile should show for a mode that
has never reported.

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

## Open questions before writing code

- ~~What does `/v1.0/user/info` actually return for this account?~~ Answered: see "Coverage,
  measured". Fixture at `app/src/test/resources/yandex/user_info.json`.
- Does a `devices.capabilities.*` action come back `DONE` before the device has physically changed
  state? If yes, the tile must not report success on `DONE` alone. **Still unknown.** The tablet run
  proved the action arrives at the bulb, but the response body was not captured and the ordering was
  not timed — one tap that eventually works cannot distinguish `DONE`-on-accept from
  `DONE`-on-applied. Answering it needs the action response logged next to the moment the bulb
  visibly changes. Until then the tile keeps repainting from a fresh `/v1.0/user/info`.
- Actual 429 behaviour under a 5 s poll — and now under **three** calls per interval rather than
  one, see "The shared model, and why it grew". This is the reason the shared poll is owed.
- Why every `mode` and `toggle` on `ac-01` reports `null` while `ac-03` reports all of them. Is it
  the unit, the skill, or a state Yandex simply loses? The panel shows unknown either way, but the
  answer decides whether a mode is ever worth putting on the tile.
- What a `devices.capabilities.mode` action body looks like for this AC, and whether it is accepted
  while the unit is off. Nothing in the panel sends one — no endpoint is guessed here.
- Does `open` on the curtain mean percent *open* or percent *closed*, and does 0 mean shut? The
  panel assumes open, from the instance name alone. One tap on the real curtain settles it.
- Does `/v1.0/devices/{id}` really carry `state` (`online`/`offline`) when the list call does not?
  If it does, an unreachable device costs one extra call per tile to detect.
- What is `state_changed_at` when `last_updated` is `0.0`, and which of the two should a tile show?

The last three are exactly as open as they were before the tablet run — one tap on one bulb touched
none of them.

## Sources

- [Управление устройствами по API](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-quickstart)
- [Получение полной информации об умном доме](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-user-info)
- [Получение информации о состоянии устройства](https://yandex.ru/dev/dialogs/smart-home/doc/concepts/platform-device-info.html)
- [Управление умениями устройств](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-capabilities)
- [Регистрация приложения — OAuth для Яндекс ID](https://yandex.ru/dev/id/doc/ru/register-client)

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
