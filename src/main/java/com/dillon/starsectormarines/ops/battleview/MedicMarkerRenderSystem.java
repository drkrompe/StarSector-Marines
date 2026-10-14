package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.infantry.SquadMedic;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Which marine is the squad medic, and the treatment in hand ({@code squad-medic.md}).
 *
 * <p>The medic carries a small red cross on a white plate at the upper left of
 * the body, opposite side from nothing and clear of the health bar above, so it
 * reads as identity rather than as a state. It is deliberately not amber:
 * amber is the company's mark for <em>wounded</em>. While the medic is
 * treating, a green line joins medic and patient and a ring around the patient
 * fills with the treatment's progress; while the medic is on the way, the line
 * alone is drawn, faintly.
 *
 * <p>Drawn in {@link RenderLayer#UNITS} after the bodies, so it sits on top of
 * them the way the health bar does. Only the player's own marines are marked,
 * and a marine the fog hides is not drawn. Observation only.
 */
public final class MedicMarkerRenderSystem implements RenderSystem {
    static final float UNSELECTED_ALPHA = 0.8f;
    static final float SELECTED_ALPHA = 1f;
    private static final float CROSS_CELLS = 0.34f;
    private static final float MIN_CROSS_PX = 7f;
    /** A badge, not a sign: it stops growing as the camera closes in. */
    private static final float MAX_CROSS_PX = 15f;
    private static final float RING_SEGMENTS = 24f;

    @Override public RenderLayer layer() { return RenderLayer.UNITS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        if (ctx.sim == null || ctx.camera == null) return;
        BattleSimulation sim = ctx.sim;
        BattleCamera camera = ctx.camera;
        ViewCull view = ViewCull.of(camera);
        float cellPx = camera.cellPxSize();
        for (Squad squad : sim.getSquads()) {
            SquadMedic medic = squad.medic;
            if (medic == null || squad.faction != Faction.MARINE) continue;
            long carrier = sim.resolveUnit(medic.medicId());
            if (carrier == 0L) continue;
            boolean selected = ctx.selection != null && ctx.selection.isSquadSelected(squad.id);
            float medicAlpha = ctx.alphaMult * visibleAlpha(sim, carrier, selected);
            if (medicAlpha <= 0f) continue;
            float mx = sim.world().renderX(carrier);
            float my = sim.world().renderY(carrier);
            float radius = Math.max(0.42f, sim.getRoster().radius(carrier));
            float sx = camera.cellToScreenX(mx);
            float sy = camera.cellToScreenY(my);

            long patient = medic.patientId() == 0L ? 0L : sim.resolveUnit(medic.patientId());
            if (patient != 0L && medic.phase() != SquadMedic.Phase.IDLE) {
                float patientAlpha = ctx.alphaMult * visibleAlpha(sim, patient, selected);
                if (patientAlpha > 0f) {
                    float px = camera.cellToScreenX(sim.world().renderX(patient));
                    float py = camera.cellToScreenY(sim.world().renderY(patient));
                    boolean treating = medic.phase() == SquadMedic.Phase.TREATING;
                    float link = Math.min(medicAlpha, patientAlpha) * (treating ? 0.75f : 0.35f);
                    out.addLine(RenderLayer.UNITS, sx, sy, px, py, 2f, 0.45f, 0.95f, 0.55f, link);
                    if (treating) {
                        float ringPx = Math.max(0.42f, sim.getRoster().radius(patient)) * 1.35f * cellPx;
                        ring(out, px, py, ringPx, 1f, patientAlpha * 0.25f);
                        ring(out, px, py, ringPx, medic.treatmentProgress(), patientAlpha);
                    }
                }
            }

            if (!view.visible(mx, my, radius * 3f)) continue;
            float size = Math.max(MIN_CROSS_PX, Math.min(MAX_CROSS_PX, CROSS_CELLS * cellPx));
            cross(out, sx - radius * cellPx, sy + radius * cellPx, size, medicAlpha);
        }
    }

    /** The mark's opacity for a marine of the player's, or 0 where the fog hides them. */
    static float markerAlpha(byte visibility, boolean squadSelected, float fadeAlpha) {
        if (visibility == FogOfWarService.VIS_HIDDEN) return 0f;
        float base = squadSelected ? SELECTED_ALPHA : UNSELECTED_ALPHA;
        return visibility == FogOfWarService.VIS_FADING ? base * fadeAlpha : base;
    }

    private static float visibleAlpha(BattleSimulation sim, long unit, boolean selected) {
        int slot = sim.getRoster().indexOf(unit);
        byte visibility = sim.getFogOfWar().getUnitVisibility(slot);
        float fade = visibility == FogOfWarService.VIS_FADING ? sim.getFogOfWar().getFadeAlpha(slot) : 1f;
        return markerAlpha(visibility, selected, fade);
    }

    /** A red cross on a white plate, centred on {@code (x, y)}. */
    static void cross(DrawList out, float x, float y, float size, float alpha) {
        float h = size / 2f;
        float arm = size * 0.16f;
        float reach = size * 0.36f;
        out.addSolidRect(RenderLayer.UNITS, x - h, y - h, x + h, y + h, 0.96f, 0.96f, 0.94f, alpha);
        out.addSolidRect(RenderLayer.UNITS, x - reach, y - arm, x + reach, y + arm, 0.85f, 0.12f, 0.12f, alpha);
        out.addSolidRect(RenderLayer.UNITS, x - arm, y - reach, x + arm, y + reach, 0.85f, 0.12f, 0.12f, alpha);
    }

    /** A green arc round {@code (x, y)} covering {@code fraction} of a full turn from the top, clockwise. */
    static void ring(DrawList out, float x, float y, float radius, float fraction, float alpha) {
        int segments = Math.round(RING_SEGMENTS * Math.max(0f, Math.min(1f, fraction)));
        for (int i = 0; i < segments; i++) {
            double a = Math.PI / 2 - 2 * Math.PI * i / RING_SEGMENTS;
            double b = Math.PI / 2 - 2 * Math.PI * (i + 1) / RING_SEGMENTS;
            out.addLine(RenderLayer.UNITS,
                    x + radius * (float) Math.cos(a), y + radius * (float) Math.sin(a),
                    x + radius * (float) Math.cos(b), y + radius * (float) Math.sin(b),
                    2.5f, 0.45f, 0.95f, 0.55f, alpha);
        }
    }
}
