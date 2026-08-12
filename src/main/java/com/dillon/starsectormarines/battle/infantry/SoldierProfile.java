package com.dillon.starsectormarines.battle.infantry;

/** Immutable individual combat profile: innate aptitude plus earned XP. */
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

    public SoldierProfile withExperience(int gainedXp) {
        return new SoldierProfile(aptitude, Math.max(0, experienceXp + gainedXp));
    }

    public String shortLabel() {
        return experienceTier().displayName.substring(0, 1)
                + "/" + aptitude.displayName.substring(0, 1);
    }
}
