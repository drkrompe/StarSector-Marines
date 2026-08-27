package com.dillon.starsectormarines.battle.world.gen.ship;

import java.util.ArrayList;
import java.util.List;

/**
 * How a room was laid down: a quarter-turn count and whether it was flipped.
 *
 * <p>A {@link RoomShape} carries no orientation, and for a mask that is enough —
 * the placer turns it and the turned mask is the answer. An <em>arrangement</em>
 * is not so lucky. A mech bay with its shop aft and its doors forward is a
 * different room from its mirror image, and the two have <b>identical masks</b>:
 * flipping a rectangle changes nothing a mask can see. So the way a room was put
 * down has to be recorded rather than recovered, and this is the record.
 *
 * <p>That is also what makes mirroring worth having. One authored arrangement
 * yields eight rooms — the bay whose doors are forward and the bay whose doors
 * are aft are the same fitting seen from the other side, not two fittings — and
 * the placer picks whichever one hooks up to the deck it actually has.
 *
 * <p>Fittings author in the <b>canonical frame</b>: the shape as written, which
 * for the rooms authored so far means horizontal. Everything a fitting places is
 * mapped through the pose on its way down, so a fitting never asks which way
 * round it ended up.
 */
public record RoomPose(int quarterTurns, boolean mirrored) {

    /** The canonical frame: as authored, unturned and unflipped. */
    public static final RoomPose CANONICAL = new RoomPose(0, false);

    public RoomPose {
        if (quarterTurns < 0 || quarterTurns > 3) {
            throw new IllegalArgumentException("a pose turns 0-3 quarters, not " + quarterTurns);
        }
    }

    /** Every distinct way a room can be laid down: four turns, each way round. */
    public static List<RoomPose> all() {
        List<RoomPose> poses = new ArrayList<>(8);
        for (int turns = 0; turns < 4; turns++) {
            poses.add(new RoomPose(turns, false));
            poses.add(new RoomPose(turns, true));
        }
        return List.copyOf(poses);
    }

    /** Whether this pose leaves the canonical frame's long axis running along x. */
    public boolean upright() {
        return quarterTurns % 2 == 0;
    }

    /** The extent along x once a canonical {@code width x height} shape is posed. */
    public int posedWidth(int width, int height) {
        return upright() ? width : height;
    }

    /** The extent along y once a canonical {@code width x height} shape is posed. */
    public int posedHeight(int width, int height) {
        return upright() ? height : width;
    }

    /**
     * Carry a cell back out of this pose into the canonical frame — the inverse
     * of {@link #map}. A fitting reading its own doors needs this, since the
     * deck hands them back in the compartment's coordinates.
     */
    public int[] unmap(int x, int y, int width, int height) {
        int cx = x;
        int cy = y;
        int extentX = posedWidth(width, height);
        int extentY = posedHeight(width, height);
        for (int turn = 0; turn < (4 - quarterTurns) % 4; turn++) {
            int nextX = extentY - 1 - cy;
            int nextY = cx;
            cx = nextX;
            cy = nextY;
            int swap = extentX;
            extentX = extentY;
            extentY = swap;
        }
        return new int[]{ mirrored ? width - 1 - cx : cx, cy };
    }

    /**
     * Carry a direction into this pose.
     *
     * <p>A heading has no position, so it turns and flips without the extents
     * {@link #map} needs. Machines face out of their berths, and a berth in a
     * flipped bay faces the other way — which is the whole point of recording
     * the flip.
     */
    public int[] mapDirection(int dx, int dy) {
        int x = mirrored ? -dx : dx;
        int y = dy;
        for (int turn = 0; turn < quarterTurns; turn++) {
            int nextX = -y;
            int nextY = x;
            x = nextX;
            y = nextY;
        }
        return new int[]{ x, y };
    }

    /**
     * Carry one cell from the canonical frame into this pose.
     *
     * <p>Flip first, then turn, matching the order the shape itself is built in.
     * Coordinates outside the footprint map correctly too, which is not an
     * accident: hookups are authored on the bulkhead ring at {@code -1} and at
     * the far extent, and a mapping that only held for floor cells would place
     * every door on the wrong wall.
     *
     * @param width canonical extent along x, before any turn
     * @param height canonical extent along y, before any turn
     * @return the cell in the posed frame, as {@code {x, y}}
     */
    public int[] map(int x, int y, int width, int height) {
        int cx = mirrored ? width - 1 - x : x;
        int cy = y;
        int extentX = width;
        int extentY = height;
        for (int turn = 0; turn < quarterTurns; turn++) {
            int nextX = extentY - 1 - cy;
            int nextY = cx;
            cx = nextX;
            cy = nextY;
            int swap = extentX;
            extentX = extentY;
            extentY = swap;
        }
        return new int[]{ cx, cy };
    }
}
