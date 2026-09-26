# Shared Mech terrain clearance

Status: IN PROGRESS — resumable route proof and queue verified; shared runtime adoption remains.

Written: 2026-09-26

Read `direct-control-nouns.md`, `continuous-positions-nouns.md`, and
`mechs-nouns.md` first. This is a prerequisite for `controlled-mech.md`.

## Goal

AI and player movement obey the same actual chassis envelope. A Bulwark cannot
fit a one-cell passage, but can use the centerline of a two-cell passage.
Neither shrinking its radius nor rejecting every cell-center lane is correct.

## Plan

1. Prove continuous routes over a half-cell lattice. Validate each point and
   every connecting straight segment with the shared circular terrain sweep.
   Attach the exact start without snapping. Retain the requested destination
   separately from the resolved legal endpoint, with an explicit maximum
   endpoint displacement. Distinguish exhausted search budgets from proven
   unreachability; neither grants a point-route fallback.
2. Give Mech movement one continuous route authority. Migrate destination,
   arrival, hip bearing, lookahead, tactical reachability, and route writers
   together. Cell projections may serve occupancy and display but cannot
   decide body clearance or continuous arrival.
3. Sweep translation and separation with the actual variant radius. Recheck
   current topology before motion; a route's old proof does not authorize a
   step through a newly closed obstacle. Resolve legal deployment positions
   before spawning, without teleporting existing bodies out of bad placements.
4. Measure long routes and repeated tactical requests before enabling this
   path in ordinary battles. Keep bounded search cost visible and preserve
   infantry routing, targeting knowledge, mission bounds, and Mech pivot law.

## Remaining implementation

The pure route proof, resumable frontier, and shared work queue are available.
Before adopting them in battle, select and verify the execution policy: small
serial slices bound work but can delay long routes, while workers need frozen
terrain inputs and stale-result rejection. An expansion cap is not a frame-time
bound. `profileClearanceRoute` records whole-query cost, per-slice cost, and
the cheap local-route control case. Runtime consumers, placement, separation,
and objective-aware arrival still need the shared authority described above.

## Acceptance

- Actual Bulwark, Hound, and Sirocco radii agree between planning and motion.
- One-cell and two-cell passages, a two-cell L turn, closed reciprocal edges,
  map limits, changed walls, and sub-cell starts all have focused evidence.
- Following a route and applying crowd separation never cuts a blocked corner.
- A resolved endpoint cannot falsely satisfy an objective on the other side
  of an impassable barrier.
- Mech tactical orders, ordinary doctrine, survival, and lance handback use the
  same reachability authority; there is no silent fallback to point clearance.
- Production-size route measurements expose expansion and clearance work;
  gameplay probes do not launch unbounded repeated whole-map searches.

The half-cell graph is a deliberate discrete approximation. A failed search
does not prove that every mathematically possible continuous route is absent.
