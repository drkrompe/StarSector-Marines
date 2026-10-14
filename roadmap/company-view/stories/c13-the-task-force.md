# C13 — Complete the task force

Status: PARTIAL — bulk home-command assignment implemented; per-officer outcomes implemented; live acceptance remains
Written: 2026-08-23
Updated: 2026-10-14 — the Armory squad gallery and its Organize squads mode were
retired; bulk assignment acceptance now targets the Barracks formation board.

Read `company-view-nouns.md` before changing this story.

## Open outcomes

### 1. Resolve every participating officer

Implemented in `officer-careers.md`: launch-frozen officer profiles and actual
engaged named seats produce per-officer XP, promotions, injury/wipe fate and
service entries. Results lists every officer. Per-attempt persisted receipts
prevent repeated officer credit; held-back officers receive none. The live
campaign and save/reload pass remains open.

Coordinate the after-action presentation with `c6-after-action-by-fireteam.md`.

### 2. Accept bulk organization on the Barracks formation board

Barracks selects several squads and assigns them to one officer through the
commander card's capacity-checked Assign Selected. The roster rechecks the entire
batch, preserves rank caps and stationed locks, and commits all assignments
together. The standing model is in `company-view-nouns.md` and `personnel-nouns.md`.

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
