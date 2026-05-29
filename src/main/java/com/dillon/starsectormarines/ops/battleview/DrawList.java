package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Arrays;

/**
 * Per-frame, per-layer command buffer the renderer owns. Collecting passes emit
 * {@link DrawCommand}s tagged by {@link RenderLayer} through the typed
 * {@code add*} methods; {@link com.dillon.starsectormarines.render2d.DrawListRenderer}
 * drains each layer in submission order.
 *
 * <p><strong>Pooled.</strong> The command objects are reused across frames: each
 * layer keeps a growable {@code DrawCommand[]} plus a live count, and {@link #clear()}
 * just resets the counts (keeping the objects). Emitting overwrites the next slot
 * in place via {@code DrawCommand.set*}. A dense pass (GROUND, ~38k tiles) thus
 * allocates nothing in steady state — slots are only {@code new}'d while a layer's
 * high-water mark grows. Callers never construct a {@link DrawCommand}; the buffer
 * owns their lifecycle.
 *
 * <p>Not thread-safe; render is single-threaded.
 */
public final class DrawList {

    private static final int INITIAL_CAP = 64;

    private final DrawCommand[][] byLayer = new DrawCommand[RenderLayer.values().length][];
    private final int[] counts = new int[RenderLayer.values().length];

    public DrawList() {
        for (int i = 0; i < byLayer.length; i++) {
            byLayer[i] = new DrawCommand[INITIAL_CAP];
        }
    }

    /** Reserve and return the next reusable slot for {@code layer}, growing if needed. */
    private DrawCommand slot(RenderLayer layer) {
        int li = layer.ordinal();
        DrawCommand[] buf = byLayer[li];
        int c = counts[li];
        if (c == buf.length) {
            buf = Arrays.copyOf(buf, c * 2);
            byLayer[li] = buf;
        }
        DrawCommand cmd = buf[c];
        if (cmd == null) {
            cmd = new DrawCommand();
            buf[c] = cmd;
        }
        counts[li] = c + 1;
        return cmd;
    }

    public void addSheetQuad(RenderLayer layer, SpriteAPI sheet,
                             int srcX, int srcY, int srcW, int srcH,
                             float cx, float cy, float w, float h,
                             float r, float g, float b, float a) {
        slot(layer).setSheetQuad(sheet, srcX, srcY, srcW, srcH, cx, cy, w, h, r, g, b, a);
    }

    public void addSprite(RenderLayer layer, SpriteAPI sprite,
                          float cx, float cy, float w, float h, float angleDeg,
                          float r, float g, float b, float a) {
        slot(layer).setSprite(sprite, cx, cy, w, h, angleDeg, r, g, b, a);
    }

    /** {@code (x0,y0)}–{@code (x1,y1)} are opposing screen-space corners. */
    public void addSolidRect(RenderLayer layer, float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        slot(layer).setSolidRect(x0, y0, x1, y1, r, g, b, a);
    }

    public void addCustom(RenderLayer layer, Runnable draw) {
        slot(layer).setCustom(draw);
    }

    /** The live command backing array for one layer (valid indices: {@code 0..count(layer)}). */
    public DrawCommand[] buffer(RenderLayer layer) {
        return byLayer[layer.ordinal()];
    }

    /** Number of commands queued for one layer this frame. */
    public int count(RenderLayer layer) {
        return counts[layer.ordinal()];
    }

    /** Resets every layer's count to 0, retaining the pooled command objects. Call once per frame. */
    public void clear() {
        Arrays.fill(counts, 0);
    }
}
