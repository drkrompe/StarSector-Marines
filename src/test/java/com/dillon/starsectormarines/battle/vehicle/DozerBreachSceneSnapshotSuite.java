package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The dozer breach scene's frames, from the same run that produced its verdicts: both loops at 1920x1080 and 100%
 * scale, whole map plus a full-size close view on the breach. The scene's walls have no art, so every standing wall
 * cell is marked each frame — ordinary wall in one colour, fortified in another — and the gap the blade makes shows
 * as marks disappearing. Every fourth close frame is also written as a PNG still.
 */
public final class DozerBreachSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public DozerBreachSceneSnapshotSuite() {
        super(new DozerBreachScene(15), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected float[] closeView(String loopId) {
        return new float[] {22f, (DozerBreachScene.WALL_Y0 + DozerBreachScene.WALL_Y1) / 2f + 2f, 44f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        NavigationGrid grid = simulation.getGrid();
        List<ReviewAnnotation.Cell> ordinary = new ArrayList<>();
        List<ReviewAnnotation.Cell> fortified = new ArrayList<>();
        for (int y = DozerBreachScene.WALL_Y0; y <= DozerBreachScene.WALL_Y1; y++) {
            for (int x = 0; x < DozerBreachScene.WIDTH; x++) {
                if (grid.isWalkable(x, y)) continue;
                (x < DozerBreachScene.FORTIFIED_X ? ordinary : fortified).add(new ReviewAnnotation.Cell(x, y));
            }
        }
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder()
                .cells(ordinary, "WALL " + DozerBreachScene.ORDINARY_STRUCTURE, ReviewStyle.NOTE)
                .cells(fortified, "FORTIFIED " + DozerBreachScene.FORTIFIED_STRUCTURE, ReviewStyle.KEEP)
                .label(DozerBreachScene.APC_GOAL_X + .5f, DozerBreachScene.APC_GOAL_Y + 2.5f, "APC GOAL",
                        ReviewStyle.LANDING);
        for (long id : simulation.getConvoyVehicleIds()) {
            GroundBody body = simulation.convoy().body(id);
            String name = simulation.convoy().vehicleType(id) == VehicleType.DOZER_BREACHER ? "DOZER" : "APC";
            marks.label(body.x, body.y - 3.2f, name, ReviewStyle.NOTE);
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[30];
        for (int i = 0; i < all.length; i++) all[i] = i * 4;
        return all;
    }
}
