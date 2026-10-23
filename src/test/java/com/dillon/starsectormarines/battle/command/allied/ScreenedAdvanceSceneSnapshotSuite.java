package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.List;

/**
 * Records {@link ScreenedAdvanceScene} at 1920x1080: a line across the route through each element's
 * centre (the screen's in the front's colour, the main's as a route), so the gap between them is what the
 * frame shows, the rally point marked, and while the formation is engaged an arrow from the main the way
 * it has turned to face.
 */
public final class ScreenedAdvanceSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Cells either side of the route a line is drawn. */
    private static final float HALF_WIDTH = 16f;
    /** Cells the engaged formation's facing arrow runs from the main's body. */
    private static final float FACING_ARROW = 14f;

    public ScreenedAdvanceSceneSnapshotSuite() {
        super(new ScreenedAdvanceScene(), 1920, 1080);
    }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        ScreenedAdvance advance = ScreenedAdvanceScene.current;
        if (advance == null) return ReviewAnnotations.NONE;
        AdvanceRoute route = advance.route();
        ReviewAnnotations.Builder builder = ReviewAnnotations.builder();
        across(builder, route, route.length(), "RALLY", ReviewStyle.LANDING);
        if (!ScreenedAdvance.positions(sim, advance.screen()).isEmpty())
            across(builder, route, advance.centre(sim, advance.screen()), "screen", ReviewStyle.FRONT);
        if (!ScreenedAdvance.positions(sim, advance.main()).isEmpty())
            across(builder, route, advance.centre(sim, advance.main()), "main", ReviewStyle.ROUTE);
        float[] facing = advance.facing(), main = advance.body(sim, advance.main());
        if (facing != null && main != null) {
            builder.arrow(main[0], main[1], main[0] + facing[0] * FACING_ARROW, main[1] + facing[1] * FACING_ARROW,
                    "facing the threat", ReviewStyle.KEEP);
        }
        float[] start = route.pointAt(0f), end = route.pointAt(route.length());
        builder.arrow(start[0], start[1] - HALF_WIDTH - 2f, end[0], end[1] - HALF_WIDTH - 2f, "advance", ReviewStyle.APPROACH);
        return builder.build();
    }

    private static void across(ReviewAnnotations.Builder builder, AdvanceRoute route, float along, String text, ReviewStyle style) {
        float[] at = route.pointAt(along), dir = route.directionAt(along);
        builder.polyline(List.of(
                new ReviewAnnotation.Point(at[0] + dir[1] * HALF_WIDTH, at[1] - dir[0] * HALF_WIDTH),
                new ReviewAnnotation.Point(at[0] - dir[1] * HALF_WIDTH, at[1] + dir[0] * HALF_WIDTH)), text, style);
    }
}
