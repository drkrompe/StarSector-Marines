package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.logistics.ResupplyCache;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The supply-truck scene's frames, from the same run that produced its verdicts: every loop at 1920x1080 and 100%
 * scale, whole map plus a full-size close view on the truck's halt and both squads. The truck's cache is drawn as
 * its service ring, labelled with what is left aboard and whether it is open. Every fifth close frame is also written
 * as a PNG still.
 */
public final class SupplyTruckSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int RING_SEGMENTS = 32;

    public SupplyTruckSceneSnapshotSuite() {
        super(new SupplyTruckScene(10), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected float[] closeView(String loopId) {
        return new float[] {SupplyTruckScene.TRUCK_X - 8f, SupplyTruckScene.TRUCK_Y + 4f, 40f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        int nx = SupplyTruckScene.NEAR_X;
        int ny = SupplyTruckScene.NEAR_Y;
        int fx = SupplyTruckScene.FAR_X;
        int fy = SupplyTruckScene.FAR_Y;
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder()
                .box(nx - 3, ny - 1, nx + 2, ny + 1, "SPENT SQUAD", ReviewStyle.OBJECTIVE)
                .box(fx - 3, fy - 1, fx + 2, fy + 1, "OUT OF REACH", ReviewStyle.NOTE);
        for (ResupplyCache cache : simulation.getResupplyCaches()) {
            if (!cache.carried()) continue;
            // Annotation points address cell centres; the cache stands at a world position.
            float cx = cache.centerX() - .5f;
            float cy = cache.centerY() - .5f;
            List<ReviewAnnotation.Point> ring = new ArrayList<>();
            for (int i = 0; i <= RING_SEGMENTS; i++) {
                double a = 2.0 * Math.PI * i / RING_SEGMENTS;
                ring.add(new ReviewAnnotation.Point(
                        cx + (float) (Math.cos(a) * ResupplyCache.SERVICE_RADIUS_CELLS),
                        cy + (float) (Math.sin(a) * ResupplyCache.SERVICE_RADIUS_CELLS)));
            }
            String state = cache.serving() ? "open" : cache.depleted() ? "lost" : "closed";
            marks.polyline(ring, null, cache.serving() ? ReviewStyle.LANDING : ReviewStyle.NOTE)
                    .label(cx, cy - ResupplyCache.SERVICE_RADIUS_CELLS - 1f,
                            "SUPPLY TRUCK " + cache.stock + "/" + cache.maximumStock + " " + state,
                            cache.serving() ? ReviewStyle.LANDING : ReviewStyle.NOTE);
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[18];
        for (int i = 0; i < all.length; i++) all[i] = i * 5;
        return all;
    }
}
