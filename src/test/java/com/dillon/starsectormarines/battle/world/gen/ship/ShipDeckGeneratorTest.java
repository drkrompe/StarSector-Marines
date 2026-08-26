package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
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
