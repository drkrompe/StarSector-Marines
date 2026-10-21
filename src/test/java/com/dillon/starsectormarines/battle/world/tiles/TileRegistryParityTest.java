package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fail-loud guards for the registry's load-time validation, and its dense indexing.
 * Reads the bundled JSON straight off disk (no {@code SettingsAPI} in a unit test).
 */
public class TileRegistryParityTest {

    private static final Path DIR = Paths.get("mod/data/tilesets");

    private static TileRegistry loadRegistry() throws Exception {
        TileRegistry reg = new TileRegistry();
        for (String name : new String[]{"nature-tiles.tileset.json", "urban-tileset-3.tileset.json"}) {
            reg.ingestSheet(new JSONObject(Files.readString(DIR.resolve(name))));
        }
        reg.validateReferences();
        return reg;
    }

    @Test
    void indexingIsDenseAndRoundTrips() throws Exception {
        TileRegistry reg = loadRegistry();
        int n = reg.size();
        Set<Integer> seen = new HashSet<>();
        for (TileDef d : reg.all()) {
            assertTrue(d.index >= 0 && d.index < n, "index out of range for " + d.id);
            assertTrue(seen.add(d.index), "duplicate index " + d.index + " at " + d.id);
            assertEquals(d, reg.byIndex(d.index), "byIndex round-trip failed for " + d.id);
            assertEquals(d.index, reg.indexOf(d.id), "indexOf mismatch for " + d.id);
        }
        assertEquals(n, seen.size(), "indices are not dense 0..n-1");
    }

    // ----- fail-loud guards (lock the registry's load-time validation) --------

    @Test
    void missingFrameFailsLoud() throws Exception {
        JSONObject root = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\","
                + " \"tiles\": [ { \"id\": \"x.a\", \"layer\": \"ground\" } ] }");
        assertThrows(IllegalStateException.class, () -> new TileRegistry().ingestSheet(root));
    }

    @Test
    void duplicateIdFailsLoud() throws Exception {
        JSONObject root = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"tiles\": ["
                + " { \"id\": \"x.a\", \"frame\": 0, \"layer\": \"ground\" },"
                + " { \"id\": \"x.a\", \"frame\": 1, \"layer\": \"ground\" } ] }");
        assertThrows(IllegalStateException.class, () -> new TileRegistry().ingestSheet(root));
    }

    @Test
    void doodadBallisticHeightDefaultsByCoverAndRejectsInvalidValues() throws Exception {
        JSONObject fallback = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"doodads\": ["
                + " { \"id\": \"x.crate\", \"col\": 0, \"row\": 0, \"cover\": \"med\" } ] }");
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(fallback);
        assertEquals(DoodadCover.MED.defaultBallisticHalfHeight(),
                registry.doodad("x.crate").ballisticHalfHeight, 1e-6f);

        JSONObject invalid = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"doodads\": ["
                + " { \"id\": \"x.bad\", \"col\": 0, \"row\": 0, \"cover\": \"med\","
                + " \"ballisticHalfHeight\": -0.1 } ] }");
        assertThrows(IllegalStateException.class, () -> new TileRegistry().ingestSheet(invalid));
    }

    @Test
    void doodadFootprintsDefaultToOneCellAndRejectInvalidSpans() throws Exception {
        JSONObject root = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"doodads\": ["
                + " { \"id\": \"x.one\", \"col\": 0, \"row\": 0, \"cover\": \"light\" },"
                + " { \"id\": \"x.wide\", \"col\": 1, \"row\": 0, \"cover\": \"med\","
                + "   \"footprintCells\": [2, 1] } ] }");
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(root);
        assertEquals(1, registry.doodad("x.one").footprintCellsX);
        assertEquals(1, registry.doodad("x.one").footprintCellsY);
        assertEquals(2, registry.doodad("x.wide").footprintCellsX);
        assertEquals(1, registry.doodad("x.wide").footprintCellsY);

        JSONObject invalid = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"doodads\": ["
                + " { \"id\": \"x.bad\", \"col\": 0, \"row\": 0, \"cover\": \"med\","
                + "   \"footprintCells\": [0, 2] } ] }");
        assertThrows(IllegalStateException.class, () -> new TileRegistry().ingestSheet(invalid));
    }

    @Test
    void doodadPreferredWallSideIsOptionalAndCardinal() throws Exception {
        JSONObject root = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"doodads\": ["
                + " { \"id\": \"x.free\", \"col\": 0, \"row\": 0, \"cover\": \"light\" },"
                + " { \"id\": \"x.sofa\", \"col\": 1, \"row\": 0, \"cover\": \"med\","
                + "   \"preferredWallSide\": \"w\" } ] }");
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(root);
        assertNull(registry.doodad("x.free").preferredWallSide);
        assertEquals(DoodadDef.WallSide.W, registry.doodad("x.sofa").preferredWallSide);

        JSONObject invalid = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"doodads\": ["
                + " { \"id\": \"x.bad\", \"col\": 0, \"row\": 0, \"cover\": \"med\","
                + "   \"preferredWallSide\": \"ceiling\" } ] }");
        assertThrows(IllegalStateException.class, () -> new TileRegistry().ingestSheet(invalid));
    }

    @Test
    void unknownLayerTokenFailsLoud() throws Exception {
        JSONObject root = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"tiles\": ["
                + " { \"id\": \"x.g\", \"frame\": 0, \"layer\": \"ground\" },"
                + " { \"id\": \"x.o\", \"frame\": 1, \"layer\": \"rock\", \"validOn\": [\"layer:grund\"] } ] }");
        TileRegistry reg = new TileRegistry();
        reg.ingestSheet(root);
        assertThrows(IllegalStateException.class, reg::validateReferences);
    }

    @Test
    void unknownValidOnIdFailsLoud() throws Exception {
        JSONObject root = new JSONObject(
                "{ \"sheet\": \"graphics/tilesets/x.png\", \"tiles\": ["
                + " { \"id\": \"x.o\", \"frame\": 0, \"layer\": \"rock\", \"validOn\": [\"x.does-not-exist\"] } ] }");
        TileRegistry reg = new TileRegistry();
        reg.ingestSheet(root);
        assertThrows(IllegalStateException.class, reg::validateReferences);
    }
}
