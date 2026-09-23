# Controlled vehicle

Status: PLANNED

Written: 2026-09-23

Read `direct-control-nouns.md` and `convoy-nouns.md` first.
Depends on `controlled-marine.md` and `point-aim-direct-fire.md`.

## Goal

Use the same session for the already deployed Marine HEAVY_APC, with its
existing vehicle kinematics and independent turret. Delivery convoys remain
under their delivery state machine.

## Plan

1. Accept only a live Marine vehicle in DEPLOYED state. Hand W/S throttle or
   reverse demand and A/D steering to the vehicle controller; retain chassis
   footprint, turning room, collision avoidance, and recovery behavior.
2. Route mouse aim and held fire through the turret's authored traverse,
   burst/cooldown, and source hardpoint. A manual trigger must not duplicate
   the turret AI's trigger in the same tick.
3. Suspend an existing player move order on entry and hand back to current
   vehicle authority on exit. Preserve passengers and payload state.

## Acceptance

The APC steers and reverses within its body constraints, fires only inside
turret limits, never drives through a wall, and returns to its current order
or deployed hold on exit. Incoming, landed, departing, allied, and enemy
vehicles cannot be taken over.
