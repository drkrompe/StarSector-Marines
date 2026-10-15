package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A sprite drawn at the frame's angle has its own right edge along the frame's {@code across} and its own up edge along
 * the frame's {@code downward}'s opposite on the y-up screen the battle is drawn on (cell +y is up, angles run
 * counter-clockwise), at every turn a fort is stamped at: the frame's turn is a rotation, never a mirror.
 */
class FortSpriteFrameAngleTest {

    @Test
    void theSpritesRightEdgeFollowsTheFramesAcrossAndItsUpEdgeFollowsDownwardAtEveryTurn() {
        for (FortPlan plan : List.of(BatteryFort.PLAN, ShieldFort.PLAN)) {
            for (int turn = 0; turn < 4; turn++) {
                FortFrame frame = new FortFrame(plan, 60, 40, turn);
                double angle = Math.toRadians(FortBuildingRenderSystem.frameAngle(frame));
                double c = Math.cos(angle), s = Math.sin(angle);
                int[] across = frame.across();
                int[] down = frame.downward();
                // Sprite +x is (c, s); sprite up (screen up, +y) is (-s, c). A sprite flipped top to bottom has its
                // authored (y down) frame's +y on that up edge.
                assertEquals(across[0], Math.round(c), plan.kind() + " right edge x at turn " + turn);
                assertEquals(across[1], Math.round(s), plan.kind() + " right edge y at turn " + turn);
                assertEquals(down[0], Math.round(-s), plan.kind() + " up edge x at turn " + turn);
                assertEquals(down[1], Math.round(c), plan.kind() + " up edge y at turn " + turn);
            }
        }
    }
}
