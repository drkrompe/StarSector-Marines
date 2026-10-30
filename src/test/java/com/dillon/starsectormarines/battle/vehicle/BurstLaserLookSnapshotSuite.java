package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A burst laser's click frame by frame, through the look preset at 1920x1080: a Bulwark with a medium burst laser on
 * each arm, and a laser tank with its heavy burst head, each firing on a parked enemy APC. Every tick from the first
 * pulse of the subject's first burst to a few ticks past its last is written as {@code look/<shot>-tNN.png}, so the
 * pulses can be read one by one (`mechs-nouns.md`, burst laser).
 */
public final class BurstLaserLookSnapshotSuite implements SnapshotSuite {

    private static final int MAX_TICKS = 600;
    private static final int TAIL_TICKS = 4;

    @Override public String id() { return "burst-laser-look"; }
    @Override public String label() { return "A burst laser's pulses, tick by tick, on a mech arm and a tank turret"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.addAll(burst(context.modRoot(), "bulwark-medium-burst", BurstLaserLookSnapshotSuite::bulwark));
        artifacts.addAll(burst(context.modRoot(), "laser-tank-burst", BurstLaserLookSnapshotSuite::laserTank));
        return List.copyOf(artifacts);
    }

    /** A staged subject: the simulation, the subject's id, and how many pulses of its current burst are still to go. */
    private record Stage(BattleSimulation sim, long subject, ToIntFunction<BattleSimulation> pending, int perTrigger) { }

    private static List<SnapshotArtifact> burst(Path modRoot, String name, Function<LookReview.Placement, Stage> stage)
            throws Exception {
        LookGround ground = LookGround.verdant();
        MapResult map = ground.generate();
        LookGround.Site site = LookGround.openSite(map, 40, 20);
        LookReview.Placement placement = new LookReview.Placement(ground, map, site);
        Stage s = stage.apply(placement);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = s.sim()) {
            ReviewImpactFx fx = new ReviewImpactFx(modRoot);
            BattleReviewFrameRenderer renderer = null;
            int started = -1;
            for (int tick = 1; tick <= MAX_TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                fx.feed(sim);
                int pending = s.pending().applyAsInt(sim);
                if (started < 0 && pending == s.perTrigger() - 1) started = tick;
                if (started < 0) continue;
                float[] at = where(sim, s.subject());
                float[] to = where(sim, placement.subjects().get("target"));
                if (renderer == null) {
                    renderer = BattleReviewFrameRenderer.look(modRoot, LookReview.WIDTH, LookReview.HEIGHT,
                            (at[0] + to[0]) / 2f, (at[1] + to[1]) / 2f, 28f);
                }                out.add(new SnapshotArtifact(String.format("look/%s-t%02d.png", name, tick - started),
                        renderer.render(sim, "", ReviewAnnotations.NONE, fx)));
                if (pending == 0 && tick - started > TAIL_TICKS + 2) break;
            }
            System.out.printf("    [look/%s] first burst at tick %d, %d frames%n", name, started, out.size());
        }
        return out;
    }

    private static float[] where(BattleSimulation sim, long id) {
        if (sim.convoy().isVehicle(id)) {
            GroundBody body = sim.convoy().body(id);
            return new float[] {body.x, body.y};
        }
        return new float[] {sim.world().renderX(id), sim.world().renderY(id)};
    }

    /** An enemy APC parked 18 cells east of the subject: a target that outlasts a burst, so every pulse is seen. */
    private static void target(LookReview.Placement placement, BattleSimulation sim) {
        float[] at = placement.openNear(28.5f, 10.5f);
        long apc = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, VehicleMission.deployed(at[0], at[1]));
        sim.convoy().body(apc).teleport(at[0], at[1], 180f);
        placement.subject("target", apc);
    }

    private static Stage bulwark(LookReview.Placement placement) {
        SceneBuilder builder = placement.holdouts(placement.builder());
        float[] at = placement.openNear(10.5f, 10.5f);
        int holdX = (int) at[0];
        int holdY = (int) at[1];
        MechDeploymentSpec spec = new MechDeploymentSpec(MechVariant.BULWARK, MechRole.BALANCED,
                MissileReplenisherComponent.STANDARD, MechWeaponComponent.PL_2, null, null);
        builder.squad("mech").faction(Faction.MARINE).size(1)
                .at(holdX - placement.site().x0(), holdY - placement.site().y0()).mech(spec)
                .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY)).done();
        SceneWorld world = builder.build();
        long mech = world.members("mech")[0];
        target(placement, world.sim());
        return new Stage(world.sim(), mech, sim -> {
            if (!sim.world().isAlive(mech)) return 0;
            int most = 0;
            for (MechWeaponMount mount : sim.world().mechLoadout(mech).mounts()) {
                if (mount != null) most = Math.max(most, mount.burstRemaining);
            }
            return most;
        }, MechWeaponComponent.PL_2.projectilesPerTrigger);
    }

    private static Stage laserTank(LookReview.Placement placement) {
        SceneWorld world = placement.holdouts(placement.builder()).build();
        BattleSimulation sim = world.sim();
        target(placement, sim);
        float[] at = placement.openNear(10.5f, 10.5f);
        long tank = sim.convoy().spawn(VehicleType.LASER_TANK, Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
        sim.convoy().body(tank).teleport(at[0], at[1], 0f);
        int burst = sim.convoy().turretStructure(tank).mount.weapon.burstCount;
        return new Stage(sim, tank, s -> s.convoy().isVehicle(tank) ? s.convoy().turret(tank).burstRemaining : 0, burst);
    }
}
