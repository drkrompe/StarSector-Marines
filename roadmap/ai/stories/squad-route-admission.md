# Squad route admission

Status: IN PROGRESS

Written: 2026-09-27

Updated: 2026-09-27 — implementing exact pending admission and fairness; paired late-battle evidence remains.

## Problem

Squad fields have a per-tick build allowance, but members whose fields were
not admitted synchronously fall back to independent A*. The allowance therefore
bounds shared preparation, not the routing work the tick performs.

A production-paced construction-fixture replay on `9aff622ea`, with fixed-goal
occupancy omission disabled, recorded four field builds taking 3.31 ms alongside
194 fallback searches taking 4.49 seconds of overlapping worker time in a
366 ms tick. Detailed slow-search samples identify missing fields, destination
occupancy 255, and about 150,000 expansions per sampled search. Omitting the
unavoidable terminal occupancy toll reduces individual search work; it does not
enforce admission. These are distinct mechanisms.

A follow-up capture on `3f5e1e63a` with that omission enabled recorded a
70.29 ms unit-update phase, 5.87 million flat-search expansions, and 965.85 ms
of overlapping pathfinder worker time. Retained slow searches identify
`EnterZone` / `MISSING_FIELD`, with about 28,000 nodes per member, toward the
same assigned room. Worker CPU (969.83 ms summed) nearly equals callback wall
time (974.08 ms summed), and the tick records no GC. This is redundant useful
search work, not evidence of threads idling on a join. A cheaper individual
search does not eliminate the need for explicit pending admission.

`ai-nouns.md` owns squad intent and execution authority. Waiting for computation
must not become a tactical failure or an unreachable verdict.

## Scope

- Distinguish an exact request deferred by preparation from absent provider
  input, failed preparation, and an uncovered start in a usable field.
- Publish deferred intent in the immutable pre-dispatch batch. A matching
  squad, epoch, step, and goal may return an explicit pending result without
  member A*.
- Pending authors no path, does not clear or stamp the existing path, does not
  complete or fail the action, and preserves existing firing/contact work.
  Initially it also performs no travel that tick: an old path is not known to
  belong to the current intent merely because it still exists.
- Admit oldest waiting requests within the existing new/uncovered priority
  group. Repeated low-ID churn must not starve a persistent request.
- Keep ordinary fallback for failed, uncovered, mismatched, and intentionally
  unprepared queries. Do not silently reinterpret every missing field as a
  budget delay.
- Report pending requests/calls, oldest wait age, admissions, and fallback work.

Do not move this work into the per-member defended-site async service. That
service has a different cost and formation contract and would duplicate work
the squad field is intended to share. Do not increase the build allowance to
hide the missing scheduling state.

## Acceptance

Small unit fixtures prove that budget-plus-two intents admit only the allowed
number, deferred members execute zero A*, subsequent preparation serves them,
and stale epoch/token/goal results cannot be consumed. Pending does not mutate
path/repath state or report failure. Intentionally unprepared callers preserve
their old behavior. A persistent high-ID request eventually wins despite
low-ID request churn.

An opt-in same-build control and subject replay measure late-battle tick tail,
total worker routing time, actual waits, and remaining misses. Async battle
outcomes may differ; report populations and action mix rather than claiming
identical tactical histories. The experiment must not trade a spike for squads
that never resume travel.

## Separate follow-up

Coverage repair is not admission. A fresh field can omit a member displaced
beyond its settled region. Rebuilding for every uncovered start can repeatedly
charge for unreachable members. Any later repair must remember attempted starts,
preserve usable field portions while deferred, and retain epoch-pinned costing.
A bounded legal neighbor join is another option, with explicit route-quality
tradeoffs. Select that work from fallback evidence rather than bundling it here.
