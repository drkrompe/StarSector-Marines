# Moddable Tilesets

Status: ACTIVE — additive external catalogs shipped; variant-pool cleanup remains

Written: 2026-08-23

Updated: 2026-08-29 — walls, doorways and roofs dispatch through the mapping's `surfaceRender` section; content is found by purpose as well as by sheet; a block may be a variant pool as well as an autotile; every sheet is exported from its authoring document; and authoring is reached as a walkthrough.

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
- A **surface role** is a thing the renderer draws that is not a ground kind:
  the wall, the open doorway, and the intact roof. Ground kinds and surface
  roles are orthogonal per cell rather than alternatives — a cell has exactly
  one ground kind and may additionally be a wall, a doorway, or roofed — so
  they are two key spaces, not one. Both are dispatched by the mapping, and a
  role that no mapping names falls back to the id this mod ships for it.
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
  An atlas has one of two **shapes**, and they are alternatives rather than
  settings. A **cell grid** partitions the sheet on a stated cell size and
  addresses its content by `(col, row)`; a **strip** lays pieces in a row at
  whatever size and aspect each was drawn at, separated by gutters, and its
  content is addressed by the frame index the loader assigns when it scans the
  atlas at load. A strip has no cell size, no coordinates and no blocks, and
  giving it one would be inventing a grid the art does not have. Which shape a
  sheet is, is a property of the sheet, and the authoring document says it.
- A **derived companion** is a height or normal map baked from an albedo atlas
  and found beside it by name. It is part of the sheet even though no file
  references it.
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

Render dispatch covers both key spaces. Ground kinds resolve through the
mapping's `groundRender` section and surface roles through its `surfaceRender`
section; neither the wall, the doorway, nor the roof is named by the render
systems any more. That symmetry is the point rather than a tidiness: while the
orthogonal surfaces resolved by compiled id, a second wall could be authored,
slotted, and exported correctly and still never be drawn, and nothing anywhere
failed — every id resolved and every cell was opaque. A surface that is drawn
but not dispatchable is authorable only in appearance, so any new one joins the
role vocabulary at the same time as the code that draws it.

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

Authoring is reached as a **walkthrough** rather than as a workspace. Three
ways in — start from a need, start from a sheet, or only look — each a sequence
of screens carrying one step apiece, in order, with a way back. The distinction
is not cosmetic: presenting every command at once states that they are peers and
leaves their order to be learned elsewhere, when in fact the cut precedes the
annotation, the annotation precedes the grouping, and the export presumes all
three. A screen that cannot be finished says what it still wants, so a step that
is not yet possible reads as a question rather than as a disabled control.

Content is also *found* by purpose rather than by sheet. Ingesting art is
sheet-first — this plate arrived, cut it, say what its pieces are — but needing
art is not: the work starts from a wall being wanted, and which of the project's
sheets holds one is the answer rather than the question. So a **surface listing**
derives, for every ground kind and surface role, the blocks that could fill it,
whichever sheet they were packed on, marking the one the mapping uses. It is
derived on every read from the mapping, the tilesets, and the authoring
documents; a written-down list of "these are the walls" would be a second
authority over a fact those files already state, and would go stale the first
time a block was renamed in the one file that defines it.

The authoring tool groups the surfaces for browsing — where a thing is, then
what kind of thing it is — and that grouping is deliberately not part of this
model. Nothing the generator does depends on it, and putting it on
`GroundKind` or `SurfaceRole` would make a browsing convenience look like an
authority those enums answer to.

Choosing between candidates is done by looking at them. A block id says which
sheet a thing came from and nothing about what it is: two walls are a masonry
wall and a sandbag revetment, and only the picture separates them. So a surface
and its candidates are shown as pictures, and the candidate is also drawn as a
room three cells on a side — the one view in which a wall assigned inside out is
visible, because every cell of such a room has a distinct neighbour mask.

Which candidate a surface uses is part of the mapping, so choosing one is a
mapping edit rather than an authoring one. It is made in place, as a replacement
of one value, and validated against the whole catalog before it lands: a
re-serialised mapping loses the ordering of a hand-maintained file, and a
mapping naming an id nothing defines is a startup crash rather than a
wrong-looking map. Withdrawing a candidate is the opposite act — the block is
dissolved and its pieces go back to being doodads — and is refused while the
mapping still points at it.

The listing reports whether a candidate's slicing can be edited, and that is a
real distinction rather than a caveat. A block declared by an authoring document
can be re-cut, because the document records which pieces of which raw sheet it
was made from. A block that exists only in an exported tileset can be seen and
mapped but not re-cut, because nothing in the project records that. Sheets
derived by script rather than exported from a document are in the second
category, and the listing says so rather than offering an edit that leads
nowhere.

These properties of that pass are part of the model rather than of the tool:

- **A piece is a doodad or one cell of a block.** Facing is not a property of a
  piece. A wall or a corner is a block whose cells the game selects from the
  four-neighbour mask through its layout, so the authoring act is assigning
  pieces to that layout's slots. Authored content may never carry a per-piece
  facing: that would be a second answer to a question the layout already
  answers, and would move geometry authority out of code into art data. A
  doodad placement may still rotate one canonical piece to fit its generated
  room; the turn belongs to the placement, not the authored piece identity.
- **A block has one of two shapes, and they are alternatives.** An *autotile*
  answers "which cell for this neighbour mask", so it occupies a fixed patch
  addressed as an origin plus a layout offset. A *variant pool* answers "any of
  these", is chosen by hashing the cell's coordinate, and has no geometry at
  all — so it is written as an explicit list of cells and laid out as a plain
  run. Giving a pool a layout would put it where every autotile resolver then
  has to special-case it out again.
- **A block's origin is generated, never counted.** Its cells are addressed as
  origin plus a layout offset, so they must be packed as one contiguous patch
  and the packer reports where it put them. A slot a sheet does not fill stays
  empty; a hollow layout's fill colour is exactly what that case is for.
- **A repeating surface says so, and is treated for it.** Two authored markers
  carry that claim, and both mean the same thing from different directions: a
  *material* says the picture comes from a tileable file rather than from the
  plate, and a *sprite border* says the plate's own art carries a drawn rim that
  has to be mirrored away or the field shows a lattice at every tile boundary.
  A piece that claims neither is packed as it was cut. The treatment is per
  piece rather than per sheet because it is a statement about the object: the
  same dark edge is a rim on a field and the drawn edge of a paving slab that
  has to keep it, and nothing about the pixels tells the two apart. Applying it
  to a whole sheet instead softens art that was never a repeating field.
- **Only kept pieces are packed.** A sheet's unused art does not reach the
  atlas, so a tileset's size reflects what the game uses rather than what was
  drawn. Nothing is lost by leaving a cell out: the raw plate and the document's
  entry both keep it, and including it again is the whole of bringing it back.
  A sheet still produced by a script rather than exported is the exception, and
  the gap is large — one such sheet ships 650 cells to deliver the seventeen the
  game addresses.
- **A packed sheet is addressed by id and never by coordinate.** The packer is
  free to lay an atlas out differently on every export, so an id is the only
  thing an export preserves. A `(col, row)` held anywhere outside the tileset
  that describes that atlas — a constant, a picker's origin, a frozen test
  golden — is a reference that goes wrong without going missing: the map still
  draws, and simply draws something else. Nothing downstream can detect it, so
  the rule is not "keep such references up to date" but "do not hold one".
  Content the game reaches for therefore needs an id even when it is only
  paint: deck markings and floor covering are addressed exactly like props.
- **On a strip, order is the address.** A frame index is not authored: it is
  assigned by the loader as it scans the atlas left to right, so a tile's id is
  bound to its picture by position and nothing else. Moving a piece, dropping
  one, fusing two across a gutter the loader walks through, or splitting one
  down an internal gap renames every piece after it — and leaves the tileset and
  the atlas each internally consistent, which is why no amount of reading them
  can catch it. The export therefore preserves document order and **runs the
  loader's own slicer over the atlas before writing it**, requiring the frames it
  finds to be the frames that were packed. That check is not defensive
  programming; it is the only place the binding can be verified at all.
- **A strip states the scale it is drawn at.** The raw art is several times the
  size the sheet ships at, and nothing in the art says which fraction is
  intended — so a single divisor, authored once for the sheet, is what turns raw
  pieces into frames. Taking the sizes from the previous atlas instead would make
  that atlas an input again, which is the same circularity the alpha law
  forbids. **Reducing is not only resampling**: an average over six source pixels
  is a flat average, and a paved surface has only a few pixels at 39px to say it
  is made of stones with, so the reduction sharpens back the local contrast it
  removed. That belongs to reducing rather than being a treatment applied
  afterwards.
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
  that reached its middle. **The key's settings are per cell as well as its
  flooding.** A paving slab and a wrought-iron bench disagree about what
  background is on the same sheet: the bench's own rails and legs are drawn
  inside the range that is unambiguously background beside the slab, so a single
  threshold either deletes the bench's frame and leaves four planks floating, or
  leaves the slab a dark halo it then has to tile with. The cell is the unit the
  judgement is made on, so it is the unit the settings attach to.
- **Keying a sheet withdraws it from the alpha-transfer derivation, and that
  withdrawal is measured rather than announced.** Two producers of a shipped
  atlas exist and they are alternatives: one transfers fresh colour onto the
  previous atlas's alpha, which is tolerable only while the raw plate is opaque
  and has recorded nothing; the other exports from a keyed raw sheet and its
  document. A sheet claimed by both reverts to the first the next time anyone
  runs it — a valid image, the right size, the wrong art — and nothing
  downstream can see it, because the atlas and the tileset describing it stay
  consistent with each other. What decides which producer owns a sheet is the
  sheet's own alpha, never a flag on the document or a note in the script:
  keying the plate *is* the withdrawal, so there is no second record to keep in
  step and nothing for the next person to have read. A derivation that would
  take a keyed plate as input refuses to run, and the same fact is asserted at
  build time as well, because the run that matters is the one nobody was
  watching.
- **A derived companion is re-derived, never preserved.** A sheet's height and
  normal maps are baked from its albedo, found again by naming convention alone,
  and sampled at the coordinates the albedo's own frames give. Nothing connects
  the three files, so re-packing an albedo leaves the parallax reading the relief
  of a sheet that no longer exists — silently, because a stale companion is a
  valid image of the right size and every check that can see it passes. An
  export that changes an atlas owes a re-derivation. Reproducing the old geometry
  instead is not the easier option but a far stricter one, because identical
  frame boxes are not identical pixels: relief inside a box that did not move is
  still the wrong relief. What makes the obligation checkable rather than
  remembered is that the derivation leaves everything outside a cell flat, so the
  companions state their own frame boxes and those must be the albedo's.
- **A piece's picture must be on the sheet its document annotates, or the
  document must say where else it is.** The alpha law's sibling, and it fails in
  the same silent way. A document that names a piece whose art lives somewhere
  else — a tileable material from a library, a patch composited in afterwards —
  and does not say so can hold every id, every cover level and every number for
  that sheet and still not reproduce it: exporting puts back whatever was under
  the replacement. The two producers stay consistent with each other and the
  substitution is invisible in the tileset, so nothing downstream can report it.
  Where a sheet's frames disagree about where their picture comes from and the
  document cannot say so, the sheet is not exportable until they agree, and a
  strip cannot compromise: order is the address, so it exports whole or not at
  all. `nature-tiles-material-provenance.md` is the worked case.
- **A frame's picture may be a material, and then it is sized by the material.**
  The way out of the law above is a declaration rather than a prohibition: an
  entry may name the tileable **material** file its picture comes from, and the
  export places it there. A material is a *surface* rather than a picture drawn
  at a size, so nothing about the sheet's scale applies to it — resampling a
  seamless texture to fit a frame either loses its seams or has to wrap-pad to
  keep them, and sizing the frame from the material avoids the question
  entirely. It is placed with the renderer's own ground inset of itself wrapped
  periodically round it, so that the inset the renderer crops away is exactly
  the guard and what remains is exactly the material. **That width is taken from
  the renderer rather than restated**, because it is one number for one reason
  and two copies of it would drift into a seam nothing can see. A material is a
  repeating surface, so it may only back a ground frame, it carries no sprite
  border — the border is the repair for art drawn as a slab, and honouring both
  would silently ignore one — and it must be solid, for the same reason a field
  must be.
- **A sheet has exactly one producer, and a manifest that pastes into it after
  the fact is a second one.** Placing content into an already-written atlas at
  pixel rectangles is the pinned-coordinate defect and the two-producer defect at
  once: the rectangles address a layout the packer owns and is free to change,
  the two steps must run in an order nothing records, and the atlas and the
  tileset beside it stay consistent through every way it can go wrong. Content
  that is not on the plate belongs on the authoring entry that uses it, so the
  export is the only thing that writes the sheet.
- **A repeating field is solid where it is drawn, and its sprite border is not
  part of the surface.** A field is the one kind of piece drawn around nothing,
  so the void law inverts for it: every pixel a key carves off its rim is a
  puncture the tiling repeats, and the statement to check is that its box is
  filled. Generated field art also arrives as a *slab* — a lit rim across the
  top, a shadowed skirt under the foot, a darker column down each side — and
  those belong to the sprite rather than to the surface. Repeat one and they
  rule a lattice over the ground, one line per cell, which no id, frame count or
  coverage figure can see. The depth of that border is authored per piece and in
  two numbers, because what a sprite puts under itself is deeper than what it
  puts beside itself, and because the same reading of the same pixels is a rim
  on a field and the drawn edge of a paving slab that has to keep it. The export
  replaces it with the interior mirrored back out through it. That is a repair
  and not a cure: a mirror deep enough to remove a deep border reads as an
  ornamental symmetry at every join, so art drawn as a slab remains worse than
  art drawn as a surface.
- **A piece may be drawn as several bodies.** A scatter of pebbles is one prop
  drawn as three stones with daylight between them, and the daylight inside it
  is not a gutter. Which gaps are internal is settled once, by the gap the
  loader itself splits on: anything narrower is inside a piece, anything wider
  separates two. Keying, packing and checking all have to agree with that or a
  piece the document treats as one is two on the atlas — which renames every
  frame after it. The count of bodies a piece is drawn as is authored, like a
  void: it is the judgement a threshold silently overrules in either direction,
  dissolving a pebble or admitting a speck, and it moves no total worth reading.
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
