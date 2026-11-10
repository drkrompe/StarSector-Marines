package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;

/**
 * Periodic pass that decides which buildings reveal their interiors to the
 * player. Driven by {@link FogOfWarService} at roughly 10 Hz (every 3rd 30 Hz
 * tick) — the render path lerps {@code currentAlpha → targetAlpha} per frame so
 * the cadence stutter doesn't pop visibly.
 *
 * <p><strong>Open rule</strong> (owner: the 3D look was lost wherever the player was fighting, because seeing into a
 * building opened it): a building opens (roof alpha → 0, near walls down to the knee) only while one of the player's
 * own units stands on its floor ({@link #opens}). Seeing in through a door or window no longer opens it; the units the
 * player can see inside a closed building are marked over its roof by the renderer, and the fog still decides who
 * those are. The pointer can peek into one building at a time ({@link #advanceAlpha(Buildings, float, int)}), which
 * is presentation alone and shows only what the fog lets the player see.
 *
 * <p><strong>Fog rule:</strong> a closed roof lies in fog ({@link Building#targetFog} 1) unless a cell
 * under it is revealed: an interior cell, or one of the walls it is laid over, the cells beside the
 * floor. A roof is drawn above the fog, so it carries its own darkening; without this a building
 * nobody can see showed its roof at full brightness.
 */
public final class BuildingVisibilityPass {

    /** Per-frame roof fade rate (1/sec): {@code dt × this} is the {@link #advanceAlpha} lerp fraction. */
    private static final float ALPHA_FADE_RATE = 3f;

    private BuildingVisibilityPass() {}

    /**
     * Sets each building's roof target from who stands in it and its fog from what the player sees.
     *
     * @param occupants  cells ({@code x + y * gridWidth}) the player's own units stand on
     * @param occupantCount how many of {@code occupants} are filled
     */
    public static void update(Buildings buildings, boolean[] cellRevealed, int gridWidth, int gridHeight,
                              int[] occupants, int occupantCount) {
        if (buildings == null || buildings.isEmpty() || cellRevealed == null) return;

        for (Building b : buildings.all()) {
            boolean seenInside = anyInteriorCellRevealed(b, cellRevealed, gridWidth, gridHeight);
            b.targetAlpha = opens(b, occupants, occupantCount, gridWidth) ? 0f : 1f;
            b.targetFog = seenInside || anyWallCellRevealed(b, cellRevealed, gridWidth, gridHeight) ? 0f : 1f;
        }
    }

    /** Whether one of the player's units stands on this building's floor: the only thing that opens it. */
    static boolean opens(Building b, int[] occupants, int occupantCount, int gridWidth) {
        for (int k = 0; k < occupantCount; k++) {
            int x = occupants[k] % gridWidth, y = occupants[k] / gridWidth;
            if (x < b.minX || x > b.maxX || y < b.minY || y > b.maxY) continue;
            for (int i = 0, n = b.cellCount(); i < n; i++) {
                if (b.cellsX[i] == x && b.cellsY[i] == y) return true;
            }
        }
        return false;
    }

    /**
     * Animate each building's {@link Building#currentAlpha} toward the {@link Building#targetAlpha}
     * the reveal {@link #update} last wrote, on real frame {@code dt} — decoupled from the ~10 Hz
     * reveal cadence so the fade reads smooth. The render path reads {@code currentAlpha}, so
     * <em>every</em> render host must call this once per frame; a host that skips it leaves roofs
     * frozen at their initial opaque {@code currentAlpha} and interiors never reveal. No-op on a
     * null/empty registry.
     */
    public static void advanceAlpha(Buildings buildings, float dt) {
        advanceAlpha(buildings, dt, 0);
    }

    /**
     * {@link #advanceAlpha(Buildings, float)}, with the building under the pointer ({@code peekId}, 0 for none) opened
     * as if a unit stood in it. A peek is presentation alone: it shows only what the fog lets the player see.
     */
    public static void advanceAlpha(Buildings buildings, float dt, int peekId) {
        if (buildings == null || buildings.isEmpty()) return;
        float lerpAmount = Math.min(1f, dt * ALPHA_FADE_RATE);
        for (Building b : buildings.all()) {
            float target = peekId != 0 && b.id == peekId ? 0f : b.targetAlpha;
            b.currentAlpha += (target - b.currentAlpha) * lerpAmount;
            b.currentFog += (b.targetFog - b.currentFog) * lerpAmount;
        }
    }

    private static boolean anyInteriorCellRevealed(Building b, boolean[] cellRevealed,
                                                   int gridWidth, int gridHeight) {
        for (int i = 0, n = b.cellCount(); i < n; i++) {
            int cx = b.cellsX[i];
            int cy = b.cellsY[i];
            if (cx < 0 || cx >= gridWidth || cy < 0 || cy >= gridHeight) continue;
            if (cellRevealed[cy * gridWidth + cx]) return true;
        }
        return false;
    }

    /** Whether a cell beside the floor (a wall the roof is laid over) is revealed. */
    private static boolean anyWallCellRevealed(Building b, boolean[] cellRevealed,
                                               int gridWidth, int gridHeight) {
        for (int i = 0, n = b.cellCount(); i < n; i++) {
            int cx = b.cellsX[i];
            int cy = b.cellsY[i];
            for (int dy = -1; dy <= 1; dy++) {
                int y = cy + dy;
                if (y < 0 || y >= gridHeight) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    int x = cx + dx;
                    if (x < 0 || x >= gridWidth) continue;
                    if (cellRevealed[y * gridWidth + x]) return true;
                }
            }
        }
        return false;
    }
}
