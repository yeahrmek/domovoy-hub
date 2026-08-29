# REPORT — executing PLAN.md on `feat/panel-shape`

Run 2026-08-29. T1 → T2 → T3 → T4 in order, T6 alongside. T5 not built. `PLAN.md` unmodified.

## What landed

Six commits on `feat/panel-shape`, branched from `main` at `e84388a`, not pushed.

| | Commit | Task |
| --- | --- | --- |
| | `bea50c2` | `docs(panel): the plan for the panel's shape` — see *Assumptions* |
| T1 | `d6140ab` | `feat(panel): a typography for wall distance` |
| T2 | `c6ccb93` | `feat(panel): rooms stack instead of tabbing` |
| T3 | `4664e2c` | `feat(panel): one tile anatomy` |
| T4 | `b04fed7` | `feat(panel): the lamp row becomes one group tile` |
| T6 | `f91da94` | `feat(panel): the glyphs are drawn at the wall's size` |

No task was squashed into another. Every commit carries its own tests and its own re-recorded
Roborazzi references.

**T1** — `panelTypography` in `PanelTheme.kt`, passed from `MainActivity` in the same call as the
scheme. `bodySmall` no longer appears as a status line in `panel/`. One promoted value per tile,
decided by a pure `promoted()` beside `hue` and `span`: AC → target, curtain → open percent, strip →
brightness, recuperator → temperature, bulb and launcher → null.

**T2** — `PanelTabs.kt` → `PanelHeadings.kt`; one `LazyVerticalGrid` with full-width headings, rooms
as sections down one scroll. `PrimaryScrollableTabRow` is gone. The room mark moved onto the heading.
Главная keeps its job. The `LazyGridState` is no longer keyed on the tile count — it is hoisted to
`MainActivity` and the top is asked for on exactly two events (idle reset, first poll after a
reboot), neither of which can fire under a hand. Resolves `panel-redesign.md` item 9.

**T3** — `TileCard.kt` is the single anatomy: art, controls, slider, promoted value, name, status
line, each with a reserved height, so an empty slot stays empty instead of re-flowing. One radius
(22 dp), one padding, 328 dp minimum height across every kind. 12-column grid, thirds and quarters
(251 dp / 188 dp) replacing halves and thirds of six. Sliders 6 dp → 20 dp.

**T4** — **the row collapses into one group tile that opens the seven** (the second of the two
options). `BulbCircles`/`BulbCircle` deleted. The closed tile carries both counts and the oldest
reading, so nothing readable is behind the tap; what the tap opens is *which lamp is which*, which
the discs never showed at any number of taps. `bulbGroup` survives and gained work.

**T6** — `GLYPH_SIZE` 24 dp → 48 dp, one number for the whole set. `TileGlyphTest` asserts the
laid-out height against a literal 48 dp, not against the constant.

## Gates

Run at **each commit independently**, with `--rerun-tasks` so nothing came from cache:

```
source scripts/env.sh && ./gradlew test ktlintCheck verifyRoborazziDebug --rerun-tasks
```

| Commit | `test` | `ktlintCheck` | `verifyRoborazziDebug` | tests | failures |
| --- | --- | --- | --- | --- | --- |
| `d6140ab` T1 | green | green | green | 261 | 0 |
| `c6ccb93` T2 | green | green | green | 266 | 0 |
| `4664e2c` T3 | green | green | green | 275 | 0 |
| `b04fed7` T4 | green | green | green | 291 | 0 |
| `f91da94` T6 | green | green | green | 294 | 0 |

`BUILD SUCCESSFUL` on all five; counts summed from `app/build/test-results/testDebugUnitTest/*.xml`.
Every commit's committed references match its own code — the branch can be bisected without a
screenshot failing on an unrelated commit.

Branch tip, clean build:

```
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 17s
43 actionable tasks: 15 executed, 25 from cache, 3 up-to-date
```

No test was deleted, skipped, `@Ignore`d or weakened; the count only goes up. No
`AndroidManifest.xml`, permission, `minSdk`, signing or dependency change — `git diff main..HEAD`
touches `app/src/`, `docs/` and `PLAN.md`/`REPORT.md` only.

## Roborazzi — what visibly changed, and why it is intended

A re-recorded reference proves nothing on its own. Each capture below was looked at before the
record, and again after.

**T1.** Same palette to the byte — the `tiles-*` matrices are the same twelve fills and the same
outline. Same six-column grid, same spans, same corners. What is new: a 44sp value on the curtain,
both strips and the reporting recuperator where there was none, and every status line legibly
larger. *Intended:* T1 is a type change and a promoted-value change and must show as exactly those
two things and no third.

**T2.** The tab strip is replaced by `Главная` as a 52sp heading; the tiles under it are
pixel-identical, and `tiles-*` and `lights-group` did not change at all. `Коридор`'s heading now
appears at the bottom edge with the scroll continuing past it. `tabs-marked.png` → `headings.png`.
*Intended:* the shell moved and the tiles did not — T3 owns tile colour and size, and this proves T2
did not reach into it. The `Коридор` heading below the fold is the vertical-fill claim made visible.

**T3.** Three columns where there were two. Bottom edges now agree **across kinds**, not only within
one — AC, curtain and strip end on one line in row 1; strip, recuperator and the failing recuperator
in row 2. The sliders read as grabbable rather than as decorative rules. *Intended:* this is the
whole of T3. The visible cost is that `Бра` — a narrow bulb tile with no slider and no promoted
value — now shows a large empty band. That is the anatomy working as specified ("a tile with nothing
for a slot leaves it empty rather than re-flowing"), not a regression, and it is what buys the
aligned edges.

**T4.** The full-width disc row is gone from the foot of Главная; in its place a quarter-width
`5 lamps / 3 on / tap to see them` card sits in the mosaic beside the launcher tiles, and the last
row's four bottom edges align. `tiles-*` and `headings` did not change at all. *Intended:* the
amber that used to be seven discs and the most saturated thing on the wall is now one tile among
tiles, which was the point — the eye no longer lands first on the thing that says least.

**T6.** Every glyph is twice the size. Nothing else moved: same colours, same spans, same words,
same sliders, same switches, **same tile heights and same bottom edges**. *Intended:* T3's art slot
already reserves 64 dp, so 48 fits where 24 sat and no tile grows — which is precisely the check
that says T6 is an art change and not a layout change.

## On the tablet — 2026-08-29 23:29

Added after the fact. The five task runs above were **JVM-only**; Roborazzi renders through
Robolectric and is a layout and palette check, not a picture of the Galaxy Tab. `installDebug` was
then run on the wall tablet (SM-T875, `R52RC042MSH`) and the panel captured with `screencap` at
23:29 and scrolled to its end. Three captures, in `build/` (gitignored, not committed).

A screencap answers layout questions. It cannot answer legibility at four metres — that still needs
a person in the hallway, and the T1 and T6 assumptions below stand unmeasured.

### Confirmed on glass

- **The anatomy survives real Roboto.** The failure I called most likely — the tablet wrapping a
  status line one line further than Robolectric, so heights stop agreeing — **did not happen**.
  Bottom edges align within every row in all three captures.
- **Real device names are longer than the fixtures and they fit.** `Кондиционер в зале`,
  `Подсветка в зале`, `Детская ванная` on one line; `Бризер данина комната` wraps to two rather than
  clipping.
- **T2's acceptance criterion holds on the wall.** The scroll reaches `Без комнаты`, and `Гардероб`
  and `Кабинет` — two of the rooms that were off the end of the tab strip entirely — are reachable
  and unclipped.
- **48 dp glyphs render and read**; no tile grew for them, as the JVM capture predicted.
- Vertical fill: content runs the full height with the next room's heading below the fold.

### The blue light filter is back on, and that is T5's premise returning

`settings get system blue_light_filter` → **1**, at 23:29, inside the 19:00–07:00 dark window.
Sampled off the capture:

| | `PanelTheme.kt` asks | on the wall tonight | T0's filter-on capture |
| --- | --- | --- | --- |
| background | `#111318` | `#402F13` | `#402F13` |
| `surfaceContainer` (AC, off) | — | `#4A3A1D` | `#4A3A1D` |
| light container (lit) | `#663E00` | `#845200` | `#845200` |

**Byte-identical to the numbers T0 measured with the filter on.** The wall is one brown; background,
neutral container and lit amber differ by lightness alone.

This matters because of *how* T5 was dropped. T0 turned the filter off, saw the palette land exactly,
and concluded the palette was never wrong — which is true. But the filter's standing state on this
tablet is **on**; that is a measured fact from 2026-08-16, and it is 1 again now. T0 wrote its own
condition: _"If the filter goes back on at night, the panel needs a palette that survives it — that
is the same work T5 described, and it should be re-opened deliberately rather than assumed."_ That
condition is met tonight.

**I have not built T5 and did not touch it** — it is dropped and I was told not to. Nor did I change
the filter: T0 is explicit that an agent must not change device display settings. This is a report,
and re-opening T5 is a deliberate decision that is not mine to make.

### The blue family is still unverified on the wall — third capture running

Every climate tile in all three captures is `off`: the air conditioner, and all four `Бризер`. So no
`primaryContainer` was drawn tonight either. T0 already flagged this about its own two captures
(_"No climate tile was on in either capture, so the blue family is still unverified on the wall"_),
and that remains exactly true. Whatever is decided about the filter, **nobody has yet seen a lit blue
tile on this tablet.**

### New, from real content rather than fixtures

- **Single-tile rooms waste most of the width.** A room whose only device is one lamp gets one 188 dp
  tile and ~565 dp of empty wall beside it, and most of the fourteen rooms are that. T2 fixed the
  empty *bottom half*; on real data a horizontal version of the same problem is now the dominant
  visual. Neither T2 nor T3 owns it — it is a question about what a one-device room should look like.
- **`0 on` at 44sp** is what a room whose single lamp is off promotes. It is a lot of wall for that
  fact, and it is the promoted-value rule working exactly as T1 specified.
- **The redundancy I flagged is starker on real content** than in the fixtures: `16 °C` above
  `off · 14 h ago · 16 °C · 14 h ago`; `1 on` above `1 lamp · 1 on · never read`.
- **An air conditioner that is `off` still promotes its target, `16 °C`.** Deliberate — `promoted()`
  does not move with `mood`, so a tile keeps its last value — but a setpoint for a unit that is off
  reads as a temperature the room is at. Worth a decision.
- The panel is not immersive: Samsung's status bar and navigation bar frame it. Pre-existing, not
  touched by this work.

## Assumptions

- **`PLAN.md` was committed first** (`bea50c2`). It arrived untracked, and the T6 agent ran in an
  isolated git worktree, where an untracked file does not exist. Committing it was the only way it
  could read its own task. The file's contents are unmodified.
- **The wall type scale is reasoned, not measured** (T1). Nobody has read this panel from four
  metres. The arithmetic is in `panelTypography`'s KDoc: 4 m taken literally wants ~157sp, which is
  five characters across the whole wall, so the panel is treated as a two-distance object — promoted
  value and name for the walk-past, status line for arm's length. **This wants a walk to the
  hallway.** Take the promoted value; if 44sp is short, the scale moves at the top, not at the floor.
- **48 dp glyphs are reasoned, not measured** (T6) — it is the size the disc's lamp was already
  defended at, which is the best-supported number available and still not a measurement.
- **328 dp tiles and the four-line status reserve are computed from Robolectric's text layout**, not
  the tablet's (T3). If Roboto on the actual tablet wraps one line further than Robolectric does,
  that tile grows and the bottom edges stop agreeing. This is the single most likely way this work
  fails on glass.
- **20 dp slider track** unmeasured on the tablet (the 6 dp it replaces had been measured at 6.1).
- **Density is a judgement nobody has made standing in front of the wall** (T3): ~3.5 rows now fit.
- **`tap to see them` as an affordance** is unverified at wall distance (T4). There is no chevron —
  that needs new vector artwork or a transitive icon dependency, and a dependency is an ask-first.
- **Heading contrast through the blue light filter is unmeasured** (T2). `docs/ui.md` has a real
  on-glass number for the marked *tab* at 14sp (4.3:1, marginally under WCAG); a 52sp bold heading
  is the same two colour roles at a different size, so that number does not carry over.

## What I skipped, and why

- **T5 — dropped, as instructed.** Not built, not started, no agent given it. **But see "On the
  tablet": the filter is on again tonight and the wall reads as one brown, which is the exact
  condition T0 attached to its own answer.** T5 stays dropped until somebody re-opens it
  deliberately; this report only records that the state it was dropped on is not the state the
  tablet is in.
- **T0** is not an agent task and was already answered.
- **T6's first two options were reported and stopped, per the task's own instruction.** Photographing
  the hardware and sourcing renders both need assets an agent cannot produce (and the second brings
  licence questions). The third option — keep glyphs, unify them — is what landed, and it landed
  **half**: the size is unified, the *family* is not, and that is a blocked judgement rather than
  unfinished work. `docs/ui.md` had pre-committed to "move the other seven to Tabler, not the bulb
  back"; measured against Tabler 3.31.0's 4,936 outline icons, that direction **does not exist** —
  no covering icon at all, so the curtain would lose the open/closed pair that is the one glyph here
  carrying state, and nothing for a light strip. The alternative reverses the recorded reason the
  bulb is Tabler's (every mockup was drawn in Tabler). That is the mockups' owner's call and is now
  in `docs/ui.md` under *Icons* and *Open*.

Nothing was stopped for a failing test, a manifest change or a new dependency. The stop conditions
did not fire.

## Corrections to PLAN.md's premises

Not applied — `PLAN.md` is unmodified — but worth recording:

- **T6 says "nine drawables"; there are eight.** `ic_bulb_filled.xml` went with the disc change in
  `a535dc0`, which `docs/ui.md` already records.
- **T6 says "a filled bulb glyph in the disc row"** — the disc's lamp was the *outlined* `ic_bulb`;
  the filled one is the file that was deleted.
- **T6's "mixed weight" does not hold.** Material Symbols outlined at weight 400 strokes 80 of the
  960 grid, which is 2 of 24 — Tabler's `stroke-width` exactly. What actually differs is terminals
  (Tabler's round caps and joins against Material's square), and it is subtle.
- **T2's "47%" vertical fill** is the pre-T1 tablet figure. The committed pre-T2 reference composed
  to 1103 dp of 1204 because the grid sized to its content; the fresh capture reaches the bottom
  edge with the next room's heading below the fold. Materially above it on either reading.

## Found on the way, belongs to another task

- **`panel-redesign.md` item 7 — the unbounded vendor error string — is mitigated but not closed.**
  T3's status reserve absorbs the normal case and its recuperator span change absorbs the worst one
  the flat has, but a long enough error still grows one tile past the others. Turning the reserve
  into a guarantee is that item's "cap what a status line may occupy".
- **`panel-redesign.md` item 6** (the unlit disc's tone) is **moot** — there is no `BulbCircle` any
  more. T4 marked it so rather than settling the underlying `Off` vs `Unknown` neutral question,
  which stays open for every tile at once.
- **item 10's bulb half resolved itself**; its remaining question is sharper — the group tile took
  the launcher's whole-card-is-tappable rule without anyone deciding that is the rule.
- **The idle reset does not close an open lamp group** (T4). Recorded as deliberate in `PanelRooms`
  and `ui.md`, but it is a bet; it wants `openLamps` hoisted to `MainActivity` beside the scroll if
  the wall says otherwise.
- **The promoted value repeats itself in the status line** on several tiles — `40% open` appears
  twice on the curtain, `60%` twice on the strip, `3 on` twice on the lamp group. `promoted()` is
  deliberately the only formatter for each, so the two cannot drift; whether the demoted copy should
  then be dropped from the line is a question T1 did not open and no later task owns.
- **`Flat.kt` has never exercised the empty promoted slot.** No fixture has an AC with a null target,
  a curtain with no position or a strip with no brightness, so the null branch is covered by
  `TileLayoutTest` and has never been drawn.
- **`docs/ui.md`'s History table** has no rows for this work. T1 set that precedent and the later
  tasks followed it rather than diverging mid-plan.
