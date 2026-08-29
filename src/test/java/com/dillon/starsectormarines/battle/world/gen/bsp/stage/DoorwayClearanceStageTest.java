package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing stands in a doorway — and what happens to the thing standing there
 * depends on whose ground it is on.
 *
 * <p>Thirty of six and a half thousand doorways in ten generated cities opened
 * into something solid. None was placed in error: a door is cut by the pass
 * that built its wall, and the ground outside is dressed by a pass that runs
 * later and knows nothing about it. Only a reconciliation after both have
 * finished can see it.
 */
class DoorwayClearanceStageTest {

    private static final int W = 9;
    private static final int H = 9;

    /**
     * A wall run along y=4 with a doorway at (4,4), open ground either side.
     *
     * <p>The wall is stamped the way the finished map records one — the topology
     * tag and an unwalkable cell — because that is what the stage reads.
     */
    private static GenContext lonelyDoorway() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }
        for (int x = 0; x < W; x++) {
            if (x == 4) continue;
            grid.setWalkable(x, 4, false);
            topology.setWall(x, 4, true);
        }
        return new GenContext(grid, topology, new Random(1L), W, H, 1L);
    }

    /** A blocking prop on {@code (x, y)}, stamped the way a placed prop is. */
    private static void prop(GenContext ctx, int x, int y) {
        ctx.doodads.add(new Doodad(x, y, new TileManifest.TileFrame(0, 0), false, 2));
        ctx.grid.setWalkable(x, y, false);
        ctx.topology.setFixture(x, y, true);
    }

    @Test
    void aPropStandingInADoorwayIsMovedOutOfIt() {
        GenContext ctx = lonelyDoorway();
        prop(ctx, 4, 5);

        new DoorwayClearanceStage().run(ctx);

        assertTrue(ctx.doodads.isEmpty(), "the prop blocking the doorway was left there");
        assertTrue(ctx.grid.canTraverseCellStep(4, 4, 4, 5),
                "the doorway still cannot be walked through");
        assertFalse(ctx.topology.isWall(4, 4),
                "the doorway was walled up when the prop could simply have been moved");
    }

    /**
     * A door onto a facility's own surface is walled, not opened.
     *
     * <p>A compound's perimeter is not clutter. Punching a hole in it to honour
     * a neighbour's door gives that building a private way into a fenced lot;
     * the honest reading is that the wall should have been solid there.
     */
    @Test
    void aDoorOntoAFacilitysOwnGroundIsSealedRatherThanBreached() {
        GenContext ctx = lonelyDoorway();
        prop(ctx, 4, 5);
        ctx.markMadeGround(4, 5);
        // The doorway's only other way out, so it is the dead-end stub the
        // seal is allowed to close.
        ctx.grid.setWalkable(4, 3, false);

        new DoorwayClearanceStage().run(ctx);

        assertTrue(ctx.topology.isWall(4, 4), "the doorway onto the facility was left open");
        assertFalse(ctx.doodads.isEmpty(), "the facility's own perimeter was torn down");
    }

    /**
     * A threshold with two ways out is left exactly as it was.
     *
     * <p>Walling a cell with one way out removes a stub; walling one with two
     * severs whatever they joined, and this stage has no business doing that to
     * honour a cosmetic rule. It is a real case rather than a defensive one:
     * a map carries cells that are wall to look at and passable to walk on, and
     * a threshold beside one of those has a second way through.
     */
    @Test
    void aThresholdThatLeadsSomewhereIsNeverSealed() {
        GenContext ctx = lonelyDoorway();
        prop(ctx, 4, 5);
        ctx.markMadeGround(4, 5);
        // Wall to look at, passable to walk on — so the threshold beside it
        // still joins two places and closing it would cut them apart.
        ctx.grid.setWalkableFloor(3, 4);

        new DoorwayClearanceStage().run(ctx);

        assertFalse(ctx.topology.isWall(4, 4),
                "a doorway with a way through either side was walled up");
        assertFalse(ctx.doodads.isEmpty(), "the facility's own perimeter was torn down");
    }
}
