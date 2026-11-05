package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
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

/**
 * The Onager, the heavy repulsor tank (hegemony-armor-line.md, slice 4): owner-facing look shots of the hull and the
 * shared head over its gun beside the medium tank and the APC, a turning drive on its skirt, its wrecks, and its weight
 * crushing an ordinary wall; then production convoy art poses and a controlled autonomous-fire recoil review.
 */
public final class TankSnapshotSuite implements SnapshotSuite {
    private static final VehicleType TANK = VehicleType.BREAKTHROUGH_TANK;

    @Override public String id() { return "tank"; }
    @Override public String label() { return "Onager hull, head, drive, wreck and wall crush; registration and recoil"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        List<SnapshotArtifact> looks = new ArrayList<>(LookReview.record(context.modRoot(), lineup()));
        looks.addAll(LookReview.record(context.modRoot(), turning()));
        looks.addAll(LookReview.record(context.modRoot(), wrecks()));
        looks.addAll(LookReview.record(context.modRoot(), crush()));
        if (Boolean.getBoolean("snapshot.lookOnly")) return List.copyOf(looks);
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
        List<SnapshotArtifact> artifacts = new ArrayList<>(looks);
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
                    case BREAKTHROUGH_TANK -> "ONAGER / HC-20";
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
        // The ring sits a little ahead of the hull's centre; the muzzle is the fitted gun's own.
        double radians = Math.toRadians(body.facingDegrees);
        float ringX = body.x + (float) (TANK.turretMountX * Math.cos(radians) - TANK.turretMountY * Math.sin(radians));
        float ringY = body.y + (float) (TANK.turretMountX * Math.sin(radians) + TANK.turretMountY * Math.cos(radians));
        var muzzle = TurretMountGeometry.muzzle(ringX, ringY,
                sim.convoy().turret(id).facingDeg, sim.convoy().turretStructure(id).mount, 0);
        // Polyline points address cell centers; physical muzzles are world positions.
        float x = muzzle.x() - .5f, y = muzzle.y() - .5f;
        marks.polyline(List.of(new Point(x - .08f, y), new Point(x + .08f, y)), "", ReviewStyle.NOTE);
        marks.polyline(List.of(new Point(x, y - .08f), new Point(x, y + .08f)), "", ReviewStyle.NOTE);
    }

    /**
     * Four Onagers at the four hull headings, then the medium tank and the APC beside them for size, every turret
     * sweeping a full turn off its hull's axis: at play zoom, along the line and close on one Onager.
     */
    private static LookReview.Shot lineup() {
        return LookReview.Shot.of("onager", 34, 18, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            List<long[]> hulls = new ArrayList<>();
            for (int column = 0; column < 4; column++) {
                float[] at = placement.openNear(5f + 8f * column, 5f);
                hulls.add(placed(placement, sim, TANK, MechWeaponComponent.HC_20, at, 90f * column, "onager" + column));
            }
            hulls.add(placed(placement, sim, VehicleType.MEDIUM_TANK, MechWeaponComponent.HC_10,
                    placement.openNear(9f, 13f), 90f, "medium"));
            hulls.add(placed(placement, sim, VehicleType.HEAVY_APC, MechWeaponComponent.LIGHT_MACHINE_GUN,
                    placement.openNear(23f, 13f), 90f, "apc"));
            int[] tick = {0};
            placement.beforeTick(s -> {
                float sweep = 60f + 1.5f * tick[0]++;
                for (long[] hull : hulls) s.convoy().turret(hull[0]).facingDeg = hull[1] + sweep;
            });
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "onager0", "onager1", "onager2", "onager3", "medium", "apc"),
                LookReview.View.follow("line", placement, 32f, "onager0", "onager3", "medium", "apc"),
                LookReview.View.follow("close", placement, 9f, "onager1")), 240).withStills(0, 10, 20, 30);
    }

    /**
     * One Onager ordered to a point behind it and off to one side: it swings round on its skirt and drives, coasting
     * and drifting as eighty tonnes on a repulsor do. The turret keeps its bearing on the hull's nose.
     */
    private static LookReview.Shot turning() {
        return LookReview.Shot.of("onager-turning", 30, 18, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            long[] hull = placed(placement, sim, TANK, MechWeaponComponent.HC_20, placement.openNear(20f, 9f), 0f,
                    "onager");
            float[] goal = placement.openNear(6f, 4f);
            sim.getVehicleMoveOrderService().requestMove(hull[0], (int) goal[0], (int) goal[1]);
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "onager"),
                LookReview.View.follow("close", placement, 12f, "onager")), 300).withStills(0, 12, 24, 40);
    }

    /** Three wrecks at different hull headings, each head knocked off its bearing: play zoom and close. */
    private static LookReview.Shot wrecks() {
        return LookReview.Shot.of("onager-wrecks", 28, 10, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            float[] bearings = {35f, 150f, 250f};
            for (int i = 0; i < bearings.length; i++) {
                long[] hull = placed(placement, sim, TANK, MechWeaponComponent.HC_20,
                        placement.openNear(5f + 9f * i, 5f), 90f * i, "wreck" + i);
                sim.convoy().turret(hull[0]).facingDeg = 90f * i + bearings[i];
                sim.applyDamage(hull[0], 10000, 10000);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "wreck0", "wreck1", "wreck2"),
                LookReview.View.follow("close", placement, 9f, "wreck1")), 60).withStills(0, 8);
    }

    /** The wall row's place in the crush shot's window, and its structure: an ordinary city wall. */
    private static final int CRUSH_WALL_Y = 9;
    private static final int CRUSH_WALL_X0 = 2, CRUSH_WALL_X1 = 25;

    /**
     * An ordinary wall row (100 structure) laid across the window, the Onager north of it ordered to a point south:
     * its move order is proved over the ground its weight opens, and it drives through the wall, leaving rubble.
     */
    private static LookReview.Shot crush() {
        return LookReview.Shot.of("onager-crush", 28, 20, placement -> {
            layWall(placement);
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            long[] hull = placed(placement, sim, TANK, MechWeaponComponent.HC_20, placement.openNear(14f, 16f), 180f,
                    "onager");
            sim.convoy().turret(hull[0]).facingDeg = 180f;
            float[] goal = placement.openNear(14f, 2f);
            sim.getVehicleMoveOrderService().requestMove(hull[0], (int) goal[0], (int) goal[1]);
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "onager"),
                LookReview.View.follow("close", placement, 14f, "onager")), 300).withStills(0, 10, 16, 24, 40);
    }

    /** Clears a strip of the window to bare ground and stands an ordinary perimeter-faced wall row on it. */
    private static void layWall(LookReview.Placement placement) {
        NavigationGrid grid = placement.map().grid;
        CellTopology topology = placement.map().topology;
        LookGround.Site site = placement.site();
        int surface = topology.surfaceIndex(InstallationWalls.PERIMETER);
        int x0 = site.x0() + CRUSH_WALL_X0 - 1, x1 = site.x0() + CRUSH_WALL_X1 + 1;
        int y0 = site.y0() + CRUSH_WALL_Y - 2, y1 = site.y0() + CRUSH_WALL_Y + 2;
        placement.map().doodads.removeIf(d -> d.cellX >= x0 && d.cellX <= x1 && d.cellY >= y0 && d.cellY <= y1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID, false);
                grid.setTag(x, y, NavigationGrid.CellTag.CRUSHABLE, false);
                grid.setWalkable(x, y, true);
                topology.setFixture(x, y, false);
                topology.setNatureOverlayIndex(x, y, -1);
            }
        }
        int y = site.y0() + CRUSH_WALL_Y;
        for (int x = site.x0() + CRUSH_WALL_X0; x <= site.x0() + CRUSH_WALL_X1; x++) {
            grid.setWalkable(x, y, false);
            grid.setSeeThrough(x, y, false);
            grid.setWallHp(x, y, WallCrush.ULTRA_HEAVY_CEILING);
            topology.setWall(x, y, true);
            topology.setSurface(x, y, surface);
            topology.setGroundKind(x, y, CellTopology.GroundKind.STRIPED);
        }
        for (int cy = y - 1; cy <= y + 1; cy++) {
            for (int x = x0; x <= x1; x++) {
                if (topology.isWall(x, cy)) {
                    int mask = 0;
                    if (!topology.isWall(x, cy + 1)) mask |= CellTopology.WALL_DIR_N;
                    if (!topology.isWall(x, cy - 1)) mask |= CellTopology.WALL_DIR_S;
                    if (!topology.isWall(x + 1, cy)) mask |= CellTopology.WALL_DIR_E;
                    if (!topology.isWall(x - 1, cy)) mask |= CellTopology.WALL_DIR_W;
                    topology.setWallDirMask(x, cy, mask);
                }
                grid.recomputeCoverAt(x, cy);
            }
        }
    }

    private static long[] placed(LookReview.Placement placement, BattleSimulation sim, VehicleType type,
                                 MechWeaponComponent gun, float[] at, float heading, String name) {
        long hull = sim.convoy().spawn(type, VehiclePlating.FACTORY, VehicleEngine.STOCK, gun, Faction.MARINE,
                VehicleMission.deployed(at[0], at[1]));
        sim.convoy().body(hull).teleport(at[0], at[1], heading);
        placement.subject(name, hull);
        return new long[] {hull, (long) heading};
    }
}
