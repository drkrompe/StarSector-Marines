package com.dillon.starsectormarines.campaign.ruins;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the fleet's sensors read of a ruins site from orbit ({@code ruins-expedition.md}, Briefing): the assessment
 * naming the occupant, a strength band set by the grade, and two or three survey readings from that occupant's pool.
 * Every reading is true of the site and never names a place on the map or a unit count. The draw is seeded from the
 * site alone, so a revisit reads the same.
 *
 * <p>Each occupant reads through its own instruments, so each has a {@link Pool}, which {@link #poolFor} names: the
 * scavengers' comms, heat and traces; an empty site's quiet, whose structure and power hint at its works; the Rav's
 * biological lifesigns; derelict security's movement and power, its contingency lineage with Tri-Tachyon codes.
 *
 * <p>This class holds string keys only; the briefing resolves them.
 */
public final class RuinsSurvey {

    /** The fewest and most readings a briefing shows. */
    static final int MIN_READINGS = 2;
    static final int MAX_READINGS = 3;

    private static final long SALT = 0x5255494E53535256L;

    /** How strong the occupant reads, set by the grade: larger ruins hold more. */
    public enum Band {
        LIGHT("ruinsSurveyBandLight"),
        MODERATE("ruinsSurveyBandModerate"),
        HEAVY("ruinsSurveyBandHeavy"),
        SEVERE("ruinsSurveyBandSevere");

        /** The band's name in the briefing. */
        public final String nameKey;

        Band(String nameKey) {
            this.nameKey = nameKey;
        }

        public static Band of(RuinsGrade grade) {
            if (grade == null) throw new IllegalArgumentException("a band needs a grade");
            return switch (grade) {
                case SCATTERED -> LIGHT;
                case WIDESPREAD -> MODERATE;
                case EXTENSIVE -> HEAVY;
                case VAST -> SEVERE;
            };
        }
    }

    /**
     * One occupant's kind of reading.
     *
     * @param assessmentKey the line naming the occupant, always true
     * @param bandKey       the strength line, read through the same instruments; {@code {0}} is the band's name
     * @param readingKeys   the pool the site's readings are drawn from; at least {@link #MAX_READINGS}
     */
    public record Pool(String assessmentKey, String bandKey, List<String> readingKeys) {
        public Pool {
            if (assessmentKey == null || bandKey == null || readingKeys == null
                    || readingKeys.size() < MAX_READINGS || readingKeys.stream().distinct().count() != readingKeys.size()) {
                throw new IllegalArgumentException("a pool needs an assessment, a band line and enough distinct readings");
            }
            readingKeys = List.copyOf(readingKeys);
        }
    }

    /** A pirate salvage crew: comms, heat and the traces of its work. */
    public static final Pool SCAVENGERS = new Pool(
            "ruinsSurveyScavengersAssessment",
            "ruinsSurveyScavengersBand",
            List.of("ruinsSurveyScavengersComms",
                    "ruinsSurveyScavengersHeat",
                    "ruinsSurveyScavengersBoat",
                    "ruinsSurveyScavengersTracks",
                    "ruinsSurveyScavengersSpoil"));

    /** An empty site: quiet, with the structure and power that hint at its works ({@code ruins-works.md}). */
    public static final Pool NOBODY = new Pool(
            "ruinsSurveyNobodyAssessment",
            "ruinsSurveyNobodyBand",
            List.of("ruinsSurveyNobodySilent",
                    "ruinsSurveyNobodyPowerHall",
                    "ruinsSurveyNobodySeal",
                    "ruinsSurveyNobodyCollapse",
                    "ruinsSurveyNobodyRecords"));

    /** An old seeding site's sleepers: biological lifesigns. */
    public static final Pool RAV = new Pool(
            "ruinsSurveyRavAssessment",
            "ruinsSurveyRavBand",
            List.of("ruinsSurveyRavLifesigns",
                    "ruinsSurveyRavSeismic",
                    "ruinsSurveyRavWarm",
                    "ruinsSurveyRavDark",
                    "ruinsSurveyRavBores"));

    /** Domain security, a live ruin: movement and power. */
    public static final Pool DOMAIN_SECURITY = new Pool(
            "ruinsSurveyDerelictAssessment",
            "ruinsSurveyDerelictBand",
            List.of("ruinsSurveyDerelictPatrols",
                    "ruinsSurveyDerelictPower",
                    "ruinsSurveyDerelictPing",
                    "ruinsSurveyDerelictRelays",
                    "ruinsSurveyDerelictFlock"));

    /** Contingency security: the same instruments, with the lineage's own codes and its lockdown. */
    public static final Pool CONTINGENCY_SECURITY = new Pool(
            "ruinsSurveyContingencyAssessment",
            "ruinsSurveyDerelictBand",
            List.of("ruinsSurveyContingencyCodes",
                    "ruinsSurveyContingencyLockdown",
                    "ruinsSurveyDerelictPatrols",
                    "ruinsSurveyDerelictPower",
                    "ruinsSurveyDerelictFlock"));

    /**
     * A site's briefing, as string keys.
     *
     * @param assessmentKey the occupant's line
     * @param band          the grade's strength band
     * @param bandKey       the strength line, formatted with the band's name
     * @param readingKeys   the site's readings, in the order shown
     */
    public record Reading(String assessmentKey, Band band, String bandKey, List<String> readingKeys) {
        public Reading {
            readingKeys = List.copyOf(readingKeys);
        }
    }

    private RuinsSurvey() {}

    /** The occupant's own pool; null for no occupant. */
    public static Pool poolFor(RuinsOccupant occupant) {
        if (occupant == null) return null;
        return switch (occupant) {
            case NOBODY -> NOBODY;
            case SCAVENGERS -> SCAVENGERS;
            case RAV -> RAV;
            case DOMAIN_SECURITY -> DOMAIN_SECURITY;
            case CONTINGENCY_SECURITY -> CONTINGENCY_SECURITY;
        };
    }

    /**
     * @param pool     the occupant's pool
     * @param marketId the ruins world's market id, which seeds the draw
     * @param grade    the ruins grade, which sets the band
     */
    public static Reading read(Pool pool, String marketId, RuinsGrade grade) {
        if (pool == null || marketId == null || grade == null) throw new IllegalArgumentException("a reading needs a site");
        Random rng = new Random(SALT ^ marketId.hashCode() * 0x9E3779B97F4A7C15L ^ pool.assessmentKey().hashCode());
        int count = MIN_READINGS + rng.nextInt(MAX_READINGS - MIN_READINGS + 1);
        List<String> left = new ArrayList<>(pool.readingKeys());
        List<String> drawn = new ArrayList<>(count);
        for (int i = 0; i < count; i++) drawn.add(left.remove(rng.nextInt(left.size())));
        return new Reading(pool.assessmentKey(), Band.of(grade), pool.bandKey(), drawn);
    }
}
