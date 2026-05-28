# Next session — battle-tier feature-vertical reorg

Read [`overview.md`](overview.md) first — concept, target tree, full
file-by-file mapping, the GOAP partition rule, and the 10-slice plan.

## State of play

- **Design only — nothing moved yet.** This is a documented, locked
  direction awaiting execution.
- **Blocked on sequencing:** execute *after*
  [`drop-sim-facade-delegators`](../ecs-migration/stories/drop-sim-facade-delegators.md)
  lands. Both are large mechanical churns over the same shared tree;
  stacking them multiplies merge conflicts, and the facade-drop changes
  consumer→service wiring that should settle before services relocate.

## When picking up

1. Confirm the facade-drop has landed (or is far enough along to not
   collide).
2. Spin out the slices in `overview.md` § Slice plan as individual story
   files under `stories/`, one per slice, in order.
3. Start with slice 1 (`combat/` consolidation) — it removes the worst
   conflation (`fx/` holding `Projectile`/`ShotEvent`/`PendingDetonation`)
   and is fully self-contained.
4. Each slice: mechanical move + import rewrite only → `compileJava`
   clean → tests green → commit. Fan the mechanical sweep to a Sonnet
   subagent; keep the per-file GOAP partition judgment (slice 6) on the
   main thread.

## Decisions already locked (don't relitigate)

- Feature-vertical, with a thin framework core (engine vs game framing).
- Three boundary calls: shared GOAP vocab → framework built-ins; single
  `EffectsService` sink in `combat/`; `HeavyWeapons` mechanism → `combat/`.
- `entity/` rename (slice 8) is optional — decide at slice time.
