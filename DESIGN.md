---
name: Domovoy Hub
description: Working Orbit controls on neutral White surfaces for a hallway wall tablet.
colors:
  primary-light: "#4C6D13"
  primary-dark: "#B3DF5A"
  primary-fixed-dim: "#B3DF5A"
  on-primary-fixed: "#283D12"
  tertiary-fixed-light: "#FFE2D6"
  tertiary-fixed-dark: "#FFDCCE"
  tertiary-fixed-dim: "#FF865F"
  on-primary-light: "#FFFFFF"
  on-primary-dark: "#283D12"
  primary-container-light: "#EBF5D5"
  primary-container-dark: "#34451E"
  on-primary-container-light: "#4C6D13"
  on-primary-container-dark: "#D9F3AF"
  secondary-light: "#4E5D98"
  secondary-dark: "#AEBFFF"
  on-secondary-light: "#FFFFFF"
  on-secondary-dark: "#202C57"
  secondary-container-light: "#E1E6FF"
  secondary-container-dark: "#34436B"
  on-secondary-container-light: "#283A70"
  on-secondary-container-dark: "#E1E6FF"
  tertiary-light: "#9E492E"
  tertiary-dark: "#FF865F"
  on-tertiary-light: "#FFFFFF"
  on-tertiary-dark: "#4B241B"
  tertiary-container-light: "#FFE2D6"
  tertiary-container-dark: "#57352C"
  on-tertiary-container-light: "#522819"
  on-tertiary-container-dark: "#FFDCCE"
  background-light: "#FAFAFC"
  background-dark: "#14171C"
  on-background-light: "#232735"
  on-background-dark: "#EDF0F5"
  surface-light: "#FAFAFC"
  surface-dark: "#14171C"
  on-surface-light: "#232735"
  on-surface-dark: "#EDF0F5"
  on-surface-variant-light: "#535A69"
  on-surface-variant-dark: "#B7C0CD"
  surface-container-lowest-light: "#F6F6F8"
  surface-container-lowest-dark: "#1A1E24"
  surface-container-low-light: "#F3F4F7"
  surface-container-low-dark: "#1D2229"
  surface-container-light: "#F0F1F4"
  surface-container-dark: "#20252D"
  surface-container-high-light: "#E7E9EE"
  surface-container-high-dark: "#29303A"
  surface-container-highest-light: "#DDE0E6"
  surface-container-highest-dark: "#333C48"
  outline-light: "#6C7180"
  outline-dark: "#8795A8"
  outline-variant-light: "#C6CAD4"
  outline-variant-dark: "#485363"
  error-light: "#9F2943"
  error-dark: "#FFB4C3"
  on-error-light: "#FFFFFF"
  on-error-dark: "#5E1B2C"
  error-container-light: "#FCE5E8"
  error-container-dark: "#422B33"
  on-error-container-light: "#6A1830"
  on-error-container-dark: "#FFB4C3"
typography:
  display-large: {fontFamily: "Manrope", fontSize: "64sp", lineHeight: "76sp"}
  display-medium: {fontFamily: "Manrope", fontSize: "52sp", lineHeight: "62sp"}
  display-small: {fontFamily: "Manrope", fontSize: "44sp", lineHeight: "52sp"}
  headline-large: {fontFamily: "Manrope", fontSize: "40sp", lineHeight: "48sp"}
  headline-medium: {fontFamily: "Manrope", fontSize: "34sp", lineHeight: "42sp"}
  headline-small: {fontFamily: "Manrope", fontSize: "28sp", lineHeight: "36sp"}
  title-large: {fontFamily: "Manrope", fontSize: "26sp", lineHeight: "32sp"}
  title-medium: {fontFamily: "Manrope", fontSize: "22sp", lineHeight: "28sp"}
  title-small: {fontFamily: "Manrope", fontSize: "18sp", lineHeight: "24sp"}
  body-large: {fontFamily: "Manrope", fontSize: "20sp", lineHeight: "28sp"}
  body-medium: {fontFamily: "Manrope", fontSize: "18sp", lineHeight: "24sp"}
  body-small: {fontFamily: "Manrope", fontSize: "16sp", lineHeight: "22sp"}
  label-large: {fontFamily: "Manrope", fontSize: "18sp", lineHeight: "24sp"}
  label-medium: {fontFamily: "Manrope", fontSize: "16sp", lineHeight: "20sp"}
  label-small: {fontFamily: "Manrope", fontSize: "16sp", lineHeight: "20sp"}
rounded:
  tile: "12dp"
  power: "10dp"
  sheet-top: "20dp"
  range-thumb: "50%"
spacing:
  tile-outer: "6dp"
  tile-content: "20dp"
  home-content-gap: "8dp"
  navigation-horizontal: "24dp"
  navigation-vertical: "12dp"
  navigation-gap: "8dp"
  sheet-gap: "16dp"
  sheet-content: "24dp"
components:
  home-card: {rounded: "{rounded.tile}", padding: "{spacing.tile-content}"}
  power-touch-target: {size: "64dp"}
  power-disc: {size: "56dp", rounded: "{rounded.power}"}
  temperature-dial: {size: "210dp"}
  slider-track: {height: "16dp"}
  slider-rail-outline: {height: "18dp"}
  slider-thumb-round: {size: "34dp", rounded: "{rounded.range-thumb}"}
  slider-thumb-oval: {width: "48dp", height: "28dp", rounded: "{rounded.range-thumb}"}
  temperature-marker: {size: "16dp"}
  slider-touch-target: {height: "64dp"}
  sheet: {padding: "{spacing.sheet-content}"}
---
# Design System: Domovoy Hub

## Overview

**Creative North Star: "Orbit controls, neutral White"**

A native Android control panel for short interactions at a hallway wall. The chosen Orbit composition puts a working circular thermostat, curtain controls and light adjustment on Home. The selected White scheme gives it neutral white and grey surfaces, dark readings and lime actions. The range controls use a continuous light-to-rich peach colour scale; the app mark uses the selected lime and peach palette.

The application follows Android’s light/dark setting: neutral White in light mode and graphite in dark mode, both with lime actions. No separate in-app theme selector is added. The existing Domovoy mark identifies the header and device sheets; its geometry remains intact and its colours follow the lime and peach palette.

**Key Characteristics:**

- Real adjustment on the device card, with full capabilities still available in details.
- Fixed branded navigation above the scrolling Home and room sections.
- Large readings, explicit command feedback and separate reading and polling ages.

The surface-specific layout and acceptance limits are recorded in [Orbit](docs/design/orbit-mint.md). [PanelTheme.kt](app/src/main/kotlin/ru/domovoy/PanelTheme.kt) owns the native colour and typography values.

## Colors

Filled power and Home buttons use lime `#B3DF5A` with dark `#283D12` glyphs in both themes. Light-theme text and state marks use the darker green `#4C6D13`; dark-theme marks use lime.

Promoted readings, including the thermostat target, use on-surface. Lime identifies active controls and selected navigation. All horizontal range rails and the circular thermostat use the same light-to-rich peach scale, from tertiary-fixed to tertiary-fixed-dim. Peach and lavender do not colour device readings. The app mark uses the selected lime and peach palette. White and grey surface roles separate the page, cards and details; the dark scheme uses graphite surfaces. Context and range labels use on-surface-variant; control outlines use outline, while quiet card borders and dividers use outline-variant.

Home cards normally use surface-container. Device failures may use error-container; stale or shared failures add an error outline. Room cards retain their neutral state ramp from unknown to off, failing and on. Power is accented only when the state is confirmed on.

**The Separate Error Rule.** Failures use the separate pink-red error role and explanatory text; peach is an accent, not an error colour. Shared polling failure preserves the last known device state; a failed command stays local to that device.

The user selected neutral **White** from the [light-theme previews](app/src/test/kotlin/ru/domovoy/panel/LightThemePreviewTest.kt). The earlier **Porcelain** and **Silver** alternatives remain test-only palettes; they are not a runtime theme switcher.

## Typography

Manrope is bundled locally in regular, medium, semibold and bold weights; its OFL licence is included in [assets/licenses](app/src/main/assets/licenses/manrope-OFL.txt). Material typography roles map to the sp sizes and line heights above. The brand uses a semibold headline, device names use title-medium, promoted readings use display-small, context uses body-medium and freshness uses body-small. System font scaling remains enabled.

## Layout

The reference device is a portrait wall tablet with approximately 753 × 1204 dp of content space. Navigation stays above the scrolling grid: the app mark and name return Home, the header shows local time, and the next row contains Home, Domonap and the room picker.

Home gives the thermostat a full-width card with its identity and status beside the circular control. The curtain is full-width with its value on the right and a slider between close/open actions. Light strips, light-group summaries, dimmable lamps and recuperators use two-column Home cards; compact lamps without dimming keep their compact format. Grouped lamps expand directly below their summary row. Every card names its room. The thermostat stacks below its identity on narrow screens; rows otherwise grow to the tallest neighbour. Empty grid slots remain absent.

Actual cards are taller than the browser concept because the native implementation includes real controls, bounds, ages and feedback. Scrolling is expected. Room sections retain their capability-based formats. There is no fixed card height that may clip text or controls.

## Elevation & Depth

Home uses flat tonal surfaces with a thin card outline, without decorative shadows. Device details appear over a scrim while the panel remains mounted. Details scroll when needed and occupy up to 94% of the panel height. Dropdowns and other Material components retain their native elevation and focus behaviour.

## Shapes

Cards share the tile radius. Power has a rounded square visible surface inside a larger touch target. The thermostat is a circular 270-degree gradient range with a draggable arc and a round position marker; card-level plus/minus buttons are removed. Its centre continues to open device details. Sheets have rounded top corners. Horizontal range controls use a conventional round or oval knob over a continuous gradient rail. Oval is the selected implementation default; Round is the preview alternative.

## Components

- **Home thermostat:** a working 210 dp target dial built from advertised temperature bounds and precision, plus power. Its full 270-degree arc runs light-to-rich peach from the minimum at 135 degrees to the maximum at 405 degrees; a 16 dp round marker locates the target. An unknown reading has no marker. There are no plus/minus buttons on the card. Without advertised bounds, show the reading without inventing adjustment.
- **Curtain:** promoted neutral position, vendor-bounded slider and close/open actions using the actual minimum and maximum.
- **Lighting:** power and brightness adjustment when supported. Room light groups expose their lit/total count, group power and expansion; expanded dimmable lamps expose their own brightness slider. Colours, scenes and other advanced modes remain in details.
- **Recuperator:** power, climate readings and available speed controls. Offline/device-error cards show the problem without offering unsupported speed adjustment.
- **Power:** switch semantics, a lime confirmed-on state and a neutral off state. Pending controls remain visibly disabled.
- **Horizontal range control:** local draft, setting label, vendor range and unit, a conventional round or oval knob and a 64 dp touch area. The whole 16 dp rail has a continuous light-to-rich peach gradient over an 18 dp outlined bed; it has no selected-value fill, ticks or grip marks. The knob has a 1.5 dp outline. Oval is the default; Round is an alternative in the real Compose preview. Brightness and curtain opening retain their own labels and units; the colour scale applies to all sliders without relabelling them as temperatures. Cards have no plus/minus buttons. Device sheets retain optional step controls using vendor precision.
- **Navigation and brand:** the app mark, lime Home action, Domonap shortcut and room menu. An unavailable Domonap app is disabled and explained. [PanelBrand](app/src/main/kotlin/ru/domovoy/panel/PanelBrand.kt) chooses the official light/dark mark by the app theme, independently of Android's night setting.
- **Details:** the same brand, full vendor name, current readings and their ages, verified capabilities and a dismiss action. System Back and outside tap dismiss; incoming calls close details and yield to Domonap.

**The Confirmed Command Rule.** Dragging previews a local draft and sends one command when the gesture finishes. Pending disables controls for that device, while other devices remain usable; group power waits for the affected group.

## Do's and Don'ts

- **Do** use on-surface for promoted numbers and light-to-rich peach gradients for range scales.
- **Do** retain the correct setting name and unit on every range control, including brightness and curtain opening.
- **Do** place working controls on Home when the device advertises the capability and bounds.
- **Do** preserve vendor ranges, precision and units in dials, sliders and end actions.
- **Do** keep reading age distinct from successful service polling.
- **Do** keep wall touch targets at least 64 dp and allow text and rows to grow.
- **Do** use the app mark in the header and device sheets without tinting it.

- **Don't** colour promoted readings lime, lavender or peach.
- **Don't** add plus/minus controls to device cards; precise step controls remain in details.
- **Don't** draw an adjustment control that has no connected command.
- **Don't** present a local draft as a confirmed physical reading.
- **Don't** add an unlock, open or other write action to the Aqara lock.
- **Don't** cover, delay or suppress Domonap's incoming-call screen.
- **Don't** shrink live controls or hide state text to reproduce the shorter browser mock.
