# Marine Ops UI nouns

Status: SHIPPED — retained UI foundation proven in-engine
Written: 2026-08-23
Updated: 2026-08-24 — live-accepted layout, typography, input, canvas, theme, transition, MLX, binding, and reload contracts.

## Purpose

The Marine Ops screens are a full-canvas application hosted inside a Starsector
custom visual dialog. Starsector owns the window, dialog lifetime, and the final
rectangle. The mod owns the document inside that rectangle: layout, paint order,
input routing, navigation, and the projection of game state.

This feature owns the reusable presentation machinery. It does not own company,
mission, roster, armory, or battle facts. A surface reads those domains through a
view model and invokes their commands; it never becomes a second authority for
them.

## Vocabulary

- A **host viewport** is the final rectangle Starsector grants to a custom panel.
  Its origin is expressed in Starsector's bottom-left, Y-up UI coordinates.
- A **document** is one retained element tree laid out against a host viewport.
  Its own coordinate space starts at the top-left and grows downward so authored
  layout follows the same convention as HTML and CSS.
- An **element** is a retained node with stable identity, ordered children,
  presentation state, and optional interaction behavior.
- A **layout box** is the computed border rectangle of one element. Painting and
  hit-testing consume the same box; neither independently derives geometry.
- A **layout context** arranges a parent's children. The initial contexts are row,
  column, and stack; later capability stories may add grid and absolute placement.
- **Overflow** is CSS's relationship between a box and content that exceeds it.
  `visible` is the default; `hidden` and `scroll` establish the same padding-box
  clip, while `scroll` additionally promises navigation chrome and input.
- A **painter** projects document-space boxes into Starsector's UI-space render
  callback and restores every OpenGL state it changes.
- A **theme** maps semantic component roles and interaction states to typography,
  color, spacing, and decoration. Domain screens do not own a second palette.
- A **binding** is an explicit invalidation edge from view-model state to one
  element mutation. It updates retained identity rather than rebuilding the
  document.
- A **signal** is a value in one reactor graph. Reading it while a binding or
  computed value evaluates records the dependency; writing a different value
  invalidates only those readers.
- An **MLX component** is one authored template plus an optional component-scoped
  style sheet. It builds ordinary retained elements and receives no privileged
  layout, input, paint, or cascade behavior.
- A **reload** is an explicit development transaction: every known edited component
  parses successfully before the registry changes, then a fresh document is built
  from the existing view model. Domain and view-model state are not component state.
- A **canvas element** is the procedural escape hatch for visuals that do not fit
  ordinary boxes: formation connectors, graphs, paper dolls, or transaction flows.
- A **surface** is a document plus its view model, navigation behavior, and host
  lifecycle. Fleet Armory, Company HQ, and the UI workbench are surfaces.

## Coordinate spaces

There are three distinct spaces:

1. **Framebuffer pixels**, used by OpenGL scissors and offscreen targets.
2. **Starsector UI coordinates**, used by `PositionAPI` and `InputEventAPI`.
3. **Document pixels**, used by retained layout with a top-left origin.

One host adapter owns each conversion. Layout never reads the framebuffer, and
elements never add the dialog origin themselves. UI scale is observed at the host
boundary rather than guessed from the physical monitor.

## End-to-end flow

For a stable frame:

```
domain invalidation -> view model -> bindings -> layout if geometry changed
                                              -> paint if appearance changed

Starsector input -> host coordinate conversion -> reverse-order hit test
                                                   -> element behavior
```

The first implementation may repaint the whole document after a change. The
standing contract is still that unchanged state preserves element identity and
does not reconstruct the tree. Incremental paint ranges are an optimization, not
the retained model.

## Design laws

1. **One geometry authority.** Layout boxes drive paint, clipping, hover, click,
   focus, and procedural-canvas coordinate mapping.
2. **Retain identity.** Selection, hover, focus, scroll, and transition state live
   on enduring elements and survive ordinary view-model updates.
3. **Projection is not authority.** A surface may stage text-field state and
   selection, but roster and armory mutations still cross their existing command
   seams.
4. **The host boundary is explicit.** Nothing assumes control outside the granted
   custom-panel rectangle or assumes that a requested screen fraction was granted.
5. **Layout is resolution-aware.** Screens compose from intrinsic sizes, flexible
   tracks, and bounded regions rather than one monitor's absolute coordinates.
6. **Paint order and hit order agree.** Later content paints above earlier content
   and is tested first. Invisible or clipped content cannot receive a click.
7. **OpenGL state is borrowed.** A painter or canvas producer restores programs,
   buffers, textures, scissors, blend state, and matrices that it changes.
8. **Standard vocabulary wins.** When HTML or CSS already names a layout or input
   concept, the toolkit uses that name and meaning. The supported surface may be a
   subset; it does not become a private lookalike dialect.
9. **Java is the authority.** Any later markup format must produce the same tree
   the Java API produces and receives no privileged layout or behavior path.
10. **Exceptional drawing stays exceptional.** A new visual does not require a new
    element kind when a canvas producer can express it.

## Intrinsic text and typography

Single-line text is measured through one document-owned seam used by both layout and paint. In a
row, an auto-sized text element contributes its glyph advance plus padding and border before free
space is distributed; in a column, its line height contributes on the main axis. A declared size
still wins. Changing retained text marks geometry dirty so the next document update buys one layout
pass rather than leaving a stale box.

Typography roles remain ordinary semantic classes and inherited CSS properties. The theme supplies
a regular body face for controls, values, and prose and a display face for headings. Casing belongs
to authored copy: all caps is a heading treatment, not a global font policy. Horizontal placement is
`text-align`; button widgets center their measured line box vertically inside the content box.

## Overflow and clipping

An element paints its own background and border under the clip inherited from its
ancestors. Its content and descendants then inherit the intersection of that clip
and its padding box when `overflow` is `hidden` or `scroll`. This is the CSS rule:
the border remains visible while content cannot paint through it.

The one `UiLayoutEngine.clipForChildren` expression is consumed by both the painter
and reverse-order hit test. A clipped-away child is therefore neither visible nor
clickable. Only the OpenGL backend converts that document rectangle into physical
framebuffer pixels; the conversion observes Starsector UI scale and flips the
top-left document Y axis exactly once.

## Vertical scrolling

`scrollTop` is retained content state on the element, while `scrollHeight` is a
layout result on its box. Layout clamps the effective offset to
`max(0, scrollHeight - content height)` and translates descendants only: the
scroll surface, padding-box clip, and host viewport do not move.

Wheel input uses the geometric target chain rather than a clickable-only hit. It
walks from the deepest element outward, skips `overflow: hidden`, and moves the
nearest `overflow: scroll` surface that has room in the requested direction. At a
nested boundary the next movable ancestor receives the delta. At the Starsector
host seam, a wheel aimed at a retained scroll surface is still consumed when every
candidate is at its boundary, preventing the campaign layer from receiving the
same notch.

The scrollbar thumb is overlay chrome painted after descendants and clipped to the
surface's padding box. It advertises position and range but does not reserve layout
space or participate in hit-testing.

## Input, focus, and capture

Starsector `InputEventAPI` objects and LWJGL integer key codes end at one host
adapter. The retained tree receives document-space coordinates, pointer buttons
named by role, named keys, and a modifier set. Character input remains a separate
future seam; a key press is not text.

Geometric targeting and action eligibility are distinct. The deepest painted box
under the pointer is the event target even when it is a non-clickable label; pointer
events bubble through its retained parents, while click and focus defaults search
for the nearest eligible ancestor. Hover is likewise an ancestor chain.

Each document has at most one focused element. Tab traverses eligible elements in
document order, `tabIndex=-1` remains directly focusable but is skipped, and
disabled controls are ineligible. Enter confirms the focused control, Space uses a
press/release action, and unmodified Escape belongs to the document cancel seam.
Focus acquired by keyboard is visibly distinguished from focus acquired by pointer.

Pointer capture is explicit rather than an automatic consequence of pressing.
While held, pointer movement, hover, and release retarget to the captured element;
primary release dispatches before ending capture. Leaving a screen clears active
input state, while a resize/position callback only relayouts the enduring document.

## Procedural canvas

A canvas has two independent sizes. Its integer surface width and height define the
producer's local coordinate space; its retained content box defines where that
surface is rendered. With no authored rendered size, the surface provides the
canvas's intrinsic layout size. If both differ, each axis maps by its own ratio just
as an HTML canvas stretched by CSS would.

`CanvasMetrics` owns both directions of this mapping. Document pointer coordinates
are never decorated with invented canvas fields; a canvas handler explicitly calls
`toCanvasX` and `toCanvasY`. Samples delivered under capture may therefore be
negative or beyond the surface edge. UI scale does not alter the mapping because
both sides are document-space quantities; device-pixel ratio is separate metadata.

A document-owned registry associates attached canvas identity with one Java
producer. The producer draws deterministic projection state through a bounded
`CanvasContext`, not raw OpenGL, after the element background and before following
content. Its output is clipped to the canvas content box intersected with ancestor
clips, and its `visibleBounds` is expressed in canvas-local units.

## Authority boundaries

- Starsector owns the campaign UI, custom-dialog placement, callback cadence, and
  final screen composition.
- The retained UI toolkit owns document geometry, paint/hit ordering, interaction
  state, styling, and host-space conversion inside the granted rectangle.
- Each game feature owns its view model and commands. The toolkit imports no
  company, campaign, or battle type.
- Existing immediate widgets remain supported while surfaces migrate. A retained
  surface may coexist with legacy widgets in an explicit render order, but one
  interactive region must have one input owner.

## Extension points

- Add layout or style capabilities only when a real surface needs them.
- Add procedural visuals through the canvas seam before expanding the ordinary
  element vocabulary.
- Add authored component files only after the Java tree, layout, paint, and input
  contracts are proven in-engine.
- A new screen adopts the retained stack as one surface; migration does not require
  a flag day across every Marine Ops screen.
