# Multi-cell residential doodads — shipped

**Code:** `fc212e56`

**Status:** rectangular doodad footprints complete; residential beds and sofas
now occupy their natural two-cell spans.

## Player-visible result

Apartment beds and sofas no longer read as compressed one-cell icons. Their
horizontal and vertical variants render across two contiguous cells, block
movement across that full physical footprint, and provide cover and ballistic
silhouette on both occupied cells. Raised planters deliberately remain one-cell
fixtures.

The residential planner considers the whole furniture footprint when choosing
a location. A fixture must stay inside its bedroom or living-room purpose,
avoid doors and existing props across every occupied cell, and leave the
remaining room floor connected. That last rule prevents a two-cell sofa from
turning a shallow room into two sealed pockets.

## Reusable footprint contract

- Doodad atlas entries may declare `footprintCells: [width, height]`; omitted
  metadata remains backward-compatible at 1x1.
- The stitcher proportionally fits source art to the complete authored
  footprint and packs rectangular assets into the fixed grid with deterministic
  first-fit placement. One manifest frame remains the top-left anchor.
- `DoodadDef` and runtime `Doodad` carry the positive cell span, and
  `Doodad.occupiesCell(...)` is the common intersection query.
- Runtime and preview renderers read one contiguous atlas rectangle and draw it
  centered over the complete world-space footprint.
- Fixture stamping, collision checks, prop exclusion, shoreline cleanup,
  fortress demolition, and compound gate clearing all operate on every
  occupied cell rather than only the anchor.
- `DoodadService` publishes directional cover and ballistic half-height for
  every physical footprint cell. Neighbor cover still bleeds outward by one
  cardinal cell; ballistic occupancy does not.

## Tactical sizing

The existing residential planner already sizes two-cell halls and gates around
the 0.3-cell marine radius. Beds and sofas now consume two cells along their
long axis, making them roughly three marine diameters long while retaining a
full-cell movement boundary. Furniture may create useful firing cover inside a
private room, but the connectivity filter prevents that cover from becoming an
accidental wall across the room.

## Art pipeline

No new generated art was needed for this slice. The existing residential
masters and deterministic horizontal/vertical derivatives are reused. The
doodad atlas grew vertically because the bed and sofa frames now reserve their
true 2x1 or 1x2 contiguous source spans instead of a single 32x32 frame.

`mod/graphics/doodads/README.md` documents the optional footprint field and
packing behavior. The atlas manifest locks bed/sofa orientation metadata while
preserving 1x1 defaults for legacy doodads and planters.

## Verification

- `GatedHousingFillerTest` verifies every bed and sofa occupies exactly two
  cells, stays inside its owning apartment and room purpose, stamps fixture and
  navigation state over both cells, respects reserved roads, and leaves rooms
  connected. Seed 777 is a focused shallow-room regression.
- `DoodadServiceTest`, `DoodadMappingParityTest`, and
  `TileRegistryParityTest` cover footprint occupancy, two-cell cover and
  ballistics, manifest parity, defaults, parsing, and invalid spans.
- `FixedGridTileDrawerTest` locks the contiguous multi-cell source rectangle
  and destination contract.
- `test_stitch_atlas.py` covers deterministic 2x1 and 1x2 packing; all three
  atlas tests pass.
- `build/zone-previews/residential-courtyard-compound.png` was inspected at
  sprite resolution: oriented two-cell beds and sofas remain inside their
  rooms while halls and entrances stay open.
- Full merged root suite: 1,739 tests green. Asset-pipeline suite green. Legacy,
  conquest, and station validation batches retain one connected walkable
  component and valid deployment.

## Deliberate next boundary

The next ground-structure slice remains reuse of the apartment planner on
qualifying standalone residential lots, with street frontage replacing the
compound courtyard signal. Public-service or medical campuses follow. The new
footprint seam also makes larger counters, machinery, vehicles, and exterior
equipment possible without special-case rendering or combat logic.
