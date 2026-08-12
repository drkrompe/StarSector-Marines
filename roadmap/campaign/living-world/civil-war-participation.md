# Civil-war participation contract

**Status:** OFFER LIFECYCLE CODE COMPLETE (2026-08-12); terminal player
consequences next.

**Implemented:** `926047e8`, `4332927f`, `50591863`, `3610923d`,
`487134ae`, `551081ab`, `4f6afb8b`

This document fixes how the player enters an autonomous `CIVIL_WAR` without
turning every discovered plot intervention into a kingmaker decision. The
political chain remains autonomous; contracts let the player accelerate,
suppress, or ultimately decide it while leaving an attributable record.

## Identity and lineage

Existing contract lineage already expresses which side an operation serves:

- **Claimant-aligned:** `contractChainId == civilWarChainId` and
  `contractOpposedChainId == -1`. The contract advances its parent chain.
- **Incumbent-aligned:** `contractChainId == -1` and
  `contractOpposedChainId == civilWarChainId`. The contract opposes the chain.
- A civil-war contract may never bind both columns. Ordinary contracts bind
  neither. No duplicate persisted side field is added to contracts.

The chain persists its player relationship separately:

| Column | Meaning |
| --- | --- |
| `chainPlayerAllegiance` | `NONE`, `CLAIMANT`, or `INCUMBENT`; locked by first successful contribution |
| `chainPlayerContribution` | Saturating total of successful contribution weight |
| `chainPlayerLastContributionTick` | Last day a contribution applied; `-1` until touched |

Accepting an offer does not lock allegiance: the player may withdraw and take
the normal abandonment consequences. The first **completed** aligned operation
locks it. Once locked, opposite-side offers withdraw and opposite-side contracts
cannot contribute even if stale external state somehow completes one.

Each aligned contract snapshots its offer band and owns an exactly-once applied
tick. A completed contract is therefore safe to process after save/load without
replaying its chain progress or attribution.

## Three progress bands

Band is determined and persisted when the offer is created, never recomputed
from later chain progress.

| Chain progress | Political phase | Claimant operation | Incumbent operation | Weight |
| --- | --- | --- | --- | ---: |
| 0–59 | Coalition building | `ESCORT` envoys, defectors, or materiel | `STRIKE` organizers and caches | 15 |
| 60–119 | Mobilization | `CADRE` training for the claimant coalition | `GARRISON` the incumbent's threatened center | 30 |
| 120–179 | Open conflict | `PLANETARY_ASSAULT` to seize the market | `PLANETARY_ASSAULT` to break the rebellion | decisive (60 attribution) |

The first two bands move chain progress by their weight: claimant success adds,
incumbent success subtracts, clamped to `0..threshold`. Daily autonomous progress
continues normally. Open-conflict Planetary Assault is the decisive player route:
claimant success completes the chain and prepares the throne handoff; incumbent
success fails it. Without player participation, daily advancement still resolves
the civil war autonomously at 180.

Contract targets remain concrete even though the chain is market-wide. Offer
generation must choose and persist a valid local industry/objective for mission
construction; it must not mutate the chain's `-1` industry sentinel.

## Offer lifecycle

- Only a discovered ACTIVE `CIVIL_WAR` may create participation offers.
- At most one historical offer per side per band is generated. Terminal history
  deduplicates the band; advancing to the next band permits the next pair.
- Both sides may initially offer work. Accepting one withdraws the opposite
  OFFERED row for that band and blocks simultaneous contradictory active work.
- A failed or abandoned contract applies ordinary contract reputation rules but
  contributes nothing. If allegiance is still `NONE`, a later offer may come
  from either side in a later band.
- On first successful contribution, all opposite-side OFFERED rows expire.
- When the chain becomes terminal, every remaining OFFERED participation row
  expires. Accepted work is not silently rewritten; mission resolution observes
  terminal chain state and applies no political contribution.

## Attribution and consequences

The chain record—not current offers and not inferred faction standing—is the
source of truth for player involvement. When a successful chain prepares its
throne claim, it snapshots allegiance, contribution total, and last contribution
tick into the handoff. This preserves attribution after contract compaction and
through ownership/diplomacy retries.

Autonomous (`NONE`) victory stays player-reputation-neutral. Claimant-aligned
victory may later apply claimant/incumbent player consequences exactly once from
the handoff snapshot. An incumbent-aligned decisive victory has no throne claim;
its terminal consequences must consume the failed chain's persisted attribution.

### Player-reputation consequence contract

Successful contribution is cumulative and fixes the terminal house-reputation
magnitude. Values clamp to the existing `-100..100` range:

| Successful contribution | Supported house | Opposed house | Meaning |
| ---: | ---: | ---: | --- |
| 1–29 | +5 | -8 | visible support |
| 30–59 | +10 | -15 | material intervention |
| 60+ | +15 | -25 | decisive/kingmaker intervention |

For an applied claimant handoff, the claimant is supported and the displaced
incumbent is opposed. The immutable handoff snapshot supplies allegiance and
contribution; the source chain supplies the persisted displaced-house identity.
For an incumbent victory, the incumbent is supported and claimant opposed, but
only when the failed chain's resolution day equals its last contribution day.
This same-day condition distinguishes the decisive incumbent assault from a
later unrelated invalidation of a chain the player merely touched earlier.

These are political outcome deltas, not another contract payout: they do not
change MRB reputation or completed/failed contract counters. Separate persisted
claim/chain consequence states and applied days make terminal scanning exactly
once. Claimant consequences wait for ownership state `APPLIED`; incumbent
consequences wait for a terminal failed chain and recovered contribution.
Autonomous outcomes, offer acceptance, failed/abandoned operations, and late
terminal contribution replays remain neutral.

## Implementation slices

1. ~~Persist chain allegiance/contribution and contract band/applied tick,
   including growth, compaction, and legacy-save backfill.~~
2. ~~Add a pure validator/recorder for completed civil-war contributions.~~
3. ~~Snapshot successful claimant attribution into `throneClaims[]`.~~
4. ~~Generate paired band offers and wire acceptance/withdrawal behavior.~~
5. Apply exactly-once player consequences from terminal attributed outcomes.

Weighted and decisive chain outcomes landed with the validator: early bands
apply ±15/±30 progress; open-conflict claimant work arms threshold resolution;
open-conflict incumbent work fails the rebellion. A daily system after contract
lifecycle recovers completed mission-mode or stationing contributions exactly
once. Banded contracts are excluded from the older generic intervention shortcut.

The dedicated daily offer producer now owns discovered active civil wars. It
creates one historical offer per side/band, freezes the strongest concrete
contested local objective, withdraws stale band/terminal offers, and leaves the
chain's market-wide industry sentinel untouched. Mission and stationing
acceptance share one domain validator: choosing a side activates that work and
immediately expires the opposing offer, while later Planetary Assault phases
remain deployable without being treated as a new choice.

## Non-goals for the foundation

- No moral-compass values or capstone dialogue yet.
- No player reputation change from merely discovering or accepting an offer.
- No generic change to how non-civil-war intervention success fails a plot.
- No manual playtest requirement during this implementation session.
