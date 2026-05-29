# Story — Station interior fills

> The first map type beyond ground compounds. Rooms + corridors +
> hangars + habitation, driven by `RoomPurpose` labels the way the keep
> chambers already are. The payoff the room-purpose refactor was built
> toward. Scoped from the north star ([`../overview.md`](../overview.md))
> and the audit's coupling notes.

**Status:** not started. **Blocked on**
[`corridors-first-class`](corridors-first-class.md) — corridors are the
load-bearing connective primitive a station needs, and BSP doesn't model
them yet.

## What it adds

A station-interior generator that produces rooms + corridors + hangars +
habitation, where each room's purpose drives both AI tactical reads and
filler behavior:

- **`RoomPurpose` extension** — add station values (e.g. `HANGAR`,
  `COMMAND`, `HABITATION`, `CORRIDOR`) to the enum. The `ordinal()+1`
  byte storage on `CellTopology` is non-breaking, so this is additive.
- **A station `PartitionStrategy`** — "implement the partitioner + extend
  the enum" is the whole point of the refactor: adding a new map type
  should not mean teaching every consumer a new zone-inference rule.
- **Station fillers** that emit purpose-labeled rooms; stampers + AI read
  `topology.getRoomPurpose(x, y)` exactly as the keep stampers do today.

## Why it rides on the refactor

The shipped room-purpose machinery (labeled rooms as the seam between
carve-time intent and post-fill consumer logic) is precisely what makes
this "implement a strategy + extend the enum" rather than a central
plumbing rewrite. Slice A's labels, Slice B's `PartitionStrategy`
interface, and Slices C/D's N-chamber generalization are all the seam
this story plugs into.

## Open scoping question

Stations may not fit BSP at all — corridors-as-primary / rooms-as-
vertices may warrant a **different orchestrator** rather than another
`PartitionStrategy` inside `BspCityGenerator`. Resolve as part of the
corridors story before committing to a partitioner shape here.

## Cross-refs

- [`corridors-first-class`](corridors-first-class.md) — hard prerequisite.
- [`../overview.md`](../overview.md) — north star (the three biomes).
- [`../complete/room-purpose-refactor.md`](../complete/room-purpose-refactor.md)
  — the machinery this story extends.
