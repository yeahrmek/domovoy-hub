# PLAN — the panel's shape

Reshaping `panel/` after the Yandex smart home app: rooms stack instead of tabbing, one tile
anatomy, surfaces stop carrying hue, the lamp row becomes tiles, art per device.

**This file is written to be executed by agents working one task at a time, without the
conversation that produced it.** Each task is self-contained: context, files, acceptance criteria,
verification. Read *Ground rules* and *Reference* before starting any task; read no other task than
your own.

---

## Ground rules

Non-negotiable. These come from `CLAUDE.md`; an agent that breaks one has failed its task even if
the feature works.

1. **TDD.** Failing test first, then the code that makes it pass, same commit. A bug fix starts
   with a test that reproduces the bug.
2. **`./gradlew test` must pass before a task is reported done.** "It compiles" is not done.
3. **Never delete, skip, `@Ignore` or weaken a test to make the build green.** Fix the code.
4. **Do not touch `AndroidManifest.xml`, permissions, foreground services, minSdk or signing.**
   Any task that seems to need it: stop and report, do not proceed.
5. **Do not add a dependency, a DI framework or a module.** Stop and report.
6. **Do not reformat, rename or tidy files outside your task.**
7. **Assert on observable behaviour** — returned values, emitted state — not on which collaborator
   was called. A pure refactor must not break a test.
8. **No hex literals in `panel/`.** `PanelTheme.kt` is the only file allowed to hold colour values.
9. **The lock tile never gets a write action.** It reports; it does not act.
10. **Never cover, delay or suppress the Domonap call screen.**
11. Conventional commits, one concern per commit: `feat(panel): …`, `fix(panel): …`.

## Environment

There is no JDK, Android SDK or `adb` on the system PATH and there is not meant to be. Everything
lives in `.toolchain/`; `scripts/env.sh` puts it on PATH and must be sourced in the *same* command
as gradle. Gradle and `adb` both need the sandbox disabled — they bind local sockets.

```bash
source scripts/env.sh && ./gradlew test                    # after every change, must be green
source scripts/env.sh && ./gradlew ktlintCheck             # formatting
source scripts/env.sh && ./gradlew verifyRoborazziDebug    # compare against committed screenshots
source scripts/env.sh && ./gradlew recordRoborazziDebug    # rewrite them after a deliberate change
```

**The screenshot gate matters here.** `app/src/test/screenshots/` holds committed reference images
(`panel-home-dark.png`, `panel-home-light.png`, `tiles-dark.png`, `tiles-light.png`,
`lights-group.png`, `tabs-marked.png`). Every task below changes the wall, so `verifyRoborazziDebug`
*will* fail — that is expected and is not a licence to skip it. Run it, **look at the diff** in
`build/outputs/roborazzi/`, satisfy yourself the change is the one you intended, then
`recordRoborazziDebug` and commit the new references as part of the same commit.

## Order and dependencies

```
T0  answered — T5 dropped
T1 ──> T2 ──> T3 ──> T4
T6  independent, any time
```

- **T1 before T2** — section headings cannot be sized until the wall type scale exists.
- **T3 before T4** — T4 operates on the anatomy T3 establishes.
- **T5 is dropped.** Do not build it.
- **T6 never blocks anything.**

Do not run T2–T4 in parallel: they all touch `TileLayout.kt`, `TileCard.kt` and `PanelRooms.kt`.
T6 can run alongside anything.

---

## T0 · ANSWERED 2026-08-29 — the filter was the problem

**Result: yes. T5 is dropped.** With Eye comfort shield off (`system.blue_light_filter 1 → 0`) the
wall matches `PanelTheme.kt` exactly: background `#111318`, light container `#663E00`, tabs the
proper `#A5C8FF`. The palette was never wrong. **Do not build T5.**

Standing caveat, not a task: with the filter on, everything shifts warm and the wall reads as one
brown. If the filter goes back on at night, the panel needs a palette that survives it — that is
the same work T5 described, and it should be re-opened deliberately rather than assumed.

Note for whoever reads the history: an earlier sample of the "climate container" was taken from an
air conditioner that was **off**, so it was reading `surfaceContainer`, not `primaryContainer`. No
climate tile was on in either capture, so the blue family is still unverified on the wall. Not a
defect — just not evidence.

<details><summary>Original T0 instructions, kept for the record</summary>

**Not an agent task.** An agent must not change device display settings.

The panel was captured off the tablet on 2026-08-29 21:13 — inside the 19:00–07:00 dark window,
with the blue light filter on — and its pixels sampled against what `PanelTheme.kt` asks for:

| | asked for | on the wall |
| --- | --- | --- |
| background | `#111318` | `#402F13` |
| climate container | `#03497C` | `#4A3A1D` |
| light container | `#663E00` | `#845200` |
| promoted value | `#D4E3FF` | `#E6D6BB` |

The amber seed survives. The blue seed does not — 124 of blue down to 29, a deep blue tile arriving
as brown. The filter is a display colour transform and `screencap` captures it, so this is
approximately what the wall shows.

**Turn the filter off and look at the wall after 19:00.** If the panel reads correctly without it,
T5 is a device setting and not a commit — mark T5 dropped and say so here. If the filter stays on,
T5 proceeds as written.

</details>

---

## T1 · `feat(panel): a typography for wall distance`

**Depends on:** nothing.

**Context.** `MainActivity` passes `MaterialTheme` a `colorScheme` and nothing else, so the panel
runs on Material's baseline typography — a scale drawn for a phone held 30 cm from the face. Counted
across `panel/`: nine uses of `bodySmall` (12sp), two `titleMedium`, one `displaySmall`, one
`bodyMedium`. Every status line on a wall read from four metres is 12sp. Only the air conditioner
promotes a value; the curtain's position, the strip's brightness and the recuperator's temperature
sit at 12sp inside a dot-separated run-on line.

**Files.** `app/src/main/kotlin/ru/domovoy/PanelTheme.kt`,
`app/src/main/kotlin/ru/domovoy/MainActivity.kt`, `app/src/main/kotlin/ru/domovoy/panel/TileLayout.kt`,
the tile composables.

**Do.**
- Add a `panelTypography` to `PanelTheme.kt`, beside the two schemes and for the same reason — it is
  data, and that file is the one allowed to hold values. Pass it from `MainActivity` in the same
  call as the scheme.
- Size for the wall, not the phone. The floor is what is legible at four metres on a 753 dp panel at
  340 dpi. **Nobody has measured that on this tablet.** Pick a defensible scale, state the assumption
  in the KDoc, and flag in your report that it wants a walk to the hallway. What is not a guess:
  12sp is under it.
- **One promoted value per tile.** The AC has one; give the curtain its open percent, the strip its
  brightness, the recuperator its temperature. Ages and the rest of the line stay demoted.

**Done when.**
- No `bodySmall` remains as a status line in `panel/`.
- Which value a tile promotes is a pure function per tile state, living beside `hue` and `span` in
  `TileLayout.kt`, with a test table covering every tile type and its states.
- `./gradlew test` green; `ktlintCheck` green; Roborazzi references re-recorded and eyeballed.

**Do not.** Do not change any colour. Do not touch the grid.

---

## T2 · `feat(panel): rooms stack instead of tabbing`

**Depends on:** T1.

**Context.** The tab strip holds fourteen rooms across a 753 dp panel and cannot. `Гардеробная` is
clipped mid-word at the right edge; Ванная, Балкон and Гардероб are off the end entirely. Separately,
measured off the same capture: content stops at 563 dp of a 1205 dp screen — **53% of the wall is
empty**, every tile crammed into the top half. A horizontal strip is the wrong answer to a vertical
problem.

`docs/design/panel-redesign.md` item 9 describes this and defers it as "a bigger change than this
list wants". **This is that change; item 9 there is resolved by this task and must not be built
separately.**

**Files.** `app/src/main/kotlin/ru/domovoy/panel/PanelTabs.kt`, `PanelRooms.kt`, `RoomSections.kt`,
`app/src/main/kotlin/ru/domovoy/MainActivity.kt`.

**Do.**
- Rooms become sections down one scroll: heading, that room's tiles, next heading. One
  `LazyVerticalGrid` with headers spanning full width. `PanelTabs.kt` stops being a strip.
- The heading is the largest type on the wall and uses T1's scale.
- **Главная keeps its job** — pulling failing and stale tiles to the top is what makes a fourteen-room
  scroll bearable, and is what the tab marks stood in for.
- The room mark (the `•` and the error colour) moves onto the section heading, where it cannot
  scroll off the end of anything.
- Fix the grid-position bug while here: `PanelRooms` keys `LazyGridState` on the tile count, so a
  device appearing throws whoever is scrolled back to the top. Keep the state; scroll to top
  explicitly on the two events that need it, not under somebody's finger.

**Done when.**
- No room is unreachable and no room name is clipped.
- Section order and which tiles Главная pulls in are pure functions with tests.
- A room with a failing group carries its mark on its heading — asserted.
- Vertical fill measured from a fresh Roborazzi capture is materially above the 47% it is now.
- `./gradlew test` green; `ktlintCheck` green; references re-recorded and eyeballed.

**Do not.** Do not add a bottom navigation bar. Do not change tile colours or sizes — T3 owns that.

---

## T3 · `feat(panel): one tile anatomy`

**Depends on:** T2.

**Context.** Measured off the capture: the air conditioner is 169 dp tall with a large dead area
under its slider; the strip beside it is shorter; the recuperator shorter again; the launcher tiles
shorter still. Four heights, ragged bottom edges, no grid. `docs/ui.md` records "two tiles of the
same kind come out the same height" — that holds *within* a kind and there is no rule *across* kinds.
Internal rhythms differ too: a 3.6 dp hairline on the AC, a 6 dp filled bar on the strip, neither on
the launchers, bare discs for bulbs.

**Files.** `app/src/main/kotlin/ru/domovoy/panel/TileCard.kt`, `TileLayout.kt`, and every
`*Tile.kt`.

**Do.**
- `TileCard.kt` becomes the single anatomy. Slots: **art**, **controls**, **name**, **promoted
  value**, **status line**. A tile with nothing for a slot leaves it empty rather than re-flowing, so
  heights agree across kinds and not only within one.
- One radius, one padding, one column width. Span stays derived from the tile as today.
- Slot order follows the reference — art and controls on the top line, words at the bottom — **plus
  the promoted value**, which is the thing this panel does not give up (see *Reference*).
- Aim for three or four columns on 753 dp. Two columns is a phone proportion.
- While here: the sliders are 3.6 dp and 6 dp tall and read as decorative rules rather than
  controls. Give them a height that says they can be grabbed.

**Done when.**
- Every tile type returns something for every slot — asserted as a pure function, which is what stops
  a kind quietly re-flowing.
- Bottom edges align across kinds in a fresh Roborazzi capture.
- `./gradlew test` green; `ktlintCheck` green; references re-recorded and eyeballed.

**Do not.** Do not drop a value from a tile to make it look more like the reference — see
*Reference*, the load-bearing refusal. Do not change the palette.

---

## T4 · `feat(panel): the lamp row becomes tiles`

**Depends on:** T3.

**Context.** Seven identical amber discs in a row, unlabelled, one shared line beneath reading
`7 lamps · 7 on · never read`. They are 67 dp across — the best touch targets on the wall — and the
most saturated, highest-contrast thing on screen, so the eye lands there first and learns nothing.
Which lamp is which is not recoverable from the wall.

**Files.** `app/src/main/kotlin/ru/domovoy/panel/BulbTile.kt`, `BulbTiles.kt`, `BulbGroup.kt`.

**Do.** Pick one and say which in the commit message:
- the seven become ordinary tiles in T3's grid and carry their names; **or**
- the row collapses into one group tile saying `7 lamps` that opens the seven.

Both are honest; the row as it stands is neither. Either way the discs stop out-shouting every tile
that carries a reading.

**Done when.**
- Every lamp is identifiable from the wall without touching it, or is behind a group tile that says
  how many and what state.
- `bulbGroup` tests cover one lamp, seven lamps, and a group part-stale.
- `./gradlew test` green; `ktlintCheck` green; references re-recorded and eyeballed.

**Do not.** Do not settle the unlit-disc colour question here — that is `panel-redesign.md` item 6.

---

## T5 · DROPPED — `feat(panel): the surfaces stop carrying hue`

**Do not build this.** T0 was answered on 2026-08-29: the blue light filter was the whole problem,
and with it off the wall matches `PanelTheme.kt` exactly. Kept below only so that re-opening it —
if the filter goes back on permanently — does not start from nothing.

<details><summary>Dropped task, kept for the record</summary>

**Depends on:** T3, and on T0 having been answered.

**Context.** See T0's table. The consequence is the ΔE table in `PanelTheme.kt`: climate/light is
computed at 75 in dark, and with the blue axis flattened both families land in the same hue — the
wall is one colour. The palette is not wrong; it is being destroyed downstream of itself, at night,
which is half of every day.

**Files.** `app/src/main/kotlin/ru/domovoy/PanelTheme.kt`,
`app/src/main/kotlin/ru/domovoy/panel/TileCard.kt`, `TileLayout.kt`.

**Do.**
- Tiles sit on the neutral ramp — `surfaceContainer` and its four neighbours — instead of reaching
  for `primaryContainer` and `tertiaryContainer` for their family. The neutral family is already told
  apart by lightness rather than hue; extend that to all three families.
- Hue survives as **accent, not field**: the on-state indicator, the promoted value, the failing
  outline. A small saturated mark loses less to the filter than a large field does.
- **Land `panel-redesign.md` item 4 in the same commit** — group failure outlines (using the existing
  `groupFailureBorder`), a tile's own failure fills. And the `Off`/`Unknown` neutral disagreement
  recorded at that doc's foot. All three are one question — what the neutral ramp carries — and
  answering them separately will answer them three different ways.

**Done when.**
- No large field of `primaryContainer` or `tertiaryContainer` remains in `panel/`.
- The family a tile belongs to is still recoverable from the wall — verify on a re-recorded dark
  capture, not by reasoning.
- A group failure outlines and does not erase family colour across ~34 tiles at once.
- `./gradlew test` green; `ktlintCheck` green; references re-recorded and eyeballed.

**Do not.** Do not retune the two seeds or the generated ramps — `docs/ui.md` records them as decided
and this is not a reason to reopen them.

</details>

---

## T6 · `feat(panel): art per device`

**Depends on:** nothing. Independent of every other task; can land last or never.

**Context.** Nine drawables of mixed weight — a thin outline snowflake and fan against a filled bulb
glyph in the disc row. As a set they do not agree, and at four metres a thin outline glyph is the
least legible thing that could occupy that slot. In the reference, device art is the single largest
contributor to looking like a product: you identify a device by recognising the object, not by
decoding a symbol.

**Do.** Three options, in the order they are worth doing. **The first two need assets an agent
cannot produce — for those, report what is needed and stop.**
- **Photograph the actual hardware** on a neutral background. A couple of hours with a phone, and
  more honest than a render: the tile shows the lamp that is in that room.
- **Source renders** for the AC, curtains, seven bulbs, strips, recuperators, lock, vacuum, intercom
  — with the licence questions that brings.
- **Keep glyphs, unify them** — one stroke weight, one family, roughly double the size. The cheap
  answer, fully doable by an agent, and it fixes the inconsistency without buying the recognition.

**Done when.** Every device id resolves to an asset; the set shares one weight and family;
`./gradlew test` green; references re-recorded and eyeballed.

---

## Reference

### What was looked at

A screenshot of the Yandex smart home app on a phone, 2026-08-29, showing Ванная, Гардероб and
Детская. What it does, in the order it matters:

- **Colour is spent only on meaning.** Surfaces are near-white and barely-there grey. The only
  saturated pixels are a green "on" dot, a red struck-through wifi glyph, and the assistant orb.
- **Photographs of devices, not glyphs.**
- **Rooms are section headings, not tabs** — bold, several steps larger than anything in the tiles.
- **One tile anatomy, repeated exactly** — art top-left, small round controls top-right, name at the
  bottom. Uniform size, radius, aligned edges.
- **Almost no state on the tile.** No temperature, no percentage, no age.

The ad card is not incidental: it is what makes the tiles small and the page a feed. A panel with no
ad slot has that space back and should not inherit proportions chosen around one.

### What is taken and what is refused

Decided. No task re-argues this table.

| Yandex | Here | Why |
| --- | --- | --- |
| Rooms as vertical sections | **take** | Fixes the strip that cannot hold fourteen rooms *and* the empty half, in one change |
| Uniform tile grid | **take** | Four tile heights, misaligned bottom edges |
| One tile anatomy | **take** | Five tile types, five internal rhythms |
| Neutral surfaces, colour as status only | **take** | And it survives the filter |
| Device art instead of glyphs | **take** | T6, and the expensive one |
| Light theme | **refuse** | White in a dark hallway at 03:00; the 19:00–07:00 schedule exists for a reason |
| Small, truncated labels (`Свет в гарде…`) | **refuse** | Fine at 30 cm, useless at four metres |
| Bottom navigation bar | **refuse** | One screen, nothing to navigate to |
| Two columns | **adapt** | Two columns of a 411 dp phone is not two of a 753 dp wall. Three or four |
| State hidden behind a tap | **refuse** | Load-bearing — below |

**Why the last row governs everything.** Yandex shows almost nothing on a tile because it is a phone
app: opened, tapped, read, closed. A wall panel is read *without being touched* — the 16 °C and the
33.5 % visible from the hallway is the whole point, and `CLAUDE.md` requires a tile to say how old
its reading is. Copy the minimalism literally and the result is a handsome thing that has stopped
being a panel.

**Their layout discipline, this panel's information density.** A task that quietly drops a value to
look more like the reference is wrong.

### What must survive all of this

`mood`, `hue`, `span`, `isStale` and `bulbGroup` as pure functions outside the composables, and the
distinction between when the device reported and when the panel last read it. Every task above is
layered on those, not against them.

### Related documents

- `docs/ui.md` — what is already decided about the mosaic. Read before changing anything visual.
- `docs/design/panel-redesign.md` — thirteen audit items about what a tile *says*. T1 is its item 1;
  T2 resolves its item 9; T5 must land with its item 4. Its item 13 is an ask-first and is not in
  scope here.

## Reporting

On finishing a task, report: what changed, the test and ktlint result, what the Roborazzi diff
showed and why it is the intended change, anything assumed that wants a walk to the hallway, and
anything found that belongs in another task rather than yours.
