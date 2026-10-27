package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.ui.retained.CanvasMesh;
import com.dillon.starsectormarines.ui.retained.CanvasMeshBatch;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;

/**
 * The winged foot over a squad the player has told to run, and beneath it the squad's stamina
 * ({@code SquadRunSystem}).
 *
 * <p>The badge stands at the upper right of the squad's lead body, opposite the medic's cross, and
 * is the squad's rather than the leader's: one per running squad. The bar under it is the mean of
 * its Marines' stamina, so a squad running itself out shows it before its men drop to a walk; it
 * greys while most of them are winded. Drawn in {@link RenderLayer#UNITS} after the bodies, like
 * the medic's cross. Observation only.
 */
public final class SquadRunMarkerRenderSystem implements RenderSystem {
    static final String ICON = "graphics/ui/orders/run-winged-foot.svg";
    private static final float BADGE_CELLS = 0.8f;
    private static final float MIN_BADGE_PX = 18f;
    /** A badge, not a sign: it stops growing as the camera closes in. */
    private static final float MAX_BADGE_PX = 30f;
    private static final float[] STAMINA = {0.62f, 0.86f, 1f};
    private static final float[] WINDED = {0.58f, 0.62f, 0.59f};
    private static final float[] OUTLINE = {0.05f, 0.08f, 0.06f};
    private static final float[] EMPTY = {0.13f, 0.18f, 0.22f};

    private final PolyMesh mesh = new PolyMesh(64);
    private SvgAsset icon;

    @Override public RenderLayer layer() { return RenderLayer.UNITS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim == null || ctx.camera == null) return;
        BattleSimulation sim = ctx.sim;
        BattleCamera camera = ctx.camera;
        ViewCull view = ViewCull.of(camera);
        mesh.reset();
        float cellPx = camera.cellPxSize();
        float size = Math.max(MIN_BADGE_PX, Math.min(MAX_BADGE_PX, BADGE_CELLS * cellPx));
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || !sim.getSquadMoveOrderService().isRunning(squad.id)) continue;
            long lead = lead(sim, squad);
            if (lead == 0L) continue;
            float x = sim.world().renderX(lead);
            float y = sim.world().renderY(lead);
            float radius = Math.max(0.42f, sim.getRoster().radius(lead));
            if (!view.visible(x, y, radius * 3f)) continue;
            boolean selected = ctx.selection != null && ctx.selection.isSquadSelected(squad.id);
            float alpha = ctx.alphaMult * (selected ? MedicMarkerRenderSystem.SELECTED_ALPHA
                    : MedicMarkerRenderSystem.UNSELECTED_ALPHA);
            Stamina stamina = stamina(sim, squad);
            float cx = camera.cellToScreenX(x) + radius * cellPx + size * 0.5f;
            float cy = camera.cellToScreenY(y) + radius * cellPx + size * 0.4f;
            badge(cx, cy, size, stamina, alpha);
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.UNITS, mesh);
    }

    /** The squad's acting leader, or its first live member. */
    private static long lead(BattleSimulation sim, Squad squad) {
        long leader = sim.resolveUnit(squad.leaderId);
        if (leader != 0L) return leader;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
            if (member != 0L) return member;
        }
        return 0L;
    }

    /** The mean stamina of the squad's living Marines, and whether most of them are winded. */
    record Stamina(float fraction, boolean winded) { }

    static Stamina stamina(BattleSimulation sim, Squad squad) {
        MarineSprint sprint = sim.directControl().marineStamina();
        float sum = 0f;
        int count = 0;
        int winded = 0;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
            if (member == 0L) continue;
            MarineSprint.Status status = sprint.status(member);
            sum += status.staminaFraction();
            count++;
            if (status.exhausted()) winded++;
        }
        return count == 0 ? new Stamina(1f, false) : new Stamina(sum / count, winded * 2 > count);
    }

    private void badge(float cx, float cy, float size, Stamina stamina, float alpha) {
        SvgAsset art = icon();
        float[] box = art.viewBox();
        float w = size;
        float h = size * box[3] / box[2];
        float x0 = cx - w * 0.5f;
        float top = cy + h * 0.5f;
        float scale = w / box[2];
        float tint = stamina.winded() ? 0.6f : 1f;
        CanvasMeshBatch batch = art.geometry();
        for (int i = 0; i < batch.layerCount(); i++) {
            CanvasMeshBatch.Layer layer = batch.layer(i);
            Color c = layer.color();
            float r = c.getRed() / 255f * tint, g = c.getGreen() / 255f * tint, b = c.getBlue() / 255f * tint;
            float a = c.getAlpha() / 255f * alpha;
            CanvasMesh tris = layer.mesh();
            for (int v = 0; v + 2 < tris.vertexCount(); v += 3) {
                // SVG y runs down the box; screen y runs up.
                float ax = x0 + (tris.x(v) - box[0]) * scale, ay = top - (tris.y(v) - box[1]) * scale;
                float bx = x0 + (tris.x(v + 1) - box[0]) * scale, by = top - (tris.y(v + 1) - box[1]) * scale;
                float px = x0 + (tris.x(v + 2) - box[0]) * scale, py = top - (tris.y(v + 2) - box[1]) * scale;
                mesh.appendQuad(ax, ay, bx, by, px, py, px, py, r, g, b, a);
            }
        }
        float barW = w * 0.8f;
        float barH = Math.max(2f, size * 0.12f);
        float bx0 = cx - barW * 0.5f;
        float by1 = cy - h * 0.5f - 1f;
        float by0 = by1 - barH;
        rect(bx0 - 1f, by0 - 1f, bx0 + barW + 1f, by1 + 1f, OUTLINE, alpha);
        rect(bx0, by0, bx0 + barW, by1, EMPTY, alpha);
        float fill = Math.max(0f, Math.min(1f, stamina.fraction()));
        if (fill > 0f) rect(bx0, by0, bx0 + barW * fill, by1, stamina.winded() ? WINDED : STAMINA, alpha);
    }

    private void rect(float x0, float y0, float x1, float y1, float[] c, float alpha) {
        mesh.appendQuad(x0, y0, x1, y0, x1, y1, x0, y1, c[0], c[1], c[2], alpha);
    }

    private SvgAsset icon() {
        if (icon == null) icon = SvgAsset.load(ICON);
        return icon;
    }
}
