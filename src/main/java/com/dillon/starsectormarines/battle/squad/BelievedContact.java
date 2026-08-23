package com.dillon.starsectormarines.battle.squad;

/**
 * One hostile contact remembered by a squad. The cell is where the unit was
 * last directly observed or localized by sound, not a live projection of its
 * current position.
 */
public record BelievedContact(long unitId,
                              int lastSeenCellX,
                              int lastSeenCellY,
                              int lastSeenTick,
                              float confidence,
                              BeliefSource source,
                              int previousDirectCellX,
                              int previousDirectCellY,
                              int previousDirectTick) {

    public static final int NO_PREVIOUS_DIRECT = -1;

    /** Compatibility constructor for a contact without a usable motion sample. */
    public BelievedContact(long unitId, int lastSeenCellX, int lastSeenCellY,
                           int lastSeenTick, float confidence, BeliefSource source) {
        this(unitId, lastSeenCellX, lastSeenCellY, lastSeenTick, confidence,
                source, NO_PREVIOUS_DIRECT, NO_PREVIOUS_DIRECT, NO_PREVIOUS_DIRECT);
    }

    /** True when the serial alert pass refreshed this contact on {@code tick}. */
    public boolean observedOnTick(int tick) {
        return lastSeenTick == tick;
    }

    /** True only for a fresh direct track with two consecutive observations. */
    public boolean hasFreshMotionSample(int tick) {
        return source == BeliefSource.DIRECT
                && lastSeenTick == tick
                && previousDirectTick == tick - 1;
    }

    /** Squared cell distance from this remembered position to another. */
    public float distanceSquaredTo(BelievedContact other) {
        float dx = other.lastSeenCellX - lastSeenCellX;
        float dy = other.lastSeenCellY - lastSeenCellY;
        return dx * dx + dy * dy;
    }
}
