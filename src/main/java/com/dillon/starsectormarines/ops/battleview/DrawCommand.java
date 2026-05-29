package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * One unit of deferred world-render work, appended into a {@link DrawList} by a
 * collecting pass (a {@link RenderSystem}) and replayed in layer order by
 * {@link BattleRenderer#drainLayer}.
 *
 * <p>The command model deliberately is <em>not</em> "everything is a textured
 * quad": the renderer mixes batched sheet quads, single rotated sprites, solid
 * geometry, ribbon strips, and FBO/lightmap accumulators. This sealed hierarchy
 * carries the variants the migrated layers need so far; {@code SolidRect}/
 * {@code Ribbon}-style variants are added as the passes that need them migrate.
 */
public sealed interface DrawCommand
        permits DrawCommand.SheetQuad, DrawCommand.Sprite, DrawCommand.Custom {

    /**
     * A sub-rect of a named sprite sheet, batched through a per-sheet
     * {@link com.dillon.starsectormarines.render2d.QuadBatch}. This is the
     * many-quads-one-sheet form (tiles, doodads, roofs): the drain groups
     * consecutive {@code SheetQuad}s by {@link #sheet()} identity, appends each
     * to that sheet's batch, and flushes the touched batches under one
     * {@link com.dillon.starsectormarines.render2d.GlStateBracket}.
     *
     * <p>{@code src*} is the sub-rect in sheet-image pixels (top-down);
     * {@code cx/cy} + {@code w/h} are the screen-space destination center + size
     * (already resolved against camera zoom by the collecting system);
     * {@code r/g/b/a} are the per-quad tint + alpha. Axis-aligned only — no
     * rotation field yet (added when a sheet-based pass that rotates, e.g. UNITS,
     * migrates).
     */
    record SheetQuad(SpriteAPI sheet,
                     int srcX, int srcY, int srcW, int srcH,
                     float cx, float cy, float w, float h,
                     float r, float g, float b, float a) implements DrawCommand {}

    /**
     * A single whole-texture sprite, drawn via {@link SpriteAPI#renderAtCenter}.
     * For sprites that don't share a sheet (projectiles, shuttles, turrets,
     * drones), so there's no {@code QuadBatch} batching win — the drain just
     * sets size/angle/tint and renders each. {@code w}/{@code h} are screen-space
     * pixels; {@code angleDeg} is the rotation; {@code r/g/b/a} are tint + alpha.
     *
     * <p>The drain wraps a run of consecutive {@code Sprite}s in one
     * {@link com.dillon.starsectormarines.render2d.GlStateBracket} and resets the
     * sprite angle to 0 after each render so the cached {@link SpriteAPI} carries
     * no rotation into other passes.
     */
    record Sprite(SpriteAPI sprite,
                  float cx, float cy, float w, float h, float angleDeg,
                  float r, float g, float b, float a) implements DrawCommand {}

    /**
     * The escape hatch for passes that own their GL state — FBO blits, the
     * lightmap multiply, ribbon contrails, raw {@code GL_LINES} tracers. The
     * callback is responsible for its own {@code GlStateBracket} (or for being
     * faithful to the ambient state at its draw point); the drain just invokes it
     * in submission order, never touching GL around it.
     */
    record Custom(Runnable draw) implements DrawCommand {}
}
