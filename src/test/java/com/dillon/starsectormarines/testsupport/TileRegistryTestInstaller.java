package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Auto-registered JUnit extension that installs a disk-loaded {@link TileRegistry}
 * as {@link TileRegistry#installed()} before any test runs — mirroring what
 * {@code onApplicationLoad} does in-game. Without it, gen code under test takes
 * its {@code installed() == null} path: {@code NatureZoneFiller} skips overlay
 * scatter, which diverges the gen RNG stream (and therefore every preview PNG)
 * from production. Installing it makes tests exercise the real registry-backed
 * path.
 *
 * <p>Registered globally via {@code META-INF/services/org.junit.jupiter.api.extension.Extension}
 * + {@code junit.jupiter.extensions.autodetection.enabled=true} in
 * {@code junit-platform.properties}. Idempotent — only the first call loads.
 */
public final class TileRegistryTestInstaller implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (TileRegistry.installed() != null) return;
        TileRegistry reg = new TileRegistry();
        for (String name : new String[]{"nature-tiles.tileset.json", "urban-tileset-3.tileset.json",
                "urban-tileset.tileset.json", "urban-tileset-2.tileset.json"}) {
            reg.ingestSheet(new JSONObject(Files.readString(Paths.get("mod/data/tilesets", name))));
        }
        reg.validateReferences();
        TileRegistry.install(reg);
    }
}
