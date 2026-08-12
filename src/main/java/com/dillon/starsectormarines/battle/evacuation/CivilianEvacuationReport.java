package com.dillon.starsectormarines.battle.evacuation;

/**
 * Immutable terminal snapshot of a representative civilian cohort.
 *
 * <p>The report says only what the battle measured. Campaign code may scale
 * that representative result to the mission's frozen civilian stakes through
 * {@link #campaignRescued(int)}; ordinary battle victory is intentionally not
 * part of the calculation.
 */
public final class CivilianEvacuationReport {

    public final int initial;
    public final int evacuated;
    public final int lost;

    CivilianEvacuationReport(int initial, int evacuated, int lost) {
        if (initial <= 0 || evacuated < 0 || lost < 0
                || (long) evacuated + lost != initial) {
            throw new IllegalArgumentException("invalid evacuation totals");
        }
        this.initial = initial;
        this.evacuated = evacuated;
        this.lost = lost;
    }

    /**
     * Scales the representative result to the frozen campaign population.
     * Returns {@code -1} for invalid negative stakes. Integer inputs multiply
     * in {@code long}, so the floor calculation cannot overflow.
     */
    public int campaignRescued(int civiliansAtRisk) {
        if (civiliansAtRisk < 0) return -1;
        if (evacuated == initial) return civiliansAtRisk;
        return (int) (((long) civiliansAtRisk * evacuated) / initial);
    }
}
