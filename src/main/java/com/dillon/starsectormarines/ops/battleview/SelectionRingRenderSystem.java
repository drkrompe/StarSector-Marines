package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;

/** Ground rings follow rendered bodies rather than their navigation cells. */
public final class SelectionRingRenderSystem implements RenderSystem {
    @Override public RenderLayer layer() { return RenderLayer.HIGHLIGHTS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        Selection selection = ctx.selection;
        if (selection == null || ctx.sim == null || ctx.camera == null
                || ctx.sim.directControl().active()) return;
        var sim = ctx.sim;
        var camera = ctx.camera;
        ViewCull view = ViewCull.of(camera);
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.squad().hasSquad(unit)) continue;
            int squad = sim.squad().squadId(unit);
            boolean selected = selection.isSquadSelected(squad);
            boolean hovered = squad == selection.getHoveredSquadId();
            if (!selected && !hovered) continue;
            int slot = sim.getRoster().indexOf(unit);
            byte visibility = sim.getFogOfWar().getUnitVisibility(slot);
            if (visibility == FogOfWarService.VIS_HIDDEN) continue;
            float x = sim.world().renderX(unit), y = sim.world().renderY(unit);
            float radius = Math.max(0.46f, sim.getRoster().radius(unit) + 0.12f);
            if (!view.visible(x, y, radius * 2f)) continue;
            float alpha = ctx.alphaMult * (selected ? 0.9f : 0.55f);
            if (visibility == FogOfWarService.VIS_FADING) alpha *= sim.getFogOfWar().getFadeAlpha(slot);
            boolean primary = selected && unit == selection.getSelectedUnitEntityId();
            ring(out, camera.cellToScreenX(x), camera.cellToScreenY(y),
                    radius * camera.cellPxSize(), primary, selected, alpha);
        }
    }

    static void ring(DrawList out, float x, float y, float radius,
                     boolean primary, boolean selected, float alpha) {
        float r = primary ? 1f : selected ? 0.5f : 0.7f;
        float g = primary ? 0.83f : 1f;
        float b = primary ? 0.39f : selected ? 0.5f : 1f;
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            double next = (i + 1) * Math.PI * 2 / 32;
            out.addLine(RenderLayer.HIGHLIGHTS,
                    x + radius * (float) Math.cos(a), y + radius * (float) Math.sin(a),
                    x + radius * (float) Math.cos(next), y + radius * (float) Math.sin(next),
                    selected ? 1.8f : 1.2f, r, g, b, alpha);
        }
    }
}
