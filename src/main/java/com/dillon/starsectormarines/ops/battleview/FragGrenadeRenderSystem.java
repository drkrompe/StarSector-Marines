package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/** Readable landing-footprint warning for friendly-known and visibly observed frag throws. */
public final class FragGrenadeRenderSystem implements RenderSystem {

    private static final int RING_SEGMENTS = 48;
    private final PolyMesh rings = new PolyMesh(192);

    @Override
    public RenderLayer layer() {
        return RenderLayer.HAZARDS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        rings.reset();
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        for (Projectile projectile : ctx.sim.getActiveProjectiles()) {
            if (!"weapon.frag-grenade".equals(projectile.sourceWeaponId)
                    || projectile.onArrival == null) continue;
            boolean friendly = Allegiance.of(projectile.shooterFaction).friendly();
            if (!friendly) {
                int px = (int) Math.floor(projectile.currentX());
                float groundProgress = projectile.progress();
                if (projectile.hasBoostRamp) {
                    groundProgress = Projectile.applyBoostCurve(groundProgress);
                }
                int py = (int) Math.floor(projectile.fromY
                        + (projectile.toY - projectile.fromY) * groundProgress);
                if (!ctx.sim.getGrid().inBounds(px, py)
                        || !ctx.sim.getFogOfWar().isCellRevealed(px, py)) continue;
            }
            float sx = camera.cellToScreenX(projectile.toX);
            float sy = camera.cellToScreenY(projectile.toY);
            float radius = projectile.onArrival.aoeRadius * cellPx;
            float remaining = projectile.totalFlightTime > 0f
                    ? Math.max(0f, Math.min(1f,
                    projectile.remainingTime / projectile.totalFlightTime)) : 0f;
            float pulse = 0.7f + 0.3f * (float) Math.sin(
                    projectile.remainingTime * (14f + 18f * (1f - remaining)));
            float red = 1f;
            float green = friendly ? 0.68f : 0.18f;
            float blue = friendly ? 0.12f : 0.08f;
            PolyTess.appendAnnulus(rings, sx, sy, radius - 2.5f, radius,
                    RING_SEGMENTS, red, green, blue,
                    (0.34f + 0.22f * pulse) * ctx.alphaMult);
            PolyTess.appendArc(rings, sx, sy, radius - 6f, radius - 3.5f,
                    remaining, RING_SEGMENTS, red, green, blue,
                    0.82f * ctx.alphaMult);
        }
        if (!rings.isEmpty()) out.addPoly(RenderLayer.HAZARDS, rings);
    }
}
