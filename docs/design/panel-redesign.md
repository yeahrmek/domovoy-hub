# Design: what the panel still does not say

**Scope:** the work left in `panel/` after the seven mosaic commits, as an ordered list of changes.
Not a re-argument of what is already decided — `docs/ui.md` is the record of that and this doc does
not repeat it. Where the two disagree, this doc says so and says why.

**Status: item 1 landed with the wall typography, item 4 with the neutral surfaces, items 6 and 9
dissolved; the rest are open.** Every item below is written to be implementable on its own, in the
order given, one concern per commit. Items 1–5 change what the wall looks like; 6–12 are smaller.
Item 13 is an "ask first" and the branch stops there until it is asked.

Read `docs/ui.md` first. It already tracks nine open questions and most of them are not here; the
two it holds open that this doc argues are decided are in "Where this disagrees with ui.md".

---

## 1 · `feat(panel): a typography for wall distance`

**What is wrong.** [`MainActivity`](../../app/src/main/kotlin/ru/domovoy/MainActivity.kt#L83) passes
`MaterialTheme` a `colorScheme` and nothing else, so the panel runs on Material's **baseline
typography** — a scale drawn for a phone held 30 cm from the face. `docs/ui.md` has ninety lines on
colour and not one line on type.

Counted across `panel/`: **nine uses of `bodySmall`, two of `titleMedium`, one of `displaySmall`,
one of `bodyMedium`.** `bodySmall` is 12sp, and it is what every status line on the wall is set in —
every "on", every age, every error, on a screen read standing up from four metres.

The same gap produces an inconsistent hierarchy. The air conditioner promotes its target to
`displaySmall` ([`AcTile`](../../app/src/main/kotlin/ru/domovoy/panel/AcTile.kt#L46)) and **nothing
else on the wall promotes anything.** The curtain's position, the strip's brightness and the
recuperator's temperature and humidity — the numbers most worth reading on the way past — are 12sp
inside a dot-separated run-on line.

**What to do.**

- A `panelTypography` in `PanelTheme.kt`, beside the two schemes and for the same reason: it is data,
  it is the one file allowed to hold values, and `panel/` stays clean of them. Passed from
  `MainActivity` in the same call as the scheme.
- Size it for the wall rather than for the phone. The floor is what is legible at four metres on a
  753 dp panel at 340 dpi; nobody has measured that on this tablet, so it is a number to take there
  rather than to pick here. What is not a guess is that 12sp is under it.
- **One promoted value per tile.** The AC has one; give the curtain its open percent, the strip its
  brightness, and the recuperator its temperature. Ages and the rest of the line stay demoted.

**Test.** Typography is a wall check, not an assertion — there is nothing here a unit test can hold.
What *can* be tested is the split it forces: which value a tile promotes is a pure function per tile
state, out beside `hue` and `span` in `TileLayout.kt`, and that gets a test like they do.

---

## 2 · `feat(panel): a tap says it landed`

**What is wrong.** Every switch on the wall is written like this:

```kotlin
Switch(checked = tile.isOn == true, onCheckedChange = { onToggle(tile.id) })
```

The callback discards its argument and `checked` is driven only by polled state. Compose's `Switch`
holds no state of its own, so **the thumb does not move when it is tapped.** Nothing moves. What
follows is one HTTP round trip plus a re-read for Yandex, or a command plus a re-read against a
6-minute cadence for Tuya, with no acknowledgement of any kind.

`AGENTS.md` requires every failure a visible state in the UI and no spinner that can spin forever.
This is the other half of that rule and it is unmet: **there is no in-flight state anywhere in the
panel.** On a wall panel an unacknowledged tap is the tap that gets made four more times.

**What to do.**

- A fifth `TileMood` — the value is confirmed by nobody yet. It ranks below `Failing` and above
  `On`/`Off`, on the same reasoning `mood` already uses: the panel must not assert what it has not
  read, and it must say which of the two kinds of not-knowing this is.
- The switch takes the optimistic position while the mood is pending, so the thumb moves under the
  finger; the next poll confirms it or the failure replaces it.
- It has to expire. A command whose re-read never lands must fall back to `Failing` rather than
  sitting pending for ever — that is the spinner the rule is about, wearing a different shape.

**Test.** `mood` is a pure function and this is one more branch of it. The expiry is a clock, so it
goes where `resetAfterIdle` and `pollPausingForCalls` already are — a suspend function over a flow,
driven by a test that advances time rather than waits.

---

## 3 · `fix(panel): the sliders resync from the vendor`

**What is wrong.** All three sliders — [`AcTile`](../../app/src/main/kotlin/ru/domovoy/panel/AcTile.kt#L66),
[`CurtainTile`](../../app/src/main/kotlin/ru/domovoy/panel/CurtainTile.kt#L52),
[`LightStripTile`](../../app/src/main/kotlin/ru/domovoy/panel/LightStripTile.kt#L69) — hold their
dragged value like this:

```kotlin
var dragged by remember(tile.id) { mutableFloatStateOf(sliderStart(tile, bounds.min)) }
```

Keyed on `tile.id` and on nothing else, so once the tile is composed `dragged` never resyncs. Set
the air conditioner from the Yandex app and the panel's **status line updates while the bar does
not** — indefinitely, for any tile that stays on screen, which is exactly the Главная tiles. The bar
is the thing being read from four metres and it is the one that is wrong.

The same mismatch shows up right after a drag, for up to 15 s: `dragged` holds the new value while
`temperatureLabel(tile)` still prints the old one. The doc's stated answer to the handle-less
slider — *"the three tiles that have one are the three whose status line already prints a number"* —
is leaning on the number that is wrong during exactly that window.

**What to do.** Keep the local value; it is right, and the comment explaining it is right. Key it on
the reported value as well, so a reading the panel did not produce moves the bar, and a reading it
did produce does not drag the handle back out from under the finger. Once item 2 lands, the pending
mood is what tells those two cases apart and the key can be honest about which is which.

**Test.** The reconcile rule is a pure function over (reported value, local value, pending) and
belongs beside `mood`. Assert the three cases: vendor moved and we are idle → bar follows; vendor
moved and we are mid-drag → bar holds; our own write came back → bar holds.

---

## 4 · DONE — `feat(panel): a failing group keeps its colour`

**Landed inside `feat(panel): the surfaces stop carrying hue`**, which is where PLAN.md put it: the
question "what does a group failure do to a tile" and the question "what does a tile's surface carry
at all" have one answer between them, and answering them apart would have answered them differently.

What was built, against what is written below: the split is `TilePaint` in `TileLayout.kt`, a pure
function per tile type, so a group error reaches the border and a tile's own error reaches the mood
— for **every** kind and not only the recuperator. The hue survives a group failure completely,
because it is an accent now and the outline is the only thing that changes. The second
inconsistency this item found — the tab mark dual-coded, the tile fill colour-only — is gone with the
fill: the failing tile's mark is a shape *and* a colour, an `error` chip under the glyph.

The boot case this item predicted it would dispose of is disposed of, and by more than it expected:
an unpolled tile is not outlined either, it is the quietest step of the neutral ramp.

<details><summary>The item as it stood, kept for the record</summary>

**What is wrong.** `docs/ui.md` defends `Failing` as a filled `errorContainer` on the grounds that
this palette's rose is muted rather than alarming. That answers the wrong objection.

Every Yandex tile shares one error string, so a single failed `/v1.0/user/info` turns **every air
conditioner, curtain, strip and bulb in the flat rose in the same frame** — about 34 of the 35
tiles. The problem is not that it looks like an emergency. It is that the mosaic's entire
information structure is the climate-blue / light-amber / neutral coding, and a group failure
*erases* it exactly when somebody needs to work out what is broken. The loudest signal on the wall
is spent saying the thing that was already obvious from the wall changing colour.

There is a second inconsistency under it. The tab mark is dual-coded — a `•` **and** the error
colour — with a measured argument: the permanent blue light filter erodes red against neutral, so
the shape carries the state and the colour reinforces it. The tile fill is **colour only**. Same
filter, same wall, opposite conclusion, and no reason given for the difference.

**What to do.** Invert the existing split, which is already built:

- **A group's failure outlines**, using [`groupFailureBorder`](../../app/src/main/kotlin/ru/domovoy/panel/TileCard.kt#L168)
  — which exists and today only the recuperators use — plus the one line at the top of Главная that
  `groupFailures` already knows how to write. The hue survives, so the wall still says which family
  each tile belongs to while it is failing.
- **A tile's own failure fills**, which is the recuperator's per-device error and nothing else. That
  is the case where rose is carrying information the outline cannot.

This also disposes of the boot case the doc records as known and accepted — every tile rose until
the first poll lands — without needing the `lastPolledAt == null` special case it holds in reserve.
An outline at boot is a wall saying "not read yet", which is what it means.

**Test.** `mood` and `groupFailureBorder` are already the seam; what changes is which of the two a
group error reaches. The existing tile tests cover the pairs — add the group/own distinction for
every tile type, not only the recuperator.

</details>

---

## 5 · `fix(panel): a stale group reaches the tile`

**What is wrong.** [`mood`](../../app/src/main/kotlin/ru/domovoy/panel/TileLayout.kt#L82) is a
function of `isOn` and `error` and nothing else. So a poll that stopped landing **without any call
failing** leaves every tile in that group painted confidently on, printing the vendor's
`last_updated` as though it were freshness.

`Staleness.kt` models the distinction properly and the reasoning in it is right. It reaches the tab
mark and it reaches which tiles Главная pulls in. It does not reach the paint, which is the only
thing read from four metres.

`docs/ui.md` files this under "Open" as a spec question. `AGENTS.md` says *a tile that cannot say
when it was last read is a bug*, and this is a tile that cannot say it — so it is a bug, and the
spec question was answered before it was asked.

**What to do.** `mood` takes the group's staleness as a third input, ranked with the other two:
`Failing` still outranks everything, stale outranks `On`, and a stale tile draws as item 4's
outline rather than as its domain colour. The interval is already threaded down to `PanelRooms` for
both vendors — nothing new has to be plumbed.

**Test.** One more axis on `mood`'s existing table, plus the tile tests asserting that a stale group
paints differently from a current one.

---

## 6 · MOOT — `fix(panel): the unlit lamp takes the colour every other off tile takes`

**There is no `BulbCircle` to fix.** The lamp row became one group tile (PLAN.md T4), so the
divergent `when` this item was about is gone with it and every tile on the wall reads `tileColors`.
Nothing here is left to build.

**It is moot rather than settled**, and the difference matters: this item never argued for a tone,
only against having two answers. The open question it deferred to — `Off` and `Unknown` sharing
`surfaceContainer` — is still open, for every tile at once, and is in "Where this disagrees with
ui.md" below.

<details><summary>The item as it stood, kept for the record</summary>

`BulbCircle` drew `Off`/`Unknown` as `surfaceContainer` → **`onSurfaceVariant`**, while
[`tileColors`](../../app/src/main/kotlin/ru/domovoy/panel/TileCard.kt) drew the same mood as
`surfaceContainer` → **`onSurface`**. `docs/ui.md` said the disc wore "the mood colours every card
wears" and that "the lamp takes the on-colour of whichever it is sitting on". It did not. The one
glyph the doc was worried about anybody finding got the weaker of the two on-colours, in the one
state the doc says is unsettled.

</details>

---

## 7 · `fix(panel): an error is a sentence, not an exception`

`describe()` in `BulbTiles.kt` is `message ?: className`, and that string is concatenated raw onto
the status line by every tile. On the wall that reads
`not updating: Unable to resolve host "openapi.tuyaeu.com"` — Java's words, in the middle of a line
whose other half is the panel's.

It is also load-bearing for layout. The string is unbounded, so at 12sp on a 376 dp tile it wraps,
and the tile grows taller than the one beside it. `docs/ui.md` records this happening to a
placeholder recuperator and treats it as a one-off of that case; it is systemic, and nothing
constrains it.

**What to do.** A small set of reasons the panel is willing to print — unreachable, timed out,
refused, and one fallback — mapped from the throwable at the edge where it is caught. The tile
prints the reason; the exception text goes to `Log`. Cap what a status line may occupy so the
mosaic's "two tiles of the same kind come out the same height" rule cannot be broken by a vendor's
error text.

**Test.** The mapping is a pure function over a throwable and gets a table.

---

## 8 · `feat(panel): one language on the wall`

Recorded in `docs/ui.md` under "Open" as needing its own commit. This is that commit, and it is
listed here so it is scheduled rather than only acknowledged. Today a failing group prints
`Бризеры: not updating: Unable to resolve host` — three registers in one line. Item 7 is worth doing
first: it decides what the strings *are* before this decides what language they are in.

---

## 9 · RESOLVED — `feat(panel): a marked room that scrolled off still says so`

**Done, by the second of the two options below.** The tab strip is gone: rooms are sections down one
scroll and the mark is on the heading, where it travels with the room and cannot scroll off the end
of anything. Nothing is left of this item to build — do not build the indicator.

<details><summary>The item as it stood, kept for the record</summary>

Rule 2 of the tab shell says the mark is "visible from Главная without opening the room". Rule 4
says Ванная, Балкон and Гардероб scroll off the end of the strip. For those three rooms the two
rules contradict each other and rule 4 wins.

Главная pulling in the failing and stale tiles covers most of it, which is why this is item 9 and
not item 2. What is missing is the mark itself doing what it claims for the last third of the flat.
The cheapest honest fix is an indicator at the scrolled-off end of the strip; the alternative is
admitting the strip cannot hold fourteen rooms and giving room navigation a different shape, which
is a bigger change than this list wants.

</details>

---

## 10 · `feat(panel): decide what a tile's body does`

Three rules on one wall today, none of them written down:

- a device tile is tappable only in its 64 dp switch, because `TileCard(onClick = …)` is null for
  every one of them;
- a launcher tile takes the whole card;
- and so does a lights group tile, which is the newest of the three and was written to the launcher's
  rule rather than to a rule.

So on a 251 dp air conditioner most of the surface is inert while the tile beside it is live end to
end. Refusing the body may well be right — an accidental elbow should not switch the flat off — but
it is currently an accident rather than a decision. Pick one rule and write it in `TileCard`'s doc
beside the `onClick` null comment that already explains the launcher's dead tap.

_Half of this item has already gone away on its own:_ the row of discs used to be tappable across
its whole 72 dp while the same bulb as a named tile was not, and the discs are gone (PLAN.md T4).
The two shapes a bulb comes in agree now — both are cards, and both take the tap in the switch.

---

## 11 · `feat(panel): the wall changes state without flickering`

There is no `animate*`, `Crossfade`, `AnimatedVisibility` or `Modifier.animateItem()` anywhere in
`ru.domovoy`, while `docs/ui.md` states under "Compose APIs" that motion is the expressive spring
specs. Nothing implements it.

Two places it shows. A wall going from blue to rose in one frame on a 15 s cadence reads as a
display glitch rather than as a state change. And the Tuya cold start the doc describes — five
placeholders replaced 0.4 s later, one of them changing span from a third to a half — is a visible
jump that a short transition would absorb.

Motion is the last thing to add and the first thing to overdo. One duration for a colour change, one
for a tile arriving or leaving, and nothing else.

---

## 12 · `fix(panel): the grid keeps its place`

[`PanelRooms`](../../app/src/main/kotlin/ru/domovoy/panel/PanelRooms.kt#L112) keys `LazyGridState` on
the tile count, so a device appearing or disappearing throws whoever is scrolled into a room back to
the top. The two problems that key solves are real and are documented in the comment above it. Both
are reachable with a kept state and an explicit scroll-to-top on the two events that need it, which
does not also fire under somebody's finger.

While in here: nothing tests what the panel does at a raised system font size. Everything is `sp`
text inside `dp` tiles, and the layout's proudest property — two tiles of the same kind coming out
the same height — depends on nothing wrapping. Raising the tablet's font scale is a plausible thing
for somebody to do to a wall panel.

---

## 13 · Ask first: the panel has no opinion about being always on

`AGENTS.md` opens with "one always-on screen for the flat". Nothing in the code implements that:
no `keepScreenOn`, no `enableEdgeToEdge`, no insets handling, and `Theme.DomovoyHub` inherits
`android:Theme.Material.Light.NoActionBar` — the *platform* Light theme. Three consequences, none of
them recorded anywhere:

- **The system bars do not follow the panel.** They are themed by a light platform theme and do not
  switch at 19:00 with everything else, so at night the dark mosaic sits between two foreign-coloured
  bars — and the navigation bar permanently takes height from a panel that has nothing to navigate
  back to.
- **A light `windowBackground` is a white flash on cold start**, in a dark hallway, at night.
- **The screen sleeps at two minutes and the tablet PIN-locks** — `docs/ui.md` records the lock under
  "Open" and records that the tablet's own screen timeout is also two minutes, so the idle reset and
  the timeout are racing. Either the screen sleeps, and the always-on panel is a lock screen most of
  the time and the idle reset is nearly pointless; or the screen is kept on, and burn-in on the
  SM-T875's AMOLED under a fixed tab strip and a fixed grid becomes a real constraint. **Nothing in
  the app chooses.**

All of it touches `AndroidManifest.xml`, window flags or a wake lock, which `AGENTS.md` makes an
"ask first". Nothing here is to be built until it is asked.

---

## Where this disagrees with ui.md

Two things `docs/ui.md` files under "Open" that this doc treats as decided, so that the
disagreement is explicit rather than discovered later:

- **Whether a stale group should reach the tile's paint.** ui.md calls it a spec change rather than
  a bug fix. `AGENTS.md`'s rule about a tile that cannot say when it was last read decides it. See
  item 5.
- ~~**`Off` and `Unknown` sharing `surfaceContainer`,**~~ **settled with item 4, as this said it
  should be.** The ramp carries the mood and only the mood: `On` `surfaceContainerHighest`,
  `Failing` `High`, `Off` the base, `Unknown` `Lowest`. Four steps, four moods, and the paint no
  longer undoes what the strings were careful about. What it cost is written down in ui.md under
  "Watch on the wall": `Lowest` is past the background in both schemes, and a launcher is `Unknown`
  for ever.

## What is not in this list, and why

Not because it does not matter — because ui.md already records it as decided, measured or
deliberately accepted, and re-opening it needs a reason this audit did not find: the two seeds and
the generated ramps, the six-column halves-and-thirds grid, the corner derived from the span, the
Tabler bulb among seven Material Symbols, the handle-less slider, the ×8 staleness multiple, the
2-minute idle reset, and the recuperator's content-decided width.

The parts worth protecting while doing any of the above: `mood`, `hue`, `span`, `isStale` and
`bulbGroup` as pure functions outside the composables, and the distinction between when the device
reported and when the panel last read it. Everything in this list is layered on those, not against
them.
