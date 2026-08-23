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
Read the umbrella and the owning noun doc before changing campaign-tier code.

- `campaign-nouns.md` — umbrella vocabulary, boundaries, and player-facing
  campaign flow.
- `architecture.md` — the storage and runtime commitments any new campaign
  code has to honor: SoA state, stateless systems, read/write declarations,
  and stable id↔index access. **Read this first.**
- `living-world-nouns.md` — the political model: houses, stakes, ambitions,
  rank, chains, and the boundary before vanilla faction state.
- `economy.md` — **DRAFT direction** for the money loop: scale inefficiency,
  retainer vs lump-sum, and "fence on the spot" patterns. Licensing remains
  future direction, not current behavior.
- `themes.md` — **DRAFT direction** for flavor and tone across the four house
  flavors (Corporate / Feudal / Underworld / Sectarian).
- `backgrounds.md` — **DRAFT direction** for player starting-state seeds and
  their campaign meaning.
- `campaign-event-nouns.md` — exceptional campaign-event lifecycle and
  ownership boundary.
- `moral-compass.md` — the hidden record of player moral choices.

## Feature threads

Each thread is a sub-directory whose canonical target is `design/<noun>.md`,
`design/stories.md`, `design/shipped.md`, and bounded files under `stories/`.

| Thread | Status | What it is |
| --- | --- | --- |
| `campaign-framework-nouns.md` | **shipped; clock acceptance ready** | Persisted campaign state, monotonic time, and ordered autonomous systems. The runtime substrate below campaign features. |
| `contracts-nouns.md` | **G1-G32 shipped; acceptance parked** | Five contract types, two modes, lifecycle state machine, three-layer salvage model, MRB rep, mission-resolver bridge, and reactive Cadre/Garrison obligations. |
| `early-operation-nouns.md` | **opening ladder shipped; acceptance ready** | Two one-shot Independent jobs sized for a green company: local-line relief and a joint militia counterattack against finite ragtag forces. |
| `living-world-nouns.md` | **G9 shipped; no active implementation** | Autonomous politics, Chronicle, civil-war participation/consequences, civilian rescue, defector asylum, and Silent Colony through deterministic dead-site signals and Dead Letter choices. |
| `loot-nouns.md` | **ready for acceptance** | Manifest, picker, capacity-aware settlement, recovery modifiers, and rare AI-core gates are code-complete; one live in-game shipping check remains. Consumes the contract salvage entitlement. |
| `infrastructure-nouns.md` | **draft; no story contracted** | Location-bound investments that may supply bounded modifiers to campaign policies once the first vertical slice is specified. |
| `narrative-nouns.md` | **S1–S5 shipped; acceptance ready** | Fact-bound comms-officer narration with immutable patron memory, bounded local/Chronicle context, and remembered target locations. |
| `t3-endgame-nouns.md` | **shipped; acceptance ready** | Tier-4 faction-flip handoff and kingmaker Last Testament capstone. The only domain allowed to write vanilla ownership and faction diplomacy. |

[`flavors/`](flavors/README.md) is an authoring bucket (one file per
house flavor), not a feature thread.

## Implementation history (sharded)

History is sharded per-thread — each feature owns its `complete/` log,
mirroring the [`../ai/complete/`](../ai/complete/) pattern but split by
thread rather than a single numbered spine:

- `contracts-nouns.md` and `shipped.md`
  — `contracts[]` SoA table, `ContractType` + `ContractState` enums,
  MissionResolver bridge (battle outcomes write back to contracts +
  patron rep), `ContractLifecycleSystem` + `ContractGenerator`, patron
  houses surfacing as Clients on local planets, in-briefing salvage
  negotiation UI, debug client + intel for contract-pipeline forcing. The
  remaining in-game checks live in `contracts-live-acceptance.md`.

## Current focus + immediate next-up

See [`../README.md`](../README.md) — the top-level roadmap names the
campaign tier as the active surface and tracks the next-up list there.

## Related

- [`../ai/`](../ai/) — battle AI roadmap (GOAP for infantry/mechs).
  Per-squad tactical AI inside the missions the campaign tier generates.
- `convoy-nouns.md` — ground-vehicle reinforcement
  for the battle layer.
- See also: `campaign-nouns.md`, `architecture.md`, `living-world-nouns.md`,
  `themes.md`, `loot-nouns.md`, `backgrounds.md`,
  `campaign-event-nouns.md`, `moral-compass.md`.
- Memory: [[user-battletech-campaign-lineage]],
  [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]].
