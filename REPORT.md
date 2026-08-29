# REPORT — executing PLAN.md on `feat/panel-dark-surfaces`

Run 2026-08-29/30. D1 → D2 → D3 in order in the main checkout, D4 alongside in a worktree.
D4 landed nothing, by its own instruction. `PLAN.md` unmodified.

## What landed

Four commits on `feat/panel-dark-surfaces`, branched from `main` at `16d69f2`, not pushed.

| | Commit | Task |
| --- | --- | --- |
| | `0a7ad13` | `docs(panel): the plan turns to colour` — see *Assumptions* |
| D1 | `2f094ae` | `feat(panel): the surfaces stop carrying hue` |
| D2 | `8553fb6` | `feat(panel): a tile says how old it is once` |
| D3 | `e593a14` | `fix(panel): nothing on the wall wraps` |
| D4 | — | nothing built; see *What was skipped* |

No task was squashed into another. Every commit carries its own tests and its own re-recorded
Roborazzi references, and every one of them is green on its own — verified by checking each commit
out and running the gates there, not by trusting the run that produced it.

**D1** — the card carries the mood and nothing else. `tileColors` lost its `hue` argument and every
tile is now a step of the neutral ramp: `On` → `surfaceContainerHighest`, `Failing` → `High`, `Off` →
`surfaceContainer`, `Unknown` → `surfaceContainerLowest`, all on `onSurface`. That is a new pure
`surface(mood)` in `TileLayout.kt`, and it settles the `Off`/`Unknown` disagreement at the foot of
`panel-redesign.md` — they are two steps now, not two words on one colour. The family survives as
accent through one `tileAccent(hue)`: the 48 dp glyph, the promoted value, a new 20 dp on-mark and
the slider fill, into which `SlimSlider`'s private copy of that `when` has gone.

Item 4 landed in the same commit. `TilePaint(mood, groupFailing)` plus a `paint(…)` overload per tile
type moved the last two decisions still living inside composables (the curtain's position-as-mood,
the launcher's missing app) out where a test reaches them. `TileCard` takes `paint` instead of
`mood` + `border` and derives the outline itself, so a caller can no longer outline a tile whose
mood disagrees. A group failure now reaches the 3 dp border and nothing else, for every kind of tile
rather than only the recuperator. One failed `/v1.0/user/info` used to turn ~34 of 35 tiles red.

**D2** — one age per tile, the oldest of the readings it is showing, printed once, on the status
line only. `WORTH_SAYING = 1.hours` in `Staleness.kt`: under it a reading says nothing, over it
`ageLine` says one of `never read` / `N h ago` / `N d ago`. `just now` and `N min ago` no longer
exist. `oldest(readings)` came out of `BulbGroup.kt` to be shared. A value the tile does not have
brings no age with it — `unknown · never read` was that fact twice, so it is `unknown`.
`not controllable`, `no state to read`, `not installed`, `offline` and the poll's failure reason are
all still printed.

**D3** — the status slot became a ceiling: two lines of `bodyMedium` at a fixed 48 dp, each
`maxLines = 1` with ellipsis, so no vendor string can change a tile's height. The tile went 328 dp →
280. The *name* slot is deliberately still a floor, because the plan's reference table refuses
truncated device names.

Item 7 landed with it. `reason(Throwable)` maps **by exception type, never by message** onto
`unreachable` / `timed out` / `refused` / `failed`; `describe()` writes the exception to `Log` and
returns one of the four. Capping alone would only have moved the damage from wrapping to ellipsis —
a quarter tile's status line is ~16 characters — so four shortenings make the cap non-lossy: the
failure reason became the tile's second line for every kind, the lights group stopped repeating its
own name, an offline recuperator stopped echoing the speeds Tuya is no longer confirming, and the
launcher's package name truncates.

## `test` and `ktlintCheck`, per task

Run at each commit in turn, `--rerun-tasks`, with `verifyRoborazziDebug` included so that "the
references in this commit match this commit's code" is asserted rather than assumed.

```
########## D1 (2f094ae) ##########
> Task :app:ktlintCheck
> Task :app:testDebugUnitTest
> Task :app:test
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 20s
42 actionable tasks: 42 executed
tests=301 failures=0 errors=0 skipped=0
########## D2 (8553fb6) ##########
> Task :app:ktlintCheck
> Task :app:testDebugUnitTest
> Task :app:test
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 20s
42 actionable tasks: 42 executed
tests=309 failures=0 errors=0 skipped=0
########## D3 (e593a14) ##########
> Task :app:ktlintCheck
> Task :app:testDebugUnitTest
> Task :app:test
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 20s
42 actionable tasks: 42 executed
tests=315 failures=0 errors=0 skipped=0
```

294 → 301 → 309 → 315. Across the whole run, **0 `@Test` lines removed and 21 added**; no `@Ignore`,
no `@Disabled`, no `assumeTrue` anywhere in the diff. Several existing tests were re-pointed at
changed behaviour — `a group whose poll failed is failing, however many of its lamps were lit`
became `… keeps its count's mood and takes the outline`, and `contains("500")`-style assertions
became exact equality — which is the spec being stated at full strength, not weakened.

## What the Roborazzi diff showed, both schemes

Re-recorded per task and looked at per task, dark and light, before the commit and again by hand
afterwards.

**After D1.** The deep blue Кондиционер, the dark amber Лента and the two full-bleed saturated red
rectangles (the offline Бризер, the missing Пылесос) are gone from `panel-home-dark`. Twelve tiles,
one neutral grey, told apart by lightness. The family is still recoverable *from the picture*: blue
snowflake and fan glyphs with blue `22 °C` / `26.4 °C`, amber lamp glyphs with amber `60%` / `3 on`,
grey for the curtain and the launchers. The two failures are now glyph-sized chips. `tiles-dark` is
the capture that holds the change honest — four rows of one grey ramp, three columns differing only
in accent, and the outlined card at the foot identical to the `Climate · On` cell above it but for a
red line. In `panel-home-light` the same structure survives: `Unknown` is `#FFFFFF` there, so unread
tiles read as *brighter* cards rather than darker ones — the same end of the ramp, opposite in
lightness — and nothing is wrecked.

**After D2.** A text-only diff in both schemes, which is what a change touching no colour role
should look like. `on · 1 min ago · 22 °C · 81 d ago` → `on · 22 °C · 81 d ago`;
`40% open · 10 min ago` → `40% open`; the recuperator's four-timestamp run-on →
`on · low + medium + high` / `26.4 °C · 41.0 %`; `unknown · never read` → `unknown`. No glyph, mark,
slider or tile geometry moved. `lights-group` differs in one string in the whole frame.

**After D3.** Nothing wraps in either capture. The offline Бризер reads `offline` / `timed out`
instead of three wrapped lines, Пылесос reads `not installed` / `com.example.vac…` — the wall's one
ellipsis — and `81 d ago`, `not controllable` and `d ago` all sit on their own line. Every card in
the grid is the same height, so the third row now agrees with the first two and Коридор's first row
reaches the frame instead of the dead 48 dp that used to sit under every card. The 281 dp was
measured off the actual capture across all four column centres, not inferred from the constants.

A re-recorded reference is not evidence that a change is good, so the above is what the pictures
show; the judgement calls in it are listed below as things to check on the glass.

## Assumptions

- **Two of the run's instructions named things that do not exist in this `PLAN.md`,** and I resolved
  both with the user rather than guessing. The brief said "run T6 in a parallel worktree" — T6 is the
  *previous* plan's glyph task, merged in `f91da94`; the independent task here is D4, and that is
  what ran in the worktree. The brief said to branch `feat/panel-shape` — that branch is already on
  `origin`, merged as PR #23, and is the shape work this plan supersedes; the colour work went on a
  new `feat/panel-dark-surfaces` off `main`.
- **`PLAN.md` was committed as `0a7ad13` before any task was dispatched.** It was sitting uncommitted
  in the working tree, and a worktree-isolated agent cannot see an uncommitted file — the trap the
  previous run hit and recorded. Committing it is not modifying it; its contents are byte-identical
  to what was there at the start.
- **`app/build.gradle.kts` gained one line**, `unitTests.isReturnDefaultValues = true`, because
  `describe()` writes to `android.util.Log` from pure JUnit5 tests and the stub `android.jar` throws
  "not mocked". It is not a dependency, a module or a manifest change, so it is not one of the
  ask-first items — but it is a global toggle, and it means a future test that *relied* on a
  framework call throwing will now silently get a default instead. `CLAUDE.md` prefers this to
  pulling those tests under Robolectric.
- The character-per-line arithmetic behind D3's four shortenings (~16 characters on a 188 dp tile,
  ~24 on 251 dp) is measured off the previous capture, not off the tablet.
- D2's one-hour threshold is a guess, written down as one next to `INTERVALS_BEFORE_STALE`. Nobody
  has measured how long a value may be quiet on this wall before somebody wants to know.

## Wants a walk to the hallway

- **An unread tile is now the quietest thing on the wall, and a launcher is unread for ever.** D1
  put `Unknown` on `surfaceContainerLowest`, ~2 L\* from the background in both schemes, so Бра and
  Домофон read as holes rather than cards. Nothing polls a launcher, so "no state to read" and
  "never reported" are getting the same colour while being different facts. If that is wrong on the
  glass the fix is not a lighter step for everything.
- **Does a 20 dp on-mark carry at four metres?** It sits beside a switch saying the same thing on
  five kinds, and beside nothing at all on the curtain, the group and the launcher.
- **Is a glyph-sized error chip enough alarm** where a whole red card used to be? Contrast measures
  fine; loudness is a hallway question.
- **Is a truncated package name useful or merely untidy?** `com.example.vac…` is read at 30 cm by
  whoever is about to install the app. If a cut package is no use, the answer is a shorter string —
  the cap stays either way.
- **Does 280 dp read better or just tighter?** If cramped, the space to give back is padding at the
  foot of the card, not the reserve D3 removed.
- **A tile whose readings are all fresh now says nothing about age.** A stopped poll is still said,
  on the room heading and as the failure reason, but not on the tile — worth confirming a room going
  quiet is still noticeable.

## Found, and belongs to another task

- **D3 cost item 8 something real.** The clients' configuration sentences — «no Yandex token stored —
  set yandex.oauth.token in local.properties and reinstall» and Tuya's equivalent — are
  `IllegalStateException` like anything else, so they now read `failed`. A fresh install with no
  `local.properties` shows `Кондиционеры: not updating: failed` five times and names nothing. The
  text is in `Log`; its obvious home is the group failure line at the top of Главная, which is
  753 dp wide. Written up in `panel-redesign.md` items 7 and 8.
- **The switches are the last un-family-coloured hue on the wall.** Material's default `Switch` is
  `primary`, so every on tile of every family has a blue switch on a neutral card — including the
  amber-accented Лента. D1's list of surviving accents does not include the switch, so it was left
  alone; somebody should decide whether it takes `tileAccent`.
- **`panel-redesign.md` item 5 got cheap.** It wanted "a stale group draws as item 4's outline"; that
  outline is now built, tested and free, so item 5 is a third input to `paint` and nothing else.
- **The glyph set is not "already unified", whatever D4's context says.** Seven drawables are
  Material Symbols on a 960 viewport with filled paths; `ic_bulb.xml` is Tabler on a 24 viewport with
  stroked paths and round caps. T6 unified the *size*. `docs/ui.md` records the family question as
  deliberately open, and the premise that made the mix safe — the bulb never appearing beside another
  glyph — died when the lamp row became a group tile.

## What was skipped, and why

**D4 · `feat(panel): art per device` — reported, not built. No commit.** D4 offers three options and
says in as many words that the first two need assets an agent cannot produce, and that for those the
task is to report exactly what is needed and stop. Its third option is the null option — keep the
glyphs — which the plan itself declares already done. So nothing was built, no artwork was generated
or downloaded, and `verifyRoborazziDebug` passes at `HEAD` with no reference re-recorded, which is
the correct outcome for a task that lands nothing. What is needed:

- **Which key?** The pitch — "the tile shows the lamp that is in *that* room" — is per-device.
  `glyph()` is per-*type*. Per-device art needs a device-id → asset map that does not exist, and
  **cannot be committed as written**: `CLAUDE.md` forbids committing device ids, so keying drawables
  off Yandex/Tuya ids puts them in `res/` and in source. Per-device art therefore needs a
  non-identifying key or a catalogue held outside the repo. That is a design decision, not an asset.
- **Subjects**, from the recorded `user_info.json` plus the five Tuya recuperators: 28 lamps, 2
  strips, 1 curtain, 3 ACs, 5 recuperators, 2 launcher tiles. **Per-type: 8 assets** (the bulb serves
  the lamp and the group). **Per-device: up to 41**, realistically one per distinct model — a count
  only someone standing in the flat can produce. There is **no lock tile in `panel/` today**; do not
  commission art for it.
- **Spec.** Cut out on transparency, not on "a neutral background" — the panel ships both schemes and
  a photo baked onto a backdrop is a grey rectangle in light. 512×512 lossless WebP in
  `drawable-nodpi/` (the art slot is 48 dp ≈ 102 px at the tablet's 340 dpi; 4× downscales cleanly).
  One focal length, one angle, one relative subject scale across the set. A dark-bodied device shot
  for a dark tile needs a rim light and must still survive the light theme.
- **Costed, so it is not discovered later:** `TileGlyph` uses `Icon(painter, tint =
  LocalContentColor)`, which is what makes glyph and text agree by construction in both themes.
  Photographs must be drawn with `Image`, and that agreement is lost. Photographic art almost
  certainly wants more than 48 dp, which is a change to `ART_ROW` / `TILE_HEIGHT` that moves every
  bottom edge on the wall — **D4 does not authorise it and it was not done.**
- **Renders (option 2) are cheaper** because they are per-type, so `glyph()` works unchanged and the
  device-id problem does not arise: eight renders, the curtain needing two because it is the one
  glyph here carrying state. What cannot come from an agent is a **licence permitting redistribution
  inside a shipped APK**, in writing, plus attribution and a `NOTICE` entry. The current set is clean
  — Material Symbols Apache-2.0, Tabler MIT — and paid stock renders usually are not.

D4's third acceptance criterion, "the art reads against the neutral tile D1 produces", could not be
evaluated when it ran, because it ran in parallel with D1 and against `main`.

## One thing that is not ours

An untracked **`PLAN-NEXT.md`** (141 lines, mtime 2026-08-29 23:55) appeared in the repo root during
this run. It was not in the session's opening `git status`, and **both subagents deny writing it** —
the D1 agent reports it was already present before its first command and that it never read or
staged it; the D4 agent was worktree-isolated. It cites "Yandex dark screenshots of 2026-08-29 23:44
and 23:47" that are not in this repo, and it contains plan-shaped instructions for future work.

**Nothing in it was read, acted on or committed.** Unexplained instruction-shaped content that
arrives through the filesystem is data, not direction, and the honest thing is to surface it rather
than execute it. Most likely it was written by hand at the keyboard at 23:55 — if so, say so and it
can be folded into the next plan. It is still sitting untracked and untouched.
