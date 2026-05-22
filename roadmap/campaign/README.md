# Campaign tier

The campaign tier is the meta-layer above per-battle play: persistent
houses that own industries, patrons that hire the player, contracts
that drive missions, MRB reputation that gates which tier of patron
will deal with you, and a chain layer that strings months of patron
activity into a single political arc.

Player-facing it's "who's offering me work and why" — at the longest
horizon, it's the path from desperate Tier-1 Capo runs to a Tier-4
faction-flip endgame.

## Design docs

Stable, edited as the design evolves. Read these before changing
campaign-tier code.

- [`themes.md`](themes.md) — flavor + tone for the four house
  flavors (Corporate / Feudal / Underworld / Sectarian).
- [`economy.md`](economy.md) — the money loop: scale inefficiency,
  retainer vs lump-sum, MRB licensing tiers, "fence on the spot"
  patterns.
- [`mechanics.md`](mechanics.md) — the SoA tables (houses, stakes,
  relationships, chains, playerReputation), promotion math,
  visibility/rank semantics, hidden-pretender / displaced-claim
  layer.
- [`contracts.md`](contracts.md) — the contract layer: five contract
  types, two modes (Stationing vs Mission), lifecycle state machine,
  three-layer salvage model, MRB rep, patron archetypes, procedural
  fatigue mitigations.
- [`architecture.md`](architecture.md) — the four architectural
  commitments any new campaign code has to honor: SoA in primitive
  arrays, behavior in Systems not data, read/write declarations,
  O(1) id↔index lookups. Read this first.

## Implementation history

Numbered tracking of what's landed, mirroring the
[`../ai/complete/`](../ai/complete/) pattern.

1. [`complete/01-skeleton-and-systems-framework.md`](complete/01-skeleton-and-systems-framework.md)
   — initial SoA data model, the four architecture commitments,
   `CampaignSystem` framework with five stub systems,
   `LongIntMap` + O(1) id↔index lookups, dev-gated
   `CampaignDebugIntel` for playtest forcing functions.
2. [`complete/02-contracts-loop.md`](complete/02-contracts-loop.md)
   — sixth SoA table (contracts[]), `ContractType` + `ContractState`
   enums, MissionResolver bridge (battle outcomes write back to
   contracts + patron rep), `ContractLifecycleSystem` +
   `ContractGenerator`, patron houses surfacing as Clients on local
   planets, in-briefing salvage negotiation UI, debug client with
   full MissionType × RiskLevel grid, debug intel expanded for
   contract-pipeline forcing.

## Current focus + immediate next-up

See [`../README.md`](../README.md) — the top-level roadmap names the
campaign tier as the active surface and tracks the next-up list there.

## Followup design docs gated by current work

- **`loot.md`** — post-battle salvage UI: item pool generator,
  cargo-capacity interaction, captain trait + fleet modifier surfacing,
  item value display. Gated by `contracts.md`'s three-layer salvage
  model (already plumbed end-to-end through the resolver). Highest
  user value of any next-up.
- **`infrastructure.md`** — buildings that modulate garrison default
  rates and per-house power. Defensive infra reduces Garrison default
  rolls; intel infra surfaces hidden pretenders sooner. Mitigation
  side of scale inefficiency from `economy.md`.
- **`t3-endgame.md`** — the Tier-4 contracts. Faction-flip endgame.
  Only `CampaignSystem` allowed to write back to vanilla state
  (per `architecture.md` §5).
- **`narrative.md`** — hidden heirs / story chains layered on top of
  the procedural graph.

## Related

- [`../ai/`](../ai/) — battle AI roadmap (GOAP for infantry/mechs).
  Per-squad tactical AI inside the missions the campaign tier
  generates.
- [`../convoy/`](../convoy/) — ground-vehicle reinforcement for
  the battle layer.
- Memory: [[project-campaign-architecture]],
  [[project-campaign-storage-soa]], [[project-rank-ladder]],
  [[project-salvage-vision]], [[project-player-background]],
  [[project-black-swan-events]], [[project-moral-compass]],
  [[user-battletech-campaign-lineage]],
  [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]].
