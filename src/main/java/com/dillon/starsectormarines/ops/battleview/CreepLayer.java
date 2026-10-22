package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
 * <p>Off GL (a review frame) the same command carries the same numbers and the
 * offline drain shades the mirror through {@link CreepShading}. If the shader
 * fails, the layer falls back for good to flat cells in the palette's base
 * colour. Turn the layer off for a control run with
 * {@code -Dbattle.render.creep=false}.
 */
final class CreepLayer implements RenderSystem, CreepShading.Field {

    /** Control-run switch; on by default. */
    static final String PROPERTY = "battle.render.creep";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Whether creep is drawn at all; for evidence that reports which run it was. */
    static boolean enabled() { return ENABLED; }

    /** Changed tiles above which a frame re-uploads the whole field rather than tile by tile. */
    private static final int TILE_UPLOAD_LIMIT = 48;

    private final CreepShader shader = new CreepShader();
    private final CreepPass pass = new CreepPass(this);
    private final CreepPalette palette = CreepPalette.active();

    private CreepField bound;
    private int gridW, gridH, tilesX, tilesY;
    private byte[] mirror;
    private long[] seen;
    /** Tiles whose walls changed since they were last copied. */
    private boolean[] forced;
    private boolean[] dirtyTile;
    private int dirtyCount;
    private boolean wholeDirty;
    private int extentMinX = Integer.MAX_VALUE, extentMinY = Integer.MAX_VALUE, extentMaxX = -1, extentMaxY = -1;

    private int fieldTexture, noiseTexture, surfaceTexture;
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
        if (extentMaxX < extentMinX) return;
        BattleCamera camera = ctx.camera;
        VisibleCellRect view = camera.visibleCells(1, gridW, gridH);
        // Two cells of margin: the softened edge reaches past the last covered cell.
        int x0 = Math.max(Math.max(0, extentMinX - 2), view.minX()), y0 = Math.max(Math.max(0, extentMinY - 2), view.minY());
        int x1 = Math.min(Math.min(gridW - 1, extentMaxX + 2), view.maxX()), y1 = Math.min(Math.min(gridH - 1, extentMaxY + 2), view.maxY());
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
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int t = ty * tilesX + tx;
                long changed = field.tileRevision(tx, ty);
                if (changed <= seen[t] && !forced[t]) continue;
                forced[t] = false;
                if (changed == 0L) continue;   // never held creep: nothing of it to draw
                copyTile(field, grid, tx, ty);
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
    }

    private void bind(CreepField field) {
        bound = field;
        gridW = field.width();
        gridH = field.height();
        tilesX = field.tilesX();
        tilesY = field.tilesY();
        mirror = new byte[gridW * gridH * 4];
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

    private boolean isWall(NavigationGrid grid, int x, int y, int idx) {
        return topology != null ? topology.isWall(x, y) : !grid.isWalkableAt(idx);
    }

    @Override
    public void sample(float cellX, float cellY, float[] out) { sampleField(cellX, cellY, out); }

    @Override
    public float coverage(float cellX, float cellY) { return coverageAt(cellX, cellY); }

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

    /** Whether any cell within two of the point holds coverage: as far as a softened edge reaches. */
    boolean coverageNear(float cellX, float cellY) {
        int cx = (int) Math.floor(cellX), cy = (int) Math.floor(cellY);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) if (mirrorAt(cx + dx, cy + dy, 0) > 0) return true;
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
                if (cov < CreepField.CREEPED) continue;
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
        }
        if (fieldTexture == 0) {
            fieldTexture = glGenTextures();
            wholeDirty = true;
        }
        if (!wholeDirty && dirtyCount == 0) return;
        glBindTexture(GL_TEXTURE_2D, fieldTexture);
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            if (wholeDirty || dirtyCount > TILE_UPLOAD_LIMIT) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, gridW, gridH, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                        packed(0, 0, gridW, gridH));
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                wholeDirty = false;
            } else {
                for (int t = 0; t < dirtyTile.length; t++) {
                    if (!dirtyTile[t]) continue;
                    int x = (t % tilesX) * CreepField.TILE, y = (t / tilesX) * CreepField.TILE;
                    int w = Math.min(CreepField.TILE, gridW - x), h = Math.min(CreepField.TILE, gridH - y);
                    glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, packed(x, y, w, h));
                }
            }
        } finally {
            glPopClientAttrib();
        }
        Arrays.fill(dirtyTile, false);
        dirtyCount = 0;
    }

    private ByteBuffer packed(int x, int y, int w, int h) {
        int needed = w * h * 4;
        if (scratch == null || scratch.capacity() < needed) scratch = BufferUtils.createByteBuffer(Math.max(needed, 16384));
        scratch.clear();
        for (int row = 0; row < h; row++) scratch.put(mirror, ((y + row) * gridW + x) * 4, w * 4);
        scratch.flip();
        return scratch;
    }

    /** Releases the textures and the program; the context that made them must be current. */
    void dispose() {
        if (fieldTexture != 0) glDeleteTextures(fieldTexture);
        if (noiseTexture != 0) glDeleteTextures(noiseTexture);
        if (surfaceTexture != 0) glDeleteTextures(surfaceTexture);
        fieldTexture = noiseTexture = surfaceTexture = 0;
        shader.dispose();
        bound = null;
    }
}
