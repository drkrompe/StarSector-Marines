package com.dillon.starsectormarines.campaign.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * The vault prize draw ({@code ruins-expedition.md}, The vault and its prize): a pure function of facts fixed when
 * the sector was generated, like the occupant draw, so a site holds the same prize on every visit, save and retry.
 *
 * <ul>
 *   <li>The grade weighs the families: goods lead at a scattered site, and colony items appear only from
 *       widespread up.</li>
 *   <li>The occupant leans it: derelict security doubles the AI core weight; the Rav and an empty site keep no
 *       cores, and the Rav only living or deep-ground items; only derelict security keeps a drone replicator.</li>
 *   <li>An AI core's tier rises with the grade; a colony item is drawn by vanilla's {@code rare_tech} weights.</li>
 * </ul>
 *
 * <p>The survey's tell ({@link #hintedCore}) is never false about the family; a core's tier reads right three times
 * in four, else one step off (pick {@code ruins-prize-hint} a). The weights are untuned starting points.
 */
public final class RuinsVaultPrizes {

    /** How often a core's hinted tier is its true tier. */
    static final double CORE_HINT_TRUE = 0.75;
    /** The AI core weight's multiplier on a derelict-held site. */
    static final int DERELICT_CORE_FACTOR = 2;

    private static final long FAMILY_SALT = 0x5641554C54464D4CL;
    private static final long DETAIL_SALT = 0x5641554C54445449L;
    private static final long HINT_SALT = 0x5641554C5448494EL;

    private RuinsVaultPrizes() {}

    /**
     * @return the site's vault prize; null when there is no market, grade or occupant
     */
    public static RuinsVaultPrize draw(long sectorSeed, String marketId, RuinsGrade grade, RuinsOccupant occupant) {
        if (marketId == null || grade == null || occupant == null) return null;
        long site = RuinsOccupants.mix(sectorSeed, marketId.hashCode());
        RuinsVaultPrize.Family family = family(RuinsOccupants.unit(RuinsOccupants.mix(site, FAMILY_SALT)), grade,
                occupant);
        double detail = RuinsOccupants.unit(RuinsOccupants.mix(site, DETAIL_SALT));
        return switch (family) {
            case GOODS -> new RuinsVaultPrize(family, RuinsVaultTheme.DEPOT, null, 0);
            case TECH -> new RuinsVaultPrize(family, RuinsVaultTheme.ARMORY, null, 1);
            case AI_CORE -> {
                String core = core(detail, grade);
                yield new RuinsVaultPrize(family, RuinsVaultTheme.SERVER_HALL, core, coreStakes(core));
            }
            case COLONY_ITEM -> {
                RuinsVaultTheme theme = item(detail, occupant);
                yield new RuinsVaultPrize(family, theme, theme.itemId,
                        theme == RuinsVaultTheme.PRISTINE_NANOFORGE ? 3 : 2);
            }
        };
    }

    /**
     * The AI core tier the survey reads for a core prize: the true tier three times in four, else one step off
     * (toward beta from either end). Null for any other prize, whose theme alone is its tell.
     */
    public static String hintedCore(long sectorSeed, String marketId, RuinsVaultPrize prize) {
        if (prize == null || prize.family() != RuinsVaultPrize.Family.AI_CORE) return null;
        long site = RuinsOccupants.mix(sectorSeed, marketId.hashCode());
        double roll = RuinsOccupants.unit(RuinsOccupants.mix(site, HINT_SALT));
        if (roll < CORE_HINT_TRUE) return prize.itemId();
        return switch (prize.itemId()) {
            case RuinsVaultPrize.GAMMA_CORE, RuinsVaultPrize.ALPHA_CORE -> RuinsVaultPrize.BETA_CORE;
            default -> roll < (1 + CORE_HINT_TRUE) / 2 ? RuinsVaultPrize.GAMMA_CORE : RuinsVaultPrize.ALPHA_CORE;
        };
    }

    /** Family weights, goods, tech, AI core, colony item, for a grade before the occupant's lean. */
    static int[] familyWeights(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> new int[] {60, 30, 10, 0};
            case WIDESPREAD -> new int[] {40, 35, 20, 5};
            case EXTENSIVE -> new int[] {25, 35, 25, 15};
            case VAST -> new int[] {15, 30, 30, 25};
        };
    }

    /** Core tier weights, gamma, beta, alpha, for a grade. */
    static int[] coreWeights(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> new int[] {100, 0, 0};
            case WIDESPREAD -> new int[] {70, 30, 0};
            case EXTENSIVE -> new int[] {30, 50, 20};
            case VAST -> new int[] {10, 50, 40};
        };
    }

    private static RuinsVaultPrize.Family family(double roll, RuinsGrade grade, RuinsOccupant occupant) {
        int[] weights = familyWeights(grade);
        int core = weights[2];
        if (occupant.isDerelict()) core *= DERELICT_CORE_FACTOR;
        if (occupant == RuinsOccupant.RAV || occupant == RuinsOccupant.NOBODY) core = 0;
        int[] leaned = {weights[0], weights[1], core, weights[3]};
        RuinsVaultPrize.Family[] families = RuinsVaultPrize.Family.values();
        return families[pick(roll, leaned)];
    }

    private static String core(double roll, RuinsGrade grade) {
        String[] cores = {RuinsVaultPrize.GAMMA_CORE, RuinsVaultPrize.BETA_CORE, RuinsVaultPrize.ALPHA_CORE};
        return cores[pick(roll, coreWeights(grade))];
    }

    private static RuinsVaultTheme item(double roll, RuinsOccupant occupant) {
        List<RuinsVaultTheme> themes = new ArrayList<>();
        for (RuinsVaultTheme theme : RuinsVaultTheme.values()) {
            if (theme.family == RuinsVaultPrize.Family.COLONY_ITEM && theme.allowedFor(occupant)) themes.add(theme);
        }
        int[] weights = new int[themes.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = themes.get(i).rareTechWeight;
        return themes.get(pick(roll, weights));
    }

    private static int coreStakes(String core) {
        return switch (core) {
            case RuinsVaultPrize.ALPHA_CORE -> 3;
            case RuinsVaultPrize.BETA_CORE -> 2;
            default -> 1;
        };
    }

    private static int pick(double roll, int[] weights) {
        int total = 0;
        for (int weight : weights) total += weight;
        double at = roll * total;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] > 0 && at < weights[i]) return i;
            at -= weights[i];
        }
        for (int i = weights.length - 1; i >= 0; i--) if (weights[i] > 0) return i;
        throw new IllegalArgumentException("no positive weight");
    }
}
