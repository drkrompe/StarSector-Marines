package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

/** One independent modular sprite plus its authored pixel dimensions. */
public final class LayeredSpriteCache {
    public final SpriteAPI sprite;
    public final String sourcePath;
    public final int pxWidth;
    public final int pxHeight;

    public LayeredSpriteCache(SpriteAPI sprite, int pxWidth, int pxHeight) {
        this(sprite, null, pxWidth, pxHeight);
    }

    public LayeredSpriteCache(SpriteAPI sprite, String sourcePath,
                              int pxWidth, int pxHeight) {
        this.sprite = sprite;
        this.sourcePath = sourcePath;
        this.pxWidth = pxWidth;
        this.pxHeight = pxHeight;
    }

    /** Headless asset token: dimensions and source identity without a game sprite. */
    public static LayeredSpriteCache headless(String sourcePath, int pxWidth, int pxHeight) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("headless sprite source path is required");
        }
        return new LayeredSpriteCache(null, sourcePath, pxWidth, pxHeight);
    }
}
