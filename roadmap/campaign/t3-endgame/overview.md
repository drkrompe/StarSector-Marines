# Campaign T3 endgame — the faction flip

**Status:** ownership/rank/diplomacy/player-reputation path CODE COMPLETE
(2026-08-12); player-facing capstone narrative remains.

**Implemented:** `76c7579a`, `49f057ad`, `9bf2356b`, `bdb45f7b`,
`35ec0ccf`, `77657bb8`, `51fad0ca`, `42f00725`, `ae35056d`, `870d5b96`,
`5174f44c`, `ad6ff5fd`, `527535fb`, `d7be2649`, `31be86ed`, `926047e8`,
`4332927f`, `50591863`, `3610923d`, `487134ae`, `551081ab`, `4f6afb8b`,
`753f3969`, `0ef347d3`, `03422b15`

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
| `throneClaimResultFactionId` | Predeclared Claimant League faction-registry slot |
| `throneClaimMarketId` | Claimant home market; the first vanilla flip target |
| `throneClaimPlayerAllegiance` | `NONE`, `CLAIMANT`, or `INCUMBENT` captured from the source chain |
| `throneClaimPlayerContribution` | Saturating successful-operation weight captured at preparation |
| `throneClaimPlayerLastContributionTick` | Last attributed operation day; `-1` when autonomous |
| `throneClaimPlayerConsequenceState` | Player-reputation lifecycle: `PENDING`, `APPLIED`, or `NOT_APPLICABLE` |
| `throneClaimPlayerConsequenceAppliedTick` | Player-reputation completion day; `-1` until applied |
| `throneClaimState` | `PREPARED`, `APPLIED`, or `FAILED` |
| `throneClaimPreparedTick` | Campaign day the political chain completed |
| `throneClaimAppliedTick` | Vanilla-write day; `-1` until terminal |
| `throneClaimConsequenceState` | Independent diplomacy lifecycle: `PENDING`, `APPLIED`, or `FAILED` |
| `throneClaimConsequenceAppliedTick` | Diplomacy completion day; `-1` until applied |

Preparation is idempotent: a source chain can own exactly one row. The living
world may append and read this table but cannot move a row out of `PREPARED`.
Only the isolated T3 endgame system consumes it.

The consumer verifies the vanilla postcondition before mutation. If the target
market and its primary/connected entities already belong to the recorded result
faction, it finalizes the row without replaying writeback. Otherwise it transfers
the market and entities once, verifies the result, then marks `APPLIED`, promotes
the house to Tier 4, clears its ambition, and records its new local faction. A
partial prior transfer is repaired; transient API failure stays `PREPARED` for
retry; a rejected precondition marks the claim and source chain `FAILED` without
partial rank or reputation effects. This check-before-write rule makes a repeated
tick or load recovery idempotent even if persistence follows the vanilla mutation.

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

Public Starsector APIs do not support registering arbitrary per-house faction
ids at runtime. The result therefore uses one predeclared faction identity,
`starsector_marines_claimants`, loaded from the mod's faction data. Preparation
interns that identity so the consumer never invents identity during writeback.
Multiple successful houses join the same Claimant League rather than creating
unsupported runtime faction objects.

## Diplomatic rupture — locked v1

Once ownership is `APPLIED`, the same isolated consumer applies the factional
consequence through a second narrow port. The Claimant League and former ruler
become mutually hostile at a `-0.5` relationship ceiling. Existing standings
worse than hostile are preserved. The two directions are probed independently,
so a partial mutation is repaired and an already-satisfied postcondition is not
replayed. Only verified success marks the separate consequence state `APPLIED`;
transient failures retry without touching ownership or rank.

An autonomous civil war does **not** change player reputation. Mere failure to
stop an off-screen rebellion is not a player choice. Player/claimant/incumbent
deltas belong to explicit kingmaker contracts and decisions, where attribution
can be persisted rather than inferred from a successful autonomous chain.
The locked terminal scale is +5/-8 for 1–29 contribution, +10/-15 for 30–59,
and +15/-25 for 60+. It changes house reputation only—not MRB reputation or
contract counters—and an incumbent outcome qualifies only when its decisive
contribution and failed-chain resolution share a day.

## Chronicle outcome — locked v1

An applied claim produces a dedicated immutable `THRONE_CLAIM_APPLIED`
Chronicle snapshot instead of the generic resolved-chain event. It records the
claimant and displaced rival, source and result faction identities, flipped
market, and actual ownership-write day. The dispatch remains blocked while the
claim is `PREPARED`, then renders the concrete faction transition exactly once.

## Still to specify after core writeback

- ~~Contract composition and player choices across the three progress bands.~~
  Locked in [civil-war participation](../living-world/civil-war-participation.md);
  persistence, contribution resolution, paired offers, and shared acceptance are
  shipped (`551081ab`, `4f6afb8b`).
- ~~Player reputation consequences for those explicit choices.~~ Shipped with
  contribution scaling and autonomous/stale neutrality (`03422b15`).
- The kingmaker capstone — see [moral compass](../moral-compass.md) for the
  multi-axis reveal that pays off at this tier. Its hidden ledger foundation is
  shipped (`facfa007`, `6765eac6`, `2a1924e7`), but the reveal waits for more
  than one meaningful source family.

## Related

- [`../mechanics.md`](../mechanics.md) — promotion math, stake transfer,
  rank ladder this arc tops out.
- [`../contracts/overview.md`](../contracts/overview.md) — Tier-4 sits
  above the five standard contract types.
- [`../architecture.md`](../architecture.md) §5 — the vanilla-writeback
  isolation rule.
- [`../economy.md`](../economy.md) — marginal-colony reward.
- Memory: [[user-battletech-campaign-lineage]].
