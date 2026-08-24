package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.preview.HeadlessTurretCatalogPreviewRenderer;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Command-line entry point for the shared headless turret preview pipeline. */
public final class HeadlessTurretPreviewCli {

    private HeadlessTurretPreviewCli() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        Path coreRoot = args.length > 1
                ? Path.of(args[1])
                : Path.of(System.getProperty("starsectorDir"), "starsector-core");
        Path outputDirectory = args.length > 2
                ? Path.of(args[2])
                : projectRoot.resolve("build/turret-previews/catalog");
        Path modRoot = projectRoot.resolve("mod");

        TurretCatalogRegistry registry = loadRegistry(modRoot);
        HeadlessTurretCatalogPreviewRenderer renderer =
                new HeadlessTurretCatalogPreviewRenderer(modRoot, coreRoot);
        List<Path> outputs = renderer.writeCatalog(registry, outputDirectory);
        System.out.println("Wrote " + outputs.size() + " turret previews to "
                + outputDirectory.toAbsolutePath());
    }

    private static TurretCatalogRegistry loadRegistry(Path modRoot) throws Exception {
        WeaponRegistry weapons = new WeaponRegistry();
        for (String path : WeaponRegistry.BUILTIN_CATALOGS) {
            weapons.ingest(new JSONObject(Files.readString(modRoot.resolve(path))));
        }
        TurretCatalogRegistry registry = new TurretCatalogRegistry();
        for (String path : TurretCatalogRegistry.BUILTIN_CATALOGS) {
            registry.ingest(new JSONObject(Files.readString(modRoot.resolve(path))), weapons);
        }
        return registry;
    }
}
