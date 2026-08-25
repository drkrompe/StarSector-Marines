package com.dillon.starsectormarines.render2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void zoomOneVisibleCellsCoverTheWholeMap() {
        BattleCamera camera = fittedCamera();

        VisibleCellRect view = camera.visibleCells();

        assertEquals(0, view.minX());
        assertEquals(0, view.minY());
        assertEquals(111, view.maxX());
        assertEquals(63, view.maxY());
        assertEquals(112, view.width());
        assertEquals(64, view.height());
    }

    @Test
    void zoomedInVisibleCellsAreASliceAroundPan() {
        BattleCamera camera = fittedCamera();
        camera.zoomAt(20f, 560f, 320f);

        VisibleCellRect view = camera.visibleCells();

        assertTrue(view.width() < 30, "zoomed view should be a small slice, was " + view.width());
        assertTrue(view.height() < 20, "zoomed view should be a small slice, was " + view.height());
        assertTrue(view.contains((int) camera.panCellX(), (int) camera.panCellY()));
        assertFalse(view.contains(0, 0));
        assertFalse(view.contains(111, 63));
    }

    @Test
    void visibleCellsMarginExpandsAndClampsToWorld() {
        BattleCamera camera = fittedCamera();
        camera.zoomAt(20f, 560f, 320f);
        VisibleCellRect tight = camera.visibleCells(0);
        VisibleCellRect padded = camera.visibleCells(2);

        assertEquals(tight.minX() - 2, padded.minX());
        assertEquals(tight.maxX() + 2, padded.maxX());
        assertEquals(tight.minY() - 2, padded.minY());
        assertEquals(tight.maxY() + 2, padded.maxY());

        VisibleCellRect huge = camera.visibleCells(10_000);
        assertEquals(0, huge.minX());
        assertEquals(0, huge.minY());
        assertEquals(111, huge.maxX());
        assertEquals(63, huge.maxY());
    }

    @Test
    void zeroViewportYieldsEmptyVisibleCells() {
        BattleCamera camera = new BattleCamera(112, 64);
        camera.setViewport(0f, 0f, 0f, 0f, 10f);

        assertTrue(camera.visibleCells().isEmpty());
    }

    @Test
    void centerOnProvidesAStableProgrammaticCameraTarget() {
        BattleCamera camera = fittedCamera();
        camera.zoomAt(4f, 560f, 320f);

        camera.centerOn(72.5f, 20.5f);

        assertEquals(72.5f, camera.panCellX(), 1e-5f);
        assertEquals(20.5f, camera.panCellY(), 1e-5f);
        assertEquals(560f, camera.cellToScreenX(72.5f), 1e-5f);
        assertEquals(320f, camera.cellToScreenY(20.5f), 1e-5f);
    }

    private static BattleCamera fittedCamera() {
        BattleCamera camera = new BattleCamera(112, 64);
        camera.setViewport(0f, 0f, 1120f, 640f, 10f);
        return camera;
    }
}
