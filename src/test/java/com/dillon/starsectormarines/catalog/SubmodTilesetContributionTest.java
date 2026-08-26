package com.dillon.starsectormarines.catalog;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Acceptance fixture for an external provider's additive tile and mapping data. */
class SubmodTilesetContributionTest {

    private static final CatalogSource OC = new CatalogSource(
            "example_oc", "data/example_oc/colony.tileset.json");
    private static final CatalogSource OC_MAPPING = new CatalogSource(
            "example_oc", "data/example_oc/colony.mapping.json");

    @Test
    void contributedTilesAndDoodadPoolsMergeWithProviderProvenance() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry tiles = loadCoreTiles();
        tiles.ingestSheet(externalTileset(), OC);
        tiles.validateReferences();
        TileRegistry.install(tiles);
        try {
            GenMappingRegistry mappings = loadCoreMapping();
            mappings.ingest(new JSONObject("""
                    {
                      "doodadPools": {
                        "EXAMPLE_OC_COLONY": ["example_oc.glow-crate"]
                      }
                    }
                    """), OC_MAPPING);
            mappings.validateReferences();

            assertSame(OC, tiles.sourceOf("example_oc.glow-crate"));
            assertSame(OC_MAPPING, mappings.sourceOfDoodadPool("EXAMPLE_OC_COLONY"));
            DoodadDef doodad = mappings.doodadPool("EXAMPLE_OC_COLONY").get(0);
            assertEquals("graphics/example_oc/colony-tiles.png", doodad.sheetPath);
            assertEquals("example_oc.glow-crate", doodad.id);
        } finally {
            TileRegistry.install(previous);
        }
    }

    @Test
    void tileIdCollisionNamesBothProviderResources() throws Exception {
        TileRegistry tiles = new TileRegistry();
        CatalogSource first = new CatalogSource("first_mod", "data/first.tileset.json");
        CatalogSource second = new CatalogSource("second_mod", "data/second.tileset.json");
        tiles.ingestSheet(externalTileset(), first);

        IllegalStateException collision = assertThrows(IllegalStateException.class,
                () -> tiles.ingestSheet(externalTileset(), second));

        assertTrue(collision.getMessage().contains(first.describe()));
        assertTrue(collision.getMessage().contains(second.describe()));
        assertTrue(collision.getMessage().contains("example_oc.ground"));
    }

    @Test
    void mappingCollisionNamesBothProviderResources() throws Exception {
        GenMappingRegistry mappings = new GenMappingRegistry();
        CatalogSource first = new CatalogSource("first_mod", "data/first.mapping.json");
        CatalogSource second = new CatalogSource("second_mod", "data/second.mapping.json");
        JSONObject mapping = new JSONObject("""
                {"doodadPools": {"SHARED_POOL": ["example_oc.glow-crate"]}}
                """);
        mappings.ingest(mapping, first);

        JSONException collision = assertThrows(JSONException.class,
                () -> mappings.ingest(mapping, second));

        assertTrue(collision.getMessage().contains(first.describe()));
        assertTrue(collision.getMessage().contains(second.describe()));
        assertTrue(collision.getMessage().contains("SHARED_POOL"));
    }

    @Test
    void contributedMappingReferenceMustResolveAgainstCompleteTileCatalog() throws Exception {
        TileRegistry previous = TileRegistry.installed();
        TileRegistry tiles = loadCoreTiles();
        TileRegistry.install(tiles);
        try {
            GenMappingRegistry mappings = loadCoreMapping();
            mappings.ingest(new JSONObject("""
                    {"doodadPools": {"EXAMPLE_OC_BAD": ["example_oc.missing"]}}
                    """), OC_MAPPING);

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class, mappings::validateReferences);
            assertTrue(failure.getMessage().contains(OC_MAPPING.describe()));
            assertTrue(failure.getMessage().contains("example_oc.missing"));
        } finally {
            TileRegistry.install(previous);
        }
    }

    private static TileRegistry loadCoreTiles() throws Exception {
        TileRegistry tiles = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            tiles.ingestSheet(read(path), new CatalogSource("starsector_marines", path));
        }
        tiles.validateReferences();
        return tiles;
    }

    private static GenMappingRegistry loadCoreMapping() throws Exception {
        GenMappingRegistry mappings = new GenMappingRegistry();
        for (String path : GenMappingRegistry.BUILTIN_MAPPINGS) {
            mappings.ingest(read(path), new CatalogSource("starsector_marines", path));
        }
        return mappings;
    }

    private static JSONObject read(String path) throws Exception {
        return new JSONObject(Files.readString(Path.of("mod").resolve(path)));
    }

    private static JSONObject externalTileset() throws JSONException {
        return new JSONObject("""
                {
                  "sheet": "graphics/example_oc/colony-tiles.png",
                  "tiles": [
                    {"id": "example_oc.ground", "frame": 0, "layer": "ground"}
                  ],
                  "doodads": [
                    {"id": "example_oc.glow-crate", "col": 1, "row": 0,
                     "cover": "light"}
                  ]
                }
                """);
    }
}
