package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

/**
 * How many squads the protected market fields beside the company.
 *
 * <p>This is {@code polity-ground-doctrine.md}'s "Numbers" in code: a market's
 * headcount is <b>its own vanilla ground-defence strength, less the company's
 * stationed contribution there</b>, scaled by the mission's numbers doctrine and
 * divided into squads. The subtraction is what stops a detachment counting
 * twice — a stationed Garrison writes a flat modifier onto the same stat, so
 * without it the company's own marines would turn out again as somebody else's
 * militia.
 *
 * <p><b>Stability is already in the number and must not be applied again.</b>
 * {@code MarketCMD.getDefenderStr} reads the ground-defence stat's modified
 * value, and vanilla's own population industry writes the stability multiplier
 * ({@code 0.25 + stability/10 * 0.75}) onto that stat. A colony at stability 3
 * therefore arrives here already down to half of what it fields at stability 10.
 *
 * <p>A pure function of the profile and one multiplier, so it can be asked
 * without a battle, and so there is exactly one place that answers this.
 *
 * <p>Faction identity is not an input. The market's own owner decides what its
 * troops <em>carry</em> ({@link GroundRosterRegistry}); how many of them there
 * are is a reading of the market. A defender-faction override therefore cannot
 * move this number, which is the property that keeps a faction swap a controlled
 * comparison — see {@code campaign-battle-bridge-nouns.md}.
 *
 * <h2>Where the constants come from</h2>
 *
 * Vanilla's ground defence for a market is
 * {@code PopulationAndInfrastructure.getBaseGroundDefenses(size)} — 10/20/50 for
 * sizes 1–3, then {@code (size - 3) * 100} — multiplied by the stability term
 * above and by each defence industry's own factor: Ground Defenses x2, Heavy
 * Batteries x3, Military Base x1.2, High Command x1.3, Planetary Shield x3,
 * orbital station x1.5 through x3.
 *
 * <p>So the reference colony — size 5, no defence industry, stability 6 — is
 * {@code 200 * (0.25 + 0.45) = 140}. {@link #STRENGTH_PER_SQUAD} is 45 because
 * {@code 140 / 45 = 3.1}, and three fireteams is the garrison a plain colony
 * should turn out. The same colony at stability 3 fields 2, at stability 10
 * fields 4; a size-3 outpost fields the floor; a size-8 world fields 8.
 *
 * <p>{@link #MAX_SQUADS} is 8 because that is where the well-defended end lands
 * on its own: the same size-5 colony with Heavy Batteries is {@code 140 * 3 =
 * 420}, or 9.3 squads uncapped. The cap is what stops a size-10 capital with a
 * Star Fortress and a shield fielding a battalion the company is incidental to —
 * the allied garrison is a line beside the player, not the battle.
 */
public final class AlliedGarrisonSize {

    /**
     * Vanilla ground-defence strength one allied fireteam stands for. See the
     * class notes: sized so a plain size-5 colony fields three squads.
     */
    public static final float STRENGTH_PER_SQUAD = 45f;

    /**
     * Every real market fields at least this, however small, unstable, or fully
     * garrisoned by the company it is. A colony with a garrison of nobody is a
     * colony that was already taken.
     */
    public static final int MIN_SQUADS = 1;

    /** The allied line stays a line: a well-defended world caps here. */
    public static final int MAX_SQUADS = 8;

    private AlliedGarrisonSize() {}

    /** As {@link #squads(TargetProfile, float)} at the doctrine-neutral multiplier. */
    public static int squads(TargetProfile profile) {
        return squads(profile, 1f);
    }

    /**
     * Squads the market's garrison fields, never negative.
     *
     * <p>Zero when no market backs the battle — {@link TargetProfile#NEUTRAL},
     * the reading a story op or a headless fixture gets. Nothing owns the ground,
     * so nobody defends it.
     *
     * @param profile            the target market's bridge read; its
     *                           {@link TargetProfile#groundDefence()} already
     *                           carries vanilla's stability scaling
     * @param strengthMultiplier the mission's numbers doctrine, {@code 1} for a
     *                           mission that states none. Zero or negative reads
     *                           as none of the market's strength turning out,
     *                           which still leaves {@link #MIN_SQUADS}.
     */
    public static int squads(TargetProfile profile, float strengthMultiplier) {
        if (profile == null || profile.marketSize() <= 0) return 0;
        float net = Math.max(0f, profile.groundDefence() - profile.stationedStrength());
        float mult = Float.isNaN(strengthMultiplier) || strengthMultiplier < 0f
                ? 0f : strengthMultiplier;
        int squads = Math.round(net * mult / STRENGTH_PER_SQUAD);
        return Math.max(MIN_SQUADS, Math.min(MAX_SQUADS, squads));
    }
}
