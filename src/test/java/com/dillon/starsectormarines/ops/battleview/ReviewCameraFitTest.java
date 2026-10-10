package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewCameraFitTest {
    @Test void depotOverviewFitsWithoutFractionalCellEdges() {
        var camera = BattleReviewFrameRenderer.cameraFor(1920, 1080, 160, 96);
        assertEquals(10f, camera.cellPxSize());
        assertTrue(camera.cellToScreenX(0) >= 0);
        assertTrue(camera.cellToScreenX(160) <= 1920);
        assertTrue(camera.cellToScreenY(0) >= 0);
        assertTrue(camera.cellToScreenY(96) <= 1050);
        assertEquals(camera.cellPxSize(), camera.cellToScreenX(1) - camera.cellToScreenX(0));
    }
    @Test void extremelySmallPreviewStillFitsItsEntireMap() {
        var camera = BattleReviewFrameRenderer.cameraFor(100, 80, 160, 96);
        assertTrue(camera.cellPxSize() > 0 && camera.cellPxSize() < 1);
    }
}
