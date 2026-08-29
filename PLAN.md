# PLAN — the dark wall

The shape work is done and merged (PR #23: typography, rooms as sections, one tile anatomy, the
lamp group, glyphs at wall size). What is left is **colour**, judged against the Yandex app's dark
theme, which is the thing being aimed at.

**This file is written to be executed by agents working one task at a time, without the
conversation that produced it.** Each task is self-contained. Read *Ground rules* and *Reference*
before starting any task; read no task other than your own.

The previous plan and its report are in git — `bea50c2` for the plan, `655a683` for the report.
This file replaces it and does not re-open anything it settled.

---

## Ground rules

From `CLAUDE.md`. An agent that breaks one has failed its task even if the feature works.

1. **TDD.** Failing test first, then the code, same commit. A bug fix starts with a test that
   reproduces the bug.
2. **`./gradlew test` must pass before a task is done.** "It compiles" is not done.
3. **Never delete, skip, `@Ignore` or weaken a test to go green.** Fix the code.
4. **Do not touch `AndroidManifest.xml`, permissions, foreground services, minSdk or signing.**
   Stop and report instead.
5. **Do not add a dependency, a DI framework or a module.** Stop and report.
6. **Do not reformat, rename or tidy files outside your task.**
7. **Assert on observable behaviour**, not on which collaborator was called.
8. **No hex literals in `panel/`.** `PanelTheme.kt` is the only file allowed to hold colour values.
9. **The lock tile never gets a write action.**
10. **Never cover, delay or suppress the Domonap call screen.**
11. Conventional commits, one concern per commit.

## Environment

No JDK, SDK or `adb` on the system PATH by design; `scripts/env.sh` puts the repo-local toolchain
there and must be sourced in the *same* command as gradle.

```bash
source scripts/env.sh && ./gradlew test
source scripts/env.sh && ./gradlew ktlintCheck
source scripts/env.sh && ./gradlew verifyRoborazziDebug
source scripts/env.sh && ./gradlew recordRoborazziDebug
```

**Every task here changes colour, so `verifyRoborazziDebug` will fail — that is expected and is not
licence to skip it.** Run it, open the diff in `build/outputs/roborazzi/`, satisfy yourself the
change is the one you intended, then `recordRoborazziDebug` and commit the new references in the
same commit.

**Both schemes move together.** `panel-home-dark.png` is the one being judged, but every task edits
a token mapping that light also reads. Check `panel-home-light.png` in the same diff; a task that
fixes dark and wrecks light is not done.

## Order

```
D1 ──> D2 ──> D3
D4  independent, any time
```

- **D1 first.** It decides what the neutral ramp carries; D2 and D3 are shaped by that answer.
- **D4 never blocks anything** and can run in parallel, in a worktree.

Do not run D1–D3 in parallel: they all touch `TileCard.kt` and `TileLayout.kt`.

---

## D1 · `feat(panel): the surfaces stop carrying hue`

**Depends on:** nothing. Do this first.

**Context.** In the reference, **every tile is the same neutral dark grey.** Colour appears in
exactly three places on the whole screen: a green dot for on, a red struck-through wifi glyph for
offline, and the assistant orb. Nothing else is coloured.

On this wall, in `panel-home-dark.png`: the air conditioner is a deep blue field, the strip a dark
amber field, the curtain a mid grey, and **two of the twelve tiles are full saturated red
rectangles** (the offline Бризер and the missing Пылесос). It reads as a patchwork of colour blocks
rather than as a set of tiles, and the two red ones are by a wide margin the loudest thing on the
wall — spending the strongest signal available on "this one is offline".

This is the task that was dropped as T5 in the previous plan, when the blue light filter turned out
to be why the wall looked brown. That was correct and is settled — the filter *was* that problem.
This is a different one: with the filter off and the palette rendering exactly as designed, the
surfaces still carry more hue than the thing being aimed at.

**Files.** `PanelTheme.kt`, `panel/TileCard.kt`, `panel/TileLayout.kt`.

**Do.**
- Tiles sit on the neutral ramp — `surfaceContainer` and its four neighbours — instead of taking
  `primaryContainer` / `tertiaryContainer` / `errorContainer` as a field. `PanelTheme.kt` already
  argues that the neutral family is told apart by lightness rather than hue; extend that answer to
  all three families instead of leaving it as one family's compromise.
- **Hue survives as accent, not as field.** The promoted value, the slider fill, the on indicator,
  the failing outline. A small saturated mark says the same thing a filled card says and does not
  cost the whole surface.
- **Land `docs/design/panel-redesign.md` item 4 in this commit.** A *group's* failure outlines,
  using the existing `groupFailureBorder`; a *tile's own* failure fills. Today one failed
  `/v1.0/user/info` turns roughly 34 of 35 tiles red in a single frame and erases the family coding
  exactly when somebody needs to work out what broke.
- Also settle the `Off`/`Unknown` neutral disagreement recorded at the foot of that doc. It is the
  same question — what the neutral ramp carries — and answering it separately will answer it
  differently.

**Done when.**
- No tile takes a container colour as a full-bleed field.
- The family a tile belongs to is still recoverable from the wall. **Verify on the re-recorded dark
  capture, not by reasoning about tokens.**
- A group failure outlines and does not turn the wall red.
- `test` green, `ktlintCheck` green, both schemes' references re-recorded and looked at.

**Do not.** Do not retune the two seeds or the generated ramps — `docs/ui.md` records them as
decided and this is not a reason to reopen them. Do not remove a value from a tile.

---

## D2 · `feat(panel): a tile says how old it is once`

**Depends on:** D1.

**Context.** The reference says almost nothing under a tile name — `Needs configuring`, in grey, and
otherwise nothing. This wall prints, on one tile:

```
on · 3 min ago · low + medium + high · 3 min ago
26.4 °C · 3 min ago · 41.0 % · 3 min ago
```

**Four timestamps in one paragraph, three of them the same.** The AC prints `on · 1 min ago · 22 °C
· 81 d ago`; the strip prints two ages plus `not controllable`. `CLAUDE.md` requires a tile to say
how old its reading is — it does not require it to say so once per field, and the run-on line is
what makes the wall look busy next to the reference.

D1 removes the colour that was competing for attention; this removes the text that was.

**Files.** `panel/Staleness.kt`, `panel/TileCard.kt`, the tile composables.

**Do.**
- **One age per tile**, the oldest of the readings it is showing, printed once.
- Fresh readings do not need to speak. Give staleness a threshold below which the line says nothing,
  and above which it says one thing. `Staleness.kt` already models the distinction and already
  reaches the headings; it is the wording that is doing too much.
- Keep the promoted value exactly as it is — `22 °C`, `40% open`, `60%` are the best thing on the
  wall today and are not in scope.
- Keep everything the panel is required to be honest about: `not controllable`, `no state to read`,
  `not installed`, and the reason a poll failed. Shorten them; do not drop them.

**Done when.**
- No tile prints the same age twice.
- A tile whose readings are all fresh prints no age at all.
- The staleness rules stay pure functions with their existing tests extended, not moved into
  composables.
- `test` green, `ktlintCheck` green, references re-recorded and looked at.

**Do not.** Do not remove a value or a state the panel is required to report — that is the load-
bearing refusal in *Reference*.

---

## D3 · `fix(panel): nothing on the wall wraps`

**Depends on:** D2.

**Context.** In `panel-home-dark.png`, `81 d ago` wraps to a second line, `not controllable` wraps,
and `com.example.vacuum` breaks mid-word across three lines. The third row of tiles is visibly
shorter than the first two. `docs/ui.md` records "two tiles of the same kind come out the same
height" as the mosaic's proudest property, and T3 gave every kind a reserved-height anatomy — but
the status line is still unbounded, so a long string is the one thing that can still break it.

`docs/design/panel-redesign.md` item 7 covers the vendor-exception half of this (raw Java messages
concatenated onto the line). **Land item 7 here**, in this commit: they are one bug.

**Files.** `panel/TileCard.kt`, `panel/BulbTiles.kt`, wherever the throwable is caught.

**Do.**
- Cap what a status line may occupy so no vendor string can change a tile's height.
- Map the throwable to a small set of reasons the panel is willing to print — unreachable, timed
  out, refused, and one fallback. The exception text goes to `Log`, not to the wall.
- Package names and other identifiers that cannot be shortened get truncated rather than wrapped.
- Row heights agree across the whole grid, not only within a kind.

**Done when.**
- Nothing wraps in either re-recorded capture, at the default font scale.
- The throwable mapping is a pure function with a table test.
- `test` green, `ktlintCheck` green, references re-recorded and looked at.

**Do not.** Do not fix wrapping by shrinking type — D1 of the previous plan raised it deliberately
and the wall is read from four metres.

---

## D4 · `feat(panel): art per device`

**Depends on:** nothing. Independent; can land any time or never.

**Context.** The previous run's T6 enlarged the glyph set to 48 dp and unified it, which was the
cheap third of this. The reference does something different in kind: **photographs of the devices**
— a white bulb, a wall switch, a door sensor, a coil of strip — light objects on dark tiles. You
identify a device by recognising the object, not by decoding a symbol, and it is the largest single
contributor to the reference looking like a product rather than a dashboard.

It matters more after D1 than before it. Once the tiles are all one neutral grey, the art is what
tells them apart at a glance.

**Do.** Three options, in the order they are worth doing. **The first two need assets an agent
cannot produce — for those, report exactly what is needed and stop.**
- **Photograph the actual hardware** on a neutral background. A couple of hours with a phone, and
  more honest than a render: the tile shows the lamp that is in that room.
- **Source renders** for the AC, curtains, bulbs, strips, recuperators, lock, vacuum, intercom —
  with the licence questions that brings.
- **Keep the glyphs.** Already done and already unified; this is the null option and is acceptable.

**Done when.** Every device id resolves to an asset, the set shares one treatment, and the art reads
against the neutral tile D1 produces. `test` green, references re-recorded and looked at.

---

## Reference

### What is being aimed at

The Yandex smart home app, **dark theme**, captured 2026-08-29 23:33. What it does, in the order it
matters:

- **No hue in any surface.** Near-black background, every tile the same neutral dark grey.
- **Three coloured things on the entire screen**: a green dot meaning on, a red struck-through wifi
  glyph meaning offline, and the assistant orb. That is the whole colour budget.
- **Photographic device art**, light objects against dark tiles.
- **Room names as headings**, bold, well above the tiles in weight.
- **One short grey line under a name, or nothing.**

### What is taken and what is refused

Settled. No task re-argues this table.

| Yandex dark | Here | Why |
| --- | --- | --- |
| Neutral surfaces, no hue in tiles | **take** | D1 — the remaining visible difference |
| Colour only as small status marks | **take** | D1 |
| A quiet or absent secondary line | **take** | D2 |
| Photographic device art | **take** | D4, and the expensive one |
| Near-black background | **already have it** | `#111318`, verified against the wall |
| Showing almost no state on a tile | **refuse** | Load-bearing — below |
| Truncated device names | **refuse** | Fine at 30 cm, useless at four metres |
| Small secondary type | **refuse** | Same reason |
| A bottom navigation bar | **refuse** | One screen |

**Why the refusals hold.** Yandex shows nearly nothing on a tile because it is a phone app: opened,
tapped, read, closed. This panel is read *without being touched* — the 22 °C and the 40% open
visible from the hallway is the point, and `CLAUDE.md` requires a tile to say how old its reading
is. D2 makes the secondary line quieter; it does not make it disappear.

**Their colour discipline, this panel's information density.** A task that drops a value to look
more like the reference is wrong.

### What must survive all of this

`mood`, `hue`, `span`, `promoted`, `isStale` and `bulbGroup` as pure functions outside the
composables, and the distinction between when the device reported and when the panel last read it.
Every task is layered on those, not against them.

### Known and out of scope

- **The blue light filter** turns the whole wall warm and brown. Verified 2026-08-29: with it off,
  the wall matches `PanelTheme.kt` exactly. It is a device setting, not a bug, and D1 is not an
  attempt to work around it — though neutral surfaces will survive it better.
- **`docs/design/panel-redesign.md` items 1, 2, 3, 5, 6, 8, 10, 11, 12** are open and are not in
  this plan. Item 4 lands in D1 and item 7 in D3; do not build those two separately.
- **Item 13 there is an ask-first** — always-on, system bars, screen timeout — and stays unbuilt
  until asked. The tablet PIN-locking after two minutes is part of it.

### Related documents

- `docs/ui.md` — what is already decided about the mosaic. Read before changing anything visual.
- `docs/design/panel-redesign.md` — the audit's thirteen items.
- `REPORT.md` — the previous run. **Overwrite it** when this plan is executed; `655a683` keeps it.

## Reporting

On finishing a task, report: what changed, `test` and `ktlintCheck` output (real output, not a
summary), what the Roborazzi diff showed in **both** schemes and why it is the intended change,
anything assumed that wants a walk to the hallway, and anything found that belongs to another task.
