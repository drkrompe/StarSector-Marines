package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** Sprite sheet + sliced frame list for one unit type or vehicle sheet. */
public final class UnitSpriteCache {
    public final SpriteAPI sheet;
    public final SpriteSheetFrames frames;

    public UnitSpriteCache(SpriteAPI sheet, SpriteSheetFrames frames) {
        this.sheet = sheet;
        this.frames = frames;
    }
}
