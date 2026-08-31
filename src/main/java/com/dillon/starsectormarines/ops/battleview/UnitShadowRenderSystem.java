package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.air.engine.HullPivotResolver;
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
 * <h2>Two shapes, because two things are being drawn</h2>
 * <p>A body on the ground casts an <b>ellipse</b>. Nothing about a marine's
 * outline survives being projected onto the ground at this scale, so a soft
 * oval at the feet is both what an eye expects and all the information there
 * is. An earlier version stretched that oval down-sun by the sun's whole
 * reach, on the theory that a shadow is as long as the light says it is. At a
 * marine's height that is three times the body's own length, and a soft blob
 * three times too long does not read as a shadow — it reads as a smear
 * trailing off the model.
 *
 * <p>An aircraft casts <b>its own hull</b>. The same sprite the aircraft is
 * drawn with, tinted away to nothing and laid on the ground: a silhouette,
 * because at eight to twelve cells long the outline is the whole point and an
 * oval that size is a puddle. It costs no new art and cannot drift out of step
 * with the hull, since it <em>is</em> the hull.
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
     * How wide the ellipse is relative to the caster's body radius. Wider than
     * the body because a soft falloff has no edge — sized to the body exactly,
     * the visible part reads much smaller than the thing casting it.
     */
    private static final float BLOB_WIDTH_PER_RADIUS = 2.6f;

    /**
     * How much longer than wide the ellipse is, along the down-sun axis.
     *
     * <p>A lean rather than a projection. The sun's true reach at a marine's
     * height is about three body-lengths, which drawn out is the smear this
     * replaced; but a perfectly round blob under a body standing beside a wall
     * that <em>does</em> lie down-sun reads as a second, contradictory light.
     * This is the smallest elongation that keeps the two agreeing.
     */
    private static final float ELLIPSE_LENGTH_PER_WIDTH = 1.3f;

    /**
     * How far down-sun the ellipse sits, as a fraction of the sun's true reach
     * for the caster's height. Small, for the same reason the elongation is: at
     * anything near the full reach the ellipse leaves the feet of the thing
     * casting it and becomes a separate object on the ground.
     */
    private static final float ELLIPSE_LEAN_FRACTION = 0.25f;

    /**
     * An aircraft silhouette's opacity, below a ground body's.
     *
     * <p>Lower rather than higher, which reverses the previous value and is
     * worth saying why. While the aircraft cast a stretched radial blob it had
     * to ask for roughly triple a marine's alpha merely to be visible at all,
     * because a falloff spread over seven times the length is nearly all faint
     * tail. A silhouette has no falloff: every pixel inside the hull's outline
     * is at full strength, so the number that was barely visible as a blob
     * would be a hole in the ground as a shape.
     */
    private static final float AIR_SHADOW_ALPHA = 0.42f;

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
     * <p>Judged against the aircraft panel of the {@code sun-shadows} suite,
     * where it is what separates the hull from its shadow at full altitude
     * without throwing the shadow clean out of the same shot.
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
     * A ground body's ellipse is the engine glow, tinted dark: a radial falloff
     * is a radial falloff whatever it was drawn for, and the soft edge is most
     * of what sells this. A hard-edged quad reads as a sticker. An aircraft
     * brings its own shape and needs no such stand-in.
     */
    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (!sun.casts()) return;
        sprites.ensureEngineFxSprites();
        SpriteAPI blob = sprites.engineGlowSprite();
        if (blob != null) collectGroundBodies(ctx, out, blob);
        collectAircraft(ctx, out);
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

                // A lean away from the sun rather than a projection along
                // it. The reach still sets the lean, so a taller body's shadow
                // sits further out than a shorter one's.
                float lean = sun.reachCells(heightCells) * ELLIPSE_LEAN_FRACTION;
                float shadowX = rx[r] - sun.dirX() * lean;
                float shadowY = ry[r] - sun.dirY() * lean;

                float width = radiusCells * BLOB_WIDTH_PER_RADIUS * cellPx;
                float length = width * ELLIPSE_LENGTH_PER_WIDTH;
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
    private void collectAircraft(RenderContext ctx, DrawList out) {
        long[] airIds = ctx.sim.getAirEntityIds();
        if (airIds.length == 0) return;
        sprites.ensureAirframeSprites();

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
            ShuttleSpriteCache cache = sprites.airframeSprites().get(frame);
            if (cache == null) continue;

            // The hull's real extent, from the same resolver the hull sprite
            // uses. An earlier version sized this from AirAppearance.scaleMult,
            // which is an altitude zoom of about 1.2 rather than a length in
            // cells -- so a twelve-cell transport cast a shadow the size of a
            // marine's and it was invisible under its own aircraft. The
            // preview is what found it; the arithmetic had looked fine.
            float hullLengthCells = HullFootprintResolver.visualLengthCells(frame.renderHullId());
            if (hullLengthCells <= 0f) continue;

            float alpha = ctx.alphaMult * AIR_SHADOW_ALPHA * sun.shadowStrength();

            // Away from the sun by the altitude the hull is drawn at, which is
            // the game's own altitude rather than a second one invented here.
            float reach = sun.reachCells(AIR_SHADOW_ALTITUDE_CELLS * world.altitudeT(id));

            // The craft's footprint on the ground, and nothing about how high it
            // is. Under a directional sun a rigid body's shadow is the size of
            // the body whatever its altitude; the hull drawing larger as it
            // climbs is a camera-proximity cue rather than growth. So this is
            // AirAppearance.GROUND_SCALE where the hull draw uses scaleMult, and
            // the two deliberately diverge as the aircraft rises.
            float pxLen = hullLengthCells * cellPx * AirAppearance.GROUND_SCALE;

            // The same pivot correction the hull draw makes, at the scale the
            // shadow is drawn at. Without it the silhouette sits off its own
            // aircraft by the hull's centre-of-gravity offset -- a fixed error
            // that would read as a sun bearing nobody set.
            float[] pivot = HullPivotResolver.pivotOffset(frame.renderHullId());
            float rad = (float) Math.toRadians(body.facingDegrees);
            float pc = (float) Math.cos(rad);
            float psn = (float) Math.sin(rad);
            float pvx = pivot[0] * AirAppearance.GROUND_SCALE;
            float pvy = pivot[1] * AirAppearance.GROUND_SCALE;
            float shadowX = body.x + (pvx * pc - pvy * psn) - sun.dirX() * reach;
            float shadowY = body.y + (pvx * psn + pvy * pc) - sun.dirY() * reach;

            emit(out, cache.sprite, cam, shadowX, shadowY,
                    pxLen * cache.aspect, pxLen, body.facingDegrees, alpha);
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

    /**
     * Lays one sprite on the ground as a shadow of itself: tinted to the sun's
     * own shade at a third strength, so what survives the draw is the sprite's
     * alpha and none of its colour. A silhouette and a soft ellipse are the
     * same draw; only the sprite differs.
     */
    private static void emit(DrawList out, SpriteAPI shape, BattleCamera cam,
                             float worldX, float worldY,
                             float width, float length, float angleDegrees, float alpha) {
        if (alpha <= 0.004f || width <= 0f || length <= 0f) return;
        out.addSprite(RenderLayer.UNIT_SHADOWS, shape,
                cam.cellToScreenX(worldX), cam.cellToScreenY(worldY),
                length, width, angleDegrees,
                SunLight.TINT_R * 0.35f, SunLight.TINT_G * 0.35f, SunLight.TINT_B * 0.35f,
                alpha);
    }
}
