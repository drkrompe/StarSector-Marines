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

    @Test
    void northLookLeavesMechAndApcAboveTheActionHudAtProductionUi150Scale() {
        float width = 1744f / 1.5f, height = 938f / 1.5f;
        float reserve = BattleDirectControlOverlay.ACTION_HEIGHT + 12f;
        for (float radius : new float[]{.6f, .5f * (float) Math.hypot(2.4f, 1.4f)}) {
            BattleCamera camera = productionCamera(width, height);
            BattleActionCamera follow = new BattleActionCamera();
            camera.zoomAt(20, camera.vpX() + width * .5f, camera.vpY() + height * .5f);
            float pointerX = camera.vpX() + width * .5f;
            float pointerY = camera.vpY() + height - 1f;
            follow.follow(camera, 280f, 168f, radius, pointerX, pointerY, true, 2f, reserve);
            float bodyPixels = radius * camera.cellPxSize();
            float y = camera.cellToScreenY(168f);
            assertTrue(y - bodyPixels >= camera.vpY() + reserve + 24f - .001f,
                    "the north look must leave the whole chassis above the bottom HUD");
            assertTrue(y + bodyPixels <= camera.vpY() + height - 24f + .001f);
            assertEquals(pointerY, camera.cellToScreenY(camera.screenToCellY(pointerY)), .001f);
        }
    }

    @Test
    void reservedFramingRemainsFrameIndependentAndDoesNotLagBodyOrZoom() {
        BattleCamera a = productionCamera(1744f / 1.5f, 938f / 1.5f);
        BattleCamera b = productionCamera(1744f / 1.5f, 938f / 1.5f);
        BattleActionCamera fa = new BattleActionCamera(), fb = new BattleActionCamera();
        float reserve = BattleDirectControlOverlay.ACTION_HEIGHT + 12f;
        float pointerX = a.vpX() + a.vpW() * .8f;
        float pointerY = a.vpY() + a.vpH() - 1f;
        for (int i = 0; i < 30; i++) fa.follow(a, 280f, 168f, .6f, pointerX, pointerY, true, 1f / 30f, reserve);
        for (int i = 0; i < 120; i++) fb.follow(b, 280f, 168f, .6f, pointerX, pointerY, true, 1f / 120f, reserve);
        assertEquals(a.panCellX(), b.panCellX(), .0001f);
        assertEquals(a.panCellY(), b.panCellY(), .0001f);
        float screenX = a.cellToScreenX(280f), screenY = a.cellToScreenY(168f);
        a.zoomAt(20, pointerX, pointerY);
        fa.follow(a, 285f, 171f, .6f, pointerX, pointerY, true, 0f, reserve);
        assertEquals(screenX, a.cellToScreenX(285f), .001f);
        assertEquals(screenY, a.cellToScreenY(171f), .001f);
        float y = a.cellToScreenY(171f);
        assertTrue(y - .6f * a.cellPxSize() >= a.vpY() + reserve + 24f - .001f);
    }

    @Test
    void bottomWorldEdgeStillUsesMapClampSoHostCanChooseAnAlternateHudDock() {
        BattleCamera camera = productionCamera(1744f / 1.5f, 938f / 1.5f);
        BattleActionCamera follow = new BattleActionCamera();
        float reserve = BattleDirectControlOverlay.ACTION_HEIGHT + 12f;
        camera.zoomAt(20, camera.vpX() + camera.vpW() * .5f, camera.vpY() + camera.vpH() * .5f);
        follow.follow(camera, 280f, 1.4f, 1.4f,
                camera.vpX() + camera.vpW() * .5f, camera.vpY() + camera.vpH() - 1f,
                true, 2f, reserve);
        assertEquals(0f, camera.screenToCellY(camera.vpY()), .0001f,
                "HUD clearance must not expose space outside the map");
        assertTrue(camera.cellToScreenY(1.4f) < camera.vpY() + reserve,
                "the host must dock the HUD elsewhere when the map clamp prevents bottom clearance");
    }

    private static BattleCamera productionCamera(float width, float height) {
        BattleCamera camera = new BattleCamera(560, 336);
        camera.setViewport(37f, 29f, width, height, Math.max(width / 560f, height / 336f));
        camera.zoomAt(8, 37f + width * .5f, 29f + height * .5f);
        camera.centerOn(280f, 168f);
        return camera;
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(200, 200);
        camera.setViewport(100, 40, 1000, 600, 10);
        camera.zoomAt(8, 600, 340);
        camera.centerOn(100, 100);
        return camera;
    }
}
