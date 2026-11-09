package com.dillon.starsectormarines.campaign.ruins;

import java.util.Collection;

/**
 * The occupant draw ({@code ruins-expedition.md}, Occupants): a pure function of facts fixed when the sector was
 * generated (the sector seed, the market id, the ruins grade, the site's reservoir fact and its system's theme
 * tags). It reads nothing that changes over a campaign and stores nothing, so a site's occupant is the same on every
 * visit, save and retry.
 *
 * <ul>
 *   <li>On a reservoir world the Rav hold the site one time in two, before the table is drawn.</li>
 *   <li>Otherwise the grade's weights pick nobody, scavengers or derelict security; only a scattered site has a
 *       weight for nobody.</li>
 *   <li>In a {@value #THEME_DERELICT} system the derelict weight doubles; in a {@value #THEME_REMNANT} system it
 *       doubles and the security is the contingency lineage. Elsewhere it is Domain security.</li>
 * </ul>
 *
 * <p>The live sector's reader is {@link SectorRuinsOccupants}.
 */
public final class RuinsOccupants {

    /** Vanilla's {@code Tags.THEME_DERELICT}. */
    public static final String THEME_DERELICT = "theme_derelict";
    /** Vanilla's {@code Tags.THEME_REMNANT}. */
    public static final String THEME_REMNANT = "theme_remnant";

    /** The Rav's share of a reservoir world, drawn before the table. */
    static final double RAV_ON_RESERVOIR = 0.5;
    /** The derelict weight's multiplier in a derelict or Remnant system. */
    static final int THEMED_DERELICT_FACTOR = 2;

    private static final long RAV_SALT = 0x52554E5352415646L;
    private static final long TABLE_SALT = 0x52554E535441424CL;

    private RuinsOccupants() {}

    /**
     * @param sectorSeed the save's fixed seed
     * @param marketId   the ruins world's market id
     * @param grade      the ruins grade
     * @param reservoir  whether a bloom reservoir lies under the world ({@code colony-breach-nouns.md}, Reservoir)
     * @param systemTags the system's tags; null reads as none
     * @return the site's occupant; null when there is no market or grade
     */
    public static RuinsOccupant draw(long sectorSeed, String marketId, RuinsGrade grade, boolean reservoir,
                                     Collection<String> systemTags) {
        if (marketId == null || grade == null) return null;
        long site = mix(sectorSeed, marketId.hashCode());
        if (reservoir && unit(mix(site, RAV_SALT)) < RAV_ON_RESERVOIR) return RuinsOccupant.RAV;

        boolean remnant = systemTags != null && systemTags.contains(THEME_REMNANT);
        boolean derelictTheme = remnant || systemTags != null && systemTags.contains(THEME_DERELICT);
        int nobody = nobodyWeight(grade);
        int scavengers = scavengerWeight(grade);
        int derelict = derelictWeight(grade) * (derelictTheme ? THEMED_DERELICT_FACTOR : 1);

        double roll = unit(mix(site, TABLE_SALT)) * (nobody + scavengers + derelict);
        if (roll < nobody) return RuinsOccupant.NOBODY;
        if (roll < nobody + scavengers) return RuinsOccupant.SCAVENGERS;
        return remnant ? RuinsOccupant.CONTINGENCY_SECURITY : RuinsOccupant.DOMAIN_SECURITY;
    }

    static int nobodyWeight(RuinsGrade grade) {
        return grade == RuinsGrade.SCATTERED ? 35 : 0;
    }

    static int scavengerWeight(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED, EXTENSIVE -> 45;
            case WIDESPREAD -> 55;
            case VAST -> 35;
        };
    }

    static int derelictWeight(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> 20;
            case WIDESPREAD -> 45;
            case EXTENSIVE -> 55;
            case VAST -> 65;
        };
    }

    static long mix(long a, long b) {
        long value = a * 0x9E3779B97F4A7C15L ^ (b + 0x632BE59BD9B4E019L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
