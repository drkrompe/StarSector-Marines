# Progressive convoy route fields

Status: READY — entrance probes are local; route proof still prepares whole-map fields.

Written: 2026-09-23

Read `convoy-nouns.md` and `reinforcement-nouns.md` before implementing this story.

## Goal

Derive APC clearance and terrain cost only where an inbound or outbound route
search actually goes, rather than preparing all 188,160 Conquest cells before
the first search. Bound work per tick without silently declaring unexplored
ground impassable.

## Current measured boundary

The production-shaped tail capture now records the route proof's actual
clearance, component, and terrain-cost build buckets when a convoy is chosen.
Because a timing-dependent battle may choose another means, the opt-in
`battle.tail.convoyUncachedStages` pass also builds those three fields in
isolation after measured ticks on the same grid. On the 560×336 fixture that
uncached construction took 9.0 ms for APC clearance, 28.8 ms for connectivity
labels, and 16.7 ms for terrain costs on this host. These are diagnostic
machine-local samples, not timing gates or a claim that all three appeared in
one live dispatch.

## Work

- Give the convoy route search an indexed passability/cost view that evaluates
  and memoizes cells on demand. Preserve a frozen navigation/topology view for
  an in-flight proof and any later vehicle recovery that retains the route;
  do not let an unqueried cell read a newer live grid revision.
- Route A* expansion, endpoint snapping, and string-pulling through that view.
  Failed-turn exclusions must be a separate overlay, not a clone that forces
  materialization of the entire clearance mask. Keep existing eager APIs for
  other vehicle callers until they migrate deliberately.
- Replace the global vehicle-component build in this path. An unknown region
  is not a disconnected region: either prove connectivity incrementally or
  defer that rejection to a bounded search. A fallback may expand the explored
  area up to the whole map when necessary, but a valid detour outside the
  initial A-to-B area must remain discoverable.
- Account for work in cells or node expansions per tick as well as searches.
  The existing four-search allowance does not bound one failed A* flood.
- Include terrain costs in the lazy view. Lazifying clearance alone leaves the
  measured terrain-cost and component scans on the dispatch tick.

## Acceptance

- A representative Conquest convoy proof visits and evaluates far fewer cells
  than the full map when its route stays local, and no full-field build occurs
  on its initial dispatch tick. Report both evaluated-cell counts and wall-time
  stages; compare on the same fixture without a portable timing threshold.
- Match eager proof feasibility and route validity on canonical rear-entry
  maps, narrow/blocked gates, disconnected vehicle regions, wide detours,
  inbound/outbound pairing, and failed-turn retry cases. Different valid route
  choices are acceptable; a false rejection caused only by unexplored ground
  is not.
- A topology change invalidates a running proof. A committed route's retained
  clearance view remains a snapshot and does not observe later grid writes.
- Ordinary infantry pathfinding and other vehicle routing APIs retain their
  existing behavior unless moved under their own measured change.

## Model update on shipping

`convoy-nouns.md` currently requires global clearance connectivity before
search. Replace that law with the tested progressive-reachability rule in the
same commit that ships this path; do not quietly skip the prefilter and leave
the old contract stated as fact.
