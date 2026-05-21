package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;

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
    public final MissionSource  missionSource;
    public final int            payoutEarned;
    public final int            marinesEngaged;
    public final int            marinesLost;
    /** Planet name the mission targeted; null if no specific target. */
    public final String         targetPlanetName;
    /** Industry id the mission targeted; null if no industry-specific target. */
    public final String         targetIndustryId;

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
    /** Final salvage % entitlement after captain trait + fleet rig modifiers (0..255). 0 = no salvage. */
    public final int     salvageEntitlement;

    public MissionOutcome(boolean victory,
                          String missionId, String missionName,
                          MissionType missionType, MissionSource missionSource,
                          int payoutEarned, int marinesEngaged, int marinesLost,
                          String captainId, String captainName,
                          Status priorCaptainStatus, Status newCaptainStatus,
                          int xpGained, float injuredUntilDay, Rank promotedTo,
                          String targetPlanetName, String targetIndustryId,
                          long contractId, int salvageEntitlement) {
        this.victory            = victory;
        this.missionId          = missionId;
        this.missionName        = missionName;
        this.missionType        = missionType;
        this.missionSource      = missionSource != null ? missionSource : MissionSource.GENERATED;
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
        this.contractId         = contractId;
        this.salvageEntitlement = salvageEntitlement;
    }
}
