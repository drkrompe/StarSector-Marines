# Fog of War — V1 (shipped)

Per-cell fog-of-war driven by player-faction unit vision. Enemies only render
when a contributor unit has line-of-sight to their cell.

**Shipped:** `cce5e26` (extract `VisionService` — fog state + visibility tick),
`a883bd3` (V1 — per-cell shadowcast vision + unit visibility gate). Earlier
LOS-driven reveal groundwork landed in `786a3e4` (building roofs + LOS reveal).

## Architecture

### Data

- `UnitType.visionRange` / `Unit.visionRange` — how far the unit can see (cells).
  Strictly >= attackRange so units spot enemies before they enter weapon range.
- `VisionService.revealCount[w*h]` — ref-counted short array. Each contributor's
  shadowcast increments cells it can see; recomputation decrements old, increments new.
- `VisionService.cellRevealed[w*h]` — derived boolean: `revealCount[i] > 0`.
- `VisionService.unitVisibility[denseIdx]` — HIDDEN / VISIBLE / FADING per unit.
- `VisionService.fadeAlpha[denseIdx]` — smooth alpha for FADING units.

### Shadowcast

Recursive 8-octant shadowcasting (`Shadowcast.java`). Air units (drones, shuttles)
pass their `airLosRadius` so walls near the observer are treated as transparent —
they see over nearby rooftops.

### Cohort dispatch

Contributors are round-robin'd across 6 cohorts. Each vision tick (~10 Hz) processes
one cohort. Contributors that haven't moved since their last shadowcast are skipped.
This keeps per-tick cost flat regardless of contributor count (O(N/6) shadowcasts per
tick where N = contributor count).

### Rendering

- Fog overlay: `SolidQuadBatch` pass after doodads, before units. Unrevealed cells
  get a dark quad at 0.85 alpha. Boundary cells get softer alpha proportional to
  how many neighbors are unrevealed (edge softening).
- Unit gate: `renderUnits` / `renderDrones` check `unitVisibility[denseIdx]` —
  HIDDEN units skip draw, FADING units draw with decaying alpha.
- Building roofs: unchanged — the two systems stack (fog darkens terrain, roofs
  occlude interiors).

### Ephemeral vision sources

Shuttles and strafing fighters contribute vision without going through the cohort
system. Each frame, BattleScreen clears the ephemeral source list and re-pushes
current positions for all visible player-faction shuttles (range 50, airLos 3.5)
and fighters (range 40, airLos 5.0). VisionService processes them on vision-tick
frames: decrement old footprint, shadowcast from new positions, increment.

## Tuning knobs

| Knob | Location | Default |
|------|----------|---------|
| Cohort count | `VisionService.COHORT_COUNT` | 6 |
| Fog alpha | `BattleScreen.renderFogOverlay` | 0.85 |
| Edge alpha per neighbor | `BattleScreen.renderFogOverlay` | 0.15 |
| Fade rate | `VisionService.advanceFade` | 3.0/sec |
| Vision ranges | `UnitType` enum entries | varies |
