package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Frozen snapshot of a completed mission's results — everything the RESULTS
 * screen displays and everything {@link MissionResolver#apply} writes back to
 * the player's game state. Compute it once when the battle ends, apply it
 * once, then read it for display. Immutable so it can't drift after creation.
 *
 * <p>Build one with {@link #builder()}. Every field has an "absent" default
 * (see {@link Builder}), so a caller sets only what it actually knows —
 * a debug fixture with no battle in hand names four values, not thirty-six.
 */
public final class MissionOutcome {

    public final boolean        victory;
    public final String         missionId;
    public final String         missionName;
    public final MissionType    missionType;
    public final RiskLevel      risk;
    public final MissionSource  missionSource;
    /** Contract payout before the briefing's cash-for-salvage multiplier. */
    public final int            payoutBase;
    public final int            payoutEarned;
    public final int            marinesEngaged;
    public final int            marinesLost;
    /**
     * Allied combatants that took the field beside the company, and how many
     * of them fell. Their own ledger: an allied casualty is reported but never
     * reaches {@link #survivingSoldierIds}, {@link #fallenSoldierIds}, the
     * personnel outcome, or reputation — allies are not the company's people.
     */
    public final int            alliesEngaged;
    public final int            alliesLost;
    /** Planet name the mission targeted; null if no specific target. */
    public final String         targetPlanetName;
    /** Industry id the mission targeted; null if no industry-specific target. */
    public final String         targetIndustryId;
    /** Faction whose equipment flavors the recovery pool; null when unknown. */
    public final String         targetFactionId;

    /** Captain id may be null if the player had no roster at briefing time. */
    public final String  captainId;
    public final String  captainName;
    public final Status  priorCaptainStatus;
    public final Status  newCaptainStatus;
    public final int     xpGained;
    /** Sector clock day the captain returns to ACTIVE; 0 unless newStatus is INJURED. */
    public final float   injuredUntilDay;
    /** Non-null when the XP gained crossed one or more promotion thresholds; the new rank. */
    public final Rank    promotedTo;

    /** Campaign-tier contract id this resolved a phase of; {@code -1} for ad-hoc missions. */
    public final long    contractId;
    /** Stable black-swan event lineage; {@code -1} for non-event outcomes. */
    public final long    campaignEventId;
    /** Frozen event market registry slot; {@code -1} for non-event outcomes. */
    public final int     campaignEventMarketId;
    /** Frozen Silent Colony threat lineage; {@code -1} when absent. */
    public final long    campaignEventThreatSeed;
    /** Frozen civilian stakes from mission creation; zero otherwise. */
    public final int     civiliansAtRisk;
    /** Explicit battle evacuation report; {@code -1} means no valid report. */
    public final int     civiliansRescued;
    /** Representative cohort size in the sealed battle report; {@code -1} when absent. */
    public final int     evacuationRepresentatives;
    /** Representatives evacuated from that cohort; {@code -1} when absent. */
    public final int     representativesEvacuated;
    /** Explicit Silent Colony archive report; {@code NONE} means no valid report. */
    public final AbandonedColonyArchiveOutcome colonyArchiveOutcome;
    /** Salvage percentage consumed by the loot roll (0..255). 0 = no salvage. */
    public final int     salvageEntitlement;
    /** Frozen captain + fleet recovery-pool bonus, in percentage points. */
    public final int     salvageRecoveryBonusPct;
    /** Frozen deterministic chance for the high-value catalog roll. */
    public final int     salvageHighValueChancePct;
    /**
     * The company's own boats this mission destroyed, in the order they were
     * lost. Named here rather than looked up later because the outcome is shown
     * after the deck has been struck: by then the boat is off its berth and the
     * only place its name still exists is this list.
     */
    public final List<BoatLoss> boatsLost;
    /**
     * Marines who went down aboard a lost boat, counted separately from the
     * ground casualties they are folded into. They are fallen the same way and
     * roll the same fate; this is what says how many of the losses never
     * reached the ground.
     */
    public final int marinesLostAboard;
    public final Set<String> survivingSoldierIds;
    public final Set<String> fallenSoldierIds;
    /** Mission-time persistent squad selection, in briefing order. */
    public final Set<String> deployedFireteamIds;
    /**
     * What each deployed marine actually did, keyed by campaign soldier id.
     * Frozen here for the same replay-determinism reason as the rest of this
     * class: computing an outcome twice must yield identical telemetry.
     *
     * <p>Only marines the campaign roster tracks appear. Defenders, employer
     * militia and turrets are recorded in battle and reach the per-mission
     * debug table, but never a career record
     * ({@code progression-nouns.md}).
     * Empty for every outcome built by a caller that has no battle in hand.
     */
    public final Map<String, CombatTelemetryRow> soldierTelemetry;

    /** @return a builder with every field defaulted to its "absent" value. */
    public static Builder builder() {
        return new Builder();
    }

    private MissionOutcome(Builder b) {
        this.victory            = b.victory;
        this.missionId          = b.missionId;
        this.missionName        = b.missionName;
        this.missionType        = b.missionType;
        this.risk               = b.risk;
        this.missionSource      = b.missionSource != null ? b.missionSource : MissionSource.GENERATED;
        this.payoutBase         = b.payoutBase;
        this.payoutEarned       = b.payoutEarned;
        this.marinesEngaged     = b.marinesEngaged;
        this.marinesLost        = b.marinesLost;
        this.alliesEngaged      = b.alliesEngaged;
        this.alliesLost         = b.alliesLost;
        this.captainId          = b.captainId;
        this.captainName        = b.captainName;
        this.priorCaptainStatus = b.priorCaptainStatus;
        this.newCaptainStatus   = b.newCaptainStatus;
        this.xpGained           = b.xpGained;
        this.injuredUntilDay    = b.injuredUntilDay;
        this.promotedTo         = b.promotedTo;
        this.targetPlanetName   = b.targetPlanetName;
        this.targetIndustryId   = b.targetIndustryId;
        this.targetFactionId    = b.targetFactionId;
        this.contractId         = b.contractId;
        this.campaignEventId = b.campaignEventId > 0L ? b.campaignEventId : -1L;
        this.campaignEventMarketId = this.campaignEventId > 0L
                ? Math.max(-1, b.campaignEventMarketId) : -1;
        this.campaignEventThreatSeed = this.campaignEventId > 0L
                && b.campaignEventThreatSeed >= 0L
                ? b.campaignEventThreatSeed : -1L;
        this.civiliansAtRisk = this.missionSource.isCivilianRescue()
                ? Math.max(0, b.civiliansAtRisk) : 0;
        this.civiliansRescued = this.missionSource.isCivilianRescue()
                ? b.civiliansRescued : -1;
        boolean validEvacuation = this.missionSource.isCivilianRescue()
                && b.evacuationRepresentatives > 0
                && b.representativesEvacuated >= 0
                && b.representativesEvacuated <= b.evacuationRepresentatives;
        this.evacuationRepresentatives = validEvacuation
                ? b.evacuationRepresentatives : -1;
        this.representativesEvacuated = validEvacuation
                ? b.representativesEvacuated : -1;
        this.colonyArchiveOutcome = b.colonyArchiveOutcome != null
                ? b.colonyArchiveOutcome : AbandonedColonyArchiveOutcome.NONE;
        this.salvageEntitlement = b.salvageEntitlement;
        this.salvageRecoveryBonusPct = Math.max(0, b.salvageRecoveryBonusPct);
        this.salvageHighValueChancePct = Math.max(0, Math.min(100, b.salvageHighValueChancePct));
        this.boatsLost = immutableBoatLosses(b.boatsLost);
        this.marinesLostAboard = Math.max(0, b.marinesLostAboard);
        this.survivingSoldierIds = immutableIds(b.survivingSoldierIds);
        this.fallenSoldierIds = immutableIds(b.fallenSoldierIds);
        this.deployedFireteamIds = immutableOrderedIds(b.deployedFireteamIds);
        this.soldierTelemetry = immutableTelemetry(b.soldierTelemetry);
    }

    /**
     * Mutable staging area for one {@link MissionOutcome}. Defaults are the
     * "nothing to report" values the outcome's own normalization treats as
     * absent — {@code -1} for every lineage id and every optional battle
     * report, zero for civilian stakes and salvage, empty for the soldier
     * collections. That is what lets a caller name only the fields it knows.
     *
     * <p>Not thread-safe and not reusable in spirit: build one, call
     * {@link #build()}, drop it.
     */
    public static final class Builder {

        private boolean victory;
        private String missionId;
        private String missionName;
        private MissionType missionType;
        private RiskLevel risk;
        private MissionSource missionSource;
        private int payoutBase;
        private int payoutEarned;
        private int marinesEngaged;
        private int marinesLost;
        private int alliesEngaged;
        private int alliesLost;
        private String targetPlanetName;
        private String targetIndustryId;
        private String targetFactionId;

        private String captainId;
        private String captainName;
        private Status priorCaptainStatus;
        private Status newCaptainStatus;
        private int xpGained;
        private float injuredUntilDay;
        private Rank promotedTo;

        private long contractId = -1L;
        private long campaignEventId = -1L;
        private int campaignEventMarketId = -1;
        private long campaignEventThreatSeed = -1L;
        private int civiliansAtRisk;
        private int civiliansRescued = -1;
        private int evacuationRepresentatives = -1;
        private int representativesEvacuated = -1;
        private AbandonedColonyArchiveOutcome colonyArchiveOutcome =
                AbandonedColonyArchiveOutcome.NONE;
        private int salvageEntitlement;
        private int salvageRecoveryBonusPct;
        private int salvageHighValueChancePct;

        private List<BoatLoss> boatsLost = Collections.emptyList();
        private int marinesLostAboard;
        private Set<String> survivingSoldierIds = Collections.emptySet();
        private Set<String> fallenSoldierIds = Collections.emptySet();
        private Set<String> deployedFireteamIds = Collections.emptySet();
        private Map<String, CombatTelemetryRow> soldierTelemetry = Collections.emptyMap();

        private Builder() {
        }

        /**
         * Copies every field the outcome inherits verbatim from the contract it
         * resolves: identity, type, risk, source, the pre-multiplier payout,
         * the physical target, and the contract/event lineage the campaign
         * tier bridges back on. Set the battle-derived fields separately.
         */
        public Builder mission(Mission mission) {
            this.missionId = mission.id;
            this.missionName = mission.name;
            this.missionType = mission.type;
            this.risk = mission.risk;
            this.missionSource = mission.source;
            this.payoutBase = mission.payout;
            this.targetPlanetName = mission.targetPlanetName;
            this.targetIndustryId = mission.targetIndustryId;
            this.targetFactionId = mission.targetFactionId;
            this.contractId = mission.contractId;
            this.campaignEventId = mission.campaignEventId;
            this.campaignEventMarketId = mission.campaignEventMarketId;
            this.campaignEventThreatSeed = mission.campaignEventThreatSeed;
            this.civiliansAtRisk = mission.civiliansAtRisk;
            return this;
        }

        /** Null-safe: a mission run with no captain leaves both name fields null. */
        public Builder captain(MarineCaptain captain) {
            this.captainId = captain != null ? captain.id() : null;
            this.captainName = captain != null ? captain.name() : null;
            return this;
        }

        public Builder victory(boolean victory) {
            this.victory = victory;
            return this;
        }

        public Builder missionId(String missionId) {
            this.missionId = missionId;
            return this;
        }

        public Builder missionName(String missionName) {
            this.missionName = missionName;
            return this;
        }

        public Builder missionType(MissionType missionType) {
            this.missionType = missionType;
            return this;
        }

        public Builder risk(RiskLevel risk) {
            this.risk = risk;
            return this;
        }

        public Builder missionSource(MissionSource missionSource) {
            this.missionSource = missionSource;
            return this;
        }

        public Builder payoutBase(int payoutBase) {
            this.payoutBase = payoutBase;
            return this;
        }

        public Builder payoutEarned(int payoutEarned) {
            this.payoutEarned = payoutEarned;
            return this;
        }

        public Builder marinesEngaged(int marinesEngaged) {
            this.marinesEngaged = marinesEngaged;
            return this;
        }

        public Builder marinesLost(int marinesLost) {
            this.marinesLost = marinesLost;
            return this;
        }

        public Builder alliesEngaged(int alliesEngaged) {
            this.alliesEngaged = alliesEngaged;
            return this;
        }

        public Builder alliesLost(int alliesLost) {
            this.alliesLost = alliesLost;
            return this;
        }

        public Builder targetPlanetName(String targetPlanetName) {
            this.targetPlanetName = targetPlanetName;
            return this;
        }

        public Builder targetIndustryId(String targetIndustryId) {
            this.targetIndustryId = targetIndustryId;
            return this;
        }

        public Builder targetFactionId(String targetFactionId) {
            this.targetFactionId = targetFactionId;
            return this;
        }

        public Builder captainId(String captainId) {
            this.captainId = captainId;
            return this;
        }

        public Builder captainName(String captainName) {
            this.captainName = captainName;
            return this;
        }

        public Builder priorCaptainStatus(Status priorCaptainStatus) {
            this.priorCaptainStatus = priorCaptainStatus;
            return this;
        }

        public Builder newCaptainStatus(Status newCaptainStatus) {
            this.newCaptainStatus = newCaptainStatus;
            return this;
        }

        public Builder xpGained(int xpGained) {
            this.xpGained = xpGained;
            return this;
        }

        public Builder injuredUntilDay(float injuredUntilDay) {
            this.injuredUntilDay = injuredUntilDay;
            return this;
        }

        public Builder promotedTo(Rank promotedTo) {
            this.promotedTo = promotedTo;
            return this;
        }

        public Builder contractId(long contractId) {
            this.contractId = contractId;
            return this;
        }

        public Builder campaignEventId(long campaignEventId) {
            this.campaignEventId = campaignEventId;
            return this;
        }

        public Builder campaignEventMarketId(int campaignEventMarketId) {
            this.campaignEventMarketId = campaignEventMarketId;
            return this;
        }

        public Builder campaignEventThreatSeed(long campaignEventThreatSeed) {
            this.campaignEventThreatSeed = campaignEventThreatSeed;
            return this;
        }

        public Builder civiliansAtRisk(int civiliansAtRisk) {
            this.civiliansAtRisk = civiliansAtRisk;
            return this;
        }

        public Builder civiliansRescued(int civiliansRescued) {
            this.civiliansRescued = civiliansRescued;
            return this;
        }

        /**
         * The sealed representative cohort and how much of it got out. Set as a
         * pair: the outcome discards both unless they describe a coherent
         * report on a civilian-rescue mission.
         */
        public Builder evacuationReport(int representatives, int evacuated) {
            this.evacuationRepresentatives = representatives;
            this.representativesEvacuated = evacuated;
            return this;
        }

        public Builder colonyArchiveOutcome(AbandonedColonyArchiveOutcome colonyArchiveOutcome) {
            this.colonyArchiveOutcome = colonyArchiveOutcome;
            return this;
        }

        public Builder salvageEntitlement(int salvageEntitlement) {
            this.salvageEntitlement = salvageEntitlement;
            return this;
        }

        public Builder salvageRecoveryBonusPct(int salvageRecoveryBonusPct) {
            this.salvageRecoveryBonusPct = salvageRecoveryBonusPct;
            return this;
        }

        public Builder salvageHighValueChancePct(int salvageHighValueChancePct) {
            this.salvageHighValueChancePct = salvageHighValueChancePct;
            return this;
        }

        public Builder boatsLost(List<BoatLoss> boatsLost) {
            this.boatsLost = boatsLost;
            return this;
        }

        public Builder marinesLostAboard(int marinesLostAboard) {
            this.marinesLostAboard = marinesLostAboard;
            return this;
        }

        public Builder survivingSoldierIds(Set<String> survivingSoldierIds) {
            this.survivingSoldierIds = survivingSoldierIds;
            return this;
        }

        public Builder fallenSoldierIds(Set<String> fallenSoldierIds) {
            this.fallenSoldierIds = fallenSoldierIds;
            return this;
        }

        public Builder deployedFireteamIds(Set<String> deployedFireteamIds) {
            this.deployedFireteamIds = deployedFireteamIds;
            return this;
        }

        public Builder soldierTelemetry(Map<String, CombatTelemetryRow> soldierTelemetry) {
            this.soldierTelemetry = soldierTelemetry;
            return this;
        }

        public MissionOutcome build() {
            return new MissionOutcome(this);
        }
    }

    /**
     * One of the company's boats that did not come home.
     *
     * @param boatId the campaign boat, so the deck can be struck by it
     * @param displayName her tail name, frozen because the deck will not have
     *     her by the time this is read
     * @param pattern what she was
     * @param passengersLost how many marines were still aboard when she went
     */
    public record BoatLoss(String boatId, String displayName, ShuttleType pattern,
                           int passengersLost) {
    }

    private static List<BoatLoss> immutableBoatLosses(List<BoatLoss> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<BoatLoss> copy = new ArrayList<>(source.size());
        for (BoatLoss loss : source) if (loss != null) copy.add(loss);
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, CombatTelemetryRow> immutableTelemetry(
            Map<String, CombatTelemetryRow> source) {
        if (source == null || source.isEmpty()) return Collections.emptyMap();
        Map<String, CombatTelemetryRow> copy = new LinkedHashMap<>();
        for (Map.Entry<String, CombatTelemetryRow> e : source.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) copy.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableIds(Set<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptySet();
        Set<String> copy = new HashSet<>();
        for (String id : source) if (id != null) copy.add(id);
        return Collections.unmodifiableSet(copy);
    }

    private static Set<String> immutableOrderedIds(Set<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptySet();
        Set<String> copy = new LinkedHashSet<>();
        for (String id : source) if (id != null) copy.add(id);
        return Collections.unmodifiableSet(copy);
    }
}
