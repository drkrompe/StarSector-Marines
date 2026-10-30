package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation.Point;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
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

/** Production convoy art poses plus a controlled autonomous-fire recoil review. */
public final class TankSnapshotSuite implements SnapshotSuite {
    private static final VehicleType TANK = VehicleType.BREAKTHROUGH_TANK;

    @Override public String id() { return "tank"; }
    @Override public String label() { return "Tank silhouette, turret registration and recoil"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        BattleSimulation matrix = scene();
        ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
        for (int row = 0; row < 2; row++) {
            float y = row == 0 ? 26 : 14;
            for (int column = 0; column < 4; column++) {
                float x = 17 + column * 10;
                spawn(matrix, x, y, column * 90, column * 90 + row * 135);
                labels.label(x, y - 4, "Hull " + column * 90 + " / turret "
                        + (column * 90 + row * 135) % 360, ReviewStyle.NOTE);
            }
        }
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                context.modRoot(), 1920, 1080, 32, 20, 46);
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("headings.png", renderer.render(matrix,
                "Tank silhouette | four hull headings, independent turret bearings | 1920 x 1080, UI 100%",
                labels.build())));
        artifacts.add(new SnapshotArtifact("battle-scale.png", renderer.refocus(32, 20, 60).render(matrix,
                "Tank silhouette | battle-scale camera, 60 cells across | 1920 x 1080, UI 100%",
                labels.build())));

        BattleSimulation sweep = scene();
        long north = spawn(sweep, 25, 20, 0, 0);
        long diagonal = spawn(sweep, 39, 20, 225, 0);
        List<BufferedImage> frames = new ArrayList<>();
        renderer.refocus(32, 20, 28);
        for (int angle = 0; angle < 360; angle += 15) {
            sweep.convoy().turret(north).facingDeg = angle;
            sweep.convoy().turret(diagonal).facingDeg = angle;
            ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
            markMuzzles(sweep, north, marks);
            markMuzzles(sweep, diagonal, marks);
            frames.add(renderer.render(sweep, "Tank registration | posed bearing " + angle
                    + " degrees | crosses mark the single ballistic muzzle | camera zoom only, UI 100%",
                    marks.build()));
        }
        artifacts.add(new SnapshotArtifact("muzzle-registration.png", frames.get(0)));
        artifacts.add(SnapshotArtifact.colorAnimation("turret-sweep.gif", frames, 125));

        BattleSimulation firing = scene();
        // This is an isolated gun-range fixture without a company's infantry roster.
        firing.setMissionCompletionEnabled(false);
        long tank = spawn(firing, 32, 20, 0, 90);
        long target = firing.convoy().spawn(TANK, Faction.DEFENDER, VehicleMission.deployed(10, 20));
        firing.convoy().body(target).teleport(10, 20, 0);
        firing.convoy().turret(target).ammo = 0;
        renderer.refocus(32, 20, 16).withImpactEffects();
        List<BufferedImage> recoil = new ArrayList<>();
        int rounds = 0;
        for (int tick = 0; tick < 120; tick++) {
            firing.advance(BattleSimulation.TICK_DT);
            rounds += (int) firing.getShotsThisFrame().stream().filter(shot -> shot.shooterId == tank).count();
            if (tick % 2 == 0) recoil.add(renderer.render(firing,
                    "Tank firing review | single cannon, existing two-round burst and recoil | 1920 x 1080, UI 100%"));
        }
        if (rounds < 2) throw new IllegalStateException("Cannon burst did not fire: " + rounds);
        artifacts.add(SnapshotArtifact.colorAnimation("firing-recoil.gif", recoil, 67));
        firing.close();
        sweep.close();
        matrix.close();
        campaignComparison(context, artifacts);
        return List.copyOf(artifacts);
    }

    /** Actual campaign factory and initial vehicle positions; only gun bearings are posed. */
    private static void campaignComparison(SnapshotContext context, List<SnapshotArtifact> artifacts)
            throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        var profile = new TargetProfile(0, 0, 0, 0, "", Set.of(),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        var attempt = new FreightRunProgress("tank-art-thunder-run").previewAttempt();
        try (var sim = BattleSetup.createThunderRun(9301, manifest, profile, attempt, 0)) {
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            for (long id : sim.getConvoyVehicleIds()) {
                var body = sim.convoy().body(id);
                var type = sim.convoy().vehicleType(id);
                if (type.hasTurretWeapon()) sim.convoy().turret(id).facingDeg = body.facingDegrees;
                if (body.x > 45) continue;
                String name = switch (type) {
                    case BREAKTHROUGH_TANK -> "TANK / SINGLE CANNON";
                    case HEAVY_APC -> "APC ESCORT";
                    case MILITIA_TECHNICAL -> "MILITIA TECHNICAL";
                    case CARGO_CARRIER -> "FREIGHT HAULER";
                    default -> type.name();
                };
                labels.label(body.x, body.y - 2.8f, name, ReviewStyle.NOTE);
            }
            var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 27, 76, 60);
            artifacts.add(new SnapshotArtifact("campaign-context.png", renderer.render(sim,
                    "THUNDER RUN / staging apron / seed 9301 / initial vehicle positions, guns posed forward / UI 100%")));
            artifacts.add(new SnapshotArtifact("campaign-comparison.png", renderer.refocus(24, 76, 44).render(sim,
                    "THUNDER RUN / tank, APCs, technical and freight / camera zoom only / 1920 x 1080, UI 100%",
                    labels.build())));
            VehicleLifecycleSnapshots.collect(context, artifacts, sim, sim.getThunderRun().heavyLead(), "TANK", 12);
        }
    }

    private static BattleSimulation scene() {
        NavigationGrid grid = new NavigationGrid(64, 40);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 64; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(64, 40));
    }

    private static long spawn(BattleSimulation sim, float x, float y, float hull, float turret) {
        long id = sim.convoy().spawn(TANK, Faction.MARINE, VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, hull);
        sim.convoy().turret(id).facingDeg = turret;
        return id;
    }

    private static void markMuzzles(BattleSimulation sim, long id, ReviewAnnotations.Builder marks) {
        var body = sim.convoy().body(id);
        // Tank bearing is registered at the chassis center; no body-relative mount offset.
        var muzzle = TurretMountGeometry.muzzle(body.x, body.y,
                sim.convoy().turret(id).facingDeg, TANK.turretStructure().mount, 0);
        // Polyline points address cell centers; physical muzzles are world positions.
        float x = muzzle.x() - .5f, y = muzzle.y() - .5f;
        marks.polyline(List.of(new Point(x - .08f, y), new Point(x + .08f, y)), "", ReviewStyle.NOTE);
        marks.polyline(List.of(new Point(x, y - .08f), new Point(x, y + .08f)), "", ReviewStyle.NOTE);
    }
}
