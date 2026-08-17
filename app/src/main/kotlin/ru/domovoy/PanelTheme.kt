package ru.domovoy

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

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
