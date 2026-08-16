# Panel UI

**Scope:** how the panel is laid out and drawn. Not what it reads — that is one doc per vendor.

**Status: commits 1 and 2 built and merged (#11, #12); 3 and 4 not started.** What was a brief is
now half a record. Numbers that were guesses when this was written and have since been measured on
the tablet say so and give the measurement; the ones still marked _measure on the tablet_ are numbers
nobody has taken, and are not settled just because they are written down.

The tablet is a Samsung Galaxy Tab S7 (SM-T875, Android 13). Everything measured below was measured
on it, on 2026-08-16.

## What is decided

1. **A tab shell.** One "Главная" tab holding the favourites, then one tab per room in the existing
   room order. Replaces the single scroll of 29 tiles.
2. **Material 3 Expressive mosaic tiles.** Mixed tile sizes and shapes instead of one full-width
   card per device: half-width tiles for the things with a slider, third-width ones for the rest,
   small circles for the bulbs.
3. **Both themes, following the system.** The panel is light by day and dark by night, driven by
   `isSystemInDarkTheme()`.

Rejected, and why, so it is not re-proposed: **fill-level tiles** (the tile's fill height or width
is the value, Apple-Home style). The recorded `/v1.0/user/info` holds 28 `devices.types.light`
against 3 ACs, 2 light strips and 1 curtain, and all 5 recuperators are on/off — so of the flat's
tiles, six have a continuous value and the rest have nothing to fill with. On those a fill bar is a
coloured rectangle that means "on", which the tile's colour already says.

## What the panel did before this work

The baseline commits 1 and 2 replaced, kept so the diff stays legible:

- [`PanelRooms`](../app/src/main/kotlin/ru/domovoy/panel/PanelRooms.kt) was one `LazyColumn`. Group
  failures at the top, then a `Text` heading per room, then every tile in that room as a full-width
  `Card`, in the fixed order ac → curtain → strip → recuperator → bulb → launcher. It is now a tab
  strip over a `LazyVerticalGrid`; the tile order within a room is unchanged.
- [`roomSections`](../app/src/main/kotlin/ru/domovoy/panel/RoomSections.kt) decides which room a tile
  lands in and in what order the rooms come. **It did not change, and has not.** The tab shell
  consumes exactly what it returns — one tab per `RoomSection`, in the order that function already
  produces, with the roomless section last under "Без комнаты".
- Every tile prints a status line ending in `ageLabel(...)`, and appends `not updating: <error>` when
  its group's poll failed. Still true: commit 2 was a re-skin and changed no string.
- `MainActivity` wraps everything in a bare `MaterialTheme {}` — no colour scheme is passed, so the
  panel is on the Material baseline light palette in both system themes. **Still true**, and it is
  what commit 4 is for.

## Tile sizes

A **six-column** grid, laid out in halves and thirds. The span is a property of the tile type, not
of the room:

| Tile | Count | Span | Width | What it shows |
| --- | --- | --- | --- | --- |
| Air conditioner | 3 | 3 (half) | 376 dp | Name, target temperature at display size, on/off, temperature slider, both ages |
| Curtain | 1 | 3 (half) | 376 dp | Name, open percent, slider, age |
| Light strip | 2 | 3 (half) | 376 dp | Name, on/off, brightness slider, colour, both ages |
| Recuperator | 5 | 3 or 2 | 376 / 251 dp | Name, on/off, fan speeds, and — when it reports them — temperature and humidity. Up to four ages |
| Bulb | many | 2 (third) | 251 dp | Not a grid cell once commit 3 lands. See "The lights group" below |
| Launcher | 2 | 2 (third) | 251 dp | Name and one line. No age — there is no reading to age |

**Four columns was the first draft of this and the tablet threw it out.** Four came from a 10"
tablet nobody had measured; the panel is 753 dp, so a full-width hero was 753 dp holding a name, a
temperature and a slider, with the switch stranded 700 dp from the value it switches. The launcher
at one column was 188 dp and wrapped its one line onto two.

**Halves and thirds, because both divide six.** This is the rule that matters, and it was learned
rather than designed: a row fills instead of trailing dead cells, and — the part that is not
obvious — two tiles of the same kind beside each other come out the same height. At a third of the
panel the two light strips wrapped differently, one onto two lines and the other onto three, and
stood side by side at visibly different sizes. Widths that do not divide the grid produce that, and
it reads as breakage rather than as variety.

The recuperator is the densest tile the flat has and the only one whose span is decided by its
content: **half when `climateLine` returns a line, a third when it returns null.** A device
reporting neither temperature nor humidity has a second line that does not exist, and a half-width
tile holding one line of "on · 2 min ago" is a hole in the wall. _Unexercised on this wall:_ all
five recuperators report both values, so the third-width branch is covered by `TileLayoutTest` and
has never been seen.

It is also the only tile with **an error of its own**. Every other group shares one — a failed
`/v1.0/user/info` failed for all of them — but recuperator state costs one Tuya call per device, so
`RecuperatorTileState.error` is per-tile and four working units must not be labelled "not updating"
because the fifth timed out. The mosaic keeps that distinction: the tile's own error colours the
tile, the group's error colours all five.

The AC keeps both of its ages: on `ac-01` the power and temperature capabilities were read 81 days
apart, and collapsing them to one number would print a lie on the bigger of the two.

Sizes to hold to, since this is read and touched at arm's length from a wall:

- Minimum hit area **64 dp** on anything tappable, not the platform's 48 dp. _Measured:_ every
  switch in the panel dumps as exactly 64.0 × 64.0 dp. **The tab strip does not hold to this** — its
  tabs are 48 dp tall, Material's default, because `PrimaryScrollableTabRow` sizes them and nothing
  overrides it. Known, not fixed, and it is the one thing on the wall a finger can miss.
- Bulb circles **72 dp**.
- Grid gutter 8 dp, tile corner radius 22 dp on a half tile, 18 dp on a third, full round on bulbs.
  The corner is derived from the span rather than passed beside it, so a tile's shape and its width
  cannot disagree. On the wall the two radii are a real but subtle difference; nobody standing back
  from it is going to name which is which.
- **The panel is 753 dp wide.** 1600 px at 340 dpi, portrait, which is the orientation it hangs in.
  This is what six columns is sized from. Landscape would be 1204 dp and the panel is not laid out
  for it: **auto-rotate is off on the tablet** (`accelerometer_rotation` 0) rather than
  `screenOrientation` being set in the manifest, so a settings reset puts landscape back and the
  mosaic will be wrong until it is turned off again.

### The lights group

28 bulbs at 72 dp each is the whole point of the mosaic — they are the many, they are on/off only,
and one full-width card each is what makes the panel a mile of scrolling. So a room's bulbs render
as one wrapping row of circles with a single line under it: how many there are, how many are on, and
one age.

That single age is a problem, and it has to be solved rather than waved at: **a tile that cannot say
when it was last read is a bug**, and a group line quoting the freshest reading would hide a bulb
that stopped answering a week ago.

The rule, which is a pure function and gets a test:

- A bulb the panel **has no state for** — `isOn` null, which is `Reading.Never` on the capability —
  leaves the group and renders as its own named third-width tile.
- Every other bulb stays in the group, and the group line quotes the **oldest** `last_updated` among
  those that stayed, plus how many there are and how many are on.

Staleness is deliberately not the split, and that was the first draft of this. Poll freshness is a
group fact — one call feeds every bulb, so either all of them are stale or none are (see "Stale"),
and a rule that fires on all 28 at once is not a split. What genuinely varies bulb by bulb is
whether Yandex has any state for it at all, and that is the thing worth pulling out of a row of
circles: a circle is a claim that the panel knows whether that lamp is on, and for a `Never` bulb it
does not. It says "unknown" on a named tile instead, which is what the status line has always said.

A stale *group* is still visible — the tab is marked and the group's error reaches every tile in it,
including the circles. It is just not what decides who is a circle.

## Stale

Three things in this doc ask the same question — which bulbs leave the lights group, which rooms get
a mark on their tab, which tiles Главная pulls in — so it is answered once, in one function, and
that function is where the number lives.

**Stale means the panel has stopped reading, not that the flat has stopped changing.** Commit 1
shipped it the other way round and it was wrong. The rest of this section is why, because the
mistake is easy to make twice.

`BulbTileState.lastUpdated` comes from Yandex's `last_updated` on the capability — **when the device
last reported a value, not when we last read it.** A bulb switched on three weeks ago and untouched
since carries a three-week-old timestamp while every poll since has read it successfully. 33 of the
116 recorded capabilities are `0.0`, which is `Never`, and `ac-01`'s two capabilities are 81 days
apart. So judging health on that timestamp calls a steady device broken: it asks *has this changed
lately*, and the panel needs *have we been able to read this lately*. That is why Коридор's tab is
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

The AC has two readings and the light strip has two; both still print both ages, because on `ac-01`
they are 81 days apart and one number for the pair would have to lie about the older.

### The recuperators before the first poll

Every other group heals in seconds. Yandex is one call every 15 s, so a poll that missed the Wi-Fi
coming up is retried before anybody reaches the hallway. Tuya is five calls every **6 minutes**, and
the recuperator tiles exist only once the inventory call has answered — so a tablet that rebooted
into a network that was not up yet shows **one line of error where five tiles belong, for six
minutes**. Seen on the wall on 2026-08-16: `Бризеры: not updating: Unable to resolve host
"openapi.tuyaeu.com"`, with the Yandex tiles already back.

So the panel remembers who they are. `KnownRecuperators` keeps the last successful inventory — **id,
name, room, and nothing else** — in the same encrypted store as the credentials, because device ids
identify the flat. On a cold start those become tiles with no values on them: "unknown · never read",
no climate line, third-width, and the group stale until a refresh lands, which is what marks the tab
and pulls them onto Главная.

What is deliberately *not* remembered is any value. A switch position from before the reboot is not
something the panel has read, and a tile printing it would be claiming a poll that never happened —
the same rule as "Stale", one layer down: the panel may remember what exists, never what it said.

A remembered tile is still tappable. The command needs an id and the re-read needs the device, and
both survive the restart; a tile on the wall that swallowed the tap would be worse than no tile.

A tablet with no usable keystore — restored backup, wiped key — remembers nothing and runs anyway.

**Seen on the wall, 2026-08-16.** The six-minute hole was real and reproduced twice: a cold start at
21:20 stood on `Бризеры: not updating: Unable to resolve host "openapi.tuyaeu.com"` with every
Yandex tile already up, and cleared by itself at the next poll — the host resolved fine from the
shell throughout, so it is the poll's cadence and not the network. After one successful inventory,
a restart shows all five recuperators inside a second: named, in their rooms, third-width,
"unknown · never read · unknown · never read", every room tab marked, and the whole set replaced by
real values 0.4 s later when the poll landed. `Бризер зал` then goes back to half-width with its
climate line, and the marks clear.

Two things that fall out of it, neither fixed: a placeholder has no climate line, so it is
third-width and its status line *wraps* onto two lines there — and "Бризер данина комната" wraps its
name too, so that one tile stands taller than the four beside it for the second it is up. And on a
tablet whose first read of the day fails, the tabs of five rooms are marked at once, which is the
tab mark doing its job and looks alarming anyway.

## The tab shell

**Главная** first, then the rooms. Rules, each of which exists because a wall panel is not a phone:

1. **It returns to Главная by itself.** After **2 minutes** with no touch, whatever tab is showing
   goes back to Главная. A phone app may stay where you left it; a wall panel is walked up to by
   someone who did not leave it there, and a panel showing Балкон because that is where the last
   person got to is a panel showing the wrong room to everyone after them.
2. **A room tab carries its own bad news.** A room is marked on the tab strip when its group's poll
   failed, or when every reading in it is stale. Without this the tabs hide eleven rooms, and Спальня
   can be dead for a day behind a Главная that looks fine. The mark is on the tab, so it is visible
   from Главная without opening the room.
3. **Group failures stay above everything.** `groupFailures` today prints the groups that failed
   before they ever had a tile. Those have no room to be marked in — a group with no tiles is in no
   room — so that line stays at the top of Главная, unchanged.
4. **The strip scrolls horizontally.** Twelve rooms plus Главная plus Без комнаты does not fit. The
   rooms that scroll off are the ones the existing order already puts last — Ванная, Балкон,
   Гардероб — which are switched at their own door and are the long way round from the hallway
   anyway.
5. **Без комнаты is a tab like any other**, last, and never dropped: it holds the recuperators when
   `TUYA_ROOMS` is unset and the vacuum's launcher tile, and a device falling off the wall because no
   vendor placed it is the bug that section exists to prevent.

### What is on Главная

No settings screen. Favourites are defined in code, in one place, in the same spirit as `ROOM_ORDER`
— which is a list with a comment explaining the hallway it hangs in, not a preference.

The rule: **every tile in Коридор and Зал, plus every launcher tile, plus any tile anywhere that is
failing or stale.** The first two are the rooms switched on the way in and on the way out; the
launchers because the intercom is why someone walks up to this panel at all; and the last so that
rule 2's mark has somewhere to lead — a failing tile appears on Главная itself, not only as a dot on
a tab.

This is a pure function of the room sections. It gets a test.

## Theme

- `MainActivity` passes `lightColorScheme()` / `darkColorScheme()` chosen by `isSystemInDarkTheme()`.
  No dynamic colour: the wallpaper of a kiosk tablet is not a design input.
- Every tile colour is a Material role — `primaryContainer` / `onPrimaryContainer` for a tile that is
  on, `surfaceContainer` for everything else. **No hex literals in the panel package.** A hardcoded
  colour is a tile that is unreadable in one of the two themes, and the theme that breaks is the one
  nobody is looking at when they check. Done in commit 2 and grep-clean.
- `Off`, `Unknown` and `Failing` all share `surfaceContainer`. There is no second and third neutral
  to give them, and the difference between the three is said in words on the status line, where it
  was always said — "off", "unknown", "not updating: <reason>". What must not happen is any of them
  borrowing the *on* colour and claiming a reading nobody has taken.
- **`Failing` was `errorContainer` until the wall had several at once.** Commit 2 painted a failing
  tile red on the reasoning that it is showing a value nobody has confirmed — true, and still the
  reason `mood` ranks `Failing` above `isOn`. But one unreachable vendor makes a panel that reads as
  an emergency, and the paint is loudest exactly when it is least useful: at boot, before anything
  has been read, every tile is failing at once. The reason is on the tile in words either way.
- The one failure still *painted* is the group's, and it outlines rather than fills — the red border
  on the recuperators when the inventory call failed. It is now the only red on the panel, which is
  the point: five outlined tiles is one vendor, not five broken units.
- **Confirmed: the tablet's dark theme is on a real schedule, 19:00–07:00** (`mNightMode=0 (auto)`,
  `customStart=19:00 customEnd=07:00`). So `darkColorScheme()` is not dead code and commit 4 is worth
  doing. Forcing night mode on today shows the panel staying light, which is the expected state
  until commit 4 lands: `MainActivity` still wraps a bare `MaterialTheme {}`.
- **Samsung's blue light filter is on** (`settings get system blue_light_filter` → 1) and it tints
  screencaps too, system UI included. Anything warm-looking in a screenshot of this panel is that
  filter and not the palette. Judge colour with it in mind, or turn it off first.

## Compose APIs

The Compose BOM is already `2026.08.00`, so Material 3 Expressive is on the classpath and **no new
dependency is needed** — which is the reason this direction was picked over anything needing a card
library.

- `PrimaryScrollableTabRow` for the tab strip. Done, commit 1.
- `LazyVerticalGrid` with `GridCells.Fixed(6)` and `GridItemSpan` for the mosaic. Done, commit 2.
  `FlowRow` for the lights group is commit 3.
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

## What changes, and what does not

Changes:

- `PanelRooms.kt` — becomes the tab shell. Loses the `LazyColumn`. ✅ commits 1 and 2.
- Every tile composable — new shape, new span, colour roles instead of a bare `Card`. ✅ commit 2.
- `MainActivity.kt` — the idle timer that returns to Главная ✅ commit 1; the colour schemes are
  commit 4 and not done.
- New: the favourites function ✅, the tab-mark function ✅, the stale-bulb split (commit 3, not
  done). All three are pure and live next to `roomSections`.
- New in commit 2, not foreseen when this was written: `TileLayout.kt` for the two decisions a
  composable cannot be tested through, and `TileCard.kt` for the one card all five tiles draw.

Does not change, and a diff touching these is out of scope:

- `roomSections` and `ROOM_ORDER` — room membership and room order are already decided and correct.
- Every `*Tiles.kt` — the tile state models and the `statusLine` functions. This is a re-skin; if a
  poll or a mapping changed, the change went too far.
- Anything under `integrations/`.
- The Domonap call takeover. It goes on top of the panel, and the panel having tabs underneath it
  changes nothing about that. Whatever tab was showing is what the panel comes back to — subject to
  the 2-minute reset, which keeps running through the call the same way the ages do.

## Testing

TDD, and the test comes first in the same commit.

The unit test dependencies are JUnit5, kotlin.test, Turbine and MockK — **there is no Compose test
dependency**, and adding one is an "ask first". So every rule in this doc that can be got wrong is
written as a pure function over the tile states and tested directly, the way `RoomSectionsTest`
tests `roomSections`. That is not a workaround; it is the same reason the room order is a function
and not a layout.

What gets a test, each asserting the value returned and not which composable was called:

- The favourites list: Коридор and Зал tiles present; launchers present; a stale tile from Спальня
  pulled in; a fresh tile from Спальня not.
- The tab marks: a room marked when its group errored, marked when everything in it is stale,
  unmarked otherwise.
- The bulb split: `Reading.Never` leaves the group; a bulb 3 minutes old leaves the group; a bulb
  90 seconds old stays; the group line quotes the oldest of those that stayed.
- The tab list: Главная first, rooms in `roomSections` order, Без комнаты last and present even when
  every other section is empty.
- The tile layout: the recuperator's span from whether it reports climate, and `mood` from `isOn`
  and the error. Added in commit 2 — see section 2 for why a re-skin turned out to have things to
  assert after all.

What does not get a unit test, and is checked on the tablet instead: the grid spans, the shapes, the
dark palette, the touch targets, and the idle reset actually firing.

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

## Open

- Status strings are English today (`on`, `just now`, `not updating`) while room names arrive in
  Russian from the vendors. The mosaic does not change that, and it should not be changed quietly as
  part of this work; if the panel is to speak one language it is its own commit.
- Whether the 2-minute idle reset is right, or whether it should be the screen's own dim timeout.
  Two minutes is a guess and is a single constant. The tablet's own screen timeout is 2 minutes as
  well, so today the two fire together; that is a coincidence of settings, not a design.
- The ×8 in "Stale". Tying it to each poll's own interval is right; eight of them is a guess, and the
  number that matters is how long a device can be quiet before somebody would want to know.
- Whether a **stale group** should reach the tile's paint, or only the tab mark. `mood` is a function
  of `isOn` and the error and nothing else, so a group that has stopped polling still paints every
  tile in it as confidently on. Commit 3 makes the signal trustworthy enough to be worth asking; it
  does not answer it, and wiring it in is a spec change rather than a bug fix.
- What a tile should look like when `isOn` is null. Today `Unknown` and `Off` share
  `surfaceContainer`, so a lamp the panel knows nothing about is indistinguishable from one it knows
  is off — the strings tell them apart and the colours do not. Commit 4 pulls the null-state bulbs
  out of the circles, which is the same problem answered for one tile type only.
- The tablet is locked with a PIN and locks itself on screen-off. Nothing in the panel handles that
  — the wall goes to a lock screen rather than to the panel, and the Domonap takeover's behaviour
  over a locked screen is unverified. See `docs/domonap.md`.

Answered since this was written, kept here so they are not re-asked: the panel width (753 dp, see
"Tile sizes"), whether the dark theme is on a schedule (it is, 19:00–07:00, see "Theme"), and
whether shape morphing is affordable (moot — `MaterialShapes` is not on this BOM, see "Compose
APIs").

## The plan

Four commits, each a concern, each green on `./gradlew test` and `ktlintCheck` before the next one
starts. The shell first because it is the part that can be wrong about *behaviour*; the skin after
it, because a skin that is wrong is visible from the hallway.

One branch per commit rather than the one branch this originally named: 1 shipped as
`feat/panel-mosaic-tabs` (#11) and 2 as `feat/panel-expressive-tiles` (#12).

No `AndroidManifest.xml` change, no new dependency, no new permission anywhere in this work. Held so
far, including for the portrait lock — that is a device setting, not `screenOrientation`. If one
turns out to be needed, that is an "ask first" and the branch stops until it is asked.

### 1 · `feat(panel): tab shell` — done, #11

Navigation only. The tiles stay exactly as they are — full-width cards — so anything that breaks in
this commit is the shell and not the drawing.

New, all pure, all in `panel/` next to `roomSections`:

| File | Holds |
| --- | --- |
| `Staleness.kt` | `isStale(reading: Reading, now: Instant, interval: Duration): Boolean`, and the ×8 multiplier. See "Stale" |
| `Favourites.kt` | `favourites(sections: List<RoomSection>, now: Instant): RoomSection` |
| `PanelTabs.kt` | `panelTabs(sections, errors, now): List<PanelTab>`; `PanelTab` carries a title, a `RoomSection` and `marked` |
| `IdleReset.kt` | `suspend fun resetAfterIdle(touches: Flow<Unit>, timeout: Duration, onIdle: () -> Unit)` |

`resetAfterIdle` is a suspend function over a flow rather than something inside a composable for the
same reason `pollPausingForCalls` is: it is the one piece of the shell with a clock in it, and a
clock that cannot be advanced in a test is a clock nobody checks.

Changed: `PanelRooms.kt` gains a `PrimaryScrollableTabRow` and renders one tab's section where it
used to render all of them. `MainActivity.kt` feeds touches into `resetAfterIdle`.

Tests, written first — `StalenessTest`, `PanelTabsTest`, `FavouritesTest`, `IdleResetTest`:

- A Yandex reading 90 s old is fresh and one 3 min old is stale; a Tuya reading 7 min old is **fresh**
  and one an hour old is stale; `Never` is stale at every interval.
- Главная first; rooms in `roomSections` order; Без комнаты last and present even when every other
  section is empty.
- A room marked when its group errored, marked when every reading in it is stale, unmarked otherwise.
- Favourites hold every Коридор and Зал tile and every launcher; a stale Спальня tile is pulled in;
  a fresh one is not.
- The reset fires after the timeout, does not fire while touched, and restarts its clock on each
  touch.

### 2 · `feat(panel): expressive tiles` — done, #12

Changed: `PanelRooms.kt` swaps the `LazyColumn` for `LazyVerticalGrid(GridCells.Fixed(6))` with the
spans from "Tile sizes"; all five tile composables get shapes, spans and colour roles in place of a
bare `Card`; `app/build.gradle.kts` gains the Expressive opt-in in its existing `compilerOptions`
block. `TileCard.kt` holds the one card every tile draws — five callers, which is what earns it —
and maps `TileMood` to the colour roles.

"A re-skin has nothing to test" was the first draft of this section and it is wrong. Two of the
rules above are decisions with a right and a wrong answer, and a decision that only exists inside a
`@Composable` is a decision no test can reach. So they come out of the composables the same way
`statusLine` and `ageLabel` already have — pure, `internal`, tested first — and the composable is
left doing nothing but drawing what they returned.

New, in `TileLayout.kt`:

- `fun span(tile: RecuperatorTileState): Int` — half the panel when it reports climate, a third when
  it does not. The one span in the panel that is not a constant per type. It asks `climateLine`
  rather than re-testing `temperature != null || humidity != null`, so the span and the line it
  exists to hold cannot drift apart.
- `fun mood(isOn: Boolean?, error: String?): TileMood` — `On`, `Off`, `Unknown`, `Failing`. The
  composable maps a `TileMood` to a Material colour role and does no thinking of its own. Five
  callers, so it is not an abstraction invented for one.

`Failing` outranks everything: a tile whose poll failed is showing a value nobody has confirmed, and
painting it as merely "on" is the panel asserting something it does not know. `Unknown` is not `Off`
— 33 of the 116 recorded capabilities have never reported, and the tiles have always said "unknown"
rather than "off" for them; the colours must not undo in paint what the strings were careful about.

Tests, written first — `TileLayoutTest`, 6 cases:

- A recuperator with a temperature is a half tile; with a humidity and no temperature, half; with
  neither, a third.
- `mood` is `Failing` whenever there is an error, whatever `isOn` says — including when `isOn` is
  null and including when it is true.
- `mood` is `Unknown` for a null `isOn` with no error, and never `Off`.

Beyond those, the commit changes no behaviour, so **every existing test must pass untouched.** If one
needs editing to go green, the production change did more than it claimed and the edit is the bug.
It held: 203 tests, none edited.

What stayed untestable was checked on the tablet, and the checks are the reason this section's
numbers changed:

| Check | Result |
| --- | --- |
| Columns against the real width | **Failed at four**, and the span table was rewritten. 753 dp, six columns, halves and thirds |
| 64 dp hit areas | Every switch dumps as exactly 64.0 × 64.0 dp. The tab strip's tabs are 48 dp — see "Open" |
| The shapes | 22 dp and 18 dp both applied; the difference is real but subtle from across a hallway |
| Both palettes | Light only. The panel stays light under forced night mode, as expected until commit 4 |
| Shape morph on press | Moot — `MaterialShapes` is not on this BOM |

The four-column pass is worth recording rather than quietly fixing, because the mistake was not the
number: it was writing a span table against a width nobody had measured and calling the result
decided.

### 3 · `fix(panel): stale means the poll stopped` — done, #14

Found while planning the bulb grouping, and it came first because commit 4's group line and commit
1's tab marks both rest on it. The full reasoning is in "Stale"; the short version is that commit 1
judged health on the vendor's `last_updated` and so calls a lamp nobody has touched in three weeks
broken.

Changed, and this is the one place the plan's fence around the poll classes comes down — knowingly,
because the fact being added is the poll's own and nowhere else can honestly hold it:

- Each `*PanelState` gains `lastPolledAt: Instant?`; `YandexPoll` and `TuyaPoll` stamp it on a
  refresh that succeeded. Null until the first one lands.
- `Staleness.kt` — `isStale` takes the group's `lastPolledAt` and its interval, not a `Reading`. The
  `readings(...)` helpers that fed the old rule go with it; nothing else reads them.
- `PanelTabs.kt`, `Favourites.kt` — a room is marked, and a tile is pulled onto Главная, when its
  group errored **or** its group is stale. Same rules, corrected input.

Not changed: what the tiles print. `ageLabel(tile.lastUpdated, now)` stays exactly as it is on every
tile. "20 d ago" is an honest answer to how old a value is and always was; it is only its use as a
health signal that was wrong.

Tests, written first — `StalenessTest` rewritten, `PanelTabsTest` and `FavouritesTest` amended:

- A group polled 90 s ago is fresh and one polled 3 min ago is stale, at the Yandex interval; a group
  polled 7 min ago is **fresh** at the Tuya interval and one polled an hour ago is stale.
- A `lastPolledAt` of null is stale at every interval — the panel has read nothing yet.
- A room of bulbs whose every `last_updated` is `Never` is **not** marked while its group is polling
  fine. This is the regression the commit exists for; it fails before the fix.
- A recuperator with its own error still marks its room while the other four do not.

### 4 · `feat(panel): group the bulbs`

New: `BulbGroup.kt` — `bulbGroup(bulbs: List<BulbTileState>): BulbGroup`, returning the circles, the
ones that broke out, how many are on, and the oldest `last_updated` among those that stayed.

**No `now`.** Finding the oldest of a set of readings needs no clock; formatting one does, and that
is `ageLabel`'s job at the point of drawing, as on every other tile. A `now` parameter this function
does not use is a clock in a pure function nobody can see is unused.

`Reading.Never` is the oldest of all, and it can appear inside the group: `isOn` and `lastUpdated`
come from different fields of the same capability, so a bulb whose value is known while its
`last_updated` is `0.0` stays a circle and carries a `Never` the group line has to be able to quote.
The split is on `isOn`, not on the reading — those are two different questions and the fixture
answers them separately.

Reuse rather than reinvent, both already there:

- `notUpdating(error, lastPolledAt, now, interval)` in `Staleness.kt` — whether the group behind the
  circles has stopped being read. The group line says it once for the row instead of 28 times.
- `mood(isOn, error)` in `TileLayout.kt` — what colours a circle. A circle is a tile and takes the
  same four moods as one; nothing new is needed to paint it.

Changed: `PanelRooms.kt` renders the group as a `FlowRow` of 72 dp circles under one line, and each
broken-out bulb as a named third-width tile. Until this lands the bulbs are third-width tiles, one
per cell, three to a row — which is what commit 2 left them as and is why Главная is still fourteen
rows of lamps.

Tests, written first — `BulbGroupTest`: a bulb with `isOn` null leaves the group; one that is on and
one that is off both stay, however old their readings are; the group line quotes the **oldest**
`last_updated` of those that stayed and not the freshest; the count of "how many on" excludes the
ones that broke out; a room with no bulbs yields no group rather than an empty row.

Expect this to be almost all of them in a circle. On today's data the split is the handful Yandex has
never reported against the twenty-odd it has, which is the reduction the commit is for — and the
opposite of what the stale rule would have done.

### 5 · `feat(panel): follow the system theme`

Changed: `MainActivity.kt` picks `lightColorScheme()` / `darkColorScheme()` off
`isSystemInDarkTheme()`. No dynamic colour, and no theme file — one caller does not earn a wrapper.

No unit test is possible without a Compose test dependency, which is an "ask first" and is not being
asked for here. Checked on the tablet in both themes, and by grepping `panel/` for hex literals,
of which there should be none.

### 6 · `fix(panel): the tab strip's touch height`

`PrimaryScrollableTabRow` sizes its tabs at Material's default 48 dp while this doc asks for 64 dp on
everything a finger goes near, and the tab strip is the one control on the wall a finger can miss.
One override, no new logic, no test to write — it is a dp. Independent of 3, 4 and 5 and can go
whenever; last only because it is the smallest thing here.

### Order, and what it buys

1 before 2 because the shell is the only part with behaviour to get wrong, and it is worth having it
green before anything visual moves. 2 before the rest because the lights group is a mosaic idea and
has nowhere to live until the grid exists.

3 before 4 because the group line and the tab marks both read staleness, and grouping the bulbs on
top of a signal known to be wrong means doing it twice. 3 is also the only commit here that is a fix
rather than a feature, and it stays its own commit for that reason — a correction folded into a
feature is a correction nobody can find again.

5 last because it touches every colour the commits before it introduced, and doing it earlier means
doing it twice.

The order held. What it did not buy, and nothing in it could have: 2 was still wrong about the grid
until the grid was on the wall, because the number it was wrong about was a measurement and not a
decision.
