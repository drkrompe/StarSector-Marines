package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.world.gen.layout.Line;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartLayoutTest {

    @Test
    void theForwardLineLeavesEveryLaneOpen() {
        for (Form form : Form.values()) {
            List<Piece> runs = HeartLayout.forwardLine(form);
            assertFalse(runs.isEmpty(), form + " digs a line");
            float[] gaps = form == Form.BROAD_FRONT ? HeartLayout.BROAD_GAPS : HeartLayout.BURN_GAPS;
            int across = form.advancesAlongY() ? form.scale.width : form.scale.height;
            for (float gap : gaps) {
                int lane = Math.round(gap * (across - 1));
                for (Piece piece : runs) {
                    Line line = (Line) piece;
                    int a = along(form, line.path().get(0)), b = along(form, line.path().get(1));
                    assertTrue(lane < Math.min(a, b) - 1 || lane > Math.max(a, b) + 1,
                            form + ": " + line.name() + " closes the lane at " + lane);
                }
            }
        }
    }

    private static int along(Form form, int[] cell) {
        return form.advancesAlongY() ? cell[0] : cell[1];
    }
}
