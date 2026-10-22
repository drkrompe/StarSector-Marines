package com.dillon.starsectormarines.campaign.bloom;

/**
 * The owner's three picks for blooms, each a preset ({@code colony-breach-nouns.md}, Campaign: blooms): how punishing
 * an ignored bloom is, how often blooms erupt, and whether the player's own colonies can bloom.
 * Everything else about a bloom is a rule, not a pick, and lives in {@link RavBloomRules}.
 */
public final class BloomTuning {

    public enum Severity {
        /** At most one size lost, never spreads, burns out quickly. */
        SCAR(60, 1, 0.0, 0, 120, false),
        /** Eaten down to the floor; can seed one neighbour. */
        DARK_AND_SPREAD(60, Integer.MAX_VALUE, 0.04, 1, 240, false),
        /** Faster, a colony at the floor decivilises, up to two seeded neighbours. */
        DEADLY(45, Integer.MAX_VALUE, 0.06, 2, 240, true);

        public final int sizeLossDays;
        public final int maxSizeLoss;
        public final double weeklySpreadChance;
        public final int maxSeeded;
        public final int burnoutDays;
        public final boolean decivilizesAtFloor;

        Severity(int sizeLossDays, int maxSizeLoss, double weeklySpreadChance, int maxSeeded,
                 int burnoutDays, boolean decivilizesAtFloor) {
            this.sizeLossDays = sizeLossDays;
            this.maxSizeLoss = maxSizeLoss;
            this.weeklySpreadChance = weeklySpreadChance;
            this.maxSeeded = maxSeeded;
            this.burnoutDays = burnoutDays;
            this.decivilizesAtFloor = decivilizesAtFloor;
        }
    }

    public enum Frequency {
        RARE(1.0), SEASONAL(3.0), FREQUENT(6.0);

        /** Blooms woken a year sector-wide while below the running cap; spread comes on top. */
        public final double bloomsPerYear;

        Frequency(double bloomsPerYear) {
            this.bloomsPerYear = bloomsPerYear;
        }
    }

    public enum PlayerColonies {
        EXPOSED,
        /** Safe until the player has answered a bloom somewhere. */
        AFTER_FIRST,
        NEVER
    }

    public static final BloomTuning DEFAULT = new BloomTuning(
            Severity.DARK_AND_SPREAD, Frequency.SEASONAL, PlayerColonies.EXPOSED);

    public final Severity severity;
    public final Frequency frequency;
    public final PlayerColonies playerColonies;

    public BloomTuning(Severity severity, Frequency frequency, PlayerColonies playerColonies) {
        this.severity = severity;
        this.frequency = frequency;
        this.playerColonies = playerColonies;
    }

    /** The live game's tuning: the defaults, or {@code -Dmarines.bloom.*} overrides for a control run. */
    public static BloomTuning live() {
        return new BloomTuning(
                pick(Severity.class, "marines.bloom.severity", DEFAULT.severity),
                pick(Frequency.class, "marines.bloom.frequency", DEFAULT.frequency),
                pick(PlayerColonies.class, "marines.bloom.playerColonies", DEFAULT.playerColonies));
    }

    static <E extends Enum<E>> E pick(Class<E> type, String property, E fallback) {
        String value = System.getProperty(property);
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return severity + "/" + frequency + "/" + playerColonies;
    }
}
