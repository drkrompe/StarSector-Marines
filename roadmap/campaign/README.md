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
cross-cutting design docs plus several feature-sized threads. Each migrated
thread keeps its durable model and shipped ledger under `design/`, with only
genuinely open work under `stories/`; legacy overview/complete layouts remain
migration input until their thread is folded.

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

Each thread is a sub-directory whose canonical target is `design/<noun>.md`,
`design/stories.md`, `design/shipped.md`, and bounded files under `stories/`.

| Thread | Status | What it is |
| --- | --- | --- |
| [`framework/`](framework/overview.md) | **shipped** | SoA tables + `CampaignSystem` tick framework + the architecture commitments. The substrate everything else sits on. |
| [`contracts/`](contracts/overview.md) | **G1-G30 shipped; no active story** | Five contract types, two modes, lifecycle state machine, three-layer salvage model, MRB rep, mission-resolver bridge, and reactive Cadre/Garrison obligations. |
| [`early-operations/`](early-operations/overview.md) | **opening ladder shipped** | Two one-shot Independent jobs sized for a green company: local-line relief and a joint militia counterattack against finite ragtag forces. |
| [`living-world/`](living-world/overview.md) | **G9 active; Slice 2 complete** | Autonomous politics, Chronicle, civil-war participation/consequences, civilian rescue, defector asylum, and Silent Colony through deterministic dead-site signals and Dead Letter choices. |
| `loot-nouns.md` | **ready for acceptance** | Manifest, picker, capacity-aware settlement, recovery modifiers, and rare AI-core gates are code-complete; one live in-game shipping check remains. Consumes the contract salvage entitlement. |
| [`infrastructure/`](infrastructure/overview.md) | designed | Buildings that modulate garrison default rates and house power; the mitigation side of scale inefficiency. |
| [`narrative/`](narrative/overview.md) | **S1–S5 shipped** | Comms-officer narration with exact-once patron history, two-engagement patterns, bounded local/Chronicle context, and remembered target locations. |
| [`t3-endgame/`](t3-endgame/overview.md) | **shipped** | Tier-4 faction-flip handoff and complete kingmaker Last Testament capstone. The only System allowed to write back to vanilla state. |

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
- `convoy-nouns.md` — ground-vehicle reinforcement
  for the battle layer.
- See also: [architecture](architecture.md), [mechanics](mechanics.md),
  [themes](themes.md), `loot-nouns.md`,
  [backgrounds](backgrounds.md), [events](events.md),
  [moral compass](moral-compass.md).
- Memory: [[user-battletech-campaign-lineage]],
  [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]].
