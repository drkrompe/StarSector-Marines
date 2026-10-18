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
 * The scout car scene's frames, from the same run that produced its verdicts: all three loops at 1920x1080 and 100%
 * scale, whole map plus a full-size close view framing the car's vantage and the hidden position. A review frame is a
 * neutral observer and draws no fog, so the position, the car and the walled-off squad are boxed and named instead;
 * the caption carries what the car sees, what the commander holds and how much of the position the player's fog
 * shows. Every fourth close frame is also written as a PNG still.
 */
public final class ScoutCarSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public ScoutCarSceneSnapshotSuite() {
        super(new ScoutCarScene(15), WIDTH, HEIGHT);
    }

    @Override protected float[] closeView(String loopId) {
        // 68 cells across is 38 cells tall at 16:9: enough to hold the car's vantage and the position in one frame.
        return new float[] {64f, (ScoutCarScene.SCOUT_Y + ScoutCarScene.BOX_Y1) / 2f - 1.5f, 68f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        // Scene walls have no art, so the ones the scene built are drawn as marks.
        NavigationGrid grid = simulation.getGrid();
        List<ReviewAnnotation.Cell> screen = new ArrayList<>();
        for (int y = ScoutCarScene.SCREEN_Y0; y < ScoutCarScene.HEIGHT; y++) {
            if (grid.isWalkable(ScoutCarScene.SCREEN_X, y)) continue;
            screen.add(new ReviewAnnotation.Cell(ScoutCarScene.SCREEN_X, y));
        }
        List<ReviewAnnotation.Cell> blind = new ArrayList<>();
        for (int x = ScoutCarScene.BLIND_X0; x <= ScoutCarScene.BLIND_X1; x++) {
            if (grid.isWalkable(x, ScoutCarScene.BLIND_Y)) continue;
            blind.add(new ReviewAnnotation.Cell(x, ScoutCarScene.BLIND_Y));
        }
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder()
                .cells(screen, "WALL", ReviewStyle.NOTE)
                .box(ScoutCarScene.BOX_X0, ScoutCarScene.BOX_Y0, ScoutCarScene.BOX_X1, ScoutCarScene.BOX_Y1,
                        "HIDDEN POSITION", ReviewStyle.OBJECTIVE)
                .box(ScoutCarScene.SQUAD_X - 4, ScoutCarScene.SQUAD_Y - 4, ScoutCarScene.SQUAD_X + 4,
                        ScoutCarScene.SQUAD_Y + 4, "SQUAD (NO LINE OF SIGHT)", ReviewStyle.NOTE);
        if (!blind.isEmpty()) marks.cells(blind, "WALL ACROSS THE VIEW", ReviewStyle.KEEP);
        for (long id : simulation.getConvoyVehicleIds()) {
            if (simulation.convoy().vehicleType(id) == VehicleType.SCOUT_CAR) {
                marks.label(ScoutCarScene.SCOUT_X, ScoutCarScene.SCOUT_Y + 3f, "SCOUT CAR", ReviewStyle.LANDING);
            }
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[10];
        for (int i = 0; i < all.length; i++) all[i] = i * 4;
        return all;
    }
}
