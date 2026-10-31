---
name: ui-ux-pro-max
description: "Search local UI/UX guidance when designing, building, reviewing, or fixing web, mobile, and desktop interfaces: layout, interaction, accessibility, typography, color, charts, and stack-specific implementation."
---

# UI/UX Pro Max

Use for visual design, interface structure, interaction, and UX quality; skip backend, infrastructure, and other non-visual work. Search results are recommendations subordinate to user and repository rules. They do not authorize package installation, OS changes, or unrelated work. Keep private project data out of queries and persisted output.

## Choose a search

Identify product, audience/use context, visual direction, and implementation stack. Detect the stack from project files (`package.json`, `pubspec.yaml`, `*.xcodeproj`/`Package.swift`, `composer.json`, or Android Gradle files); React Native needs its project markers plus the `react-native` dependency. Never assume a stack; ask when it cannot be detected and affects the guidance.

Invoke `scripts/search.py` by its absolute path, resolved from this SKILL.md's directory. It requires Python 3.x and only the standard library; try `python3`, `python`, then `py -3`. Do not install a runtime automatically.

```bash
# Set to the absolute directory containing this SKILL.md, independent of cwd.
skill_dir="/Users/ykapushev/Work/gitlab/domovoy-hub/.agents/skills/ui-ux-pro-max"
python3 "$skill_dir/scripts/search.py" "keyboard focus modal" --domain ux
python3 "$skill_dir/scripts/search.py" "touch target accessibility" --stack jetpack-compose
python3 "$skill_dir/scripts/search.py" "smart home dashboard calm" --design-system -p "Domovoy Hub"
```

- **New page/project or product-wide visual direction:** use `--design-system` (required for new pages/projects). It combines product/style/color/landing/typography matches with `ui-reasoning.csv`, returning pattern, style, palette, typography, effects, and anti-patterns.
- **Targeted component or UX issue:** use one explicit `--domain`.
- **Implementation guidance:** use the detected `--stack`; add a domain query only for a distinct design concern.

Use 2–5 meaningful terms for one dominant intent plus a useful product/platform/interaction constraint. Check the returned domain/category, top result identity, and fit for the product and platform. Domain auto-detection can confuse overlapping terms such as fonts: pass `--domain` explicitly if misrouted. For empty or off-topic output, retry once with a narrower query or explicit domain/stack. If that fails, report no verified match, use the built-in review guidance/quick-reference as the fallback, and label it explicitly. Never fabricate matches or persist unverified output.

For accessibility and text-layout bugs, search the observable UX outcome before implementation details; do not substitute a framework keyword or generic accessibility match for a specific interaction/WCAG criterion. Use a component domain if needed, then the detected stack:

- Accessibility: `"error summary validation"`, `"focus not obscured"`, `"dragging movements"`, `"accessible authentication"` with `--domain ux`; icon semantics: `"decorative icon aria hidden"` or `"icon button accessible label"` with `--domain icons`.
- Text/compact UI: `"orphan heading line balance"`, `"badge chip label wraps"`, `"live badge count screen reader"`, `"rapid chip animation interrupted"` with `--domain ux`; then, for example, `"chip badge overflow nowrap" --stack html-tailwind` only for that detected stack.

Use [search-options.md](references/search-options.md) for the domain/stack catalog, output formats, optional design dials, and persistence commands. Run only searches relevant to the task; synthesize verified results into the implementation.

## Preserve saved design decisions

Before generating for an existing project/page, read `design-system/<project-slug>/MASTER.md` and any `pages/<page-name>.md`; page rules override Master, otherwise Master applies.

For saving across sessions, use `--persist` with `--output-dir` set explicitly to the project root. Existing Master/page files are skipped; a new page may be added without changing Master. Read existing files before considering replacement and use `--force` only with explicit user authorization.

## Review guidance

Read [quick-reference.md](references/quick-reference.md) for UI audits or when a category needs detail; otherwise search the relevant rules on demand. Priorities below retain their order even where impact labels differ:

| Priority | Category / impact | Domain |
|---|---|---|
| 1 | Accessibility / critical | `ux` |
| 2 | Touch & interaction / critical | `ux` |
| 3 | Performance / high | `ux` |
| 4 | Style selection / high | `style`, `product` |
| 5 | Layout & responsive / high | `ux` |
| 6 | Typography & color / medium | `typography`, `color` |
| 7 | Animation / medium | `ux`, `gsap` |
| 8 | Forms & feedback / medium | `ux` |
| 9 | Navigation / high | `ux` |
| 10 | Charts & data / low | `chart` |

For web performance, reserve layout space and target CLS <0.1. Apply platform units and guidance to native UI; do not transplant web CSS rules or pixel thresholds.

Useful quick-reference rule IDs by issue: dark mode → §6 `color-dark-mode`, `color-accessible-pairs`; motion → §7 `spring-physics`, `easing`, `exit-faster-than-enter`; forms → §8 `inline-validation`, `error-clarity`, `focus-management`; navigation → §9 `nav-hierarchy`, `bottom-nav-limit`, `back-behavior`; layout → §5 `mobile-first`, `breakpoint-consistency`; performance → §3 `virtualize-lists`, `main-thread-budget`, `debounce-throttle`.

Before delivering native/mobile app UI, read and verify the single checklist in [pro-rules.md](references/pro-rules.md). Also use it when app polish problems are unclear from the category guidance. For web/desktop review use quick-reference instead; native touch, safe-area, and gesture conventions do not transfer 1:1.
