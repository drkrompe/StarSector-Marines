package com.dillon.starsectormarines.battle.world.gen.ship;

/** Installed condition of the starting hull's permanent machine shop. */
public enum WorkshopState {
    NONE, BURIED, CLEARED, REPAIRED, EQUIPPED;
    public boolean operational() { return this == REPAIRED || this == EQUIPPED; }
    public WorkshopState next() {
        return switch (this) {
            case BURIED -> CLEARED;
            case CLEARED -> REPAIRED;
            case REPAIRED -> EQUIPPED;
            default -> this;
        };
    }
    public int price() {
        return switch (this) {
            case BURIED -> 750;
            case CLEARED -> 3000;
            case REPAIRED -> 4500;
            default -> 0;
        };
    }
}
