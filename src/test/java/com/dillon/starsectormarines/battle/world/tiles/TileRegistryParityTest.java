package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frozen golden spec for the sliced-sheet tilesets. This started life as a
 * parity oracle against the {@code NatureTile} / {@code UrbanTile3} enums; those
 * enums are now deleted (every consumer reads {@link TileDef} from the registry),
 * so the golden values below ARE the spec and this test is the regression guard
 * that a sheet edit doesn't silently drift a frame / layer / cover / passability
 * or break the overlay-placement contract.
 *
 * <p>Each {@link G} row pins one tile's authored semantics. {@code frame} is an
 * asserted output (NOT the lookup key) so a frame typo that stays in-range and
 * unique still fails — the "PNG order silently shifts" failure this track exists
 * to kill. Reads the bundled JSON straight off disk (no {@code SettingsAPI} in a
 * unit test).
 */
public class TileRegistryParityTest {

    private static final Path DIR = Paths.get("mod/data/tilesets");
    private static final String NATURE_SHEET = "graphics/tilesets/nature-tiles.png";
    private static final String URBAN3_SHEET = "graphics/tilesets/urban-tileset-3.png";

    /** One golden tile row — the authored semantics frozen as the spec. */
    private record G(String id, String sheet, int frame, TileLayer layer, TileCover cover, boolean passable) {}

    private static final List<G> GOLDEN = List.of(
            new G("nature.grass-1",       NATURE_SHEET, 0,  TileLayer.GROUND, TileCover.NONE,  true),
            new G("nature.grass-2",       NATURE_SHEET, 1,  TileLayer.GROUND, TileCover.NONE,  true),
            new G("nature.dirt-1",        NATURE_SHEET, 2,  TileLayer.GROUND, TileCover.NONE,  true),
            new G("nature.dirt-2",        NATURE_SHEET, 3,  TileLayer.GROUND, TileCover.NONE,  true),
            new G("nature.sand",          NATURE_SHEET, 4,  TileLayer.GROUND, TileCover.NONE,  true),
            new G("nature.water-1",       NATURE_SHEET, 5,  TileLayer.GROUND, TileCover.NONE,  true),
            new G("nature.water-2",       NATURE_SHEET, 6,  TileLayer.GROUND, TileCover.NONE,  true),
            new G("nature.shrub-1",       NATURE_SHEET, 7,  TileLayer.PLANT,  TileCover.NONE,  true),
            new G("nature.shrub-2",       NATURE_SHEET, 8,  TileLayer.PLANT,  TileCover.NONE,  true),
            new G("nature.tuft-1",        NATURE_SHEET, 9,  TileLayer.PLANT,  TileCover.NONE,  true),
            new G("nature.tuft-2",        NATURE_SHEET, 10, TileLayer.PLANT,  TileCover.NONE,  true),
            new G("nature.shrub-3",       NATURE_SHEET, 11, TileLayer.PLANT,  TileCover.NONE,  true),
            new G("nature.rock-small-1",  NATURE_SHEET, 12, TileLayer.ROCK,   TileCover.NONE,  true),
            new G("nature.rock-small-2",  NATURE_SHEET, 13, TileLayer.ROCK,   TileCover.NONE,  true),
            new G("nature.rock-small-3",  NATURE_SHEET, 14, TileLayer.ROCK,   TileCover.NONE,  true),
            new G("nature.rock-medium-1", NATURE_SHEET, 15, TileLayer.ROCK,   TileCover.LIGHT, true),
            new G("nature.rock-medium-2", NATURE_SHEET, 16, TileLayer.ROCK,   TileCover.LIGHT, true),
            new G("nature.rock-large-1",  NATURE_SHEET, 17, TileLayer.ROCK,   TileCover.NONE,  false),
            new G("nature.rock-large-2",  NATURE_SHEET, 18, TileLayer.ROCK,   TileCover.NONE,  false),
            new G("nature.rock-large-3",  NATURE_SHEET, 19, TileLayer.ROCK,   TileCover.NONE,  false),

            new G("urban3.street-square",    URBAN3_SHEET, 0, TileLayer.GROUND,  TileCover.NONE, true),
            new G("urban3.street-irregular", URBAN3_SHEET, 1, TileLayer.GROUND,  TileCover.NONE, true),
            new G("urban3.sidewalk",         URBAN3_SHEET, 2, TileLayer.GROUND,  TileCover.NONE, true),
            new G("urban3.sidewalk-corner",  URBAN3_SHEET, 3, TileLayer.GROUND,  TileCover.NONE, true),
            new G("urban3.culvert",          URBAN3_SHEET, 4, TileLayer.OVERLAY, TileCover.NONE, true),
            new G("urban3.bench-s",          URBAN3_SHEET, 5, TileLayer.OVERLAY, TileCover.NONE, true),
            new G("urban3.bench-e",          URBAN3_SHEET, 6, TileLayer.OVERLAY, TileCover.NONE, true));

    private static TileRegistry loadRegistry() throws Exception {
        TileRegistry reg = new TileRegistry();
        for (String name : new String[]{"nature-tiles.tileset.json", "urban-tileset-3.tileset.json"}) {
            reg.ingestSheet(new JSONObject(Files.readString(DIR.resolve(name))));
        }
        reg.validateReferences();
        return reg;
    }

    @Test
    void semanticsMatchGolden() throws Exception {
        TileRegistry reg = loadRegistry();
        assertEquals(GOLDEN.size(), reg.size(),
                "registry has tiles the golden table doesn't cover (or vice-versa)");
        for (G g : GOLDEN) {
            TileDef d = reg.tile(g.id());
            assertNotNull(d, "no registry tile for golden id '" + g.id() + "'");
            assertEquals(g.sheet(),    d.sheetPath, "sheet mismatch for " + g.id());
            assertEquals(g.frame(),    d.frame,     "frame mismatch for " + g.id());
            assertEquals(g.layer(),    d.layer,     "layer mismatch for " + g.id());
            assertEquals(g.cover(),    d.cover,     "cover mismatch for " + g.id());
            assertEquals(g.passable(), d.passable,  "passable mismatch for " + g.id());
        }
    }

    /**
     * The overlay-placement contract the deleted {@code NatureTile.canOverlay}
     * encoded, asserted over the nature sheet (its original domain): plants only
     * on the two grass tiles, rocks on any non-water ground, ground tiles overlay
     * nothing.
     */
    @Test
    void canOverlayContractHolds() throws Exception {
        TileRegistry reg = loadRegistry();
        Set<String> grasses = Set.of("nature.grass-1", "nature.grass-2");
        Set<String> waters = Set.of("nature.water-1", "nature.water-2");
        List<String> plants = List.of("nature.shrub-1", "nature.shrub-2", "nature.tuft-1", "nature.tuft-2", "nature.shrub-3");
        List<String> rocks = List.of(
                "nature.rock-small-1", "nature.rock-small-2", "nature.rock-small-3",
                "nature.rock-medium-1", "nature.rock-medium-2",
                "nature.rock-large-1", "nature.rock-large-2", "nature.rock-large-3");
        List<String> natureGrounds = GOLDEN.stream()
                .filter(g -> g.sheet().equals(NATURE_SHEET) && g.layer() == TileLayer.GROUND)
                .map(G::id).toList();

        for (String p : plants) {
            for (String ground : natureGrounds) {
                assertEquals(grasses.contains(ground), reg.tile(p).canOverlayOn(reg.tile(ground)),
                        "plant " + p + " on " + ground);
            }
        }
        for (String r : rocks) {
            for (String ground : natureGrounds) {
                assertEquals(!waters.contains(ground), reg.tile(r).canOverlayOn(reg.tile(ground)),
                        "rock " + r + " on " + ground);
            }
        }
        for (String ground : natureGrounds) {
            for (G other : GOLDEN) {
                if (!other.sheet().equals(NATURE_SHEET)) continue;
                assertFalse(reg.tile(ground).canOverlayOn(reg.tile(other.id())),
                        "ground tile " + ground + " must not overlay " + other.id());
            }
        }
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
        assertEquals(GOLDEN.size(), loadRegistry().size());
    }
}
