package com.dillon.starsectormarines.ops.battleview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing.Facility;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullOutline;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckSnapshotSuite;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipRestoration;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The Second Wind's roomless facilities across layout seeds: every tier of
 * hydroponics, the wardroom, the briefing room and the holds dresses its room
 * on every layout, buying a tier only adds pieces, and planning the dressing
 * moves nothing programmed — every compartment, door, berth, fixture, work
 * point and walkable cell is what generation laid. The pieces stand on open
 * floor of their own room, off work points and doorways, and leave every work
 * point and every reachable cell of the room reachable round them.
 *
 * <p>Evidence rather than a unit test: it lays the real hull's deck twice per
 * seed to ask its question. See {@code second-wind-refit-tree.md}.
 */
@Tag("generated-space-evidence")
class SecondWindFacilitiesEvidence {

    private static final int SEEDS = 40;

    private static final Map<Facility, RoomPurpose> ROOMS = Map.of(Facility.HYDROPONICS, RoomPurpose.LOADING_BAY,
            Facility.WARDROOM, RoomPurpose.MESS_HALL, Facility.BRIEFING, RoomPurpose.MESS_HALL,
            Facility.HOLDS, RoomPurpose.STOCKROOM);

    @Test
    void everyTierDressesItsRoomOnEveryLayoutAndMovesNothing() throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        Path core = Path.of(System.getProperty("starsectorDir"), "starsector-core");
        var spec = new JSONObject(Files.readString(core.resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        // Power and the mess restored: what the facilities' own orders need before any is bought.
        var ship = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED)
                .withWorkshop(WorkshopState.BURIED).withRestoration(new ShipRestoration(true, false, false, true, true));
        var plan = ship.deckPlan();

        List<Long> seeds = new ArrayList<>(List.of(42L, 81L, 417L, 2026L, 987654L));
        for (long i = 1; seeds.size() < SEEDS; i++) seeds.add(i * 7919L);
        Map<Facility, int[]> fewest = new EnumMap<>(Facility.class);
        for (long seed : seeds) {
            var reference = new ShipDeckGenerator();
            MapResult m0 = reference.generateDeck(plan, seed, outline);
            var dressed = new ShipDeckGenerator();
            MapResult m1 = dressed.generateDeck(plan, seed, outline);
            DeckGraph graph = dressed.getLastDeckGraph();
            List<String> before = snapshot(m1, graph);

            var dressing = ShipFacilityDressing.of(m1.topology, m1.grid, graph, m1.doodads, m1.fixtureTasks, seed);
            for (int tier = 0; tier <= FacilityDressing.TOP; tier++) dressing.doodads(FacilityDressing.uniform(tier));

            assertEquals(snapshot(m0, reference.getLastDeckGraph()), before, "seed " + seed + ": generation is not repeatable");
            assertEquals(before, snapshot(m1, graph), "seed " + seed + ": dressing moved something programmed");

            for (Facility facility : Facility.values()) {
                var room = graph.largest(ROOMS.get(facility));
                assertTrue(room != null, "seed " + seed + ": no room for " + facility);
                int top = facility == Facility.BRIEFING ? 1 : FacilityDressing.TOP;
                int[] counts = fewest.computeIfAbsent(facility, f -> new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE});
                List<ShipFacilityDressing.Piece> shown = List.of();
                for (int tier = 1; tier <= top; tier++) {
                    List<ShipFacilityDressing.Piece> next = dressing.pieces(FacilityDressing.uniform(tier)).stream()
                            .filter(p -> p.facility() == facility).toList();
                    String where = "seed " + seed + " " + facility + " tier " + tier;
                    assertTrue(next.containsAll(shown), where + ": moved an earlier tier's piece");
                    assertTrue(next.size() > shown.size(), where + ": changes nothing in the " + room.purpose());
                    counts[tier] = Math.min(counts[tier], next.size() - shown.size());
                    shown = next;
                }
                for (var piece : shown) for (int[] c : cells(piece.doodad())) {
                    String where = "seed " + seed + " " + facility + " " + piece.def().id + " at " + c[0] + "," + c[1];
                    assertTrue(room.contains(c[0], c[1]), where + ": outside its room");
                    assertTrue(m1.grid.isWalkable(c[0], c[1]), where + ": on closed deck");
                    for (var door : room.doors())
                        assertTrue(Math.abs(c[0] - door.x()) > 2 || Math.abs(c[1] - door.y()) > 2, where + ": in a doorway");
                }
            }
            Set<Long> work = new HashSet<>();
            for (FixtureTask task : m1.fixtureTasks) work.add(key(task.cellX(), task.cellY()));
            Set<Long> standing = new HashSet<>();
            for (var piece : dressing.pieces(FacilityDressing.uniform(FacilityDressing.TOP)))
                for (int[] c : cells(piece.doodad())) {
                    assertFalse(work.contains(key(c[0], c[1])), "seed " + seed + ": " + piece.def().id + " on a work point");
                    standing.add(key(c[0], c[1]));
                }
            for (RoomPurpose purpose : Set.copyOf(ROOMS.values())) {
                var room = graph.largest(purpose);
                Set<Long> open = reached(m1, room, Set.of()), around = reached(m1, room, standing);
                for (long cell : open)
                    assertTrue(standing.contains(cell) || around.contains(cell),
                            "seed " + seed + " " + purpose + ": cell " + (cell >> 32) + "," + (int) cell + " cut off by the dressing");
            }
        }
        StringBuilder report = new StringBuilder("Fewest pieces each tier adds over " + SEEDS + " layouts:\n");
        fewest.forEach((f, c) -> report.append(f).append(": ").append(c[1]).append(" / ").append(c[2] == Integer.MAX_VALUE ? "-" : c[2])
                .append(" / ").append(c[3] == Integer.MAX_VALUE ? "-" : c[3]).append('\n'));
        System.out.print(report);
    }

    /** Everything generation decides that a dressing could disturb. */
    private static List<String> snapshot(MapResult map, DeckGraph graph) {
        List<String> facts = new ArrayList<>();
        for (var room : graph.compartments()) facts.add(room.toString());
        for (var g : map.gantries) facts.add(g.holds + "@" + g.left() + "," + g.bottom() + ":" + g.facing);
        for (Doodad d : map.doodads) facts.add(d.definitionId() + "@" + d.cellX + "," + d.cellY + ":" + d.quarterTurns);
        for (FixtureTask t : map.fixtureTasks) facts.add(t.toString());
        StringBuilder walk = new StringBuilder();
        for (int y = 0; y < map.grid.getHeight(); y++) for (int x = 0; x < map.grid.getWidth(); x++)
            walk.append(map.grid.isWalkable(x, y) ? '.' : '#').append(map.grid.isSeeThrough(x, y) ? ' ' : '|');
        facts.add(walk.toString());
        return facts;
    }

    private static Set<Long> reached(MapResult map, DeckGraph.Compartment room, Set<Long> standing) {
        Set<Long> seen = new HashSet<>();
        var queue = new ArrayDeque<int[]>();
        for (var door : room.doors()) queue.add(new int[]{door.x(), door.y()});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + step[0], y = c[1] + step[1];
                long k = key(x, y);
                if (!room.contains(x, y) || !map.grid.isWalkable(x, y) || standing.contains(k) || !seen.add(k)) continue;
                queue.add(new int[]{x, y});
            }
        }
        return seen;
    }

    private static List<int[]> cells(Doodad d) {
        List<int[]> cells = new ArrayList<>();
        for (int dy = 0; dy < d.footprintCellsY; dy++) for (int dx = 0; dx < d.footprintCellsX; dx++)
            cells.add(new int[]{d.cellX + dx, d.cellY + dy});
        return cells;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
