package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMesh;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * The holo board's drawing space: deck metres pushed through the guide camera and the
 * banner's fit, plus the few paint helpers the board's volume and bodies share.
 *
 * <p>Every point the hologram draws is a deck point projected here, one vertex at a time,
 * so rings, globes and cards foreshorten with the room instead of being flat decals.
 * The disc frame is the board's own: {@code a} runs right, {@code b} away along the
 * tilted disc, and {@code c} along the disc's normal.
 */
final class HoloSpace {
    /** The soft round particle the game ships; drawn additively for halos and light. */
    static final String GLOW = "graphics/fx/particlealpha64linear.png";

    final CommandDeckLayout layout;
    final CommandDeckLayout.Fit fit;
    private final float[] centre;
    private final float sinTilt;
    private final float cosTilt;

    HoloSpace(CommandDeckLayout layout, CommandDeckLayout.Fit fit) {
        this.layout = layout;
        this.fit = fit;
        float[] table = layout.tableCentre();
        this.centre = new float[]{table[0], table[1] + HqHoloBoard.LIFT, table[2]};
        this.sinTilt = (float) Math.sin(HqHoloBoard.TILT);
        this.cosTilt = (float) Math.cos(HqHoloBoard.TILT);
    }

    /** Deck point of a disc-frame point: {@code a} right, {@code b} away on the disc, {@code c} off it. */
    float[] deck(float a, float b, float c) {
        return new float[]{centre[0] + a, centre[1] + b * sinTilt + c * cosTilt, centre[2] + b * cosTilt - c * sinTilt};
    }

    /** Banner pixels of a deck point. */
    float[] screen(float x, float y, float z) {
        float[] uv = layout.project(x, y, z);
        return new float[]{fit.x(uv[0]), fit.y(uv[1])};
    }

    float[] screen(float[] deck) { return screen(deck[0], deck[1], deck[2]); }

    /** Banner pixels of a disc-frame point. */
    float[] disc(float a, float b, float c) { return screen(deck(a, b, c)); }

    /** Banner pixels per deck metre, across the line of sight, at depth {@code z}. */
    float pixelsPerMetre(float z) {
        return layout.camera().focal() / z * fit.scale();
    }

    /** Depth of the disc's centre; nearer points than this read brighter. */
    float centreDepth() { return centre[2]; }

    /** 1 at the near rim of the volume, 0 at the far rim, for depth-dimming. */
    float nearness(float z) {
        return clamp01(0.5f + (centre[2] - z) / (2f * HqHoloBoard.RADIUS));
    }

    // ---- paint ------------------------------------------------------------------

    static void glow(CanvasContext context, float cx, float cy, float w, float h, Color color) {
        if (color.getAlpha() <= 0 || w <= 0f || h <= 0f) return;
        context.sprite(GLOW, null, cx, cy, w, h, 0f, color, CanvasSpriteRegion.FULL, CanvasBlend.ADDITIVE);
    }

    /** A filled convex polygon as one fan mesh, so translucent fills carry no seams. */
    static void fill(CanvasContext context, float[] xy, Color color) {
        int n = xy.length / 2;
        if (n < 3 || color.getAlpha() <= 0) return;
        float[] tris = new float[(n - 2) * 6];
        for (int i = 1; i < n - 1; i++) {
            int o = (i - 1) * 6;
            tris[o] = xy[0];
            tris[o + 1] = xy[1];
            tris[o + 2] = xy[i * 2];
            tris[o + 3] = xy[i * 2 + 1];
            tris[o + 4] = xy[i * 2 + 2];
            tris[o + 5] = xy[i * 2 + 3];
        }
        context.mesh(new CanvasMesh(tris), 0f, 0f, 1f, color);
    }

    /** A screen-space circle outline. */
    static void ring(CanvasContext context, float cx, float cy, float r, Color color, float width) {
        if (color.getAlpha() == 0 || r <= 0f) return;
        int n = Math.max(12, Math.min(40, Math.round(r * 1.2f)));
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            context.line(cx + r * (float) Math.cos(a0), cy + r * (float) Math.sin(a0),
                    cx + r * (float) Math.cos(a1), cy + r * (float) Math.sin(a1), color, width);
        }
    }

    /** The board's SVG art, loaded once per board; empty where the asset source is unavailable. */
    static final class Art {
        private final Map<String, SvgAsset> loaded = new HashMap<>();
        private boolean failed;

        /** A board SVG by file stem, or null where the asset source is unavailable. */
        SvgAsset get(String stem) {
            if (failed) return null;
            SvgAsset cached = loaded.get(stem);
            if (cached != null) return cached;
            try {
                SvgAsset asset = SvgAsset.load("graphics/ui/hq-command-deck/" + stem + ".svg");
                loaded.put(stem, asset);
                return asset;
            } catch (RuntimeException unavailable) {
                failed = true;
                return null;
            }
        }
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color alpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, Math.round(alpha))));
    }

    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    static float fraction(float value) { return value - (float) Math.floor(value); }

    /** A stable pseudo-random value in [0, 1) for an integer key. */
    static float hash(int key) {
        int h = key * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return (h >>> 8) / (float) (1 << 24);
    }
}
