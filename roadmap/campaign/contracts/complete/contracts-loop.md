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

## Contract lifecycle, power, and stationing defaults

```
src/main/java/com/dillon/starsectormarines/campaign/systems/
  HousePowerSystem.java
    Rebuilds housePower[] from current stake-share totals before downstream
    promotion/default consumers (`40cc9454`).

  StationingDefaultSystem.java
    Runs before retainer/training delivery. Handles:
    - patron DEPOSED → stationing contract DEFAULTED
    - deterministic whole-month default checks from a persisted clock
    - 8% zero-power risk, reduced 1 point per 100 power to a 1% floor

  ContractLifecycleSystem.java
    Runs after retainer/training delivery. Handles:
    - OFFERED rows past their offer window → EXPIRED
    - stationing expiresTick passed → COMPLETED (if phases done)
      or FAILED (if not)

  StationingDefaultExtractionSystem.java
    Creates exactly one linked Recovery contract for stranded personnel.

  ExtractionResolutionSystem.java
    Settles successful/failed Recovery personnel and the one-time employer
    breach reputation penalty (`1051c22a`, `13e1f22e`, `0516486c`).

  StationingWithdrawalService.java
    Atomically returns idle stationing personnel, marks ABANDONED, and applies
    the forfeiture/reputation consequences. Active local assignments remain
    visible through the Ops management surface (`6c205512`, `92c4910e`).
```

`ContractReputation` now centralizes completion, failure, abandonment, and
employer-breach mutations. Ordinary completions award tier-scaled MRB
credibility (T1/T2/T3/T4 = 1/3/10/20), failures cost 1, abandonment costs 10,
and employer default remains MRB-neutral (`7fe8971f`).

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
  + Buttons: "Spawn local Escort/Garrison/Cadre offers" — direct-create
    production-shaped offers for every eligible patron in the player's
    current system that doesn't already have an outstanding offer. Rank
    gates and target requirements match the generator. Stationing offers
    must be accepted through the Ops assignment UI, never the old debug
    Accept shortcut (`c641cff0`)
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
- **OFFERED → ACTIVE flip on briefing accept** — single-phase STRIKE/Escort
  contracts still resolve OFFERED → terminal directly. Planetary Assault now
  freezes contract-wide terms on first deployment and resolves the first phase
  into IN_PROGRESS, which is the only mode that needs persistent recurrence.
- ~~**Offer expiry** — offers don't lapse.~~ **Shipped** (`7136bc09`):
  `ContractGenerator` stamps `offerExpiresTick` per the patron's
  archetype-driven window; `ContractLifecycleSystem` lapses `OFFERED → EXPIRED`.
- ~~**Patron archetype byte populated at seed time**~~ **Shipped**
  (`e3cbe306`, `1e6afe6d`): `HouseSeeder` populates `houseArchetype[]`
  deterministically and the briefing path reads it through
  `BriefingComposer.compose(archetype, mood, …)` (archetype × comms-officer
  mood). See the `narrative/` track.
- ~~**Contract generator for non-STRIKE types**~~ — **shipped**:
  rank-gated one-shot Escort offers and Extraction mission mapping landed in
  `df0a5d19`.
  Garrison/Cadre now have persisted term/retainer, personnel assignment/release,
  rank-gated generation, and dedicated acceptance UI (`2487cfaf`, `644b0a1f`,
  `0b2829ec`), plus monthly Cadre training XP (`68a3673d`). Power-priced monthly
  defaults create system-generated Recovery missions, whose outcome settles
  stranded personnel and employer breach reputation (`1051c22a`, `13e1f22e`,
  `40cc9454`, `0516486c`). Local early withdrawal and tier-scaled MRB outcome
  scoring are also shipped (`6c205512`, `92c4910e`, `7fe8971f`). Tier-3
  Planetary Assault adds production/debug offers plus a 3–5 phase
  Recon→Softening→Main Assault→optional Mop-up/Consolidation sequence with
  staged economy, non-final retry, contract-wide negotiation, idempotent phase
  result keys, and three-day refit cadence (`d567c838`, `58a3715f`, `b297a8c5`,
  `e404b8a9`, `80f12862`, `28734a6f`). Shared MRB/house-standing eligibility
  gates now constrain generation and first acceptance without hiding Recovery
  or accepted obligations (`9de789d4`). EXTRACTION remains system-generated.
- ~~**ContractGenerator unit test**~~ — **shipped** in `fb268bbe`: seeded
  reproducibility, offer shape, per-patron cap, and global cap are covered.

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
