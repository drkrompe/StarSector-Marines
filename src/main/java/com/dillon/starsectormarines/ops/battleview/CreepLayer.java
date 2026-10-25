package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.evacuation.NestTrails;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.lwjgl.BufferUtils;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ALPHA;
import static org.lwjgl.opengl.GL11.GL_ALPHA8;
import static org.lwjgl.opengl.GL11.GL_LUMINANCE8_ALPHA8;
import static org.lwjgl.opengl.GL11.GL_LUMINANCE_ALPHA;
import static org.lwjgl.opengl.GL11.GL_CLIENT_PIXEL_STORE_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.GL_TEXTURE3;
import static org.lwjgl.opengl.GL13.GL_TEXTURE4;
import static org.lwjgl.opengl.GL13.GL_TEXTURE5;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_GENERATE_MIPMAP;

/**
 * Hive creep, resident on the GPU (`colony-breach-nouns.md`, Creep).
 *
 * <p>The simulation's {@link CreepField} is three bytes per cell; this layer
 * keeps a CPU mirror of them, plus whether the cell is a wall, as one RGBA
 * texel per cell, and patches the mirror and the texture only in the {@link
 * CreepField#TILE}-cell tiles whose revision moved, or whose topology did (a
 * wall knocked down stops carrying crust). Collection is the catch-up
 * (GL-free) and one custom command; the drain uploads the changed tiles and
 * draws one quad over the creep's extent through {@link CreepShader}. A frame
 * with no change is one draw and no upload, however much of the map is
 * infested.
 *
 * <p>The layer drains inside the ground's colour target, straight after the
 * ground itself, so the relief composite displaces it with the ground under it
 * and a crust on a wall top moves with the wall, and the walls' shadows fall
 * across it.
 *
 * <p>A second, per-cell picture of the ground under the creep is kept beside the
 * field: whether the cell is vegetation (grass, crops, the plants on them) and its
 * <em>blight</em>, the creep's coverage spread {@link #BLIGHT_CELLS} cells out and
 * fading with distance, over vegetation only. It is what lets grass and crops
 * wilt and take veins ahead of the creep's edge where pavement keeps a clean one.
 * It is recomputed for the tiles that changed and their neighbours.
 *
 * <p>Off GL (a review frame) the same command carries the same numbers and the
 * offline drain shades the mirror through {@link CreepShading}. If the shader
 * fails, the layer falls back for good to flat cells in the palette's base
 * colour. Turn the layer off for a control run with
 * {@code -Dbattle.render.creep=false}.
 *
 * <p>Raised ground's cliff faces and ramp walls are drawn by {@link CliffArtLayer}
 * under the creep, so creep over them would hide every ridge and bank in a bloom.
 * The layer keeps the layout's {@link CliffFaceMask} beside the field and the
 * creep stands back from the rock, veining and filming it where it stands round.
 *
 * <p>Water has no creep, so the field's coverage falls away at a water cell's
 * square edge, while {@link WaterShore} draws the waterline as a contour. On the
 * shore the mirror lends each water cell its creeped neighbours' coverage, and
 * the layer keeps a {@link ShoreWaterMask} beside the field that the creep
 * stands back from as from a cliff face: its edge runs along the wet band,
 * over the corners the contour cuts to bank and short of those it floods.
 */
final class CreepLayer implements RenderSystem, CreepShading.Field {

    /** Control-run switch; on by default. */
    static final String PROPERTY = "battle.render.creep";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * Control-run switch for the nest's roots ({@link NestTrails}); on by default. renderEvidence control lever:
     * remove it with its off arm once its renderEvidence reading has been accepted.
     */
    static final String TRAILS_PROPERTY = "battle.render.nestTrails";

    private static final boolean TRAILS = Boolean.parseBoolean(System.getProperty(TRAILS_PROPERTY, "true"));

    /** Whether creep is drawn at all; for evidence that reports which run it was. */
    static boolean enabled() { return ENABLED; }

    /** How far, in cells, the creep's blight reaches over vegetation ahead of its edge. */
    static final int BLIGHT_CELLS = 3;

    /**
     * Tiles whose blight is recomputed for each tick the field has advanced since the last catch-up. The field
     * dirties most tiles it is growing in every tick, and a tile's blight costs tens of microseconds, so recomputing
     * every due tile each frame cost over a millisecond on a spreading outbreak. The blight is a slow, soft halo, so
     * due tiles are taken in turn: a live frame pays for a tick or two, and a review frame drawn hundreds of ticks
     * after the last one catches every due tile up.
     */
    static final int BLIGHT_TILES_PER_TICK = 3;

    /** Changed tiles above which a frame re-uploads the whole field rather than tile by tile. */
    private static final int TILE_UPLOAD_LIMIT = 48;

    private final CreepShader shader = new CreepShader();
    private final CreepPass pass = new CreepPass(this);
    private final CreepPalette palette = CreepPalette.active();

    private CreepField bound;
    private int gridW, gridH, tilesX, tilesY;
    private byte[] mirror;
    /**
     * Per cell: blight, vegetated, and the nest's roots ({@link NestTrails#rasterize}: distance to a root's edge, and
     * its half-width there), uploaded beside the field.
     */
    private byte[] ground;
    /** The roots last laid into {@link #ground}, and the array they were laid into. */
    private long trailSignature;
    private byte[] trailGround;
    /** Tiles holding any vegetation; a tile without any has no blight to compute. */
    private boolean[] vegetatedTile;
    /** Vegetated tiles whose blight is stale, and where the round-robin over them resumes. */
    private boolean[] blightDue;
    private int blightCursor;
    private int blightTick = Integer.MIN_VALUE;
    private int[] blightRow;
    private long[] seen;
    /** Tiles whose walls changed since they were last copied. */
    private boolean[] forced;
    private boolean[] dirtyTile;
    private int dirtyCount;
    private boolean wholeDirty;
    private int extentMinX = Integer.MAX_VALUE, extentMinY = Integer.MAX_VALUE, extentMaxX = -1, extentMaxY = -1;

    private int fieldTexture, noiseTexture, surfaceTexture, groundTexture, faceTexture, shoreTexture;
    /** The shore's water mask (null with no organic shore), whether it is on the GPU, and its water cells. */
    private ShoreWaterMask shore;
    private boolean shoreUploaded;
    private boolean[] shoreWater;
    /** The layout the face mask was made from, the mask (null with no raised ground), and whether it is on the GPU. */
    private ElevationLayout faceLayout;
    private CliffFaceMask faces;
    private boolean facesUploaded;
    private CellTopology topology;
    private long topologySeen;
    private SunLight sun = new SunLight();
    private boolean broken;
    private float clockSeconds;
    private ByteBuffer scratch;

    @Override
    public RenderLayer layer() { return RenderLayer.CREEP; }

    /** The scene's sun, which the creep shades its relief and casts its own shadow from. */
    void useSun(SunLight sun) {
        if (sun != null) this.sun = sun;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (!ENABLED) return;
        CreepField field = ctx.sim.creepFieldIfPresent();
        if (field == null) return;
        catchUp(field, ctx.sim.getGrid(), ctx.sim.getTopology());
        layTrails(ctx.sim);
        if (extentMaxX < extentMinX) return;
        BattleCamera camera = ctx.camera;
        VisibleCellRect view = camera.visibleCells(1, gridW, gridH);
        // Margin: the softened edge reaches past the last covered cell, and the blight past that.
        int m = BLIGHT_CELLS + 1;
        int x0 = Math.max(Math.max(0, extentMinX - m), view.minX()), y0 = Math.max(Math.max(0, extentMinY - m), view.minY());
        int x1 = Math.min(Math.min(gridW - 1, extentMaxX + m), view.maxX()), y1 = Math.min(Math.min(gridH - 1, extentMaxY + m), view.maxY());
        if (x1 < x0 || y1 < y0) return;
        clockSeconds = (Float.isFinite(ctx.animationTimeSeconds)
                ? Math.max(0f, ctx.animationTimeSeconds)
                : clockSeconds + Math.max(0f, ctx.realDt)) % 4096f;
        if (broken || shader.isBroken()) {
            collectFlat(out, camera, ctx.alphaMult, x0, y0, x1, y1);
            return;
        }
        pass.minX = x0;
        pass.minY = y0;
        pass.maxX = x1;
        pass.maxY = y1;
        pass.screenX0 = camera.cellToScreenX(0f);
        pass.screenY0 = camera.cellToScreenY(0f);
        pass.cellPx = camera.cellPxSize();
        pass.timeSeconds = clockSeconds;
        pass.alpha = ctx.alphaMult;
        pass.palette = palette;
        pass.sun = new CreepShading.Sun(sun.dirX(), sun.dirY(), sun.risePerCell(), sun.casts() ? sun.shadowStrength() : 0f);
        out.addCustom(RenderLayer.CREEP, pass);
    }

    // ---- mirror ----------------------------------------------------------

    /** Re-lays the nest's roots into the ground picture when the nest has changed since they were last laid. */
    void layTrails(BattleSimulation sim) {
        if (!TRAILS || ground == null) return;
        List<NestTrails.Trail> trails = NestTrails.of(sim);
        long signature = NestTrails.signature(trails);
        if (signature == trailSignature && ground == trailGround) return;
        trailSignature = signature;
        trailGround = ground;
        NestTrails.rasterize(trails, gridW, gridH, ground, 2, 3);
        wholeDirty = true;
    }

    /** Brings the mirror up to the field; GL-free. */
    void catchUp(CreepField field, NavigationGrid grid) {
        catchUp(field, grid, null);
    }

    /** As {@link #catchUp(CreepField, NavigationGrid)}, also re-reading any tile whose walls changed. */
    void catchUp(CreepField field, NavigationGrid grid, CellTopology topology) {
        if (field != bound || topology != this.topology) {
            bind(field);
            this.topology = topology;
            topologySeen = topology == null ? 0L : topology.changeCount();
            bindShore(topology);
        }
        ElevationLayout layout = topology == null ? null : topology.cliffArt();
        if (layout != faceLayout) {
            faceLayout = layout;
            faces = layout == null ? null : CliffFaceMask.of(layout);
            facesUploaded = false;
        }
        long revision = field.revision();
        if (topology != null && topology.changeCount() != topologySeen) {
            long count = topology.changeCount();
            boolean lost = count - topologySeen > topology.changeLogCapacity();
            for (long seq = lost ? count : topologySeen; seq < count; seq++) {
                int idx = topology.changedCellAt(seq);
                int t = ((idx / gridW) / CreepField.TILE) * tilesX + (idx % gridW) / CreepField.TILE;
                if (t >= 0 && t < forced.length) forced[t] = true;
            }
            if (lost) Arrays.fill(forced, true);
            topologySeen = count;
        }
        boolean[] copied = null;
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int t = ty * tilesX + tx;
                long changed = field.tileRevision(tx, ty);
                if (changed <= seen[t] && !forced[t]) continue;
                boolean topologyMoved = forced[t];
                forced[t] = false;
                if (topologyMoved) vegetatedTile[t] = readVegetation(tx, ty);
                if (changed == 0L) continue;   // never held creep: nothing of it to draw
                copyTile(field, grid, tx, ty);
                if (copied == null) copied = new boolean[tilesX * tilesY];
                copied[t] = true;
                // A tile written mid-tick reads one ahead; holding it at the published
                // revision makes the next frame look again once the tick has finished.
                seen[t] = Math.min(changed, revision);
                if (!dirtyTile[t]) {
                    dirtyTile[t] = true;
                    dirtyCount++;
                }
                extentMinX = Math.min(extentMinX, tx * CreepField.TILE);
                extentMinY = Math.min(extentMinY, ty * CreepField.TILE);
                extentMaxX = Math.max(extentMaxX, Math.min(gridW, (tx + 1) * CreepField.TILE) - 1);
                extentMaxY = Math.max(extentMaxY, Math.min(gridH, (ty + 1) * CreepField.TILE) - 1);
            }
        }
        if (copied != null && shoreWater != null) lendShore(field, copied);
        if (copied != null) markBlightDue(copied);
        int tick = field.now();
        int elapsed = blightTick == Integer.MIN_VALUE ? Integer.MAX_VALUE / BLIGHT_TILES_PER_TICK
                : Math.max(0, Math.min(Integer.MAX_VALUE / BLIGHT_TILES_PER_TICK, tick - blightTick));
        blightTick = tick;
        if (elapsed > 0) blightSome(elapsed * BLIGHT_TILES_PER_TICK);
    }

    /** Marks every vegetated tile that changed or borders one that did as owing its blight. */
    private void markBlightDue(boolean[] copied) {
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int t = ty * tilesX + tx;
                if (vegetatedTile[t] && nearCopied(copied, tx, ty)) blightDue[t] = true;
            }
        }
    }

    /** Recomputes the blight of up to {@code budget} due tiles, resuming where the last frame stopped. */
    void blightSome(int budget) {
        int tiles = tilesX * tilesY;
        for (int i = 0; i < tiles && budget > 0; i++) {
            int t = (blightCursor + i) % tiles;
            if (!blightDue[t]) continue;
            blightDue[t] = false;
            blightTile(t % tilesX, t / tilesX);
            if (!dirtyTile[t]) {
                dirtyTile[t] = true;
                dirtyCount++;
            }
            budget--;
            blightCursor = (t + 1) % tiles;
        }
    }

    private boolean nearCopied(boolean[] copied, int tx, int ty) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = tx + dx, y = ty + dy;
                if (x >= 0 && y >= 0 && x < tilesX && y < tilesY && copied[y * tilesX + x]) return true;
            }
        }
        return false;
    }

    /**
     * Blight over one tile's vegetation: the largest coverage within {@link #BLIGHT_CELLS} cells, each weighed down
     * by its distance along each axis ({@code 1 - d / (BLIGHT_CELLS + 1)}), so it fades out from the edge. Done as a
     * row pass then a column pass.
     */
    private void blightTile(int tx, int ty) {
        int r = BLIGHT_CELLS;
        int x0 = tx * CreepField.TILE, y0 = ty * CreepField.TILE;
        int x1 = Math.min(gridW, x0 + CreepField.TILE), y1 = Math.min(gridH, y0 + CreepField.TILE);
        int rx0 = Math.max(0, x0), rx1 = x1;
        int ry0 = Math.max(0, y0 - r), ry1 = Math.min(gridH, y1 + r);
        int w = rx1 - rx0, h = ry1 - ry0;
        if (blightRow == null || blightRow.length < w * h) blightRow = new int[w * h];
        int[] rows = blightRow;
        // In whole numbers: a tap d cells off weighs (r + 1 - |d|), so a pass scales by r + 1 and both by its square.
        int scale = (r + 1) * (r + 1);
        for (int y = ry0; y < ry1; y++) {
            int row = y * gridW;
            for (int x = rx0; x < rx1; x++) {
                int best = 0;
                int from = Math.max(0, x - r), to = Math.min(gridW - 1, x + r);
                for (int cx = from; cx <= to; cx++) {
                    int v = (mirror[(row + cx) * 4] & 0xFF) * (r + 1 - Math.abs(cx - x));
                    if (v > best) best = v;
                }
                rows[(y - ry0) * w + (x - rx0)] = best;
            }
        }
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int at = (y * gridW + x) * 4;
                if (ground[at + 1] == 0) continue;
                int best = 0;
                int from = Math.max(ry0, y - r), to = Math.min(ry1 - 1, y + r);
                for (int cy = from; cy <= to; cy++) {
                    int v = rows[(cy - ry0) * w + (x - rx0)] * (r + 1 - Math.abs(cy - y));
                    if (v > best) best = v;
                }
                ground[at] = (byte) ((best + scale / 2) / scale);
            }
        }
    }

    /** Reads which of one tile's cells are vegetation; whether any is. */
    private boolean readVegetation(int tx, int ty) {
        if (topology == null) return false;
        boolean any = false;
        int xEnd = Math.min(gridW, (tx + 1) * CreepField.TILE), yEnd = Math.min(gridH, (ty + 1) * CreepField.TILE);
        for (int y = ty * CreepField.TILE; y < yEnd; y++) {
            for (int x = tx * CreepField.TILE; x < xEnd; x++) {
                boolean veg = topology.getGroundKind(x, y) == CellTopology.GroundKind.GRASS && !topology.isWall(x, y);
                int at = (y * gridW + x) * 4;
                ground[at + 1] = (byte) (veg ? 255 : 0);
                if (!veg) ground[at] = 0;
                any |= veg;
            }
        }
        return any;
    }

    private void bind(CreepField field) {
        bindField(field);
        // Every tile's vegetation is read on the first catch-up.
        Arrays.fill(forced, true);
    }

    private void bindField(CreepField field) {
        bound = field;
        gridW = field.width();
        gridH = field.height();
        tilesX = field.tilesX();
        tilesY = field.tilesY();
        mirror = new byte[gridW * gridH * 4];
        ground = new byte[gridW * gridH * 4];
        vegetatedTile = new boolean[tilesX * tilesY];
        blightDue = new boolean[tilesX * tilesY];
        blightCursor = 0;
        blightTick = Integer.MIN_VALUE;
        seen = new long[tilesX * tilesY];
        forced = new boolean[tilesX * tilesY];
        dirtyTile = new boolean[tilesX * tilesY];
        dirtyCount = 0;
        wholeDirty = true;
        extentMinX = extentMinY = Integer.MAX_VALUE;
        extentMaxX = extentMaxY = -1;
    }

    private void copyTile(CreepField field, NavigationGrid grid, int tx, int ty) {
        int xEnd = Math.min(gridW, (tx + 1) * CreepField.TILE), yEnd = Math.min(gridH, (ty + 1) * CreepField.TILE);
        for (int y = ty * CreepField.TILE; y < yEnd; y++) {
            for (int x = tx * CreepField.TILE; x < xEnd; x++) {
                int idx = y * gridW + x, at = idx * 4;
                int cov = field.coverageAt(idx);
                mirror[at] = (byte) cov;
                mirror[at + 1] = (byte) field.vitalityAt(idx);
                mirror[at + 2] = (byte) field.pulseAt(idx);
                mirror[at + 3] = (byte) (isWall(grid, x, y, idx) ? 255 : 0);
            }
        }
    }

    /** Reads the map's shore: its water mask and which cells are shore water. */
    private void bindShore(CellTopology topology) {
        shore = null;
        shoreWater = null;
        shoreUploaded = false;
        if (topology == null || topology.getWidth() != gridW || topology.getHeight() != gridH) return;
        shore = ShoreWaterMask.of(topology);
        if (shore == null) return;
        shoreWater = new boolean[gridW * gridH];
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) shoreWater[y * gridW + x] = WaterShore.isShoreWater(topology, x, y);
        }
    }

    /**
     * Lends each shore water cell in or beside a copied tile the coverage, vitality and pulse of its most creeped
     * neighbour, so the creep's coverage runs on past the cell's square edge and the shore mask alone decides where
     * it stops. Done after every tile is copied, because a copy writes the field's own zero back over its water.
     */
    private void lendShore(CreepField field, boolean[] copied) {
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                if (!copied[ty * tilesX + tx]) continue;
                int x0 = Math.max(0, tx * CreepField.TILE - 1), y0 = Math.max(0, ty * CreepField.TILE - 1);
                int x1 = Math.min(gridW, (tx + 1) * CreepField.TILE + 1), y1 = Math.min(gridH, (ty + 1) * CreepField.TILE + 1);
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int idx = y * gridW + x;
                        if (shoreWater[idx]) lend(field, x, y, idx);
                    }
                }
            }
        }
    }

    private void lend(CreepField field, int x, int y, int idx) {
        int best = -1, bestCov = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= gridW || ny >= gridH) continue;
                int n = ny * gridW + nx;
                // A wall's coverage is its age, not ground the creep holds.
                if (shoreWater[n] || topology.isWall(nx, ny)) continue;
                int cov = field.coverageAt(n);
                if (cov > bestCov) {
                    bestCov = cov;
                    best = n;
                }
            }
        }
        int at = idx * 4;
        byte cov = (byte) bestCov;
        byte vit = (byte) (best < 0 ? 0 : field.vitalityAt(best));
        byte pulse = (byte) (best < 0 ? 0 : field.pulseAt(best));
        if (mirror[at] == cov && mirror[at + 1] == vit && mirror[at + 2] == pulse) return;
        mirror[at] = cov;
        mirror[at + 1] = vit;
        mirror[at + 2] = pulse;
        int t = (y / CreepField.TILE) * tilesX + x / CreepField.TILE;
        if (!dirtyTile[t]) {
            dirtyTile[t] = true;
            dirtyCount++;
        }
    }

    private boolean isWall(NavigationGrid grid, int x, int y, int idx) {
        return topology != null ? topology.isWall(x, y) : !grid.isWalkableAt(idx);
    }

    @Override
    public void sample(float cellX, float cellY, float[] out) { sampleField(cellX, cellY, out); }

    @Override
    public float coverage(float cellX, float cellY) { return coverageAt(cellX, cellY); }

    @Override
    public float face(float cellX, float cellY) { return faces == null ? 0f : faces.sample(cellX, cellY); }

    @Override
    public float water(float cellX, float cellY) { return shore == null ? 0f : shore.sample(cellX, cellY); }

    @Override
    public float ford(float cellX, float cellY) { return shore == null ? 0f : shore.sampleFord(cellX, cellY); }

    @Override
    public void ground(float cellX, float cellY, float[] out) {
        float u = cellX - 0.5f, v = cellY - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        int cx0 = clamp(x0, gridW), cx1 = clamp(x0 + 1, gridW), cy0 = clamp(y0, gridH), cy1 = clamp(y0 + 1, gridH);
        for (int c = 0; c < 4; c++) {
            float a = groundAt(cx0, cy0, c), b = groundAt(cx1, cy0, c), d = groundAt(cx0, cy1, c), e = groundAt(cx1, cy1, c);
            out[c] = ((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy) / 255f;
        }
    }

    /** The ground picture at a cell, channel 0 (blight), 1 (vegetated), 2 (root distance) or 3 (root width), as 0..255; 0 off the map. */
    int groundAt(int x, int y, int channel) {
        if (ground == null || x < 0 || y < 0 || x >= gridW || y >= gridH) return 0;
        return ground[(y * gridW + x) * 4 + channel] & 0xFF;
    }

    int gridWidth() { return gridW; }
    int gridHeight() { return gridH; }

    /** The mirrored field at a cell, channel 0..3 (coverage, vitality, pulse, wall) as 0..255; 0 off the map. */
    int mirrorAt(int x, int y, int channel) {
        if (mirror == null || x < 0 || y < 0 || x >= gridW || y >= gridH) return 0;
        return mirror[(y * gridW + x) * 4 + channel] & 0xFF;
    }

    /**
     * The field bilinearly between cell centres, as GL's linear filter samples it:
     * coverage, vitality and wall in 0..1, pulse in cells.
     */
    void sampleField(float cellX, float cellY, float[] out) {
        float u = cellX - 0.5f, v = cellY - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        int cx0 = clamp(x0, gridW), cx1 = clamp(x0 + 1, gridW), cy0 = clamp(y0, gridH), cy1 = clamp(y0 + 1, gridH);
        for (int c = 0; c < 4; c++) {
            float a = mirrorAt(cx0, cy0, c), b = mirrorAt(cx1, cy0, c);
            float d = mirrorAt(cx0, cy1, c), e = mirrorAt(cx1, cy1, c);
            out[c] = ((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy) / 255f;
        }
        out[2] *= 255f;
    }

    /** Bilinear coverage alone, 0..1, as the shader's edge taps read it. */
    float coverageAt(float cellX, float cellY) {
        float u = cellX - 0.5f, v = cellY - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        int cx0 = clamp(x0, gridW), cx1 = clamp(x0 + 1, gridW), cy0 = clamp(y0, gridH), cy1 = clamp(y0 + 1, gridH);
        float a = mirrorAt(cx0, cy0, 0), b = mirrorAt(cx1, cy0, 0), d = mirrorAt(cx0, cy1, 0), e = mirrorAt(cx1, cy1, 0);
        return ((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy) / 255f;
    }

    /** Whether any cell within two of the point holds coverage, or blight: as far as anything of the creep reaches. */
    boolean coverageNear(float cellX, float cellY) {
        int cx = (int) Math.floor(cellX), cy = (int) Math.floor(cellY);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (mirrorAt(cx + dx, cy + dy, 0) > 0 || groundAt(cx + dx, cy + dy, 0) > 0) return true;
            }
        }
        return false;
    }

    private static int clamp(int v, int size) { return v < 0 ? 0 : (v >= size ? size - 1 : v); }

    // ---- flat fallback ------------------------------------------------------

    private void collectFlat(DrawList out, BattleCamera camera, float alphaMult, int x0, int y0, int x1, int y1) {
        float[] c = palette.base();
        float cell = camera.cellPxSize();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int cov = mirrorAt(x, y, 0);
                if (cov < CreepField.CREEPED || shoreWater != null && shoreWater[y * gridW + x]) continue;
                float sx = camera.cellToScreenX(x), sy = camera.cellToScreenY(y);
                out.addSolidRect(RenderLayer.CREEP, sx, sy, sx + cell, sy + cell, c[0], c[1], c[2],
                        0.8f * cov / 255f * alphaMult);
            }
        }
    }

    // ---- GL ------------------------------------------------------------------

    void draw(CreepPass p) {
        if (broken || mirror == null) return;
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_CURRENT_BIT);
        try {
            GlStateBracket.applyTextured2DState();
            upload();
            glActiveTexture(GL_TEXTURE1);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, noiseTexture);
            glActiveTexture(GL_TEXTURE2);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, surfaceTexture);
            glActiveTexture(GL_TEXTURE3);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, groundTexture);
            glActiveTexture(GL_TEXTURE4);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, faceTexture);
            glActiveTexture(GL_TEXTURE5);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, shoreTexture);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fieldTexture);
            if (!shader.begin(p, gridW, gridH)) return;
            float cx0 = p.minX, cy0 = p.minY, cx1 = p.maxX + 1, cy1 = p.maxY + 1;
            float sx0 = p.cellToScreenX(cx0), sy0 = p.cellToScreenY(cy0);
            float sx1 = p.cellToScreenX(cx1), sy1 = p.cellToScreenY(cy1);
            glBegin(GL_QUADS);
            glTexCoord2f(cx0, cy0); glVertex2f(sx0, sy0);
            glTexCoord2f(cx1, cy0); glVertex2f(sx1, sy0);
            glTexCoord2f(cx1, cy1); glVertex2f(sx1, sy1);
            glTexCoord2f(cx0, cy1); glVertex2f(sx0, sy1);
            glEnd();
        } catch (RuntimeException failure) {
            broken = true;
        } finally {
            ShaderProgram.useNone();
            glActiveTexture(GL_TEXTURE5);
            glBindTexture(GL_TEXTURE_2D, 0);
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_2D, 0);
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_2D, 0);
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, 0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, 0);
            glActiveTexture(GL_TEXTURE0);
            glPopAttrib();
        }
    }

    private void upload() {
        if (noiseTexture == 0) {
            noiseTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, noiseTexture);
            // Unmipmapped, as CreepNoise.sample reads it: the noise only moves the edge, and is coarse at any framing.
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            ByteBuffer noise = BufferUtils.createByteBuffer(CreepNoise.SIZE * CreepNoise.SIZE * 4);
            noise.put(CreepNoise.rgba()).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, CreepNoise.SIZE, CreepNoise.SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, noise);
            Reference.reachabilityFence(noise);   // LWJGL 2 keeps only the address
        }
        if (surfaceTexture == 0) {
            surfaceTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, surfaceTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_GENERATE_MIPMAP, GL_TRUE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            ByteBuffer surface = BufferUtils.createByteBuffer(CreepSurface.SIZE * CreepSurface.SIZE * 4);
            surface.put(CreepSurface.rgba()).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, CreepSurface.SIZE, CreepSurface.SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, surface);
            Reference.reachabilityFence(surface);   // LWJGL 2 keeps only the address
        }
        if (faceTexture == 0 || !facesUploaded) uploadFaces();
        if (shoreTexture == 0 || !shoreUploaded) {
            shoreTexture = uploadMask(shoreTexture, shore == null ? null : shore.texels(),
                    shore == null ? 1 : shore.width(), shore == null ? 1 : shore.height(), 2);
            shoreUploaded = true;
        }
        if (fieldTexture == 0) {
            fieldTexture = glGenTextures();
            groundTexture = glGenTextures();
            wholeDirty = true;
        }
        if (!wholeDirty && dirtyCount == 0) return;
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            boolean whole = wholeDirty || dirtyCount > TILE_UPLOAD_LIMIT;
            uploadPicture(fieldTexture, mirror, whole);
            uploadPicture(groundTexture, ground, whole);
            wholeDirty = false;
        } finally {
            glPopClientAttrib();
        }
        Arrays.fill(dirtyTile, false);
        dirtyCount = 0;
    }

    /** The face mask, or one clear texel where there is none (the shader then cuts nothing). */
    private void uploadFaces() {
        faceTexture = uploadMask(faceTexture, faces == null ? null : faces.texels(),
                faces == null ? 1 : faces.width(), faces == null ? 1 : faces.height(), 1);
        facesUploaded = true;
    }

    /**
     * Uploads a mask of one byte a texel (alpha) or two (luminance, alpha) into {@code texture} (made if 0), or one
     * clear texel for none; returns the texture.
     */
    private static int uploadMask(int texture, byte[] mask, int w, int h, int channels) {
        if (texture == 0) texture = glGenTextures();
        ByteBuffer texels = BufferUtils.createByteBuffer(w * h * channels);
        if (mask != null) texels.put(mask);
        else texels.put(new byte[channels]);
        texels.flip();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            if (channels == 2) glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE8_ALPHA8, w, h, 0, GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, texels);
            else glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, w, h, 0, GL_ALPHA, GL_UNSIGNED_BYTE, texels);
            Reference.reachabilityFence(texels);   // LWJGL 2 keeps only the address
        } finally {
            glPopClientAttrib();
        }
        return texture;
    }

    /** Uploads one per-cell picture, whole or the dirty tiles only. */
    private void uploadPicture(int texture, byte[] cells, boolean whole) {
        glBindTexture(GL_TEXTURE_2D, texture);
        if (whole) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, gridW, gridH, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                    packed(cells, 0, 0, gridW, gridH));
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return;
        }
        for (int t = 0; t < dirtyTile.length; t++) {
            if (!dirtyTile[t]) continue;
            int x = (t % tilesX) * CreepField.TILE, y = (t / tilesX) * CreepField.TILE;
            int w = Math.min(CreepField.TILE, gridW - x), h = Math.min(CreepField.TILE, gridH - y);
            glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, packed(cells, x, y, w, h));
        }
    }

    private ByteBuffer packed(byte[] cells, int x, int y, int w, int h) {
        int needed = w * h * 4;
        if (scratch == null || scratch.capacity() < needed) scratch = BufferUtils.createByteBuffer(Math.max(needed, 16384));
        scratch.clear();
        for (int row = 0; row < h; row++) scratch.put(cells, ((y + row) * gridW + x) * 4, w * 4);
        scratch.flip();
        return scratch;
    }

    /** Releases the textures and the program; the context that made them must be current. */
    void dispose() {
        if (fieldTexture != 0) glDeleteTextures(fieldTexture);
        if (noiseTexture != 0) glDeleteTextures(noiseTexture);
        if (surfaceTexture != 0) glDeleteTextures(surfaceTexture);
        if (groundTexture != 0) glDeleteTextures(groundTexture);
        if (faceTexture != 0) glDeleteTextures(faceTexture);
        if (shoreTexture != 0) glDeleteTextures(shoreTexture);
        fieldTexture = noiseTexture = surfaceTexture = groundTexture = faceTexture = shoreTexture = 0;
        facesUploaded = shoreUploaded = false;
        shader.dispose();
        bound = null;
    }
}
