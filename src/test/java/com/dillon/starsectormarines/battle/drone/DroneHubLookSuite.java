package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * The look preset for a drone hub's drones in each tech family ({@link DroneLook#forHub}): three defender hubs on
 * generated ground, built low-tech, midline and high-tech, each with its three drones out on patrol round it. No
 * marine is within a drone's sight (the look holdouts stand beyond it), so the drones keep their stations instead of
 * leaving to fight. Followed close on each hub, and at play zoom on the middle one. Opt-in.
 */
public final class DroneHubLookSuite implements SnapshotSuite {

    static final int FOOT_W = 90;
    static final int FOOT_H = 50;
    static final int LOOK_TICKS = 600;
    /** Close enough in that a holdout 24 cells beyond the window's edge is out of every drone's sight. */
    static final float HUB_SPACING = 18f;
    static final float CLOSE_CELLS = 26f;

    @Override public String id() { return "drone-hub-look"; }

    @Override public String label() { return "A drone hub's drones as low-tech, midline and high-tech hubs build them"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            return LookReview.record(context.modRoot(), LookReview.Shot.of("hub-drones", FOOT_W, FOOT_H,
                    DroneHubLookSuite::stage,
                    placement -> List.of(LookReview.View.followAtPlay(placement, "midline"),
                            LookReview.View.follow("low-tech", placement, CLOSE_CELLS, "low-tech"),
                            LookReview.View.follow("midline-close", placement, CLOSE_CELLS, "midline"),
                            LookReview.View.follow("high-tech", placement, CLOSE_CELLS, "high-tech")),
                    LOOK_TICKS).withStills(40, 60, 80, 99));
        }
    }

    private static BattleSimulation stage(LookReview.Placement placement) {
        float midX = FOOT_W / 2f, hubY = FOOT_H / 2f;
        SceneBuilder builder = placement.builder();
        placement.holdouts(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        hub(placement, sim, "low-tech", midX - HUB_SPACING, hubY, EmplacementStyle.BRUTALIST);
        hub(placement, sim, "midline", midX, hubY, EmplacementStyle.MODULAR);
        hub(placement, sim, "high-tech", midX + HUB_SPACING, hubY, EmplacementStyle.COMPOSITE);
        return sim;
    }

    private static void hub(LookReview.Placement placement, BattleSimulation sim, String key, float localX,
                            float localY, EmplacementStyle style) {
        float[] at = placement.openNear(localX, localY);
        long hub = sim.spawn(DroneHub.create("hub-" + key, Faction.DEFENDER, (int) at[0], (int) at[1]));
        sim.setStructureStyle(hub, style);
        placement.subject(key, hub);
        // A hub launches one drone every DroneHub.SPAWN_INTERVAL_SEC; put its full screen out at once.
        for (int i = 0; i < DroneHub.MAX_ACTIVE_DRONES; i++) DroneSpawner.tryLaunch(hub, sim);
    }
}
