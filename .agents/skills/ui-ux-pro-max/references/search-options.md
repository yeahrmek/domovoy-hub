# Search options

Read for domain/stack selection or advanced `scripts/search.py` options. Invoke the script as described in SKILL.md; commands below assume `skill_dir` is the absolute skill directory.

The local catalog contains 79 searchable styles (50 active), 192 product palettes and exact reasoning profiles, 74 font pairings, 119 UX guidelines, 105 curated icons, 17 GSAP presets, 25 chart types, and 22 stacks.

## Domains and stacks

Use `--domain NAME` for a focused search; `-n NUMBER` limits results (1–20, default 3).

| Domain | Search for | Example query |
|---|---|---|
| `product` | Product patterns | `entertainment social` |
| `style` | Visual styles | `glassmorphism dark` |
| `color` | Palettes | `entertainment vibrant` |
| `typography` | Font pairings | `playful modern` |
| `google-fonts` | Individual fonts | `sans serif popular variable` |
| `chart` | Chart selection | `real-time dashboard` |
| `ux` | UX outcomes | `error summary validation` |
| `landing` | Landing composition | `hero social-proof` |
| `icons` | Icons and semantics | `decorative icon aria hidden` |
| `gsap` | GSAP motion presets | `scroll reveal stagger` |
| `react` | React/Next.js performance | `rerender memo list` |
| `web` | App/native guidelines (despite name) | `accessibilityLabel touch safe-areas` |

Use `--stack NAME` for implementation guidance: `react`, `nextjs`, `vue`, `svelte`, `astro`, `nuxtjs`, `nuxt-ui`, `angular`, `laravel`, `swiftui`, `react-native`, `flutter`, `jetpack-compose`, `html-tailwind`, `shadcn`, `threejs`, `javafx`, `wpf`, `winui`, `avalonia`, `uno`, `uwp`.

`--design-system` accepts `-p "Project Name"`, `-f ascii` (default terminal output), `-f markdown` (documentation), or `--json` (raw design-system dict plus persistence status).

## Save a design system

```bash
python3 "$skill_dir/scripts/search.py" "<product> <industry> <direction>" \
  --design-system --persist -p "Project Name" --output-dir "<project-root>"
```

Creates `design-system/<project-slug>/MASTER.md` and `pages/`. Add `--page "dashboard"` for `pages/dashboard.md`. Existing-file handling and authorization for `--force` are defined in SKILL.md; always supply the project root because the default output directory is cwd.

## Optional design dials

Each dial accepts 1–10 with `--design-system`; omit it to retain unchanged default behavior.

| Dial | Low (1–3) | Mid (4–7) | High (8–10) |
|---|---|---|---|
| `--variance` | Centered/minimal; Minimalism bias | Balanced/modern | Bold/asymmetric; Brutalism/Bento bias |
| `--motion` | Subtle micro-interactions | Standard scroll/stagger | Complex pin/Flip/SplitText choreography |
| `--density` | Spacious, 24–96px | Standard/default, 16–64px | Dense dashboards, 8–32px |

`--motion` attaches a GSAP snippet from the matching `gsap` tier (Subtle/Standard/Complex), including framework, Do/Don't, and performance notes. `--density` overrides the `--space-*` CSS variable table in ASCII/Markdown/MASTER.md output; useful for dense dashboards versus marketing pages.

```bash
python3 "$skill_dir/scripts/search.py" "internal analytics dashboard" \
  --design-system --variance 8 --motion 7 --density 8 -p "Ops Console"
```
