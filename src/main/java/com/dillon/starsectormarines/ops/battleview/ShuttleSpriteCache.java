package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

/** Single-frame sprite + natural aspect ratio for rotated sprites (shuttle, turret, drone). */
public final class ShuttleSpriteCache {
    public final SpriteAPI sprite;
    public final float aspect;

    public ShuttleSpriteCache(SpriteAPI sprite, float aspect) {
        this.sprite = sprite;
        this.aspect = aspect;
    }
}
