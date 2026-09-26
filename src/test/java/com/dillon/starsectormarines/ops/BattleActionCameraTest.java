package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleActionCameraTest {
    @Test
    void nearCursorKeepsBodyCenteredAndFarCursorSettlesWithoutFeedbackDrift() {
        BattleCamera camera = camera();
        BattleActionCamera follow = new BattleActionCamera();
        follow.follow(camera, 100, 100, 0f, 640, 340, true, 1f);
        assertEquals(100f, camera.panCellX());
        follow.follow(camera, 100, 100, 0f, 900, 340, true, .05f);
        assertTrue(camera.panCellX() > 100f);
        assertTrue(camera.panCellX() < 100f + 132f / camera.cellPxSize(), "lead eases rather than snapping to its cap");
        for (int i = 0; i < 240; i++) follow.follow(camera, 100, 100, 0f, 900, 340, true, 1f / 60f);
        float settled = camera.panCellX();
        for (int i = 0; i < 240; i++) follow.follow(camera, 100, 100, 0f, 900, 340, true, 1f / 60f);
        assertEquals(settled, camera.panCellX(), .00001f);
        assertEquals(100f + 132f / camera.cellPxSize(), settled, .0001f, "lead is capped to 22% of the short viewport axis");
    }

    @Test
    void framingBetweenBodyAndCursorUsesTheDeadZoneWithoutMovingTheBody() {
        BattleCamera camera = camera();
        BattleActionCamera follow = new BattleActionCamera();
        for (int i = 0; i < 120; i++) follow.follow(camera, 100, 100, 0f, 750, 340, true, 1f / 60f);
        // 150px cursor offset minus 72px dead zone gives 78px camera lead.
        assertEquals(100f + 78f / camera.cellPxSize(), camera.panCellX(), .0001f);
        assertTrue(camera.cellToScreenX(100) < 600);
        assertTrue(camera.panCellX() < camera.screenToCellX(750));
    }

    @Test
    void viewportChromeAndOutsidePointerEaseBackToUnitAndResetDropsOldLead() {
        BattleCamera camera = camera();
        BattleActionCamera follow = new BattleActionCamera();
        follow.follow(camera, 100, 100, 0f, 900, 340, true, 2f);
        float led = camera.panCellX();
        follow.follow(camera, 100, 100, 0f, 900, 340, false, .05f);
        assertTrue(camera.panCellX() < led && camera.panCellX() > 100);
        follow.follow(camera, 100, 100, 0f, 5000, 340, true, 2f);
        assertEquals(100f, camera.panCellX(), .0001f);
        follow.follow(camera, 100, 100, 0f, 900, 340, true, 2f);
        follow.reset();
        follow.follow(camera, 110, 105, 0f, 900, 340, true, 0f);
        assertEquals(110f, camera.panCellX());
        assertEquals(105f, camera.panCellY());
    }

    @Test
    void smoothingUsesRealTimeAndFollowsTranslationWithoutLag() {
        BattleCamera a = camera(), b = camera();
        BattleActionCamera fa = new BattleActionCamera(), fb = new BattleActionCamera();
        for (int i = 0; i < 30; i++) fa.follow(a, 100, 100, 0f, 900, 500, true, 1f / 30f);
        for (int i = 0; i < 120; i++) fb.follow(b, 100, 100, 0f, 900, 500, true, 1f / 120f);
        assertEquals(a.panCellX(), b.panCellX(), .0001f);
        assertEquals(a.panCellY(), b.panCellY(), .0001f);
        float oldX = a.panCellX(), oldY = a.panCellY();
        fa.follow(a, 105, 103, 0f, 900, 500, true, 0f);
        assertEquals(oldX + 5f, a.panCellX(), .0001f);
        assertEquals(oldY + 3f, a.panCellY(), .0001f);
    }

    @Test
    void closeZoomResizeAndMapEdgesKeepUnitVisibleAndProjectionConsistent() {
        BattleCamera camera = camera();
        BattleActionCamera follow = new BattleActionCamera();
        follow.follow(camera, 100, 100, 0f, 1090, 630, true, 2f);
        float unitScreenX = camera.cellToScreenX(100), unitScreenY = camera.cellToScreenY(100);
        camera.zoomAt(20, 1090, 630);
        follow.follow(camera, 100, 100, 0f, 1090, 630, true, 0f);
        assertEquals(16f, camera.zoom());
        assertEquals(unitScreenX, camera.cellToScreenX(100), .001f);
        assertEquals(unitScreenY, camera.cellToScreenY(100), .001f);
        assertEquals(1090f, camera.cellToScreenX(camera.screenToCellX(1090)), .001f);
        camera.setViewport(70, 80, 500, 900, 10);
        follow.follow(camera, 100, 100, 0f, 560, 970, true, 2f);
        assertTrue(camera.containsScreen(camera.cellToScreenX(100), camera.cellToScreenY(100)));
        for (float edge : new float[]{1f, 199f}) {
            follow.follow(camera, edge, edge, 0f, 560, 970, true, 2f);
            assertTrue(camera.containsScreen(camera.cellToScreenX(edge), camera.cellToScreenY(edge)));
            assertTrue(camera.screenToCellX(camera.vpX()) >= 0f);
            assertTrue(camera.screenToCellY(camera.vpY()) >= 0f);
        }
    }

    @Test
    void zoomingCloseToALargeBodyImmediatelyReducesExistingLead() {
        BattleCamera camera = camera();
        BattleActionCamera follow = new BattleActionCamera();
        follow.follow(camera, 100, 100, 1.5f, 900, 620, true, 2f);
        camera.zoomAt(20, 900, 620);
        follow.follow(camera, 100, 100, 1.5f, 900, 620, true, 0f);
        float x = camera.cellToScreenX(100), y = camera.cellToScreenY(100);
        float radius = 1.5f * camera.cellPxSize();
        assertTrue(x - radius >= camera.vpX() + 24f - .001f);
        assertTrue(y - radius >= camera.vpY() + 24f - .001f);
        assertTrue(x + radius <= camera.vpX() + camera.vpW() - 24f + .001f);
        assertTrue(y + radius <= camera.vpY() + camera.vpH() - 24f + .001f);
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(200, 200);
        camera.setViewport(100, 40, 1000, 600, 10);
        camera.zoomAt(8, 600, 340);
        camera.centerOn(100, 100);
        return camera;
    }
}
