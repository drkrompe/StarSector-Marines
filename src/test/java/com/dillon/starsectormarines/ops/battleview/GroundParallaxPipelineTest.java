package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundParallaxPipelineTest {

    @Test
    void liveStrengthSettingClampsToDebugDialRange() {
        GroundParallaxPipeline pipeline = new GroundParallaxPipeline();
        assertEquals(GroundParallaxPipeline.DEFAULT_STRENGTH, pipeline.parallaxStrength(), 1e-6f);
        pipeline.setParallaxStrength(0.0125f);
        assertEquals(0.0125f, pipeline.parallaxStrength(), 1e-6f);
        pipeline.setParallaxStrength(-1f);
        assertEquals(GroundParallaxPipeline.MIN_STRENGTH, pipeline.parallaxStrength(), 1e-6f);
        pipeline.setParallaxStrength(0.25f);
        assertEquals(0.25f, pipeline.parallaxStrength(), 1e-6f);
        pipeline.setParallaxStrength(10f);
        assertEquals(GroundParallaxPipeline.MAX_STRENGTH, pipeline.parallaxStrength(), 1e-6f);
    }

    @Test
    void sunSettingsClampToTheirDebugDialRanges() {
        GroundParallaxPipeline pipeline = new GroundParallaxPipeline();
        assertEquals(GroundParallaxPipeline.DEFAULT_SUN_SHADOW_STRENGTH,
                pipeline.sunShadowStrength(), 1e-6f);
        assertEquals(GroundParallaxPipeline.DEFAULT_SUN_AZIMUTH_DEGREES,
                pipeline.sunAzimuthDegrees(), 1e-6f);
        assertEquals(GroundParallaxPipeline.DEFAULT_SUN_ELEVATION_DEGREES,
                pipeline.sunElevationDegrees(), 1e-6f);

        pipeline.setSunShadowStrength(-1f);
        pipeline.setSunAzimuthDegrees(-30f);
        pipeline.setSunElevationDegrees(-5f);
        assertEquals(GroundParallaxPipeline.MIN_SUN_SHADOW_STRENGTH,
                pipeline.sunShadowStrength(), 1e-6f);
        assertEquals(GroundParallaxPipeline.MIN_SUN_AZIMUTH_DEGREES,
                pipeline.sunAzimuthDegrees(), 1e-6f);
        assertEquals(GroundParallaxPipeline.MIN_SUN_ELEVATION_DEGREES,
                pipeline.sunElevationDegrees(), 1e-6f);

        pipeline.setSunShadowStrength(99f);
        pipeline.setSunAzimuthDegrees(999f);
        pipeline.setSunElevationDegrees(999f);
        assertEquals(GroundParallaxPipeline.MAX_SUN_SHADOW_STRENGTH,
                pipeline.sunShadowStrength(), 1e-6f);
        assertEquals(GroundParallaxPipeline.MAX_SUN_AZIMUTH_DEGREES,
                pipeline.sunAzimuthDegrees(), 1e-6f);
        assertEquals(GroundParallaxPipeline.MAX_SUN_ELEVATION_DEGREES,
                pipeline.sunElevationDegrees(), 1e-6f);
    }

    @Test
    void independentSurfaceAndWaterSettingsClampToTheirDialRanges() {
        GroundParallaxPipeline pipeline = new GroundParallaxPipeline();
        assertEquals(GroundParallaxPipeline.DEFAULT_SURFACE_STRENGTH,
                pipeline.surfaceStrength(), 1e-6f);
        assertEquals(GroundParallaxPipeline.DEFAULT_WATER_WAVE_AMPLITUDE,
                pipeline.waterWaveAmplitude(), 1e-6f);

        pipeline.setSurfaceStrength(-1f);
        pipeline.setWaterWaveAmplitude(-1f);
        assertEquals(GroundParallaxPipeline.MIN_SURFACE_STRENGTH,
                pipeline.surfaceStrength(), 1e-6f);
        assertEquals(GroundParallaxPipeline.MIN_WATER_WAVE_AMPLITUDE,
                pipeline.waterWaveAmplitude(), 1e-6f);

        pipeline.setSurfaceStrength(99f);
        pipeline.setWaterWaveAmplitude(99f);
        assertEquals(GroundParallaxPipeline.MAX_SURFACE_STRENGTH,
                pipeline.surfaceStrength(), 1e-6f);
        assertEquals(GroundParallaxPipeline.MAX_WATER_WAVE_AMPLITUDE,
                pipeline.waterWaveAmplitude(), 1e-6f);
    }

    @Test
    void bumpLightingSettingClampsToItsDebugDialRange() {
        GroundParallaxPipeline pipeline = new GroundParallaxPipeline();
        assertEquals(GroundParallaxPipeline.DEFAULT_LIGHTING_STRENGTH,
                pipeline.lightingStrength(), 1e-6f);
        pipeline.setLightingStrength(-1f);
        assertEquals(GroundParallaxPipeline.MIN_LIGHTING_STRENGTH,
                pipeline.lightingStrength(), 1e-6f);
        pipeline.setLightingStrength(99f);
        assertEquals(GroundParallaxPipeline.MAX_LIGHTING_STRENGTH,
                pipeline.lightingStrength(), 1e-6f);
    }
}
