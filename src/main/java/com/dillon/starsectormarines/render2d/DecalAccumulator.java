package com.dillon.starsectormarines.render2d;

import com.dillon.starsectormarines.battle.fx.Decal;
import com.dillon.starsectormarines.battle.sprites.SpriteSheetFrames;
import com.dillon.starsectormarines.ops.battleview.BattleCamera;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
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
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
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
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;

/**
 * Persistent decal accumulator backed by an offscreen framebuffer.
 *
 * <p>Decals are stamped into the FBO ONCE when they spawn, then the FBO is
 * blitted as a single textured quad on the world rect every frame. The
 * per-frame cost is O(new-decals-this-frame) + O(1 blit), regardless of
 * how many decals have accumulated. Replaces the previous per-call
 * {@code decalSheet.renderAtCenter(…)} loop which scaled linearly with
 * the decal cap and dominated the late-battle decal pass.
 *
 * <p>Tradeoff: stamping is one-way. The FBO is a rasterized snapshot —
 * individual decals can't be removed, faded, or repositioned after
 * stamping. {@code BattleSimulation.addDecal} still tracks the source
 * decal list (for ordering / eventual removal logic), but the visual
 * layer is decoupled from that count. See {@code render2d_batching}
 * memory + the project conversation log for context.
 *
 * <p>FBO is half-resolution per cell ({@link #DEFAULT_FBO_PX_PER_CELL})
 * — sharp at battle-overview zoom, slightly soft at max zoom; ~10 MB on
 * a 100×100 grid. Adjust via the constructor if needed.
 *
 * <p>Modeled on {@link com.dillon.starsectormarines.render.BridgeRenderer}'s
 * state-save/restore pattern (push GL attribs, save program/buffers/FBO/
 * texture/viewport, restore in finally). No depth attachment — 2D only.
 */
public final class DecalAccumulator {

    private static final Logger LOG = Global.getLogger(DecalAccumulator.class);

    /**
     * Default FBO pixel resolution per nav-grid cell. 32px matches the
     * source tile sheets' native resolution — at neutral zoom, one FBO
     * texel = one source pixel = one screen pixel, so stamped decals
     * look identical to the pre-FBO direct-draw path. Drop to 16 (half
     * res) to quarter the VRAM cost in exchange for visible softness;
     * raise to 64 if you want sharp decals at max zoom (×4 VRAM).
     */
    public static final int DEFAULT_FBO_PX_PER_CELL = 32;

    private final int fboPxPerCell;

    private int fbo;
    private int fboColor;
    private int fboPxW;
    private int fboPxH;
    private int gridCellsW;
    private int gridCellsH;

    private boolean broken;

    /** QuadBatch bound to the decal sheet — created lazily on first stamp so we can wait for the sheet's lazy load. */
    private QuadBatch decalBatch;
    private SpriteAPI batchSheet;

    /**
     * Stamp tracking — high-water mark of {@code BattleSimulation.getDecalsEverAdded()}
     * at last stamp, plus the identity of the decal collection. A different
     * collection reference indicates a new battle, triggering a clear + full
     * re-stamp.
     *
     * <p>The counter is monotonic regardless of FIFO eviction at the source
     * list cap, so the accumulator stays accurate even when the deque
     * saturates and {@code decals.size()} stops changing — the old size-based
     * bookkeeping went permanently asleep at saturation and froze the FBO.
     */
    private Object lastStampedCollection;
    private long lastStampedTotal;

    public DecalAccumulator() {
        this(DEFAULT_FBO_PX_PER_CELL);
    }

    public DecalAccumulator(int fboPxPerCell) {
        this.fboPxPerCell = Math.max(1, fboPxPerCell);
    }

    /**
     * Stamp any new decals and blit the FBO to the world rect. No-op if
     * the decal sheet hasn't loaded yet — caller stays responsible for
     * the lazy-load and just hands us whatever's currently available.
     *
     * <p>{@code totalEverAdded} is the sim's monotonic decal-add counter
     * (from {@code BattleSimulation.getDecalsEverAdded()}). Drives incremental
     * stamping that survives source-list eviction at the cap: the difference
     * between this and {@link #lastStampedTotal} is the count of decals
     * spawned since the last stamp, regardless of how many were evicted
     * from the head of the source deque in the meantime.
     */
    public void render(BattleCamera camera, int gridW, int gridH,
                       java.util.Collection<Decal> decals, long totalEverAdded,
                       SpriteAPI sheet, SpriteSheetFrames frames,
                       float alphaMult) {
        if (broken) return;
        if (sheet == null || frames == null) return;
        if (gridW <= 0 || gridH <= 0) return;

        ensureFbo(gridW, gridH);
        if (broken) return;

        ensureBatch(sheet, frames);

        // Sim change = different collection reference = new battle. FBO
        // contents are stale; clear and start fresh.
        boolean simChanged = (lastStampedCollection != decals);
        if (simChanged) {
            lastStampedCollection = decals;
            lastStampedTotal = 0L;
            clearFbo();
            if (broken) return;
        }

        long newCount = totalEverAdded - lastStampedTotal;
        if (newCount > 0) {
            // Stamp the LAST min(newCount, decals.size()) entries. If newCount
            // exceeds the deque's current size, some of those decals were
            // spawned-then-evicted before this render call (we missed them
            // forever — intentional per the source-list-cap design). Stamping
            // the tail of the current deque covers the ones still visible in
            // the source list.
            int toStamp = (int) Math.min(newCount, (long) decals.size());
            if (toStamp > 0) {
                stampLastN(decals, toStamp, frames);
                if (broken) return;
            }
            lastStampedTotal = totalEverAdded;
        }

        blit(camera, alphaMult);
    }

    /**
     * Re-stamps everything from scratch on the next render. Use when the
     * caller knows the FBO content is stale (e.g. after a screen detach
     * if you've reused the same instance across sims).
     */
    public void invalidate() {
        lastStampedCollection = null;
        lastStampedTotal = 0L;
    }

    /**
     * Release GPU resources. Call from the owning screen's detach path
     * — Starsector doesn't auto-clean orphaned FBOs and accumulating
     * one per attach/detach cycle would leak VRAM over a session.
     */
    public void dispose() {
        if (fbo != 0)      { glDeleteFramebuffers(fbo);  fbo = 0; }
        if (fboColor != 0) { glDeleteTextures(fboColor); fboColor = 0; }
        invalidate();
    }

    // ------------------------------------------------------------------

    private void ensureBatch(SpriteAPI sheet, SpriteSheetFrames frames) {
        if (decalBatch == null || batchSheet != sheet) {
            decalBatch = new QuadBatch(sheet, frames.sheetWidth, frames.sheetHeight, 128);
            batchSheet = sheet;
        }
    }

    private void stampLastN(java.util.Collection<Decal> decals, int n, SpriteSheetFrames frames) {
        // Walk to the position (decals.size() - n) and stamp from there. The
        // skip cost is O(decals.size() - n) — a simple counter increment per
        // entry, microseconds even at the new larger source-list caps.
        int skip = decals.size() - n;
        int idx = 0;
        for (Decal d : decals) {
            if (idx++ < skip) continue;
            if (d.decalIndex < 0 || d.decalIndex >= frames.frames.length) continue;
            SpriteSheetFrames.Frame f = frames.frames[d.decalIndex];

            // Decal position in FBO pixel space — world-cell coords scaled by px/cell.
            float cx = d.x * fboPxPerCell;
            float cy = d.y * fboPxPerCell;
            float longPx  = d.scaleCells * fboPxPerCell;
            float shortPx = (f.w > 0) ? longPx * ((float) f.h / (float) f.w) : longPx;

            decalBatch.appendRotated(
                    f.x, f.y, f.w, f.h,
                    cx, cy, longPx, shortPx,
                    d.rotationDeg,
                    1f, 1f, 1f, 1f);
        }
        if (decalBatch.isEmpty()) return;

        // Render the queued quads INTO the FBO. State-save pattern mirrors
        // BridgeRenderer: heavy push to isolate Starsector's UI render state,
        // explicit binding saves for anything outside the attrib stack.
        withFboBound(() -> decalBatch.flush());
    }

    private void clearFbo() {
        withFboBound(() -> {
            glColorMask(true, true, true, true);
            glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT);
        });
    }

    /**
     * Runs {@code body} with the decal FBO bound, viewport sized to the
     * FBO, ortho projection matching FBO pixel coords, and Starsector's
     * UI GL state isolated via {@code glPushAttrib} + matrix push/pop.
     */
    private void withFboBound(Runnable body) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW);  glPushMatrix();
        glMatrixMode(GL_TEXTURE);    glPushMatrix();

        int prevProgram  = glGetInteger(GL_CURRENT_PROGRAM);
        int prevArrayBuf = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int prevElemBuf  = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int prevFbo      = glGetInteger(GL_FRAMEBUFFER_BINDING);
        int prevTex      = glGetInteger(GL_TEXTURE_BINDING_2D);

        IntBuffer vpBuf = BufferUtils.createIntBuffer(16);
        glGetInteger(GL_VIEWPORT, vpBuf);
        int vpX = vpBuf.get(0), vpY = vpBuf.get(1), vpW = vpBuf.get(2), vpH = vpBuf.get(3);

        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, fboPxW, fboPxH);
            glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            glColorMask(true, true, true, true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glUseProgram(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glMatrixMode(GL_PROJECTION); glLoadIdentity();
            glOrtho(0, fboPxW, 0, fboPxH, -1, 1);
            glMatrixMode(GL_MODELVIEW);  glLoadIdentity();

            body.run();
        } catch (RuntimeException e) {
            LOG.error("DecalAccumulator FBO body failed; disabling further stamps", e);
            broken = true;
        } finally {
            glBindTexture(GL_TEXTURE_2D, prevTex);
            glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, prevElemBuf);
            glBindBuffer(GL_ARRAY_BUFFER, prevArrayBuf);
            glUseProgram(prevProgram);
            glViewport(vpX, vpY, vpW, vpH);
            glMatrixMode(GL_TEXTURE);    glPopMatrix();
            glMatrixMode(GL_MODELVIEW);  glPopMatrix();
            glMatrixMode(GL_PROJECTION); glPopMatrix();
            glPopAttrib();
        }
    }

    /**
     * Blit the FBO as one textured quad over the world rect. Runs inside
     * Starsector's UI render pass (already inside the scissor bracket
     * BattleScreen.render establishes), so we just need correct alpha
     * blending + the texture bound.
     */
    private void blit(BattleCamera camera, float alphaMult) {
        float x0 = camera.cellToScreenX(0);
        float y0 = camera.cellToScreenY(0);
        float x1 = camera.cellToScreenX(gridCellsW);
        float y1 = camera.cellToScreenY(gridCellsH);

        glPushAttrib(GL_ALL_ATTRIB_BITS);
        int prevProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int prevTex     = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glUseProgram(0);
            glColorMask(true, true, true, true);
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fboColor);
            glColor4f(1f, 1f, 1f, alphaMult);

            glBegin(GL_QUADS);
            // V=0 at bottom-left of the FBO — matches the ortho we stamped under.
            glTexCoord2f(0f, 0f); glVertex2f(x0, y0);
            glTexCoord2f(1f, 0f); glVertex2f(x1, y0);
            glTexCoord2f(1f, 1f); glVertex2f(x1, y1);
            glTexCoord2f(0f, 1f); glVertex2f(x0, y1);
            glEnd();
        } finally {
            glBindTexture(GL_TEXTURE_2D, prevTex);
            glUseProgram(prevProgram);
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
            // Force a restamp — the new FBO is empty.
            invalidate();
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
        checkGL("glTexImage2D (decal FBO color)");
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
            LOG.error("DecalAccumulator FBO incomplete: 0x" + Integer.toHexString(status)
                    + " (size " + fboPxW + "x" + fboPxH + ")");
            broken = true;
            return;
        }

        LOG.info("DecalAccumulator FBO " + fbo + " complete at " + fboPxW + "x" + fboPxH
                + " (cells " + gridW + "x" + gridH + " × " + fboPxPerCell + "px)");
        // FBO starts with random / zeroed contents — make sure it's a clean
        // transparent surface so the blit doesn't paint random pixels over the
        // floor pass before any decals stamp.
        clearFbo();
    }

    private static void checkGL(String label) {
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            LOG.error("GL error at " + label + ": 0x" + Integer.toHexString(err));
        }
    }
}
