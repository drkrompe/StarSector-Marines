# U3 — Theme, component roles, and transitions

Status: IN PROGRESS
Written: 2026-08-23

Read `ui-nouns.md` and `ui-toolkit.md` first. Depends on U1 and U2.

## Outcome

Marine Ops acquires one authorable visual language. Screens select semantic roles and
states; the theme owns typography, spacing, palette, borders, and retained motion.

## Scope

- Closed style-property surface using CSS names and meanings.
- Tag/class/id/pseudo-state selectors, ordered cascade, inherited text properties,
  and explicit unsupported-property errors.
- Component-scoped rules plus a final theme layer that may reskin components.
- UI-scale-aware lengths and a small responsive set sufficient for Fleet Armory.
- Paint-only color/opacity transitions and bounded layout transitions on unscaled
  real time.
- A reusable Marine Ops theme and workbench state gallery.

## Implementation slices

| Slice | Size | Dependency | Proof |
| --- | --- | --- | --- |
| U3.1 — authored style and cascade | M | U1 retained identity | CSS-named closed properties; tag, class, id, descendant, and interaction-state selectors; ordered component sheets with a final replaceable theme; inherited text properties. |
| U3.2 — retained transitions | M | U3.1 computed styles, U2 interaction states | Color and opacity interpolation, bounded numeric layout interpolation, reversal shortening, and an idle fast path driven by unscaled real seconds. |
| U3.3 — Marine Ops theme gallery | M | U3.1–U3.2 | The workbench uses semantic classes and selected state rather than local palette/state painting, and exposes the standard control states together for live review. |

The Java construction API remains available as inline style. This is the migration seam for
legacy screens, not a second presentation model: inline values enter the same computed cascade and
retain the ordinary last-layer precedence.

## Acceptance

- Hover, active, focus, disabled, and selected treatments require no screen-local GL.
- Theme replacement changes component appearance without changing its tree or view
  model.
- An idle document with no running transition performs no style or layout work.
- Transition reversal does not leave a trail when the pointer crosses adjacent rows.
- Common 16:9, 16:10, and ultrawide viewports remain legible at UI scales 1.0–1.5.
