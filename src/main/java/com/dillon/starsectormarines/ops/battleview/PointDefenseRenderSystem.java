package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.deployable.PointDefenseService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * Draws what point defence is doing: one readable mark per engagement, drawn
 * differently for a warhead it stopped and one it fired at and lost.
 *
 * <p>The ground a mount covers is deliberately not drawn. A ring at the
 * intercept radius around every pod and every marine wearing a pack was a
 * standing circle on the field that said nothing a player acts on; what the
 * mechanic needs to be legible is the moment it acts.
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
        return Allegiance.of(faction).friendly()
                || ctx.sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y));
    }
}
