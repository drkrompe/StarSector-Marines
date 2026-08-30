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
    private static final float FOGGED_ALPHA_MULTIPLIER = 1.75f;
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

        SpriteAPI fieldSheet = sprites.smokeFieldSheet();
        if (fieldSheet == null) return;
        SpecialEquipmentPresentationDef.Field recipe = equipment.presentation().field();
        int framePxW = (int) (fieldSheet.getWidth() / recipe.columns());
        int framePxH = (int) (fieldSheet.getHeight() / recipe.rows());
        if (framePxW <= 0 || framePxH <= 0) return;

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
                // Each puff runs the flipbook on its own clock: its own phase
                // offset and a mild rate spread. Played in lockstep the whole
                // field would billow and thin as one object, which is what
                // reads as a decal rather than smoke.
                float rate = recipe.framesPerSecond() * (0.75f + 0.5f * hash01(field.id() + 61, i));
                float cycle = elapsed * rate + hash01(field.id() + 79, i) * recipe.frameCount();
                int frame = recipe.firstFrame() + pingPong((int) cycle, recipe.frameCount());
                boolean revealed = ctx.sim.getFogOfWar().isCellRevealed(
                        (int) Math.floor(x), (int) Math.floor(y));
                float puffAlpha = alphaForVisibility(
                        alpha * (i == 0 ? 0.72f : 0.48f), revealed);
                out.addSheetQuad(RenderLayer.SMOKE, fieldSheet,
                        (frame % recipe.columns()) * framePxW,
                        (frame / recipe.columns()) * framePxH,
                        framePxW, framePxH,
                        camera.cellToScreenX(x), camera.cellToScreenY(y),
                        diameter, diameter,
                        hash01(field.id() + 47, i) * 360f,
                        recipe.tintRed(), recipe.tintGreen(), recipe.tintBlue(),
                        puffAlpha);
            }
        }
    }

    /**
     * Fog already paints before smoke, but its near-black field makes the
     * translucent flipbook difficult to read at its ordinary opacity. A
     * fogged puff receives presentation-only contrast without revealing the
     * terrain or actors beneath it, and still obeys the field's fade envelope.
     */
    static float alphaForVisibility(float alpha, boolean revealed) {
        return revealed ? alpha : Math.min(1f, alpha * FOGGED_ALPHA_MULTIPLIER);
    }

    /**
     * Frame offset within a {@code frames}-long band, played forward then
     * back. The sheet's frames are a puff dissipating, so looping them
     * would snap a vanished puff back to full size once a cycle; bouncing
     * makes the same art read as one cloud boiling, and never empties a
     * puff for longer than the turn at the thin end.
     */
    private static int pingPong(int step, int frames) {
        if (frames <= 1) return 0;
        int span = (frames - 1) * 2;
        int wrapped = ((step % span) + span) % span;
        return wrapped < frames ? wrapped : span - wrapped;
    }

    private static float hash01(long id, int index) {
        long value = id * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return (value >>> 40) / (float) (1L << 24);
    }
}
