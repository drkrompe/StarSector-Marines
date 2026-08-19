# Residential courtyard compounds — shipped

**Code:** `444e0f97`  
**Status:** gated-housing compound identity and apartment interiors complete.

## Player-visible result

Gated housing no longer reads as generic houses scattered across grass. A
qualifying residential seed now anchors a paved, walled courtyard compound:

- the two primary parcels become roomed apartment blocks facing shared
  circulation;
- each apartment has a public lobby, clear two-cell common hall, two living
  rooms, two bedrooms, and an opposed rear exit;
- beds and sofas provide low, see-through tactical cover inside private rooms;
- sparse raised planters break up the shared courtyard without obstructing
  building approaches or the reserved vehicle route;
- one deterministic two-cell public gate connects the enclosure to a
  contiguous road edge; and
- a smaller third parcel remains a utility outbuilding, giving the site a
  useful mix of residential and service space.

The result creates an exterior fighting space with cross-courtyard lines of
fire, multiple building entries, and room-to-room clearing routes rather than
another perimeter-furnished open box.

## Agent-size and lot-size decisions

`UnitType.MARINE.radius == 0.3` cells. The shared common hall and public gate
are both two cells wide, comfortably exceeding two marine diameters (1.2
cells). Private room doors remain one-cell tactical chokes, while the common
route allows a pair to maneuver abreast.

The apartment planner requires a 12x10 actual building footprint. Because
compound members retain a one-cell courtyard rim, `GATED_HOUSING` seed lots
must be at least 14x12 in either orientation. Smaller rolls demote to ordinary
residential buildings. When no natural gated-housing roll survives, the label
stage promotes only the largest already-residential qualifying lot; failed
compound claims still demote cleanly.

## Structural seams

- `ResidentialPartitionStrategy` owns the frontage-aware apartment plan. It
  aligns the common hall with the courtyard-facing entrance and the opposed
  rear exit, labels rooms at carve time, and falls back to the existing binary
  home plan below its size threshold.
- `RoomPurpose` gained append-only `APARTMENT_LOBBY`, `RESIDENTIAL_HALL`,
  `APARTMENT_LIVING`, and `BEDROOM` values.
- `BuildingLayouts.APARTMENT_BLOCK` furnishes disconnected private rooms by
  purpose while deliberately leaving every common-hall cell free of fixtures.
- `GatedHousingFiller` derives frontage from the bridged shared-courtyard mask,
  not the whole member rim. This keeps doors aimed at actual common space while
  preserving road reservations.
- Courtyard planters require a clear 3x3 walkable envelope and four-cell
  spacing. They block movement, preserve line of sight, and are tagged as
  fixtures rather than walls.

## Art pipeline

The built-in ImageGen tool, using the `stylized-concept` style, produced three
transparent masters: a civilian bed, sofa, and raised planter. Untouched
outputs are retained under `mod/graphics/doodads/imagegen-raw/`; working
masters live under `imagegen-masters/`. `derive_residential_frames.py` crops
alpha content and deterministically emits horizontal/vertical source variants.
The exact prompt contract is recorded in `IMAGEGEN-PROMPTS.md`.

The doodad atlas now exposes six residential frames with authored ballistic
heights: beds (0.28), sofas (0.42), and planters (0.55). Beds are light cover;
sofas and planters are medium cover. All remain transparent enough to preserve
their intended firing lanes.

## Verification

- `GatedHousingFillerTest` covers frontage and opposed entries, all four room
  purposes, private-room fixtures, a completely clear common hall, exact
  two-cell public gate width, planter semantics, road-reservation safety,
  whole-map walkable connectivity, determinism, marine-radius sizing, and
  representative 60-seed prevalence.
- `LabelLeavesStageTest` locks the asymmetric 14x12 outer-lot threshold.
- `DoodadMappingParityTest` locks the new atlas ballistic metadata.
- `BuildingZonePreviewTest.renderResidentialCourtyardCompound` writes
  `build/zone-previews/residential-courtyard-compound.png`.
- Full merged root suite: 1,706 tests green. Asset-pipeline suite green.
- `mod/graphics/doodads/test_stitch_atlas.py`: two tests green.
- Legacy, conquest, and station validation scans retain one cell component,
  one edge component, and valid deployment on every batch seed.

## Deliberate next boundary

The next small ground-structure slice should reuse this apartment planner for
qualifying standalone residential lots, where street frontage replaces the
compound courtyard signal. After that, public-service or medical campuses are
the next compound-scale identity gap. A genuinely fused multi-lot building
still belongs behind an earlier footprint-planning stage that can suppress or
reroute internal road-graph edges safely.
