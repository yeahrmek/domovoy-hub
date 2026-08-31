package ru.domovoy

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * The panel's two colour schemes, written out.
 *
 * `lightColorScheme()` with no arguments is not a palette, it is Material's *baseline*. So the
 * values are here, and this is the one file in the app allowed to hold any: `panel/` is grep-clean
 * of hex literals and stays that way. It is not a wrapper with one caller — it is data, and the
 * alternative is 48 arguments inline in [MainActivity].
 *
 * ## One accent, and everything else neutral
 *
 * **It was two seeds and it is one.** A cool blue for climate and a warm amber for light, spent on
 * the glyph, the promoted value, the on mark and the slider of every tile. Photographed off the
 * wall, that palette does not survive the tablet it runs on: the Galaxy Tab sits behind Samsung's
 * blue light filter at level 7 with Extra dim at 25 %, which is a warm film over the whole screen.
 * It turned the cool-grey surfaces beige, and it turned the amber accent brown — so the wall's two
 * families came out as *beige and browner beige*, and the one thing on it that must never be
 * mistaken for anything else, red, was the nearest neighbour of the second family.
 *
 * So: **neutral surfaces, one violet accent, and red reserved.** Violet is chosen for the filter
 * rather than in spite of it — a warm film subtracts blue, and what a violet loses is saturation
 * rather than lightness, so it stays legibly *other* than the greys around it where an amber stops
 * being other than a brown. The accent is `#7047EB` in light. Nothing else on the wall is coloured.
 *
 * | Role | What wears it | Weakest ratio on any tile step |
 * | --- | --- | --- |
 * | `primary` | the accented power button, the slider fill, the on mark | 4.0 light / 4.2 dark |
 * | `onSurface` | every word on every tile, **the promoted value included** | 11.4 / 8.8 |
 * | `error` | the offline glyph and the group-failure outline | 4.7 / 7.4, with `onError` at 5.9 / 7.7 |
 *
 * The accent carries no text after this change — the promoted value went neutral with the rest of
 * the words — so its bar is 3:1 for a graphical object and not 4.5:1. It clears 3 on all four steps
 * in both schemes.
 *
 * **The dark accent is a lighter tone of the same violet, and that is deliberate.** `#7C4DFF` was
 * the obvious partner to the light `#7047EB` and it does not work: against the dark card at
 * `#30333D` it is 2.6:1, under the 3:1 a fill needs, and it is the *blue* channel carrying nearly
 * all of it — precisely the channel the tablet's filter takes away, so at 19:00 the power button
 * would fade into the card it sits on. `#B49CFF` is the same hue two tones up: 4.2:1 on the
 * brightest card step, and high enough in red and green to stay lighter than the card when the
 * filter drains the blue. `#7C4DFF` is still here, as dark's `primaryContainer`.
 *
 * **`secondary` and `tertiary` are neutrals now, and nothing in `panel/` reads either.** They were
 * the light family and the neutral family; with one accent there is no second or third family for
 * them to be. They stay written out — as violet-tinted greys rather than as a fourth and fifth hue —
 * for the reason the `fixed` roles do: an unstyled Material component that reaches for one must not
 * open a colour the wall never chose.
 *
 * ## The surfaces
 *
 * The five `surfaceContainer` steps are the panel's whole tile ramp — which step a tile sits on is
 * its *mood*, and `surface` in `panel/TileLayout.kt` is the table. Two of the five values are the
 * ones this change is specified in: **`#E5E7EC` is the resting card in light and `#30333D` is the
 * resting card in dark**, on `#F5F6F8` and `#191B23` backgrounds. The steps around them are the
 * same colours a little further along: more present for a lit device, less for one nobody has read.
 * `surfaceContainerLowest` sits **a hair off the background on the cards' side**, so a tile with no
 * reading at all all but dissolves into the wall.
 *
 * _That rule is the reverse of the one this file carried until the palette went neutral, and the
 * tablet is what reversed it._ `Lowest` used to sit *past* the background — white on a near-white
 * wall — on the argument that an unread tile should read as a hole rather than as a card. That works
 * only while the cards are barely off the background, which they were: 4 L\* in the old light
 * scheme. The cards are 5 L\* off it on the *other* side now, so "past the background" put the two
 * tiles the panel knows least about — Домофон and Пылесос, permanently `Unknown` because nothing
 * polls a launcher — at the largest separation on the wall. On the glass they were the loudest cards
 * on Главная. A step meaning "asserting least" has to be the step nearest the background, whichever
 * side the cards are on.
 *
 * The error ramp is Material's, untouched: red is the one thing on this panel that must look like
 * everybody else's, and after this change it is also the only saturated thing on the wall that is
 * not the accent.
 *
 * No dynamic colour anywhere. The wallpaper of a kiosk tablet is not a design input, and a palette
 * that moves when somebody changes the launcher background is a panel that stops meaning what it
 * meant yesterday.
 */
internal val panelLightScheme = lightColorScheme(
    primary = Color(0xFF7047EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF20005E),
    inversePrimary = Color(0xFFB49CFF),
    secondary = Color(0xFF5C5A6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E0F0),
    onSecondaryContainer = Color(0xFF191826),
    tertiary = Color(0xFF4C4A5A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEDCEC),
    onTertiaryContainer = Color(0xFF171626),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF202228),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF202228),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceTint = Color(0xFF7047EB),
    inverseSurface = Color(0xFF303138),
    inverseOnSurface = Color(0xFFF1F1F6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC9C7D2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5F6F8),
    surfaceDim = Color(0xFFD6D8DE),
    // The tile ramp. `surfaceContainer` is the resting card this change is specified in; the two
    // above it are a failing tile and a lit one, and `Lowest` is the step nearest the background —
    // see [surface].
    surfaceContainer = Color(0xFFE5E7EC),
    surfaceContainerHigh = Color(0xFFDFE1E7),
    surfaceContainerHighest = Color(0xFFD8DAE2),
    surfaceContainerLow = Color(0xFFECEDF2),
    surfaceContainerLowest = Color(0xFFF2F3F7),
    primaryFixed = Color(0xFFE6DEFF),
    primaryFixedDim = Color(0xFFB49CFF),
    onPrimaryFixed = Color(0xFF20005E),
    onPrimaryFixedVariant = Color(0xFF5730C7),
    secondaryFixed = Color(0xFFE3E0F0),
    secondaryFixedDim = Color(0xFFC7C4D6),
    onSecondaryFixed = Color(0xFF191826),
    onSecondaryFixedVariant = Color(0xFF454355),
    tertiaryFixed = Color(0xFFDEDCEC),
    tertiaryFixedDim = Color(0xFFC3C0D2),
    onTertiaryFixed = Color(0xFF171626),
    onTertiaryFixedVariant = Color(0xFF424050),
)

/**
 * The same accent at the tone dark asks for and the same neutral ramp inverted: the cards come up
 * from `#30333D` as a lit tile asserts more, and the background sits below all of them.
 *
 * Not dead code, and that was checked before it was written: the tablet's dark theme is on a real
 * schedule, 19:00–07:00, so the wall is on this scheme for half of every day and nobody is looking
 * at it when it switches. It is also the half of the day the blue light filter matters most in,
 * which is why `primary` here is a lighter tone than light's rather than a darker one — the
 * argument is on [panelLightScheme].
 */
internal val panelDarkScheme = darkColorScheme(
    primary = Color(0xFFB49CFF),
    onPrimary = Color(0xFF2E0A7A),
    // `#7C4DFF`, the violet this change was specified with. It is too dark to be dark's `primary` —
    // 2.6:1 on the card it would sit on — and it is exactly right as the fill under a white glyph.
    primaryContainer = Color(0xFF7C4DFF),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF7047EB),
    secondary = Color(0xFFC7C4D6),
    onSecondary = Color(0xFF2E2C3B),
    secondaryContainer = Color(0xFF454355),
    onSecondaryContainer = Color(0xFFE3E0F0),
    tertiary = Color(0xFFC3C0D2),
    onTertiary = Color(0xFF2B2A38),
    tertiaryContainer = Color(0xFF424050),
    onTertiaryContainer = Color(0xFFDEDCEC),
    background = Color(0xFF191B23),
    onBackground = Color(0xFFF3F4F7),
    surface = Color(0xFF191B23),
    onSurface = Color(0xFFF3F4F7),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceTint = Color(0xFFB49CFF),
    inverseSurface = Color(0xFFF3F4F7),
    inverseOnSurface = Color(0xFF303138),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3D404A),
    surfaceDim = Color(0xFF191B23),
    surfaceContainer = Color(0xFF30333D),
    surfaceContainerHigh = Color(0xFF383B46),
    surfaceContainerHighest = Color(0xFF41444F),
    surfaceContainerLow = Color(0xFF272A33),
    surfaceContainerLowest = Color(0xFF1F2129),
    // The fixed roles are the same values in both schemes — that is what "fixed" means. Nothing in
    // the panel reads them today; they are here so that a component that does cannot fall back to
    // the baseline through the one door left open.
    primaryFixed = Color(0xFFE6DEFF),
    primaryFixedDim = Color(0xFFB49CFF),
    onPrimaryFixed = Color(0xFF20005E),
    onPrimaryFixedVariant = Color(0xFF5730C7),
    secondaryFixed = Color(0xFFE3E0F0),
    secondaryFixedDim = Color(0xFFC7C4D6),
    onSecondaryFixed = Color(0xFF191826),
    onSecondaryFixedVariant = Color(0xFF454355),
    tertiaryFixed = Color(0xFFDEDCEC),
    tertiaryFixedDim = Color(0xFFC3C0D2),
    onTertiaryFixed = Color(0xFF171626),
    onTertiaryFixedVariant = Color(0xFF424050),
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
