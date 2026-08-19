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
    /** Last sector day long-horizon ambition transitions were reviewed; -1 until first review. */
    public int[]  houseLastAmbitionReviewTick = filledInts(INITIAL_CAPACITY, -1);
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
    /** Player side locked by the first completed civil-war participation contract. */
    public byte[]  chainPlayerAllegiance = new byte[INITIAL_CAPACITY];
    /** Saturating total weight of successful player civil-war operations. */
    public short[] chainPlayerContribution = new short[INITIAL_CAPACITY];
    /** Last day a player civil-war contribution applied; -1 until untouched. */
    public int[]   chainPlayerLastContributionTick = filledInts(INITIAL_CAPACITY, -1);
    /** Terminal incumbent-victory player-reputation lifecycle. */
    public byte[]  chainPlayerConsequenceState = new byte[INITIAL_CAPACITY];
    /** Day terminal incumbent-victory reputation applied; -1 otherwise. */
    public int[]   chainPlayerConsequenceAppliedTick = filledInts(INITIAL_CAPACITY, -1);
    public int     chainCount        = 0;

    // ---------- playerReputation[] ----------

    public long[]  repHouseId         = new long[INITIAL_CAPACITY];
    public int[]   repValue           = new int[INITIAL_CAPACITY]; // -100..100
    public short[] repContractsCompleted = new short[INITIAL_CAPACITY];
    public short[] repContractsFailed = new short[INITIAL_CAPACITY];
    public int[]   repLastContractTick = new int[INITIAL_CAPACITY];
    public int     repCount           = 0;

    // ---------- moralChoices[] (hidden player-character record) ----------

    /** Ruthless (-100) to merciful (+100). Never rendered numerically. */
    public int moralMercy = 0;
    /** Expedient (-100) to principled (+100). Never rendered numerically. */
    public int moralIntegrity = 0;
    /** Exploitative (-100) to protective (+100). Never rendered numerically. */
    public int moralStewardship = 0;
    /** Insurgent (-100) to establishment (+100). Never rendered numerically. */
    public int moralInstitutionalism = 0;
    public long[] moralChoiceId = new long[INITIAL_CAPACITY];
    public byte[] moralChoiceSourceType = new byte[INITIAL_CAPACITY];
    public long[] moralChoiceSourceId = filledLongs(INITIAL_CAPACITY, -1L);
    public short[] moralChoiceMercyDelta = new short[INITIAL_CAPACITY];
    public short[] moralChoiceIntegrityDelta = new short[INITIAL_CAPACITY];
    public short[] moralChoiceStewardshipDelta = new short[INITIAL_CAPACITY];
    public short[] moralChoiceInstitutionalismDelta = new short[INITIAL_CAPACITY];
    public int[] moralChoiceHappenedTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] moralChoiceRecordedTick = filledInts(INITIAL_CAPACITY, -1);
    public int moralChoiceCount = 0;

    // ---------- campaignEvents[] (black-swan choices; not contracts) ----------

    public long[] eventId = new long[INITIAL_CAPACITY];
    public byte[] eventType = new byte[INITIAL_CAPACITY];
    public long[] eventTriggerKey = filledLongs(INITIAL_CAPACITY, -1L);
    public byte[] eventState = new byte[INITIAL_CAPACITY];
    public int[] eventMarketId = filledInts(INITIAL_CAPACITY, -1);
    public int[] eventCreatedTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] eventDeadlineTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] eventDecisionTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] eventResolvedTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] eventSuppliesRequired = new int[INITIAL_CAPACITY];
    public int[] eventFuelRequired = new int[INITIAL_CAPACITY];
    public int[] eventCiviliansAtRisk = new int[INITIAL_CAPACITY];
    public int[] eventCiviliansRescued = new int[INITIAL_CAPACITY];
    public long[] eventSourceChainId = filledLongs(INITIAL_CAPACITY, -1L);
    public long[] eventActorHouseId = filledLongs(INITIAL_CAPACITY, -1L);
    public long[] eventTargetHouseId = filledLongs(INITIAL_CAPACITY, -1L);
    public int[] eventFollowupTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] eventFollowupDeadlineTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] eventCreditsOffered = new int[INITIAL_CAPACITY];
    public byte[] eventDefectorOutcome = new byte[INITIAL_CAPACITY];
    public int eventCount = 0;

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
    /** Source faction snapshot for faction-flip dispatches; -1 otherwise. */
    public int[] chronicleSourceFactionId = filledInts(INITIAL_CAPACITY, -1);
    /** Result faction snapshot for faction-flip dispatches; -1 otherwise. */
    public int[] chronicleResultFactionId = filledInts(INITIAL_CAPACITY, -1);
    /** Kingmaker testament linked by this dispatch; -1 for other event types. */
    public long[] chronicleTestamentId = filledLongs(INITIAL_CAPACITY, -1L);
    public int[] chronicleHappenedTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] chronicleLearnedTick = filledInts(INITIAL_CAPACITY, -1);
    public int chronicleCount = 0;

    // ---------- throneClaims[] (Tier-3 endgame handoff; no vanilla writes here) ----------

    public long[] throneClaimId = new long[INITIAL_CAPACITY];
    public long[] throneClaimSourceChainId = filledLongs(INITIAL_CAPACITY, -1L);
    public long[] throneClaimHouseId = filledLongs(INITIAL_CAPACITY, -1L);
    public int[] throneClaimSourceFactionId = filledInts(INITIAL_CAPACITY, -1);
    public int[] throneClaimResultFactionId = filledInts(INITIAL_CAPACITY, -1);
    public int[] throneClaimMarketId = filledInts(INITIAL_CAPACITY, -1);
    /** Player allegiance captured from the source civil war at preparation. */
    public byte[] throneClaimPlayerAllegiance = new byte[INITIAL_CAPACITY];
    /** Player contribution total captured from the source civil war. */
    public short[] throneClaimPlayerContribution = new short[INITIAL_CAPACITY];
    /** Last player contribution day captured from the source civil war. */
    public int[] throneClaimPlayerLastContributionTick =
            filledInts(INITIAL_CAPACITY, -1);
    /** Terminal claimant-victory player-reputation lifecycle. */
    public byte[] throneClaimPlayerConsequenceState = new byte[INITIAL_CAPACITY];
    /** Day terminal claimant-victory reputation applied; -1 otherwise. */
    public int[] throneClaimPlayerConsequenceAppliedTick =
            filledInts(INITIAL_CAPACITY, -1);
    public byte[] throneClaimState = new byte[INITIAL_CAPACITY];
    public int[] throneClaimPreparedTick = filledInts(INITIAL_CAPACITY, -1);
    public int[] throneClaimAppliedTick = filledInts(INITIAL_CAPACITY, -1);
    /** Post-writeback diplomacy lifecycle; independent from ownership application. */
    public byte[] throneClaimConsequenceState = new byte[INITIAL_CAPACITY];
    /** Day diplomacy consequences completed; -1 while pending or failed. */
    public int[] throneClaimConsequenceAppliedTick = filledInts(INITIAL_CAPACITY, -1);
    public int throneClaimCount = 0;

    // ---------- kingmakerTestaments[] (immutable T3 moral-capstone snapshots) ----------

    public long[] kingmakerTestamentId = new long[INITIAL_CAPACITY];
    public long[] kingmakerTestamentThroneClaimId =
            filledLongs(INITIAL_CAPACITY, -1L);
    public long[] kingmakerTestamentSourceChainId =
            filledLongs(INITIAL_CAPACITY, -1L);
    public long[] kingmakerTestamentClaimantHouseId =
            filledLongs(INITIAL_CAPACITY, -1L);
    public long[] kingmakerTestamentDeposedHouseId =
            filledLongs(INITIAL_CAPACITY, -1L);
    public int[] kingmakerTestamentSourceFactionId =
            filledInts(INITIAL_CAPACITY, -1);
    public int[] kingmakerTestamentResultFactionId =
            filledInts(INITIAL_CAPACITY, -1);
    public int[] kingmakerTestamentMarketId = filledInts(INITIAL_CAPACITY, -1);
    public short[] kingmakerTestamentPlayerContribution =
            new short[INITIAL_CAPACITY];
    /** Hidden axis snapshots; never rendered numerically. */
    public int[] kingmakerTestamentMercy = new int[INITIAL_CAPACITY];
    public int[] kingmakerTestamentIntegrity = new int[INITIAL_CAPACITY];
    public int[] kingmakerTestamentStewardship = new int[INITIAL_CAPACITY];
    public int[] kingmakerTestamentInstitutionalism = new int[INITIAL_CAPACITY];
    /** Exclusive upper ledger-row boundary available when this snapshot was sealed. */
    public int[] kingmakerTestamentMoralChoiceCount =
            filledInts(INITIAL_CAPACITY, -1);
    public int[] kingmakerTestamentSealedTick = filledInts(INITIAL_CAPACITY, -1);
    public byte[] kingmakerTestamentState = new byte[INITIAL_CAPACITY];
    public int kingmakerTestamentCount = 0;

    // ---------- contracts[] (sixth table — see contracts/overview.md §"contracts[]") ----------

    public long[]  contractId            = new long[INITIAL_CAPACITY];
    public long[]  contractPatronHouseId = new long[INITIAL_CAPACITY];
    /** Target house id for strikes/decapitations; -1 for stationing/escort/extraction. */
    public long[]  contractTargetHouseId = new long[INITIAL_CAPACITY];
    /** Parent chain id, or -1 for one-off contracts. */
    public long[]  contractChainId       = new long[INITIAL_CAPACITY];
    /** Hostile chain this intervention opposes, or -1 for ordinary contracts. */
    public long[]  contractOpposedChainId = filledLongs(INITIAL_CAPACITY, -1L);
    /** Civil-war phase captured at offer creation; {@link CivilWarBand#NONE} otherwise. */
    public byte[]  contractCivilWarBand = new byte[INITIAL_CAPACITY];
    /** Day this contract's political contribution applied; -1 until unprocessed. */
    public int[]   contractCivilWarContributionAppliedTick =
            filledInts(INITIAL_CAPACITY, -1);
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
    public LongIntMap throneClaimIndexById     = new LongIntMap();
    public LongIntMap eventIndexById           = new LongIntMap();
    /** house id → row index in {@code playerReputation[]}. Sparse — only touched houses get rep rows. */
    public final LongIntMap repIndexByHouseId  = new LongIntMap();

    // ---------- Sequence counters ----------

    private long nextHouseId    = 1;
    private long nextStakeId    = 1;
    private long nextChainId    = 1;
    private long nextChronicleId = 1;
    private long nextContractId = 1;
    private long nextThroneClaimId = 1;
    private long nextKingmakerTestamentId = 1;
    private long nextMoralChoiceId = 1;
    private long nextEventId = 1;

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
        houseLastAmbitionReviewTick[i] = -1;
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
        chainPlayerAllegiance[i] = CivilWarAllegiance.NONE.toByte();
        chainPlayerContribution[i] = 0;
        chainPlayerLastContributionTick[i] = -1;
        chainPlayerConsequenceState[i] =
                CivilWarPlayerConsequenceState.PENDING.toByte();
        chainPlayerConsequenceAppliedTick[i] = -1;
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
        chronicleSourceFactionId[i] = -1;
        chronicleResultFactionId[i] = -1;
        chronicleTestamentId[i] = -1L;
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
        chronicleSourceFactionId[i] = -1;
        chronicleResultFactionId[i] = -1;
        chronicleTestamentId[i] = -1L;
        chronicleHappenedTick[i] = initiatedTick;
        chronicleLearnedTick[i] = learnedTick;
        return id;
    }

    /** Appends a confirmed house-dormancy dispatch. */
    public long addChronicleHouseDormancy(ChronicleBand band, long houseId,
                                          int marketId, int happenedTick,
                                          int learnedTick) {
        ensureChronicleCapacity(chronicleCount + 1);
        int i = chronicleCount++;
        long id = nextChronicleId++;
        chronicleId[i] = id;
        chronicleEventType[i] = ChronicleEventType.HOUSE_DORMANT.toByte();
        chronicleSourceChainId[i] = -1L;
        chronicleChainOutcome[i] = ChainState.ACTIVE.toByte();
        chronicleBand[i] = band.toByte();
        chronicleConfidence[i] = ChronicleConfidence.CONFIRMED.toByte();
        chronicleActorHouseId[i] = houseId;
        chronicleTargetHouseId[i] = -1L;
        chronicleMarketId[i] = marketId;
        chronicleIndustryId[i] = -1;
        chronicleSourceFactionId[i] = -1;
        chronicleResultFactionId[i] = -1;
        chronicleTestamentId[i] = -1L;
        chronicleHappenedTick[i] = happenedTick;
        chronicleLearnedTick[i] = learnedTick;
        return id;
    }

    /** Appends a confirmed faction-flip dispatch from an applied throne claim. */
    public long addChronicleThroneClaimApplied(long sourceChainId,
                                                ChronicleBand band,
                                                long actorHouseId,
                                                long targetHouseId,
                                                int sourceFactionId,
                                                int resultFactionId,
                                                int marketId,
                                                int happenedTick,
                                                int learnedTick) {
        ensureChronicleCapacity(chronicleCount + 1);
        int i = chronicleCount++;
        long id = nextChronicleId++;
        chronicleId[i] = id;
        chronicleEventType[i] = ChronicleEventType.THRONE_CLAIM_APPLIED.toByte();
        chronicleSourceChainId[i] = sourceChainId;
        chronicleChainOutcome[i] = ChainState.RESOLVED.toByte();
        chronicleBand[i] = band.toByte();
        chronicleConfidence[i] = ChronicleConfidence.CONFIRMED.toByte();
        chronicleActorHouseId[i] = actorHouseId;
        chronicleTargetHouseId[i] = targetHouseId;
        chronicleMarketId[i] = marketId;
        chronicleIndustryId[i] = -1;
        chronicleSourceFactionId[i] = sourceFactionId;
        chronicleResultFactionId[i] = resultFactionId;
        chronicleTestamentId[i] = -1L;
        chronicleHappenedTick[i] = happenedTick;
        chronicleLearnedTick[i] = learnedTick;
        return id;
    }

    /** Appends one confirmed, intimate dispatch linked to a sealed testament. */
    public long addChronicleKingmakerTestament(long testamentId,
                                                long sourceChainId,
                                                long claimantHouseId,
                                                long deposedHouseId,
                                                int sourceFactionId,
                                                int resultFactionId,
                                                int marketId,
                                                int sealedTick,
                                                int learnedTick) {
        for (int row = 0; row < chronicleCount; row++) {
            if (chronicleTestamentId[row] == testamentId) {
                return chronicleId[row];
            }
        }
        ensureChronicleCapacity(chronicleCount + 1);
        int i = chronicleCount++;
        long id = nextChronicleId++;
        chronicleId[i] = id;
        chronicleEventType[i] = ChronicleEventType.KINGMAKER_TESTAMENT.toByte();
        chronicleSourceChainId[i] = sourceChainId;
        chronicleChainOutcome[i] = ChainState.RESOLVED.toByte();
        chronicleBand[i] = ChronicleBand.INTIMATE.toByte();
        chronicleConfidence[i] = ChronicleConfidence.CONFIRMED.toByte();
        chronicleActorHouseId[i] = claimantHouseId;
        chronicleTargetHouseId[i] = deposedHouseId;
        chronicleMarketId[i] = marketId;
        chronicleIndustryId[i] = -1;
        chronicleSourceFactionId[i] = sourceFactionId;
        chronicleResultFactionId[i] = resultFactionId;
        chronicleTestamentId[i] = testamentId;
        chronicleHappenedTick[i] = sealedTick;
        chronicleLearnedTick[i] = learnedTick;
        return id;
    }

    /** Prepares one idempotent Tier-3 handoff row per source chain. */
    public long prepareThroneClaim(long sourceChainId, long houseId,
                                   int sourceFactionId, int resultFactionId,
                                   int marketId, int preparedTick) {
        for (int row = 0; row < throneClaimCount; row++) {
            if (throneClaimSourceChainId[row] == sourceChainId) {
                return throneClaimId[row];
            }
        }
        ensureThroneClaimCapacity(throneClaimCount + 1);
        int i = throneClaimCount++;
        long id = nextThroneClaimId++;
        throneClaimId[i] = id;
        throneClaimSourceChainId[i] = sourceChainId;
        throneClaimHouseId[i] = houseId;
        throneClaimSourceFactionId[i] = sourceFactionId;
        throneClaimResultFactionId[i] = resultFactionId;
        throneClaimMarketId[i] = marketId;
        int sourceChainRow = chainIndex(sourceChainId);
        if (sourceChainRow >= 0) {
            throneClaimPlayerAllegiance[i] = chainPlayerAllegiance[sourceChainRow];
            throneClaimPlayerContribution[i] = chainPlayerContribution[sourceChainRow];
            throneClaimPlayerLastContributionTick[i] =
                    chainPlayerLastContributionTick[sourceChainRow];
        } else {
            throneClaimPlayerAllegiance[i] = CivilWarAllegiance.NONE.toByte();
            throneClaimPlayerContribution[i] = 0;
            throneClaimPlayerLastContributionTick[i] = -1;
        }
        throneClaimPlayerConsequenceState[i] =
                CivilWarPlayerConsequenceState.PENDING.toByte();
        throneClaimPlayerConsequenceAppliedTick[i] = -1;
        throneClaimState[i] = ThroneClaimState.PREPARED.toByte();
        throneClaimPreparedTick[i] = preparedTick;
        throneClaimAppliedTick[i] = -1;
        throneClaimConsequenceState[i] = ThroneClaimConsequenceState.PENDING.toByte();
        throneClaimConsequenceAppliedTick[i] = -1;
        throneClaimIndexById.put(id, i);
        return id;
    }

    /** O(1) lookup: throne-claim id to row index, or {@code -1}. */
    public int throneClaimIndex(long id) {
        return throneClaimIndexById.get(id);
    }

    /** Finds a testament by its source throne claim, or {@code -1}. */
    public int kingmakerTestamentIndexForClaim(long throneClaimIdValue) {
        for (int row = 0; row < kingmakerTestamentCount; row++) {
            if (kingmakerTestamentThroneClaimId[row] == throneClaimIdValue) {
                return row;
            }
        }
        return -1;
    }

    /** Finds a testament by its stable id, or {@code -1}. */
    public int kingmakerTestamentIndex(long testamentIdValue) {
        for (int row = 0; row < kingmakerTestamentCount; row++) {
            if (kingmakerTestamentId[row] == testamentIdValue) return row;
        }
        return -1;
    }

    /** Seals one immutable testimony snapshot per source throne claim. */
    public long sealKingmakerTestament(long throneClaimIdValue,
                                       long sourceChainId,
                                       long claimantHouseId,
                                       long deposedHouseId,
                                       int sourceFactionId,
                                       int resultFactionId,
                                       int marketId,
                                       short playerContribution,
                                       int mercy,
                                       int integrity,
                                       int stewardship,
                                       int institutionalism,
                                       int moralChoiceCountAtSeal,
                                       int sealedTick) {
        int existing = kingmakerTestamentIndexForClaim(throneClaimIdValue);
        if (existing >= 0) return kingmakerTestamentId[existing];

        ensureKingmakerTestamentCapacity(kingmakerTestamentCount + 1);
        int i = kingmakerTestamentCount++;
        long id = nextKingmakerTestamentId++;
        kingmakerTestamentId[i] = id;
        kingmakerTestamentThroneClaimId[i] = throneClaimIdValue;
        kingmakerTestamentSourceChainId[i] = sourceChainId;
        kingmakerTestamentClaimantHouseId[i] = claimantHouseId;
        kingmakerTestamentDeposedHouseId[i] = deposedHouseId;
        kingmakerTestamentSourceFactionId[i] = sourceFactionId;
        kingmakerTestamentResultFactionId[i] = resultFactionId;
        kingmakerTestamentMarketId[i] = marketId;
        kingmakerTestamentPlayerContribution[i] = playerContribution;
        kingmakerTestamentMercy[i] = mercy;
        kingmakerTestamentIntegrity[i] = integrity;
        kingmakerTestamentStewardship[i] = stewardship;
        kingmakerTestamentInstitutionalism[i] = institutionalism;
        kingmakerTestamentMoralChoiceCount[i] = moralChoiceCountAtSeal;
        kingmakerTestamentSealedTick[i] = sealedTick;
        kingmakerTestamentState[i] = KingmakerTestamentState.SEALED.toByte();
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

    /** Appends one already-validated immutable moral-choice snapshot. */
    long appendMoralChoice(MoralChoiceSource sourceType, long sourceId,
                           short mercyDelta, short integrityDelta,
                           short stewardshipDelta, short institutionalismDelta,
                           int happenedTick, int recordedTick) {
        ensureMoralChoiceCapacity(moralChoiceCount + 1);
        int i = moralChoiceCount++;
        long id = nextMoralChoiceId++;
        moralChoiceId[i] = id;
        moralChoiceSourceType[i] = sourceType.toByte();
        moralChoiceSourceId[i] = sourceId;
        moralChoiceMercyDelta[i] = mercyDelta;
        moralChoiceIntegrityDelta[i] = integrityDelta;
        moralChoiceStewardshipDelta[i] = stewardshipDelta;
        moralChoiceInstitutionalismDelta[i] = institutionalismDelta;
        moralChoiceHappenedTick[i] = happenedTick;
        moralChoiceRecordedTick[i] = recordedTick;
        return id;
    }

    /** Appends one already-validated black-swan event snapshot. */
    long appendCampaignEvent(CampaignEventType type, long triggerKey,
                             int marketId, int createdTick, int deadlineTick,
                             int suppliesRequired, int fuelRequired,
                             int civiliansAtRisk) {
        ensureEventCapacity(eventCount + 1);
        int i = eventCount++;
        long id = nextEventId++;
        eventId[i] = id;
        eventType[i] = type.toByte();
        eventTriggerKey[i] = triggerKey;
        eventState[i] = CampaignEventState.PENDING_CHOICE.toByte();
        eventMarketId[i] = marketId;
        eventCreatedTick[i] = createdTick;
        eventDeadlineTick[i] = deadlineTick;
        eventDecisionTick[i] = -1;
        eventResolvedTick[i] = -1;
        eventSuppliesRequired[i] = suppliesRequired;
        eventFuelRequired[i] = fuelRequired;
        eventCiviliansAtRisk[i] = civiliansAtRisk;
        eventCiviliansRescued[i] = 0;
        eventSourceChainId[i] = -1L;
        eventActorHouseId[i] = -1L;
        eventTargetHouseId[i] = -1L;
        eventFollowupTick[i] = -1;
        eventFollowupDeadlineTick[i] = -1;
        eventCreditsOffered[i] = 0;
        eventDefectorOutcome[i] = DefectorAsylumOutcome.NONE.toByte();
        eventIndexById.put(id, i);
        return id;
    }

    /** O(1) lookup: campaign event id to row index, or {@code -1}. */
    public int eventIndex(long id) {
        return eventIndexById.get(id);
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
        contractOpposedChainId[i]   = -1L;
        contractCivilWarBand[i]     = CivilWarBand.NONE.toByte();
        contractCivilWarContributionAppliedTick[i] = -1;
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
        houseLastAmbitionReviewTick = Arrays.copyOf(houseLastAmbitionReviewTick, n);
        Arrays.fill(houseLastAmbitionReviewTick, oldLength, n, -1);
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
        chainPlayerAllegiance = Arrays.copyOf(chainPlayerAllegiance, n);
        chainPlayerContribution = Arrays.copyOf(chainPlayerContribution, n);
        chainPlayerLastContributionTick = Arrays.copyOf(
                chainPlayerLastContributionTick, n);
        Arrays.fill(chainPlayerLastContributionTick, oldLength, n, -1);
        chainPlayerConsequenceState = Arrays.copyOf(
                chainPlayerConsequenceState, n);
        chainPlayerConsequenceAppliedTick = Arrays.copyOf(
                chainPlayerConsequenceAppliedTick, n);
        Arrays.fill(chainPlayerConsequenceAppliedTick, oldLength, n, -1);
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
        chronicleSourceFactionId = Arrays.copyOf(chronicleSourceFactionId, n);
        Arrays.fill(chronicleSourceFactionId, oldLength, n, -1);
        chronicleResultFactionId = Arrays.copyOf(chronicleResultFactionId, n);
        Arrays.fill(chronicleResultFactionId, oldLength, n, -1);
        chronicleTestamentId = Arrays.copyOf(chronicleTestamentId, n);
        Arrays.fill(chronicleTestamentId, oldLength, n, -1L);
        chronicleHappenedTick = Arrays.copyOf(chronicleHappenedTick, n);
        Arrays.fill(chronicleHappenedTick, oldLength, n, -1);
        chronicleLearnedTick = Arrays.copyOf(chronicleLearnedTick, n);
        Arrays.fill(chronicleLearnedTick, oldLength, n, -1);
    }

    private void ensureThroneClaimCapacity(int needed) {
        if (needed <= throneClaimId.length) return;
        int oldLength = throneClaimId.length;
        int n = Math.max(needed, throneClaimId.length * 2);
        throneClaimId = Arrays.copyOf(throneClaimId, n);
        throneClaimSourceChainId = Arrays.copyOf(throneClaimSourceChainId, n);
        Arrays.fill(throneClaimSourceChainId, oldLength, n, -1L);
        throneClaimHouseId = Arrays.copyOf(throneClaimHouseId, n);
        Arrays.fill(throneClaimHouseId, oldLength, n, -1L);
        throneClaimSourceFactionId = Arrays.copyOf(throneClaimSourceFactionId, n);
        Arrays.fill(throneClaimSourceFactionId, oldLength, n, -1);
        throneClaimResultFactionId = Arrays.copyOf(throneClaimResultFactionId, n);
        Arrays.fill(throneClaimResultFactionId, oldLength, n, -1);
        throneClaimMarketId = Arrays.copyOf(throneClaimMarketId, n);
        Arrays.fill(throneClaimMarketId, oldLength, n, -1);
        throneClaimPlayerAllegiance = Arrays.copyOf(throneClaimPlayerAllegiance, n);
        throneClaimPlayerContribution = Arrays.copyOf(throneClaimPlayerContribution, n);
        throneClaimPlayerLastContributionTick = Arrays.copyOf(
                throneClaimPlayerLastContributionTick, n);
        Arrays.fill(throneClaimPlayerLastContributionTick, oldLength, n, -1);
        throneClaimPlayerConsequenceState = Arrays.copyOf(
                throneClaimPlayerConsequenceState, n);
        throneClaimPlayerConsequenceAppliedTick = Arrays.copyOf(
                throneClaimPlayerConsequenceAppliedTick, n);
        Arrays.fill(throneClaimPlayerConsequenceAppliedTick, oldLength, n, -1);
        throneClaimState = Arrays.copyOf(throneClaimState, n);
        throneClaimPreparedTick = Arrays.copyOf(throneClaimPreparedTick, n);
        Arrays.fill(throneClaimPreparedTick, oldLength, n, -1);
        throneClaimAppliedTick = Arrays.copyOf(throneClaimAppliedTick, n);
        Arrays.fill(throneClaimAppliedTick, oldLength, n, -1);
        throneClaimConsequenceState = Arrays.copyOf(throneClaimConsequenceState, n);
        throneClaimConsequenceAppliedTick = Arrays.copyOf(
                throneClaimConsequenceAppliedTick, n);
        Arrays.fill(throneClaimConsequenceAppliedTick, oldLength, n, -1);
    }

    private void ensureKingmakerTestamentCapacity(int needed) {
        if (needed <= kingmakerTestamentId.length) return;
        int oldLength = kingmakerTestamentId.length;
        int n = Math.max(needed, kingmakerTestamentId.length * 2);
        kingmakerTestamentId = Arrays.copyOf(kingmakerTestamentId, n);
        kingmakerTestamentThroneClaimId = Arrays.copyOf(
                kingmakerTestamentThroneClaimId, n);
        Arrays.fill(kingmakerTestamentThroneClaimId, oldLength, n, -1L);
        kingmakerTestamentSourceChainId = Arrays.copyOf(
                kingmakerTestamentSourceChainId, n);
        Arrays.fill(kingmakerTestamentSourceChainId, oldLength, n, -1L);
        kingmakerTestamentClaimantHouseId = Arrays.copyOf(
                kingmakerTestamentClaimantHouseId, n);
        Arrays.fill(kingmakerTestamentClaimantHouseId, oldLength, n, -1L);
        kingmakerTestamentDeposedHouseId = Arrays.copyOf(
                kingmakerTestamentDeposedHouseId, n);
        Arrays.fill(kingmakerTestamentDeposedHouseId, oldLength, n, -1L);
        kingmakerTestamentSourceFactionId = Arrays.copyOf(
                kingmakerTestamentSourceFactionId, n);
        Arrays.fill(kingmakerTestamentSourceFactionId, oldLength, n, -1);
        kingmakerTestamentResultFactionId = Arrays.copyOf(
                kingmakerTestamentResultFactionId, n);
        Arrays.fill(kingmakerTestamentResultFactionId, oldLength, n, -1);
        kingmakerTestamentMarketId = Arrays.copyOf(kingmakerTestamentMarketId, n);
        Arrays.fill(kingmakerTestamentMarketId, oldLength, n, -1);
        kingmakerTestamentPlayerContribution = Arrays.copyOf(
                kingmakerTestamentPlayerContribution, n);
        kingmakerTestamentMercy = Arrays.copyOf(kingmakerTestamentMercy, n);
        kingmakerTestamentIntegrity = Arrays.copyOf(kingmakerTestamentIntegrity, n);
        kingmakerTestamentStewardship = Arrays.copyOf(
                kingmakerTestamentStewardship, n);
        kingmakerTestamentInstitutionalism = Arrays.copyOf(
                kingmakerTestamentInstitutionalism, n);
        kingmakerTestamentMoralChoiceCount = Arrays.copyOf(
                kingmakerTestamentMoralChoiceCount, n);
        Arrays.fill(kingmakerTestamentMoralChoiceCount, oldLength, n, -1);
        kingmakerTestamentSealedTick = Arrays.copyOf(
                kingmakerTestamentSealedTick, n);
        Arrays.fill(kingmakerTestamentSealedTick, oldLength, n, -1);
        kingmakerTestamentState = Arrays.copyOf(kingmakerTestamentState, n);
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

    private void ensureMoralChoiceCapacity(int needed) {
        if (needed <= moralChoiceId.length) return;
        int oldLength = moralChoiceId.length;
        int n = Math.max(needed, moralChoiceId.length * 2);
        moralChoiceId = Arrays.copyOf(moralChoiceId, n);
        moralChoiceSourceType = Arrays.copyOf(moralChoiceSourceType, n);
        moralChoiceSourceId = Arrays.copyOf(moralChoiceSourceId, n);
        Arrays.fill(moralChoiceSourceId, oldLength, n, -1L);
        moralChoiceMercyDelta = Arrays.copyOf(moralChoiceMercyDelta, n);
        moralChoiceIntegrityDelta = Arrays.copyOf(moralChoiceIntegrityDelta, n);
        moralChoiceStewardshipDelta = Arrays.copyOf(moralChoiceStewardshipDelta, n);
        moralChoiceInstitutionalismDelta = Arrays.copyOf(
                moralChoiceInstitutionalismDelta, n);
        moralChoiceHappenedTick = Arrays.copyOf(moralChoiceHappenedTick, n);
        Arrays.fill(moralChoiceHappenedTick, oldLength, n, -1);
        moralChoiceRecordedTick = Arrays.copyOf(moralChoiceRecordedTick, n);
        Arrays.fill(moralChoiceRecordedTick, oldLength, n, -1);
    }

    private void ensureEventCapacity(int needed) {
        if (needed <= eventId.length) return;
        int oldLength = eventId.length;
        int n = Math.max(needed, eventId.length * 2);
        eventId = Arrays.copyOf(eventId, n);
        eventType = Arrays.copyOf(eventType, n);
        eventTriggerKey = Arrays.copyOf(eventTriggerKey, n);
        Arrays.fill(eventTriggerKey, oldLength, n, -1L);
        eventState = Arrays.copyOf(eventState, n);
        eventMarketId = Arrays.copyOf(eventMarketId, n);
        Arrays.fill(eventMarketId, oldLength, n, -1);
        eventCreatedTick = Arrays.copyOf(eventCreatedTick, n);
        Arrays.fill(eventCreatedTick, oldLength, n, -1);
        eventDeadlineTick = Arrays.copyOf(eventDeadlineTick, n);
        Arrays.fill(eventDeadlineTick, oldLength, n, -1);
        eventDecisionTick = Arrays.copyOf(eventDecisionTick, n);
        Arrays.fill(eventDecisionTick, oldLength, n, -1);
        eventResolvedTick = Arrays.copyOf(eventResolvedTick, n);
        Arrays.fill(eventResolvedTick, oldLength, n, -1);
        eventSuppliesRequired = Arrays.copyOf(eventSuppliesRequired, n);
        eventFuelRequired = Arrays.copyOf(eventFuelRequired, n);
        eventCiviliansAtRisk = Arrays.copyOf(eventCiviliansAtRisk, n);
        eventCiviliansRescued = Arrays.copyOf(eventCiviliansRescued, n);
        eventSourceChainId = Arrays.copyOf(eventSourceChainId, n);
        Arrays.fill(eventSourceChainId, oldLength, n, -1L);
        eventActorHouseId = Arrays.copyOf(eventActorHouseId, n);
        Arrays.fill(eventActorHouseId, oldLength, n, -1L);
        eventTargetHouseId = Arrays.copyOf(eventTargetHouseId, n);
        Arrays.fill(eventTargetHouseId, oldLength, n, -1L);
        eventFollowupTick = Arrays.copyOf(eventFollowupTick, n);
        Arrays.fill(eventFollowupTick, oldLength, n, -1);
        eventFollowupDeadlineTick = Arrays.copyOf(eventFollowupDeadlineTick, n);
        Arrays.fill(eventFollowupDeadlineTick, oldLength, n, -1);
        eventCreditsOffered = Arrays.copyOf(eventCreditsOffered, n);
        eventDefectorOutcome = Arrays.copyOf(eventDefectorOutcome, n);
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
        if (houseLastAmbitionReviewTick == null) {
            int n = houseId != null ? houseId.length : INITIAL_CAPACITY;
            houseLastAmbitionReviewTick = filledInts(n, -1);
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
        if (chainPlayerAllegiance == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainPlayerAllegiance = new byte[n];
        }
        if (chainPlayerContribution == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainPlayerContribution = new short[n];
        }
        if (chainPlayerLastContributionTick == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainPlayerLastContributionTick = filledInts(n, -1);
        }
        if (chainPlayerConsequenceState == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainPlayerConsequenceState = new byte[n];
        }
        if (chainPlayerConsequenceAppliedTick == null) {
            int n = chainId != null ? chainId.length : INITIAL_CAPACITY;
            chainPlayerConsequenceAppliedTick = filledInts(n, -1);
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
        if (chronicleSourceFactionId == null) {
            chronicleSourceFactionId = filledInts(chronicleCapacity, -1);
        }
        if (chronicleResultFactionId == null) {
            chronicleResultFactionId = filledInts(chronicleCapacity, -1);
        }
        if (chronicleTestamentId == null) {
            chronicleTestamentId = filledLongs(chronicleCapacity, -1L);
        }
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
        int throneClaimCapacity = throneClaimId != null
                ? throneClaimId.length : INITIAL_CAPACITY;
        if (throneClaimId == null) throneClaimId = new long[throneClaimCapacity];
        if (throneClaimSourceChainId == null) {
            throneClaimSourceChainId = filledLongs(throneClaimCapacity, -1L);
        }
        if (throneClaimHouseId == null) {
            throneClaimHouseId = filledLongs(throneClaimCapacity, -1L);
        }
        if (throneClaimSourceFactionId == null) {
            throneClaimSourceFactionId = filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimResultFactionId == null) {
            throneClaimResultFactionId = filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimMarketId == null) {
            throneClaimMarketId = filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimPlayerAllegiance == null) {
            throneClaimPlayerAllegiance = new byte[throneClaimCapacity];
        }
        if (throneClaimPlayerContribution == null) {
            throneClaimPlayerContribution = new short[throneClaimCapacity];
        }
        if (throneClaimPlayerLastContributionTick == null) {
            throneClaimPlayerLastContributionTick =
                    filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimPlayerConsequenceState == null) {
            throneClaimPlayerConsequenceState = new byte[throneClaimCapacity];
        }
        if (throneClaimPlayerConsequenceAppliedTick == null) {
            throneClaimPlayerConsequenceAppliedTick =
                    filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimState == null) throneClaimState = new byte[throneClaimCapacity];
        if (throneClaimPreparedTick == null) {
            throneClaimPreparedTick = filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimAppliedTick == null) {
            throneClaimAppliedTick = filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimConsequenceState == null) {
            throneClaimConsequenceState = new byte[throneClaimCapacity];
        }
        if (throneClaimConsequenceAppliedTick == null) {
            throneClaimConsequenceAppliedTick = filledInts(throneClaimCapacity, -1);
        }
        if (throneClaimIndexById == null) throneClaimIndexById = new LongIntMap();
        throneClaimIndexById.clear();
        for (int i = 0; i < throneClaimCount; i++) {
            throneClaimIndexById.put(throneClaimId[i], i);
        }
        if (nextThroneClaimId <= 0L) {
            nextThroneClaimId = 1L;
            for (int i = 0; i < throneClaimCount; i++) {
                nextThroneClaimId = Math.max(nextThroneClaimId, throneClaimId[i] + 1L);
            }
        }
        int testamentCapacity = kingmakerTestamentId != null
                ? kingmakerTestamentId.length : INITIAL_CAPACITY;
        if (kingmakerTestamentId == null) {
            kingmakerTestamentId = new long[testamentCapacity];
        }
        if (kingmakerTestamentThroneClaimId == null) {
            kingmakerTestamentThroneClaimId = filledLongs(testamentCapacity, -1L);
        }
        if (kingmakerTestamentSourceChainId == null) {
            kingmakerTestamentSourceChainId = filledLongs(testamentCapacity, -1L);
        }
        if (kingmakerTestamentClaimantHouseId == null) {
            kingmakerTestamentClaimantHouseId = filledLongs(testamentCapacity, -1L);
        }
        if (kingmakerTestamentDeposedHouseId == null) {
            kingmakerTestamentDeposedHouseId = filledLongs(testamentCapacity, -1L);
        }
        if (kingmakerTestamentSourceFactionId == null) {
            kingmakerTestamentSourceFactionId = filledInts(testamentCapacity, -1);
        }
        if (kingmakerTestamentResultFactionId == null) {
            kingmakerTestamentResultFactionId = filledInts(testamentCapacity, -1);
        }
        if (kingmakerTestamentMarketId == null) {
            kingmakerTestamentMarketId = filledInts(testamentCapacity, -1);
        }
        if (kingmakerTestamentPlayerContribution == null) {
            kingmakerTestamentPlayerContribution = new short[testamentCapacity];
        }
        if (kingmakerTestamentMercy == null) {
            kingmakerTestamentMercy = new int[testamentCapacity];
        }
        if (kingmakerTestamentIntegrity == null) {
            kingmakerTestamentIntegrity = new int[testamentCapacity];
        }
        if (kingmakerTestamentStewardship == null) {
            kingmakerTestamentStewardship = new int[testamentCapacity];
        }
        if (kingmakerTestamentInstitutionalism == null) {
            kingmakerTestamentInstitutionalism = new int[testamentCapacity];
        }
        if (kingmakerTestamentMoralChoiceCount == null) {
            kingmakerTestamentMoralChoiceCount = filledInts(testamentCapacity, -1);
        }
        if (kingmakerTestamentSealedTick == null) {
            kingmakerTestamentSealedTick = filledInts(testamentCapacity, -1);
        }
        if (kingmakerTestamentState == null) {
            kingmakerTestamentState = new byte[testamentCapacity];
        }
        if (nextKingmakerTestamentId <= 0L) {
            nextKingmakerTestamentId = 1L;
            for (int i = 0; i < kingmakerTestamentCount; i++) {
                nextKingmakerTestamentId = Math.max(
                        nextKingmakerTestamentId,
                        kingmakerTestamentId[i] + 1L);
            }
        }
        int moralChoiceCapacity = moralChoiceId != null
                ? moralChoiceId.length : INITIAL_CAPACITY;
        if (moralChoiceId == null) moralChoiceId = new long[moralChoiceCapacity];
        if (moralChoiceSourceType == null) {
            moralChoiceSourceType = new byte[moralChoiceCapacity];
        }
        if (moralChoiceSourceId == null) {
            moralChoiceSourceId = filledLongs(moralChoiceCapacity, -1L);
        }
        if (moralChoiceMercyDelta == null) {
            moralChoiceMercyDelta = new short[moralChoiceCapacity];
        }
        if (moralChoiceIntegrityDelta == null) {
            moralChoiceIntegrityDelta = new short[moralChoiceCapacity];
        }
        if (moralChoiceStewardshipDelta == null) {
            moralChoiceStewardshipDelta = new short[moralChoiceCapacity];
        }
        if (moralChoiceInstitutionalismDelta == null) {
            moralChoiceInstitutionalismDelta = new short[moralChoiceCapacity];
        }
        if (moralChoiceHappenedTick == null) {
            moralChoiceHappenedTick = filledInts(moralChoiceCapacity, -1);
        }
        if (moralChoiceRecordedTick == null) {
            moralChoiceRecordedTick = filledInts(moralChoiceCapacity, -1);
        }
        if (nextMoralChoiceId <= 0L) {
            nextMoralChoiceId = 1L;
            for (int i = 0; i < moralChoiceCount; i++) {
                nextMoralChoiceId = Math.max(
                        nextMoralChoiceId, moralChoiceId[i] + 1L);
            }
        }
        int eventCapacity = eventId != null ? eventId.length : INITIAL_CAPACITY;
        if (eventId == null) eventId = new long[eventCapacity];
        if (eventType == null) eventType = new byte[eventCapacity];
        if (eventTriggerKey == null) {
            eventTriggerKey = filledLongs(eventCapacity, -1L);
        }
        if (eventState == null) eventState = new byte[eventCapacity];
        if (eventMarketId == null) eventMarketId = filledInts(eventCapacity, -1);
        if (eventCreatedTick == null) {
            eventCreatedTick = filledInts(eventCapacity, -1);
        }
        if (eventDeadlineTick == null) {
            eventDeadlineTick = filledInts(eventCapacity, -1);
        }
        if (eventDecisionTick == null) {
            eventDecisionTick = filledInts(eventCapacity, -1);
        }
        if (eventResolvedTick == null) {
            eventResolvedTick = filledInts(eventCapacity, -1);
        }
        if (eventSuppliesRequired == null) {
            eventSuppliesRequired = new int[eventCapacity];
        }
        if (eventFuelRequired == null) eventFuelRequired = new int[eventCapacity];
        if (eventCiviliansAtRisk == null) {
            eventCiviliansAtRisk = new int[eventCapacity];
        }
        if (eventCiviliansRescued == null) {
            eventCiviliansRescued = new int[eventCapacity];
        }
        if (eventSourceChainId == null) {
            eventSourceChainId = filledLongs(eventCapacity, -1L);
        }
        if (eventActorHouseId == null) {
            eventActorHouseId = filledLongs(eventCapacity, -1L);
        }
        if (eventTargetHouseId == null) {
            eventTargetHouseId = filledLongs(eventCapacity, -1L);
        }
        if (eventFollowupTick == null) {
            eventFollowupTick = filledInts(eventCapacity, -1);
        }
        if (eventFollowupDeadlineTick == null) {
            eventFollowupDeadlineTick = filledInts(eventCapacity, -1);
        }
        if (eventCreditsOffered == null) {
            eventCreditsOffered = new int[eventCapacity];
        }
        if (eventDefectorOutcome == null) {
            eventDefectorOutcome = new byte[eventCapacity];
        }
        if (eventIndexById == null) eventIndexById = new LongIntMap();
        eventIndexById.clear();
        for (int i = 0; i < eventCount; i++) {
            eventIndexById.put(eventId[i], i);
        }
        if (nextEventId <= 0L) {
            nextEventId = 1L;
            for (int i = 0; i < eventCount; i++) {
                nextEventId = Math.max(nextEventId, eventId[i] + 1L);
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
        if (contractOpposedChainId == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractOpposedChainId = filledLongs(n, -1L);
        }
        if (contractCivilWarBand == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractCivilWarBand = new byte[n];
        }
        if (contractCivilWarContributionAppliedTick == null) {
            int n = contractId != null ? contractId.length : INITIAL_CAPACITY;
            contractCivilWarContributionAppliedTick = filledInts(n, -1);
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
        contractOpposedChainId    = Arrays.copyOf(contractOpposedChainId, n);
        Arrays.fill(contractOpposedChainId, oldLength, n, -1L);
        contractCivilWarBand      = Arrays.copyOf(contractCivilWarBand, n);
        contractCivilWarContributionAppliedTick = Arrays.copyOf(
                contractCivilWarContributionAppliedTick, n);
        Arrays.fill(contractCivilWarContributionAppliedTick, oldLength, n, -1);
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
