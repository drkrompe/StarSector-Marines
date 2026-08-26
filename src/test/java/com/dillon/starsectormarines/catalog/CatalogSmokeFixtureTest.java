package com.dillon.starsectormarines.catalog;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Keeps the live two-provider fixtures aligned with the public manifest contract. */
class CatalogSmokeFixtureTest {

    private static final Path ROOT = Path.of("src/liveTest/catalog-smoke");

    @Test
    void additiveProvidersDeclareDistinctIdsAndCollisionProviderRepeatsAlpha() throws Exception {
        Provider alpha = provider("provider-alpha");
        Provider beta = provider("provider-beta-additive");
        Provider collision = provider("provider-beta-collision");

        assertEquals("catalog_smoke_alpha", alpha.modId());
        assertEquals("catalog_smoke_beta", beta.modId());
        assertEquals("catalog_smoke_beta", collision.modId());
        assertEquals("starsector_marines", alpha.dependencyId());
        assertEquals("catalog_smoke_alpha", beta.dependencyId());
        assertEquals(beta.dependencyId(), collision.dependencyId());
        assertNotEquals(alpha.tileId(), beta.tileId());
        assertEquals(alpha.tileId(), collision.tileId());
        assertEquals("data/catalog-smoke/provider.tileset.json", alpha.catalogPath());
        assertEquals(alpha.catalogPath(), beta.catalogPath());
    }

    private static Provider provider(String directory) throws Exception {
        Path root = ROOT.resolve(directory);
        JSONObject modInfo = read(root.resolve("mod_info.json"));
        JSONObject manifest = read(root.resolve(
                "data/marines/starsector-marines.catalog.json"));
        String catalogPath = manifest.getJSONArray("tilesets").getString(0);
        JSONObject tileset = read(root.resolve(catalogPath));
        String dependencyId = modInfo.getJSONArray("dependencies")
                .getJSONObject(0).getString("id");
        return new Provider(modInfo.getString("id"), dependencyId, catalogPath,
                tileset.getJSONArray("tiles").getJSONObject(0).getString("id"));
    }

    private static JSONObject read(Path path) throws Exception {
        return new JSONObject(Files.readString(path));
    }

    private record Provider(String modId, String dependencyId,
                            String catalogPath, String tileId) {}
}
