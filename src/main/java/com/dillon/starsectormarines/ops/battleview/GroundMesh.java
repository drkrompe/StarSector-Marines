package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_TRANSFORM_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTexCoordPointer;
import static org.lwjgl.opengl.GL11.glTranslatef;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;

/**
 * The battle's static ground, resident on the GPU as vertex buffers.
 *
 * <p><b>Why this and not fewer commands.</b> {@code renderEvidence} measured the
 * whole-map frame on the canonical 560x336 Conquest: the ground layer is 266 of
 * the 276 ms our side of the frame, and 259 of that is the <em>drain</em>. The
 * collector's 199,345 sheet quads left as 49,514 draw calls and 49,461 texture
 * binds — four quads a draw — because the sheet a cell draws from changes from
 * one cell to the next and the batcher has to flush every time it does.
 * Collection was 7 ms. Nothing about that is fixed by collecting less; it is
 * fixed by not submitting the ground again every frame.
 *
 * <p><b>A cell owns a slot.</b> Every cell that draws a base terrain tile gets
 * four vertices in the buffer for its sheet, and keeps them. Position is in
 * <em>cell space</em>, so panning and zooming are a modelview transform and
 * change nothing on the GPU: the camera contributes one translate and one scale
 * for the whole map. UVs are the cell's own sub-rectangle of its atlas, which is
 * what makes this work where a merged run does not — a run of identical cells
 * would want the driver's repeat wrap, and a sheet cut from an atlas has no wrap
 * to give.
 *
 * <p><b>A change is a patch, not a rebuild.</b> A breach, rubble or a new
 * bulkhead moves a handful of cells; {@link CellTopology#changeCount} says which,
 * and each is re-resolved with its four neighbours (an autotile frame is a
 * function of what is beside it) and written back with {@code glBufferSubData}
 * over its own slot. Only a reader that has fallen further behind than the
 * topology's log remembers rebuilds from scratch.
 *
 * <p><b>One bucket per sheet, not per sub-layer.</b> The base terrain is at most
 * one quad per cell and cells do not overlap, so floors and walls can share a
 * buffer without any question of which paints over which. A later sub-layer that
 * genuinely overlapped its neighbours would need its own buckets drawn in order;
 * this one does not, and pretending otherwise would double the buffers for
 * nothing.
 *
 * <p><b>What it does not hold.</b> Anything that is not the cell's own base tile:
 * solid fills (reported separately, because a cell with no tile still paints its
 * block's colour), crosswalk stripes, nature overlays, doorway decals, window
 * panes and shared-edge barriers. Those are sparse, they are already one batch
 * each, and several of them overlap two cells.
 *
 * <p><b>It fails soft.</b> A driver without buffer objects, a failed allocation,
 * any GL error at all — {@code sync} answers false and the caller emits the
 * ordinary per-cell command stream, with the same paint order and the same
 * picture. Turn it off for a control run with
 * {@code -Dbattle.render.groundMesh=false}.
 */
public final class GroundMesh {

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.groundMesh";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * Slots past the baked count each bucket keeps spare.
     *
     * <p>A cell that changes sheet — floor becoming rubble on another atlas —
     * needs a slot in a bucket it was not baked into. The margin means the common
     * case does not reallocate and re-upload a whole buffer to seat one cell.
     */
    private static final int SPARE_SLOTS = 256;

    /**
     * Dirty slots past which a bucket re-uploads whole instead of patching.
     *
     * <p>A hundred and twenty-eight sub-uploads of eight floats each is more
     * driver traffic than one upload of the buffer, and a change that large is a
     * demolition rather than a bullet hole.
     */
    private static final int PATCH_LIMIT = 128;

    /** Whether the mesh is armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Where a resolved base-terrain cell goes.
     *
     * <p>The same interface the ordinary command path implements, so the mesh and
     * the per-cell stream cannot disagree about what a cell looks like: there is
     * one resolver and two sinks, rather than two copies of the resolution.
     */
    public interface CellSink {
        /** This cell draws {@code sheet}'s sub-rectangle over its own square. */
        void quad(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH);

        /** This cell has no tile and paints {@code rgb} instead. */
        void fill(int rgb);
    }

    /** Resolves one cell's single base quad, or nothing at all. */
    public interface CellResolver {
        void resolve(int gridX, int gridY, CellSink sink);
    }

    private CellTopology bound;
    private int gridW;
    private int gridH;
    private long caughtUpTo;
    private boolean broken;

    private final List<Bucket> buckets = new ArrayList<>();
    private final Map<SpriteAPI, Bucket> bucketBySheet = new IdentityHashMap<>();

    /** Which bucket holds each cell's slot, by bucket index; {@code -1} for none. */
    private short[] bucketOf;
    private int[] slotOf;

    /** {@code 0xFF000000 | rgb} for a cell that paints a solid fill, else 0. */
    private int[] fillArgb;
    private int[] fillCells = new int[0];
    private int fillCellCount;

    /** Scratch for one sub-upload: four vertices of two floats. */
    private final FloatBuffer slotScratch = BufferUtils.createFloatBuffer(8);

    /** Cells to re-resolve this sync, and the stamp that keeps the list unique. */
    private int[] touched = new int[64];
    private int touchedCount;
    private long[] touchedStamp;
    private long syncStamp;

    /**
     * Catches the mesh up to {@code topology}, building it if this is a new
     * battle.
     *
     * @return whether the mesh can draw this frame; false means the caller owns
     *         the ground and must emit it cell by cell
     */
    public boolean sync(CellTopology topology, CellResolver resolver) {
        if (!catchUp(topology, resolver)) return false;
        try {
            for (Bucket bucket : buckets) bucket.upload();
            return !broken;
        } catch (RuntimeException failure) {
            broken = true;
            return false;
        }
    }

    /**
     * The half of {@link #sync} that touches no GL: decide whether this is a
     * build or a patch, and re-resolve the cells that need it.
     *
     * <p>Separate from the upload so it can be exercised without a context —
     * every rule worth pinning here (which cells a change re-resolves, whether a
     * slot is reused, when the log has been outrun) is arithmetic, and a test
     * that needed a GPU to ask about arithmetic would not be run.
     */
    boolean catchUp(CellTopology topology, CellResolver resolver) {
        if (!ENABLED || broken || topology == null) return false;
        try {
            if (topology != bound) {
                build(topology, resolver);
                return true;
            }
            long now = topology.changeCount();
            if (now == caughtUpTo) return true;
            if (now - caughtUpTo > topology.changeLogCapacity()) build(topology, resolver);
            else patch(topology, resolver, now);
            return true;
        } catch (RuntimeException failure) {
            broken = true;
            return false;
        }
    }

    /**
     * Whether this mesh already holds {@code topology}'s ground.
     *
     * <p>GL-free, because a collector is ({@code battle-render-nouns.md}, law
     * 2). It answers false for the frame in which a battle's ground is baked, so
     * that frame collects the ordinary per-cell stream and the bake happens in
     * its drain; from the next frame the mesh serves. It answers false forever
     * once anything has failed, which is what makes the fallback automatic
     * rather than something a host has to know about.
     */
    public boolean isServing(CellTopology topology) {
        return ENABLED && !broken && topology != null && topology == bound;
    }

    /** Cell indices painting a solid fill; valid to {@link #fillCellCount()}. */
    public int[] fillCells() {
        return fillCells;
    }

    public int fillCellCount() {
        return fillCellCount;
    }

    /** The packed RGB a fill cell paints. */
    public int fillRgb(int cellIndex) {
        return fillArgb[cellIndex] & 0xFFFFFF;
    }

    public int gridWidth() {
        return gridW;
    }

    /**
     * Visible for tests: the four screen-space corners, in cell units, that
     * {@code cellIndex} occupies, or null when it holds no slot.
     */
    float[] cellPos(int cellIndex) {
        return slotFloats(cellIndex, true);
    }

    /** Visible for tests: the four texture coordinates {@code cellIndex} holds. */
    float[] cellUv(int cellIndex) {
        return slotFloats(cellIndex, false);
    }

    /** Visible for tests: slots given back and available for the next cell that needs one. */
    int freeSlotCount() {
        int total = 0;
        for (Bucket bucket : buckets) total += bucket.freeCount;
        return total;
    }

    private float[] slotFloats(int cellIndex, boolean position) {
        if (bucketOf == null || cellIndex < 0 || cellIndex >= bucketOf.length) return null;
        short bucketIndex = bucketOf[cellIndex];
        if (bucketIndex < 0) return null;
        Bucket bucket = buckets.get(bucketIndex);
        float[] source = position ? bucket.pos : bucket.uv;
        return Arrays.copyOfRange(source, slotOf[cellIndex] * 8, slotOf[cellIndex] * 8 + 8);
    }

    /** Quads currently resident — what one frame no longer has to submit. */
    public int residentQuads() {
        int total = 0;
        for (Bucket bucket : buckets) total += bucket.used;
        return total;
    }

    /** Buffers drawn per frame: the whole ground layer's draw calls and texture binds. */
    public int bucketCount() {
        return buckets.size();
    }

    /**
     * Draws the resident ground.
     *
     * <p>Owns its complete GL lifecycle, as a custom pass must
     * ({@code battle-render-nouns.md}, law 3). The camera is a translate and a
     * scale on the modelview rather than an arithmetic pass over the vertices,
     * which is the whole reason panning costs nothing: the buffer holds cell
     * coordinates and {@code cellToScreenX(0)} is where cell zero lands.
     *
     * <p>Colour is one {@code glColor4f} with the array disabled, because every
     * ground quad is untinted and carries only the frame's alpha. Storing four
     * white vertices per cell would be another three megabytes of buffer saying
     * nothing.
     */
    public void draw(BattleCamera camera, float alphaMult) {
        if (broken || buckets.isEmpty() || camera == null) return;
        GlStateBracket bracket = GlStateBracket.textured2D();
        try {
            glColor4f(1f, 1f, 1f, alphaMult);
            // Which matrix the caller was editing is not knowable without a
            // glGet, and a glGet per frame stalls an async-renderer bridge. The
            // attrib bracket carries the mode back instead.
            glPushAttrib(GL_TRANSFORM_BIT);
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glTranslatef(camera.cellToScreenX(0f), camera.cellToScreenY(0f), 0f);
            float cell = camera.cellPxSize();
            glScalef(cell, cell, 1f);
            glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
            try {
                glEnableClientState(GL_VERTEX_ARRAY);
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                // Colour is uniform for the whole layer; an enabled colour array
                // left over from the previous batch would override it.
                glDisableClientState(GL_COLOR_ARRAY);
                glEnable(GL_TEXTURE_2D);
                for (Bucket bucket : buckets) bucket.draw();
                // Back to the client-array default the rest of the drain runs
                // under. The attrib bracket does not cover the buffer binding.
                glBindBuffer(GL_ARRAY_BUFFER, 0);
            } finally {
                glPopClientAttrib();
            }
            glPopMatrix();
            glPopAttrib();
        } catch (RuntimeException failure) {
            broken = true;
        } finally {
            bracket.close();
        }
    }

    /** Releases the buffers. The context that made them must still be current. */
    public void dispose() {
        for (Bucket bucket : buckets) bucket.dispose();
        buckets.clear();
        bucketBySheet.clear();
        bound = null;
        caughtUpTo = 0L;
        fillCellCount = 0;
    }

    // ---- build and patch -----------------------------------------------------

    private void build(CellTopology topology, CellResolver resolver) {
        for (Bucket bucket : buckets) bucket.dispose();
        buckets.clear();
        bucketBySheet.clear();

        bound = topology;
        gridW = topology.getWidth();
        gridH = topology.getHeight();
        int cells = gridW * gridH;
        bucketOf = new short[cells];
        slotOf = new int[cells];
        fillArgb = new int[cells];
        touchedStamp = new long[cells];
        Arrays.fill(bucketOf, (short) -1);

        Placement placement = new Placement();
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                placement.begin(y * gridW + x, x, y);
                resolver.resolve(x, y, placement);
                placement.commit();
            }
        }
        rebuildFillList();
        caughtUpTo = topology.changeCount();
    }

    /**
     * Re-resolves the cells the topology has recorded as changed, plus their
     * four neighbours.
     *
     * <p>The neighbours are not superstition: an autotile frame is chosen from
     * the wall-mask of the cells around it, so knocking a hole in a wall changes
     * the picture of everything it touched.
     */
    private void patch(CellTopology topology, CellResolver resolver, long now) {
        syncStamp++;
        touchedCount = 0;
        for (long sequence = caughtUpTo; sequence < now; sequence++) {
            int cell = topology.changedCellAt(sequence);
            if (cell < 0 || cell >= bucketOf.length) continue;
            int x = cell % gridW;
            int y = cell / gridW;
            touch(x, y);
            touch(x - 1, y);
            touch(x + 1, y);
            touch(x, y - 1);
            touch(x, y + 1);
        }
        caughtUpTo = now;

        Placement placement = new Placement();
        boolean fillsMoved = false;
        for (int i = 0; i < touchedCount; i++) {
            int cell = touched[i];
            int x = cell % gridW;
            int y = cell / gridW;
            int before = fillArgb[cell];
            placement.begin(cell, x, y);
            resolver.resolve(x, y, placement);
            placement.commit();
            if (fillArgb[cell] != before) fillsMoved = true;
        }
        if (fillsMoved) rebuildFillList();
    }

    private void touch(int x, int y) {
        if (x < 0 || y < 0 || x >= gridW || y >= gridH) return;
        int cell = y * gridW + x;
        if (touchedStamp[cell] == syncStamp) return;
        touchedStamp[cell] = syncStamp;
        if (touchedCount == touched.length) {
            touched = Arrays.copyOf(touched, touched.length * 2);
        }
        touched[touchedCount++] = cell;
    }

    private void rebuildFillList() {
        int count = 0;
        for (int argb : fillArgb) if (argb != 0) count++;
        if (fillCells.length < count) fillCells = new int[count];
        int at = 0;
        for (int cell = 0; cell < fillArgb.length; cell++) {
            if (fillArgb[cell] != 0) fillCells[at++] = cell;
        }
        fillCellCount = count;
    }

    /**
     * One cell's resolution, applied.
     *
     * <p>A resolver may emit a quad, a fill, or nothing, and this is what turns
     * whichever it was into a slot in a bucket — reusing the cell's existing slot
     * when the sheet has not moved, which is the case a bullet hole in a wall
     * actually takes.
     */
    private final class Placement implements CellSink {
        private int cell;
        private int gx;
        private int gy;
        private boolean placed;

        void begin(int cell, int gx, int gy) {
            this.cell = cell;
            this.gx = gx;
            this.gy = gy;
            this.placed = false;
            fillArgb[cell] = 0;
        }

        /** Anything the resolver did not claim gives up whatever slot it held. */
        void commit() {
            if (!placed) release(cell);
        }

        @Override
        public void quad(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH) {
            if (sheet == null || placed) return;
            placed = true;
            Bucket bucket = bucketFor(sheet);
            if (bucket == null) {
                release(cell);
                return;
            }
            int slot;
            if (bucketOf[cell] == bucket.index) {
                slot = slotOf[cell];
            } else {
                release(cell);
                slot = bucket.take();
                bucketOf[cell] = (short) bucket.index;
                slotOf[cell] = slot;
            }
            bucket.write(slot, gx, gy, srcX, srcY, srcW, srcH);
        }

        @Override
        public void fill(int rgb) {
            if (placed) return;
            placed = true;
            release(cell);
            fillArgb[cell] = 0xFF000000 | (rgb & 0xFFFFFF);
        }
    }

    private void release(int cell) {
        short bucketIndex = bucketOf[cell];
        if (bucketIndex < 0) return;
        buckets.get(bucketIndex).give(slotOf[cell]);
        bucketOf[cell] = -1;
    }

    private Bucket bucketFor(SpriteAPI sheet) {
        Bucket existing = bucketBySheet.get(sheet);
        if (existing != null) return existing;
        int pxW = Math.max(1, Math.round(sheet.getWidth()));
        int pxH = Math.max(1, Math.round(sheet.getHeight()));
        Bucket bucket = new Bucket(buckets.size(), sheet, pxW, pxH);
        buckets.add(bucket);
        bucketBySheet.put(sheet, bucket);
        return bucket;
    }

    // ---- one sheet's buffer --------------------------------------------------

    /**
     * Every cell drawing from one sheet, as two buffers.
     *
     * <p>Position and texture coordinates live in <em>separate</em> buffers
     * rather than interleaved in one. That is not a preference: a co-loaded
     * async-renderer bridge asserts that every attribute pointer is stride zero
     * from offset zero and crashes hard when one is not, which an interleaved
     * layout cannot satisfy. See {@code ClientArray} for the same constraint on
     * the client-array path.
     */
    private final class Bucket {
        private final int index;
        private final SpriteAPI sheet;
        private final int sheetPxW;
        private final int sheetPxH;

        private float[] pos = new float[SPARE_SLOTS * 8];
        private float[] uv = new float[SPARE_SLOTS * 8];
        private int used;
        private int[] free = new int[16];
        private int freeCount;

        private int posVbo;
        private int uvVbo;
        private boolean wholeDirty = true;
        private int[] dirtySlots = new int[PATCH_LIMIT];
        private int dirtyCount;

        Bucket(int index, SpriteAPI sheet, int sheetPxW, int sheetPxH) {
            this.index = index;
            this.sheet = sheet;
            this.sheetPxW = sheetPxW;
            this.sheetPxH = sheetPxH;
        }

        int take() {
            if (freeCount > 0) return free[--freeCount];
            if (used * 8 == pos.length) {
                int grown = pos.length * 2;
                pos = Arrays.copyOf(pos, grown);
                uv = Arrays.copyOf(uv, grown);
                wholeDirty = true;
            }
            return used++;
        }

        void give(int slot) {
            degenerate(slot);
            if (freeCount == free.length) free = Arrays.copyOf(free, free.length * 2);
            free[freeCount++] = slot;
        }

        /** A released slot keeps its place and draws nothing: four coincident vertices. */
        private void degenerate(int slot) {
            int at = slot * 8;
            for (int i = 0; i < 8; i++) pos[at + i] = 0f;
            markDirty(slot);
        }

        void write(int slot, int gx, int gy, int srcX, int srcY, int srcW, int srcH) {
            int at = slot * 8;
            float x0 = gx;
            float y0 = gy;
            float x1 = gx + 1f;
            float y1 = gy + 1f;
            pos[at] = x0;     pos[at + 1] = y0;
            pos[at + 2] = x1; pos[at + 3] = y0;
            pos[at + 4] = x1; pos[at + 5] = y1;
            pos[at + 6] = x0; pos[at + 7] = y1;

            // Same convention QuadBatch uses: source pixels are top-down and GL's
            // V is bottom-up, and getTextureWidth/Height is the image's normalised
            // extent inside a possibly padded texture.
            float texU = sheet.getTextureWidth();
            float texV = sheet.getTextureHeight();
            float u0 = ((float) srcX / sheetPxW) * texU;
            float u1 = ((float) (srcX + srcW) / sheetPxW) * texU;
            float v0 = texV - ((float) srcY / sheetPxH) * texV;
            float v1 = texV - ((float) (srcY + srcH) / sheetPxH) * texV;
            uv[at] = u0;     uv[at + 1] = v1;
            uv[at + 2] = u1; uv[at + 3] = v1;
            uv[at + 4] = u1; uv[at + 5] = v0;
            uv[at + 6] = u0; uv[at + 7] = v0;
            markDirty(slot);
        }

        private void markDirty(int slot) {
            if (wholeDirty) return;
            if (dirtyCount == dirtySlots.length) {
                wholeDirty = true;
                dirtyCount = 0;
                return;
            }
            dirtySlots[dirtyCount++] = slot;
        }

        void upload() {
            if (used == 0) return;
            if (posVbo == 0) {
                posVbo = glGenBuffers();
                uvVbo = glGenBuffers();
                wholeDirty = true;
            }
            if (wholeDirty) {
                uploadWhole();
                wholeDirty = false;
                dirtyCount = 0;
                return;
            }
            for (int i = 0; i < dirtyCount; i++) {
                int slot = dirtySlots[i];
                uploadSlot(posVbo, pos, slot);
                uploadSlot(uvVbo, uv, slot);
            }
            dirtyCount = 0;
        }

        private void uploadWhole() {
            int floats = used * 8;
            FloatBuffer buffer = BufferUtils.createFloatBuffer(floats);
            buffer.put(pos, 0, floats).flip();
            glBindBuffer(GL_ARRAY_BUFFER, posVbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            buffer.clear();
            buffer.put(uv, 0, floats).flip();
            glBindBuffer(GL_ARRAY_BUFFER, uvVbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        private void uploadSlot(int vbo, float[] data, int slot) {
            if (slot >= used) return;
            slotScratch.clear();
            slotScratch.put(data, slot * 8, 8).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferSubData(GL_ARRAY_BUFFER, (long) slot * 8 * Float.BYTES, slotScratch);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        void draw() {
            if (used == 0 || posVbo == 0) return;
            sheet.bindTexture();
            glBindBuffer(GL_ARRAY_BUFFER, posVbo);
            glVertexPointer(2, GL_FLOAT, 0, 0L);
            glBindBuffer(GL_ARRAY_BUFFER, uvVbo);
            glTexCoordPointer(2, GL_FLOAT, 0, 0L);
            glDrawArrays(GL_QUADS, 0, used * 4);
        }

        void dispose() {
            if (posVbo != 0) glDeleteBuffers(posVbo);
            if (uvVbo != 0) glDeleteBuffers(uvVbo);
            posVbo = 0;
            uvVbo = 0;
        }
    }
}
