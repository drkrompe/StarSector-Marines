# Station topology roles — depth / articulation / bridge / on-loop — ✅ SHIPPED

Commit `6a07e8f`. The follow-on to [station interiors slice 1](station-interiors-slice-1.md).
Slice 1 *published* the room/corridor `StationGraph`; this derives the
**topological roles** placement passes will *query* — the actual payoff of the
"generator publishes structure, passes consume it" seam the corridors-first-class
story centers on.

## What landed

### `StationTopologyStage` (`battle.world.gen.bsp.stage`)

Runs after `StationSpawnStage` in the station recipe (depth is measured from the
attacker spawn, so spawns must exist first). **Pure analysis** — no `rng`, no
grid mutation → maps stay byte-identical. Computes, in one pass each:

| Role | How | Meaning |
| --- | --- | --- |
| `depthFromEntry(room)` | BFS hops from the marine-spawn room | indoor assault gradient: 0 at the breach, rising to the defender's deep end |
| `isArticulation(room)` | Tarjan low-link DFS | must-pass room — its removal splits the station |
| `isBridge(corridor)` | same Tarjan DFS | sole link to a subtree — on-spine, no alternate |
| `isOnLoop(room)` | room has any non-bridge corridor | on a flank/cycle vs purely on the spine |

`degree(room)` was already free on the graph (1 = dead-end, 2 = pass-through,
≥3 = hub).

### Storage / API

`StationGraph` gains the role arrays + accessors, installed once by the stage via
`applyRoles(entryRoom, depth, articulation, onLoop, bridgeCorridor)`. A two-phase
lifecycle: **structure** at carve time (`CorridorStage`), **roles** after spawns
(`StationTopologyStage`); `hasRoles()` guards the role accessors. Room id ==
`rooms()` index, corridor index == `corridors()` order. `BspCityGenerator`
surfaces `getLastStationGraph()` for the tests / future consumers.

## Gate — independent brute-force oracle

`StationTopologyTest` is the teeth: it cross-checks every bridge + articulation
flag against a **remove-and-flood oracle** that shares no code with Tarjan — a
corridor is a bridge iff physically removing it disconnects the graph; a room is
an articulation point iff removing it (and its corridors) disconnects what
remains (`n-1 < 2` → never). Cheap at gen scale. Also asserts the depth gradient
is a valid BFS layering (entry = 0, every corridor spans ≤ 1 depth step),
on-loop consistency, and full seed determinism (regenerate → identical roles).

Result across all 6 seeds — oracle agrees with Tarjan, everything deterministic:

| | rooms | corridors | bridges | articulation | maxDepth |
| --- | --- | --- | --- | --- | --- |
| typical | ~43 | ~46 | ~30 | ~22 | 13–20 |

The shape checks out: a spanning tree of N rooms is N−1 edges, +`N/10` sparse
loops → corridors ≈ rooms + ~4, most of them bridges. The graph is **tree-
dominant**, so articulation points are abundant (~half the rooms) and bridges
dominate — the tense-funnel station character, with the few cyan loops the only
flankable redundancy.

## Preview

`BspMapPreviewTest.renderStationRolesBatch` → `station-roles-*.png`: rooms filled
by depth gradient (green breach → red deep end), articulation rooms white-ringed,
corridors drawn as center-to-center lines (red = bridge/on-spine, cyan =
loop/flank). The depth gradient visibly flows spawn-to-spawn; bridges sit on the
chokepoints; loops are the visibly redundant connections.

## Observations / follow-ups

- **Articulation is near-default** under tree+sparse-loops (~half the rooms), so
  the *discriminating* defensive signal is really **bridge + depth**, with loops
  marking the flankable minority. If we want fewer must-pass points / more
  tactical variety, **bump the loop budget** (`CorridorStage.LOOP_FRACTION_DENOM`)
  — the topology recomputes for free. A real tuning lever now that we can measure
  it.
- **No consumer yet** — this is deliberately the foundation. Next slices query
  these roles: defensive placement biased to `{bridge mouth, high depth,
  articulation}`, fallback bunkers to `{deep terminal, low degree}`, flank watch
  to `{on-loop}`. That's where roles become gameplay.
- Tarjan is recursive; recursion depth is bounded by room count (tens, low
  hundreds for big stations) — fine for the JVM stack. Revisit only if station
  dimensions grow far beyond 240×160.

## Next on the station track

Per [`../next-session.md`](../next-session.md): the **width policy / junction
bulges** (degree-≥3 → 4–6-wide arenas, ban hold-nodes on degree-2 cells) and the
**thematic station kinds** (`HANGAR`/`COMMAND`/`HABITATION`) which sit on top of
these roles. Plus the first **role-querying placement pass** — the first time the
roles drive something player-visible.
