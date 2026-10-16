package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;

/**
 * The conversions from the game's ship-combat units to ground-battle cells.
 *
 * <p>{@link #cells} is the density that sizes every aircraft ({@link AirScale}), and it is what a gun's reach against
 * the ground, a shell's blast and a round's speed are read at: a Devastator Cannon that reaches 900 units reaches
 * about forty cells. {@link #hullCells} is what an overhead ship's <em>body</em> is read at: its length and beam, the
 * pivot, the slots its guns and bays sit in, its engine nozzles and its shield bubble, all {@link #HULL_PRESENCE}
 * times larger. A warship over a ground battle is a giant beside the fighters that share the density ladder: at the
 * ladder's own size a Wolf was four cells long and a capital a compound's corner. {@link #hullReach} is what a gun
 * fired at another <em>hull</em> reaches: the game's range read at the hull's own drawn scale and then shortened by
 * {@link #HULL_REACH_SHARE}, so ships fight a few of their own lengths apart rather than across the map. Ground fire,
 * anti-air and point defence keep {@link #cells}, so nothing a ship does to the ground or the air changes with how
 * big it is drawn.
 */
public final class WarshipScale {

    /**
     * How many times larger an overhead hull's body is than the shared density ladder draws it. One factor for every
     * hull, so the ladder between frigate and capital is the game's own. {@code -Dbattle.warship.hullPresence=1} is
     * the control that draws hulls at the shared ladder's size.
     */
    public static final float HULL_PRESENCE = Float.parseFloat(System.getProperty("battle.warship.hullPresence", "6"));

    /**
     * The share of the game's range, read at the hull's drawn scale, that a gun reaches against another hull. The
     * game's ranges are open-space ranges, six frigate lengths and more; in atmosphere ships of the line slug it out
     * two or three lengths apart, with the order between guns (who outranges whom) kept exactly.
     * {@code -Dbattle.warship.hullReachShare} re-dials it.
     */
    public static final float HULL_REACH_SHARE =
            Float.parseFloat(System.getProperty("battle.warship.hullReachShare", "0.5"));

    private WarshipScale() {}

    /** The reach, in cells, of a gun firing at a hull: the game's range at the hull's drawn scale, shortened. */
    public static float hullReach(float rangeGameUnits) {
        return cells(rangeGameUnits) * HULL_REACH_MULTIPLIER;
    }

    /** {@link #hullReach} over {@link #cells}: the range multiplier a gun firing at a hull reaches by. */
    public static final float HULL_REACH_MULTIPLIER = HULL_PRESENCE * HULL_REACH_SHARE;

    /** Game units to cells, for reach, blasts and speeds. */
    public static float cells(float gameUnits) {
        return gameUnits * AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
    }

    /** Hull-spec pixels to cells, for a warship's own body: its extent, slots, nozzles and shield. */
    public static float hullCells(float hullPixels) {
        return cells(hullPixels) * HULL_PRESENCE;
    }
}
