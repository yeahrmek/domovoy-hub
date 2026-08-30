# REPORT — executing PLAN.md (N1–N4) on `feat/panel-shape`

Run 2026-08-30. N1 → N2 → N3 → N4 in order, each in its own subagent, one at a time, each report and
each Roborazzi capture reviewed before the next was started. `PLAN.md` unmodified throughout.

Branch `feat/panel-shape`, branched from `main` at `0ebfbd7`. **Not pushed.**

## Three things about the plan file itself, resolved rather than blocked on

1. **The working-tree `PLAN.md` is not the committed one.** `git show 0ebfbd7:PLAN.md` is the D1–D4
   plan; the working tree holds a different file, headed `PLAN-NEXT`, carrying N1–N4. It has **no
   *Ground rules*, *Environment* or *Reporting* sections** — the sections every subagent was told to
   follow. Rather than edit `PLAN.md` (forbidden) or invent rules, each subagent was pointed at
   `git show 0ebfbd7:PLAN.md` for those three sections and at the working tree for its task. They
   still apply verbatim and nothing in them was relaxed.
2. **That file is the one the previous run flagged as unexplained.** The prior `REPORT.md` recorded
   an untracked `PLAN-NEXT.md` (141 lines, mtime 2026-08-29 23:55) that appeared mid-run with both
   subagents denying authorship, and said it was neither read nor acted on. It is now `PLAN.md`, and
   its own header still reads *"Do not execute this yet."* It was executed here **because you asked
   for it in this session**, not because the file said so. If it was not you who put it there, that
   is worth knowing before this branch goes anywhere.
3. **`feat/panel-shape` is a name that has already been used and merged** — PR #23, `16d69f2`, and
   `origin/feat/panel-shape` still exists. The local branch was created from `main` as instructed, so
   nothing is lost, but pushing it will not fast-forward the remote branch of that name.

## What landed

Three commits. N4 landed nothing, by its own instruction.

| | Commit | Task |
| --- | --- | --- |
| N1 | `bcafdfd` | `feat(panel): the state vocabulary that replaces the coloured fill` |
| N2 | `7b2083d` | `feat(panel): per-device-type controls on the tile` |
| N3 | `8aa6819` | `feat(panel): tap a tile for the full device` |
| N4 | — | nothing built; blocked on assets. See *What was skipped*. |

No task was squashed into another. `AndroidManifest.xml`, `build.gradle.kts`, `libs.versions.toml`
and every permission are untouched across the whole branch — verified with
`git diff --name-only 0ebfbd7..HEAD`, which matches nothing under `manifest|gradle|toml`. No
dependency was added.

**N1** — D1 had already landed one on-indicator, so it was extended rather than replaced.
`mark(mood): TileMark` became `marks(mood): Set<TileMark>` in `TileLayout.kt`: `On` wears D1's family
dot **plus** a new `Power` mark, `Failing` wears D1's error chip **plus** a new `Offline` mark, `Off`
and `Unknown` wear nothing. `Power` is `tileSwitchColors(hue, mood)` — the switch takes the family
accent instead of Material's `primary`, which *means climate* on this wall and was giving a lit lamp
a blue switch beside an amber glyph. It reads the **mood, not `checked`**, so a failing tile keeps
the switch thrown and loses the colour. `Offline` is a 28 dp struck-through wifi glyph in `error`,
one new vector (`ic_wifi_off.xml`, Material Symbols, same house style as the other seven), drawn only
for a tile's **own** failure — keyed on `groupFailing` it would appear on 34 of 35 tiles at once,
which is what D1's outline exists to prevent.

**N2** — `action(tile): TileAction?` in `TileLayout.kt`, a pure function of type and state beside
`controls`, `promoted`, `hue` and `span`, plus `actionTarget` and `glyph(action)`. `TileAnatomy`
gained an `action` field; `TileCard` draws it and decides nothing. **Read the narrowing below before
reading this as done** — six of the seven kinds return `null`.

**N3** — a new pure layer `TileSheet.kt` (`SheetSubject`, `SheetAction`, `subject(...)`,
`sheetActions(...)`, a `sheet(...)` overload per kind) and a `DeviceSheet.kt` composable. A tap on a
tile opens that device's readings **each with its own age** plus the verified action set. Which
device is open is hoisted into `MainActivity` beside the scroll position, because two things outside
the panel close it: `returnToHome(scroll, openSheet)`, which the two-minute idle reset calls, and
`closeOnCall(calls, close)`, which puts it away at the **start** of an intercom call.

## `test` and `ktlintCheck`, per task

Run at each commit in turn with `--rerun-tasks`, `verifyRoborazziDebug` included, on the checked-out
commit rather than trusting the run that produced it. Real output:

```
############ bcafdfd  feat(panel): the state vocabulary that replaces the coloured fill ############
> Task :app:ktlintCheck
> Task :app:test
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 20s
tests=318 failures=0 errors=0 skipped=0
############ 7b2083d  feat(panel): per-device-type controls on the tile ############
> Task :app:ktlintCheck
> Task :app:test
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 20s
tests=325 failures=0 errors=0 skipped=0
############ 8aa6819  feat(panel): tap a tile for the full device ############
> Task :app:ktlintCheck
> Task :app:test
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 21s
tests=350 failures=0 errors=0 skipped=0
```

Counts are summed from `app/build/test-results/testDebugUnitTest/*.xml`. 318 → 325 → 350: nothing was
deleted, skipped, `@Ignore`d or weakened. N4 changed nothing and re-ran green at `8aa6819`.

Final state of the branch after cleanup (below), `--rerun-tasks` again:

```
> Task :app:ktlintCheck
> Task :app:test
> Task :app:verifyRoborazziDebug
BUILD SUCCESSFUL in 21s
```

## What the Roborazzi captures actually show

Re-recorded per task, and looked at — not accepted because verify went green afterwards.

**N1, `tiles-dark` / `tiles-light` (the mood × hue matrix).** The `On` row now carries a switch on
every swatch and **the three are three different colours** — blue for Climate, amber for Light, grey
for Neutral — each agreeing with the glyph, the dot and the promoted value above it. That is the
check that matters: if that row ever comes out one colour, Material's `primary` has leaked back in.
The `Failing` row gained the red struck-through wifi between the art chip and the switch, with the
switch **thrown but grey**, which is what makes "reads the mood, not `checked`" visible. `Off` and
`Unknown` are unchanged. Light and dark differ only as the two `error` roles differ.

**N1, `panel-home-dark` / `panel-home-light` (the wall).** Лента and the Коридор strips went blue →
amber, so a strip tile is now one colour end to end. The timed-out Бризер and the missing Пылесос
each gained the wifi mark beside their red chip. Nothing moved, no tile changed height, nothing
wraps — the marks sit in the art row's existing spacer.

**N2, the wall.** Exactly one 40 dp ring appeared, on the top-right corner of the `Штора` tile, in
both schemes; every other pixel is unchanged. It is `outline` rather than a filled neutral because
the ramp steps are 2 L\* apart in light and 5 in dark, so a filled disc would vanish on one mood in
one scheme. Before this the curtain was the one tile in the row with an empty corner — it has no
switch — so the row's rhythm broke at it.

**N3, `device-sheet-dark` / `device-sheet-light` (new).** The wall behind goes grey under the scrim
and stays readable; the sheet is a slab across the bottom third. The recuperator's sheet shows `Зал /
Бризер`, a `done` button, then `power on 3 min ago`, `fan low + medium + high 3 min ago`,
`temperature 26.4 °C 3 min ago`, `humidity 41.0 % 3 min ago`. **The argument for the whole task is in
that side-by-side**: the tile behind prints *no age at all* — those readings are under the hour
`ageLine` holds its tongue below — and the sheet answers "how old, per value" one tap away instead of
nowhere. The six existing references are **byte-identical**, which is right: a `Card(onClick = …)`
renders as a `Card` at rest, and marking every tile "tap for detail" would have had to displace a
real reading off a status line D2/D3 spent two commits clearing.

## What I found reviewing the captures that the subagents did not flag

- **The sheet's power switch is unlabelled.** `SheetControls` draws a bare `Switch` with no text
  beside it. On a tile that is fine — the name and status line say "on" — but on a 753 dp sheet whose
  point is "large primary actions", the recuperator's sheet is one small toggle floating at the
  bottom-left of an otherwise empty row. It is visible in `device-sheet-dark.png`. This is the first
  thing to fix on top of N3.
- **The curtain's on-dot is grey.** `Штора` at 40 % open is `On`, but its hue is `Neutral`, the family
  deliberately without a colour — so N1's headline on-mark is a grey dot on a grey card for that tile.
  The curtain, the lights group and the launcher have no switch either, so the dot is their *only*
  on-mark. That is the case to look at in the hallway if the dot is being trusted.
- **The wifi glyph on Пылесос says "unreachable" about an app that is simply not installed.** The N1
  agent raised this itself and I agree it is the weakest edge of an otherwise clean rule.

## Assumptions

- *Ground rules*, *Environment* and *Reporting* were taken from `git show 0ebfbd7:PLAN.md` — see
  point 1 at the top. If the intent was a different set of rules, N1–N3 were built to the wrong ones.
- **N2 and N3 both read `CLAUDE.md`'s "never write code against a vendor endpoint nobody has
  verified" as binding over the reference's button and sheet inventory.** That single reading is what
  produced the narrowing below. It is the assumption most worth overruling if you disagree.
- N2's rule that a part-open curtain is offered **Close** and not both ends is a judgement about what
  somebody walking past wants in one tap, not an observation.
- N3's `SCRIM_ALPHA` (0.6) and `LABEL_WIDTH` (220 dp) are single constants, guessed, never seen on
  the glass.
- The sheet is anchored to the **bottom** of a head-height panel: the easiest half to reach, the last
  half the eye finds. Nobody has stood in front of it.

## What was skipped, and why

**N4 — `feat(panel): art per device` — landed nothing. Blocked on assets, as the task itself says.**
No art was generated, downloaded, traced or invented, and the glyph set was not touched as a
substitute. What the run added over the previous investigation:

- **The buildable set is 9 files for 7 kinds, not 8.** `curtainGlyph(openPercent)` is the one piece of
  art on this wall that already carries state, and its rule is load-bearing — *a null position takes
  the open image, because closed is a positive claim the panel cannot make*. One curtain photograph
  is a regression. So: AC, strip, recuperator, vacuum, intercom ×1; **bulb ×2; curtain ×2**.
- **Two of PLAN.md's nine have nowhere to land.** There is no lock tile (`SheetSubject.Lock` exists
  with an empty action row and nothing constructing it; Aqara is still in review) and **no humidifier
  tile at all** — `YandexClient` maps five device types and humidifier is not one, though the
  recorded `user_info.json` holds two. Shooting those two today produces files nothing references.
- **"The lit one glows warm, the unlit one is white" is a known-bad spec on this wall.** A lit/unlit
  lamp pair existed once (`ic_bulb_filled.xml`) and was deleted: measured behind the tablet's blue
  light filter, lit composited `#865301` and unlit `#3F4754 → #473719` — **both brown, separable
  mostly by lightness**. The lit frame has to be visibly *brighter*, not merely warmer. That was in
  `docs/ui.md` filed under the deleted disc treatment, never connected to the art question.
- **The tint objection got worse, not better, because of N1.** The art is now tinted `tileAccent(hue)`
  and is one of only four things carrying family colour on a neutral card, so an untinted photograph
  removes one of the four. And `TileArt` draws the art tinted `onError` on a filled chip when a poll
  fails — a photograph cannot be tinted, so `TileMark.Failure` needs a third answer. N3 added a
  **third draw site** (the sheet heading), so two more references move too.
- Before anyone spends an hour shooting 28 lamps: **shoot one lamp lit and unlit, drop both in at
  48 dp, and look at the wall with the filter on.** If the two frames are not separable at four
  metres the pair does not work and the dot and the switch stay the only on-marks.

The N4 agent offered two things that need no assets and did **not** do either: recording the
corrected list into `docs/ui.md`, and deciding the four-moods-to-two-images mapping as a tested pure
function ahead of the files. Both are yours to call.

## The narrowing in N2 you should see before accepting it

N2 asked for one or two small buttons per tile, differing by device type — AC power + fan mode, lamp
power + reset, TV power + overflow, strip power only. **What landed is the table, with the curtain as
the only kind that has anything in it.** Every other overload returns `null`, each refusal traceable:

- the AC's fan mode is `devices.capabilities.mode`, which `docs/yandex.md` still lists as never sent
  and unverified;
- the recuperators' speeds are unverified the same way **and** Tuya is metered, so a button anybody
  can fidget with spends allowance;
- the strip's colour is reported and not controllable;
- the launchers open somebody else's app and have no state to act on.

"Power" was already there as the tile's `Switch`, so what the reference contributes that this panel
lacks is the *second* action — and on this flat every second action maps to an unverified endpoint.
That reading is defensible and it is `CLAUDE.md`'s own rule, but the visible result is **one new
button on the whole wall**, which is not what the task's opening paragraph describes. Unblocking it
is a real-device job: send one `mode` action to an AC and record the body in `docs/yandex.md`.

The lock's rule was honoured in both N2 and N3 even though **no lock tile exists**. N2 wrote it into
`TileAction`'s KDoc where the overload will go; N3 did better and made it assertable — `SheetSubject`
carries `Lock` with nothing constructing it, and `sheetActions(SheetSubject.Lock) == emptySet()` is
tested twice, once as a table row and once as a test that iterates `SheetAction.entries` and fails if
any of them ever appears there.

## On the tablet

The wall tablet was connected (`SM-T875`, Android 13, 1600×2560 at 340 dpi — the 753 dp portrait the
mosaic is laid out for).

- ✅ `./gradlew installDebug` — `Installed on 1 device.`
- ✅ `ru.domovoy/.MainActivity` starts and resumes: `ResumedActivity: ActivityRecord{… ru.domovoy/.MainActivity}`,
  process alive at 186 MB, and **no `FATAL` or `AndroidRuntime` in logcat** across the launch.
- ❌ **Nothing could be seen or touched.** The tablet is behind a secure keyguard —
  `dumpsys window policy` reports `secure=true`, `dumpsys trust` reports `deviceLocked=1`, and
  `screencap` returns Samsung's lockscreen. Unlocking it means entering the PIN, which I will not do.

So the three commits are confirmed to **build, install and run** on the real device without crashing,
and **none of the visual questions this run raised has been answered on the glass**. This is exactly
the two-minute PIN lock that `docs/design/panel-redesign.md` item 13 files as an ask-first, still
unbuilt. Unlock the tablet and the whole list in *Wants a walk to the hallway* below can be answered
in five minutes.

The lockscreen capture came out warm and sepia, which is the blue light filter still on — the thing
`docs/ui.md` records as turning the wall brown, and the thing the bulb pair has to survive.

## Wants a walk to the hallway

1. The unlabelled power switch on the sheet, and whether the sheet's bottom anchor is reachable.
2. Whether a 40 dp `outline` ring reads as *a button* from four metres behind the filter, and whether
   the closed-shades glyph inside it is told apart from the tile's own 48 dp shades art.
3. Whether N1's dot alone carries "on" on the three tiles with no switch — curtain, lights group,
   launcher — where it is grey by design.
4. Whether two red marks on one failing tile (chip **and** wifi glyph) read as noise. If so the chip
   is the one to drop.
5. One lamp, shot lit and unlit, at 48 dp, filter on. That single test decides N4.

## Found, belonging elsewhere

- **`stateChangedAt` is held and shown nowhere.** The sheet is its obvious home, but only
  `BulbTileState`, `CurtainTileState` and `ColorSetting` carry it — the AC, strip and recuperator
  mappers drop it — so putting it there today says it for some kinds and not others. Wiring it
  through the three mappers is its own commit and answers `docs/yandex.md`'s open question.
- **Four copies of the on/off wording** live in `AcTile.kt`, `LightStripTile.kt`, `RecuperatorTile.kt`
  and `BulbTile.kt`. N3 made the last one `internal` and reused it rather than collapsing the other
  three, which would be tidying files outside the task.
- **The launcher's art is keyed on package name, not kind** — a third launcher tile falls through to
  the vacuum image.

## One thing I broke and put back

Collecting the per-commit gate output above, I ran a `git stash` / `checkout` / `stash pop` loop, and
its trailing `pop` popped a **pre-existing** stash that was in the repo before this session —
`stash@{0}: On feat/panel-icons-slim-slider: commit-6 amend` — which conflicted in `BulbTile.kt`,
`TileLayout.kt` and `TileLayoutTest.kt` and dropped an untracked `ic_bulb_filled.xml` into
`res/drawable/`.

Nothing was lost: git keeps the entry on a conflicted pop. The conflict was reset, the three files
restored from `HEAD`, the stray file deleted, and **all three stashes are still on the list**
(`commit-6 amend` and the two `abandoned: filled-lamp variant` entries). The tree is back to `M
PLAN.md` and untracked `.claude/`, and the gates were re-run green afterwards. No commit on this
branch was touched. Worth knowing that those three stashes are sitting there, since the top one is
the deleted filled-lamp work N4 turns out to need.
