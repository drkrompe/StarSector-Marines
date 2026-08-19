# Civic headquarters interiors

**Shipped `4d929309`** — large civic lots now generate purpose-built municipal
headquarters instead of another generic shop shell.

## Player-visible result

- `BUILDING_CIVIC` appears primarily in civic districts, with a small mixed-zone
  chance. Lots smaller than 13x11 are demoted to ordinary commercial buildings.
- The public frontage faces the map interior and an opposed rear service door
  aligns to the same two-cell circulation spine.
- A three-cell-deep public lobby wraps the frontage. Beyond it, four side rooms
  open directly onto the spine: two offices, one conference room, and one
  secured server room. Squads never need to cross one office to reach another.
- Every office receives a workstation bank; reception receives a low desk; the
  conference room receives a planning table. These fixtures block movement but
  preserve lines of fire.
- The server cabinet blocks movement and line of sight, giving the secure room
  a distinct breach/cover profile rather than only a different label.

## Tactical and sizing contract

The 13x11 minimum leaves enough interior depth for a three-cell lobby, two room
banks, and the wall splitting them. The two-cell spine is wider than two marine
diameters abreast: marines have a 0.3-cell radius, so the pair needs 1.2 cells.

`RoomPurpose` gained append-only `CIVIC_RECEPTION`, `OFFICE_CORRIDOR`,
`CIVIC_OFFICE`, `CONFERENCE_ROOM`, and `SERVER_ROOM` labels. `BuildingKind`
gained `CIVIC`, and the building emits an `ADMINISTRATIVE` point of interest.

## New art and atlas tooling

The generated doodad atlas gained:

- `doodad.office-workstation-bank` — medium cover, 0.45 ballistic half-height
- `doodad.office-server-rack` — heavy cover, 0.75 ballistic half-height
- `doodad.office-conference-table` — medium cover, 0.38 ballistic half-height

Untouched transparent ImageGen outputs live under
`mod/graphics/doodads/imagegen-raw/`; runtime sources and the exact prompt
contract live in `sources/` and `IMAGEGEN-PROMPTS.md`.

Rebuilding the atlas exposed that existing authored ballistic heights lived
only in generated JSON. `_atlas.json` is now their source of truth and
`stitch_atlas.py` validates and preserves them, preventing future rebuilds from
silently replacing low sandbags/bunks with cover-bucket defaults.

## Verification

- `python mod/graphics/doodads/test_stitch_atlas.py`
- focused civic, size-guard, commercial-regression, atlas-golden, and visual
  preview tests pass
- `CivicHeadquartersFloorPlanTest` covers all four frontages, aligned entrances,
  two-cell circulation, four purpose rooms, fixture LOS behavior, whole-floor
  reachability, representative prevalence, and the marine-radius sizing rule
- `build/zone-previews/buildings-civic-headquarters.png` was inspected at four
  qualifying footprint sizes
- after the final concurrent-main integration, all mapgen previews,
  connectivity/deployability scans, atlas goldens, and focused regressions pass;
  the full 1,623-test run has one unrelated suite-order-dependent
  `SwarmEvacuationOutcomeBridgeTest` failure that passes in the focused rerun

## Deliberate next boundary

Industrial interiors are now the conspicuous generic holdout. Warehouses still
line visual-only crates around the perimeter. The next ground slice should give
large industrial lots a loading/service spine with a production floor,
supervisor/control room, parts cage, and opaque machinery or tank fixtures.
A truly fused structure spanning several BSP leaves still requires an earlier
footprint-plan stage that can suppress or replan internal road-graph edges.
