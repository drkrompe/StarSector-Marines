# Slice 3 — Retire the dead RoadGraph routing

> Cleanup, gated on a consumer audit. Delete what the cost router made dead.

## Goal

Remove `RoadGraph`-based routing now that the cost field is the router — but
only after confirming nothing else depends on the graph.

## What lands

1. **Audit RoadGraph consumers.** Grep the tree for `RoadGraph`,
   `RoadGraphBuilder`, `ConvoyPlanner.planPath`, `expandToWaypoints`,
   `pickExitNode`. Classify each: routing (dead after slice 2), rendering, debug
   overlay, tests, other systems.
2. **Delete the dead routing code** — `ConvoyPlanner.planPath` /
   `expandToWaypoints` / `pickExitNode`, and `RoadGraphBuilder` / `RoadGraph`
   themselves **iff** the audit shows no non-routing consumer. If something else
   uses the graph (e.g. road rendering reads it), keep that surface and delete
   only the routing methods; record what stayed and why.
3. `ConvoyPlanner` either shrinks to nothing (delete) or keeps only whatever
   survives the audit.

## Out of scope

- Cost/clearance tuning (slice 4).

## Acceptance

- Build + tests green with the routing code gone.
- No dangling references; no `RoadGraph` left solely for routing.
- The shipped doc records the audit result (what was deleted, what stayed and why).

## Notes

- Roads-as-cells (`GroundKind.STREET` → cost bias) is all routing needs now; the
  centerline graph adds nothing once the cost field is in.
- If road *rendering* turns out to read `RoadGraph`, that's the one legit keeper
  — note it and leave it.
