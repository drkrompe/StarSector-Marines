package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation.Point;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Registered live/wreck poses and controlled chassis/destruction evidence; not mission balance. */
public final class ApcSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "apc"; }
    @Override public String label() { return "APC hull and independent turret registration"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        BattleSimulation matrix = scene();
        ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
        VehicleType[] types = {VehicleType.HEAVY_APC, VehicleType.FIRE_SUPPORT_APC};
        for (int row = 0; row < types.length; row++) {
            float y = row == 0 ? 23 : 17;
            for (int column = 0; column < 4; column++) {
                float x = 23 + column * 6;
                spawn(matrix, types[row], x, y, column * 90, column * 90 + row * 135);
                labels.label(x, y - 2, types[row].name() + " / hull " + column * 90,
                        ReviewStyle.NOTE);
            }
        }
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                context.modRoot(), 1920, 1080, 32, 20, 28);
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("headings.png", renderer.render(matrix,
                "APC silhouette review | four hull headings, independent turret bearings | 1920 x 1080, UI 100%",
                labels.build())));
        artifacts.add(new SnapshotArtifact("battle-scale.png", renderer.refocus(32, 20, 60).render(matrix,
                "APC silhouette review | battle-scale camera, 60 cells across | 1920 x 1080, UI 100%",
                labels.build())));

        BattleSimulation sweep = scene();
        long heavy = spawn(sweep, types[0], 27.5f, 20, 0, 0);
        long escort = spawn(sweep, types[1], 36.5f, 20, 225, 0);
        List<BufferedImage> frames = new ArrayList<>();
        renderer.refocus(32, 20, 18);
        for (int angle = 0; angle < 360; angle += 15) {
            sweep.convoy().turret(heavy).facingDeg = angle;
            sweep.convoy().turret(escort).facingDeg = angle;
            ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
            markMuzzle(sweep, heavy, marks);
            markMuzzle(sweep, escort, marks);
            frames.add(renderer.render(sweep,
                    "APC turret registration | posed bearing " + angle
                            + " degrees | crosses mark both ballistic muzzles | camera zoom only, UI 100%",
                    marks.build()));
        }
        artifacts.add(new SnapshotArtifact("muzzle-registration.png", frames.get(0)));
        artifacts.add(SnapshotArtifact.colorAnimation("turret-sweep.gif", frames, 125));
        for (long id : matrix.getConvoyVehicleIds()) matrix.applyDamage(id, 10000, 10000);
        artifacts.add(new SnapshotArtifact("wreck-headings.png", renderer.refocus(32, 20, 28).render(matrix,
                "APC wrecks / both chassis sizes, final independent bearings / particles omitted / UI 100%",
                labels.build())));
        matrix.close();
        sweep.close();
        campaignLifecycle(context, artifacts);
        return List.copyOf(artifacts);
    }

    private static void campaignLifecycle(SnapshotContext context, List<SnapshotArtifact> artifacts)
            throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        var profile = new TargetProfile(0, 0, 0, 0, "", Set.of(),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        var attempt = new FreightRunProgress("apc-art-thunder-run").previewAttempt();
        try (var sim = BattleSetup.createThunderRun(9301, manifest, profile, attempt, 0)) {
            long escort = 0;
            for (long id : sim.getConvoyVehicleIds()) {
                var type = sim.convoy().vehicleType(id);
                if (type.hasTurretWeapon()) sim.convoy().turret(id).facingDeg = sim.convoy().body(id).facingDegrees;
                if (type == VehicleType.HEAVY_APC && escort == 0) escort = id;
            }
            if (escort == 0) throw new IllegalStateException("Thunder Run has no APC escort");
            artifacts.add(new SnapshotArtifact("campaign-comparison.png",
                    new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 24, 76, 44).render(sim,
                            "THUNDER RUN / APCs beside tank, technical and freight / starting poses / UI 100%")));
            VehicleLifecycleSnapshots.collect(context, artifacts, sim, escort, "APC", 10);
        }
    }

    private static BattleSimulation scene() {
        NavigationGrid grid = new NavigationGrid(64, 40);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 64; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(64, 40));
    }

    private static long spawn(BattleSimulation sim, VehicleType type,
                              float x, float y, float hull, float turret) {
        long id = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, hull);
        sim.convoy().turret(id).facingDeg = turret;
        return id;
    }

    private static void markMuzzle(BattleSimulation sim, long id, ReviewAnnotations.Builder marks) {
        GroundBody body = sim.convoy().body(id);
        VehicleType type = sim.convoy().vehicleType(id);
        double radians = Math.toRadians(body.facingDegrees);
        float x = body.x + (float) (type.turretMountX * Math.cos(radians) - type.turretMountY * Math.sin(radians));
        float y = body.y + (float) (type.turretMountX * Math.sin(radians) + type.turretMountY * Math.cos(radians));
        for (int release = 0; release < 2; release++) {
            TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(x, y,
                    sim.convoy().turret(id).facingDeg, type.turretStructure().mount, release);
            // Thin crossed lines keep the tip visible; large arrowheads obscure it.
            // Annotation points address cell centers, while muzzles are world coordinates.
            float markX = muzzle.x() - .5f, markY = muzzle.y() - .5f;
            marks.polyline(List.of(new Point(markX - .06f, markY),
                    new Point(markX + .06f, markY)), "", ReviewStyle.NOTE);
            marks.polyline(List.of(new Point(markX, markY - .06f),
                    new Point(markX, markY + .06f)), "", ReviewStyle.NOTE);
        }
        marks.label(body.x, body.y - 2.4f, type.name(), ReviewStyle.NOTE);
    }
}
