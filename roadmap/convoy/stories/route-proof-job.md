# Route Proof Job

Status: IN PROGRESS — both reductions landed and measured; the Conquest matrix
comparison against `main` is the remaining acceptance.

Written: 2026-09-03

Updated: 2026-09-03 — measured on the fixture: worst `REINFORCEMENT` tick
143.29 ms → 22.69 ms, drop cell unchanged at (263,218), commit five ticks later
(2190 → 2195). A steady-state second dispatch in the same replay costs 4.5 ms
and 3.0 ms across its two ticks.

Read `convoy-nouns.md` and `reinforcement-nouns.md` before implementing this
story.

## Problem

`route-proof-budget.md` cut one convoy dispatch on the 560x336 Conquest map
from 3,073 ms of a single game-thread tick to 125 ms, and reported the residue
rather than acting on it. Re-measured on the same fixture at
`2904bd1fe`, the dispatch tick is **143.29 ms** of `REINFORCEMENT` phase, of
which 102.11 ms is 41 cost-field grid searches and the remaining ~41 ms is the
clearance erosion, the component labelling and the road-graph work — all of it
rebuilt from nothing on every dispatch and on every feasibility probe.

A frame that fits on this machine is not a budget met. 143 ms every ~13 s of
play is a visible hitch on weaker hardware, and both halves of it are avoidable
without changing what the dispatch decides.

## Goal

Two independent reductions, each measured on
`tick_profile_spike_2010.fixture.json` replayed forced-serial for 2,500 ticks
(the dispatch lands at tick 2190):

1. **The mask and its labels are held, not rebuilt.** Both are pure derivations
   of the navigation grid, and the grid already counts every change that could
   invalidate them. Erosion and labelling should disappear from the steady-state
   dispatch tick and from every probe.
2. **The proof is spread across ticks.** The searches do not have to happen in
   one tick. A resumable job does a bounded number of searches per sim tick and
   dispatch reads its finished result, so no single `REINFORCEMENT` tick carries
   the whole enumeration.

## Constraints

- `VehicleRoutePlanner` stays pure — a function of its inputs. Resumability is a
  caller-owned object, not planner state.
- `canFulfill` / `arrivalSeconds` stay cheap and unchanged in what they answer.
- The dispatch must reach the same decision: same commit, same drop cell, same
  fall-through where there is no route.
- Nothing may leave a partial delivery actor behind — the
  `ReinforcementDispatchResult` contract is unchanged.

## Acceptance

- No single tick's `REINFORCEMENT` phase above ~25 ms on the fixture.
- Dispatch outcomes identical: 1 COMMITTED, 0 REJECTED, drop cell (263,218).
- Commit latency reported in ticks against the 2190 baseline.
- `ConvoyMeansTest` still passes; a test that drove `dispatch` synchronously
  steps the job to completion rather than weakening its assertion.
- `commanderEvidence -Pmission=conquest -PmaxTicks=6000` compared against the
  same command on a clean `main`.

## Open questions to settle in the work

- Where the job lives, and how it is keyed (request identity survives the
  RETRYABLE re-post).
- When a job is abandoned (request dropped, or served by another means) and when
  it restarts (the world changed under it).
- Whether `RETRYABLE` stopping the whole `soonestFirst` walk is right for a
  convoy whose proof is merely pending. If it changes, pin it with a
  `ReinforcementSystem` unit test and say why in `reinforcement-nouns.md`.

## Out of scope

- Changing the ranking, the budget size, or what a drop is scored on.
- Multi-truck dispatch.
- Any change to the vehicle lifecycle after the route commits.
