package com.dillon.starsectormarines.battle.world.gen.fortress;

import java.util.List;

/**
 * The control yard of a fort's optional anti-air aspect, as authored geometry in the fort's own local frame: a
 * seven-by-seven sandbagged yard in the open ground of the works holding the plotting console and the two flak positions
 * that are the aspect's close-in defence. The ring of bags is broken by one gate that opens on a walkway to the console.
 *
 * <p>The yard is the aspect's <em>control point</em>, not its launchers. The Ridgewatch missile batteries stand out on the
 * fort's bastions ({@link FortPlan#airBatteries()}), so the controlled airspace is as wide as the works and no single
 * strike silences both; wrecking the console still silences them, losing power still slows them, and holding the yard
 * still turns every mount to the holder. The yard is open air, not a building: whoever holds the ground inside it holds
 * the console. Each kind of fort chooses where its yard stands, on ground its layout left free; see {@code forts.md}.
 *
 * @param centreX centre of the yard, in the footprint frame
 * @param centreY centre of the yard, in the footprint frame
 * @param gapDx   local direction of the one break in the ring of bags (a unit axis vector)
 * @param gapDy   as {@code gapDx}
 */
public record AirDefenceYard(int centreX, int centreY, int gapDx, int gapDy) {

    /** Half the width of the inner yard: the yard is {@code 2 * HALF + 1} cells on a side. */
    public static final int HALF = 3;
    /** Row, from the yard's centre, of the plotting console's middle; the console stands over the {@link #CONSOLE_COLUMN} column. */
    public static final int CONSOLE_ROW = 2;
    /** Column, from the yard's centre, of the console's middle (east of the walkway). */
    public static final int CONSOLE_COLUMN = 2;
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
        return new int[]{centreX + CONSOLE_COLUMN, centreY + CONSOLE_ROW};
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
}
