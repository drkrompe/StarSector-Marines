package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;

/**
 * Periodic pass that decides which buildings reveal their interiors to the
 * player. Driven by {@link VisionService} at roughly 10 Hz (every 3rd 30 Hz
 * tick) — the render path lerps {@code currentAlpha → targetAlpha} per frame so
 * the cadence stutter doesn't pop visibly.
 *
 * <p><strong>Reveal rule:</strong> a building reveals (roof alpha → 0) iff any of
 * its <em>interior</em> cells is currently revealed in the fog-of-war bitmap
 * ({@link VisionService#cellRevealedArray()}) — the same per-cell shadowcast
 * visibility that gates unit rendering. A building's {@link Building#cellsX}/
 * {@link Building#cellsY} are interior floor cells only (the flood-fill seed
 * set, walls excluded), so a cell is revealed exactly when a contributor can
 * actually see into the room — through a door, a breach, or from inside.
 *
 * <p>This replaced an earlier closest-contributor + 5-perimeter-sample raycast
 * that <em>under</em>-revealed: it tested only the single nearest unit (a
 * farther unit with a clear shot into the room never counted) and only five
 * sample points (a sightline to a non-sampled interior cell was missed), so the
 * roof could stay opaque while a unit was demonstrably shooting in. Reading the
 * fog bitmap closes that gap — it's the player's true vision, every contributor
 * and every visible cell — and is cheaper (an array lookup per interior cell, no
 * raycasts). Note: air vision sources ({@code airLosRadius} shuttles/fighters)
 * also populate the bitmap, so a craft overhead can briefly reveal a roof — an
 * intended consequence of "if the player can see in, the roof opens."
 */
public final class BuildingVisibilityPass {

    /** Per-frame roof fade rate (1/sec): {@code dt × this} is the {@link #advanceAlpha} lerp fraction. */
    private static final float ALPHA_FADE_RATE = 3f;

    private BuildingVisibilityPass() {}

    public static void update(Buildings buildings, boolean[] cellRevealed,
                              int gridWidth, int gridHeight) {
        if (buildings == null || buildings.isEmpty() || cellRevealed == null) return;

        for (Building b : buildings.all()) {
            b.targetAlpha = anyInteriorCellRevealed(b, cellRevealed, gridWidth, gridHeight) ? 0f : 1f;
        }
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
        if (buildings == null || buildings.isEmpty()) return;
        float lerpAmount = Math.min(1f, dt * ALPHA_FADE_RATE);
        for (Building b : buildings.all()) {
            b.currentAlpha += (b.targetAlpha - b.currentAlpha) * lerpAmount;
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
}
