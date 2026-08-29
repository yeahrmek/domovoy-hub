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
  tallest tab, so the strip came up 64.0 dp with them. The selection indicator survived the taller
  row intact: 3 dp of it at px 181–186 against a row ending at 186, flush with the bottom edge and
  still the width of the selected tab's text rather than floating in the extra 16 dp.
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

   **A tab that is marked _and_ selected says both things, and that is deliberate.** Material's
   `Tab` defaults *both* of its content colours to the strip's own — confirmed by reading `Tab`'s
   signature out of material3's `classes.jar` on this BOM, not from the docs — so on this panel
   "which tab is open" has only ever been said by the indicator underneath and never by the label
   colour. So the error colour is given to a marked tab whether or not it is the open one, and the
   indicator is left alone at `primary`. Neither signal loses: an error-coloured title with the
   selection bar still under it.

   **Measured on the glass, 2026-08-29, filter on, both themes**, with the network dropped so that
   Yandex failed for real. The strip composited as `#F9E8CD` in light and `#402F13` in dark, both
   the values the bulb work recorded for a filter that is genuinely on, so these are real readings.
   A marked title came out `#C23F18` in light and `#F5B492` in dark against an unmarked `#366E82`
   and `#B7C1CD` — **ΔE 91 apart in light and 40 in dark**, where the filter costs 6 and 5 of that
   respectively. This is not a distinction the filter is going to erode; the tiles live at 13 to 19.
   A marked title against the strip behind it is 4.3:1 in light and 7.2:1 in dark **as composited**,
   against 6.2:1 and 10.9:1 as designed. _The light figure is marginally under WCAG's 4.5:1 for text
   this size, and it is the filter rather than the palette:_ an **unmarked** tab measures 4.7:1
   through the same filter against 6.2:1 designed, so the filter costs every label on this strip
   about the same and the mark is not what put it there. If it is ever worth fixing it is a palette
   change and not a tab-strip one.
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
- **The bulb circles take it too, and take it from the same function.** A circle is a 72 dp disc
  wearing the `mood(isOn, error)` colours every card wears — see "Icons" — so a failing lamp is the
  same rose as a failing tile rather than a lamp pretending to be off. It was the one place none of
  this could land for as long as commit 6's bare lamp had no container to fill; the group's line
  under the row still carries the reason in words, which the colour cannot.
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

### The roles, measured on the glass

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
| The bulb circles, on | `tertiaryContainer` — _as commit 5 left them, and as they are again: commit 6 took the disc away for a lamp tinted `tertiary` on bare `surface`, and the disc came back_ |
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

## Icons

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
- On a half tile the icon sits on the first line beside the name; on a third tile it sits above it,
  which is what the mockups showed and what stops a 251 dp tile from spending its width on a glyph.
- **A bulb circle is a filled disc carrying the mood, with the same outlined lamp on every one of
  them.** A 72 dp disc holding a 48 dp `ic_bulb` and nothing else — no text at any size: the count
  and the age are on the group's one line underneath, which is what lets 28 lamps be a row rather
  than fourteen rows.

  | Mood | Disc | Lamp |
  | --- | --- | --- |
  | On | `tertiaryContainer` | `onTertiaryContainer` |
  | Off | `surfaceContainer` | `onSurfaceVariant` |
  | Failing | `errorContainer` | `onErrorContainer` |

  **The disc says the state and the lamp says what kind of thing it is**, which is the split every
  other tile on the wall already makes: a card's colour is its mood and its glyph is its type. Those
  are the **container** roles the cards wear rather than the accent ones, because there is a
  container here to hold them — the row is the light family's own `tertiaryContainer` when lit, and
  the same rose as every other failing tile when its poll failed. It is `mood(isOn, error)` that
  picks, the same function all five tile composables ask, so a failing lamp cannot quietly rank
  differently from a failing card. The lamp is 48 dp rather than the 24 dp every other glyph takes
  because it is the only one with nothing to share its cell with, and at 24 dp on a 72 dp disc the
  row is a status bar of coloured dots with specks on them.

  **A circle is only ever `On`, `Off` or `Failing`** — three rows above and not four. A bulb with
  `isOn == null` breaks out of the row and becomes a named tile, which is `bulbGroup`'s whole split,
  so `Unknown` never reaches a disc.

  **The group's line still says the failure in words**, unchanged: the rose says which lamps, the
  line says why. Neither is enough on its own — a colour cannot name a hostname and a line of
  `bodySmall` is not read from four metres.

  One treatment came before this one and is recorded so the ground is not re-covered: a **white disc
  inset in a coloured tile**, two nested shapes with the glyph shrunk to 24 dp to fit inside them.
  What it bought over one disc was not worth two shapes and a smaller lamp.

  _Commit 6 built a third and it was on the wall until this change_: no disc at all, a bare lamp in
  its cell, `ic_bulb_filled` in `tertiary` when lit and `ic_bulb` in `onSurfaceVariant` when not,
  with `bulbGlyph(isOn)` picking between the two. It is worth keeping the measurement that was taken
  for it, because it settles something a screenshot cannot: with the filter on, its lit lamp
  composited `#865301 → #9E6301` and its unlit one `#3F4754 → #473719` — both brown, told apart
  mostly by lightness. **A hue distinction really does erode on this wall.** That is an argument
  about a 48 dp glyph's tint against a bare surface, not about a 72 dp disc, whose On and Off states
  are `tertiaryContainer` against `surfaceContainer` — a pair the filter was separately measured
  against at commit 5 and left at 34 of separation. But anything on this wall that plans to say
  something with a hue alone should be measured the same way before it is trusted.

  **Measured on the glass, 2026-08-29, filter on, both themes.** The surface composited as
  `#F9E8CD` in light and `#402F13` in dark — both the values commit 6 recorded for a filter that is
  genuinely on, so this is a real reading and not an un-tinted reconstruction. In light: a lit disc
  `#FFD296`, which is the light strip tile's `tertiaryContainer` **to the byte**, two different
  things through one filter and therefore a filter-independent check; an unlit disc `#EFDFC3`, the
  same as the off ac tile and both launchers; a failing disc `#FAD2B1`, which is `errorContainer`
  through the filter's own gain. Separations: on/off **21**, on/failing **14**, failing/off **11**.

  **The unlit disc is all but invisible against the panel, and that is the palette rather than the
  filter.** `surfaceContainer` on `surface` is ΔE **4.0 as designed** in light and 6.3 in dark; it
  measured 3.3 on the glass. So an unlit lamp still reads as a bare lamp with a faint halo, which is
  very nearly what commit 6 drew — the disc earns its keep on a lit lamp and on a failing one and
  earns nothing on an off one. This is the same step every *off card* on the wall already sits at,
  and a card carries text and a corner to be found by where a disc has only its fill. _This is the
  one thing the wall check turned up that the plan did not, and it is a bet rather than a bug — see
  "Can a finger find an unlit lamp?" under "Watch on the wall", which holds the number to move if it
  turns out to matter._

  Two things that argued for the disc all along, and what it actually bought:

  - **The 64 dp touch target is visible on a lit lamp, and on a failing one.** The bare lamp's only
    drawn shape was the ripple it took to press — the same cost the handle-less slider still pays,
    and worse there, because a slider at least prints a number beside it and an unlit lamp offered
    nothing. The disc is the "quiet disc under the glyph" that bullet named as the fix. On the wall
    it is quiet enough that an unlit lamp is not much better off than before, which is now its own
    bet; a room whose lamps are all on — which is most of them, most of the time — is.
  - **A failing poll has somewhere to put its colour.** `Failing` is a filled `errorContainer` on
    every *tile* on this wall — see "Theme" — and the circles used to be the one place that
    treatment could not reach, so a failed group dropped every lamp to its unlit shape and a failing
    lamp pretended to be an off one. It is rose like everything else now.
  - A glow was mocked and dropped, and the *implementation* note outlives the choice: a halo would
    have had to be a `Brush.radialGradient` and not `Modifier.blur`, which is API 31+ against a
    minSdk of 26 — it would have drawn perfectly on this Android 13 tablet and silently nothing on
    Android 8 to 11. That trap is still there for anything else that reaches for a blur.
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
- The tab marks: a room marked when its group errored, marked when everything in it is stale,
  unmarked otherwise.
- The bulb split: `Reading.Never` leaves the group; a bulb 3 minutes old leaves the group; a bulb
  90 seconds old stays; the group line quotes the oldest of those that stayed.
- The tab list: Главная first, rooms in `roomSections` order, Без комнаты last and present even when
  every other section is empty.
- The tile layout: the recuperator's span from whether it reports climate, and `mood` from `isOn`
  and the error. Added in commit 2, which is where "a re-skin has nothing to test" turned out to be
  wrong: both are decisions with a right and a wrong answer, and a decision that only exists inside
  a `@Composable` is a decision no test can reach.
- The one glyph of the eight that is a decision rather than a picture: the curtain's. 0 is closed, 40
  and 100 are open, and **null is open** — a shut curtain is a positive claim the panel cannot make.
  The other seven are a lookup from tile type to drawable and hold nothing a test could catch, the
  bulb's included since the disc took the state off it; what is asserted there is only that a
  broken-out bulb tile draws `ic_bulb`, which is that tile's whole contract. Both assert
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

Six images in `app/src/test/screenshots/`, committed, drawn by Robolectric and Roborazzi at the
wall's own geometry — **753 × 1204 dp at 340 dpi, portrait**, which is the 1600 × 2560 px the tablet
measured. A screenshot at any other width is a picture of a panel that does not exist, since six
columns is sized from that 753 and from nothing else.

| Image | What it is for |
| --- | --- |
| `panel-home-light`, `panel-home-dark` | Главная whole, in both schemes: the spans, the corners, the mosaic as one thing |
| `tiles-light`, `tiles-dark` | Every `TileHue` × `TileMood` pair, plus the group-failure outline. This is the ΔE table in `PanelTheme.kt` made visible |
| `lights-group` | The row of 72 dp circles, and the `Never` bulb broken out above it |
| `tabs-marked` | A strip with three rooms carrying the dot and the error colour |

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
these six as ordinary tests and neither records nor compares, so the other 241 keep costing what
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
- **Does Главная read as alarm at boot?** Every tile is rose until the first poll lands. If it does,
  tell `lastPolledAt == null` from a stale timestamp — `Staleness.kt` has both — and leave the
  never-polled case neutral.
- **Does the Tabler bulb look foreign beside seven Material Symbols?** If it does, move the other
  seven to Tabler rather than the bulb back to Material.
- **Can a finger find an _unlit_ lamp?** The disc settled this for a lit lamp and for a failing one
  and did not settle it for an off one: `surfaceContainer` on `surface` is ΔE 4 in light and 6 in
  dark, so an off circle is a lamp with a faint halo and not much more than commit 6 drew — measured,
  see "Icons". The wider question the disc did answer is struck from this list; this is what is left
  of it, and it is the last state on the wall whose only affordance is the ripple under a finger.

  _Doing it, if the wall says so:_ **move the tone, not the role.** `surfaceContainerHigh` is one
  step up, is already in both schemes, and is one word in `BulbCircle`'s `when` — `#E5E8EE` against
  the `#EBEEF3` it wears now, which takes the light separation from ΔE 4 to about 7. Do not reach for
  `secondaryContainer` or any other *on* colour: an off lamp borrowing one is the tile claiming a
  reading nobody took, which is the rule the whole palette is built on.

  Two things to know before spending anything on it. It is **the same step every off card already
  sits at** — an off ac tile is `surfaceContainer` on `surface` too, and nobody has complained,
  because a card is found by its name and its corner where a circle has only its fill. And it is
  **rare on this wall**: the flat's lamps are mostly on, and a room with all of them off has a group
  line saying `0 on` right underneath. So this is worth a look from four metres before it is worth a
  commit.

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
the tab marks both read staleness and grouping the bulbs on top of a signal known to be wrong means
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