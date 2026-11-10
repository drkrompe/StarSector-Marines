package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.lwjgl.BufferUtils;

import java.lang.ref.Reference;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CLIENT_PIXEL_STORE_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE;
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
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * A volcanic world's older flows ({@link CellTopology#lavaFlows}), resident on the GPU. The flows never change once
 * the map is made, so the smoothed cover and the heat ({@link LavaFlowShading#cover}) are mirrored once per map, one
 * RGBA texel per cell, and uploaded once; each frame is one custom command whose drain draws one quad over the
 * flows' visible extent through {@link LavaFlowShader} twice, the crust and then its glow. Off GL (a review frame)
 * the offline drain shades the same mirror through {@link LavaFlowShading}.
 */
final class LavaFlowLayer implements LavaFlowShading.Field {

    private final LavaFlowShader shader = new LavaFlowShader();
    private final LavaFlowPass pass = new LavaFlowPass(this);

    private CellTopology builtFrom;
    private int gridW, gridH;
    private byte[] mirror;
    private int extentMinX, extentMinY, extentMaxX = -1, extentMaxY = -1;
    private int fieldTexture, noiseTexture;
    private boolean uploaded, broken;

    /** Queues this frame's flows, if the map has any in view. GL-free. */
    void collect(RenderContext ctx, DrawList out, ShuttleSpriteCache crust, String crustPath, float time) {
        CellTopology topology = ctx.sim.getTopology();
        if (topology != builtFrom) bind(topology);
        if (extentMaxX < extentMinX || broken || shader.isBroken() || crust == null) return;
        BattleCamera camera = ctx.camera;
        VisibleCellRect view = camera.visibleCells(1, gridW, gridH);
        int x0 = Math.max(extentMinX, view.minX()), y0 = Math.max(extentMinY, view.minY());
        int x1 = Math.min(extentMaxX, view.maxX()), y1 = Math.min(extentMaxY, view.maxY());
        if (x1 < x0 || y1 < y0) return;
        pass.minX = x0;
        pass.minY = y0;
        pass.maxX = x1;
        pass.maxY = y1;
        pass.screenX0 = camera.cellToScreenX(0f);
        pass.screenY0 = camera.cellToScreenY(0f);
        pass.cellPx = camera.cellPxSize();
        pass.time = time;
        pass.alpha = ctx.alphaMult;
        pass.crust = crust.sprite;
        pass.crustPath = crustPath;
        out.addCustom(RenderLayer.GROUND, pass);
    }

    /** Mirrors the map's flows: the smoothed cover and the heat, and the extent they reach. GL-free. */
    void bind(CellTopology topology) {
        builtFrom = topology;
        uploaded = false;
        float[] heat = topology.lavaFlows();
        gridW = topology.getWidth();
        gridH = topology.getHeight();
        extentMinX = extentMinY = Integer.MAX_VALUE;
        extentMaxX = extentMaxY = -1;
        if (heat.length != gridW * gridH) {
            mirror = null;
            return;
        }
        byte[] cover = LavaFlowShading.cover(heat, gridW, gridH);
        mirror = new byte[gridW * gridH * 4];
        for (int y = 0; y < gridH; y++) for (int x = 0; x < gridW; x++) {
            int i = y * gridW + x;
            mirror[i * 4] = cover[i];
            mirror[i * 4 + 1] = (byte) Math.round(Math.max(0f, Math.min(1f, heat[i])) * 255f);
            mirror[i * 4 + 3] = (byte) 255;
            if (cover[i] == 0) continue;
            extentMinX = Math.min(extentMinX, x);
            extentMinY = Math.min(extentMinY, y);
            extentMaxX = Math.max(extentMaxX, x);
            extentMaxY = Math.max(extentMaxY, y);
        }
    }

    // ---- LavaFlowShading.Field ------------------------------------------

    @Override public int width() { return gridW; }
    @Override public int height() { return gridH; }
    @Override public int texel(int x, int y, int c) { return mirror[(y * gridW + x) * 4 + c] & 0xFF; }

    // ---- GL ------------------------------------------------------------------

    void draw(LavaFlowPass p) {
        if (broken || mirror == null || p.crust == null) return;
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_ENABLE_BIT | GL_CURRENT_BIT);
        try {
            GlStateBracket.applyTextured2DState();
            upload();
            glActiveTexture(GL_TEXTURE2);
            glEnable(GL_TEXTURE_2D);
            p.crust.bindTexture();
            glActiveTexture(GL_TEXTURE1);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, noiseTexture);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fieldTexture);
            if (!shader.begin(gridW, gridH, p.crust.getTextureWidth(), p.crust.getTextureHeight(), p.time, p.alpha)) {
                return;
            }
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            shader.mode(0);
            quad(p);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            shader.mode(1);
            quad(p);
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

    private static void quad(LavaFlowPass p) {
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
            Reference.reachabilityFence(noise);   // LWJGL 2 keeps only the address
        }
        if (fieldTexture == 0) {
            fieldTexture = glGenTextures();
            uploaded = false;
        }
        if (uploaded) return;
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glBindTexture(GL_TEXTURE_2D, fieldTexture);
            ByteBuffer field = BufferUtils.createByteBuffer(mirror.length);
            field.put(mirror).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, gridW, gridH, 0, GL_RGBA, GL_UNSIGNED_BYTE, field);
            Reference.reachabilityFence(field);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            uploaded = true;
        } finally {
            glPopClientAttrib();
        }
    }

    /** Releases the textures and the program; the context that made them must be current. */
    void dispose() {
        if (fieldTexture != 0) glDeleteTextures(fieldTexture);
        if (noiseTexture != 0) glDeleteTextures(noiseTexture);
        fieldTexture = noiseTexture = 0;
        uploaded = false;
        shader.dispose();
        builtFrom = null;
    }
}
