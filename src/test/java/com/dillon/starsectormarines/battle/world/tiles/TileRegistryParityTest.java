package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity oracle for moddable-tilesets Phase 1a: asserts the
 * {@code *.tileset.json} registry reproduces the hardcoded {@link NatureTile} /
 * {@link UrbanTile3} semantics exactly. This is the contract that lets Phase 1b
 * delete the enum fields and read from the registry without changing behavior.
 *
 * <p>The enum→id correspondence is pinned by an <b>explicit table</b>
 * ({@link #NATURE_IDS} / {@link #URBAN3_IDS}), NOT by frame index — and the
 * test then asserts {@code def.frame == enumConstant.frameIndex()}. That ordering
 * matters: if the test looked tiles up <em>by</em> frame (as a first cut did), a
 * frame swap between two same-semantics tiles (e.g. the two shrubs) would render
 * the sheet wrong yet pass green — relocating the very "PNG order silently
 * shifts" fragility this track exists to kill into the JSON. Pinning id→frame
 * explicitly closes that hole.
 *
 * <p>Reads the bundled JSON straight off disk (no {@code SettingsAPI} in a unit
 * test), mirroring {@link com.dillon.starsectormarines.tools.tilesets.TilesetCatalogAuditTest}.
 */
public class TileRegistryParityTest {

    private static final Path DIR = Paths.get("mod/data/tilesets");
    private static final String NATURE_SHEET = "graphics/tilesets/nature-tiles.png";
    private static final String URBAN3_SHEET = "graphics/tilesets/urban-tileset-3.png";

    /** Explicit enum→id contract — authored from intent, independent of the JSON's frame field. */
    private static final Map<NatureTile, String> NATURE_IDS = Map.ofEntries(
            entry(NatureTile.GRASS_1,       "nature.grass-1"),
            entry(NatureTile.GRASS_2,       "nature.grass-2"),
            entry(NatureTile.DIRT_1,        "nature.dirt-1"),
            entry(NatureTile.DIRT_2,        "nature.dirt-2"),
            entry(NatureTile.SAND,          "nature.sand"),
            entry(NatureTile.WATER_1,       "nature.water-1"),
            entry(NatureTile.WATER_2,       "nature.water-2"),
            entry(NatureTile.SHRUB_1,       "nature.shrub-1"),
            entry(NatureTile.SHRUB_2,       "nature.shrub-2"),
            entry(NatureTile.GRASS_TUFT_1,  "nature.tuft-1"),
            entry(NatureTile.GRASS_TUFT_2,  "nature.tuft-2"),
            entry(NatureTile.SHRUB_3,       "nature.shrub-3"),
            entry(NatureTile.ROCKS_SMALL_1, "nature.rock-small-1"),
            entry(NatureTile.ROCKS_SMALL_2, "nature.rock-small-2"),
            entry(NatureTile.ROCKS_SMALL_3, "nature.rock-small-3"),
            entry(NatureTile.ROCK_MEDIUM_1, "nature.rock-medium-1"),
            entry(NatureTile.ROCK_MEDIUM_2, "nature.rock-medium-2"),
            entry(NatureTile.ROCK_LARGE_1,  "nature.rock-large-1"),
            entry(NatureTile.ROCK_LARGE_2,  "nature.rock-large-2"),
            entry(NatureTile.ROCK_LARGE_3,  "nature.rock-large-3"));

    private static final Map<UrbanTile3, String> URBAN3_IDS = Map.ofEntries(
            entry(UrbanTile3.STREET_SQUARE,    "urban3.street-square"),
            entry(UrbanTile3.STREET_IRREGULAR, "urban3.street-irregular"),
            entry(UrbanTile3.SIDEWALK,         "urban3.sidewalk"),
            entry(UrbanTile3.SIDEWALK_CORNER,  "urban3.sidewalk-corner"),
            entry(UrbanTile3.CULVERT,          "urban3.culvert"),
            entry(UrbanTile3.BENCH_S,          "urban3.bench-s"),
            entry(UrbanTile3.BENCH_E,          "urban3.bench-e"));

    private static TileRegistry loadRegistry() throws Exception {
        TileRegistry reg = new TileRegistry();
        for (String name : new String[]{"nature-tiles.tileset.json", "urban-tileset-3.tileset.json"}) {
            reg.ingestSheet(new JSONObject(Files.readString(DIR.resolve(name))));
        }
        reg.validateReferences();
        return reg;
    }

    private static long sheetTileCount(TileRegistry reg, String sheetPath) {
        return reg.all().stream().filter(d -> d.sheetPath.equals(sheetPath)).count();
    }

    @Test
    void natureSemanticsAndFrameMatchEnum() throws Exception {
        TileRegistry reg = loadRegistry();
        assertEquals(NatureTile.values().length, NATURE_IDS.size(), "NATURE_IDS table is missing a constant");
        assertEquals(NatureTile.values().length, sheetTileCount(reg, NATURE_SHEET),
                "nature-tiles.tileset.json has tiles the parity table doesn't cover (or vice-versa)");
        for (NatureTile t : NatureTile.values()) {
            String id = NATURE_IDS.get(t);
            TileDef d = reg.tile(id);
            assertNotNull(d, "no registry tile for id '" + id + "' (" + t + ")");
            assertEquals(NATURE_SHEET, d.sheetPath, "wrong sheet for " + id);
            assertEquals(t.frameIndex(), d.frame, "frame mismatch for " + t + " (" + id + ")");
            assertEquals(expectedLayer(t.kind), d.layer, "layer mismatch for " + t);
            assertEquals(expectedCover(t.cover), d.cover, "cover mismatch for " + t);
            assertEquals(t.passable, d.passable, "passable mismatch for " + t);
        }
    }

    @Test
    void natureCanOverlayMatchesEnum() throws Exception {
        TileRegistry reg = loadRegistry();
        // Full cross product: every (overlay, base) pair must agree with NatureTile#canOverlay.
        for (NatureTile overlay : NatureTile.values()) {
            TileDef od = reg.tile(NATURE_IDS.get(overlay));
            for (NatureTile base : NatureTile.values()) {
                TileDef bd = reg.tile(NATURE_IDS.get(base));
                assertEquals(overlay.canOverlay(base), od.canOverlayOn(bd),
                        "canOverlay drift: " + overlay + " on " + base);
            }
        }
    }

    @Test
    void urban3SemanticsAndFrameMatchEnum() throws Exception {
        TileRegistry reg = loadRegistry();
        assertEquals(UrbanTile3.values().length, URBAN3_IDS.size(), "URBAN3_IDS table is missing a constant");
        assertEquals(UrbanTile3.values().length, sheetTileCount(reg, URBAN3_SHEET),
                "urban-tileset-3.tileset.json has tiles the parity table doesn't cover (or vice-versa)");
        for (UrbanTile3 t : UrbanTile3.values()) {
            String id = URBAN3_IDS.get(t);
            TileDef d = reg.tile(id);
            assertNotNull(d, "no registry tile for id '" + id + "' (" + t + ")");
            assertEquals(URBAN3_SHEET, d.sheetPath, "wrong sheet for " + id);
            assertEquals(t.frameIndex(), d.frame, "frame mismatch for " + t + " (" + id + ")");
            assertEquals(expectedLayer(t.kind), d.layer, "layer mismatch for " + t);
        }
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

    @Test
    void builtinSheetsValidateClean() throws Exception {
        // The real bundled sheets must pass the same load-time validation.
        assertTrue(loadRegistry().size() >= NatureTile.values().length + UrbanTile3.values().length);
    }

    private static TileLayer expectedLayer(NatureTile.Kind k) {
        switch (k) {
            case GROUND:        return TileLayer.GROUND;
            case PLANT_OVERLAY: return TileLayer.PLANT;
            case ROCK_OVERLAY:  return TileLayer.ROCK;
            default: throw new AssertionError(k);
        }
    }

    private static TileLayer expectedLayer(UrbanTile3.Kind k) {
        switch (k) {
            case GROUND:  return TileLayer.GROUND;
            case OVERLAY: return TileLayer.OVERLAY;
            default: throw new AssertionError(k);
        }
    }

    private static TileCover expectedCover(NatureTile.Cover c) {
        return c == NatureTile.Cover.LIGHT ? TileCover.LIGHT : TileCover.NONE;
    }
}
