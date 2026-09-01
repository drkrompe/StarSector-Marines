package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A boat bay has boats in it, and they are berths like any other.
 *
 * <p>The room was fitted for a long time as though its clear deck were the whole
 * design — gear ranked round four bulkheads and nothing in the middle — which is
 * a hold with an empty middle rather than a boat deck. What was missing is the
 * thing the compartment is named for, and with it the only job on a ship that a
 * garrison airfield's ground crew would recognise: servicing a machine that
 * stands there between lifts.
 *
 * <p>Asked of the fitting and the floor it fills. No deck, no ship, no seed —
 * how many boats a bay holds is a fact about the arrangement and the space, and
 * generating hulls to look at the result would establish nothing the one fitting
 * did not.
 */
class ABoatBayHoldsBoatsTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /** The room's origin on the synthetic floor it is fitted onto. */
    private static final int ORIGIN = 4;

    /** One fitted boat bay, and what it laid. */
    private static GenContext bay(int width, int height) {
        int mapWidth = width + 8;
        int mapHeight = height + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        Room room = new Room(RoomShape.rectangle(width, height), ORIGIN, ORIGIN,
                RoomPose.CANONICAL, RoomPurpose.HANGAR,
                List.of(new Doorway(ORIGIN, ORIGIN + height / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new BoatBayFitting().fit(floor);
        floor.dropUnreachableWork();
        return ctx;
    }

    private static List<Gantry> boats(GenContext ctx) {
        List<Gantry> found = new ArrayList<>();
        for (Gantry berth : ctx.gantries) {
            if (berth.holds == Gantry.Holds.BOAT) found.add(berth);
        }
        return found;
    }

    /** How many places each berth is worked from, by berth index. */
    private static Map<Integer, Integer> perBerth(GenContext ctx) {
        Map<Integer, Integer> worked = new HashMap<>();
        for (FixtureTask task : ctx.fixtureTasks) {
            if (task.berth() == FixtureTask.NO_BERTH) continue;
            if (task.affordance() != Affordance.SERVICE) continue;
            worked.merge(task.berth(), 1, Integer::sum);
        }
        return worked;
    }

    /** A boat deck keeps boats, and they are boats rather than machines. */
    @Test
    void aBoatDeckKeepsARankOfBoats() {
        List<Gantry> boats = boats(bay(28, 16));

        assertTrue(boats.size() > 1,
                "a cruiser's boat deck holds " + boats.size() + " boats");
    }

    /**
     * A gig bay holds one, not none.
     *
     * <p>The case that matters most, and the one a fixed boat size got wrong: a
     * frigate's whole way off the ship is her gig, and a bay too shallow for a
     * launch gave her an empty deck rather than a smaller boat.
     */
    @Test
    void aGigBayHoldsAGig() {
        List<Gantry> gigs = boats(bay(16, 10));

        assertEquals(1, gigs.size(), "a gig bay holds " + gigs.size() + " boats");
        assertTrue(gigs.get(0).halfWidth < 3,
                "the gig is a launch's size in a bay that could not take a launch");
    }

    /** And a boat deck's boats are bigger than a gig, because it has the deck for them. */
    @Test
    void aBiggerBayKeepsBiggerBoats() {
        Gantry launch = boats(bay(28, 16)).get(0);
        Gantry gig = boats(bay(16, 10)).get(0);

        assertTrue(launch.halfWidth > gig.halfWidth || launch.halfHeight > gig.halfHeight,
                "a boat deck and a gig bay keep the same size boat");
    }

    /**
     * Every boat is backed onto the fuelling run, in every size of bay.
     *
     * <p>Which is also how the berth is checked against the deck it reserved. A
     * berth records half-extents about a centre cell, so it can only describe an
     * odd number of cells — ask it for four and it records three and quietly
     * keeps the fourth as reserved deck belonging to nothing. The symptom is
     * exactly this: a boat that no longer reaches the bulkhead it is supposed to
     * be parked against, which is visible here and invisible in a size.
     *
     * <p>The odd sizes are the ones that would pass anyway. An 11-deep bay is
     * the case that catches it, because that is where the deck it can spare
     * comes out even.
     */
    @Test
    void everyBoatIsBackedOntoTheFuellingRun() {
        int rim = BoatBayFitting.WORKING_BAND + 1;
        for (int[] size : new int[][]{{28, 16}, {24, 14}, {20, 12}, {20, 11}, {16, 10}}) {
            List<Gantry> boats = boats(bay(size[0], size[1]));
            assertTrue(!boats.isEmpty(),
                    "a " + size[0] + "x" + size[1] + " bay kept no boat at all");
            int against = ORIGIN + size[1] - rim - 1;
            for (Gantry boat : boats) {
                assertEquals(against, boat.top(),
                        "a boat in a " + size[0] + "x" + size[1]
                                + " bay is parked off the fuelling run");
            }
        }
    }

    /**
     * Every boat is worked from the deck, which is the trade a garrison apron
     * already has and the reason this room now shares it.
     */
    @Test
    void everyBoatIsServicedFromTheDeck() {
        GenContext ctx = bay(28, 16);
        Map<Integer, Integer> worked = perBerth(ctx);

        List<Gantry> boats = boats(ctx);
        assertTrue(!boats.isEmpty(), "the bay laid no boats to service");
        for (int index = 0; index < ctx.gantries.size(); index++) {
            if (ctx.gantries.get(index).holds != Gantry.Holds.BOAT) continue;
            assertTrue(worked.getOrDefault(index, 0) >= 2,
                    "boat " + index + " is worked from "
                            + worked.getOrDefault(index, 0) + " place(s)");
        }
    }

    /**
     * The servicing is a berth's, not the room's, so an empty bay offers none of
     * it. A boat deck with nothing in it is stores and a deck office.
     */
    @Test
    void servicingIsPublishedAgainstABerthRatherThanACell() {
        for (FixtureTask task : bay(28, 16).fixtureTasks) {
            if (task.affordance() != Affordance.SERVICE) continue;
            assertTrue(task.berth() != FixtureTask.NO_BERTH,
                    "an empty bay would still offer this servicing");
        }
    }

    /**
     * The deck in front of the rank stays open. It is the lane a boat is moved
     * out through and the deck a landing party forms up on, and a bay that
     * filled it would pass a berth count while being useless for the one thing
     * the compartment is for.
     */
    @Test
    void theLaneInFrontOfTheRankStaysOpen() {
        GenContext ctx = bay(28, 16);
        List<Gantry> boats = boats(ctx);
        assertTrue(!boats.isEmpty(), "the bay laid no boats");

        int noseRow = Integer.MAX_VALUE;
        for (Gantry boat : boats) noseRow = Math.min(noseRow, boat.bottom());
        for (int y = ORIGIN + 3; y < noseRow; y++) {
            for (int x = ORIGIN + 3; x < ORIGIN + 28 - 3; x++) {
                assertTrue(ctx.grid.isWalkable(x, y),
                        "the lane in front of the boats is blocked at " + x + "," + y);
            }
        }
    }
}
