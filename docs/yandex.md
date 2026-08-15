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
- Actual 429 behaviour under a 5 s poll.
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
