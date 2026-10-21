package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Second Wind's reserves across layout seeds: every one is placed, sealed and
 * walled off, and reserving them leaves every programmed room exactly as it
 * was laid without them — its place, floor, furniture, doors and berths.
 *
 * <p>Evidence rather than a unit test: it lays the real hull's deck twice per
 * seed to ask its question. See {@code second-wind-refit-tree.md}.
 */
@Tag("generated-space-evidence")
class SecondWindReservesEvidence {

    private static final int SEEDS = 60;

    @Test
    void reservesPlaceOnEveryLayoutWithoutMovingTheProgram() throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        Path core = Path.of(System.getProperty("starsectorDir"), "starsector-core");
        var spec = new JSONObject(Files.readString(core.resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        var ship = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED)
                .withWorkshop(WorkshopState.BURIED).withRestoration(ShipRestoration.WRECK);
        var plan = ship.deckPlan();
        assertEquals(106, plan.frames(), "reserves never size the deck");
        assertEquals(79, plan.height(), "reserves never size the deck");
        var arrangement = plan.arrangement();
        var bare = plan.withArrangement(new DeckArrangement(arrangement.name(), arrangement.sites(), arrangement.fairings()));
        int program = plan.rooms().size();

        List<Long> seeds = new ArrayList<>(List.of(42L, 81L, 417L, 2026L, 987654L));
        for (long i = 1; seeds.size() < SEEDS; i++) seeds.add(i * 7919L);
        for (long seed : seeds) {
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
