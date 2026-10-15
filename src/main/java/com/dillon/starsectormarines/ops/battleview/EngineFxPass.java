package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer;
import com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * One aircraft's engine plumes as a {@link RenderLayer#SHUTTLES} custom pass.
 *
 * <p>It is a named type rather than a lambda so a host that cannot run GL can recognise it: the live drain simply
 * {@link #run runs} it, and the headless review drain calls {@link #draw} with sprites of its own that record where
 * each plume lands instead of drawing it. Both go through the one {@link EngineFxRenderer}, so the plume geometry
 * cannot drift between them.
 */
public record EngineFxPass(Airframe frame, float worldX, float worldY, float facingDegrees,
                           float scaleMult, float altOffsetCells, float intensity, float alphaMult,
                           BattleCamera camera, SpriteAPI glowSprite, SpriteAPI flameSprite,
                           float[] perSlotDemand) implements Runnable {

    @Override
    public void run() {
        draw(glowSprite, flameSprite);
    }

    /** Draws the plumes through the given sprites rather than the pass's own. */
    public void draw(SpriteAPI glow, SpriteAPI flame) {
        EngineFxRenderer.draw(EngineSlotResolver.resolve(frame), worldX, worldY, facingDegrees, scaleMult,
                altOffsetCells, intensity, alphaMult, camera, glow, flame, perSlotDemand);
    }
}
