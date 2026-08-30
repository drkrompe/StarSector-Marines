package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

/** Single-frame sprite + natural aspect ratio for rotated sprites (shuttle, turret, drone). */
public final class ShuttleSpriteCache {
    public final SpriteAPI sprite;
    public final float aspect;
    /**
     * The image's pixel size, or {@code 0} when the loader did not record it.
     *
     * <p>Only a caller that addresses <em>part</em> of the image needs these —
     * a source sub-rect is in image pixels, so a whole-sprite draw never asks.
     * A cache built without them is still perfectly usable for that; the
     * broken-hull pass checks and falls back to drawing the whole sprite.
     */
    public final int pxW;
    public final int pxH;

    public ShuttleSpriteCache(SpriteAPI sprite, float aspect) {
        this(sprite, aspect, 0, 0);
    }

    public ShuttleSpriteCache(SpriteAPI sprite, float aspect, int pxW, int pxH) {
        this.sprite = sprite;
        this.aspect = aspect;
        this.pxW = pxW;
        this.pxH = pxH;
    }
}
