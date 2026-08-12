# Phase 2c — spatial services + boundary floors + radius consumers

- `UnitSpatialIndex`: snapshot arrays `int[]` → `float[]` (true positions),
  `gather(float cx, float cy, float radius, out)`; bucket binning floors.
  Same for `UnitDestinationSpatialIndex` (dest stays a cell — only the
  "already there" compare changes to arrival-radius).
- `NavigationService` occupancy map: bin current position via floor (dest
  cell unchanged). Semantics documented as a density field.
- Fog of war: floor at the shadowcast-origin and visibility-check sites
  (`FogOfWarService` ~3 sites); `lastCellX/Y` move-gate keeps flooring.
- LoS callers: floor both endpoints before `NavigationGrid.hasLineOfSight` /
  `LosCache` (12-bit int key unchanged).
- Zone/region lookups (`zoneIdAt`, `regionIdAt`, `biomeAt`): floor at call.
- Radius consumers land here: `Detonations` radius test becomes
  `dist(pos, endpoint) <= aoeRadius + type.radius` on true positions;
  `WorldPicker` picks on `type.radius`; arrival helper may consult radius.
- Drone floor-back hack in `DroneSwarmAction` deleted — drone POSITION is
  written from its `AirBody` as true floats (or drones drop the dual body
  entirely if the mover subsumes them — decide in-story).
- `DeathEvent` record: carry float x/y (floor at construction sites that
  need cells).
