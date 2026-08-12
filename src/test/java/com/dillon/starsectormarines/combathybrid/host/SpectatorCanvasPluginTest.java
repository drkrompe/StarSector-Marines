package com.dillon.starsectormarines.combathybrid.host;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectatorCanvasPluginTest {

    @Test
    void scrollCanReachGroundScaleCloseUp() {
        float visibleWidth = 1_200f;
        for (int i = 0; i < 40; i++) {
            visibleWidth = SpectatorCanvasPlugin.zoomedVisibleWidth(
                    visibleWidth, 1f);
        }

        assertEquals(120f, visibleWidth, 1e-6f);
    }

    @Test
    void zoomOutRetainsExistingWorldScaleCeiling() {
        float visibleWidth = 1_200f;
        for (int i = 0; i < 80; i++) {
            visibleWidth = SpectatorCanvasPlugin.zoomedVisibleWidth(
                    visibleWidth, -1f);
        }

        assertEquals(40_000f, visibleWidth, 1e-6f);
    }
}
