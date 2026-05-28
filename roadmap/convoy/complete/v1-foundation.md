# Convoy V1 — foundation PoC

**Shipped `76fe54d`** (2026-05-20) — "Convoy V1: RoadGraph +
ground-vehicle layer + debug spawn". The proof-of-concept that earned
the rest of the stack.

## What landed

One vehicle spawns per battle behind `DEBUG_SPAWN_TEST_CONVOY`, drives in
from a perimeter trunk exit, deboards 6 units, and leaves. Three new
pieces, all parallel to the existing shuttle / air stack:

- **`RoadGraph`** skeleton extracted from BSP trunks + frame roads —
  depth-field local-max for frames, `TrunkPlan`-aware overlay for trunks
  (including perimeter exits), spur stitching to connect frame ends
  through trunk bands. Produces a single connected graph.
- **`Vehicle` / `VehicleType` / `GroundSystem`** parallel to
  `Shuttle` / `ShuttleType` / `AirSystem`. At V1 the body still rode the
  `AirBody` hover model (replaced in [`v1-polish`](v1-polish.md)).
- **`ConvoyPlanner`** — BFS over the road graph, cell-list waypoint
  expansion, reversal for outbound.

## What this was not yet

- No bicycle kinematics — the hover model orbited tight waypoints.
- No docking, no wall constraint — trucks clipped buildings.
- No reinforcement-system integration — pure debug spawn.
- One vehicle type (MILITIA_TRUCK, since retired), default `COMBATANT`
  loadout.

Everything above was addressed in the V1-polish maturation pass and the
reinforcement integration — see [`v1-polish.md`](v1-polish.md) and
[`reinforcement-integration.md`](reinforcement-integration.md).
