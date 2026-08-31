# PLAN-NEXT — capability-driven controls and device sheets

**Status:** N1–N4 and the verified portion of N5 are implemented. The live gate left ambiguous
writes out of the app: Kelvin colour, Tuya sleep/humidity/airflow and untested future capability
values remain documented design, not disabled promises on the wall.

---

## N1 · The state vocabulary that replaces the coloured fill — **done 2026-08-30**

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

## N3 · Tap a tile for the full device — **baseline done 2026-08-30; expanded by N5**

The second screenshot is what a tap opens: a sheet with every lamp in the group as its own tile
(name + room), a `Show all devices` tile, then large primary actions — power, reset, brightness, add
— then a `Color` section with a full-width `Soft white · 83%` bar, five colour swatches, and `Modes`
below.

The baseline sheet now opens from every real device tile: air conditioner, curtain, strip, bulb and
recuperator. It shows each reading with its own age and repeats the controls already verified on the
tile. Launcher tiles still open their vendor apps and the lamp group still expands its bulbs; those
two exceptions are intentional. N5 keeps that navigation and replaces the sheet's small fixed
action vocabulary with the complete capability-driven design.

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

## N5 · Capability-driven quick controls and complete sheets — **verified subset implemented 2026-08-31**

The detailed design below is retained as the capability map. The implementation follows its live
verification gate: AC modes/toggles, RGB/scenes, curtain position and Tuya power/speeds reflected in
subsequent reads and are enabled. Kelvin actions reached Yandex while the tested lights reported
offline and did not reflect; Tuya sleep, humidity targets and airflow modes were not exercised.
Those controls are therefore deferred. `docs/yandex.md` and `docs/tuya.md` record the probes and the
restored end state.

### The split between the wall and the sheet

The type is only the starting point. The actual control set comes from the capabilities on that
specific device. This matters on the real account: five bulbs are power-only relays, twelve bulbs
have brightness plus a Kelvin range, one has brightness plus RGB and four scenes, and `ac-03` has no
backlight capability while the other two do.

The panel has two interaction depths, with one job each:

1. **The tile is for the two or three actions used every day.** It keeps every reading already on
   the wall, allows no more than two 64 dp quick-action targets beside the 80 dp art, and uses the
   existing 64 dp level row for either one slider or one compact segmented control. It never grows
   an overflow menu.
2. **The existing bottom sheet is the complete device.** A tap on the body of a real device tile
   opens it exactly as today. The sheet is scrollable and contains every capability that device
   actually reported, every readable value with its own age, and explicit labels for controls whose
   glyph is not universally obvious.

The mockups use the proposed neutral surfaces with one violet active accent. That colour decision is
separable from N5: the capability layout must work with either the current palette or the proposed
one. Red remains reserved for offline and failed state in both.

### Capability rules shared by every device

- **Presence beats type.** A missing capability produces no control and no empty placeholder. A
  capability with a null state still produces a control, because the device says it accepts the
  action; its current choice is drawn as `unknown`, with no selected segment invented.
- **The server supplies the choices and bounds.** Sliders use the device's min, max and precision.
  Mode controls contain only `parameters.modes` from that device. Known values get a Russian label
  and icon; an unknown future value remains available under a readable title made from its raw id.
- **A write is explicit.** Power sets a boolean, a mode sets one named value, a range sets one
  snapped value and a toggle sets one boolean. No sheet control is an unlabeled “do something”
  button. The one exception is a tile's fan quick button, whose cycle is specified below.
- **Pending is local and bounded.** Pressed controls show a thin progress ring and are disabled
  until the normal re-read lands or the existing network timeout expires. The old value stays
  visible underneath. Success is never painted from the command response alone.
- **Offline and failure are not red controls.** Explicitly offline devices disable writes and keep
  the red struck-through Wi-Fi mark. A poll failure keeps the last state and ages visible; the sheet
  keeps the same short failure reason currently shown on the tile.
- **Touch size does not shrink to fit.** Every button and segment is at least 64 dp high. The visible
  round disc stays 44 dp, as now. A wide sheet wraps options into another row rather than making
  them smaller.
- **Icons are local resources.** Add small monochrome vectors for fan, climate modes, swing,
  ionisation, warmth, backlight, airflow, sleep and humidity. Do not add an icon dependency.

### The sheet anatomy

Every real-device sheet keeps the current dimmed wall and rounded bottom surface, and is allowed to
grow to roughly 90% of the portrait screen with its body scrolling. Its order is stable:

1. **Header:** 80 dp device art, room, full device name, `done`, and offline/failure state.
2. **Summary:** the same promoted value and state that remain visible on the tile.
3. **Primary:** power and the device's main range or position actions.
4. **Modes:** one labelled segmented group per enum-like capability.
5. **Options:** secondary booleans as labelled toggle rows. These are switches in the semantic
   sense, but visually use the same round on/off button rather than Material's sliding switch.
6. **Colour:** only for a light that reports a colour capability.
7. **Readings:** every value, current choice and boolean with its own `last_updated` age; read-only
   properties live here too.

Sections that would be empty are omitted. The header and Primary remain fixed while the rest
scrolls, so power and `done` are always reachable.

### Air conditioner — `devices.types.thermostat.ac`

The recorded units expose power, target temperature, thermostat mode, fan speed, swing and two or
three secondary toggles.

**On the tile**

- Keep the target temperature as the promoted value and the existing 16–32 °C slider.
- Put **Power** and **Fan** beside the art. Power is the existing button. Fan shows a propeller plus
  a compact current badge (`A`, `Q`, `1`, `2`, `3`, `T`). One press advances through
  `auto → quiet → low → medium → high → turbo`, filtered to the values that unit reported; a mode
  unknown to the UI is appended in the device's order. With unknown current state, the first press
  chooses `auto` when available, otherwise the first reported value.
- The quiet status line becomes `cool · auto fan` (or the current reported equivalents) before the
  existing oldest age. Thermostat and fan values do not move off the tile when the buttons arrive.

**In the sheet**

- **Power** button and **Target temperature** with a large current value, slider, and 1 °C minus and
  plus buttons.
- **Climate mode:** `Auto`, `Cool`, `Heat`, `Dry`, `Fan only`, filtered by that unit's
  `mode/thermostat` list.
- **Fan:** `Auto`, `Quiet`, `Low`, `Medium`, `High`, `Turbo`, filtered by `mode/fan_speed`.
- **Swing:** `Stationary`, `Vertical`, `Horizontal`, `Auto`, filtered by `mode/swing`.
- **Options:** `Ionisation`, `Keep warm`, and `Display light`; `Display light` is absent for
  `ac-03`, not disabled.
- **Readings:** power, target, thermostat mode, fan, swing and every present toggle, each with its
  own age. Add measured room temperature when the Yandex float property is parsed; it is read-only
  and never confused with the setpoint.

### Curtain — `devices.types.openable.curtain`

The useful capability is the absolute `range/open` position. The device also reports `on_off`, but
its meaning and timestamps do not describe useful curtain power and it must not be rendered as a
power button unless the real curtain proves otherwise.

**On the tile**

- Keep position as the promoted value and keep the position slider.
- Replace the single changing action with two fixed round actions beside the art: **Close** uses two
  halves/arrows moving inward; **Open** uses them moving outward. Each has its own 64 dp target.
- While a position command is awaiting the re-read, the chosen button gets the progress ring; the
  tile does not claim the curtain is moving because Yandex reports no moving state.

**In the sheet**

- Large current percent and the full position slider.
- Five explicit presets: `Closed`, `25%`, `50%`, `75%`, `Open`. They are range values snapped to the
  device's reported bounds and precision, not assumed constants when the bounds differ.
- Position reading and age. The `event/button` and `signal_level` properties may be shown under
  Diagnostics once parsed, but are not controls.

Before implementation, move the real curtain to one reversible test position and settle whether
`0` is closed and `100` open. Until then the labels in this design are a hypothesis, not code.

### Bulb — `devices.types.light`

There are three real bulb surfaces, selected by capability presence rather than by model name.

**On the tile**

- Every bulb gets **Power**.
- A bulb with `range/brightness` also gets the brightness slider in the already reserved level row
  and promotes its percentage. A relay with no range keeps that row empty and remains power-only.
- Colour is not a tile control. Its current Kelvin/RGB value may occupy the second status line, but
  the picker belongs in the sheet where it can be labelled and large enough to use.

**In the sheet**

- **Power** on every bulb.
- **Brightness** value, minus/plus and slider only when `range/brightness` exists; use that bulb's
  floor (`0` or `1`) and precision.
- For `temperature_k`: a warm-to-cool Kelvin slider plus three large presets calculated from the
  reported range (`Warm`, `Neutral`, `Cool`). Never assume 2700–6500: the GLEDOPTO reports
  1996–6369.
- For `rgb`: a large hue/saturation field, the current colour swatch and reusable recent swatches.
  The packed integer is an implementation detail and never appears on the primary control.
- For `color_scene`: one labelled button per reported scene. The Aqara light currently lists
  `Candle`, `Rest`, `Movie`, `Sunrise`; a different list produces a different row.
- **Readings:** power, brightness and colour with separate ages, then signal level under
  Diagnostics when that read-only property is parsed.

The five relay bulbs therefore have a deliberately short sheet: power, state and age. They do not
get disabled brightness or colour controls that imply a broken device.

### LED strip — `devices.types.light.strip`

Both recorded strips have power, brightness and Kelvin colour. Their state shape is the same as a
Kelvin-capable bulb, but their tile remains wide because brightness is a primary strip action.

**On the tile**

- **Power** beside the art and the existing brightness slider below it.
- Keep brightness as the promoted value. Colour temperature stays a readable status line, not a
  tiny second slider.

**In the sheet**

- Power, brightness with its `1..100` bounds, and the same Kelvin control used by capable bulbs.
- Three computed `Warm`, `Neutral`, `Cool` presets, the continuous Kelvin slider, current Kelvin
  and age. If a strip's colour state is null, no preset is selected; the controls still use the
  bounds its capability reported.

### Recuperator — Tuya `xfj`

The thing model exposes thirteen datapoints: power; three speeds; sleep; three humidity presets;
three airflow modes; and read-only temperature and humidity. They are separate booleans on the wire,
not enums, so the UI may group them only after live verification settles their exclusivity.

**On the tile**

- **Power** beside the art.
- Replace the unused level row with three 64 dp fan segments: **Low**, **Medium**, **High**, each
  carrying the fan glyph and one/two/three marks. The current boolean combination is drawn exactly;
  if Tuya ever reports two true speeds, both remain selected rather than the UI choosing one.
- Keep measured temperature as the promoted value and humidity plus the active airflow mode on the
  quiet lines, with the existing oldest age.

**In the sheet**

- **Power** and the three fan-speed segments.
- **Airflow:** `Fresh air` (`in_mode`), `Exhaust` (`out_mode`), `Recovery` (`auto_mode`).
- **Humidity target:** `Low`, `Medium`, `High` from the three `huimidity_*` booleans. Keep the
  vendor's misspelling only in code; the UI says `Humidity`.
- **Sleep mode** as a labelled moon toggle.
- **Readings:** online, power, every true speed/mode, temperature and humidity with the independent
  timestamps Tuya supplies.

No one-of-three group is implemented as a radio group until a deliberate device test establishes
that selecting one clears the other two. The first implementation may have to send one atomic
property object that sets the chosen value true and its siblings false; that exact body and its
metering cost must be recorded in `docs/tuya.md` first. Opening the sheet never changes the six
minute poll cadence; after a write, only that recuperator is re-read once, as today.

### Launcher surfaces — vacuum and intercom

These are present on the panel but are not controllable panel devices.

- **Пылесос:** the whole tile remains one large action that opens Mi Home. No quick power button,
  no fabricated battery and no empty sheet; Mi Home has the map, consumables and cleaning state the
  panel cannot read.
- **Домофон:** the whole tile opens Domonap. It never gets an unlock/open button or a sheet. An
  incoming call continues to place Domonap's own activity over everything immediately.

Adding a sheet containing only `Open app` would add a tap without adding functionality, so launcher
tiles are the explicit exception to “tile tap opens the complete sheet”.

### Composite and future surfaces

- The **lights group** is not a device. Its tap continues to expand the named bulbs in place; each
  bulb then has its own capability-driven sheet. A group power button is outside N5 until the
  Yandex group-action endpoint is verified and its timestamp problem is resolved.
- The **Aqara lock** is not on the panel yet. When it arrives, both tile and sheet remain read-only:
  state, battery and events, never unlock/open/door release.
- The **humidifier**, Yandex TV, Yandex speakers and Yandex buttons/switches are not current panel
  types. N5 does not quietly add them while solving controls for the devices already on the wall.

### Verification gate before UI implementation

The action shapes are documented, but several have never reached these devices. Perform small,
reversible live probes from the tablet and restore the previous value after each:

1. Yandex AC: one `mode/fan_speed`, one `mode/thermostat`, one `mode/swing`, and one safe toggle.
2. Yandex light: brightness, Kelvin, RGB and one reported scene on devices that advertise each.
3. Yandex strip: Kelvin while on, then restore; explicitly test whether a colour action is accepted
   while off without leaving it on.
4. Yandex curtain: one non-extreme `range/open`, observe direction and reporting delay, then restore.
5. Tuya recuperator: first verify the existing power route, then one speed, one airflow mode, sleep
   and one humidity preset; record whether sibling booleans clear and how many metered calls each
   interaction costs.

Record the request shape, redacted response, re-read result and physical observation in the vendor
doc. A failed or ambiguous probe leaves that control out; it does not become a disabled promise.

### Implementation order

1. **Tests first: capability-to-control tables.** Fixtures cover a relay bulb, Kelvin bulb, RGB
   scene bulb, both strips, all three AC capability differences, the curtain, a fully populated
   recuperator, missing capabilities and null state.
2. **Preserve capability metadata.** Extend the existing shared maps to retain colour bounds and
   scenes and the read-only float properties the sheet will show. Do not add a repository/use-case
   layer or a generic vendor command framework.
3. **Add only verified writes.** `YandexClient` gains direct `setMode`, `setToggle` and `setColor`
   methods beside `setOn`/`setRange`; Tuya gains the smallest property-write function proven by the
   probes. Every call keeps the existing explicit timeout and re-read-on-success rule.
4. **Pure presentation models.** One function per tile type returns quick actions; one per sheet
   returns ordered sections. Capability absence, null state, offline and pending are table-tested
   before Compose draws them.
5. **Reusable controls.** Build the round action, segmented options, stepped range, toggle row,
   Kelvin control and RGB/scene control once because at least two device kinds consume each shared
   primitive where applicable.
6. **Wire one type at a time:** bulb → strip → curtain → AC → recuperator. Keep each type's tests
   and screenshot baseline in the same change so a partly migrated wall never loses current
   controls.
7. **Tablet verification.** Run unit tests, ktlint, lint and screenshot verification; install the
   debug build; check 64 dp targets, scrolling, pending/error states, idle dismissal and Domonap
   takeover in both system themes. No change is complete on compile or screenshots alone.

### Acceptance table

| Surface | Tile quick controls | Complete sheet | Capability-sensitive cases |
| --- | --- | --- | --- |
| AC | power, fan, target slider | target, thermostat, fan, swing, present toggles, readings | missing backlight; null current modes |
| Curtain | one contextual open/close action, position slider | open, close, position slider, reading | direction verified; no fake power |
| Relay bulb | power | power, reading | no empty brightness/colour sections |
| Kelvin bulb | power, brightness | power, brightness, Kelvin reading | control deferred after ambiguous offline probe |
| RGB/scene bulb | power, brightness | power, brightness, RGB, reported scenes, readings | only reported scenes |
| LED strip | power, brightness | power, brightness, Kelvin reading | control deferred after ambiguous offline probe; null colour remains unknown |
| Recuperator | power, one fan-cycle shortcut | low/medium/high and readings | other writable booleans deferred until verified |
| Vacuum launcher | whole tile opens Mi Home | none | no readable state invented |
| Intercom launcher | whole tile opens Domonap | none | no door action; call takeover intact |

**Tests.** In addition to the capability tables, assert that a nested quick action does not open the
sheet, a tile-body tap does; an absent capability leaves no semantics node; null state leaves no
selected node; every hit target is at least 64 dp; pending prevents duplicate writes and times out;
all sheets dismiss on idle reset and call takeover; the lock and both launchers have empty panel
action sets. Roborazzi covers one dense AC sheet, each bulb shape, the curtain, the strip, the
recuperator and both themes.

---

## What is not here

`docs/design/panel-redesign.md` items 1, 2, 3, 5, 6, 8, 10, 11, 12 remain open and are in neither
plan. Item 13 there is still an ask-first — always-on, system bars, screen timeout, the two-minute
PIN lock — and stays unbuilt until asked.
