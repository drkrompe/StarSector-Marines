package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.LanceSweep;
import com.dillon.starsectormarines.battle.warship.LanceSweepSystem;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * Draws a warship's lance: the line laid before it is lit, then the beam, its
 * hot tip, and the scar the tip has drawn across the ground.
 *
 * <p>The telegraph is the part that makes the weapon fair. It marks the whole
 * arc the beam will walk — both edges and the far rim — and pulses faster as
 * the burn approaches, so a squad has a stretch of time in which to leave the
 * ground and a reason to. Lit, the beam is three stacked strips (a wide dim
 * glow, a hot core, a white spine) rather than one flat colour, because a flat
 * strip that long reads as a UI marker and not as light.
 *
 * <p>Drawn in {@code HAZARDS}, a simulation-owned field effect above the bodies
 * it kills, so an intact roof still paints over it: the player is not shown
 * the inside of a building they cannot see, and the beam removes the roof the
 * moment it arrives.
 *
 * <p>Reads the simulation's sweeps and nothing else; the damage the beam does
 * is the simulation's, and no rendering decision is readable from it.
 */
public final class LanceSweepRenderSystem implements RenderSystem {

    private static final int SCAR_SEGMENTS = 32;
    private static final int RIM_SEGMENTS = 24;

    private final PolyMesh mesh = new PolyMesh(256);

    @Override
    public RenderLayer layer() {
        return RenderLayer.HAZARDS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        mesh.reset();
        BattleCamera camera = ctx.camera;
        for (LanceSweepSystem.Active a : ctx.sim.lanceSweeps().active()) {
            if (a.isBurning()) {
                drawBeam(camera, a, ctx.alphaMult);
            } else {
                drawTelegraph(camera, a, ctx.alphaMult);
            }
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.HAZARDS, mesh);
    }

    private void drawTelegraph(BattleCamera camera, LanceSweepSystem.Active a, float alphaMult) {
        LanceSweep s = a.sweep;
        float cellPx = camera.cellPxSize();
        float remaining = Math.max(0f, s.telegraphSeconds() - a.elapsedSeconds());
        // Urgency: the pulse quickens as the beam comes on.
        float rate = 3f + 9f * (1f - Math.min(1f, remaining / Math.max(0.001f, s.telegraphSeconds())));
        float pulse = 0.55f + 0.45f * (float) Math.sin(a.elapsedSeconds() * rate * Math.PI);
        float alpha = (0.35f + 0.4f * pulse) * alphaMult;
        float widthPx = Math.max(1.5f, 0.18f * cellPx);
        // Both edges of the arc, and the rim between them.
        edge(camera, s, s.startAngle(), widthPx, alpha);
        edge(camera, s, s.endAngle(), widthPx, alpha);
        float px = 0f, py = 0f;
        for (int i = 0; i <= RIM_SEGMENTS; i++) {
            float angle = s.startAngle() + (s.endAngle() - s.startAngle()) * i / RIM_SEGMENTS;
            float x = camera.cellToScreenX(s.originX() + (float) Math.cos(angle) * s.reach());
            float y = camera.cellToScreenY(s.originY() + (float) Math.sin(angle) * s.reach());
            if (i > 0) line(px, py, x, y, widthPx, 1f, 0.35f, 0.25f, alpha * 0.8f);
            px = x;
            py = y;
        }
        // The line the beam will start on, brightest.
        edge(camera, s, s.startAngle(), widthPx * 2f, alpha * 0.9f);
    }

    private void edge(BattleCamera camera, LanceSweep s, float angle, float widthPx, float alpha) {
        float x0 = camera.cellToScreenX(s.originX());
        float y0 = camera.cellToScreenY(s.originY());
        float x1 = camera.cellToScreenX(s.originX() + (float) Math.cos(angle) * s.reach());
        float y1 = camera.cellToScreenY(s.originY() + (float) Math.sin(angle) * s.reach());
        line(x0, y0, x1, y1, widthPx, 1f, 0.35f, 0.25f, alpha);
    }

    private void drawBeam(BattleCamera camera, LanceSweepSystem.Active a, float alphaMult) {
        LanceSweep s = a.sweep;
        float cellPx = camera.cellPxSize();
        float angle = a.angle();
        float flicker = 0.92f + 0.08f * (float) Math.sin(a.elapsedSeconds() * 90.0);
        float fade = fadeEnvelope(s, a.elapsedSeconds()) * alphaMult;
        float ox = camera.cellToScreenX(s.originX());
        float oy = camera.cellToScreenY(s.originY());
        float tx = camera.cellToScreenX(s.originX() + (float) Math.cos(angle) * s.reach());
        float ty = camera.cellToScreenY(s.originY() + (float) Math.sin(angle) * s.reach());
        float w = s.halfWidth() * 2f * cellPx;

        // The scar: where the tip has been, a continuous strip along the rim
        // that cools from the tip backwards to where the sweep began.
        float swept = angle - s.startAngle();
        float px = camera.cellToScreenX(s.originX() + (float) Math.cos(s.startAngle()) * s.reach());
        float py = camera.cellToScreenY(s.originY() + (float) Math.sin(s.startAngle()) * s.reach());
        for (int i = 1; i <= SCAR_SEGMENTS; i++) {
            float t = (float) i / SCAR_SEGMENTS;
            float da = s.startAngle() + swept * t;
            float dx = camera.cellToScreenX(s.originX() + (float) Math.cos(da) * s.reach());
            float dy = camera.cellToScreenY(s.originY() + (float) Math.sin(da) * s.reach());
            float heat = t * t;
            line(px, py, dx, dy, w * 0.6f, 1f, 0.3f + 0.25f * heat, 0.1f, (0.1f + 0.35f * heat) * fade);
            px = dx;
            py = dy;
        }

        line(ox, oy, tx, ty, w * 1.9f, 1f, 0.35f, 0.12f, 0.20f * flicker * fade);
        line(ox, oy, tx, ty, w * 1.0f, 1f, 0.62f, 0.25f, 0.55f * flicker * fade);
        line(ox, oy, tx, ty, w * 0.38f, 1f, 0.97f, 0.88f, 0.95f * flicker * fade);

        // The tip, where the ground is actually being cut.
        PolyTess.appendAnnulus(mesh, tx, ty, 0f, w * 1.1f, 20, 1f, 0.55f, 0.2f, 0.45f * fade);
        PolyTess.appendAnnulus(mesh, tx, ty, 0f, w * 0.55f, 20, 1f, 0.95f, 0.8f, 0.9f * fade);
    }

    /** Full brightness through the burn, with a short ramp in and out. */
    private static float fadeEnvelope(LanceSweep s, float elapsed) {
        float into = elapsed - s.telegraphSeconds();
        float left = s.totalSeconds() - elapsed;
        float ramp = 0.25f;
        return Math.max(0f, Math.min(1f, Math.min(into / ramp, left / ramp)));
    }

    /** A strip of {@code widthPx} between two screen points. */
    private void line(float x0, float y0, float x1, float y1, float widthPx,
                      float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * widthPx * 0.5f;
        float ny = dx / len * widthPx * 0.5f;
        mesh.appendQuad(x0 + nx, y0 + ny, x1 + nx, y1 + ny,
                x1 - nx, y1 - ny, x0 - nx, y0 - ny, r, g, b, a);
    }
}
