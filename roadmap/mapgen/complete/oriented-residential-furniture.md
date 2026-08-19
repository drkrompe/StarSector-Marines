# Oriented residential furniture — shipped

**Code:** `8e7a4815`

## Player-visible result

Apartment furniture now respects the room around it instead of inheriting one
orientation from the building axis. Every sofa places its authored back against
a physical wall and faces open living-room floor. Every bed places its head
against a wall and leaves open bedroom floor beyond its foot.

The placement rule still preserves doorway clearance, room-purpose containment,
non-overlap, and the multi-cell room-connectivity guard. A non-walkable fixture
cannot masquerade as wall support, and every cell along the furniture's usable
front must remain walkable floor in the same room.

## Reusable orientation seam

- Doodad atlas metadata accepts optional `preferredWallSide: N|S|E|W`, defining
  the authored edge that naturally belongs against a wall.
- `DoodadDef` parses and validates that direction, and runtime `Doodad` retains
  it for downstream geometry inspection.
- Generic scatter remains unchanged: the field is a preference for layouts
  that deliberately consume it, not a global wall-mounting rule.
- Apartment furnishing evaluates all cardinal bed or sofa variants together,
  then randomly selects only among candidates with complete physical wall
  support and an open front.

## Art pipeline

No new generated master art was required. The existing transparent bed and sofa
masters already contained clear head/back direction. The deterministic
residential derivation script now emits the two missing 180-degree counterparts,
giving each fixture four cardinal variants while retaining the existing
horizontal and vertical ids.

The stitcher validates and publishes `preferredWallSide` into the generated
tileset manifest. The doodad atlas now contains 42 assets.

## Verification

- `GatedHousingFillerTest` proves every apartment bed and sofa has a physical
  wall behind its complete authored edge, no fixture acting as fake support,
  and walkable same-purpose floor across its complete front edge.
- Existing two-cell footprint, room containment, doorway clearance,
  connectivity, determinism, reserved-road, and prevalence assertions remain
  green.
- `DoodadMappingParityTest` locks all eight cardinal bed/sofa definitions,
  footprints, and preferred wall sides.
- `TileRegistryParityTest` covers optional parsing, case normalization, and
  rejection of non-cardinal values.
- `test_stitch_atlas.py` covers metadata publication and invalid values; all
  four atlas tests pass.
- `build/zone-previews/residential-courtyard-compound.png` was inspected at
  sprite resolution and shows beds and sofas correctly backed against each
  room's walls.
- Full merged root suite: 1,764 tests green. Asset-pipeline suite green. All legacy,
  conquest, and station validation batches retain one connected walkable
  component and valid deployment.

## Deliberate next boundary

The ground-structure track can now return to reusing the apartment planner on
qualifying standalone residential lots. The orientation seam is also ready for
future wall-backed counters, workstations, consoles, and machinery without
making those assets residential-specific.
