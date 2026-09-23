# Controlled Mech

Status: PLANNED

Written: 2026-09-23

Updated: 2026-09-23 — specified Mech clearance and pivot collision gates.

Read `direct-control-nouns.md` and `mechs-nouns.md` first.
Depends on `controlled-marine.md`.

## Goal

Use the same one-body control session for an exact deployed Marine combat
Mech, retaining chassis gait and independent mount constraints.

## Plan

1. Adapt manual travel direction to the Mech mover's pivot, speed, collision,
   and gait rules. Sweep the actual post-pivot travel against the same closed
   edges and solid cells as infantry, with Mech-specific body clearance;
   terrain-blocked motion never becomes an unearned gait step. Mouse aim
   supplies temporary torso/mount intent rather than rewriting the chassis's
   mission or lance order.
2. Expose a deliberate direct-weapon selection/trigger rule. Fire through
   each installed mount's own hardpoint, arc, cooldown, ammunition, and
   modeled round. Define indirect missiles in a separate contract before
   enabling their trigger.
3. Reconcile an active one-shot Mech move order on entry and replan on exit so
   the chassis never has two locomotion owners. Other lance members remain
   under their current assignment.

## Acceptance

The Mech neither strafes through a forbidden pivot, clips a wall corner,
crosses a closed edge, nor fires a mount through its own arc limit. Narrow
passages agree with the shared AI clearance policy. Each mount has one
cooldown/shot stream, the lance keeps acting, and exit resumes its current
AI context without a stale manual aim.
