package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.deployable.PointDefenseService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * Draws what a placed point-defence emplacement is doing: the bubble it
 * covers, how much of its magazine is left, and — the part that matters — one
 * readable mark per engagement, drawn differently for a warhead it stopped and
 * one it fired at and lost.
 *
 * <p>Without those marks the mechanic is invisible in the worst possible way.
 * A stopped round is deleted mid-air, which on screen is indistinguishable from
 * a missile that was never fired; and a saturating volley reads as some
 * explosions arriving and some not, with nothing to say the defence was
 * involved at all. Both failure modes look exactly like a bug. The interesting
 * frame in this whole feature is a five-round burst where three die and two get
 * through, and that frame is only interesting if a viewer can tell those two
 * apart.
 *
 * <p>Sibling to {@code SatchelRenderSystem} in the {@code HAZARDS} layer, which
 * draws the other simulation-owned field object a marine can leave behind.
 */
public final class PointDefenseRenderSystem implements RenderSystem {

    private static final int RING_SEGMENTS = 64;
    private static final int MARK_SEGMENTS = 20;
    /** Radius of an engagement mark, in cells. Sized to read at review-GIF scale without swamping the round it replaces. */
    private static final float MARK_CELLS = 0.75f;

    private final PolyMesh mesh = new PolyMesh(256);

    @Override
    public RenderLayer layer() {
        return RenderLayer.HAZARDS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        mesh.reset();
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        PointDefenseService pointDefense = ctx.sim.pointDefense();

        for (PointDefenseService.EmplacementView pod : pointDefense.activeEmplacements()) {
            if (!isKnown(ctx, pod.faction(), pod.x(), pod.y())) continue;
            float screenX = camera.cellToScreenX(pod.x());
            float screenY = camera.cellToScreenY(pod.y());
            float radius = pod.interceptRadius() * cellPx;
            float life = pod.totalLifetime() > 0f
                    ? clamp01(pod.remainingLifetime() / pod.totalLifetime()) : 0f;
            // The bubble itself: faint, so it reads as an area of cover rather
            // than an objective marker.
            PolyTess.appendAnnulus(mesh, screenX, screenY, radius - 1.5f, radius,
                    RING_SEGMENTS, 0.55f, 0.86f, 1f, 0.18f * ctx.alphaMult);
            // A short arc of the same ring drains with the pod's clock, so
            // "this thing is about to stop working" is legible from the field
            // rather than only from an after-action.
            PolyTess.appendArc(mesh, screenX, screenY, radius - 5f, radius - 2.5f,
                    life, RING_SEGMENTS, 0.62f, 0.92f, 1f, 0.5f * ctx.alphaMult);
        }

        for (PointDefenseService.EngagementView shot : pointDefense.recentEngagements()) {
            if (!isKnown(ctx, shot.faction(), shot.x(), shot.y())) continue;
            float screenX = camera.cellToScreenX(shot.x());
            float screenY = camera.cellToScreenY(shot.y());
            float fade = shot.freshness() * ctx.alphaMult;
            if (shot.stopped()) {
                // A kill: a filled bright burst that expands slightly as it
                // fades, reading as the warhead coming apart in the air.
                float radius = MARK_CELLS * cellPx * (1.35f - 0.35f * shot.freshness());
                PolyTess.appendAnnulus(mesh, screenX, screenY, 0f, radius,
                        MARK_SEGMENTS, 0.78f, 0.96f, 1f, 0.55f * fade);
                PolyTess.appendAnnulus(mesh, screenX, screenY, radius, radius + 2.5f,
                        MARK_SEGMENTS, 1f, 1f, 1f, 0.85f * fade);
            } else {
                // A miss: the same burst position, hollow and amber, so the
                // viewer sees the mount tried and the round kept going.
                float radius = MARK_CELLS * cellPx;
                PolyTess.appendAnnulus(mesh, screenX, screenY, radius - 2f, radius,
                        MARK_SEGMENTS, 1f, 0.72f, 0.28f, 0.7f * fade);
            }
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.HAZARDS, mesh);
    }

    /**
     * The player's own emplacements and engagements are always drawn; a hostile
     * one is drawn only where the map has actually been seen, so an enemy pod
     * is discovered rather than announced.
     */
    private static boolean isKnown(RenderContext ctx, Faction faction, float x, float y) {
        return faction == Faction.MARINE
                || ctx.sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
