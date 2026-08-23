# Marine Ops UI nouns

Status: ACTIVE — four foundation stories open
Written: 2026-08-23

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
- A **painter** projects document-space boxes into Starsector's UI-space render
  callback and restores every OpenGL state it changes.
- A **theme** maps semantic component roles and interaction states to typography,
  color, spacing, and decoration. Domain screens do not own a second palette.
- A **binding** is an explicit invalidation edge from view-model state to one
  element mutation. It updates retained identity rather than rebuilding the
  document.
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
