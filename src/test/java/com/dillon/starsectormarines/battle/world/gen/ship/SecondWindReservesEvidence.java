package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Second Wind's reserves on her one deck: her arrangement pins the layout
 * seed, so every campaign lays the same deck, and on it every reserve is
 * placed, sealed and walled off. The gymnasium is laid ahead of the mess and
 * owns its ground; the other reserves are laid after the program and leave
 * every room before them exactly as it was laid without them — its place,
 * floor, furniture, doors and berths.
 *
 * <p>Evidence rather than a unit test: it lays the real hull's deck several
 * times to ask its question. See {@code second-wind-refit-tree.md}.
 */
@Tag("generated-space-evidence")
class SecondWindReservesEvidence {

    /** Seeds a caller might pass; the pinned layout ignores every one. */
    private static final List<Long> CALLER_SEEDS = List.of(42L, 81L, 417L, 2026L, 987654L);

    /** What each opened reserve publishes on her deck. */
    private static final Map<ReservedFacility, Map<Affordance, Integer>> PUBLISHES = Map.of(
            ReservedFacility.FIRING_RANGE, Map.of(Affordance.PRACTICE, 3),
            ReservedFacility.GYMNASIUM, Map.of(Affordance.EXERCISE, 16, Affordance.UNWIND, 3),
            ReservedFacility.SICK_BAY, Map.of(Affordance.TREAT, 4),
            ReservedFacility.REC_LOUNGE, Map.of(Affordance.UNWIND, 6),
            ReservedFacility.BRIG, Map.of(Affordance.GUARD, 1));

    private static HullSilhouette outline;

    /** The converted original hull as the start finds her: wrecked, every reserve sealed. */
    private static CompanyShip secondWind() throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        Path core = Path.of(System.getProperty("starsectorDir"), "starsector-core");
        var spec = new JSONObject(Files.readString(core.resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        outline = HullOutline.fromBounds(polygon, "cerberus");
        return new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED)
                .withWorkshop(WorkshopState.BURIED).withRestoration(ShipRestoration.WRECK);
    }

    @Test
    void everySeedLaysTheSameDeck() throws Exception {
        var plan = secondWind().deckPlan();
        var first = new ShipDeckGenerator();
        MapResult m0 = first.generateDeck(plan, CALLER_SEEDS.get(0), outline);
        for (long seed : CALLER_SEEDS.subList(1, CALLER_SEEDS.size())) {
            var gen = new ShipDeckGenerator();
            MapResult m = gen.generateDeck(plan, seed, outline);
            assertEquals(first.getLastDeckGraph().compartments(), gen.getLastDeckGraph().compartments(),
                    "seed " + seed + " laid a different deck");
            assertEquals(berths(m0), berths(m), "seed " + seed + " moved a berth");
        }
    }

    @Test
    void reservesPlaceWithoutMovingTheProgram() throws Exception {
        var ship = secondWind();
        var plan = ship.deckPlan();
        assertEquals(106, plan.frames(), "reserves never size the deck");
        assertEquals(79, plan.height(), "reserves never size the deck");
        var arrangement = plan.arrangement();
        // Reserves laid ahead of a programmed room own their ground; the rest must not move anything.
        var early = arrangement.reserves().stream().filter(r -> r.laidBefore() != null).toList();
        var bare = plan.withArrangement(new DeckArrangement(arrangement.name(), arrangement.sites(),
                arrangement.fairings(), early, arrangement.layoutSeed()));
        int program = plan.rooms().size() + early.size();

        for (long seed : List.of(42L)) {
            var without = new ShipDeckGenerator();
            MapResult m0 = without.generateDeck(bare, seed, outline);
            var with = new ShipDeckGenerator();
            MapResult m1 = with.generateDeck(plan, seed, outline);
            DeckGraph a = without.getLastDeckGraph();
            DeckGraph b = with.getLastDeckGraph();

            assertTrue(b.unplaced().isEmpty(), "seed " + seed + " left " + b.unplaced() + " unplaced");
            assertEquals(a.compartments().subList(0, program), b.compartments().subList(0, program),
                    "seed " + seed + ": a reserve moved a programmed room or its doors");
            for (int i = 0; i < program; i++) {
                var room = a.compartments().get(i);
                for (int x = room.left(); x <= room.right(); x++) for (int y = room.top(); y <= room.bottom(); y++)
                    if (room.contains(x, y))
                        assertEquals(m0.grid.isWalkable(x, y), m1.grid.isWalkable(x, y),
                                "seed " + seed + ": floor changed in " + room.purpose() + " at " + x + "," + y);
                for (var door : room.doors())
                    assertEquals(m0.grid.isWalkable(door.x(), door.y()), m1.grid.isWalkable(door.x(), door.y()),
                            "seed " + seed + ": door changed on " + room.purpose());
                assertEquals(furniture(m0, room), furniture(m1, room),
                        "seed " + seed + ": furniture changed in " + room.purpose());
            }
            assertEquals(berths(m0), berths(m1), "seed " + seed + ": a reserve moved a berth");

            long reserves = b.compartments().stream().filter(r -> b.reserved(r.id())).count();
            assertEquals(arrangement.reserves().size(), reserves, "seed " + seed);
            for (var room : b.compartments()) {
                if (!b.reserved(room.id())) continue;
                assertTrue(b.damaged(room.id()), "seed " + seed + ": a reserve is in service");
                for (int x = room.left(); x <= room.right(); x++) for (int y = room.top(); y <= room.bottom(); y++)
                    if (room.contains(x, y))
                        assertFalse(m1.grid.isWalkable(x, y), "seed " + seed + ": open floor in reserve " + room.purpose());
                for (var door : room.doors())
                    assertFalse(m1.grid.isWalkable(door.x(), door.y()), "seed " + seed + ": open hatch on " + room.purpose());
            }
            for (var task : m1.fixtureTasks)
                assertTrue(m1.grid.isWalkable(task.cellX(), task.cellY()), "seed " + seed + ": work on closed floor");
        }
    }

    /**
     * Opening every reserve on her deck moves nothing: every compartment,
     * door and berth is where the sealed deck has it, and everything outside the
     * reserves is furnished and worked exactly as before. Inside, each reserve is
     * in service, entered through working hatches, publishing its work where
     * somebody can walk to it, and the range's lanes are shut, see-through deck
     * with nothing standing in them.
     */
    @Test
    void openingReservesMovesNothingAndPutsThemInService() throws Exception {
        var ship = secondWind();
        var closedPlan = ship.deckPlan();
        var openPlan = ship.withRestoration(ShipRestoration.WRECK.withOpenReserves(EnumSet.allOf(ReservedFacility.class))).deckPlan();
        assertEquals(closedPlan.frames(), openPlan.frames());
        assertEquals(closedPlan.height(), openPlan.height());

        for (long seed : List.of(42L)) {
            var sealedGen = new ShipDeckGenerator();
            MapResult m0 = sealedGen.generateDeck(closedPlan, seed, outline);
            var openGen = new ShipDeckGenerator();
            MapResult m1 = openGen.generateDeck(openPlan, seed, outline);
            DeckGraph a = sealedGen.getLastDeckGraph();
            DeckGraph b = openGen.getLastDeckGraph();

            assertTrue(b.unplaced().isEmpty(), "seed " + seed + " left " + b.unplaced() + " unplaced");
            assertEquals(a.compartments(), b.compartments(), "seed " + seed + ": opening a reserve moved a room or a door");
            assertEquals(berths(m0), berths(m1), "seed " + seed + ": opening a reserve moved a berth");
            List<DeckGraph.Compartment> reserves = b.compartments().stream().filter(r -> b.reserved(r.id())).toList();
            assertEquals(ReservedFacility.values().length, reserves.size(), "seed " + seed);
            assertEquals(furnitureOutside(m0, reserves), furnitureOutside(m1, reserves),
                    "seed " + seed + ": opening a reserve changed furniture outside it");
            assertEquals(tasksOutside(m0, reserves), tasksOutside(m1, reserves),
                    "seed " + seed + ": opening a reserve changed work outside it");
            for (var room : a.compartments()) {
                if (reserves.contains(room)) continue;
                for (int x = room.left(); x <= room.right(); x++) for (int y = room.top(); y <= room.bottom(); y++)
                    if (room.contains(x, y))
                        assertEquals(m0.grid.isWalkable(x, y), m1.grid.isWalkable(x, y),
                                "seed " + seed + ": floor changed in " + room.purpose() + " at " + x + "," + y);
            }

            for (var room : reserves) {
                ReservedFacility facility = b.reserve(room.id());
                String where = "seed " + seed + " " + facility;
                assertFalse(b.damaged(room.id()), where + ": an opened reserve is still out of service");
                for (var door : room.doors())
                    assertTrue(m1.grid.isWalkable(door.x(), door.y()), where + ": a hatch is still sealed");
                Set<Long> reached = reachedFromDoors(m1, room);
                Map<Affordance, Integer> counts = new EnumMap<>(Affordance.class);
                for (FixtureTask task : m1.fixtureTasks) {
                    if (!room.contains(task.cellX(), task.cellY())) continue;
                    assertTrue(reached.contains(key(task.cellX(), task.cellY())),
                            where + ": " + task.affordance() + " at " + task.cellX() + "," + task.cellY() + " cannot be walked to");
                    counts.merge(task.affordance(), 1, Integer::sum);
                }
                for (var expected : PUBLISHES.get(facility).entrySet())
                    assertTrue(counts.getOrDefault(expected.getKey(), 0) >= expected.getValue(),
                            where + ": published " + counts + ", wanted at least " + expected.getValue() + " " + expected.getKey());
                if (facility == ReservedFacility.FIRING_RANGE) lanesStayClear(m1, room, where);
            }
        }
    }

    /** Every lane from a firing point to its target is shut, see-through deck with nothing in it but the target plate. */
    private static void lanesStayClear(MapResult map, DeckGraph.Compartment room, String where) {
        Set<Long> plates = new HashSet<>();
        Map<Long, String> furnished = new HashMap<>();
        for (var d : map.doodads) {
            if (!room.contains(d.cellX, d.cellY)) continue;
            if ("firing-range-target.plate".equals(d.definitionId())) { plates.add(key(d.cellX, d.cellY)); continue; }
            // Flat wear on the deck is not standing in anything; cover or height is.
            if (d.cover == 0 && d.ballisticHalfHeight == 0) continue;
            for (int dx = 0; dx < d.footprintCellsX; dx++) for (int dy = 0; dy < d.footprintCellsY; dy++)
                furnished.put(key(d.cellX + dx, d.cellY + dy), d.definitionId());
        }
        int lanes = 0;
        for (FixtureTask task : map.fixtureTasks) {
            if (task.affordance() != Affordance.PRACTICE || !room.contains(task.cellX(), task.cellY())) continue;
            lanes++;
            int dx = Integer.signum(task.fixtureX() - task.cellX()), dy = Integer.signum(task.fixtureY() - task.cellY());
            assertNotEquals(dx == 0, dy == 0, where + ": a lane is not straight");
            assertTrue(plates.contains(key(task.fixtureX(), task.fixtureY())), where + ": a lane has no target");
            int length = 0;
            for (int x = task.cellX() + dx, y = task.cellY() + dy; ; x += dx, y += dy) {
                assertFalse(map.grid.isWalkable(x, y), where + ": lane deck open to walking at " + x + "," + y);
                assertTrue(map.grid.isSeeThrough(x, y), where + ": lane blocked to sight at " + x + "," + y);
                assertFalse(furnished.containsKey(key(x, y)),
                        where + ": " + furnished.get(key(x, y)) + " stands in the lane at " + x + "," + y);
                length++;
                if (x == task.fixtureX() && y == task.fixtureY()) break;
            }
            assertTrue(length >= 6, where + ": a lane only " + length + " cells long");
        }
        assertEquals(3, lanes, where + ": wrong lane count");
    }

    private static Set<Long> reachedFromDoors(MapResult map, DeckGraph.Compartment room) {
        Set<Long> seen = new HashSet<>();
        var queue = new ArrayDeque<int[]>();
        for (var door : room.doors()) if (seen.add(key(door.x(), door.y()))) queue.add(new int[]{door.x(), door.y()});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + step[0], y = c[1] + step[1];
                if (!room.contains(x, y) || !map.grid.isWalkable(x, y) || !seen.add(key(x, y))) continue;
                queue.add(new int[]{x, y});
            }
        }
        return seen;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /** Furniture outside the reserves and off their doorways, where a sealed reserve keeps its hatches. */
    private static List<String> furnitureOutside(MapResult map, List<DeckGraph.Compartment> reserves) {
        List<String> kept = new ArrayList<>();
        for (var d : map.doodads)
            if (reserves.stream().noneMatch(r -> r.contains(d.cellX, d.cellY)
                    || r.doors().stream().anyMatch(door -> door.x() == d.cellX && door.y() == d.cellY)))
                kept.add(d.definitionId() + "@" + d.cellX + "," + d.cellY + ":" + d.quarterTurns);
        return kept;
    }

    private static List<String> tasksOutside(MapResult map, List<DeckGraph.Compartment> reserves) {
        List<String> kept = new ArrayList<>();
        for (FixtureTask t : map.fixtureTasks)
            if (reserves.stream().noneMatch(r -> r.contains(t.cellX(), t.cellY())))
                kept.add(t.affordance() + "@" + t.cellX() + "," + t.cellY() + ">" + t.fixtureX() + "," + t.fixtureY() + "#" + t.berth());
        return kept;
    }

    private static List<String> furniture(MapResult map, DeckGraph.Compartment room) {
        List<String> found = new ArrayList<>();
        for (var d : map.doodads) if (room.contains(d.cellX, d.cellY)) found.add(d.definitionId() + "@" + d.cellX + "," + d.cellY);
        return found;
    }

    private static List<String> berths(MapResult map) {
        List<String> found = new ArrayList<>();
        for (var g : map.gantries) found.add(g.holds + "@" + g.left() + "," + g.bottom() + ":" + g.facing);
        return found;
    }
}
