package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The weighted director's rule on bare positions: objective bias, the friendly penalty, refusal, margin and ties. */
class WarshipWeightedDirectorTest {

    private static final float RADIUS = 6f;
    private static final float REACH = 200f;
    private static final float BLAST = 4f;
    private static final float MARGIN = 3f;

    private static final WarshipDirector.Friendlies NO_FRIENDLIES = WarshipDirector.Friendlies.NONE;

    private static WarshipDirector.Friendlies friendlies(float... xy) {
        int n = xy.length / 2;
        float[] x = new float[n];
        float[] y = new float[n];
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = xy[2 * i];
            y[i] = xy[2 * i + 1];
            w[i] = 1f;
        }
        return new WarshipDirector.Friendlies(x, y, w, new float[n], n);
    }

    private static WarshipDirector.Selection pick(float[] xs, float[] ys, WarshipDirector.Friendlies friendlies,
                                                  List<float[]> focus, boolean dangerClose, int slots) {
        return WarshipDirector.selectWeighted(xs, ys, xs.length, friendlies, focus, BLAST, MARGIN, dangerClose,
                0f, 0f, REACH, RADIUS, true, List.of(), List.of(), slots);
    }

    /** A knot of {@code n} hostiles about {@code (cx, cy)}. */
    private static float[][] knots(float[] cxs, float[] cys, int[] counts) {
        int total = 0;
        for (int c : counts) total += c;
        float[] xs = new float[total];
        float[] ys = new float[total];
        int k = 0;
        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[i]; j++) {
                xs[k] = cxs[i] + (j % 3) * 0.5f;
                ys[k] = cys[i] + (j / 3) * 0.5f;
                k++;
            }
        }
        return new float[][]{xs, ys};
    }

    @Test
    void withNothingToWeighItPicksTheDensestGroundLikeTheShippedRule() {
        float[][] p = knots(new float[]{40, 100}, new float[]{0, 0}, new int[]{3, 9});
        WarshipDirector.Selection s = pick(p[0], p[1], NO_FRIENDLIES, List.of(), false, 1);
        assertEquals(1, s.areas().size());
        assertTrue(s.areas().get(0).area.x() > 90f, "the denser knot");
        assertFalse(s.refusedForFriendlies());
    }

    @Test
    void anObjectiveOutweighsADenserDecoyAndOnlyWhenTheGroundIsNearIt() {
        float[][] p = knots(new float[]{40, 150}, new float[]{0, 0}, new int[]{6, 10});
        List<float[]> focus = List.of(new float[]{40f, 0f});
        WarshipDirector.Selection withObjective = pick(p[0], p[1], NO_FRIENDLIES, focus, false, 1);
        assertTrue(withObjective.areas().get(0).area.x() < 60f, "the objective-side knot of six beats the decoy of ten");
        List<float[]> farAway = List.of(new float[]{40f, 400f});
        WarshipDirector.Selection withFarObjective = pick(p[0], p[1], NO_FRIENDLIES, farAway, false, 1);
        assertTrue(withFarObjective.areas().get(0).area.x() > 140f, "an objective out of range pulls nothing");
    }

    @Test
    void anObjectiveWithNobodyOnItAttractsNoFire() {
        float[][] p = knots(new float[]{150}, new float[]{0}, new int[]{4});
        WarshipDirector.Selection s = pick(p[0], p[1], NO_FRIENDLIES, List.of(new float[]{40f, 0f}), false, 1);
        assertEquals(150f, s.areas().get(0).area.x(), 2f);
    }

    @Test
    void aFriendlyInsideTheBlastAndMarginRefusesTheDenserKnotForACleanOne() {
        float[][] p = knots(new float[]{40, 150}, new float[]{0, 0}, new int[]{5, 12});
        // One friendly just inside blast + margin of the big knot.
        WarshipDirector.Friendlies f = friendlies(150f + 1f, BLAST + MARGIN - 0.5f);
        WarshipDirector.Selection s = pick(p[0], p[1], f, List.of(), false, 1);
        assertEquals(1, s.areas().size());
        assertTrue(s.areas().get(0).area.x() < 60f, "the clean knot, though smaller");
        assertFalse(s.refusedForFriendlies(), "a clean alternative existed");
    }

    @Test
    void aFriendlyJustOutsideTheBlastAndMarginDoesNotCount() {
        float[][] p = knots(new float[]{150}, new float[]{0}, new int[]{6});
        // Hostiles span roughly x 150..151, y 0..0.5; the friendly is farther than blast + margin from all of them.
        WarshipDirector.Friendlies f = friendlies(150f, BLAST + MARGIN + 1.5f);
        WarshipDirector.Selection s = pick(p[0], p[1], f, List.of(), false, 1);
        assertEquals(1, s.areas().size());
    }

    @Test
    void whenEveryCandidateIsDirtyTheShipHoldsAndSaysSo() {
        float[][] p = knots(new float[]{40, 150}, new float[]{0, 0}, new int[]{5, 12});
        WarshipDirector.Friendlies f = friendlies(41f, 1f, 151f, 1f);
        WarshipDirector.Selection s = pick(p[0], p[1], f, List.of(), false, 2);
        assertTrue(s.areas().isEmpty());
        assertTrue(s.refusedForFriendlies());
    }

    @Test
    void dangerCloseFiresButStillPrefersTheFewerFriendlies() {
        float[][] p = knots(new float[]{40, 150}, new float[]{0, 0}, new int[]{12, 12});
        // Three friendlies at the first knot, one at the second.
        WarshipDirector.Friendlies f = friendlies(41f, 1f, 41.5f, 1f, 40.5f, 1.5f, 151f, 1f);
        WarshipDirector.Selection s = pick(p[0], p[1], f, List.of(), true, 1);
        assertEquals(1, s.areas().size(), "danger close lifts the refusal");
        assertTrue(s.areas().get(0).area.x() > 140f, "the knot with one friendly in the blast, not three");
        assertFalse(s.refusedForFriendlies());
    }

    @Test
    void aProtectedPositionCountsHeavilyAndCarriesItsOwnRadius() {
        float[][] p = knots(new float[]{40, 150}, new float[]{0, 0}, new int[]{4, 12});
        WarshipDirector.Friendlies keep = new WarshipDirector.Friendlies(
                new float[]{150f}, new float[]{BLAST + MARGIN + 8f}, new float[]{WarshipDirector.PROTECTED_WEIGHT},
                new float[]{10f}, 1);
        WarshipDirector.Selection s = pick(p[0], p[1], keep, List.of(), false, 1);
        assertTrue(s.areas().get(0).area.x() < 60f, "the keep's own radius reaches the big knot");
    }

    @Test
    void equalCandidatesGoToTheNearerGroundAndSeveralSlotsDoNotOverlap() {
        float[][] p = knots(new float[]{60, 120}, new float[]{0, 0}, new int[]{5, 5});
        WarshipDirector.Selection s = pick(p[0], p[1], NO_FRIENDLIES, List.of(), false, 2);
        assertEquals(2, s.areas().size());
        assertTrue(s.areas().get(0).area.x() < s.areas().get(1).area.x(), "the nearer knot is chosen first on a tie");
        assertTrue(Math.abs(s.areas().get(0).area.x() - s.areas().get(1).area.x()) >= RADIUS);
    }

    @Test
    void exposureCountsAFriendlyOnceHoweverManyHostilesAreNearIt() {
        float[][] p = knots(new float[]{50}, new float[]{0}, new int[]{9});
        float e = WarshipDirector.exposure(p[0], p[1], 9, 50.5f, 0.5f, RADIUS, friendlies(51f, 1f), BLAST + MARGIN);
        assertEquals(1f, e);
    }
}
