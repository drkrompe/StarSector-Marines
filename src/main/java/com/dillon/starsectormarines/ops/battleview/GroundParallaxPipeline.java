package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

/**
 * S2 (screen-space ground parallax) orchestrator: redirects the
 * {@code RenderLayer#GROUND} drain into a color+height FBO pair instead of the
 * backbuffer, then composites them onto the backbuffer through a fullscreen
 * offset-limited-parallax fragment shader (Welsh 2004) before the rest of the
 * frame draws on top. See {@code roadmap/surface-relief/overview.md} and
 * {@code stories/s2-screenspace-parallax.md}.
 *
 * <p>FBO pair is sized to the battle grid VIEWPORT (framebuffer px), not the
 * whole screen — {@link BattleCamera}'s {@code vpX/vpY/vpW/vpH} are already
 * the UI-space rect {@link GroundRenderSystem} and {@link GroundHeightPass}
 * emit their quads into (via {@code cellToScreenX/Y}), so the FBO's ortho is
 * set to that SAME UI-space rect ({@code (vpX, vpX+vpW, vpY, vpY+vpH)}, not
 * {@code (0, fboPxW)}) — the existing GROUND/height-pass draw calls need zero
 * coordinate translation to land correctly in the FBO. The composite quad then
 * draws over that same rect on the backbuffer, under whatever UI ortho is
 * already active (matching {@code DecalAccumulator.blit}'s pattern) — no extra
 * matrix setup needed there either.
 *
 * <p><strong>Fail-soft, mirrors {@link com.dillon.starsectormarines.render2d.DecalAccumulator}
 * and {@link ShaderProgram}.</strong> A shader compile/link failure or an
 * incomplete FBO logs once and flips {@link #broken}; every subsequent call —
 * and any mid-frame failure this frame — falls back to draining GROUND straight
 * to the backbuffer, i.e. pixel-identical to the pipeline never having run.
 */
public final class GroundParallaxPipeline {

    private static final Logger LOG = Global.getLogger(GroundParallaxPipeline.class);

    // ---- shader tuning (playtest-tunable; see overview.md "Risks") -----------

    /** UV-space offset per unit of (biased height × eye.xy). Keeps peak offset in the few-screen-pixel range the paper calls for. */
    public static final float MIN_STRENGTH = 0f;
    public static final float MAX_STRENGTH = 5.0f;
    public static final float DEFAULT_STRENGTH = 0.006f;
    /** Fake-perspective eye height above the screen plane, in the same normalized units as the UV-space screen-center vector. */
    /** Package-visible so the headless pixel-reference test cannot drift from the shader uniform. */
    static final float EYE_HEIGHT = 1.2f;
    /** height*scale+bias centers macro/micro height (nominally in [0,1], ~0.5 = "ground level") on zero before it's applied as an offset. */
    static final float HEIGHT_SCALE = 1.0f;
    static final float HEIGHT_BIAS = -0.5f;

    private static final String VERTEX_SRC = ""
            + "#version 120\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "    vUv = gl_MultiTexCoord0.xy;\n"
            + "    gl_FrontColor = gl_Color;\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

    private static final String FRAGMENT_SRC = ""
            + "#version 120\n"
            + "uniform sampler2D colorTex;\n"
            + "uniform sampler2D heightTex;\n"
            + "uniform vec2 screenCenter;\n"
            + "uniform float eyeHeight;\n"
            + "uniform float strength;\n"
            + "uniform float heightScale;\n"
            + "uniform float heightBias;\n"
            + "uniform float aspect;\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "    float h = texture2D(heightTex, vUv).r;\n"
            + "    float hsb = h * heightScale + heightBias;\n"
            // Eye vector in an isotropic (aspect-corrected) space, so equal
            // screen-pixel distances from center pull equally hard on both axes;
            // the offset converts back to UV space before sampling.
            + "    vec2 d = (screenCenter - vUv) * vec2(aspect, 1.0);\n"
            + "    vec3 eye = normalize(vec3(d, eyeHeight));\n"
            // Offset-limited form (Welsh 2004) -- no divide by eye.z, so shallow
            // eye vectors at screen edges can't explode into shimmer.
            + "    vec2 off = hsb * eye.xy * strength * vec2(1.0 / aspect, 1.0);\n"
            + "    vec2 offsetUv = clamp(vUv + off, 0.0, 1.0);\n"
            + "    gl_FragColor = texture2D(colorTex, offsetUv) * gl_Color;\n"
            + "}\n";

    private final ShaderProgram shader = new ShaderProgram("GroundParallax", VERTEX_SRC, FRAGMENT_SRC);
    private final GroundHeightPass heightPass;

    /** Runtime-tunable through the battle debug panel; read every composite. */
    private float strength = DEFAULT_STRENGTH;

    private boolean broken;

    private int colorFbo, colorTex;
    private int heightFbo, heightTex;
    private int fboPxW, fboPxH;

    /** UI-space rect the FBOs' ortho + the composite quad are drawn against — cached from the camera each call. */
    private float vpX, vpY, vpW, vpH;

    /** Sampled once, like {@code DecalAccumulator.uiFboBinding} — never a per-frame {@code glGet*} (async-renderer stall). */
    private int uiFboBinding = -1;
    private boolean uiFboSampled;

    /** Compatibility constructor; without a sprite registry sliced sheets remain macro-only/fallback. */
    public GroundParallaxPipeline() {
        this.heightPass = new GroundHeightPass(new GroundMicroHeightSampler(() -> null, () -> null));
    }

    public GroundParallaxPipeline(BattleSprites sprites) {
        this.heightPass = new GroundHeightPass(new GroundMicroHeightSampler(sprites));
    }

    public float parallaxStrength() { return strength; }

    /** Applies immediately to the next rendered frame. */
    public void setParallaxStrength(float strength) {
        if (Float.isNaN(strength)) return;
        this.strength = Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength));
    }

    /**
     * Renders {@code RenderLayer#GROUND} through the parallax pipeline:
     * color+height FBO pair, then the composite blit. {@code drainColor} is the
     * caller's {@code BattleRenderer.drainLayer(GROUND)} — invoked inside the
     * color FBO bracket on the happy path, or directly against the backbuffer
     * (its normal target) on ANY failure, so the layer always ends up drawn
     * exactly once regardless of which path ran.
     */
    void renderGround(RenderContext rc, Runnable drainColor) {
        if (broken || !shader.ensure()) {
            drainColor.run();
            return;
        }

        vpX = rc.camera.vpX();
        vpY = rc.camera.vpY();
        vpW = rc.camera.vpW();
        vpH = rc.camera.vpH();
        if (vpW <= 0f || vpH <= 0f) {
            drainColor.run();
            return;
        }

        ensureFbo();
        if (broken) {
            drainColor.run();
            return;
        }

        if (!renderColorFbo(drainColor)) {
            drainColor.run();
            return;
        }
        if (!renderHeightFbo(rc)) {
            drainColor.run();
            return;
        }
        if (!composite(rc.alphaMult)) {
            drainColor.run();
        }
    }

    /** Releases GPU resources. Call from the owning screen's detach path — mirrors {@code DecalAccumulator.dispose}. */
    public void dispose() {
        releaseFbos();
        shader.dispose();
        heightPass.dispose();
    }

    private void releaseFbos() {
        if (colorFbo != 0) { glDeleteFramebuffers(colorFbo); colorFbo = 0; }
        if (colorTex != 0) { glDeleteTextures(colorTex); colorTex = 0; }
        if (heightFbo != 0) { glDeleteFramebuffers(heightFbo); heightFbo = 0; }
        if (heightTex != 0) { glDeleteTextures(heightTex); heightTex = 0; }
        fboPxW = 0;
        fboPxH = 0;
    }

    // ------------------------------------------------------------------

    private boolean renderColorFbo(Runnable drainColor) {
        return withFboBound(colorFbo, () -> {
            glColorMask(true, true, true, true);
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            drainColor.run();
        });
    }

    private boolean renderHeightFbo(RenderContext rc) {
        BattleSimulation sim = rc.sim;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        return withFboBound(heightFbo, () -> {
            glColorMask(true, true, true, true);
            glClearColor(0.5f, 0.5f, 0.5f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            heightPass.render(rc.camera, grid, topology, mapping);
        });
    }

    private boolean composite(float alphaMult) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            // Bind AFTER the push so glPopAttrib restores the caller's texture
            // bindings/active unit (GL_TEXTURE_BIT covers both) -- same order as
            // DecalAccumulator.blit.
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, heightTex);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, colorTex);

            shader.use();
            shader.set1i("colorTex", 0);
            shader.set1i("heightTex", 1);
            shader.set2f("screenCenter", 0.5f, 0.5f);
            shader.set1f("eyeHeight", EYE_HEIGHT);
            shader.set1f("strength", strength);
            shader.set1f("heightScale", HEIGHT_SCALE);
            shader.set1f("heightBias", HEIGHT_BIAS);
            shader.set1f("aspect", fboPxW / (float) fboPxH);

            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(1f, 1f, 1f, alphaMult);

            float x0 = vpX, y0 = vpY, x1 = vpX + vpW, y1 = vpY + vpH;
            glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f); glVertex2f(x0, y0);
            glTexCoord2f(1f, 0f); glVertex2f(x1, y0);
            glTexCoord2f(1f, 1f); glVertex2f(x1, y1);
            glTexCoord2f(0f, 1f); glVertex2f(x0, y1);
            glEnd();
            return true;
        } catch (RuntimeException e) {
            LOG.error("GroundParallaxPipeline composite draw failed; disabling effect", e);
            broken = true;
            return false;
        } finally {
            // The program binding lives outside the attrib stack; texture
            // bindings/active unit are restored by the pop.
            ShaderProgram.useNone();
            glPopAttrib();
        }
    }

    /**
     * Runs {@code body} with {@code fbo} bound, viewport sized to the FBO, and
     * an ortho spanning the SAME UI-space rect the GROUND/height quads are
     * already emitted into ({@code (vpX, vpX+vpW, vpY, vpY+vpH)} — see class
     * doc) — not {@code (0, fboPxW)} like {@code DecalAccumulator}, which owns
     * its own FBO-local coordinate space instead of replaying existing UI-space
     * draw calls. State-save pattern is otherwise identical to
     * {@code DecalAccumulator.withFboBound}. Returns {@code false} (and flips
     * {@link #broken}) if {@code body} throws.
     */
    private boolean withFboBound(int fbo, Runnable body) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW);  glPushMatrix();
        glMatrixMode(GL_TEXTURE);    glPushMatrix();

        if (!uiFboSampled) {
            uiFboBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);
            uiFboSampled = true;
        }

        boolean ok = true;
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, fboPxW, fboPxH);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_DEPTH_TEST);
            glColorMask(true, true, true, true);
            glUseProgram(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glMatrixMode(GL_PROJECTION); glLoadIdentity();
            glOrtho(vpX, vpX + vpW, vpY, vpY + vpH, -1, 1);
            glMatrixMode(GL_MODELVIEW);  glLoadIdentity();

            body.run();
        } catch (RuntimeException e) {
            LOG.error("GroundParallaxPipeline FBO body failed; disabling effect", e);
            broken = true;
            ok = false;
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, uiFboBinding);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glUseProgram(0);
            glMatrixMode(GL_TEXTURE);    glPopMatrix();
            glMatrixMode(GL_MODELVIEW);  glPopMatrix();
            glMatrixMode(GL_PROJECTION); glPopMatrix();
            glPopAttrib();
        }
        return ok;
    }

    /**
     * Sanity bound on FBO side length. {@link #renderGround} is reachable with a
     * WORLD-UNIT {@link BattleCamera} (the vanilla-combat-bridge backdrop uses one —
     * see {@code combathybrid.bridge.GroundSceneBackdrop}), whose {@code vpW/vpH}
     * aren't UI pixels at all; the {@code sx/sy} scale below assumes they are. Rather
     * than special-case that camera, clamp: an absurd size degrades to the fallback
     * path exactly like any other failure, instead of a multi-GB allocation attempt.
     */
    private static final int MAX_FBO_DIM = 8192;

    private void ensureFbo() {
        float sx = Display.getWidth() / Math.max(1f, Global.getSettings().getScreenWidth());
        float sy = Display.getHeight() / Math.max(1f, Global.getSettings().getScreenHeight());
        int wantW = Math.max(1, Math.round(vpW * sx));
        int wantH = Math.max(1, Math.round(vpH * sy));
        if (wantW > MAX_FBO_DIM || wantH > MAX_FBO_DIM) {
            LOG.warn("GroundParallaxPipeline: refusing " + wantW + "x" + wantH
                    + " FBO (over " + MAX_FBO_DIM + "px) -- camera isn't UI-space; disabling effect for this view");
            broken = true;
            return;
        }
        if (colorFbo != 0 && wantW == fboPxW && wantH == fboPxH) return;

        releaseFbos(); // shader program is independent of FBO size -- left alone, no recompile on resize
        fboPxW = wantW;
        fboPxH = wantH;

        int[] color = buildFbo();
        if (broken) return;
        colorFbo = color[0];
        colorTex = color[1];

        int[] height = buildFbo();
        if (broken) {
            glDeleteFramebuffers(colorFbo);
            glDeleteTextures(colorTex);
            colorFbo = 0;
            colorTex = 0;
            return;
        }
        heightFbo = height[0];
        heightTex = height[1];

        LOG.info("GroundParallaxPipeline FBOs (" + colorFbo + "/" + heightFbo + ") complete at "
                + fboPxW + "x" + fboPxH);
    }

    /** Builds one RGBA8 FBO + color-attachment texture at the current {@link #fboPxW}/{@link #fboPxH}. {@code {fbo, tex}}. */
    private int[] buildFbo() {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        ByteBuffer empty = BufferUtils.createByteBuffer(fboPxW * fboPxH * 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, fboPxW, fboPxH, 0, GL_RGBA, GL_UNSIGNED_BYTE, empty);
        checkGL("glTexImage2D (ground parallax FBO)");
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        int prevFbo = glGetInteger(GL_FRAMEBUFFER_BINDING);
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);

        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOG.error("GroundParallaxPipeline FBO incomplete: 0x" + Integer.toHexString(status)
                    + " (size " + fboPxW + "x" + fboPxH + ")");
            glDeleteFramebuffers(fbo);
            glDeleteTextures(tex);
            broken = true;
            return new int[]{0, 0};
        }
        return new int[]{fbo, tex};
    }

    private static void checkGL(String label) {
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            LOG.error("GL error at " + label + ": 0x" + Integer.toHexString(err));
        }
    }
}
