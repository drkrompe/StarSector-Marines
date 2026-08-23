# 30 — Mech formation discipline

**Shipped 2026-08-22 in `861a5bf1`.**

**Formation tuning superseded by `31-adaptive-squad-formations.md`**
(`4c0864d9`): 2.5 cells is now the constrained-terrain floor rather than the
open-ground target, and members of one squad receive terrain-aware slots.

## Player-facing result

Hounds no longer convince one another that an unsupported close-assault push
has a proper screen. They still advance with combat infantry or a different
mech chassis nearby. While allied mechs are moving, every chassis also receives
a gentle short-range repulsion from its lance-mates so mixed and same-variant
groups travel as a loose formation instead of piling onto one centerline.

## Shipped contract

- `BreachAndAssault.nearestSupport` rejects a mech whose effective
  `MechVariant` matches the advancing Hound. The identity profile is
  authoritative, with the attached loadout as a legacy-safe fallback.
- Same-faction combat infantry and a different live, non-rescue mech variant
  retain the twelve-cell acquisition radius and six-cell lead clamp from
  `28-assault-mech-cohesion.md`.
- `SeparationSystem` adds a weaker non-overlap correction for same-faction mech
  pairs while either unit has an active path. It applies to Bulwark, Hound, and
  Sirocco without coupling formation behavior to their doctrine.
- The initial tuning is 2.5 cells center-to-center at 0.04 stiffness. The
  existing physical-overlap correction remains additive and keeps its 0.5
  stiffness and 1.5-cells/second cap.
- Idle allied mechs keep authored posts, enemy mechs receive no formation
  force, and the existing collision-stall escape suppresses mech-on-mech
  corrections when a walker needs to break a deadlock.

## Verification

- `AssaultAssignedObjectiveTest` distinguishes a Bulwark support anchor from a
  second Hound and keeps the infantry, enemy, distance, and hold-and-fire cases
  covered.
- `SeparationSystemTest` exercises all three current variants, gradual
  formation spacing, and non-interference for idle allies and nearby enemies.
- Focused suites and the full `gradlew.bat build` passed before integration.

## Manual follow-up

Use the DEBUG family/lance fixtures to tune the 2.5-cell spacing and 0.04
stiffness by eye in streets and door approaches. The force should break up a
stack without making walkers orbit, fight distinct authored firing posts, or
lag materially behind their paths.
