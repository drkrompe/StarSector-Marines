package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Arrays;

/**
 * A fixed run of whole-image quads laid out once in a local frame and drawn every frame afterwards by a
 * translation, a uniform scale and an alpha: what a composition that has stopped moving costs to draw again.
 *
 * <p>A burst Rav body is about thirty-three sprites, each placed by trigonometry over its rig every frame, and a
 * field of the settled dead is hundreds of them that will never move again. Recorded once, the body is one
 * command; the drain writes its quads with no trigonometry at all, because each quad's four corners were turned
 * when it was recorded and only have to be scaled and moved.
 *
 * <p>The quads keep their submission order, so the body paints exactly as it did quad by quad. Every coordinate
 * is in the unit the frame is scaled by (a cell, for a body), relative to the point it is drawn at.
 *
 * <p>Two addresses for every quad, because a draw may or may not have the atlas: the image itself (the host's
 * sprite call, and the headless drains) and, once {@link #addressAtlas} has resolved them, the quad's rectangle
 * in one atlas sheet, which lets the whole mesh join the sheet batch the quads around it are already in.
 */
public final class QuadMesh {

    private SpriteAPI[] images = new SpriteAPI[8];
    /** Per quad: centre x, centre y, width, height, angle in degrees. */
    private float[] place = new float[8 * 5];
    /** Per quad: the four corners already turned, BL BR TR TL, as offsets from the point drawn at. */
    private float[] corners = new float[8 * 8];
    /** Per quad: red, green, blue, alpha. */
    private float[] tint = new float[8 * 4];
    private int size;

    /** The sheet the atlas addresses were resolved against, or null before they have been. */
    private SpriteAPI atlasSheet;
    private Object atlasOwner;
    private int[] atlasRects;
    /** {@link #corners} with each quad whose image the atlas holds trimmed drawn over the part it holds; null when none is. */
    private float[] atlasCorners;
    private boolean atlasMissing;
    /** The atlas texture coordinates, worked out for the batch that last drew the mesh. */
    private QuadBatch uvBatch;
    private float[] uvs;

    /** Appends one quad, in the order it should paint. */
    public void add(SpriteAPI image, float cx, float cy, float w, float h, float angleDeg,
                    float r, float g, float b, float a) {
        if (size == images.length) {
            int grown = size * 2;
            images = Arrays.copyOf(images, grown);
            place = Arrays.copyOf(place, grown * 5);
            corners = Arrays.copyOf(corners, grown * 8);
            tint = Arrays.copyOf(tint, grown * 4);
        }
        images[size] = image;
        int p = size * 5;
        place[p] = cx;
        place[p + 1] = cy;
        place[p + 2] = w;
        place[p + 3] = h;
        place[p + 4] = angleDeg;
        // The same corner arithmetic as QuadBatch.appendRotated, done once.
        float halfW = w * 0.5f, halfH = h * 0.5f;
        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        int k = size * 8;
        corners[k] = cx + (-halfW) * cos - (-halfH) * sin;
        corners[k + 1] = cy + (-halfW) * sin + (-halfH) * cos;
        corners[k + 2] = cx + halfW * cos - (-halfH) * sin;
        corners[k + 3] = cy + halfW * sin + (-halfH) * cos;
        corners[k + 4] = cx + halfW * cos - halfH * sin;
        corners[k + 5] = cy + halfW * sin + halfH * cos;
        corners[k + 6] = cx + (-halfW) * cos - halfH * sin;
        corners[k + 7] = cy + (-halfW) * sin + halfH * cos;
        int t = size * 4;
        tint[t] = r;
        tint[t + 1] = g;
        tint[t + 2] = b;
        tint[t + 3] = a;
        size++;
        uvBatch = null;
        atlasSheet = null;
        atlasOwner = null;
        atlasMissing = false;
    }

    public int size() { return size; }
    public SpriteAPI image(int i) { return images[i]; }
    public float centerX(int i) { return place[i * 5]; }
    public float centerY(int i) { return place[i * 5 + 1]; }
    public float width(int i) { return place[i * 5 + 2]; }
    public float height(int i) { return place[i * 5 + 3]; }
    public float angleDegrees(int i) { return place[i * 5 + 4]; }
    public float red(int i) { return tint[i * 4]; }
    public float green(int i) { return tint[i * 4 + 1]; }
    public float blue(int i) { return tint[i * 4 + 2]; }
    public float alpha(int i) { return tint[i * 4 + 3]; }

    /**
     * Resolves an image to its rectangle in an atlas: {@code {x, y, w, h}}, or null for one it does not hold. An
     * atlas that holds only part of an image answers {@code {x, y, w, h, trimX, trimY, fullW, fullH}}: the held
     * {@code w x h} starts {@code trimX, trimY} into an image of {@code fullW x fullH}, {@code y} down from the top.
     */
    public interface AtlasAddress {
        int[] rect(SpriteAPI image);
    }

    /**
     * Resolves every quad's rectangle in {@code sheet}, once per {@code owner} (the atlas's identity, so a new
     * atlas is resolved afresh).
     *
     * @return whether every image is in the atlas; when one is not, the mesh draws through its images instead
     */
    public boolean addressAtlas(Object owner, SpriteAPI sheet, AtlasAddress address) {
        if (owner == atlasOwner && sheet == atlasSheet) return !atlasMissing;
        atlasOwner = owner;
        atlasSheet = sheet;
        uvBatch = null;
        atlasMissing = false;
        if (atlasRects == null || atlasRects.length < size * 4) atlasRects = new int[size * 4];
        atlasCorners = null;
        for (int i = 0; i < size; i++) {
            int[] rect = address.rect(images[i]);
            if (rect == null) {
                atlasMissing = true;
                break;
            }
            System.arraycopy(rect, 0, atlasRects, i * 4, 4);
            if (rect.length >= 8 && (rect[2] != rect[6] || rect[3] != rect[7])) trimCorners(i, rect);
        }
        return !atlasMissing;
    }

    /** Quad {@code i}'s corners over the part of its image the atlas holds, {@code rect} as {@link AtlasAddress} has it. */
    private void trimCorners(int i, int[] rect) {
        if (atlasCorners == null) atlasCorners = Arrays.copyOf(corners, size * 8);
        QuadBatch.partCorners(corners, i * 8, atlasCorners, i * 8,
                rect[4] / (float) rect[6], (rect[4] + rect[2]) / (float) rect[6],
                rect[5] / (float) rect[7], (rect[5] + rect[3]) / (float) rect[7], false);
    }

    /** Writes the mesh into {@code batch}, whose sheet is the atlas resolved by {@link #addressAtlas}. */
    void appendTo(QuadBatch batch, float x, float y, float scale, float alphaMult) {
        if (uvBatch != batch) {
            uvs = batch.meshUvs(atlasRects, size);
            uvBatch = batch;
        }
        batch.appendMesh(uvs, atlasCorners != null ? atlasCorners : corners, tint, size, x, y, scale, alphaMult);
    }
}
