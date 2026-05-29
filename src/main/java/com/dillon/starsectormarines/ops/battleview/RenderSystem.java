package com.dillon.starsectormarines.ops.battleview;

/**
 * A stateless world-render producer: reads services/camera/vision off the
 * per-frame {@link RenderContext} and appends {@link DrawCommand}s into the
 * {@link DrawList}. The render-side analog of a sim {@code System}.
 *
 * <p>Pull model — {@link #collect} runs fresh every frame (render is per-frame,
 * faster than the 30Hz sim, and interpolates), so systems recompute their draw
 * commands rather than caching. The producer lives here, <strong>not</strong> on
 * the domain object: {@code Unit}/{@code Doodad}/etc. stay pure data and never
 * import {@code SpriteAPI}.
 *
 * <p>Systems never touch GL — {@link BattleRenderer#drainLayer} owns batching
 * and the {@code GlStateBracket}s. A system holds only immutable refs it needs
 * to resolve sprites (e.g. {@code BattleSprites}); per-frame state comes in via
 * the {@link RenderContext}.
 */
public interface RenderSystem {

    /** Read the frame's {@link RenderContext} and append commands into {@code out}. */
    void collect(RenderContext ctx, DrawList out);
}
