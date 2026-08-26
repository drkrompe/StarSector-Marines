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
 * Acceptance for the ship-deck family's defining properties: the deck is
 * elongated, its beam varies, it is not mirrored fore to aft, the spine runs
 * bow to stern enclosed by bulkhead, and the whole thing is deterministic.
 */
class ShipDeckGeneratorTest {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 28;
    private static final long[] SEEDS = { 1L, 7L, 42L, 1337L, 90210L };

    @Test
    void deckIsElongatedAlongTheAxis() {
        for (long seed : SEEDS) {
            DeckProfile profile = profileFor(seed);
            int widest = 0;
            for (int f = 0; f < profile.frames(); f++) {
                widest = Math.max(widest, profile.beam(f));
            }
            assertTrue(profile.frames() > widest * 2,
                    "seed " + seed + ": deck should be longer than it is wide, beam=" + widest);
        }
    }

    @Test
    void beamVariesWithFrame() {
        for (long seed : SEEDS) {
            DeckProfile profile = profileFor(seed);
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (int f = 0; f < profile.frames(); f++) {
                min = Math.min(min, profile.beam(f));
                max = Math.max(max, profile.beam(f));
            }
            assertTrue(max > min,
                    "seed " + seed + ": a rectangular deck fails the family; beam " + min + ".." + max);
        }
    }

    @Test
    void hullIsNotMirroredForeToAft() {
        for (long seed : SEEDS) {
            DeckProfile profile = profileFor(seed);
            boolean asymmetric = false;
            for (int f = 0; f < profile.frames() && !asymmetric; f++) {
                if (profile.beam(f) != profile.beam(profile.frames() - 1 - f)) asymmetric = true;
            }
            assertTrue(asymmetric,
                    "seed " + seed + ": deck is mirrored fore to aft, which is station geometry");
        }
    }

    @Test
    void bowIsPointedTheSternIsBluntAndMidshipsIsBroadest() {
        for (long seed : SEEDS) {
            DeckProfile profile = profileFor(seed);
            int bow = profile.beam(0);
            int stern = profile.beam(profile.frames() - 1);
            int midships = profile.beam(profile.frames() / 2);
            assertTrue(midships > bow, "seed " + seed + ": midships should be broader than the bow");
            assertTrue(midships > stern, "seed " + seed + ": midships should be broader than the stern");
            assertTrue(stern > bow, "seed " + seed + ": the stern is blunt, the bow is pointed");
        }
    }

    @Test
    void zonesRunForeToAft() {
        DeckProfile profile = profileFor(SEEDS[0]);
        assertEquals(DeckZone.FORE, profile.zone(0));
        assertEquals(DeckZone.MIDSHIPS, profile.zone(profile.frames() / 2));
        assertEquals(DeckZone.AFT, profile.zone(profile.frames() - 1));
    }

    @Test
    void spineIsFourWideAndEnclosedByBulkhead() {
        for (long seed : SEEDS) {
            ShipDeckGenerator generator = new ShipDeckGenerator();
            MapResult result = generator.generateDeck(WIDTH, HEIGHT, seed);
            DeckProfile profile = generator.getLastDeckProfile();
            assertEquals(4, profile.spineWidth());

            NavigationGrid grid = result.grid;
            for (int x = 0; x < WIDTH; x++) {
                for (int y = profile.spineTop(); y <= profile.spineBottom(); y++) {
                    assertTrue(grid.isWalkable(x, y),
                            "seed " + seed + ": spine cell " + x + "," + y + " must be walkable");
                }
                assertTrue(!grid.isWalkable(x, profile.spineTop() - 1),
                        "seed " + seed + ": bulkhead expected above the spine at frame " + x);
                assertTrue(!grid.isWalkable(x, profile.spineBottom() + 1),
                        "seed " + seed + ": bulkhead expected below the spine at frame " + x);
            }
        }
    }

    @Test
    void spineConnectsBowToStern() {
        for (long seed : SEEDS) {
            ShipDeckGenerator generator = new ShipDeckGenerator();
            MapResult result = generator.generateDeck(WIDTH, HEIGHT, seed);
            DeckProfile profile = generator.getLastDeckProfile();
            int row = (profile.spineTop() + profile.spineBottom()) / 2;
            assertTrue(reachable(result.grid, 0, row, WIDTH - 1, row),
                    "seed " + seed + ": bow must reach stern along the spine");
        }
    }

    @Test
    void spawnsSitAtOppositeEndsOfTheAxis() {
        MapResult result = new ShipDeckGenerator().generateDeck(WIDTH, HEIGHT, SEEDS[0]);
        assertEquals(0, result.marineSpawnX);
        assertEquals(WIDTH - 1, result.defenderSpawnX);
    }

    @Test
    void generationIsDeterministic() {
        for (long seed : SEEDS) {
            MapResult first = new ShipDeckGenerator().generateDeck(WIDTH, HEIGHT, seed);
            MapResult second = new ShipDeckGenerator().generateDeck(WIDTH, HEIGHT, seed);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    assertEquals(first.grid.isWalkable(x, y), second.grid.isWalkable(x, y),
                            "seed " + seed + ": cell " + x + "," + y + " diverged between runs");
                }
            }
        }
    }

    @Test
    void hullPlatingEnclosesTheDeck() {
        for (long seed : SEEDS) {
            DeckProfile profile = profileFor(seed);
            for (int f = 0; f < profile.frames(); f++) {
                assertTrue(profile.top(f) >= 1, "seed " + seed + ": frame " + f + " breaches the top plating");
                assertTrue(profile.bottom(f) <= HEIGHT - 2,
                        "seed " + seed + ": frame " + f + " breaches the bottom plating");
            }
        }
    }

    private static DeckProfile profileFor(long seed) {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        generator.generateDeck(WIDTH, HEIGHT, seed);
        DeckProfile profile = generator.getLastDeckProfile();
        assertNotNull(profile, "the recipe must publish a deck profile");
        return profile;
    }

    /** Flood fill over walkable cells; the spine is the only carved space in this slice. */
    private static boolean reachable(NavigationGrid grid, int fromX, int fromY, int toX, int toY) {
        boolean[][] seen = new boolean[grid.getHeight()][grid.getWidth()];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{ fromX, fromY });
        seen[fromY][fromX] = true;
        int[][] steps = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (cell[0] == toX && cell[1] == toY) return true;
            for (int[] step : steps) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= grid.getWidth() || ny >= grid.getHeight()) continue;
                if (seen[ny][nx] || !grid.isWalkable(nx, ny)) continue;
                seen[ny][nx] = true;
                queue.add(new int[]{ nx, ny });
            }
        }
        return false;
    }
}
