package com.dillon.starsectormarines.render2d;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;

/**
 * AutoCloseable wrapper around {@code glPushAttrib}/{@code glPopAttrib}
 * for the GL state textured-2D batching needs.
 *
 * <p>Starsector hands UI hooks a polluted GL state — most notably
 * {@code glColorMask} with alpha disabled, plus scissor and various
 * matrix-stack landmines (see the {@code gl_state_gotchas} memory note).
 * Calling {@link #textured2D()} pushes the relevant attribute bits,
 * forces a clean baseline, and restores the prior state on
 * {@link #close()}.
 *
 * <p>Pairs naturally with {@link QuadBatch#flush()} — one bracket can
 * span multiple flushes on different sheets, e.g.:
 * <pre>{@code
 * try (GlStateBracket gl = GlStateBracket.textured2D()) {
 *     urbanBatch.flush();
 *     floorsBatch.flush();
 *     waterBatch.flush();
 *     roadBatch.flush();
 * }
 * }</pre>
 *
 * <p>Saving {@code GL_TEXTURE_BIT} preserves {@code GL_TEXTURE_BINDING_2D}
 * (per the GL spec) so the caller's prior bound texture survives the
 * batched draws.
 */
public final class GlStateBracket implements AutoCloseable {

    private GlStateBracket() {}

    /**
     * Push + set state suitable for textured 2D quads with normal alpha
     * blending. Restores on {@link #close()}.
     */
    public static GlStateBracket textured2D() {
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_CURRENT_BIT);
        // Starsector leaves the alpha channel masked off in some UI paths
        // — force it on so per-vertex alpha actually reaches the framebuffer.
        glColorMask(true, true, true, true);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        return new GlStateBracket();
    }

    @Override
    public void close() {
        glPopAttrib();
    }
}
