# Moddable Tilesets

Status: ACTIVE — additive external catalogs shipped; variant-pool cleanup remains

Written: 2026-08-23

Updated: 2026-08-28 — the alpha law now says keying a plate is an edit to the art rather than a per-sheet setting, a silhouette is checked as a shape and never as a total, and a disowned fit's residual does not measure the cut in force.

Read `stories.md` for open work.

## Purpose

Moddable tilesets separate visual content from the code that uses it. Stable
ids describe the available tile, block, and prop content; a separate mapping
describes how existing generation and rendering policies select that content.
The result removes PNG-order and coordinate-table authority from Java while
keeping terrain generation deterministic and its tactical rules explicit.

The feature makes both built-in and contributed catalogs data-driven. Enabled
mods can add tiles, blocks, doodads, and named mapping entries through the
shared marine-catalog manifest. Contributions are additive rather than an
override layer: changing core generation policy remains a deliberate core edit.

## Vocabulary and ownership

- A **tileset definition** is the authoritative description of visual assets on
  one sheet. It may describe sliced tiles, fixed-grid blocks, decorative props,
  and viewer-only cell labels.
- A **tile** is a sliced-sheet visual with semantic metadata such as layer,
  cover, passability, and permitted overlay bases.
- A **grid block** is a named fixed-grid surface. Its named layout remains a
  code algorithm; the definition supplies the asset coordinates or a stable
  variant pool.
- A **doodad** is a prop with an authored source cell and intrinsic tactical
  properties. A doodad definition says what the prop is; a pool says where
  generation may choose it. Prop is a role, not a size or a subject: a
  parked truck is a doodad exactly as a crate is, because neither moves,
  neither ticks, and neither is an entity. Anything that is scenery is
  authored, placed, drawn, and scored as one kind of thing; a second prop
  model for scenery that happens to look important is duplication wearing a
  domain name. One placement may carry a quarter-turn: the source span remains
  the definition's, while its rendered and tactical world footprint turns with
  the art.
- A **mapping definition** is the selection policy between generated concepts
  and registry ids: render-surface dispatch, named doodad pools, and tunables
  for an existing filler. It does not currently select or define a filler
  algorithm.
- A **stable id** is the durable name by which any tile, block, or doodad is
  selected. These three content shapes share one namespace. A dense handle is
  only an in-memory implementation detail for a generated cell, never a
  cross-load identity or save format.
- A **raw sheet** is the art as it was generated or commissioned: an arbitrary
  arrangement of pieces at an arbitrary scale, with no ids and no semantics. It
  is an input to the shipped assets, not one of them.
- An **authoring document** is the annotation of one raw sheet — which piece is
  what, how much deck it covers, which block cell it is, and what it is for. It
  is the record of judgements that cannot be re-derived mechanically, and it is
  the only durable home for them.
- A **packed atlas** is the exported sheet a tileset definition addresses. It
  contains the pieces that were kept, arranged by the packer, and nothing else.
- A **cut** is where a plate's grid sits on its sheet: the stated
  `cols x rows` together with, per axis, the coordinate its first line falls on
  and the distance between lines. A cut is not a division of the canvas. It is
  recorded on the authoring document, so re-cutting a sheet reproduces the same
  cells without measuring it again.

`TileRegistry` owns the merged asset catalog and `GenMappingRegistry` owns
the merged use mapping. The application lifecycle loads the asset catalog
before the mapping, because mapping references must resolve to assets.
Render systems consume surface mappings; mapgen fillers consume pools and
tunables. Fillers, layouts, autotile resolvers, and topology own algorithms
and tactical effects; JSON supplies their content parameters, not executable
generation logic.

## Data and authority flow

At application load, every enabled mod's fixed marine-catalog manifest is
discovered in game load order. Its explicit `tilesets` resources are loaded
from that exact provider into one registry and checked for duplicate ids and
resolvable overlay selectors. Explicit `tileMappings` resources then populate
the mapping registry and validate their tile, block, doodad, and filler-pool
references against the complete asset catalog.
During battle generation, code-owned fillers select mapped pools and values;
during rendering, mapped ids select most ground tiles and blocks. Primary
sliced grass/dirt variants are the current exception: code still owns their
two-item pool membership, which `nature-variant-pool-authority-cleanup.md`
contracts to move into declared content. The same registries are installed from
the bundled JSON in tests so the normal test path exercises production content
rather than a test-only catalog.

The two JSON layers deliberately have different authority:

- Tileset definitions answer **what may be drawn and what it means**.
- Mapping definitions answer **where established code may use it**.

This prevents an art rearrangement from becoming a code edit, without falsely
claiming that data can replace geometry, connectivity recovery, placement, or
other procedural decisions.

## Catalog and mapping behavior

### Id-addressed visual catalog

Both sliced sheets and fixed-grid sheets resolve by explicit ids rather than
enum order or hardcoded origins. Sliced definitions pin their frame explicitly;
fixed-grid definitions name the layout convention or the member cells of a
deterministic variant pool. Per-cell viewer labels — a name, a free-text
description, and descriptive tags — are documentation only and never participate
in generation or combat. They are where a sheet says what a piece is *for*,
which neither an id nor a cover level can express, and are the annotation a
reader (frequently an LLM assembling a map) has to work from.

The registry preserves prior visual behavior: ids and frames are pinned to
their established output, and cell overlay handles are resolved through the
current registry. Changing a frame assignment now requires a reviewable
manifest edit rather than changing an enum declaration order in code.

### Mapping-driven use

Generation now reads data-owned doodad membership and cover, fixed-grid and
fallback ground-render dispatch, and nature-zone pools/chances. Primary sliced
grass/dirt membership remains a declared cleanup exception. This changes where
the source of truth lives, not who owns the algorithm: a nature zone still
carves wetlands, protects connectivity, and spends its seeded random stream in
code; JSON only sets the choices and rates it consumes. Doodad cover is likewise
a property of the data definition, while directional markers and embankment
resolvers remain code-owned geometry.

Nature overlays now exercise that authority boundary end to end. Small rocks
are visual only; medium rocks publish light cover while remaining walkable;
large rocks publish heavy cover and block navigation while remaining
see-through. The filler applies passability, visibility, and connectivity law;
the battle setup publishes authored cover quality into tactical scoring and
ballistic interception. Large rocks are non-structural fixtures rather than
walls: they receive neither wall art nor destructible wall HP.

Passability also selects the cover shape for doodads. A walkable doodad is a
physical feature that may intercept a ray crossing its occupied cell. A
navigation-blocking doodad instead publishes its authored level and height as
directional edge cover on adjacent standable cells; it never publishes both
shapes. Map generation must tag such props as fixtures so finalization does not
promote them to walls. Water is the complementary zero-cover blocker: it is
non-walkable and see-through but explicitly supplies no edge profile.

`GenMappingRegistry` is also the storage location for surface-relief material
overrides. The tile feature owns the mapping container and its load order;
surface relief owns what those height values mean and how rendering uses them.

## Authoring: raw sheet to packed atlas

Content reaches the catalog through an annotation pass. A raw sheet is sliced
into pieces mechanically; each piece is then given a role, an id or block slot,
a footprint, and a usage annotation; the kept pieces are packed into an atlas
and described by a generated tileset definition. The slicing is repeatable and
the annotation is not, so the annotation is saved to an authoring document
beside the raw sheet and can be resumed, corrected, and re-sliced without being
lost.

These properties of that pass are part of the model rather than of the tool:

- **A piece is a doodad or one cell of a block.** Facing is not a property of a
  piece. A wall or a corner is a block whose cells the game selects from the
  four-neighbour mask through its layout, so the authoring act is assigning
  pieces to that layout's slots. Authored content may never carry a per-piece
  facing: that would be a second answer to a question the layout already
  answers, and would move geometry authority out of code into art data. A
  doodad placement may still rotate one canonical piece to fit its generated
  room; the turn belongs to the placement, not the authored piece identity.
- **A block's origin is generated, never counted.** Its cells are addressed as
  origin plus a layout offset, so they must be packed as one contiguous patch
  and the packer reports where it put them. A slot a sheet does not fill stays
  empty; a hollow layout's fill colour is exactly what that case is for.
- **Only kept pieces are packed.** A sheet's unused art does not reach the
  atlas, so a tileset's size reflects what the game uses rather than what was
  drawn.
- **A packed sheet is addressed by id and never by coordinate.** The packer is
  free to lay an atlas out differently on every export, so an id is the only
  thing an export preserves. A `(col, row)` held anywhere outside the tileset
  that describes that atlas — a constant, a picker's origin, a frozen test
  golden — is a reference that goes wrong without going missing: the map still
  draws, and simply draws something else. Nothing downstream can detect it, so
  the rule is not "keep such references up to date" but "do not hold one".
  Content the game reaches for therefore needs an id even when it is only
  paint: deck markings and floor covering are addressed exactly like props.
- **The authoring document must be able to say everything the tileset says.**
  A sheet whose document cannot express one of a definition's fields cannot be
  re-exported without silently dropping it, and a dropped combat number — how
  high a thing stops a shot, which edge of it wants a wall — changes fights
  while changing nothing a reader would look at. The document and the exported
  tileset are one round trip, and a field that only one of them has is a defect
  in the model rather than a limitation of the tool.
- **A raw sheet must carry its own alpha.** What is background and what is art
  is a judgement about the picture, and an opaque plate has not recorded it.
  Deriving the shipped atlas by transferring new colour onto a previous atlas's
  alpha makes that previous atlas an input no source of truth can replace: the
  authoring document can hold every id and every number and still not be able
  to regenerate the sheet. Alpha belongs in `art-source/` with the art, and a
  sheet that lacks it is not yet re-exportable however completely it is
  annotated. **Giving an opaque plate its alpha is an edit to the art, not a
  setting on the sheet**, because what counts as background is a fact about a
  cell rather than about the plate: the same dark gutter is background beside a
  crate and is the outer edge of the floor tile next to it, and a plate's own
  dark outlines sit inside the range its background occupies. A colour key
  declared once for the sheet cannot say that, and a key that could would be a
  judgement stored as a number where a picture is what was judged. The keyed
  sheet is then reviewed piece by piece against the atlas it replaces, over a
  contrasting backdrop, because both a passing suite and a per-piece opacity
  figure stayed green through the failure that made this a law: a prop keyed to
  a plausible fraction can still be one cut in half by a corridor of background
  that reached its middle.
- **A silhouette is checked as a shape, never as a total.** How much of a cell a
  piece covers is the one thing about its alpha that is easy to measure, and it
  is blind to every way the shape can be wrong, because the errors are signed
  and they cancel: a chair whose leg gap is plugged and whose outline is a
  little tighter reports the coverage it should have and draws with a brick
  between its legs. What has to be stated instead is structure — **the voids a
  piece is drawn around must stay open, and nothing may float beside it.** Those
  two say what a total cannot: the first is the half of a silhouette that is
  absence rather than presence, and the second separates the piece from the
  dust a key admits and the sliver of its neighbour a cut leaves behind. A void
  is authored, not derived; it is a judgement about what the object is, and it
  belongs with cover and half-height rather than in a threshold.
- **A grid needs an origin and a pitch, not a division of the canvas.**
  Generated art sits inside a margin and is rarely drawn to a pitch that divides
  its own pixel size evenly, so dividing a plate proportionally puts every
  boundary somewhere the art does not change and leaves a sliver of the
  neighbouring cell in each exported tile. The counts stay stated by the
  operator; only the placement may be measured. **A plate that separates its
  cells with a dark gutter has its boundary in the middle of that gap, so its
  grid is measured from the gutters; a plate drawn cell against cell has no gap
  and its boundary is where the art changes, so that is what is measured
  instead.** Which one a sheet is, is a fact about the art and is itself
  measured — a gutter is a trough with lit art on both sides of it, so an empty
  region of a plate is not one however dark it is. The distinction is not
  cosmetic: a gutter has an edge on each side, so a placement fitted to the
  strongest change lands on one of those edges, several pixels inside the
  neighbouring cell. **Detecting the cell count is a different and
  ill-posed problem and stays out of the model** — it was tried here, ranked
  "two cells" above the correct ten, and was deleted. Fitting two parameters to
  an already-stated count is over-determined and works.
- **A measurement is evidence offered to the operator, never a decision taken
  for them.** Sheets fail a regular-grid fit in several distinct ways — a plate
  whose lower rows are empty offers no boundaries to fit there, and a strip of
  props whose frames vary in width has no single pitch at all — so a fit reports
  how many of the stated boundaries landed on a real feature of the art and how
  far those lie from the straight line through them, and disowns itself when
  either is poor. An axis that disowns itself keeps the cut already in force.
  Applying it anyway is possible and is an explicit act. **A disowned fit's
  numbers are about the feature it fell back to, so they do not say how wrong
  the cut in force is.** An axis that could not find its gutters is reported
  against the change seams, which are the far edge of each gutter rather than
  its middle; a line correctly placed in the gutter reads as several pixels
  "off" against them. Whether a stated cut is actually misplaced is answered by
  measuring it against the same feature it was meant to sit on, not by reading
  the residual of a fit that disowned itself.
- **A cut cell is named for where it sits.** Splitting a fused plate names its
  cells `<idPrefix>.c<col>r<row>`, zero-based and column first, because the
  annotation pass is a person and a model looking at the same picture and a
  serial name gives neither of them a way to point at one cell of a hundred. The
  address is the id, so a cut whose names would collide with pieces already on
  the sheet is refused rather than renumbered. Pieces found by alpha have no
  grid position and keep their serial names.
- **A mechanical pass never silently discards a judged one.** Slicing derives
  pieces from pixels, so it recovers only what a threshold can see; a piece that
  reconciles to nothing takes everything else it held with it. Keeping a
  re-slice that would drop an entry carrying judgement — a block assignment, a
  note, tags, a cover level, a footprint, a stand-in binding, a chosen id — or
  that would drop a plate's cut cells is refused, naming what would go, until
  the discard is asked for explicitly. This is what makes "re-sliced without
  being lost" true of a cut plate, whose cells reconcile to nothing every time.
  Dropping and re-finding pieces that carry nothing but their bounds stays free:
  that is what tuning a threshold is, and excluding a speck and then raising the
  threshold until it vanishes is the sweep working rather than work being lost.
  For the same reason, correcting a plate's cut moves its existing cells onto
  new rectangles rather than slicing and splitting again: a cell's id is its
  address and its block membership is on the entry, and a round trip through the
  slicer would take both.

The pass is entered from the project rather than from a file chooser: raw
sheets, their authoring documents and the exported tilesets are paired by name,
and each sheet reports whether it is raw, seeded, annotated or exported. A
document that names a sheet and its slice settings but no pieces is a valid
starting point — the natural thing to write when setting a sheet up, by hand or
by a model — and is sliced on open rather than treated as empty. What such a
seed cannot supply is which piece is which, because that needs the slice.

Slot names state the mask the way its layout reads it — "the exterior is on this
side", not "the neighbour is a wall". A mirrored assignment still loads, still
resolves, and is still opaque, so no validation can detect it. The defences are
therefore all at the moment of assignment and all of them are legibility: the
wording of the label, a preview that draws the block as a room, and an
assignment that answers with what each slot it filled means. An authoring
surface that lets a piece be put into a slot owes that reading back to whoever
made the assignment, because afterwards there is nothing left to ask.

The pass is reachable both from a window and headlessly, over one shared domain
layer: the editor is a view over that code rather than the code itself, and a
tool and the page must never become two implementations of the same act. They
must also never be two writers — the tools read and write files, and the editor
reopens them — which is why the tools are a separate entry point rather than a
server inside the running workbench. The mechanical half is all a tool may do:
what a sheet is for, what a piece is, and which pieces form a block are
judgements a tool records and never invents. See `authoring-entry-points.md`.

Raw sheets, masters, authoring documents and derivation scripts are pre-pack
input and live outside `mod/`, which is synchronized wholesale into every
install. They stay version-controlled: a sheet has to remain re-derivable and
re-annotatable, and its annotation cannot be recovered by re-running anything.

## Validation and fallback laws

1. A built-in id collision, a missing sliced frame, or an unresolved tile
   overlay selector is an authored-content error and must be surfaced during
   catalog loading. Ids must never silently become last-one-wins.
2. Built-in loading is defensive at application startup: failures are logged
   and leave the prior installation (or no installation) in place. Consumers
   may use their explicit degraded path where one exists, but a mapping may not
   silently substitute an unrelated asset for an unresolved id.
3. A mapping depends on the tile catalog. Pool members and render targets are
   registry ids, never copied coordinates or enum ordinals.
4. Seeded content must retain deterministic RNG consumption unless an authored
   mapping intentionally changes behavior. The data migration itself does not
   authorize a visual or tactical reroll.
5. Tile metadata is not automatic navigation authority. A new visual property
   can affect walkability, cover, sight, or collision only through the code
   path that owns that tactical law.
6. Cross-provider ids and mapping keys are add-only. A collision reports both
   declaring mod ids and resource paths; it never becomes load-order override.
   Mapping references preflight against the complete tile catalog before the
   mapping is installed.
7. Code may choose a layout from topology, but declared content owns the
   membership of a visual variant pool. A compatibility fallback may preserve
   output only while the registry is unavailable; it is not another catalog.
8. A tag or a description is documentation. It becomes a generation or tactical
   selector only through the code path that owns that law, never by being read
   from a label.
9. Pre-pack art inputs must not live under `mod/`. The shipped folder is
   synchronized in full, so an input left inside it is distributed as content.

## External-provider contract

Tilesets use the same provider manifest, exact-mod resource loading, stable
iteration order, and provenance diagnostics as equipment catalogs. An absent
manifest is not an error; an unreadable manifest or declared resource is.
Registry installation is atomic after all contributions and cross-references
validate. Provider ids, definition ids, mapping keys, sheet paths, and other
asset paths should be namespaced by the contributing mod. Art paths still live
in Starsector's shared asset namespace even though their catalog JSON is loaded
from an exact provider.

Mapping contributions may add named doodad pools and any still-unclaimed
closed mapping key. They may not replace a core ground-render target, filler
configuration, macro-height value, or named pool. Richer selectable terrain
themes require a future map-generation selection noun rather than implicit
load-order overrides.

## Adjacent features

Map generation owns the relationship between generated topology and playable
terrain. This feature gives it named content and parameters; it does not own
map recipes, fillers, or the tactical validity of a map. `GenRecipe` chooses
which generation stages execute, while a mapping chooses the content an
already-selected stage consumes.

Moddable weapons supplies the shared provider discovery mechanism while
remaining a different domain. The catalogs share discovery and provenance,
but their ids, schemas, validation, and gameplay authority remain independent.
