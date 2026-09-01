package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

/**
 * Where a point in the ship lands on a room canvas's own surface.
 *
 * <p>The conversion every room view needs and none of them should own: a host
 * pass draws in Y-up host pixels stretched per axis, while canvas primitives are
 * Y-down surface units, so a mark placed with the camera's own numbers lands
 * mirrored and at the wrong scale. Shared rather than copied because the two
 * canvases that need it are marking the same ship through the same camera, and
 * two copies of this arithmetic are two chances for one of them to be subtly
 * wrong in a way only a screenshot would show.
 *
 * <p>Captured from the viewport the host handed back on the last draw, so it is
 * only meaningful for that frame's framing.
 */
record Projection(float surfaceHeight, float scaleX, float scaleY, BattleCamera camera) {

    static Projection forHost(ShipDeckBattleScene aboard,
                              ShipDeckBattleScene.RoomView view,
                              CanvasHostViewport viewport) {
        BattleCamera camera = aboard.cameraFor(
                view, 0f, 0f, viewport.width(), viewport.height());
        return new Projection(viewport.surfaceHeight(),
                1f / viewport.scaleX(), 1f / viewport.scaleY(), camera);
    }

    float x(float worldX) {
        return camera.cellToScreenX(worldX) * scaleX;
    }

    float y(float worldY) {
        return surfaceHeight - camera.cellToScreenY(worldY) * scaleY;
    }
}
