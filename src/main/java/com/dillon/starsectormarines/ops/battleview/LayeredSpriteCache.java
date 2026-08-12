package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

/** One independent modular sprite plus its authored pixel dimensions. */
public final class LayeredSpriteCache {
    public final SpriteAPI sprite;
    public final int pxWidth;
    public final int pxHeight;

    public LayeredSpriteCache(SpriteAPI sprite, int pxWidth, int pxHeight) {
        this.sprite = sprite;
        this.pxWidth = pxWidth;
        this.pxHeight = pxHeight;
    }
}
