package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * Draws a planetary shield dome the way a warship's shield is drawn: the game's
 * pale cyan for a dome that protects the player's side, a warm red for one that
 * protects the enemy's, a faint fill and a bright rim at the dome's edge, a
 * collapse amber when it overloads, and a flash where a round was taken.
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

        for (ShieldDomeService.DomeView dome : domes.domes()) {
            if (!isKnown(ctx, dome.holder(), dome.x(), dome.y())) continue;
            drawDome(ctx, dome, camera, cellPx);
        }
        for (ShieldDomeService.HitView hit : domes.recentHits()) {
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
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.HAZARDS, mesh);
    }

    private void drawDome(RenderContext ctx, ShieldDomeService.DomeView dome,
                          BattleCamera camera, float cellPx) {
        float sx = camera.cellToScreenX(dome.x());
        float sy = camera.cellToScreenY(dome.y());
        float radius = dome.radius() * cellPx;
        float alpha = ctx.alphaMult;
        float[] tint = Allegiance.of(dome.holder()).friendly() ? FRIENDLY : HOSTILE;

        if (dome.overloaded()) {
            // Down: no fill, only the rim climbing back round the circle as it recovers.
            float recovered = 1f - dome.recovery();
            PolyTess.appendAnnulus(mesh, sx, sy, radius - 1.5f, radius, RING_SEGMENTS,
                    COLLAPSE[0], COLLAPSE[1], COLLAPSE[2], 0.10f * alpha);
            PolyTess.appendArc(mesh, sx, sy, radius - 3.5f, radius, recovered, RING_SEGMENTS,
                    COLLAPSE[0], COLLAPSE[1], COLLAPSE[2], 0.55f * alpha);
        } else {
            float level = dome.level();
            float flash = dome.hitFlash();
            // Soft fill, a little denser as the pool fills and for a moment when struck.
            PolyTess.appendAnnulus(mesh, sx, sy, 0f, radius, RING_SEGMENTS,
                    tint[0], tint[1], tint[2], (0.045f + 0.05f * level + 0.06f * flash) * alpha);
            PolyTess.appendAnnulus(mesh, sx, sy, radius - 2.5f, radius, RING_SEGMENTS,
                    lerp(tint[0], HIT[0], flash), lerp(tint[1], HIT[1], flash),
                    lerp(tint[2], HIT[2], flash), (0.40f + 0.45f * flash) * alpha);
            // The pool, as an inner gauge: cyan when empty, amber as it nears overload.
            if (level > 0.01f) {
                PolyTess.appendArc(mesh, sx, sy, radius - 7f, radius - 4f, level, RING_SEGMENTS,
                        lerp(tint[0], COLLAPSE[0], level), lerp(tint[1], COLLAPSE[1], level),
                        lerp(tint[2], COLLAPSE[2], level), 0.65f * alpha);
            }
        }
        if (dome.dropFlash() > 0.01f) {
            // The collapse: the rim thrown outward and gone.
            float spent = 1f - dome.dropFlash();
            float r = radius * (1f + 0.12f * spent);
            PolyTess.appendAnnulus(mesh, sx, sy, r - 4f, r, RING_SEGMENTS,
                    COLLAPSE[0], COLLAPSE[1], COLLAPSE[2], 0.8f * dome.dropFlash() * alpha);
        }
        // The core at the generator: lit while the dome is up, dull while it is down.
        float core = CORE_CELLS * cellPx;
        float coreAlpha = dome.overloaded() ? 0.18f : 0.40f + 0.25f * dome.hitFlash();
        PolyTess.appendAnnulus(mesh, sx, sy, 0f, core, MARK_SEGMENTS,
                tint[0], tint[1], tint[2], coreAlpha * alpha);
        // A capture under way, in the colour of the side taking it.
        if (dome.captureProgress() > 0f && dome.raider() != null) {
            float[] taker = Allegiance.of(dome.raider()).friendly() ? FRIENDLY : HOSTILE;
            PolyTess.appendAnnulus(mesh, sx, sy, core * 1.9f, core * 1.9f + 2f, MARK_SEGMENTS * 2,
                    taker[0], taker[1], taker[2], 0.25f * alpha);
            PolyTess.appendArc(mesh, sx, sy, core * 1.9f - 2f, core * 1.9f + 4f,
                    dome.captureProgress(), MARK_SEGMENTS * 2,
                    taker[0], taker[1], taker[2], 0.9f * alpha);
        }
    }

    /**
     * The player's own dome and its hits are always drawn; a hostile one only
     * where the map has actually been seen.
     */
    private static boolean isKnown(RenderContext ctx, Faction faction, float x, float y) {
        return Allegiance.of(faction).friendly()
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
