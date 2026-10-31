# Smart Life (Tuya)

**Scope:** the recuperators, and nothing else. The panel uses native Tuya LAN protocol 3.4 over
Wi-Fi, with local keys in encrypted storage. Cloud API and allowance notes below describe the
earlier cloud implementation; its recorded fixtures remain useful references. See the local
verification and panel transport sections at the end for the current implementation.

Read from the public docs on 2026-08-15. **First real reads made the same day; first reversible
writes made 2026-08-30** — the account is linked and answering; see "Recorded responses" and
"Live write verification". Everything below marked _verified against the account_ came from a
real call, not from a doc page.

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
- **Local reads and power/speed writes are verified.** See the local verification sections below.
  LAN access uses a per-device local key and the unofficial Tuya binary protocol. All four
  currently reachable units return all 13 datapoints after the report check below.
  The panel uses the native LAN client.
- The signing scheme is the fiddliest of the five — sorted query strings, body digests, uppercase
  hex. Expect to spend the first session getting `1004 sign invalid` and nothing else.

## Ecosystem health

Very alive; the largest of the five by device count, docs are extensive and versioned, SDKs in five
languages. The friction is commercial, not technical: quotas, editions and renewals.

## Live write verification — 2026-08-30

The thing-model route used by the panel was exercised against one online recuperator with a
redaction-safe temporary script. Every change was followed by a shadow read, and the unit was
restored to fully off at the end.

- **Power is verified.** Issuing `{"switch": true}` turned the shadow on; issuing
  `{"switch": false}` restored it and cleared all speed/mode booleans.
- **Medium speed is verified, but only after power.** With the unit on, issuing
  `{"speed_two": true}` changed the mutually exclusive speed state from `speed_three` to
  `speed_two`; the other two became false.
- **A speed write while off is ignored.** `{"speed_two": true}` returned success but the shadow
  remained fully off. The UI therefore disables speed buttons until a re-read confirms `switch`.
- **The device is asynchronous.** Six-second restore loops could observe an earlier queued command
  after a later restore. Controlled checks used 20-second waits and explicit final reads. The app
  never updates a tile from the command response; it reads the one touched device again.
- **Power-on defaults are not stable enough to hardcode.** Separate runs came up as low/inflow and
  high/auto. The panel shows the shadow it read rather than inventing a default.
- Sleep, humidity presets, and airflow modes were not exercised as independent controls. They stay
  visible as readings when present, but are not writable from the panel.

## Open questions before writing code

- ~~Does the trial expire outright, or just reset monthly?~~ Both: the quota refreshes monthly, the
  pack expires **2026-09-15**. See the console table above.
- **How long a recuperator actually takes to report a write.** The panel now waits ~30 s for it, in
  widening steps, and that number comes from the live checks above using 20-second waits — not from
  timing a tap on the wall. If a tile routinely takes the full window, the steps are too slow; if it
  never does, the last one is a call being spent for nothing.
- **What happens on 2026-09-15, and what "Extend Trial Period" grants.** This is now the question
  that decides whether Tuya is viable past a month. Press it before the expiry, not after.
- ~~What $0.20/month of basic resources actually buys~~ — ~54,000 calls at the foreign rate,
  ~80,600 at the domestic one. **Which of the two applies to a Central Europe project** is the
  remaining unknown, and it is a 1.5× swing on the poll interval.
- **How the Controllable Device Pool changes after a command.** It read 0 / 10 before the live
  write. Whether a slot is released and what the eleventh device does remain unknown.
- ~~How to write power and speed datapoints.~~ The thing-model issue route is verified for `switch`
  and `speed_two`; see "Live write verification". Sleep, humidity presets, and airflow modes remain
  deliberately unverified.
- ~~Whether the three speed booleans are mutually exclusive.~~ On the tested unit, selecting medium
  while high was active set medium true and high/low false. The parser still preserves every true
  value rather than assuming the device can never misreport.
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

   All five are mapped in the flat: four onto rooms Yandex already reports, and "Бризер данина
   комната" onto **Маленькая детская** — a room no Yandex device is in, so that name reaches the
   panel from this file alone. It is named in `ROOM_ORDER` as well, or it would sort after every
   known room, down by the bathrooms.

   **The room names are Cyrillic, and that is a trap in the build.** `Properties.load(InputStream)`
   decodes ISO-8859-1, so read that way "Спальня" arrives as "Ð¡Ð¿Ð°Ð»ÑŒÐ½Ñ" — no error
   anywhere, just five sections named after the mojibake, sorted below the real rooms. Verified on
   the tablet, and fixed by reading the file through a UTF-8 `Reader` in `app/build.gradle.kts`.
   Nothing in `src/test/` can see `local.properties`, so if the rooms ever look wrong, read the
   generated `BuildConfig.java` first.
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
- **Speeds and modes are three separate booleans each, not an enum.** A live speed write on one
  online unit behaved mutually exclusively: selecting medium cleared low and high. The parser still
  preserves every true flag so an inconsistent shadow remains visible rather than being hidden.
- dp 110 does not exist; the numbering has a hole.
- `shadow/properties` carries a per-datapoint `time`, so staleness can be shown per reading rather
  than per tile. It is **milliseconds** — read as seconds it puts the switch 54,000 years out — and
  it is one timestamp, not two: whether it means "last reported" or "last changed" is not stated
  anywhere, and the response gives nothing to tell them apart. On the recorded read the humidity
  was 26 s old while the switch had not moved in 3 days, on a device that was online throughout.
  The panel stores that instant once, in the capability's `lastUpdated`, without inventing a
  separate change timestamp.

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
- **A write is confirmed by re-reading until the device reports it: at once, then after 2 s, 4 s,
  8 s and 16 s.** The single read straight after the command was a bug on the wall, and this is the
  entry above meeting "the device is asynchronous" from the live write verification: the command is
  taken instantly and the shadow follows seconds later, so that read returned the *old* switch and
  the tile was repainted with it. The fan came on and the panel said "off" for the rest of the
  6-minute interval — with the speed buttons disabled the whole time, because a speed write while
  off is ignored and the UI refuses one until power is confirmed. The tile is repainted from each
  read as it lands, so it flips when the device does. It costs at most 5 calls per tap and normally
  1; a tap happens a few times a day against a refresh's 5 every 6 minutes.
- **What it still does not do is paint what it asked for.** An unconfirmed write leaves the tile
  saying what the device last reported, which is the panel's rule everywhere: the next refresh
  corrects it, and a tile that showed a state nothing has confirmed would be the one kind of wrong
  a wall panel cannot recover from.
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

**The write is implemented and verified** — see "Live write verification — 2026-08-30".
`RecuperatorTiles.toggle` sends `POST /v2.0/cloud/thing/{id}/shadow/properties/issue` with
`{"properties":"{\"switch\":true}"}` — the body is a JSON object encoded as a string, which is
Tuya's own shape. The tile is repainted from a re-read rather than from the command's answer, so a
command that silently does nothing shows up as a tile that does not change rather than as a tile
that lies. Because the device reports asynchronously, that re-read is repeated over ~30 s until the
shadow reflects the write; see "What the panel does with this".

### Errors seen

| Code | `msg` | What it actually meant |
| --- | --- | --- |
| 1108 | uri path invalid | wrong **method** — POST on the token endpoint |
| 2009 | clientId is invalid | reached the right route; `client_id` header not recognised |

## Local read verification — 2026-10-31

The cloud inventory currently returns `28841002: IoT Core service subscription has expired`.
Without renewing that subscription, Smart Life User Code + QR authorization through
`tuya-device-sharing-sdk` supplied local keys for all five recuperators. Local UDP discovery on the
tablet identified four recuperators announcing protocol **3.4**. No keys, device IDs or addresses
are reproduced here; connection details are in the gitignored `.toolchain/tuya-local/` directory.

One recuperator returned this decrypted LAN response using TinyTuya 1.20.0:

```json
{"dps":{"108":207,"109":221}}
```

The recorded thing model maps DP 108 to `huimi` and DP 109 to `temper`, both with scale 1:
20.7 and 22.1 respectively. Repeated reads returned fresh values (humidity also read 20.6).
Their physical units retain the caveat documented above.

The test used USB forwarding to a temporary TCP relay on the tablet, whose device socket was
explicitly bound to the Wi-Fi `Network`. VPN settings were unchanged. Direct Mac sockets failed
with `EHOSTUNREACH`/`EACCES`, including with an explicit Wi-Fi interface binding; their cause was
not established. Keys came from Smart Life once; the successful state reads used device LAN TCP,
not cloud state. Internet-independent key retrieval was not tested.

Only handshake, status queries and `UPDATEDPS` report-refresh requests were sent; no power, speed
or mode values were written. Status queries and an explicit report refresh returned only the two
climate datapoints. Subsequent power/speed/mode reads, writes and panel deployment are documented
below. No Android application code or installed APK changed during this initial read-only probe.

References: [Tuya Local authorization](https://github.com/make-all/tuya-local#configuration),
[TinyTuya](https://github.com/jasonacox/tinytuya).

## Reading power, speed and modes over LAN — 2026-10-31

All five saved product IDs match the public KCvents VT501-W configuration. Four units were
discovered locally. Separate connections to each of the four units below returned all 13
datapoints using the normal protocol 3.4 handshake followed by `DP_QUERY_NEW` (command 16), with
payload `{}`. No cloud authorization or cloud state request is involved in these reads.

| Unit | Power | Speed | Airflow | Query completeness |
| --- | --- | --- | --- | --- |
| Hall | Off | All three flags false | All three flags false | 13/13 |
| Study | On | Low | Inflow | 13/13 |
| Children's room | Off | All three flags false | All three flags false | 13/13 |
| Danina's room | Off | All three flags false | All three flags false | 13/13 after report check |
| Bedroom | Not tested | Not tested | Not tested | Not discovered in this check |

Redacted complete response from the study (sensor values are a snapshot):

```json
{"dps":{"1":true,"101":true,"102":false,"103":false,"104":false,"105":false,"106":false,"107":false,"108":200,"109":245,"111":true,"112":false,"113":false}}
```

Verified mapping from the previously recorded thing model:

- `1`: power; `101`/`102`/`103`: low/medium/high speed.
- `104`: sleep mode; `105`/`106`/`107`: humidity presets.
- `111`/`112`/`113`: inflow/exhaust/auto airflow modes.
- `108`/`109`: humidity/temperature, scale 1.

Danina's unit returned a **matching command-16 query reply** containing only DP 108/109, followed
by incremental command-8 climate reports. Its decrypted query payload was 32 bytes versus 160
bytes for complete replies. This is not a client dropping Boolean values or confusing an
unsolicited sensor report with the query reply. Identity/time fields, explicit null-valued DP
selectors, legacy query command 10, refresh command 18 (all DPs, power alone, speeds alone, modes
alone, empty list), bounded passive receives and heartbeats did not produce the missing Boolean
DPs. An empty-object refresh returned `query dp failed`. Opening and closing its Smart Life tile
without changing controls did not populate the missing values.

The user then switched power on and back off using the manufacturer's controls while the
diagnostic connection only queried/listened. The unit immediately sent command-8 reports for
power, all speed flags, sleep/humidity flags and airflow flags. A subsequent command-16 query
returned all 13 DPs. It reported on + low speed + inflow, then off with the active flags cleared.
A new connection after this test also returned all 13 DPs. Thus those values are available over
LAN; the initial device query contained only the values the module had reported at that point.
The initial omission is consistent with an incomplete module cache, but why it started that way
and whether it recurs after a physical restart remain unverified. Do not initialize missing
Boolean values to `false`, infer them from the other units, or treat command acceptance as a
state report. Do not automatically toggle power to populate missing state.

For the Android migration: serialize queries per device and merge incremental reports per DP
without clearing values not mentioned in an update. TinyTuya 1.20.0 can return the first nonempty
unsolicited packet from `status()`; a sensor report alone must not complete a status request.
These units use an independent reply counter even on protocol 3.4: an actual request with sequence
3 received challenge/reply counters 18717/18718. The sequence field is not a request ID on this
firmware. A fresh authenticated connection with one outstanding query, command 16 and advancing
peer counters identifies its reply; peers that echo request IDs are supported too. Use an absolute
deadline even when sensor reports continue arriving. Keep missing state explicitly unknown.

This establishes reading power, speed and mode from the four available units without an IoT
Core subscription. The fifth unit was not discovered. Local power/speed writes are documented
below; recovery after an appliance restart and operation with the internet disconnected remain
separate checks.
No application code, permissions, VPN settings or installed APK were changed for this check.

References: [matching device configuration](https://github.com/make-all/tuya-local/blob/main/custom_components/tuya_local/devices/kcvents_vt501_fan.yaml),
[matching device report](https://github.com/make-all/tuya-local/issues/1349),
[TinyTuya protocol](https://github.com/jasonacox/tinytuya/blob/master/PROTOCOL.md).

## Local power and speed commands — 2026-10-31

An authorized reversible check on the hall recuperator started from power off with all 11 Boolean
DPs false. Protocol 3.4 control command 13 used this shape, with the current Unix time:

```json
{"protocol":5,"t":1793454000,"data":{"dps":{"1":true}}}
```

Power on, medium (`102: true`), high (`103: true`), low (`101: true`), then power off were sent
once each. Every step was confirmed from device state rather than from command acceptance. Each
speed selection made the other two speed flags false. The final read matched all 11 original
Boolean values: power, speeds, sleep, humidity presets and airflow flags were false. Temperature
and humidity naturally change and are not restoration targets. No mode or humidity-preset writes
were attempted.

## Panel transport

`MainActivity` selects `TuyaLanClient`; runtime reads and commands use LAN TCP only, with no cloud
fallback. `TuyaDevices` is the small shared contract for the two existing vendor transports, so
the panel keeps its per-device errors, cached identity and command-confirmation behavior. The
cloud client and its recorded-response tests remain separate from the selected runtime transport.

- Native AES/HMAC framing, authenticated nonce negotiation and bounded frames use existing Java
  cryptography; no dependency or service was added.
- `tuya.local.devices` is a JSON array in gitignored `local.properties`, seeded into the existing
  encrypted preferences. Records have `id`, `name`, `localKey`, and optional `host`/`port`. No real
  identifiers, addresses or keys belong in source control. Build seeds are extractable from a local
  APK, like the existing Yandex seed; do not publish this apartment-specific APK.
- Sockets select the actual Wi-Fi `Network`, excluding VPN transports. The user approved adding
  `ACCESS_NETWORK_STATE`. When a non-bypassable VPN denies explicit binding with `EPERM`, ordinary
  sockets follow Android's existing routes instead. The tablet's selective VPN routing permits
  the LAN connection this way. No VPN exclusion or setting change was needed.
- Address hints are refreshed from bounded local advertisements at startup, after read errors
  and periodically, so DHCP changes do not require a new APK. Discovery only supplies address
  guidance; the TCP session authenticates the per-device key before reading or sending controls.
- Queries are serialized per device, distinguish echo-style request IDs from this model's
  independent peer counter, and have an absolute deadline. Incremental reports update only
  mentioned datapoints and retain other values/ages.
  Cancelling a poll closes its socket, including when an intercom call interrupts the panel.
- Before a command, the client reads the actual device. Power must have reported in that current
  query/session; old cached power is not enough. Already selected power/speed is not written again.
  A write is sent once, with no automatic retry after an uncertain transport failure. The existing
  panel then confirms the requested state by reading, never by trusting an echoed acknowledgement.
- LAN cadence is 15 seconds during interaction, 1 minute idle and 5 minutes at night, with the
  existing lifecycle/intercom pause and failure backoff. Each failing device keeps its own tile
  error; an unavailable fifth unit does not prevent the other four from updating.

Physical restart recovery and operation with the internet disconnected are still separate checks.

## Installed tablet verification — 2026-10-31

The native-client APK was installed on the connected tablet after the full unit suite,
`ktlintCheck`, Android lint and `assembleDebug` passed. The previous APK is backed up locally in
the gitignored `.toolchain/tuya-local/` directory.

In the installed panel, the hall unit was switched on, medium speed was selected and confirmed,
then power was restored to off. Accessibility snapshots showed the actual reported state and
selected controls at every step, with all speed flags clear after restoration. A separate LAN
read afterwards confirmed all 11 original Boolean flags were false.

Room cards showed fresh power, speed, temperature and humidity for the hall, study, children's
room and Danina's room. The study remained on/low/inflow throughout the hall-unit check. The
bedroom unit remained undiscovered and showed its own visible failure.

The tablet VPN stayed enabled with its original settings. Its restriction on explicit socket
binding is handled by following normal system routes; the independent reply counter is handled
by the client. No VPN exclusion was applied. Appliance restart recovery, intercom calls during a
LAN command, and operation with internet disconnected were not physically exercised here.
