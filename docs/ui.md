# Panel UI

**Scope:** how the panel is laid out and drawn. Not what it reads — that is one doc per vendor.

**Status: done and merged** — seven commits: #11, #12, #14, #15, #17, #19, #20. See "History".
This is no longer a brief and is now a record: the plan that produced the panel has been trimmed
away and what is left is what the panel *is*, plus what was measured on the tablet while getting
there. Numbers that were guesses when this was written and have since been measured say so and give
the measurement; the ones still marked _measure on the tablet_ are numbers nobody has taken, and are
not settled just because they are written down.

What is not settled is in "Watch on the wall" — bets already placed, waiting on the wall to settle
them — and in "Open". The work that follows from this doc is in
[design/panel-redesign.md](design/panel-redesign.md).

The tablet is a Samsung Galaxy Tab S7 (SM-T875, Android 13). Everything measured below was measured
on it, on 2026-08-16.

## What is decided

1. **Rooms stack down one scroll.** A "Главная" section holding the favourites, then one section
   per room in the existing room order, each behind its own heading. **This replaced a tab shell**,
   which is what commits 1–7 shipped: it could not hold fourteen rooms across 753 dp and it answered
   a vertical problem with a horizontal control. See "The scroll".
2. **Material 3 Expressive mosaic tiles.** Mixed tile sizes instead of one full-width card per
   device: third-width tiles for the things with a slider, quarter-width ones for the rest, and a
   room's bulbs behind one group tile that opens them.
3. **Both themes, following the system.** The panel is light by day and dark by night, driven by
   `isSystemInDarkTheme()`.
4. **A tap on a tile opens that device's sheet.** Not the reference app's split — nothing moves off
   a tile into it. What the sheet adds is an age per reading and the actions a third-width card had
   no room for. See "The device sheet".

Rejected, and why, so it is not re-proposed: **fill-level tiles** (the tile's fill height or width
is the value, Apple-Home style). The recorded `/v1.0/user/info` holds 28 `devices.types.light`
against 3 ACs, 2 light strips and 1 curtain, and all 5 recuperators are on/off — so of the flat's
tiles, six have a continuous value and the rest have nothing to fill with. On those a fill bar is a
coloured rectangle that means "on", which the tile's colour already says.

## What the panel did before this work

The baseline commits 1 and 2 replaced, kept so the diff stays legible:

- [`PanelRooms`](../app/src/main/kotlin/ru/domovoy/panel/PanelRooms.kt) was one `LazyColumn`. Group
  failures at the top, then a `Text` heading per room, then every tile in that room as a full-width
  `Card`, in the fixed order ac → curtain → strip → recuperator → bulb → launcher. It became a tab
  strip over a `LazyVerticalGrid`, and is now one `LazyVerticalGrid` with a full-width heading per
  room — a scroll again, of mosaic tiles rather than full-width cards. The tile order within a room
  has not changed through any of it.
- [`roomSections`](../app/src/main/kotlin/ru/domovoy/panel/RoomSections.kt) decides which room a tile
  lands in and in what order the rooms come. **It did not change, and has not** — not for the tab
  shell and not for the scroll that replaced it. `panelHeadings` consumes exactly what it returns —
  one heading per `RoomSection`, in the order that function already produces, with the roomless
  section last under "Без комнаты".
- Every tile prints a status line ending in `ageLabel(...)`, and appends `not updating: <error>` when
  its group's poll failed. Commit 2 was a re-skin and changed no string. **No longer true:** a tile
  prints *one* age, the oldest of the readings it is showing, and none at all while they are all
  fresh — see "One age per tile". `ageLabel` is gone; `ageLine` answers null instead of "just now".
  The error is not appended to that line either: it is the tile's second line, and it is one of four
  words rather than the vendor's own — see "Why a poll failed".
- `MainActivity` wraps everything in a bare `MaterialTheme {}` — no colour scheme is passed, so the
  panel is on the Material baseline light palette in both system themes. **No longer true:** commit
  5 passes it one of the two schemes in `PanelTheme.kt`, chosen by `isSystemInDarkTheme()`.

## Tile sizes

A **twelve-column** grid, laid out in thirds and quarters. The span is a property of the tile type,
not of the room, and **every tile is 296 dp tall whatever its span** — see "One tile anatomy":

| Tile | Count | Span | Width | What it shows |
| --- | --- | --- | --- | --- |
| Air conditioner | 3 | 4 (wide) | 251 dp | Name, target temperature at display size, on/off, temperature slider, one age |
| Curtain | 1 | 4 (wide) | 251 dp | Name, open percent, slider, one age |
| Light strip | 2 | 4 (wide) | 251 dp | Name, on/off, brightness slider, colour, one age |
| Recuperator | 5 | 4 or 3 | 251 / 188 dp | Name, on/off, fan speeds, and — when it reports them — temperature and humidity. One age for the four |
| Bulb | many | 3 (narrow) | 188 dp | Name, on/off, one age. On the wall when it has never reported, or when its room's group has been opened. See "The lights group" below |
| Lights group | 1 per room | 3 (narrow) | 188 dp | How many lamps the room has as its name, how many are lit, the oldest of their ages. Opens the lamps |
| Launcher | 2 | 3 (narrow) | 188 dp | Name, what the tile does, and — when the app is missing — the package. No age; there is no reading to age |

**This was six columns, halves and thirds, and the widest tile was half the wall.** Two columns of
anything is a phone's proportion — the reference smart-home app is two columns of a 411 dp phone —
and 376 dp for a name, a value and a slider left a great deal of nothing between them. Three across
and four across is what a 753 dp panel has room for.

**Four columns as a *grid* is still rejected and this is not that.** The first draft made `COLUMNS`
itself 4, so a hero tile spanned all four and came out 753 dp with its switch stranded 700 dp from
the value it switches. What is here is twelve columns with nothing ever one column wide. The other
half of that old rejection — "the launcher at 188 dp wrapped its one line onto two" — has stopped
being a defect: every tile reserves the same block of status lines whether it fills them or not,
and since "nothing on the wall wraps" that block is a ceiling — a line too long for 188 dp is cut
short rather than allowed onto a second one.

**Thirds and quarters, because both divide twelve.** This is the rule that matters, and it was
learned rather than designed: a row fills instead of trailing dead cells. It used to carry a second
job — two tiles of the same kind beside each other coming out the same height — and that job has
been taken over by the anatomy, which makes tiles of *different* kinds agree as well.

The recuperator is the densest tile the flat has and the only one whose span is decided by its
content: **wide when it has a second line to put there, narrow when it has neither.** Two things
count as a second line — `climateLine`, and its own error. A wide tile holding one line of
"on · no speed" is a hole in the wall. The error used to be the longest string any tile printed and
is four words now (see "Why a poll failed"), so the width it buys is for having something on the
second line at all rather than for the length of it.
_Unexercised on this wall:_ all five recuperators report both values, so the narrow branch is
covered by `TileLayoutTest` and has never been seen.

### One tile anatomy

Five slots, in this order, on every tile of every kind:

| Slot | Reserved | What is in it |
| --- | --- | --- |
| Art and controls | 80 dp | The untinted hardware art, left; the round power button, right, inside its 64 dp touch box |
| Level | 64 dp | The slider, centred — the same 64 dp `SlimSlider`'s track slot measures |
| Promoted value | 52 dp | One line of `displaySmall`, or nothing |
| Name | 28 dp | One line of `titleMedium`, wrapped rather than truncated |
| Status line | 48 dp | **Two lines of `bodyMedium`, and a ceiling rather than a reserve**: the status line, and the second line — the strip's colour, the recuperator's climate, or why the poll stopped landing |

**296 dp = 80 + 64 + 52 + 28 + 48 + 2 × 12 of padding**, and the same 296 for a bulb as for an air
conditioner. Before this the mosaic had four heights — the air conditioner 169 dp with a dead area
under its slider, the strip shorter, the recuperator shorter again, the launchers shorter still —
because each kind laid itself out around whatever it happened to have.

It was 328 while the status slot reserved four lines. Two of the four were never filled by anything
the flat produces, and the 48 dp they left at the foot of every card read as a reserve showing
rather than as padding; capping the slot took the tile to 280. Enlarging the art row from 64 to 80
then brought it to 296.

**An empty slot is empty, not absent.** A launcher has no power button, no slider and no value and
reserves all three anyway. That is what buys bottom edges that line up across kinds, and it is the
whole cost of it too: a relay bulb carries the 64 dp band where a dimmable bulb uses its slider.

**The status slot is a ceiling; every other slot is a floor.** That is the reverse of what this
doc said until "nothing on the wall wraps", and the argument it reverses — that a vendor error long
enough to run past four lines should make that tile taller rather than be swallowed — had the
priority backwards. The status line was the last unbounded thing on the panel, so a string nobody in
this flat controls decided how tall a tile came out, and *two tiles of the same kind coming out the
same height* is the property this whole section exists for. Each of the slot's two lines is now one
line, `maxLines = 1`, ellipsised.

What made that affordable rather than lossy is that the strings were shortened first — see "Why a
poll failed" and "One age per tile". The only thing left long enough to meet the ellipsis is a
package name, and truncating an identifier is the answer `docs/design/panel-redesign.md` item 7 asks
for outright.

**The name is deliberately still a floor.** A long device name wraps onto a second line and grows
the card, because PLAN.md's reference table refuses truncated device names — fine at 30 cm, useless
at four metres. Nothing in the flat's 35 devices comes near it: "Кондиционер" is 145 dp of the 156 a
quarter tile gives it, and every longer name is on a tile a third of the wall wide.

What goes in the slots is one pure function per tile type — `anatomy(...)` in `TileLayout.kt`,
returning a `TileAnatomy` — so "does this kind still fill all five" is a test rather than a picture.
`TileCard` draws it and decides nothing.

It is also the only tile with **an error of its own**. Every other group shares one — a failed
`/v1.0/user/info` failed for all of them — but recuperator state costs one Tuya call per device, so
`RecuperatorTileState.error` is per-tile and four working units must not be labelled "not updating"
because the fifth timed out. The mosaic keeps that distinction: the tile's own error colours the
tile, the group's error colours all five.

The AC prints **the older** of its two ages: on `ac-01` the power and temperature capabilities were
read 81 days apart, and the tile says "81 d ago" rather than the on/off's minute — see "One age per
tile". It printed both until then, and what that refused is intact; what is gone is the second
timestamp.

Sizes to hold to, since this is read and touched at arm's length from a wall:

- Minimum hit area **64 dp** on anything tappable, not the platform's 48 dp. _Measured:_ every
  switch in the panel dumps as exactly 64.0 × 64.0 dp. The tab strip used to be measured here too —
  it was the one thing on the wall a finger could miss, at Material's default 48, until a `heightIn`
  on the `Tab` raised it and the row came up 64.0 dp with them. **The strip is gone** and the
  measurement with it; the rooms are headings on a scroll, which nothing has to hit.
- Grid gutter 8 dp, **one tile corner radius of 22 dp**. It was two radii — 22
  on a half tile and 18 on a third, derived from the span so that a tile's shape and its width could
  not disagree — and that was a rule about widths at a time when the mosaic had four heights and no
  anatomy. This doc already recorded that on the wall the two were "a real but subtle difference"
  nobody standing back from it could name. One anatomy, one shape.
- **The panel is 753 dp wide.** 1600 px at 340 dpi, portrait, which is the orientation it hangs in.
  This is what the column widths are sized from. Landscape would be 1204 dp and the panel is not laid out
  for it: **auto-rotate is off on the tablet** (`accelerometer_rotation` 0) rather than
  `screenOrientation` being set in the manifest, so a settings reset puts landscape back and the
  mosaic will be wrong until it is turned off again. **That is exactly what happened** — read
  2026-08-31, `accelerometer_rotation` was back to `1` and `mUserRotationMode=USER_ROTATION_FREE`.
  It is a device setting with nothing in the repo holding it down, so re-check it whenever the
  mosaic looks wrong. Since the same day the display also carries
  `wm set-ignore-orientation-request true` — set for Domonap, which forces landscape at runtime
  (docs/domonap.md) — which stops *any* app turning this display, the panel included.

### The lights group

28 bulbs against 7 of everything else — they are the many, some are relays and others have
brightness/colour capabilities, and a card each is what makes the panel a mile of scrolling. So a
room's bulbs render as **one group tile in the
mosaic**: `7 lamps` as its name, how many of them are lit as its promoted value, and one line
carrying both counts again with the oldest of their readings. Tapping it opens the lamps under it as
ordinary named tiles, each with its own age, power button, and brightness slider when advertised;
tapping it again puts them away.

**This was a wrapping row of 72 dp discs, and the row is gone.** Seven identical amber circles,
unlabelled, under one shared line — the most saturated thing on the wall and the biggest touch
targets on it, so the eye landed there first and learned nothing, and which lamp was which could not
be recovered from the wall at all. They were also the one thing here that was a tile without being a
card: their own shape, their own colour `when`, their own touch target, outside the anatomy every
other kind agreed on.

**The other option was seven ordinary tiles, and the count is why it was not taken.** One
`/v1.0/user/info` call feeds every bulb in the flat, so the moment it stops landing `favourites`
pulls all 28 onto Главная — at 296 dp each that is seven rows of lamp before the wall says anything
about the air conditioner, which is the "fourteen rows of lamps" the group exists to prevent, four
times taller.

**No reading is behind the tap**, which is the line that matters: how many lamps, how many are on and
how old the oldest of them is are all on the closed tile, and the panel's refusal is about hiding a
*reading*, not about hiding a name. What the tap opens is which lamp is which — the one thing the
row of discs never showed at any number of taps.

That single age is a problem, and it has to be solved rather than waved at: **a tile that cannot say
when it was last read is a bug**, and a group line quoting the freshest reading would hide a bulb
that stopped answering a week ago.

The rule, which is a pure function and gets a test:

- A bulb the panel **has no state for** — `isOn` null, which is `Reading.Never` on the capability —
  stays out of the group and renders as its own named quarter-width tile, whether or not the group
  is open.
- Every other bulb is in the group, and the group tile quotes the **oldest** `last_updated` among
  them, plus how many there are and how many are on.

Staleness is deliberately not the split, and that was the first draft of this. Poll freshness is a
group fact — one call feeds every bulb, so either all of them are stale or none are (see "Stale"),
and a rule that fires on all 28 at once is not a split. What genuinely varies bulb by bulb is
whether Yandex has any state for it at all, and that is the thing worth keeping out of the group: the
group tile is a claim that the panel knows whether each of those lamps is on, and for a `Never` bulb
it does not. It says "unknown" on a named tile instead, which is what the status line has always
said.

Two things the group tile deliberately does not do:

- **No master switch.** Yandex has no group action — one lamp is one call — so a switch here would be
  seven requests behind one finger, each able to fail separately, with one status line to report the
  mixture. The lamps keep their own switches, one tap further in.
- **Its colour answers the coarse question only:** on when any lamp is lit, off when none is. How
  many of the seven are lit is said exactly, in words, at wall size.

A stale *group* is still visible — the room's heading is marked and the group's error reaches the
group tile and every lamp opened under it. It is just not what decides who is in the group.

Which rooms have their lamps open is the panel's only piece of state that a person put there with a
finger. It is a `remember` and not a `rememberSaveable`: a tablet that rebooted at 04:00 comes up
closed, showing the counts, like a panel nobody has touched. The idle reset does not close them
either — it scrolls to the top, and an open group eleven sections down is out of sight rather than in
the way.

## Stale

Three things in this doc ask the same question — which bulbs leave the lights group, which rooms get
a mark on their heading, which tiles Главная pulls in — so it is answered once, in one function, and
that function is where the number lives.

**Stale means the panel has stopped reading, not that the flat has stopped changing.** Commit 1
shipped it the other way round and it was wrong. The rest of this section is why, because the
mistake is easy to make twice.

`BulbTileState.lastUpdated` comes from Yandex's `last_updated` on the capability — **when the device
last reported a value, not when we last read it.** A bulb switched on three weeks ago and untouched
since carries a three-week-old timestamp while every poll since has read it successfully. 33 of the
116 recorded capabilities are `0.0`, which is `Never`, and `ac-01`'s two capabilities are 81 days
apart. So judging health on that timestamp calls a steady device broken: it asks *has this changed
lately*, and the panel needs *have we been able to read this lately*. That is why Коридор's heading is
marked while both strips inside it are working.

The reading a poll produced is a group fact, not a tile fact. One `/v1.0/user/info` call feeds every
Yandex tile — it succeeded or it did not, and there is no per-bulb answer hiding inside it. So:

- Each `*PanelState` carries **`lastPolledAt: Instant?`**, stamped by `YandexPoll` / `TuyaPoll` when
  a refresh succeeds. Null until the first one lands.
- A group is stale when `lastPolledAt` is null, or older than **eight times its own poll interval** —
  2 minutes for the Yandex groups, 48 minutes for the recuperators (`POLL_INTERVAL` and
  `TUYA_POLL_INTERVAL` in `MainActivity`). Eight is a guess; the interval is not, and tying staleness
  to it means retuning either cadence carries staleness along instead of quietly falling out of step.
- The recuperators keep their per-tile exception. Tuya state costs one call per device, so
  `RecuperatorTileState.error` already says which one failed, and that stays a tile fact.

What survives from the first version: the vendor's `last_updated` is still what every tile *prints*.
"20 d ago" is an honest answer to how old a value is, and a bulb nobody has touched in three weeks
should say so. It is simply not a health signal, and the two must not be the same number.

The AC has two readings, the light strip three and the recuperator four. They print **one age each**,
and it is the oldest of them — see below.

### One age per tile

**A tile says how old it is once, and only when it is worth saying.** The rule and its threshold are
in `Staleness.kt` beside the poll's, because they are two halves of the same question and the panel
must not answer them with one number.

What was on the wall before it, on one recuperator:

```
on · 3 min ago · low + medium + high · 3 min ago
26.4 °C · 3 min ago · 41.0 % · 3 min ago
```

Four timestamps on one tile, three of them the same. The AC printed `on · 1 min ago · 22 °C · 81 d
ago`, the strip two ages plus `not controllable`. CLAUDE.md requires a tile to say how old its state
is; it does not require it to say so once per field, and that run-on is most of what made this wall
look busy beside the app it is judged against, which prints one short grey line under a name or
nothing at all.

- **One age, the oldest of the readings the tile is showing**, printed on the status line — the
  second line carries none. The oldest and not the freshest, for the reason the lights group already
  quoted its oldest lamp: a tile under-claims how current it is rather than hiding the reading that
  stopped moving. So the recuperator above says `3 d ago` while its humidity is 26 s old.
- **Under an hour, nothing is printed.** Yandex is read every 15 s and Tuya every 6 minutes, so a
  reading younger than that has been confirmed by dozens of polls and "3 min ago" is a line nobody
  acts on. An hour is a guess and is one constant, `WORTH_SAYING`; the vocabulary above it is hours,
  days and `never read`, which is why "just now" and "N min ago" no longer exist.
- **A value the tile does not have brings no age.** A capability that reported nothing prints
  `unknown`, and `unknown · never read` was that fact twice. So a bulb with no state says `unknown`,
  and an AC with no target says `off · unknown · 2 h ago` — the on/off's age, not the missing
  temperature's 81 days.
- The rest is untouched: `not controllable`, `no state to read`, `not installed` and `offline` all
  still print, and the promoted value is exactly what it was. The reason a poll failed prints too,
  and has since moved to the tile's second line — see "Why a poll failed".

_What this does not change:_ the vendor's `last_updated` is still what a tile prints, and the poll's
own staleness is still what marks a heading. A tile that has gone quiet for under an hour says
nothing about it — the room's heading is where that is said, and it is said about the poll.

### A curtain position expires

One tile breaks the paragraph above, and the vendor is the reason. `WORTH_SAYING` decides whether a
tile *prints* an age; on the curtain it also decides whether the tile still *states* the value.

Every other device on this wall reports its own changes, so an old timestamp means a quiet device: a
lamp switched on three weeks ago is still on, and that is exactly why staleness is about the poll and
not about the reading. **The flat's curtain does not report at all.** Its `range/open` has not moved
since the panel's own last write — not for a hand on the fabric, and not for a station opening it on
Yandex's own hub, while a light on that same hub reports an on/off within minutes (verified live
2026-08-31, see docs/yandex.md). So an hour on, its reading is not an old fact about the flat; it is
the panel quoting its own last write back at itself. The morning this was found, the wall said
`0% open` at 44sp in front of a curtain standing fully open.

**Which number that is, is not simply the percentage the vendor holds.** Neither of the curtain's two
capabilities is a sensor: `range/open` is the last percentage something commanded, `on_off` is the
last open or close, and the motor reports nothing of its own. So there is no reason to prefer the
percentage except that it carries a number — and preferring it puts `50% open` on the wall in front
of a curtain a voice command shut a minute ago. **The newer of the two commands is the position**, an
open or close read as the end of travel *the device named*, and its own reading is the age the tile
prints. Three spoken commands on 2026-08-31, every one matching the curtain in the room.

Past the hour the curtain falls back to what the tile already does for a position nobody has read:

| | fresh | history |
| --- | --- | --- |
| promoted value | `0% open` | — |
| status line | `0% open` | `0% open · 13 h ago` |
| mood | `Off`, or `On` above the minimum | `Unknown` |
| top-right button | Open | Close |

**The number is demoted, not deleted.** The status line keeps it next to the age that is the reason
for demoting it, the sheet keeps it labelled `position`, and the slider still starts from it. What it
loses is the four-metre line — the one place a tile speaks with no room for a caveat.

_It is the curtain's rule and not the wall's_, until a second device is shown to behave the same way.
Applied to the bulbs it would blank a lamp that is on and has merely been on for weeks, which is the
mistake the whole section above exists to prevent.

### Why a poll failed

**Four words, and no vendor ever writes one of them.** `Throwable.describe()` was
`message ?: className`, so Java's own sentence went onto a tile in the middle of a line whose other
half was the panel's:

```
not updating: Unable to resolve host "openapi.tuyaeu.com"
```

Two things were wrong with that and only one is about language. The string is *unbounded*, and it
was the last unbounded thing on the wall — so a vendor's error text decided how tall a tile came
out, which is what the whole anatomy above exists to stop anything doing.

`reason(Throwable)` in `BulbTiles.kt` maps it, **by exception type and never by message**, onto:

| Reason | What throws it |
| --- | --- |
| `unreachable` | `UnknownHostException`, `NoRouteToHostException` — the tablet's Wi-Fi or DNS |
| `timed out` | `SocketTimeoutException`, and `InterruptedIOException`, which is what OkHttp's own call timeout arrives as |
| `refused` | `ConnectException` |
| `failed` | everything else |

`describe()` writes the exception to `Log` and returns one of the four. Every error string on any
tile state comes through it, so a tile's `error` is one of these words by construction.

**`failed` covers two very different things and that is the price of this.** An I/O failure with no
name of its own, and the panel's *own* `error(…)` checks on a response it did not like — `HTTP 403`,
a `status` that is not `ok`, and the two configuration sentences the clients used to put on the wall
outright: Yandex's «no Yandex token stored — set yandex.oauth.token in local.properties and
reinstall» and Tuya's equivalent. Those are 76 characters and could not be on a 188 dp tile under
any rule; they are in `Log`, docs/yandex.md and docs/tuya.md say so, and finding them a home on the
wall belongs with `docs/design/panel-redesign.md` item 8.

**It is the tile's second line, not the first.** A quarter tile's status line is 156 dp — about
sixteen characters of `bodyMedium` — so `on · 20 d ago · not updating: unreachable` was never going
to be one line of anything. The rule is one for every kind: *why the panel is not updating this
tile, if it is not; otherwise the tile's second reading, or the one thing it has to say that is not
a reading.* A second reading is stale by definition once the poll behind it stopped landing, so the
strip's colour and the recuperator's climate give way to the reason while it lasts and come straight
back when the poll does.

**The lights group is the one tile that can be not updating with nothing to name**, since a poll can
stop landing without any call having failed. It says `not updating` for that and the reason for the
other, which are two facts and get two words.

**An offline recuperator stopped echoing what it can no longer confirm.** Tuya's `offline` used to
lead a queue of them — `offline · unknown · low + medium + high · not updating: timeout`, 62
characters on a tile that holds about 24, wrapped onto three lines and the longest thing on the
panel. `offline` replaces the power word now and the speed goes with it; what is left is the state,
its age, and the reason on the line below. The values are still on `RecuperatorTileState` — they are
not forgotten, they are not claimed.

### The recuperators before the first poll

Every other group heals in seconds. Yandex is one call every 15 s, so a poll that missed the Wi-Fi
coming up is retried before anybody reaches the hallway. Tuya is five calls every **6 minutes**, and
the recuperator tiles exist only once the inventory call has answered — so a tablet that rebooted
into a network that was not up yet shows **one line of error where five tiles belong, for six
minutes**. Seen on the wall on 2026-08-16: `Бризеры: not updating: Unable to resolve host
"openapi.tuyaeu.com"`, with the Yandex tiles already back. _That string is quoted as it was seen;_
the same failure reads `Бризеры: not updating: unreachable` now — see "Why a poll failed".

So the panel remembers who they are. `KnownRecuperators` keeps the last successful inventory — **id,
name, room, and nothing else** — in the same encrypted store as the credentials, because device ids
identify the flat. On a cold start those become tiles with no values on them: "unknown · unknown",
no climate line, third-width, and the group stale until a refresh lands, which is what marks the
heading and pulls them onto Главная.

What is deliberately *not* remembered is any value. A switch position from before the reboot is not
something the panel has read, and a tile printing it would be claiming a poll that never happened —
the same rule as "Stale", one layer down: the panel may remember what exists, never what it said.

A remembered tile is still tappable. The command needs an id and the re-read needs the device, and
both survive the restart; a tile on the wall that swallowed the tap would be worse than no tile.

**A tap on a recuperator does not repaint the tile in one go, because the device does not answer in
one go.** It takes the command instantly and reports the new state seconds later, so the panel reads
it again — at once, then after 2 s, 4 s, 8 s and 16 s — and stops as soon as the shadow says what was
written. Seen on the wall as the bug it fixes: the fan came on, the tile stayed "off" for the rest of
the 6-minute interval, and the sheet's speed buttons stayed disabled with it, since a speed is only
offered once power is confirmed. What the tile shows is still only what was read — a write the device
never reflects leaves it saying "off", and the next poll settles it. See docs/tuya.md.

A tablet with no usable keystore — restored backup, wiped key — remembers nothing and runs anyway.

**Seen on the wall, 2026-08-16.** The six-minute hole was real and reproduced twice: a cold start at
21:20 stood on `Бризеры: not updating: Unable to resolve host "openapi.tuyaeu.com"` with every
Yandex tile already up, and cleared by itself at the next poll — the host resolved fine from the
shell throughout, so it is the poll's cadence and not the network. After one successful inventory,
a restart shows all five recuperators inside a second: named, in their rooms, third-width,
"unknown · unknown", every room heading marked, and the whole set replaced by
real values 0.4 s later when the poll landed. `Бризер зал` then goes back to half-width with its
climate line, and the marks clear.

Two things that fall out of it, neither fixed: a placeholder has no climate line, so it is
third-width and its status line *wraps* onto two lines there — and "Бризер данина комната" wraps its
name too, so that one tile stands taller than the four beside it for the second it is up. And on a
tablet whose first read of the day fails, the headings of five rooms are marked at once, which is
the mark doing its job and looks alarming anyway.

## The scroll

**Главная** first, then the rooms, each behind a heading, all on one vertical scroll. Rules, each of
which exists because a wall panel is not a phone:

0. **It is a scroll and not a strip of tabs, and that is the second answer to this question.**
   Commits 1–7 shipped a `PrimaryScrollableTabRow`; on the wall it held fourteen rooms across 753 dp
   and could not. `Гардеробная` was clipped mid-word at the right edge and Ванная, Балкон and
   Гардероб were off the end entirely — so rule 2's "visible from Главная without opening the room"
   was false for the last third of the flat, which is what `design/panel-redesign.md` item 9 was
   about. Measured off the same capture, content stopped at 563 dp of a 1205 dp screen: **53 % of the
   wall empty**, every tile crammed into the top half. A horizontal control was the wrong answer to
   a vertical problem twice over, and the fix for both is the same one. _Measured after:_ on the
   Roborazzi capture at the wall's own 753 × 1204 dp the panel now fills the full height and the
   scroll continues past the bottom edge.
1. **It returns to Главная by itself.** After **2 minutes** with no touch, the wall scrolls back to
   the top, which is where Главная is. A phone app may stay where you left it; a wall panel is walked
   up to by someone who did not leave it there, and a panel showing Балкон because that is where the
   last person got to is a panel showing the wrong room to everyone after them.

   **The scroll position is otherwise left alone.** It used to be a `LazyGridState` keyed on the tile
   count, which threw the position away whenever any device appeared or disappeared — under the hand
   of whoever was reading a room at the time. That key was doing one useful thing and one harmful
   one, and only the useful half survives: **a rebooted panel goes back to the top when the first
   poll lands.** A tablet that came up into a Wi-Fi that was not ready holds nothing but its launcher
   tiles, and twenty tiles are then inserted *above* them; keyed items would hold the launcher in
   view and the wall would come up showing the last two tiles of the list. Those are the only two
   events that move it, and neither can fire under a finger.
2. **A room heading carries its own bad news.** A room is marked when its group's poll failed, or
   when every reading in it is stale. Without this Спальня can be dead for a day behind a Главная
   that looks fine. The mark is on the heading, which travels with the room — this is the half of
   the old rule the strip could not keep, because three rooms' marks were off the end of it.

   **The mark is a `•` after the title _and_ the title in the error colour — both, not either.**
   Commit 1 wrote it as a character *rather than* a colour, because there was no palette to trust in
   both themes; commit 5 wrote both schemes out and that stopped being true, so the colour is here
   now. The dot stays for two reasons that are this wall's rather than general principle. Colour
   alone is not a signal everyone can perceive, and this dot is the panel's only word that a room
   has gone quiet. And **Samsung's blue light filter is on permanently on this tablet** and tints
   the whole screen warm, which is exactly what erodes a red against a neutral — that has bitten
   here once already, on the bulbs, where a lit and an unlit lamp composited to two browns told
   apart by lightness (see "Icons"). The answer is the same both times: the shape carries the state
   and the colour reinforces it.

   **Measured on the glass, 2026-08-29, filter on, both themes**, on the tab strip this replaced,
   with the network dropped so that
   Yandex failed for real. The strip composited as `#F9E8CD` in light and `#402F13` in dark, both
   the values the bulb work recorded for a filter that is genuinely on, so these are real readings.
   A marked title came out `#C23F18` in light and `#F5B492` in dark against an unmarked `#366E82`
   and `#B7C1CD` — **ΔE 91 apart in light and 40 in dark**, where the filter costs 6 and 5 of that
   respectively. This is not a distinction the filter is going to erode; the tiles live at 13 to 19.
   A marked title against the strip behind it is 4.3:1 in light and 7.2:1 in dark **as composited**,
   against 6.2:1 and 10.9:1 as designed. _The light figure is marginally under WCAG's 4.5:1 for text
   this size, and it is the filter rather than the palette:_ an **unmarked** tab measured 4.7:1
   through the same filter against 6.2:1 designed, so the filter costs every label about the same
   and the mark is not what put it there. If it is ever worth fixing it is a palette change.
   _Not re-measured on a heading:_ the colours are the same two roles, but the type is now 52sp
   bold against the tab strip's 14sp, and a contrast figure taken at one size is not a figure at the
   other. **This wants a walk to the hallway** with the filter on, like the type scale does.
3. **The heading is the largest type on the wall.** `displayMedium` at 52sp, bold — a step above the
   44sp a tile promotes, which is what makes fourteen rooms navigable from across the hallway: what
   somebody is looking for when they walk up is the room, and only then the reading. A heading is
   full-width and wraps rather than clips, which is the direct answer to `Гардеробная`; the longest
   name the flat has, "Маленькая детская", measures 517 dp of the 753 and does not need to.
4. **Group failures stay above everything.** `groupFailures` prints the groups that failed before
   they ever had a tile. Those have no room to be marked in — a group with no tiles is in no room —
   so that line stays at the very top, above the Главная heading.
5. **Без комнаты is a section like any other**, last: it holds the recuperators when `TUYA_ROOMS` is
   unset, and a device falling off the wall because no vendor placed it is the bug that section
   exists to prevent.

   **It no longer holds the vacuum's launcher tile.** "Пылесос" was roomless until 2026-08-30 and
   rendered here, one scroll past all fourteen rooms; it is in the **Коридор** now, because that is
   where the vacuum docks. The section is for tiles waiting on an answer, not for one that has been
   given the answer "nowhere" — see docs/xiaomi.md.

   **A section with nothing in it gets no heading**, and that includes this one — which is a change
   from the strip, where Без комнаты was drawn empty or not. The strip's reason was that a tab that
   is not there is a section that cannot be opened; stacked, a section's tiles are on the same scroll
   as its heading, so an empty heading cannot lead anywhere and only claims a room that has nothing
   in it. Nothing can be lost by it: a tile is what makes its own section appear.

### What is on Главная

No settings screen. Favourites are defined in code, in one place, in the same spirit as `ROOM_ORDER`
— which is a list with a comment explaining the hallway it hangs in, not a preference.

The rule: **every tile in Коридор and Зал, plus every launcher tile, plus any tile anywhere that is
failing or stale.** The first two are the rooms switched on the way in and on the way out; the
launchers because the intercom is why someone walks up to this panel at all; and the last so that
rule 2's mark has somewhere to lead — a failing tile appears at the top of the wall, not only as a
dot beside a room name eleven sections down the scroll.

Its tiles are on the wall twice, once here and once in the room they are in. That was true of the
strip too and is the point rather than a duplication: Главная is a view of the flat, not a place
tiles move to.

This is a pure function of the room sections. It gets a test.

## Theme

- **The panel has its own palette, and `lightColorScheme()` on its own is not it.** Called with no
  arguments those builders return Material's *baseline*, which is a violet — and a violet run through
  the neutral surfaces of an unstyled panel is the grey-mauve wall this brief did not ask for. The
  schemes are written out with explicit values. **Neutral surfaces and one violet accent**:
  `#F5F6F8` background, `#E5E7EC` cards, `#202228` text and `#7047EB` accent in light; `#191B23`,
  `#30333D`, `#F3F4F7` and a lighter tone of the same violet in dark. It was two seeds — a cool blue
  for climate and a warm amber for light — until a photograph of the tablet ended them; see "One
  accent, and the amber that was a brown". Material's error ramp is kept as it is.
- `MainActivity` picks between the two by `isSystemInDarkTheme()`. No dynamic colour: the wallpaper
  of a kiosk tablet is not a design input, and on a wall that shows two rooms' worth of amber and
  blue, a palette that changes when somebody changes the launcher background is a panel that stops
  meaning what it meant yesterday.
- **A tile's colour still has two axes — but only one of them is the surface.** The domain fills the
  *accents* and the state fills the *card*, and the two swapped places in `feat(panel): the surfaces
  stop carrying hue`. Before that, domain filled the card: climate `primaryContainer`, light
  `tertiaryContainer`, everything else `secondaryContainer`, anything failing `errorContainer`. On
  the wall that read as a patchwork of colour blocks rather than as a set of tiles — a deep blue air
  conditioner, a dark amber strip, and two full saturated red rectangles among twelve — against a
  reference that paints every tile the same neutral dark grey and spends its whole colour budget on
  three small marks.
  - **One accent, on the three things a finger uses or looks for**, through `tileAccent`, which
    takes no argument any more: the round power button when the tile is on, the slider fill, and the
    20 dp lit dot. `primary` and not a container, because all three are drawn *on* a neutral surface
    and have to show against it — worst ratio 4.0 in light and 4.2 in dark, and the bar is the 3:1 a
    graphical object needs because the accent carries no text now. **The promoted value went
    neutral** with the rest of the words: it is `onSurface`, at 11.4:1 and 8.8:1 on the worst step.
    What kind of device a tile is, is said by the photograph of the hardware on it.
  - **State picks the step of the neutral ramp** the card sits on, through `surface`: `On`
    `surfaceContainerHighest`, `Failing` `High`, `Off` `surfaceContainer`, `Unknown` `Lowest`. One
    content colour, `onSurface`, on all four — they are all neutral surfaces now.
  - **The marks are the third thing**, and they are where the colour budget went. `marks` in
    `TileLayout.kt`, and it answers with a **set** rather than with one mark, because a state is
    allowed more than one way of saying itself:

    | State | Marks |
    | --- | --- |
    | on | a 20 dp dot in the accent, over the top-right of the art |
    | on | the round power button in the accent — neutral grey in every other mood |
    | on | the bulb and LED-strip art itself lights up |
    | this device's own poll failed | a 28 dp struck-through wifi glyph in `error`, over the top-right of the unchanged art |
    | off, or never read | nothing at all |

    **The redundancy is the point and is copied deliberately.** The reference says "on" three times
    on one tile, and this wall is read behind a blue light filter that erodes a saturated colour
    against a neutral — the same thing that made a room heading's mark a `•` *and* a colour. A mark
    carrying a state on its own is a state that can be lost.

    **The power button reads the mood and not only its last Boolean.** A failing tile whose device
    last reported on keeps that direction — it is the last thing known — but its disc is grey,
    because an accented button there would assert something the panel can no longer confirm.

    **The wifi glyph is a tile's own failure only.** Keyed on the group's it would draw on 34 of the
    35 tiles at once, which is the wall going red — the thing the outline exists to avoid.
- `hue(...)` **is gone**, and with it `TileHue` and `TileSheet.hue`. It was a pure function per tile
  type answering `Climate`, `Light` or `Neutral`; with one accent it answered the same thing three
  ways. `mood`, `surface`, `marks`, `paint` and `span` are still in `TileLayout.kt` and still where a
  test reaches them; the composable maps them to roles and does no thinking of its own.
- **No hex literals in the panel package.** A hardcoded colour is a tile that is unreadable in one of
  the two themes, and the theme that breaks is the one nobody is looking at when they check. The
  schemes are the one place values are written, and they are in the theme, not in `panel/`. Done in
  commit 2 and grep-clean; it stays that way.
- **`Off` and `Unknown` are two different neutrals, and that is settled.** They shared
  `surfaceContainer` until the surfaces went neutral, because there was said to be no second neutral
  to give them; there were five all along — `surfaceContainerLowest`, `Low`, `High`, `Highest` and
  the base — and the reason to spend them arrived when the ramp stopped being one family's
  compromise and became the whole panel's mood axis. `Unknown` takes `Lowest`.
  - **What `Lowest` means changed on the glass, and that is the one thing the tablet sent back.** It
    used to sit 2 L\* *past* the wall's own background, so an unread tile read as a hole rather than
    as a card. That holds only while the cards are barely off the background, which they were at 4
    L\*. Under the neutral palette they are 5 L\* off it on the *other* side, and "past the
    background" became the largest separation on the wall: on Главная, Домофон and Пылесос — which
    are `Unknown` for ever, because nothing polls a launcher — came out as two white cards among
    grey ones, the loudest thing on a panel that knows nothing about either of them. `Lowest` is now
    the step *nearest* the background, on the cards' side, and `PanelThemeTest` asserts that rather
    than the old rule. The words still say it too, where they always did.
- **`Failing` no longer fills the card**, which reverses commit 2's reversal and keeps what each of
  them was right about. Both halves are kept here because the reversal is the decision rather than
  the drift.

  Commit 2 painted it neutral, and that lost the signal: a failing tile that looks identical to a
  working one puts the whole weight on a status line nobody reads from four metres. The commit after
  it filled the whole card with `errorContainer`, and that cost the surface at the moment the
  surface was most needed — on the wall it came out as two of the twelve tiles on Главная being full
  saturated red rectangles, by a wide margin the loudest thing on the panel, spending the strongest
  signal available on "this one is offline".

  The third answer is the reference's red struck-through wifi mark, over the corner of the unchanged
  hardware art. It is local and explicit without making the device itself look red.

  **The boot case is answered rather than accepted.** Until the first poll lands every tile is
  `Unknown` rather than rose — a wall of quiet unmarked cards, which is what "nothing has been read
  yet" looks like. The `lastPolledAt == null` special case this doc held in reserve is not needed.
- **A group's failure outlines and a tile's own failure gets the offline glyph** — `docs/design/panel-redesign.md`
  item 4, landed with the neutral surfaces because it is the same question. One `/v1.0/user/info`
  feeds every ac, curtain, strip and bulb in the flat, so one failed call used to repaint about 34 of
  the 35 tiles in a single frame and erase the family coding exactly when somebody needed it. Now
  every kind follows the rule the recuperator already had: the group's bad news is a 3 dp `error`
  border and *nothing else* changes on the tile; the device's own bad news is the wifi glyph. `TilePaint`
  carries both and is the seam a test reaches.
- **Every tile on the wall is a card, so there is one colour table again.** The bulbs used to draw as
  72 dp discs reaching into `tileColors` through a `when` of their own, and that second copy had
  already drifted: the unlit disc took `onSurfaceVariant` where the card beside it took `onSurface`.
  The lamps are one group tile now (see "The lights group") and nothing outside `TileCard.kt` reads
  the table.
- **Confirmed: the tablet's dark theme is on a real schedule, 19:00–07:00** (`mNightMode=0 (auto)`,
  `customStart=19:00 customEnd=07:00`). So the dark scheme is not dead code and the theme commit is
  worth doing.

**What commit 5 actually wrote**, since "a cool blue and a warm amber" is a brief and not a value.
Four tonal ramps in `PanelTheme.kt`, generated in CIE LCh at a fixed hue and chroma per ramp with
the chroma clipped down to sRGB, one tone per role: **blue at Lab hue 272** — the hue of `#2196F3` —
at chroma 46 for climate, **amber at hue 72** — the hue of `#FFA000` — at chroma 70 for light, and
the same blue at chroma 3 and 9 for the surfaces and the outlines, which is Material's own way of
tinting neutrals with the seed. Material's error ramp is kept as it is.

The neutral family is the one that had to be solved rather than generated. It is told apart by
**lightness, not by hue**: sRGB holds no more than ~16 chroma of blue at tone 90, so a quieted blue
at the container tone comes out as the same colour as the climate blue — 12 ΔE, which on a wall is
one colour. Its container sits at **tone 75** instead of Material's 90, and that is what buys the
separations, measured as CIE ΔE between the three on-colours and the `surfaceContainer` every off
tile wears: worst pair 13 in light (climate against off) and 19 in dark, against 6 for the
tone-90 version of the same thing. Every on-colour is ≥ 7:1 on its container in both schemes.
- **Samsung's blue light filter is on** (`settings get system blue_light_filter` → 1) and it tints
  screencaps too, system UI included. Anything warm-looking in a screenshot of this panel is that
  filter and not the palette. Judge colour with it in mind, or turn it off first — which is harder
  than it sounds, and is now measured: see "The filter cannot be turned off the way this doc said"
  below.

### One accent, and the amber that was a brown

**A photograph of the tablet ended the two-seed palette.** The wall runs behind Samsung's blue light
filter at level 7 with Extra dim at 25 %, and that is a warm film over everything on the glass. The
"Theme" bullets above and the two measured sections below had already recorded that the filter erodes
a hue against a neutral; what the photograph showed is that it does something worse to two hues
against each other.

| What was designed | What is on the wall |
| --- | --- |
| `#F7F9FF` cool-grey surfaces | beige |
| `#0561A2` climate accent | a muted blue-grey |
| `#865301` light accent | brown |
| `#B3261E` error | brown-red |

So the wall's two families came out as beige and browner beige, and the one colour on this panel
whose meaning cannot be recovered from anywhere else on the card — red, the offline mark and the
group outline — was the nearest neighbour of the second family. Two states a metre apart, said in
two colours nobody could tell apart. The amber and the red are 29° apart in hue; the violet that
replaces them is 108°, and `PanelThemeTest` asserts the separation rather than describing it.

**What replaced it.**

- **Neutral surfaces, one violet, red reserved.** Violet is chosen *for* the filter and not in spite
  of it: a warm film subtracts blue, and what a violet loses is saturation rather than lightness, so
  it stays legibly other than the greys around it where an amber stops being other than a brown.
- **The accent is on the two controls and the one dot, and nothing else.** The promoted value — the
  44sp number this panel exists to show from four metres — was the largest thing wearing a family
  colour, which made it the largest thing going brown. It is `onSurface` now, like every other word
  on the card.
- **The families are not replaced, they are dropped.** The device art is the answer to "what kind of
  thing is this", it is a photograph of the actual hardware, and it was already there — a bulb looks
  like a bulb and lights up when it is lit. That is the warm light on this wall, and it is in the
  raster where the filter cannot make it mean something else.
- **Dark's accent is a lighter tone than light's**, which is the one departure from the brief as it
  was specified. `#7C4DFF` against the dark card at `#30333D` is 2.6:1, under the 3:1 a fill needs,
  and nearly all of that ratio is carried by the blue channel the filter takes away — so the power
  button would fade into its card at exactly 19:00. `#B49CFF` is the same hue two tones up at 4.2:1,
  and high enough in red and green to stay lighter than the card once the blue is drained. `#7C4DFF`
  is kept as dark's `primaryContainer`.

**The numbers are computed and not typed.** `PanelThemeTest` measures WCAG contrast for every role
the wall spends, on every step of the tile ramp, in both schemes, and checks that the ramp is
monotonic and that its bottom step sits past the background. Three times the contrast table in
`PanelTheme.kt` was retuned and retyped by hand; it is now a claim a test can fail.

**What is still unmeasured.** All of it is sRGB arithmetic on the JVM. Nothing here models what the
filter actually does — it rotates hue as well as draining it — and the hue separation above is a
proxy for the thing that matters. The wall is the measurement. See "Watch on the wall".

### The roles, measured on the glass

**This measured the mapping that has since been replaced** — the one where a tile's family filled its
card. It is kept because the *method* is the record worth having, and because it is the only time
anybody has fitted the wall's actual pixels against the scheme: whatever replaces the table has to
be checked the same way. The roles a tile takes today are in the two bullets above.

**Checked on the wall, 2026-08-17, both themes.** Every tile takes the role it should, and the
check was done by *measuring the screencap* rather than by looking at it, because looking at it is
the thing this tablet will not let anybody do — see the filter below. Each tile's pixels were fitted
against the scheme's four candidate roles; every one landed on its own by a wide margin, in light
(fit distance ≤ 8 against a next-best ≥ 30) and in dark (≤ 1 against ≥ 30):

| Tile | Role it landed on |
| --- | --- |
| Кондиционер, off | `surfaceContainer` |
| Подсветка в зале, on | `tertiaryContainer` — light |
| Бризер зал, on | `primaryContainer` — climate |
| The bulb circles, on | `tertiaryContainer` — _measured when the lamps were a row of discs. The discs are gone; their room's group tile is an ordinary card and takes the same `tertiaryContainer` from the same table_ |
| Домофон, Пылесос | `surfaceContainer` |
| The panel behind them | `surface` |

**Two of the values were not exercised and are still unseen**, both because of what the flat happens
to be doing rather than because of anything in the code. `secondaryContainer` — the neutral family's
*on* colour, the tone-75 one — needs a curtain that is open, and the flat's one curtain reads
`0% open · 3 d ago`, which is `Off`. `primaryContainer` in **dark** needs a climate tile that is on,
and by the time the dark capture was taken both the ac and the breather had gone off. Neither is a
code path — the hue-to-role map is one `when` and is theme-blind, and it was proven in light — but
they are two colours nobody has laid eyes on. Opening the curtain to see it would move the flat's
curtain, which is not a thing to do for a screenshot without asking.

_Both of those questions are gone rather than answered_: no tile is painted with a container any
more, so an open curtain and a lit ac in dark are ordinary steps of the neutral ramp with accents on
them. What has taken their place is one question of the same shape — nobody has seen the `Unknown`
step on the glass, and it is the step that goes past the background.

### The filter cannot be turned off the way this doc said

`settings put system blue_light_filter 0` **does not turn it off.** The setting reads back as 0 and
the screen stays warm. Verified against the framebuffer rather than by eye: pure white composites as
`#FFEDCD`, and the tint survived every one of `blue_light_filter=0`,
`blue_light_filter_scheduled=0`, `reduce_bright_colors_activated=0`, `screen_mode_automatic_setting=0`,
`screen_mode_setting=1` (Natural) and a display power cycle. There is no overlay window and no
SurfaceFlinger colour matrix, so it is applied in composition by One UI, which keeps its own state
that these keys only mirror. **The quick-settings tile is the only way found to move it.** It also
ramped up on schedule at 19:00 with `blue_light_filter_scheduled` set to 0, which is the same fact
again.

What it does, measured: **an amber overlay**, about `#FFB838` at ~22–25 % — a multiply on light
colours (gain R 1.00, G 0.94, B 0.81) and a lift on dark ones (`#111318` composites as `#402F13`).
So a screencap can be *un-tinted* arithmetically, which is what the table above rests on. Anything
warm in a screenshot of this panel is still that filter and not the palette.

The palette survives it better than expected. The three families' separations, as designed against
as the tablet actually renders them in light: climate/light 37 → 33, light/neutral 35 → 34,
neutral/off 20 → 20, and the tightest pair, climate/off, 13.1 → **12.2**. Muted rather than
collapsed — though a climate tile under the filter reads as a warm grey rather than as blue, which
is worth knowing before anybody concludes the blue is wrong.

Two things that cost time and are worth writing down. The tablet **relocks on screen-off** at the
2-minute timeout, so a check has to run inside that window or set `svc power stayon true` first; and
a `screencap` over the keyguard is a screencap of the keyguard, not of the panel. And on the
capture side: `zsh` has `noclobber` on here, so `adb exec-out screencap -p > shot.png` onto an
existing file **silently writes nothing** and the analysis then measures the previous shot. Three
"the setting made no difference" results came from exactly that. Use `>|`.

## Icons

**Current as of N4:** tile identity is the untinted 80 dp realistic PNG art in
`res/drawable-nodpi/`; the vector set below is the superseded implementation record. Vectors remain
for control and status symbols — power, curtain target position, and offline wifi — rather than for
device identity.

Every tile carries one. The panel today has none at all — no `Icon(` anywhere in `ru.domovoy`, and
`app/src/main/res` holds only `themes.xml` and `strings.xml` — which is why a wall of it reads as
paragraphs. A tile is recognised across a hallway by its shape and its glyph long before its name is
legible, and on a panel that is looked at on the way past, that is most of the looking.

**As vector drawables in `app/src/main/res/drawable/`, not as a dependency.**
`androidx.compose.material:material-icons-extended` carries the glyphs this needs, and it is a large
artifact to add for eight of them — and adding a dependency is an "ask first" in CLAUDE.md. Eight
glyphs exported to vector XML cost nothing at build time and are `res/` files, which is neither a
dependency nor a manifest change. Held: commit 6 added no dependency, and `res/drawable/` is the
whole of what it added outside `panel/`. It added nine; `ic_bulb_filled.xml` went with the disc.

| Tile | Glyph | File | Chosen over |
| --- | --- | --- | --- |
| Air conditioner | `ac_unit` | `ic_ac_unit.xml` | `air`, `thermostat`, `hvac` |
| Recuperator | `mode_fan` | `ic_mode_fan.xml` | `swap_vert`, `filter_alt`, `vent` |
| Light strip | `wb_iridescent` | `ic_wb_iridescent.xml` | `horizontal_rule`, `linear_scale`, `light` |
| Curtain | `vertical_shades` / `vertical_shades_closed` | `ic_vertical_shades.xml`, `ic_vertical_shades_closed.xml` | `curtains`, `roller_shades`, `blinds`, `shade` |
| Bulb | Tabler `bulb`, outlined — **not Material Symbols** | `ic_bulb.xml` | `lightbulb`, `wb_incandescent`, `tips_and_updates`, `emoji_objects`, `flare`, `lightbulb_circle` |
| Домофон | `video_camera_front` | `ic_video_camera_front.xml` | `doorbell`, `ring_volume` |
| Пылесос | `vacuum` | `ic_vacuum.xml` | `robot_2`, `smart_toy`, `cleaning_services` |
| *Not a tile* — the unreachable mark | `wifi_off` | `ic_wifi_off.xml` | `signal_wifi_off`, `cloud_off`, `sync_problem`, `link_off` |

**The eighth is the only glyph here that is not a device**, and it is drawn at 28 dp rather than 48:
it is a note in the corner about a tile, not the tile's identity. `wifi_off` ships with the strike
already in the path, which is what it was chosen for — a bar composited over a wifi symbol is a bar
that lands differently at every size, and the strike is the whole of what the mark means.

**The bulb comes from Tabler and the other seven from Material Symbols, and that mix is a
decision rather than an accident.** Six Material bulbs were rendered and none was the one wanted:
`lightbulb` is plain, and the bulb that had been approved all along has short rays around its top. It
had been approved all along because **every mockup in this project was drawn in Tabler** — so the look
being signed off was Tabler's, and Material's bulb was never the thing anyone had looked at.

Moving all seven to Tabler was the tidier answer and was turned down: it re-opens six settled glyphs
to fix one. The judgement is that the clash will not read, because the bulb is the only glyph that
never appears beside another — it sits alone in its own cell in the lights row, with no tile, no name
and no Material glyph anywhere near it. _If it does read on the wall, this is the note that says which
way to resolve it: move the other seven, not the bulb back._

Tabler is MIT, so the SVG is vendored into `res/drawable/` like the rest. It draws on a 24 grid
against Material's 960, so it is the one file here whose path data is not simply Google's numbers
moved into the viewport — and the one that therefore carries no `<group>` translate at all. It is
also stroked rather than filled: three paths of `currentColor` at width 2, round caps and joins,
which is Tabler's whole construction and is what carries the short rays around the top.

`video_camera_front` over `doorbell` because Domonap's call screen is video: the tile opens the app
you watch someone at the door through, and the glyph now says which of those two things it is.

**All seven Material names exist**, and the two nobody had rendered — `vacuum` and
`video_camera_front`, the ones this doc had down as names off a list — were confirmed rather than
assumed: `fonts.gstatic.com/s/i/short-term/release/``materialsymbolsoutlined/<name>/default/24px.svg`
answers **404 for a name the set does not have** (checked against a made-up one), and every candidate
answered 200. So the rejected `robot_2` and `doorbell` are real too, and `cleaning_services` was never
needed. Every one of the nine was then seen on the wall — see commit 6.

The path data is Google's, unmodified. The set draws on a 960 grid with a `viewBox` of
`"0 -960 960 960"`, so each file carries the negative y offset as `<group android:translateY="960">`
rather than as a rewritten path: the same numbers, moved into the viewport, and nothing to get wrong
by hand.

`wb_iridescent` over `horizontal_rule` because the plain rule is a minus sign: correct as a shape,
carrying no light, and indistinguishable from a divider on a panel that has dividers.

**The curtain's glyph follows its value.** It is the only tile whose icon carries state rather than
labelling a type, and it can because Material Symbols ships the covering icons as open/closed pairs.
The one curtain in the flat then says what it is doing from across the room instead of only in the
status line.

- `vertical_shades_closed` when the open percent is **0**, `vertical_shades` above it. A curtain 40 %
  open is open; only a shut one is shut. _The threshold is a guess and is one constant_ — if a
  curtain that has crept to 2 % reads as open on the wall and should not, this is the number. It is
  `curtainGlyph` in `TileLayout.kt`, and it is one of the two glyphs of the nine with a test — the
  other is the bulb's, below.
- **A null open percent takes the open glyph, not the closed one.** The closed glyph is a positive
  claim that the curtain is shut, and the panel does not know. Same rule the strings have always
  followed: unknown is not off, and the paint must not undo what the words were careful about.

- **Outlined, 24 dp**, tinted with the tile's content colour so the glyph and the text agree by
  construction and cannot drift apart in one theme.
- **Top-left of the tile, on its own line, on every kind and every span.** It used to sit beside the
  name on a half tile and above it on a third — a rule that existed because a 251 dp tile spending
  its width on a glyph had none left for the name. The anatomy answers that instead: art and the
  switch share the top line, and the name is at the bottom with the words, so there is no width to
  compete for and no second arrangement to get wrong.
- **The bulb wears the same lamp everywhere it appears** — on a lamp's own tile and on its room's
  group tile — at the same 24 dp as every other glyph. It labels a type and carries no state; the
  card's colour is what says whether anything is lit.

  <details><summary>The row of 72 dp discs, and what its four treatments measured</summary>

  Until the group tile landed, a room's lamps were a wrapping row of 72 dp discs: each a filled
  circle carrying `mood(isOn, error)` — `tertiaryContainer` lit, `surfaceContainer` unlit,
  `errorContainer` failing — with a 48 dp `ic_bulb` on it and no text at any size. Three treatments
  came before it and are recorded so the ground is not re-covered: a **white disc inset in a coloured
  tile**, two nested shapes with the glyph shrunk to fit; a **bare lamp with no disc at all**
  (commit 6), `ic_bulb_filled` in `tertiary` when lit and `ic_bulb` in `onSurfaceVariant` when not;
  and the filled disc that replaced it.

  Two measurements outlive all of them:

  - **A hue distinction really does erode on this wall.** With the filter on, commit 6's lit lamp
    composited `#865301 → #9E6301` and its unlit one `#3F4754 → #473719` — both brown, told apart
    mostly by lightness. Anything here that plans to say something with a hue alone should be
    measured the same way before it is trusted.
  - **`surfaceContainer` on `surface` is ΔE 4.0 as designed** in light and 6.3 in dark, and measured
    3.3 on the glass. That is the step every *off card* on this wall sits at, which is why an off
    tile is found by its text and its corner rather than by its fill.

  And one implementation trap: the halo that was mocked for the discs and dropped would have had to
  be a `Brush.radialGradient` rather than `Modifier.blur`, which is API 31+ against a minSdk of 26 —
  it would have drawn perfectly on this Android 13 tablet and silently nothing on Android 8 to 11.
  That trap is still there for anything else that reaches for a blur.

  </details>
- `contentDescription = null` on the glyph in every tile, with no exceptions left. They are
  decorative: the name is right there, and a screen reader announcing "lightbulb Лампа в коридоре"
  says the noun twice. The discs used to be the exception — they carried the lamp's name and state as
  the circle's own content description, because at 72 dp there was no room for either in text — and
  every lamp has a tile with its name on it now.

### The size, and the family question that is still open

**A glyph is 48 dp everywhere on the wall.** `GLYPH_SIZE` in `TileCard.kt`, one number, and
`TileGlyphTest` asserts the laid-out height against a literal 48 dp rather than against the constant —
a test that reads the constant passes whatever the constant becomes. It was 24 dp, which is a phone's
icon size measured for something held 30 cm from the face; the lamp on a bulb disc had been given 48
for exactly the wall reason, and when the disc went the number survived it. The drawables are vectors,
so 48 dp is redrawn and not resampled: a stroke at 2 of a 24 grid comes out at 4 dp. No tile changed
height for it — the anatomy's art slot reserves 64 dp and 48 fits where 24 sat.

**One family is not settled, and the way this doc said to settle it turns out not to be available.**
Above: _"move the other seven, not the bulb back"_. Measured against Tabler 3.31.0's 4,936 outline
icons, that direction has **no covering icon at all** — no `curtain`, `blind`, `shade`, `shutter`,
`roller`, `awning` — so the curtain would lose the open/closed pair that is the one glyph here
carrying state, and there is nothing for a light strip either. Three glyphs would have to be drawn in
Tabler's construction, which is what "Adding or drawing a glyph" below warns against.

Two premises above have also moved:

- **The weight clash is smaller than it was written up as.** Material Symbols outlined at weight 400
  strokes 80 of the 960 grid, which is 2 of 24 — Tabler's `stroke-width` exactly. What differs is
  terminals: Tabler's round caps and joins against Material's square. All eight were rendered at
  24/48/96 px on the dark surface to check.
- **"The bulb never appears beside another glyph" is no longer true.** That was the reason the mix was
  judged safe, and it held while the lamps were bare discs in their own row. The lamps are one group
  tile now and its lamp sits in a mosaic beside `mode_fan` and `vacuum`.

So the question is live again and is the mockups' owner's call, not an agent's. It is in **Open**.

### Adding or drawing a glyph

A glyph here is an Android vector drawable — XML with path data, not an image file — in
`app/src/main/res/drawable/`, read by `painterResource` and tinted at the call site.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000" android:pathData="…"/>
</vector>
```

Three ways to get the path data, in order of how much they fight back:

1. **Take the Material Symbol and modify it.** Download the SVG from fonts.google.com, edit the
   shape, convert. This is the route to prefer: the grid, the stroke weight and the terminals come
   with it.
2. **Draw it and export SVG**, then convert — Android Studio's `File > New > Vector Asset > Local
   file`, or `npx svg2vectordrawable in.svg -o out.xml` without it.
3. **Hand-write the path.** Fine for rods and rectangles, painful for curves.

The constraint that matters is not technical. **One hand-drawn glyph among the others looks wrong**,
in a way that is hard to name and easy to see: Material Symbols outlined at weight 400 sits on a 24
grid with a ~2 dp stroke and consistent terminals, and a glyph that misses any of those is the one
tile on the wall that looks like it came from somewhere else. Match the set or replace all of it —
not one.

### The glyphs, measured on the glass

**Checked on the wall, 2026-08-17, both themes, and the filter was off for it** — the light surface
composited as `#F7F9FF` and the dark one as `#111318`, both the scheme's own values to the byte, so
nothing below is an un-tinted reconstruction and no arithmetic stands between the wall and this table.
One observation to set beside "The filter cannot be turned off the way this doc said", and not a
second measurement of it: at **19:38** the screen was untinted with `blue_light_filter` at 0 and
`blue_light_filter_scheduled` at **1**, where that section recorded it ramping up on schedule at 19:00
with the schedule key at 0. So the keys still say nothing about what is on the glass — this time in the
other direction.

| Check | Result |
| --- | --- |
| All nine glyphs draw | Every one of them, and none is the empty box a bad path renders as |
| `vacuum`, `video_camera_front` — the two unverified names | Both real artwork on the tablet: an upright vacuum, and a camera with a face in it |
| The curtain's pair | Visibly different at 24 dp — four tight slats shut, three gathered ones open |
| Half beside, third above | `Кондиционер`/`Подсветка`/`Бризер`/`Шторы` beside the name, `Домофон`/`Пылесос` above it. **Superseded:** the anatomy puts every glyph top-left, and this row records the build that was on the glass that day |
| The bulb circles | 48 dp lamp alone in its 72 dp cell, no container in either state — a filled `tertiary` lamp for a lit one, an outlined `onSurfaceVariant` lamp for an unlit one. **Superseded:** the row is one group tile, and its lamp is the ordinary 24 dp glyph |
| Track height | 13 px = **6.1 dp**, both schemes. **Superseded:** the track is 20 dp now — 6 read as a hairline rather than as something to take hold of. Unmeasured on the glass at the new height |
| Touch area | **64.0 dp** on both sliders, dumped as `SeekBar` |
| Slider colours, light | Fill `#0561A2` = `primary` on the ac, `#865301` = `tertiary` on the strip; rest `#D1B9A2` and `#C2C6CD`, both the 24 % composite to the byte |
| Slider colours, dark | Fill `#FFB863` = `tertiary`; rest `#7B5F33` and `#44484E`, again the composite to the byte |

The `primary` fill was confirmed the way commit 5's roles were — against something else the scheme
paints with the same role, here the light strip's checked `Switch`, which measures the same
`#0561A2`. Two things through the same filter is a filter-independent check, and it was worth keeping
even on a day the filter turned out to be off.

**The open curtain needed a throwaway build to see**, and it is the one glyph the flat cannot show on
demand: the curtain reads `0% open · 3 d ago`, so the wall only ever draws the closed one, and opening
the flat's curtain for a screenshot is not a thing to do without asking. So `curtainGlyph` was locally
forced to the open branch, installed, captured beside the closed one, and reverted — the shipped code
is the reverted code and `TileLayout.kt`'s diff is additions only. What that check buys is the thing
`./gradlew test` cannot: **a vector drawable with bad path data fails silently as an empty box**, and
the open shades are 141 characters of path data nobody had rendered on this device.

**Checked again on 2026-08-18, and this time the filter was on the glass** — which is what the bulb
change needed, since its whole argument is about what the filter does to a hue. The surface composited
as `#F9E8CD` rather than `#F7F9FF`, about what "an amber overlay at ~22–25 %" predicts, so this pass is
a real reading and not the arithmetic un-tinting the filter section rests on. `blue_light_filter` read 1
for it, agreeing with the glass for once.

| Check | Result |
| --- | --- |
| The lamp pair, filter on | Lit `#9E6301`, unlit `#473719` — from `#865301` and `#3F4754`. Both brown under the filter, and told apart by shape rather than hue |
| A row of both at once | Unmistakable at 1:1 from a throwaway build: filled amber lamps against dark hollow ones |
| The unlit lamp, for real | Кабинет holds one lamp and it is off, so this one needed no forcing |
| The open curtain, for real | Спальня read `100% open` on the second pass — the curtain had moved on its own, so the glyph the flat could not show in the first pass showed itself in the second |
| The closed curtain | Forced instead, this time, being the state the flat was no longer in. Four tight slats at 6× against the open one's three |

The lamp measurement is the one worth keeping, because it settles an argument rather than recording a
colour: **the filter erodes an amber-against-neutral distinction and leaves a filled-against-hollow one
intact.** That was the reason for spending a second drawable on the bulb, and it was a prediction until
this pass. Anything else on this wall that plans to say something with a hue alone should be measured
the same way before it is trusted.

Two throwaway builds for it, both reverted, and `grep THROWAWAY` is empty in the shipped tree. The
mixed row is the honest way to check a distinction — a lit row on one tab and an unlit lamp on another
proves each glyph draws but not that the two read apart at a glance.

Still unseen, both for the same reason as before — the flat is not doing the thing that would show
them: a **broken-out bulb tile** (every bulb has state today, so the glyph-above-the-name layout is
proven on the launchers only) and a **third-width recuperator** (all five report climate).

## Sliders

Three tiles have one: the air conditioner's temperature, the curtain's position, the light strip's
brightness. All three are Material's `Slider` today, at its default weight — a 16 dp track and a tall
handle — and on a 376 dp tile that control is the loudest thing on it, louder than the value it sets.

**Slim: a 6 dp fully rounded track and no visible handle.** The filled portion is the value. It reads
as a reading with a range behind it rather than as a control demanding to be operated, which is what
a wall panel wants — the number is the point and the slider is how it is changed, not the other way
round.

- Built with Material 3's `Slider` and its slot overrides — a custom `track` at **20 dp** and a
  `thumb` that renders nothing. It was 6 dp, and the reasoning behind 6 stands — Material's own is a
  16 dp track with a tall handle beside it, and on a wide tile that assembly is louder than the value
  it sets — but 6 went past quiet and came out decorative: a hairline that reads as a rule between
  two lines of text rather than as something a finger takes hold of, which is a real cost on a slider
  with no handle to announce itself either. Not a hand-rolled draggable: the slot version keeps the drag behaviour, the
  value semantics and the accessibility that a `Box` with a `pointerInput` would silently drop. An
  empty thumb is safe rather than clever: the slider wraps each slot in a `Box` of its own and
  measures that, so an empty one is a zero-size box and not a missing child. Both slots are annotated
  `ExperimentalMaterial3Api` on this BOM, opted in on the one function rather than module-wide —
  `build.gradle.kts` keeps the Expressive opt-in it already had and gained nothing.
- **The touch area stays 64 dp tall** whatever the track looks like. A drawn bar is not a target,
  and this is the wall panel's rule that overrides the aesthetic one. It is the *track slot*
  that is 64 dp, with the bar drawn centred inside it, because the slider's height is the taller of
  its two slots and its drag handling covers exactly that — a `heightIn` on the outside would have
  left the gesture on the bar. _Measured:_ both sliders on Главная dump as **64.0 dp** tall, as
  `android.widget.SeekBar`, which is also the value semantics surviving the override. The track
  measures 13 px = **6.1 dp** in both schemes, and both the fill and the rest-of-track composite to
  the byte — the full reading is in "The glyphs, measured on the glass" under "Icons", which is the
  pass that captured them.
- The filled portion takes the tile's domain colour, the rest a low-emphasis neutral. Same two axes
  as everything else, so a climate slider and a light slider do not come out the same colour. The
  *accent* of each family rather than its container — `primary`, `tertiary`, `secondary` — because the
  container is what an on tile is already painted with, and a climate slider on a climate tile has to
  be the blue that shows against pale blue rather than that same pale blue again.
- The rest of the track is **`onSurfaceVariant` at 24 %, composited over whatever the tile is
  wearing**, and that is the one number here that had to be worked out rather than picked.
  `outlineVariant` was the obvious role for it and is **the same value as `secondaryContainer` in the
  dark scheme** (`#3F4754`), so the track would have disappeared on exactly one tile — an open curtain
  at night, which is the tile nobody has looked at. A neutral composited over the container cannot do
  that. _The 24 % is a guess and is one constant._
- The dragged value stays local and commits on `onValueChangeFinished`, exactly as `AcTile` and
  `CurtainTile` already do — the tile behind it only changes on the next poll, and binding straight
  to it drags the handle back out from under the finger.

_Accepted with its cost:_ a slider with no handle does not announce that it can be dragged, and
nobody standing at a wall gets a tooltip. The three tiles that have one are the three whose status
line already prints a number and a unit, which is the hint there is; whether that is enough is a
thing to watch on the wall rather than to argue about here.

## The button at the top right

The reference app carries one or two small round buttons on every tile and picks them by device
type — power and a fan mode on an air conditioner, power and a reset on a lamp, power and an
overflow on a TV. Here power is now the same round button — a 44 dp disc inside the 64 dp touch
target — and is not repeated as a second action. `action` in `TileLayout.kt` is the additional
per-kind control, beside `controls`, `promoted` and `span`, and is a pure function of type and state.

**One kind has one, and the rest have none.** The curtain gets the end of travel it is not already
at — Open when it is shut, Close otherwise, including when its position has never been read. It
drives to the ends of the range
*the vendor reported* rather than to 0 and 100, on the same rule `Bounds.snap` exists for.

**Why the others are empty is the whole of the decision, and each one is a refusal made elsewhere in
this repo already:**

- **The air conditioner's fan mode is an unverified endpoint.** docs/yandex.md still lists "what a
  `devices.capabilities.mode` action body looks like for this AC, and whether it is accepted" as
  open. AGENTS.md: say what is unknown rather than write against it.
- **The recuperators' speeds are unverified in the same way, and Tuya is metered.** A button that
  reads spends monthly allowance every time somebody walks past and fidgets.
- **The strip's colour is reported and not controllable**, which the tile already says in words.
- **The launchers get nothing.** They open somebody else's app; there is no state to act on.
- **The lock gets nothing, and gets nothing when it exists.** There is no lock tile in `panel/` yet,
  so this is a rule waiting for a subject rather than a case an assertion can reach; it is written
  where the overload would go. It reports and does not act — no action, no power button, no slider. See
  docs/aqara.md.

**Never two, and never on a quarter tile — that is width and not taste.** The target is 64 dp
(`MIN_TOUCH`; the reference's "a third the width of the art" is a phone's measurement, and the ring
drawn inside it is 40 dp). A third tile is 219 dp of content: art 80, reserved power box 64, and
button 64 — 208, with 11 to spare. The state mark overlays the art. A quarter tile has 156 and cannot hold the first one; a second
one fits on nothing.

**It is drawn last on the art line, after the reserved power box**, so that it lands in the corner.
Drawn before it, the one tile that has a button — a curtain, which has no power control — came out with the
button 64 dp in from the edge and an empty square beside it. Outlined and neutral rather than
filled: the four steps of the ramp are 5 L\* apart in dark and 2 in light, so a filled disc
disappears on one mood, and a control that is merely available is not news worth spending the
colour budget on. Its vector glyph is the state it produces; the realistic curtain art remains the
hardware identity. Its `contentDescription` is the one on this wall that is not null — the device
art itself sits beside a name that says the same thing.

## The device sheet

A tap on a tile opens one surface over the wall showing that device whole: `DeviceSheet.kt` draws
it, `TileSheet.kt` decides what is on it. Everything that decides is a pure function next to
`anatomy`, `controls` and `action`, for the reason all of those are out there.

**It is not the reference app's split, and the rule is the one thing to read before changing it.** A
phone app is opened, tapped, read and closed, so its tile can be almost empty; this panel is read
*without being touched*, so the tile is where detail belongs. **Nothing moves off a tile into the
sheet.** A number that lives only behind a tap has turned a glance into a walk, and a change that
does it is wrong however much better the tile looks afterwards. What the sheet adds is the two
things a 251 dp card genuinely could not hold:

- **An age per reading.** "One age per tile" is right for a wall read from four metres and is not an
  answer to "how old is *this* number" — on `ac-01` the on/off and the target were read 81 days
  apart and the tile prints the older of the two for both. The sheet breaks the pair back out. Its
  wording is its own, `sheetAge`, and deliberately not `ageLine`: the wall says nothing under an
  hour, and the sheet always answers, minutes included, because that is the question it was opened
  with.
- **The actions that did not fit.** One 64 dp button is a third-width tile's lot; the sheet is
  753 dp wide.

**What a kind offers is `sheetActions`, and it is a table with a refused row.**

| Subject | Actions |
| --- | --- |
| air conditioner | power, level |
| light strip | power, level |
| curtain | level, open, close |
| bulb | power |
| recuperator | power |
| **lock** | **nothing** |

Power is `on_off` and the level is `range` — the two requests this panel has actually sent to real
devices (docs/yandex.md, "Run on the tablet"). Everything else the reference sheet has is left out
rather than guessed: the `Color` section and `Modes` need a `color_setting` and a `mode` action body
that are still open questions, `reset` is not a capability any vendor here reported, and the
recuperators' three speeds are an unverified Tuya command path *and* a metered allowance. The
strip's colour is on its sheet as a reading and still says "not controllable".

**The lock's empty row is the load-bearing line.** It reports and does not act — no unlock, no open,
no door release, and no power switch that could be mistaken for one (CLAUDE.md, docs/aqara.md).
There is no lock tile in `panel/` because Aqara's project is still in review, so `SheetSubject.Lock`
exists with nothing constructing it: the rule is written before the tile that would have to obey it,
and `TileSheetTest` asserts it as its own case rather than as an absence.

**Two tiles open no sheet at all**, and `subject` says so out loud rather than by omission. The
launchers open somebody else's app and have no readable state behind them — Xiaomi will issue no
credentials and Domonap has no API the panel calls. The lights group's tap was already spoken for:
it opens the room's lamps in the grid, each of which is a bulb with a sheet of its own.

**It is drawn inside the panel's own composition**, in a `Box` over the grid, and not in a `Dialog`
or a Material `ModalBottomSheet`. Two reasons, both practical: a sheet in a window of its own is a
sheet `compose.onRoot().captureRoboImage` cannot see, and this wall is checked by picture; and the
overlay makes it obvious that the sheet is a thing *this* app draws, which is why it cannot be in
front of Domonap's call screen — that is another app's activity.

**Which device is open is hoisted out of `PanelRooms`**, next to the scroll position and for the
same reason: two things outside the composable close it.

- **The idle reset.** `returnToHome` is what the two-minute reset calls now — close the sheet, then
  scroll to the top. A sheet left open by a passer-by is a panel that has stopped being a panel.
- **An intercom call.** `closeOnCall` puts it away at the *start* of the call, so that when the call
  is over the wall is a wall rather than the one device somebody was looking at. Nothing there
  cancels, delays, covers or silences anything of Domonap's; it only lets go of our own screen.

**The state is a device id, not a tile.** A poll lands every fifteen seconds and replaces every tile
state in the panel, so a held tile would be a sheet frozen at the moment somebody touched it; an id
is looked up again each frame, which also means **the sheet starts no poll of its own**. Tuya is
metered by the month, and a sheet that read faster while open would spend that allowance on being
looked at. An id matching nothing draws nothing.

The controls carry no words of their own — the reading directly above names them, exactly as a
tile's unlabelled switch is named by the status line under it. The one word on the sheet that is not
a reading or an action is `done`, which closes it, and it is not called "close" because the curtain's
own action is.

## Compose APIs

The Compose BOM is already `2026.08.00`, so Material 3 Expressive is on the classpath and **no new
dependency is needed** — which is the reason this direction was picked over anything needing a card
library.

- ~~`PrimaryScrollableTabRow` for the tab strip.~~ Done in commit 1 and removed again: the rooms are
  one scroll of headings now, and no tab row is on the wall. See "The scroll".
- `LazyVerticalGrid` with `GridCells.Fixed(12)` and `GridItemSpan` for the mosaic. Done, commit 2 at
  six columns and twelve since the anatomy landed. ~~`FlowRow` for the lights group~~ — the row of
  circles it laid out is gone; a room's lamps are one ordinary tile in the grid, and the lamps it
  opens are ordinary items after it.
- ~~`MaterialShapes` + shape morph on press for the bulb circles.~~ **Not available.**
  `MaterialShapes` is not in material3's `classes.jar` on the 2026.08.00 BOM — it lives in a
  separate artifact, which would be a new dependency and therefore an "ask first". Shapes are
  `RoundedCornerShape` at the two documented radii instead, which is what the corner rule asked for
  anyway. The open question about whether morphing costs a frame on this tablet is therefore moot,
  not answered.
- Motion is the expressive spring specs, not a duration and an easing curve.

Expressive components are opt-in annotated, so `app/build.gradle.kts` gained one in its
`compilerOptions` block. **The annotation is `androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`**
— confirmed by reading the class out of material3's `classes.jar` on this BOM, not taken from the
release notes.

## Testing

TDD, and the test comes first in the same commit.

The unit test dependencies are JUnit5, kotlin.test, Turbine and MockK. Every rule in this doc that
can be got wrong is written as a pure function over the tile states and tested directly, the way
`RoomSectionsTest` tests `roomSections`. That is not a workaround; it is the same reason the room
order is a function and not a layout, and it stays the first choice for anything with an answer to
assert on.

There is now a Compose test dependency, and it is there for the things that have no such answer —
see "Screenshots" below. It changed nothing about the rule above: a decision that can be a function
is still a function, and no assertion moved into a `@Composable`.

What gets a test, each asserting the value returned and not which composable was called:

- The favourites list: Коридор and Зал tiles present; launchers present; a stale tile from Спальня
  pulled in; a fresh tile from Спальня not.
- The heading marks: a room marked when its group errored, marked when everything in it is stale,
  unmarked otherwise.
- The bulb split: a bulb with no `isOn` stays out of the group however fresh its reading, and one
  with an `isOn` is in it however old — the group tile quotes the oldest of those that are. Plus the
  one thing about the group that is Compose state rather than a pure function: the tap opens the
  lamps, names and all, and a second tap puts them away.
- The heading list: Главная first, rooms in `roomSections` order, Без комнаты last, and a section
  with no tiles in it dropped rather than given an empty heading.
- The tile layout: the recuperator's span from whether it reports climate, and `mood` from `isOn`
  and the error. Added in commit 2, which is where "a re-skin has nothing to test" turned out to be
  wrong: both are decisions with a right and a wrong answer, and a decision that only exists inside
  a `@Composable` is a decision no test can reach.
- The one glyph of the eight that is a decision rather than a picture: the curtain's. 0 is closed, 40
  and 100 are open, and **null is open** — a shut curtain is a positive claim the panel cannot make.
  The other seven are a lookup from tile type to drawable and hold nothing a test could catch, the
  bulb's included since its card's colour carries the state; what is asserted there is only that a
  lamp and its room's group tile draw the same `ic_bulb`. Both assert
  `R.drawable.*` ids directly — the generated `R` is on the unit test classpath, so this needs no
  Compose and no Robolectric.

What no assertion can reach — the spans as drawn, the shapes, the two palettes side by side,
**whether a glyph draws at all** — is now recorded as images rather than only looked at. A vector
drawable with bad path data is still not a build error and still not a test failure; it is an empty
box, and an empty box is visible in a screenshot. See "Screenshots".

What remains the tablet's alone, because nothing off the glass can answer it: the touch targets in
real px, the idle reset actually firing, the blue light filter over the palette, and whether any of
it is legible from four metres.

### Screenshots

Eight images in `app/src/test/screenshots/`, committed, drawn by Robolectric and Roborazzi at the
wall's own geometry — **753 × 1204 dp at 340 dpi, portrait**, which is the 1600 × 2560 px the tablet
measured. A screenshot at any other width is a picture of a panel that does not exist, since the
column widths are sized from that 753 and from nothing else.

The one exception is the swatch sheet, and only in *height*: `tiles-light` and `tiles-dark` are
captured at 753 × 1700 dp. Thirteen swatches at 296 dp is 1480 dp of column, and in a 1204 dp frame
the failing row and the outlined case fell off the bottom and were recorded as nothing at all. They
are not a picture of the wall; the two Главная captures are, and those keep the wall's own frame.

| Image | What it is for |
| --- | --- |
| `panel-home-light`, `panel-home-dark` | Главная whole, in both schemes: the spans, the corner, whether two kinds of tile actually end on the same line |
| `tiles-light`, `tiles-dark` | Every `TileHue` × `TileMood` pair, plus the group-failure outline. This is the ΔE table in `PanelTheme.kt` made visible — and, since the marks became a set, the one picture of every mark: each swatch carries a round power button, accented on the lit row and neutral on the failing one, so a row of three that comes out one colour is Material's `primary` leaking back in |
| `lights-group` | The `Never` bulb's own tile beside its room's group tile, closed and open — three cards at the quarter width, which is where a group tile that stopped agreeing with an ordinary one would show |
| `device-sheet-light`, `device-sheet-dark` | One device sheet over the real Главная, in both schemes. The recuperator on purpose: it is the tile whose four separately-timestamped datapoints the wall prints one age for, so its sheet is the four rows and four ages that are the argument for a sheet existing. What the picture is for is the pair of things no assertion sees — that the tiles behind the scrim are still legible, and that the sheet is unmistakably in front of them |
| `headings` | A plain heading, a marked one, and the longest room name in the flat at heading size |

```bash
source scripts/env.sh && ./gradlew verifyRoborazziDebug
```

```bash
source scripts/env.sh && ./gradlew recordRoborazziDebug
```

`verify` fails on any difference and writes the expected, the actual and a side-by-side into
`app/build/outputs/roborazzi/`. `record` rewrites the references — run it after a deliberate change,
then **look at what it wrote**. A reference nobody opened is a test that asserts whatever the code
did on the day, which is not the same as asserting the panel is right. A plain `./gradlew test` runs
these eight as ordinary tests and neither records nor compares, so the other 342 keep costing what
they cost.

The fixtures are in `app/src/test/kotlin/ru/domovoy/panel/Flat.kt`: one flat's worth of tiles with a
fixed `NOW` and fixed readings. A clock in a screenshot test is a test that fails every minute —
every tile on this panel prints how old its reading is.

**These render on the JVM, not on the tablet.** Robolectric has its own fonts and its own text
layout, so an image here is the panel's *layout and palette* and not a preview of the Galaxy Tab.
There is no CI, so the references are whatever machine last recorded them — a diff that is nothing
but text antialiasing is a machine difference, not a regression. What they are trusted for is
geometry and colour; the row in "Watch on the wall" that only the glass can answer did not move.

**Do the tablet check before calling a layout commit done, not after.** Commit 2 shipped its span
table twice: once from a guessed panel width, and again from the measured one after the wall showed
what the guess looked like. Two of the five checks above came back negative the first time. The
tablet is the only thing that can fail them, and it takes about ten minutes:

```bash
source scripts/env.sh && ./gradlew installDebug
```

Then `adb shell uiautomator dump` for the hit areas — it reports Compose semantics as accessibility
nodes, so switch bounds come out in px and divide by 2.125 for dp on this tablet. `adb shell cmd
uimode night yes` forces the dark palette, and `auto` puts it back. The screen sleeps after 2
minutes and locks behind a PIN; `adb shell input keyevent KEYCODE_WAKEUP` immediately before a
`screencap` avoids capturing a black frame.

`./gradlew test` and `ktlintCheck` green before push, as everywhere else.

## Watch on the wall

Not tasks — bets already placed, each with the note that says which way to resolve it if it turns
out wrong. None of them can be settled from a screenshot.

- **Does anyone work out that the sliders are draggable?** They have no handle. If not, the answer is
  a handle, not a thicker track.
- **Does the violet survive the filter, and is one accent enough?** This is the bet the whole
  palette rests on and it was placed from a photograph of the *old* one — nobody has seen the new
  accent on the glass. Two ways it can be wrong. If the violet itself goes muddy behind the filter,
  the answer is a lighter tone of it in *both* schemes, not a second hue: the filter is why there is
  one. If instead the wall reads as undifferentiated — every tile the same card, nothing saying
  which of the 35 is an air conditioner — the answer is the device art, which is already the thing
  carrying that, made larger; it is not the families coming back.
- **Is the lit dot still doing anything?** It is the accent in a third place on a tile that already
  has an accented power button and, on lamps, art that lights up. The redundancy is deliberate — see
  the marks table — but it was sized against three family colours, and with one accent a wall of lit
  tiles is a wall of identical violet dots. If it reads as noise, it is the mark to drop: it is the
  only one of the three that says nothing the other two do not.
- ~~**Does Главная read as alarm at boot?**~~ **Gone with the filled `errorContainer`.** Nothing is
  rose at boot any more: an unread tile is the quietest step of the neutral ramp, which is what
  "nothing has been read yet" looks like.
- ~~**Does an `Unknown` tile read as a hole or as a missing tile?**~~ **Half-answered on the
  glass, and it came back the other way round.** Under the neutral palette `Lowest` was still "past
  the background", and on the wall that made Домофон and Пылесос the *loudest* cards on Главная
  rather than the quietest — see "Theme". `Lowest` is the step nearest the background now. What is
  still open is the part the old note got right: **the launcher tiles are `Unknown` permanently**,
  so whichever way that step reads, it reads that way for ever on the two tiles nothing polls. If
  the quietest card on the wall turns out to be the wrong home for them, the answer is not a
  different step for everything — it is that a launcher's "no state to read" is a different thing
  from a device's "never reported", and `mood` has no value for it today.
- **Is a truncated package name useful or just untidy?** `com.example.vac…` is the wall's one
  ellipsis, on the tile of an app that is not installed. It is read at 30 cm by whoever is about to
  go and install it, so it may be that a cut package is no use at all and the honest answer is the
  first half of the name and the tile's *name* doing the rest. If so, the fix is a shorter string,
  not a second line: the cap stays.
- **Is 296 dp a tile that reads better or one that reads tighter?** The 80 dp art row gives the
  hardware room without restoring the old unfilled status reserve. If it comes out cramped, the
  space to give back is padding at the foot of the card, not the reserve.
- **Does a 20 dp on mark carry at four metres?** It overlays the enlarged art and is reinforced by
  the round power button on power-capable kinds and by lit art on bulbs and strips. If it does not
  carry, it grows before it changes shape.
- **Does a struck-through wifi glyph read as "not installed"?** It is a tile's own failure, and on
  Пылесос that failure is a missing app rather than a network. "The panel cannot get to this device"
  covers both and the second line says which — but a wifi symbol on an app that was uninstalled may
  be the panel naming the wrong cause. If it reads wrong on the wall, the mark takes a second glyph
  keyed on the reason rather than on the mood.
- ~~**Are two red marks one too many on a failing tile?**~~ **Resolved:** yes. The filled error chip
  is gone; the red wifi glyph alone overlays the unchanged device art.
- ~~**The unreachable mark is not in the corner on the narrowest tiles.**~~ **Resolved:** the glyph
  now overlays the top-right of the art and consumes no row width.
- **Does the Tabler bulb look foreign beside seven Material Symbols?** If it does, move the other
  seven to Tabler rather than the bulb back to Material.
- ~~**Can a finger find an _unlit_ lamp?**~~ **Dissolved by the group tile, not answered.** The
  question was about a 72 dp disc whose only drawn shape was its fill, at ΔE 4 from the surface in
  light; there is no disc. An unlit lamp is a card with its name, its age and its corner on it, which
  is the same step every off card on this wall already sits at and has never been a complaint. If a
  *room* whose lamps are all off ever goes missing on the wall, the group tile is an off card like
  any other and the question is the general one — see "Open", where `Off` and `Unknown` sharing
  `surfaceContainer` is still open for every tile at once.

## Open

- **Where the two configuration sentences go.** «no Yandex token stored — set yandex.oauth.token in
  local.properties and reinstall» and Tuya's equivalent are the most useful thing either client can
  say, and since "Why a poll failed" they are in `Log` and not on the wall: 76 characters do not fit
  a tile, and the group failure line at the top of Главная — which is 753 dp wide and could hold
  them — takes the same mapped word every tile does. A fresh install with no `local.properties` now
  says `Кондиционеры: not updating: failed` five times over and names nothing. This is a real loss
  and it wants a home; item 8 in `docs/design/panel-redesign.md` is the commit that would give it
  one.
- Status strings are English today (`on`, `never read`, `not updating`) while room names arrive in
  Russian from the vendors. The mosaic does not change that, and it should not be changed quietly as
  part of this work; if the panel is to speak one language it is its own commit.
- Whether the 2-minute idle reset is right, or whether it should be the screen's own dim timeout.
  Two minutes is a guess and is a single constant. The tablet's own screen timeout is 2 minutes as
  well, so today the two fire together; that is a coincidence of settings, not a design.
- The ×8 in "Stale". Tying it to each poll's own interval is right; eight of them is a guess, and the
  number that matters is how long a device can be quiet before somebody would want to know.
- Whether a **stale group** should reach the tile's paint, or only the heading mark. `mood` is a function
  of `isOn` and the error and nothing else, so a group that has stopped polling still paints every
  tile in it as confidently on. Commit 3 makes the signal trustworthy enough to be worth asking; it
  does not answer it, and wiring it in is a spec change rather than a bug fix.
- ~~What a tile should look like when `isOn` is null.~~ **Answered**: `Unknown` takes
  `surfaceContainerLowest` and `Off` the base container, so the two are different cards and not only
  different words. See "Theme". What is left of it is a wall check rather than a question — see
  "Watch on the wall".
- **Whether the eight glyphs need one family**, now that the Tabler bulb sits in the mosaic beside
  Material's `mode_fan` and `vacuum` rather than alone in a disc row. "Move the other seven" is not
  available — Tabler has no covering icon and no light strip — so the choices are the bulb back to
  Material, against the recorded reason it is Tabler's, or three glyphs hand-drawn. Whoever owns the
  mockups decides; see "The size, and the family question that is still open".
- **Whether 48 dp is the right glyph size**, which only the hallway can say. It is reasoned from the
  size the disc's lamp was already defended at, not measured at four metres.
- **The device sheet has never been opened on the wall.** Two numbers in it are guesses and are one
  constant each: `SCRIM_ALPHA` at 0.6, which is meant to leave the tiles behind it legible while
  putting the sheet unmistakably in front, and `LABEL_WIDTH` at 220 dp, which lines the values up
  and holds the longest label the flat produces (`temperature`). Both look right in the two recorded
  captures and neither has been seen from four metres, or behind the blue light filter — which is
  exactly what erodes a low-contrast neutral. The sheet is also anchored to the bottom of a
  head-height panel, which is the half of the screen a hand reaches most easily and the half the
  eye finds last; nobody has stood in front of it to say whether that is right.
- **Whether the recuperator's sheet should carry a power switch at all.** It does, on the grounds
  that the tile's switch already sends the same Tuya command and a second surface is not a second
  cost per tap. But Tuya is metered by the month and the command path is unverified (docs/tuya.md),
  so this is the one row of `sheetActions` that is a judgement rather than a verified capability. If
  the allowance turns out tight, it is the first thing to drop.
- The tablet is locked with a PIN and locks itself on screen-off. Nothing in the panel handles that
  — the wall goes to a lock screen rather than to the panel, and the Domonap takeover's behaviour
  over a locked screen is unverified. See `docs/domonap.md`.

Answered since this was written, kept here so they are not re-asked: the panel width (753 dp, see
"Tile sizes"), whether the dark theme is on a schedule (it is, 19:00–07:00, see "Theme"), and
whether shape morphing is affordable (moot — `MaterialShapes` is not on this BOM, see "Compose
APIs").

## History

Seven commits, all merged. What each of them decided is in the sections above; this is the record
that they happened, and what the doing of them taught that the planning of them could not.

| | Commit | PR |
| --- | --- | --- |
| 1 | `feat(panel): tab shell` — the tab strip, the favourites, the idle reset | #11 |
| 2 | `feat(panel): expressive tiles` — the six-column grid, the spans, the shapes | #12 |
| 3 | `fix(panel): stale means the poll stopped` — `lastPolledAt` instead of the vendor's `last_updated` | #14 |
| 4 | `feat(panel): group the bulbs` — the circles, and who breaks out of the row | #15 |
| 5 | `feat(panel): the panel's own colours` — the two schemes, and hue as the second axis | #17 |
| 6 | `feat(panel): an icon per tile, and a slimmer slider` — nine drawables, the 6 dp track | #19 |
| 7 | `fix(panel): the tab strip's touch height` — 64 dp on a `Tab` | #20 |

No `AndroidManifest.xml` change, no new dependency and no new permission in any of them. That held
for the whole of it, the portrait lock included — that is a device setting, not `screenOrientation`.

**What the order bought.** The shell first, because it is the only part with behaviour to get wrong
and it is worth having green before anything visual moves. 3 before 4, because the group line and
the heading marks both read staleness and grouping the bulbs on top of a signal known to be wrong means
doing it twice. 5 before 6, because a glyph takes its tile's content colour and drawing seven of
them against a palette about to be replaced is drawing them twice. 3 stayed its own commit because
it is the only fix among six features, and a correction folded into a feature is a correction nobody
can find again.

The order held. What it did not buy, and nothing in it could have: 2 was still wrong about the grid
until the grid was on the wall, because the number it was wrong about was a measurement and not a
decision.

The same caught up with the look. Commits 1 to 4 were specified entirely in behaviour — spans,
splits, staleness, what a tile *says* — and every one of them was right about that and silent about
whether the result was worth looking at. What went up was a correct grey panel of text: no glyph
anywhere, one colour for everything that is on, and a scheme nobody had chosen, because
`lightColorScheme()` with no arguments is a decision that reads like a default. 5 and 6 exist
because a brief can be complete about behaviour and still not have described the thing on the wall.
