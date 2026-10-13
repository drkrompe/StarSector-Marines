package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.preview.HeadlessTurretCatalogPreviewRenderer;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
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
        artifacts.add(new SnapshotArtifact("multi-cell-structure.png", multiCellStill(context)));
        return List.copyOf(artifacts);
    }

    /**
     * A 3x3 structure in a production battle frame, with a marine's route round
     * it: the footprint pad drawn at its full extent, not the one cell it is
     * anchored on.
     */
    private static BufferedImage multiCellStill(SnapshotContext context) throws Exception {
        TurretCatalogRegistry registry = loadRegistry(context.modRoot());
        TurretCatalogRegistry previous = TurretCatalogRegistry.installed();
        TurretCatalogRegistry.install(registry);
        try {
            NavigationGrid grid = new NavigationGrid(24, 16);
            CellTopology topology = new CellTopology(24, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
            }
            BattleSimulation sim = new BattleSimulation(grid, topology);
            sim.spawn(MapTurret.create("battery", Faction.DEFENDER,
                    "structure.fortress-battery-placeholder", 12, 8));
            sim.spawn(MapTurret.create("vulcan", Faction.DEFENDER,
                    TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 5, 8));
            long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 2, 8));
            sim.setPath(marine, GridPathfinder.findPath(grid, 2, 8, 21, 8));
            for (int i = 0; i < 70; i++) sim.advance(BattleSimulation.TICK_DT);
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 720, 480);
            return renderer.render(sim, "3x3 structure beside a 1x1: the footprint pad is drawn at its full extent");
        } finally {
            TurretCatalogRegistry.install(previous);
        }
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
