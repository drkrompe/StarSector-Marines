package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Frozen snapshot of a completed mission's results — everything the RESULTS
 * screen displays and everything {@link MissionResolver#apply} writes back to
 * the player's game state. Compute it once when the battle ends, apply it
 * once, then read it for display. Immutable so it can't drift after creation.
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
    /** Frozen civilian stakes from mission creation; zero otherwise. */
    public final int     civiliansAtRisk;
    /** Explicit battle evacuation report; {@code -1} means no valid report. */
    public final int     civiliansRescued;
    /** Salvage percentage consumed by the loot roll (0..255). 0 = no salvage. */
    public final int     salvageEntitlement;
    /** Frozen captain + fleet recovery-pool bonus, in percentage points. */
    public final int     salvageRecoveryBonusPct;
    /** Frozen deterministic chance for the high-value catalog roll. */
    public final int     salvageHighValueChancePct;
    public final Set<String> survivingSoldierIds;
    public final Set<String> fallenSoldierIds;

    public MissionOutcome(boolean victory,
                          String missionId, String missionName,
                          MissionType missionType, RiskLevel risk, MissionSource missionSource,
                          int payoutBase, int payoutEarned, int marinesEngaged, int marinesLost,
                          String captainId, String captainName,
                          Status priorCaptainStatus, Status newCaptainStatus,
                          int xpGained, float injuredUntilDay, Rank promotedTo,
                          String targetPlanetName, String targetIndustryId, String targetFactionId,
                          long contractId, long campaignEventId,
                          int campaignEventMarketId, int civiliansAtRisk,
                          int civiliansRescued, int salvageEntitlement,
                          int salvageRecoveryBonusPct, int salvageHighValueChancePct) {
        this(victory, missionId, missionName, missionType, risk, missionSource,
                payoutBase, payoutEarned, marinesEngaged, marinesLost,
                captainId, captainName, priorCaptainStatus, newCaptainStatus,
                xpGained, injuredUntilDay, promotedTo, targetPlanetName,
                targetIndustryId, targetFactionId, contractId, campaignEventId,
                campaignEventMarketId, civiliansAtRisk, civiliansRescued,
                salvageEntitlement, salvageRecoveryBonusPct,
                salvageHighValueChancePct, Collections.emptySet(), Collections.emptySet());
    }

    public MissionOutcome(boolean victory,
                          String missionId, String missionName,
                          MissionType missionType, RiskLevel risk, MissionSource missionSource,
                          int payoutBase, int payoutEarned, int marinesEngaged, int marinesLost,
                          String captainId, String captainName,
                          Status priorCaptainStatus, Status newCaptainStatus,
                          int xpGained, float injuredUntilDay, Rank promotedTo,
                          String targetPlanetName, String targetIndustryId, String targetFactionId,
                          long contractId, long campaignEventId,
                          int campaignEventMarketId, int civiliansAtRisk,
                          int civiliansRescued, int salvageEntitlement,
                          int salvageRecoveryBonusPct, int salvageHighValueChancePct,
                          Set<String> survivingSoldierIds, Set<String> fallenSoldierIds) {
        this.victory            = victory;
        this.missionId          = missionId;
        this.missionName        = missionName;
        this.missionType        = missionType;
        this.risk               = risk;
        this.missionSource      = missionSource != null ? missionSource : MissionSource.GENERATED;
        this.payoutBase         = payoutBase;
        this.payoutEarned       = payoutEarned;
        this.marinesEngaged     = marinesEngaged;
        this.marinesLost        = marinesLost;
        this.captainId          = captainId;
        this.captainName        = captainName;
        this.priorCaptainStatus = priorCaptainStatus;
        this.newCaptainStatus   = newCaptainStatus;
        this.xpGained           = xpGained;
        this.injuredUntilDay    = injuredUntilDay;
        this.promotedTo         = promotedTo;
        this.targetPlanetName   = targetPlanetName;
        this.targetIndustryId   = targetIndustryId;
        this.targetFactionId    = targetFactionId;
        this.contractId         = contractId;
        this.campaignEventId = campaignEventId > 0L ? campaignEventId : -1L;
        this.campaignEventMarketId = this.campaignEventId > 0L
                ? Math.max(-1, campaignEventMarketId) : -1;
        this.civiliansAtRisk = this.campaignEventId > 0L
                ? Math.max(0, civiliansAtRisk) : 0;
        this.civiliansRescued = this.campaignEventId > 0L
                ? civiliansRescued : -1;
        this.salvageEntitlement = salvageEntitlement;
        this.salvageRecoveryBonusPct = Math.max(0, salvageRecoveryBonusPct);
        this.salvageHighValueChancePct = Math.max(0, Math.min(100, salvageHighValueChancePct));
        this.survivingSoldierIds = immutableIds(survivingSoldierIds);
        this.fallenSoldierIds = immutableIds(fallenSoldierIds);
    }

    private static Set<String> immutableIds(Set<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptySet();
        Set<String> copy = new HashSet<>();
        for (String id : source) if (id != null) copy.add(id);
        return Collections.unmodifiableSet(copy);
    }
}
