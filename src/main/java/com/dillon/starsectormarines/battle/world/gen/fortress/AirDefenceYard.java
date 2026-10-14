package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.List;

/**
 * The optional anti-air yard of a fort, as authored geometry in the fort's own local frame: a seven-by-seven sandbagged
 * yard in the open ground of the works, a plotting console at one end of it and a guided surface-to-air battery at the
 * other, with a flak position either side of the battery. The ring of bags round the yard is broken by one gate.
 *
 * <p>The yard is open air, not a building: whoever holds the ground inside it holds the console, and the mounts obey the
 * holder. That keeps the aspect reachable (a squad can stand in it) and exposed (it sits inside the wall, under the
 * fort's other guns), and it needs no building art. Each kind of fort chooses where its yard stands, on ground its
 * layout left free; see {@code forts.md}.
 *
 * @param centreX centre of the yard, in the footprint frame
 * @param centreY centre of the yard, in the footprint frame
 * @param gapDx   local direction of the one break in the ring of bags (a unit axis vector)
 * @param gapDy   as {@code gapDx}
 */
public record AirDefenceYard(int centreX, int centreY, int gapDx, int gapDy) {

    /** Half the width of the inner yard: the yard is {@code 2 * HALF + 1} cells on a side. */
    public static final int HALF = 3;
    /** Rows from the yard's centre to the middle of the console (south) and of the missile battery (north). */
    public static final int CONSOLE_ROW = 2, BATTERY_ROW = -2;
    /** Columns from the yard's centre to its flak positions, beside the battery. */
    public static final int FLAK_COLUMN = 3;

    /** The inner yard: x, y, width, height in the footprint frame. This is the ground whose holder holds the aspect. */
    public int[] floor() {
        int side = 2 * HALF + 1;
        return new int[]{centreX - HALF, centreY - HALF, side, side};
    }

    /** The middle of the three-by-three plotting console. */
    public int[] consoleCentre() {
        return new int[]{centreX, centreY + CONSOLE_ROW};
    }

    /** The middle of the three-by-three guided battery. */
    public int[] batteryCentre() {
        return new int[]{centreX, centreY + BATTERY_ROW};
    }

    /** The cells of the flak positions, one either side of the battery. */
    public List<int[]> flakCells() {
        return List.of(new int[]{centreX - FLAK_COLUMN, centreY + BATTERY_ROW},
                new int[]{centreX + FLAK_COLUMN, centreY + BATTERY_ROW});
    }

    /** The gap in the ring of bags as its middle cell. */
    public int[] gapCell() {
        return new int[]{centreX + gapDx * (HALF + 1), centreY + gapDy * (HALF + 1)};
    }
}
