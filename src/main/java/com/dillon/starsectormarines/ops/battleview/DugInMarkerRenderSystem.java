package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;

/**
 * A small sandbag arc under each friendly marine who is dug in.
 *
 * <p>Shown for every dug-in friendly marine, selected or not: it is a status the player wants to
 * read at a glance while choosing whom to send, and it is a short gold arc beside the body rather
 * than a ring or a cell fill, so it stays quiet. A selected squad's marines draw it brighter.
 * Observation only; an enemy is never marked and a marine the fog hides is not drawn.
 */
public final class DugInMarkerRenderSystem implements RenderSystem {
    static final float UNSELECTED_ALPHA = 0.6f;
    static final float SELECTED_ALPHA = 0.95f;

    @Override public RenderLayer layer() { return RenderLayer.HIGHLIGHTS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim == null || ctx.camera == null) return;
        var sim = ctx.sim;
        var camera = ctx.camera;
        ViewCull view = ViewCull.of(camera);
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || !sim.isDugIn(unit)) continue;
            int slot = sim.getRoster().indexOf(unit);
            byte visibility = sim.getFogOfWar().getUnitVisibility(slot);
            boolean selected = ctx.selection != null && sim.squad().hasSquad(unit)
                    && ctx.selection.isSquadSelected(sim.squad().squadId(unit));
            float fade = visibility == FogOfWarService.VIS_FADING ? sim.getFogOfWar().getFadeAlpha(slot) : 1f;
            float alpha = markerAlpha(true, visibility, selected, fade);
            if (alpha <= 0f) continue;
            float x = sim.world().renderX(unit), y = sim.world().renderY(unit);
            float radius = Math.max(0.46f, sim.getRoster().radius(unit) + 0.12f);
            if (!view.visible(x, y, radius * 2f)) continue;
            arc(out, camera.cellToScreenX(x), camera.cellToScreenY(y),
                    radius * camera.cellPxSize(), ctx.alphaMult * alpha);
        }
    }

    /** The marker's opacity before the frame's own multiplier, or 0 where none is drawn. */
    static float markerAlpha(boolean dugIn, byte visibility, boolean squadSelected, float fadeAlpha) {
        if (!dugIn || visibility == FogOfWarService.VIS_HIDDEN) return 0f;
        float base = squadSelected ? SELECTED_ALPHA : UNSELECTED_ALPHA;
        return visibility == FogOfWarService.VIS_FADING ? base * fadeAlpha : base;
    }

    /** A shallow arc on the near (screen-down; the HIGHLIGHTS space has y up) side of the body, like a sandbag lip. */
    static void arc(DrawList out, float x, float y, float radius, float alpha) {
        final int segments = 6;
        final double from = Math.toRadians(-140), to = Math.toRadians(-40);
        float r = radius * 1.15f;
        for (int i = 0; i < segments; i++) {
            double a = from + (to - from) * i / segments;
            double next = from + (to - from) * (i + 1) / segments;
            out.addLine(RenderLayer.HIGHLIGHTS,
                    x + r * (float) Math.cos(a), y + r * (float) Math.sin(a),
                    x + r * (float) Math.cos(next), y + r * (float) Math.sin(next),
                    2.6f, 1f, 0.82f, 0.25f, alpha);
        }
    }
}
