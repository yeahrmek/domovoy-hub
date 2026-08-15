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
  **the seconds left on the token, not a fresh lifetime** — two calls minutes apart answered 5433
  and then 5385, because the host hands back the token it has already issued rather than minting
  one. So the expiry is the moment of *that* call plus `expire_time`, and a client that assumes a
  flat 7200 will send a dead token. `TuyaClient` takes a minute off it and refetches on the next
  call after that.
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

**What the console actually shows (2026-08-15), and it does not match the above.** Cloud → Cloud
Services → IoT Core → My Subscriptions lists two packs, both effective 2026-08-15 and both expiring
**2026-09-15**:

| Resource pack | Usage / quota | Quota refresh | Expires | Status |
| --- | --- | --- | --- | --- |
| Cloud Develop Base Resource Trial | **0 / 0.2 USD** | Monthly | 2026-09-15 | In service |
| IoT Core | — | — | 2026-09-15, with an **Extend Trial Period** button | In service |

So the allowance is **denominated in money, not in calls** — $0.20 a month of "basic resources",
which the page's own banner says is what API calls consume. Expanding the rows gives the rates, and
with them the conversion the pricing doc does not:

| Line item | Billing rule | What $0.20 buys |
| --- | --- | --- |
| `CLOUD_API` | 2.48 USD / 1,000,000 | ~80,600 calls |
| `CLOUD_API_FOREIGN` | 3.71 USD / 1,000,000 | **~53,900 calls** |
| `CLOUD_MSG` | 0.58 USD / 1,000,000 | ~345,000 messages |
| `CLOUD_MSG_FOREIGN` | 1.46 USD / 1,000,000 | ~137,000 messages |

Which of the two API rates applies is not stated. This project is in Central Europe, so the
`_FOREIGN` rate is the one to plan against — **~54,000 calls/month, twice the documented 26,000**,
and one pot shared with messages (which the panel does not use, since Pulsar is out).

Expanding IoT Core gives the pools, and they match what the API returned: **Device Pool 20 / 50**,
**Controllable Device Pool 0 / 10** (nothing has been commanded yet), **Data Center 1 / 1**.

Three things this settles:

- **The trial does not simply reset forever.** The quota refreshes monthly, but the pack itself
  expires 2026-09-15 — one month after signup. There is an **Extend Trial Period** button, so it is
  extendable; by how much, how often, and whether it needs justification is unknown.
- **The `0 / 0.2 USD` reading after ~20 calls is not a lagging meter — it is rounding.** Twenty
  calls cost $0.00007. One cent is ~2,700 calls at the foreign rate, so the counter cannot visibly
  move at probe volumes. Watching it is only a measurement once the panel has polled for days.
- **~54,000 calls/month is the number the poll interval comes from**, not 26,000:

| Per refresh | Refreshes/month | Poll interval, 24/7 | Poll interval, 16 h/day |
| --- | --- | --- | --- |
| 5 calls (per-device, today) | ~10,800 | **~4 min** | ~2.7 min |
| 1 call (if batch is unlocked) | ~54,000 | **~48 s** | ~32 s |

Token refreshes add ~360/month, under 1%. Taps cost a command plus a re-read on top.

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

- ~~Does the trial expire outright, or just reset monthly?~~ Both: the quota refreshes monthly, the
  pack expires **2026-09-15**. See the console table above.
- **What happens on 2026-09-15, and what "Extend Trial Period" grants.** This is now the question
  that decides whether Tuya is viable past a month. Press it before the expiry, not after.
- ~~What $0.20/month of basic resources actually buys~~ — ~54,000 calls at the foreign rate,
  ~80,600 at the domestic one. **Which of the two applies to a Central Europe project** is the
  remaining unknown, and it is a 1.5× swing on the poll interval.
- **Controllable Device Pool reads 0 / 10 against a Device Pool of 20 / 50.** So a device presumably
  takes a controllable slot the first time it is commanded, and the panel's 5 recuperators would
  leave 5. Whether a slot is ever released, and what the eleventh device does when commanded, is
  unknown — nothing has been commanded yet.
- **How to write a custom datapoint.** `POST /v1.0/devices/{id}/commands` is the standard-set path,
  and the standard set for this product is just `switch` — so fan speed almost certainly needs the
  thing-model write, `POST /v2.0/cloud/thing/{id}/shadow/properties/issue`. Still unverified, and
  now *written against*: the panel's on/off switch sends exactly that, and nothing has confirmed it
  works. Writing turns a real fan on in a real flat, so it needs a deliberate test, not a probe.
  Fan speed is read-only on the tile until the on/off write is proven.
- **Whether the three speed booleans are mutually exclusive.** Nothing has been written, so nothing
  has forced the question. The tile prints every speed reported as on rather than picking one, so
  two at once would show up as `low + high` instead of being quietly halved.
- **Nothing in the API confirms `temper` is °C and `huimi` is %RH** — `typeSpec.unit` is `""` for
  both, and the app is the only check. That is now on the wall, so it is worth a second look:
  compare the tile against a thermometer in the same room, or against the app on a cold day when
  a wrong unit would be obvious.
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

Redacted copies of the five below are the test fixtures, in `app/src/test/resources/tuya/`:
`token.json`, `devices.json`, `shadow_properties.json`, `thing_model.json` and
`batch_no_permission.json`. Real device ids are replaced with `xfj-01`…`xfj-05` (and `dj-`, `mjj-`,
`wg2-`, `kg-` for the rest), local keys with zeroes, the WAN address with `203.0.113.1`, the
coordinates with `0.0000` and the uid with `eu-test-uid` — the names, timestamps, datapoint values
and envelope shapes are the account's own. Nothing in `src/test/` calls Tuya.

**The envelope, on every route.** `{ "result": …, "success": true, "t": …, "tid": … }`, and a
failure is `{ "code": …, "msg": …, "success": false, "t": …, "tid": … }` — **arriving as HTTP 200**.
A client that only checks the status code reads a refused call as an empty house.

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
- **There is no room field, on any of the 20 devices.** The keys are `active_time`, `biz_type`,
  `category`, `create_time`, `icon`, `id`, `ip`, `lat`, `local_key`, `lon`, `model`, `name`,
  `online`, `owner_id`, `product_id`, `product_name`, `status`, `sub`, `time_zone`, `uid`,
  `update_time`, `uuid` — and the only grouping among them, `owner_id`, is the same `00000000` on
  all 20, so it is the home, not a room. The Smart Life app *does* have the recuperators in rooms,
  so the grouping exists in Tuya's own product; this endpoint does not carry it, and the shadow
  routes below carry even less. `Device.room` is therefore null for every Tuya device.

**What the panel does about it.** The panel groups its tiles by room, so a device with no room is a
real problem, and the answer is not to invent one. Three ways were on the table:

1. **Parse the name.** They read "Бризер зал", "Бризер спальня", "Бризер детская", "Бризер
   кабинет" — four of them do name a room. Rejected: `name` is a free-text field the owner edits in
   the Smart Life app, so a rename would silently move a tile to another room, or to none; the
   spelling would have to be matched to Yandex's rooms case- and declension-insensitively, which is
   a second guess on top of the first; and it does not even work here — the fifth is "Бризер данина
   комната", which names no room Yandex knows. Parsing places four and still needs an answer for
   the fifth.
2. **Record it by hand** in `local.properties` as `tuya.rooms=<device id>=<room>;…`, reaching the
   code as the `TUYA_ROOMS` `BuildConfig` constant and parsed by `panel.recuperatorRooms`. **This
   is what the panel does.** The knowledge exists — the flat knows where its recuperators are — and
   the only thing missing is a vendor willing to say it, so it is written down once, explicitly, by
   someone who checked. `local.properties` rather than a checked-in file because the keys are
   device ids, which are apartment-identifying. The room names must be spelled as Yandex spells
   them, or the recuperator gets its own section next to the room it belongs to.
3. **An unplaced section.** Kept as well, as the fallback: anything the mapping does not name — a
   new recuperator, a typo, an unset property — renders under "Без комнаты" at the bottom of the
   panel. A device is never dropped for want of a room, and a mistake in (2) shows up on the wall
   where it can be seen, instead of disappearing.

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
- **The thing model names no unit for either:** `"unit": ""` on both `typeSpec`s. So the API never
  says these are °C and %RH — the Smart Life app does, showing the same figures for the same
  device, which is where the reading above comes from. The tile prints `°C` and `%` on that basis,
  hardcoded, with the app check named in the code as its only source. Worth being clear about the
  footing: `scale` is asserted against the recorded thing model by a test, and the unit cannot be.
  The two together are what the number on the wall rests on.
- Each property is `{code, custom_name, dp_id, type, value, time}`, and `type` is `bool` or `value`
  — which is what says how to read `value`, since the shadow carries no schema.
- **Speeds and modes are three separate booleans each, not an enum.** Whether the device enforces
  mutual exclusion, or whether two speeds can be true at once, is unverified — nothing has been
  written yet.
- dp 110 does not exist; the numbering has a hole.
- `shadow/properties` carries a per-datapoint `time`, so staleness can be shown per reading rather
  than per tile. It is **milliseconds** — read as seconds it puts the switch 54,000 years out — and
  it is one timestamp, not two: whether it means "last reported" or "last changed" is not stated
  anywhere, and the response gives nothing to tell them apart. On the recorded read the humidity
  was 26 s old while the switch had not moved in 3 days, on a device that was online throughout.
  The panel therefore carries the same instant in both fields of its model rather than inventing a
  distinction the vendor does not make.

### Batch reads — the awkward part

| Endpoint | Result |
| --- | --- |
| `GET /v1.0/devices/status?device_ids=a,b` | works, **standard-set filtered** → only `switch` |
| `GET /v1.0/iot-03/devices/status?device_ids=a,b` | same, different envelope → only `switch` |
| `GET /v2.0/cloud/thing/batch?device_ids=a,b` | works — device *info*, no properties |
| `GET /v2.0/cloud/thing/batch/shadow/properties?device_ids=a,b` | **`40001900 No space permission`** |

The batch properties route exists — a business error, not `1108` — but the project is not authorised
for it. Until that is sorted, **real state costs one call per device: 5 calls per refresh, not 1.**

Against the ~54,000 calls/month the console's billing rules actually buy, that is ~10,800 refreshes:
**a poll every ~4 minutes** around the clock, or ~2.7 minutes at 16 h a day. Unlocking the batch
route would take the same allowance to **~48 seconds**, which is the difference between a tile that
lags a tap by minutes and one that does not. See "What the console actually shows" for the rates.

### What the panel does with this

`integrations/tuya/` — `TuyaClient` plus the signature, sharing nothing with `integrations/yandex/`.

- **The tile shows on/off, fan speed, temperature and humidity — four ages, on two lines.** Not one
  age: on the recorded read the humidity was 26 s old while the switch had not moved in three days,
  and the two climate readings were minutes apart from each other. A device that reported neither
  `temper` nor `huimi` gets no second line at all rather than a row of "unknown".
- **A refresh is `devices()` then `read()` per recuperator: 6 calls, or 7 with a token fetch.** The
  inventory's `status` is not read at all — it is standard-set filtered to `switch`, and its only
  timestamp is the device-level `update_time`, which belongs to no one datapoint. What it *is* read
  for is the name and `online`.
- **The access token is held until it expires**, not fetched per call, behind a lock so a tap and a
  poll cannot each spend a call fetching one.
- **`MainActivity` polls this every 6 minutes**, on its own timer, separate from Yandex's 15 s. A
  tap re-reads only the device it touched — one call, not another five.
- **A single recuperator's read failing is not the group failing.** That tile keeps the values it
  had, says why it is not moving, and the other four update normally; only the inventory call
  failing takes the whole group down. This is where the shape differs from the Yandex tiles, and it
  differs because the call does.
- **The panel is grouped by room and Tuya names none**, so the recuperators are placed by hand from
  `tuya.rooms` in `local.properties`, and whatever is not in there renders in the panel's "Без
  комнаты" section rather than being guessed from the device name. See the inventory section above
  for why the name is not parsed.
- `Device.online` exists on the shared model for this vendor: 11 of the account's 20 devices came
  back `false` over a perfectly good HTTP 200, so a tile needs an offline state that has nothing to
  do with whether the call worked. Yandex reports no such field and leaves it null.

**The write is implemented and UNVERIFIED.** `RecuperatorTiles.toggle` sends `POST
/v2.0/cloud/thing/{id}/shadow/properties/issue` with `{"properties":"{\"switch\":true}"}` — the
body is a JSON object encoded as a string, which is Tuya's own shape. Neither the route nor the
body has ever been sent to the account: writing turns a real fan on in a real flat, and it would
also take one of the 10 controllable-device slots for the first time. The tile is repainted from a
re-read rather than from the command's answer, so a command that silently does nothing shows up as
a tile that does not change rather than as a tile that lies. **Try it deliberately before trusting
the switch on the wall.**

### Errors seen

| Code | `msg` | What it actually meant |
| --- | --- | --- |
| 1108 | uri path invalid | wrong **method** — POST on the token endpoint |
| 2009 | clientId is invalid | reached the right route; `client_id` header not recognised |
