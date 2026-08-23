# U1 — Retained layout workbench

Status: IN PROGRESS — implementation and headless verification complete; live acceptance pending
Written: 2026-08-23
Updated: 2026-08-23 — retained tree, layout, painter/input parity, and Company HQ workbench entry implemented.

Read `ui-nouns.md` and `ui-toolkit.md` first.

## Problem

The custom dialog already grants broad pixel and input control, but the current flat
widget list does not prove that a retained hierarchy can coexist safely with
Starsector's panel lifecycle and coordinate system. Beginning the Fleet Armory
rewrite before proving that seam would mix host discovery, framework design, and
product information architecture in one change.

## Outcome

A developer can open a retained UI workbench from Company HQ. The workbench fills
the granted custom-panel rectangle, reports that viewport, and presents a responsive
three-pane Fleet Armory sketch built from stable nested elements. Clicking fire teams
and templates updates retained elements without rebuilding the tree.

## Scope

- Document-space geometry with a top-left origin and a single conversion at the
  Starsector host boundary.
- Retained elements with ordered children and stable layout boxes.
- Row, column, and stack layout with padding, gaps, preferred sizes, flexible growth,
  and start/center/end/stretch cross alignment.
- Box, border, and text painting through the existing OpenGL/font infrastructure.
- Reverse-paint-order hover and armed-click behavior from the same boxes used to draw.
- A dev-only workbench screen reachable from the campaign Company HQ and safe in its
  planet-free host.
- Headless layout and hit-order tests.

## Not in this story

- Scissor clipping, scroll offsets, keyboard focus, pointer capture, text editing, or
  canvas producers — U2.
- Stylesheets, transitions, or component themes — U3.
- `.mlx`, signals, bindings, keyed list reconciliation, or reload — U4.
- Production Fleet Armory replacement — `c15-retained-fleet-armory.md`.

## Acceptance

- The document's root box exactly equals the granted viewport in document pixels.
- Nested row and column children divide remaining space by declared growth after
  preferred sizes and gaps are paid.
- A stack can overlay a centered bounded child without changing sibling layout.
- Painting and hit-testing consume the same retained `LayoutBox` instances.
- Later-painted overlapping content receives input first.
- A press only clicks when its release resolves to the same element.
- Hover, selected team, and selected template survive updates because the workbench
  mutates retained elements rather than rebuilding its document.
- Back returns to Company HQ and the workbench never routes a planet-free context to
  a planet-dependent screen.
- The workbench visibly marks all four viewport edges and displays the granted width
  and height.
- Unit tests cover flexible allocation, nested box placement, hit order, and armed
  click cancellation.
- The Java 17 build and focused tests pass.
