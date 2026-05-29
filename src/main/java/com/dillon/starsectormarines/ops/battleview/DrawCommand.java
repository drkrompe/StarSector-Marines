package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * One unit of deferred world-render work, appended into a {@link DrawList} by a
 * collecting pass and replayed in layer order by {@link BattleRenderer#drainLayer}.
 *
 * <p>The command model deliberately is <em>not</em> "everything is a textured
 * quad": the renderer mixes batched sprites, solid geometry, ribbon strips, and
 * FBO/lightmap accumulators. This sealed hierarchy starts with the two variants
 * the {@code SHOTS} layer needs (Story C); {@code SolidRect}/{@code Ribbon}-style
 * variants are added as the passes that need them migrate (stories D…N).
 */
public sealed interface DrawCommand permits DrawCommand.SpriteQuad, DrawCommand.Custom {

    /**
     * A single rotated sprite, drawn via {@link SpriteAPI#renderAtCenter}. Carries
     * a whole-texture sprite (projectiles, shuttles, turrets, drones) rather than a
     * sheet + sub-rect — those single sprites don't share a sheet, so there's no
     * batching win in routing them through {@code QuadBatch}. {@code w}/{@code h}
     * are screen-space pixels (already resolved against camera zoom by the
     * collecting pass); {@code angleDeg} is the sprite rotation; {@code r/g/b/a}
     * are the tint and alpha.
     *
     * <p>The drain wraps a run of consecutive {@code SpriteQuad}s in one
     * {@link com.dillon.starsectormarines.render2d.GlStateBracket} and resets the
     * sprite angle to 0 after each render so the cached {@link SpriteAPI} carries
     * no rotation into other passes.
     */
    record SpriteQuad(SpriteAPI sprite,
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
