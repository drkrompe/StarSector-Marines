package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which worn-chitin marks a Rav body shows ({@code RavWear}), on a synthetic mask. */
class RavWearTest {

    /** A part whose every point may carry a mark: a 10 x 10 grid of points. */
    private static VehicleScuffs.Mask mask() {
        short[] points = new short[200];
        for (int i = 0; i < 100; i++) {
            points[i * 2] = (short) (10 + (i % 10) * 8);
            points[i * 2 + 1] = (short) (10 + (i / 10) * 8);
        }
        return new VehicleScuffs.Mask(100, 100, 12f, new short[][] {points});
    }

    /** Each mark as {x, y, kind}. */
    private static List<float[]> marks(float shell, float flesh) {
        List<float[]> out = new ArrayList<>();
        RavWear.place(mask(), RavWear.MATRIARCH_BODY, shell, flesh, 99L,
                (x, y, kind, variant, spin, alpha) -> out.add(new float[] {x, y, kind}));
        return out;
    }

    private static long count(List<float[]> marks, int kind) {
        return marks.stream().filter(m -> m[2] == kind).count();
    }

    @Test
    void aWholeBodyIsClean() {
        assertTrue(marks(0f, 0f).isEmpty());
    }

    @Test
    void strippedPlateScoresAndBruisesTheShellButOpensNoWound() {
        List<float[]> marks = marks(1f, 0f);
        assertEquals(RavWear.MATRIARCH_BODY.shell(), marks.size());
        assertEquals(0, count(marks, RavWear.WOUND));
        assertTrue(count(marks, RavWear.SCORE) > 0 && count(marks, RavWear.BRUISE) > 0);
        // Scores come first as the plate goes, bruises after.
        List<float[]> early = marks(0.3f, 0f);
        assertTrue(!early.isEmpty() && count(early, RavWear.BRUISE) == 0, "light wear shows only scores");
    }

    @Test
    void lostHealthOpensWounds() {
        List<float[]> marks = marks(0f, 1f);
        assertEquals(RavWear.MATRIARCH_BODY.flesh(), marks.size());
        assertEquals(marks.size(), count(marks, RavWear.WOUND));
    }

    @Test
    void marksBuildUpInPlace() {
        List<float[]> before = marks(0.5f, 0.4f);
        List<float[]> after = marks(0.9f, 0.8f);
        for (float[] mark : before) {
            assertTrue(after.stream().anyMatch(m -> m[0] == mark[0] && m[1] == mark[1] && m[2] == mark[2]),
                    "a mark stays where it appeared as the body wears further");
        }
        assertTrue(after.size() > before.size());
    }

    @Test
    void wearIsTheShareGone() {
        assertEquals(0.25f, RavWear.shellWear(525f, 700f), 1e-5f);
        assertEquals(0f, RavWear.shellWear(0f, 0f), 1e-5f);
        assertEquals(0.5f, RavWear.fleshWear(600f, 1200f), 1e-5f);
        assertEquals(1f, RavWear.fleshWear(-5f, 1200f), 1e-5f);
    }
}
