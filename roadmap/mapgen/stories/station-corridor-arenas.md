# Station corridor arenas

Status: PROPOSED

Written: 2026-08-23

Read `mapgen-nouns.md` before implementing this story.

Corridors now establish a connected station graph. Give only selected junctions,
gates, and route transitions a wider, intentional arena treatment, with a
declared room purpose and a clear tactical job. Ordinary transit must remain
legible and must not accumulate arbitrary cover.

## Acceptance

- The recipe distinguishes transit from authored tactical arenas without
  coordinate conventions.
- Arena geometry preserves graph reachability, spawn deployment, and doors.
- A deterministic seed sweep shows that any added cover or hardpoint has a
  stated purpose and cannot accidentally seal a route.

## Out of scope

Battle-time multi-port insertion and unrelated door mechanics.
