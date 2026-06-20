package com.dillon.starsectormarines.render2d;

import com.dillon.starsectormarines.battle.world.model.TimeOfDay;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_DST_COLOR;
import static org.lwjgl.opengl.GL11.GL_ZERO;
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
 * Lightmap accumulator — sibling of {@link DecalAccumulator} that drives the
 * pseudo time-of-day pass. Each frame the lightmap FBO is cleared to the
 * current {@link TimeOfDay#ambientR ambient} color, then transient and
 * persistent {@link Light}s are emitted additively as radial kernels into
 * the FBO. The FBO is then multiply-blended over the world layer — so any
 * region the lights haven't reached darkens by the ambient, and regions
 * under bright kernels brighten back toward white.
 *
 * <p>Two pools:
 * <ul>
 *   <li><b>Transients</b> — fire-and-forget lights with a finite
 *       {@link Light#lifetimeMax lifetime}. {@link #advance(float)} ticks
 *       them and evicts on expiry. Use for muzzle flashes and HE bursts.
 *   <li><b>Persistents</b> — id-keyed lights kept until removed. Use for
 *       wreck fires; the screen re-asserts them every frame from the live
 *       wreck list and calls {@link #retainPersistent(Set)} to evict the
 *       ones that vanished.
 * </ul>
 *
 * <p>FBO resolution mirrors {@link DecalAccumulator#DEFAULT_FBO_PX_PER_CELL}
 * by default — light kernels are inherently soft so half-res would also
 * be fine, but matching the decal FBO keeps the two layers visually
 * consistent.
 *
 * <p>Note on dynamic range: the lightmap FBO is RGBA8, so additive
 * contributions saturate at 1.0. Intensity values above 1.0 have no
 * "overbright" effect — push radius or stack overlapping kernels to read
 * brighter, not the intensity scalar.
 */
public final class LightAccumulator {

    private static final Logger LOG = Global.getLogger(LightAccumulator.class);

    private final int fboPxPerCell;

    private int fbo;
    private int fboColor;
    private int fboPxW;
    private int fboPxH;
    private int gridCellsW;
    private int gridCellsH;

    private boolean broken;
    private boolean kernelsBroken;

    /**
     * Inherited UI draw-FBO, sampled once and reused — see
     * {@link DecalAccumulator#uiFboBinding} for the no-per-frame-readback
     * rationale (a {@code glGet*} stalls async-renderer bridges).
     * {@code -1} = not yet sampled.
     */
    private int uiFboBinding = -1;
    private boolean uiFboSampled;

    private final List<Light> transients = new ArrayList<>();
    private final HashMap<Long, Light> persistents = new HashMap<>();

    private EnumMap<LightKernel, KernelHandle> kernels;

    public LightAccumulator(int fboPxPerCell) {
        this.fboPxPerCell = Math.max(1, fboPxPerCell);
    }

    /**
     * Queue a finite-lifetime light. Lifetime ticks down in
     * {@link #advance(float)}; on expiry the entry is dropped.
     */
    public void addTransient(float x, float y, float radiusCells,
                             LightKernel kernel, float r, float g, float b,
                             float intensity, float lifetimeSecs) {
        if (kernel == null || lifetimeSecs <= 0f) return;
        Light l = new Light();
        l.x = x;
        l.y = y;
        l.radiusCells = radiusCells;
        l.kernel = kernel;
        l.r = r;
        l.g = g;
        l.b = b;
        l.intensity = intensity;
        l.lifetimeMax = lifetimeSecs;
        l.lifetimeRemaining = lifetimeSecs;
        l.persistentId = 0L;
        transients.add(l);
    }

    /**
     * Add or update a persistent (id-keyed) light. Idempotent — re-asserting
     * with the same id overwrites pos / radius / color / intensity in place,
     * which is the contract callers depend on when re-pumping from a live
     * source list every frame.
     */
    public void putPersistent(long id, float x, float y, float radiusCells,
                              LightKernel kernel, float r, float g, float b,
                              float intensity) {
        if (id == 0L || kernel == null) return;
        Light l = persistents.get(id);
        if (l == null) {
            l = new Light();
            l.persistentId = id;
            l.lifetimeMax = -1f;
            persistents.put(id, l);
        }
        l.x = x;
        l.y = y;
        l.radiusCells = radiusCells;
        l.kernel = kernel;
        l.r = r;
        l.g = g;
        l.b = b;
        l.intensity = intensity;
    }

    public void removePersistent(long id) {
        persistents.remove(id);
    }

    /**
     * Evict every persistent whose id is NOT in {@code keepIds}. Lets the
     * caller pump from a live source list each frame and let this method
     * handle the deletions implicitly.
     */
    public void retainPersistent(Set<Long> keepIds) {
        if (persistents.isEmpty()) return;
        persistents.keySet().removeIf(id -> !keepIds.contains(id));
    }

    /** Tick transient lifetimes; evict expired entries. Persistents are unaffected. */
    public void advance(float dt) {
        if (dt <= 0f || transients.isEmpty()) return;
        // Swap-remove on expiry to keep the loop O(n) without compaction churn.
        for (int i = transients.size() - 1; i >= 0; i--) {
            Light l = transients.get(i);
            l.lifetimeRemaining -= dt;
            if (l.lifetimeRemaining <= 0f) {
                int last = transients.size() - 1;
                if (i != last) transients.set(i, transients.get(last));
                transients.remove(last);
            }
        }
    }

    /**
     * Re-bake the lightmap and blit it over the world rect. No-op if the
     * FBO can't be allocated, the kernel sprites can't be loaded, or the
     * grid hasn't been set up yet.
     */
    public void render(BattleCamera camera, TimeOfDay tod,
                       int gridW, int gridH, float alphaMult) {
        if (broken) return;
        if (tod == null || tod.bypass) return;
        if (gridW <= 0 || gridH <= 0) return;

        ensureFbo(gridW, gridH);
        if (broken) return;
        ensureKernels();
        if (kernelsBroken) return;

        fillFbo(tod);
        if (broken) return;
        blit(camera, alphaMult);
    }

    /** Release GPU resources. Pair with the owning screen's detach path. */
    public void dispose() {
        if (fbo != 0)      { glDeleteFramebuffers(fbo);  fbo = 0; }
        if (fboColor != 0) { glDeleteTextures(fboColor); fboColor = 0; }
        transients.clear();
        persistents.clear();
        // Kernel SpriteAPIs are owned by SettingsAPI's texture cache — no per-instance dispose needed.
        kernels = null;
        kernelsBroken = false;
    }

    // ------------------------------------------------------------------

    private void ensureKernels() {
        if (kernels != null || kernelsBroken) return;
        EnumMap<LightKernel, KernelHandle> built = new EnumMap<>(LightKernel.class);
        for (LightKernel k : LightKernel.values()) {
            try {
                Global.getSettings().loadTexture(k.spritePath);
                SpriteAPI s = Global.getSettings().getSprite(k.spritePath);
                if (s == null) {
                    LOG.warn("LightAccumulator: kernel sprite missing for " + k + " @ " + k.spritePath);
                    kernelsBroken = true;
                    return;
                }
                int w = Math.max(1, (int) s.getWidth());
                int h = Math.max(1, (int) s.getHeight());
                built.put(k, new KernelHandle(s, w, h, new QuadBatch(s, w, h, 64)));
            } catch (Exception ex) {
                LOG.warn("LightAccumulator: kernel load failed for " + k + " @ " + k.spritePath, ex);
                kernelsBroken = true;
                return;
            }
        }
        kernels = built;
    }

    private void fillFbo(TimeOfDay tod) {
        withFboBound(() -> {
            // Clear to ambient color. Alpha=1 isn't load-bearing (the
            // multiply blit ignores source alpha) but keeps the FBO content
            // tidy in case anything else samples it.
            glClearColor(tod.ambientR, tod.ambientG, tod.ambientB, 1f);
            glClear(GL_COLOR_BUFFER_BIT);

            // Emit lights additively. The withFboBound default is standard
            // alpha — switch to GL_SRC_ALPHA, GL_ONE so kernel contributions
            // stack toward white rather than averaging.
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);

            // Queue persistents first, then transients. Order within the FBO
            // doesn't matter for additive blending, so we don't sort.
            for (Light l : persistents.values()) appendLight(l);
            for (int i = 0; i < transients.size(); i++) appendLight(transients.get(i));

            // One flush per kernel sprite — texture bind happens inside flush().
            for (LightKernel k : LightKernel.values()) {
                KernelHandle h = kernels.get(k);
                if (h != null && !h.batch.isEmpty()) h.batch.flush();
            }
        });
    }

    private void appendLight(Light l) {
        KernelHandle h = kernels.get(l.kernel);
        if (h == null) return;
        if (l.radiusCells <= 0f) return;

        float cx = l.x * fboPxPerCell;
        float cy = l.y * fboPxPerCell;
        // Diameter in FBO pixels. Kernel sprites are square, so dst is square too.
        float diameterPx = l.radiusCells * 2f * fboPxPerCell;

        // Transients fade by lifetimeRemaining/Max; persistents stay full.
        float fade = (l.lifetimeMax > 0f)
                ? Math.max(0f, Math.min(1f, l.lifetimeRemaining / l.lifetimeMax))
                : 1f;

        // Pre-multiply RGB by intensity; alpha carries the fade and combines
        // with the kernel sprite's falloff alpha under (SRC_ALPHA, ONE).
        float r = Math.max(0f, Math.min(1f, l.r * l.intensity));
        float g = Math.max(0f, Math.min(1f, l.g * l.intensity));
        float b = Math.max(0f, Math.min(1f, l.b * l.intensity));

        h.batch.append(0, 0, h.spritePxW, h.spritePxH,
                cx, cy, diameterPx, diameterPx,
                r, g, b, fade);
    }

    /**
     * Bind the lightmap FBO, set viewport + ortho to FBO pixel space, and
     * run {@code body}. Mirrors {@link DecalAccumulator#withFboBound} — see
     * that javadoc for the state-save rationale.
     */
    private void withFboBound(Runnable body) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW);  glPushMatrix();
        glMatrixMode(GL_TEXTURE);    glPushMatrix();

        // Sample the inherited UI draw-FBO once (see uiFboBinding). Texture
        // binding and viewport come back via glPopAttrib; program + buffer
        // bindings restore to the fixed-function defaults below. No per-frame
        // glGet*, which would stall async-renderer bridge mods.
        if (!uiFboSampled) {
            uiFboBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);
            uiFboSampled = true;
        }

        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, fboPxW, fboPxH);
            glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            glColorMask(true, true, true, true);
            glEnable(GL_BLEND);
            // Initial blend func — body switches to additive after the clear.
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            glUseProgram(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glMatrixMode(GL_PROJECTION); glLoadIdentity();
            glOrtho(0, fboPxW, 0, fboPxH, -1, 1);
            glMatrixMode(GL_MODELVIEW);  glLoadIdentity();

            body.run();
        } catch (RuntimeException e) {
            LOG.error("LightAccumulator FBO body failed; disabling further passes", e);
            broken = true;
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
    }

    /**
     * Multiply-blit the lightmap over the world rect. Result:
     * {@code scene = scene * lightmap}. Runs inside the world scissor
     * the caller already established.
     *
     * <p>{@code alphaMult} interpolates between "lightmap fully active"
     * (1) and "lightmap not applied" (0). We implement the fade by
     * lerping the sampled lightmap color toward white via the texture-env
     * constant — but for V1 simplicity we just use {@code alphaMult} on
     * the source color when {@code alphaMult ≥ 1} (i.e., always pass 1
     * from the BattleScreen) and skip the fade math entirely. If
     * BattleScreen-level fades become a thing we'll need to revisit.
     */
    private void blit(BattleCamera camera, float alphaMult) {
        float x0 = camera.cellToScreenX(0);
        float y0 = camera.cellToScreenY(0);
        float x1 = camera.cellToScreenX(gridCellsW);
        float y1 = camera.cellToScreenY(gridCellsH);

        // glPushAttrib restores the texture binding on pop; we only put the
        // shader program back to the fixed-function default (not attrib state)
        // afterward. No glGet* readback — see uiFboBinding.
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            glUseProgram(0);
            glColorMask(true, true, true, true);
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_DST_COLOR, GL_ZERO);
            glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fboColor);
            glColor4f(1f, 1f, 1f, 1f);

            glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f); glVertex2f(x0, y0);
            glTexCoord2f(1f, 0f); glVertex2f(x1, y0);
            glTexCoord2f(1f, 1f); glVertex2f(x1, y1);
            glTexCoord2f(0f, 1f); glVertex2f(x0, y1);
            glEnd();
        } finally {
            glUseProgram(0);
            glPopAttrib();
        }
    }

    private void ensureFbo(int gridW, int gridH) {
        if (fbo != 0 && gridW == gridCellsW && gridH == gridCellsH) return;

        if (fbo != 0) {
            glDeleteFramebuffers(fbo);
            glDeleteTextures(fboColor);
            fbo = 0;
            fboColor = 0;
        }

        gridCellsW = gridW;
        gridCellsH = gridH;
        fboPxW = gridW * fboPxPerCell;
        fboPxH = gridH * fboPxPerCell;

        fbo      = glGenFramebuffers();
        fboColor = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, fboColor);
        ByteBuffer empty = BufferUtils.createByteBuffer(fboPxW * fboPxH * 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, fboPxW, fboPxH, 0, GL_RGBA, GL_UNSIGNED_BYTE, empty);
        checkGL("glTexImage2D (light FBO color)");
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        int prevFbo = glGetInteger(GL_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fboColor, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);

        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOG.error("LightAccumulator FBO incomplete: 0x" + Integer.toHexString(status)
                    + " (size " + fboPxW + "x" + fboPxH + ")");
            broken = true;
            return;
        }

        LOG.info("LightAccumulator FBO " + fbo + " complete at " + fboPxW + "x" + fboPxH
                + " (cells " + gridW + "x" + gridH + " × " + fboPxPerCell + "px)");
    }

    private static void checkGL(String label) {
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            LOG.error("GL error at " + label + ": 0x" + Integer.toHexString(err));
        }
    }

    private static final class KernelHandle {
        final SpriteAPI sprite;
        final int spritePxW;
        final int spritePxH;
        final QuadBatch batch;

        KernelHandle(SpriteAPI sprite, int spritePxW, int spritePxH, QuadBatch batch) {
            this.sprite = sprite;
            this.spritePxW = spritePxW;
            this.spritePxH = spritePxH;
            this.batch = batch;
        }
    }
}
