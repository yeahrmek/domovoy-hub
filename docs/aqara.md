# Aqara

**Scope:** the door lock and its hub — **read-only**. The tile reports locked/unlocked, battery and
the last event; it has no unlock, and per AGENTS.md it does not get one. A hallway panel that
unlocks the front door can be tapped by anyone standing in the hallway.

Reading is also the half that certainly works. Whether the API exposes any lock *action* for this
model is unverified — check the console under Application Management → Resource Authorization,
where the resources available for the lock are listed. Lock vendors commonly withhold remote
unlock, gate it behind extra review, or allow it only on specific models.

Not to be confused with the Domonap door release: that is the building entrance, this is the flat.

Read from the public docs on 2026-08-15. **Nothing has been called yet** — the developer project was
submitted for review on 2026-08-15 and no keys exist until it is approved. See "Registration, in
practice".

## Verified in the docs

**Official third-party API: yes** — the Aqara Developer Platform (`developer.aqara.com`, docs at
`opendoc.aqara.com`). It exposes HTTP APIs for device state query, remote control and automation
config, plus a message push service that pushes real-time device data **to a third-party server**.

**Host — one per region, and Russia has its own:**

| Region | Base URL |
| --- | --- |
| Russia | `https://open-ru.aqara.com/v3.0/open/api` |
| Europe | `https://open-ger.aqara.com/v3.0/open/api` |
| China | `https://open-cn.aqara.com/v3.0/open/api` |
| USA | `https://open-usa.aqara.com/v3.0/open/api` |
| Korea / Singapore | `https://open-kr…` / `https://open-sg…` |

The region is fixed by where the user's Aqara account lives. Wrong region = the account does not
exist as far as that host is concerned.

**Auth: OAuth 2.0 authorization_code**, plus a per-request signature on top.

- Authorize: `https://${domain}/v3.0/open/authorize` — browser redirect, user logs in with their
  Aqara account, `code` comes back in the redirect and is valid **10 minutes**.
- Exchange `code` → `accessToken` + `refreshToken`; refresh when it expires.
- Only a real Aqara account works in OAuth 2.0 mode. The alternative "virtual account" mode
  (`config.auth.createAccount`) bridges a third-party account system, cannot be used in the Aqara
  Home app, and requires their SDK — not our case.

**Every request** carries these headers: `Appid`, `Keyid`, `Nonce` (fresh per request), `Time`
(ms), `Sign`, and `Accesstoken` (optional — absent for the calls that do not need a user).
Optional `Lang` (`en` / `zh`). Body is always `{ "intent": "<name>", "data": { … } }` — one URL,
the operation is in the body. The signature algorithm itself lives on a separate "Signature Rules"
page I did not open; read it before implementing.

**Push: yes, but server-to-server.** Configured in the console under Message Push Settings:

- **HTTP push** — Aqara POSTs to a URL you own. The address is verified periodically; if the
  failure rate exceeds **5% within 5 minutes** you get an SMS/email and push is **suspended half an
  hour later** until you re-enable it in the console.
- **Message queue push** — a modified RocketMQ, last **12 hours** of messages retained. Not
  available in private/on-premise deployments.

Three subscription modes: receive-all, receive-all (trait), and user-defined. In user-defined mode
you call `config.resource.subscribe` with a `subjectId` (the did) and a `resourceIds` array
(e.g. `4.1.85`, `14.1.85`); `config.resource.unsubscribe` cancels. Turning on a receive-all mode
**invalidates** existing per-resource subscriptions and makes the subscribe interface refuse calls.
`query.push.errorMsg` returns failed pushes, past 12 hours only.

Webhook signature: order `token`, `appkey`, `nonce`, `time`, join as `appkey=…&nonce=…&time=…`,
append `appSecret`, lowercase the whole string, take a 32-bit MD5. Signing is **off by default**.

**Registration cost:** the account itself is free. You register on `developer.aqara.com` (an
existing Aqara Home account works), pick individual or enterprise, create a project, and the
project goes through **review** — keys (`AppId`, `AppKey`) only appear under Project Details → Key
Management after approval. No published price list. Aqara's own FAQ says registration is not
restricted by region.

## Registration, in practice

**2026-08-15 — project submitted, awaiting review.** The console's confirmation reads, verbatim:

> Your application has been submitted successfully,Please wait patiently for the result of review.

So the review gate in "Registration cost" above is real and is not instant. No `AppId` or `AppKey`
exists yet, which means **nothing in this file below the auth section can be verified until the
review returns** — every endpoint, signature and quota here is still read from documentation only.

Not yet known, to be filled in when the result arrives:

- how long the review actually took, and whether it was approved first time
- whether anything beyond the form was asked for (justification, company details, a callback URL)
- which resources the lock exposes under Application Management → Resource Authorization — the
  thing that settles whether this model offers any action at all, and therefore whether the
  read-only tile is a choice or the only option
- whether the personal-account limits in the section below are shown anywhere in the console

## Second-hand — from the forum and doc summaries, not confirmed on the page itself

- Personal (non-enterprise) developer accounts are reported to be capped at **under 50,000 API
  calls** and to have **no App SDK access**. Whether that cap is lifetime or per period was
  unclear even to the person who asked.
- Message pushes are counted per account type since 2023-08-28, with a free developer quota of
  **100,000/month and no more than 2 million cumulative**.

Both numbers change the calculus if true. Confirm on the console before relying on them.

## Inferred / not verified

- **Push is useless to us as an Android-only app.** It needs a public HTTPS endpoint or an MQ
  consumer. A wall tablet is neither. Either we poll, or the project grows a server component —
  and a server is not in the AGENTS.md structure. **Plan on polling.**
- **No supported local path.** There is no documented local LAN API. What exists:
  - Hub M3 has an official Edge/LAN mode (Profile → Settings → Edge Mode) but that is for the
    *Aqara app* and for automations running on the hub, not an API for us.
  - Hub M3 can act as a Matter bridge; Hub M2 exposes devices over HomeKit (HAP). Both are real
    local protocols, but speaking Matter or HAP from an Android panel is a much larger project
    than an HTTP client.
  - The community "developer mode / developer key" LAN protocol dates to the old Xiaomi-era
    gateways and is widely reported as removed from current apps.
  So: cloud-only for us, unless someone decides Matter is worth it.
- The OAuth redirect is a browser flow, so on Android it means a Custom Tab plus a redirect URI
  registered in the console. Fine, but it is real setup work, and the console has an address
  whitelist that has to match.
- The signature (`Sign`) plus token refresh plus per-region host means this client is meaningfully
  more code than Yandex's.

## Ecosystem health

Alive and commercially serious — the platform advertises 5,000+ registered companies, docs exist in
English and Chinese and are current, servers are deployed in six regions including Russia. But it is
built for *companies integrating Aqara*, not for one person with a hallway tablet: the review step,
the enterprise/personal split and the server-shaped push all point the same way.

## Open questions before writing code

- Which region does this account actually live in, and does `open-ru` answer for it?
- What does the project review ask for, and how long does it take?
- Real personal-account quota, from the console rather than the forum.
- Signature rules — the actual algorithm.

## Sources

- [API Usage Guide](https://opendoc.aqara.com/en/docs/developmanual/apiIntroduction/APIUsageGuide.html)
- [Aqara Account Authorization Mode](https://opendoc.aqara.com/en/docs/developmanual/authManagement/aqaraauthMode.html)
- [Message Push Mode](https://opendoc.aqara.com/en/docs/developmanual/messagePush/messagePushMode.html)
- [Message Push API](https://opendoc.aqara.com/en/docs/developmanual/messagePush/messagePushAPI.html)
- [Message Push Format](https://opendoc.aqara.com/en/docs/developmanual/messagePush/messagePushFormat.html)
- [Project Management](https://opendoc.aqara.com/en/docs/developmanual/manageApplication/manageProject.html)
- [Developer Platform Registration FAQ (forum)](https://forum.aqara.com/t/developer-platform-registration-faq/87)

## Recorded responses

_None yet._
