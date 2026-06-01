# Fog of War

Per-cell fog-of-war driven by player-faction unit vision. Enemies only render
when a contributor unit has line-of-sight to their cell.

## Status

**V1 is shipped.** Per-cell shadowcast vision, ref-counted reveal bitmap,
6-cohort dispatch, the unit-visibility render gate, and ephemeral shuttle/
fighter vision sources all landed. Full architecture record in
[`complete/fog-of-war-v1.md`](complete/fog-of-war-v1.md).

The fog/vision spine is in `VisionService`; queries route through it (don't
iterate `sim.getUnits()` for reveal state). See the memory note
[`project_fog_of_war`](../../) for the one-line gotcha index.

## Active stories

- [`stories/time-of-day.md`](stories/time-of-day.md) — day/night as a
  **gameplay system**, not just a render effect: night shrinks `visionRange`
  (the fog tie-in) and a dawn threshold drives reinforcement triggers. V1 is a
  battle-start ambient preset; the type is shaped so an animated cycle slots
  in without rework.

## Backlog

Smaller follow-ups, not yet sliced into stories:

- **Merge `BuildingVisibilityPass` into the fog bitmap** — buildings reveal
  from cell coverage rather than a parallel pass.
- **Last-known-position ghosts** — faded silhouette at a unit's last seen
  location after it goes HIDDEN.
- **Shot/projectile visibility gating** — shots are currently always visible
  (intentionally ungated in V1); gate them on cell reveal.

## Cross-refs

- [`battle-render/`](../battle-render/overview.md) — the layered draw-list
  pipeline the fog overlay and unit gate render through; the time-of-day
  lightmap-multiply pass slots in here too.
- [`ecs-migration/`](../ecs-migration/overview.md) — `unitVisibility` /
  `fadeAlpha` are keyed by dense index, sharing the SoA registry seam.
- [`conquest/`](../conquest/README.md) / [`../reinforcement/`](../reinforcement/) —
  consumers of the dawn-arrival reinforcement trigger once the time-of-day
  animated cycle lands.

## Memory entries to read alongside

- [`project_fog_of_war`](../../) — per-cell shadowcast vision, ref-counted
  bitmap, 6-cohort dispatch, unit visibility gate; shots intentionally ungated.
- [`air_unit_render_sync`](../../) — air-unit motion is composed not inherited;
  relevant to ephemeral shuttle/fighter vision sources.
