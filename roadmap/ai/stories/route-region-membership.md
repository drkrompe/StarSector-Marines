# Region-backed squad route preparation

Status: IN PROGRESS

Written: 2026-09-27

## Scope

Preserve the selected shared-route corridor while using the mesh's existing
cell-to-region index instead of expanding selected rectangles into another
per-cell membership mask. The bounded scheduler should spend its allowance on
necessary work, without changing path coverage or increasing its limits.

Add opt-in host CPU attribution around actual squad replanning and goal
selection. Wall time attributed to a trivial predicate must not be mistaken
for evidence of expensive algorithmic work.

Split squad-alert profiling into belief maintenance, awareness, noise,
incoming-fire and publication stages with aggregate work counts. The previous
capture attributes 8.35 seconds to that phase but cannot say which work owns it.

## Acceptance

- Identical coverage and extracted paths against the existing corridor builder
  on weighted, obstructed and open geometry; tiny slices remain bounded.
- Duplicate region selection counts corridor area once; scratch reuse and
  topology cancellation remain safe.
- A short shared route does not enumerate distant regions or selected-region
  area merely to reconstruct membership.
- Unavailable CPU readings remain distinct from zero. No per-goal CPU probes.
- Focused tests and a production-paced tail capture report route progress,
  wait ages, work limits and wall/CPU attribution without claiming replay
  identity or treating wall-clock changes alone as causal evidence.

The standing model remains in `ai-nouns.md`; no mission authority, route
priority, coverage policy, or budget value changes.
