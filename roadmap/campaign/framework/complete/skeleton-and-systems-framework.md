# Skeleton data model + Systems framework

First code drop for the campaign tier. The design discussion had already
landed in [`themes.md`](../../themes.md), [`economy.md`](../../economy.md), and
[`mechanics.md`](../../mechanics.md); this pass turned the SoA data model
into a compiling skeleton, baked in the four architectural commitments
that any future campaign code has to honor, and hung a dev-gated debug
intel off the state so subsequent systems have something concrete to
playtest against.

Source session: [`../../../sessions/2026-05-21.md`](../../../sessions/2026-05-21.md).

## SoA data model

```
src/main/java/com/dillon/starsectormarines/campaign/  (new package)
  HouseFlavor.java       enum CORPORATE / FEUDAL / UNDERWORLD / SECTARIAN
  HouseRank.java         TIER_1..TIER_4 + per-flavor displayName + next()
  HouseStatus.java       ACTIVE / DORMANT / HIDDEN_PRETENDER / DEPOSED
  HouseAmbition.java     NONE / CONSOLIDATE_STAKE / DISPLACE_RIVAL /
                         PROMOTE / CLAIM_THRONE
  ChainArchetype.java    CONSOLIDATE_STAKE / SABOTAGE_PROMOTION /
                         ELEVATE_HEIR / CIVIL_WAR
  IdRegistry.java        string → int, persistent, append-only
  CampaignState.java     five SoA tables (houses / stakes / relationships /
                         chains / playerReputation) + 3 IdRegistries
                         (faction, industry, market)
  CampaignStateScript.java  EveryFrameScript holding CampaignState,
                            daily-tick scaffold (5 phases, all stubs)
  HouseSeeder.java       1 T1 per market + 1 T2 per inhabited system,
                         flavor deterministic from id hash
```

### Design correction caught while coding

`mechanics.md` had `marketId` as `long`, treating it as "vanilla
addressable." Vanilla market ids are *strings*. Fixed during the
`CampaignState` write: added a third `marketRegistry: IdRegistry`,
flipped `marketId` columns to `int`, updated the doc and the
[`../../mechanics.md`](../../mechanics.md) doc.

## Architecture commitments — [`architecture.md`](../../architecture.md)

Baked the architectural principles in *before* systems started landing.
Four commitments, each framed with "cost of not honoring" so they're
easy to defend in review:

- **§1 SoA in primitive arrays** — no boxing in row data; soft-delete
  only (compaction would invalidate every cached index).
- **§2 Behavior in Systems, not on the data class** — `CampaignState`
  holds data + low-level mutators; tick-time behavior lives in
  pluggable `CampaignSystem` classes.
- **§3 Read/write declaration for future parallelization** — every
  System declares its `reads()` / `writes()` via `CampaignTable`. The
  scheduler runs serially today, but the declaration is cheap now and
  expensive to retrofit later.
- **§4 O(1) lookup by id, both directions** — every primary key has a
  registry path back to its row index; linear scans for row lookup
  are banned.

This is the doc every future campaign-tier system author reads first.

## Systems framework

```
src/main/java/com/dillon/starsectormarines/campaign/
  CampaignTable.java        enum HOUSES / STAKES / RELATIONSHIPS /
                            CHAINS / PLAYER_REP — referenced by
                            System.reads() / writes() declarations.
  CampaignSystem.java       interface: name(), reads(), writes(),
                            tick(state, day). Pure-behavior contract.

src/main/java/com/dillon/starsectormarines/campaign/systems/  (new)
  AutonomousPromotionSystem.java       phase 1 stub
  RelationshipInteractionSystem.java   phase 2 stub (weekly cadence)
  ChainAdvancementSystem.java          phase 3 stub
  GarrisonDefaultSystem.java           phase 4 stub
  DiscoveryPropagationSystem.java      phase 5 stub

src/main/java/com/dillon/starsectormarines/campaign/CampaignStateScript.java
  Refactored from "five empty phase comments" to "walk a
  transient List<CampaignSystem>". State persists; systems are
  rebuilt from defaultSystems() on every game load. Order is the
  registration order in defaultSystems().
```

Each stub System has its reads/writes declared and a `tick()` body
that's a TODO comment. As actual behavior wires in, the System list is
the canonical entry point — no special-case threading through the
script.

## O(1) id ↔ index lookups

```
src/main/java/com/dillon/starsectormarines/campaign/LongIntMap.java  (new)
  Open-addressed primitive long→int hash with linear probing.
  Append-only (matches our soft-delete invariant). Splitmix64 mixer
  for distribution. ~60 LoC. Serializable for xstream persistence.

src/main/java/com/dillon/starsectormarines/campaign/CampaignState.java
  + houseIndexById     LongIntMap   houseId → row index
  + stakeIndexById     LongIntMap   stakeId → row index
  + chainIndexById     LongIntMap   chainId → row index
  + repIndexByHouseId  LongIntMap   houseId → playerReputation row
  Maintained on every addX() mutator.
  houseIndex() / stakeIndex() / chainIndex() / repIndex() — all O(1).
  ensureRepRow() now hits the map instead of linear-scanning.

src/test/java/com/dillon/starsectormarines/campaign/LongIntMapTest.java  (new)
  8 cases: put/get round-trip, missing key, zero-key rejection,
  overwrite semantics, growth past initial capacity (1000 keys into
  cap-4 map), negative keys, clear, collision resolution via probing.
```

## Debug intel — playtest forcing functions

```
src/main/java/com/dillon/starsectormarines/intel/
  CampaignDebugIntel.java  dev-only intel, sorts to bottom of intel list.
    - Counters (house/stake/chain/rep/registry sizes)
    - Bypass-house-gating toggle (runtime, persisted on CampaignState)
    - Reseed button (with confirm dialog)
    - Per-house row: rank/flavor/status/faction/market + Promote/Demote
    - wipeHouses() clears houseIndexById too — without this, reseed
      would leave stale entries pointing at out-of-bounds row indices

src/main/java/com/dillon/starsectormarines/DevConfig.java
  + CAMPAIGN_DEBUG_INTEL  (gates intel registration)
  + BYPASS_HOUSE_GATING   (compile-time forward-decl; no consumers yet)

src/main/java/com/dillon/starsectormarines/StarsectorMarinesModPlugin.java
  + ensureCampaignState()       — registers script, seeds if empty
  + ensureCampaignDebugIntel()  — gated on DevConfig.CAMPAIGN_DEBUG_INTEL
```

User callout that motivated this: "we're going to need ways to debug
enable all missions and enter specific states for playtesting." The
debug intel hangs off `IntelManagerAPI` (separate entry from
BridgeIntel — easier to gate/remove for prod) and gives a concrete
view of the SoA state, a mutator surface for promote/demote/reseed,
and a toggle that future `MissionGenerator` campaign-tier gating can
branch on for end-to-end playtesting.

The debug intel is fully gated by `DevConfig.CAMPAIGN_DEBUG_INTEL`.
The underlying `CampaignStateScript` runs unconditionally (no harm in
having the data live; it persists either way).

## What the skeleton does NOT do yet

- **No stake seeding.** Every house starts owning zero industries;
  faction baseline owns 100% of every market. Stakes accrue via
  contract resolution (not wired here — that's
  [`contracts-loop.md`](../../contracts/complete/contracts-loop.md)).
- **No relationship edges.** Visibility computation is implemented in
  `mechanics.md` only; no code reads `relationships[]` yet.
- **No autonomous tick.** `onDailyTick` is five stub comments —
  promotion, interactions, chain advancement, defaults, discovery all
  pending.
- **No `MissionGenerator` campaign-tier gating.** `BYPASS_HOUSE_GATING`
  is a forward declaration with zero consumers.
- **No hidden-pretender layer.** `HIDDEN_PRETENDER` / `DEPOSED` exist
  as enum values; nothing creates rows in those states yet.

## Sanity check

- `gradlew.bat build` → BUILD SUCCESSFUL.
- `gradlew.bat :test --tests LongIntMapTest` → 8/8 pass.
- The campaign package has zero callers outside the mod plugin (script
  lives on the sector, intel surfaces state), so no integration risk
  at this stage.
