# Slice G7 — civilian-rescue outcome closure

**Status:** IN PROGRESS (2026-08-12)

**Implemented:** `38bc6323`, `a27064fc`, `cf442e11`

## Goal

Close the player-facing rescue loop after the measurable swarm battle without
changing its zero-economy, explicit-report, or hidden-moral contracts.

## Shipped checkpoint

- The debug client adds LOW/MEDIUM/HIGH `SWARM RESCUE` entries alongside its
  ordinary type/risk grid.
- Debug entries select the production rescue factory, carry no campaign event
  id, pay no credits, add no air roster, and cannot resolve a real event row.
- A controlled fixture installs a real cohort plus risk-scaled swarm roster and
  verifies sealed zero, partial, and full rescue results independent of battle
  victory.
- Mission outcomes preserve representative initial/evacuated facts alongside
  the existing campaign-scaled result.
- Results shows representative totals and, for real campaign events, the scaled
  civilians rescued out of frozen stakes.
- Focused suites and `gradlew.bat build --no-daemon --max-workers=1` pass.
  Manual playtesting remains skipped for this session.

## Remaining

1. Persist a source-unique terminal resolution dispatch only after the matching
   committed event accepts its explicit report.
2. Render that dispatch through an existing player-facing campaign surface.
3. Keep moral-axis numbers hidden and add no payout or salvage consequence.
