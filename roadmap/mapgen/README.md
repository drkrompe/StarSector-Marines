# Map generation

> The BSP city generator + its fills + the post-fill stamper passes
> that turn a partitioned grid into a playable battle map. Currently
> shaped around ground-compound conquest maps; in the middle of a
> refactor to generalize the room-detection / chamber-labeling seam
> so the same scaffolding serves station interiors, ship interiors,
> and future map types.

## What's here

- [`pipeline-audit.md`](pipeline-audit.md) — full survey of the current
  pipeline (partition → label → fill → stamp), the fill catalog, where
  rooms are inferred-not-labeled, and the coupling points that block
  other map types. **Read this first** if you're picking up the
  refactor cold; the file:line citations are load-bearing.
- [`room-purpose-refactor.md`](room-purpose-refactor.md) — the active
  refactor (Slice A shipped, B/C/D outlined). Goal: carve-time
  `RoomPurpose` labels replace post-hoc zone-graph inference, so adding
  a new map type (station, ship interior) becomes "implement the
  partitioner + extend the enum" instead of "teach every consumer a new
  zone-inference rule."

## North star

The map-gen pipeline needs to serve at least three biomes in the
long run:

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

The shared abstraction the refactor is building toward: **labeled
rooms as the seam between carve-time intent and post-fill consumer
logic**. Today's room representation is "bbox + zone-graph flood,
inferred by stampers reading geometry"; tomorrow's is "per-cell
`RoomPurpose` label written by the partitioner, read by stampers
and AI."

The same room-purpose machinery also de-fragilizes the keep stamper:
the existing slice-6 `KeepEntryChamberStamper` reverse-engineers "the
zone that isn't the COMMAND_POST's must be the antechamber" via
`ZoneGraph` flood, which works for binary partition but doesn't
generalize past two rooms. Labels make the throne / entry / inner
distinction explicit at carve time.

## Slice progression at a glance

| Slice | Status | What |
|---|---|---|
| **A** | shipped (`82c76a9` + `d3f659d`) | `RoomPurpose` enum + per-cell label storage; `BuildingShellCore` stamps labels at carve time; `KeepEntryChamberStamper` migrates to label-driven detection (no more transient `ZoneGraph`). `BuildingConfig` uses `chamberPurposesByAnchorDistance` array (extensible to N chambers). |
| **B** | pending | Extract `maybeAddInteriorWall` into `PartitionStrategy` interface. `BinaryPartitionStrategy` preserves current behavior. Pure extraction, no new gameplay. |
| **C** | pending | `TernaryPartitionStrategy` — carve two parallel partition walls along the long axis, three chambers. `MilitaryBaseFiller` COMMAND opts in when bbox is large enough. |
| **D** | pending | `KeepEntryChamberStamper` emits one `INNER_POSITION` per non-throne labeled chamber (entry + inner garrisons in the three-chamber case). |

Station-tier fills are an **extension**, not a slice in this sequence —
they'll arrive as a separate track once Slice D lands and the
`PartitionStrategy` + `RoomPurpose` machinery is proven.

## Why this isn't its own gameplay feature

The map-gen refactor doesn't ship new gameplay on its own — Slice A's
output is bit-for-bit equivalent to pre-refactor behavior, just with
labels stamped alongside the existing zone-graph paths. Slice D is the
first commit that changes what the player sees (the central keep now
has two interior garrisons instead of one). The intervening slices are
seam work the rest of map-gen needs before station fills can land
without re-writing the central plumbing.

## Cross-refs

- [`../conquest/central-keep.md`](../conquest/central-keep.md) — slice
  6 of that doc (multi-room keep) is what surfaced the need for this
  refactor. The keep is the immediate driver but not the only consumer.
- [[battle_services_systems]] — Services vs Systems decomposition the
  rest of the battle tier follows. The map-gen refactor is structurally
  similar: extract the partitioner from the carve as a strategy, pass
  it via config, keep the carve orchestration stateless.
- [[wall_picker_semantic]] — `WallMasks.stampPerimeter` is the
  shared seam any new partitioner must use for wall direction masks.
