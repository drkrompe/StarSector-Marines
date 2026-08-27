package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Infrastructure checks for the ship-deck pipeline: it runs, it produces a
 * playable map, and it is deterministic.
 *
 * <p>Deliberately minimal. Hull shape, zone progression, room footprints, how
 * densely the packing fills a deck, and where passages end up are authoring
 * decisions reviewed through the {@code ship-decks} snapshot suite — pinning
 * them here would turn every tuning pass into a wall of red.
 */
class ShipDeckGeneratorTest {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 28;
    private static final long[] SEEDS = { 1L, 42L, 1337L };
    /** Widest a door is ever cut; anything past this is a room missing part of a wall. */
    private static final int MAX_DOOR_WIDTH = 2;

    @Test
    void recipeRunsAndPublishesItsStructure() {
        for (long seed : SEEDS) {
            ShipDeckGenerator generator = new ShipDeckGenerator();
            generator.generateDeck(WIDTH, HEIGHT, seed);
            assertNotNull(generator.getLastDeckProfile(), "seed " + seed + ": no deck profile published");
            DeckGraph graph = generator.getLastDeckGraph();
            assertNotNull(graph, "seed " + seed + ": no deck graph published");
            assertTrue(graph.compartmentCount() > 0, "seed " + seed + ": deck placed no rooms");
        }
    }

    /**
     * A vehicle bay authors berths, and a berth is empty floor.
     *
     * <p>What stands in a berth is a unit the host spawns from a roster, so the
     * map has to leave the space for it. A berth with a crate in it, or one cut
     * out of the bulkhead, is a bay the player's mech cannot be put into — and
     * that failure is invisible until someone opens the Mech Lab and finds a
     * machine standing inside a wall.
     */
    @Test
    void vehicleBayBerthsAreClearFloor() {
        for (long seed : SEEDS) {
            // Sized from a transport's own program: a vehicle bay is too large
            // to reliably place on an arbitrary deck, and a berth test that
            // skips whenever the bay did not fit is a test that never runs.
            ShipDeckGenerator generator = new ShipDeckGenerator();
            MapResult map = generator.generateDeck(
                    DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                            10, 250, 50, 0.28f),
                    seed, null);
            assertTrue(generator.getLastDeckGraph().compartments().stream()
                            .anyMatch(c -> c.purpose() == RoomPurpose.VEHICLE_BAY),
                    "seed " + seed + ": a transport deck placed no vehicle bay");
            assertTrue(!map.gantries.isEmpty(),
                    "seed " + seed + ": a vehicle bay published no berths");
            for (Gantry gantry : map.gantries) {
                for (int y = gantry.bottom(); y <= gantry.top(); y++) {
                    for (int x = gantry.left(); x <= gantry.right(); x++) {
                        assertTrue(map.grid.inBounds(x, y) && map.grid.isWalkable(x, y),
                                "seed " + seed + ": berth cell " + x + "," + y
                                        + " is not open floor");
                    }
                }
                for (Doodad doodad : map.doodads) {
                    boolean inside = doodad.cellX >= gantry.left() && doodad.cellX <= gantry.right()
                            && doodad.cellY >= gantry.bottom() && doodad.cellY <= gantry.top();
                    assertTrue(!inside || doodad.cover == Doodad.COVER_NONE,
                            "seed " + seed + ": a fixture stands in a berth at "
                                    + doodad.cellX + "," + doodad.cellY);
                }
            }
        }
    }

    /** The map has to be somewhere a battle can actually run: connected, in-bounds, with usable spawns. */
    @Test
    void deckIsPlayableSpace() {
        for (long seed : SEEDS) {
            ShipDeckGenerator generator = new ShipDeckGenerator();
            MapResult map = generator.generateDeck(WIDTH, HEIGHT, seed);
            DeckProfile profile = generator.getLastDeckProfile();

            assertTrue(map.grid.isWalkable(map.marineSpawnX, map.marineSpawnY),
                    "seed " + seed + ": marine spawn is not walkable");
            assertTrue(map.grid.isWalkable(map.defenderSpawnX, map.defenderSpawnY),
                    "seed " + seed + ": defender spawn is not walkable");

            boolean[][] seen = flood(map.grid, map.marineSpawnX, map.marineSpawnY);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    if (!map.grid.isWalkable(x, y)) continue;
                    assertTrue(profile.containsCell(x, y),
                            "seed " + seed + ": cell " + x + "," + y + " carved outside the hull");
                    assertTrue(seen[y][x],
                            "seed " + seed + ": cell " + x + "," + y + " is carved but unreachable");
                }
            }
        }
    }

    /**
     * A room may open onto a passage through a door and nowhere else.
     *
     * <p>Not an authoring dial — an invariant, and one that has broken twice
     * without showing up as anything but a slightly odd picture. A passage that
     * eats the bulkhead it runs alongside leaves the compartment standing open
     * down its whole side, which costs the room its cover, its chokepoint, and
     * any reason for a squad to clear it rather than walk past.
     */
    @Test
    void roomsAreOpenToPassagesOnlyThroughDoors() {
        for (long seed : SEEDS) {
            MapResult map = new ShipDeckGenerator().generateDeck(WIDTH, HEIGHT, seed);
            assertTrue(widestOpening(map) <= MAX_DOOR_WIDTH,
                    "seed " + seed + ": a room stands open to a passage for "
                            + widestOpening(map) + " cells, wider than a door");
        }
    }

    @Test
    void generationIsDeterministic() {
        for (long seed : SEEDS) {
            ShipDeckGenerator first = new ShipDeckGenerator();
            MapResult firstMap = first.generateDeck(WIDTH, HEIGHT, seed);
            ShipDeckGenerator second = new ShipDeckGenerator();
            MapResult secondMap = second.generateDeck(WIDTH, HEIGHT, seed);

            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    assertEquals(firstMap.grid.isWalkable(x, y), secondMap.grid.isWalkable(x, y),
                            "seed " + seed + ": cell " + x + "," + y + " diverged between runs");
                }
            }
            assertEquals(first.getLastDeckGraph().compartments(), second.getLastDeckGraph().compartments(),
                    "seed " + seed + ": compartment layout diverged between runs");
        }
    }

    /** Longest unbroken stretch along which a room stands open to a corridor. */
    private static int widestOpening(MapResult map) {
        int widest = 0;
        for (int[] facing : new int[][]{ { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } }) {
            boolean alongX = facing[0] == 0;
            int lines = alongX ? HEIGHT : WIDTH;
            int span = alongX ? WIDTH : HEIGHT;
            for (int line = 0; line < lines; line++) {
                int run = 0;
                for (int i = 0; i < span; i++) {
                    int x = alongX ? i : line;
                    int y = alongX ? line : i;
                    if (isCorridor(map, x, y) && isRoom(map, x + facing[0], y + facing[1])) {
                        widest = Math.max(widest, ++run);
                    } else {
                        run = 0;
                    }
                }
            }
        }
        return widest;
    }

    private static boolean isCorridor(MapResult map, int x, int y) {
        return inside(x, y) && map.grid.isWalkable(x, y)
                && map.topology.getRoomPurpose(x, y) == RoomPurpose.CORRIDOR;
    }

    private static boolean isRoom(MapResult map, int x, int y) {
        return inside(x, y) && map.grid.isWalkable(x, y)
                && map.topology.getRoomPurpose(x, y) != RoomPurpose.CORRIDOR;
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT;
    }

    private static boolean[][] flood(NavigationGrid grid, int fromX, int fromY) {
        boolean[][] seen = new boolean[grid.getHeight()][grid.getWidth()];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{ fromX, fromY });
        seen[fromY][fromX] = true;
        int[][] steps = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] step : steps) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= grid.getWidth() || ny >= grid.getHeight()) continue;
                if (seen[ny][nx] || !grid.isWalkable(nx, ny)) continue;
                seen[ny][nx] = true;
                queue.add(new int[]{ nx, ny });
            }
        }
        return seen;
    }
}
