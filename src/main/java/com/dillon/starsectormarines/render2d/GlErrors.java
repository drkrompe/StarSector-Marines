package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glGetError;

/**
 * Error-flag handling for the GL calls worth guarding.
 *
 * <p>{@code glGetError} returns the <em>oldest</em> flag latched since the
 * queue was last drained and clears only that one. A lone {@code glGetError}
 * after some call therefore does not report what that call did — it reports
 * whatever the driver latched first, possibly seconds earlier and in someone
 * else's code. Starsector runs a great deal of its own GL before handing a UI
 * hook the context, so an unguarded check in this mod will happily blame our
 * call for the game's error.
 *
 * <p>This was not hypothetical: a lone check reported
 * {@code GL error at glTexImage2D (ground parallax FBO): 0x502} for a
 * {@code glTexImage2D} that had in fact succeeded — the surrounding FBO
 * completed and sampled correctly, and the identical call on the two sibling
 * FBOs built in the same breath logged nothing, because the single stale flag
 * had already been consumed by the first check to run.
 *
 * <p>So guard a call as a pair — {@link #clear()} immediately before it,
 * {@link #check(String)} immediately after:
 *
 * <pre>{@code
 * GlErrors.clear();
 * glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
 * GlErrors.check("glTexImage2D (decal FBO color)");
 * }</pre>
 *
 * <p>Only then does the label name the call that actually raised the error.
 * Both ends drain in a bounded loop: a single call can raise several distinct
 * flags, and a lost or otherwise broken context can report an error forever,
 * which an unbounded {@code while} would turn into a frame-hanging spin.
 */
public final class GlErrors {

    private static final Logger LOG = Global.getLogger(GlErrors.class);

    /**
     * Cap on {@code glGetError} calls per drain. Above any plausible number of
     * genuine flags from one call, and low enough that a context stuck
     * reporting errors costs a bounded handful of calls rather than a hang.
     */
    private static final int MAX_DRAIN = 32;

    private GlErrors() {}

    /**
     * Discard every flag latched before the call about to be guarded, so a
     * following {@link #check(String)} speaks only for that call.
     */
    public static void clear() {
        for (int i = 0; i < MAX_DRAIN; i++) {
            if (glGetError() == GL_NO_ERROR) return;
        }
    }

    /**
     * Drain and log every flag latched since the preceding {@link #clear()},
     * attributing them to {@code label}. Logs nothing when the call was clean.
     */
    public static void check(String label) {
        for (int i = 0; i < MAX_DRAIN; i++) {
            int err = glGetError();
            if (err == GL_NO_ERROR) return;
            LOG.error("GL error at " + label + ": 0x" + Integer.toHexString(err));
        }
        LOG.error("GL error at " + label + ": still reporting errors after "
                + MAX_DRAIN + " drains -- context may be lost");
    }
}
