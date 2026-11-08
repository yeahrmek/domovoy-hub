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

The fixed header contains the app mark, the Domovoy name and local time. Home, Domonap and the room picker remain above the scrolling device grid. Home uses a house icon; refresh and panel protection use labelled-for-accessibility icon actions beside the clock. A busy refresh replaces its glyph with an in-button progress indicator and blocks duplicate taps. Compact headers use the smaller brand lockup and move the utility row below it when needed. The same mark identifies device details.

The full-width curtain pairs device art, name, room and position with a single close/slider/open row. The end actions use the existing closed/open curtain glyphs in 64 dp targets and send the vendor minimum or maximum. Their accessible names remain “Закрыть штору” and “Открыть штору”; the slider retains “Открытие”.

Other Home cards use two independent columns. Their heights follow their own content rather than their neighbour's height; short light summaries and errors reserve no empty control space. Below an effective width of 600 dp after font scaling, the cards form one column. Expanded lamps stay directly below their group in the same column.

The thermostat occupies one column and keeps its 210 dp circular target control and 64 dp power touch target. Its illustration shares the identity/power header above the centred dial. In a wider card, the identity header sits to the left of the dial. Dragging the arc previews locally and commits once on release. The range and precision come from the device; no bounds means no invented dial. Precise step buttons remain in details.

Home device illustrations share the identity/power header. Lighting pairs a brightness glyph with the large current or draft percentage above one slider. Its accessible control name remains “Яркость”. Recuperators show climate readings, text-only speed buttons and humidity, without a separate speed title or repeated icons. Groups show compact individual device illustrations and names, power and a disclosure arrow; accessibility retains the expand/collapse action and state. Device miniatures have no overlaid power, crossed-out or connectivity glyphs. Confirmed on states use an 8 dp light-lime dot (`primaryFixedDim`) with a thin readable edge; off states have no mark. Lamp illustrations distinguish lit and unlit states without an additional dot. Pending, unknown and error states keep text; pending or failing group devices do not show an on dot. The breezer row reserves no empty status line. Plain reading cards share an identity row with 48 dp art and optional power/action controls rather than allocating a separate art row. Lock and intercom cards keep one main state without secondary state labels. Advanced modes, scenes and colours remain in details.

Home cards use 16 dp content padding and 8 dp between content groups. Home device illustrations are 48 dp. Controls retain their 64 dp touch targets. Percent sliders on cards omit printed bounds; details retain them. Labels and values wrap when needed. Room grids reduce their column count with available width and font scaling, and have no reserved card minimum heights. Reading and connection times are not displayed on cards or in details; failures and pending commands remain visible.

## Commands and state

Card controls call the existing device command paths. The redesign adds no vendor endpoint or network protocol. Dial and slider gestures hold a local draft and send once after completion; pending blocks duplicate commands on the affected device. Other devices remain usable. Group power waits while a lamp in that group is pending.

Shown device values remain confirmed readings. After confirmation finishes, an unconfirmed local draft returns to the reported value. Command errors stay on the device. Old reading age and successful polling age remain distinct; missing updates are visible. Aqara lock views remain read-only.

Incoming Domonap calls close details and yield to Domonap's own screen. No part of this design changes that priority.

## Implementation evidence

The source is [PanelRooms.kt](../../app/src/main/kotlin/ru/domovoy/panel/PanelRooms.kt), [HomeDeviceTile.kt](../../app/src/main/kotlin/ru/domovoy/panel/HomeDeviceTile.kt), [OrbitTemperatureControl.kt](../../app/src/main/kotlin/ru/domovoy/panel/OrbitTemperatureControl.kt), [SlimSlider.kt](../../app/src/main/kotlin/ru/domovoy/panel/SlimSlider.kt) and [PanelTheme.kt](../../app/src/main/kotlin/ru/domovoy/PanelTheme.kt). [HomeControlsTest](../../app/src/test/kotlin/ru/domovoy/panel/HomeControlsTest.kt) and [OrbitTemperatureControlTest](../../app/src/test/kotlin/ru/domovoy/panel/OrbitTemperatureControlTest.kt) cover the new interaction paths; theme tests retain the existing contrast thresholds.

Screenshots in [app/src/test/screenshots](../../app/src/test/screenshots) are JVM regression renders. They are not captures from the tablet. The redesigned application has not been installed on the physical tablet in this work; physical gestures, distance readability, real actuator movement and incoming-call takeover still need device validation. This note does not certify the final full-suite result.

## Icon and density verification — 2026-11-08

Refresh, protection, Home, brightness and curtain endpoints use pictograms; device identity, modes and exceptional states keep readable text. Plain cards align art with their title, and normal group-device states no longer consume a separate text row. Targets remain 64 dp.

All 708 unit/UI tests passed, as did `ktlintCheck`, Android `lint`, `assembleDebug` and `verifyRoborazziDebug`. The regression fixtures were regenerated and inspected in light/dark, narrow and large-text variants. A temporary API 33 emulator also rendered the application at 1600 × 2560 px / 340 dpi in light and dark themes (font scale 1.3), plus 835 × 1808 px at normal text size. The emulator had no Domonap app and could not reach the local recuperators; those captures show the corresponding unavailable states. Device controls were not actuated and the physical tablet was not updated.

Temporary evidence and the isolated Gradle init script remain at `/private/tmp/domovoy-ui-icons`: screenshots, test/lint reports and test result XML. The temporary AVD and disposable build intermediates were removed. The remaining directory may be removed after reviewing this UI change; the durable regression fixtures live in `app/src/test/screenshots`.

The updated build was subsequently installed on the physical Samsung SM-T875 on 2026-11-08 at 16:00 Moscow time with `adb install -r`, preserving app data. The panel process started and its rendered Home screen was inspected on the tablet. The preceding test/build run passed. The physical-device screenshot is retained at `/private/tmp/domovoy-ui-icons/tablet-installed.png`; actual actuator commands and incoming-call takeover were not exercised.

A subsequent refinement on 2026-11-08 removed all overlaid power and connectivity glyphs from miniature device art, replacing confirmed-on marks with light-lime dots. All 708 tests and `ktlintCheck` passed; light/dark regression renders were refreshed and inspected. This latest build is not installed yet because the tablet was no longer connected to ADB. Its APK is retained at `/private/tmp/domovoy-ui-icons/build/app/outputs/apk/debug/app-debug.apk` for installation, and may be removed afterwards.

Manual APK transfer does not require USB: the same Samsung tablet has the existing Termux SSH connection used by IIZI. Its owning connection guide is `~/Documents/ChatGPT/iizi/implementation/docs/tablet.md`; the Mac SSH configuration is `~/Documents/Claude/Personal/iizi-tablet/remote_config`, host `iizi-tablet-remote`. On 2026-11-08 the model was verified as SM-T875 and the latest APK was copied with SCP to `/storage/emulated/0/Download/Domovoy-2026-11-08-lime-dots.apk`. The remote SHA-256 matched the local build. This copy was not installed; the user will install it manually. The temporary HTTP transfer server was stopped and its helper and URL metadata removed.

Humidifier artwork now shares one transparent off-state bitmap between power states, with fixed-coordinate theme-lime light and mist overlays when on. `HumidifierArtworkTest` verifies identical pixels outside those two effects at 48 and 80 dp in light and dark themes, including restoration of the off image. After aligning the indicator with the theme accent, all 778 tests and `ktlintCheck` passed on 2026-11-08. Inspected comparison renders and per-state pixel evidence remain in `app/build/outputs/humidifier/artwork/` and can be removed after visual acceptance; the tests regenerate them. The generation prompt is in [device-artwork-prompts.json](device-artwork-prompts.json). This artwork change has not been installed on the tablet.
