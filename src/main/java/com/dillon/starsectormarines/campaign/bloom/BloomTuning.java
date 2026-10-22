package com.dillon.starsectormarines.campaign.bloom;

/**
 * The owner's picks for blooms, each a preset ({@code colony-breach-nouns.md}, Campaign: blooms):
 * how much an unanswered bloom costs, how often blooms erupt, and whether the player's own
 * colonies can bloom. Everything else about a bloom is a rule, not a pick, and lives in
 * {@link RavBloomRules}.
 *
 * <p>Severity is a tier, not a switch: every consequence of an unanswered bloom (size lost,
 * workings disrupted, unrest left behind, neighbours seeded, a colony lost) is read from the tier,
 * so raising it later is a new preset rather than a rewrite. {@link Severity#BENIGN} is the first
 * stage the mod ships, because the systems for worlds actually lost or held do not exist yet; the
 * harsher tiers are the intended direction and stay runnable for evidence.
 */
public final class BloomTuning {

    public enum Severity {
        /**
         * The first stage: the owner's military puts an unanswered bloom down on its own, and all
         * it costs the market is the condition while it lasts.
         */
        BENIGN(0, 0, 0.0, 0, 60, false, false, 0, BloomEnd.PUT_DOWN),
        /** At most one size lost, never spreads, burns out quickly. */
        SCAR(60, 1, 0.0, 0, 120, false, true, 2, BloomEnd.BURNED_OUT),
        /** Eaten down to the floor; can seed one neighbour. */
        DARK_AND_SPREAD(60, Integer.MAX_VALUE, 0.04, 1, 240, false, true, 2, BloomEnd.BURNED_OUT),
        /** Faster, a colony at the floor decivilises, up to two seeded neighbours. */
        DEADLY(45, Integer.MAX_VALUE, 0.06, 2, 240, true, true, 2, BloomEnd.BURNED_OUT);

        public final int sizeLossDays;
        public final int maxSizeLoss;
        public final double weeklySpreadChance;
        public final int maxSeeded;
        /** Days after going dark until an unanswered bloom ends on its own. */
        public final int burnoutDays;
        public final boolean decivilizesAtFloor;
        /** Whether going dark (and each size lost) shuts the colony's mines, refineries and farms for a while. */
        public final boolean disruptsWorkings;
        /** Recent unrest a bloom leaves when it ends. */
        public final int unrestOnEnd;
        /** How an unanswered bloom ends when its time runs out. */
        public final BloomEnd unansweredEnd;

        Severity(int sizeLossDays, int maxSizeLoss, double weeklySpreadChance, int maxSeeded,
                 int burnoutDays, boolean decivilizesAtFloor, boolean disruptsWorkings,
                 int unrestOnEnd, BloomEnd unansweredEnd) {
            this.sizeLossDays = sizeLossDays;
            this.maxSizeLoss = maxSizeLoss;
            this.weeklySpreadChance = weeklySpreadChance;
            this.maxSeeded = maxSeeded;
            this.burnoutDays = burnoutDays;
            this.decivilizesAtFloor = decivilizesAtFloor;
            this.disruptsWorkings = disruptsWorkings;
            this.unrestOnEnd = unrestOnEnd;
            this.unansweredEnd = unansweredEnd;
        }

        /** Whether an unanswered bloom eats the colony down at this tier. */
        public boolean costsSize() {
            return maxSizeLoss > 0;
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

    /**
     * The owner's picks (2026-10-22): the benign first stage, frequent (more jobs for a player who
     * wants them, the consequences being minor), player colonies exposed.
     */
    public static final BloomTuning DEFAULT = new BloomTuning(
            Severity.BENIGN, Frequency.FREQUENT, PlayerColonies.EXPOSED);

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
