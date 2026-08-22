# G31 — Stationing response deadlines and lapse consequences

**Status:** CODE COMPLETE (2026-08-22)

**Implemented in:** `e25fa582`

## Goal

Make an unanswered Garrison defense or Cadre incident a real loss instead of a
free win. A stationing row is accepted with `contractPhasesTotal = 0`, so
`ContractLifecycleSystem` COMPLETED it at term expiry regardless of a live
payload, and `ContractRetainerSystem` paid through `IN_PROGRESS`. Take the
retainer, watch the market burn, collect tier-scaled MRB credibility.

## What shipped

```
campaign/
  StationingLapseResolution.java   NEW — terminal writeback for a response the
                                   player never answered. Sibling to
                                   StationingNoForceResolution (which covers a
                                   detachment that *could not* fight); both
                                   delegate to the shipped
                                   GarrisonDefenseResolution /
                                   StationingIncidentResolution policies.
  ContractReputation.java          + LAPSED_HOUSE_DELTA (-20), LAPSED_MRB_DELTA
                                   (-10), lapsedForContract(...)
  CampaignState.java               + contractResponseDeadlineTick column, its
                                   addContract reset, legacy backfill, growth
  ContractTableCompactor.java      + deadline column in the row copy
  CampaignStateScript.java         + StationingLapseSystem between
                                   StationingIncidentSystem and
                                   ContractLifecycleSystem

campaign/systems/
  StationingLapseSystem.java       NEW — arms the deadline on first observation
                                   of a pending payload, resolves once the day
                                   passes, disarms when nothing is outstanding.
  ContractLifecycleSystem.java     + hasPendingResponse backstop invariant
```

## Locked rules as shipped

- Garrison defenses get a 7-day response window, Cadre incidents 14, both
  clamped to the assignment's own `contractExpiresTick`.
- The lapse resolves through the shipped resolution policies with
  `victory = false` / `responseSucceeded = false`, zero `marinesLost`, and the
  payload's own fireteam set. The assignment goes FAILED, named fireteams
  release, and the consumed event key is retained as the re-arm watermark.
- The bound captain is restored from `GARRISONED` to `ACTIVE` with a
  commendation-log entry, since no battle ran to move them.
- A resolution that refuses (stale identity, failed personnel delivery) leaves
  the payload and the armed deadline in place and retries on the next tick.
- Lapsing costs -20 employer reputation and -10 MRB — above an honest early
  withdrawal (-15 / -10) and well above losing the fight (-1).
- Retainer and Cadre XP keep accruing while a response is pending. One lever.
- `ContractLifecycleSystem` cannot COMPLETE a stationing row with a live
  payload; it FAILS instead, independent of the lapse system.
- Ordering: defense producers and `StationingIncidentSystem` → lapse →
  lifecycle.

## Deviations from the contracted story

Two locked rules changed during implementation. Both are recorded here rather
than silently absorbed.

### The deadline is persisted, not derived

The story locked "**No new SoA column** — deadlines are derived", *and*
separately locked "no retroactive instant-fail on first load after upgrade".
Those two rules are incompatible. A purely derived
`contractDefenseTriggeredTick + 7` fails a save carrying an event armed thirty
days before the deadline layer existed, on the very first tick after upgrade,
with no chance to answer.

Shipped instead: `contractResponseDeadlineTick`, armed by
`StationingLapseSystem` on first observation as
`min(max(armedDay, observedDay) + window, expiresTick)` and cleared when
nothing is outstanding. In the normal flow the producers and the lapse system
run in the same tick, so `observedDay == armedDay` and the window starts at the
trigger exactly as designed; only a pre-existing payload gets the later anchor.

This also makes G32 cheaper — the popup reads the countdown instead of
recomputing it.

### The lapse carries `FAILED`, not a withdrawal-shaped outcome

`PatronEngagementMemory.record` only accepts an outcome whose
`terminalState(outcome)` matches the contract row, and recording is what arms
the exactly-once guard in `ContractReputation.applyForContract`. A lapse leaves
the row FAILED, so `PatronEngagementOutcome.WITHDREW` would have silently
skipped the memory entry *and* left the reputation mutation unguarded against a
replay. Shipped with `FAILED`; the lapse's severity lives in its deltas, not in
the outcome discriminator.

## Automated verification

- `StationingLapseSystemTest` (8) — window boundary (arm day / day before / day
  of), the longer Cadre window, term-clamped deadline, legacy payload getting a
  full window from first observation, reputation severity and exactly-once
  across repeated ticks, answering before the deadline disarming the window,
  named-detachment release with captain restored, and non-stationing rows
  untouched.
- `ContractLifecycleStationingPendingTest` (4) — pending defense at expiry →
  FAILED, pending incident at expiry → FAILED, quiet term → COMPLETED, and the
  retained event-key watermark not blocking completion.
- `ContractReputationTest` — `lapsedForContract` deltas, failed-counter
  increment, engagement record, and replay no-op.
- `CampaignStateSystemOrderTest` — producers → lapse → lifecycle.
- `CampaignStateStationingColumnsTest` / `ContractTableCompactorTest` — growth,
  legacy backfill, and compaction alignment for the new column.
- `gradlew.bat build` green on 2026-08-22 — 1931 tests, 0 failures.

## Follow-ups

### Resolved — `getClock().getDay()` was not a day counter

Found while validating the deadline arithmetic here, and since **fixed**: the
whole campaign tick layer measured durations from a calendar component that
wraps monthly, so a 7-day window armed on calendar day 27 targeted day 34 and
was never reached. `CampaignClock` now supplies a monotonic counter, anchored so
existing saves keep their numbering, and all 28 call sites read it. See
[`../../framework/complete/monotonic-campaign-clock.md`](../../framework/complete/monotonic-campaign-clock.md).
G31's deadlines needed no change — they were already written against whatever
`day` the tick loop supplies. An in-game confirmation pass is still queued.

### Carried forward from the contracted story

- **Cadre incident failure applies no reputation.** `MissionResolver` applies
  `failedForContract` when `GarrisonDefenseResolution` returns
  `ASSIGNMENT_FAILED`, but the adjacent Cadre incident branch only logs. Still
  true after G31 — the lapse path applies its own reputation, the battle-loss
  path for incidents does not.
- **Stationing retainer economics.** A Tier-2 Garrison with 20 marines pays 660
  credits/month; a six-month Tier-3 Garrison with 40 marines totals ~15,800
  against 75,000 for one Tier-3 Strike. Now that lapsing bites, the risk/reward
  wants a balance pass.
- **Tier-1 stationing is unimplemented** versus `overview.md`'s rank table.
  Either implement it or correct the table.
