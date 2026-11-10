package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaVoidLookTest {

    @Test
    void theMeltLightsTheRockMostJustAboveItAndLessHigherUp() {
        assertEquals(LavaVoidLook.WALL_GLOW, LavaVoidLook.wallGlow(0f), 1e-6f);
        assertEquals(LavaVoidLook.WALL_GLOW / 2f, LavaVoidLook.wallGlow(LavaVoidLook.WALL_GLOW_HALF_METERS), 1e-6f);
        assertTrue(LavaVoidLook.wallGlow(30f) < LavaVoidLook.wallGlow(10f));
    }

    @Test
    void theLipIsLitOnlyNearTheDropAndLessOverADeeperFloor() {
        assertTrue(LavaVoidLook.lipGlow(0f, 20f) > LavaVoidLook.lipGlow(2f, 20f));
        assertEquals(0f, LavaVoidLook.lipGlow(LavaVoidLook.LIP_GLOW_CELLS, 20f));
        assertTrue(LavaVoidLook.lipGlow(0f, 60f) < LavaVoidLook.lipGlow(0f, 20f));
    }

    @Test
    void theCrustBreaksUpAgainstTheWalls() {
        assertEquals(0f, LavaVoidLook.crustCover(0f));
        assertEquals(1f, LavaVoidLook.crustCover(LavaVoidLook.CRUST_FULL));
    }

    @Test
    void aShallowChannelRunsAndADeepRiftCrawls() {
        assertEquals(MoltenVoidRenderSystem.CRUST_SPEED, MoltenVoidRenderSystem.crustSpeed(40f));
        assertEquals(MoltenVoidRenderSystem.CHANNEL_CRUST_SPEED, MoltenVoidRenderSystem.crustSpeed(3f), 1e-6f);
        assertTrue(MoltenVoidRenderSystem.meltSpeed(10f) > MoltenVoidRenderSystem.meltSpeed(20f));
    }
}
