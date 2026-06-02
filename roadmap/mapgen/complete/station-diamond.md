# Diamond defense station — cardinal ports converging inward — ✅ SHIPPED

Commit `f04c2d5`. A third station layout (after the BSP warren and the square
concentric onion), and the most characterful: a **diamond/cruciform** footprint
with 4 cardinal **landing-zone ports** and isolated corridors converging inward
to a besieged core. Square rooms throughout — the "diamond" is the macro
silhouette (dead map corners), *not* diagonal walls.

## The one geometric move

Outer rings **omit their 4 corner rooms**. That single change does two things at
once — exactly the two ideas the user proposed:
1. the 4 map corners become **dead space** → the diamond/cruciform footprint;
2. the corners were what linked a ring's galleries laterally, so the 4 edge
   galleries become **isolated cardinal ports**, each leading inward only.

Everything else falls out of the topology we already had:
- **Radial spokes** — same-side galleries of consecutive rings are linked on all
  4 sides (the cardinal corridors). With no lateral links, every outer spoke
  corridor is a must-pass **bridge** → the forced-inward gauntlet (the "geometric
  reason fighting gets intense").
- **Connective ring** — the innermost ring (`CONNECTIVE_RINGS = 1`) keeps its
  corners + the intra-ring loop, so the 4 sections finally interconnect here.
- **Core gate** — exactly one, from the connective ring → the core stays a
  degree-1 besieged objective.

The roles preview tells the whole story: outer shell all red (bridges), one cyan
connective loop, a radial depth gradient green-port→red-core. With a single spawn
at one port, the other 3 spokes sit off-path — the believable "untraversed far
side."

## What landed

- **`RingGeometry`** (new, shared) — the nested-rect insets + the per-band 8-rect
  subdivision (`TL, TOP, TR, RIGHT, BR, BOTTOM, BL, LEFT`; `galleryIndex(side) =
  2·side+1`). `ConcentricLayoutStage` was **refactored onto it**
  (behavior-preserving — its tests are byte-identical), so the diamond doesn't
  duplicate the bug-prone rect math.
- **`DiamondLayoutStage`** — outer rings = 4 isolated galleries (corners omitted),
  connective inner ring = 8-room loop, 4 radial spokes, 1 core gate. Publishes
  `StationGraph` + rings + **ports**.
- **`StationGraph.setPorts()` / `ports()`** — the cardinal landing-zone room ids,
  **published for a future multi-spawn insertion** (the battle system is
  single-spawn today: `MapResult.marineSpawnX/Y` + `BattleSetup.pickLandingZones`
  cluster squads at one point; multi-port insertion lands forces at several ports
  and is a clean follow-on now that the structure exists).
- **`StationCarve.connect`** — the shared carve-then-record-edge primitive (graph
  never claims a connection the cells don't have); both ring layouts use it.
- `CoreSpawnStage` **reused as-is** — it already picks marine = a random
  outermost-ring room (= a random port) and defender = the core.
- `BspCityGenerator.generateDiamondStation` + `DiamondStation` recipe. The BSP +
  concentric stations are kept.

### Per-seed variation
Geometry is dimension-derived; the marine's **port** + the **core-gate side** vary
per seed (e.g. core depth ranges 3–7 across seeds while maxDepth stays 8). All
deterministic from the seed.

## Gate (all 6 seeds green)
- `MapValidationScanTest.scanDiamondStationBatch` — one walkable component (no
  port left a dead pod) + marine→core pathable via the real `GridPathfinder`.
- `StationTopologyTest.diamondStationStructureInvariants` — every port is a
  degree-1 bridge entry; the core is a degree-1 bridge room; outer-shell rooms are
  never on a loop while a connective ring loops; valid BFS depth layering; and the
  **4 map corners are dead** (the diamond footprint) — all cross-checked by the
  independent brute-force bridge oracle. Per seed: 17 rooms, 4 ports, 9 bridges.

### Design note — depth is radial, not concentric
The "monotone inward" invariant from the concentric station is **false** here: the
3 non-entry ports are dead-end branches, so depth rises *outward* toward them. The
valid invariant is BFS-layering (every corridor spans ≤ 1 depth step), measured
from the single entry port.

## Follow-ups
- **Multi-port insertion** (battle-system) — land forces at several ports at once,
  converging inward. The ports are already published on `StationGraph`; needs
  `MapResult`/`BattleSetup` multi-spawn.
- **Sharper diamond** — corner-bite size / staggered rooms for a tighter rhombus
  vs the current cruciform.
- **Tunable gauntlet depth** — `CONNECTIVE_RINGS` (how deep before sections
  merge); 1 today.
- **First role-querying placement pass** — emplacements at the spoke bridges
  (defender chokepoints), still the next gameplay step; the diamond makes them
  unmissable.
