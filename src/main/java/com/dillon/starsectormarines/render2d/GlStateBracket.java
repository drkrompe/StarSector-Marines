package com.dillon.starsectormarines.render2d;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_DST_COLOR;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.GL_ZERO;
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
 * <p>Starsector hands UI hooks a context in whatever state its own rendering
 * left it, and a co-loaded mod's draw may have moved it again. {@link #textured2D()}
 * pushes the relevant attribute bits, forces the baseline below, and restores
 * what the caller had on {@link #close()} — so the bracket both defends our
 * draw from what arrives and keeps our own settings out of the game's.
 *
 * <h2>Exactly what it normalises</h2>
 * <ul>
 *   <li>{@code glColorMask} to all four channels. Some UI paths arrive with
 *       alpha masked off, and per-vertex alpha then never reaches the
 *       framebuffer.</li>
 *   <li>{@code GL_TEXTURE_2D} and {@code GL_BLEND} on, with the ordinary
 *       {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA} function — a foreign blend
 *       func left behind by another draw renders transparent texels opaque.</li>
 *   <li>{@code GL_DEPTH_TEST} off.</li>
 * </ul>
 *
 * <h2>What it deliberately leaves alone</h2>
 * <p><b>The scissor test.</b> A batched tile pass draws into the game's own
 * target, inside the panel rect Starsector has already clipped to, and wants to
 * stay inside it; turning scissor off here would let terrain paint over the
 * surrounding UI. The FBO paths do the opposite — {@code GroundParallaxPipeline}
 * disables scissor inside its own bracket — because there the target is ours
 * and the UI's clip rect refers to a different surface entirely. Same
 * reasoning, opposite answer.
 *
 * <p><b>The matrix stacks.</b> This bracket pushes no matrices; a caller that
 * needs its own projection pushes and pops them itself, as the FBO paths do.
 *
 * <p>{@code GlStateBracketHostileStateEvidence} holds both halves of that
 * contract against a real driver, with the incoming state set hostile — a
 * clean context cannot tell whether any of this normalisation is still here,
 * because the values above are very nearly the GL defaults.
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
        applyTextured2DState();
        return new GlStateBracket();
    }

    /**
     * Forces the clean textured-2D-with-normal-alpha baseline <em>without</em>
     * pushing/popping. Used by {@link textured2D()} after its push, and by the
     * drain to <strong>re-assert</strong> this state mid-bracket after a foreign
     * draw (a {@code SPRITE}'s {@code SpriteAPI.renderAtCenter}) has mutated the
     * blend func / colorMask — otherwise a subsequent batched
     * {@link QuadBatch#flush()} in the same bracket would inherit the foreign blend
     * state and render transparent texels opaque (black). Cheap (a handful of GL
     * enables); no attrib stack traffic.
     */
    public static void applyTextured2DState() {
        // Starsector leaves the alpha channel masked off in some UI paths
        // — force it on so per-vertex alpha actually reaches the framebuffer.
        glColorMask(true, true, true, true);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
    }

    /**
     * Push + set additive blending suitable for emitting radial light
     * kernels into a lightmap. Source alpha (the kernel's falloff) gates
     * the contribution so overlapping lights stack toward white rather
     * than averaging.
     */
    public static GlStateBracket additiveBlend() {
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_CURRENT_BIT);
        glColorMask(true, true, true, true);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glDisable(GL_DEPTH_TEST);
        return new GlStateBracket();
    }

    /**
     * Push + set multiply blending — used to composite a lightmap texture
     * over a previously-rendered scene. Result: {@code scene * lightmap}.
     * Ambient values ≤1 darken the scene; light kernels that pushed the
     * lightmap above 1 brighten regions back toward (or past) white.
     */
    public static GlStateBracket multiplicativeLighting() {
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_CURRENT_BIT);
        glColorMask(true, true, true, true);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_DST_COLOR, GL_ZERO);
        glDisable(GL_DEPTH_TEST);
        return new GlStateBracket();
    }

    @Override
    public void close() {
        glPopAttrib();
    }
}
