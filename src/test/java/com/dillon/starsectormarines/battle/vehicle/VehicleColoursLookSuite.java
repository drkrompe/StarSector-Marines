package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hegemony line's weapon-point variants in play (hegemony-armor-line.md): the Scorpion and the Onager each in
 * yellow, green and red with the part a stock spawn of that colour fits ({@link VehicleType#standardPart(HardpointType)}),
 * firing north on a pinned, unarmed scout car each, so the green racks stand up in their open barbettes and the red
 * emitters fire from under the closed head. At play zoom and close on each class. Owner-facing look frames only.
 */
public final class VehicleColoursLookSuite implements SnapshotSuite {

    private static final List<VehicleType> HULLS = List.of(VehicleType.MEDIUM_TANK, VehicleType.BREAKTHROUGH_TANK);
    private static final List<HardpointType> POINTS =
            List.of(HardpointType.BALLISTIC, HardpointType.MISSILE, HardpointType.ENERGY);
    private static final float FILE = 9f;
    private static final float RANGE = 14f;
    private static final int TICKS = 720;

    @Override public String id() { return "vehicle-colours"; }

    @Override public String label() { return "Scorpion and Onager in yellow, green and red, firing"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return List.copyOf(LookReview.record(context.modRoot(), firing()));
    }

    static LookReview.Shot firing() {
        int files = HULLS.size() * POINTS.size();
        int width = (int) (FILE * files + 10);
        int height = (int) (RANGE + 16);
        return LookReview.Shot.of("vehicle-colours", width, height, placement -> {
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            int file = 0;
            for (VehicleType hull : HULLS) {
                for (HardpointType point : POINTS) {
                    float x = 5f + FILE * (file + .5f);
                    float[] at = placement.openNear(x, 6f);
                    float[] targetAt = placement.openNear(x, 6f + RANGE);
                    String key = hull.name().toLowerCase() + "-" + point.name().toLowerCase();
                    long tank = placement.subject(key, sim.convoy().spawn(new VehicleDeploymentSpec(null, null, hull,
                            VehiclePlating.FACTORY, VehicleEngine.STOCK, hull.standardPart(point)), Faction.MARINE,
                            VehicleMission.deployed(at[0], at[1])));
                    sim.convoy().body(tank).teleport(at[0], at[1], 0f);
                    pin(sim, tank);
                    long target = sim.convoy().spawn(VehicleType.SCOUT_CAR, Faction.DEFENDER,
                            VehicleMission.deployed(targetAt[0], targetAt[1]));
                    sim.convoy().body(target).teleport(targetAt[0], targetAt[1], 0f);
                    pin(sim, target);
                    file++;
                }
            }
            float[] spotter = placement.openNear(width / 2f, 2f);
            long car = sim.convoy().spawn(VehicleType.SCOUT_CAR, Faction.MARINE,
                    VehicleMission.deployed(spotter[0], spotter[1]));
            sim.convoy().body(car).teleport(spotter[0], spotter[1], 0f);
            pin(sim, car);
            return sim;
        }, placement -> {
            List<LookReview.View> views = new ArrayList<>();
            List<String> all = new ArrayList<>();
            for (VehicleType hull : HULLS) {
                List<String> keys = new ArrayList<>();
                for (HardpointType point : POINTS) keys.add(hull.name().toLowerCase() + "-" + point.name().toLowerCase());
                all.addAll(keys);
                views.add(LookReview.View.follow(hull.name().toLowerCase(), placement, 30f, keys.toArray(new String[0])));
            }
            views.add(0, LookReview.View.followAtPlay(placement, all.toArray(new String[0])));
            return views;
        }, TICKS);
    }

    /** Holds a hull where it stands: a paused authored route keeps engagement off it; its turret works. */
    private static void pin(BattleSimulation sim, long id) {
        VehicleMission mission = sim.convoy().mission(id);
        GroundBody body = sim.convoy().body(id);
        mission.authoredRouteX = new float[] {body.x};
        mission.authoredRouteY = new float[] {body.y};
        mission.authoredRoutePaused = true;
    }
}
