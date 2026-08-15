# Vendor scope and build order

Desk research, 2026-08-15. No vendor endpoint has been called; nothing has been run on the tablet.
Per-vendor detail and the verified/inferred split live in [yandex.md](yandex.md),
[aqara.md](aqara.md), [tuya.md](tuya.md), [xiaomi.md](xiaomi.md), [domonap.md](domonap.md).

## Scope

| Vendor | Devices | How |
| --- | --- | --- |
| Yandex | air conditioner, curtains, bulbs | full integration |
| Aqara | door lock (+ hub) | full integration, read-only |
| Tuya | recuperators | full integration, metered quota |
| Xiaomi | vacuum, humidifier | hosted app widget, no client |
| Domonap | intercom | launcher tile + notification-driven call |

The Yandex smart speaker is deliberately not a tile: it appears in the API, but the IoT API cannot
make it speak or play — that is a different and largely unofficial surface.

## The evidence behind those choices

| | Yandex | Aqara | Tuya | Xiaomi | Domonap |
| --- | --- | --- | --- | --- | --- |
| Official API for third-party apps | yes | yes | yes | not self-serve | none |
| Auth | Yandex OAuth, `Bearer` | OAuth 2.0 + per-request `Sign` | client_id/secret + HMAC-SHA256 sign | Mi OAuth 2.0, client by review | phone number, private API |
| Account setup | register OAuth app, minutes | register, create project, **wait for review** | register, cloud project, subscribe service, QR-link the app account | register + per-interface review, terms unknown | none possible |
| Cost | free | free tier, quotas unclear | trial free but capped; paid tiers metered | unpublished | n/a |
| Cloud / local | cloud only | cloud only for us (M3 Edge/Matter is not an API) | cloud; unofficial LAN exists | cloud + real LAN + hub MQTT | cloud |
| State push | none documented | yes, but **to a server** (HTTP/MQ) | yes, but **Pulsar, server SDKs only** | yes, MQTT from MIoT Cloud | unknown |
| Push usable from an Android-only panel | no | no | no | probably yes | no |

The push row is the one that decides the architecture. Three vendors have real push and none of it
reaches us: Aqara and Tuya both push to a *server*, and this project has no server. Xiaomi's MQTT
would reach us, and is the one we cannot get credentials for. **Everything is polled**, and tiles
say how old their state is rather than pretending to be live.

Xiaomi is the only vendor whose blocker is administrative rather than technical: a client_id comes
from the Mi Dev Platform by manual review with a written justification, and no page states the
terms for an individual. Rather than wait on that, the vacuum and humidifier get a hosted Mi Home
widget — real state, vendor-maintained, in their design language, invisible to our device model.

## Build order

1. **Yandex** — the reference integration. Register, token, `/v1.0/user/info`, one tile that turns
   a bulb on. Everything the panel needs — shared device model, poll loop, stale-state UI, failure
   states — gets shaped here, unblocked by anyone's review queue.
2. **Domonap** — cheapest high-value feature, but do the experiment first: ring the intercom and
   confirm the app actually posts a notification we can see. If it does, the call path is a
   notification listener plus yielding the screen, and no private API is touched.
3. **Aqara lock** — read-only tile: locked/unlocked, battery, last event.
4. **Tuya recuperators** — measure API calls per refresh against the monthly allowance before
   choosing a poll interval.
5. **Xiaomi widget** — last, and smallest.

**Start the gated accounts now**, in parallel with step 1: Aqara's project review stands between
registration and keys, and Tuya's cloud project needs the Smart Life QR link before anything works.
Both can queue while Yandex is being built.

Two of these need manifest work that AGENTS.md puts in "ask first" — the notification listener for
Domonap, and widget binding for Xiaomi. Raise them before starting, not mid-branch.

## What to verify before any of it becomes code

- **Yandex:** real `/v1.0/user/info` for this account; 429 behaviour under a 5 s poll.
- **Domonap:** does an incoming call post a visible notification, and with what latency?
- **Aqara:** which region host answers for this account; whether the console lists anything beyond
  state for this lock model under Resource Authorization.
- **Tuya:** what one panel refresh costs, and whether the trial expires outright or resets monthly.
- **Xiaomi:** does Mi Home ship a widget for this vacuum, and does it show battery and status?

One shortcut worth ten minutes before step 3: check the Yandex app for Smart Life and Xiaomi
skills. Anything already linked into the Yandex account shows up in `/v1.0/user/info` regardless of
who made it, and could remove a whole integration from this list.
