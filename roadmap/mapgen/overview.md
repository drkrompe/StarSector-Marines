# Map generation

> Long-form companion to the story docs. Open this when picking up the
> map-gen work cold; read [`pipeline-audit.md`](pipeline-audit.md)
> alongside it — its file:line citations are load-bearing.

## What this is

The BSP city generator + its fills + the post-fill stamper passes that
turn a partitioned grid into a playable battle map. Currently shaped
around ground-compound conquest maps; the room-purpose refactor (Slices
A–D, all shipped) generalized the room-detection / chamber-labeling seam
so the same scaffolding can serve station interiors, ship interiors, and
future map types.

## North star

The map-gen pipeline needs to serve at least three biomes in the long
run:

1. **Ground compounds** (current) — BSP partitions a city into building
   leaves; building shells + perimeter walls + roads + compounds are
   what fillers emit. Conquest mode + the central-keep work live here.
2. **Station interiors** (future) — rooms + corridors + hangars +
   habitation. Very different from ground compounds: corridors are
   first-class connective structure (BSP today produces isolated bbox
   buildings), and room "purpose" (HANGAR / COMMAND / HABITATION /
   CORRIDOR) drives both AI tactical reads and filler behavior.
3. **Ship interiors** (further future) — even tighter rooms, fewer
   open spaces, purpose-driven (BRIDGE / CRYOBAY / LAB / CARGO_HOLD).

The shared abstraction the refactor built toward: **labeled rooms as the
seam between carve-time intent and post-fill consumer logic**. The
pre-refactor room representation was "bbox + zone-graph flood, inferred
by stampers reading geometry"; today's is "per-cell `RoomPurpose` label
written by the partitioner, read by stampers and AI."

The same room-purpose machinery also de-fragilized the keep stamper: the
old `KeepEntryChamberStamper` reverse-engineered "the zone that isn't the
COMMAND_POST's must be the antechamber" via `ZoneGraph` flood, which
worked for a binary partition but didn't generalize past two rooms.
Labels make the throne / entry / inner distinction explicit at carve
time.

## Architecture at a glance

Orchestrator: `BspCityGenerator` — a four-step sequence (full file:line
survey in [`pipeline-audit.md`](pipeline-audit.md)):

- **Partition** — `Bsp.partition()` carves the grid with axis-aligned
  cuts, each cut reserving a road strip; trunks painted first, BSP runs
  in the sub-rects between them on a shared road mask.
- **Label** — each leaf gets a `BlockKind` rolled from a zoning overlay
  (`BiomeMap` / legacy `DistrictMap`), with post-roll constraint filters.
- **Fill** — per-leaf `BlockFiller.fill(...)` for ordinary leaves;
  `CompoundFiller.fill(...)` once per multi-leaf compound. The fill
  catalog (residential / commercial / industrial shells, plazas, LZs,
  parks, nature zones; military-base / gated-housing / dense-quarter
  compounds) is enumerated in the audit.
- **Stampers + finalize** — post-fill passes that read what fillers built
  and emit tactical anchors or overlay structures (pedestrian frames,
  biome overrides, fortress wall, defense posts, keep entry chamber),
  then wall-HP seeding, cover baking, building flood-fill, spawn-anchor
  selection.

The room-purpose seam, introduced by the refactor:

- **`RoomPurpose` enum** in `battle.map` (next to `BuildingKind`) —
  `GENERIC`, `KEEP_ENTRY`, `KEEP_INNER`, `KEEP_THRONE`; `ordinal()+1`
  byte storage on `CellTopology` keeps it extensible/non-breaking.
- **`PartitionStrategy`** interface in `mapgen.bsp.fill` —
  `BinaryPartitionStrategy` (one interior wall) and
  `TernaryPartitionStrategy` (two parallel walls, asymmetric middle
  chamber, binary fallback for small buildings). `BuildingConfig` carries
  the strategy + a distance-indexed `RoomPurpose[]
  chamberPurposesByAnchorDistance`.
- **`BuildingShellCore`** writes labels at carve time;
  **`KeepEntryChamberStamper`** reads `topology.getRoomPurpose(x, y)`
  directly — no transient `ZoneGraph`.

## Status

The **room-purpose refactor is complete** — Slices A–D all shipped. The
full slice-by-slice record (commits, what landed vs. planned, the Slice A
critique findings, the corridor risk note) is sealed in
[`complete/room-purpose-refactor.md`](complete/room-purpose-refactor.md).

| Slice | Status | What |
|---|---|---|
| **A** | shipped (`82c76a9` + `d3f659d`) | `RoomPurpose` enum + per-cell label storage; carve-time labels; `KeepEntryChamberStamper` migrated off `ZoneGraph` |
| **B** | shipped (`042d084`) | `PartitionStrategy` interface + `BinaryPartitionStrategy` extraction; `PartitionLayout` replaces private `InteriorWall` |
| **C** | shipped (`ee55eb0`) | `TernaryPartitionStrategy` — two parallel walls, asymmetric middle, binary fallback; `int[] axes` + sorted-bisect `chamberIndex` |
| **D** | shipped (`b8b7b9d`) | `MilitaryBaseFiller.COMMAND_CONFIG` wired to ternary `[THRONE, INNER, ENTRY]`; stamper emits per-chamber `INNER_POSITION` nodes; preview overlay |

The intervening slices were seam work — Slice A's output is bit-for-bit
equivalent to pre-refactor behaviour; Slice D is the first commit that
changes what the player sees (the central keep now has two interior
garrisons instead of one).

## Active stories

Station-tier fills are an **extension**, not a slice in the A–D sequence.
The two articulated next tracks live in [`stories/`](stories/):

| Story | What it adds |
| --- | --- |
| [`corridors-first-class`](stories/corridors-first-class.md) | corridor abstraction — the real blocker for station fills; BSP today treats leaves as atomic buildings and roads as perimeter filler |
| [`station-interior-fills`](stories/station-interior-fills.md) | a station `PartitionStrategy` + `RoomPurpose` extension (HANGAR / COMMAND / HABITATION / CORRIDOR) once corridors land |

### Why this order

Corridors are the load-bearing blocker: BSP leaves are atomic buildings
and roads are perimeter filler, so connecting rooms as first-class
connective structure has to exist before station "rooms + corridors"
fills mean anything. Station interior fills are the payoff that rides on
top. Ship interiors are the same machinery turned up (tighter rooms) and
follow stations.

## Cross-references

- [`pipeline-audit.md`](pipeline-audit.md) — full survey of the current
  pipeline (partition → label → fill → stamp), the fill catalog, where
  rooms are inferred-not-labeled, and the coupling points that block
  other map types. **Read this first** when picking up cold.
- [`../conquest/central-keep.md`](../conquest/central-keep.md) — Slice 6
  of that doc (multi-room keep) is what surfaced the need for this
  refactor. The keep is the immediate driver but not the only consumer.
- [`../convoy/overview.md`](../convoy/overview.md) — the road graph the
  partition step reserves is consumed by convoy path-planning; see
  [[road_graph_design]].
- [[battle_services_systems]] — Services vs Systems decomposition the
  rest of the battle tier follows. The map-gen refactor is structurally
  similar: extract the partitioner from the carve as a strategy, pass it
  via config, keep the carve orchestration stateless.
- [[wall_picker_semantic]] — `WallMasks.stampPerimeter` is the shared
  seam any new partitioner must use for wall direction masks.

## How this directory is laid out

- **`overview.md`** (this file) — concept, north star, architecture,
  scope framing. The stable view; edit rarely.
- **`pipeline-audit.md`** — the load-bearing pipeline survey; the audit
  the refactor was scoped against.
- **`stories/`** — active/queued story docs, one per slice.
- **`complete/`** — sealed shipped work (commit hash + what actually
  landed).
- **`next-session.md`** — handoff state for picking up cold.
