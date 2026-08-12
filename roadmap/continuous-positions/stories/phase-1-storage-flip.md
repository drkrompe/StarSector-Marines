# Phase 1 — POSITION storage flip (behavior-identical)

`POSITION` component `INT,INT` → `FLOAT,FLOAT` in continuous cell space
(see overview for the convention). No behavior change: the mover still snaps
cell-to-cell, `RENDER_POSITION` keeps its old cell-index-space convention,
every distance and gate computes the same values as before. Gate:
`gradlew.bat build` green (compiles + full test suite).

## Changes

### `battle/component/BattleComponents.java`
- Register `POSITION` as `FieldKind.FLOAT, FieldKind.FLOAT`.
- Rename field constants `POSITION_CELL_X/Y` → `POSITION_X/POSITION_Y`
  (forces every direct column walk through the compiler). Update Javadoc:
  "continuous position x (FLOAT) — cell (cx,cy) spans [cx,cx+1), center at
  cx+0.5; floor for the grid cell."
- Update the class-level `table.ints(POSITION, …)` doc example.

### `battle/sim/World.java`
- New authoritative accessors, same style as the cell pair they replace:
  `float x(long)`, `float y(long)`, `void setPos(long, float, float)` —
  fail-loud, POSITION persists alive→dead (corpse still answers).
- `cellX(id)` / `cellY(id)` keep their `int` signatures, now derived:
  `floor(x)`. Javadoc: derived grid cell for nav/LoS/fog lookups.
- `setCellPos(id, cx, cy)` keeps its signature, now writes the cell center
  `(cx + 0.5f, cy + 0.5f)`. Javadoc: convenience for spawn/nav code that
  thinks in cells.
- Update the class Javadoc paragraph about the cell pair.

### `battle/sim/MovementService.java`
- `advanceAlongPath` keeps its exact cell-hop behavior this phase. It reads
  `world.cellX/cellY` (now floored — identical values, since units only ever
  sit on centers this phase) and calls `world.setCellPos` on arrival
  (now writes the center — floors back to the same cell). Verify the
  render-lerp math still produces the same values; do not rewrite the method.

### `battle/unit/UnitRosterService.java` (adopt/spawn seeding)
- Seed `POSITION` floats from `spec.cellX/cellY` as centers
  (`cellX + 0.5f`). The two direct `POSITION_CELL_*` writes become float
  writes with the new constants.

### `battle/appearance/FacingSystem.java`
- The bulk column walk `t.ints(POSITION, POSITION_CELL_X)` →
  `t.floats(POSITION, POSITION_X)`. Facing deltas computed from floats
  (identical values this phase — everything sits on centers; the shared +0.5
  offsets cancel in deltas). Keep any cell-space math by flooring locally
  where a true grid cell is required.

### `battle/nav/NavigationService.java` (rebuildOccupancyMap:228-229)
- Column walk goes float; bin at `(int) Math.floor(x)`.

### `battle/unit/UnitType.java`
- Add `public final float radius;` (cells) to the stat block, wired through
  the ctor like the other stats. Values: infantry/civilian-family 0.3f,
  drone 0.35f, turret 0.45f, hub 0.5f, mech 0.6f. Unconsumed this phase —
  documented as "collision/AoE footprint radius, consumers arrive in
  phase 2c".

### Tests
- `battle/unit/DeadBodySystemTest` column walk → floats + new constants.
- Any test asserting exact cell equality still passes (floor of center).
- Add a small `WorldPositionTest` (or extend an existing World/roster test):
  spawn at cell (3,4) → `x()==3.5f`, `cellX()==3`; `setPos(3.99f, 4.0f)` →
  `cellX()==3`, `cellY()==4`.

## Out of scope (later phases)
- Any change to `RENDER_POSITION`, the mover algorithm, arrival gates,
  spatial index, drone floor-back, `EntitySpec` (stays int cells — spawns
  are naturally cell-addressed).
