package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.List;

/**
 * The optional anti-air yard of a fort, as authored geometry in the fort's own local frame: a seven-by-seven sandbagged
 * yard in the open ground of the works that is a <em>missile site</em>. Two Ridgewatch missile batteries stand side by
 * side on the far row with a walkway between them, a plotting console sits across the yard from them, and two flak
 * positions stand beside the console as the close-in defence. The ring of bags round the yard is broken by one gate that
 * opens on the walkway, so a squad can walk in between the launchers rather than into a wall of equipment.
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
    /** Rows from the yard's centre to the middle of the launcher row (south) and of the console (north). */
    public static final int BATTERY_ROW = -2, CONSOLE_ROW = 2;
    /** Columns from the yard's centre to the middle of each launcher (west, east); the console stands over the east one. */
    public static final int BATTERY_COLUMN = 2;
    /** Row, from the yard's centre, of the close-in flak positions, and the two columns they stand in (west of the walkway). */
    public static final int FLAK_ROW = 2;
    public static final int[] FLAK_COLUMNS = {-3, -1};

    /** The inner yard: x, y, width, height in the footprint frame. This is the ground whose holder holds the aspect. */
    public int[] floor() {
        int side = 2 * HALF + 1;
        return new int[]{centreX - HALF, centreY - HALF, side, side};
    }

    /** The middle of the three-by-three plotting console, east of the yard's walkway. */
    public int[] consoleCentre() {
        return new int[]{centreX + BATTERY_COLUMN, centreY + CONSOLE_ROW};
    }

    /** The middle of each three-by-three Ridgewatch missile battery: either side of the one-cell walkway. */
    public List<int[]> batteryCentres() {
        return List.of(new int[]{centreX - BATTERY_COLUMN, centreY + BATTERY_ROW},
                new int[]{centreX + BATTERY_COLUMN, centreY + BATTERY_ROW});
    }

    /** The cells of the close-in flak positions, beside the console. */
    public List<int[]> flakCells() {
        return List.of(new int[]{centreX + FLAK_COLUMNS[0], centreY + FLAK_ROW},
                new int[]{centreX + FLAK_COLUMNS[1], centreY + FLAK_ROW});
    }

    /** The gap in the ring of bags as its middle cell. */
    public int[] gapCell() {
        return new int[]{centreX + gapDx * (HALF + 1), centreY + gapDy * (HALF + 1)};
    }

    /** How many Ridgewatch batteries the yard carries. */
    public static int batteryCount() {
        return 2;
    }
}
