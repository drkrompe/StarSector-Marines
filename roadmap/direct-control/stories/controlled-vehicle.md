# Controlled vehicle

Status: PLANNED

Written: 2026-09-23

Updated: 2026-09-23 — required swept footprint checks across turns and closed edges.

Read `direct-control-nouns.md` and `convoy-nouns.md` first.
Depends on `controlled-marine.md`; uses the point-fire contract in `ballistics-nouns.md`.

## Goal

Use the same session for the already deployed Marine HEAVY_APC, with its
existing vehicle kinematics and independent turret. Delivery convoys remain
under their delivery state machine.

## Plan

1. Accept only a live Marine vehicle in DEPLOYED state. Hand W/S throttle or
   reverse demand and A/D steering to the vehicle controller; retain chassis
   footprint, turning room, collision avoidance, and recovery behavior.
   Validate intermediate poses during translation and rotation with
   `VehicleFootprint.isPoseFeasible` and a sweep of the whole footprint across
   closed edges, not only the center or end pose.
   Reject a blocked step at its last legal pose before recovery can run.
2. Route mouse aim and held fire through the turret's authored traverse,
   burst/cooldown, and source hardpoint. A manual trigger must not duplicate
   the turret AI's trigger in the same tick.
3. Suspend an existing player move order on entry and hand back to current
   vehicle authority on exit. Preserve passengers and payload state.

## Acceptance

The APC steers and reverses within its body constraints, fires only inside
turret limits, and neither noses through a wall nor rotates its rear through
one. It cannot cross a closed edge between walkable cells or tunnel across
an obstacle in a turn. A newly opened route becomes usable without a stale
collision cache. Exit returns it to its current order or deployed hold.
Incoming, landed, departing, allied, and enemy vehicles cannot be taken over.
Focused movement tests include a turn whose endpoints fit but whose swept
footprint intersects a wall or closed edge.
