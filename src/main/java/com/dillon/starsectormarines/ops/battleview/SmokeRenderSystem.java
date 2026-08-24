package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** Visual mirror of simulation-owned grenade arcs and smoke-field footprints. */
public final class SmokeRenderSystem implements RenderSystem {

    private static final int PUFF_COUNT = 9;
    private final BattleSprites sprites;
    private final SpecialEquipmentDef equipment;

    public SmokeRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
        this.equipment = SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
    }

    @Override public RenderLayer layer() { return RenderLayer.SMOKE; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        SpriteAPI grenadeSprite = sprites.smokeGrenadeSprite();
        if (grenadeSprite != null) {
            for (SmokeFieldService.SmokeThrowView grenade
                    : ctx.sim.smokeFields().throwsInFlight()) {
                SpecialEquipmentPresentationDef.Thrown thrown =
                        equipment.presentation().thrown();
                out.addSprite(RenderLayer.SHOTS, grenadeSprite,
                        camera.cellToScreenX(grenade.x()),
                        camera.cellToScreenY(grenade.y() + grenade.z()),
                        cellPx * thrown.widthCells(), cellPx * thrown.heightCells(),
                        grenade.progress() * 540f,
                        0.82f, 0.88f, 0.90f, ctx.alphaMult);
            }
        }

        SpriteAPI puffSprite = sprites.smokePuffSprite();
        if (puffSprite == null) return;
        for (SmokeFieldService.SmokeFieldView field : ctx.sim.smokeFields().activeFields()) {
            float elapsed = field.totalDuration() - field.remaining();
            float fadeIn = Math.min(1f, elapsed / 0.7f);
            float fadeOut = Math.min(1f, field.remaining() / 1.5f);
            float alpha = fadeIn * fadeOut * ctx.alphaMult;
            for (int i = 0; i < PUFF_COUNT; i++) {
                float unit = hash01(field.id(), i);
                float angle = unit * (float) (Math.PI * 2.0) + i * 2.3999632f;
                float radial = i == 0 ? 0f : field.radius() * (0.20f + 0.42f * hash01(field.id() + 17, i));
                float x = field.x() + (float) Math.cos(angle) * radial;
                float y = field.y() + (float) Math.sin(angle) * radial;
                float diameter = field.radius() * cellPx
                        * (i == 0 ? 1.55f : 0.90f + 0.45f * hash01(field.id() + 31, i));
                out.addSprite(RenderLayer.SMOKE, puffSprite,
                        camera.cellToScreenX(x), camera.cellToScreenY(y),
                        diameter, diameter,
                        hash01(field.id() + 47, i) * 360f,
                        0.70f, 0.73f, 0.75f, alpha * (i == 0 ? 0.72f : 0.48f));
            }
        }
    }

    private static float hash01(long id, int index) {
        long value = id * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return (value >>> 40) / (float) (1L << 24);
    }
}
