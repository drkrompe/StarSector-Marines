# Black-swan events — the third content stream

> Design discussion, not a spec. Continues from [`themes.md`](themes.md).
> The cadence/balance numbers here are intentions, not committed values.

Black-swan events are a **third orthogonal content stream** in the
campaign tier, distinct from Chains (multi-month political plots) and
Contracts (commercial agreements). Events deliver the gameplay weight
that doesn't fit the commercial layer — the moments where optimization
breaks down and the player has to decide *what kind of merc they are*.

| Stream | Cadence | Driver | Reward | Choice surface |
| --- | --- | --- | --- | --- |
| Chain | Months | Patron's political plot | Stake / power | Strategic alignment |
| Contract | Days–weeks | House-offered job | Cash + salvage | Tactical commitment |
| **Event** | Hours, unpredictable | World conditions / black-swan triggers | Unknown (often moral, not material) | Snap moral test |

## Why events matter for the mod's thesis

Starsector gives the player goals (don't go broke, get the best ship,
build a faction) but doesn't give them a *reason*. Events are how we
deliver the reason — situations where optimization doesn't apply, where
the player chooses based on *who they are* rather than *what pays*. They
are the densest [moral-compass](moral-compass.md)-touching content in the
campaign.

## Proof-of-concept: civilian rescue, swarm defense

There is already **held art for the first event content** — a
zergling-type swarm race. The PoC event:

- **Civilian rescue, swarm defense** — spend resources to help colonists
  escape offworld. A defense mission against swarms of the swarm race.
  Unknown rewards (maybe nothing material; maybe a captain's loyalty
  shift; maybe a moral-compass tick; maybe a piece of unique gear). The
  **cost-shaped** structure — you *spend*, you don't *earn* — is the
  defining feature.

## Design rules

- **Events fire from world conditions, not patron offers.** Triggers:
  random rolls, sector-level crises, specific chain side-effects, rare
  encounter generation.
- **Cost-shaped framing preferred** — events that *charge* the player
  rather than reward them. The reward is narrative / moral / unique-drop,
  not credits.
- **Unknown rewards** — events explicitly do NOT preview their payouts.
  The player commits blind; some events offer nothing material.
- **Feeds the [moral compass](moral-compass.md)** — accepting / refusing /
  the outcome of an event shapes the silent track.
- **Visual hook** — the held swarm-race art unlocks the swarm-defense
  archetype. Other archetypes can follow (sector plague, refugee fleet,
  abandoned colony with unknown threat, defector arriving with intel).

## Open questions (parked)

- **Cadence** — probably balanced to ~1 event per 30–60 in-game days,
  varying by sector activity.
- **Refusal** — is "refuse to engage" always available, or do some events
  force a choice? Lean always-refusable (the moral weight comes from being
  *able* to walk away).
- **Repeatable vs one-shot** — the civilian-rescue template can re-spawn;
  specific named/chain-tied events are one-shot.

## Implementation surfaces

- New System: `EventSpawnSystem` (a `CampaignSystem` impl) — per-tick rolls
  against world conditions to spawn event intel.
- Storage: a new `events[]` SoA table, or reuse `contracts[]` with a
  `source = EVENT` discriminator (TBD — see [`mechanics.md`](mechanics.md)).
- Battle-tier: the swarm race needs its own `Faction` enum entry + unit
  types + AI behaviors. Significant infantry/AI work, and it needs the
  full-screen battle takeover style the mod is building toward (see
  [`../README.md`](../README.md) § Vision).

## Related

- [moral compass](moral-compass.md) — events are how the compass gets
  tested.
- [`themes.md`](themes.md) — the Chain/Contract streams events sit beside.
- [[feedback-world-reactive-over-expressive]],
  [[feedback-patron-narrative-discoverable]] — the durable principles
  events serve.
