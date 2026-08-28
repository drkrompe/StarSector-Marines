package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code urban-tileset} must still contain, whoever writes it.
 *
 * <p>The sheet is hand-maintained today, and its authoring document has adopted
 * every last id so that it can one day be generated instead. A generated sheet
 * is packed afresh on each export, which makes ids the only thing that survives
 * one; this pins the ids that have to. {@code urban.mapping.json} names
 * eighteen of these doodads in its pools and
 * {@link GenMappingRegistry#validateReferences()} throws on an id it cannot
 * resolve, so dropping one is a startup crash rather than a missing crate.
 *
 * <p>Cover and half-height are pinned beside them because they are combat
 * numbers that an export can change without changing anything a reader would
 * look at. A crate that stops shots at 0.38 cells and one that stops them at
 * the {@code med} bucket's default are the same row in every catalog and
 * different fights.
 *
 * <p>The coordinates are deliberately <em>not</em> pinned. Where the packer put
 * a piece is the packer's business, and asserting it here would turn every
 * re-pack into a failure that means nothing.
 */
class UrbanTilesetContractTest {

    /** id -> {cover, ballistic half-height}, as the sheet shipped before it was generated. */
    private static final Map<String, Object[]> DOODADS = new LinkedHashMap<>();

    static {
        DOODADS.put("doodad.chair-south-yellow", new Object[]{DoodadCover.MED, 0.30f});
        DOODADS.put("doodad.chair-south-green", new Object[]{DoodadCover.MED, 0.30f});
        DOODADS.put("doodad.box", new Object[]{DoodadCover.MED, 0.28f});
        DOODADS.put("doodad.crate", new Object[]{DoodadCover.MED, 0.38f});
        DOODADS.put("doodad.door-closed", new Object[]{DoodadCover.MED, 0.75f});
        DOODADS.put("doodad.desk-1", new Object[]{DoodadCover.MED, 0.38f});
        DOODADS.put("doodad.chest-1", new Object[]{DoodadCover.MED, 0.32f});
        DOODADS.put("doodad.chest-2", new Object[]{DoodadCover.MED, 0.32f});
        DOODADS.put("doodad.shelf-empty", new Object[]{DoodadCover.HEAVY, 0.75f});
        DOODADS.put("doodad.shelf-1", new Object[]{DoodadCover.HEAVY, 0.75f});
        DOODADS.put("doodad.shelf-2", new Object[]{DoodadCover.HEAVY, 0.75f});
        DOODADS.put("doodad.shelf-3", new Object[]{DoodadCover.HEAVY, 0.75f});
        DOODADS.put("doodad.desk-2", new Object[]{DoodadCover.MED, 0.38f});
        DOODADS.put("doodad.decal-rubble-1", new Object[]{DoodadCover.LIGHT, 0.16f});
        DOODADS.put("doodad.decal-rubble-2", new Object[]{DoodadCover.LIGHT, 0.16f});
        DOODADS.put("doodad.decal-rubble-3", new Object[]{DoodadCover.LIGHT, 0.16f});
        DOODADS.put("doodad.decal-rubble-4", new Object[]{DoodadCover.LIGHT, 0.16f});
        DOODADS.put("doodad.shelf-dam-1", new Object[]{DoodadCover.HEAVY, 0.55f});
        DOODADS.put("doodad.shelf-dam-2", new Object[]{DoodadCover.HEAVY, 0.55f});
        DOODADS.put("doodad.desk-dam", new Object[]{DoodadCover.MED, 0.28f});
        DOODADS.put("doodad.box-dam", new Object[]{DoodadCover.MED, 0.22f});
        DOODADS.put("doodad.chair-s-yellow-dam", new Object[]{DoodadCover.MED, 0.22f});
        DOODADS.put("doodad.chair-s-green-dam", new Object[]{DoodadCover.MED, 0.22f});
    }

    /** id -> {layout, fill or null}. */
    private static final Map<String, Object[]> BLOCKS = new LinkedHashMap<>();

    static {
        BLOCKS.put("urban.wall", new Object[]{GridLayout.WALL_3X3, 0x060A10});
        BLOCKS.put("urban.floor", new Object[]{GridLayout.FLOOR_3X3, null});
        BLOCKS.put("urban.rubble", new Object[]{GridLayout.FLOOR_3X3, null});
        BLOCKS.put("urban.door-open", new Object[]{GridLayout.SINGLE, null});
    }

    /**
     * Paving the deck of a vehicle bay. These cells had no ids until now:
     * {@code VehicleBayFitting} reached them by hardcoded {@code (col,row)},
     * which no export could have survived and no test could have caught.
     */
    private static final String[] PAVING = {
            "doodad.fl-grate-1", "doodad.fl-striped-yellow", "doodad.fl-grate-2" };

    @Test
    void everyShippedDoodadKeepsItsIdCoverAndHeight() throws Exception {
        TileRegistry registry = loadTilesets();
        for (Map.Entry<String, Object[]> expected : DOODADS.entrySet()) {
            DoodadDef def = registry.doodad(expected.getKey());
            assertNotNull(def, "urban-tileset no longer defines " + expected.getKey());
            assertSame(expected.getValue()[0], def.cover,
                    expected.getKey() + " changed cover level");
            assertEquals((float) expected.getValue()[1], def.ballisticHalfHeight, 1e-6f,
                    expected.getKey() + " changed ballistic half height");
        }
    }

    @Test
    void bayPavingIsAddressableById() throws Exception {
        TileRegistry registry = loadTilesets();
        for (String id : PAVING) {
            DoodadDef def = registry.doodad(id);
            assertNotNull(def, "vehicle-bay paving lost " + id);
            assertSame(DoodadCover.NONE, def.cover, id + " should not grant cover");
        }
    }

    @Test
    void everyShippedBlockKeepsItsLayoutAndFill() throws Exception {
        TileRegistry registry = loadTilesets();
        for (Map.Entry<String, Object[]> expected : BLOCKS.entrySet()) {
            GridBlockDef block = registry.block(expected.getKey());
            assertNotNull(block, "urban-tileset no longer defines block " + expected.getKey());
            assertSame(expected.getValue()[0], block.layout,
                    expected.getKey() + " changed layout");
            assertEquals(expected.getValue()[1], block.fillRgb,
                    expected.getKey() + " changed fill colour");
        }
    }

    /**
     * The check that actually models the failure: the shipped catalog and the
     * real generation mapping, loaded together and cross-validated the way
     * {@code onApplicationLoad} does it.
     */
    @Test
    void theShippedCatalogAndTheRealMappingResolveEachOther() throws Exception {
        TileRegistry tiles = loadTilesets();
        tiles.validateReferences();

        TileRegistry previous = TileRegistry.installed();
        try {
            TileRegistry.install(tiles);
            GenMappingRegistry mapping = new GenMappingRegistry();
            for (String path : GenMappingRegistry.BUILTIN_MAPPINGS) {
                mapping.ingest(new JSONObject(read(path)));
            }
            mapping.validateReferences();

            // Not a formality: these are the pools that name urban-tileset ids.
            assertTrue(mapping.doodadPoolIds("MIXED").contains("doodad.box"),
                    "the MIXED pool should still reach the urban sheet");
        } finally {
            TileRegistry.install(previous);
        }
    }

    private static TileRegistry loadTilesets() throws Exception {
        TileRegistry registry = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            registry.ingestSheet(new JSONObject(read(path)));
        }
        return registry;
    }

    private static String read(String modRelativePath) {
        Path path = Paths.get("mod", modRelativePath.split("/"));
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
