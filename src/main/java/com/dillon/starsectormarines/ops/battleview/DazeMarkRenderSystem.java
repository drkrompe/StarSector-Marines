package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.StaggerService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * The daze mark ({@code colony-breach-nouns.md}, Brute): a small amber ring with three motes circling it,
 * over anything that {@linkplain StaggerService can't act}: a thrown Marine, a stalled hull, a
 * staggered mech, a stunned Brute. The same mark everywhere means the same thing. A body knocked
 * down also kicks up a ring of dust where it lands.
 *
 * <p>Drawn in {@link RenderLayer#UNITS} after the bodies, as the health bars are. The motes turn with
 * the battle's tick, so a review frame is a function of the simulation. Observation only.
 */
public final class DazeMarkRenderSystem implements RenderSystem {

    private static final int SEGMENTS = 24;
    /** The dust ring spreads for this many ticks after a body lands. */
    private static final int DUST_TICKS = 12;

    private final PolyMesh marks = new PolyMesh(128);

    @Override public RenderLayer layer() { return RenderLayer.UNITS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim == null || ctx.camera == null) return;
        marks.reset();
        BattleSimulation sim = ctx.sim;
        BattleCamera camera = ctx.camera;
        StaggerService stagger = sim.stagger();
        ViewCull view = ViewCull.of(camera);
        float cellPx = camera.cellPxSize();
        int tick = sim.getSimTickIndex();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!stagger.isStaggered(unit)) continue;
            float x = sim.world().renderX(unit), y = sim.world().renderY(unit);
            float body = Math.max(0.35f, sim.getRoster().radius(unit));
            if (!view.visible(x, y, body * 3f)) continue;
            float alpha = ctx.alphaMult * ChargeTellRenderSystem.fogAlpha(sim, unit);
            if (alpha <= 0f) continue;

            int since = stagger.ticksSince(unit);
            if (stagger.cause(unit) == StaggerService.Cause.KNOCKED_DOWN && since < DUST_TICKS) {
                float k = since / (float) DUST_TICKS;
                float r = (0.35f + 0.6f * k) * cellPx;
                PolyTess.appendAnnulus(marks, camera.cellToScreenX(x), camera.cellToScreenY(y),
                        r * 0.82f, r, SEGMENTS, 0.66f, 0.58f, 0.46f, alpha * 0.55f * (1f - k));
            }

            // Above the body: cell y runs up the screen.
            float cx = camera.cellToScreenX(x);
            float cy = camera.cellToScreenY(y + body + 0.25f);
            float ring = Math.max(5f, 0.22f * cellPx);
            PolyTess.appendAnnulus(marks, cx, cy, ring - Math.max(1f, ring * 0.16f), ring, SEGMENTS,
                    1f, 0.72f, 0.2f, alpha * 0.85f);
            float mote = Math.max(1.5f, ring * 0.22f);
            for (int m = 0; m < 3; m++) {
                double a = tick * 0.21 + m * Math.PI * 2 / 3;
                PolyTess.appendAnnulus(marks, cx + ring * (float) Math.cos(a),
                        cy + ring * 0.45f * (float) Math.sin(a), 0f, mote, 10, 1f, 0.9f, 0.55f, alpha);
            }
        }
        if (!marks.isEmpty()) out.addPoly(RenderLayer.UNITS, marks);
    }
}
