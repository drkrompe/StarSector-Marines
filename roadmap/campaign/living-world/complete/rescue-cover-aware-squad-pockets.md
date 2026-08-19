# Cover-aware rescue squad pockets — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `ba42612b`

## Outcome

- Pickup guards and post-relief moving escorts now distribute members through
  a complete 5x5 tactical pocket around their commander-authored squad anchor,
  instead of filling the same fixed 3x3 ring.
- Every member still receives a distinct relative cell. The formation therefore
  moves immediately with a mobile escort screen and cannot roam away from an LZ
  star point in search of distant cover.
- Candidate cells prioritize local wall and doodad cover, then separation, then
  a small deterministic variation keyed to the squad. Different squads avoid a
  copy-pasted silhouette without rerolling destinations every tick.
- The initial shelter-relief phase retains its strict circular two-cell bound so
  wider corner slots cannot prevent marines from entering the evacuation trigger.
- A stable full offset-slot set is rewritten on every replan, allowing cover
  choices to update around a new anchor without leaving stale member roles.

## Verification

- Regression coverage proves both eight-person pickup and moving squads occupy
  distinct destinations outside the old 3x3 ring while remaining inside 5x5.
- A heavy doodad fixture proves the pocket spends its local leeway on cover.
- Existing moving-anchor coverage proves relative member slots translate with
  the escort screen instead of producing movement churn.
- The full Gradle suite passes: 1,827 tests, zero failures.

## Manual follow-up

Play a rescue on cluttered urban terrain and confirm squads visibly fan into
nearby props/walls without looking over-dispersed or repeatedly changing cells.
The cover/spacing/variation weights are the intended tuning seam.
