# S3d — Shuttle scale-down handoff (stub)

> Shuttles fly from the fleet layer and "scale down" to land on the surface, dropping off
> ground forces. A presentation transition + a gameplay handoff between the two engines.
> Scoped, not built. Wishlist item from the fleet-above/ground-below vision.

## Goal

A shuttle ([[air_vehicle_kinematics]] `AirBody`) descends from the fleet layer toward the
surface; a camera/LOD transition zooms from fleet scale to ground scale; at "touchdown"
the troops leave the fleet/combat layer and appear as a squad in the ground sim.

## Why this exists

It's the diegetic bridge between the two scales — how player ground forces *get* into the
ground battle that the rest of the S3 phase renders and couples. Also the most expressive
moment of the fleet⇄ground interaction.

## Scope

**In:**
- A shuttle entity descending to a landing zone (reuse `AirBody`); the free camera
  (`viewport.set()`) drives the zoom from fleet scale toward ground scale.
- The handoff: on touchdown, spawn/activate the dropped squad in the `BattleSimulation`
  (via the same sim-side control channel S3a uses).

**Out:**
- Full LOD pipeline for everything (just enough scale transition to read the landing).
- Whether the player *follows* the shuttle down or stays at fleet view — a UX decision
  to settle when building (note both).
- Combat consequences of the shuttle being shot down en route (later — ties to S3a area
  damage + the proxy model for the shuttle itself).

## Open questions

- Is the shuttle a vanilla entity (fighter/ship) on the combat layer, or a sim `AirBody`
  drawn on the backdrop layer, during descent? The scale transition likely hands off
  from one representation to the other at a threshold zoom.
- Does "scale down" mean a continuous camera zoom into the same world, or a discrete
  swap into the ground-sim view? (Continuous reads better but couples the scales harder.)

## Acceptance

A shuttle visibly leaves the fleet, descends, and on landing the troops exist in the
ground sim; the camera transition between scales reads cleanly. Verdict: continuous vs.
discrete scale handoff, and follow-cam vs. stay-at-fleet.
