package com.dillon.starsectormarines.ops.battleview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing.Core;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Placement laws of the Deficit dressing, on a hand-made two-room deck. */
class ShipDeckDressingTest {

    private static final int W = 24, H = 14;
    private static final int[] DOOR = {10, 6};
    private static final int[] BERTH = {2, 8, 4, 11};

    /**
     * Room 0 (x 1-9) has a berth and a blocked corner; a door at (10, 6) opens
     * onto a two-abreast passage (x 11-12); room 1 (x 14-22) is a firing range.
     */
    private static ShipDeckDressing.Deck deck() {
        boolean[] walkable = new boolean[W * H], wall = new boolean[W * H];
        int[] region = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            boolean inside = y >= 1 && y <= 12;
            int r = !inside ? -2 : x >= 1 && x <= 9 ? 0 : x == 11 || x == 12 ? -1 : x >= 14 && x <= 22 ? 1 : -2;
            if (x == DOOR[0] && y == DOOR[1]) r = -1;
            region[i] = r;
            wall[i] = r == -2;
            walkable[i] = r != -2 && !(x >= 6 && x <= 8 && y >= 9 && y <= 11);
        }
        return new ShipDeckDressing.Deck(W, H, walkable, wall, region, new boolean[]{false, true},
                List.<int[]>of(DOOR.clone()), List.<int[]>of(BERTH.clone()), List.of());
    }

    private static Function<String, DoodadDef> registry() {
        Map<String, DoodadDef> defs = new HashMap<>();
        return id -> defs.computeIfAbsent(id, key -> {
            boolean edge = List.of("dead-panel", "conduit", "grille", "buckled-panel", "bracing", "cable-run")
                    .contains(key.substring(key.indexOf('.') + 1));
            return new DoodadDef(key, "sheet.png", 0, 0, DoodadCover.NONE, 0f, 2, edge ? 1 : 2);
        });
    }

    private static List<int[]> cells(ShipDeckDressing.Piece piece) {
        boolean turned = (piece.quarterTurns() & 1) == 1;
        int across = turned ? piece.def().footprintCellsY : piece.def().footprintCellsX;
        int down = turned ? piece.def().footprintCellsX : piece.def().footprintCellsY;
        List<int[]> cells = new ArrayList<>();
        for (int dy = 0; dy < down; dy++) for (int dx = 0; dx < across; dx++)
            cells.add(new int[]{piece.x() + dx, piece.y() + dy});
        return cells;
    }

    @Test
    void theSameShipIsDressedTheSameEveryVisit() {
        var registry = registry();
        for (long seed = 0; seed < 20; seed++)
            assertEquals(ShipDeckDressing.plan(deck(), seed, registry).pieces(),
                    ShipDeckDressing.plan(deck(), seed, registry).pieces());
        assertNotEquals(ShipDeckDressing.plan(deck(), 1, registry).pieces(),
                ShipDeckDressing.plan(deck(), 2, registry).pieces());
    }

    @Test
    void restoringOneCoreClearsOnlyItsOwnDressing() {
        var plan = ShipDeckDressing.plan(deck(), 7, registry());
        var all = plan.pieces(CoreDressing.DEFICIT);
        assertEquals(plan.pieces(), all);
        assertTrue(plan.pieces(CoreDressing.RESTORED).isEmpty());
        for (Core core : Core.values()) {
            var expected = all.stream().filter(p -> p.kind().core != core).toList();
            assertEquals(expected, plan.pieces(CoreDressing.DEFICIT.with(core, true)));
        }
    }

    @Test
    void nothingLandsOnDoorsBerthsBlockedDeckOrTheRange() {
        var registry = registry();
        var deck = deck();
        int placed = 0;
        for (long seed = 0; seed < 200; seed++) {
            for (var piece : ShipDeckDressing.plan(deck, seed, registry).pieces()) {
                placed++;
                int region = deck.region()[deck.index(piece.x(), piece.y())];
                for (int[] c : cells(piece)) {
                    int i = deck.index(c[0], c[1]);
                    assertTrue(deck.walkable()[i], "on unwalkable deck " + piece);
                    assertFalse(deck.wall()[i], "on a bulkhead " + piece);
                    assertNotEquals(1, deck.region()[i], "on the firing range " + piece);
                    assertEquals(region, deck.region()[i], "across a threshold " + piece);
                    assertFalse(Math.abs(c[0] - DOOR[0]) <= 1 && Math.abs(c[1] - DOOR[1]) <= 1, "in a doorway " + piece);
                    assertFalse(c[0] >= BERTH[0] && c[0] <= BERTH[2] && c[1] >= BERTH[1] && c[1] <= BERTH[3],
                            "in a berth " + piece);
                }
            }
        }
        assertTrue(placed > 50, "the deck should be dressed at all: " + placed);
    }

    @Test
    void edgePiecesHugAStraightBulkheadAndLeaveTheLaneClear() {
        var registry = registry();
        var deck = deck();
        int edges = 0, passageEdges = 0;
        for (long seed = 0; seed < 200; seed++) {
            var pieces = ShipDeckDressing.plan(deck, seed, registry).pieces();
            for (var piece : pieces) {
                if (piece.kind().placement != ShipDeckDressing.Placement.EDGE) continue;
                edges++;
                if (deck.region()[deck.index(piece.x(), piece.y())] == -1) passageEdges++;
                int wx = piece.wallDx(), wy = piece.wallDy();
                assertEquals(1, Math.abs(wx) + Math.abs(wy));
                for (int[] c : cells(piece)) {
                    assertTrue(deck.wall()[deck.index(c[0] + wx, c[1] + wy)], "not against its wall " + piece);
                    // The cell inboard is lane: walkable, and no other edge piece stands in it.
                    int ix = c[0] - wx, iy = c[1] - wy;
                    assertTrue(deck.walkable()[deck.index(ix, iy)], "nothing inboard " + piece);
                    for (var other : pieces) {
                        if (other == piece || other.kind().placement != ShipDeckDressing.Placement.EDGE) continue;
                        for (int[] o : cells(other))
                            assertFalse(o[0] == ix && o[1] == iy, "facing pieces close the lane " + piece + " " + other);
                    }
                }
            }
        }
        assertTrue(edges > 20, "edges " + edges);
        assertTrue(passageEdges > 0, "a two-abreast passage still takes wall pieces");
    }

    @Test
    void anUnpackedSheetFallsBackToItsStandInOrIsLeftOut() {
        var plan = ShipDeckDressing.plan(deck(), 3, id -> id.equals("ship-wear.patch") || id.equals("doodad.decal-rubble-2")
                ? new DoodadDef(id, "sheet.png", 0, 0, DoodadCover.NONE, 0f, 2, 2) : null);
        for (var piece : plan.pieces())
            assertTrue(piece.def().id.equals("ship-wear.patch") || piece.def().id.equals("doodad.decal-rubble-2"));
    }

    @Test
    void screensAreRecognisedByWhatTheyAre() {
        assertTrue(ShipDeckDressing.isScreen("ship-bridge.console-east"));
        assertTrue(ShipDeckDressing.isScreen("ship-utility.server"));
        assertTrue(ShipDeckDressing.isScreen("briefing-room.tactical-map"));
        assertFalse(ShipDeckDressing.isScreen("barracks-bunks.bunk"));
        assertFalse(ShipDeckDressing.isScreen(null));
    }

    @Test
    void hazeDensityIsAPureFunctionOfPlaceAndTime() {
        assertEquals(ShipDressingRenderSystem.density(12.3f, 4.5f, 9f), ShipDressingRenderSystem.density(12.3f, 4.5f, 9f));
        for (int i = 0; i < 200; i++) {
            float d = ShipDressingRenderSystem.density(i * .37f, i * .91f, i * .5f);
            assertTrue(d >= 0f && d <= 1f);
        }
    }
}
