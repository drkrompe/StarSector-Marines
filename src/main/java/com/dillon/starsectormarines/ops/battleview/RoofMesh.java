package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY;
import static org.lwjgl.opengl.GL11.GL_TRANSFORM_BIT;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glColorPointer;
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
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;

/**
 * The battle's roofs, resident on the GPU as vertex buffers.
 *
 * <p><b>Why.</b> {@code renderEvidence} on the 280x168 control at whole-map
 * framing: {@code ROOFS} is 6,035 commands leaving as <em>one draw</em>, and
 * costs 0.60 ms to collect against 0.20 to submit. That is the opposite shape
 * to the one an atlas answers — the layer already coalesces perfectly, and what
 * it is paying for is building the same six thousand commands again on every
 * frame of a battle in which nothing about them has moved. Residency is the
 * answer to submitting the same thing again ({@code battle-render-nouns.md},
 * law 20), and a roof is a function of the topology in exactly the way a ground
 * tile is.
 *
 * <p><b>What varies and what does not.</b> A roof cell's pieces are a function
 * of the topology ({@link RoofTiles}) and its tint belongs to its building, so
 * both are settled at the bake. What moves every frame is one number per
 * building: the fade that reveals an interior with the player's units inside
 * it. So the geometry is baked once and a building whose fade has moved is an
 * <em>alpha patch</em> of its own vertices — which is one contiguous run,
 * because a building's cells are baked together: its floor, then the walls its
 * roof is laid over ({@link com.dillon.starsectormarines.battle.world.model.RoofCover}).
 *
 * <p><b>A cell holds {@link RoofTiles#MAX_QUADS} slots.</b> An eave is the
 * field cut short and its trim, an inside corner two rectangles of field and
 * its trim; a cell drawing fewer leaves the rest degenerate. A fixed share per
 * cell keeps a re-resolve in place, whatever the cell becomes.
 *
 * <p><b>Damage is a patch, not a rebuild.</b> A blow to a roof is recorded in
 * {@link CellTopology}'s change log (law 21, which is why that log is one list
 * rather than one per reader), and the cell it names re-resolves to its broken
 * frame. Its neighbours re-resolve with it, and only they: a cell's cover is cut
 * by whether the cells beside it are outside, which a fallen wall changes.
 *
 * <p><b>It fails soft.</b> A driver without buffer objects, a failed allocation,
 * any GL error at all — {@code sync} answers false and the caller emits the
 * ordinary per-cell command stream, with the same paint order and the same
 * picture.
 */
public final class RoofMesh {

    /**
     * The fade below which a roof is not drawn at all.
     *
     * <p>The same threshold the command stream uses, so the two paint the same
     * picture: a building faded this far is one the streamed path skips
     * entirely, and a resident quad at that alpha would put a couple of levels
     * of roof over a revealed interior.
     */
    private static final float INVISIBLE_BELOW = 0.01f;

    private static final int SLOTS = RoofTiles.MAX_QUADS;

    /**
     * Resolves one roof cell into the pieces it draws ({@link RoofTiles.Sink}),
     * or nothing at all.
     *
     * <p>The same interface the ordinary command path implements, so the mesh
     * and the per-cell stream cannot disagree about what a roof looks like:
     * there is one resolver and two sinks.
     */
    public interface RoofResolver {
        void resolve(Building building, int gridX, int gridY, RoofTiles.Sink sink);
    }

    /** One building's contiguous run of slots, and the fade its vertices carry. */
    private static final class Span {
        final Building building;
        final int firstSlot;
        final int slotCount;
        float alpha = Float.NaN;

        Span(Building building, int firstSlot, int slotCount) {
            this.building = building;
            this.firstSlot = firstSlot;
            this.slotCount = slotCount;
        }
    }

    private CellTopology bound;
    private Buildings boundBuildings;
    private SpriteAPI sheet;
    private int sheetPxW;
    private int sheetPxH;
    private int gridW;
    private long caughtUpTo;
    private boolean broken;

    private final List<Span> spans = new ArrayList<>();
    /** The first of the slots each cell holds, or {@code -1}; sized to the grid. */
    private int[] slotOfCell;
    /** The building each slot-holding cell is roofed by, by its first slot over {@link #SLOTS}. */
    private Building[] buildingOfCell = new Building[0];

    private float[] pos = new float[0];
    private float[] uv = new float[0];
    private float[] col = new float[0];
    private int used;

    private int posVbo;
    private int uvVbo;
    private int colVbo;

    /** Slots whose geometry or colour has moved since the last upload; {@code -1} for none. */
    private int geometryFrom = -1;
    private int geometryTo = -1;
    private int colourFrom = -1;
    private int colourTo = -1;

    /** Cells re-resolved by the last catch-up; see {@link #lastResolvedCells()}. */
    private int lastResolvedCells;

    /** Which cells this patch has re-resolved already, by stamp, so a cell two changes touch resolves once. */
    private long[] patchSeen = new long[0];
    private long patchStamp;

    /**
     * Whether this mesh already holds {@code topology}'s roofs.
     *
     * <p>GL-free, because a collector is ({@code battle-render-nouns.md}, law
     * 2). It answers false for the frame in which a battle's roofs are baked, so
     * that frame collects the ordinary per-cell stream and the bake happens in
     * its drain; from the next frame the mesh serves. It answers false forever
     * once anything has failed.
     */
    public boolean isServing(CellTopology topology, Buildings buildings) {
        return !broken && topology != null && topology == bound
                && buildings == boundBuildings;
    }

    /** Quad slots currently resident — what one frame no longer has to submit. */
    public int residentQuads() {
        return used;
    }

    /** Cells re-resolved by the last catch-up; every roofed cell for a bake. */
    public int lastResolvedCells() {
        return lastResolvedCells;
    }

    /** Visible for tests: the first slot {@code (x, y)} holds, or {@code -1}. */
    int slotOf(int x, int y) {
        if (slotOfCell == null || gridW <= 0) return -1;
        int cell = y * gridW + x;
        return cell < 0 || cell >= slotOfCell.length ? -1 : slotOfCell[cell];
    }

    /** Visible for tests: the four corners, in cell units, a slot occupies. */
    float[] positionAt(int slot) {
        return Arrays.copyOfRange(pos, slot * 8, slot * 8 + 8);
    }

    /** Visible for tests: a slot's four texture coordinates, as {@code u, v} pairs. */
    float[] uvAt(int slot) {
        return Arrays.copyOfRange(uv, slot * 8, slot * 8 + 8);
    }

    /** Visible for tests: one slot's first vertex colour, as {@code r, g, b, a}. */
    float[] colourAt(int slot) {
        return Arrays.copyOfRange(col, slot * 16, slot * 16 + 4);
    }

    /**
     * Catches the mesh up to {@code topology} and {@code buildings} and uploads
     * what moved, building it if this is a new battle.
     *
     * <p>Owns its complete GL lifecycle, as a custom pass must (law 3).
     *
     * @param frameAlpha the frame's own fade, multiplied into every roof's own
     * @return whether the mesh can draw this frame; false means the caller owns
     *         the roofs and must emit them cell by cell
     */
    public boolean sync(CellTopology topology, Buildings buildings, SpriteAPI roofSheet,
                        int roofSheetPxW, int roofSheetPxH,
                        RoofResolver resolver, float frameAlpha) {
        if (!catchUp(topology, buildings, roofSheet, roofSheetPxW, roofSheetPxH,
                resolver, frameAlpha)) {
            return false;
        }
        try {
            upload();
            return !broken;
        } catch (RuntimeException failure) {
            broken = true;
            return false;
        }
    }

    /**
     * The half of {@link #sync} that touches no GL: decide whether this is a
     * build or a patch, and re-resolve or re-shade what needs it.
     *
     * <p>Separate from the upload so it can be exercised without a context —
     * every rule worth pinning here is arithmetic, and a test that needed a GPU
     * to ask about arithmetic would not be run.
     */
    boolean catchUp(CellTopology topology, Buildings buildings, SpriteAPI roofSheet,
                    int roofSheetPxW, int roofSheetPxH,
                    RoofResolver resolver, float frameAlpha) {
        if (broken || topology == null || buildings == null
                || roofSheet == null || resolver == null) {
            return false;
        }
        try {
            if (topology != bound || buildings != boundBuildings || roofSheet != sheet) {
                build(topology, buildings, roofSheet, roofSheetPxW, roofSheetPxH, resolver);
            } else {
                long now = topology.changeCount();
                if (now != caughtUpTo) {
                    if (now - caughtUpTo > topology.changeLogCapacity()) {
                        build(topology, buildings, roofSheet, roofSheetPxW, roofSheetPxH,
                                resolver);
                    } else {
                        patch(topology, resolver, now);
                    }
                } else {
                    lastResolvedCells = 0;
                }
            }
            shade(frameAlpha);
            return true;
        } catch (RuntimeException failure) {
            broken = true;
            return false;
        }
    }

    /**
     * Draws the resident roofs.
     *
     * <p>The camera is a translate and a scale on the modelview rather than an
     * arithmetic pass over the vertices, which is what makes panning free: the
     * buffer holds cell coordinates and {@code cellToScreenX(0)} is where cell
     * zero lands. Unlike the ground, the colour array is enabled and carries
     * everything per-quad — a building's tint and its fade — because those are
     * exactly what differs between one roof and the next.
     */
    public void draw(BattleCamera camera) {
        if (broken || used == 0 || posVbo == 0 || camera == null || sheet == null) return;
        GlStateBracket bracket = GlStateBracket.textured2D();
        try {
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
                glEnableClientState(GL_COLOR_ARRAY);
                glEnable(GL_TEXTURE_2D);
                sheet.bindTexture();
                glBindBuffer(GL_ARRAY_BUFFER, posVbo);
                glVertexPointer(2, GL_FLOAT, 0, 0L);
                glBindBuffer(GL_ARRAY_BUFFER, uvVbo);
                glTexCoordPointer(2, GL_FLOAT, 0, 0L);
                glBindBuffer(GL_ARRAY_BUFFER, colVbo);
                glColorPointer(4, GL_FLOAT, 0, 0L);
                glDrawArrays(GL_QUADS, 0, used * 4);
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
        if (posVbo != 0) glDeleteBuffers(posVbo);
        if (uvVbo != 0) glDeleteBuffers(uvVbo);
        if (colVbo != 0) glDeleteBuffers(colVbo);
        posVbo = 0;
        uvVbo = 0;
        colVbo = 0;
        bound = null;
        boundBuildings = null;
        sheet = null;
        spans.clear();
        used = 0;
        caughtUpTo = 0L;
        broken = false;
        geometryFrom = geometryTo = -1;
        colourFrom = colourTo = -1;
    }

    // ---- build, patch, shade -------------------------------------------------

    private void build(CellTopology topology, Buildings buildings, SpriteAPI roofSheet,
                       int roofSheetPxW, int roofSheetPxH, RoofResolver resolver) {
        bound = topology;
        boundBuildings = buildings;
        sheet = roofSheet;
        sheetPxW = Math.max(1, roofSheetPxW);
        sheetPxH = Math.max(1, roofSheetPxH);
        gridW = topology.getWidth();
        int cells = gridW * topology.getHeight();
        slotOfCell = new int[cells];
        Arrays.fill(slotOfCell, -1);
        spans.clear();

        // Each building's cells: its floor, then the walls its roof is laid over.
        int[] claimed = new int[cells];
        List<int[]> cellsOf = new ArrayList<>();
        int total = 0;
        for (Building b : buildings.all()) {
            int[] own = RoofPlans.roofedCells(topology, b, claimed);
            cellsOf.add(own);
            total += own.length;
        }
        pos = new float[total * SLOTS * 8];
        uv = new float[total * SLOTS * 8];
        col = new float[total * SLOTS * 16];
        buildingOfCell = new Building[total];
        used = total * SLOTS;

        int slot = 0;
        int index = 0;
        for (Building b : buildings.all()) {
            int[] own = cellsOf.get(index++);
            spans.add(new Span(b, slot, own.length * SLOTS));
            for (int at : own) {
                slotOfCell[at] = slot;
                buildingOfCell[slot / SLOTS] = b;
                for (int k = 0; k < SLOTS; k++) writeTint(slot + k, b);
                resolveInto(slot, at % gridW, at / gridW, resolver);
                slot += SLOTS;
            }
        }
        lastResolvedCells = total;
        caughtUpTo = topology.changeCount();
        geometryFrom = 0;
        geometryTo = used;
        colourFrom = 0;
        colourTo = used;
        wholeUpload = true;
    }

    /**
     * Re-resolves the cells the topology has recorded as changed, and the cells
     * beside each.
     *
     * <p>The neighbours because a roof's cover is cut by whether the cells
     * beside it are outside: a wall that falls turns the eaves either side of it
     * into corners. A cell's own frame needs nothing else, so the sweep stops
     * one cell out.
     */
    private void patch(CellTopology topology, RoofResolver resolver, long now) {
        int resolved = 0;
        long stamp = ++patchStamp;
        if (patchSeen.length != slotOfCell.length) patchSeen = new long[slotOfCell.length];
        int gridH = topology.getHeight();
        for (long sequence = caughtUpTo; sequence < now; sequence++) {
            int cell = topology.changedCellAt(sequence);
            if (cell < 0 || cell >= slotOfCell.length) continue;
            int cx = cell % gridW;
            int cy = cell / gridW;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= gridW || ny >= gridH) continue;
                    int at = ny * gridW + nx;
                    int slot = slotOfCell[at];
                    if (slot < 0 || patchSeen[at] == stamp) continue;
                    patchSeen[at] = stamp;
                    resolveInto(slot, nx, ny, resolver);
                    resolved++;
                }
            }
        }
        caughtUpTo = now;
        lastResolvedCells = resolved;
    }

    /** Writes the fade every building's vertices carry, for the ones that moved. */
    private void shade(float frameAlpha) {
        for (Span span : spans) {
            float own = span.building.currentAlpha;
            float effective = own <= INVISIBLE_BELOW ? 0f
                    : Math.max(0f, Math.min(1f, own * frameAlpha));
            if (Math.abs(effective - span.alpha) < 1f / 512f) continue;
            span.alpha = effective;
            int at = span.firstSlot * 16;
            int end = at + span.slotCount * 16;
            for (int i = at + 3; i < end; i += 4) col[i] = effective;
            markColour(span.firstSlot, span.firstSlot + span.slotCount);
        }
    }

    private void writeTint(int slot, Building b) {
        int at = slot * 16;
        for (int vertex = 0; vertex < 4; vertex++) {
            col[at++] = RoofTiles.tint(b.tintR);
            col[at++] = RoofTiles.tint(b.tintG);
            col[at++] = RoofTiles.tint(b.tintB);
            col[at++] = 0f;
        }
    }

    /** Applies one cell's resolution into its slots, degenerating what it leaves unused. */
    private void resolveInto(int slot, int cx, int cy, RoofResolver resolver) {
        placedSlot = slot;
        placedCellX = cx;
        placedCellY = cy;
        placed = 0;
        resolver.resolve(buildingOfCell[slot / SLOTS], cx, cy, sink);
        for (int k = placed; k < SLOTS; k++) degenerate(slot + k);
        markGeometry(slot);
        markGeometry(slot + SLOTS - 1);
    }

    /** A slot with nothing to draw keeps its place: four coincident vertices. */
    private void degenerate(int slot) {
        int at = slot * 8;
        for (int i = 0; i < 8; i++) pos[at + i] = 0f;
    }

    // Scratch for the cell currently being resolved; the sink is one object
    // rather than a lambda per cell, because a bake resolves the whole roof.
    private int placedSlot;
    private int placedCellX;
    private int placedCellY;
    private int placed;
    private boolean wholeUpload = true;

    private final RoofTiles.Sink sink = (column, row, turns, x0, y0, x1, y1) -> {
        if (placed >= SLOTS) return;
        int at = (placedSlot + placed++) * 8;
        pos[at] = placedCellX + x0;     pos[at + 1] = placedCellY + y0;
        pos[at + 2] = placedCellX + x1; pos[at + 3] = placedCellY + y0;
        pos[at + 4] = placedCellX + x1; pos[at + 5] = placedCellY + y1;
        pos[at + 6] = placedCellX + x0; pos[at + 7] = placedCellY + y1;
        cornerUv(at, column, row, turns, x0, y0);
        cornerUv(at + 2, column, row, turns, x1, y0);
        cornerUv(at + 4, column, row, turns, x1, y1);
        cornerUv(at + 6, column, row, turns, x0, y1);
    };

    /**
     * The texture coordinate of one corner: the frame point the turned frame
     * puts there ({@link RoofTiles#frameU}), held half a texel inside the frame
     * so a bilinear tap never reaches the next one. Same convention QuadBatch
     * uses: sheet pixels are top-down and GL's V is bottom-up, and
     * getTextureWidth/Height is the image's normalised extent inside a possibly
     * padded texture.
     */
    private void cornerUv(int at, int column, int row, int turns, float px, float py) {
        float frameW = sheetPxW / (float) RoofTiles.COLUMNS;
        float frameH = sheetPxH / (float) RoofStyle.COUNT;
        float u = RoofTiles.insideFrame(RoofTiles.frameU(turns, px, py), frameW);
        float v = RoofTiles.insideFrame(RoofTiles.frameV(turns, px, py), frameH);
        float texU = sheet.getTextureWidth();
        float texV = sheet.getTextureHeight();
        uv[at] = (column + u) * frameW / sheetPxW * texU;
        uv[at + 1] = texV - (row + 1f - v) * frameH / sheetPxH * texV;
    }

    private void markGeometry(int slot) {
        geometryFrom = geometryFrom < 0 ? slot : Math.min(geometryFrom, slot);
        geometryTo = Math.max(geometryTo, slot + 1);
    }

    private void markColour(int from, int to) {
        colourFrom = colourFrom < 0 ? from : Math.min(colourFrom, from);
        colourTo = Math.max(colourTo, to);
    }

    // ---- upload --------------------------------------------------------------

    /**
     * Uploads what moved.
     *
     * <p>A dirty <em>range</em> rather than a list of slots, because the change
     * this layer actually takes is a whole building's fade and a building's
     * cells are one contiguous run. A list would overflow into a whole re-upload
     * on the first roof that faded.
     */
    private void upload() {
        if (used == 0) return;
        if (posVbo == 0) {
            posVbo = glGenBuffers();
            uvVbo = glGenBuffers();
            colVbo = glGenBuffers();
            wholeUpload = true;
        }
        if (wholeUpload) {
            uploadWhole(posVbo, pos, GL_STATIC_DRAW);
            uploadWhole(uvVbo, uv, GL_STATIC_DRAW);
            uploadWhole(colVbo, col, GL_DYNAMIC_DRAW);
            wholeUpload = false;
        } else {
            uploadRange(posVbo, pos, 8, geometryFrom, geometryTo);
            uploadRange(uvVbo, uv, 8, geometryFrom, geometryTo);
            uploadRange(colVbo, col, 16, colourFrom, colourTo);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        geometryFrom = geometryTo = -1;
        colourFrom = colourTo = -1;
    }

    private static void uploadWhole(int vbo, float[] data, int usage) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, usage);
    }

    private void uploadRange(int vbo, float[] data, int floatsPerSlot, int from, int to) {
        if (from < 0 || to <= from) return;
        int first = Math.max(0, from) * floatsPerSlot;
        int last = Math.min(used, to) * floatsPerSlot;
        if (last <= first) return;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(last - first);
        buffer.put(data, first, last - first).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, (long) first * Float.BYTES, buffer);
    }
}
