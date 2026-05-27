# 16 — Assault Commander

**Active.** Third commander partition shape, pulled from
[`12-squad-of-squads.md`](12-squad-of-squads.md) Assault section. Adds
strategic sweep coordination to ASSAULT missions where squads currently
dogpile the nearest defender cluster.

## What ASSAULT is

Search-and-destroy on a wide urban map. No fixed objectives (no charge
sites, no compounds to capture). Victory = eliminate all defenders.
Squads need to sweep distinct areas to prevent the dogpile-on-nearest
convergence that makes multi-squad deployment pointless.

## Partition shape: sector grid

Distinct from Sabotage (objective-cluster) and Conquest (lateral strips).
ASSAULT has no traversal axis and no named targets.

**Uniform rectangular grid** over the map cells, zones bucketed by
centroid into sectors:

- `sectorCols = clamp(gridWidth / 30, 2, 3)`
- `sectorRows = clamp(gridHeight / 15, 2, 3)`
- Produces 4-9 sectors on typical maps (100x50 = 3x3 = 9; 60x30 = 2x2 = 4).

Each `NavigationZone` is assigned to exactly one sector by its centroid.
Per-sector zone lists and sector centroids are precomputed at lazy init
(first tick, same as ConquestCommand).

## Assignment: non-sticky, proximity-based

Unlike Conquest's sticky strip assignment, ASSAULT re-evaluates
squad-to-sector each slow tick. Why: no directional push means squads
must converge on remaining hotspots as sectors clear. Sticky assignment
would strand squads in empty sectors.

Per slow tick:

1. Compute **active sectors** (any sector with >= 1 defender-occupied zone).
2. **Greedy nearest-sector**: each alive marine squad assigned to nearest
   active sector by centroid distance. Bias toward current sector to
   prevent flip-flop churn.
3. **Load balance**: when squads outnumber active sectors, surplus doubles
   up on the sector with the most defender-occupied zones. This is
   implicit convergence — no explicit `CONVERGE_ON_CONTACT` kind needed.
4. Within the assigned sector, pick **nearest defender-occupied zone**
   -> write `CLEAR_ZONE`. Null if sector fully clear.

## What's reused

- `CLEAR_ZONE` assignment kind + `ClearAssignedZoneGoal` + `ZoneQueries.synthesizeZonePushPlan` (zone-by-zone sweep via EnterZone + ClearZone)
- `ObjectiveAssignment.clearZone()` factory
- `ZoneQueries.zoneClear()` for zone occupancy checks
- Idempotent re-assignment guard (same pattern as Sabotage/Conquest)

## Observable behavior

- Squads spread across the map into distinct sectors instead of clustering
- As sectors clear, freed squads reposition to remaining hotspots
- Multiple squads converge on the last occupied sector
- Debug panel: `ClearAssignedZone` goal with zone targets in different map regions

## Status

**Shipped** (2026-05-27). `AssaultCommand` wired in `BattleSetup.createPlaceholder`
for `MissionType.ASSAULT`.

## What's not in v1

- `SWEEP_SECTOR` / `CONVERGE_ON_CONTACT` as distinct `AssignmentKind` values
  (reusing `CLEAR_ZONE` is sufficient — behavior distinction doesn't justify
  new goal types yet)
- Defender-side assault commander (gated on doc 15 perception)
- Contact convergence as an explicit mechanism (v1 convergence is emergent
  from the load-balancing math)

## Cross-references

- [Squad-of-squads design](12-squad-of-squads.md) -- parent doc, Assault commander section
- [Perception & influence](15-perception-and-influence.md) -- gates defender-side commander
- [Story bank](10-tactical-stories.md) -- Story K (room-clear sweep) provides the tactical layer
