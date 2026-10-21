package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.StaggerService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * The daze mark ({@code colony-breach-nouns.md}, Brute): a quiet amber arc on the ground under anything that
 * {@linkplain StaggerService can't act} (a thrown Marine, a stalled hull), emptying as
 * the hold runs out, so the player reads how long it lasts as well as that it is there. A Brute shows its
 * own stun in its body ({@code RavBruteRig}) and a staggered mech only holds its
 * hips still; neither gets a mark. A body knocked down also kicks up a ring of
 * dust where it lands.
 *
 * <p>Drawn in {@link RenderLayer#UNITS} after the bodies, as the health bars are. A function of the
 * simulation's tick. Observation only.
 */
public final class DazeMarkRenderSystem implements RenderSystem {

    private static final int SEGMENTS = 32;
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
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!stagger.isStaggered(unit)) continue;
            float x = sim.world().renderX(unit), y = sim.world().renderY(unit);
            float body = Math.max(0.35f, sim.getRoster().radius(unit));
            if (!view.visible(x, y, body * 3f)) continue;
            float alpha = ctx.alphaMult * ChargeTellRenderSystem.fogAlpha(sim, unit);
            if (alpha <= 0f) continue;
            float sx = camera.cellToScreenX(x), sy = camera.cellToScreenY(y);

            int since = stagger.ticksSince(unit);
            if (stagger.cause(unit) == StaggerService.Cause.KNOCKED_DOWN && since < DUST_TICKS) {
                float k = since / (float) DUST_TICKS;
                float r = (0.35f + 0.6f * k) * cellPx;
                PolyTess.appendAnnulus(marks, sx, sy, r * 0.82f, r, 24, 0.66f, 0.58f, 0.46f,
                        alpha * 0.55f * (1f - k));
            }
            UnitType type = sim.identity().type(unit);
            if (type.isSwarm() || type.isMech()) continue;

            int left = stagger.ticksLeft(unit);
            float progress = left / (float) Math.max(1, left + since);
            float outer = (body + 0.18f) * cellPx;
            float width = Math.max(1.5f, 0.06f * cellPx);
            PolyTess.appendAnnulus(marks, sx, sy, outer - width, outer, SEGMENTS, 1f, 0.72f, 0.2f, alpha * 0.18f);
            PolyTess.appendArc(marks, sx, sy, outer - width, outer, progress, SEGMENTS,
                    1f, 0.72f, 0.2f, alpha * 0.6f);
        }
        if (!marks.isEmpty()) out.addPoly(RenderLayer.UNITS, marks);
    }
}
