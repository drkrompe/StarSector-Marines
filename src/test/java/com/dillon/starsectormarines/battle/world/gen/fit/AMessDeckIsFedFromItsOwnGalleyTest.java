package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mess is the one compartment aboard that is a workplace and an amenity at
 * once, and the arrangement is what keeps those two facts apart.
 *
 * <p>Fitted as ranks of tables it published {@link Affordance#MESS} and nothing
 * else, which is a hall where the whole ship eats three meals a day that nobody
 * made. So what is asked here is not how much the room holds but whether it has
 * two ends: a galley that is worked, a floor that is sat at, and the counter
 * between them.
 *
 * <p>Asked of synthetic rooms rather than of a generated deck. It is a fact
 * about the fitting and the floor it fills; a ship would add ninety world
 * generations and a dozen unrelated reasons to fail.
 */
class AMessDeckIsFedFromItsOwnGalleyTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    private static final int ORIGIN = 4;

    /**
     * The galley end is worked and the floor past it is eaten at, and no point
     * of either kind is on the wrong side of the counter.
     *
     * <p>The ordering is the whole assertion. A room that merely published both
     * affordances somewhere would pass a count and could still be a hall with a
     * stove in the middle of it.
     */
    @Test
    void theWorkedEndAndTheEatingFloorAreDifferentEndsOfTheRoom() {
        Fitted mess = fit(22, 12);

        assertTrue(mess.cookPoints() > 0,
                "the mess publishes nowhere to cook, so the ship eats food nobody made");
        assertTrue(mess.messPoints() > 0, "there is nowhere to sit down and eat");
        assertTrue(mess.lastCook() < mess.firstMess(),
                "cooking reaches to " + mess.lastCook() + " and eating starts at "
                        + mess.firstMess() + ", so the galley and the tables are mixed"
                        + " together rather than divided by the counter");
    }

    /**
     * A table seats a rank of people down each side.
     *
     * <p>Placing a table with an affordance affords exactly one sitting, because
     * the floor finds a single standing cell beside the fixture. A mess deck
     * built that way seats one person per table — furnished, plausible from
     * above, and wrong by a factor of six.
     */
    @Test
    void aTableSeatsMoreThanOnePerson() {
        Fitted mess = fit(22, 12);

        assertTrue(mess.messPoints() >= 2 * mess.tables(),
                "the room holds " + mess.tables() + " tables and seats "
                        + mess.messPoints() + " people, so a table seats one");
    }

    /**
     * A room too small for a galley is a place to eat, not a second galley.
     *
     * <p>The negative matters more than the positive. Every {@link
     * Affordance#COOK} point is a posting, so a ship that stamped a stove into
     * each of her small messes would acquire a watch of cooks per compartment.
     */
    @Test
    void asmallMessIsSomewhereToEatAndNotSomewhereToCook() {
        Fitted small = fit(9, 8);

        assertTrue(small.messPoints() > 0, "a small mess seats nobody at all");
        assertFalse(small.affordances().contains(Affordance.COOK),
                "a nine-cell compartment was given a galley, so the ship posts"
                        + " cooks to a room that cannot hold a range");
    }

    /** What the room holds scales with the room. */
    @Test
    void alargerMessFeedsMorePeople() {
        assertTrue(fit(30, 18).messPoints() > fit(22, 12).messPoints(),
                "a larger mess seats no more people than a small one");
    }

    /** Nothing is walled in and the room can still be walked through. */
    @Test
    void theFillLeavesTheRoomWalkable() {
        Fitted mess = fit(22, 12);

        assertEquals(0, mess.dropped(),
                mess.dropped() + " job(s) were walled in by the room's own fill");
        assertTrue(mess.survives(),
                "the mess severed its own circulation, so it ships as bare deck");
    }

    private record Fitted(int cookPoints, int messPoints, int tables,
                          int firstMess, int lastCook, int dropped,
                          Set<Affordance> affordances, boolean survives) { }

    /**
     * Furnish one mess on a floor of its own and report what came of it.
     *
     * <p>Laid out canonically and upright, so a task's x is its distance from
     * the galley bulkhead and the two ends can be told apart by coordinate.
     */
    private static Fitted fit(int width, int height) {
        int mapWidth = width + 2 * ORIGIN + ORIGIN;
        int mapHeight = height + 2 * ORIGIN + ORIGIN;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        // The hatch is at the dining end, which is where people come in.
        Room room = new Room(RoomShape.rectangle(width, height), ORIGIN, ORIGIN,
                RoomPose.CANONICAL, RoomPurpose.MESS_HALL,
                List.of(new Doorway(ORIGIN + width - 1, ORIGIN + height / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new MessHallFitting().fit(floor);
        int dropped = floor.dropUnreachableWork();

        Set<Affordance> affordances = EnumSet.noneOf(Affordance.class);
        int cook = 0;
        int mess = 0;
        int firstMess = Integer.MAX_VALUE;
        int lastCook = Integer.MIN_VALUE;
        for (FixtureTask task : ctx.fixtureTasks) {
            affordances.add(task.affordance());
            if (task.affordance() == Affordance.COOK) {
                cook++;
                lastCook = Math.max(lastCook, task.cellX());
            } else if (task.affordance() == Affordance.MESS) {
                mess++;
                firstMess = Math.min(firstMess, task.cellX());
            }
        }
        return new Fitted(cook, mess, tables(ctx), firstMess, lastCook, dropped,
                affordances, floor.circulationSurvives());
    }

    /** How many tables went down, counted as the multi-cell fixtures on the floor. */
    private static int tables(GenContext ctx) {
        int tables = 0;
        for (var doodad : ctx.doodads) {
            if (doodad.footprintCellsX > 1 && doodad.footprintCellsY > 1) tables++;
        }
        return Math.max(1, tables);
    }
}
