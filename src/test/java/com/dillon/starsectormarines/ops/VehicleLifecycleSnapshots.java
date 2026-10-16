package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.VehicleControlSystem;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Shared controlled movement and destruction review on the Thunder Run staging apron. */
final class VehicleLifecycleSnapshots {
    /** Controlled inputs through the normal chassis/sweep, then one real lethal damage event. */
    static void collect(SnapshotContext context, List<SnapshotArtifact> artifacts,
                                  BattleSimulation sim, long vehicle, String label, float zoom) throws Exception {
        sim.setMissionCompletionEnabled(false);
        var body = sim.convoy().body(vehicle);
        for (long id : sim.getConvoyVehicleIds()) {
            if (sim.convoy().vehicleType(id).hasTurretWeapon()) sim.convoy().turret(id).ammo = 0;
        }
        var control = new VehicleControlSystem(sim.convoy(),
                new NavigationService(sim.getGrid(), sim.getTopology()));
        var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                body.x, body.y, zoom);
        List<BufferedImage> motion = new ArrayList<>();
        for (int tick = 0; tick < 180; tick++) {
            // Low speed keeps individual tread links readable at the GIF's 10 fps.
            float throttle = tick < 65 ? .2f : tick < 100 ? 0 : tick < 150 ? -.35f : 0;
            float steer = tick >= 30 && tick < 65 ? .3f : 0;
            control.tickManual(vehicle, BattleSimulation.TICK_DT, throttle, steer);
            sim.convoy().turret(vehicle).facingDeg = body.facingDegrees;
            sim.advance(BattleSimulation.TICK_DT);
            if (tick % 3 == 0) motion.add(renderer.refocus(body.x, body.y, zoom).render(sim,
                    label + " TREADS / controlled drive: " + (tick < 65 ? "forward + turn" : tick < 100
                            ? "brake / stop" : tick < 150 ? "reverse" : "brake / stop")
                            + " / actual chassis travel drives tread UVs / camera follows / UI 100%"));
        }
        artifacts.add(SnapshotArtifact.colorAnimation("tracks-drive.gif", motion, 100));
        // Stop through the same controller before the damage demonstration.
        for (int tick = 0; tick < 30; tick++) {
            control.tickManual(vehicle, BattleSimulation.TICK_DT, 0, 0);
            sim.advance(BattleSimulation.TICK_DT);
        }
        var impacts = new ReviewImpactFx(context.modRoot());
        List<BufferedImage> destruction = new ArrayList<>();
        renderer.refocus(body.x, body.y, zoom * 1.5f).withImpactEffects();
        for (int tick = 0; tick < 150; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            if (tick == 15) sim.applyDamage(vehicle, 10000, 10000);
            impacts.feed(sim);
            if (tick % 3 == 0) destruction.add(renderer.render(sim,
                    label + " / THUNDER RUN / controlled lethal hit / real destruction blast, fire and smoke / UI 100%",
                    ReviewAnnotations.NONE, impacts));
        }
        artifacts.add(SnapshotArtifact.colorAnimation("destruction.gif", destruction, 100));
        // Show the persistent art clearly, without the particle overlay concealing its damage.
        artifacts.add(new SnapshotArtifact("campaign-wreck.png",
                new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 29, 76, 44).render(sim,
                        label + " / THUNDER RUN / wreck beside convoy / particles omitted for art review / UI 100%")));
    }

}
