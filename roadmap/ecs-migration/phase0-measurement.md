# Phase 0 — the measurement gate (RESULT)

> **Status: DONE (2026-06-29).** This is the artifact the
> [`systems-to-columns`](stories/systems-to-columns.md) epic is gated on — the
> answer to "nobody knows which world we're in. Find out first." The harness is
> [`EcsAccessBenchTest`](../../src/test/java/com/dillon/starsectormarines/battle/component/EcsAccessBenchTest.java);
> the verdict is below.

## What was measured

A deterministic micro-bench isolating the **access mechanism** — the only thing the
systems-half conversion changes — over the **real** live combatant archetype
`{IDENTITY, POSITION, HEALTH, COMBAT, MOVEMENT, AI_STATE}` using the real
`BattleComponents` field layout. Two kernels run the *same arithmetic* (the Phase-1
candidates: the `AI_STATE` cadence countdowns, the `MOVEMENT` `moveProgress` advance,
the `COMBAT` cooldown/burst timers — ~15 field ops per unit):

- **by-id** — `world.getFloat(id, ct, field)` / `setFloat` per field, as
  `UnitUpdateSystem` and the mainline systems do today. Each call = one
  `Long2LongOpenHashMap` location probe + `tables.get` + column-object deref.
- **column-walk** — `world.matched(query)`, raw column arrays grabbed once per
  matched table, dense `for (row)` loop (the `DroneCrashSystem` idiom). Zero probes.

`byIdAndColumnWalkAgree()` asserts the two paths produce **bit-identical** results
(always-on regression guard); `benchmark()` is the timing run (gated behind
`-Decs.bench=true`). Method: 30k-iter shared warmup, then min over 7 trials of
20k-rep batches (min = least-noisy estimator). Two populations: **fresh** (ids
1..N, rows contiguous) and **churned** (over-allocate 4N, destroy a random 3N so
survivor ids scatter across the hash buckets and rows are shuffled by swap-pop —
the honest steady state). JDK 25, 32-core dev box.

## Numbers

```
| N   | population | by-id ns/tick | column ns/tick | speedup | by-id ns/unit | column ns/unit | delta µs/tick |
|-----|------------|---------------|----------------|---------|---------------|----------------|---------------|
| 50  | fresh      | 1947          | 100            | 19.47x  | 38.9          | 2.0            | 1.85          |
| 50  | churned    | 1913          | 99             | 19.32x  | 38.3          | 2.0            | 1.81          |
| 200 | fresh      | 7644          | 384            | 19.91x  | 38.2          | 1.9            | 7.26          |
| 200 | churned    | 7496          | 380            | 19.73x  | 37.5          | 1.9            | 7.12          |
| 800 | fresh      | 31225         | 1507           | 20.72x  | 39.0          | 1.9            | 29.72         |
| 800 | churned    | 30783         | 1510           | 20.39x  | 38.5          | 1.9            | 29.27         |
```

## Verdict — two facts, both true

1. **The migration's premise is confirmed in *relative* terms.** Column-walk is a
   consistent **~20× faster on the access mechanism** (≈2.5 ns per by-id op — the
   fastutil probe + indirection — vs ≈0.15 ns per array-indexed op, essentially
   free / vectorizable). The by-id path is emphatically **not** "within noise of a
   column walk," so the gate's literal stop-and-re-scope trigger did **not** fire.
   The ratio holds linearly N=50→800, and **churn barely moved it** (7.6 vs 7.5 µs
   at N=200) — the hashmap probe dominates regardless of id scatter, and the N=200
   working set stays in cache.

2. **But the *absolute* cost is a rounding error at the design N.** Converting the
   entire Phase-1 arithmetic slice for all 200 units saves **~7.3 µs per tick** —
   **0.022% of a 30 Hz frame** (33,333 µs). Even at N=800 it is ~30 µs ≈ 0.09%.
   The real `UPDATE_UNITS` tick is dominated by pathfinding, target-picking, GOAP,
   and LoS — none of which this conversion touches — and it already runs **parallel**
   across a ForkJoinPool, which hides most of this 7 µs anyway.

**So the world we're in: the cache-locality win is real but worth single-digit
microseconds per tick at N=200 — it is a *clean-architecture* win, not a *perf*
necessity.** The storage migration stands on its own merits (composition,
corpse-cell-for-free, optional-capability presence); the systems conversion is, on
these numbers, **optional polish** as a perf play.

## Scope / honesty caveats

- Isolates **only** the access mechanism. It excludes role dispatch, the
  megamorphic `behaviorFor(role).update()` virtual call, and the branchy behavior
  bodies — but SoA column conversion doesn't fix those either, so they're out of
  scope by design.
- Single homogeneous table is a mild **best case for column-walk**; a real world
  has ~8–12 archetypes so the query walks a few smaller tables. That adds a handful
  of table iterations — still negligible.
- Single-threaded measurement; the real loop is already parallel, which further
  shrinks the realizable wall-clock saving.
- `7.6 µs` is the cost of the **convertible arithmetic slice** specifically. Most
  other per-tick by-id reads in the codebase are already dense-iterator
  `registry.col(i)` zero-probe reads (see the step-4 / slice-2d notes in
  `next-session.md`), so this is representative of what Phase-1 would actually move.

## Recommendation (pending user direction)

- **Phase 1 (convert the universal arithmetic sub-passes) — worth doing as
  *idiom/architecture completion*, not as perf.** It is low-risk pure arithmetic,
  it is the natural ECS shape the engine was built for (today the `Query`/column
  path has near-zero game adopters), and it would be `CommandBuffer`/`Query`'s first
  real combatant-loop consumer. Justify it on "build it right" + "default to ECS
  shape," and report the (tiny) before/after honestly — **do not** sell it as a
  frame-time win.
- **Phase 3 (the deep behavior-body conversion) — do NOT pursue as a perf play.**
  The high-churn, high-risk push of rich per-role behaviors onto column reads is
  **not** justified by these numbers and should stay parked unless N grows by an
  order of magnitude or a real in-situ `UPDATE_UNITS` profile shows the access (not
  the pathfind/LoS/GOAP work) is the bottleneck.
- **If the priority is shipped gameplay**, the honest read is that the ECS track has
  reached the point of diminishing returns and the gameplay backlog (loot picker,
  non-STRIKE contracts, compound-capture v2) is the higher-leverage place to spend.

## How to re-run

```
gradlew :test --tests "*EcsAccessBenchTest" -Decs.bench=true
```

(PowerShell: quote the flag — `"-Decs.bench=true"` — or it gets mangled. Scope to
`:test`, not `test`, so the `:asset-pipeline` subproject's test task isn't asked to
match the filter.)
