package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationBrush;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.lwjgl.BufferUtils;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.lwjgl.opengl.GL11.GL_CLIENT_PIXEL_STORE_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
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
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
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
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_GENERATE_MIPMAP;

/**
 * Grass-topped raised ground, drawn as {@link CliffShading} baked once per
 * battle. Only the ground within reach of a face or a ramp is baked, in
 * square chunks packed into one mipmapped texture; each texel is a
 * premultiplied colour over whatever ground the tiles drew, so a frame costs
 * one textured quad per visible chunk inside the ground's colour target, where
 * the composite then shadows and lights it like the rest of the ground.
 *
 * <p>The generator hands the layout over ({@link CellTopology#cliffArt()}) in
 * place of the cliff tiles; with {@link ElevationBrush#BAKED_CLIFFS_PROPERTY}
 * off it hands nothing and the tiles draw instead, which is the control run.
 */
final class CliffArtLayer implements RenderSystem {

    /** Cells a chunk spans on a side. */
    static final int CHUNK = 8;
    /** Texels per cell the look is baked at; halved until the atlas fits. */
    private static final int TEXELS_PER_CELL = 32;
    /** Texels baked past each side of a chunk, so mipmaps do not bleed between chunks. */
    private static final int GUTTER = 8;
    /** Cells beyond a face or ramp the look can reach: the apron and the ring it is measured over. */
    private static final int REACH = 3;
    private static final int MAX_ATLAS = 4096;

    private final CliffArtPass pass = new CliffArtPass(this);
    private SunLight sun = new SunLight();

    private ElevationLayout baked;
    private float bakedAzimuth, bakedElevation;
    private int mapW, mapH, chunksX, chunksY, texels, side, columns, atlasW, atlasH;
    /** Per chunk in the map: its slot in the atlas, or -1 where nothing is drawn. */
    private int[] slot;
    /** Chunk origins (cells) in slot order. */
    private int[] originX, originY;
    /** Per slot: the padded chunk's premultiplied RGBA, rows bottom-up. */
    private byte[][] pixels;
    private int texture;
    private boolean uploaded, broken;

    @Override
    public RenderLayer layer() { return RenderLayer.GROUND; }

    /** The scene's sun, which the faces and the ramp walls are lit from. */
    void useSun(SunLight sun) {
        if (sun != null) this.sun = sun;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        CellTopology topology = ctx.sim.getTopology();
        ElevationLayout art = topology == null ? null : topology.cliffArt();
        if (art == null) return;
        bake(art, topology);
        if (pixels.length == 0) return;
        BattleCamera camera = ctx.camera;
        VisibleCellRect view = camera.visibleCells(1, mapW, mapH);
        pass.minChunkX = Math.max(0, view.minX() / CHUNK);
        pass.minChunkY = Math.max(0, view.minY() / CHUNK);
        pass.maxChunkX = Math.min(chunksX - 1, view.maxX() / CHUNK);
        pass.maxChunkY = Math.min(chunksY - 1, view.maxY() / CHUNK);
        if (pass.maxChunkX < pass.minChunkX || pass.maxChunkY < pass.minChunkY) return;
        pass.screenX0 = camera.cellToScreenX(0f);
        pass.screenY0 = camera.cellToScreenY(0f);
        pass.cellPx = camera.cellPxSize();
        pass.alpha = ctx.alphaMult;
        out.addCustom(RenderLayer.GROUND, pass);
    }

    // ---- bake ------------------------------------------------------------------

    /**
     * Cells of sand, stone or snow, where a lip is bare rather than turfed (raised ground on a basin's dry rim); null
     * when there are none, as on a green map. A dirt patch on a meadow keeps its turf.
     */
    static boolean[] dryGround(CellTopology topology) {
        if (topology == null) return null;
        int w = topology.getWidth(), h = topology.getHeight();
        boolean[] dry = new boolean[w * h];
        boolean any = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                GroundKind kind = topology.getGroundKind(x, y);
                if (kind != GroundKind.SAND && kind != GroundKind.STONE && kind != GroundKind.SNOW) continue;
                dry[y * w + x] = true;
                any = true;
            }
        }
        return any ? dry : null;
    }

    /** Bakes the layout's look, unless it is already baked for this layout and sun. GL-free. */
    void bake(ElevationLayout art, CellTopology topology) {
        if (art == baked && sun.azimuthDegrees() == bakedAzimuth && sun.elevationDegrees() == bakedElevation) return;
        baked = art;
        bakedAzimuth = sun.azimuthDegrees();
        bakedElevation = sun.elevationDegrees();
        uploaded = false;
        mapW = art.width();
        mapH = art.height();
        chunksX = (mapW + CHUNK - 1) / CHUNK;
        chunksY = (mapH + CHUNK - 1) / CHUNK;
        boolean[] near = CliffShading.reach(art, REACH);
        slot = new int[chunksX * chunksY];
        Arrays.fill(slot, -1);
        List<int[]> origins = new ArrayList<>();
        for (int cy = 0; cy < chunksY; cy++) {
            for (int cx = 0; cx < chunksX; cx++) {
                if (!anyNear(near, cx, cy)) continue;
                slot[cy * chunksX + cx] = origins.size();
                origins.add(new int[]{cx * CHUNK, cy * CHUNK});
            }
        }
        int count = origins.size();
        texels = TEXELS_PER_CELL;
        while (true) {
            side = CHUNK * texels + 2 * GUTTER;
            columns = Math.max(1, MAX_ATLAS / side);
            int rows = (count + columns - 1) / columns;
            if (rows * side <= MAX_ATLAS || texels <= 4) break;
            texels /= 2;
        }
        atlasW = Math.min(count, columns) * side;
        atlasH = ((count + columns - 1) / columns) * side;
        originX = new int[count];
        originY = new int[count];
        for (int i = 0; i < count; i++) {
            originX[i] = origins.get(i)[0];
            originY[i] = origins.get(i)[1];
        }
        CliffShading shading = new CliffShading(art, sun, texels, dryGround(topology));
        pixels = new byte[count][];
        IntStream.range(0, count).parallel().forEach(i -> pixels[i] = bakeChunk(shading, near, originX[i], originY[i]));
    }

    private boolean anyNear(boolean[] near, int cx, int cy) {
        for (int y = cy * CHUNK; y < Math.min(mapH, (cy + 1) * CHUNK); y++) {
            for (int x = cx * CHUNK; x < Math.min(mapW, (cx + 1) * CHUNK); x++) {
                if (near[y * mapW + x]) return true;
            }
        }
        return false;
    }

    private byte[] bakeChunk(CliffShading shading, boolean[] near, int x0, int y0) {
        byte[] out = new byte[side * side * 4];
        float[] rgba = new float[4];
        for (int ty = 0; ty < side; ty++) {
            float wy = y0 + (ty - GUTTER + .5f) / texels;
            int cellY = (int) Math.floor(wy);
            for (int tx = 0; tx < side; tx++) {
                float wx = x0 + (tx - GUTTER + .5f) / texels;
                int cellX = (int) Math.floor(wx);
                if (cellX < 0 || cellY < 0 || cellX >= mapW || cellY >= mapH || !near[cellY * mapW + cellX]) continue;
                shading.shade(wx, wy, rgba);
                if (rgba[3] <= 0f) continue;
                int o = (ty * side + tx) * 4;
                out[o] = (byte) Math.round(rgba[0] * 255f);
                out[o + 1] = (byte) Math.round(rgba[1] * 255f);
                out[o + 2] = (byte) Math.round(rgba[2] * 255f);
                out[o + 3] = (byte) Math.round(rgba[3] * 255f);
            }
        }
        return out;
    }

    /**
     * The baked look at a point (cells), bilinear between texels, into
     * {@code rgba} as premultiplied 0..1; false where nothing is drawn. The
     * offline drain reads the bake through this.
     */
    boolean sample(float cellX, float cellY, float[] rgba) {
        if (pixels == null || cellX < 0 || cellY < 0 || cellX >= mapW || cellY >= mapH) return false;
        int cx = (int) cellX / CHUNK, cy = (int) cellY / CHUNK;
        int s = slot[cy * chunksX + cx];
        if (s < 0) return false;
        byte[] p = pixels[s];
        float fx = (cellX - originX[s]) * texels + GUTTER - .5f, fy = (cellY - originY[s]) * texels + GUTTER - .5f;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        float tx = fx - ix, ty = fy - iy;
        Arrays.fill(rgba, 0f);
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                int x = Math.max(0, Math.min(side - 1, ix + i)), y = Math.max(0, Math.min(side - 1, iy + j));
                float wgt = (i == 0 ? 1 - tx : tx) * (j == 0 ? 1 - ty : ty);
                int o = (y * side + x) * 4;
                for (int c = 0; c < 4; c++) rgba[c] += (p[o + c] & 0xFF) / 255f * wgt;
            }
        }
        return rgba[3] > 0f;
    }

    /** Chunks baked, for evidence. */
    int chunkCount() { return pixels == null ? 0 : pixels.length; }

    // ---- GL --------------------------------------------------------------------

    void draw(CliffArtPass p) {
        if (broken || pixels == null || pixels.length == 0) return;
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_CURRENT_BIT);
        try {
            GlStateBracket.applyTextured2DState();
            ShaderProgram.useNone();
            upload();
            glBindTexture(GL_TEXTURE_2D, texture);
            // Premultiplied: the bake already carries its coverage in its colour.
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(p.alpha, p.alpha, p.alpha, p.alpha);
            glBegin(GL_QUADS);
            for (int cy = p.minChunkY; cy <= p.maxChunkY; cy++) {
                for (int cx = p.minChunkX; cx <= p.maxChunkX; cx++) {
                    int s = slot[cy * chunksX + cx];
                    if (s < 0) continue;
                    float u0 = ((s % columns) * side + GUTTER) / (float) atlasW;
                    float v0 = ((s / columns) * side + GUTTER) / (float) atlasH;
                    float u1 = u0 + CHUNK * texels / (float) atlasW, v1 = v0 + CHUNK * texels / (float) atlasH;
                    float sx0 = p.cellToScreenX(cx * CHUNK), sx1 = p.cellToScreenX((cx + 1) * CHUNK);
                    float sy0 = p.cellToScreenY(cy * CHUNK), sy1 = p.cellToScreenY((cy + 1) * CHUNK);
                    glTexCoord2f(u0, v0); glVertex2f(sx0, sy0);
                    glTexCoord2f(u1, v0); glVertex2f(sx1, sy0);
                    glTexCoord2f(u1, v1); glVertex2f(sx1, sy1);
                    glTexCoord2f(u0, v1); glVertex2f(sx0, sy1);
                }
            }
            glEnd();
        } catch (RuntimeException failure) {
            broken = true;
        } finally {
            glBindTexture(GL_TEXTURE_2D, 0);
            glPopAttrib();
        }
    }

    private void upload() {
        if (uploaded) return;
        if (texture == 0) texture = glGenTextures();
        ByteBuffer atlas = BufferUtils.createByteBuffer(atlasW * atlasH * 4);
        for (int s = 0; s < pixels.length; s++) {
            int ox = (s % columns) * side, oy = (s / columns) * side;
            for (int row = 0; row < side; row++) {
                atlas.position(((oy + row) * atlasW + ox) * 4);
                atlas.put(pixels[s], row * side * 4, side * 4);
            }
        }
        atlas.position(0).limit(atlasW * atlasH * 4);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_GENERATE_MIPMAP, GL_TRUE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, atlasW, atlasH, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlas);
            Reference.reachabilityFence(atlas);   // LWJGL 2 keeps only the address
        } finally {
            glPopClientAttrib();
        }
        uploaded = true;
    }

    /** Releases the texture; the context that made it must be current. */
    void dispose() {
        if (texture != 0) glDeleteTextures(texture);
        texture = 0;
        uploaded = false;
    }
}
