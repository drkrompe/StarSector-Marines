package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.DebugOnly;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Campaign-tier data model: structure-of-arrays tables backed by Java primitive
 * arrays, persisted via xstream (no SQLite — see
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
    /** Captain ids interned for the {@code contractCaptainId[]} column — UUID strings → ints. */
    public final IdRegistry captainRegistry = new IdRegistry();

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
    /** Last sector day weekly stake drift evaluated; -1 until the first cadence tick. */
    public int[]  houseLastDriftTick = filledInts(INITIAL_CAPACITY, -1);
    public long[] houseClaimAgainst = new long[INITIAL_CAPACITY];
    public byte[] houseArchetype    = new byte[INITIAL_CAPACITY];
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
    /** House that hired the player, or -1 for an autonomous chain. */
    public long[]  chainPatron       = filledLongs(INITIAL_CAPACITY, -1L);
    /** House politically pursuing the chain outcome, including autonomous chains. */
    public long[]  chainActorHouseId = filledLongs(INITIAL_CAPACITY, -1L);
    public long[]  chainTarget       = new long[INITIAL_CAPACITY];
    /** Market registry slot where the chain operates, or -1 when not location-bound. */
    public int[]   chainMarketId     = filledInts(INITIAL_CAPACITY, -1);
    /** Industry registry slot where the chain operates, or -1 when not industry-bound. */
    public int[]   chainIndustryId   = filledInts(INITIAL_CAPACITY, -1);
    public byte[]  chainTier         = new byte[INITIAL_CAPACITY];
    public byte[]  chainArchetype    = new byte[INITIAL_CAPACITY];
    public byte[]  chainState        = new byte[INITIAL_CAPACITY];
    public short[] chainProgress     = new short[INITIAL_CAPACITY];
    public short[] chainThreshold    = new short[INITIAL_CAPACITY];
    public byte[]  chainDiscoveryRisk = new byte[INITIAL_CAPACITY];
    public int[]   chainInitiatedTick = new int[INITIAL_CAPACITY];
    /** Last day autonomous advancement evaluated this row; -1 until first evaluation. */
    public int[]   chainLastAdvanceTick = filledInts(INITIAL_CAPACITY, -1);
    /** Day the chain reached a terminal state; -1 while active. */
    public int[]   chainResolvedTick = filledInts(INITIAL_CAPACITY, -1);
    /** Day Chronicle discovery classified this terminal row; -1 until processed. */
    public int[]   chainDiscoveryProcessedTick = filledInts(INITIAL_CAPACITY, -1);
    /** Last day an active-rumor discovery window evaluated this row; -1 until checked. */
    public int[]   chainLastDiscoveryCheckTick = filledInts(INITIAL_CAPACITY, -1);
    /** Day an active-chain rumor was learned; -1 while undiscovered. */
    public int[]   chainDiscoveredTick = filledInts(INITIAL_CAPACITY, -1);
    public int     chainCount        = 0;

    // ---------- playerReputation[] ----------

    public long[]  repHouseId         = new long[INITIAL_CAPACITY];
    public int[]   repValue           = new int[INITIAL_CAPACITY]; // -100..100
    public short[] repContractsCompleted = new short[INITIAL_CAPACITY];
    public short[] repContractsFailed = new short[INITIAL_CAPACITY];
    public int[]   repLastContractTick = new int[INITIAL_CAPACITY];
    public int     repCount           = 0;

    // ---------- chronicle[] (learned events only) ----------

    public long[] chronicleId = new long[INITIAL_CAPACITY];
    public byte[] chronicleEventType = new byte[INITIAL_CAPACITY];
    public long[] chronicleSourceChainId = filledLongs(INITIAL_CAPACITY, -1L);
    /** {@link ChainState} captured when this dispatch was learned. */
    public byte[] chronicleChainOutcome = new byte[INITIAL_CAPACITY];
    public byte[] chronicleBand = new byte[INITIAL_CAPACITY];
    public byte[] chronicleConfidence = new byte[INITIAL_CAPACITY];
    public long[] chronicleActorHouseId = filledLongs(INITIAL_CAPACITY, -1L);
    public long[] chronicleTargetHouseId = filledLongs(INITIAL_CAPACITY, -1L);
    public int[] chronicleMarketId = filledInts(INITIAL_CAPACITY, -1);
    public int[] chronicleIndustryId = filledInts(INITIAL_CAPACITY, -1);
    public int[] chronicleHappenedTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] chronicleLearnedTick = filledInts(INITIAL_CAPACITY, -1);
    public int chronicleCount = 0;

    // ---------- contracts[] (sixth table — see contracts/overview.md §"contracts[]") ----------

    public long[]  contractId            = new long[INITIAL_CAPACITY];
    public long[]  contractPatronHouseId = new long[INITIAL_CAPACITY];
    /** Target house id for strikes/decapitations; -1 for stationing/escort/extraction. */
    public long[]  contractTargetHouseId = new long[INITIAL_CAPACITY];
    /** Parent chain id, or -1 for one-off contracts. */
    public long[]  contractChainId       = new long[INITIAL_CAPACITY];
    /** Parent contract id for system-generated followups; -1 for ordinary contracts. */
    public long[]  contractSourceContractId = filledLongs(INITIAL_CAPACITY, -1L);
    public byte[]  contractType          = new byte[INITIAL_CAPACITY];
    public byte[]  contractState         = new byte[INITIAL_CAPACITY];
    public int[]   contractAcceptedTick  = new int[INITIAL_CAPACITY];
    /** Sector day when retainer/term ends; -1 for mission-mode (no expiry). */
    public int[]   contractExpiresTick   = new int[INITIAL_CAPACITY];
    /**
     * Sector day this OFFERED contract lapses if not accepted; -1 for non-OFFERED
     * contracts (and for contracts that should never lapse, e.g. debug-spawned).
     * Cleared (set to -1) when an offer is accepted and the row flips to ACTIVE.
     * See {@link PatronArchetype#rollOfferWindowDays} for the per-archetype window.
     */
    public int[]   contractOfferExpiresTick = new int[INITIAL_CAPACITY];
    public byte[]  contractPhasesTotal   = new byte[INITIAL_CAPACITY];
    public byte[]  contractPhasesDone    = new byte[INITIAL_CAPACITY];
    /** Attempts made at the current phase; reset after phase advancement. */
    public int[]   contractPhaseAttempts = new int[INITIAL_CAPACITY];
    /** Earliest day the current assault phase may deploy; -1 when immediately ready. */
    public int[]   contractNextPhaseReadyTick = filledInts(INITIAL_CAPACITY, -1);
    /** Captain index in {@link #captainRegistry}; -1 if no captain bound yet. */
    public int[]   contractCaptainId     = new int[INITIAL_CAPACITY];
    public int[]   contractMarketId      = new int[INITIAL_CAPACITY];
    /** Industry index in {@link #industryRegistry}; -1 if not industry-targeted. */
    public int[]   contractIndustryId    = new int[INITIAL_CAPACITY];
    public int[]   contractBasePayout    = new int[INITIAL_CAPACITY];
    /** Retainer paid per in-game month for stationing contracts; 0 for mission-mode. */
    public int[]   contractRetainerPerMonth = new int[INITIAL_CAPACITY];
    /** Marines removed from player cargo for a stationing contract; 0 for mission-mode. */
    public int[]   contractMarinesCommitted = new int[INITIAL_CAPACITY];
    /** Last day through which stationing retainer was paid; -1 until acceptance. */
    public int[]   contractLastRetainerTick = filledInts(INITIAL_CAPACITY, -1);
    /** Last day through which Cadre training XP was awarded; -1 until acceptance. */
    public int[]   contractLastTrainingTick = filledInts(INITIAL_CAPACITY, -1);
    /** Last monthly stationing-default checkpoint evaluated; -1 for legacy/unassigned rows. */
    public int[]   contractLastDefaultCheckTick = filledInts(INITIAL_CAPACITY, -1);
    /** Next day a Cadre incident becomes due; -1 when unscheduled or already pending. */
    public int[]   contractNextIncidentTick = filledInts(INITIAL_CAPACITY, -1);
    /** 1 while a Cadre incident awaits a player-facing payload; otherwise 0. */
    public byte[]  contractIncidentPending = new byte[INITIAL_CAPACITY];
    /** Persisted {@link StationingIncidentType} for an armed incident; NONE otherwise. */
    public byte[]  contractIncidentType = new byte[INITIAL_CAPACITY];
    /** Stable external event identity for the current/last Garrison defense; 0 means none. */
    public long[]  contractDefenseEventKey = new long[INITIAL_CAPACITY];
    /** Day the current Garrison defense was armed; -1 when absent. */
    public int[]   contractDefenseTriggeredTick = filledInts(INITIAL_CAPACITY, -1);
    /** Persisted {@link GarrisonDefenseTriggerType}; NONE when absent. */
    public byte[]  contractDefenseTriggerType = new byte[INITIAL_CAPACITY];
    /** Attacking house for a rival strike, or -1 for faction/world events. */
    public long[]  contractDefenseAttackerHouseId = filledLongs(INITIAL_CAPACITY, -1L);
    /** Attacking faction registry slot, or -1 when unknown. */
    public int[]   contractDefenseAttackerFactionId = filledInts(INITIAL_CAPACITY, -1);
    /** Salvage % cap for this contract (0..255). Per-type default at offer. */
    public byte[]  contractSalvageBaseline   = new byte[INITIAL_CAPACITY];
    /** Salvage % actually locked in at acceptance (0..salvageBaseline). */
    public byte[]  contractSalvageNegotiated = new byte[INITIAL_CAPACITY];
    /** Cash multiplier (0..255; 100 = baseline). Higher = traded salvage for cash. */
    public byte[]  contractCashMultiplier    = new byte[INITIAL_CAPACITY];
    public int     contractCount         = 0;

    // ---------- O(1) id → row-index maps (architecture.md §4) ----------

    public final LongIntMap houseIndexById     = new LongIntMap();
    public final LongIntMap stakeIndexById     = new LongIntMap();
    public final LongIntMap chainIndexById     = new LongIntMap();
    public final LongIntMap contractIndexById  = new LongIntMap();
    /** house id → row index in {@code playerReputation[]}. Sparse — only touched houses get rep rows. */
    public final LongIntMap repIndexByHouseId  = new LongIntMap();

    // ---------- Sequence counters ----------

    private long nextHouseId    = 1;
    private long nextStakeId    = 1;
    private long nextChainId    = 1;
    private long nextChronicleId = 1;
    private long nextContractId = 1;

    /** Last advanced sector-day; the script uses this to drive a daily-tick cadence. */
    public int lastTickDay = -1;

    /** MRB / industry-credibility rep — see contracts/overview.md §"MRB reputation track". */
    public int playerMrbRep = 0;

    // ---------- Debug overrides (not persisted intentionally? keep persisted — small) ----------

    /** When true, mission generators ignore campaign-tier gating (rep, rank, flavor). Debug only. */
    @DebugOnly
    public boolean debugBypassHouseGating = false;

    // ---------- Mutators ----------

    /** Appends a house. Returns the new house id. */
    public long addHouse(int marketId, int factionId, HouseFlavor flavor, HouseRank rank,
                        HouseStatus status, PatronArchetype archetype, String displayName) {
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
        houseLastDriftTick[i] = -1;
        houseClaimAgainst[i] = -1L;
        houseArchetype[i] = archetype.toByte();
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
        return appendChain(patron, patron, target, -1, -1, tier, archetype,
                threshold, discoveryRisk, initiatedTick);
    }

    /**
     * Appends an autonomous, location-bound chain. The actor is retained even
     * though no player-contract patron exists.
     */
    public long addAutonomousChain(long actorHouseId, long targetHouseId,
                                   int marketId, int industryId,
                                   byte tier, ChainArchetype archetype,
                                   short threshold, byte discoveryRisk, int initiatedTick) {
        return appendChain(-1L, actorHouseId, targetHouseId, marketId, industryId,
                tier, archetype, threshold, discoveryRisk, initiatedTick);
    }

    private long appendChain(long patronHouseId, long actorHouseId, long targetHouseId,
                             int marketId, int industryId,
                             byte tier, ChainArchetype archetype,
                             short threshold, byte discoveryRisk, int initiatedTick) {
        ensureChainCapacity(chainCount + 1);
        int i = chainCount++;
        long id = nextChainId++;
        chainId[i] = id;
        chainPatron[i] = patronHouseId;
        chainActorHouseId[i] = actorHouseId;
        chainTarget[i] = targetHouseId;
        chainMarketId[i] = marketId;
        chainIndustryId[i] = industryId;
        chainTier[i] = tier;
        chainArchetype[i] = archetype.toByte();
        chainState[i] = ChainState.ACTIVE.toByte();
        chainProgress[i] = 0;
        chainThreshold[i] = threshold;
        chainDiscoveryRisk[i] = discoveryRisk;
        chainInitiatedTick[i] = initiatedTick;
        chainLastAdvanceTick[i] = -1;
        chainResolvedTick[i] = -1;
        chainDiscoveryProcessedTick[i] = -1;
        chainLastDiscoveryCheckTick[i] = -1;
        chainDiscoveredTick[i] = -1;
        chainIndexById.put(id, i);
        return id;
    }

    /** O(1) lookup: chain id → row index in chains table, or {@code -1}. */
    public int chainIndex(long id) {
        return chainIndexById.get(id);
    }

    /** Appends a learned chain-outcome dispatch. Returns the Chronicle event id. */
    public long addChronicleChainOutcome(long sourceChainId, ChainState outcome,
                                         ChronicleBand band,
                                         long actorHouseId, long targetHouseId,
                                         int marketId, int industryId,
                                         int happenedTick, int learnedTick) {
        ensureChronicleCapacity(chronicleCount + 1);
        int i = chronicleCount++;
        long id = nextChronicleId++;
        chronicleId[i] = id;
        chronicleEventType[i] = ChronicleEventType.CHAIN_OUTCOME.toByte();
        chronicleSourceChainId[i] = sourceChainId;
        chronicleChainOutcome[i] = outcome.toByte();
        chronicleBand[i] = band.toByte();
        chronicleConfidence[i] = ChronicleConfidence.CONFIRMED.toByte();
        chronicleActorHouseId[i] = actorHouseId;
        chronicleTargetHouseId[i] = targetHouseId;
        chronicleMarketId[i] = marketId;
        chronicleIndustryId[i] = industryId;
        chronicleHappenedTick[i] = happenedTick;
        chronicleLearnedTick[i] = learnedTick;
        return id;
    }

    /** Appends an active-chain rumor snapshot. Returns the Chronicle event id. */
    public long addChronicleChainRumor(long sourceChainId, ChronicleBand band,
                                       long actorHouseId, long targetHouseId,
                                       int marketId, int industryId,
                                       int initiatedTick, int learnedTick) {
        ensureChronicleCapacity(chronicleCount + 1);
        int i = chronicleCount++;
        long id = nextChronicleId++;
        chronicleId[i] = id;
        chronicleEventType[i] = ChronicleEventType.ACTIVE_CHAIN_RUMOR.toByte();
        chronicleSourceChainId[i] = sourceChainId;
        chronicleChainOutcome[i] = ChainState.ACTIVE.toByte();
        chronicleBand[i] = band.toByte();
        chronicleConfidence[i] = ChronicleConfidence.RUMOR.toByte();
        chronicleActorHouseId[i] = actorHouseId;
        chronicleTargetHouseId[i] = targetHouseId;
        chronicleMarketId[i] = marketId;
        chronicleIndustryId[i] = industryId;
        chronicleHappenedTick[i] = initiatedTick;
        chronicleLearnedTick[i] = learnedTick;
        return id;
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

    /**
     * Appends a contract. Returns the new contract id. Salvage / cash columns
     * default to the per-type baseline at the negotiated value; callers should
     * overwrite at acceptance time per <code>contracts/overview.md</code> §"Salvage layers".
     */
    public long addContract(long patronHouseIdValue, long targetHouseIdValue, long chainIdValue,
                            ContractType type, ContractState state, int acceptedTick, int expiresTick,
                            int offerExpiresTick,
                            byte phasesTotal, int captainIdx, int marketIdx, int industryIdx,
                            int basePayout, int retainerPerMonth,
                            byte salvageBaseline, byte salvageNegotiated, byte cashMultiplier) {
        ensureContractCapacity(contractCount + 1);
        int i = contractCount++;
        long id = nextContractId++;
        contractId[i]               = id;
        contractPatronHouseId[i]    = patronHouseIdValue;
        contractTargetHouseId[i]    = targetHouseIdValue;
        contractChainId[i]          = chainIdValue;
        contractSourceContractId[i] = -1L;
        contractType[i]             = type.toByte();
        contractState[i]            = state.toByte();
        contractAcceptedTick[i]     = acceptedTick;
        contractExpiresTick[i]      = expiresTick;
        contractOfferExpiresTick[i] = offerExpiresTick;
        contractPhasesTotal[i]      = phasesTotal;
        contractPhasesDone[i]       = 0;
        contractPhaseAttempts[i]    = 0;
        contractNextPhaseReadyTick[i] = -1;
        contractCaptainId[i]        = captainIdx;
        contractMarketId[i]         = marketIdx;
        contractIndustryId[i]       = industryIdx;
        contractBasePayout[i]       = basePayout;
        contractRetainerPerMonth[i] = retainerPerMonth;
        contractMarinesCommitted[i] = 0;
        contractLastRetainerTick[i] = type.isStationing()
                && state != ContractState.OFFERED ? acceptedTick : -1;
        contractLastTrainingTick[i] = type == ContractType.CADRE
                && state != ContractState.OFFERED ? acceptedTick : -1;
        contractLastDefaultCheckTick[i] = type.isStationing()
                && state != ContractState.OFFERED ? acceptedTick : -1;
        contractNextIncidentTick[i] = -1;
        contractIncidentPending[i] = 0;
        contractIncidentType[i] = StationingIncidentType.NONE.toByte();
        contractDefenseEventKey[i] = 0L;
        contractDefenseTriggeredTick[i] = -1;
        contractDefenseTriggerType[i] = GarrisonDefenseTriggerType.NONE.toByte();
        contractDefenseAttackerHouseId[i] = -1L;
        contractDefenseAttackerFactionId[i] = -1;
        contractSalvageBaseline[i]  = salvageBaseline;
        contractSalvageNegotiated[i] = salvageNegotiated;
        contractCashMultiplier[i]   = cashMultiplier;
        contractIndexById.put(id, i);
        return id;
    }

    /** O(1) lookup: contract id → row index in contracts table, or {@code -1}. */
    public int contractIndex(long id) {
        return contractIndexById.get(id);
    }

    /**
     * Days remaining on an OFFERED contract before it lapses, given the
     * current sector day. Returns {@code -1} for non-OFFERED rows, contracts
     * with no offer expiry (e.g. debug-spawned), and contracts that should
     * have already lapsed (caller should not be displaying these — they
     * tombstone to {@link ContractState#EXPIRED} on the next tick). Bound
     * for the dossier-card days-left bar on the mission-select surface.
     */
    public int contractDaysLeft(int row, int currentDay) {
        if (row < 0 || row >= contractCount) return -1;
        if (ContractState.fromByte(contractState[row]) != ContractState.OFFERED) return -1;
        int expires = contractOfferExpiresTick[row];
        if (expires < 0) return -1;
        int left = expires - currentDay;
        return left < 0 ? 0 : left;
    }

    // ---------- Capacity growth ----------

    private void ensureHouseCapacity(int needed) {
        if (needed <= houseId.length) return;
        int oldLength = houseId.length;
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
        houseLastDriftTick    = Arrays.copyOf(houseLastDriftTick, n);
        Arrays.fill(houseLastDriftTick, oldLength, n, -1);
        houseClaimAgainst     = Arrays.copyOf(houseClaimAgainst, n);
        houseArchetype        = Arrays.copyOf(houseArchetype, n);
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
        int oldLength = chainId.length;
        int n = Math.max(needed, chainId.length * 2);
        chainId            = Arrays.copyOf(chainId, n);
        chainPatron        = Arrays.copyOf(chainPatron, n);
        Arrays.fill(chainPatron, oldLength, n, -1L);
        chainActorHouseId  = Arrays.copyOf(chainActorHouseId, n);
        Arrays.fill(chainActorHouseId, oldLength, n, -1L);
        chainTarget        = Arrays.copyOf(chainTarget, n);
        chainMarketId      = Arrays.copyOf(chainMarketId, n);
        Arrays.fill(chainMarketId, oldLength, n, -1);
        chainIndustryId    = Arrays.copyOf(chainIndustryId, n);
        Arrays.fill(chainIndustryId, oldLength, n, -1);
        chainTier          = Arrays.copyOf(chainTier, n);
        chainArchetype     = Arrays.copyOf(chainArchetype, n);
        chainState         = Arrays.copyOf(chainState, n);
        chainProgress      = Arrays.copyOf(chainProgress, n);
        chainThreshold     = Arrays.copyOf(chainThreshold, n);
        chainDiscoveryRisk = Arrays.copyOf(chainDiscoveryRisk, n);
        chainInitiatedTick = Arrays.copyOf(chainInitiatedTick, n);
        chainLastAdvanceTick = Arrays.copyOf(chainLastAdvanceTick, n);
        Arrays.fill(chainLastAdvanceTick, oldLength, n, -1);
        chainResolvedTick = Arrays.copyOf(chainResolvedTick, n);
        Arrays.fill(chainResolvedTick, oldLength, n, -1);
        chainDiscoveryProcessedTick = Arrays.copyOf(chainDiscoveryProcessedTick, n);
        Arrays.fill(chainDiscoveryProcessedTick, oldLength, n, -1);
        chainLastDiscoveryCheckTick = Arrays.copyOf(chainLastDiscoveryCheckTick, n);
        Arrays.fill(chainLastDiscoveryCheckTick, oldLength, n, -1);
        chainDiscoveredTick = Arrays.copyOf(chainDiscoveredTick, n);
        Arrays.fill(chainDiscoveredTick, oldLength, n, -1);
    }

    private void ensureChronicleCapacity(int needed) {
        if (needed <= chronicleId.length) return;
        int oldLength = chronicleId.length;
        int n = Math.max(needed, chronicleId.length * 2);
        chronicleId = Arrays.copyOf(chronicleId, n);
        chronicleEventType = Arrays.copyOf(chronicleEventType, n);
        chronicleSourceChainId = Arrays.copyOf(chronicleSourceChainId, n);
        Arrays.fill(chronicleSourceChainId, oldLength, n, -1L);
        chronicleChainOutcome = Arrays.copyOf(chronicleChainOutcome, n);
        chronicleBand = Arrays.copyOf(chronicleBand, n);
        chronicleConfidence = Arrays.copyOf(chronicleConfidence, n);
        chronicleActorHouseId = Arrays.copyOf(chronicleActorHouseId, n);
        Arrays.fill(chronicleActorHouseId, oldLength, n, -1L);
        chronicleTargetHouseId = Arrays.copyOf(chronicleTargetHouseId, n);
        Arrays.fill(chronicleTargetHouseId, oldLength, n, -1L);
        chronicleMarketId = Arrays.copyOf(chronicleMarketId, n);
        Arrays.fill(chronicleMarketId, oldLength, n, -1);
        chronicleIndustryId = Arrays.copyOf(chronicleIndustryId, n);
        Arrays.fill(chronicleIndustryId, oldLength, n, -1);
        chronicleHappenedTick = Arrays.copyOf(chronicleHappenedTick, n);
        Arrays.fill(chronicleHappenedTick, oldLength, n, -1);
        chronicleLearnedTick = Arrays.copyOf(chronicleLearnedTick, n);
        Arrays.fill(chronicleLearnedTick, oldLength, n, -1);
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

    /**
     * Backfills {@code houseArchetype} for saves written before that column
     * existed. xstream bypasses the constructor, so any column added after
     * an initial release deserializes as {@code null} on legacy saves and
     * NPEs at first read. Same pattern used by
     * {@code MarineRoster.completedStoryIds}.
     *
     * <p>Backfill is a zero-byte array of the right length — every legacy
     * house reads as {@link PatronArchetype#TIME_RUSHED} (ordinal 0) until
     * the user reseeds via the debug intel. Acceptable for dev playtesting;
     * production seeds populate the column at house-creation time.
     */
    private Object readResolve() {
        if (houseArchetype == null) {
            houseArchetype = new byte[houseId != null ? houseId.length : INITIAL_CAPACITY];
        }
        if (houseLastDriftTick == null) {
            int n = houseId != null ? houseId.length : INITIAL_CAPACITY;
            houseLastDriftTick = filledInts(n, -1);
        }
        if (chainPatron == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainPatron = filledLongs(n, -1L);
        }
        if (chainActorHouseId == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainActorHouseId = filledLongs(n, -1L);
            int existingRows = Math.min(chainCount, chainPatron.length);
            for (int i = 0; i < existingRows; i++) {
                if (chainPatron[i] >= 0L) chainActorHouseId[i] = chainPatron[i];
            }
        }
        if (chainMarketId == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainMarketId = filledInts(n, -1);
        }
        if (chainIndustryId == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainIndustryId = filledInts(n, -1);
        }
        if (chainState == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainState = new byte[n];
        }
        if (chainLastAdvanceTick == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainLastAdvanceTick = filledInts(n, -1);
        }
        if (chainResolvedTick == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainResolvedTick = filledInts(n, -1);
        }
        if (chainDiscoveryProcessedTick == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainDiscoveryProcessedTick = filledInts(n, -1);
        }
        if (chainLastDiscoveryCheckTick == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainLastDiscoveryCheckTick = filledInts(n, -1);
        }
        if (chainDiscoveredTick == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainDiscoveredTick = filledInts(n, -1);
        }
        int chronicleCapacity = chronicleId != null ? chronicleId.length : INITIAL_CAPACITY;
        if (chronicleId == null) chronicleId = new long[chronicleCapacity];
        if (chronicleEventType == null) chronicleEventType = new byte[chronicleCapacity];
        if (chronicleSourceChainId == null) {
            chronicleSourceChainId = filledLongs(chronicleCapacity, -1L);
        }
        if (chronicleChainOutcome == null) chronicleChainOutcome = new byte[chronicleCapacity];
        if (chronicleBand == null) chronicleBand = new byte[chronicleCapacity];
        if (chronicleConfidence == null) chronicleConfidence = new byte[chronicleCapacity];
        if (chronicleActorHouseId == null) {
            chronicleActorHouseId = filledLongs(chronicleCapacity, -1L);
        }
        if (chronicleTargetHouseId == null) {
            chronicleTargetHouseId = filledLongs(chronicleCapacity, -1L);
        }
        if (chronicleMarketId == null) chronicleMarketId = filledInts(chronicleCapacity, -1);
        if (chronicleIndustryId == null) chronicleIndustryId = filledInts(chronicleCapacity, -1);
        if (chronicleHappenedTick == null) {
            chronicleHappenedTick = filledInts(chronicleCapacity, -1);
        }
        if (chronicleLearnedTick == null) {
            chronicleLearnedTick = filledInts(chronicleCapacity, -1);
        }
        if (nextChronicleId <= 0L) {
            nextChronicleId = 1L;
            for (int i = 0; i < chronicleCount; i++) {
                nextChronicleId = Math.max(nextChronicleId, chronicleId[i] + 1L);
            }
        }
        if (contractOfferExpiresTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractOfferExpiresTick = new int[n];
            // Legacy saves predating this column: treat every existing contract as
            // "no offer expiry" so nothing lapses unexpectedly on first load. New
            // offers spawned post-load get the real archetype-driven window.
            Arrays.fill(contractOfferExpiresTick, -1);
        }
        if (contractMarinesCommitted == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractMarinesCommitted = new int[n];
        }
        if (contractLastRetainerTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractLastRetainerTick = filledInts(n, -1);
        }
        if (contractLastTrainingTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractLastTrainingTick = filledInts(n, -1);
        }
        if (contractSourceContractId == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractSourceContractId = filledLongs(n, -1L);
        }
        if (contractLastDefaultCheckTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractLastDefaultCheckTick = filledInts(n, -1);
        }
        if (contractPhaseAttempts == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractPhaseAttempts = new int[n];
        }
        if (contractNextPhaseReadyTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractNextPhaseReadyTick = filledInts(n, -1);
        }
        if (contractNextIncidentTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractNextIncidentTick = filledInts(n, -1);
        }
        if (contractIncidentPending == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractIncidentPending = new byte[n];
        }
        if (contractIncidentType == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractIncidentType = new byte[n];
        }
        if (contractDefenseEventKey == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractDefenseEventKey = new long[n];
        }
        if (contractDefenseTriggeredTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractDefenseTriggeredTick = filledInts(n, -1);
        }
        if (contractDefenseTriggerType == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractDefenseTriggerType = new byte[n];
        }
        if (contractDefenseAttackerHouseId == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractDefenseAttackerHouseId = filledLongs(n, -1L);
        }
        if (contractDefenseAttackerFactionId == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractDefenseAttackerFactionId = filledInts(n, -1);
        }
        return this;
    }

    private void ensureContractCapacity(int needed) {
        if (needed <= contractId.length) return;
        int oldLength = contractId.length;
        int n = Math.max(needed, contractId.length * 2);
        contractId                = Arrays.copyOf(contractId, n);
        contractPatronHouseId     = Arrays.copyOf(contractPatronHouseId, n);
        contractTargetHouseId     = Arrays.copyOf(contractTargetHouseId, n);
        contractChainId           = Arrays.copyOf(contractChainId, n);
        contractSourceContractId  = Arrays.copyOf(contractSourceContractId, n);
        Arrays.fill(contractSourceContractId, oldLength, n, -1L);
        contractType              = Arrays.copyOf(contractType, n);
        contractState             = Arrays.copyOf(contractState, n);
        contractAcceptedTick      = Arrays.copyOf(contractAcceptedTick, n);
        contractExpiresTick       = Arrays.copyOf(contractExpiresTick, n);
        contractOfferExpiresTick  = Arrays.copyOf(contractOfferExpiresTick, n);
        contractPhasesTotal       = Arrays.copyOf(contractPhasesTotal, n);
        contractPhasesDone        = Arrays.copyOf(contractPhasesDone, n);
        contractPhaseAttempts     = Arrays.copyOf(contractPhaseAttempts, n);
        contractNextPhaseReadyTick = Arrays.copyOf(contractNextPhaseReadyTick, n);
        Arrays.fill(contractNextPhaseReadyTick, oldLength, n, -1);
        contractCaptainId         = Arrays.copyOf(contractCaptainId, n);
        contractMarketId          = Arrays.copyOf(contractMarketId, n);
        contractIndustryId        = Arrays.copyOf(contractIndustryId, n);
        contractBasePayout        = Arrays.copyOf(contractBasePayout, n);
        contractRetainerPerMonth  = Arrays.copyOf(contractRetainerPerMonth, n);
        contractMarinesCommitted  = Arrays.copyOf(contractMarinesCommitted, n);
        contractLastRetainerTick  = Arrays.copyOf(contractLastRetainerTick, n);
        Arrays.fill(contractLastRetainerTick, oldLength, n, -1);
        contractLastTrainingTick  = Arrays.copyOf(contractLastTrainingTick, n);
        Arrays.fill(contractLastTrainingTick, oldLength, n, -1);
        contractLastDefaultCheckTick = Arrays.copyOf(contractLastDefaultCheckTick, n);
        Arrays.fill(contractLastDefaultCheckTick, oldLength, n, -1);
        contractNextIncidentTick = Arrays.copyOf(contractNextIncidentTick, n);
        Arrays.fill(contractNextIncidentTick, oldLength, n, -1);
        contractIncidentPending = Arrays.copyOf(contractIncidentPending, n);
        contractIncidentType = Arrays.copyOf(contractIncidentType, n);
        contractDefenseEventKey = Arrays.copyOf(contractDefenseEventKey, n);
        contractDefenseTriggeredTick = Arrays.copyOf(contractDefenseTriggeredTick, n);
        Arrays.fill(contractDefenseTriggeredTick, oldLength, n, -1);
        contractDefenseTriggerType = Arrays.copyOf(contractDefenseTriggerType, n);
        contractDefenseAttackerHouseId = Arrays.copyOf(contractDefenseAttackerHouseId, n);
        Arrays.fill(contractDefenseAttackerHouseId, oldLength, n, -1L);
        contractDefenseAttackerFactionId = Arrays.copyOf(contractDefenseAttackerFactionId, n);
        Arrays.fill(contractDefenseAttackerFactionId, oldLength, n, -1);
        contractSalvageBaseline   = Arrays.copyOf(contractSalvageBaseline, n);
        contractSalvageNegotiated = Arrays.copyOf(contractSalvageNegotiated, n);
        contractCashMultiplier    = Arrays.copyOf(contractCashMultiplier, n);
    }

    private static int[] filledInts(int length, int value) {
        int[] out = new int[length];
        Arrays.fill(out, value);
        return out;
    }

    private static long[] filledLongs(int length, long value) {
        long[] out = new long[length];
        Arrays.fill(out, value);
        return out;
    }
}
