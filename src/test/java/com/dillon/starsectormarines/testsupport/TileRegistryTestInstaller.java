package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Auto-registered JUnit extension that installs the disk-loaded
 * {@link TileRegistry}, {@link GenMappingRegistry} and {@link WeaponRegistry}
 * instances before any test runs — mirroring what
 * {@code onApplicationLoad} does in-game. Without the tile registry, gen code
 * under test takes its {@code installed() == null} path: {@code NatureZoneFiller}
 * skips overlay scatter, which diverges the gen RNG stream (and therefore every
 * preview PNG) from production. Without the mapping registry, fillers that read
 * {@code GenMappingRegistry.doodadPool(theme)} (moddable-tilesets Phase 2) fail.
 * Without the weapon registry, every {@code MarineWeapon} stat read throws —
 * that one is deliberately fail-loud rather than degrading (moddable-weapons W1).
 *
 * <p>Registered globally via {@code META-INF/services/org.junit.jupiter.api.extension.Extension}
 * + {@code junit.jupiter.extensions.autodetection.enabled=true} in
 * {@code junit-platform.properties}. Idempotent — each registry loads only once.
 */
public final class TileRegistryTestInstaller implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (TileRegistry.installed() == null) {
            TileRegistry reg = new TileRegistry();
            for (String path : TileRegistry.BUILTIN_TILESETS) {
                reg.ingestSheet(new JSONObject(Files.readString(Paths.get("mod", path))));
            }
            reg.validateReferences();
            TileRegistry.install(reg);
        }
        if (GenMappingRegistry.installed() == null) {
            GenMappingRegistry mapping = new GenMappingRegistry();
            for (String path : GenMappingRegistry.BUILTIN_MAPPINGS) {
                mapping.ingest(new JSONObject(Files.readString(Paths.get("mod", path.split("/")))));
            }
            GenMappingRegistry.install(mapping);
        }
        if (WeaponRegistry.installed() == null) {
            WeaponRegistry weapons = new WeaponRegistry();
            for (String path : WeaponRegistry.BUILTIN_CATALOGS) {
                weapons.ingest(new JSONObject(Files.readString(Paths.get("mod", path))));
            }
            WeaponRegistry.install(weapons);
        }
    }
}
