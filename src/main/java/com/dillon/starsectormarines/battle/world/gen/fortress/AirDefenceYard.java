package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;

import java.util.List;

/**
 * The control room of a fort's optional anti-air aspect, as authored geometry in the fort's own local frame: a small
 * hardened command room (seven by five) in the open ground of the works holding the plotting console between two banks
 * of seated plotters, with a single door.
 *
 * <p>The room is the aspect's <em>control point</em>, not its launchers. The Locust batteries and Swarmer towers stand out on the
 * fort's bastions ({@link FortPlan#airBatteries()}, {@link FortPlan#airTowers()}), so the controlled airspace is as wide as the works and no single
 * strike silences both; wrecking the console still silences them, losing power still slows them, and holding the room
 * still turns every mount to the holder. Whoever holds the floor inside it holds the console. Each kind of fort chooses
 * where its room stands, on ground its layout left free; see {@code forts.md}.
 *
 * @param centreX centre of the room's floor, in the footprint frame
 * @param centreY centre of the room's floor, in the footprint frame
 * @param gapDx   local direction of the room's door, which must be a north or south one: always 0
 * @param gapDy   {@code 1} for a door on the south (the console against the north wall), {@code -1} for the north
 */
public record AirDefenceYard(int centreX, int centreY, int gapDx, int gapDy) {

    /** Half the width of the floor: it is {@code 2 * HALF_WIDTH + 1} cells across. */
    public static final int HALF_WIDTH = 3;
    /** Half the depth of the floor. */
    public static final int HALF_DEPTH = 2;

    /** How many plotters the garrison keeps at the console's banks. */
    public static final int CREW = 2;

    public AirDefenceYard {
        if (gapDx != 0 || Math.abs(gapDy) != 1) throw new IllegalArgumentException("the control room's door is on a north or south wall");
    }

    /** The room's floor: x, y, width, height in the footprint frame. This is the ground whose holder holds the aspect. */
    public int[] floor() {
        return new int[]{centreX - HALF_WIDTH, centreY - HALF_DEPTH, 2 * HALF_WIDTH + 1, 2 * HALF_DEPTH + 1};
    }

    /** The room as authored: a command room, the console against the wall opposite its door. */
    public FortRoom room() {
        int[] floor = floor();
        boolean south = gapDy > 0;
        return FortRooms.commandRoom(FortAspect.AIR_DEFENCE, true, floor[0], floor[1], south,
                List.of(new Doorway(HALF_WIDTH, south ? floor[3] : -1)), CREW);
    }

    /** The middle of the three-by-three plotting console, against the wall opposite the door. */
    public int[] consoleCentre() {
        return room().coreCentre();
    }

    /** The door as its middle cell, in the wall ring. */
    public int[] gapCell() {
        return new int[]{centreX, centreY + gapDy * (HALF_DEPTH + 1)};
    }
}
