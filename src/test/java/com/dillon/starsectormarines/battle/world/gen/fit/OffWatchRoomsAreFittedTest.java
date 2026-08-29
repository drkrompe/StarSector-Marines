package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two compartments a crew goes to when it is <em>not</em> working have to be
 * furnished as carefully as the ones it works in.
 *
 * <p>They are the only rooms aboard whose content is that there is none, and
 * that makes them easy to get wrong in a way nothing else is. A lounge fitted
 * like a berth is a waiting room; a gymnasium fitted like a store is a store
 * with a rowing machine in it. Both would still publish their jobs and both
 * would still pass a check that only counted work, so what is asked here is the
 * shape of the fill rather than its size: that the lounge's seating comes in
 * separated groups, and that the gym's work stands on open deck.
 *
 * <p>Asked of one synthetic room of each purpose rather than of a generated
 * deck. It is a fact about the fitting and the floor it fills, so a ship would
 * only add ninety world generations and a dozen unrelated reasons to fail.
 */
class OffWatchRoomsAreFittedTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /**
     * A lounge is islands, not ranks.
     *
     * <p>The count of separated clusters is the whole assertion. Ranks of
     * identical sofas down both bulkheads would furnish the room, seat the same
     * complement, publish the same jobs, and read as somewhere people wait to be
     * seen — and nothing but the arrangement tells the two apart.
     */
    @Test
    void theLoungeSeatsPeopleInGroupsRatherThanRanks() {
        Fitted fitted = fit(RoomPurpose.CREW_LOUNGE, 16, 12, new LoungeFitting());

        assertTrue(fitted.tasks() > 0, "nobody can sit down in the lounge");
        assertEquals(Set.of(Affordance.UNWIND), fitted.affordances(),
                "a lounge publishes somewhere to be, and nothing else");
        assertTrue(fitted.survives(),
                "the lounge's own furniture severed its circulation, so it ships as bare deck");
        assertTrue(fitted.clusters() >= 4,
                "the lounge furnished itself as " + fitted.clusters()
                        + " connected run(s), which is a rank rather than conversation groups");
        assertTrue(fitted.distinctFixtures() >= 5,
                "one motif repeated across the room is a waiting room, not a lounge");
    }

    /**
     * A gym's work stands on the deck the gear is standing out of the way of.
     *
     * <p>Nothing withdrawn is the sharp end of it: a training station is
     * published on open floor rather than beside a prop, so a station that
     * turned out to be unreachable would mean the middle had been filled in
     * after all.
     */
    @Test
    void theGymnasiumWorksTheClearMiddleItKeeps() {
        Fitted fitted = fit(RoomPurpose.GYMNASIUM, 14, 10, new GymFitting());

        assertTrue(fitted.doodads() > 0, "the gym has no gear at all");
        assertEquals(Set.of(Affordance.EXERCISE), fitted.affordances(),
                "a gym publishes training, and nothing else");
        assertTrue(fitted.middleTasks() > 0,
                "every job in the gym is against a bulkhead, so its middle is decoration");
        assertEquals(0, fitted.dropped(),
                "a training station was walled in, so the clear deck is not clear");
        assertTrue(fitted.survives(),
                "the gym's own gear severed its circulation, so it ships as bare deck");
    }

    /**
     * Both rooms take their capacity from their footprint.
     *
     * <p>A fixed job count would make a barge's lounge and a frigate's the same
     * room with different amounts of empty deck round it, which is the one thing
     * a fill scaled to the hull is for.
     */
    @Test
    void bothRoomsScaleTheirWorkWithTheirFootprint() {
        assertTrue(fit(RoomPurpose.CREW_LOUNGE, 27, 23, new LoungeFitting()).tasks()
                        > fit(RoomPurpose.CREW_LOUNGE, 16, 12, new LoungeFitting()).tasks(),
                "a larger lounge seats no more people than a small one");
        assertTrue(fit(RoomPurpose.GYMNASIUM, 24, 18, new GymFitting()).tasks()
                        > fit(RoomPurpose.GYMNASIUM, 14, 10, new GymFitting()).tasks(),
                "a larger gym trains no more people than a small one");
    }

    private record Fitted(int doodads, int distinctFixtures, int clusters,
                          int tasks, int middleTasks, int dropped,
                          Set<Affordance> affordances, boolean survives) { }

    /**
     * Furnish one room on a floor of its own and report what came of it.
     *
     * <p>The room sits four cells inside a walkable map so that its wall ring
     * and its one hatch have somewhere to be, and the fitting is passed in
     * rather than looked up: these two are being tested, not whichever fitting
     * happens to be registered for the purpose.
     */
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

        Set<String> distinct = new HashSet<>();
        for (Doodad doodad : ctx.doodads) {
            distinct.add(doodad.sheetPath + '@' + doodad.tile.col + ',' + doodad.tile.row);
        }
        Set<Affordance> affordances = new HashSet<>();
        int middle = 0;
        for (FixtureTask task : ctx.fixtureTasks) {
            affordances.add(task.affordance());
            if (inside(task.cellX() - 4, task.cellY() - 4, width, height, 2)) middle++;
        }
        return new Fitted(ctx.doodads.size(), distinct.size(), clusters(ctx, 4, 4, width, height),
                ctx.fixtureTasks.size(), middle, dropped, affordances,
                floor.circulationSurvives());
    }

    /** Whether a cell lies at least {@code band} cells inside the room's bulkheads. */
    private static boolean inside(int x, int y, int width, int height, int band) {
        return x >= band && y >= band && x < width - band && y < height - band;
    }

    /**
     * How many separated runs of furniture the room ended up with.
     *
     * <p>Counted four-connected over the cells a fixture claims. One run means
     * everything is touching everything else, which is what a rank looks like
     * from above; several means islands with floor between them.
     */
    private static int clusters(GenContext ctx, int originX, int originY,
                                int width, int height) {
        boolean[][] filled = new boolean[width][height];
        for (Doodad doodad : ctx.doodads) {
            for (int dx = 0; dx < doodad.footprintCellsX; dx++) {
                for (int dy = 0; dy < doodad.footprintCellsY; dy++) {
                    int x = doodad.cellX + dx - originX;
                    int y = doodad.cellY + dy - originY;
                    if (x >= 0 && y >= 0 && x < width && y < height) filled[x][y] = true;
                }
            }
        }
        int found = 0;
        boolean[][] seen = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!filled[x][y] || seen[x][y]) continue;
                found++;
                Deque<int[]> queue = new ArrayDeque<>(List.of(new int[]{ x, y }));
                seen[x][y] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    for (int[] step : new int[][]{ { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                        int nx = cell[0] + step[0];
                        int ny = cell[1] + step[1];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        if (seen[nx][ny] || !filled[nx][ny]) continue;
                        seen[nx][ny] = true;
                        queue.add(new int[]{ nx, ny });
                    }
                }
            }
        }
        return found;
    }
}
