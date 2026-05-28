# Contracts loop, end-to-end

Implementation half of the contract design from
[`../overview.md`](../overview.md). Builds on the skeleton +
Systems framework from [`skeleton-and-systems-framework.md`](../../framework/complete/skeleton-and-systems-framework.md).
This pass takes the contract layer from "design doc + empty SoA table
slot" to a **playable loop**: T1 patrons spawn offers, patrons show up
as clients on local planets, the player picks a mission with a
salvage-vs-cash negotiation knob, the battle resolves, and the
resolver bridge writes the outcome back to `CampaignState` with rep +
phase deltas.

Source session: [`../../../sessions/2026-05-21-3.md`](../../../sessions/2026-05-21-3.md).

## Contracts table — sixth SoA table on `CampaignState`

```
src/main/java/com/dillon/starsectormarines/campaign/
  ContractType.java       byte-backed: STRIKE / ESCORT / PLANETARY_ASSAULT /
                          GARRISON / CADRE / EXTRACTION. isStationing() and
                          isMissionMode() helpers.
  ContractState.java      byte-backed: ACTIVE / IN_PROGRESS / COMPLETED /
                          FAILED / DEFAULTED / ABANDONED / OFFERED.
                          OFFERED appended last to keep existing ordinals
                          stable across saves. isTerminal() helper.

  CampaignState.java
    + captainRegistry         IdRegistry (UUID strings → int slots)
    + contracts[] columns     18 parallel arrays — id, patron/target/chain
                              ids, type, state, ticks, phases, captain,
                              market, industry, basePayout, retainer, and
                              the salvage triple (baseline/negotiated/cash)
    + contractIndexById       LongIntMap for O(1) lookups
    + playerMrbRep            scalar — MRB industry-credibility track
    + addContract(...)        mutator maintains the index map
    + ensureContractCapacity  parallel-array grower

  CampaignTable.java
    + CONTRACTS               enum value for System reads()/writes() decls
```

## MissionResolver bridge — battle outcomes write back

```
src/main/java/com/dillon/starsectormarines/ops/MissionResolver.java
  compute():
    + cashMultiplier from Mission boosts payoutEarned (salvage-traded-
      for-cash bonus visible at results)
    + salvageEntitlement populated on victory — placeholder for the
      loot-roll consumer (captain SALVAGE_EXPERT trait + fleet Salvage
      Rig modifiers layer in at loot time)
  apply():
    + applyContractBridge() — finds the contract row by id, advances
      phasesDone on victory, flips state to COMPLETED/FAILED at
      terminal conditions, ticks the patron rep row
    + tickPatronRep() — clamps repValue to [-100,100], increments
      completed/failed counts, stamps repLastContractTick

src/main/java/com/dillon/starsectormarines/ops/Mission.java
  + contractId (long, -1 = ad-hoc)
  + salvageBaseline / salvageNegotiated / cashMultiplier (bytes)
  + delegating constructor keeps ad-hoc call sites untouched

src/main/java/com/dillon/starsectormarines/ops/MissionOutcome.java
  + contractId (long, -1 = ad-hoc)
  + salvageEntitlement (final %, computed in compute())
```

## ContractLifecycleSystem — time + patron-driven transitions

```
src/main/java/com/dillon/starsectormarines/campaign/systems/
  ContractLifecycleSystem.java  (replaces GarrisonDefaultSystem)
    Reads HOUSES + CONTRACTS, writes CONTRACTS + PLAYER_REP. Handles:
    - patron DEPOSED → contract DEFAULTED
    - stationing expiresTick passed → COMPLETED (if phases done)
      or FAILED (if not)
    - monthly random default roll — no-op until housePower is populated
```

## ContractGenerator — patrons put offers on the table

```
src/main/java/com/dillon/starsectormarines/campaign/systems/
  ContractGenerator.java
    Daily walk of T1 ACTIVE patrons. 5% per-tick chance to create a
    STRIKE offer at the patron's market against a random other T1
    active house. Two caps: 1 outstanding offer per patron, 20 sector-
    wide. RNG seeded from (day, patronId) for save reproducibility.
    Defaults: 25k baseline payout, 60% Strike-Raid salvage cap,
    1 phase, no expiry.
```

Wired into `CampaignStateScript.defaultSystems()` between chain
advancement and lifecycle so freshly-spawned offers survive the same
tick.

## Patron houses surface as Clients on local planets

```
src/main/java/com/dillon/starsectormarines/ops/Client.java
  + patronHouseId (long, -1 = faction-direct)
  + identity() — stable cache key for both flavors

src/main/java/com/dillon/starsectormarines/ops/MarineOpsContext.java
  + appendPatronClients() — walks CampaignState.contracts[], finds
    patrons with OFFERED rows at the current market, appends one
    Client per patron with the house's display name + faction crest
  + Cache key swaps from raw factionId to identity() so patron clients
    don't collide with faction-direct ones

src/main/java/com/dillon/starsectormarines/ops/MissionGenerator.java
  + early branch: client.patronHouseId != -1 → generateFromContracts()
    emits one Mission per OFFERED contract for that patron at this
    pickup market, with contractId + the salvage triple flowed through
  + target resolved via contractTargetHouseId → houseMarketId →
    vanilla MarketAPI; risk derived from target's defense profile;
    payout already includes cashMultiplier by the time it reaches the
    Mission constructor
```

## Salvage UI — negotiation in briefing, entitlement in results

```
mod/data/strings/strings.json
  + briefingSalvage / briefingSalvageFmt / briefingSalvageMinus / Plus
  + resultsSalvageLabel / resultsSalvageFmt

src/main/java/com/dillon/starsectormarines/ops/BriefingScreen.java
  + Payout row now shows the effective payout (m.payout × cashMult/100)
  + Salvage row beneath, only when salvageBaseline > 0 (contract
    missions). Shows "60% — +0% cash" with −/+ buttons at the right
    edge that adjust salvageNegotiated in 10-point steps within
    [0, salvageBaseline]. Curve per the contracts overview §"Salvage Layer 2":
    cashMultiplier = 100 + (baseline − negotiated) × 0.5
  + adjustSalvage() — Mission is immutable, so it builds a replacement
    and swaps it through ctx.setSelectedMission. Accept reads the
    updated values through the same path everything else does

src/main/java/com/dillon/starsectormarines/ops/ResultsScreen.java
  + Salvage row beneath Payout when outcome.salvageEntitlement > 0.
    Picker UI deferred to loot/overview.md; this just confirms the entitlement
    landed on the outcome.
```

## Debug intel — full contract pipeline forcing functions

```
src/main/java/com/dillon/starsectormarines/intel/CampaignDebugIntel.java
  + Top counter row: contractCount + captainRegistry size
  + State-breakdown row: per-state contract counts
  + Contracts list section: per-row id/type/state/patron/target/payout/
    salvage/phases with state-conditional buttons —
      OFFERED → Accept (flips to ACTIVE, stamps acceptedTick)
      ACTIVE/IN_PROGRESS → Force complete / Force fail (mirrors the
      resolver bridge so the writeback surface is exercised end-to-end
      without a battle)
  + Toggle: "Filter to local system" (default ON when in a system) —
    cross-references CampaignState.marketRegistry against
    Global.getSector().getEconomy().getMarket(id).getStarSystem();
    O(1) per-row check
  + Toggle: "Force daily tick" — runs every registered CampaignSystem
    once at the current day, bypassing the lastTickDay guard. The
    primary forcing function for watching ContractGenerator roll
  + Toggle: "Spawn offers for local patrons" — direct-creates STRIKE
    offers for every T1 patron in the player's current system that
    doesn't already have an outstanding offer. Collapses ~20 force-tick
    clicks per patron down to a single click that lights up every
    local market
  + Toggle: "Clear terminal contracts" — compacts COMPLETED/FAILED/
    DEFAULTED/ABANDONED rows out of contracts[] across every parallel
    array, rebuilding contractIndexById
```

## Debug client — full MissionType × RiskLevel grid for playtesting

```
src/main/java/com/dillon/starsectormarines/DevConfig.java
  + DEBUG_CLIENT  (default true)

src/main/java/com/dillon/starsectormarines/ops/MarineOpsContext.java
  + DEBUG_CLIENT_FACTION_ID = "marines_debug_client"
  + Prepends a synthetic "DEBUG — All Missions" client when DevConfig
    flag is on

src/main/java/com/dillon/starsectormarines/ops/MissionGenerator.java
  + generateDebugGrid() — 5 MissionType × 3 RiskLevel = 15 entries,
    one per (type, risk) combo. Bypasses MAX_MISSIONS so every combo
    is reachable from a single planet. Targets the planet's first
    non-disrupted industry so disruption writeback exercises end-to-end.
    Payouts + drop counts use production curves.
```

## Bug fix worth remembering

`MarineRoster.completedStoryIds` was added in commit 868c163 to track
one-shot story missions. Saves created before that commit deserialize
with the field as `null` — xstream bypasses the constructor so the
inline `= new HashSet<>()` never runs. First call to
`hasCompletedStory()` NPEs.

Fix: drop `final` on the field, add `private Object readResolve()`
that backfills `null` → empty set + returns `this`. xstream calls
`readResolve` after building the object graph, so the legacy save
round-trips cleanly.

Sets a precedent for the next post-initial-release `Serializable` field
added to a script-graph POJO. The field's comment + the readResolve
method together act as a copyable template — see
`MarineRoster.java:25-30,79-88`.

## What this loop does NOT do yet

- **Loot picker UI** — `salvageEntitlement` lands on the outcome and
  the results screen shows the %, but there's no item pool / item roll
  / picker grid yet. That's `loot/overview.md` territory and a session of its
  own.
- **OFFERED → ACTIVE flip on briefing accept** — currently single-
  phase STRIKE contracts skip ACTIVE entirely (OFFERED → COMPLETED
  directly on battle victory, since phasesDone goes 0→1 and
  phasesTotal=1). Functionally fine for the current scope; will need
  attention when multi-phase Planetary Assault lands.
- **Offer expiry** — offers don't lapse. Per-patron cap (1 outstanding)
  keeps the count bounded at ~80 sector-wide so it's not a real
  scaling concern, but the offer pool doesn't *feel* alive without
  expiry. Small lifecycle addition in `ContractLifecycleSystem`.
- **Patron archetype byte populated at seed time** — designed in
  `mechanics.md` and the narrative overview (CORPORATE_RUSHED / FALLEN_NOBLE /
  TRUE_BELIEVER / etc), unused so far. `HouseSeeder` doesn't populate
  it; briefing flavor doesn't read it.
- **Contract generator for non-STRIKE types** — ESCORT,
  PLANETARY_ASSAULT, GARRISON, CADRE, EXTRACTION are byte-backed in
  ContractType but never spawned. Strike is the only type the
  generator emits today.
- **ContractGenerator unit test** — caps + state machine + RNG
  determinism should have a small JUnit alongside `LongIntMapTest`.

## Sanity check

- `gradlew.bat :build` → BUILD SUCCESSFUL.
- All existing tests pass.
- Manual playtest (user): generator spawned offers, patron client
  appeared at Jangala, mission ran, bridge fired with
  `contract N COMPLETED (1/1)` + correct payout writeback. Salvage
  display rows render in both briefing and results.

## Commits

```
2842135  campaign: salvage negotiation in briefing + entitlement in results
4c16019  campaign: spawn-offers-for-local-patrons debug button
b44d6a0  campaign: debug client with full MissionType × RiskLevel grid
6a68e90  campaign: patron houses surface as clients on local planets
5c71145  campaign: local-system filter on debug intel
a99d54d  fix:      backfill MarineRoster.completedStoryIds on legacy saves
4473e23  campaign: contracts panel + force-tick in debug intel
53bb9b6  campaign: ContractGenerator + OFFERED state
94e57d4  campaign: contracts table + mission-resolver bridge
```
