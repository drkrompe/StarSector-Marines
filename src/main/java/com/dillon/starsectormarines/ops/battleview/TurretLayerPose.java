package com.dillon.starsectormarines.ops.battleview;

/**
 * Backend-neutral placement shared by the ground and shuttle turret renderers.
 * The base layer stays at the authored mount center while the recoil layer
 * slides opposite the barrel's facing during the recoil window. Sprite aspect,
 * draw order, render layer, and backend objects remain renderer concerns.
 */
public record TurretLayerPose(
        float baseCenterX,
        float baseCenterY,
        float recoilCenterX,
        float recoilCenterY,
        float spriteHeightPx,
        float facingDegrees) {

    /**
     * Resolves one two-layer turret pose using the existing linear recoil ease.
     * Callers pass their already-transformed center and visual scale explicitly:
     * ground turrets use their ordinary visual-cell size, while shuttle mounts
     * include altitude scaling before calling this method.
     */
    public static TurretLayerPose resolve(
            float centerX, float centerY, float facingDegrees,
            float visualCells, float cellPx, float recoilTimer,
            float recoilDuration, float recoilDistanceFraction) {
        float spriteHeightPx = visualCells * cellPx;
        float recoilT = 0f;
        if (recoilTimer < recoilDuration) {
            recoilT = 1f - recoilTimer / recoilDuration;
        }
        float pushPx = recoilT * recoilDistanceFraction * spriteHeightPx;
        double radians = Math.toRadians(facingDegrees);
        float recoilX = centerX + (float) Math.sin(radians) * pushPx;
        float recoilY = centerY - (float) Math.cos(radians) * pushPx;
        return new TurretLayerPose(centerX, centerY, recoilX, recoilY,
                spriteHeightPx, facingDegrees);
    }
}
