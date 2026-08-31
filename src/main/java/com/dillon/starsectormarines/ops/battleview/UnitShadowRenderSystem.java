package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
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
 * <p>So an aircraft casts from its <em>presentational</em> altitude: the same
 * {@link AirAppearance#VISUAL_ALT_PEAK_CELLS} the hull is lifted by, treated as
 * a height and run through the same {@link SunLight#reachCells} a wall uses. No
 * invented metres, and the sun is never asked to reconcile two altitude models.
 *
 * <p>An earlier version left the shadow at the true ground position, reasoning
 * that the hull's own upward shift was already the gap. It is not: three cells
 * of lift against an eight-cell transport leaves the shadow entirely underneath
 * its own aircraft at every altitude. The preview showed that immediately and
 * the arithmetic never would have — separation is the whole cue, and the
 * lateral offset is what provides it.
 *
 * <h2>Fog</h2>
 * <p>A shadow is gated on exactly the visibility its caster is, because a
 * shadow nobody should see is a unit nobody should see. That gate is load
 * bearing rather than cosmetic: an ungated shadow would report the position of
 * a hidden enemy.
 */
public final class UnitShadowRenderSystem implements RenderSystem {

    /** How dark a shadow is at full strength, before the sun's own dial scales it. */
    private static final float SHADOW_ALPHA = 0.55f;

    /**
     * How wide the blob is relative to the caster's body radius. Wider than the
     * body because a soft falloff has no edge — sized to the body exactly, the
     * visible part reads much smaller than the thing casting it.
     */
    private static final float BLOB_WIDTH_PER_RADIUS = 2.6f;

    /**
     * How wide an aircraft's shadow is against its length. A hull is longer
     * than it is wide from above, and a circular blob under a transport reads
     * as a puddle rather than as the machine casting it.
     */
    private static final float AIR_BLOB_WIDTH_FRACTION = 0.55f;

    /**
     * How much of its opacity an aircraft's shadow keeps at altitude.
     *
     * <p>The one thing height honestly changes about a shadow: thrown from
     * further off it has a wider penumbra and reads softer. It does not read
     * smaller — see the sizing below.
     */
    private static final float AIR_ALPHA_AT_ALTITUDE = 1.0f;

    /**
     * An aircraft shadow's own opacity, well above a ground body's.
     *
     * <p>Not a preference: the blob is a radial falloff, so a marine's
     * twenty-pixel shadow is almost entirely bright core while a transport's
     * hundred-and-forty-pixel one is mostly the faint tail. Stretching the same
     * sprite over seven times the length costs most of its contrast, and the
     * larger caster has to ask for it back.
     *
     * <p>Measured, not chosen. Rendering the same frame with and without this
     * layer and differencing it: at a ground body's alpha the aircraft darkened
     * its ground by a mean of 8 levels out of 255, which is invisible and cost
     * several rounds of believing the shadow was not drawn at all. Marines,
     * which read clearly, measure a mean of 10. This lands the aircraft near 19
     * — comfortably above the readable benchmark, because a shadow that large
     * is spread thin.
     */
    private static final float AIR_SHADOW_ALPHA = 0.95f;

    /**
     * The altitude, in cells, an aircraft at full height casts from.
     *
     * <p>Its own constant rather than {@link AirAppearance#VISUAL_ALT_PEAK_CELLS},
     * which was chosen to nudge a sprite a few cells up the screen and is far
     * too small for this job: three cells at the default sun offsets a shadow
     * about a fifth of a transport's length, so it never clears the aircraft
     * casting it and cannot be seen at all. Separation has to be comparable to
     * the hull to read, which makes this a presentational choice of the same
     * kind as the lift.
     *
     * <p><b>Unverified.</b> The value is reasoned, not measured: the aircraft
     * shadow is collected on every frame and has never yet been seen painted,
     * so nothing here has been judged against a picture the way the ground
     * constants were. Treat it as a starting point for whoever finds out why.
     */
    private static final float AIR_SHADOW_ALTITUDE_CELLS = 11f;

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

            Airframe frame = world.airframe(id);
            if (frame == null) continue;

            // The hull's real extent, from the same resolver the hull sprite
            // uses. An earlier version sized this from AirAppearance.scaleMult,
            // which is an altitude zoom of about 1.2 rather than a length in
            // cells -- so a twelve-cell transport cast a shadow the size of a
            // marine's and it was invisible under its own aircraft. The
            // preview is what found it; the arithmetic had looked fine.
            float hullLengthCells = HullFootprintResolver.visualLengthCells(frame.renderHullId());
            if (hullLengthCells <= 0f) continue;

            float altitudeT = world.altitudeT(id);
            float alpha = ctx.alphaMult * AIR_SHADOW_ALPHA * sun.shadowStrength()
                    * lerp(1f, AIR_ALPHA_AT_ALTITUDE, altitudeT);

            // Away from the sun by the altitude the hull is drawn at, which is
            // the game's own altitude rather than a second one invented here.
            float reach = sun.reachCells(AIR_SHADOW_ALTITUDE_CELLS * altitudeT);
            float shadowX = body.x - sun.dirX() * reach;
            float shadowY = body.y - sun.dirY() * reach;

            // The craft's footprint on the ground, and nothing about how high it
            // is. Under a directional sun a rigid body's shadow is the size of
            // the body whatever its altitude; the hull drawing larger as it
            // climbs is a camera-proximity cue rather than growth. An earlier
            // version had the shadow SHRINK as the hull grew, which is backwards
            // twice over and left a transport casting less than the marine
            // standing beside it.
            float length = hullLengthCells * AirAppearance.GROUND_SCALE * cellPx;
            emit(out, blob, cam, shadowX, shadowY,
                    length * AIR_BLOB_WIDTH_FRACTION, length, body.facingDegrees, alpha);
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
