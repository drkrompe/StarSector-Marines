# Continuous positions — point + radius units over a nav grid

Migrate battle-sim ground units from grid-cell locations (int cell pair, discrete
cell-hop movement) to continuous float positions with a per-type radius. The
navigation grid keeps its job unchanged: route planning (A*), LoS (cell
Bresenham), fog-of-war (per-cell shadowcast), zones. Units *live* at float
points and *consult* the grid by flooring at the boundary — the pattern air
craft (`AirBody`) and convoy vehicles (`GroundBody` + `PurePursuit`) already
prove out.

## Coordinate convention (locked)

Continuous **cell space**: cell `(cx, cy)` spans `[cx, cx+1) × [cy, cy+1)`,
center at `(cx + 0.5, cy + 0.5)`. A unit standing on cell `(3, 4)` has
position `(3.5, 4.5)`.

- `cellX(id) = floor(x)` — derived, keeps the existing `int` accessor
  signature so ~394 read sites compile unchanged.
- Distances between units standing on cell centers are identical to the old
  cell-index distances, so all range/falloff tuning carries over untouched.
- Render draws at `x` directly (the old `renderX + 0.5f` convention unifies
  away in Phase 2a; during Phase 1 `RENDER_POSITION` keeps the old
  cell-index-space convention and behavior is bit-identical).

## Why this is tractable (survey findings, 2026-08-12)

Two exhaustive code surveys established:

- `RENDER_POSITION` (float pair) already exists on every unit and survives the
  corpse transmute; `BattleCamera.cellToScreen*` already takes floats; the
  entire shot/projectile/FX pipeline is already float (`ShotEndpoint` is
  documented as deliberately preferring render position).
- Vehicles and air are already fully continuous and coexist with cell-based
  fog/LoS/AI via ad-hoc `floor()` at the boundary. Drones are the hybrid
  (continuous body floored back to a cell each tick) — that hack deletes.
- ~394 `cellX/cellY` call sites (114 `infantry`, 123 `decision`+`goap`, 34
  `mech`, 21 `combat`, 18 `squad`); nearly all are *reads* that keep working
  against a derived floor. Direct `POSITION` column walks are contained to
  `FacingSystem`, `NavigationService.rebuildOccupancyMap`,
  `UnitRosterService.adopt`, `World`, + 1 test.
- There is **no hitbox today**: hits are a to-hit roll against an entity id;
  no melee exists; no unit has a radius. The radius buys accurate AoE
  (`Detonations`), real click-picking (`WorldPicker`), arrival tests, and
  (later) separation steering — not hit resolution.
- Occupancy is already soft (per-cell count, A* cost penalty only); no hard
  cell reservation exists to unwind.

Danger points (all catalogued in the phase stories):

- The `moveProgress == 0f` idiom is the universal "standing on a cell center,
  safe to repath/act/change stance" gate (~20 sites). Continuous motion
  removes that semantic; replacements are arrival-radius + speed tests.
- ~12 exact-cell-equality arrival tests (`cellX(m) == postX`) will chatter or
  deadlock under continuous motion — become distance thresholds.
- ~25 `setRenderPos(id, cellX, cellY)` stop-snaps in AI hold/idle branches
  must be deleted with the mover rewrite or units visibly teleport.
- `LosCache` keys are 12-bit-packed int cells; `UnitSpatialIndex` snapshots
  int cells into its buckets. Both get floored floats / float snapshots.

## Stories

1. **phase-1-storage-flip** — `POSITION` goes `FLOAT,FLOAT` (+ `UnitType.radius`);
   `World.x/y/setPos` added; `cellX/cellY` become derived floors; direct column
   walks updated. Behavior-identical; build + tests green.
2. **phase-2a-continuous-mover** — `advanceAlongPath` rewritten as carrot-
   following continuous integration (template: `vehicle/PurePursuit`);
   `RENDER_POSITION` deleted, `renderX/renderY` become tolerant reads of
   `POSITION`; facing/locomotion phase derive from velocity.
3. **phase-2b-arrival-semantics** — behavior-family sweep replacing
   `moveProgress` gates, cell-equality arrivals, and stop-snaps.
4. **phase-2c-spatial-boundaries** — `UnitSpatialIndex`/`UnitDestinationSpatialIndex`
   go float; occupancy/fog/LoS/zone lookups floor at the boundary;
   `Detonations` + `WorldPicker` consume real positions + radius; drone
   floor-back hack deleted.

Deferred (follow-up stories, not this migration): Theta*/string-pulled path
smoothing, surface-to-surface range (subtract radii), geometric hit
resolution. Unit-unit separation steering is now **active** — see
[`stories/separation-steering.md`](stories/separation-steering.md).

## Cross-refs

- `roadmap/ecs-migration/` — storage/Service architecture this builds on.
- `battle/vehicle/PurePursuit.java`, `VehicleControlSystem` — the continuous
  mover template.
- Memory: `battle_entity_storage_topology`, `air_unit_render_sync`,
  `battle_component_naming_convention`.
