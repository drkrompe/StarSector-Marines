# C13 — Complete the task force

Status: PARTIAL — slice 1 shipped; officer outcomes and assignment UX remain
Written: 2026-08-23
Updated: 2026-08-23 — folded per-officer deployment command into `company-view-nouns.md`.

Read `company-view-nouns.md` before changing this story.

## Open outcomes

### 1. Resolve every participating officer

`MissionOutcome` still carries one `captainId`, so only the operation commander
earns experience, risks injury, and receives wipe-fate resolution. The frozen
outcome must identify every officer who actually led selected squads, and
mission resolution must apply the appropriate consequences to each without
reconstructing the task force from a later roster state.

Coordinate the after-action presentation with `c6-after-action-by-fireteam.md`.

### 2. Make organization practical at scale

Assigning dozens of squads by cycling one squad through one officer at a time is
too costly. Add a bulk, readable squad-to-officer assignment affordance on the
company roster surface. It must preserve officer squad caps and make unassigned
squads—and their operation-commander fallback—explicit.

Coordinate the surface with `c3-company-card-stack.md` and
`c4-whereabouts-and-deployed-state.md`.

## Acceptance

- Every officer represented in the frozen task force receives the correct
  mission outcome exactly once.
- Outcome application does not depend on mutable post-mission squad assignments.
- The after-action view explains which officers participated and what happened
  to them.
- A large company can assign or move several squads between officers in one
  deliberate interaction while respecting rank capacity.
- Rosters without assignments retain the operation-commander fallback.

## Out of scope

- Raising rank caps to hide a task-force-scale deployment.
- Changing stationing into a multi-officer posting.
- Retuning mission force requirements.
