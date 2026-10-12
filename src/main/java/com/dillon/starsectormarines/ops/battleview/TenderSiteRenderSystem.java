package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.tender.ConstructionOrder;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * Draws a tender's build sites while they are under construction: a marker on the
 * cell, an arc that fills as the work is done, and a line of beads from the tender
 * to the site so it is clear which ship is building what.
 *
 * <p>The marker turns red while hostile units stand the work down. A site that is
 * finished stops being drawn: the structure it became is an ordinary turret and
 * draws itself. The drones are ordinary drone units and need nothing here.
 *
 * <p>Sibling to {@code PointDefenseRenderSystem} in the {@code HAZARDS} layer.
 */
public final class TenderSiteRenderSystem implements RenderSystem {

    private static final int RING_SEGMENTS = 4;
    private static final int ARC_SEGMENTS = 48;
    private static final int BEADS = 9;
    private static final float MARKER_CELLS = 0.9f;
    private static final float ARC_CELLS = 1.4f;

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
        for (Tender tender : ctx.sim.tenders().tenders()) {
            for (ConstructionOrder order : tender.orders()) {
                if (order.phase().terminal()) continue;
                if (!isKnown(ctx, tender.faction(), order.siteX(), order.siteY())) continue;
                boolean working = order.phase() == ConstructionOrder.Phase.WORKING;
                float r = 1f;
                float g = order.contested() ? 0.35f : 0.75f;
                float b = order.contested() ? 0.3f : 0.25f;
                float sx = camera.cellToScreenX(order.siteX());
                float sy = camera.cellToScreenY(order.siteY());
                float marker = MARKER_CELLS * cellPx;
                PolyTess.appendAnnulus(mesh, sx, sy, marker - 2f, marker, RING_SEGMENTS,
                        r, g, b, (working ? 0.85f : 0.45f) * ctx.alphaMult);
                if (working) {
                    float arc = ARC_CELLS * cellPx;
                    PolyTess.appendArc(mesh, sx, sy, arc - 3f, arc, order.fraction(), ARC_SEGMENTS,
                            r, g, b, 0.9f * ctx.alphaMult);
                }
                float tx = camera.cellToScreenX(tender.bayX());
                float ty = camera.cellToScreenY(tender.bayY());
                for (int i = 1; i < BEADS; i++) {
                    float t = (float) i / BEADS;
                    PolyTess.appendAnnulus(mesh, tx + (sx - tx) * t, ty + (sy - ty) * t, 0f,
                            Math.max(1.5f, 0.07f * cellPx), 8, r, g, b,
                            (working ? 0.55f : 0.25f) * ctx.alphaMult);
                }
            }
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.HAZARDS, mesh);
    }

    /** The player's own sites are always drawn; a hostile tender's only where the map has been seen. */
    private static boolean isKnown(RenderContext ctx, Faction faction, float x, float y) {
        return Allegiance.of(faction).friendly()
                || ctx.sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y));
    }
}
