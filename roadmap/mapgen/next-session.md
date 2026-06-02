# Map generation — Next Session

Read [`overview.md`](overview.md) first for concept + north star, and
[`pipeline-audit.md`](pipeline-audit.md) for the load-bearing pipeline
survey (its file:line citations are the map for any new work). Shipped
work is sealed in [`complete/`](complete/).

## Commit chain so far

```
82c76a9  mapgen: Slice A — room-purpose labels replace zone-graph chamber inference
d3f659d  mapgen: Slice A polish — distance-indexed label contract + critique NITs
042d084  mapgen: Slice B — PartitionStrategy interface + BinaryPartitionStrategy
ee55eb0  mapgen: Slice C — TernaryPartitionStrategy + N-axis PartitionLayout
b8b7b9d  mapgen: Slice D — ternary keep wiring + multi-chamber stamper emission
5e5ae91  mapgen: Slice 1 — GenContext blackboard; collapse fill SPI signatures
09d2590  mapgen: deterministic leaf-adjacency neighbor order
53fe951  mapgen: defense-post partition guard checks real footprint, not bbox
65c5686  mapgen: Slice 2a — GenStage pipeline (extract generate() into stages)
8666b8f  mapgen: Slice 2b — fold stampers into GenStage classes
bf0cf22  mapgen: hoist FortressWallStamper ctx reads to top of run()
7016b8e  mapgen: Slice 3 — GenRecipe; conquest/legacy recipes selected by axis
537ca03  mapgen: structural taxonomy Lever 1 — TacticalRegion segmentation
6cdd6c7  mapgen: overwatch-corner overlay in TacticalRegionPreviewTest (positional gut-check)
3426109  mapgen: promote overwatch-corner scorer to taxonomy package (positional read)
9320da7  mapgen: OverwatchTowerStage — first taxonomy consumer (corner-tower guns)
aae4244  mapgen: station interiors slice 1 — rooms + corridors as a recipe
6a07e8f  mapgen: station topological roles — depth / articulation / bridge / on-loop
c447104  mapgen: concentric "onion" station layout — defensive rings around a core
f04c2d5  mapgen: diamond defense station — cardinal ports converging inward  ← latest mapgen work
```

Full per-slice mapping (what landed vs. planned, Slice A critique
findings) in
[`complete/room-purpose-refactor.md`](complete/room-purpose-refactor.md).

## State of play

- **Room-purpose refactor (Slices A–D): shipped.** Carve-time
  `RoomPurpose` labels on `CellTopology` replace post-hoc zone-graph
  chamber inference. `PartitionStrategy` (binary + ternary) +
  distance-indexed `chamberPurposesByAnchorDistance` generalize past the
  keep toward N-chamber buildings.
- **Player-visible result:** the central keep now has up to two interior
  garrisons (KEEP_INNER priority 65 / garrison 4, KEEP_ENTRY priority 60
  / garrison 3) instead of one. Slices A–C were behaviour-preserving seam
  work.
- `KeepEntryChamberStamper` no longer builds a transient `ZoneGraph`;
  it reads `topology.getRoomPurpose(x, y)` directly.
- Build green; `BuildingShellCoreLabelTest` + ternary 50-seed coverage
  pass. Diagnostic preview at
  `build/zone-previews/ternary-partition-labels.png`.
- **Validation scan harness: added.**
  `MapValidationScanTest` (sibling to the `BspMapPreviewTest` previews) runs
  three structural scans over the 10-seed batch and prints a per-seed report —
  see the "Validation harness" section below for what landed, what it found,
  and the scans still on the menu.
- **Structural taxonomy — Lever 1 + first consumer: landed.** The city publishes
  a `TacticalRegion` segmentation of its porous walkable space (texture, not
  topology — see the section below + [`stories/structural-taxonomy.md`](stories/structural-taxonomy.md)).
  The positional read is promoted to `taxonomy.OverwatchScorer` (`3426109`), and
  its **first consumer** is live: `OverwatchTowerStage` (`9320da7`) mounts a few
  unmanned defender corner-tower guns at the strongest overwatch sites in the
  conquest recipe — the first player-visible payoff. Connectivity +
  garrison-deployability scans hold; placement is rng-free + deterministic.

## Next up (priority order)

The current track is the **composable generation pipeline** — decompose
`BspCityGenerator.generate()` into context + stages + recipe so map types
compose instead of forking. Design agreed (typed-blackboard `GenContext`,
incremental rollout). Design doc:
[`composable-pipeline.md`](composable-pipeline.md).

- **Slice 1 — `GenContext` + fill-SPI collapse: ✅ shipped (`5e5ae91`).**
  `GenContext` / `GenKey<T>` / `BspKeys` landed; `BlockFiller` (6-arg) and
  `CompoundFiller` (9-arg) now take `(…, ctx)`. See
  [`complete/gen-context.md`](complete/gen-context.md).
- **Slice 2a — `GenStage` pipeline: ✅ shipped.** Each numbered `generate()`
  step is now a `GenStage` class under `bsp/stage/`; `generate()` builds `ctx`,
  runs an ordered `List<GenStage>`, assembles `MapResult`. Behavior-equivalent.
  The four stampers run as `GenStage` **lambdas** for now — folding them into
  `run(ctx)` classes is 2b. Verification surfaced + fixed two pre-existing gen
  bugs (non-deterministic adjacency `09d2590`; defense-post stranding `53fe951`).
  See [`complete/gen-stages.md`](complete/gen-stages.md).
- **Slice 2b — stampers as `GenStage` classes: ✅ shipped (`8666b8f`).** The
  four post-fill stampers implement `GenStage` directly; `run(ctx)` replaced the
  static `stamp(...)` signatures, `buildCompoundExclusion` moved into
  `FortressWallStamper`, `buildStages()` lists the stamper instances, and the
  two stamper tests drive through a `GenContext`. Behavior-equivalent; full
  suite green. `stampNonConquest` / `stampPost` / `blockedFootprint` stay static
  (called outside the pipeline). See [`complete/gen-stages.md`](complete/gen-stages.md).

- **Slice 3 — `GenRecipe` + conquest/legacy recipes: ✅ shipped (`7016b8e`).**
  `GenRecipe` is a named, ordered `List<GenStage>`; `BspCityGenerator` builds a
  `ConquestCity` recipe (full list) + a `LegacyUrban` recipe (full minus the six
  conquest-only stages) and `generate(…, axis)` selects by axis. The
  conquest/legacy fork is now recipe membership. Byte-identical output (all six
  dropped stages verified RNG/mutation-inert in legacy mode); full suite green.
  See [`complete/gen-recipe.md`](complete/gen-recipe.md).

**The composable-pipeline core (context + stages + recipes) is complete.**
Adding a map type is now additive — author a recipe + its domain stages; the
generic stages (`FillDispatchStage`, `TacticalLinkStage`, `FinalizeStage`) are
reused verbatim. Candidate next tracks (priority order):

- **Self-gate cleanup: ✅ done.** The four conquest-*geometry* stages
  (`BiomeGroundOverride`, `BeachShoreline`, `FortressWall`, `DefensePost`) now
  **fail-fast** — `if (biomeMap == null) throw` instead of the dead silent
  no-op — so mis-composing them into a non-conquest recipe surfaces loudly. The
  two compound stages kept their graceful handling on purpose: `CompoundSeed`
  delegates null-tolerance to its seeder, and `CompoundPerimeter`'s axis-skip is
  a tested, intentional "no attacker side → nothing to place" behavior — left
  flexible pending how compounds compose into future recipes.

- **Slice 1 filler-level critique nits: ✅ done.** Dead alias locals dropped
  across the POI-less fillers + `StubBlockFiller` (`1e0c72b`); the compound-filler
  road-overlay path now has isolated coverage + a fail-fast precondition
  (`requireRoadOverlays` on `CompoundFiller`, with `CompoundFillerOverlayTest`).
  See the section below for what each resolution was.

1. **Station-tier track** — [`stories/corridors-first-class.md`](stories/corridors-first-class.md)
   **slice 1 shipped (`aae4244`)**: rooms + corridors as a `StationRecipe`
   (InitSolid → StationPartition → RoomCarve → Corridor → StationSpawn), the
   room/corridor `StationGraph` published, validation scan gating one-component
   connectivity. See [`complete/station-interiors-slice-1.md`](complete/station-interiors-slice-1.md).
   **Topological roles shipped (`6a07e8f`)**: `StationTopologyStage` derives
   depth-from-entry, articulation rooms, bridge corridors, and on-spine/on-loop
   onto `StationGraph` (Tarjan + BFS, brute-force-oracle gated). See
   [`complete/station-topology-roles.md`](complete/station-topology-roles.md).
   **Concentric "onion" layout shipped (`c447104`)**: the defense-station layout —
   defensive rings around a central control core, inter-ring gates as the topology
   bridges, depth-from-entry as the radial assault gradient. See
   [`complete/station-concentric-rings.md`](complete/station-concentric-rings.md).
   **Diamond layout shipped (`f04c2d5`)**: diamond/cruciform footprint, 4 isolated
   cardinal **ports** converging inward to a besieged core (outer shell all
   bridges, one connective ring); `RingGeometry` now backs both ring layouts; ports
   published on `StationGraph` for multi-spawn. See
   [`complete/station-diamond.md`](complete/station-diamond.md). Previews:
   `build/map-previews/{concentric,diamond}-*.png` + `*-roles-*.png`.
   **Next on this track** (priority order):
   - **Multi-port insertion** (battle-system) — land forces at several ports at
     once (the diamond's ports are already published on `StationGraph`); needs
     `MapResult`/`BattleSetup` multi-spawn (single-spawn today).
   - **First role-querying placement pass** — the first time the roles drive
     something player-visible: defensive emplacements biased to
     `{bridge mouth, high depth, articulation}`, fallbacks to `{deep terminal,
     low degree}`, flank watch to `{on-loop}`. This is where the foundation
     becomes gameplay.
   - **Width policy / junction bulges** — degree-≥3 junctions widen to 4–6-wide
     arenas; ban hold-nodes on degree-2 corridor cells, then promote the scan's
     cramped-garrison finding to a hard assert. (Also: a loop-budget bump —
     `CorridorStage.LOOP_FRACTION_DENOM` — to soften the tree-dominant
     ~half-the-rooms-are-articulation funnel if we want more flank variety.)
   - **Station thematic kinds** — [`stories/station-interior-fills.md`](stories/station-interior-fills.md)
     (`HANGAR / COMMAND / HABITATION`); sits on top of the topological role.
   - **Edge-based doors** — arms the validation scan's (currently no-op)
     edge-connectivity check; hull/wall toughness tuning; spawn spatial spread.

### Slice 1 critique follow-ups — all resolved

Background critique of `5e5ae91` confirmed behavior-preservation (no blocker);
the minor items it raised are now closed:

- **Test gap — done.** The compound-filler overlay-read path
  (`ROAD_CELLS` / `ROAD_RESERVATION`) was only integration-covered by
  `BspMapPreviewTest`. `CompoundFillerOverlayTest` now covers it in isolation:
  `MilitaryBaseFiller.fill` with overlays bound (runs, emits nodes, paints the
  wall ring) and unbound (fails fast). `BuildingZonePreviewTest` was left as-is —
  it only drives the single-leaf building fillers, which don't read the overlays,
  so binding them there would exercise nothing.
- **Dead alias locals — done (`1e0c72b`).** Unused `pois` (+ a few `doodads` /
  `rng` / `grid`) aliases and the imports they orphaned were removed from the
  POI-less fillers + `StubBlockFiller`, confirmed via IntelliJ inspection.
- **`ctx.get` null seam — addressed pragmatically.** Rather than the heavier
  `stage.requires(KEY)` build-time framework (disproportionate now that recipes
  are explicit, verified lists), the highest-risk consumers — the three compound
  fillers, which index `ROAD_CELLS`/`ROAD_RESERVATION` directly and would NPE
  deep inside — now call `CompoundFiller.requireRoadOverlays(ctx)` to fail fast
  with a named error. A general per-stage `requires()` assertion remains a
  possible future addition if recipe authoring grows error-prone.
- **Unused imports** from the sweep: already cleaned (`b8d992f`).
- **Slice 2a critique nits resolved:** `PedestrianFrameStage` `~50%` comment
  fixed in `8666b8f`; the `GenStage` "no instance fields" Javadoc vs
  `FillDispatchStage`'s config registries is documented, intentional tension.

## Validation harness (gut-check loop, this session)

The map-gen working loop leans on two complementary dev-tools-dressed-as-tests:
the **previews** (`BspMapPreviewTest` — color-coded ground/biome/tactical/road
PNGs for the eye) and now the **scans** (`MapValidationScanTest` — structural
checks that surface missing rules as numbers). Run both after touching gen
rules; re-run to see the delta.

### What landed

`MapValidationScanTest` (`src/test/java/.../world/gen/bsp/`) runs three scans
over the same 10 seeds the previews use (6 legacy 80×80 + 4 conquest 240×160):

1. **Connectivity (cell vs. edge).** The preview test's `assertConnected`
   floods 4-neighbor over `isWalkable` — *cell* connectivity. The real
   `GridPathfinder` honors per-edge walls (dual-side check, `GridPathfinder`
   L289-290). This scan floods both models and reports the delta. Because a
   diagonal move in `GridPathfinder` requires both adjacent cardinal edges,
   **cardinal edge-flood reachability == the pathfinder's full connectivity**,
   so cardinal flooding is the exact oracle. Hard-asserts `edgeComponents ==
   cellComponents`.
2. **Semantic reachability.** From the marine spawn, runs the *real*
   `GridPathfinder` to the defender spawn + every tactical node; reports the
   assault-distance distribution. Hard-asserts defender reachable.
3. **Garrison deployability.** Models the real consumer
   (`BattleSetup.pickCellsNear`, `GARRISON_SPAWN_RADIUS = 5`): a garrison
   node's anchor is the *emplacement* cell (turret mount / wall crenellation,
   intentionally non-walkable); the squad spawns on walkable cells *near* it.
   Hard-asserts every `garrisonSize > 0` node has ≥ 1 reachable deployable cell
   within radius 5; reports "cramped" (fewer than `garrisonSize`).

### What it found (baseline, all 10 seeds)

- **Connectivity delta is zero everywhere.** Cell- and edge-models agree (1
  component, identical sizes). This confirms walls today are *cells*
  (non-walkable), not edge-blocks — so the two models *can't* disagree yet.
  The edge scan is a **no-op guard now**, armed to fire the moment corridors
  introduce edge-based passages or any `blockEdge` rule. That's deliberate: the
  acceptance gate exists before the work that needs it.
- **Semantic reachability is clean.** Defender reachable on every seed; every
  garrison node deployable + reachable. Assault distances sane (legacy 4–59;
  conquest median ~110–140 on a 160-tall map).
- **Investigated + resolved — non-walkable node anchors are NOT a bug.** The
  first cut of scan #3 ("anchor must be walkable") flagged ~25 anchors per
  conquest map (`HEAVY_TOWER` / `MG_NEST` / `FORWARD_BUNKER` / `GUARDPOST`) on
  non-walkable cells. Tracing the consumer settled it: those anchors are
  *deliberately* the structure cell (`FortressWallStamper.emitHeavyTower` —
  "anchor at the turret-mount center"; `emitMgNest` — "single cell on the
  wall"; `DefensePostStamper.emitGuardpostNode` — turret-center anchor). The
  garrison allocator (`BattleSetup.pickCellsNear`) never stands a unit on the
  anchor — it sweeps a radius-5 diamond for walkable cells nearby. So the
  contract is "anchor = emplacement, garrison spawns around it," and the bug was
  in the *scan*, not the generator. Scan #3 was rewritten to model the real
  invariant (≥ 1 reachable deployable cell in radius 5); all 10 seeds pass, and
  the non-walkable anchors now print as `info:` (by design), not a finding.

### Scans still on the menu (priority candidates)

These were scoped this session but not built — each isolates a further class of
missing rule, and several become load-bearing once corridors land:

- **Doorway integrity** — per interior-wall / per-building: assert a connecting
  doorway exists. Directly relevant to the corridors doorway-coordination gap
  (`BuildingShellCore` places doorways independently).
- **Zone-accurate deployability** — scan #3 counts walkable cells in the radius
  diamond (an upper bound); the real `pickCellsNear` also filters to the
  anchor's reachable *zone*. A future tightening could build the `ZoneGraph`
  and call the real picker for an exact count. Low priority — the upper-bound
  zero-check already catches the squad-silently-dropped bug.
- **Edge-scan teeth** — currently a no-op; it gains real coverage the moment an
  edge-based passage (corridor) exists. No work needed until then beyond
  keeping it in the batch.

## Structural taxonomy — Lever 1 (this session)

The reframe and full design are in
[`stories/structural-taxonomy.md`](stories/structural-taxonomy.md). What landed:

- **The premise we threw out.** The corridors story sketched the taxonomy over
  a *connectivity graph* (degree / depth / articulation / bridges). Grounding
  the code killed that for the city: `LeafAdjacency` is trunk-*segmented*
  (clustering, not movement) and the porous walkable blob has no real
  chokepoints (a road-graph "bridge" is bypassed across the adjacent park). The
  city is the *negative* of the station — streets are the space, blocks are the
  obstacles — so **texture, not topology**, is the readable signal.
- **The artifact** (`battle.world.gen.taxonomy`, generator-agnostic):
  `TacticalRegionMap.build(grid, topology, axis)` floods the walkable space into
  `TacticalRegion`s — a cardinally-connected run of one `RegionKind`
  (`STREET / PLAZA / COURTYARD / OPEN_GROUND / RUBBLE / BUILDING_INTERIOR`,
  mapped from `GroundKind`) — each tagged with **cover density**, **mean
  exposure** (cardinal open-cross reach), **enclosure + opening count** (a
  *local* defensible-pocket measure, robust to porosity), and a geometric
  **assault-depth band** (`UNSET` in legacy mode).
- **Wiring.** `TacticalRegionStage` runs after `FinalizeStage` in both recipes;
  binds `BspKeys.TACTICAL_REGIONS`; exposed via
  `BspCityGenerator.getLastTacticalRegions()`. Pure analysis (no `rng`, no
  mutation) → **byte-identical maps**. Deliberately **not** in `MapResult` yet —
  that lands with the first runtime consumer.
- **Gut-check + gate.** `TacticalRegionPreviewTest` writes
  `build/map-previews/taxonomy-*-{kind,heat}.png` (regions by kind with
  defensible pockets ringed; cover→exposure heat) + prints per-seed summaries.
  `TacticalRegionTest` asserts the invariants (exact walkable partition,
  attributes in range, no isolated pockets, axis-driven depth, seed
  determinism).

## Captured directions (other paths for future sessions)

Surfaced while scoping this session; parked deliberately so they're not lost:

- **Validation-first as corridor prep (the chosen thread's tail).** The scan
  harness above *is* the acceptance harness `corridors-first-class` needs —
  corridors are connectivity structure, so they can't be authored without a
  reachability oracle that says whether they actually connect rooms. The edge
  scan + a doorway-integrity scan are that oracle. Carry them into the corridor
  work as pass/fail gates.
- **Battlespace readability (theorycraft finding).** The conquest previews
  read as a *uniform* sea of small buildings — the fortress/keep and compounds
  don't pop out of the city texture, and the south→north assault gradient isn't
  visually legible. This is art-direction/tuning, not structure: compound
  size/placement, fortress-district density vs. city, assault-gradient
  legibility. Gut-checkable purely via the conquest previews. No story doc yet;
  worth one if we pick it up.
- **Structural taxonomy (city) — Lever 1 landed; Lever 2 + consumer parked.**
  The general direction (generators *publish* structure, passes *consume* it as
  aspect queries) hit the city this session. Key correction: the station-shaped
  "connectivity graph + topological roles" framing does **not** transfer to the
  porous open-with-obstacles city — no real chokepoints exist, so we read
  *texture* (`TacticalRegion` segmentation) instead of topology. See the
  "Structural taxonomy — Lever 1" section below + [`stories/structural-taxonomy.md`](stories/structural-taxonomy.md).
  Still parked:
  - **First consumer (Lever-1 payoff).** Migrate one placement pass
    (`DefensePostStamper`'s biome-band proxy is the natural first) to query
    region attributes; plumb `TacticalRegion` into `MapResult` here. First
    player-visible change; RNG parity checked.
  - **Lever 2 — inject structure (its own session).** Gated courtyards / walled
    pockets / denser alleys, tagged at carve time; the artifact picks them up
    automatically. The "upgrade city generation" session.
  - Still feeds the **battlespace-readability** item above.

## Sanity check before resuming

- `gradlew.bat compileJava` clean.
- `gradlew.bat :test` — map-gen label/partition tests green. (Use the
  `:test` task, not bare `test` — the `:asset-pipeline:test` subproject has a
  pre-existing unrelated failure that red-builds a project-wide `test`.)
- `gradlew.bat :test --tests "*MapValidationScanTest*"` — scan report prints,
  all hard invariants hold.
- `gradlew.bat :test --tests "*BspMapPreviewTest*"` — regenerates the preview
  PNGs under `build/map-previews/`.
- `gradlew.bat :test --tests "*TacticalRegionTest*"` — structural-taxonomy
  invariants hold (exact walkable partition, attribute ranges, determinism).
- `gradlew.bat :test --tests "*TacticalRegionPreviewTest*"` — regenerates
  `build/map-previews/taxonomy-*-{kind,heat}.png` for the gut-check.
- `git log --oneline -1 b8b7b9d` confirms Slice D is in history.
