# Campaign mechanics: data model and tick loop

> Design discussion, not a spec. Continues from
> [`themes.md`](themes.md) and [`economy.md`](economy.md). Encodes
> the data model that houses + stakes + reputation + chains operate
> on, and the per-tick simulation loop that drives it all.

## Why this is the first persistence-heavy system

[`MarineRosterScript`](../../src/main/java/com/dillon/starsectormarines/marine/MarineRosterScript.java)
already persists a captain list through Starsector's xstream
serialization. The houses graph is a different scale of state:
hundreds of entities with relationship edges, simulated every campaign
tick across months of in-game time. This is the first mod-side system
that needs *real* data-model thinking, not just "throw it in a script."

## Storage: SoA in primitive arrays

**Decision: structure-of-arrays in Java primitive arrays, persisted
through xstream the same way `MarineRosterScript` does. No SQLite.**

Paradox games use SQLite because their state is on the order of 100k+
entities and they need flexible joins. Our scale is two orders of
magnitude smaller:

| Entity | Estimated count | Per-row bytes (SoA) | Total |
| --- | --- | --- | --- |
| Houses | ~700 | ~40 | ~30 KB |
| Stakes | ~2000 | ~24 | ~50 KB |
| Relationships | ~3000 | ~24 | ~70 KB |
| Chains | ~50 active | ~32 | ~2 KB |
| Player reputation | ~200 (touched) | ~24 | ~5 KB |
| **Total** | | | **~150 KB** |

Per-tick linear scan over these tables is microseconds. SQLite would
add a native-library dependency, fight Starsector's script sandbox
(which blocks `java.io.File` and `java.nio.file`), and pay back only at
~100× our scale. xstream serializes primitive arrays natively, so
save/load is free.

The dividing line where we'd reconsider: state grows ~100×, or we
need flexible ad-hoc queries (joins, filtering) that aren't satisfied
by linear scans. Neither is currently in scope.

## Interned id registries

Three vanilla-string ids need interning to fit the SoA primitive layout:

- `factionId`  → `int` (e.g. `"hegemony"` → `0`, `"tritachyon"` → `1`)
- `industryId` → `int` (e.g. `"refining"` → `0`, `"heavyindustry"` → `1`)
- `marketId`   → `int` (vanilla markets identify by string; interning
  symmetrically with faction/industry keeps the SoA columns primitive)

Registries are populated lazily — each new id encountered at seed /
mission-resolution time gets the next available slot. Persisted as part
of the campaign-state script so slots remain stable across save/load
(no remapping at load time).

`houseId` stays as `long` — mod-internal sequential ids assigned on
creation, no string lookup involved.

## The five tables

### `houses[]`

```
id                  long      // primary key
marketId            int       // interned vanilla market id
factionId           int       // interned vanilla faction id
flavor              byte      // CORPORATE / FEUDAL / UNDERWORLD / SECTARIAN
rank                byte      // TIER_1..TIER_4
status              byte      // ACTIVE / DORMANT / HIDDEN_PRETENDER / DEPOSED
archetype           byte      // TIME_RUSHED / FALLEN_NOBLE / TRUE_BELIEVER /
                              // ESTABLISHED / SUSPICIOUS / NEWCOMER — patron
                              // content-axis; drives briefing register
                              // (see contracts.md). May shift on state events.
ambitionType        byte      // CONSOLIDATE_STAKE / DISPLACE_RIVAL /
                              // PROMOTE / CLAIM_THRONE / etc.
ambitionTargetId    long      // stake / industry / rival house id
promotionProgress   short     // 0..threshold; resets on promotion
power               int       // cached aggregate of stake values
claimAgainstHouseId long      // hidden heirs / pretenders; -1 otherwise
```

The `status` field is what enables narrative content. A
`HIDDEN_PRETENDER` exists in the table but doesn't appear in any house
list, holds no stakes, and can be *discovered* by player ops. A
`DEPOSED` house is a rightful-heir-in-exile the player can help return
to power. Normal gameplay only touches `ACTIVE` houses.

### `stakes[]`

```
id          long
houseId     long
marketId    int       // interned vanilla market id
industryId  int       // interned vanilla industry id
share       byte      // 0..255 = % controlled, divide by 255 for fraction
```

A stake is one house's claim on a slice of one industry. Multiple
houses can hold stakes in the same industry — their shares sum to
≤ 1.0; the remainder is "unclaimed" / faction-baseline.

When a mission resolves into a stake transfer, the loser's `share`
decreases and the winner's increases (or a new row is created).

### `relationships[]`

```
houseA              long
houseB              long
affinity            byte      // -128..127
lastInteractionTick int
```

Locality-and-rank-gated edges. Created when two houses become mutually
visible (both conditions met). Affinity drifts on per-tick interaction
rolls and shifts hard on player chain resolutions.

The table is *naturally sparse* because of the rank ladder: most
houses are Tier-1 (planet-local), so most edges live within a single
market. Tier-2+ edges cross planets but are fewer in count.

### `chains[]`

```
id              long
patronHouseId   long      // who hired the player; -1 for autonomous
targetHouseId   long      // who's the target
tier            byte      // T1 / T2 / T3
archetype       byte      // CONSOLIDATE_STAKE / SABOTAGE_PROMOTION /
                          // ELEVATE_HEIR / CIVIL_WAR / etc.
progress        short
threshold       short
discoveryRisk   byte      // 0..255 = per-mission chance of being exposed
initiatedTick   int
```

A chain is the multi-step political play the player runs for a
patron — CK3's *scheme* analog. Progress advances per completed
mission; threshold determines completion. Discovery risk grows with
progress: late-chain missions are more likely to be traced back to
the patron, with reputation consequences if exposed.

When `patronHouseId == -1`, the chain is autonomous (NPC houses
running their own plots) — these resolve on tick threshold without
player input.

### `playerReputation[]`

```
houseId             long
rep                 int       // -100..100
contractsCompleted  short
contractsFailed     short
lastContractTick    int
```

Separate from inter-house affinity. The player has a relationship with
every house they've worked with — positive *and* negative, because
running ops against House B for House A leaves a paper trail with B.

Reputation gates contract availability, contract pay multipliers, and
captain "preferred contractor" status (per [`economy.md`](economy.md)).

## The tick loop

Runs daily on the Sector clock (slow to per-3-days if profiling
demands). Per tick, in order:

```
onDailyTick():
  // 1. Advance autonomous house ambitions (stake-based promotion)
  for house in houses where status == ACTIVE:
    house.promotionProgress += stakeBasedDelta(house)
    if house.promotionProgress >= rankThreshold(house.rank):
      promote(house)

  // 2. Roll for relationship interactions within locality (~weekly)
  if tickCount % 7 == 0:
    for edge in relationships:
      if shouldInteract(edge):
        rollInteraction(edge)   // may shift affinity, trigger chains

  // 3. Advance active chains (player and autonomous)
  for chain in chains:
    chain.progress += chainTickDelta(chain)
    if chain.progress >= chain.threshold:
      resolveChain(chain)

  // 4. Player garrison defaults (per economy.md)
  for garrison in playerGarrisons:
    if defaultRoll(garrison):
      spawnExtractionMission(garrison)

  // 5. Discovery propagation — surface visible events to intel feed
  for event in pendingPlayerVisibleEvents:
    intelFeed.add(event)
```

Cost at our scale: well under 1 ms per tick, dominated by the
relationships loop (~3000 edges, weekly). Trivially within budget.

## Rank ladder mechanics

Vocabulary table lives in [`themes.md`](themes.md#rank-ladder).

### Visibility computation

Derived from rank, not stored as a graph property:

```
houseACanSee(houseB):
  switch min(houseA.rank, houseB.rank):
    case TIER_1: return houseA.marketId == houseB.marketId
    case TIER_2: return sameSystem(houseA, houseB)
    case TIER_3: return houseA.factionId == houseB.factionId
    case TIER_4: return true
```

Edges in `relationships[]` are *only created when* visibility allows
it. When a house promotes, its visibility expands and new edges may
be added to broader peers (see promotion below).

### Promotion sources (hybrid)

- **Autonomous**: per-tick `stakeBasedDelta` adds progress based on
  the house's current stake holdings relative to their market's
  total. A house owning >50% of a market's industries promotes
  toward Tier-2 organically.
- **Player chains**: completed chains targeting a patron's promotion
  bump `promotionProgress` directly, often by amounts that would
  take months autonomously. A player-aligned house can promote
  *fast*.
- **Suppression**: chains can target *negative* progress on a rival —
  a Baron the player wants stuck as a Baron stays that way. Also
  applies to autonomous houses the player wants kept down.

### Promotion event

When `promotionProgress >= rankThreshold(rank)`:

```
promote(house):
  house.rank += 1
  house.promotionProgress = 0
  recomputeVisibility(house)        // may add edges in relationships[]
  unlockContractTypes(house)        // see contract-type gating below
  if house.rank == TIER_4:
    triggerT3EndgameChain(house)    // faction-civil-war flip
```

Tier-4 promotion is the T3 endgame — vanilla state finally swings
(market ownership, faction rep). All of Tier 1-3 stays in our layer.

### Contract-type gating

Rank gates the contract types a house can offer the player:

| House rank | Contract types unlocked |
| --- | --- |
| Tier 1 (Baron) | Strike, small Garrison |
| Tier 2 (Count) | + cross-planet Garrison, Cadre |
| Tier 3 (Duke) | + Escort, Planetary Assault |
| Tier 4 (Crown Claimant) | + faction-civil-war T3 chain |

Working with low-tier patrons is how unproven mercs get hired; growing
with them up the ladder is how the player's *own* reputation climbs.

### Initial thresholds (balance pass)

- TIER_1 → TIER_2: 100 progress
- TIER_2 → TIER_3: 300 progress
- TIER_3 → TIER_4: 1000 progress (months of play; the chain to push
  someone over is itself the T3 endgame content)

## Stake transfer (mission resolution)

A direct contract resolves into the houses graph via:

```
onMissionVictory(mission, patron, target):
  amount = transferAmount(mission.type, mission.outcome)

  for stake in target.stakesOn(mission.targetIndustry):
    stake.share -= amount
    if stake.share <= 0: remove(stake)
  addOrIncreaseStake(patron, mission.targetIndustry, amount)

  patron.promotionProgress += missionProgressContribution(mission)
  target.promotionProgress -= sabotagedProgressLoss(mission)

  edge = relationships[patron, target]
  edge.affinity -= 20  // capped; rivals remember

  playerReputation[patron].rep += 5
  playerReputation[target].rep -= 8  // burning the target remembers

  chainsForPatron.advance(mission)
```

`transferAmount` scales by mission tier: T1 missions move small stake
amounts (a single contract changes hands); T2 missions move material
amounts (an industry's plurality shifts); T3 missions transfer entire
industries or trigger faction flips at Tier 4.

## Hidden heirs and displaced claims

Content layer hanging off the data model. Created at game-init,
scattered through the sector with `status=HIDDEN_PRETENDER` and
`claimAgainstHouseId` pointing at the house they'd displace. They
hold no stakes, have no relationships, no visible presence — they
exist only to be *discovered*.

**Discovery paths**:

- Random mission reward ("during the raid, you encountered an
  unusual prisoner...")
- Captain `Trait.SCOUT`
  ([`Trait.java`](../../src/main/java/com/dillon/starsectormarines/marine/Trait.java))
  ops increase per-mission discovery rolls.
- Story missions that surface specific heirs (handcrafted; CK3's
  "secret child" events).

**Once discovered, the player chooses**:

- **Support** — start an `ELEVATE_HEIR` chain. Long-form, multi-tier
  promotion-path for a Baron-tier heir to climb to their rightful Duke
  seat. The chain itself is the kingmaker narrative.
- **Sell to current ruler** — payout, heir is killed or exiled
  (status → `DEPOSED`, then `DORMANT` after time).
- **Ignore** — heir remains `HIDDEN_PRETENDER`, may resurface via
  another path later.

This is where the kingmaker narrative lives — the player as champion
who restores a deposed line, or who sells out a pretender to keep the
status quo paying. Both are valid, both have rep consequences.

## Open questions

1. **Stake-based promotion curve** — exact shape of
   `stakeBasedDelta`, what share counts as "consolidated enough."
   Balance pass.
2. **Chain discovery propagation** — when a chain is exposed, who
   learns and how? Probably the target house + their visible peers
   per the rank-ladder rules; affinity tanks across that set.
3. **Interaction roll cadence** — every tick is too noisy; weekly
   (per the `tickCount % 7` sketch) probably right. Tunable.
4. **House death** — a house with zero stakes and no claim becomes
   `DORMANT` (can re-emerge) or `DELETED` (gone forever)?
   Probably `DORMANT` for narrative continuity; `DELETED` only on
   explicit player wipe of a bloodline.
5. **Cross-flavor relationships** — does a Hegemony Count have an
   edge with a Tri-Tachyon Director on the same planet? Probably
   yes — edges are rank-and-locality-gated, *not* flavor-gated. A
   mixed market gets cross-flavor edges.
6. **Initial generation** — number of houses per market, initial
   stake distribution. Deterministic from market id + seed so reload
   gives identical state.
7. **Captain ↔ house binding** — captains accrue per-house reputation
   (per economy.md). Stored as a sixth table, or as a per-captain
   `Map<houseId, rep>` field on
   [`MarineCaptain`](../../src/main/java/com/dillon/starsectormarines/marine/MarineCaptain.java)?
   Per-captain field is simpler and rides existing serialization;
   sixth table is more SoA-uniform.

## Followup docs

- `contracts.md` — contract-type specifications, how each rank-tier
  maps to which contracts appear, salvage/payment/time terms.
- `infrastructure.md` — per-planet and per-region buildings, their
  in-game effects on the data model (stake influence, garrison
  default rate reduction).
- `t3-endgame.md` — Tier-4 promotion attempts: vanilla faction
  flip, splinter faction creation, market ownership change. Where
  this doc crosses into vanilla state.
- `narrative.md` (candidate) — hidden heirs, story missions,
  scripted character arcs that layer onto the procedural graph.
