package com.dillon.starsectormarines.battle.fire;

/**
 * What a cell has to burn (`fire-nouns.md`, Fuel). A cell's fuel is a mask of
 * these, and burns as their sum: its load is the total of their loads, its peak
 * the hottest of them, its ignition the easiest of them to catch, and its reach
 * the furthest any of them throws heat, and its spread the furthest any of them carries a fire.
 *
 * <p>Loads are in fire steps at the field's burn rate of one unit a step
 * ({@link FireField#STEP_TICKS}), so a load of 30 burns for six seconds.
 */
public enum FireFuel {
    /** Grass ground: the fast carrier. */
    GRASS(30, 150, 260, 2, 2),
    /** A plant on the nature overlay: shrubs, tufts, a hedge. */
    BRUSH(60, 190, 300, 2, 3),
    /** A standing tree's crown: slow to catch, long and hot, throws heat a ring further. */
    CROWN(200, 255, 1100, 3, 4),
    /** Living creep. */
    CREEP(75, 175, 520, 2, FireFuel.UNLIMITED),
    /** Creep that is dying back: dry husk. */
    DEAD_CREEP(40, 150, 260, 2, FireFuel.UNLIMITED),
    /** Timber and furniture rubble. */
    DEBRIS(50, 170, 420, 2, 2),
    /**
     * A light wall or window: board and frame, slow to catch and long to burn.
     * A cell of it that burns through falls to rubble, opening the building.
     */
    STRUCTURE(120, 210, 650, 2, FireFuel.UNLIMITED),
    /** A furnished room's floor: furniture, fittings and boards, room by room. */
    FURNISHING(80, 200, 420, 2, FireFuel.UNLIMITED);

    /**
     * A spread no fire outruns: the fuel carries a fire as far as it goes. Creep, living or dead, is the swarm's
     * own overgrowth and the fuel a burn is lit to clear; a building's boards and rooms are held by its walls.
     */
    public static final int UNLIMITED = 127;

    /** Fire steps of burning this fuel adds to a cell. */
    public final int load;
    /** The intensity, 0..255, it burns at once caught. */
    public final int peak;
    /** Soaked-up heat at which it catches. */
    public final int ignition;
    /** Rings of cells round it a burning cell of it heats. */
    public final int reach;
    /**
     * Cells from where it was lit that this fuel carries a fire (`fire-nouns.md`, Spread): a cell further than this
     * from its fire's ignition does not catch, so a grass fire burns a patch round where it was started and goes
     * out. {@link #UNLIMITED} for a fuel that carries a fire as far as it stands.
     */
    public final int spread;
    /** This fuel's bit in a fuel mask. */
    public final int bit;

    FireFuel(int load, int peak, int ignition, int reach, int spread) {
        this.load = load;
        this.peak = peak;
        this.ignition = ignition;
        this.reach = reach;
        this.spread = spread;
        this.bit = 1 << ordinal();
    }

    private static final FireFuel[] ALL = values();

    /** The lowest ignition of any fuel; heat below it can never light anything. */
    public static final int MIN_IGNITION = 260;

    /** Total load of a mask. */
    public static int load(int mask) {
        int sum = 0;
        for (FireFuel f : ALL) if ((mask & f.bit) != 0) sum += f.load;
        return sum;
    }

    /** Hottest peak in a mask, 0 for none. */
    public static int peak(int mask) {
        int best = 0;
        for (FireFuel f : ALL) if ((mask & f.bit) != 0) best = Math.max(best, f.peak);
        return best;
    }

    /** Easiest ignition in a mask, {@link Integer#MAX_VALUE} for none. */
    public static int ignition(int mask) {
        int best = Integer.MAX_VALUE;
        for (FireFuel f : ALL) if ((mask & f.bit) != 0) best = Math.min(best, f.ignition);
        return best;
    }

    /** Furthest reach in a mask, 0 for none. */
    public static int reach(int mask) {
        int best = 0;
        for (FireFuel f : ALL) if ((mask & f.bit) != 0) best = Math.max(best, f.reach);
        return best;
    }

    /** The furthest any fuel of a mask carries a fire, in cells from its ignition; {@link #UNLIMITED} if any does. */
    public static int spread(int mask) {
        int best = 0;
        for (FireFuel f : ALL) if ((mask & f.bit) != 0) best = Math.max(best, f.spread);
        return best;
    }

    /** Whether a mask holds this fuel. */
    public boolean in(int mask) { return (mask & bit) != 0; }
}
