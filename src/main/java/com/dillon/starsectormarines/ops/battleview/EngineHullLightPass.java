package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.engine.EngineHullLighting;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** One exact aircraft hull pose and its material-clipped additive engine illumination. */
public record EngineHullLightPass(SpriteAPI hullSprite, float centerX, float centerY,
        float widthPx, float heightPx, float angleDegrees, float hullWidthCells,
        float hullLengthCells, float pivotX, float pivotY, float alphaMult,
        EngineHullLighting.Light[] lights, EngineHullLightShader shader) implements Runnable {
    @Override public void run() { shader.draw(this); }
}
