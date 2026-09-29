package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.render2d.BattleCamera;

/** Bounded cursor lead for a followed body; all state is presentation-only. */
final class BattleActionCamera {
    private static final float DEAD_ZONE = .12f;
    private static final float MAX_LEAD = .22f;
    private static final float RESPONSE = 8f;
    private float leadX, leadY;

    void reset() { leadX = leadY = 0f; }

    void follow(BattleCamera camera, float unitX, float unitY, float bodyRadius,
                float pointerX, float pointerY, boolean worldPointer, float dt) {
        follow(camera, unitX, unitY, bodyRadius, pointerX, pointerY, worldPointer, dt, 0f);
    }

    /**
     * Frames the body in the band above bottom chrome without changing the world
     * viewport. At a map edge the ordinary pan clamp can prevent that framing;
     * the host then docks its chrome away from the final projected body.
     */
    void follow(BattleCamera camera, float unitX, float unitY, float bodyRadius,
                float pointerX, float pointerY, boolean worldPointer, float dt,
                float bottomReserve) {
        float cellSize = camera.cellPxSize();
        if (camera.vpW() <= 0f || camera.vpH() <= 0f || cellSize <= 0f) return;
        float bodyPadding = Math.max(0f, bodyRadius) * cellSize + 24f;
        // If the host is too short for both body and chrome, prioritize keeping
        // the body inside the viewport. The host's alternate dock remains needed.
        float reserve = Float.isFinite(bottomReserve) ? Math.max(0f, bottomReserve) : 0f;
        reserve = Math.min(reserve, Math.max(0f, camera.vpH() - 2f * bodyPadding));
        float span = Math.min(camera.vpW(), camera.vpH() - reserve);
        float maxLead = Math.min(MAX_LEAD, Math.max(0f,
                .5f - bodyPadding / span));
        float targetX = 0f, targetY = 0f;
        if (worldPointer && Float.isFinite(pointerX) && Float.isFinite(pointerY)
                && camera.containsScreen(pointerX, pointerY)) {
            float dx = (pointerX - camera.vpX() - camera.vpW() * .5f) / span;
            float dy = (pointerY - camera.vpY() - (camera.vpH() + reserve) * .5f) / span;
            float distance = (float) Math.hypot(dx, dy);
            if (distance > DEAD_ZONE) {
                // Solved in screen space: at rest this frames the midpoint of the
                // body-to-cursor distance beyond the dead zone. Feeding the
                // moving camera's world aim back here would introduce drift.
                float lead = Math.min(maxLead, distance - DEAD_ZONE);
                targetX = dx / distance * lead;
                targetY = dy / distance * lead;
            }
        }
        float blend = Float.isFinite(dt) && dt > 0f ? (float) -Math.expm1(-RESPONSE * dt) : 0f;
        leadX += (targetX - leadX) * blend;
        leadY += (targetY - leadY) * blend;
        // A closer zoom or smaller host may reduce the safe lead immediately.
        float length = (float) Math.hypot(leadX, leadY);
        if (length > maxLead) {
            leadX *= maxLead / length;
            leadY *= maxLead / length;
        }
        // Follow translation immediately; only cursor lead is eased. Normalized
        // lead preserves framing through wheel zoom and viewport/UI-scale changes.
        camera.centerOn(unitX + leadX * span / cellSize,
                unitY + (leadY * span - reserve * .5f) / cellSize);
    }
}
