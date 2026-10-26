package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dome look as a function: what its CPU model must keep true so the shader
 * it mirrors stays readable. Each case asks one thing of one point.
 */
class ShieldDomeShadingTest {

    private static ShieldDomeDraw dome() {
        ShieldDomeDraw d = new ShieldDomeDraw();
        d.radiusPx = 400f;
        d.radiusCells = 16f;
        d.tint[0] = 0.55f;
        d.tint[1] = 0.86f;
        d.tint[2] = 1.0f;
        return d;
    }

    private static float alpha(ShieldDomeDraw d, float lx, float ly) {
        float[] out = new float[4];
        ShieldDomeShading.shade(d, lx, ly, 1.3f, out);
        return out[3];
    }

    /** Mean alpha over a ring of points at one radius, so a hex edge does not decide the answer. */
    private static float ringMean(ShieldDomeDraw d, float radius) {
        float sum = 0f;
        int n = 120;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sum += alpha(d, (float) (Math.cos(a) * radius), (float) (Math.sin(a) * radius));
        }
        return sum / n;
    }

    @Test
    void theRimIsBrighterThanTheInteriorWhichIsFaintButNotEmpty() {
        ShieldDomeDraw d = dome();
        float rim = ringMean(d, 0.995f);
        float interior = ringMean(d, 0.3f);
        assertTrue(rim > 4f * interior, "rim " + rim + " interior " + interior);
        assertTrue(interior > 0.01f && interior < 0.12f, "interior stays low-alpha: " + interior);
    }

    @Test
    void nothingIsDrawnBeyondTheBloom() {
        ShieldDomeDraw d = dome();
        assertEquals(0f, alpha(d, ShieldDomeShading.EXTENT + 0.01f, 0f), 1e-6f);
        assertEquals(0f, alpha(d, 0f, ShieldDomeShading.EXTENT + 0.01f), 1e-6f);
    }

    @Test
    void aFullerPoolLightsMoreCellsAndAnEmptyOneNone() {
        ShieldDomeDraw empty = dome();
        ShieldDomeDraw full = dome();
        full.level = 0.95f;
        assertTrue(ringMean(full, 0.5f) > ringMean(empty, 0.5f) * 1.15f,
                ringMean(full, 0.5f) + " vs " + ringMean(empty, 0.5f));
    }

    @Test
    void anImpactBrightensItsPointAndThenItsRippleReachesFurtherOut() {
        ShieldDomeDraw quiet = dome();
        ShieldDomeDraw struck = dome();
        struck.impactCount = 1;
        struck.impacts[0] = 0.3f;
        struck.impacts[1] = 0f;
        struck.impacts[2] = 0.05f;
        struck.impacts[3] = 1f;

        float before = alpha(quiet, 0.3f, 0f);
        float at = alpha(struck, 0.3f, 0f);
        assertTrue(at > before + 0.2f, "flash at the point: " + before + " -> " + at);

        // Later the flash has gone, but a ring sits ring-speed times age cells out.
        struck.impacts[2] = 0.8f;
        float ringCells = 0.8f * ShieldDomeShading.RING_SPEED;
        float onRing = alpha(struck, 0.3f + ringCells / struck.radiusCells, 0f);
        float offRing = alpha(struck, 0.3f + (ringCells * 0.4f) / struck.radiusCells, 0f);
        float quietOnRing = alpha(quiet, 0.3f + ringCells / struck.radiusCells, 0f);
        assertTrue(onRing > quietOnRing + 0.03f, "ripple lights the ring: " + quietOnRing + " -> " + onRing);
        assertTrue(onRing > offRing, "and it is a ring, not a wash: " + onRing + " vs " + offRing);
    }

    @Test
    void aBiggerImpactIsBrighterThanASmallOne() {
        ShieldDomeDraw small = dome();
        ShieldDomeDraw big = dome();
        for (ShieldDomeDraw d : new ShieldDomeDraw[]{small, big}) {
            d.impactCount = 1;
            d.impacts[0] = 0.2f;
            d.impacts[2] = 0.05f;
        }
        small.impacts[3] = ShieldImpactField.MIN_INTENSITY;
        big.impacts[3] = 1f;
        assertTrue(alpha(big, 0.2f, 0f) > alpha(small, 0.2f, 0f) + 0.1f);
    }

    @Test
    void aDownDomeLosesItsInteriorAndShowsOnlyTheRecoveredPartOfItsRim() {
        ShieldDomeDraw up = dome();
        ShieldDomeDraw down = dome();
        down.overloaded = true;
        down.recovery = 0.5f;

        assertTrue(ringMean(down, 0.3f) < ringMean(up, 0.3f) * 0.2f, "interior is gone");
        // The arc fraction is atan2(lx, -ly) / 2pi + 0.5: half recovered, the side with lx > 0
        // (fraction above a half) is waiting and the side with lx < 0 is back.
        float waitingSide = alpha(down, 0.995f, 0.0f);
        float recoveredSide = alpha(down, -0.995f, 0.0f);
        assertTrue(recoveredSide > waitingSide * 2f, recoveredSide + " vs " + waitingSide);
    }

    @Test
    void theHostileColourComesThrough() {
        ShieldDomeDraw red = dome();
        red.tint[0] = 1.0f;
        red.tint[1] = 0.55f;
        red.tint[2] = 0.50f;
        float[] out = new float[4];
        ShieldDomeShading.shade(red, 0f, 0.995f, 1f, out);
        assertTrue(out[0] > out[2], "a red dome has more red than blue at its rim");
    }

    /** A 120 degree arc facing local up. */
    private static ShieldDomeDraw arc() {
        ShieldDomeDraw d = dome();
        d.arcDirX = 0f;
        d.arcDirY = 1f;
        d.arcCos = (float) Math.cos(Math.toRadians(60));
        return d;
    }

    private static float[] rgba(ShieldDomeDraw d, float lx, float ly) {
        float[] out = new float[4];
        ShieldDomeShading.shade(d, lx, ly, 1.3f, out);
        return out;
    }

    /** The point at {@code radius} that lies {@code px} screen pixels inside the arc's right-hand end. */
    private static float[] nearRightEnd(ShieldDomeDraw d, float radius, float px) {
        double off = Math.toRadians(60) - Math.asin(px / (radius * d.radiusPx));
        return new float[]{(float) Math.sin(off) * radius, (float) Math.cos(off) * radius};
    }

    @Test
    void aWholeCircleArcIsTheDomeWhateverItsFacing() {
        ShieldDomeDraw domeDraw = dome();
        ShieldDomeDraw whole = dome();
        whole.arcDirX = 0.6f;
        whole.arcDirY = -0.8f;
        whole.arcCos = -1f;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            for (float radius : new float[]{0.2f, 0.7f, 0.995f, 1.05f}) {
                float lx = (float) (Math.cos(a) * radius);
                float ly = (float) (Math.sin(a) * radius);
                float[] expected = rgba(domeDraw, lx, ly);
                float[] got = rgba(whole, lx, ly);
                for (int c = 0; c < 4; c++) assertEquals(expected[c], got[c], 0f);
            }
        }
    }

    @Test
    void nothingIsDrawnOutsideTheArcNotEvenTheRim() {
        ShieldDomeDraw d = arc();
        d.level = 0.8f;
        d.hitFlash = 0.5f;
        for (float[] p : new float[][]{{0f, -0.6f}, {0f, -0.995f}, {0.9f, -0.2f}, {-0.99f, 0f}}) {
            float[] out = rgba(d, p[0], p[1]);
            assertEquals(0f, out[3], 0f, "alpha at " + p[0] + "," + p[1]);
            assertEquals(0f, out[0] + out[1] + out[2], 0f, "colour at " + p[0] + "," + p[1]);
        }
    }

    @Test
    void wellInsideTheArcItIsTheDome() {
        ShieldDomeDraw domeDraw = dome();
        ShieldDomeDraw d = arc();
        for (float[] p : new float[][]{{0f, 0.5f}, {0f, 0.995f}, {0.3f, 0.6f}, {-0.4f, 0.8f}}) {
            float[] expected = rgba(domeDraw, p[0], p[1]);
            float[] got = rgba(d, p[0], p[1]);
            for (int c = 0; c < 4; c++) assertEquals(expected[c], got[c], 0f);
        }
    }

    @Test
    void eachEndOfTheArcIsABrightEdgeLikeTheRim() {
        ShieldDomeDraw domeDraw = dome();
        ShieldDomeDraw d = arc();
        float[] onEdge = nearRightEnd(d, 0.7f, 1f);
        float[] further = nearRightEnd(d, 0.7f, 20f);
        float edge = alpha(d, onEdge[0], onEdge[1]);
        assertTrue(edge > alpha(domeDraw, onEdge[0], onEdge[1]) + 0.1f, "the end is brighter than the dome there");
        assertTrue(edge > alpha(d, further[0], further[1]) + 0.1f, "and it is an edge, not a wash");
        assertTrue(edge < ringMean(domeDraw, 0.995f) * 1.5f, "but no brighter than a rim");
        float[] mirrored = {-onEdge[0], onEdge[1]};
        assertEquals(edge, alpha(d, mirrored[0], mirrored[1]), 0.05f, "both ends alike");
    }

    @Test
    void theSignedDistanceIsToTheNearerEndOfTheArc() {
        ShieldDomeDraw d = arc();
        // On the facing, half a radius out: the end ray lies 60 degrees off.
        float inside = ShieldDomeShading.arcSignedPx(d, 0f, 0.5f);
        assertEquals(0.5f * (float) Math.sin(Math.toRadians(60)) * d.radiusPx, inside, 0.01f);
        // Behind the apex, the nearest part of the arc is the apex.
        assertEquals(-0.5f * d.radiusPx, ShieldDomeShading.arcSignedPx(d, 0f, -0.5f), 0.01f);
        assertEquals(Float.POSITIVE_INFINITY, ShieldDomeShading.arcSignedPx(dome(), 0f, -0.5f));
    }

    @Test
    void aFortScaleDomeKeepsTheSameRimAndFaintInterior() {
        // A shield fort declares the ground it covers: several times the bare generator's reach, at game scale.
        ShieldDomeDraw d = dome();
        d.radiusCells = 90f;
        d.radiusPx = 90f * 20f;
        float rim = ringMean(d, 0.995f);
        float interior = ringMean(d, 0.3f);
        assertTrue(rim > 4f * interior, "rim " + rim + " interior " + interior);
        assertTrue(interior > 0.01f && interior < 0.12f, "interior stays low-alpha: " + interior);
        assertEquals(0f, alpha(d, ShieldDomeShading.EXTENT + 0.01f, 0f), 1e-6f);
    }
}
