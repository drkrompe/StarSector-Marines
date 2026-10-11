package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;

/**
 * The one conversion from the game's ship-combat units to ground-battle cells.
 *
 * <p>It is the same density that sizes every hull ({@link AirScale}), so a gun's
 * reach, a shell's blast and the hull that carries it stay in the proportion the
 * player knows from the game: a Devastator Cannon that reaches 900 units reaches
 * about forty cells, from a hull about seventeen cells long.
 */
public final class WarshipScale {

    private WarshipScale() {}

    /** Game units to cells. */
    public static float cells(float gameUnits) {
        return gameUnits * AirScale.METERS_PER_PX / AirScale.METERS_PER_CELL;
    }
}
