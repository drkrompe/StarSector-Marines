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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rooms a ship's hands are busiest in are busy, and busy in more than
 * one way.
 *
 * <p>Nobody aboard should ever be idle for want of something to do, and the
 * quiet way that fails is a room that is furnished, walkable, and offers one
 * job. A mech bay whose only work was two shoulders per gantry supported two
 * technicians per machine and left the rest of a watch standing in the lane; a
 * boat bay that published stowage and nothing else was a hold with an empty
 * middle. Both looked finished.
 *
 * <p>So these ask the two things a count cannot: that the work is of several
 * kinds, and that it is bounded by the room rather than by a number written into
 * the fitting. Asked of one room on a synthetic floor, because both are facts
 * about a fitting and the floor it fills — no deck, no ship, no seed.
 */
class BaysAreTheBusiestRoomsTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /** One fitted room, and everything that can be asked about how it was worked. */
    private record Fitted(GenContext ctx, int dropped, boolean survives, int left, int top) {

        List<FixtureTask> tasks() {
            return ctx.fixtureTasks;
        }

        Set<Affordance> kinds() {
            Set<Affordance> kinds = EnumSet.noneOf(Affordance.class);
            for (FixtureTask task : tasks()) kinds.add(task.affordance());
            return kinds;
        }

        /** How many places each berth is worked from, by berth index. */
        Map<Integer, Integer> perBerth() {
            Map<Integer, Integer> positions = new HashMap<>();
            for (int berth = 0; berth < ctx.gantries.size(); berth++) positions.put(berth, 0);
            for (FixtureTask task : tasks()) {
                if (task.berth() == FixtureTask.NO_BERTH) continue;
                positions.merge(task.berth(), 1, Integer::sum);
            }
            return positions;
        }

        /** Every fixture standing inside a rectangle of the room's own floor. */
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
     * A mech bay holds machines, stores, benches and a snag list, and every one
     * of those is somebody's.
     *
     * <p>The kinds are the claim rather than the total. A room can reach any
     * count by publishing the same job more often, and a bay that did would
     * still be a bay one trade can work and the rest walk through.
     */
    @Test
    void aMechBayOffersFourKindsOfWorkAtOnce() {
        Fitted bay = fit(RoomPurpose.VEHICLE_BAY, 40, 16, new VehicleBayFitting());

        assertTrue(bay.kinds().containsAll(EnumSet.of(
                        Affordance.SERVICE, Affordance.STOW, Affordance.READOUT,
                        Affordance.FABRICATE, Affordance.REPAIR)),
                "the largest room aboard only offers " + bay.kinds());
        assertTrue(bay.tasks().size() > 90,
                "the bay published " + bay.tasks().size() + " jobs for a whole watch");
        assertTrue(bay.survives(), "the bay's own fill severed its circulation");
        assertEquals(0, bay.dropped(),
                "the bay published work nobody can walk to, which it then had to withdraw");
    }

    /**
     * A berthed machine is worked from several places at once, and none of them
     * is work while the bay stands empty.
     *
     * <p>Both halves matter and they pull against each other. More positions is
     * the point; positions that survive the machine driving out would have an
     * empty gantry frame reading as five people welding air.
     */
    @Test
    void everyBerthIsWorkedFromSeveralPlacesAndOnlyWhileOccupied() {
        Fitted bay = fit(RoomPurpose.VEHICLE_BAY, 40, 16, new VehicleBayFitting());

        assertTrue(bay.ctx().gantries.size() > 1, "the bay laid no berths to work");
        for (Map.Entry<Integer, Integer> berth : bay.perBerth().entrySet()) {
            assertTrue(berth.getValue() >= 4,
                    "berth " + berth.getKey() + " is worked from only "
                            + berth.getValue() + " place(s)");
        }
        for (FixtureTask task : bay.tasks()) {
            if (task.affordance() != Affordance.SERVICE) continue;
            assertTrue(task.berth() != FixtureTask.NO_BERTH,
                    "servicing was published against a cell rather than a berth, so an"
                            + " empty bay still offers it");
        }
    }

    /**
     * The same room has the same defects every time it is built.
     *
     * <p>A defect list picked by an unseeded roll would look right and be wrong
     * in the one way that matters: the snag somebody walked away from would not
     * be there when they walked back.
     */
    @Test
    void theDefectListIsTheSameOnEveryBuild() {
        List<FixtureTask> first = defects(fit(
                RoomPurpose.VEHICLE_BAY, 40, 16, new VehicleBayFitting()));
        List<FixtureTask> again = defects(fit(
                RoomPurpose.VEHICLE_BAY, 40, 16, new VehicleBayFitting()));

        assertTrue(!first.isEmpty(), "the bay carries no defect list at all");
        assertEquals(first, again, "the same bay was built with a different snag list");
    }

    /**
     * A boat bay is worked all the way round a deck that stays clear.
     *
     * <p>The clear middle is the constraint the room is arranged against — a
     * boat is moved through it and a landing party forms up on it — so the test
     * is that the work went round it rather than into it. A bay that filled its
     * deck would pass a job count and be useless for the one thing it is for.
     */
    @Test
    void aBoatBayIsWorkedRoundAClearDeck() {
        Fitted bay = fit(RoomPurpose.HANGAR, 28, 16, new BoatBayFitting());

        assertTrue(bay.kinds().containsAll(EnumSet.of(
                        Affordance.STOW, Affordance.READOUT,
                        Affordance.TEND, Affordance.REPAIR)),
                "the boat bay only offers " + bay.kinds());
        assertTrue(bay.tasks().size() > 25,
                "the boat bay published " + bay.tasks().size() + " jobs");
        assertTrue(bay.survives(), "the boat bay's own fill severed its circulation");
        assertEquals(0, bay.dropped(),
                "the boat bay published work nobody can walk to");

        int band = BoatBayFitting.WORKING_BAND;
        assertTrue(bay.within(band, band, 28 - 2 * band, 16 - 2 * band).isEmpty(),
                "something is standing on the deck the boat has to be moved through");
    }

    /**
     * Both bays take their quota from the room rather than from a constant.
     *
     * <p>The failure this guards is a fitting that works the first N cells it is
     * given: a frigate's gig bay and a cruiser's boat deck would then hold the
     * same amount of work, and a refit that bought a bigger compartment would
     * buy nothing anybody aboard could feel.
     */
    @Test
    void aBiggerRoomHoldsMoreWork() {
        int wideBoatBay = fit(RoomPurpose.HANGAR, 28, 16, new BoatBayFitting())
                .tasks().size();
        int gigBay = fit(RoomPurpose.HANGAR, 16, 10, new BoatBayFitting())
                .tasks().size();
        assertTrue(wideBoatBay > gigBay,
                "a boat deck holds " + wideBoatBay + " jobs and a gig bay " + gigBay);

        int longBay = fit(RoomPurpose.VEHICLE_BAY, 40, 16, new VehicleBayFitting())
                .tasks().size();
        int shortBay = fit(RoomPurpose.VEHICLE_BAY, 24, 16, new VehicleBayFitting())
                .tasks().size();
        assertTrue(longBay > shortBay,
                "a forty-cell bay holds " + longBay + " jobs and a short one " + shortBay);
    }

    private static List<FixtureTask> defects(Fitted bay) {
        List<FixtureTask> defects = new ArrayList<>();
        for (FixtureTask task : bay.tasks()) {
            if (task.affordance() == Affordance.REPAIR) defects.add(task);
        }
        return defects;
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
        return new Fitted(ctx, dropped, floor.circulationSurvives(), 4, 4);
    }
}
