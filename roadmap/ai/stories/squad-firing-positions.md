# Shared squad firing-position experiment

Status: EXPERIMENT — opt-in candidate sharing; production default remains individual scoring.

Written: 2026-09-26

Updated: 2026-09-26 — bounded scorer evidence is available; paced tail and behavior controls determine adoption.

## Question

Can a squad reuse bounded firing geometry and reserve distinct member positions
instead of repeatedly scanning the same target envelope? The canonical behavior
and authority boundaries remain in `ai-nouns.md`.

## Experiment boundary

Start with constrained patrol and zone-entry firing searches. Keep target
acquisition, unconstrained ClearZone/vantage probes, weapon authority, and
caller-owned reachability/detour behavior separate. Compatible requests share a
candidate pool; membership does not force a common target or weapon range.

Candidates expire on a staggered simulation-tick TTL and material target/squad
movement. Topology and plan changes retire obsolete state. Each member's choice
is checked against live geometry, reserved exclusively within its squad, and
kept stable while valid. A denied refresh is pending work, not a failed search
or permission to perform the original broad scan inline.

The first allocator is incremental greedy: only members actually requesting a
position participate. It does not speculate on absent members' intentions or
perform a global optimal assignment. Candidate count, contexts per squad, cells
examined per build, and builds per battle tick are bounded.

## Evidence and adoption

- `profileSquadFiringPositions` asks the scorer directly on six mixed-weapon
  infantry, including reuse, target motion, topology repair and sealed routes.
  This establishes selected-cell legality, exclusivity and reachability, not
  movement, shots or battle balance.
- `profileConquestTail` compares the same build with
  `battle.targeting.squadFiringPositions=false/true`; examine total worker CPU,
  firing-search work, cache invalidation causes and tail ticks together.
- Existing firing-line and prosecution scenes exercise movement/fire semantics.
- Keep the switch off until the work reduction and remaining quality tradeoffs
  justify adoption. A microbenchmark improvement alone is insufficient.

## Remaining before adoption

Measure request-order bias and whether a centroid-ranked bounded pool excludes
useful cells for dispersed members. Evaluate behavior over longer Conquest
windows and both traversal axes, including objective progress and casualties.
Consider a constrained-first batch allocator only if incremental assignment
quality, rather than shared geometry cost, proves limiting.

Connected-cell validation also changes a refusal: a pool containing only
disconnected firing cells returns no position, so prosecution can continue its
objective where the historical selected-cell path failure would hold. Evaluate
that distinction in committed combat before adoption. Genuine negative patrol
answers still allow individual alternative-target probes; the refresh budget
does not bound that downstream work or selected-cell validation.
