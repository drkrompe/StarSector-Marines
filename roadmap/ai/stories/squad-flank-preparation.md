# Phase-owned squad flank preparation

Status: DRAFT

Written: 2026-09-26

## Question

How should a squad publish one coherent flank destination to parallel member
execution? `ai-nouns.md` owns squad intent and action authority.

`AttackMove.maneuverAim` uses the mutable `FlankAimMemo` from member callbacks.
Its freshness check, route proof, and multi-field publication are not atomic;
the memo's original single-thread assumption no longer matches unit dispatch.
Several members can repeat the same work or observe inconsistent publication.
Incumbent-score pruning reduces each calculation's candidate proofs but does
not repair this ownership boundary.

## Direction

- Prepare one squad-scoped answer from frozen contact, role, origin and terrain
  inputs before member dispatch. Members consume the answer without writing it.
- Avoid a global or per-squad mutex around path search. Define ownership and
  publication first; do not serialize the old worker-side fan-out.
- Preserve destination authority and the existing structurally-unreachable
  refusal. A stale or missing answer must not invent a new member-specific flank.
- Keep temporal reuse separate from concurrency repair. A longer lifetime needs
  explicit contact/movement/terrain invalidation and fresh behavior evidence.

## Acceptance

Prove with small tests that parallel readers cannot duplicate preparation or
observe mixed fields, and that assignment/contact changes invalidate the answer.
Use opt-in scenes for fixing/maneuver cooperation, doorway refusal and continued
objective progress. Retain preparation/search counts and late Conquest timing;
do not substitute deterministic battle outcomes for direct ownership tests.
