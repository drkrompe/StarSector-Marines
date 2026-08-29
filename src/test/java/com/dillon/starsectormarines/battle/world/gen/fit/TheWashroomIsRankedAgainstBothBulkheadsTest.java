package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The heads are the most-visited room on the ship, and a single fixture
 * standing in for the whole compartment gave {@link Affordance#WASH} exactly
 * one point to be claimed at — a queue the size of the ship's complement.
 *
 * <p>What is asked here is the shape of the fill: basins ranked against one
 * bulkhead, stalls against the other, several {@link Affordance#WASH} points
 * rather than one, and a walk-through kept clear between the two ranks —
 * asked of one synthetic room rather than a generated deck, because it is a
 * fact about the fitting and the floor it fills.
 *
 * <p>The tile registry in this build does not yet carry the multi-cell basin
 * and stall art ({@code doodad.ship-washbasin-run},
 * {@code doodad.ship-head-stall-bank}), so every assertion here exercises
 * {@link WashroomFitting}'s already-registered fallback furniture. The job
 * count and placement it asserts on are exactly what the room publishes once
 * the real art is built into the atlas, because the fitting places the same
 * number of fixtures at the same cells either way — only the sprite standing
 * on them changes.
 */
class TheWashroomIsRankedAgainstBothBulkheadsTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /** {@code RoomRecipe.WASHROOM}'s own footprint: six cells along, five deep. */
    private static final int ALONG = 6;
    private static final int ACROSS = 5;

    @Test
    void theWashroomPublishesSeveralWashPointsRatherThanOne() {
        Fitted fitted = fit(ALONG, ACROSS);

        assertTrue(fitted.count(Affordance.WASH) >= 6,
                "a washroom with " + fitted.count(Affordance.WASH)
                        + " wash points is one crate standing in for the heads, not several");
        assertEquals(Set.of(Affordance.WASH), fitted.affordances(),
                "the heads publish somewhere to wash, and nothing else");
        assertTrue(fitted.survives(),
                "the washroom's own furniture severed its circulation, so it ships as bare deck");
        assertEquals(0, fitted.dropped(),
                "a wash point was stranded behind furniture instead of being withdrawn");
    }

    /**
     * Basins on one bulkhead, stalls on the other. Furnishing everybody at one
     * kind of fixture would be a room that is all sinks or all stalls, neither
     * of which is a washroom.
     */
    @Test
    void basinsAndStallsStandOnOppositeBulkheads() {
        Fitted fitted = fit(ALONG, ACROSS);

        assertTrue(fitted.distinctFixtures() >= 2,
                "one fixture repeated across the room is a queue for one thing, not a washroom");

        int nearRow = fitted.originY();
        int farRow = fitted.originY() + ACROSS - 1;
        boolean nearFurnished = fitted.doodads().stream().anyMatch(d -> d.cellY == nearRow);
        boolean farFurnished = fitted.doodads().stream()
                .anyMatch(d -> d.cellY + d.footprintCellsY - 1 >= farRow);
        assertTrue(nearFurnished, "nothing stands against the near bulkhead");
        assertTrue(farFurnished, "nothing stands against the far bulkhead");
    }

    /**
     * A washroom scales the way every other ranked compartment does: a longer
     * bulkhead racks more banks, not the same handful spread thinner.
     */
    @Test
    void aLongerWashroomPublishesMoreWashPoints() {
        Fitted wide = fit(3 * ALONG, ACROSS);
        Fitted narrow = fit(ALONG, ACROSS);

        assertTrue(wide.count(Affordance.WASH) > narrow.count(Affordance.WASH),
                "a longer washroom washes no more people than a short one");
    }

    /**
     * A washroom too shallow for two ranks and a walk-through still furnishes
     * its one rank of basins rather than shipping bare — the same "one rank
     * beats none" rule every other ranked compartment on the ship keeps.
     */
    @Test
    void aTooShallowWashroomStillFurnishesOneRank() {
        Fitted fitted = fit(ALONG, 2);

        assertTrue(fitted.count(Affordance.WASH) > 0,
                "a shallow washroom published nowhere to wash at all");
        assertTrue(fitted.survives(), "the shallow washroom's fill severed its circulation");
    }

    private record Fitted(List<Doodad> doodads, List<FixtureTask> tasks,
                          boolean survives, int dropped, int originY) {

        int count(Affordance affordance) {
            return (int) tasks.stream().filter(task -> task.affordance() == affordance).count();
        }

        Set<Affordance> affordances() {
            Set<Affordance> found = new HashSet<>();
            for (FixtureTask task : tasks) found.add(task.affordance());
            return found;
        }

        /** Distinct pieces of art standing in the room, counted off the sprite. */
        int distinctFixtures() {
            Set<String> frames = new HashSet<>();
            for (Doodad doodad : doodads) frames.add(doodad.tile.col + "," + doodad.tile.row);
            return frames.size();
        }
    }

    private static Fitted fit(int along, int across) {
        int mapWidth = along + 8;
        int mapHeight = across + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        int originX = 4;
        int originY = 4;
        Room room = new Room(RoomShape.rectangle(along, across), originX, originY,
                RoomPose.CANONICAL, RoomPurpose.WASHROOM,
                List.of(new Doorway(originX, originY + across / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new WashroomFitting().fit(floor);
        int dropped = floor.dropUnreachableWork();
        return new Fitted(List.copyOf(ctx.doodads), List.copyOf(ctx.fixtureTasks),
                floor.circulationSurvives(), dropped, originY);
    }
}
