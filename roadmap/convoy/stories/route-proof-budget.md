# Convoy route proof: reachability first, and a bounded search

Status: IN PROGRESS

Written: 2026-09-03

Updated: 2026-09-03 — measured; count caps on drops and exits dropped in favour
of the search budget alone, after the canonical rear-entry map proved its route
at about the seventh ranked drop.

## What is wrong

Playing the 560x336 Conquest map, the game thread stalls for 3.5–5.7 seconds
roughly every four hundred ticks. Nine watchdog thread dumps all name the same
stack: the reinforcement tick, `ConvoyMeans.dispatch`, `ConvoyMeans.routePlan`,
`VehicleRoutePlanner.routeDrivable`, `GridPathfinder.findPath`. The spike tick
profiles agree with the dumps: the `PATHFIND` bucket records 720, 1,115 and
1,089 calls inside a single tick at about 4.8–5.1 ms each — 3.44 s, 5.63 s and
5.33 s of one tick, with everything else in that tick totalling about 20 ms.
One convoy dispatch is costing on the order of a thousand full-grid A* floods.

An earlier session in the same log shows the same dispatches *failing* at the
same price ("no complete HEAVY_APC route from defender rear ..."), so the cost
is not the price of success.

The standing figure in `reinforcement-nouns.md` — that the route proof "costs
about seventy milliseconds" — was measured on a much smaller map and is off by
fifty to eighty times here.

## Why it explodes

`ConvoyMeans.routePlan` is a nested enumeration: every eligible perimeter entry
crossed with every interior junction reachable from it, crossed with every exit.
Each pair calls `VehicleRoutePlanner.routeDrivable`, which is itself up to eight
full A* searches when the turn refinement rejects a bend.

The reachability test in that enumeration is the **road graph**, which says
nothing about whether a HEAVY_APC body fits. The router is gated on the
`VehicleClearance` mask instead. So a destination that is graph-reachable and
mask-unreachable makes A* flood the entire reachable component of a
188,160-cell grid — about 5 ms — and then fail, once per candidate, for as many
candidates as the graph offers.

## What ships

1. **Reachability on the clearance mask, once per dispatch.**
   `ClearanceComponents` labels the mask's connected components under the
   pathfinder's own step rule, in one pass. A destination or exit cell in a
   different component than the entry is skipped without calling the router at
   all. Pure function of the grid and the mask; unit-tested on a hand-built
   mask.
2. **A bounded enumeration.** `routePlan` carries a per-dispatch budget counted
   in grid searches, and the kinematic retries inside `routeMaskedDrivable` are
   charged against it rather than taking a flat eight per endpoint pair. When
   the budget runs out the dispatch is `REJECTED` — which is the "bugged map"
   diagnostic the reinforcement system already prints. Only the perimeter
   entries are additionally capped by count, because what an entry costs before
   any search is a road-graph flood rather than a search.
3. **`TickProfile.Phase.REINFORCEMENT`**, so the next time this happens the
   profile names it instead of hiding it inside `AIR_SYSTEM`.

## Acceptance

Replay `tick_profile_spike_2010.fixture.json` headlessly before and after, and
compare the worst reinforcement tick's wall clock and `PATHFIND` count, plus
the dispatch outcome of every request. A route the old code proved inside the
budget must still be found; a `COMMITTED` that becomes `REJECTED` is acceptable
only where the destination is genuinely unreachable for the chassis.

Then `:test` and one `commanderEvidence -Pmission=conquest` run, compared to
main for captures and compounds held.

## Out of scope

Spreading the proof across several ticks. Only if the two changes above leave a
dispatch above about 100 ms on the fixture is that worth opening.
