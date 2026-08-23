# U2 — Clipping, scrolling, focus, and canvas input

Status: PLANNED
Written: 2026-08-23

Read `ui-nouns.md` and `ui-toolkit.md` first. Depends on U1.

## Outcome

Retained surfaces can contain long libraries and editable workflows without
screen-local event plumbing: nested clips and scroll offsets agree across layout,
paint, and input; focus and pointer capture survive ordinary updates; procedural
canvases receive coordinates in their own surface space.

## Scope

- Per-element overflow clipping with nested clip intersection and framebuffer-safe
  scissor conversion.
- Vertical scroll containers, wheel routing to the nearest movable ancestor, visible
  range/thumb feedback, and retained scroll position.
- One focused element per document stack, tab traversal, confirm/cancel actions, and
  named keys above the LWJGL translation seam.
- Explicit pointer capture for drags and release outside the original bounds.
- Procedural canvas registration, invalidation, visible bounds, and document-to-canvas
  coordinate mapping.
- Workbench cases and headless tests for every capability.

## Acceptance

- Content outside an ancestor clip neither paints nor receives input.
- A scrolled-out row cannot be clicked and scroll state survives retained updates.
- Wheel input is consumed over a bounded panel even when the panel cannot move farther.
- Tab, Enter, Space, and Escape have deterministic document behavior.
- A captured drag continues outside the element and releases capture reliably.
- Canvas paint and canvas input use inverse mappings at every supported UI scale.
