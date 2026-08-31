package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scene's sun: its dial ranges, and the one piece of geometry every caster
 * reads off it.
 */
class SunLightTest {

    @Test
    void everySettingClampsToItsDebugDialRange() {
        SunLight sun = new SunLight();
        assertEquals(SunLight.DEFAULT_SHADOW_STRENGTH, sun.shadowStrength(), 1e-6f);
        assertEquals(SunLight.DEFAULT_AZIMUTH_DEGREES, sun.azimuthDegrees(), 1e-6f);
        assertEquals(SunLight.DEFAULT_ELEVATION_DEGREES, sun.elevationDegrees(), 1e-6f);

        sun.setShadowStrength(-1f);
        sun.setAzimuthDegrees(-30f);
        sun.setElevationDegrees(-5f);
        assertEquals(SunLight.MIN_SHADOW_STRENGTH, sun.shadowStrength(), 1e-6f);
        assertEquals(SunLight.MIN_AZIMUTH_DEGREES, sun.azimuthDegrees(), 1e-6f);
        assertEquals(SunLight.MIN_ELEVATION_DEGREES, sun.elevationDegrees(), 1e-6f);

        sun.setShadowStrength(99f);
        sun.setAzimuthDegrees(999f);
        sun.setElevationDegrees(999f);
        assertEquals(SunLight.MAX_SHADOW_STRENGTH, sun.shadowStrength(), 1e-6f);
        assertEquals(SunLight.MAX_AZIMUTH_DEGREES, sun.azimuthDegrees(), 1e-6f);
        assertEquals(SunLight.MAX_ELEVATION_DEGREES, sun.elevationDegrees(), 1e-6f);
    }

    @Test
    void aNotANumberSettingIsIgnoredRatherThanPoisoningTheScene() {
        SunLight sun = new SunLight();
        sun.setElevationDegrees(Float.NaN);
        sun.setAzimuthDegrees(Float.NaN);
        sun.setShadowStrength(Float.NaN);
        assertEquals(SunLight.DEFAULT_ELEVATION_DEGREES, sun.elevationDegrees(), 1e-6f);
        assertEquals(SunLight.DEFAULT_AZIMUTH_DEGREES, sun.azimuthDegrees(), 1e-6f);
        assertEquals(SunLight.DEFAULT_SHADOW_STRENGTH, sun.shadowStrength(), 1e-6f);
    }

    /**
     * The whole of the geometry, and the reason heights are metres: one cell is
     * one metre, so a shadow's length in cells <em>is</em> the tangent
     * relationship with nothing in between.
     */
    @Test
    void reachIsHeightOverTheTangentOfElevation() {
        SunLight sun = new SunLight();
        sun.setElevationDegrees(45f);
        assertEquals(3f, sun.reachCells(3f), 1e-4f, "tan(45) is 1, so a 3 m wall reaches 3 cells");

        sun.setElevationDegrees((float) Math.toDegrees(Math.atan(2.0)));
        assertEquals(1.5f, sun.reachCells(3f), 1e-3f, "twice the tangent, half the reach");

        assertEquals(0f, sun.reachCells(0f), 1e-6f, "nothing flat casts");
        assertEquals(0f, sun.reachCells(-1f), 1e-6f, "and neither does a hollow");
    }

    @Test
    void theBearingPointsTowardTheSunRatherThanAlongTheShadow() {
        SunLight sun = new SunLight();
        sun.setAzimuthDegrees(0f);
        assertEquals(1f, sun.dirX(), 1e-4f);
        assertEquals(0f, sun.dirY(), 1e-4f);

        sun.setAzimuthDegrees(90f);
        assertEquals(0f, sun.dirX(), 1e-4f);
        assertEquals(1f, sun.dirY(), 1e-4f);

        // The default is over the reader's left shoulder, so shadows fall down
        // and to the right: the direction a top-down map reads depth in.
        sun.setAzimuthDegrees(SunLight.DEFAULT_AZIMUTH_DEGREES);
        assertTrue(sun.dirX() < 0f && sun.dirY() > 0f);
    }

    @Test
    void nothingCastsAtZeroStrength() {
        SunLight sun = new SunLight();
        assertTrue(sun.casts());
        sun.setShadowStrength(0f);
        assertFalse(sun.casts());
    }
}
