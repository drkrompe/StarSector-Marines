package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
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
 * An armoury is a counter with a workshop behind it, not an even lattice of
 * shelving with a crate publishing {@link Affordance#ISSUE}.
 *
 * <p>What has to hold is the shape of the arrangement rather than a fixture
 * count: the counter is authored on the public side of the door
 * ({@link ArmoryFitting#handed()} and {@link ArmoryFitting#hookups}), only a
 * couple of counter lengths carry the counter's own job, and the rest of the
 * work is spread across more than one kind. Asked of one room on a synthetic
 * floor, because all of that is a fact about the fitting and the floor it
 * fills — no deck, no ship, no seed.
 */
class ArmoryCounterDividesTheRoomTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    private record Fitted(List<Doodad> doodads, List<FixtureTask> tasks,
                          boolean survives, int dropped) {

        int count(Affordance affordance) {
            return (int) tasks.stream().filter(task -> task.affordance() == affordance).count();
        }

        Set<Affordance> kinds() {
            Set<Affordance> kinds = EnumSet.noneOf(Affordance.class);
            for (FixtureTask task : tasks) kinds.add(task.affordance());
            return kinds;
        }
    }

    /**
     * The authored footprint this room is sited at: 12 amidships, 8 deep.
     * Every band the fitting bands the room into fits inside it exactly, so
     * this is also the size that exercises every branch of the arrangement.
     */
    private static final int ALONG = 12;
    private static final int ACROSS = 8;

    /**
     * The arrangement has a front and a back a mirror image would swap, so it
     * has to take all eight poses rather than merely the four turns.
     */
    @Test
    void theArmouryIsHanded() {
        assertTrue(new ArmoryFitting().handed(),
                "an armoury whose counter can be mirrored onto the door is a different room,"
                        + " not the same one seen from behind");
    }

    /**
     * Every doorway the fitting offers sits on the near bulkhead — row -1 in
     * the canonical frame — which is the apron the counter faces rather than
     * the workshop behind it.
     */
    @Test
    void everyAuthoredDoorwayOpensOntoTheApron() {
        List<Hookup> hookups = new ArmoryFitting().hookups(RoomShape.rectangle(ALONG, ACROSS));
        assertFalse(hookups.isEmpty(), "the armoury offers no way onto the deck at all");
        for (Hookup hookup : hookups) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                for (int[] cell : slot.cells()) {
                    assertEquals(-1, cell[1],
                            "a doorway at " + cell[0] + "," + cell[1]
                                    + " opens into the workshop rather than the apron");
                }
            }
        }
    }

    /** The counter divides the room and the fill still ships. */
    @Test
    void theFillSurvivesItsOwnCounter() {
        Fitted fitted = fit(ALONG, ACROSS);

        assertTrue(fitted.survives(),
                "the counter itself severed the room's circulation, so it ships as bare deck");
        assertEquals(0, fitted.dropped(),
                "the armoury published work nobody can walk to, which it then had to withdraw");
        assertFalse(fitted.doodads().isEmpty(), "the counter and its workshop placed nothing at all");
    }

    /**
     * A counter this size is not staffed at every length of it — six issue
     * points would be six armourers, and the room has one.
     */
    @Test
    void onlyAFewCounterLengthsIssueAnything() {
        Fitted fitted = fit(ALONG, ACROSS);

        int issued = fitted.count(Affordance.ISSUE);
        assertTrue(issued >= 1, "nobody at the counter can actually issue anything");
        assertTrue(issued <= 2,
                "the counter is staffed at " + issued + " points, which is the counter"
                        + " stamped on every length of it rather than a couple of positions");
    }

    /**
     * The workshop behind the counter is more than a bigger version of the
     * same job: restocking the racks and lockers, and a weapon actually being
     * put right on the bench.
     */
    @Test
    void theWorkshopOffersMoreThanIssuing() {
        Fitted fitted = fit(ALONG, ACROSS);

        assertTrue(fitted.kinds().containsAll(EnumSet.of(
                        Affordance.ISSUE, Affordance.STOW, Affordance.REPAIR)),
                "the armoury only offers " + fitted.kinds());
        assertTrue(fitted.count(Affordance.STOW) >= 3,
                "the racks, the ready lockers and the ammunition run are not being restocked");
        assertTrue(fitted.count(Affordance.REPAIR) >= 1,
                "the bench is where a weapon is mended, and nobody can reach it");
    }

    /**
     * Widening the room earns more counter and more rack, not a bigger version
     * of the same handful of jobs.
     */
    @Test
    void aWiderArmouryPublishesMoreWork() {
        int wide = fit(ALONG + 6, ACROSS).tasks().size();
        int narrow = fit(ALONG, ACROSS).tasks().size();
        assertTrue(wide > narrow,
                "a wider armoury published " + wide + " jobs against a narrower " + narrow);
    }

    /**
     * None of the armoury's own art is guaranteed to exist yet — the atlas
     * that bakes it in is a separate pass — so the room has to come out
     * furnished today, on ship's furniture already in service, exactly the way
     * {@code GymFitting} falls back when its own kit is missing.
     */
    @Test
    void theRoomIsFurnishedEvenBeforeItsOwnArtIsBaked() {
        Fitted fitted = fit(ALONG, ACROSS);
        assertTrue(fitted.doodads().size() > 8,
                "the armoury came out nearly bare when its bespoke art was unavailable");
    }

    /** A compartment too small for the arrangement is left bare rather than crammed. */
    @Test
    void aTooSmallArmouryIsLeftBare() {
        Fitted fitted = fit(4, 4);
        assertTrue(fitted.doodads().isEmpty(),
                "a four-by-four armoury furnished something the arrangement cannot fit");
        assertTrue(fitted.tasks().isEmpty(),
                "a four-by-four armoury published work with nowhere sound to put it");
    }

    private static Fitted fit(int along, int across) {
        int mapWidth = along + 8;
        int mapHeight = across + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), mapWidth, mapHeight, 7L);
        // The door sits inside the apron band the fitting reserves whole, so
        // this exercises the ordinary path rather than the defensive stub.
        Room room = new Room(RoomShape.rectangle(along, across), 4, 4,
                RoomPose.CANONICAL, RoomPurpose.ARMORY,
                List.of(new Doorway(4 + along / 2, 4)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new ArmoryFitting().fit(floor);
        int dropped = floor.dropUnreachableWork();
        return new Fitted(List.copyOf(ctx.doodads), List.copyOf(ctx.fixtureTasks),
                floor.circulationSurvives(), dropped);
    }
}
