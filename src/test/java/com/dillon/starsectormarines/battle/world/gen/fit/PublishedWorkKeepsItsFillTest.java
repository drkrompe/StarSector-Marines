package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Furnishing a room and giving it work must not cost it its fill.
 *
 * <p>The two are separately reasonable and were disastrous together. A standing
 * cell was held as circulation so later furniture could not take it, and a
 * room's circulation had to stay reachable or the whole fill was thrown away —
 * so the moment a fixture's standing cell landed in a sliver behind its own
 * rank, the room failed its connectivity check and shipped as bare deck. Five
 * shipboard compartment types did exactly that: the armoury, the sick bay, the
 * holds, the boat bays and the bridge generated their fixtures, published their
 * work, and appeared in the game as empty floor.
 *
 * <p>Asked of a single room rather than of a generated ship, because it is a
 * fact about the fitting and the floor it fills. One room, one hatch, no world.
 */
class PublishedWorkKeepsItsFillTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /** A sick bay: beds ranked either side of an aisle, each of them somebody's job. */
    @Test
    void aWardThatPublishesItsBedsIsStillFurnished() {
        Fitted fitted = fit(RoomPurpose.PATIENT_WARD, 14, 10);

        assertTrue(fitted.doodads() > 0, "the ward was furnished with nothing at all");
        assertTrue(fitted.tasks() > 0, "the ward's beds are nobody's work");
        assertTrue(fitted.survives(),
                "the ward's own fill severed its circulation, so it ships as bare deck");
    }

    /**
     * The same room, judged with its work withdrawn, is the control: the
     * fixtures were never the problem, and this fails if a later change makes
     * them one.
     */
    @Test
    void theSameFixturesWithoutWorkWereNeverTheProblem() {
        Fitted fitted = fit(RoomPurpose.PATIENT_WARD, 14, 10, new AisleFitting(
                RoomPurpose.PATIENT_WARD, AisleFitting.FixtureGroup.of(
                        "doodad.residential-bed-h", 2, 2,
                        new AisleFitting.FixtureGroup.Satellite("doodad.chest-2", 1, 1))));

        assertEquals(0, fitted.tasks(), "this fitting is supposed to publish nothing");
        assertTrue(fitted.survives());
    }

    /**
     * A point walled in by its own neighbours is withdrawn, not kept.
     *
     * <p>Dropping it is the whole of the fix, so the count has to be a real one:
     * a room that published every point it laid down would pass the test above
     * for the wrong reason, by no longer checking reachability at all.
     */
    @Test
    void workNobodyCanWalkToIsWithdrawn() {
        assertTrue(fit(RoomPurpose.PATIENT_WARD, 14, 10).dropped() > 0,
                "no point was withdrawn, so nothing tests that unreachable work is");
    }

    private record Fitted(int doodads, int tasks, int dropped, boolean survives) { }

    private static Fitted fit(RoomPurpose purpose, int width, int height) {
        return fit(purpose, width, height, RoomFittings.forPurpose(purpose));
    }

    private static Fitted fit(RoomPurpose purpose, int width, int height,
                              RoomFitting fitting) {
        int mapWidth = width + 8;
        int mapHeight = height + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        Room room = new Room(RoomShape.rectangle(width, height), 4, 4,
                RoomPose.CANONICAL, purpose, List.of(new Doorway(4, 4 + height / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        fitting.fit(floor);
        int dropped = floor.dropUnreachableWork();
        return new Fitted(ctx.doodads.size(), ctx.fixtureTasks.size(),
                dropped, floor.circulationSurvives());
    }
}
