package com.dillon.starsectormarines.battle.squad;

/**
 * One hostile contact remembered by a squad. The cell is where the unit was
 * last directly observed, not a live projection of its current position.
 */
public record BelievedContact(long unitId,
                              int lastSeenCellX,
                              int lastSeenCellY,
                              int lastSeenTick,
                              float confidence) {

    /** True when the serial alert pass refreshed this contact on {@code tick}. */
    public boolean observedOnTick(int tick) {
        return lastSeenTick == tick;
    }

    /** Squared cell distance from this remembered position to another. */
    public float distanceSquaredTo(BelievedContact other) {
        float dx = other.lastSeenCellX - lastSeenCellX;
        float dy = other.lastSeenCellY - lastSeenCellY;
        return dx * dx + dy * dy;
    }
}
