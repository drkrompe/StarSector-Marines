package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * A hull's size class — the game's own classification.
 *
 * <p>Class does not set how large a deck is; the rooms a ship owes do that. What
 * class says is <b>how many decks the hull has</b> to spread that program over,
 * and whether it is big enough to service heavy assets at all. A frigate puts
 * its handful of rooms on one deck; a capital spreads a much larger program
 * across four, which is what keeps any single deck navigable without capping how
 * long a deck may be.
 */
public enum HullClass {

    /** No boardable interior of its own; carried, not entered. */
    FIGHTER(0, false),
    FRIGATE(1, false),
    DESTROYER(2, false),
    CRUISER(3, true),
    CAPITAL(4, true);

    private final int decks;
    private final boolean heavyAssets;

    HullClass(int decks, boolean heavyAssets) {
        this.decks = decks;
        this.heavyAssets = heavyAssets;
    }

    /** How many decks a hull of this class has to spread its room program over. */
    public int decks() {
        return decks;
    }

    /** Whether the hull is large enough to carry and service heavy assets. */
    public boolean carriesHeavyAssets() {
        return heavyAssets;
    }

    /** Whether a hull of this class has an interior worth generating at all. */
    public boolean boardable() {
        return decks > 0;
    }

    /**
     * Map the engine's {@code hullSize} token onto a class. Unknown or absent
     * tokens fall back to {@link #DESTROYER}, the middle of the range, so a
     * modded hull still produces a plausible deck rather than nothing.
     */
    public static HullClass fromHullSize(String hullSize) {
        if (hullSize == null) return DESTROYER;
        return switch (hullSize.trim().toUpperCase()) {
            case "FIGHTER" -> FIGHTER;
            case "FRIGATE" -> FRIGATE;
            case "DESTROYER" -> DESTROYER;
            case "CRUISER" -> CRUISER;
            case "CAPITAL_SHIP", "CAPITAL" -> CAPITAL;
            default -> DESTROYER;
        };
    }
}
