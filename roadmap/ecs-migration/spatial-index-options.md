# Spatial index — future shape options

Design notes for the next time we touch
[`UnitSpatialIndex`](../../src/main/java/com/dillon/starsectormarines/battle/UnitSpatialIndex.java)
/ [`UnitDestinationSpatialIndex`](../../src/main/java/com/dillon/starsectormarines/battle/UnitDestinationSpatialIndex.java)
beyond the cellX/cellY rebuild migration that landed in `4edb1f4`.

**Not active work.** Captured here because the user pointed at the
pattern (their other engine, MoonLight, uses it at much larger scale)
and we don't want the design context lost when we eventually have
profiling evidence to justify the surgery.

## Current shape (post-`4edb1f4`)

- `ArrayList<Unit>[] buckets`, one per cell-region (BUCKET=16 cells).
- `rebuild(UnitRegistry)` iterates dense + reads `cellXArray()` /
  `cellYArray()` directly. Per-unit Unit-object deref already
  eliminated from the rebuild hot path.
- `gather(cx, cy, radius, out)` walks the relevant buckets, reads
  `u.getCellX()` / `u.getCellY()` per bucket entry for the radius
  test, appends Unit refs to the output. **Still pointer-chases the
  Unit object per gathered candidate** to get the cell read.

## Why we'd want to change it

Per-gather inner-loop pointer chase: bucket Unit ref → Unit object
header → `denseIdx` field → `registry.cellX[idx]`. Two cache lines
per candidate. At N=200 and ~600 gather calls/sec × ~5-10 candidates
each, it's measurable but not yet a hot loop.

## Reference shape — MoonLight's `LinkedSpatialGrid`

Path: `MoonLightEngine/engine/src/main/java/com/dill/MoonLight/engine/spatial/LinkedSpatialGrid.java`

Intrusive linked list per cell:

- `int[] cellHeads` — `-1` sentinel, otherwise head entityId of the chain.
- `int[] next` — keyed by entityId; `next[id]` is the next entity in
  the same cell.
- `float[] posX, posY` — keyed by entityId; **the grid owns its
  position snapshot**. Set at insert time.
- O(1) `insert` (prepend to chain), O(cells) `clear`.

The sibling `SpatialGrid` (flat pool with `cellStart[]` + `cellCount[]`
slicing into shared `int[] poolIds, float[] poolX, float[] poolZ`) is
the higher-locality variant for semi-static data. Overkill for our
churn rate.

## Port shape for our codebase

Keyed by `denseIdx` from `UnitRegistry` (not entityId — denseIdx is
already a contiguous int starting at 0 the SoA arrays use):

```java
public final class LinkedUnitSpatialIndex {
    private final int bucketsX, bucketsY;
    private final int[] cellHeads;       // -1 sentinel
    private int[] next;                  // chain pointer, keyed by denseIdx
    private int[] snapCellX, snapCellY;  // position snapshot at rebuild time
    private Unit[] units;                // ref to Unit at this denseIdx
    private int capacity;

    public void rebuild(UnitRegistry registry) {
        Arrays.fill(cellHeads, -1);
        int n = registry.liveCount();
        ensureCapacity(n);
        int[] rcx = registry.cellXArray();
        int[] rcy = registry.cellYArray();
        Unit[] dense = registry.denseArray();
        for (int i = 0; i < n; i++) {
            int bx = rcx[i] / BUCKET, by = rcy[i] / BUCKET;
            if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) continue;
            int c = by * bucketsX + bx;
            next[i] = cellHeads[c];
            cellHeads[c] = i;
            snapCellX[i] = rcx[i];
            snapCellY[i] = rcy[i];
            units[i] = dense[i];
        }
    }

    public void gather(int cx, int cy, float radius, ArrayList<Unit> out) {
        out.clear();
        // ... bucket bounds ...
        for (int by = y0; by <= y1; by++) {
            for (int bx = x0; bx <= x1; bx++) {
                int cur = cellHeads[by * bucketsX + bx];
                while (cur != -1) {
                    int dx = snapCellX[cur] - cx;
                    int dy = snapCellY[cur] - cy;
                    if (dx*dx + dy*dy <= r2) out.add(units[cur]);
                    cur = next[cur];
                }
            }
        }
    }
}
```

## Wins vs. current shape

- **No `ArrayList` per cell.** Just `int[] cellHeads` + intrusive
  `next[]` array. Zero GC pressure beyond initial alloc.
- **Gather inner loop reads `snapCellX[cur]` / `snapCellY[cur]`
  sequentially.** No pointer chase to Unit for the radius test.
  Unit ref is only read for HITS (via `units[cur]`).
- **Cleaner staleness contract.** The grid owns its position snapshot.
  Gather queries against captured positions, not live registry data,
  so no risk of "denseIdx invalidated by intervening release" — the
  index `cur` is read from data captured at rebuild and consistent
  with the `snapCellX/Y[cur]` reads.

## Why we DON'T need MoonLight's double-buffer wrapper

Their engine uses double-buffering (front grid read, back grid being
built, swap at frame boundary) because writers and readers can run
concurrently in their tick loop. **Our rebuild is fully serial before
any gather**, so a single buffer with internal position snapshot is
sufficient. The "denseIdx is stable within the rebuild→gather window"
property holds because:

- Rebuild fills `cellHeads / next / snap*` in one serial sweep.
- All gathers in the same tick read against that snapshot.
- Releases happen later in the tick (damage drain, demolition
  systems) but the spatial index already captured what it needs.
- Next tick's rebuild rebuilds against the post-release state.

If we ever parallelize the rebuild itself, revisit and add the
double-buffer wrapper. Until then the snapshot's the contract.

## Tradeoffs

- **Memory:** `snapCellX/Y` and `units` arrays sized to liveCount
  duplicate data already in registry's SoA + dense arrays. ~3KB at
  N=200. Cheap.
- **Linked-list traversal:** gather's chain walk pointer-chases
  through `next[]` — not strictly sequential. But the `next[]` array
  is small (few KB), so it lives in L1 once warm. MoonLight's design
  assumes this at 50K-entity scale and it works.
- **`add()` / `addDestination()` / `removeDestination()`:** today's
  ArrayList shape supports mid-tick incremental updates trivially.
  Linked-list version handles `add` the same way (prepend); `remove`
  requires walking the chain to find the node (O(chain length),
  small per cell).

## When to actually do this

Triggers worth waiting for:
- `gather()` shows up in a JFR profile as a hot spot.
- N grows past ~500 (we're at ~200 peak today).
- A new query type lands that wants the cleaner contract (e.g., a
  region-iteration system that wants to scan all units in a
  rectangular region with no allocation).
- We start to want a Morton sort of the SoA arrays (the spatial
  index would be a natural place to anchor the sort cadence —
  rebuild already touches every unit).

Scope when we do it: ~250-400 lines net. Rewrite both index files,
audit callers of the incremental `add` / `addDestination` /
`removeDestination` for the new int-keyed API. Keep gather output
as `ArrayList<Unit>` so call sites don't change.

## Morton sort follow-up

Once buckets are denseIdx-keyed and the staleness contract is locked,
the Morton-sort idea becomes natural: at swap time (or rebuild
cadence), sort the registry's SoA by Morton-key on (cellX, cellY) so
spatially close units land in adjacent denseIdx slots. Spatially
adjacent units in the same bucket would share cache lines.

Same triggers: meaningful at higher N. Not worth the bookkeeping at
N=200. Same memory.md entry will track it
([`battle_services_systems`](../../memory)) when the time comes.

## References

- MoonLight engine spatial sources:
  - `engine/src/main/java/com/dill/MoonLight/engine/spatial/LinkedSpatialGrid.java`
  - `engine/src/main/java/com/dill/MoonLight/engine/spatial/SpatialGrid.java`
  - `engine/src/main/java/com/dill/MoonLight/engine/spatial/AGENT.md`

User context: they shipped this pattern at 50K-entity scale with
sort-via-compute-shader on the Morton key for collision broadphase.
Confirmed proven; we just don't need the scale yet.
