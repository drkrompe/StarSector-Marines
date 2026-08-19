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
        pipeline.setParallaxStrength(1f);
        assertEquals(1f, pipeline.parallaxStrength(), 1e-6f);
        pipeline.setParallaxStrength(10f);
        assertEquals(GroundParallaxPipeline.MAX_STRENGTH, pipeline.parallaxStrength(), 1e-6f);
    }
}
