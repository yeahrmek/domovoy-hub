# PLAN-NEXT — controls, state marks, and real device art

**Do not execute this yet.** `PLAN.md` (D1–D4) is being implemented right now. This file is the
queue behind it: observations from the Yandex dark screenshots of 2026-08-29 23:44 and 23:47 that
arrived after `PLAN.md` was written. Fold it into a new `PLAN.md` once the current run has landed
and been looked at on the wall.

Two of these items overlap `PLAN.md` and must not be built twice — noted per item.

---

## N1 · The state vocabulary that replaces the coloured fill

**Overlaps `PLAN.md` D1.** D1 removes the saturated tile fills. It does not say what then answers
"is it on" at a glance. This is that answer, and it should have been part of D1.

The reference uses four marks and nothing else:

| State | Mark |
| --- | --- |
| on | a small filled green dot at the top-left corner of the art |
| on | the power button takes the accent colour; off, it is neutral grey |
| on | **the art itself lights up** — see N4 |
| unreachable | a small struck-through wifi glyph, red, top-right |

Three of them say "on" simultaneously. That redundancy is deliberate and worth copying: on a wall
behind a blue light filter, a single mark carrying a single state is a mark that can be lost.

**If D1 has already landed by the time this is built**, check what it chose for an on-indicator
first and extend it rather than replacing it.

---

## N2 · Per-device-type controls on the tile — **done 2026-08-30**

Every tile in the reference carries one or two small round buttons at its top-right, and **which
buttons depends on the device type**:

- air conditioner — power, fan mode
- lamp — power, reset
- TV — power, overflow `⋯`
- speaker — call, one more
- strip — power only

They are secondary and quiet: circular, neutral, roughly a third the width of the art. The primary
identity of the tile is still the art and the name.

Implemented with the reference's round power button: a 44 dp visible disc inside the panel's 64 dp
touch target, accented only while a current reading says the device is on. Existing range sliders
remain. The curtain keeps the one verified secondary action; unverified per-kind writes remain
absent under the constraints below.

**Constraints that decide the button set:**

- **The lock gets no write action of any kind.** `CLAUDE.md`: it reports, it does not act. No power
  button, no overflow, nothing that could be mistaken for one.
- **Launcher tiles (Домофон, Пылесос) get no buttons.** They open the vendor app; there is no state
  to act on.
- **The recuperators are on Tuya's metered monthly allowance.** A button that triggers a read is a
  button that costs allowance every time somebody walks past and fidgets.

**Test.** Which buttons a kind offers is a pure function of the tile type and state, and belongs
beside `promoted`, `hue` and `span` in `TileLayout.kt`. That is the assertion; the composable is not.

---

## N3 · Tap a tile for the full device

The second screenshot is what a tap opens: a sheet with every lamp in the group as its own tile
(name + room), a `Show all devices` tile, then large primary actions — power, reset, brightness, add
— then a `Color` section with a full-width `Soft white · 83%` bar, five colour swatches, and `Modes`
below.

This is the largest single item in this file and the one that most changes what the panel is. Today
one thing opens: the lamp group tile, which `PLAN.md`'s predecessor added.

**Decided 2026-08-29, and this is the rule the task is built to.** A wall panel is glanceable and a
phone app is not, so this does *not* copy the reference's split:

- **The tile carries more than Yandex's does, not less.** Everything readable today stays, and the
  tile is where detail belongs — it is read from the hallway without being touched.
- **The sheet adds full detail and control.** It is what a tap is for: the rest of the readings, and
  the actions that do not fit on a tile.
- **The sheet never becomes the only place a value lives.** If a number moves off a tile into the
  sheet, the change is wrong — that turns a glance into a walk.

So the reference's tile anatomy is taken for its *discipline* (art, controls, name, one quiet line)
and not for its emptiness.

**Hard constraints, all from `CLAUDE.md`:**

- **The lock's sheet is read-only** — state, battery, events. No unlock, no open, no door release.
  This is the rule most likely to be broken by an agent building "a detail sheet per device", and it
  must be stated in the task that builds it.
- **Nothing here may cover, delay or suppress the Domonap call screen.** An open sheet must yield
  instantly and must not be in the way when the panel comes back.
- **The sheet must close on the idle reset.** `IdleReset.kt` returns the panel to Главная after two
  minutes; a sheet left open by a passer-by is a panel that has stopped being a panel.
- **No new poll cadence.** The sheet renders what is already polled. A sheet that polls faster while
  open burns Tuya allowance and defeats the metering.
- **Xiaomi has no readable state** — the vacuum and humidifier are a hosted widget and a launcher
  tile. They get no sheet.

**Test.** What a sheet offers per device type is a pure function and gets a table, including the
lock's empty action set as an explicit case rather than an omission.

---

## N4 · Realistic device art — **done 2026-08-30**

Chosen and implemented: photo-like images, not glyphs. The normalized transparent PNGs live in
`app/src/main/res/drawable-nodpi/`, render untinted at 80 dp on tiles and sheets, and are selected
by the pure `art(...)` functions in `TileLayout.kt`.

Included, one image per device kind, light object on a transparent background:

- air conditioner
- curtain / blind
- bulb — **two states, lit and unlit** (see N1: the lit one glows warm, the unlit one is white)
- LED strip — **two matched states, lit and unlit**
- recuperator / breather
- door lock
- vacuum
- humidifier
- intercom panel

The bulb and LED-strip pairs share the same object, framing and canvas between states; only the
light changes. A positive `isOn == true` selects the glowing art. Off and unknown select the unlit
art, while the tile's words and mood still distinguish those states.

The current panel uses the air conditioner, curtain, bulb, strip, recuperator, vacuum and intercom
assets. Door-lock and humidifier art is already in resources for the tiles that will consume it;
today the Xiaomi humidifier remains inside Xiaomi's hosted widget as required by N3.

---

## What is not here

`docs/design/panel-redesign.md` items 1, 2, 3, 5, 6, 8, 10, 11, 12 remain open and are in neither
plan. Item 13 there is still an ask-first — always-on, system bars, screen timeout, the two-minute
PIN lock — and stays unbuilt until asked.
