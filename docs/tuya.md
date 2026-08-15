# Smart Life (Tuya)

**Scope:** the recuperators, and nothing else. Few devices, and nobody watches a ventilation
setpoint change live — so a slow poll is honest here rather than a compromise, which is what makes
the metered allowance below survivable.

Read from the public docs on 2026-08-15. **Nothing has been called yet.**

"Smart Life" is one of Tuya's white-label apps. The API we would use is the Tuya Cloud API; the
user's devices reach it by linking their Smart Life account to our cloud project.

## Verified in the docs

**Official third-party API: yes**, and it is the best-documented of the five. Region hosts:

| Region | Host |
| --- | --- |
| Europe | `https://openapi.tuyaeu.com` |
| China | `https://openapi.tuyacn.com` |
| West / East US | `https://openapi.tuyaus.com` / `https://openapi-us-e.tuyaus.com` |
| India | `https://openapi-in.tuyacn.com` |

**Auth: client credentials + a signed request.** Not user OAuth in the usual sense.

- `POST /v1.0/token?grant_type=1` with headers `client_id`, `sign`, `t`, `sign_method: HMAC-SHA256`.
  Returns `access_token` (~2 h) and `refresh_token` (~30 d).
- Business calls, e.g. `POST /v1.0/devices/{device_id}/commands`, take `client_id`, `access_token`,
  `t`, `sign`, `sign_method`.
- Signature: `str = client_id + access_token + t + nonce + stringToSign`, then
  `HMAC-SHA256(str, secret)` **uppercased**. `access_token` is the empty string for the token call
  itself. `nonce` is optional (empty string if unused).
- `stringToSign = HTTPMethod \n Content-SHA256 \n Optional_Signature_key \n URL`, where the method
  is uppercase, `Content-SHA256` is the SHA256 of the body (lowercase hex; the well-known
  `e3b0c442…b855` for an empty body), and URL includes the query string with parameters sorted.
- `t` is a 13-digit millisecond timestamp.

**How the user's devices get in:** create a cloud project (Cloud → Development), development method
"Smart Home", correct data centre; add Industry Basic Service, Smart Home Basic Service and Device
Status Notification; then Devices → Link App Account → Add App Account and scan the QR code from
the Smart Life app's "Me" screen. `client_id` / `client_secret` come from the project Overview, the
UID from Devices → Link Tuya App Account.

**Cost — this is the catch.** Cloud calls need a resource pack, which only comes with an IoT Core
subscription. The Trial Edition:

- 26,000 API calls and 68,000 messages per month
- **max 50 devices, max 10 controllable devices**
- 1 data centre, no log backtracking
- commercial use prohibited
- **no overage at all** — "Once the allowance is reached, the service will be suspended until the
  allowance is refreshed next month"

Paid tiers (Flagship / Corporate) charge overage at $3.15 / $2.97 per million API calls and
$1.24 / $1.17 per million messages, and require paid renewal when the contract expires.

26,000 calls/month is ~36/hour — a poll every 5 seconds is 720/hour and blows the allowance in about
a day and a half. **The trial quota, not the API, is the design constraint here.**

**Push: yes — Tuya Message Service, over Pulsar.** Device registration, data reports and offline
events are pushed to a subscriber. It is a modified Apache Pulsar with a custom auth algorithm and
dynamic tokens; you consume it with Tuya's SDKs (Java, Go, Node.js, C#), configured with
`ACCESS_ID`, `ACCESS_KEY` and a data-centre-specific `PULSAR_SERVER_URL`. Messages arrive as
`{protocol, pv, t, data, sign}` where `data` is AES-encrypted with part of the Access Secret.
Enabled per project under the Message Service tab; test and production subscriptions are separate.

## Second-hand — repeated in support/community pages, not confirmed on a doc page

- A one-month free trial of the Device Connection Service for accounts registered after
  2021-10-15, applied for from the console. How the trial ends, and whether it can be re-applied
  for, is the single most important unknown here.
- The separate "self-developed Smart Life app" SDK product (free dev edition, $5,000/year official
  edition) is **not** what we need — that is for shipping your own branded Tuya app. Ignore it.

## Inferred / not verified

- **Pulsar is not realistic on the tablet.** The SDKs are server-side (Java/Go/Node/C#); there is no
  Android/Kotlin client, and a persistent Pulsar consumer on a wall panel that sleeps and loses
  Wi-Fi is the wrong shape. Same conclusion as Aqara: **poll**, and poll slowly — say once a minute
  plus an immediate re-read after a tap, which lands around 45k calls/month and already exceeds the
  trial. This needs measuring before committing.
- **Local path exists but is unofficial.** LocalTuya-style control over the LAN uses a per-device
  "local key" fetched from the cloud and an undocumented binary protocol. It is real and widely
  used, it survives an internet outage, and it is exactly the kind of thing AGENTS.md means by
  "an endpoint nobody has verified". Not for the first cut.
- The signing scheme is the fiddliest of the five — sorted query strings, body digests, uppercase
  hex. Expect to spend the first session getting `1004 sign invalid` and nothing else.

## Ecosystem health

Very alive; the largest of the five by device count, docs are extensive and versioned, SDKs in five
languages. The friction is commercial, not technical: quotas, editions and renewals.

## Open questions before writing code

- Does the trial expire outright, or just reset monthly? This decides whether Tuya is viable at all.
- Exact API-call cost of one panel refresh — measure before choosing a poll interval.
- Which data centre this account is in.

## Sources

- [Pricing / IoT Core editions](https://developer.tuya.com/en/docs/iot/membership-service?id=K9m8k45jwvg9j)
- [Sign Requests](https://developer.tuya.com/en/docs/iot/singnature?id=Ka43a5mtx1gsc)
- [Sign Requests for Cloud Authorization](https://developer.tuya.com/en/docs/iot/new-singnature?id=Kbw0q34cs2e5g)
- [Device Message Subscription](https://developer.tuya.com/en/docs/iot/subscribe?id=Kbwtw7fhhjabw)
- [Manage Message Service](https://developer.tuya.com/en/docs/iot/manage-messages?id=Ka49p7loog3ze)
- [Manage API Services](https://developer.tuya.com/en/docs/iot/applying-for-api-group-permissions?id=Ka6vf012u6q76)
- [How can I use Tuya Cloud Development for free?](https://support.tuya.com/en/help/_detail/K9zsowlj19oaf)

## Recorded responses

_None yet._
