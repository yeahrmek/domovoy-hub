# Xiaomi (Mi Home / Xiaomi Home)

**Scope:** the vacuum and the humidifier, via a **hosted Mi Home app widget** — there is no Xiaomi
client in this project and no `integrations/xiaomi/` package. The widget shows real state because
the Mi Home app feeds it, and its buttons work; the cost is that it sits in Xiaomi's design
language and its values never reach our device model. A vacuum makes a poor tile anyway —
realistically start / dock / battery, not a map.

The rest of this note is why: the blocker is administrative, not technical.

Read from the public docs on 2026-08-15. **Nothing has been called yet.**

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

- Does Mi Home ship a widget for this vacuum, and does it show battery and status? If not, the
  vacuum falls back to a launcher tile.
- Widget binding needs a manifest change and a user-confirmed bind flow — "ask first" under
  AGENTS.md.
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
