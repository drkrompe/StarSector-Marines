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

## Persisted handoff contract

A `CIVIL_WAR` chain never writes vanilla state. Reaching its final threshold
prepares one append-only `throneClaims[]` row keyed uniquely by source chain:

| Column | Meaning |
| --- | --- |
| `throneClaimId` | Stable internal identity |
| `throneClaimSourceChainId` | Unique producing `CIVIL_WAR` chain |
| `throneClaimHouseId` | Tier-3 claimant house |
| `throneClaimSourceFactionId` | Persisted incumbent faction-registry slot |
| `throneClaimResultFactionId` | Deterministic splinter-faction registry slot |
| `throneClaimMarketId` | Claimant home market; the first vanilla flip target |
| `throneClaimState` | `PREPARED`, `APPLIED`, or `FAILED` |
| `throneClaimPreparedTick` | Campaign day the political chain completed |
| `throneClaimAppliedTick` | Vanilla-write day; `-1` until terminal |

Preparation is idempotent: a source chain can own exactly one row. The living
world may append and read this table but cannot move a row out of `PREPARED`.
Only the isolated T3 endgame system consumes it.

The consumer must verify the vanilla postcondition before mutation. If the
target market already belongs to the recorded result faction, it finalizes the
row without replaying writeback; otherwise it performs the faction creation /
market transfer once, verifies the result, then marks `APPLIED` and promotes the
house to Tier 4. A rejected precondition marks `FAILED` without partial rank or
reputation effects. This check-before-write rule makes a repeated tick or load
recovery idempotent even if persistence follows the vanilla mutation.

## CIVIL_WAR chain contract

- An ACTIVE Tier-3 `CLAIM_THRONE` house at the 1000-point handoff cap may create
  one 180-day `CIVIL_WAR` chain. Ordinary promotion cannot cross Tier 3.
- The chain targets the strongest ACTIVE same-faction rival by cached power as
  the incumbent coalition's political face; ties choose lowest house id. No
  viable rival means no autonomous civil war.
- The location is the claimant's home market. Progress bands represent
  coalition-building (0–59), mobilization (60–119), and open conflict
  (120–179). Base discovery risk is 128.
- Reaching 180 prepares the handoff row. It does not transfer a whole market,
  change faction reputation, create a splinter faction, or set Tier 4.
- A successful intervention before preparation fails the chain normally. Once
  a handoff is prepared, the political chain is closed and only the isolated
  consumer may apply or fail the irreversible result.

The result faction id is deterministic from claimant house id, using the
reserved form `starsector_marines_claimant_<houseId>`. Preparation interns that
identity so the eventual consumer never invents identity during writeback.

## Still to specify before vanilla writeback

- The exact vanilla API surface for faction creation and market transfer, and
  the postcondition probes used for retry recovery.
- Rep consequences across the rest of the sector when a flip lands.
- Contract composition and player choices across the three progress bands.
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
