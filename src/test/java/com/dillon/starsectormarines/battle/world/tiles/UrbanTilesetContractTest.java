package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What {@code urban-tileset} must still contain, whoever writes it.
 *
 * <p>The sheet is hand-maintained today, and its authoring document has adopted
 * every last id so that it can one day be generated instead. A generated sheet
 * is packed afresh on each export, which makes ids the only thing that survives
 * one; this checks that the ids that have to survive still resolve. The generation fittings name these
 * doodads directly (berthing kit, armory benches, boat-bay stores), and
 * {@link GenMappingRegistry#validateReferences()} throws on any mapping id it
 * cannot resolve, so a dropped id is a startup crash rather than a missing crate.
 */
class UrbanTilesetContractTest {

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

            // Not a formality: validation over empty pools would prove nothing. The district pools
            // have named colony-furnishings ids, not urban-tileset ones, since the furnishing pass.
            assertFalse(mapping.doodadPoolIds("MIXED").isEmpty(),
                    "the MIXED pool should resolve to real doodads");
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
