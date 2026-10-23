package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.List;

/**
 * Records {@link ScreenedAdvanceScene} at 1920x1080: every bound drawn as a line across the route, the
 * one the screen is on in the front's colour, and the rally point boxed.
 */
public final class ScreenedAdvanceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Cells either side of the route a bound's line is drawn. */
    private static final float HALF_WIDTH = 16f;

    public ScreenedAdvanceSceneSnapshotSuite() {
        super(new ScreenedAdvanceScene(), 1920, 1080);
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        ScreenedAdvance advance = ScreenedAdvanceScene.current;
        if (advance == null) return ReviewAnnotations.NONE;
        AdvanceRoute route = advance.route();
        ReviewAnnotations.Builder builder = ReviewAnnotations.builder();
        for (int i = 0; i < advance.boundCount(); i++) {
            float along = advance.boundAlong(i);
            float[] at = route.pointAt(along), dir = route.directionAt(along);
            boolean last = i == advance.boundCount() - 1;
            boolean on = i == advance.bound();
            ReviewStyle style = on ? ReviewStyle.FRONT : ReviewStyle.ROUTE;
            builder.polyline(List.of(
                    new ReviewAnnotation.Point(at[0] + dir[1] * HALF_WIDTH, at[1] - dir[0] * HALF_WIDTH),
                    new ReviewAnnotation.Point(at[0] - dir[1] * HALF_WIDTH, at[1] + dir[0] * HALF_WIDTH)),
                    last ? "RALLY" : "bound " + (i + 1), last ? ReviewStyle.LANDING : style);
        }
        float[] start = route.pointAt(0f), end = route.pointAt(route.length());
        builder.arrow(start[0], start[1] - HALF_WIDTH - 2f, end[0], end[1] - HALF_WIDTH - 2f, "advance", ReviewStyle.APPROACH);
        return builder.build();
    }
}
