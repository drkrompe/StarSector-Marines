package com.dillon.starsectormarines.battle.infantry;

/**
 * Immutable individual combat profile: innate aptitude plus the experience band
 * this marine deploys at.
 *
 * <p>The band is <em>issued</em> rather than earned — {@code SquadExperienceStandard}
 * resolves it from the kit the marine carries ({@code progression-nouns.md}). The
 * field remains an XP integer because {@link ExperienceTier} bands are defined by
 * XP thresholds, not because a rank-and-file marine accumulates one.
 *
 * <p>A profile is therefore fixed when the marine is equipped and never rises
 * mid-battle: there is deliberately no in-sim way to earn a band. Changing a
 * shooter's quality means re-equipping them, which is what
 * {@code CombatService.equipPrimaryWeapon} is for.
 */
public record SoldierProfile(SoldierAptitude aptitude, int experienceXp) {

    /** Neutral compatibility profile for old spawn sites and non-soldier users. */
    public static final SoldierProfile REGULAR =
            new SoldierProfile(SoldierAptitude.STEADY, ExperienceTier.REGULAR.minimumXp);

    public SoldierProfile {
        if (aptitude == null) aptitude = SoldierAptitude.STEADY;
        experienceXp = Math.max(0, experienceXp);
    }

    public ExperienceTier experienceTier() {
        return ExperienceTier.fromXp(experienceXp);
    }

    public String shortLabel() {
        return experienceTier().displayName.substring(0, 1)
                + "/" + aptitude.displayName.substring(0, 1);
    }
}
