# Concentric "onion" station layout — ✅ SHIPPED

Commit `c447104`. A second station *layout* — the **defense station**: concentric
defensive rings wrapping a central control core, the player breaching the outer
ring and fighting *inward* through gated ring walls. Where the BSP station
(slices 1–2) scatters uniform rooms, this reads as a purpose-built fortified
installation, and the far side of the outer rings is left off the player's path —
the believable "you only see the part of the station you're assaulting" feel.

## The payoff: topology roles become meaningful

This reuses the **entire** station pipeline — `StationGraph`, `StationTopologyStage`,
the validation scans, the previews — unchanged. Only the layout stage is new.
Because the rooms are still graph vertices and the gates still corridors, the
slice-2 topological roles light up with real meaning:

- **inter-ring gates ARE the bridges** — the must-pass chokepoints toward the core,
- **depth-from-entry IS the radial assault gradient** — green at the breach, red at
  the besieged core,
- **ring loops are the on-loop flanking space**, the core a single-entrance dead-end.

## What landed

### `ConcentricLayoutStage` (the geometry)
Nested centered rectangles inset by `RING_THICKNESS` (10) from a 1-cell hull
margin, stopping once the center is ≤ `CORE_MIN` — that center is the **core**
(one room, the objective / defender). 80×80 → 3 rings + core = 25 rooms.

Each ring **band** (the frame between two consecutive rects) is split into **8
rooms** — 4 corner strongpoints + 4 edge galleries — carved **inset by 1** so a
1-cell wall separates every room and every ring. Connectivity is then carved:
- **intra-ring doors** link a band's 8 rooms in a cycle → each ring is a loop,
- **inter-ring gates** breach same-side galleries of consecutive rings:
  **2 gates on the outermost boundary** (a flanking loop near the breach),
  **1 on every inner boundary** (each a must-pass *bridge*). Gate sides rotate
  per ring → the inward path spirals.

Publishes the `StationGraph` + ring metadata (`setRings`).

### Supporting
- `StationGraph` — explicit-bounds `Room` ctor (rooms aren't from `BlockLeaf`
  here) + `ringOf()` / `coreRoom()` / `hasRings()` structural metadata.
- `StationCarve` — shared `carveRoomRect` / `carveDoorBetween` / `carveDoorCell`
  (the slice-1 "convert only solid cells" door idiom, lifted for reuse).
- `CoreSpawnStage` — defender = core, marine = a random outermost-ring room.
- `BspCityGenerator.generateConcentricStation(w,h,seed)` + `ConcentricStation`
  recipe (`InitSolid → ConcentricLayout → CoreSpawn → StationTopology →
  TacticalLink → Finalize`). The BSP station (`generateStation`) stays — both are
  legit archetypes.

### Per-seed variation
The geometry is dimension-derived (so room count is constant), but a **gate-
rotation rng draw** + a **random breach-room pick** vary the inward spiral and the
assault direction per seed (e.g. seed 100 reaches depth 10 vs others' 9). All
deterministic from the seed.

## Gate (all 6 seeds green)
- `MapValidationScanTest.scanConcentricStationBatch` — one walkable component (no
  ring left ungated) + marine→core pathable via the real `GridPathfinder`.
- `StationTopologyTest.concentricStationStructureInvariants` — the **besieged-core**
  invariants on top of the reused Tarjan/oracle: the core is a single-entrance
  dead-end (`degree == 1`) whose gate is a **bridge** (cross-checked by the
  independent remove-and-flood oracle), and depth **rises monotonically inward**
  across every radial gate. Per seed: 25 rooms, 3 rings + core, 2 bridges (the
  inner single-gate boundaries), core depth ~8–9.
- `BspMapPreviewTest.renderConcentric{Station,Roles}Batch` → `concentric-*.png`
  (the onion) + `concentric-roles-*.png` (radial gradient, gate bridges, ring
  loops).

## Follow-ups / tuning levers
- **Geometry variety** — room count is currently fixed by dimensions. Levers:
  jitter `RING_THICKNESS` / corner-vs-gallery split / occasionally drop a ring
  segment, seeded — to vary the silhouette, not just the spiral.
- **Asymmetric breach** — a real hull breach (a carved entry from the map edge
  into the outer ring) rather than just spawning inside it.
- **Thematic core** — the core room is the natural home for the
  `COMMAND`/objective kind once station thematic kinds land
  (`station-interior-fills`); the ring rooms map to `HABITATION`/`HANGAR` by ring.
- **First role-querying placement pass** — emplacements at the gate bridges
  (defender chokepoints), still the next gameplay step; the concentric layout
  makes those positions obvious.
- **Wider ring → bigger stations** — the geometry already scales with map size
  (more rings); only exercised at 80×80 so far.
