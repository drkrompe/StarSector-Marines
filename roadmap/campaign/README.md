# Campaign tier (epic)

The campaign tier is the meta-layer above per-battle play: persistent
houses that own industries, patrons that hire the player, contracts
that drive missions, MRB reputation that gates which tier of patron
will deal with you, and a chain layer that strings months of patron
activity into a single political arc.

Player-facing it's "who's offering me work and why" — at the longest
horizon, it's the path from desperate Tier-1 Capo runs to a Tier-4
faction-flip endgame.

This is an **epic**, not a single feature: it's a whole tier with
cross-cutting design docs plus several feature-sized threads, each laid
out like its own mini-roadmap (`overview.md` + `stories/` + `complete/`).

## Cross-cutting design docs

Stable, edited as the design evolves, and shared across every thread.
Read these before changing campaign-tier code.

- [`architecture.md`](architecture.md) — the four architectural
  commitments any new campaign code has to honor: SoA in primitive
  arrays, behavior in Systems not data, read/write declarations,
  O(1) id↔index lookups. **Read this first.**
- [`mechanics.md`](mechanics.md) — the SoA tables (houses, stakes,
  relationships, chains, playerReputation), promotion math,
  visibility/rank semantics, hidden-pretender / displaced-claim layer.
- [`economy.md`](economy.md) — the money loop: scale inefficiency,
  retainer vs lump-sum, MRB licensing tiers, "fence on the spot"
  patterns.
- [`themes.md`](themes.md) — flavor + tone for the four house flavors
  (Corporate / Feudal / Underworld / Sectarian).

## Feature threads

Each thread is a sub-directory with its own `overview.md`, `stories/`
(active slices), and `complete/` (shipped history).

| Thread | Status | What it is |
| --- | --- | --- |
| [`framework/`](framework/overview.md) | **shipped** | SoA tables + `CampaignSystem` tick framework + the architecture commitments. The substrate everything else sits on. |
| [`contracts/`](contracts/overview.md) | **loop playable** | Five contract types, two modes, lifecycle state machine, three-layer salvage model, MRB rep, mission-resolver bridge. |
| [`living-world/`](living-world/overview.md) | designing | The autonomous political sim: makes the four stub systems real. Two-tempo (drift + chains) engine, stake genesis, the Chronicle dispatch feed. Turns houses from labels into actors. |
| [`loot/`](loot/overview.md) | next-up | Post-battle salvage screen: item pool, picker UI, cargo interaction. Consumes the contract salvage entitlement. Highest user value. |
| [`infrastructure/`](infrastructure/overview.md) | designed | Buildings that modulate garrison default rates and house power; the mitigation side of scale inefficiency. |
| [`narrative/`](narrative/overview.md) | designed | The patron tapestry: comms-officer narrator, archetype content axis, procedural-fatigue discipline. |
| [`t3-endgame/`](t3-endgame/overview.md) | future | Tier-4 chain-only contracts; the faction-flip endgame. The only System allowed to write back to vanilla state. |

[`flavors/`](flavors/README.md) is an authoring bucket (one file per
house flavor), not a feature thread.

## Implementation history (sharded)

History is sharded per-thread — each feature owns its `complete/` log,
mirroring the [`../ai/complete/`](../ai/complete/) pattern but split by
thread rather than a single numbered spine:

- [`framework/complete/skeleton-and-systems-framework.md`](framework/complete/skeleton-and-systems-framework.md)
  — initial SoA data model, the four architecture commitments,
  `CampaignSystem` framework with five stub systems, `LongIntMap` +
  O(1) id↔index lookups, dev-gated `CampaignDebugIntel`.
- [`contracts/complete/contracts-loop.md`](contracts/complete/contracts-loop.md)
  — `contracts[]` SoA table, `ContractType` + `ContractState` enums,
  MissionResolver bridge (battle outcomes write back to contracts +
  patron rep), `ContractLifecycleSystem` + `ContractGenerator`, patron
  houses surfacing as Clients on local planets, in-briefing salvage
  negotiation UI, debug client + intel for contract-pipeline forcing.

## Current focus + immediate next-up

See [`../README.md`](../README.md) — the top-level roadmap names the
campaign tier as the active surface and tracks the next-up list there.

## Related

- [`../ai/`](../ai/) — battle AI roadmap (GOAP for infantry/mechs).
  Per-squad tactical AI inside the missions the campaign tier generates.
- [`../convoy/`](../convoy/overview.md) — ground-vehicle reinforcement
  for the battle layer.
- See also: [architecture](architecture.md), [mechanics](mechanics.md),
  [themes](themes.md), [loot](loot/overview.md),
  [backgrounds](backgrounds.md), [events](events.md),
  [moral compass](moral-compass.md).
- Memory: [[user-battletech-campaign-lineage]],
  [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]].
