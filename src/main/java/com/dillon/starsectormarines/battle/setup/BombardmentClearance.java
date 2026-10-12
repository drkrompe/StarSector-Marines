package com.dillon.starsectormarines.battle.setup;

/** A continuous clear interval, reset by any survivor or committed arrival at risk. */
public final class BombardmentClearance {
    private final int warningTicks;
    private int warningTick = -1;
    public BombardmentClearance(int warningTicks) {
        if (warningTicks < 0) throw new IllegalArgumentException("Warning interval cannot be negative");
        this.warningTicks = warningTicks;
    }
    public boolean authorize(int tick, boolean evidence, int survivors, int outsideWithdrawal,
                             boolean arrivalsPending, boolean onStation) {
        if (!evidence || survivors == 0 || outsideWithdrawal > 0 || arrivalsPending || !onStation) {
            warningTick = -1;
            return false;
        }
        if (warningTick < 0) warningTick = tick;
        return tick - warningTick >= warningTicks;
    }
    public int warningTick() { return warningTick; }
}
