package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;

/**
 * A kind of airframe a side's pool holds. The kind decides what flies and what the sortie does over the target; the
 * pool only counts airframes of each kind.
 *
 * <p>The two kinds are not the same thing at different prices. A bomber carries a heavy stick that breaks works and
 * removes a massed formation, makes one pass, and is the dear airframe: it regenerates slowly. A fighter carries a nose
 * cannon whose rounds are light (they open soft ground and do little to a hardened position), makes two passes, flies
 * its attack low and so takes more of an anti-air post's fire, and is the cheap airframe that a side replaces quickly.
 * It is also the only kind that fights in the air, which is why cover is a fighter mission.
 *
 * <p>UNTUNED: the profiles, pass counts and exposures are first-pass figures.
 */
public enum AirPoolKind {
    /** Releases a stick of bombs in one pass and leaves. Strong against structures and infantry in the open. */
    BOMBER(FighterProfile.DAGGER, 1, AirOrdnance.STRIKE_BOMBS, 1.0f),
    /** Strafes with its nose cannon. Lighter per pass than a bomber, flies a second pass, and flies it lower. */
    FIGHTER(FighterProfile.BROADSWORD, 2, null, 1.5f);

    private final FighterProfile profile;
    private final int passes;
    private final AirOrdnance ordnance;
    private final float aaExposure;

    AirPoolKind(FighterProfile profile, int passes, AirOrdnance ordnance, float aaExposure) {
        this.profile = profile;
        this.passes = passes;
        this.ordnance = ordnance;
        this.aaExposure = aaExposure;
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

    /**
     * How much more of an anti-air post's fire a sortie of this kind takes than a bomber's pass does. A gun run is
     * flown nearer the deck and held on its line through two passes and the turn between them; a bomber is over the
     * defence once. Both the air system's drain and the side's estimate of what a pass will cost use it, so a plan
     * and the sortie it asks for agree.
     */
    public float aaExposure() {
        return aaExposure;
    }
}
