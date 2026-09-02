package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

/**
 * How many squads the protected market fields beside the company.
 *
 * <p><b>This is a placeholder, and deliberately a crude one.</b>
 * {@code polity-ground-doctrine.md} says what the number should be: a market's
 * headcount is its vanilla ground-defence stat with the company's own stationed
 * strength subtracted — so a detachment never counts twice — scaled by stability
 * the way vanilla scales fleet numbers. None of that exists yet: there is no
 * derived roster, no released-kit set, and nothing that reads a stationed
 * strength back out of the campaign. Until there is, a defence still needs
 * somebody standing on the market's side of the line, and this reads the three
 * signals the bridge already carries — size, defence rating, stability — and
 * returns a squad count in the same direction the real derivation will.
 *
 * <p>A pure function of the profile, so it can be asked without a battle, and so
 * the day the derivation lands there is exactly one place that answers this.
 *
 * <p>Faction identity is not an input. The market's own owner decides what its
 * troops <em>carry</em> ({@link GroundRosterRegistry}); how many of them there
 * are is a reading of the market. A defender-faction override therefore cannot
 * move this number, which is the property that keeps a faction swap a controlled
 * comparison — see {@code campaign-battle-bridge-nouns.md}.
 */
public final class AlliedGarrisonSize {

    /** A defence rating at or above this fields one extra squad. */
    private static final int WELL_DEFENDED = 3;

    /** Below this stability the market cannot turn its full garrison out. */
    private static final int SETTLED_STABILITY = 4;

    private AlliedGarrisonSize() {}

    /**
     * Squads the market's garrison fields, never negative.
     *
     * <p>Zero when no market backs the battle — {@link TargetProfile#NEUTRAL},
     * the reading a story op or a headless fixture gets. Nothing owns the ground,
     * so nobody defends it. Every real market fields at least one squad however
     * small or unstable it is: a colony with a garrison of nobody is a colony
     * that was already taken.
     */
    public static int squads(TargetProfile profile) {
        if (profile == null || profile.marketSize() <= 0) return 0;
        int squads = baseSquads(profile.marketSize());
        if (profile.defenseLevel() >= WELL_DEFENDED) squads++;
        if (profile.stability() < SETTLED_STABILITY) squads--;
        return Math.max(1, squads);
    }

    /** Population is the dominant term: a bigger place has more people to arm. */
    private static int baseSquads(int marketSize) {
        if (marketSize <= 3) return 1;
        if (marketSize == 4) return 2;
        if (marketSize == 5) return 3;
        return 4;
    }
}
