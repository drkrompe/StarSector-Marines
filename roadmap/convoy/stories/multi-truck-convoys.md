# Story: multi-truck convoys + spacing

**Partially landed.** The LZ-separation half shipped with
[`reinforcement-integration`](../complete/reinforcement-integration.md)
(`MIN_DEST_SEPARATION` keeps concurrent dispatches off each other's
junctions). What's left is *same-road staggered following* — a single
dispatch that emits 2–4 trucks down one road.

One truck per spawn reads as a coincidence; three reads as a deliberate
reinforcement push. This is the next visual upgrade after Conquest
integration.

## Scope

- **Spawn cadence.** A `ConvoyAssignment(VehicleType type, int count,
  float staggerSec)` analogous to `ShuttleAssignment` — each truck spawns
  `staggerSec` after the previous, down the same planned path.
- **Following distance.** Trucks consume the same waypoint queue but keep
  2–3 cells between centers. Cheapest implementation: a per-truck "lead
  truck" reference; if `body.distanceTo(lead.body) < minFollowDist`, clamp
  `desiredFwd` lower. Avoids real car-following dynamics.
- **Stagger on deboard.** Trucks arrive at the same LZ in sequence; the
  LZ deboard scan already handles "no free cell, retry next tick," so the
  second truck just waits its turn.

## Open

- Does a multi-truck dispatch share one Hybrid A* plan (cheaper, identical
  paths) or re-plan per truck against the others' projected positions?
  Shared plan is the cheap start.
