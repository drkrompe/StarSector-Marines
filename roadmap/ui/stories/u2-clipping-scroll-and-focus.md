# U2 — Clipping, scrolling, focus, and canvas input

Status: IN PROGRESS — overflow clipping and vertical scrolling implemented; focus, capture, and canvas remain
Written: 2026-08-23
Updated: 2026-08-23 — ported MoonLight's retained scroll state, layout extent, wheel ancestry, and overlay thumb.

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

## Implemented slice — overflow clipping

- `Overflow.VISIBLE`, `HIDDEN`, and `SCROLL` preserve CSS's names and meanings.
- `UiLayoutEngine.clipForChildren` intersects clipping overflow with the padding
  box and is the single geometry rule used by paint and hit-testing.
- `UiPainter` carries inherited clips down the retained tree and converts them to
  physical OpenGL scissors through one UI-scale-aware Starsector adapter.
- The workbench's bounded panels, labels, and buttons opt into hidden overflow so
  long content cannot paint across adjacent regions.
- Headless tests cover visible overflow, padding-box hit clipping, nested geometry,
  and top-left document to bottom-left framebuffer conversion.

This is deliberately a port of MoonLight's contract rather than a legacy Armory
widget patch. The existing Armory screenshot is acceptance evidence for C15: the
production rewrite must move onto this retained geometry before the flat widget
screen can be retired.

## Implemented slice — vertical scrolling

- `scrollTop` lives on stable `UiElement` identity; `LayoutBox` records the current
  `scrollHeight` and derives the bottom clamp from its content box.
- Layout first arranges ordinary retained boxes, measures the direct content extent,
  and then translates descendants by the effective clamped offset. Paint and input
  therefore move together without a second geometry path.
- Wheel targeting starts from the deepest geometric element, including non-clickable
  rows, and walks outward to the nearest `overflow: scroll` ancestor that can move.
  A blocked inner surface hands the wheel to a movable outer one.
- Starsector's raw wheel sign and platform-specific magnitude are normalized once at
  the workbench host boundary. A notch aimed at a retained scroll surface remains
  consumed at its terminal boundary so campaign input cannot act beneath it.
- A four-pixel overlay thumb paints after the scrolled children at the padding edge.
  It is visual browser chrome, not an element and not a hit target.
- The workbench template library is now a bounded eight-row scroll proof. Headless
  tests cover both clamps, retained offset, shifted hit-testing, nested handoff,
  terminal consumption, fitting/visible content, and thumb travel.

## Acceptance

- Content outside an ancestor clip neither paints nor receives input.
- A scrolled-out row cannot be clicked and scroll state survives retained updates.
- Wheel input is consumed over a bounded panel even when the panel cannot move farther.
- Tab, Enter, Space, and Escape have deterministic document behavior.
- A captured drag continues outside the element and releases capture reliably.
- Canvas paint and canvas input use inverse mappings at every supported UI scale.
