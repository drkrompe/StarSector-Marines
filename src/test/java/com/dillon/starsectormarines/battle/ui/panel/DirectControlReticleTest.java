package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Marks;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectControlReticleTest {
    private static final Marks LEFT_ONLY = new Marks(Mark.GOOD, Mark.NONE);

    /** The midpoint of the left button's tick, relative to the aim point at the origin. */
    private static float[] leftTick(float fireX, float fireY) {
        var strokes = DirectControlReticle.strokes(DirectControlReticle.SpreadLook.BLOOM, LEFT_ONLY, -1f, -1f,
                0f, 0f, fireX, fireY, Color.CYAN);
        var tick = strokes.get(0);
        return new float[]{(tick.x() + tick.endX()) * 0.5f, (tick.y() + tick.endY()) * 0.5f};
    }

    @Test
    void theLeftButtonsTickIsOnTheBodysLeftOfItsLineOfFire() {
        float[] up = leftTick(0f, 50f);
        assertTrue(up[0] < 0f, "aiming up the screen, the body's left is the screen's left");
        assertEquals(0f, up[1], 1e-4f, "the tick lies across the line of fire");
        assertTrue(leftTick(0f, -50f)[0] > 0f, "aiming down, the body's left is the screen's right");
        float[] east = leftTick(50f, 0f);
        assertTrue(east[1] > 0f, "aiming right, the body's left is up the screen (y up)");
        assertEquals(0f, east[0], 1e-4f);
    }

    @Test
    void noLineOfFireReadsAsAimingUp() {
        assertTrue(leftTick(0f, 0f)[0] < 0f);
    }
}
