package com.dillon.starsectormarines.campaign;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Campaign-tier data model: five structure-of-arrays tables backed by Java
 * primitive arrays, persisted via xstream (no SQLite — see
 * <code>roadmap/campaign/mechanics.md</code> for the rationale).
 *
 * <p>This is a thin data container. The simulation loop lives in
 * {@link CampaignStateScript}; seeding lives in {@link HouseSeeder}; UI hangs
 * off the debug intel. Mutators here are intentionally low-level — they just
 * append rows and grow arrays. The higher-level "transfer N stake from A to B"
 * operations belong on top of this class, not inside it.
 *
 * <p>Removal is soft: status flags ({@code DORMANT} / {@code DEPOSED}) replace
 * row deletion so house ids stay stable indices forever. Active counts are
 * tracked separately if needed.
 *
 * <h2>Field naming</h2>
 * Each table is a set of parallel primitive arrays {@code <table><Field>[]}.
 * E.g. row {@code i} of the houses table is read as
 * {@code (houseId[i], houseMarketId[i], houseFactionId[i], houseFlavor[i], ...)}.
 */
public final class CampaignState implements Serializable {

    private static final int INITIAL_CAPACITY = 16;

    // ---------- Id registries (interned vanilla strings) ----------

    public final IdRegistry factionRegistry = new IdRegistry();
    public final IdRegistry industryRegistry = new IdRegistry();
    public final IdRegistry marketRegistry = new IdRegistry();

    // ---------- houses[] ----------

    public long[] houseId           = new long[INITIAL_CAPACITY];
    public int[]  houseMarketId     = new int[INITIAL_CAPACITY];
    public int[]  houseFactionId    = new int[INITIAL_CAPACITY];
    public byte[] houseFlavor       = new byte[INITIAL_CAPACITY];
    public byte[] houseRank         = new byte[INITIAL_CAPACITY];
    public byte[] houseStatus       = new byte[INITIAL_CAPACITY];
    public byte[] houseAmbition     = new byte[INITIAL_CAPACITY];
    public long[] houseAmbitionTarget = new long[INITIAL_CAPACITY];
    public short[] housePromotionProgress = new short[INITIAL_CAPACITY];
    public int[]  housePower        = new int[INITIAL_CAPACITY];
    public long[] houseClaimAgainst = new long[INITIAL_CAPACITY];
    public String[] houseDisplayName = new String[INITIAL_CAPACITY];
    public int    houseCount        = 0;

    // ---------- stakes[] ----------

    public long[] stakeId         = new long[INITIAL_CAPACITY];
    public long[] stakeHouseId    = new long[INITIAL_CAPACITY];
    public int[]  stakeMarketId   = new int[INITIAL_CAPACITY];
    public int[]  stakeIndustryId = new int[INITIAL_CAPACITY];
    public short[] stakeShare     = new short[INITIAL_CAPACITY]; // 0..255; short for room to grow
    public int    stakeCount      = 0;

    // ---------- relationships[] ----------

    public long[] relHouseA              = new long[INITIAL_CAPACITY];
    public long[] relHouseB              = new long[INITIAL_CAPACITY];
    public byte[] relAffinity            = new byte[INITIAL_CAPACITY]; // -128..127
    public int[]  relLastInteractionTick = new int[INITIAL_CAPACITY];
    public int    relCount               = 0;

    // ---------- chains[] ----------

    public long[]  chainId           = new long[INITIAL_CAPACITY];
    public long[]  chainPatron       = new long[INITIAL_CAPACITY]; // -1 = autonomous
    public long[]  chainTarget       = new long[INITIAL_CAPACITY];
    public byte[]  chainTier         = new byte[INITIAL_CAPACITY];
    public byte[]  chainArchetype    = new byte[INITIAL_CAPACITY];
    public short[] chainProgress     = new short[INITIAL_CAPACITY];
    public short[] chainThreshold    = new short[INITIAL_CAPACITY];
    public byte[]  chainDiscoveryRisk = new byte[INITIAL_CAPACITY];
    public int[]   chainInitiatedTick = new int[INITIAL_CAPACITY];
    public int     chainCount        = 0;

    // ---------- playerReputation[] ----------

    public long[]  repHouseId         = new long[INITIAL_CAPACITY];
    public int[]   repValue           = new int[INITIAL_CAPACITY]; // -100..100
    public short[] repContractsCompleted = new short[INITIAL_CAPACITY];
    public short[] repContractsFailed = new short[INITIAL_CAPACITY];
    public int[]   repLastContractTick = new int[INITIAL_CAPACITY];
    public int     repCount           = 0;

    // ---------- O(1) id → row-index maps (architecture.md §4) ----------

    public final LongIntMap houseIndexById  = new LongIntMap();
    public final LongIntMap stakeIndexById  = new LongIntMap();
    public final LongIntMap chainIndexById  = new LongIntMap();
    /** house id → row index in {@code playerReputation[]}. Sparse — only touched houses get rep rows. */
    public final LongIntMap repIndexByHouseId = new LongIntMap();

    // ---------- Sequence counters ----------

    private long nextHouseId  = 1;
    private long nextStakeId  = 1;
    private long nextChainId  = 1;

    /** Last advanced sector-day; the script uses this to drive a daily-tick cadence. */
    public int lastTickDay = -1;

    // ---------- Debug overrides (not persisted intentionally? keep persisted — small) ----------

    /** When true, mission generators ignore campaign-tier gating (rep, rank, flavor). Debug only. */
    public boolean debugBypassHouseGating = false;

    // ---------- Mutators ----------

    /** Appends a house. Returns the new house id. */
    public long addHouse(int marketId, int factionId, HouseFlavor flavor, HouseRank rank,
                        HouseStatus status, String displayName) {
        ensureHouseCapacity(houseCount + 1);
        int i = houseCount++;
        long id = nextHouseId++;
        houseId[i] = id;
        houseMarketId[i] = marketId;
        houseFactionId[i] = factionId;
        houseFlavor[i] = flavor.toByte();
        houseRank[i] = rank.toByte();
        houseStatus[i] = status.toByte();
        houseAmbition[i] = HouseAmbition.NONE.toByte();
        houseAmbitionTarget[i] = -1L;
        housePromotionProgress[i] = 0;
        housePower[i] = 0;
        houseClaimAgainst[i] = -1L;
        houseDisplayName[i] = displayName;
        houseIndexById.put(id, i);
        return id;
    }

    /** O(1) lookup: house id → row index in houses table, or {@code -1}. */
    public int houseIndex(long id) {
        return houseIndexById.get(id);
    }

    /** Appends a stake row. Returns the new stake id. */
    public long addStake(long houseId, int marketId, int industryId, short share) {
        ensureStakeCapacity(stakeCount + 1);
        int i = stakeCount++;
        long id = nextStakeId++;
        stakeId[i] = id;
        stakeHouseId[i] = houseId;
        stakeMarketId[i] = marketId;
        stakeIndustryId[i] = industryId;
        stakeShare[i] = share;
        stakeIndexById.put(id, i);
        return id;
    }

    /** O(1) lookup: stake id → row index in stakes table, or {@code -1}. */
    public int stakeIndex(long id) {
        return stakeIndexById.get(id);
    }

    /** Appends a relationship edge. Caller is responsible for visibility-gating. */
    public void addRelationship(long a, long b, byte affinity, int tick) {
        ensureRelCapacity(relCount + 1);
        int i = relCount++;
        relHouseA[i] = a;
        relHouseB[i] = b;
        relAffinity[i] = affinity;
        relLastInteractionTick[i] = tick;
    }

    /** Appends a chain. Returns the new chain id. */
    public long addChain(long patron, long target, byte tier, ChainArchetype archetype,
                         short threshold, byte discoveryRisk, int initiatedTick) {
        ensureChainCapacity(chainCount + 1);
        int i = chainCount++;
        long id = nextChainId++;
        chainId[i] = id;
        chainPatron[i] = patron;
        chainTarget[i] = target;
        chainTier[i] = tier;
        chainArchetype[i] = archetype.toByte();
        chainProgress[i] = 0;
        chainThreshold[i] = threshold;
        chainDiscoveryRisk[i] = discoveryRisk;
        chainInitiatedTick[i] = initiatedTick;
        chainIndexById.put(id, i);
        return id;
    }

    /** O(1) lookup: chain id → row index in chains table, or {@code -1}. */
    public int chainIndex(long id) {
        return chainIndexById.get(id);
    }

    /** Finds or creates a rep row for the given house id. Returns the row index. */
    public int ensureRepRow(long houseIdValue) {
        int existing = repIndexByHouseId.get(houseIdValue);
        if (existing != LongIntMap.NOT_FOUND) return existing;
        ensureRepCapacity(repCount + 1);
        int i = repCount++;
        repHouseId[i] = houseIdValue;
        repValue[i] = 0;
        repContractsCompleted[i] = 0;
        repContractsFailed[i] = 0;
        repLastContractTick[i] = 0;
        repIndexByHouseId.put(houseIdValue, i);
        return i;
    }

    /** O(1) lookup: house id → row index in playerReputation table, or {@code -1} if no rep row exists. */
    public int repIndex(long houseIdValue) {
        return repIndexByHouseId.get(houseIdValue);
    }

    // ---------- Capacity growth ----------

    private void ensureHouseCapacity(int needed) {
        if (needed <= houseId.length) return;
        int n = Math.max(needed, houseId.length * 2);
        houseId               = Arrays.copyOf(houseId, n);
        houseMarketId         = Arrays.copyOf(houseMarketId, n);
        houseFactionId        = Arrays.copyOf(houseFactionId, n);
        houseFlavor           = Arrays.copyOf(houseFlavor, n);
        houseRank             = Arrays.copyOf(houseRank, n);
        houseStatus           = Arrays.copyOf(houseStatus, n);
        houseAmbition         = Arrays.copyOf(houseAmbition, n);
        houseAmbitionTarget   = Arrays.copyOf(houseAmbitionTarget, n);
        housePromotionProgress = Arrays.copyOf(housePromotionProgress, n);
        housePower            = Arrays.copyOf(housePower, n);
        houseClaimAgainst     = Arrays.copyOf(houseClaimAgainst, n);
        houseDisplayName      = Arrays.copyOf(houseDisplayName, n);
    }

    private void ensureStakeCapacity(int needed) {
        if (needed <= stakeId.length) return;
        int n = Math.max(needed, stakeId.length * 2);
        stakeId         = Arrays.copyOf(stakeId, n);
        stakeHouseId    = Arrays.copyOf(stakeHouseId, n);
        stakeMarketId   = Arrays.copyOf(stakeMarketId, n);
        stakeIndustryId = Arrays.copyOf(stakeIndustryId, n);
        stakeShare      = Arrays.copyOf(stakeShare, n);
    }

    private void ensureRelCapacity(int needed) {
        if (needed <= relHouseA.length) return;
        int n = Math.max(needed, relHouseA.length * 2);
        relHouseA              = Arrays.copyOf(relHouseA, n);
        relHouseB              = Arrays.copyOf(relHouseB, n);
        relAffinity            = Arrays.copyOf(relAffinity, n);
        relLastInteractionTick = Arrays.copyOf(relLastInteractionTick, n);
    }

    private void ensureChainCapacity(int needed) {
        if (needed <= chainId.length) return;
        int n = Math.max(needed, chainId.length * 2);
        chainId            = Arrays.copyOf(chainId, n);
        chainPatron        = Arrays.copyOf(chainPatron, n);
        chainTarget        = Arrays.copyOf(chainTarget, n);
        chainTier          = Arrays.copyOf(chainTier, n);
        chainArchetype     = Arrays.copyOf(chainArchetype, n);
        chainProgress      = Arrays.copyOf(chainProgress, n);
        chainThreshold     = Arrays.copyOf(chainThreshold, n);
        chainDiscoveryRisk = Arrays.copyOf(chainDiscoveryRisk, n);
        chainInitiatedTick = Arrays.copyOf(chainInitiatedTick, n);
    }

    private void ensureRepCapacity(int needed) {
        if (needed <= repHouseId.length) return;
        int n = Math.max(needed, repHouseId.length * 2);
        repHouseId            = Arrays.copyOf(repHouseId, n);
        repValue              = Arrays.copyOf(repValue, n);
        repContractsCompleted = Arrays.copyOf(repContractsCompleted, n);
        repContractsFailed    = Arrays.copyOf(repContractsFailed, n);
        repLastContractTick   = Arrays.copyOf(repLastContractTick, n);
    }
}
