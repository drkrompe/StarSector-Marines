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
 * A parts cage separates the issue side from the stock side behind a wire
 * front, rather than racking a shelf down both bulkheads with no distinction
 * between drawing a part and keeping one.
 *
 * <p>The claim under test is the arrangement, not just the job count: that the
 * cage front actually stands (or falls back to standing) between the two
 * sides, that the gate the gangway cuts through it is never itself furnished,
 * and that the dense stock behind the front still publishes real work rather
 * than being scenery nobody can reach.
 */
class PartsCagesSeparateIssueFromStockTest {

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
     * A full-sized cage offers both the dense stock's handling and its own
     * running stock-take — two kinds of work, the way the mixed stock side
     * this room's javadoc describes actually requires.
     */
    @Test
    void theCageOffersStockHandlingAndAStockTake() {
        Fitted room = fit(14, 10);

        assertTrue(room.kinds().containsAll(EnumSet.of(Affordance.STOW, Affordance.READOUT)),
                "the parts cage only offers " + room.kinds());
        assertTrue(room.tasks().size() >= 5,
                "a fourteen-by-ten parts cage published only " + room.tasks().size() + " job(s)");
        assertTrue(room.survives(), "the parts cage's own fill severed its circulation");
        assertEquals(0, room.dropped(),
                "the parts cage published work nobody can walk to");
    }

    /**
     * Stock is handled at several bins, not one, so there is somewhere to
     * carry a part between — the same rule every stores compartment on the
     * ship keeps.
     */
    @Test
    void stockIsHandledAtSeveralBins() {
        Fitted room = fit(14, 10);
        long stowPoints = room.tasks().stream()
                .filter(task -> task.affordance() == Affordance.STOW)
                .count();
        assertTrue(stowPoints >= 2,
                "the parts cage published stowage at only " + stowPoints + " bin(s)");
    }

    /**
     * The gate the gangway cuts through the cage front is never itself
     * furnished — the cage front is attempted the room's whole depth and is
     * expected to fail exactly there, which is what makes it a gate rather
     * than a wall with a hole cut in it afterwards.
     */
    @Test
    void theGateWhereTheGangwayCrossesTheFrontStaysOpen() {
        Fitted room = fit(14, 10);
        // Matches this fitting's own gangway sizing: band 3, gangway rows 3..6,
        // gate at the issue depth (3).
        assertTrue(room.within(3, 3, 1, 4).isEmpty(),
                "the cage front's gate is blocked by a fixture");
    }

    /**
     * The small utility variant of this room still comes out furnished and
     * still keeps its own gangway open.
     */
    @Test
    void theUtilityVariantIsStillFurnishedAndKeepsItsGate() {
        Fitted room = fit(6, 4);

        assertTrue(room.tasks().size() > 0, "the small parts-cage variant came out bare");
        assertTrue(room.survives(), "the small parts cage's own fill severed its circulation");
        assertEquals(0, room.dropped(),
                "the small parts cage published work nobody can walk to");
    }

    /** A bigger cage racks more bins rather than the same handful with more empty deck round it. */
    @Test
    void aBiggerCageHoldsMoreStock() {
        int big = fit(14, 10).tasks().size();
        int small = fit(9, 6).tasks().size();
        assertTrue(big > small,
                "a fourteen-by-ten parts cage holds " + big + " jobs and a nine-by-six cage " + small);
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
                RoomPose.CANONICAL, RoomPurpose.PARTS_CAGE,
                List.of(new Doorway(4, 4 + height / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new PartsCageFitting().fit(floor);
        int dropped = floor.dropUnreachableWork();
        return new Fitted(ctx, dropped, floor.circulationSurvives(), 4, 4);
    }
}
