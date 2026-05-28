# Campaign T3 endgame — the faction flip

> The longest-horizon arc: the path from desperate Tier-1 Capo runs to
> a Tier-4 faction-flip. The Tier-4 promotion *is* the endgame — see
> [rank ladder](../themes.md). This is the only place the campaign tier
> crosses into vanilla faction/market state, so per
> [`../architecture.md`](../architecture.md) §5 the System that lands it
> is the **only** `CampaignSystem` allowed to write back to vanilla.

## Concept

Tier-4 contracts are chain-only — they don't appear in the ordinary
per-rank offer table ([`../contracts/overview.md`](../contracts/overview.md)
§"Rank-gated availability"). They're the faction-civil-war payload at the
end of a multi-month chain where a patron (or the player) makes a play
for the top.

Mechanics that only exist at this tier:

- **Vanilla faction flip** — a market changes hands at the vanilla
  economy layer; vanilla reputation consequences ripple out.
- **Splinter-faction creation** — the player's company becomes (or
  midwifes) a new faction rather than just shifting an existing one.
- **Market ownership change** — the stake-transfer mechanics from
  [`../mechanics.md`](../mechanics.md) scaled up to a whole market.
- **"Marginal colony as reward"** — a low-value colony handed to the
  player as the tangible spoils of a flip, per
  [`../economy.md`](../economy.md).

## Why it's isolated

Writing vanilla state is irreversible from the player's save and
couples the mod to vanilla economy internals. Keeping all of it behind
one System at one tier contains the blast radius: every other campaign
System reads/writes only our SoA tables. That isolation is an
architecture commitment ([`../architecture.md`](../architecture.md) §5),
not a convenience.

## To specify

- The chain shape that gates a Tier-4 contract (how many contracts,
  which types, from which patron).
- The exact vanilla API surface for a faction flip and a splinter
  faction, and what's reversible vs. permanent.
- Rep consequences across the rest of the sector when a flip lands.
- The kingmaker capstone — see [moral compass](../moral-compass.md) for the
  multi-axis reveal that pays off at this tier.

## Related

- [`../mechanics.md`](../mechanics.md) — promotion math, stake transfer,
  rank ladder this arc tops out.
- [`../contracts/overview.md`](../contracts/overview.md) — Tier-4 sits
  above the five standard contract types.
- [`../architecture.md`](../architecture.md) §5 — the vanilla-writeback
  isolation rule.
- [`../economy.md`](../economy.md) — marginal-colony reward.
- Memory: [[user-battletech-campaign-lineage]].
