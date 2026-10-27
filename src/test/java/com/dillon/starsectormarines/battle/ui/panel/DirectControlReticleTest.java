package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Mark;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlFireMarks.Marks;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle.Load;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectControlReticleTest {
    private static final Marks LEFT_ONLY = new Marks(Mark.GOOD, Mark.NONE);

    /** The midpoint of the left button's tick, relative to the aim point at the origin. */
    private static float[] leftTick(float fireX, float fireY) {
        var strokes = DirectControlReticle.strokes(LEFT_ONLY, -1f, -1f,
                0f, 0f, fireX, fireY, Color.CYAN);
        var tick = strokes.get(0);
        return new float[]{(tick.x() + tick.endX()) * 0.5f, (tick.y() + tick.endY()) * 0.5f};
    }

    /** The strokes for the left button alone, aiming up the screen with the aim point at the origin. */
    private static List<DirectControlAimLane.Stroke> left(Mark mark, Load load) {
        return DirectControlReticle.strokes(new Marks(mark, Mark.NONE), load, -1f, -1f, 0f, 0f, 0f, 50f,
                Color.CYAN);
    }

    /** The magazine arc's strokes: the ones well out from the dot. */
    private static List<DirectControlAimLane.Stroke> arc(List<DirectControlAimLane.Stroke> strokes) {
        return strokes.stream().filter(s -> Math.hypot(s.x(), s.y()) > 18f).toList();
    }

    private static float length(List<DirectControlAimLane.Stroke> strokes, Color color) {
        float sum = 0f;
        for (var s : strokes) if (s.color().equals(color)) sum += (float) Math.hypot(s.endX() - s.x(), s.endY() - s.y());
        return sum;
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

    @Test
    void aRunningClockLightsTheTickOnlyAsFarAsItHasRun() {
        var strokes = left(Mark.FALLING_OFF, new Load(0.5f, 0, 0, false));
        float lit = length(strokes, DirectControlReticle.FALLING_OFF);
        float faint = length(strokes, DirectControlReticle.SPENT);
        assertEquals(lit, faint, 1e-3f, "half the clock lights half the tick, still in its range colour");
        var inner = strokes.get(0);
        assertTrue(Math.abs(inner.x()) < Math.abs(inner.endX()), "the lit part grows out from the dot");
        assertEquals(0f, length(left(Mark.GOOD, Load.READY), DirectControlReticle.SPENT), "a ready tick is whole");
    }

    @Test
    void aTickThatWillNotFireShowsNoClock() {
        assertEquals(0f, length(left(Mark.HELD, new Load(0f, 0, 0, false)), DirectControlReticle.SPENT));
    }

    @Test
    void theArcLightsTheRoundsLeftAndGoesFaintFromTheLineOfFire() {
        var arc = arc(left(Mark.GOOD, new Load(1f, 3, 10, false)));
        float loaded = length(arc, DirectControlReticle.GOOD);
        float spent = length(arc, DirectControlReticle.SPENT);
        assertEquals(0.3f, loaded / (loaded + spent), 1e-3f);
        // Aiming up, the line of fire is +y: spent rounds sit toward it, loaded ones behind.
        assertTrue(meanY(arc, DirectControlReticle.SPENT) > meanY(arc, DirectControlReticle.GOOD));
        assertTrue(arc.stream().allMatch(s -> s.x() < 0f && s.endX() < 0f), "the arc is on the left button's side");
    }

    @Test
    void aReloadRefillsTheArcPalerAsItRuns() {
        var arc = arc(left(Mark.GOOD, new Load(0.6f, 0, 10, true)));
        float refilled = length(arc, DirectControlReticle.REFILLED);
        float empty = length(arc, DirectControlReticle.SPENT);
        assertEquals(0.6f, refilled / (refilled + empty), 1e-3f);
        assertEquals(0f, length(arc, DirectControlReticle.GOOD), "nothing reads as loaded until the reload ends");
    }

    @Test
    void aLargeMagazineIsOneBarAtTheSameShare() {
        var arc = arc(left(Mark.GOOD, new Load(1f, 14, 56, false)));
        float loaded = length(arc, DirectControlReticle.GOOD);
        float spent = length(arc, DirectControlReticle.SPENT);
        assertEquals(0.25f, loaded / (loaded + spent), 1e-3f);
        assertTrue(arc.size() <= 40, "no per-round segments");
    }

    @Test
    void noMagazineOrNothingOnTheButtonDrawsNoArc() {
        assertTrue(arc(left(Mark.GOOD, new Load(0.5f, 0, 0, false))).isEmpty());
        assertTrue(arc(left(Mark.NONE, new Load(1f, 3, 10, false))).isEmpty());
    }

    @Test
    void aClockIsTheShareThatHasRun() {
        assertEquals(1f, Load.clock(0f, 2f));
        assertEquals(1f, Load.clock(1f, 0f), "no duration is ready");
        assertEquals(0.25f, Load.clock(1.5f, 2f), 1e-6f);
        assertEquals(0f, Load.clock(3f, 2f), "a clock longer than its duration reads as just begun");
    }

    private static float meanY(List<DirectControlAimLane.Stroke> strokes, Color color) {
        float sum = 0f;
        int n = 0;
        for (var s : strokes) {
            if (!s.color().equals(color)) continue;
            sum += s.y() + s.endY();
            n += 2;
        }
        return sum / n;
    }
}
