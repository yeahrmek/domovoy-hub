# Panel UI

**Scope:** how the panel is laid out and drawn. Not what it reads — that is one doc per vendor.

**Status: decided 2026-08-15, nothing built.** This is the brief for the work, not a record of it.
Everything below marked _measure on the tablet_ is a number nobody has taken yet; do not treat it
as settled just because it is written down.

## What is decided

1. **A tab shell.** One "Главная" tab holding the favourites, then one tab per room in the existing
   room order. Replaces the single scroll of 29 tiles.
2. **Material 3 Expressive mosaic tiles.** Mixed tile sizes and shapes instead of one full-width
   card per device: a hero tile for the air conditioners, medium tiles for the things with a slider,
   small circles for the bulbs.
3. **Both themes, following the system.** The panel is light by day and dark by night, driven by
   `isSystemInDarkTheme()`.

Rejected, and why, so it is not re-proposed: **fill-level tiles** (the tile's fill height or width
is the value, Apple-Home style). The recorded `/v1.0/user/info` holds 28 `devices.types.light`
against 3 ACs, 2 light strips and 1 curtain, and all 5 recuperators are on/off — so of the flat's
tiles, six have a continuous value and the rest have nothing to fill with. On those a fill bar is a
coloured rectangle that means "on", which the tile's colour already says.

## What the panel does today

The baseline this replaces, so the diff is legible:

- [`PanelRooms`](../app/src/main/kotlin/ru/domovoy/panel/PanelRooms.kt) is one `LazyColumn`. Group
  failures at the top, then a `Text` heading per room, then every tile in that room as a full-width
  `Card`, in the fixed order ac → curtain → strip → recuperator → bulb → launcher.
- [`roomSections`](../app/src/main/kotlin/ru/domovoy/panel/RoomSections.kt) decides which room a tile
  lands in and in what order the rooms come. **It does not change.** The tab shell consumes exactly
  what it returns — one tab per `RoomSection`, in the order that function already produces, with the
  roomless section last under "Без комнаты".
- Every tile prints a status line ending in `ageLabel(...)`, and appends `not updating: <error>` when
  its group's poll failed.
- `MainActivity` wraps everything in a bare `MaterialTheme {}` — no colour scheme is passed, so the
  panel is on the Material baseline light palette in both system themes today.

## Tile sizes

A four-column grid. The span is a property of the tile type, not of the room:

| Tile | Count | Span | What it shows |
| --- | --- | --- | --- |
| Air conditioner | 3 | 4 (hero) | Name, target temperature at display size, on/off, temperature slider, both ages |
| Curtain | 1 | 2 | Name, open percent, slider, age |
| Light strip | 2 | 2 | Name, on/off, brightness slider, both ages |
| Recuperator | 5 | 2 | Name, on/off, age |
| Bulb | many | — | Not a grid cell. See "The lights group" below |
| Launcher | 2 | 1 | Name and one line. No age — there is no reading to age |

The AC keeps both of its ages: on `ac-01` the power and temperature capabilities were read 81 days
apart, and collapsing them to one number would print a lie on the bigger of the two.

Sizes to hold to, since this is read and touched at arm's length from a wall:

- Minimum hit area **64 dp** on anything tappable, not the platform's 48 dp.
- Bulb circles **72 dp**.
- Grid gutter 8 dp, tile corner radius 22 dp on the hero, 18 dp on medium, full round on bulbs.
- _Measure on the tablet:_ the panel's width in dp, which is what decides whether four columns is
  right. Four is a guess from a 10" portrait tablet and nothing more.

### The lights group

28 bulbs at 72 dp each is the whole point of the mosaic — they are the many, they are on/off only,
and one full-width card each is what makes the panel a mile of scrolling. So a room's bulbs render
as one wrapping row of circles with a single line under it: how many there are, how many are on, and
one age.

That single age is a problem, and it has to be solved rather than waved at: **a tile that cannot say
when it was last read is a bug**, and a group line quoting the freshest reading would hide a bulb
that stopped answering a week ago.

The rule, which is a pure function and gets a test:

- A bulb whose reading is stale — see "Stale" below — **leaves the group** and renders as its own
  named medium tile with its own age.
- The remaining bulbs stay in the group, and the group line quotes the **oldest** age among them.

So the group is only ever a group of bulbs that agree about being fresh, and a stale one is a tile
with a name on it.

## Stale

Three things in this doc ask the same question — which bulbs leave the lights group, which rooms get
a mark on their tab, which tiles Главная pulls in — so it is answered once, in one function, and
that function is where the number lives.

**Stale is relative to the group's own poll interval, not a flat duration.** A flat 2 minutes was the
first draft of this and it is wrong: Yandex is polled every 15 s and Tuya every 6 minutes
(`POLL_INTERVAL` and `TUYA_POLL_INTERVAL` in `MainActivity`), so any constant short enough to catch a
dead bulb marks **every recuperator permanently stale**, and the panel would hang a warning on five
tiles that are working exactly as designed.

So: a reading is stale when it is older than **eight times the interval of the poll that produced
it** — 2 minutes for the Yandex tiles, 48 minutes for the recuperators — and `Reading.Never` is
always stale. Eight is a guess. The interval is not: it comes from the constant the poll loop
actually runs on, so if either cadence is retuned the staleness follows it rather than silently
falling out of step.

The AC has two readings and the light strip has two; a tile is stale when **either** is, because
either one going quiet is the panel showing a value nobody has confirmed.

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
  on, `surfaceContainer` for off, `errorContainer` / `onErrorContainer` for not-updating. **No hex
  literals in the panel package.** A hardcoded colour is a tile that is unreadable in one of the two
  themes, and the theme that breaks is the one nobody is looking at when they check.
- _Confirm on the tablet:_ "follow the system" only ever changes if Android's dark theme is on a
  sunrise/sunset schedule. If the tablet has it set to always-on or always-off, this choice is a
  fixed theme picked once, and the other one is dead code nobody sees.

## Compose APIs

The Compose BOM is already `2026.08.00`, so Material 3 Expressive is on the classpath and **no new
dependency is needed** — which is the reason this direction was picked over anything needing a card
library.

- `PrimaryScrollableTabRow` for the tab strip.
- `LazyVerticalGrid` with `GridCells.Fixed(4)` and `GridItemSpan` for the mosaic; `FlowRow` for the
  lights group.
- `MaterialShapes` + shape morph on press for the bulb circles. This is polish and it is optional:
  it must not sit between the finger and `onToggle`, and if it costs a frame on this tablet it comes
  out.
- Motion is the expressive spring specs, not a duration and an easing curve.

Expressive components are opt-in annotated and `app/build.gradle.kts` sets no `optIn` today, so the
`compilerOptions` block gains one. **Confirm the exact annotation name against the BOM when it first
fails to compile rather than trusting this line** — it is written from the release notes, not from a
build.

## What changes, and what does not

Changes:

- `PanelRooms.kt` — becomes the tab shell. Loses the `LazyColumn`.
- Every tile composable — new shape, new span, colour roles instead of a bare `Card`.
- `MainActivity.kt` — the colour schemes, and the idle timer that returns to Главная.
- New: the favourites function, the tab-mark function, the stale-bulb split. All three are pure and
  live next to `roomSections`.

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

What does not get a unit test, and is checked on the tablet instead: the grid spans, the shapes, the
dark palette, the touch targets, and the idle reset actually firing.

`./gradlew test` and `ktlintCheck` green before push, as everywhere else.

## Open

- The panel width in dp, and therefore whether four columns is right. Nobody has measured it.
- Whether the tablet's dark theme is on a schedule at all — see "Theme".
- Status strings are English today (`on`, `just now`, `not updating`) while room names arrive in
  Russian from the vendors. The mosaic does not change that, and it should not be changed quietly as
  part of this work; if the panel is to speak one language it is its own commit.
- Whether the 2-minute idle reset is right, or whether it should be the screen's own dim timeout.
  Two minutes is a guess and is a single constant.
- The ×8 in "Stale". Tying it to each poll's own interval is right; eight of them is a guess, and the
  number that matters is how long a device can be quiet before somebody would want to know.
- Whether shape morphing on press is affordable on this tablet.

## The plan

One branch, `feat/panel-mosaic-tabs`. Four commits, each a concern, each green on `./gradlew test`
and `ktlintCheck` before the next one starts. The shell first because it is the part that can be
wrong about *behaviour*; the skin after it, because a skin that is wrong is visible from the hallway.

No `AndroidManifest.xml` change, no new dependency, no new permission anywhere in this work. If one
turns out to be needed, that is an "ask first" and the branch stops until it is asked.

### 1 · `feat(panel): tab shell`

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

### 2 · `feat(panel): expressive tiles`

Changed: `PanelRooms.kt` swaps the `LazyColumn` for `LazyVerticalGrid(GridCells.Fixed(4))` with the
spans from "Tile sizes"; all five tile composables get shapes, spans and colour roles in place of a
bare `Card`; `app/build.gradle.kts` gains the Expressive opt-in in its existing `compilerOptions`
block.

**This commit adds no test, and that is a statement rather than an omission.** It changes no
behaviour — every `statusLine` still returns the same string, every callback still fires on the same
gesture — so there is nothing to assert that is not a screenshot. The check that it stayed a re-skin
is that the existing tile tests pass **untouched**: if one of them needs editing, the commit did more
than it claimed and the edit is the bug, not the test.

Verified on the tablet instead: the four columns against the real width, the 64 dp hit areas, both
palettes, and whether shape morphing on press costs a frame. If it does, it comes out here rather
than being carried.

### 3 · `feat(panel): group the bulbs`

New: `BulbGroup.kt` — `bulbGroup(bulbs: List<BulbTileState>, now: Instant): BulbGroup`, returning the
circles, the ones that broke out, and the oldest reading among those that stayed.

Changed: `PanelRooms.kt` renders the group as a `FlowRow` of 72 dp circles under one line, and each
broken-out bulb as a named medium tile.

Tests, written first — `BulbGroupTest`: `Never` leaves the group; a bulb 3 min old leaves; one 90 s
old stays; the group line quotes the **oldest** of those that stayed and not the freshest; a room
with no bulbs yields no group rather than an empty row.

### 4 · `feat(panel): follow the system theme`

Changed: `MainActivity.kt` picks `lightColorScheme()` / `darkColorScheme()` off
`isSystemInDarkTheme()`. No dynamic colour, and no theme file — one caller does not earn a wrapper.

No unit test is possible without a Compose test dependency, which is an "ask first" and is not being
asked for here. Checked on the tablet in both themes, and by grepping `panel/` for hex literals,
of which there should be none.

### Order, and what it buys

1 before 2 because the shell is the only part with behaviour to get wrong, and it is worth having it
green before anything visual moves. 3 after 2 because the lights group is a mosaic idea — it has
nowhere to live until the grid exists. 4 last because it touches every colour the three commits
before it introduced, and doing it earlier means doing it twice.
