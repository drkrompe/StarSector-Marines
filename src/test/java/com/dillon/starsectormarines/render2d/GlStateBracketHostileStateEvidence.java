package com.dillon.starsectormarines.render2d;

import com.dillon.starsectormarines.testsupport.HeadlessGl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_BLEND_DST;
import static org.lwjgl.opengl.GL11.GL_BLEND_SRC;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_ZERO;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glClearDepth;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGetBoolean;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glScissor;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * The state bracket, given the state it exists for.
 *
 * <p>{@code shaderEvidence} proves the shaders build and bind. It cannot prove
 * the brackets work, and for a specific reason: <b>a fresh context starts at GL
 * defaults</b> — alpha writes enabled, ordinary blending, scissor off, no
 * program bound — which are very nearly the values
 * {@link GlStateBracket#applyTextured2DState()} sets. So every normalisation in
 * that method is a no-op on a clean context, and deleting any one of them
 * leaves a clean-context test just as green while the game renders wrong.
 *
 * <p>This fixture supplies the missing half: it sets the state Starsector is
 * documented to hand its UI hooks — alpha masked out of the colour write mask,
 * a foreign blend function left behind by another draw — and then asks whether
 * our own draw still lands correctly, and whether the caller's state comes back
 * afterwards. Both directions matter and neither was covered.
 *
 * <p><b>What a fixture cannot do.</b> It encodes what we <em>believe</em>
 * arrives. It will catch a normalisation we drop, and it cannot discover a
 * hostile value nobody thought to write down here. That residue is the only
 * part of this that still wants a live pass.
 *
 * <p>Opt-in with the rest of the GL evidence: {@code gradlew.bat shaderEvidence}.
 */
@Tag("shader-evidence")
class GlStateBracketHostileStateEvidence {

    private static final int SURFACE = 32;

    /**
     * Background the quad is drawn over: blue, and <b>transparent</b>.
     *
     * <p>The alpha matters. A masked write cannot change what is already there,
     * so over an opaque backdrop the mask is invisible and the control proves
     * nothing. Starting at zero alpha means the masked write leaves zero and
     * the bracketed write does not, which is the whole discriminator.
     */
    private static final float[] BACKDROP = {0f, 0f, 1f, 0f};
    /** Half-transparent red. Its alpha is the thing an alpha-masked write throws away. */
    private static final float[] QUAD = {1f, 0f, 0f, 0.5f};

    /**
     * The state the game is documented to hand a UI hook, as far as this
     * bracket is concerned: alpha masked out of the write mask, and a foreign
     * blend function ({@code ONE, ZERO} — a straight replace) left behind by
     * somebody else's draw.
     */
    private static void applyHostileState() {
        glColorMask(true, true, true, false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ZERO);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
    }

    @Test
    void aHostileColourMaskAndBlendFuncDoNotSurviveIntoOurDraw() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[hostile-gl] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            setUpOrtho();

            // Control: the same draw with the hostile state left standing, so
            // the assertions below are known to be measuring the bracket
            // rather than measuring nothing.
            clearTo(BACKDROP);
            applyHostileState();
            drawQuad();
            int[] unbracketed = readCentrePixel();

            clearTo(BACKDROP);
            applyHostileState();
            try (GlStateBracket ignored = GlStateBracket.textured2D()) {
                assertEquals(GL_SRC_ALPHA, glGetInteger(GL_BLEND_SRC),
                        "the bracket must replace the foreign blend source");
                assertEquals(GL_ONE_MINUS_SRC_ALPHA, glGetInteger(GL_BLEND_DST),
                        "the bracket must replace the foreign blend destination");
                drawQuad();
            }
            int[] bracketed = readCentrePixel();

            System.out.printf("[hostile-gl] centre pixel: hostile rgba=%s, bracketed rgba=%s%n",
                    java.util.Arrays.toString(unbracketed), java.util.Arrays.toString(bracketed));

            // The control has to be genuinely damaged or the bracketed
            // assertions below are measuring nothing.
            assertEquals(0, unbracketed[3],
                    "a masked alpha channel must leave the framebuffer's alpha untouched");
            assertTrue(unbracketed[0] > 240,
                    "and a replacing blend func must put pure red down: got " + unbracketed[0]);
            assertTrue(unbracketed[2] < 8,
                    "wiping the blue backdrop entirely: got " + unbracketed[2]);

            assertTrue(bracketed[3] > 32,
                    "the bracket must restore alpha writes, or per-vertex alpha never"
                            + " reaches the framebuffer: got " + bracketed[3]);
            assertTrue(bracketed[0] > 100 && bracketed[0] < 160,
                    "half-alpha red over blue should land near half red: got " + bracketed[0]);
            assertTrue(bracketed[2] > 100 && bracketed[2] < 160,
                    "and keep half the blue backdrop: got " + bracketed[2]);
        }
    }

    @Test
    void theCallersStateComesBackWhenTheBracketCloses() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[hostile-gl] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            setUpOrtho();
            applyHostileState();
            State before = State.capture();

            try (GlStateBracket ignored = GlStateBracket.textured2D()) {
                drawQuad();
            }

            assertEquals(before, State.capture(),
                    "the bracket must hand the caller's state back exactly as it found it;"
                            + " leaking into the game is the other half of the contract");
        }
    }

    /**
     * Scissor is inherited on purpose, and this pins that it stays that way.
     *
     * <p>The bracket normalises the colour write mask and the blend function
     * but leaves the scissor test alone, and that asymmetry is correct rather
     * than an omission: a tile pass draws <em>into the game's own target</em>,
     * inside the panel rect Starsector has already clipped to, and wants to
     * stay inside it. Turning scissor off here would let terrain paint over the
     * surrounding UI.
     *
     * <p>The FBO paths do the opposite — {@code GroundParallaxPipeline}'s FBO
     * bracket disables scissor — because there the target is ours and the UI's
     * clip rect refers to a different surface entirely. Same reasoning, opposite
     * answer; neither is a default worth inheriting silently.
     */
    @Test
    void scissorIsDeliberatelyInheritedRatherThanNormalisedAway() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[hostile-gl] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            setUpOrtho();
            clearTo(BACKDROP);
            glEnable(GL_SCISSOR_TEST);
            glScissor(0, 0, SURFACE / 2, SURFACE);

            try (GlStateBracket ignored = GlStateBracket.textured2D()) {
                assertTrue(glIsEnabled(GL_SCISSOR_TEST),
                        "the bracket must not silently drop the caller's clip");
                drawQuad();
            }
            glDisable(GL_SCISSOR_TEST);

            int[] inside = readPixel(SURFACE / 4, SURFACE / 2);
            int[] outside = readPixel(SURFACE * 3 / 4, SURFACE / 2);
            assertTrue(inside[0] > 100, "inside the clip the quad drew: got " + inside[0]);
            assertTrue(outside[0] < 8,
                    "outside it the backdrop survived, which is the panel edge holding: got "
                            + outside[0]);
        }
    }

    // ------------------------------------------------------------------------

    /** The attributes {@link GlStateBracket#textured2D()} pushes, read back. */
    private record State(boolean red, boolean green, boolean blue, boolean alpha,
                         int blendSrc, int blendDst,
                         boolean blend, boolean texture2d, boolean depth) {

        static State capture() {
            // LWJGL validates the buffer against the largest reply any glGetBoolean
            // enum can produce, not against this one's four components.
            ByteBuffer mask = BufferUtils.createByteBuffer(16);
            glGetBoolean(GL_COLOR_WRITEMASK, mask);
            return new State(mask.get(0) != 0, mask.get(1) != 0,
                    mask.get(2) != 0, mask.get(3) != 0,
                    glGetInteger(GL_BLEND_SRC), glGetInteger(GL_BLEND_DST),
                    glIsEnabled(GL_BLEND), glIsEnabled(GL_TEXTURE_2D), glIsEnabled(GL_DEPTH_TEST));
        }
    }

    private static void setUpOrtho() {
        glViewport(0, 0, SURFACE, SURFACE);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE, 0, SURFACE, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    /**
     * Clears with the write mask forced open, so the backdrop is the backdrop
     * whatever was set.
     *
     * <p>Depth goes too. The surface has a depth buffer and the hostile state
     * enables the depth test; against an uncleared buffer the control quad is
     * simply rejected, which looks exactly like a bracket that worked.
     */
    private static void clearTo(float[] rgba) {
        glColorMask(true, true, true, true);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        glClearDepth(1.0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /** A full-surface quad. Untextured on purpose: this is about state, not sampling. */
    private static void drawQuad() {
        glDisable(GL_TEXTURE_2D);
        glColor4f(QUAD[0], QUAD[1], QUAD[2], QUAD[3]);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(SURFACE, 0);
        glVertex2f(SURFACE, SURFACE);
        glVertex2f(0, SURFACE);
        glEnd();
    }

    private static int[] readCentrePixel() {
        return readPixel(SURFACE / 2, SURFACE / 2);
    }

    private static int[] readPixel(int x, int y) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        return new int[]{pixel.get(0) & 0xFF, pixel.get(1) & 0xFF,
                pixel.get(2) & 0xFF, pixel.get(3) & 0xFF};
    }
}
