# Smart Life (Tuya)

**Scope:** the recuperators, and nothing else. Few devices, and nobody watches a ventilation
setpoint change live — so a slow poll is honest here rather than a compromise, which is what makes
the metered allowance below survivable.

Read from the public docs on 2026-08-15. **First real calls made the same day** — the account is
linked and answering; see "Recorded responses". Everything below marked _verified against the
account_ came from `scripts/tuya-probe.sh`, not from a doc page.

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

- `GET /v1.0/token?grant_type=1` with headers `client_id`, `sign`, `t`, `sign_method: HMAC-SHA256`.
  Returns `access_token` and `refresh_token`. _Verified against the account:_ `expire_time` is
  7200 s, matching the documented ~2 h.
  **It is GET, not POST** — this doc said POST until it was tried. `POST` returns
  `{"code":1108,"msg":"uri path invalid"}`, and so does POST to a path that does not exist at all,
  which is what makes 1108 misleading: it reads like a bad URL but means a bad method.
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
  Wi-Fi is the wrong shape. Same conclusion as Aqara: **poll**, and poll slowly. Now measured: real
  state costs 1 call *per recuperator*, so a refresh is 5, and the trial allowance buys a poll
  roughly every 5–8 minutes. A tap costs a command plus a re-read on top.
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

- Does the trial expire outright, or just reset monthly? Still open — this decides whether Tuya is
  viable at all. Check under Cloud → Cloud Services → IoT Core.
- **Trial allows max 10 controllable devices; the account has 20.** Which 10 count, and what the
  eleventh does when commanded, is unknown. The panel only needs the 5 recuperators, so this may
  never bite — but nothing has been commanded yet.
- **How to write a custom datapoint.** `POST /v1.0/devices/{id}/commands` is the standard-set path,
  and the standard set for this product is just `switch` — so fan speed almost certainly needs the
  thing-model write, `POST /v2.0/cloud/thing/{id}/shadow/properties/issue`. Unverified: writing
  turns a real fan on in a real flat, so it needs a deliberate test, not a probe.
- **Getting `batch/shadow/properties` authorised.** It would cut a refresh from 5 calls to 1, which
  is the difference between a 5-minute and a 1-minute poll. `40001900 No space permission` suggests
  a project authorisation or space-model setting rather than a subscription, but that is a guess.
- ~~What the recuperators expose beyond `switch`~~ — 13 datapoints: speeds, modes, temperature and
  humidity. The v1.0 endpoints hide them; the thing model has them.
- ~~Which data centre this account is in~~ — Central Europe, `https://openapi.tuyaeu.com`.
- ~~Exact API-call cost of one panel refresh~~ — 5 calls, one per recuperator, unless the batch
  route above can be unlocked.

## Sources

- [Pricing / IoT Core editions](https://developer.tuya.com/en/docs/iot/membership-service?id=K9m8k45jwvg9j)
- [Sign Requests](https://developer.tuya.com/en/docs/iot/singnature?id=Ka43a5mtx1gsc)
- [Sign Requests for Cloud Authorization](https://developer.tuya.com/en/docs/iot/new-singnature?id=Kbw0q34cs2e5g)
- [Device Message Subscription](https://developer.tuya.com/en/docs/iot/subscribe?id=Kbwtw7fhhjabw)
- [Manage Message Service](https://developer.tuya.com/en/docs/iot/manage-messages?id=Ka49p7loog3ze)
- [Manage API Services](https://developer.tuya.com/en/docs/iot/applying-for-api-group-permissions?id=Ka6vf012u6q76)
- [How can I use Tuya Cloud Development for free?](https://support.tuya.com/en/help/_detail/K9zsowlj19oaf)

## Recorded responses

Reproduce with `scripts/tuya-probe.sh`, which reads the credentials from `local.properties`; pass a
device id, or any path starting with `/`, to probe further. Device ids, local keys, the account uid,
the WAN address and the coordinates are all in the real responses and none of them are reproduced
here.

### `GET /v1.0/users/{uid}/devices` — verified against the account

One call returns every device with a `status` array inline — but **that array is filtered to Tuya's
standard instruction set, and for this recuperator model it contains only `switch`.** Do not read a
short `status` here as "the device has one datapoint"; see the thing model below. Useful for the
inventory, `online` and `update_time`; not sufficient as the panel's poll.

20 devices came back, of which the panel wants 5:

| What | `category` | `product_name` | Count |
| --- | --- | --- | --- |
| Recuperators — "Бризер" in each room | `xfj` | Heat Recovery Ventilator | 5 |
| Towel rails | `mjj` | (Chinese name), model SYZN168 | 2 |
| Zigbee gateway | `wg2` | Gateway | 1 |
| Zigbee lamps, all offline | `dj` | lamp | 11 |
| Wall switch, offline | `kg` | WD-01CE | 1 |

Shape per device, values replaced:

```json
{
  "id": "<device id>", "uuid": "<uuid>", "local_key": "<16 chars>",
  "name": "Бризер зал", "category": "xfj", "product_id": "<product id>",
  "product_name": "Heat Recovery Ventilator", "model": "",
  "online": true, "sub": false, "time_zone": "+03:00",
  "ip": "<WAN address>", "lat": "<...>", "lon": "<...>",
  "active_time": 1753611469, "create_time": 1753611469, "update_time": 1786783922,
  "status": [{ "code": "switch", "value": false }]
}
```

Notes that matter for the tile:

- The recuperators show only `switch` here, all five `false` and `online: true` — a filtering
  artefact, not the device. The Smart Life app shows temperature, humidity, three fan speeds and
  three modes for the same device, and the thing model below confirms 13 datapoints.
- The towel rails carry `temp_set`, `temp_current` (60 / 56 at the time), `countdown_set`,
  `countdown_left` and `work_state: "heating"` — so this endpoint is rich *when the product happens
  to use standard codes*, which is exactly what makes the recuperators' short list look credible.
- `update_time` is a device-side timestamp, i.e. **the tile can show real staleness** rather than
  "when we last polled". The five recuperators ranged from 3 to 30 minutes old at poll time.
- `online` is reported per device — the 11 lamps and the wall switch were all `false`, so a tile
  needs an offline state regardless of whether the HTTP call succeeded.
- `local_key` is handed out in this very response, so the unofficial LAN path needs no extra call.
  It is also a secret: this response must never be committed raw, and the fixture derived from it
  has to have the keys replaced.

### `GET /v1.0/devices/{id}/specifications` and `/functions` — verified, and misleading

Both return the same single datapoint:

```json
{ "category": "xfj",
  "functions": [{ "code": "switch", "type": "Boolean", "values": "{}" }],
  "status":    [{ "code": "switch", "type": "Boolean", "values": "{}" }] }
```

**This is wrong about the device, and it does not say so.** These are v1.0 endpoints and they only
report datapoints from Tuya's *standard instruction set*; this product's datapoints are custom, so
they are silently omitted. Agreement between `/specifications` and `/functions` is not corroboration
— both apply the same filter.

**The lesson for the other four vendors too: an endpoint that returns a suspiciously thin device is
evidence about the endpoint, not the device.** The vendor's own app is the check — it showed
temperature, humidity, three fan speeds and three modes for a device the API called a lone switch.

### `GET /v2.0/cloud/thing/{id}/shadow/properties` and `/model` — the real surface

13 datapoints, and they match the Smart Life app one for one:

| dp | `code` | Thing-model name | Type | Access |
| --- | --- | --- | --- | --- |
| 1 | `switch` | ON/OFF | bool | rw |
| 101 | `speed_one` | Low Speed | bool | rw |
| 102 | `speed_two` | Medium Speed | bool | rw |
| 103 | `speed_three` | High Speed | bool | rw |
| 104 | `sleep_mode` | Sleep mode | bool | rw |
| 105 | `huimidity_one` | Low Humidity | bool | rw |
| 106 | `huimidity_two` | Medium Humidity | bool | rw |
| 107 | `huimidity_three` | High Humidity | bool | rw |
| 108 | `huimi` | humidity | value | **ro** |
| 109 | `temper` | temperature | value | **ro** |
| 111 | `in_mode` | Fresh Air Mode | bool | rw |
| 112 | `out_mode` | Exhausted Air Mode | bool | rw |
| 113 | `auto_mode` | Regenerate Mode | bool | rw |

- **`huimi` and `temper` are tenths.** `330` is 33.0 %RH and `279` is 27.9 °C; `typeSpec` says
  `scale: 1`, and `min: 0 / max: 10000` is a nominal range, not a real one. The misspelled codes
  (`huimi`, `huimidity_*`) are the vendor's, not typos here.
- **Speeds and modes are three separate booleans each, not an enum.** Whether the device enforces
  mutual exclusion, or whether two speeds can be true at once, is unverified — nothing has been
  written yet.
- dp 110 does not exist; the numbering has a hole.
- `shadow/properties` carries a per-datapoint `time`, so staleness can be shown per reading rather
  than per tile.

### Batch reads — the awkward part

| Endpoint | Result |
| --- | --- |
| `GET /v1.0/devices/status?device_ids=a,b` | works, **standard-set filtered** → only `switch` |
| `GET /v1.0/iot-03/devices/status?device_ids=a,b` | same, different envelope → only `switch` |
| `GET /v2.0/cloud/thing/batch?device_ids=a,b` | works — device *info*, no properties |
| `GET /v2.0/cloud/thing/batch/shadow/properties?device_ids=a,b` | **`40001900 No space permission`** |

The batch properties route exists — a business error, not `1108` — but the project is not authorised
for it. Until that is sorted, **real state costs one call per device: 5 calls per refresh, not 1.**

Against the 26,000/month trial that is ~5,200 refreshes: a poll every **~8 minutes** around the
clock, or ~5 minutes if the panel only polls 16 h a day. Token refreshes add ~360/month. A tap costs
a command plus a re-read. For a ventilation setpoint that nobody watches change, 5 minutes is
survivable — but it is the constraint that decides the tile, so it belongs in the design, not in a
comment.

### Errors seen

| Code | `msg` | What it actually meant |
| --- | --- | --- |
| 1108 | uri path invalid | wrong **method** — POST on the token endpoint |
| 2009 | clientId is invalid | reached the right route; `client_id` header not recognised |
