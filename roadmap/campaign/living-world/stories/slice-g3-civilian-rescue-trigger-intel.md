# Slice G3 — civilian-rescue trigger and choice intel

**Status:** CONTRACT LOCKED (2026-08-12)

## Goal

Make the G2 civilian-rescue lifecycle naturally reachable and honestly
presented before the swarm-defense battle vertical exists.

## Hunk plan

1. Add a pure deterministic `CivilianRescueSpawnSystem`: 45-day epochs starting
   on day 30, one stable-hash-selected eligible market, a three-day choice
   window, size-derived frozen costs/stakes, no active-event overlap, and no
   historical catch-up.
2. Add the always-registered Distress Net intel page. Render the newest active
   row and route commit/refuse buttons through `CivilianRescueEvent` without
   displaying moral/material rewards.
3. Add a debug-only local rescue spawner that uses the same preparation policy,
   then checkpoint the slice while leaving committed events unresolved.

## Acceptance

- Economy iteration order and repeated daily ticks cannot change or duplicate
  the selected event snapshot.
- Automatic events do not overlap pending/committed rescues or spawn twice in
  one epoch, including after an early refusal.
- Costs, stakes, and deadline use the locked formula and persist at creation.
- The player can commit or explicitly decline from intel; insufficient cargo is
  non-mutating, and committed work visibly awaits a future mission.
- No producer/UI path writes hidden moral values directly or previews their
  existence.
- Focused automated tests and the full build pass. Manual playtesting remains
  skipped for this session.

## Deferred

- Swarm faction, art integration, unit roster, AI, mission factory, and battle
  resolution.
- Material reward policy and any diegetic reaction to the hidden compass.
