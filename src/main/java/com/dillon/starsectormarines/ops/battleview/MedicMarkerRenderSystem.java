package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.infantry.SquadMedic;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Which marine is the squad medic, and the treatment in hand ({@code squad-medic.md}).
 *
 * <p>The medic carries a small red cross on a white plate at the upper left of
 * the body, clear of the health bar above, so it reads as identity rather than
 * as a state. It is deliberately not amber: amber is the company's mark for
 * <em>wounded</em>. A treatment is shown in the world rather than as an
 * overlay: the medic kneels over an open kit, and the patient gives off
 * {@link TreatmentParticles} for whatever is being restored.
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
    /** Below this a particle is a pixel or two and the treatment is the kneel alone. */
    private static final float MIN_PARTICLE_CELL_PX = 10f;
    /** Cell y increases up the screen, so dressing mist rises toward +y. */
    private static final float SCREEN_UP_Y = 1f;

    private final BattleSprites sprites;

    public MedicMarkerRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

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

            if (medic.phase() == SquadMedic.Phase.TREATING && cellPx >= MIN_PARTICLE_CELL_PX) {
                long patient = medic.patientId() == 0L ? 0L : sim.resolveUnit(medic.patientId());
                if (patient != 0L) treatment(ctx, view, out, medic, patient, mx, my);
            }

            if (!view.visible(mx, my, radius * 3f)) continue;
            float size = Math.max(MIN_CROSS_PX, Math.min(MAX_CROSS_PX, CROSS_CELLS * cellPx));
            cross(out, camera.cellToScreenX(mx) - radius * cellPx,
                    camera.cellToScreenY(my) + radius * cellPx, size, medicAlpha);
        }
    }

    private void treatment(RenderContext ctx, ViewCull view, DrawList out, SquadMedic medic,
                           long patient, float mx, float my) {
        BattleSimulation sim = ctx.sim;
        BattleCamera camera = ctx.camera;
        // The particles are the world, not a mark: only the fog dims them, never selection.
        float alpha = ctx.alphaMult * visibleAlpha(sim, patient, true);
        if (alpha <= 0f) return;
        float px = sim.world().renderX(patient);
        float py = sim.world().renderY(patient);
        float body = Math.max(0.42f, sim.getRoster().radius(patient));
        if (!view.visible(px, py, body * 3f)) return;
        sprites.ensureShadowSprite();
        SpriteAPI disc = sprites.shadowBlobSprite();
        if (disc == null) return;
        boolean health = sim.world().hp(patient) < sim.world().maxHp(patient);
        boolean armor = sim.world().hasArmor(patient)
                && sim.world().armor(patient) < sim.world().maxArmor(patient);
        float tx = mx - px;
        float ty = my - py;
        float len = (float) Math.hypot(tx, ty);
        if (len < 1e-3f) {
            tx = 0f;
            ty = 1f;
        } else {
            tx /= len;
            ty /= len;
        }
        float cellPx = camera.cellPxSize();
        float sx = camera.cellToScreenX(px);
        float sy = camera.cellToScreenY(py);
        float elapsed = medic.treatmentProgress() * medic.medicine.channelSeconds();
        TreatmentParticles.emit(elapsed, patient, health, armor, tx, ty, SCREEN_UP_Y, body,
                (kind, dx, dy, size, a) -> {
                    boolean dressing = kind == TreatmentParticles.Kind.DRESSING;
                    float d = size * cellPx;
                    out.addSprite(RenderLayer.UNITS, disc, sx + dx * cellPx, sy + dy * cellPx, d, d, 0f,
                            dressing ? 0.94f : 0.62f, dressing ? 0.95f : 0.74f,
                            dressing ? 0.90f : 0.86f, a * alpha);
                });
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
}
