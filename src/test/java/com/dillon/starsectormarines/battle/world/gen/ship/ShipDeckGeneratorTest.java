package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

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

    /**
     * A furnished bay, and somewhere in it to work.
     *
     * <p>Both halves are here because the bay once passed every structural test
     * while containing nothing whatsoever: its berth reserved the full depth of
     * the module, so the station placed inside that reservation was refused and
     * the frame runs were never placed at all. Marked-out floor and clear berths
     * are exactly what that failure looks like from the outside, which is why
     * this asserts fixtures and work rather than shape.
     */
    @Test
    void vehicleBayIsFurnishedAndOffersWork() {
        for (long seed : SEEDS) {
            ShipDeckGenerator generator = new ShipDeckGenerator();
            MapResult map = generator.generateDeck(
                    DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                            10, 250, 50, 0.28f),
                    seed, null);

            DeckGraph.Compartment bay = generator.getLastDeckGraph().compartments().stream()
                    .filter(c -> c.purpose() == RoomPurpose.VEHICLE_BAY)
                    .findFirst().orElse(null);
            assertNotNull(bay, "seed " + seed + ": a transport deck placed no vehicle bay");

            int fixtures = 0;
            for (Doodad doodad : map.doodads) {
                if (within(bay, doodad.cellX, doodad.cellY) && doodad.cover != Doodad.COVER_NONE) {
                    fixtures++;
                }
            }
            assertTrue(fixtures > 0, "seed " + seed + ": the vehicle bay is painted floor and "
                    + "nothing else");

            // Every berth is worked from both shoulders of its bay, so a bay
            // with a machine in it has somebody at it and an empty one does not.
            Set<Integer> served = new HashSet<>();
            Set<Affordance> offered = EnumSet.noneOf(Affordance.class);
            Set<Long> cells = new HashSet<>();
            for (FixtureTask point : map.fixtureTasks) {
                offered.add(point.affordance());
                assertTrue(cells.add(((long) point.cellX() << 32) ^ (point.cellY() & 0xffffffffL)),
                        "seed " + seed + ": two task points share cell "
                                + point.cellX() + "," + point.cellY());
                assertTrue(map.grid.isWalkable(point.cellX(), point.cellY()),
                        "seed " + seed + ": task point " + point.cellX() + ","
                                + point.cellY() + " is somewhere nobody can stand");
                if (point.berth() == FixtureTask.NO_BERTH) continue;
                assertTrue(point.berth() >= 0 && point.berth() < map.gantries.size(),
                        "seed " + seed + ": task point serves berth " + point.berth()
                                + " of " + map.gantries.size());
                assertEquals(Affordance.SERVICE, point.affordance(),
                        "seed " + seed + ": only servicing is done on a berthed machine");
                served.add(point.berth());
            }
            assertEquals(map.gantries.size(), served.size(),
                    "seed " + seed + ": some berth has nowhere to be worked on from");
            assertTrue(offered.contains(Affordance.READOUT),
                    "seed " + seed + ": no bay published a terminal to read a machine off");
            assertTrue(offered.contains(Affordance.STOW),
                    "seed " + seed + ": no bay published stores anyone has business at");
        }
    }

    /**
     * A bay's doors land where the bay asked for them.
     *
     * <p>Doors used to be wherever the passage search happened to arrive, and a
     * bay dealt with one halfway down its side by clearing a band through both
     * ranks of gantries — two bays given up to a hatch that could have been at
     * the end. The room now states where it hooks up and the placer satisfies
     * it, so this checks the doors against what was authored rather than
     * against a coordinate that would have to be updated whenever the deck
     * changed.
     */
    @Test
    void vehicleBayDoorsLandWhereItAsksForThem() {
        RoomShape canonical = RoomRecipe.VEHICLE_BAY.shape();
        Set<Long> authored = new HashSet<>();
        for (Hookup hookup : RoomFittings.forPurpose(RoomPurpose.VEHICLE_BAY).hookups(canonical)) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                for (int[] cell : slot.cells()) {
                    authored.add(((long) cell[0] << 32) ^ (cell[1] & 0xffffffffL));
                }
            }
        }
        assertTrue(!authored.isEmpty(), "the bay authored no hookups to check against");

        int driveThrough = 0;
        for (long seed : SEEDS) {
            ShipDeckGenerator generator = new ShipDeckGenerator();
            generator.generateDeck(
                    DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                            10, 250, 50, 0.28f),
                    seed, null);
            for (DeckGraph.Compartment bay : generator.getLastDeckGraph().compartments()) {
                if (bay.purpose() != RoomPurpose.VEHICLE_BAY) continue;
                Set<Integer> sides = new HashSet<>();
                for (Doorway door : bay.doors()) {
                    int[] cell = bay.pose().unmap(door.x() - bay.left(), door.y() - bay.top(),
                            canonical.width(), canonical.height());
                    assertTrue(authored.contains(
                                    ((long) cell[0] << 32) ^ (cell[1] & 0xffffffffL)),
                            "seed " + seed + ": a bay door sits at " + cell[0] + "," + cell[1]
                                    + ", which is not a cell the bay hooks up on");
                    sides.add(cell[1]);
                }
                if (sides.size() > 1) driveThrough++;
            }
        }
        assertTrue(driveThrough > 0,
                "no deck served the bay's drive-through, so nothing proves two doorways work");
    }

    /**
     * A bay of a given size berths the same number of machines wherever it is
     * put.
     *
     * <p>Capacity is a facility's fixture count and a player reads it off the
     * room, so it cannot quietly depend on which hull the company happens to
     * have bought. It used to: a door landing partway along the bay had a band
     * cleared the full depth of the room in front of it and every bay
     * overlapping that band was skipped, so two otherwise identical decks
     * berthed six machines and eight. The doors are authored now, and the deck
     * they open onto is part of the arrangement.
     */
    @Test
    void bayCapacityDoesNotDependOnWhereTheDeckPutIt() {
        int berths = -1;
        for (long seed : SEEDS) {
            MapResult map = new ShipDeckGenerator().generateDeck(
                    DeckSizing.planFor(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                            10, 250, 50, 0.28f),
                    seed, null);
            assertTrue(!map.gantries.isEmpty(), "seed " + seed + ": no berths at all");
            if (berths < 0) {
                berths = map.gantries.size();
                continue;
            }
            assertEquals(berths, map.gantries.size(),
                    "seed " + seed + ": the same bay berthed a different number of machines");
        }
    }

    /** Whether a cell falls inside a compartment's own footprint. */
    private static boolean within(DeckGraph.Compartment compartment, int x, int y) {
        return compartment.shape().contains(x - compartment.left(), y - compartment.top());
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
