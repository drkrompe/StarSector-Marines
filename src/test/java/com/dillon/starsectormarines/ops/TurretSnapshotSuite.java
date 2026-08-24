package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.preview.HeadlessTurretCatalogPreviewRenderer;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Deterministic six-state strips for every data-authored turret mount. */
public final class TurretSnapshotSuite implements SnapshotSuite {

    @Override
    public String id() {
        return "turrets";
    }

    @Override
    public String label() {
        return "Turret catalog";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TurretCatalogRegistry registry = loadRegistry(context.modRoot());
        HeadlessTurretCatalogPreviewRenderer renderer =
                new HeadlessTurretCatalogPreviewRenderer(
                        context.modRoot(), context.starsectorCore());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (TurretMountDef mount : registry.mounts()) {
            artifacts.add(new SnapshotArtifact(fileStem(mount.id) + ".png",
                    renderer.render(mount).image()));
        }
        return List.copyOf(artifacts);
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

    private static String fileStem(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
