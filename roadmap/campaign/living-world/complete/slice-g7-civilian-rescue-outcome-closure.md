# Slice G7 — civilian-rescue outcome closure

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `38bc6323`, `a27064fc`, `cf442e11`, `9e0417aa`

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

## Terminal dispatch checkpoint

- The persisted event row remains the source of truth; no parallel political
  Chronicle schema is introduced for this non-political event.
- When no live call exists, Distress Net retains the newest resolved rescue and
  renders its market, exact rescued/at-risk totals, and resolution day.
- A newer pending or committed call always takes presentation priority.
- The dispatch exposes no moral-axis numbers and adds no payout or salvage.
