package ru.domovoy

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * The panel's two colour schemes, written out.
 *
 * `lightColorScheme()` with no arguments is not a palette, it is Material's *baseline* — a violet,
 * and a violet run through the neutral surfaces of an unstyled panel is the grey-mauve the wall
 * this hangs on already is. So the values are here, and this is the one file in the app allowed to
 * hold any: `panel/` is grep-clean of hex literals and stays that way. It is not a wrapper with one
 * caller — it is data, and the alternative is 48 arguments inline in [MainActivity].
 *
 * **Two seeds, and everything else neutral.** A cool blue at Lab hue 272 — the hue of `#2196F3` —
 * for climate, and a warm amber at hue 72 — the hue of `#FFA000` — for light. Every value below is
 * a *tone* off one of four ramps generated from those two: the blue at chroma 46 (primary), the
 * amber at chroma 70 (tertiary), and the blue again at chroma 3 and 9 for the surfaces and the
 * outlines, which is Material's own way of tinting neutrals with the seed rather than leaving them
 * dead grey. Generated rather than picked stop by stop, so light and dark are the same colours at
 * different tones and cannot drift apart when one of them is retouched. The error ramp is
 * Material's, untouched: red is the one thing on this panel that must look like everybody else's.
 *
 * **What the three tile families land on**, which is the thing this file exists to get right —
 * climate takes `primaryContainer`, light takes `tertiaryContainer`, and everything else takes
 * `secondaryContainer` (see `TileHue`). Measured as CIE ΔE against each other and against the
 * `surfaceContainer` that every *off* tile wears:
 *
 * | | light | dark |
 * | --- | --- | --- |
 * | climate / light | 37 | 75 |
 * | climate / neutral | 16 | 26 |
 * | light / neutral | 35 | 51 |
 * | neutral / off | 20 | 19 |
 * | climate / off | 13 | 36 |
 * | light / off | 26 | 48 |
 *
 * The neutral family is told apart by **lightness rather than by hue**, and that is deliberate
 * twice over. It is the family defined by being neither of the other two, so a hue of its own would
 * be a third seed the brief did not ask for — and it could not have one anyway: sRGB holds no more
 * than ~16 chroma of blue at tone 90, so a second blue at the container tone comes out as the first
 * one. Its container sits at tone 75 instead of Material's 90, which is what buys the 16 and 20
 * above; at tone 90 it was 12 from climate and 6 from a plain off tile, and 6 on a wall is one
 * colour.
 *
 * Contrast holds everywhere it has to: every on-colour is at least 7:1 against the container it is
 * written on, in both schemes, and the weakest ratio in either is `outline` on `surface` at 4.3.
 *
 * No dynamic colour anywhere. The wallpaper of a kiosk tablet is not a design input, and on a wall
 * showing two rooms' worth of amber and blue, a palette that moves when somebody changes the
 * launcher background is a panel that stops meaning what it meant yesterday.
 */
internal val panelLightScheme = lightColorScheme(
    primary = Color(0xFF0561A2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001D36),
    inversePrimary = Color(0xFFA5C8FF),
    secondary = Color(0xFF575F6D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB1B9C9),
    onSecondaryContainer = Color(0xFF141C27),
    tertiary = Color(0xFF865301),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDBA),
    onTertiaryContainer = Color(0xFF281800),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFDAE3F3),
    onSurfaceVariant = Color(0xFF3F4754),
    surfaceTint = Color(0xFF0561A2),
    inverseSurface = Color(0xFF2E3035),
    inverseOnSurface = Color(0xFFEEF1F6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF6F7786),
    outlineVariant = Color(0xFFBEC7D7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7F9FF),
    surfaceDim = Color(0xFFD7DADF),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    surfaceContainerHighest = Color(0xFFE0E3E8),
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFD4E3FF),
    primaryFixedDim = Color(0xFFA5C8FF),
    onPrimaryFixed = Color(0xFF001D36),
    onPrimaryFixedVariant = Color(0xFF03497C),
    secondaryFixed = Color(0xFFDAE3F3),
    secondaryFixedDim = Color(0xFFBEC7D7),
    onSecondaryFixed = Color(0xFF141C27),
    onSecondaryFixedVariant = Color(0xFF3F4754),
    tertiaryFixed = Color(0xFFFFDDBA),
    tertiaryFixedDim = Color(0xFFFFB863),
    onTertiaryFixed = Color(0xFF281800),
    onTertiaryFixedVariant = Color(0xFF663E00),
)

/**
 * The same two seeds at the tones dark asks for: the accents come up from tone 40 to 80 and their
 * containers down from 90 to 30, so a climate tile is a deep blue with a pale blue on it instead of
 * the reverse. The surfaces are the same near-neutral blue ramp at tones 4 to 24.
 *
 * Not dead code, and that was checked before it was written: the tablet's dark theme is on a real
 * schedule, 19:00–07:00, so the wall is on this scheme for half of every day and nobody is looking
 * at it when it switches. The separations it has to hold are in the table on [panelLightScheme] —
 * dark is the wider of the two columns, which is the opposite of the way a palette usually fails.
 */
internal val panelDarkScheme = darkColorScheme(
    primary = Color(0xFFA5C8FF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF03497C),
    onPrimaryContainer = Color(0xFFD4E3FF),
    inversePrimary = Color(0xFF0561A2),
    secondary = Color(0xFFBEC7D7),
    onSecondary = Color(0xFF29313D),
    secondaryContainer = Color(0xFF3F4754),
    onSecondaryContainer = Color(0xFFDAE3F3),
    tertiary = Color(0xFFFFB863),
    onTertiary = Color(0xFF462A01),
    tertiaryContainer = Color(0xFF663E00),
    onTertiaryContainer = Color(0xFFFFDDBA),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE0E3E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFF3F4754),
    onSurfaceVariant = Color(0xFFBEC7D7),
    surfaceTint = Color(0xFFA5C8FF),
    inverseSurface = Color(0xFFE0E3E8),
    inverseOnSurface = Color(0xFF2E3035),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8991A0),
    outlineVariant = Color(0xFF3F4754),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF282A2E),
    surfaceContainerHighest = Color(0xFF333539),
    surfaceContainerLow = Color(0xFF1A1C1F),
    surfaceContainerLowest = Color(0xFF0B0E13),
    // The fixed roles are the same values in both schemes — that is what "fixed" means. Nothing in
    // the panel reads them today; they are here so that a component that does cannot fall back to
    // the baseline violet through the one door left open.
    primaryFixed = Color(0xFFD4E3FF),
    primaryFixedDim = Color(0xFFA5C8FF),
    onPrimaryFixed = Color(0xFF001D36),
    onPrimaryFixedVariant = Color(0xFF03497C),
    secondaryFixed = Color(0xFFDAE3F3),
    secondaryFixedDim = Color(0xFFBEC7D7),
    onSecondaryFixed = Color(0xFF141C27),
    onSecondaryFixedVariant = Color(0xFF3F4754),
    tertiaryFixed = Color(0xFFFFDDBA),
    tertiaryFixedDim = Color(0xFFFFB863),
    onTertiaryFixed = Color(0xFF281800),
    onTertiaryFixedVariant = Color(0xFF663E00),
)

/**
 * Material's baseline type scale, which is what the panel ran on until now and is what every size
 * below is a departure from. Kept as the base of each `copy` so that the typeface, the weights and
 * the per-size tracking stay Material's: **only the size is the wall's.**
 */
private val baselineTypography = Typography()

/**
 * The panel's type scale, sized for a wall rather than for a phone.
 *
 * Here for the same reason the two schemes are: it is data, `PanelTheme.kt` is the one file allowed
 * to hold values, and `panel/` stays clean of them. [MainActivity] passes it in the same call as the
 * scheme.
 *
 * **What was wrong.** `MaterialTheme` was given a `colorScheme` and nothing else, so the panel ran
 * on Material's baseline — a scale drawn for a phone held 30 cm from the face. Nine status lines
 * across `panel/` were `bodySmall`, which is **12sp**: every "on", every age, every error, on a
 * screen mounted at head height in a hallway. `bodySmall` no longer appears in `panel/` at all.
 *
 * ## The arithmetic, and what it says about "four metres"
 *
 * A `dp` is 1/160 inch — **0.159 mm** — whatever the density, so an `Ns` glyph has an em box of
 * N × 0.159 mm and a cap height of roughly 0.7 of that. The panel is 753 dp, which is **120 mm** of
 * glass. Those two numbers decide the rest.
 *
 * Take the usual optics: a 20/20 letter subtends 5 arcminutes at the acuity *limit*, and comfortable
 * reading of running text wants roughly three times that. At four metres, 15 arcmin of cap height is
 * 4000 × tan(0.25°) ≈ **17.5 mm**, which is an em of about 25 mm — **157sp**. Five characters of it
 * fill the panel edge to edge. So *four metres, read literally, is one number across the whole wall*
 * and no scale can deliver it for a mosaic of 34 tiles.
 *
 * **So the panel is deliberately a two-distance object, and this scale says which line is for which
 * distance.** That is not a retreat from the brief — CLAUDE.md asks for "the 16 °C and the 33.5 %
 * visible from the hallway", one value per tile, not for the ages and the error strings:
 *
 * - **The promoted value is the four-metre line.** `displaySmall` at 44sp is a cap height of
 *   44 × 0.159 × 0.7 ≈ **4.9 mm**: 4.2 arcmin at four metres — at the acuity limit, so legible for a
 *   value whose shape is known and whose tile is known, which a temperature on a fixed tile is — and
 *   8.4 arcmin at two metres, the width of the hallway and about where somebody actually stops.
 * - **The tile name is the same walk-past line one step down.** `titleMedium` at 22sp, up from 16.
 * - **The status line is an arm's-length line and is now written down as one.** `bodyMedium` at
 *   18sp is 2.0 mm of cap: 6.9 arcmin at half a metre. It carries the ages, the units and the
 *   errors — everything CLAUDE.md requires a tile to be able to say, none of which anybody reads
 *   from the far end of a hallway at any size this panel could set it in.
 * - **The floor is 16sp**, on every one of the fifteen slots including the ones nothing uses. What
 *   is not a guess is that 12sp is under it.
 *
 * **This wants a walk to the hallway.** Nobody has read this panel from four metres, or from two,
 * and the numbers above are optics rather than a measurement. The thing to take there is the
 * promoted value: if 44sp is not readable from where somebody stands, the scale moves *at the top*
 * — `displaySmall` up towards `displayMedium`'s 52 — and the status line stays where it is, because
 * raising that one buys wrapping rather than distance. If it is comfortably readable, the honest
 * saving is at the top too. The floor does not move down.
 *
 * Every slot is written out, including the ones `panel/` never asks for, for the same reason the
 * schemes write out the `fixed` colour roles: a component that reaches for one must not fall back
 * to the phone scale through the one door left open.
 */
internal val panelTypography = Typography(
    displayLarge = baselineTypography.displayLarge.copy(fontSize = 64.sp, lineHeight = 76.sp),
    displayMedium = baselineTypography.displayMedium.copy(fontSize = 52.sp, lineHeight = 62.sp),
    // The promoted value: the one line on a tile that is for the walk past. See [PromotedValue].
    displaySmall = baselineTypography.displaySmall.copy(fontSize = 44.sp, lineHeight = 52.sp),
    headlineLarge = baselineTypography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 48.sp),
    headlineMedium = baselineTypography.headlineMedium.copy(fontSize = 34.sp, lineHeight = 42.sp),
    headlineSmall = baselineTypography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = baselineTypography.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
    // The tile's name, on every tile: the fourth slot of `TileCard`'s anatomy.
    titleMedium = baselineTypography.titleMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleSmall = baselineTypography.titleSmall.copy(fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = baselineTypography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    // Every status line on the wall, the group failure sentence, and the lights group's one line.
    bodyMedium = baselineTypography.bodyMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
    // The floor, and nothing in `panel/` uses it any more. It is 16sp rather than 12 so that a slot
    // nobody looked at cannot be the one thing on the wall set at phone size.
    bodySmall = baselineTypography.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = baselineTypography.labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
    labelMedium = baselineTypography.labelMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
    labelSmall = baselineTypography.labelSmall.copy(fontSize = 16.sp, lineHeight = 20.sp),
)
