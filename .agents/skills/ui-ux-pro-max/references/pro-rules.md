# Native/mobile UI delivery checklist

Read before delivering native/mobile app UI (iOS, Android, React Native, Flutter), or when app polish problems are unclear. Verify every applicable item below. For web/desktop use [quick-reference.md](quick-reference.md); native touch, safe-area, and gesture conventions do not transfer 1:1.

## Process

- [ ] Run only interface-relevant searches and review quick-reference §1–§3 (accessibility, interaction, performance) as the final pass.
- [ ] Test small phone (including 375px width), large phone, and tablet in portrait and landscape; verify reduced motion and Dynamic Type/largest system text without broken layouts.
- [ ] Test light and dark themes independently, including all interaction states; do not infer one from the other.

## Icons and visual quality

- [ ] Use scalable, themeable SVG/platform vector icons, not raster icons that blur or font-dependent emoji as navigation/settings/system controls. Use one family and style per hierarchy level; keep stroke widths consistent within a visual layer (e.g. 1.5px or 2px), with icon-size tokens (e.g. icon-md = 24pt).
- [ ] Align icons to text baselines with consistent padding. Use official brand assets with correct proportions, colors, spacing, and clear space; do not guess paths or modify/recolor assets outside their guidelines.
- [ ] Use color/opacity/elevation transitions for pressed feedback without shifting layout bounds or causing jitter.
- [ ] Use semantic theme tokens for surfaces, text, and icons rather than per-screen hex values. Keep light surfaces clearly distinct from the background with sufficient opacity/elevation.

## Interaction

- [ ] Give every tappable element clear pressed feedback (ripple/opacity/elevation); the app guidance specifies 80–150ms, while quick-reference §3 specifies feedback within 100ms. Choose timing that satisfies the applicable platform guidance.
- [ ] Provide hit areas >=44×44pt on iOS and >=48×48dp on Android, expanding beyond smaller visual icons; never substitute one platform's units for another's.
- [ ] Choose shared motion tokens by distance, complexity, platform, and context; verify responsiveness rather than copying one duration/easing to every transition.
- [ ] Make disabled controls semantically disabled, visually reduced in emphasis, and non-interactive.
- [ ] Prefer native interactive primitives (`Button`, `Pressable`, platform equivalents) with correct roles over generic containers lacking semantics.
- [ ] Keep one primary gesture per region; avoid nested or overlapping tap/drag/back-swipe conflicts and accidental actions.

## Theme and contrast

- [ ] Maintain >=4.5:1 for normal primary/secondary/body text in both themes; 3:1 applies only to large text or meaningful non-text UI, including icons/control boundaries against adjacent colors. Decorative icons must not convey information.
- [ ] Keep dividers/borders visible and pressed/focused/disabled states distinguishable in both themes.
- [ ] Measure modal/drawer scrims against the actual composed background; choose enough separation for foreground legibility instead of reusing an unchecked opacity.

## Layout

- [ ] Keep headers, tab bars, CTA bars, and touch targets clear of top/bottom safe areas, status/navigation bars, gesture indicators, and OS chrome. Inset scroll content so fixed/sticky bars do not hide it.
- [ ] Keep predictable content widths by device class; increase gutters on larger widths and in landscape.
- [ ] Use a consistent 4/8dp spacing rhythm and clear hierarchy tiers (e.g. 16/24/32/48) for components, sections, and pages.
- [ ] Keep long-form text measure readable on tablets; avoid edge-to-edge paragraphs.

## Accessibility and recovery

- [ ] Match screen-reader focus order to visual order, with descriptive labels. Hide decorative icons beside visible text from the accessibility tree (`aria-hidden="true"` on web or native equivalent); give meaningful standalone images/icons a text alternative. Icon controls need accessible names and applicable selected/pressed/expanded state; semantics follow use, not glyph identity.
- [ ] Label form fields, provide hints and clear inline errors, and retain inline errors after failure. After a multi-error submission, focus a linked error summary.
- [ ] Convey meaning with more than color. Keep keyboard focus clear of sticky UI and overlays.
- [ ] Provide button/keyboard alternatives for dragging and swipe-only actions.
- [ ] Allow password managers/paste and a non-cognitive authentication alternative.
- [ ] Give auto-rotating content pause/stop controls; stop it on focus or reduced motion.
