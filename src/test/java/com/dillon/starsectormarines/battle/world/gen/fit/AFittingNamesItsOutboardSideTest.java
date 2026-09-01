package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A room that must reach the outside may say <em>which</em> of its sides that
 * has to be.
 *
 * <p>Reaching the hull anywhere is enough for most of them, and for a long time
 * it was all a room could ask for. It is not enough for a bay: its outboard
 * bulkhead is a door, and the arrangement behind that door is built around it —
 * the deck kept clear, the boats nosed at it, nothing stacked against it. Left
 * free to use whichever side it could reach, the placer put bays down with an
 * end against the skin as readily as a flank, and the fitting furnished a door
 * it could not see.
 *
 * <p>The envelope here is a slot with the spine along one flank, so the bay has
 * one position and both its ends are against the outside. Meeting the edge on
 * some side is therefore satisfied whichever way round it lies; only one way
 * round puts the side the fitting named there.
 *
 * <p>Asked of the packer on a synthetic envelope, because it is a fact about
 * the placement rule rather than about any deck that happens to carry a bay.
 */
class AFittingNamesItsOutboardSideTest {

    private static final RoomPacker.Palette PALETTE =
            new RoomPacker.Palette(GroundKind.INDOOR, GroundKind.INDOOR, GroundKind.INDOOR);

    /** The bay's own footprint, as the fitting authors it: along, then across. */
    private static final int BAY_LENGTH = 20;
    private static final int BAY_DEPTH = 8;

    /** The slot: as long as the bay, and two rows deeper than it. */
    private static final int DECK_LEFT = 1;
    private static final int DECK_RIGHT = DECK_LEFT + BAY_LENGTH - 1;
    private static final int SPINE_ROW = 1;
    private static final int DECK_BOTTOM = SPINE_ROW + BAY_DEPTH + 2;

    /** The bay lies along the slot with its door wall on the skin, not an end. */
    @Test
    void theBayIsLaidWithItsDoorWallOnTheHull() {
        int mapWidth = DECK_RIGHT + 4;
        int mapHeight = DECK_BOTTOM + 4;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        GenContext ctx = new GenContext(grid, new CellTopology(mapWidth, mapHeight),
                new Random(5L), mapWidth, mapHeight, 5L);

        boolean[][] buildable = new boolean[mapWidth][mapHeight];
        boolean[][] spine = new boolean[mapWidth][mapHeight];
        for (int x = DECK_LEFT; x <= DECK_RIGHT; x++) {
            for (int y = SPINE_ROW; y <= DECK_BOTTOM; y++) buildable[x][y] = true;
            spine[x][SPINE_ROW] = true;
            grid.setWalkableFloor(x, SPINE_ROW);
        }

        RoomPacker packer = new RoomPacker(ctx, buildable, spine, PALETTE);
        RoomPacker.Placed bay = packer.place(new RoomPacker.Request(RoomPurpose.HANGAR,
                RoomShape.rectangle(BAY_LENGTH, BAY_DEPTH),
                RoomPacker.Affinity.ANYWHERE, RoomPacker.EdgeContact.ANY), true);

        assertNotNull(bay, "the bay would not place at all");
        int[] authored = RoomFittings.forPurpose(RoomPurpose.HANGAR).outboard();
        assertNotNull(authored, "a boat bay no longer names the side it opens through");
        assertFalse(buildable[bay.originX() - 1][bay.originY()],
                "the bay's end is not against the outside, so meeting the edge on some"
                        + " side was never the easier answer here");

        int[] out = bay.pose().mapDirection(authored[0], authored[1]);
        assertArrayEquals(new int[]{ 0, 1 }, out,
                "the bay's door wall faces " + out[0] + "," + out[1]);
        assertFalse(buildable[bay.originX()][bay.originY() + bay.shape().height()],
                "the bay met the edge with a bulkhead it built no door into, and its"
                        + " door wall backs onto the deck");
    }
}
