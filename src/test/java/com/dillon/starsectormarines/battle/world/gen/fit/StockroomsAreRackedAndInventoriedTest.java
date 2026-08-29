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
 * A stockroom is racked and inventoried, not a hold with crates in it.
 *
 * <p>The old fill was one anchor repeated at a pitch down both bulkheads,
 * which passes a job count and reads as a lattice. What is asked here instead
 * is the shape of the claim the room now makes: that stock sits in more than
 * one known place so there is somewhere to carry a part between, that a running
 * tally is actually taken somewhere, and that the gangway it is all worked from
 * stays clear the whole time.
 *
 * <p>Asked of synthetic floors rather than a generated deck, the same reason
 * every other fitting test in this package is: it is a fact about the fitting
 * and the floor it fills.
 */
class StockroomsAreRackedAndInventoriedTest {

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
     * A full-sized hold's worth of racking carries more than one kind of work:
     * stock handled at, a running tally taken, and — deterministically — a
     * damaged consignment nobody has dealt with yet.
     */
    @Test
    void aHoldOffersHandlingATallyAndAKnownDefect() {
        Fitted room = fit(18, 12);

        assertTrue(room.kinds().containsAll(EnumSet.of(
                        Affordance.STOW, Affordance.READOUT, Affordance.REPAIR)),
                "the hold only offers " + room.kinds());
        assertTrue(room.tasks().size() >= 6,
                "an eighteen-by-twelve hold published only " + room.tasks().size() + " job(s)");
        assertTrue(room.survives(), "the hold's own fill severed its circulation");
        assertEquals(0, room.dropped(),
                "the hold published work nobody can walk to, which it then had to withdraw");
    }

    /**
     * Stowage is work between two known places, so a hold has to publish it at
     * more than one stack — a single stow point has nowhere to carry a part to.
     */
    @Test
    void stowageIsPublishedAtSeveralKnownPlaces() {
        Fitted room = fit(18, 12);

        long stowPoints = room.tasks().stream()
                .filter(task -> task.affordance() == Affordance.STOW)
                .count();
        assertTrue(stowPoints >= 2,
                "the hold published stowage at only " + stowPoints + " place(s), so there is"
                        + " nowhere to carry a part between");
    }

    /**
     * The gangway the racking is worked from is never itself worked into. A
     * fill that racked wall to wall would pass a job count and leave nobody
     * able to walk the room's own length.
     */
    @Test
    void theGangwayStaysClear() {
        Fitted room = fit(18, 12);
        assertTrue(room.within(0, 4, 18, 4).isEmpty(),
                "something is standing in the gangway a stockroom is worked from");
    }

    /**
     * The small utility variant of this room — a four-by-four pocket rather
     * than a full hold — still comes out furnished. A fitting that only holds
     * together at the size of the reference room is not a fitting for the
     * purpose, it is a fitting for one recipe.
     */
    @Test
    void theUtilityVariantIsStillFurnished() {
        Fitted room = fit(4, 4);

        assertTrue(room.tasks().size() > 0, "the small stockroom variant came out bare");
        assertTrue(room.survives(), "the small stockroom's own fill severed its circulation");
        assertEquals(0, room.dropped(),
                "the small stockroom published work nobody can walk to");
    }

    /** A longer hold racks more of itself rather than the same racking with more empty deck round it. */
    @Test
    void aBiggerHoldHoldsMoreWork() {
        int big = fit(18, 12).tasks().size();
        int small = fit(10, 8).tasks().size();
        assertTrue(big > small,
                "an eighteen-by-twelve hold holds " + big + " jobs and a ten-by-eight hold " + small);
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
        Room room = new Room(RoomShape.rectangle(width, height), 4, 4,
                RoomPose.CANONICAL, RoomPurpose.STOCKROOM,
                List.of(new Doorway(4, 4 + height / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new StockroomFitting().fit(floor);
        int dropped = floor.dropUnreachableWork();
        return new Fitted(ctx, dropped, floor.circulationSurvives(), 4, 4);
    }
}
