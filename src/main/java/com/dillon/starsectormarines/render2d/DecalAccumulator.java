package com.dillon.starsectormarines.render2d;

import com.dillon.starsectormarines.battle.combat.fx.Decal;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_MAX_TEXTURE_SIZE;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
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
 * <p>FBO resolution is per cell ({@link #DEFAULT_FBO_PX_PER_CELL}), so its
 * VRAM cost scales with the map: 41 MB on a 100×100 grid, and 157 MB on the
 * 240×160 grid Conquest always builds. {@link #MAX_FBO_PIXELS} caps that,
 * stepping the per-cell resolution down rather than refusing the allocation.
 * Adjust the request via the constructor if needed.
 *
 * <p>Zoomed in past that density, the map-wide surface shows marks magnified
 * and soft, so the view is drawn from sharp tiles instead ({@link DecalTiles}):
 * blocks of the map at a higher density, held only while in view, each filled
 * by replaying the retained marks that reach it and stamped with new marks as
 * they land. A tile not yet filled draws its block from the map-wide surface.
 * Marks evicted from the retained list live only on the map-wide surface, so a
 * tile replayed after their eviction does not show them.
 *
 * <p>Modeled on {@link com.dillon.starsectormarines.render.BridgeRenderer}'s
 * state-save/restore pattern: push GL attribs for everything {@code glPopAttrib}
 * can restore (texture binding, viewport, blend, …), restore the non-attrib
 * bindings (program, array/element buffers) to the fixed-function defaults the
 * UI pass runs under, and rebind the inherited draw-FBO sampled once at
 * {@link #uiFboBinding}. We deliberately do NOT read GL state back per frame —
 * a {@code glGet*} forces a synchronous round-trip that stalls async-renderer
 * bridge mods (e.g. genir's). No depth attachment — 2D only.
 */
public final class DecalAccumulator {

    private static final Logger LOG = Global.getLogger(DecalAccumulator.class);

    /**
     * Default FBO pixel resolution per nav-grid cell. 32px matches the
     * source tile sheets' native resolution — at neutral zoom, one FBO
     * texel = one source pixel = one screen pixel, so stamped decals
     * look identical to the pre-FBO direct-draw path. Drop to 16 (half
     * res) to quarter the VRAM cost in exchange for visible softness.
     * Closer zooms are sharp through the view tiles ({@link DecalTiles}),
     * not a denser map-wide surface.
     */
    public static final int DEFAULT_FBO_PX_PER_CELL = 32;

    /**
     * Ceiling on the color attachment's total pixels, before the per-dimension
     * driver limit is also applied. 16.7 Mpx is 67 MB at RGBA8.
     *
     * <p>The requested resolution is per <em>cell</em>, so the allocation grows
     * with the map and nothing in the request itself is bounded: Conquest always
     * builds the LARGE 240x160 grid regardless of tier, which at the default 32
     * px/cell asks for 7680x5120 -- 157 MB of VRAM, plus a transient 157 MB
     * direct ByteBuffer, uploaded in one call on the render thread. That is a
     * large enough single-shot upload to be a plausible driver-timeout risk on a
     * VRAM-tight machine, and it was requested with no ceiling of any kind.
     *
     * <p>Prefer softer decals over a refused allocation: {@link #resolvePxPerCell}
     * steps the resolution down until it fits, and only a grid too large at 1
     * px/cell fails the accumulator outright.
     */
    private static final int MAX_FBO_PIXELS = 4096 * 4096;

    /**
     * {@code GL_MAX_TEXTURE_SIZE}, resolved from the driver on first use.
     * Cached because this class deliberately avoids {@code glGet*} readbacks
     * (they stall async-renderer bridges) -- once per process, off the
     * per-frame path, is the compromise. {@code 0} = not yet queried.
     */
    private static int maxTextureSize;

    private final int fboPxPerCell;

    private int fbo;
    private int fboColor;
    private int fboPxW;
    private int fboPxH;
    /** Px/cell the FBO was actually allocated at -- {@link #fboPxPerCell} after {@link #resolvePxPerCell} clamping. Stamping must use this, not the request. */
    private int resolvedPxPerCell;
    private int gridCellsW;
    private int gridCellsH;

    private boolean broken;

    /**
     * The UI's draw-FBO binding, sampled once on the first bracketed render and
     * reused every frame after. We restore to this instead of reading
     * {@code GL_FRAMEBUFFER_BINDING} back each frame, because that readback is
     * the call that stalls async-renderer bridges. The target is stable for the
     * lifetime of a screen, so a one-shot sample is safe; a new battle/screen
     * builds a fresh accumulator and re-samples. {@code -1} = not yet sampled.
     */
    private int uiFboBinding = -1;
    private boolean uiFboSampled;

    /** Resident sharp tiles by {@link DecalTiles#key}, least recently drawn first. */
    private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>(32, 0.75f, true);
    /** Tiles evicted from the view whose GL objects wait to be reused. */
    private final ArrayDeque<Tile> spareTiles = new ArrayDeque<>();
    /** Set when a tile target fails; the map-wide surface then draws every view. */
    private boolean tilesBroken;

    /** One sharp tile: its block of the map and the framebuffer holding it. */
    private static final class Tile {
        int fbo;
        int color;
        int tileX;
        int tileY;
        boolean ready;
    }

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
            releaseTiles();
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
                stampTilesLastN(decals, toStamp, frames);
            }
            lastStampedTotal = totalEverAdded;
        }

        if (!drawTiles(camera, decals, frames, alphaMult)) blit(camera, alphaMult);
    }

    /**
     * Re-stamps everything from scratch on the next render. Use when the
     * caller knows the FBO content is stale (e.g. after a screen detach
     * if you've reused the same instance across sims).
     */
    public void invalidate() {
        lastStampedCollection = null;
        lastStampedTotal = 0L;
        releaseTiles();
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
        for (Tile tile : spareTiles) {
            glDeleteFramebuffers(tile.fbo);
            glDeleteTextures(tile.color);
        }
        spareTiles.clear();
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
            SpriteSheetFrames.Frame f = frameOf(d, frames);
            if (f != null) append(d, f, 0f, 0f, resolvedPxPerCell, 0f);
        }
        if (decalBatch.isEmpty()) return;

        // Render the queued quads INTO the FBO. State-save pattern mirrors
        // BridgeRenderer: heavy push to isolate Starsector's UI render state,
        // explicit binding saves for anything outside the attrib stack.
        withFboBound(fbo, fboPxW, fboPxH, () -> decalBatch.flush());
    }

    /** The frame a mark draws, or null for one the sheet has no picture for. */
    private static SpriteSheetFrames.Frame frameOf(Decal d, SpriteSheetFrames frames) {
        if (d.decalIndex < 0 || d.decalIndex >= frames.frames.length) return null;
        SpriteSheetFrames.Frame f = frames.frames[d.decalIndex];
        return f.w > 0 && f.h > 0 ? f : null;
    }

    /**
     * Queues one mark for a surface whose texel {@code (padPx, padPx)} is cell
     * {@code (originX, originY)}, at {@code pxPerCell}.
     */
    private void append(Decal d, SpriteSheetFrames.Frame f, float originX, float originY, float pxPerCell,
                        float padPx) {
        float cx = (d.x - originX) * pxPerCell + padPx;
        float cy = (d.y - originY) * pxPerCell + padPx;
        float longPx = d.scaleCells * pxPerCell;
        float shortPx = longPx * ((float) f.h / (float) f.w);
        decalBatch.appendRotated(f.x, f.y, f.w, f.h, cx, cy, longPx, shortPx, d.rotationDeg, 1f, 1f, 1f, 1f);
    }

    /** Stamps the newest {@code n} marks into every filled tile they reach. */
    private void stampTilesLastN(Collection<Decal> decals, int n, SpriteSheetFrames frames) {
        if (tiles.isEmpty()) return;
        List<Decal> fresh = new ArrayList<>(n);
        int skip = decals.size() - n;
        int idx = 0;
        for (Decal d : decals) {
            if (idx++ >= skip) fresh.add(d);
        }
        for (Tile tile : tiles.values()) {
            if (tile.ready) stampTile(tile, fresh, frames);
        }
    }

    /** Stamps the marks of {@code decals} that reach {@code tile} into it. */
    private void stampTile(Tile tile, Iterable<Decal> decals, SpriteSheetFrames frames) {
        float originX = tile.tileX * DecalTiles.TILE_CELLS;
        float originY = tile.tileY * DecalTiles.TILE_CELLS;
        for (Decal d : decals) {
            SpriteSheetFrames.Frame f = frameOf(d, frames);
            if (f == null || !DecalTiles.reaches(d, f, tile.tileX, tile.tileY)) continue;
            append(d, f, originX, originY, DecalTiles.PX_PER_CELL, DecalTiles.PAD_PX);
        }
        if (decalBatch.isEmpty()) return;
        withFboBound(tile.fbo, DecalTiles.TEXTURE_PX, DecalTiles.TEXTURE_PX, () -> decalBatch.flush());
    }

    /**
     * Draws the view from sharp tiles when it is zoomed in past the map-wide
     * surface and few enough tiles cover it, filling at most
     * {@link DecalTiles#FILLS_PER_FRAME} new ones; a tile not yet filled draws
     * its block from the map-wide surface. Returns false, drawing nothing, when
     * the map-wide surface should draw the whole view instead.
     */
    private boolean drawTiles(BattleCamera camera, Collection<Decal> decals, SpriteSheetFrames frames,
                              float alphaMult) {
        if (tilesBroken || !DecalTiles.engaged(camera.cellPxSize(), resolvedPxPerCell)) return false;
        VisibleCellRect view = camera.visibleCells(0, gridCellsW, gridCellsH);
        if (view.equals(VisibleCellRect.EMPTY)) return false;
        int tx0 = DecalTiles.tileOf(view.minX());
        int ty0 = DecalTiles.tileOf(view.minY());
        int tx1 = DecalTiles.tileOf(view.maxX());
        int ty1 = DecalTiles.tileOf(view.maxY());
        if ((tx1 - tx0 + 1) * (ty1 - ty0 + 1) > DecalTiles.MAX_RESIDENT) return false;

        // Touch the resident tiles in view first, so making room for a new one
        // never evicts a tile this same frame still draws.
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) tiles.get(DecalTiles.key(tx, ty));
        }
        int fills = 0;
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                Tile tile = tiles.get(DecalTiles.key(tx, ty));
                if (tile == null && fills < DecalTiles.FILLS_PER_FRAME && !tilesBroken) {
                    tile = fillTile(tx, ty, decals, frames);
                    fills++;
                }
                float x0 = tx * DecalTiles.TILE_CELLS;
                float y0 = ty * DecalTiles.TILE_CELLS;
                float x1 = Math.min(gridCellsW, x0 + DecalTiles.TILE_CELLS);
                float y1 = Math.min(gridCellsH, y0 + DecalTiles.TILE_CELLS);
                if (tile != null && tile.ready) {
                    float texel = 1f / DecalTiles.TEXTURE_PX;
                    float span = DecalTiles.PX_PER_CELL * texel;
                    float u0 = DecalTiles.PAD_PX * texel;
                    blitRect(camera, tile.color, x0, y0, x1, y1,
                            u0, u0, u0 + (x1 - x0) * span, u0 + (y1 - y0) * span, alphaMult);
                } else {
                    blitRect(camera, fboColor, x0, y0, x1, y1,
                            x0 / gridCellsW, y0 / gridCellsH, x1 / gridCellsW, y1 / gridCellsH, alphaMult);
                }
            }
        }
        return true;
    }

    /**
     * Makes tile {@code (tileX, tileY)} resident, evicting the least recently
     * drawn tile when {@link DecalTiles#MAX_RESIDENT} are held, and fills it by
     * replaying every retained mark that reaches it. Returns null, and stops
     * using tiles, if its target cannot be made.
     */
    private Tile fillTile(int tileX, int tileY, Collection<Decal> decals, SpriteSheetFrames frames) {
        if (tiles.size() >= DecalTiles.MAX_RESIDENT) {
            Iterator<Tile> eldest = tiles.values().iterator();
            Tile evicted = eldest.next();
            eldest.remove();
            evicted.ready = false;
            spareTiles.push(evicted);
        }
        Tile tile = spareTiles.isEmpty() ? newTile() : spareTiles.pop();
        if (tile == null) {
            tilesBroken = true;
            LOG.warn("DecalAccumulator: a sharp decal tile could not be made -- the map-wide surface draws every view");
            return null;
        }
        tile.tileX = tileX;
        tile.tileY = tileY;
        withFboBound(tile.fbo, DecalTiles.TEXTURE_PX, DecalTiles.TEXTURE_PX, () -> {
            glColorMask(true, true, true, true);
            glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT);
        });
        stampTile(tile, decals, frames);
        tile.ready = !broken;
        tiles.put(DecalTiles.key(tileX, tileY), tile);
        return tile;
    }

    /** A new tile's framebuffer and color texture, or null if the driver will not complete it. */
    private Tile newTile() {
        Tile tile = new Tile();
        tile.color = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tile.color);
        GlErrors.clear();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, DecalTiles.TEXTURE_PX, DecalTiles.TEXTURE_PX, 0, GL_RGBA,
                GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlErrors.check("glTexImage2D (decal tile color)");
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        tile.fbo = glGenFramebuffers();
        int prevFbo = glGetInteger(GL_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, tile.fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tile.color, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
        if (status == GL_FRAMEBUFFER_COMPLETE) return tile;
        LOG.error("DecalAccumulator tile FBO incomplete: 0x" + Integer.toHexString(status));
        glDeleteFramebuffers(tile.fbo);
        glDeleteTextures(tile.color);
        return null;
    }

    /** Moves every resident tile to the spare pool, unfilled: their marks are stale. */
    private void releaseTiles() {
        for (Tile tile : tiles.values()) {
            tile.ready = false;
            spareTiles.push(tile);
        }
        tiles.clear();
    }

    private void clearFbo() {
        withFboBound(fbo, fboPxW, fboPxH, () -> {
            glColorMask(true, true, true, true);
            glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT);
        });
    }

    /**
     * Runs {@code body} with {@code target} bound, viewport sized to its
     * {@code width} x {@code height}, ortho projection matching its pixel
     * coords, and Starsector's UI GL state isolated via {@code glPushAttrib} +
     * matrix push/pop.
     */
    private void withFboBound(int target, int width, int height, Runnable body) {
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
            glBindFramebuffer(GL_FRAMEBUFFER, target);
            glViewport(0, 0, width, height);
            glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            glColorMask(true, true, true, true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glUseProgram(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glMatrixMode(GL_PROJECTION); glLoadIdentity();
            glOrtho(0, width, 0, height, -1, 1);
            glMatrixMode(GL_MODELVIEW);  glLoadIdentity();

            body.run();
        } catch (RuntimeException e) {
            LOG.error("DecalAccumulator FBO body failed; disabling further stamps", e);
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
     * Blit the FBO as one textured quad over the world rect. Runs inside
     * Starsector's UI render pass (already inside the scissor bracket
     * BattleScreen.render establishes), so we just need correct alpha
     * blending + the texture bound.
     */
    private void blit(BattleCamera camera, float alphaMult) {
        blitRect(camera, fboColor, 0f, 0f, gridCellsW, gridCellsH, 0f, 0f, 1f, 1f, alphaMult);
    }

    /**
     * Draws texture {@code color}'s {@code [u0,u1] x [v0,v1]} over the cell
     * rect {@code [x0,x1] x [y0,y1]}. V=0 is the texture's bottom row, the
     * row stamped at the rect's low cell y.
     */
    private void blitRect(BattleCamera camera, int color, float x0, float y0, float x1, float y1,
                          float u0, float v0, float u1, float v1, float alphaMult) {
        float sx0 = camera.cellToScreenX(x0);
        float sy0 = camera.cellToScreenY(y0);
        float sx1 = camera.cellToScreenX(x1);
        float sy1 = camera.cellToScreenY(y1);

        // glPushAttrib restores the texture binding on pop; we only need to put
        // the shader program back to the fixed-function default (not attrib
        // state) afterward. No glGet* readback — see uiFboBinding.
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            glUseProgram(0);
            glColorMask(true, true, true, true);
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, color);
            glColor4f(1f, 1f, 1f, alphaMult);

            glBegin(GL_QUADS);
            glTexCoord2f(u0, v0); glVertex2f(sx0, sy0);
            glTexCoord2f(u1, v0); glVertex2f(sx1, sy0);
            glTexCoord2f(u1, v1); glVertex2f(sx1, sy1);
            glTexCoord2f(u0, v1); glVertex2f(sx0, sy1);
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
            // Force a restamp — the new FBO is empty.
            invalidate();
        }

        int perCell = resolvePxPerCell(gridW, gridH);
        if (perCell < 1) {
            LOG.error("DecalAccumulator: " + gridW + "x" + gridH
                    + " cell grid does not fit a texture even at 1px/cell (driver max "
                    + maxTextureSize + "px) -- disabling decal accumulation");
            broken = true;
            return;
        }
        if (perCell < fboPxPerCell) {
            LOG.warn("DecalAccumulator: " + gridW + "x" + gridH + " cell grid at "
                    + fboPxPerCell + "px/cell would need "
                    + ((long) gridW * fboPxPerCell) + "x" + ((long) gridH * fboPxPerCell)
                    + "; clamped to " + perCell + "px/cell -- decals will be softer");
        }

        gridCellsW = gridW;
        gridCellsH = gridH;
        resolvedPxPerCell = perCell;
        fboPxW = gridW * perCell;
        fboPxH = gridH * perCell;

        fbo      = glGenFramebuffers();
        fboColor = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, fboColor);
        GlErrors.clear();
        // Null, not a zeroed buffer: this sizes the texture storage without
        // uploading anything. The transparent surface this accumulator needs
        // comes from the clearFbo() at the end of this method, which has to run
        // regardless -- an uploaded zero fill only duplicated it, at the cost of
        // an fboPxW*fboPxH*4 direct ByteBuffer.
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, fboPxW, fboPxH, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlErrors.check("glTexImage2D (decal FBO color)");
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

        LOG.debug("DecalAccumulator FBO " + fbo + " complete at " + fboPxW + "x" + fboPxH
                + " (cells " + gridW + "x" + gridH + " × " + perCell + "px)");
        // Freshly sized storage holds undefined pixels, and this accumulator
        // blits every frame while only clearing on demand -- so this clear is
        // what makes the surface transparent, not an initial upload.
        clearFbo();
    }

    /**
     * Largest px-per-cell for this grid that satisfies both the driver's
     * per-dimension texture limit and {@link #MAX_FBO_PIXELS}, never above the
     * requested {@link #fboPxPerCell}. Returns {@code 0} when even 1 px/cell
     * exceeds the driver limit, which no clamping can rescue.
     */
    private int resolvePxPerCell(int gridW, int gridH) {
        int dimCap = maxTextureDimension();
        for (int perCell = fboPxPerCell; perCell >= 1; perCell--) {
            long w = (long) gridW * perCell;
            long h = (long) gridH * perCell;
            if (w <= dimCap && h <= dimCap && w * h <= MAX_FBO_PIXELS) return perCell;
        }
        return 0;
    }

    /**
     * {@code GL_MAX_TEXTURE_SIZE}, queried once and cached. Falls back to the
     * GL2.1-guaranteed minimum if the driver reports something implausible,
     * so a bad readback clamps conservatively rather than removing the ceiling.
     */
    private static int maxTextureDimension() {
        if (maxTextureSize == 0) {
            int reported = glGetInteger(GL_MAX_TEXTURE_SIZE);
            maxTextureSize = reported >= 1024 ? reported : 1024;
        }
        return maxTextureSize;
    }
}
