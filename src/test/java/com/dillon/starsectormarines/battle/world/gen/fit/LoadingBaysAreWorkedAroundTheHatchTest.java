package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A loading bay is a working floor arranged around one hatch, not a second
 * stockroom with a different name.
 *
 * <p>What is asked here is the thing a job count cannot show: that the room
 * actually states where its hatch is and is handed about the two different ends
 * it has, that the marshalling floor a load is broken down on stays clear, and
 * that the staged run and the dispatch desk publish distinct kinds of work
 * rather than one motif repeated the length of the room.
 */
class LoadingBaysAreWorkedAroundTheHatchTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    private record Fitted(GenContext ctx, int dropped, boolean survives, int left, int top) {

        List<FixtureTask> tasks() {
            return ctx.fixtureTasks;
        }

        Set<Affordance> kinds() {
            Set<Affordance> kinds = EnumSet.noneOf(Affordance.class);
            for (FixtureTask task : tasks()) kinds.add(task.affordance());
            return kinds;
        }

        List<Doodad> within(int x, int y, int spanX, int spanY) {
            List<Doodad> found = new ArrayList<>();
            for (Doodad doodad : ctx.doodads) {
                int localX = doodad.cellX - left;
                int localY = doodad.cellY - top;
                if (localX < x || localY < y) continue;
                if (localX >= x + spanX || localY >= y + spanY) continue;
                found.add(doodad);
            }
            return found;
        }
    }

    /**
     * The hatch is authored on the near short bulkhead, not left for the
     * passage search to discover, and the room is handed about the two ends
     * that hatch makes different.
     */
    @Test
    void theHatchIsAuthoredOnTheNearBulkheadAndTheRoomIsHanded() {
        LoadingBayFitting fitting = new LoadingBayFitting();
        assertTrue(fitting.handed(), "a loading bay's hatch end and far end are not interchangeable");

        List<Hookup> hookups = fitting.hookups(RoomShape.rectangle(18, 12));
        assertTrue(!hookups.isEmpty(), "the loading bay names no hookup at all");
        for (Hookup hookup : hookups) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                for (int[] cell : slot.cells()) {
                    assertEquals(-1, cell[0],
                            "a loading bay hookup cell sits off the near short bulkhead: "
                                    + cell[0] + "," + cell[1]);
                }
            }
        }
    }

    /**
     * A full-sized bay publishes both the staged run's handling and the
     * dispatch desk's manifest check — two different kinds of work, not the
     * one motif the old fill repeated down both sides.
     */
    @Test
    void theBayOffersStagingAndDispatchAsDistinctWork() {
        Fitted bay = fit(18, 12);

        assertTrue(bay.kinds().containsAll(EnumSet.of(Affordance.STOW, Affordance.READOUT)),
                "the loading bay only offers " + bay.kinds());
        assertTrue(bay.tasks().size() >= 5,
                "an eighteen-by-twelve loading bay published only "
                        + bay.tasks().size() + " job(s)");
        assertTrue(bay.survives(), "the loading bay's own fill severed its circulation");
        assertEquals(0, bay.dropped(),
                "the loading bay published work nobody can walk to");
    }

    /**
     * Stowage at the staged run is published at several pallets, not one — the
     * same "work between two points" rule every stores compartment keeps.
     */
    @Test
    void theStagedRunPublishesStowageAtSeveralPallets() {
        Fitted bay = fit(18, 12);
        long stowPoints = bay.tasks().stream()
                .filter(task -> task.affordance() == Affordance.STOW)
                .count();
        assertTrue(stowPoints >= 2,
                "the staged run published stowage at only " + stowPoints + " pallet(s)");
    }

    /**
     * The marshalling floor down the middle is where a load is actually broken
     * down or built up, and nothing may stand on it.
     */
    @Test
    void theMarshallingFloorStaysClear() {
        Fitted bay = fit(18, 12);
        assertTrue(bay.within(0, 4, 18, 4).isEmpty(),
                "something is standing on the loading bay's own marshalling floor");
    }

    /** A longer bay stages more of a run rather than the same run with more empty deck round it. */
    @Test
    void aLongerBayStagesMoreOfTheRun() {
        int big = fit(18, 12).tasks().size();
        int small = fit(10, 8).tasks().size();
        assertTrue(big > small,
                "an eighteen-by-twelve loading bay holds " + big + " jobs and a ten-by-eight bay " + small);
    }

    private static Fitted fit(int width, int height) {
        int mapWidth = width + 8;
        int mapHeight = height + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        // A door on the long bulkhead, same as the sibling fittings' synthetic
        // rooms: fit() itself treats every door generically through its own
        // door-stub logic, so this is enough to exercise that path even though
        // the door does not land on the hookup this fitting names — the
        // hookup is asserted separately, against the fitting's own hookups().
        Room room = new Room(RoomShape.rectangle(width, height), 4, 4,
                RoomPose.CANONICAL, RoomPurpose.LOADING_BAY,
                List.of(new Doorway(4, 4 + height / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new LoadingBayFitting().fit(floor);
        int dropped = floor.dropUnreachableWork();
        return new Fitted(ctx, dropped, floor.circulationSurvives(), 4, 4);
    }
}
