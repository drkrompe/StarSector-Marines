package com.dillon.starsectormarines.render2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleCameraTest {

    @Test
    void wheelZoomReachesExtendedTacticalCloseUp() {
        BattleCamera camera = new BattleCamera(112, 64);
        camera.setViewport(0f, 0f, 1120f, 640f, 10f);

        camera.zoomAt(20f, 560f, 320f);

        assertEquals(8f, camera.zoom(), 1e-6f);
        assertEquals(80f, camera.cellPxSize(), 1e-6f);
    }

    @Test
    void extendedZoomStillKeepsCursorWorldPointAnchored() {
        BattleCamera camera = new BattleCamera(112, 64);
        camera.setViewport(0f, 0f, 1120f, 640f, 10f);
        float anchorX = 760f;
        float anchorY = 410f;
        float worldX = camera.screenToCellX(anchorX);
        float worldY = camera.screenToCellY(anchorY);

        camera.zoomAt(20f, anchorX, anchorY);

        assertEquals(worldX, camera.screenToCellX(anchorX), 1e-4f);
        assertEquals(worldY, camera.screenToCellY(anchorY), 1e-4f);
    }
}
