package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.List;

/**
 * The derelict flock's frames: the scene's {@code split}, {@code strike-open} and {@code strike-indoors} loops at
 * 1920x1080 on a bare arena (verdict evidence), and for the owner a look recording on real generated ground: a flock
 * of sixteen put up at one end of a colony window and sent to strike a fire team at the other, so it pours down the
 * streets, divides round the buildings between and wraps the team. Followed at play zoom and wide on the window.
 */
public final class DerelictFlockSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    static final int FOOT_W = 90;
    static final int FOOT_H = 50;
    static final int LOOK_TICKS = 900;

    public DerelictFlockSceneSnapshotSuite() {
        super(new DerelictFlockScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(LookReview.Shot.of("flock-strike", FOOT_W, FOOT_H, DerelictFlockSceneSnapshotSuite::stage,
                placement -> List.of(LookReview.View.followAtPlay(placement, "flock"),
                        LookReview.View.follow("wide", placement, 96f, "flock", "team")),
                LOOK_TICKS));
    }

    private static BattleSimulation stage(LookReview.Placement placement) {
        float[] launch = placement.openNear(8f, FOOT_H / 2f);
        float[] team = behindBuildings(placement, launch);
        SceneBuilder builder = placement.builder();
        builder.squad("team").faction(Faction.MARINE).size(4)
                .at((int) team[0] - placement.site().x0(), (int) team[1] - placement.site().y0())
                .primary("weapon.field-rifle").stationary().done();
        placement.holdouts(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long[] men = world.members("team");
        Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, launch[0], launch[1], DerelictFlockScene.FLOCK_SIZE,
                FlockWeights.WASP, DerelictFlockScene.LEASH * 2f);
        flock.strike(men[0]);
        placement.subject("flock", flock.memberIds().get(0));
        placement.subject("team", men[0]);
        return sim;
    }

    /**
     * Where to stand the team so the flock must go round buildings to reach it: the open cell, 36 to 56 cells from
     * the launch, whose straight line from it crosses the most roofed cells. The flock's own route then pours down
     * the streets and courts and divides round what is in the way.
     */
    static float[] behindBuildings(LookReview.Placement placement, float[] launch) {
        NavigationGrid grid = placement.map().grid;
        CellTopology topology = placement.map().topology;
        float[] best = placement.openNear(FOOT_W - 14f, FOOT_H / 2f);
        int bestRoofed = -1;
        int lx = (int) launch[0], ly = (int) launch[1];
        for (int y = ly - 56; y <= ly + 56; y++) {
            for (int x = lx - 56; x <= lx + 56; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)
                        || topology.getBuildingId(x, y) != 0) continue;
                float d = (float) Math.hypot(x + .5f - launch[0], y + .5f - launch[1]);
                if (d < 36f || d > 56f) continue;
                int roofed = roofedAlong(topology, launch[0], launch[1], x + .5f, y + .5f);
                if (roofed > bestRoofed) {
                    bestRoofed = roofed;
                    best = new float[]{x + .5f, y + .5f};
                }
            }
        }
        return best;
    }

    private static int roofedAlong(CellTopology topology, float x0, float y0, float x1, float y1) {
        int steps = (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2f);
        int count = 0, lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int cx = (int) Math.floor(x0 + (x1 - x0) * t), cy = (int) Math.floor(y0 + (y1 - y0) * t);
            if (cx == lastX && cy == lastY) continue;
            lastX = cx;
            lastY = cy;
            if (topology.isRoofIntact(cx, cy)) count++;
        }
        return count;
    }
}
