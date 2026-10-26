package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.TurretShieldService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a planetary shield dome the way a warship's shield is drawn: the game's
 * pale cyan for a dome that protects the player's side, a warm red for one that
 * protects the enemy's, a faint fill and a bright rim at the dome's edge, a
 * collapse amber when it overloads, and an impact where a round was taken.
 *
 * <p><b>The dome itself is a shader</b> ({@link ShieldDomeShader}): an energy
 * bubble seen from above, a bright rim over a faint interior with a drifting
 * hexagonal field, the share of cells alight tracking the flux held, a collapse
 * that goes out cell by cell and a rim that re-forms round the circle as it
 * recovers. Each absorbed round is an impact on it: a flash where it landed, a
 * ripple crossing the dome and the hex cells it passes lighting, scaled by what
 * the round cost. Where the shader is switched off or fails to build, the flat
 * fill-and-rim drawing below is used instead. A directional shield is the same
 * bubble cut to an arc ({@link ShieldDomeDraw#arcCos}), in the shader and in the
 * flat drawing alike.
 *
 * <p>What the dome has left is drawn, not printed: an inner gauge ring fills as
 * the pool does and goes amber as the dome nears overload, so a dome about to
 * fall looks like one. A dome that is down draws only its recovery, a rim
 * filling back around the circle, because the gap in which ordnance lands is the
 * moment the player and the ship are both waiting for. At the generator, a core
 * glow shows it is running and a ring shows a capture in progress, in the colour
 * of the side taking it.
 *
 * <p>Presentation only, in the {@code HAZARDS} layer beside the point-defence
 * bubble. A dome the player's side does not hold is drawn only where the map has
 * been seen, so an enemy generator is discovered rather than announced.
 */
public final class ShieldDomeRenderSystem implements RenderSystem {

    private static final int RING_SEGMENTS = 96;
    private static final int MARK_SEGMENTS = 20;
    /** Radius of a hit mark at its brightest, in cells. */
    private static final float MARK_CELLS = 1.6f;
    /** Radius of the generator's core glow, in cells. */
    private static final float CORE_CELLS = 1.0f;

    private static final float[] FRIENDLY = {0.55f, 0.86f, 1.00f};
    private static final float[] HOSTILE = {1.00f, 0.55f, 0.50f};
    private static final float[] COLLAPSE = {1.00f, 0.75f, 0.47f};
    private static final float[] HIT = {0.92f, 0.98f, 1.00f};

    private final PolyMesh mesh = new PolyMesh(512);
    private final ShieldDomeShader shader = new ShieldDomeShader();
    private final ShieldDomePass pass = new ShieldDomePass(shader);
    private final List<ShieldDomeDraw> drawPool = new ArrayList<>();
    private float clockSeconds;

    @Override
    public RenderLayer layer() {
        return RenderLayer.HAZARDS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        mesh.reset();
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        ShieldDomeService domes = ctx.sim.shieldDomes();
        clockSeconds = (Float.isFinite(ctx.animationTimeSeconds)
                ? Math.max(0f, ctx.animationTimeSeconds)
                : clockSeconds + Math.max(0f, ctx.realDt)) % 4096f;
        boolean shaded = shader.usable();
        pass.reset(clockSeconds);

        for (ShieldDomeService.DomeView dome : domes.domes()) {
            if (!isKnown(ctx, dome.holder(), dome.x(), dome.y())) continue;
            if (shaded) {
                pass.add(describe(ctx, dome, domes.recentHits(), camera, cellPx, pass.draws().size()));
                drawGauge(ctx, dome, camera, cellPx);
                drawGenerator(ctx, dome, camera, cellPx);
            } else {
                drawDome(ctx, dome, camera, cellPx);
            }
        }
        for (ShieldDomeService.HitView hit : domes.recentHits()) {
            if (shaded || hit.freshness() <= 0f) continue;
            if (!isKnown(ctx, hit.holder(), hit.x(), hit.y())) continue;
            float sx = camera.cellToScreenX(hit.x());
            float sy = camera.cellToScreenY(hit.y());
            float fade = hit.freshness() * ctx.alphaMult;
            float radius = MARK_CELLS * cellPx * (1.4f - 0.4f * hit.freshness());
            PolyTess.appendAnnulus(mesh, sx, sy, 0f, radius, MARK_SEGMENTS,
                    HIT[0], HIT[1], HIT[2], 0.30f * fade);
            PolyTess.appendAnnulus(mesh, sx, sy, radius, radius + 2.5f, MARK_SEGMENTS,
                    1f, 1f, 1f, 0.85f * fade);
        }
        collectTurretShields(ctx, camera, cellPx, shaded);
        if (!pass.isEmpty()) out.addCustom(RenderLayer.HAZARDS, pass);
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.HAZARDS, mesh);
    }

    /**
     * The directional flux shield of every live hardpoint that has one ({@code TurretShieldService}), by the dome
     * shader, or where it is off by the same flat drawing a dome falls back to, cut to the shield's arc. The
     * hardware's heat on the plinth is drawn with the structure ({@link TurretFluxHeat}). A company boat's shield is
     * one of them, drawn where her hull is drawn (raised by her altitude) and only while she is over the battle.
     */
    private void collectTurretShields(RenderContext ctx, BattleCamera camera, float cellPx, boolean shaded) {
        long tick = ctx.sim.getSimTickIndex();
        World world = ctx.sim.world();
        TurretShieldService shields = ctx.sim.turretShields();
        for (int i = 0, n = shields.size(); i < n; i++) {
            TurretShieldService.Fitted fitted = shields.at(i);
            long id = fitted.id;
            if (!world.isAlive(id)) continue;
            float x = world.renderX(id);
            float y = world.renderY(id);
            ShuttleMission boat = world.mission(id);
            if (boat != null) {
                if (!boat.isOverTheBattle()) continue;
                AirBody body = world.kinematics(id);
                x = body.x;
                y = body.y + AirAppearance.visualAltitudeOffsetCells(world.altitudeT(id));
            }
            if (!isKnown(ctx, ctx.sim.identity().faction(id), x, y)) continue;
            int slot = pass.draws().size();
            while (drawPool.size() <= slot) drawPool.add(new ShieldDomeDraw());
            ShieldDomeDraw draw = drawPool.get(slot);
            if (!TurretShieldField.describe(draw, id, x, y, fitted.shield, fitted.tint,
                    camera, cellPx, tick, ctx.alphaMult)) continue;
            if (shaded) pass.add(draw);
            else appendFlat(mesh, draw);
        }
    }

    /** The shader inputs for one dome: where it is, how full, what state, and the impacts crossing it. */
    private ShieldDomeDraw describe(RenderContext ctx, ShieldDomeService.DomeView dome,
                                    List<ShieldDomeService.HitView> hits, BattleCamera camera,
                                    float cellPx, int slot) {
        while (drawPool.size() <= slot) drawPool.add(new ShieldDomeDraw());
        ShieldDomeDraw draw = drawPool.get(slot);
        draw.sx = camera.cellToScreenX(dome.x());
        draw.sy = camera.cellToScreenY(dome.y());
        draw.radiusPx = dome.radius() * cellPx;
        draw.radiusCells = dome.radius();
        float[] tint = tintFor(dome.holder());
        draw.tint[0] = tint[0];
        draw.tint[1] = tint[1];
        draw.tint[2] = tint[2];
        draw.level = dome.level();
        draw.overloaded = dome.overloaded();
        draw.recovery = dome.recovery();
        draw.hitFlash = dome.hitFlash();
        draw.dropFlash = dome.dropFlash();
        draw.alpha = ctx.alphaMult;
        System.arraycopy(ShieldDomeShading.AMBER, 0, draw.collapse, 0, 3);
        draw.arcCos = -2f;
        ShieldImpactField.gather(draw, dome, hits, camera);
        return draw;
    }

    /** The flat placeholder drawing: used where the shader is off or failed to build. */
    private void drawDome(RenderContext ctx, ShieldDomeService.DomeView dome,
                          BattleCamera camera, float cellPx) {
        appendFlat(mesh, describe(ctx, dome, List.of(), camera, cellPx, 0));
        drawGauge(ctx, dome, camera, cellPx);
        drawGenerator(ctx, dome, camera, cellPx);
    }

    /**
     * The flat placeholder for one shield, read off the numbers the shader takes, so a directional shield keeps its
     * arc: a soft fill and a rim while it is up, the arc's two ends marked as the rim is, only the rim climbing back
     * while it is down (round the circle for a dome, out from the facing for an arc), and on overload the rim thrown
     * outward and gone.
     */
    static void appendFlat(PolyMesh mesh, ShieldDomeDraw d) {
        float radius = d.radiusPx;
        float alpha = d.alpha;
        float[] tint = d.tint;
        float[] strain = d.collapse;
        boolean full = !(d.arcCos > -1f);
        float facing = full ? 90f : (float) Math.toDegrees(Math.atan2(d.arcDirY, d.arcDirX));
        float sweep = full ? 360f : 2f * (float) Math.toDegrees(Math.acos(d.arcCos));

        if (d.overloaded) {
            float recovered = 1f - d.recovery;
            band(mesh, d, facing, sweep, radius - 1.5f, radius, strain[0], strain[1], strain[2], 0.10f * alpha);
            if (full) {
                PolyTess.appendArc(mesh, d.sx, d.sy, radius - 3.5f, radius, recovered, RING_SEGMENTS,
                        strain[0], strain[1], strain[2], 0.55f * alpha);
            } else {
                band(mesh, d, facing, sweep * recovered, radius - 3.5f, radius,
                        strain[0], strain[1], strain[2], 0.55f * alpha);
            }
        } else {
            float flash = d.hitFlash;
            float rimR = lerp(tint[0], HIT[0], flash);
            float rimG = lerp(tint[1], HIT[1], flash);
            float rimB = lerp(tint[2], HIT[2], flash);
            // Soft fill, a little denser as the pool fills and for a moment when struck.
            band(mesh, d, facing, sweep, 0f, radius, tint[0], tint[1], tint[2],
                    (0.045f + 0.05f * d.level + 0.06f * flash) * alpha);
            float rimA = (0.40f + 0.45f * flash) * alpha;
            band(mesh, d, facing, sweep, radius - 2.5f, radius, rimR, rimG, rimB, rimA);
            if (!full) {
                float from = radius * ShieldDomeShading.END_FROM;
                float endA = rimA * ShieldDomeShading.END_ALPHA / 0.62f;
                PolyTess.appendRadialSpoke(mesh, d.sx, d.sy, from, radius, facing - sweep * 0.5f, 2.5f,
                        rimR, rimG, rimB, endA);
                PolyTess.appendRadialSpoke(mesh, d.sx, d.sy, from, radius, facing + sweep * 0.5f, 2.5f,
                        rimR, rimG, rimB, endA);
            }
        }
        if (d.dropFlash > 0.01f) {
            float spent = 1f - d.dropFlash;
            float r = radius * (1f + 0.12f * spent);
            band(mesh, d, facing, sweep, r - 4f, r, strain[0], strain[1], strain[2], 0.8f * d.dropFlash * alpha);
        }
    }

    /** A ring band round the whole circle, or, for a directional shield, only across its arc. */
    private static void band(PolyMesh mesh, ShieldDomeDraw d, float facing, float sweep,
                             float innerR, float outerR, float r, float g, float b, float a) {
        if (sweep >= 360f) {
            PolyTess.appendAnnulus(mesh, d.sx, d.sy, innerR, outerR, RING_SEGMENTS, r, g, b, a);
        } else {
            int segments = Math.max(4, (int) Math.ceil(RING_SEGMENTS * sweep / 360f));
            PolyTess.appendSector(mesh, d.sx, d.sy, innerR, outerR, facing, sweep, segments, r, g, b, a);
        }
    }

    /** The pool, as an inner gauge: cyan when empty, amber as it nears overload. */
    private void drawGauge(RenderContext ctx, ShieldDomeService.DomeView dome,
                           BattleCamera camera, float cellPx) {
        float level = dome.level();
        if (dome.overloaded() || level <= 0.01f) return;
        float sx = camera.cellToScreenX(dome.x());
        float sy = camera.cellToScreenY(dome.y());
        float radius = dome.radius() * cellPx;
        float[] tint = tintFor(dome.holder());
        PolyTess.appendArc(mesh, sx, sy, radius - 7f, radius - 4f, level, RING_SEGMENTS,
                lerp(tint[0], COLLAPSE[0], level), lerp(tint[1], COLLAPSE[1], level),
                lerp(tint[2], COLLAPSE[2], level), 0.65f * ctx.alphaMult);
    }

    /** At the generator: a core glow while it runs, and a ring for a capture in progress. */
    private void drawGenerator(RenderContext ctx, ShieldDomeService.DomeView dome,
                               BattleCamera camera, float cellPx) {
        float sx = camera.cellToScreenX(dome.x());
        float sy = camera.cellToScreenY(dome.y());
        float alpha = ctx.alphaMult;
        float[] tint = tintFor(dome.holder());
        // The core at the generator: lit while the dome is up, dull while it is down.
        float core = CORE_CELLS * cellPx;
        float coreAlpha = dome.overloaded() ? 0.18f : 0.40f + 0.25f * dome.hitFlash();
        PolyTess.appendAnnulus(mesh, sx, sy, 0f, core, MARK_SEGMENTS,
                tint[0], tint[1], tint[2], coreAlpha * alpha);
        // A capture under way, in the colour of the side taking it.
        if (dome.captureProgress() > 0f && dome.raider() != null) {
            float[] taker = tintFor(dome.raider());
            PolyTess.appendAnnulus(mesh, sx, sy, core * 1.9f, core * 1.9f + 2f, MARK_SEGMENTS * 2,
                    taker[0], taker[1], taker[2], 0.25f * alpha);
            PolyTess.appendArc(mesh, sx, sy, core * 1.9f - 2f, core * 1.9f + 4f,
                    dome.captureProgress(), MARK_SEGMENTS * 2,
                    taker[0], taker[1], taker[2], 0.9f * alpha);
        }
    }

    /**
     * The player's own dome and its hits are always drawn; a hostile one only
     * where the map has actually been seen, unless the host draws past the fog
     * (a review frame is a neutral observer).
     */
    private static boolean isKnown(RenderContext ctx, Faction faction, float x, float y) {
        return Allegiance.of(faction).friendly() || !ctx.hostProfile.fogHidesUnits()
                || ctx.sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y));
    }

    /** The colour a dome of this holder is drawn in: pale cyan for the player's side, warm red for the enemy's. */
    static float[] tintFor(Faction holder) {
        return Allegiance.of(holder).friendly() ? FRIENDLY : HOSTILE;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }
}
