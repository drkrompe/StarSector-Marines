# Tactical industrial facilities

**Shipped `e8ad9c4b`** — qualifying industrial lots now generate complete
factories instead of stretching the legacy perimeter-crate warehouse recipe
over a larger shell.

## Player-visible result

- Industrial lots at least 15x12 cells receive a frontage facing the map
  interior and an aligned rear service entrance.
- A clear two-cell service spine joins those entrances. The loading bay and
  broad production floor sit on one side; an enclosed supervisor/control room
  and parts cage open directly from the other side.
- Opaque machine tools and pressure tanks form a production line against the
  exterior wall. Their three-cell spacing leaves two-cell cross-gaps, while
  the floor depth preserves a broad firing and movement lane beside the line.
- The control room receives a low process console, the parts cage receives
  stacked crates, and the loading bay receives pallet cover. These fixtures
  block movement but preserve lines of fire.
- Smaller industrial lots intentionally retain the old visual-only warehouse
  shelves and supervisor desk, preserving variety and seeded behavior outside
  the new size tier.

## Tactical and sizing contract

The 15x12 minimum reserves four production cells, two separator walls, a
two-cell spine, and a two-cell support wing across the short axis. The spine
and machinery gaps exceed the 1.2 cells required for two 0.3-radius marines
abreast.

`RoomPurpose` gained append-only `INDUSTRIAL_SPINE`, `LOADING_BAY`,
`PRODUCTION_FLOOR`, `CONTROL_ROOM`, and `PARTS_CAGE` labels. The building keeps
the existing `INDUSTRIAL` building kind and `DEPOT` point of interest.

## New art

The generated doodad atlas gained:

- `doodad.industrial-machine-tool` — heavy cover, 0.68 ballistic half-height
- `doodad.industrial-fluid-tank` — heavy cover, 0.82 ballistic half-height
- `doodad.industrial-control-console` — medium cover, 0.45 ballistic half-height

Untouched transparent ImageGen outputs live under
`mod/graphics/doodads/imagegen-raw/`; runtime sources and the exact prompt
contract live in `sources/` and `IMAGEGEN-PROMPTS.md`.

## Verification

- `python mod/graphics/doodads/test_stitch_atlas.py`
- focused industrial floor-plan, atlas-golden, and visual-preview tests pass
- `IndustrialFacilityFloorPlanTest` covers all four frontages, aligned opposed
  entrances, two-cell spine continuity, purpose-room connectivity, fixture LOS
  behavior, machinery lane spacing, whole-floor reachability, representative
  city prevalence, small-lot fallback, and the marine-radius sizing rule
- `build/zone-previews/buildings-industrial.png` was inspected across a legacy
  warehouse, both minimum orientations, and a larger factory
- the full root test suite and the asset-pipeline suite pass

## Deliberate next boundary

The strongest next ground slice is compound-scale industrial identity: pair a
large factory with an adjacent fenced service yard, tank farm, or utility shed
and orient their gates/doors toward shared circulation. This can reuse the
existing compound overlay and frontage machinery. A truly fused structure
spanning several BSP leaves remains gated on an earlier footprint-plan stage
that can suppress or replan internal road-graph edges.
