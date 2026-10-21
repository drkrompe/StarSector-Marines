package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.BruteChargeSystem;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

/**
 * The Brute's charge tell on the ground ({@code colony-breach-nouns.md}, Brute): for the tell, a faint
 * amber wedge along the line the charge will run, as far as open ground lets it.
 * The player sees where it is going before it goes. It strengthens over the tell and is gone once
 * the run starts, when the running Brute is the warning.
 *
 * <p>Drawn in {@link RenderLayer#HAZARDS} with the other threat warnings (a frag's landing ring),
 * faint enough that the bodies it crosses still read. A Brute the fog hides draws no wedge.
 * Observation only.
 */
public final class ChargeTellRenderSystem implements RenderSystem {

    /** Half-width of the wedge at the Brute and at its far end, in cells. */
    private static final float NEAR_HALF = 0.4f;
    private static final float FAR_HALF = 0.85f;
    private static final int BANDS = 6;

    private final PolyMesh wedge = new PolyMesh(64);

    @Override public RenderLayer layer() { return RenderLayer.HAZARDS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim == null || ctx.camera == null) return;
        wedge.reset();
        BattleSimulation sim = ctx.sim;
        BattleCamera camera = ctx.camera;
        BruteChargeSystem charges = sim.bruteCharges();
        int tellTicks = Math.max(1, charges.tellTicks());
        for (long brute : charges.tracked()) {
            if (charges.phase(brute) != BruteChargeSystem.Phase.TELL) continue;
            if (!sim.getRoster().isAliveById(brute)) continue;
            float alpha = ctx.alphaMult * fogAlpha(sim, brute);
            if (alpha <= 0f) continue;
            float grown = 1f - Math.min(1f, charges.phaseTicks(brute) / (float) tellTicks);
            alpha *= 0.18f + 0.22f * grown;
            float x = sim.world().renderX(brute), y = sim.world().renderY(brute);
            double heading = Math.toRadians(charges.headingDegrees(brute));
            float hx = (float) Math.cos(heading), hy = (float) Math.sin(heading);
            float reach = charges.reach(brute);
            // Bands that thin toward the far end: the wedge reads as a direction, not a slab.
            for (int i = 0; i < BANDS; i++) {
                float t0 = i / (float) BANDS, t1 = (i + 1) / (float) BANDS;
                float w0 = NEAR_HALF + (FAR_HALF - NEAR_HALF) * t0;
                float w1 = NEAR_HALF + (FAR_HALF - NEAR_HALF) * t1;
                quad(camera, x, y, hx, hy, reach * t0, reach * t1, w0, w1, alpha * (1f - 0.7f * t0));
            }
        }
        if (!wedge.isEmpty()) out.addPoly(RenderLayer.HAZARDS, wedge);
    }

    private void quad(BattleCamera camera, float x, float y, float hx, float hy,
                      float d0, float d1, float w0, float w1, float a) {
        float px = -hy, py = hx;
        wedge.appendQuad(
                camera.cellToScreenX(x + hx * d0 + px * w0), camera.cellToScreenY(y + hy * d0 + py * w0),
                camera.cellToScreenX(x + hx * d1 + px * w1), camera.cellToScreenY(y + hy * d1 + py * w1),
                camera.cellToScreenX(x + hx * d1 - px * w1), camera.cellToScreenY(y + hy * d1 - py * w1),
                camera.cellToScreenX(x + hx * d0 - px * w0), camera.cellToScreenY(y + hy * d0 - py * w0),
                1f, 0.72f, 0.2f, a);
    }

    /** How visible a unit is to the player: 0 hidden, its fade while fading, else 1. */
    static float fogAlpha(BattleSimulation sim, long unit) {
        int slot = sim.getRoster().indexOf(unit);
        if (slot < 0) return 0f;
        byte visibility = sim.getFogOfWar().getUnitVisibility(slot);
        if (visibility == FogOfWarService.VIS_HIDDEN) return 0f;
        return visibility == FogOfWarService.VIS_FADING ? sim.getFogOfWar().getFadeAlpha(slot) : 1f;
    }
}
