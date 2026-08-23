# Retained UI toolkit direction

Status: ACTIVE
Written: 2026-08-23

## Decision

Marine Ops will grow a retained document UI above Starsector's custom-panel host.
The direction deliberately follows MoonLightEngine's gameplay UX workflow:
stable elements, one box tree shared by layout and input, component-owned styles,
explicit bindings, and an authored component format layered over an authoritative
Java API.

The rendering backend does not transfer wholesale. MoonLight owns a Vulkan frame
graph and post-tone-map overlay; Marine Ops borrows an OpenGL compatibility context
inside Starsector's UI pass. Its painter therefore targets the existing sprite,
font, batch, shader, and FBO infrastructure and treats GL-state restoration as part
of every rendering contract.

## Why retained

The current `WidgetRoot` is a flat immediate list. Screens rebuild that list after
selection and domain changes, manually repeat layout arithmetic, and separately
encode the rectangles that draw and the rectangles that receive input. That was a
good bootstrap and remains suitable for small diagnostic overlays. It is the wrong
authoring cost for long-lived management applications such as Fleet Armory.

A retained tree gives the UI stable identity. It makes nested layout, scroll,
focus, transitions, component styling, and hot reload coherent rather than a set of
screen-local side tables. It also permits headless geometry and input tests without
booting Starsector or an OpenGL context.

## Fidelity law

Every public layout, input, element, attribute, and style name that HTML5 or CSS
already defines uses the standard name for the standard behavior. The toolkit is a
subset, not a renamed dialect. Unsupported capabilities fail explicitly once an
authoring format exists; they do not silently parse and behave differently.

This law is about the reusable toolkit, not game-domain language. Fleet Armory still
says squad, fire team, billet, template, assignment, and arrangement because those
are company-view nouns.

## Layer order

The layers are introduced in dependency order:

1. Retained Java tree, host viewport, boxes, row/column/stack layout, paint, and
   reverse-order hit-testing.
2. Nested clipping, scrolling, focus, keyboard actions, pointer capture, and a
   procedural canvas seam.
3. Theme sheets, semantic component roles, interaction states, and transitions.
4. `.mlx` single-file components, bindings, scoped styles, and explicit development
   reload.
5. Production surfaces, beginning with Fleet Armory.

Markup does not land first. The loader must emit ordinary Java construction calls;
otherwise the convenience format becomes a parallel UI engine and tests exercise a
different path than the game.

## Layout scope

The initial toolkit needs row, column, and stack. Grid, absolute placement, intrinsic
text measurement, and bounded scrolling enter when their consuming stories require
them. Layout is measure-bottom-up and arrange-top-down once intrinsic content lands;
the first workbench may use explicit preferred sizes to prove the retained seam
without pretending that a complete CSS layout engine ships in one slice.

Document coordinates are top-left and Y-down. The Starsector host adapter converts
once to absolute Y-up UI coordinates for paint and converts input back once before
hit-testing. Framebuffer scaling enters only where OpenGL requires physical pixels,
such as scissors and FBO allocation.

## Rendering scope

Ordinary elements paint boxes, borders, sprites, and text through a shared painter.
The existing `BitmapFont`, `SpriteAPI`, render2d batches, shader helper, and FBO
renderers remain valid backend material. A procedural canvas is the escape hatch for
formation lines, equipment illustrations, transaction diagrams, and other visuals
whose geometry should not expand the ordinary layout vocabulary.

The immediate goal is authoring correctness, not one draw call. Batching is pulled by
a profile after real retained surfaces exist. GL state isolation and matching paint
and hit order are correctness requirements from the first slice.

## State and updates

View-model state outlives a document rebuild and domain state outlives the view
model. A binding updates one retained element property and marks the smallest
necessary kind of work: layout when geometry changes, paint when appearance changes.
Collection bindings reconcile by stable key so focus, hover, scroll, and transitions
stay attached to the same row.

The workbench begins with direct retained mutations. General signals and keyed
reconciliation belong to the authoring/binding story, where a real repeated Fleet
Armory surface can prove their API.

## Testing and preview workflow

- Geometry and hit-testing are headless unit tests over document pixels.
- Every host-facing capability receives an in-game workbench case before a production
  screen depends on it.
- Resolution evidence covers common aspect ratios and UI scale 1.0, 1.25, and 1.5.
- The workbench states its granted viewport and draws edge markers, so a host clamp or
  coordinate mismatch is visible rather than inferred from a crowded screen.
- Production surface acceptance includes a screenshot review, but domain behavior is
  asserted below the renderer.

## Rejected directions

- Porting MoonLight's Vulkan backend into Starsector: wrong host and duplicate render
  ownership.
- Building the `.mlx` parser before the Java tree: creates a second authority before
  there is a stable target API.
- Extending the flat widget list with more composite positioning conventions: keeps
  layout, paint, clipping, and hit-testing as separate derivations.
- Rewriting every existing screen at once: prevents the toolkit from being measured
  against a working legacy surface and makes integration all-or-nothing.
