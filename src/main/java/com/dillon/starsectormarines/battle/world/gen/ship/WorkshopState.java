package com.dillon.starsectormarines.battle.world.gen.ship;

/** Installed condition of the starting hull's permanent machine shop. */
public enum WorkshopState {
    NONE, BURIED, CLEARED, REPAIRED, EQUIPPED,
    /** Tooled, with a reclamation line that breaks a spare back down to its whole bill. */
    RECLAIMING;
    public boolean operational() { return this == REPAIRED || tooled(); }
    /** Whether the shop's tooling is in: every stage from {@link #EQUIPPED} up. */
    public boolean tooled() { return this == EQUIPPED || this == RECLAIMING; }
    public WorkshopState next() {
        return switch (this) {
            case BURIED -> CLEARED;
            case CLEARED -> REPAIRED;
            case REPAIRED -> EQUIPPED;
            case EQUIPPED -> RECLAIMING;
            default -> this;
        };
    }
    public int price() {
        return switch (this) {
            case BURIED -> 750;
            case CLEARED -> 3000;
            case REPAIRED -> 4500;
            case EQUIPPED -> 7500;
            default -> 0;
        };
    }
}
