package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;

/**
 * A kind of airframe a side's pool holds. The kind decides what flies and what the sortie does over the target; the
 * pool only counts airframes of each kind.
 *
 * <p>UNTUNED: the profiles and pass counts are first-pass figures. A bomber makes one bombing pass (its whole stick
 * is the pass); a fighter strafes twice.
 */
public enum AirPoolKind {
    /** Releases a stick of bombs in one pass and leaves. Strong against structures and infantry in the open. */
    BOMBER(FighterProfile.DAGGER, 1, AirOrdnance.STRIKE_BOMBS),
    /** Strafes with its nose cannon. Lighter per pass than a bomber, and flies a second pass. */
    FIGHTER(FighterProfile.BROADSWORD, 2, null);

    private final FighterProfile profile;
    private final int passes;
    private final AirOrdnance ordnance;

    AirPoolKind(FighterProfile profile, int passes, AirOrdnance ordnance) {
        this.profile = profile;
        this.passes = passes;
        this.ordnance = ordnance;
    }

    /** The airframe that flies a sortie of this kind. */
    public FighterProfile profile() {
        return profile;
    }

    /** Attack passes a sortie of this kind makes before it turns for home. */
    public int passes() {
        return passes;
    }

    /** What a sortie of this kind carries: the called bomber's heavier stick, or the airframe's own gun. */
    public AirOrdnance ordnance() {
        return ordnance != null ? ordnance : profile.ordnance();
    }
}
