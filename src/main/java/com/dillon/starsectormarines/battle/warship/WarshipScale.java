package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;

/**
 * The conversions from the game's ship-combat units to ground-battle cells.
 *
 * <p>{@link #cells} is the density that sizes every aircraft ({@link AirScale}), and it is what a gun's reach, a
 * shell's blast and a round's speed are read at: a Devastator Cannon that reaches 900 units reaches about forty
 * cells. {@link #hullCells} is what an overhead ship's <em>body</em> is read at: its length and beam, the pivot, the
 * slots its guns and bays sit in, its engine nozzles and its shield bubble, all {@link #HULL_PRESENCE} times larger.
 * A warship over a ground battle is a much bigger thing than the fighters that share the density ladder, and at the
 * ladder's own size a Wolf was four cells long, a marine's length or two of hull. The two conversions are kept apart
 * on purpose: growing the hull does not grow its reach or its blasts, so nothing a ship does to the ground changes
 * with how big it is drawn.
 */
public final class WarshipScale {

    /**
     * How many times larger an overhead hull's body is than the shared density ladder draws it. One factor for every
     * hull, so the ladder between frigate and capital is the game's own.
     */
    public static final float HULL_PRESENCE = 2f;

    private WarshipScale() {}

    /** Game units to cells, for reach, blasts and speeds. */
    public static float cells(float gameUnits) {
        return gameUnits * AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
    }

    /** Hull-spec pixels to cells, for a warship's own body: its extent, slots, nozzles and shield. */
    public static float hullCells(float hullPixels) {
        return cells(hullPixels) * HULL_PRESENCE;
    }
}
