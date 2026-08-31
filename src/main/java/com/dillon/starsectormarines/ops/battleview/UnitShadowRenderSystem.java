package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * What the sun casts from bodies, as opposed to from terrain.
 *
 * <p>The ground composite already shades a street from the wall beside it, and
 * does it without drawing anything: terrain heights live in a cell field the
 * shader marches over. A marine has no cell to put a height in, so a body
 * draws its own shadow, on its own layer, beneath itself.
 *
 * <h2>Two kinds of height, and only one of them is physical</h2>
 * <p>A ground unit's height is real. It comes from
 * {@code UnitType.hitHalfHeight}, the silhouette ballistics already fires at,
 * so a shadow and a bullet cannot disagree about how tall a marine is — the
 * same argument that puts a window's sill at the height its barrier already
 * gives as cover. Its shadow is therefore offset away from the sun by
 * {@link SunLight#reachCells}, exactly as a wall's is.
 *
 * <p><b>An aircraft's altitude is not real.</b> {@code AirAppearance} sells
 * height with a screen-Y offset of at most
 * {@link AirAppearance#VISUAL_ALT_PEAK_CELLS} cells and says plainly that
 * sim-space position is unchanged. There is no altitude in metres to hand the
 * sun. Casting from an invented one would be worse than useless: a cruising
 * shuttle at any honest altitude throws its shadow clean off the screen.
 *
 * <p>So an aircraft's shadow sits at its <em>true ground position</em> and does
 * not chase the sun at all. The hull is already drawn those few cells up, so the
 * gap between hull and shadow is the altitude cue the game had all along —
 * previously ambiguous, because a sprite shifted up the screen and a sprite
 * further north look identical from above. The shadow is what disambiguates it.
 * Mixing in a sun offset as well would be two altitude models arguing.
 *
 * <h2>Fog</h2>
 * <p>A shadow is gated on exactly the visibility its caster is, because a
 * shadow nobody should see is a unit nobody should see. That gate is load
 * bearing rather than cosmetic: an ungated shadow would report the position of
 * a hidden enemy.
 */
public final class UnitShadowRenderSystem implements RenderSystem {

    /** How dark a shadow is at full strength, before the sun's own dial scales it. */
    private static final float SHADOW_ALPHA = 0.42f;

    /**
     * How wide the blob is relative to the caster's body radius. Wider than the
     * body because a soft falloff has no edge — sized to the body exactly, the
     * visible part reads much smaller than the thing casting it.
     */
    private static final float BLOB_WIDTH_PER_RADIUS = 2.6f;

    /** An aircraft's shadow at full altitude, relative to its size on the ground. */
    private static final float AIR_BLOB_MIN_SCALE = 0.55f;

    /** And how much of its opacity it keeps up there. A shadow thrown from higher is fainter and more diffuse. */
    private static final float AIR_ALPHA_AT_ALTITUDE = 0.45f;

    private final BattleSprites sprites;
    private final SunLight sun;

    public UnitShadowRenderSystem(BattleSprites sprites, SunLight sun) {
        this.sprites = sprites;
        this.sun = sun;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.UNIT_SHADOWS;
    }

    /**
     * Every shadow is the engine glow, tinted dark: a radial falloff is a radial
     * falloff whatever it was drawn for, and the softness is most of what sells
     * this. A hard-edged quad reads as a sticker.
     */
    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (!sun.casts()) return;
        sprites.ensureEngineFxSprites();
        SpriteAPI blob = sprites.engineGlowSprite();
        if (blob == null) return;

        collectGroundBodies(ctx, out, blob);
        collectAircraft(ctx, out, blob);
    }

    /**
     * Every live body on the ground, offset away from the sun by its own
     * height. Walks the same archetype the unit sprites do and applies the same
     * two gates in the same order — a released row first, then visibility.
     */
    private void collectGroundBodies(RenderContext ctx, DrawList out, SpriteAPI blob) {
        BattleComponents c = ctx.sim.getBattleComponents();
        BattleCamera cam = ctx.camera;
        UnitRosterService roster = ctx.sim.getRoster();
        FogOfWarService vis = ctx.sim.getFogOfWar();
        float cellPx = cam.cellPxSize();

        for (ArchetypeTable t : ctx.sim.getEntityWorld().matched(c.liveSprites)) {
            float[] hp = t.floats(c.HEALTH, BattleComponents.HEALTH_HP).array();
            float[] rx = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] ry = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();

            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (hp[r] <= 0f) continue;

                long entityId = t.entityAt(r);
                int denseIdx = roster.indexOf(entityId);
                byte uv = vis.getUnitVisibility(denseIdx);
                if (uv == FogOfWarService.VIS_HIDDEN) continue;
                float alpha = ctx.alphaMult;
                if (uv == FogOfWarService.VIS_FADING) alpha *= vis.getFadeAlpha(denseIdx);

                float radiusCells = roster.radius(entityId);
                float heightCells = 2f * roster.hitHalfHeight(entityId);
                if (radiusCells <= 0f || heightCells <= 0f) continue;

                float reach = sun.reachCells(heightCells);
                // Away from the sun, from the body's own feet.
                float shadowX = rx[r] - sun.dirX() * reach * 0.5f;
                float shadowY = ry[r] - sun.dirY() * reach * 0.5f;

                float width = radiusCells * BLOB_WIDTH_PER_RADIUS * cellPx;
                float length = width + reach * cellPx;
                emit(out, blob, cam, shadowX, shadowY, width, length,
                        shadowAngleDegrees(), alpha * SHADOW_ALPHA * sun.shadowStrength());
            }
        }
    }

    /**
     * Aircraft, at their true ground position. See the class doc: the hull's own
     * upward render offset is the altitude cue, and the shadow is what makes it
     * readable as height rather than as northward travel.
     */
    private void collectAircraft(RenderContext ctx, DrawList out, SpriteAPI blob) {
        long[] airIds = ctx.sim.getAirEntityIds();
        if (airIds.length == 0) return;

        World world = ctx.sim.world();
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();

        for (long id : airIds) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.isOnMap()) continue;
            AirBody body = world.kinematics(id);
            if (body == null) continue;

            float altitudeT = world.altitudeT(id);
            float footprint = AirAppearance.scaleMult(altitudeT, world.flightPhase(id));
            float scale = lerp(1f, AIR_BLOB_MIN_SCALE, altitudeT);
            float alpha = ctx.alphaMult * SHADOW_ALPHA * sun.shadowStrength()
                    * lerp(1f, AIR_ALPHA_AT_ALTITUDE, altitudeT);

            float size = footprint * scale * BLOB_WIDTH_PER_RADIUS * cellPx;
            emit(out, blob, cam, body.x, body.y, size, size, body.facingDegrees, alpha);
        }
    }

    /**
     * The bearing a ground shadow lies along: directly away from the sun.
     *
     * <p>The draw list rotates a quad about its centre and cannot shear one, so
     * a shadow is an ellipse pointed down-sun rather than a true projection of
     * the body's silhouette. At the sizes a body casts that reads as a shadow;
     * it would not for something as tall as a building, which is exactly why
     * terrain is shaded by the height field instead of by this.
     */
    private float shadowAngleDegrees() {
        return (float) Math.toDegrees(Math.atan2(-sun.dirY(), -sun.dirX()));
    }

    private static void emit(DrawList out, SpriteAPI blob, BattleCamera cam,
                             float worldX, float worldY,
                             float width, float length, float angleDegrees, float alpha) {
        if (alpha <= 0.004f || width <= 0f || length <= 0f) return;
        out.addSprite(RenderLayer.UNIT_SHADOWS, blob,
                cam.cellToScreenX(worldX), cam.cellToScreenY(worldY),
                length, width, angleDegrees,
                SunLight.TINT_R * 0.35f, SunLight.TINT_G * 0.35f, SunLight.TINT_B * 0.35f,
                alpha);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }
}
