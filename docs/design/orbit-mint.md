# Orbit layout on neutral White

The native panel uses the selected Orbit composition on neutral White surfaces. Home contains real controls rather than a visual-only replica of the browser proposal. [DESIGN.md](../../DESIGN.md) records the shared tokens; this note records the Home composition and its acceptance limits.

## Palette

The selected lime palette uses neutral White in light mode and graphite in dark mode, following Android’s theme setting. Promoted numbers use on-surface, including the circular temperature target. Lime identifies controls and navigation. Horizontal range controls and the circular thermostat use a continuous light-to-rich peach scale; device readings remain neutral. The logo uses matching lime and peach gradients. Errors use a separate pink-red family.

The dark scheme is active when Android uses dark mode. Earlier Porcelain and Silver palettes in [LightThemePreviewTest](../../app/src/test/kotlin/ru/domovoy/panel/LightThemePreviewTest.kt) remain test-only alternatives; the application has no runtime theme selector.

## Range controls

Every horizontal slider uses the same continuous light-to-rich peach gradient, including brightness and curtain opening. Labels and units retain the actual setting: brightness and opening are not labelled cold/warm. The full rail shows the range; the knob locates the selected value without a filled-progress segment, ticks or grip marks.

[ScrollbarPreviewTest](../../app/src/test/kotlin/ru/domovoy/panel/ScrollbarPreviewTest.kt) renders and exercises the two conventional knob shapes:

| Shape | Rail | Knob |
| --- | --- | --- |
| Round | 16 dp gradient rail over an 18 dp outlined bed | 34 × 34 dp |
| Oval | The same gradient rail | 48 × 28 dp |

Oval is the selected implementation default and Round is the preview alternative. Both knobs have a 1.5 dp outline, with a 64 dp touch height and the same vendor-bounded behaviour and accessibility semantics. Dragging previews locally and sends one command on completion. Plus/minus controls remain in device details for precise steps; cards have none.

The 210 dp thermostat retains its circular control. Its full 270-degree arc runs continuously from light peach at the minimum (135 degrees) to peach at the maximum (405 degrees). A 16 dp round marker shows the current target; an unknown reading has no marker. The central number uses on-surface.

## Home

The fixed header contains the app mark, the Domovoy name and local time. Home, Domonap and the room picker remain above the scrolling device grid. The same mark identifies device details.

The thermostat is full-width. Its identity, room, power, status and ages sit beside a 210 dp circular target control. The card has no decrease/increase buttons. Dragging the arc previews a target locally and commits once on release. Accessibility adjustment uses the same target command; precise step buttons remain in the device sheet. The range and precision come from the device; no bounds means no invented dial. On narrow screens, the control stacks below its identity.

The curtain is full-width, with its promoted value to the right of the name and room. Close, a labelled slider and open share one row. End actions send the vendor minimum or maximum. Brightness sliders and power remain on the light-strip and supported individual-lamp cards. Groups use two-column Home summaries with a lit/total count, group power and expandable lamps; dimmable lamps have their own controls after expansion. Compact lamps without dimming retain their compact format. Recuperators have two-column Home cards with power and speed adjustment when available. Advanced modes, scenes and colour controls remain in details.

Cards grow with real controls, bounds, feedback and text, so the native Home is taller and scrolls more than the browser concept. Do not compress the controls to force the whole inventory into one viewport.

## Commands and state

Card controls call the existing device command paths. The redesign adds no vendor endpoint or network protocol. Dial and slider gestures hold a local draft and send once after completion; pending blocks duplicate commands on the affected device. Other devices remain usable. Group power waits while a lamp in that group is pending.

Shown device values remain confirmed readings. After confirmation finishes, an unconfirmed local draft returns to the reported value. Command errors stay on the device. Old reading age and successful polling age remain distinct; missing updates are visible. Aqara lock views remain read-only.

Incoming Domonap calls close details and yield to Domonap's own screen. No part of this design changes that priority.

## Implementation evidence

The source is [PanelRooms.kt](../../app/src/main/kotlin/ru/domovoy/panel/PanelRooms.kt), [HomeDeviceTile.kt](../../app/src/main/kotlin/ru/domovoy/panel/HomeDeviceTile.kt), [OrbitTemperatureControl.kt](../../app/src/main/kotlin/ru/domovoy/panel/OrbitTemperatureControl.kt), [SlimSlider.kt](../../app/src/main/kotlin/ru/domovoy/panel/SlimSlider.kt) and [PanelTheme.kt](../../app/src/main/kotlin/ru/domovoy/PanelTheme.kt). [HomeControlsTest](../../app/src/test/kotlin/ru/domovoy/panel/HomeControlsTest.kt) and [OrbitTemperatureControlTest](../../app/src/test/kotlin/ru/domovoy/panel/OrbitTemperatureControlTest.kt) cover the new interaction paths; theme tests retain the existing contrast thresholds.

Screenshots in [app/src/test/screenshots](../../app/src/test/screenshots) are JVM regression renders. They are not captures from the tablet. The redesigned application has not been installed on the physical tablet in this work; physical gestures, distance readability, real actuator movement and incoming-call takeover still need device validation. This note does not certify the final full-suite result.
