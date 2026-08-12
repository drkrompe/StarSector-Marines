# Civil-war participation contract

**Status:** DESIGN LOCKED (2026-08-12); persistence implementation next.

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
| 120–179 | Open conflict | `PLANETARY_ASSAULT` to seize the market | `PLANETARY_ASSAULT` to break the rebellion | decisive |

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

## Implementation slices

1. Persist chain allegiance/contribution and contract band/applied tick, including
   growth, compaction, and legacy-save backfill.
2. Add a pure validator/recorder for completed civil-war contributions.
3. Snapshot successful claimant attribution into `throneClaims[]`.
4. Generate paired band offers and wire acceptance/withdrawal behavior.
5. Apply weighted/decisive outcomes and exactly-once player consequences.

## Non-goals for the foundation

- No moral-compass values or capstone dialogue yet.
- No player reputation change from merely discovering or accepting an offer.
- No generic change to how non-civil-war intervention success fails a plot.
- No manual playtest requirement during this implementation session.
