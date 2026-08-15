# Yandex Smart Home

**Scope:** air conditioner, curtains, bulbs — full integration, and the reference one for the rest
of the panel. The smart speaker is not a tile: it appears in the API, but the IoT API cannot make
it speak or play.

Read from the public docs on 2026-08-15. **Nothing here has been called yet** — no request has
been made from the tablet or anywhere else, so there is no recorded response to paste in.
Fill in real JSON under "Recorded responses" as soon as the first call happens.

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
- **Coverage:** whatever the user has linked into their Yandex account shows up, including devices
  that reached Yandex through *other* vendors' Yandex skills. So a Tuya socket linked to Yandex may
  already be reachable here without touching Tuya at all. Worth checking against the real account
  before writing a second integration.

## Ecosystem health

Alive and first-party. Docs are current, in Russian, versioned under `yandex.ru/dev`, and this is
the same platform Alice itself runs on, so it is not a side project that can quietly rot. Russian
availability is not a question the way it is for Aqara/Tuya regions.

## Open questions before writing code

- What does `/v1.0/user/info` actually return for this account? Record it into
  `app/src/test/resources/` with fake ids.
- Does a `devices.capabilities.*` action come back `DONE` before the device has physically changed
  state? If yes, the tile must not report success on `DONE` alone.
- Actual 429 behaviour under a 5 s poll.

## Sources

- [Управление устройствами по API](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-quickstart)
- [Получение полной информации об умном доме](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-user-info)
- [Получение информации о состоянии устройства](https://yandex.ru/dev/dialogs/smart-home/doc/concepts/platform-device-info.html)
- [Управление умениями устройств](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-capabilities)
- [Регистрация приложения — OAuth для Яндекс ID](https://yandex.ru/dev/id/doc/ru/register-client)

## Recorded responses

_None yet._
