# D4 — Durability balance pass

> Tune time to break and time to kill as separate promises.

Status: PLANNED
Written: 2026-08-23
Updated: 2026-10-18 — mech and hard-target time-to-kill harnesses exist (`mech-time-to-kill`, `hard-target-time-to-kill`); AMR (70, five rounds) and mech heavy cannon (contact 100) tuned; readings in `combat-durability-nouns.md`, Platform identities.

Read `combat-durability-nouns.md` before implementing this story.

## Goal

Calibrate armor capacitys, ratings, structure, damage, and penetration around
readable infantry and mech relationships rather than inherited HP totals.

## Scope

- Extend the real-pipeline TTK harness with armor profiles, mechs, turrets, and
  drone hubs.
- Report time to armor break and time to kill independently across relevant
  cover, range, grade, and experience cases.
- Preserve ordinary infantry matchup feel as the migration anchor, subject to
  the outstanding S1 live feel pass.
- Establish SMG/pulse/field-rifle/DMR/AMR/rocket and mech-weapon penetration
  bands, then tune platform profiles against them.
- Run mission-level acceptance for ammunition pressure, target selection,
  mech role readability, and mixed-force encounters.

## Acceptance

- DMR advantage grows with armor rating while close automatic weapons remain
  effective against exposed structure.
- AMRs, rockets, and heavy cannon are practical answers to intact mech armor
  without becoming universal best weapons against soft targets.
- Armor endurance orders Bulwark above Hound above Warden; Warden remains
  screen-dependent and Hound remains meaningfully less durable than Bulwark.
- Published calibration artifacts state both break-time and kill-time targets,
  sample size, and tolerated variance.
- Live play confirms the numerical relationships remain readable in missions.

