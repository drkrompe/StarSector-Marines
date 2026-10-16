package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The concrete emplacement pieces wear through the ordinary cover-prop channel, piece by piece, corners included. */
class EmplacementDamageTest {

    private static final String[] SHAPES = {"n", "e", "s", "w", "inner-nw", "inner-ne", "inner-sw", "inner-se"};

    private static TileRegistry registry() throws Exception {
        TileRegistry registry = new TileRegistry();
        for (String sheet : new String[]{"fort-emplacement", "fort-emplacement-damage", "fort-emplacement-corners"}) {
            registry.ingestSheet(new JSONObject(Files.readString(Path.of("mod/data/tilesets/" + sheet + ".tileset.json"))));
        }
        return registry;
    }

    @Test
    void everyPieceGoesIntactThenDamagedThenDestroyedByItsOwnHits() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry registry = registry();
        TileRegistry.install(registry);
        try {
            for (String shape : SHAPES) {
                String prefix = TileManifest.EMPLACEMENT_PIECE_PREFIX;
                NavigationGrid grid = new NavigationGrid(5, 5);
                CellTopology topology = new CellTopology(5, 5);
                for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) grid.setWalkableFloor(x, y);
                grid.setWalkable(2, 2, false);
                grid.setSeeThrough(2, 2, true);
                topology.setFixture(2, 2, true);
                DoodadService service = new DoodadService(grid);
                service.addDoodad(new Doodad(2, 2, registry.doodad(prefix + shape)));
                // A piece beside it must keep its own state.
                grid.setWalkable(3, 2, false);
                grid.setSeeThrough(3, 2, true);
                service.addDoodad(new Doodad(3, 2, registry.doodad(prefix + shape)));
                try (NavigationService navigation = new NavigationService(grid, topology, false)) {
                    MapEditor editor = new MapEditor(navigation);
                    editor.setDoodads(service);
                    assertEquals(prefix + shape, service.getDoodads().get(0).definitionId());
                    assertFalse(editor.damageWall(2, 2, 25), shape);
                    assertEquals(prefix + "damaged-" + shape, service.getDoodads().get(0).definitionId(), shape);
                    assertEquals(prefix + shape, service.getDoodads().get(1).definitionId(), "the neighbour is untouched");
                    assertTrue(editor.damageWall(2, 2, 75), shape);
                    assertEquals(prefix + "destroyed-" + shape, service.getDoodads().get(0).definitionId(), shape);
                    assertEquals(0, service.getDoodads().get(0).cover, "a destroyed piece is rubble, not cover");
                    assertEquals(prefix + shape, service.getDoodads().get(1).definitionId(), "the neighbour is untouched");
                }
            }
        } finally {
            TileRegistry.install(previous);
        }
    }

    @Test
    void theTightCollarPicksTheEdgeFacingTheBodyAndTheInnerCornerOnDiagonals() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry.install(registry());
        try {
            assertEquals("doodad.fort-emplacement-s", TileManifest.emplacementTightParapet(0, 0, 0, 1).definitionId());
            assertEquals("doodad.fort-emplacement-e", TileManifest.emplacementTightParapet(0, 0, -1, 0).definitionId());
            assertEquals("doodad.fort-emplacement-inner-se", TileManifest.emplacementTightParapet(0, 0, -1, 1).definitionId());
            assertEquals("doodad.fort-emplacement-inner-nw", TileManifest.emplacementTightParapet(0, 0, 1, -1).definitionId());
            assertNull(TileManifest.emplacementTightParapet(0, 0, 0, 0));
            assertNotNull(TileManifest.emplacementTightParapet(0, 0, 1, 0));
            // A three-by-three body (half-extent 1): the 5x5 ring is three edge pieces per side and four corners.
            String[][] ring = new String[5][5];
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != 2) continue;
                    ring[dx + 2][dy + 2] = TileManifest.emplacementTightParapet(0, 0, dx, dy, 1).definitionId()
                            .substring(TileManifest.EMPLACEMENT_PIECE_PREFIX.length());
                }
            }
            for (int along = -1; along <= 1; along++) {
                assertEquals("s", ring[along + 2][4], "north side");
                assertEquals("n", ring[along + 2][0], "south side");
                assertEquals("e", ring[0][along + 2], "west side");
                assertEquals("w", ring[4][along + 2], "east side");
            }
            assertEquals("inner-se", ring[0][4]);
            assertEquals("inner-sw", ring[4][4]);
            assertEquals("inner-ne", ring[0][0]);
            assertEquals("inner-nw", ring[4][0]);
        } finally {
            TileRegistry.install(previous);
        }
    }
}
