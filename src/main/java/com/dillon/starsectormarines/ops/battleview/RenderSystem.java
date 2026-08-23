package com.dillon.starsectormarines.ops.battleview;

import java.util.function.BiConsumer;

/**
 * A stateless world-render producer: reads services/camera/vision off the
 * per-frame {@link RenderContext} and appends
 * {@link com.dillon.starsectormarines.render2d.DrawCommand}s into the
 * {@link DrawList}. The render-side analog of a sim {@code System}.
 *
 * <p>Pull model — {@link #collect} runs fresh every frame (render is per-frame,
 * faster than the 30Hz sim, and interpolates), so systems recompute their draw
 * commands rather than caching. The producer lives here, <strong>not</strong> on
 * the domain object: {@code Entity}/{@code Doodad}/etc. stay pure data and never
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

    /**
     * The {@link RenderLayer} this system feeds. Lets {@link BattleRenderer} hold
     * an ordered registry and drain a system's layer without re-stating the layer
     * at the call site (the system is the single source of truth for its layer).
     */
    RenderLayer layer();

    /**
     * Adapter for lightweight or renderer-owned producers that do not warrant a
     * dedicated class. They may emit ordinary commands through renderer helpers;
     * only passes with a genuine own-GL/FBO lifecycle use
     * {@link DrawList#addCustom}.
     *
     * @param layer the layer this pass feeds (also the layer it must emit into)
     * @param collect appends this frame's commands; runs once per frame, GL-free
     */
    static RenderSystem of(RenderLayer layer, BiConsumer<RenderContext, DrawList> collect) {
        return new RenderSystem() {
            @Override public void collect(RenderContext ctx, DrawList out) { collect.accept(ctx, out); }
            @Override public RenderLayer layer() { return layer; }
        };
    }
}
