package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Parity oracle for moddable-tilesets Phase 1a: asserts the
 * {@code *.tileset.json} registry reproduces the hardcoded {@link NatureTile} /
 * {@link UrbanTile3} semantics exactly. This is the contract that lets Phase 1b
 * delete the enum fields and read from the registry without changing behavior —
 * a failure here means the JSON drifted from the enum it's replacing.
 *
 * <p>Reads the bundled JSON straight off disk (no {@code SettingsAPI} in a unit
 * test), mirroring {@link com.dillon.starsectormarines.tools.tilesets.TilesetCatalogAuditTest}.
 * Frame index equals enum ordinal for both sliced sheets, so a tile's enum
 * counterpart is {@code values()[def.frame]}.
 */
public class TileRegistryParityTest {

    private static final Path DIR = Paths.get("mod/data/tilesets");
    private static final String NATURE_SHEET = "graphics/tilesets/nature-tiles.png";
    private static final String URBAN3_SHEET = "graphics/tilesets/urban-tileset-3.png";

    private static TileRegistry loadRegistry() throws Exception {
        TileRegistry reg = new TileRegistry();
        for (String name : new String[]{"nature-tiles.tileset.json", "urban-tileset-3.tileset.json"}) {
            reg.ingestSheet(new JSONObject(Files.readString(DIR.resolve(name))));
        }
        reg.validateReferences();
        return reg;
    }

    /** {@code frame -> TileDef} for one sheet, so an enum ordinal maps to its registry tile. */
    private static Map<Integer, TileDef> byFrame(TileRegistry reg, String sheetPath) {
        Map<Integer, TileDef> out = new HashMap<>();
        for (TileDef d : reg.all()) {
            if (d.sheetPath.equals(sheetPath)) out.put(d.frame, d);
        }
        return out;
    }

    @Test
    void natureTileCountMatchesEnum() throws Exception {
        Map<Integer, TileDef> nature = byFrame(loadRegistry(), NATURE_SHEET);
        assertEquals(NatureTile.values().length, nature.size(),
                "nature-tiles.tileset.json tile count drifted from NatureTile");
    }

    @Test
    void natureSemanticsMatchEnumFields() throws Exception {
        Map<Integer, TileDef> nature = byFrame(loadRegistry(), NATURE_SHEET);
        for (NatureTile t : NatureTile.values()) {
            TileDef d = nature.get(t.frameIndex());
            assertNotNull(d, "no registry tile for NatureTile." + t + " (frame " + t.frameIndex() + ")");
            assertEquals(expectedLayer(t.kind), d.layer, "layer mismatch for " + t);
            assertEquals(expectedCover(t.cover), d.cover, "cover mismatch for " + t);
            assertEquals(t.passable, d.passable, "passable mismatch for " + t);
        }
    }

    @Test
    void natureCanOverlayMatchesEnum() throws Exception {
        Map<Integer, TileDef> nature = byFrame(loadRegistry(), NATURE_SHEET);
        // Full cross product: every (overlay, base) pair must agree with NatureTile#canOverlay.
        for (NatureTile overlay : NatureTile.values()) {
            TileDef od = nature.get(overlay.frameIndex());
            for (NatureTile base : NatureTile.values()) {
                TileDef bd = nature.get(base.frameIndex());
                assertEquals(overlay.canOverlay(base), od.canOverlayOn(bd),
                        "canOverlay drift: " + overlay + " on " + base);
            }
        }
    }

    @Test
    void urban3SemanticsMatchEnumFields() throws Exception {
        TileRegistry reg = loadRegistry();
        Map<Integer, TileDef> urban = byFrame(reg, URBAN3_SHEET);
        assertEquals(UrbanTile3.values().length, urban.size(),
                "urban-tileset-3.tileset.json tile count drifted from UrbanTile3");
        for (UrbanTile3 t : UrbanTile3.values()) {
            TileDef d = urban.get(t.frameIndex());
            assertNotNull(d, "no registry tile for UrbanTile3." + t + " (frame " + t.frameIndex() + ")");
            assertEquals(expectedLayer(t.kind), d.layer, "layer mismatch for " + t);
        }
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
