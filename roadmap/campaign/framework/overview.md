# Campaign framework — the SoA + Systems skeleton

> The cross-cutting foundation every other campaign sub-feature builds
> on: the SoA data tables, the `CampaignSystem` tick framework, and the
> four architecture commitments. The *spec* is
> [`../architecture.md`](../architecture.md) (read it before any campaign
> code); this thread tracks the framework's implementation history.

## Status: shipped

The skeleton landed first — SoA data model compiling, the four
architectural commitments baked in (SoA primitive arrays, behavior in
Systems not data, read/write declarations, O(1) id↔index lookups),
`CampaignSystem` framework with stub systems, `LongIntMap`, and a
dev-gated `CampaignDebugIntel` for playtest forcing.

See [`complete/skeleton-and-systems-framework.md`](complete/skeleton-and-systems-framework.md)
for what actually landed.

## Why it's its own thread

Under the epic's sharded-history model, each sub-feature owns its
`complete/` log. The skeleton isn't a feature the player sees — it's
the substrate the contract loop, loot, infrastructure, and the T3
endgame all sit on — so it gets its own thread rather than squatting
in any one feature's history. New framework-wide work (a new SoA table
that isn't tied to a single feature, a change to the tick loop, a new
architecture commitment) logs here.

## Related

- [`../architecture.md`](../architecture.md) — the design spec and the
  four commitments this thread implements.
- [`../mechanics.md`](../mechanics.md) — the data model the tables encode.
- See also: [architecture](../architecture.md), [mechanics](../mechanics.md).
