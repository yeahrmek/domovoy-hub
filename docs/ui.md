# Panel UI

**Scope:** how the panel is laid out and drawn. Not what it reads — that is one doc per vendor.

**Status: commits 1 to 5 built and merged (#11, #12, #14, #15, #17); 6 built and on its branch;
7 — the tab strip's touch height — is all that is left.** What was a brief is
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
  panel is on the Material baseline light palette in both system themes. **No longer true:** commit
  5 passes it one of the two schemes in `PanelTheme.kt`, chosen by `isSystemInDarkTheme()`.

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
  switch in the panel dumps as exactly 64.0 × 64.0 dp, and so does every tab — the strip used to be
  the one thing on the wall a finger could miss, at Material's default 48, until a `heightIn` on the
  `Tab` raised it. The `PrimaryScrollableTabRow` needed nothing: it takes its height from its
  tallest tab, so the strip came up 64.0 dp with them.
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

- **The panel has its own palette, and `lightColorScheme()` on its own is not it.** Called with no
  arguments those builders return Material's *baseline*, which is a violet — and a violet run through
  the neutral surfaces of an unstyled panel is the grey-mauve wall this brief did not ask for. The
  schemes are written out with explicit values. Seeds: a **cool blue** for climate and a **warm
  amber** for light, tonal palettes generated from those two rather than picked stop by stop, so
  light and dark stay related to each other. Everything else in the scheme is Material's neutral.
- `MainActivity` picks between the two by `isSystemInDarkTheme()`. No dynamic colour: the wallpaper
  of a kiosk tablet is not a design input, and on a wall that shows two rooms' worth of amber and
  blue, a palette that changes when somebody changes the launcher background is a panel that stops
  meaning what it meant yesterday.
- **A tile's colour has two axes, not one: what kind of thing it is, and what state it is in.** One
  colour for everything that is on makes a wall where the air conditioner and the bedroom lamp are
  the same object. So:
  - **Domain** picks the role — climate (air conditioners, recuperators) takes `primaryContainer`,
    light (bulbs, strips) takes `tertiaryContainer`, everything else (curtains, launchers) takes
    `secondaryContainer`. Three families and no more; a fourth hue on a wall read from four metres
    is decoration rather than information.
  - **State** picks whether the domain colour is used at all. `On` fills with the domain container;
    `Off` and `Unknown` are `surfaceContainer` whatever the domain, because an unlit lamp is not
    warm and a stopped recuperator is not cool.
- `hue(...)` is the domain half and lives in `TileLayout.kt` beside `mood` and `span` — a pure
  function per tile type, out where a test reaches it. The composable maps the `(hue, mood)` pair to
  a role pair and does no thinking of its own, exactly as it already does for `mood` alone.
- **No hex literals in the panel package.** A hardcoded colour is a tile that is unreadable in one of
  the two themes, and the theme that breaks is the one nobody is looking at when they check. The
  schemes are the one place values are written, and they are in the theme, not in `panel/`. Done in
  commit 2 and grep-clean; it stays that way.
- `Off` and `Unknown` share `surfaceContainer`. There is no second neutral to give them, and the
  difference is said in words on the status line, where it was always said — "off" against
  "unknown". What must not happen is either of them borrowing the *on* colour and claiming a reading
  nobody has taken.
- **`Failing` is a filled `errorContainer`, on every tile.** This reverses what commit 2 landed on,
  and the reversal is the decision rather than the drift, so both halves are kept here.

  Commit 2 painted it red on the reasoning that a failing tile is showing a value nobody has
  confirmed — true, and still why `mood` ranks `Failing` above `isOn`. It was pulled because one
  unreachable vendor made the panel read as an emergency, and because the paint is loudest exactly
  when it is least useful: at boot, before anything has been read, every tile fails at once.

  It comes back because the neutral treatment failed the other way. A failing tile that looks
  identical to a working one puts the whole weight on a status line nobody reads from four metres,
  and the point of the mosaic is that a wall is read by colour and shape before it is read by words.
  A pale rose is also not what commit 2 tried: the error container at this palette's tone is close to
  the neutral in weight, and a wall of it reads as *muted* rather than as alarm.

  **The boot case is known and accepted, not overlooked.** Until the first poll lands every tile on
  Главная will be rose. _If that reads as alarm on the wall rather than as "nothing has been read
  yet", the fix is to tell "never polled" apart from "stopped polling" — `lastPolledAt == null`
  against a stale timestamp, both of which `Staleness.kt` already has — and leave the first one
  neutral._ That is the third option that was on the table and was not taken; it is written down so
  it does not have to be rediscovered.
- The group's own failure keeps its outline as well as the fill — the border on the recuperators when
  the inventory call failed. Five outlined tiles is one vendor, not five broken units, and that
  distinction survives everything above.
- **The bulb circles are the one place none of this can land**, because commit 6 left them with no
  container to fill or outline. A failed group drops them to their unlit lamp instead, and the group's
  line under the row carries the reason in words — see "Icons". That is the cost of taking the disc
  away, and it was accepted with the eyes open rather than missed.
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
  under commit 5.

## Icons

Every tile carries one. The panel today has none at all — no `Icon(` anywhere in `ru.domovoy`, and
`app/src/main/res` holds only `themes.xml` and `strings.xml` — which is why a wall of it reads as
paragraphs. A tile is recognised across a hallway by its shape and its glyph long before its name is
legible, and on a panel that is looked at on the way past, that is most of the looking.

**As vector drawables in `app/src/main/res/drawable/`, not as a dependency.**
`androidx.compose.material:material-icons-extended` carries the glyphs this needs, and it is a large
artifact to add for nine of them — and adding a dependency is an "ask first" in CLAUDE.md. Nine
glyphs exported to vector XML cost nothing at build time and are `res/` files, which is neither a
dependency nor a manifest change. Held: commit 6 added no dependency, and `res/drawable/` is the
whole of what it added outside `panel/`.

| Tile | Glyph | File | Chosen over |
| --- | --- | --- | --- |
| Air conditioner | `ac_unit` | `ic_ac_unit.xml` | `air`, `thermostat`, `hvac` |
| Recuperator | `mode_fan` | `ic_mode_fan.xml` | `swap_vert`, `filter_alt`, `vent` |
| Light strip | `wb_iridescent` | `ic_wb_iridescent.xml` | `horizontal_rule`, `linear_scale`, `light` |
| Curtain | `vertical_shades` / `vertical_shades_closed` | `ic_vertical_shades.xml`, `ic_vertical_shades_closed.xml` | `curtains`, `roller_shades`, `blinds`, `shade` |
| Bulb | Tabler `bulb` and its filled variant — **not Material Symbols** | `ic_bulb.xml`, `ic_bulb_filled.xml` | `lightbulb`, `wb_incandescent`, `tips_and_updates`, `emoji_objects`, `flare`, `lightbulb_circle` |
| Домофон | `video_camera_front` | `ic_video_camera_front.xml` | `doorbell`, `ring_volume` |
| Пылесос | `vacuum` | `ic_vacuum.xml` | `robot_2`, `smart_toy`, `cleaning_services` |

**The bulb's two come from Tabler and the other seven from Material Symbols, and that mix is a
decision rather than an accident.** Six Material bulbs were rendered and none was the one wanted:
`lightbulb` is plain, and the bulb that had been approved all along has short rays around its top. It
had been approved all along because **every mockup in this project was drawn in Tabler** — so the look
being signed off was Tabler's, and Material's bulb was never the thing anyone had looked at.

Moving all seven to Tabler was the tidier answer and was turned down: it re-opens six settled glyphs
to fix one. The judgement is that the clash will not read, because the bulb is the only glyph that
never appears beside another — it sits alone in its own cell in the lights row, with no tile, no name
and no Material glyph anywhere near it. _If it does read on the wall, this is the note that says which
way to resolve it: move the other seven, not the bulb back._

Tabler is MIT, so the SVGs are vendored into `res/drawable/` like the rest. They draw on a 24 grid
against Material's 960, so they are the two files here whose path data is not simply Google's numbers
moved into the viewport — and the two that therefore carry no `<group>` translate at all. The filled
variant is built differently from the outline, which is worth knowing before editing either: the
outline is three stroked paths, the filled one seven filled shapes, one per ray plus the glass, with
`fill="currentColor"` on the root rather than on the paths.

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
- On a half tile the icon sits on the first line beside the name; on a third tile it sits above it,
  which is what the mockups showed and what stops a 251 dp tile from spending its width on a glyph.
- **A bulb circle has no container at all, and a lit lamp is a filled lamp.** A 72 dp cell holding a
  48 dp lamp and nothing else — no disc, no tile, no border, no halo, in either state. No text at any
  size either: the count and the age are on the group's one line underneath, which is what lets 28
  lamps be a row rather than fourteen rows. The row then reads as a wall of lamps rather than as a row
  of buttons, which is the thing 28 of anything most needs.

  | State | Lamp | Colour |
  | --- | --- | --- |
  | On | `ic_bulb_filled` | `tertiary` |
  | Off | `ic_bulb` | `onSurfaceVariant` |

  The colours are the **accent** roles rather than the container ones the cards wear, for the same
  reason the slider's fill takes them: there is no container left to hold `tertiaryContainer`, and
  `onTertiaryContainer` is a colour for sitting *on* that container. The lamp is 48 dp rather than the
  24 dp every other glyph takes because it is the only one with nothing to share its cell with — it
  inherits the footprint the old inset well occupied, and at 24 dp in a bare cell the row reads as a
  scatter of specks.

  **The state is a shape and not only a hue, and that is the point rather than a detail.** Measured on
  the glass with the filter on: the lit lamp composites `#865301 → #9E6301` and the unlit one
  `#3F4754 → #473719`. Off-filter those are amber against slate; on-filter they are both brown and the
  difference is mostly lightness — so the **hue** distinction really does erode on this wall, exactly
  as feared, and it is the filled-against-outline silhouette that survives the filter, four metres, and
  whatever the dark scheme does to the amber at night. `bulbGlyph(isOn)` in `TileLayout.kt` picks
  between the two, and `glyph(BulbTileState)` asks it rather than fixing the outline, so the lamp in a
  named tile and the lamp in a circle cannot come out as different lamps.

  Two treatments came before this one, and are recorded so the ground is not re-covered: a **filled
  amber disc per lamp**, which turns the row into 28 coloured dots — a status bar, not a set of
  lights — and a **white disc inset in a coloured tile**, which fixes that by making each lamp an
  object with a light in it, at two nested shapes and a glyph shrunk to 24 dp to fit inside them.
  Neither puts the light in the lamp, which is the whole idea: a lamp that is on should look lit, not
  look labelled.

  _This bullet briefly said the opposite_ — bring the disc back, let it carry the whole of the state,
  and put the same outlined lamp on it in every mood. That was reversed and the bare filled lamp
  confirmed, so the disc version is history and not an open question. Its two arguments were sound,
  though, and they are kept below as the two costs of the choice rather than argued away.

  - A glow was mocked and dropped. Recorded because the *implementation* note outlives the choice: a
    halo would have had to be a `Brush.radialGradient` and not `Modifier.blur`, which is API 31+
    against a minSdk of 26 — it would have drawn perfectly on this Android 13 tablet and silently
    nothing on Android 8 to 11. That trap is still there for anything else that reaches for a blur.
  - **The 64 dp touch target stays, invisible**, which is the first cost. It is the same one the
    handle-less slider took — cleaner to look at, less obviously pressable — and worse here, because a
    slider at least prints a number beside it and an unlit lamp offers nothing. The only shape that
    ever draws is the ripple, clipped to a circle the size of the target so a press has somewhere to
    land. _Watch whether anyone works out the lamps are tappable._ If they do not, the answer is a
    quiet disc under the glyph and not a return to coloured discs.
  - **A failing poll has no container to colour, which is the second cost.** `Failing` is a filled
    `errorContainer` on every *tile* on this wall — see "Colour" — and the circles are the one place
    that treatment cannot reach. So a failed group drops every circle to unlit, **shape included**:
    `Failing` outranks a perfectly good `isOn` for the reason it does in `mood`, and a filled lamp is a
    positive claim that this lamp is lit right now. The row's count still says how many were on when
    the reading was taken, and the group's line under the row carries the failure **in words**, once,
    which is where it was always said.
  - A circle is only ever `On` or `Off`. A bulb with no state at all breaks out of the row and becomes
    a named tile, so `Unknown` never reaches a circle — which is why two rows in the table above and
    not four. **A null takes the outline**, because the filled lamp is a positive claim the panel
    cannot make: the same rule `curtainGlyph` takes for its null, and the same rule the words have
    always followed.
- `contentDescription = null` on the glyph in every tile. They are decorative: the name is right
  there, and a screen reader announcing "lightbulb Лампа в коридоре" says the noun twice. **The bulb
  circles are the exception and already handle it** — they carry the lamp's name and state as the
  circle's own content description, because at 72 dp there is no room for either in text.

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

## Sliders

Three tiles have one: the air conditioner's temperature, the curtain's position, the light strip's
brightness. All three are Material's `Slider` today, at its default weight — a 16 dp track and a tall
handle — and on a 376 dp tile that control is the loudest thing on it, louder than the value it sets.

**Slim: a 6 dp fully rounded track and no visible handle.** The filled portion is the value. It reads
as a reading with a range behind it rather than as a control demanding to be operated, which is what
a wall panel wants — the number is the point and the slider is how it is changed, not the other way
round.

- Built with Material 3's `Slider` and its slot overrides — a custom `track` at 6 dp and a `thumb`
  that renders nothing. Not a hand-rolled draggable: the slot version keeps the drag behaviour, the
  value semantics and the accessibility that a `Box` with a `pointerInput` would silently drop. An
  empty thumb is safe rather than clever: the slider wraps each slot in a `Box` of its own and
  measures that, so an empty one is a zero-size box and not a missing child. Both slots are annotated
  `ExperimentalMaterial3Api` on this BOM, opted in on the one function rather than module-wide —
  `build.gradle.kts` keeps the Expressive opt-in it already had and gained nothing.
- **The touch area stays 64 dp tall** whatever the track looks like. A 6 dp visual is not a 6 dp
  target, and this is the wall panel's rule that overrides the aesthetic one. It is the *track slot*
  that is 64 dp, with the bar drawn centred inside it, because the slider's height is the taller of
  its two slots and its drag handling covers exactly that — a `heightIn` on the outside would have
  left the gesture on the 6 dp. _Measured:_ both sliders on Главная dump as **64.0 dp** tall, as
  `android.widget.SeekBar`, which is also the value semantics surviving the override.
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
- The two glyphs of the nine that are decisions rather than pictures. The curtain's: 0 is closed, 40
  and 100 are open, and **null is open**. The bulb's: on is filled, off is outlined, and **null is
  outlined** — a shut curtain and a lit lamp are both positive claims the panel cannot make. The other
  seven are a lookup from tile type to drawable and hold nothing a test could catch. Both assert
  `R.drawable.*` ids directly — the generated `R` is on the unit test classpath, so this needs no
  Compose and no Robolectric.

What does not get a unit test, and is checked on the tablet instead: the grid spans, the shapes, the
dark palette, the touch targets, the idle reset actually firing, and **whether a glyph draws at all** —
a vector drawable with bad path data is not a build error and not a test failure, it is an empty box
on the wall.

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

## What is left

Decided and not yet built, which is a different list from "Open" below — nothing here needs an
answer, only doing. Ordered by how much of the wall it changes.

**1 · The bulb circles do not match what was chosen.** The screenshot that was picked is a filled
disc carrying the mood colour — light container when on, `surfaceContainer` when off,
`errorContainer` when failing — with the same outlined lamp on every one of them. What commit 6
built is the treatment before it: a bare lamp in its own cell, filled when lit and outlined when
not, no disc in any state. The tile half of that change did land — every half and third tile fills
with `errorContainer` now — so it is only the lamps that are behind.

_Doing it:_ `BulbCircle` in `BulbTile.kt` gets a 72 dp disc and one glyph; `ic_bulb_filled.xml` and
`bulbGlyph` both go, along with `bulbGlyph`'s test. Note this also gives a failing lamp somewhere to
put its colour — today a failed group drops the circles to their unlit glyph and leaves the reason
to the group's line, because with no container there is nothing to fill.

**2 · The tab mark could be a colour now, and its comment is wrong until it is.**
[`PanelRooms.kt`](../app/src/main/kotlin/ru/domovoy/panel/PanelRooms.kt) says the mark is a `•`
character "because a colour is the one thing a tile cannot be trusted to have in both themes yet".
That was true when commit 1 wrote it and stopped being true at commit 5, which is where the panel
got a palette that works in both. The comment is misleading whether or not the mark changes, so the
smallest honest version of this is to fix the comment; the fuller one is to make the mark carry the
error colour like everything else that fails.

**3 · Commit 7, the tab strip's 64 dp.** See "The plan". One dp, no logic.

### Watch on the wall

Not tasks — bets already placed, each with the note that says which way to resolve it if it turns
out wrong. None of them can be settled from a screenshot.

- **Does anyone work out that the sliders are draggable?** They have no handle. If not, the answer is
  a handle, not a thicker track.
- **Does Главная read as alarm at boot?** Every tile is rose until the first poll lands. If it does,
  tell `lastPolledAt == null` from a stale timestamp — `Staleness.kt` has both — and leave the
  never-polled case neutral.
- **Does the Tabler bulb look foreign beside seven Material Symbols?** If it does, move the other
  seven to Tabler rather than the bulb back to Material.
- **Can a finger find the lamps?** Only while they have no container; item 1 above settles it either
  way.

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

### 4 · `feat(panel): group the bulbs` — done, #15

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
  same four moods as one; nothing new is needed to paint it. _Commit 6 took the container off the
  circles, so what a mood does to one is now a glyph and a tint rather than a disc colour — see
  "Icons"._

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

### 5 · `feat(panel): the panel's own colours` — done, #17

The commit that stops the wall being grey. Two things, and they are one concern because they are the
same file and neither works without the other: the panel gets a palette instead of Material's
baseline, and a tile's colour starts saying what kind of device it is.

New: `PanelTheme.kt` — the light and dark schemes, written out, generated from a cool blue and a warm
amber. This is the one file in the app allowed to hold colour values. It is not a wrapper with one
caller: it is data, and the alternative is 40 arguments inline in `MainActivity`.

Changed: `MainActivity.kt` picks between the two by `isSystemInDarkTheme()`. `TileLayout.kt` gains
`hue(...)`; the five tile composables map `(hue, mood)` to a role pair instead of `mood` alone.

Tests, written first — `TileLayoutTest` amended: an air conditioner and a recuperator are `Climate`;
a bulb and a strip are `Light`; a curtain and a launcher are `Neutral`. `hue` is a function of the
tile's type and nothing else — it does not consult `isOn`, because a lamp that is off is still a
lamp, and it is `mood` that decides whether the hue is used.

The scheme itself has no unit test — there is no Compose test dependency and adding one is an "ask
first". Checked on the tablet in both themes, and by grepping `panel/` for hex literals, of which
there should still be none. **Turn the blue light filter off before judging any of it**; it is on,
and it tints screencaps too.

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
| The bulb circles, on | `tertiaryContainer` — _as commit 5 left them; commit 6 took the disc away, and a lit lamp is now `tertiary` on bare `surface`_ |
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

### 6 · `feat(panel): an icon per tile, and a slimmer slider` — done, #19

The other half of what makes the wall read as a panel rather than as paragraphs. See "Icons" for the
glyph per tile, the sizes, and why they are `res/drawable` files rather than a dependency, and
"Sliders" for the 6 dp track.

New: nine vector drawables in `app/src/main/res/drawable/` — seven tiles, the curtain's second state
and the bulb's filled variant. Changed: the five tile composables and the bulb circles.

Two tests, because two of the nine are decisions rather than pictures. `curtainGlyph(openPercent:
Double?)` in `TileLayout.kt`, returning open or closed — 0 is closed, 40 is open, 100 is open, and
**null is open**. And `bulbGlyph(isOn: Boolean?)` beside it, returning filled or outlined — and **null
is outlined**, because the filled lamp is the same kind of claim the closed curtain is. Both written
first. The other seven are a lookup from tile type to drawable and hold nothing a test could catch.

The slim slider lands here too — the three tiles with one, per "Sliders". It is the same pass over
the same five composables, and splitting the glyph off from the control on the same tile would mean
opening each of them twice.

What this commit must not do is add a dependency: if `material-icons-extended` starts to look
necessary, stop and ask rather than adding it.

After 5, deliberately. Icons take the tile's content colour, so drawing them before the palette
exists means placing every glyph against the baseline violet and re-judging all of them once it
changes.

Also new, in `panel/`: `glyph(...)`, `curtainGlyph(...)` and `bulbGlyph(...)` in `TileLayout.kt` — one
overload per tile state, as `hue` already has; `TileGlyph` and `TileHeading` in `TileCard.kt`, where the
half-beside-third-above rule lives once and is taken from the tile's own span, so the recuperator
gets whichever its width earns without being asked twice; and `SlimSlider.kt`, three callers.

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
| Half beside, third above | `Кондиционер`/`Подсветка`/`Бризер`/`Шторы` beside the name, `Домофон`/`Пылесос` above it |
| The bulb circles | 48 dp lamp alone in its 72 dp cell, no container in either state — a filled `tertiary` lamp for a lit one, an outlined `onSurfaceVariant` lamp for an unlit one |
| Track height | 13 px = **6.1 dp**, both schemes |
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
a real reading and not the arithmetic un-tinting the table above rests on. `blue_light_filter` read 1
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

### 7 · `fix(panel): the tab strip's touch height` — done

`PrimaryScrollableTabRow` sizes its tabs at Material's default 48 dp while this doc asks for 64 dp on
everything a finger goes near, and the tab strip is the one control on the wall a finger can miss.
One override, no new logic, no test to write — it is a dp. Independent of everything else here and
can go whenever; last only because it is the smallest thing on the list.

`Modifier.heightIn(min = MIN_TOUCH)` on the `Tab`, which is where the 48 comes from: Material's
text-only tab asks for 48 dp of its own, and the row only passes on whatever its tallest tab wanted.
A floor rather than a fixed height, so a title that ever needs more room can have it.

**Measured on the wall rather than eyeballed**, the same way the switches were — `uiautomator dump`
at 340 dpi, density 2.125. Every tab comes back **64.0 dp** tall (136 px), and so does the row that
holds them, so the second override the row might have needed was not needed. The indicator survived
the taller row intact: 3 dp of it at px 181–186 against a row ending at 186, flush with the bottom
edge and still the width of the selected tab's text, not floating in the extra 16 dp.

### Order, and what it buys

1 before 2 because the shell is the only part with behaviour to get wrong, and it is worth having it
green before anything visual moves. 2 before the rest because the lights group is a mosaic idea and
has nowhere to live until the grid exists.

3 before 4 because the group line and the tab marks both read staleness, and grouping the bulbs on
top of a signal known to be wrong means doing it twice. 3 is also the only commit here that is a fix
rather than a feature, and it stays its own commit for that reason — a correction folded into a
feature is a correction nobody can find again.

5 and 6 last because they touch every colour and every tile the commits before them introduced, and
doing either earlier means doing it twice — 6 after 5 in particular, since a glyph takes its tile's
content colour and drawing seven of them against a palette about to be replaced is drawing them
twice.

The order held. What it did not buy, and nothing in it could have: 2 was still wrong about the grid
until the grid was on the wall, because the number it was wrong about was a measurement and not a
decision.

The same caught up with the look. Commits 1 to 4 were specified entirely in behaviour — spans,
splits, staleness, what a tile *says* — and every one of them was right about that and silent about
whether the result was worth looking at. What went up was a correct grey panel of text: no glyph
anywhere, one colour for everything that is on, and a scheme nobody had chosen, because
`lightColorScheme()` with no arguments is a decision that reads like a default. 5 and 6 exist
because a brief can be complete about behaviour and still not have described the thing on the wall.
