package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link VehicleController}'s curvature speed governor —
 * {@link VehicleController#previewTurnDegrees} (the bend signal) and
 * {@link VehicleController#curvatureSpeedCap} (its mapping to a speed cap). These
 * back the fix for "trucks miss a tight turn at cruise and reverse out": the
 * speed cap slows the truck into the bend so the bounded steering slew can make
 * it. Tests pin the geometry (bearing-wrap, the preview window) and the
 * deadband/clamp shape, not the tuning constants' exact values.
 */
public class VehicleControllerCurvatureTest {

    private static final float MAX = 10f;
    private static final float EPS = 1e-3f;

    /** Body sits at the origin; the polyline starts at index 0. */
    private static float capOf(float[] xs, float[] ys) {
        return VehicleController.curvatureSpeedCap(xs, ys, 0, 0f, 0f, MAX);
    }

    private static float turnOf(float[] xs, float[] ys) {
        return VehicleController.previewTurnDegrees(xs, ys, 0, 0f, 0f);
    }

    @Test
    public void straightPathHasNoTurnAndFullSpeed() {
        float[] xs = {0, 0, 0, 0, 0};
        float[] ys = {1, 2, 3, 4, 5};
        assertEquals(0f, turnOf(xs, ys), EPS, "a straight run has zero heading change");
        assertEquals(MAX, capOf(xs, ys), EPS, "no bend → no speed cut");
    }

    @Test
    public void rightAngleCornerReadsNinetyDegreesAndCapsToTheFloor() {
        // North to (0,2), then due east — a 90° corner inside the preview window.
        float[] xs = {0, 0, 1, 2, 3};
        float[] ys = {1, 2, 2, 2, 2};
        assertEquals(90f, turnOf(xs, ys), 0.5f, "a right-angle corner is ~90° of heading change");
        // 90° is past CURVE_FULL_DEG, so the cap saturates at the floor fraction.
        assertEquals(MAX * 0.35f, capOf(xs, ys), EPS, "a hard corner caps at the floor speed");
    }

    @Test
    public void gentleBendWithinDeadbandKeepsFullSpeed() {
        // ~14° kink — below the deadband.
        float[] xs = {0, 0, 0.25f, 0.5f};
        float[] ys = {1, 2, 3, 4};
        float turn = turnOf(xs, ys);
        assertTrue(turn > 0f && turn < 20f, "kink should be a small sub-deadband angle, was " + turn);
        assertEquals(MAX, capOf(xs, ys), EPS, "a bend within the deadband isn't slowed");
    }

    @Test
    public void moderateCornerSitsBetweenFloorAndCruise() {
        // North, then a ~50° turn.
        float[] xs = {0, 0, 0.766f};
        float[] ys = {1, 2, 2.643f};
        float cap = capOf(xs, ys);
        assertTrue(cap > MAX * 0.35f + EPS, "a moderate corner is faster than the floor");
        assertTrue(cap < MAX - EPS, "a moderate corner is slower than cruise");
        // Monotonic: sharper ⇒ slower.
        float[] sharpXs = {0, 0, 1, 2, 3};
        float[] sharpYs = {1, 2, 2, 2, 2};
        assertTrue(capOf(sharpXs, sharpYs) < cap, "a 90° corner caps lower than a 50° one");
    }

    @Test
    public void bendBeyondThePreviewWindowDoesNotSlow() {
        // Straight for >CURVE_PREVIEW_CELLS, then a hard turn the window never reaches.
        float[] xs = {0, 0, 0, 0, 0, 5};
        float[] ys = {1, 2, 3, 4, 5, 5};
        assertEquals(0f, turnOf(xs, ys), EPS, "the far corner is outside the preview window");
        assertEquals(MAX, capOf(xs, ys), EPS, "a corner beyond the window doesn't pre-emptively slow");
    }

    @Test
    public void hairpinClampsAtFloorNotBelow() {
        // 180° reversal across two right angles — total turn far past CURVE_FULL_DEG.
        float[] xs = {0, 0, 1, 1, 1};
        float[] ys = {1, 2, 2, 1, 0};
        assertTrue(turnOf(xs, ys) > 75f, "a hairpin is well past the full-slow threshold");
        assertEquals(MAX * 0.35f, capOf(xs, ys), EPS, "the cap floors at the min fraction, never below");
    }

    @Test
    public void measuresFromTheBodyWithANonZeroStartIndexOffTheVertices() {
        // Production anchoring: trajectory tracking passes startIdx=1 and the
        // corridor passes corridor.cursor(), with the body sitting off the
        // polyline vertices. Vertices before startIdx are skipped; the first
        // measured segment runs body → xs[startIdx]. Corridor: a straight east
        // run that turns north at index 3; body mid-segment, slightly off-line.
        float[] xs = {0, 2, 4, 4, 4};
        float[] ys = {0, 0, 0, 3, 6};
        // startIdx=2 skips (0,0)/(2,0); body just shy of (4,0), 0.3 off the line.
        float turn = VehicleController.previewTurnDegrees(xs, ys, 2, 2.5f, 0.3f);
        assertTrue(turn > 75f, "the upcoming ~right-angle turn is measured from the body, was " + turn);
        float cap = VehicleController.curvatureSpeedCap(xs, ys, 2, 2.5f, 0.3f, MAX);
        assertEquals(MAX * 0.35f, cap, EPS, "a hard corner ahead caps to the floor regardless of start index");
    }
}
