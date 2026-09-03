# Shared-goal fields: rebuild on what changed, not on a clock

Status: DRAFT

Written: 2026-09-03

## Why

`SharedGoalPathfinder` grows one reverse Dijkstra field per (goal, step
family, cost revision) so many movers sharing a goal extract paths instead of
each running A*. The field bakes the occupancy snapshot it was grown under
into its step costs, so it cannot outlive that snapshot for long:
`DEFAULT_MAX_BUILD_AGE_SNAPSHOTS` expires a field after fifteen snapshots and
the next request regrows it from scratch, a whole-map Dijkstra over 188,160
cells.

Measured on the full-strength Conquest played on 2026-09-03: every auto-spike
dump early in the battle (ticks 334 to 653, one every ~22 ticks) carries
exactly one `SHARED_PATH_FIELD_BUILD` at 17 to 21 ms on a tick whose baseline
is 5 ms. That cadence is the expiry. A serial JFR replay of the same launch
fixture put `ReverseField.rebuild` and its heap sifts at 55% of all
simulation samples across the first ten minutes of the battle.

## Scope

- Take occupancy out of what a field is grown under, so a field's validity
  is the navigation grid's topology revision plus the cost field's revision,
  the same key the clearance and mesh derivations already use. Crowds are a
  local concern for extraction and steering, not for the global tree.
- If occupancy must stay in the tree, replace the fixed-age expiry with a
  reason: rebuild when the occupancy along the field's extracted corridors
  has actually changed, or bound the Dijkstra to the region requests come
  from.
- Keep the field byte-identical across replicas; `simDeterminism` stays
  green.

## Acceptance

- The spike dumps stop showing a 20 ms `SHARED_PATH_FIELD_BUILD` every
  fifteen ticks; builds happen on topology or cost changes only.
- `commanderEvidence -Pmission=conquest` against its own control: captures
  and held compounds within the run-to-run band on both fixtures, since
  routing through crowds is what changes.
- Serial JFR replay of the same fixture: `ReverseField.rebuild` under 10% of
  simulation samples.
