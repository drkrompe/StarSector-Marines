# Industrial works compound

**Shipped `7296aeec`** — industrial districts can now claim a coherent
three-parcel works site instead of rendering a factory, yard, and warehouse as
unrelated neighboring lots.

## Player-visible result

- The qualifying 15x12-or-larger seed becomes the tactical multi-room factory;
  the larger neighbor becomes a fenced service or tank yard; and the remaining
  neighbor becomes a utility warehouse.
- Factory doors, utility doors, and the yard gate face the strongest shared
  circulation edge. Internal road shoulders become a striped loading apron,
  while the reserved STREET centerline stays clear for the vehicle graph.
- The yard rotates among freight, maintenance, salvage, and tank-farm grammars.
  Compound-owned equipment is tactical cover: tanks block movement and LOS;
  other equipment blocks movement while preserving fire lanes.
- A transparent chain-link perimeter encloses the yard. Its two-cell gate and
  two-cell-deep entrance throat are sized for two 0.3-radius marines abreast and
  cannot be sealed by rotated yard equipment.
- Sparse pallets, pipe bundles, and cable reels give the shared apron a working
  loading-site silhouette without occupying its reserved vehicle lane.

## Claim and sizing contract

`INDUSTRIAL_COMPOUND` claims exactly three parcels, at most once per generated
map. Its factory seed must be at least 15 cells on the long axis and 12 on the
short axis; both neighbors must have a minimum dimension of 7. Failed or
undersized seeds demote to ordinary `BUILDING_INDUSTRIAL` lots.

Industrial, harbor/port, and (rarely) mixed districts can roll the site. To
keep representative city batches from losing the feature to weighted-table
variance, the largest qualifying lot that is already industrial is promoted
when no natural seed rolled; claim failure still restores the ordinary factory.

## New art

The generated doodad atlas gained four chain-link corner orientations and two
straight orientations. Fence cells block movement, preserve LOS, and carry
light-cover ballistics without masquerading as structural walls.

The two transparent masters were generated with the built-in ImageGen tool in
`stylized-concept` mode. Untouched outputs are in
`mod/graphics/doodads/imagegen-raw/`, copied masters are in
`imagegen-masters/`, and deterministic rotations are produced by
`derive_industrial_fence_frames.py` into `sources/`. Exact prompts and final
paths are recorded in `mod/graphics/doodads/IMAGEGEN-PROMPTS.md`.

## Verification

- `IndustrialCompoundFillerTest` covers exact parcel roles, apron-facing
  entrances, factory room purposes, a transparent fenced perimeter, the
  marine-width gate throat, tactical yard equipment, reserved-lane exclusion,
  determinism, and 60-seed prevalence.
- The seed-1234 regression verifies that rotated equipment cannot isolate a
  yard; both cell and edge connectivity scans return one component.
- `build/zone-previews/industrial-compound.png` was inspected as a sprite-level
  site preview: factory, utility building, fenced tank/service yard, striped
  apron, and protected centerline read as one facility.
- `python mod/graphics/doodads/test_stitch_atlas.py` passes.
- The full 1,634-test root suite and the asset-pipeline suite pass.

## Deliberate next boundary

The next comparable ground slice is residential compound identity: turn larger
apartment or gated-housing lots into roomed residential blocks around a shared
courtyard, with entrances, sightlines, and cover oriented as one site. A fused
building spanning BSP leaves remains gated on an earlier footprint-plan stage
that can suppress or replan internal road-graph edges safely.
