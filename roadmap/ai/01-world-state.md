# 01 — WorldState + Predicate

**Stage 1, task 1.** Foundation — everything else depends on these.

## Goal

A typed, cheap-to-copy bag of named facts the planner reasons over. Boolean
values only; numeric comparisons get bucketed into named predicates
(`SQUAD_HP_BELOW_HALF`) so the planner stays pure-boolean.

**Data-oriented layout (revised from EnumMap).** Backed by two primitive
`long`s — `presentMask` (which predicates are specified) and `truthMask`
(their values). Makes `WorldState` a value type: ~16 bytes per instance,
trivial copy with no allocation, thread-safe across the parallel replan
pass, branchless `satisfies` (`((a ^ b) & desired.presentMask) == 0`).
Caps us at 64 predicates which is well above Stage 3 projections; if we
ever cross it, swap to a `long[]` pair behind the same API.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/Predicate.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/WorldState.java`

## `Predicate` — initial enum entries

Just what Stage 1 parity needs. We add entries as actions/goals demand them.

- `HAS_TARGET` — squad has at least one alive enemy known.
- `HAS_LOS_TO_TARGET` — assigned member has LOS to the squad's primary target.
- `IN_RANGE_OF_TARGET` — assigned member is within attack range.
- `WITHIN_COHESION_RADIUS` — assigned member within 12 cells of squad centroid.
- `ENEMY_DAMAGED` — the goal predicate `EliminateEnemies` checks for.

## `WorldState` API

Immutable. `with` and `apply` return new instances; no mutator setters.

```
static final WorldState EMPTY
boolean get(Predicate p)                     // unset → false
boolean isSpecified(Predicate p)             // distinguishes "false" from "unconstrained"
WorldState with(Predicate p, boolean v)      // returns derived state
boolean satisfies(WorldState desired)        // every specified predicate in `desired` matches
WorldState apply(WorldState effects)         // returns derived state with effects merged
```

`equals`/`hashCode` over the two longs so planner nodes can dedupe.

## Acceptance

- Compiles.
- `WorldState.satisfies` returns true when current ⊇ desired on the specified
  predicates.
- `with` does not mutate the receiver — value semantics hold.
- Static check throws if `Predicate.values().length > 64` at class load.

## Open questions

None. Bitmask now per the DOD pivot; `long[]` pair if we ever exceed 64
predicates.
