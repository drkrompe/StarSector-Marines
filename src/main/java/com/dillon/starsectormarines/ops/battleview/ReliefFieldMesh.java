package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY;
import static org.lwjgl.opengl.GL11.GL_TRANSFORM_BIT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glDisable;
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
 * One of the ground composite's relief fields, resident on the GPU.
 *
 * <p><b>Why this and not a rebuild.</b> The height and normal targets were
 * re-rasterised as a quad per visible cell on every frame, and at whole-map
 * framing on the canonical 560x336 Conquest that is 188,000 cells twice.
 * {@code renderEvidence} measured the whole-map frame at 150 ms with 134 of it
 * in the GROUND drain, of which almost none was the ground itself — the
 * resident {@link GroundMesh} had already taken that away. What was left was
 * these two fields: a {@code HashMap} lookup, a terrain fingerprint (a five by
 * five neighbourhood scan on every street cell) and a batch append, per cell,
 * per field, per frame.
 *
 * <p><b>What was rebuilt for is invalidation, not the rebuild.</b> A roof caves
 * in and a wall is breached mid-battle, and a field held across frames would
 * keep shadowing a building that is no longer there. That is a correct reason to
 * invalidate one cell and it was being paid as a reason to redraw the map —
 * exactly the argument the resident ground answered. {@link CellTopology}
 * already records which cells may now draw differently, so a change here is a
 * patch of the affected cell and its four neighbours.
 *
 * <p><b>A cell owns a slot.</b> Every cell owns four vertices in the buffer for
 * whatever it draws from, and keeps them. Position is in <em>cell space</em>, so
 * the camera is one translate and one scale on the modelview and panning costs
 * nothing; UVs are the cell's own sub-rectangle of its derived atlas. A cell
 * with no derived art at all owns a slot in the untextured bucket instead,
 * which is what the flat-normal and macro-only fallbacks were already drawing.
 *
 * <p><b>Colour is the cell's own material signal</b> — the height field's
 * encoded macro metres, water identity and shore proximity, or the normal
 * field's flat encoding — carried per vertex as unsigned bytes. Bytes rather
 * than floats because the target is an RGBA8 texture: a float array would be
 * four times the memory to express the same 256 levels the field can hold.
 *
 * <p><b>It fails soft.</b> A driver without buffer objects, a failed allocation,
 * any GL error at all — {@link #sync} answers false and the caller re-rasterises
 * the field cell by cell with the same picture. Turn it off for a control run
 * with {@code -Dbattle.render.residentRelief=false}.
 */
final class ReliefFieldMesh {

    /** Control-run switch; see the class note. On by default. */
    static final String PROPERTY = "battle.render.residentRelief";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Slots past the baked count each bucket keeps spare; see {@code GroundMesh}. */
    private static final int SPARE_SLOTS = 256;

    /** Dirty slots past which a bucket re-uploads whole instead of patching. */
    private static final int PATCH_LIMIT = 128;

    /** Whether resident relief is armed at all — for evidence that reports which run it was. */
    static boolean enabled() {
        return ENABLED;
    }

    /** Where a resolved cell of the field goes. */
    interface CellSink {
        /**
         * This cell samples {@code sheet}'s sub-rectangle over its own square,
         * tinted with the material signal the field's shader reads off
         * {@code gl_Color}.
         *
         * @param sheetPxW the sheet's <em>content</em> width, which is not the
         *                 padded texture width {@code SpriteAPI} reports
         */
        void quad(SpriteAPI sheet, int sheetPxW, int sheetPxH,
                  int srcX, int srcY, int srcW, int srcH,
                  float r, float g, float b, float a);

        /** This cell has no derived art and writes {@code (r, g, b, a)} flat. */
        void solid(float r, float g, float b, float a);
    }

    /** Resolves one cell's single quad. Every cell resolves to exactly one. */
    interface CellResolver {
        void resolve(int gridX, int gridY, CellSink sink);
    }

    private final String label;

    private CellTopology bound;
    private int gridW;
    private int gridH;
    private long caughtUpTo;
    private boolean broken;

    /**
     * Bucket zero is the untextured one and always exists; the rest are one per
     * sheet, in the order cells first asked for them.
     */
    private final List<Bucket> buckets = new ArrayList<>();
    private final Map<SpriteAPI, Bucket> bucketBySheet = new IdentityHashMap<>();

    /** Which bucket holds each cell's slot; {@code -1} for none. */
    private short[] bucketOf;
    private int[] slotOf;

    private final FloatBuffer slotScratch = BufferUtils.createFloatBuffer(8);
    private final ByteBuffer colorScratch = BufferUtils.createByteBuffer(16);

    private int[] touched = new int[64];
    private int touchedCount;
    private long[] touchedStamp;
    private long syncStamp;

    ReliefFieldMesh(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return "ReliefFieldMesh[" + label + " quads=" + residentQuads()
                + " buckets=" + buckets.size() + (broken ? " broken" : "") + "]";
    }

    /**
     * Catches the field up to {@code topology}, building it if this is a new
     * battle, and uploads whatever moved.
     *
     * @return whether the field can be drawn from the buffers this frame; false
     *         means the caller owns it and must re-rasterise cell by cell
     */
    boolean sync(CellTopology topology, CellResolver resolver) {
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
     * The half of a sync that touches no GL: decide whether this is a build or a
     * patch, and re-resolve the cells that need it.
     *
     * <p>Separate from the upload so the rules can be exercised without a
     * context, exactly as {@code GroundMesh.catchUp} is. Which cells a change
     * re-resolves, whether a slot is reused, and when the change log has been
     * outrun are all arithmetic, and a test that needed a GPU to ask about
     * arithmetic would not be run.
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
     * Whether this field already holds {@code topology}'s relief.
     *
     * <p>GL-free. Answers false forever once anything has failed, which is what
     * makes the fallback automatic rather than something a caller has to know
     * about.
     */
    boolean isServing(CellTopology topology) {
        return ENABLED && !broken && topology != null && topology == bound;
    }

    /**
     * Whether residency is worth attempting at all.
     *
     * <p>Asked before the caller gathers a bake's inputs, so a field that has
     * failed does not have its macro relief and shore distances rebuilt every
     * frame on the way to being told so again.
     */
    boolean isUsable() {
        return ENABLED && !broken;
    }

    /**
     * Whether the field has to be re-resolved before it can be drawn again.
     *
     * <p>The caller owns the inputs a cell's colour is derived from — the macro
     * relief most of all, which is gathered from the building registry rather
     * than read off the topology — so it asks this before deciding whether to
     * build one.
     */
    boolean isBehind(CellTopology topology) {
        return topology == null || topology != bound || topology.changeCount() != caughtUpTo;
    }

    /** Quads currently resident — what one frame no longer has to rasterise. */
    int residentQuads() {
        int total = 0;
        for (Bucket bucket : buckets) total += bucket.used;
        return total;
    }

    /** Buffers drawn per frame: one untextured, plus one per derived sheet in use. */
    int bucketCount() {
        return buckets.size();
    }

    /** Visible for tests: slots given back and available for the next cell that needs one. */
    int freeSlotCount() {
        int total = 0;
        for (Bucket bucket : buckets) total += bucket.freeCount;
        return total;
    }

    /** Visible for tests: the four corners, in cell units, that {@code cellIndex} occupies. */
    float[] cellPos(int cellIndex) {
        Bucket bucket = bucketOfCell(cellIndex);
        if (bucket == null) return null;
        int at = slotOf[cellIndex] * 8;
        return Arrays.copyOfRange(bucket.pos, at, at + 8);
    }

    /** Visible for tests: the four texture coordinates {@code cellIndex} holds, or null when untextured. */
    float[] cellUv(int cellIndex) {
        Bucket bucket = bucketOfCell(cellIndex);
        if (bucket == null || bucket.sheet == null) return null;
        int at = slotOf[cellIndex] * 8;
        return Arrays.copyOfRange(bucket.uv, at, at + 8);
    }

    /** Visible for tests: {@code cellIndex}'s material signal as four 0..255 channels. */
    int[] cellColor(int cellIndex) {
        Bucket bucket = bucketOfCell(cellIndex);
        if (bucket == null) return null;
        int at = slotOf[cellIndex] * 16;
        int[] channels = new int[4];
        for (int i = 0; i < 4; i++) channels[i] = bucket.col[at + i] & 0xFF;
        return channels;
    }

    /** Visible for tests: whether {@code cellIndex} draws from a sheet at all. */
    boolean cellIsTextured(int cellIndex) {
        Bucket bucket = bucketOfCell(cellIndex);
        return bucket != null && bucket.sheet != null;
    }

    private Bucket bucketOfCell(int cellIndex) {
        if (bucketOf == null || cellIndex < 0 || cellIndex >= bucketOf.length) return null;
        short bucketIndex = bucketOf[cellIndex];
        return bucketIndex < 0 ? null : buckets.get(bucketIndex);
    }

    /**
     * Draws the whole resident field into whatever target is bound.
     *
     * <p>Owns its complete GL lifecycle, as a custom pass must
     * ({@code battle-render-nouns.md}, law 3). Blending is off, because a field
     * is written rather than composited: every cell owns its own square and the
     * clear beneath it is the datum, not something to blend with.
     *
     * <p>The untextured bucket goes first and the sheets follow, which is only a
     * habit — cells do not overlap, so nothing here paints over anything else.
     * {@code beginTextured} is where the field's own compose shader is bound;
     * a field that samples its sheets unmodified passes a no-op.
     */
    void draw(BattleCamera camera, Runnable beginTextured, Runnable endTextured) {
        if (broken || buckets.isEmpty() || camera == null) return;
        glPushAttrib(GL_ENABLE_BIT | GL_TEXTURE_BIT | GL_CURRENT_BIT
                | GL_TRANSFORM_BIT | GL_COLOR_BUFFER_BIT);
        try {
            glDisable(GL_BLEND);
            glDisable(GL_DEPTH_TEST);
            glColorMask(true, true, true, true);
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glTranslatef(camera.cellToScreenX(0f), camera.cellToScreenY(0f), 0f);
            float cell = camera.cellPxSize();
            glScalef(cell, cell, 1f);
            glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
            try {
                glEnableClientState(GL_VERTEX_ARRAY);
                glEnableClientState(GL_COLOR_ARRAY);
                glDisableClientState(GL_TEXTURE_COORD_ARRAY);
                glDisable(GL_TEXTURE_2D);
                for (Bucket bucket : buckets) {
                    if (bucket.sheet == null) bucket.draw();
                }
                if (hasTexturedWork()) {
                    if (beginTextured != null) beginTextured.run();
                    glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                    glEnable(GL_TEXTURE_2D);
                    for (Bucket bucket : buckets) {
                        if (bucket.sheet != null) bucket.draw();
                    }
                    if (endTextured != null) endTextured.run();
                }
                glBindBuffer(GL_ARRAY_BUFFER, 0);
            } finally {
                glPopClientAttrib();
            }
            glPopMatrix();
        } catch (RuntimeException failure) {
            broken = true;
        } finally {
            glPopAttrib();
        }
    }

    private boolean hasTexturedWork() {
        for (Bucket bucket : buckets) {
            if (bucket.sheet != null && bucket.used > 0) return true;
        }
        return false;
    }

    /** Releases the buffers. The context that made them must still be current. */
    void dispose() {
        for (Bucket bucket : buckets) bucket.dispose();
        buckets.clear();
        bucketBySheet.clear();
        bound = null;
        caughtUpTo = 0L;
    }

    // ---- build and patch -----------------------------------------------------

    private void build(CellTopology topology, CellResolver resolver) {
        for (Bucket bucket : buckets) bucket.dispose();
        buckets.clear();
        bucketBySheet.clear();
        buckets.add(new Bucket(0, null, 1, 1));

        bound = topology;
        gridW = topology.getWidth();
        gridH = topology.getHeight();
        int cells = gridW * gridH;
        bucketOf = new short[cells];
        slotOf = new int[cells];
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
        caughtUpTo = topology.changeCount();
    }

    /**
     * Re-resolves the cells the topology has recorded as changed, plus their four
     * neighbours — the neighbours because a derived atlas rectangle is chosen
     * from what stands beside the cell, exactly as the colour tile is.
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
        for (int i = 0; i < touchedCount; i++) {
            int cell = touched[i];
            placement.begin(cell, cell % gridW, cell / gridW);
            resolver.resolve(cell % gridW, cell / gridW, placement);
            placement.commit();
        }
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

    /**
     * One cell's resolution, applied — reusing the slot the cell already has
     * whenever it has not changed bucket, which is the case a bullet hole in a
     * wall actually takes.
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
        }

        /** Anything the resolver did not claim gives up whatever slot it held. */
        void commit() {
            if (!placed) release(cell);
        }

        @Override
        public void quad(SpriteAPI sheet, int sheetPxW, int sheetPxH,
                         int srcX, int srcY, int srcW, int srcH,
                         float r, float g, float b, float a) {
            if (placed) return;
            if (sheet == null) {
                solid(r, g, b, a);
                return;
            }
            placed = true;
            Bucket bucket = bucketFor(sheet, sheetPxW, sheetPxH);
            int slot = seat(bucket);
            bucket.write(slot, gx, gy, srcX, srcY, srcW, srcH, r, g, b, a);
        }

        @Override
        public void solid(float r, float g, float b, float a) {
            if (placed) return;
            placed = true;
            Bucket bucket = buckets.get(0);
            int slot = seat(bucket);
            bucket.writeSolid(slot, gx, gy, r, g, b, a);
        }

        private int seat(Bucket bucket) {
            if (bucketOf[cell] == bucket.index) return slotOf[cell];
            release(cell);
            int slot = bucket.take();
            bucketOf[cell] = (short) bucket.index;
            slotOf[cell] = slot;
            return slot;
        }
    }

    private void release(int cell) {
        short bucketIndex = bucketOf[cell];
        if (bucketIndex < 0) return;
        buckets.get(bucketIndex).give(slotOf[cell]);
        bucketOf[cell] = -1;
    }

    private Bucket bucketFor(SpriteAPI sheet, int sheetPxW, int sheetPxH) {
        Bucket existing = bucketBySheet.get(sheet);
        if (existing != null) return existing;
        Bucket bucket = new Bucket(buckets.size(), sheet,
                Math.max(1, sheetPxW), Math.max(1, sheetPxH));
        buckets.add(bucket);
        bucketBySheet.put(sheet, bucket);
        return bucket;
    }

    // ---- one sheet's buffers -------------------------------------------------

    /**
     * Every cell drawing from one sheet — or, for bucket zero, every cell drawing
     * from none — as three buffers.
     *
     * <p>Position, texture coordinates and colour live in <em>separate</em>
     * buffers rather than interleaved in one, for the reason {@code GroundMesh}
     * and {@code ClientArray} carry: a co-loaded async-renderer bridge asserts
     * that every attribute pointer is stride zero from offset zero and crashes
     * hard when one is not.
     */
    private final class Bucket {
        private final int index;
        private final SpriteAPI sheet;
        private final int sheetPxW;
        private final int sheetPxH;

        private float[] pos = new float[SPARE_SLOTS * 8];
        private float[] uv;
        private byte[] col = new byte[SPARE_SLOTS * 16];
        private int used;
        private int[] free = new int[16];
        private int freeCount;

        private int posVbo;
        private int uvVbo;
        private int colVbo;
        private boolean wholeDirty = true;
        private int[] dirtySlots = new int[PATCH_LIMIT];
        private int dirtyCount;

        Bucket(int index, SpriteAPI sheet, int sheetPxW, int sheetPxH) {
            this.index = index;
            this.sheet = sheet;
            this.sheetPxW = sheetPxW;
            this.sheetPxH = sheetPxH;
            if (sheet != null) this.uv = new float[SPARE_SLOTS * 8];
        }

        int take() {
            if (freeCount > 0) return free[--freeCount];
            if (used * 8 == pos.length) {
                pos = Arrays.copyOf(pos, pos.length * 2);
                col = Arrays.copyOf(col, col.length * 2);
                if (uv != null) uv = Arrays.copyOf(uv, uv.length * 2);
                wholeDirty = true;
            }
            return used++;
        }

        void give(int slot) {
            int at = slot * 8;
            for (int i = 0; i < 8; i++) pos[at + i] = 0f;
            markDirty(slot);
            if (freeCount == free.length) free = Arrays.copyOf(free, free.length * 2);
            free[freeCount++] = slot;
        }

        void writeSolid(int slot, int gx, int gy, float r, float g, float b, float a) {
            writePos(slot, gx, gy);
            writeColor(slot, r, g, b, a);
            markDirty(slot);
        }

        void write(int slot, int gx, int gy, int srcX, int srcY, int srcW, int srcH,
                   float r, float g, float b, float a) {
            writePos(slot, gx, gy);
            // Same convention QuadBatch uses: source pixels are top-down and GL's
            // V is bottom-up, and getTextureWidth/Height is the image's normalised
            // extent inside a possibly padded texture.
            int at = slot * 8;
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
            writeColor(slot, r, g, b, a);
            markDirty(slot);
        }

        private void writePos(int slot, int gx, int gy) {
            int at = slot * 8;
            float x0 = gx;
            float y0 = gy;
            float x1 = gx + 1f;
            float y1 = gy + 1f;
            pos[at] = x0;     pos[at + 1] = y0;
            pos[at + 2] = x1; pos[at + 3] = y0;
            pos[at + 4] = x1; pos[at + 5] = y1;
            pos[at + 6] = x0; pos[at + 7] = y1;
        }

        private void writeColor(int slot, float r, float g, float b, float a) {
            byte cr = channel(r);
            byte cg = channel(g);
            byte cb = channel(b);
            byte ca = channel(a);
            int at = slot * 16;
            for (int vertex = 0; vertex < 4; vertex++) {
                col[at] = cr; col[at + 1] = cg; col[at + 2] = cb; col[at + 3] = ca;
                at += 4;
            }
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
                colVbo = glGenBuffers();
                if (uv != null) uvVbo = glGenBuffers();
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
                if (slot >= used) continue;
                uploadSlotFloats(posVbo, pos, slot);
                if (uv != null) uploadSlotFloats(uvVbo, uv, slot);
                uploadSlotColor(slot);
            }
            dirtyCount = 0;
        }

        /**
         * Uploads the backing arrays whole, spare slots and all.
         *
         * <p>The spare capacity is not waste, it is the fix for a sub-upload
         * that would otherwise land past the end of the buffer. A cell that
         * moves between buckets — a wall breached over a floor, which is
         * routine here — takes a slot the last upload had no room for, and
         * {@code glBufferSubData} at that offset writes nothing at all and says
         * so only through {@code glGetError}. Sizing the buffer to the array
         * rather than to the slots in use means every offset a patch can name
         * is already inside it; nothing past {@code used} is ever drawn, since
         * the draw counts vertices.
         */
        private void uploadWhole() {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(pos.length);
            buffer.put(pos).flip();
            glBindBuffer(GL_ARRAY_BUFFER, posVbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            if (uv != null) {
                buffer.clear();
                buffer.put(uv).flip();
                glBindBuffer(GL_ARRAY_BUFFER, uvVbo);
                glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            }
            ByteBuffer colors = BufferUtils.createByteBuffer(col.length);
            colors.put(col).flip();
            glBindBuffer(GL_ARRAY_BUFFER, colVbo);
            glBufferData(GL_ARRAY_BUFFER, colors, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        private void uploadSlotFloats(int vbo, float[] data, int slot) {
            slotScratch.clear();
            slotScratch.put(data, slot * 8, 8).flip();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferSubData(GL_ARRAY_BUFFER, (long) slot * 8 * Float.BYTES, slotScratch);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        private void uploadSlotColor(int slot) {
            colorScratch.clear();
            colorScratch.put(col, slot * 16, 16).flip();
            glBindBuffer(GL_ARRAY_BUFFER, colVbo);
            glBufferSubData(GL_ARRAY_BUFFER, (long) slot * 16, colorScratch);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        void draw() {
            if (used == 0 || posVbo == 0) return;
            if (sheet != null) sheet.bindTexture();
            glBindBuffer(GL_ARRAY_BUFFER, posVbo);
            glVertexPointer(2, GL_FLOAT, 0, 0L);
            glBindBuffer(GL_ARRAY_BUFFER, colVbo);
            glColorPointer(4, GL_UNSIGNED_BYTE, 0, 0L);
            if (uvVbo != 0) {
                glBindBuffer(GL_ARRAY_BUFFER, uvVbo);
                glTexCoordPointer(2, GL_FLOAT, 0, 0L);
            }
            glDrawArrays(GL_QUADS, 0, used * 4);
        }

        void dispose() {
            if (posVbo != 0) glDeleteBuffers(posVbo);
            if (uvVbo != 0) glDeleteBuffers(uvVbo);
            if (colVbo != 0) glDeleteBuffers(colVbo);
            posVbo = 0;
            uvVbo = 0;
            colVbo = 0;
        }
    }

    /** A 0..1 signal as the 0..255 channel the RGBA8 field actually holds. */
    private static byte channel(float value) {
        int level = Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
        return (byte) level;
    }
}
