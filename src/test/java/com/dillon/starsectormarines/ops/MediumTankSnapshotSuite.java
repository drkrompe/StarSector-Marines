package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleControlSystem;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation.Point;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The Hegemony medium tank (hegemony-armor-line.md, slice 5): its hull and the shared head over its gun beside the
 * Onager and the APC, the guns its two-row card takes, its wrecks, and its tracks driven. Art poses, not balance.
 */
public final class MediumTankSnapshotSuite implements SnapshotSuite {
    private static final VehicleType TANK = VehicleType.MEDIUM_TANK;

    @Override public String id() { return "medium-tank"; }
    @Override public String label() { return "Medium tank hull, head, wreck and tracks"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        List<SnapshotArtifact> looks = new ArrayList<>(LookReview.record(context.modRoot(), lineup()));
        looks.addAll(LookReview.record(context.modRoot(), guns()));
        looks.addAll(LookReview.record(context.modRoot(), wrecks()));
        if (Boolean.getBoolean("snapshot.lookOnly")) return List.copyOf(looks);
        List<SnapshotArtifact> artifacts = new ArrayList<>(looks);

        BattleSimulation matrix = scene();
        ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
        for (int column = 0; column < 4; column++) {
            float x = 22 + column * 7;
            spawn(matrix, TANK, x, 20, column * 90, column * 90 + 45);
            labels.label(x, 17, "hull " + column * 90, ReviewStyle.NOTE);
        }
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 32, 20, 30);
        artifacts.add(new SnapshotArtifact("headings.png", renderer.render(matrix,
                "MEDIUM TANK | four hull headings, HC-10 under the Hegemony head | 1920 x 1080, UI 100%",
                labels.build())));

        BattleSimulation sweep = scene();
        long tank = spawn(sweep, TANK, 32, 20, 0, 0);
        List<BufferedImage> frames = new ArrayList<>();
        renderer.refocus(32, 20, 10);
        for (int angle = 0; angle < 360; angle += 15) {
            sweep.convoy().turret(tank).facingDeg = angle;
            ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
            markMuzzle(sweep, tank, marks);
            frames.add(renderer.render(sweep, "MEDIUM TANK turret registration | posed bearing " + angle
                    + " degrees | the cross marks the fitted gun's muzzle | UI 100%", marks.build()));
        }
        artifacts.add(new SnapshotArtifact("muzzle-registration.png", frames.get(0)));
        artifacts.add(SnapshotArtifact.colorAnimation("turret-sweep.gif", frames, 125));

        for (long id : matrix.getConvoyVehicleIds()) matrix.applyDamage(id, 10000, 10000);
        artifacts.add(new SnapshotArtifact("wreck-headings.png", renderer.refocus(32, 20, 30).render(matrix,
                "MEDIUM TANK wrecks | four hull headings, final bearings | particles omitted | UI 100%",
                labels.build())));
        matrix.close();
        sweep.close();
        tracksDrive(context, artifacts);
        return List.copyOf(artifacts);
    }

    /** Controlled forward, turn, brake and reverse through the production controller; the tread UVs follow travel. */
    private static void tracksDrive(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        BattleSimulation sim = scene();
        sim.setMissionCompletionEnabled(false);
        long tank = spawn(sim, TANK, 26, 20, 90, 90);
        sim.convoy().turret(tank).ammo = 0;
        GroundBody body = sim.convoy().body(tank);
        VehicleControlSystem control = new VehicleControlSystem(sim.convoy(),
                new NavigationService(sim.getGrid(), sim.getTopology()));
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                body.x, body.y, 9);
        List<BufferedImage> motion = new ArrayList<>();
        for (int tick = 0; tick < 180; tick++) {
            // Low speed keeps individual links readable at the GIF's 10 fps.
            float throttle = tick < 65 ? .2f : tick < 100 ? 0 : tick < 150 ? -.35f : 0;
            float steer = tick >= 30 && tick < 65 ? .3f : 0;
            control.tickManual(tank, BattleSimulation.TICK_DT, throttle, steer);
            sim.convoy().turret(tank).facingDeg = body.facingDegrees;
            sim.advance(BattleSimulation.TICK_DT);
            if (tick % 3 == 0) motion.add(renderer.refocus(body.x, body.y, 9).render(sim,
                    "MEDIUM TANK TRACKS / controlled drive: " + (tick < 65 ? "forward + turn" : tick < 100
                            ? "brake / stop" : tick < 150 ? "reverse" : "brake / stop")
                            + " / chassis travel drives the tread bands / camera follows / UI 100%"));
        }
        artifacts.add(SnapshotArtifact.colorAnimation("tracks-drive.gif", motion, 100));
        sim.close();
    }

    /**
     * Four medium tanks at the four hull headings, then the Onager and the APC beside them for size, every turret
     * sweeping a full turn off its hull's axis: at play zoom, and close on one tank.
     */
    private static LookReview.Shot lineup() {
        return LookReview.Shot.of("medium-tank", 34, 18, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            List<long[]> hulls = new ArrayList<>();
            for (int column = 0; column < 4; column++) {
                float[] at = placement.openNear(5f + 7f * column, 5f);
                hulls.add(placed(placement, sim, TANK, MechWeaponComponent.HC_10, at, 90f * column, "tank" + column));
            }
            hulls.add(placed(placement, sim, VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.HC_20,
                    placement.openNear(9f, 12f), 90f, "onager"));
            hulls.add(placed(placement, sim, VehicleType.HEAVY_APC, MechWeaponComponent.LIGHT_MACHINE_GUN,
                    placement.openNear(23f, 12f), 90f, "apc"));
            int[] tick = {0};
            placement.beforeTick(s -> {
                float sweep = 60f + 1.5f * tick[0]++;
                for (long[] hull : hulls) s.convoy().turret(hull[0]).facingDeg = hull[1] + sweep;
            });
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "tank0", "tank1", "tank2", "tank3", "onager", "apc"),
                LookReview.View.follow("line", placement, 30f, "tank0", "tank3", "onager", "apc"),
                LookReview.View.follow("close", placement, 8f, "tank1")), 240).withStills(0, 10, 20, 30);
    }

    /** The guns a two-row ballistic card takes, each under the head at one bearing: play zoom and close. */
    private static LookReview.Shot guns() {
        MechWeaponComponent[] guns = {MechWeaponComponent.HC_10, MechWeaponComponent.AC_6,
                MechWeaponComponent.DC_5, MechWeaponComponent.CHAINGUN};
        return LookReview.Shot.of("medium-tank-guns", 30, 10, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            for (int i = 0; i < guns.length; i++) {
                long[] hull = placed(placement, sim, TANK, guns[i], placement.openNear(4f + 7f * i, 5f), 90f,
                        "gun" + i);
                sim.convoy().turret(hull[0]).facingDeg = 60f;
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "gun0", "gun1", "gun2", "gun3"),
                LookReview.View.follow("row", placement, 26f, "gun0", "gun1", "gun2", "gun3")), 30).withStills(0);
    }

    /** Three wrecks at different hull headings, each head knocked off its bearing: play zoom and close. */
    private static LookReview.Shot wrecks() {
        return LookReview.Shot.of("medium-tank-wrecks", 26, 10, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            float[] bearings = {35f, 150f, 250f};
            for (int i = 0; i < bearings.length; i++) {
                long[] hull = placed(placement, sim, TANK, MechWeaponComponent.HC_10,
                        placement.openNear(5f + 8f * i, 5f), 90f * i, "wreck" + i);
                sim.convoy().turret(hull[0]).facingDeg = 90f * i + bearings[i];
                sim.applyDamage(hull[0], 10000, 10000);
            }
            return sim;
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "wreck0", "wreck1", "wreck2"),
                LookReview.View.follow("close", placement, 8f, "wreck1")), 60).withStills(0, 8);
    }

    private static long[] placed(LookReview.Placement placement, BattleSimulation sim, VehicleType type,
                                 MechWeaponComponent gun, float[] at, float heading, String name) {
        long hull = sim.convoy().spawn(type, VehiclePlating.FACTORY, VehicleEngine.STOCK, gun, Faction.MARINE,
                VehicleMission.deployed(at[0], at[1]));
        sim.convoy().body(hull).teleport(at[0], at[1], heading);
        placement.subject(name, hull);
        return new long[] {hull, (long) heading};
    }

    private static BattleSimulation scene() {
        NavigationGrid grid = new NavigationGrid(64, 40);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 64; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(64, 40));
    }

    private static long spawn(BattleSimulation sim, VehicleType type, float x, float y, float hull, float turret) {
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
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(x, y,
                sim.convoy().turret(id).facingDeg, sim.convoy().turretStructure(id).mount, 0);
        float markX = muzzle.x() - .5f, markY = muzzle.y() - .5f;
        marks.polyline(List.of(new Point(markX - .06f, markY), new Point(markX + .06f, markY)), "", ReviewStyle.NOTE);
        marks.polyline(List.of(new Point(markX, markY - .06f), new Point(markX, markY + .06f)), "", ReviewStyle.NOTE);
    }
}
