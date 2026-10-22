package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CLIENT_PIXEL_STORE_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_DST_COLOR;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_ZERO;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
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
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Burnt ground, resident on the GPU (`fire-nouns.md`, Look). It drains with the
 * creep, inside the ground's colour target and before the creep itself, so the
 * relief composite displaces it with the ground it darkens.
 *
 * <p>The fire field is read into a CPU mirror, one RGBA texel per cell (see
 * {@link ScorchShading} for the channels), patched only in the {@link
 * FireField#TILE}-cell tiles whose revision moved. Collection is that catch-up
 * (GL-free) and one custom command; the drain uploads the changed tiles and
 * draws one quad over the burn's extent through {@link ScorchShader} twice: a
 * multiply that darkens and browns the ground, then the overlay. Off GL (a
 * review frame) the offline drain shades the same mirror through {@link
 * ScorchShading}. {@code -Dbattle.render.fire=false} turns it off with the rest
 * of fire's look.
 */
final class ScorchLayer implements RenderSystem, ScorchShading.Field {

    /** Changed tiles above which a frame re-uploads the whole picture rather than tile by tile. */
    private static final int TILE_UPLOAD_LIMIT = 48;

    private final ScorchShader shader = new ScorchShader();
    private final ScorchPass pass = new ScorchPass(this);

    private FireField bound;
    private int gridW, gridH, tilesX, tilesY;
    private byte[] mirror;
    private long[] seen;
    private boolean[] dirtyTile;
    private int dirtyCount;
    private boolean wholeDirty;
    private int extentMinX = Integer.MAX_VALUE, extentMinY = Integer.MAX_VALUE, extentMaxX = -1, extentMaxY = -1;
    private int fieldTexture, noiseTexture;
    private boolean broken;
    private ByteBuffer scratch;

    @Override
    public RenderLayer layer() { return RenderLayer.CREEP; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (!FireRenderSystem.enabled()) return;
        FireField field = ctx.sim.fireFieldIfPresent();
        if (field == null || field.revision() == 0) return;
        catchUp(field, ctx.sim.getGrid(), ctx.sim.getTopology());
        if (extentMaxX < extentMinX || broken || shader.isBroken()) return;
        BattleCamera camera = ctx.camera;
        VisibleCellRect view = camera.visibleCells(1, gridW, gridH);
        // A cell past the burn for the singe and the smudges at its edge.
        int x0 = Math.max(Math.max(0, extentMinX - 1), view.minX()), y0 = Math.max(Math.max(0, extentMinY - 1), view.minY());
        int x1 = Math.min(Math.min(gridW - 1, extentMaxX + 1), view.maxX()), y1 = Math.min(Math.min(gridH - 1, extentMaxY + 1), view.maxY());
        if (x1 < x0 || y1 < y0) return;
        pass.minX = x0;
        pass.minY = y0;
        pass.maxX = x1;
        pass.maxY = y1;
        pass.screenX0 = camera.cellToScreenX(0f);
        pass.screenY0 = camera.cellToScreenY(0f);
        pass.cellPx = camera.cellPxSize();
        pass.alpha = ctx.alphaMult;
        out.addCustom(RenderLayer.CREEP, pass);
    }

    // ---- mirror ----------------------------------------------------------

    /** Brings the mirror up to the field; GL-free. */
    void catchUp(FireField field, NavigationGrid grid, CellTopology topology) {
        if (field != bound) bind(field, grid, topology);
        long revision = field.revision();
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int t = ty * tilesX + tx;
                long changed = field.tileRevision(tx, ty);
                if (changed <= seen[t]) continue;
                copyTile(field, grid, topology, tx, ty);
                // A tile written mid-tick reads one ahead; holding it at the published
                // revision makes the next frame look again once the tick has finished.
                seen[t] = Math.min(changed, revision);
                if (!dirtyTile[t]) {
                    dirtyTile[t] = true;
                    dirtyCount++;
                }
                extentMinX = Math.min(extentMinX, tx * FireField.TILE);
                extentMinY = Math.min(extentMinY, ty * FireField.TILE);
                extentMaxX = Math.max(extentMaxX, Math.min(gridW, (tx + 1) * FireField.TILE) - 1);
                extentMaxY = Math.max(extentMaxY, Math.min(gridH, (ty + 1) * FireField.TILE) - 1);
            }
        }
    }

    private void bind(FireField field, NavigationGrid grid, CellTopology topology) {
        bound = field;
        gridW = field.width();
        gridH = field.height();
        tilesX = field.tilesX();
        tilesY = field.tilesY();
        mirror = new byte[gridW * gridH * 4];
        seen = new long[tilesX * tilesY];
        dirtyTile = new boolean[tilesX * tilesY];
        dirtyCount = 0;
        wholeDirty = true;
        extentMinX = extentMinY = Integer.MAX_VALUE;
        extentMaxX = extentMaxY = -1;
        // What lies underfoot everywhere, not only where it has burnt: the singe and the smudges reach a cell
        // into tiles the fire never touched, and an untouched texel would otherwise read as soil.
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                int idx = y * gridW + x;
                mirror[idx * 4 + 2] = (byte) (ground(grid, topology, idx, x, y, 0) * ScorchShading.GROUND_STEP);
            }
        }
    }

    private void copyTile(FireField field, NavigationGrid grid, CellTopology topology, int tx, int ty) {
        int x0 = tx * FireField.TILE, y0 = ty * FireField.TILE;
        int x1 = Math.min(gridW, x0 + FireField.TILE), y1 = Math.min(gridH, y0 + FireField.TILE);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int idx = y * gridW + x;
                int amount = field.burnProgress(idx);
                int mask = field.burnedMaskAt(idx);
                int i = idx * 4;
                mirror[i] = (byte) amount;
                mirror[i + 1] = (byte) Math.round(heaviness(mask) * amount);
                mirror[i + 2] = (byte) (ground(grid, topology, idx, x, y, mask) * ScorchShading.GROUND_STEP);
                mirror[i + 3] = (byte) 255;
            }
        }
    }

    /** How heavy a burnt cell's fuel was, from what burnt there. */
    static float heaviness(int mask) {
        if (FireFuel.CROWN.in(mask)) return 1f;
        if (FireFuel.STRUCTURE.in(mask)) return 0.9f;
        if (FireFuel.FURNISHING.in(mask)) return 0.85f;
        if (FireFuel.DEBRIS.in(mask)) return 0.8f;
        if (FireFuel.BRUSH.in(mask)) return 0.75f;
        if (FireFuel.CREEP.in(mask)) return 0.55f;
        if (FireFuel.DEAD_CREEP.in(mask)) return 0.3f;
        return 0.25f;
    }

    /** What lies underfoot, as {@link ScorchShading} reads it. */
    static int ground(NavigationGrid grid, CellTopology topology, int idx, int x, int y, int mask) {
        if (FireFuel.CREEP.in(mask) || FireFuel.DEAD_CREEP.in(mask)) return ScorchShading.CREEP;
        // A burnt room's boards char as burnt ground does; a burnt-through wall is rubble.
        if (FireFuel.CROWN.in(mask) || FireFuel.FURNISHING.in(mask) || FireFuel.STRUCTURE.in(mask)) return ScorchShading.SOIL;
        if (!grid.isWalkableAt(idx)) return ScorchShading.NONE;
        CellTopology.GroundKind kind = topology == null ? null : topology.getGroundKind(x, y);
        if (kind == null) return ScorchShading.PAVED;
        return switch (kind) {
            case GRASS, DIRT, SAND, SNOW, RUBBLE -> ScorchShading.SOIL;
            case WATER, VOID -> ScorchShading.NONE;
            default -> ScorchShading.PAVED;
        };
    }

    // ---- ScorchShading.Field --------------------------------------------

    @Override public int width() { return gridW; }
    @Override public int height() { return gridH; }
    @Override public int texel(int x, int y, int c) { return mirror[(y * gridW + x) * 4 + c] & 0xFF; }

    // ---- GL ------------------------------------------------------------------

    void draw(ScorchPass p) {
        if (broken || mirror == null) return;
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_CURRENT_BIT);
        try {
            GlStateBracket.applyTextured2DState();
            upload();
            glActiveTexture(GL_TEXTURE1);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, noiseTexture);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fieldTexture);
            if (!shader.begin(gridW, gridH, p.alpha)) return;
            glEnable(GL_BLEND);
            glBlendFunc(GL_DST_COLOR, GL_ZERO);
            shader.mode(0);
            quad(p);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            shader.mode(1);
            quad(p);
        } catch (RuntimeException failure) {
            broken = true;
        } finally {
            ShaderProgram.useNone();
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, 0);
            glActiveTexture(GL_TEXTURE0);
            glPopAttrib();
        }
    }

    private static void quad(ScorchPass p) {
        float cx0 = p.minX, cy0 = p.minY, cx1 = p.maxX + 1, cy1 = p.maxY + 1;
        float sx0 = p.cellToScreenX(cx0), sy0 = p.cellToScreenY(cy0);
        float sx1 = p.cellToScreenX(cx1), sy1 = p.cellToScreenY(cy1);
        glBegin(GL_QUADS);
        glTexCoord2f(cx0, cy0); glVertex2f(sx0, sy0);
        glTexCoord2f(cx1, cy0); glVertex2f(sx1, sy0);
        glTexCoord2f(cx1, cy1); glVertex2f(sx1, sy1);
        glTexCoord2f(cx0, cy1); glVertex2f(sx0, sy1);
        glEnd();
    }

    private void upload() {
        if (noiseTexture == 0) {
            noiseTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, noiseTexture);
            // Unmipmapped, as CreepNoise.sample reads it.
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            ByteBuffer noise = BufferUtils.createByteBuffer(CreepNoise.SIZE * CreepNoise.SIZE * 4);
            noise.put(CreepNoise.rgba()).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, CreepNoise.SIZE, CreepNoise.SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, noise);
        }
        if (fieldTexture == 0) {
            fieldTexture = glGenTextures();
            wholeDirty = true;
        }
        if (!wholeDirty && dirtyCount == 0) return;
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glBindTexture(GL_TEXTURE_2D, fieldTexture);
            if (wholeDirty || dirtyCount > TILE_UPLOAD_LIMIT) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, gridW, gridH, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                        packed(0, 0, gridW, gridH));
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            } else {
                for (int t = 0; t < dirtyTile.length; t++) {
                    if (!dirtyTile[t]) continue;
                    int x = (t % tilesX) * FireField.TILE, y = (t / tilesX) * FireField.TILE;
                    int w = Math.min(FireField.TILE, gridW - x), h = Math.min(FireField.TILE, gridH - y);
                    glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, packed(x, y, w, h));
                }
            }
            wholeDirty = false;
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
        fieldTexture = noiseTexture = 0;
        shader.dispose();
        bound = null;
    }
}
