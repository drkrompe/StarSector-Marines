package com.dillon.starsectormarines.ops.battleview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.FacilityDressing.Facility;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Placement laws of the roomless facilities' dressing, on a hand-made three-room deck. */
class ShipFacilityDressingTest {

    private static final int W = 36, H = 17;
    private static final int PROVISIONS = 0, MESS = 1, HOLD = 2;

    /** Art at its shipped footprints. */
    private static final Map<String, int[]> FOOTPRINTS = Map.ofEntries(
            Map.entry("ship-utility.shelf", new int[]{2, 1}), Map.entry("ship-utility.parts", new int[]{2, 1}),
            Map.entry("crew-lounge.planter", new int[]{1, 1}), Map.entry("crew-lounge.books", new int[]{1, 3}),
            Map.entry("crew-lounge.refreshments", new int[]{1, 3}), Map.entry("crew-lounge.media", new int[]{3, 1}),
            Map.entry("briefing-room.display", new int[]{4, 1}), Map.entry("briefing-room.lectern", new int[]{2, 1}),
            Map.entry("main-stores.tally", new int[]{3, 1}), Map.entry("main-stores.pallet", new int[]{2, 2}),
            Map.entry("main-stores.rack", new int[]{2, 6}), Map.entry("main-stores.cartons", new int[]{2, 2}),
            Map.entry("main-stores.cases", new int[]{2, 2}), Map.entry("main-stores.drums", new int[]{2, 2}),
            Map.entry("mess-galley.range", new int[]{1, 2}), Map.entry("mess-galley.table", new int[]{3, 1}));

    private static Function<String, DoodadDef> registry() {
        Map<String, DoodadDef> defs = new HashMap<>();
        return id -> {
            int[] size = FOOTPRINTS.get(id);
            return size == null ? null : defs.computeIfAbsent(id, key ->
                    new DoodadDef(key, "sheet.png", 0, 0, DoodadCover.MED, .5f, size[0], size[1]));
        };
    }

    /**
     * A provisions store (x 1-12, y 1-8) with a pallet in the middle and a
     * hatch east at (13, 4); a mess (x 15-34, y 1-10) with its galley at the
     * west end, two ranks of two tables seated down both sides and a hatch in
     * its north bulkhead at (25, 0); a hold (x 1-12, y 10-15) with a hatch west
     * at (0, 12) and a work point inside it.
     */
    private static ShipFacilityDressing.Deck deck() {
        boolean[] walkable = new boolean[W * H], wall = new boolean[W * H], work = new boolean[W * H];
        int[] region = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            int r = x >= 1 && x <= 12 && y >= 1 && y <= 8 ? PROVISIONS
                    : x >= 15 && x <= 34 && y >= 1 && y <= 10 ? MESS
                    : x >= 1 && x <= 12 && y >= 10 && y <= 15 ? HOLD : ShipFacilityDressing.Deck.NONE;
            region[i] = r;
            wall[i] = r == ShipFacilityDressing.Deck.NONE;
            walkable[i] = !wall[i];
        }
        int[][] doors = {{13, 4}, {25, 0}, {0, 12}};
        for (int[] door : doors) { wall[door[1] * W + door[0]] = false; walkable[door[1] * W + door[0]] = true; }
        List<Doodad> doodads = new ArrayList<>();
        var registry = registry();
        doodads.add(new Doodad(5, 3, registry.apply("main-stores.pallet"), 0, false));
        doodads.add(new Doodad(6, 3, registry.apply("main-stores.pallet"), 0, false));
        for (int y = 2; y <= 8; y += 2) doodads.add(new Doodad(15, y, registry.apply("mess-galley.range"), 0, false));
        for (int x : new int[]{22, 28}) for (int y : new int[]{3, 7}) {
            doodads.add(new Doodad(x, y, registry.apply("mess-galley.table"), 0, false));
            for (int k = 0; k < 3; k++) { work[(y - 1) * W + x + k] = true; work[(y + 1) * W + x + k] = true; }
        }
        work[12 * W + 6] = true;
        boolean[] furnished = new boolean[W * H];
        for (Doodad d : doodads)
            for (int dy = 0; dy < d.footprintCellsY; dy++) for (int dx = 0; dx < d.footprintCellsX; dx++) {
                furnished[(d.cellY + dy) * W + d.cellX + dx] = true;
                walkable[(d.cellY + dy) * W + d.cellX + dx] = false;
            }
        List<ShipFacilityDressing.Room> rooms = List.of(
                new ShipFacilityDressing.Room(PROVISIONS, RoomPurpose.LOADING_BAY, 1, 1, 12, 8, List.<int[]>of(doors[0])),
                new ShipFacilityDressing.Room(MESS, RoomPurpose.MESS_HALL, 15, 1, 34, 10, List.<int[]>of(doors[1])),
                new ShipFacilityDressing.Room(HOLD, RoomPurpose.STOCKROOM, 1, 10, 12, 15, List.<int[]>of(doors[2])));
        return new ShipFacilityDressing.Deck(W, H, walkable, wall, region, furnished, work, rooms, doodads);
    }

    private static List<int[]> cells(Doodad d) {
        List<int[]> cells = new ArrayList<>();
        for (int dy = 0; dy < d.footprintCellsY; dy++) for (int dx = 0; dx < d.footprintCellsX; dx++)
            cells.add(new int[]{d.cellX + dx, d.cellY + dy});
        return cells;
    }

    @Test
    void theSameShipIsDressedTheSameEveryVisit() {
        var registry = registry();
        for (long seed = 0; seed < 10; seed++)
            assertEquals(ShipFacilityDressing.plan(deck(), seed, registry).pieces(),
                    ShipFacilityDressing.plan(deck(), seed, registry).pieces());
    }

    @Test
    void everyTierAddsToItsRoomAndMovesNothingAlreadyThere() {
        var plan = ShipFacilityDressing.plan(deck(), 7, registry());
        assertTrue(plan.pieces(FacilityDressing.NONE).isEmpty());
        Map<Facility, Integer> room = Map.of(Facility.HYDROPONICS, PROVISIONS, Facility.WARDROOM, MESS,
                Facility.BRIEFING, MESS, Facility.HOLDS, HOLD);
        var deck = deck();
        for (Facility facility : Facility.values()) {
            int top = facility == Facility.BRIEFING ? 1 : FacilityDressing.TOP;
            List<ShipFacilityDressing.Piece> before = List.of();
            for (int tier = 1; tier <= top; tier++) {
                int t = tier;
                List<ShipFacilityDressing.Piece> shown = plan.pieces(FacilityDressing.uniform(t)).stream()
                        .filter(p -> p.facility() == facility).toList();
                assertTrue(shown.size() > before.size(), facility + " tier " + tier + " changed nothing");
                assertTrue(shown.containsAll(before), facility + " tier " + tier + " moved an earlier piece");
                for (var p : shown) for (int[] c : cells(p.doodad()))
                    assertEquals(room.get(facility), deck.region()[c[1] * W + c[0]], facility + " strayed out of its room");
                before = shown;
            }
        }
    }

    @Test
    void piecesStandOnOpenFloorAndLeaveTheWayThrough() {
        var deck = deck();
        var plan = ShipFacilityDressing.plan(deck, 3, registry());
        boolean[] blocked = deck.walkable().clone();
        for (int i = 0; i < blocked.length; i++) blocked[i] = !blocked[i];
        for (var piece : plan.pieces()) for (int[] c : cells(piece.doodad())) {
            int i = c[1] * W + c[0];
            assertTrue(deck.walkable()[i], piece + " on closed deck");
            assertFalse(deck.furnished()[i], piece + " on furniture");
            assertFalse(deck.work()[i], piece + " on a work point");
            for (var room : deck.rooms()) for (int[] door : room.doors())
                assertTrue(Math.abs(c[0] - door[0]) > 2 || Math.abs(c[1] - door[1]) > 2, piece + " in a doorway");
            blocked[i] = true;
        }
        for (var room : deck.rooms()) {
            boolean[] before = reach(deck, room, deck.walkable());
            boolean[] open = new boolean[blocked.length];
            for (int i = 0; i < open.length; i++) open[i] = !blocked[i];
            boolean[] after = reach(deck, room, open);
            for (int i = 0; i < before.length; i++)
                if (before[i] && !blocked[i]) assertTrue(after[i], room.purpose() + ": cell " + (i % W) + "," + (i / W) + " cut off");
        }
    }

    @Test
    void aDeckWithoutTheRoomsOrTheArtIsLeftBare() {
        var deck = deck();
        var bare = new ShipFacilityDressing.Deck(W, H, deck.walkable(), deck.wall(), deck.region(), deck.furnished(),
                deck.work(), List.of(), deck.doodads());
        assertTrue(ShipFacilityDressing.plan(bare, 1, registry()).pieces().isEmpty());
        assertTrue(ShipFacilityDressing.plan(deck, 1, id -> null).pieces().isEmpty());
    }

    private static boolean[] reach(ShipFacilityDressing.Deck deck, ShipFacilityDressing.Room room, boolean[] open) {
        boolean[] seen = new boolean[W * H];
        ArrayDeque<int[]> queue = new ArrayDeque<>(room.doors());
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + s[0], y = c[1] + s[1];
                if (x < 0 || y < 0 || x >= W || y >= H) continue;
                int i = y * W + x;
                if (seen[i] || !open[i] || deck.region()[i] != room.id()) continue;
                seen[i] = true;
                queue.add(new int[]{x, y});
            }
        }
        return seen;
    }
}
