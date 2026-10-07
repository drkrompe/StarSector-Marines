# C13 — Complete the task force

Status: PARTIAL — bulk home-command assignment implemented; officer outcomes and live acceptance remain
Written: 2026-08-23
Updated: 2026-10-06 — folded bulk organization semantics into the company and
personnel noun docs; retained Armory acceptance remains open.

Read `company-view-nouns.md` before changing this story.

## Open outcomes

### 1. Resolve every participating officer

`MissionOutcome` still carries one `captainId`, so only the operation commander
earns experience, risks injury, and receives wipe-fate resolution. The frozen
outcome must identify every officer who actually led selected squads, and
mission resolution must apply the appropriate consequences to each without
reconstructing the task force from a later roster state.

Coordinate the after-action presentation with `c6-after-action-by-fireteam.md`.

### 2. Accept bulk organization in the live company gallery

Fleet Armory's company squad gallery stages an explicit multi-squad assignment
to one active home officer or Unassigned. The roster rechecks the entire batch,
preserves rank caps and stationed locks, and commits all assignments together.
The standing model is in `company-view-nouns.md` and `personnel-nouns.md`.

The shared UI snapshot catalog covers valid and over-capacity drafts, explicit
Unassigned, a twenty-four-squad gallery, and enlarged UI scale. These establish
headless presentation, not in-game feel or campaign persistence.

The live acceptance still needs a company of roughly twenty squads: move several
squads between officers, refuse an over-capacity destination, clear assignments,
confirm stationed formations remain locked, and cancel or leave without applying.
Confirm home assignments persist through save/load and ordinary deployment still
resolves unassigned squads to the operation commander.

Formation grouping and richer whereabouts remain owned by
`c3-company-card-stack.md` and `c4-whereabouts-and-deployed-state.md`.

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
