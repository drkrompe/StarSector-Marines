package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * A hull's size class — the game's own classification, and the coarse signal for
 * how large a ship's decks are.
 *
 * <p>Class sets the deck's footprint band. Complement and hold then decide how
 * many decks of that footprint the ship needs, which is why a Superfreighter and
 * a Battlecruiser can share a class and still end up with very different
 * interiors.
 */
public enum HullClass {

    /** No boardable interior of its own; carried, not entered. */
    FIGHTER(0),
    FRIGATE(56),
    DESTROYER(84),
    CRUISER(116),
    CAPITAL(148);

    private final int frames;

    HullClass(int frames) {
        this.frames = frames;
    }

    /** Deck length, in frames, that this class of hull affords. */
    public int deckFrames() {
        return frames;
    }

    /** Whether a hull of this class has an interior worth generating at all. */
    public boolean boardable() {
        return frames > 0;
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
