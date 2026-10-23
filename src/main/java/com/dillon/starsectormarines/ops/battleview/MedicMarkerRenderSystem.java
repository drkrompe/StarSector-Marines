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
            float medicAlpha = ctx.alphaMult * visibleAlpha(ctx, carrier, selected);
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
            float fill = MedicPoolGauge.fill(medic.energy(), medic.medicine.energyMax());
            boolean ready = MedicPoolGauge.ready(medic.energy(), medic.medicine.minStartEnergy());
            mark(out, camera.cellToScreenX(mx) - radius * cellPx,
                    camera.cellToScreenY(my) + radius * cellPx, size, fill, ready, medicAlpha);
        }
    }

    private void treatment(RenderContext ctx, ViewCull view, DrawList out, SquadMedic medic,
                           long patient, float mx, float my) {
        BattleSimulation sim = ctx.sim;
        BattleCamera camera = ctx.camera;
        // The particles are the world, not a mark: only the fog dims them, never selection.
        float alpha = ctx.alphaMult * visibleAlpha(ctx, patient, true);
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
        float elapsed = medic.treatmentSeconds();
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

    private static float visibleAlpha(RenderContext ctx, long unit, boolean selected) {
        BattleSimulation sim = ctx.sim;
        int slot = sim.getRoster().indexOf(unit);
        byte visibility = ctx.hostProfile.fogHidesUnits()
                ? sim.getFogOfWar().getUnitVisibility(slot) : FogOfWarService.VIS_VISIBLE;
        float fade = visibility == FogOfWarService.VIS_FADING ? sim.getFogOfWar().getFadeAlpha(slot) : 1f;
        return markerAlpha(visibility, selected, fade);
    }

    /** Look options for the pool, compared on review frames; one survives. */
    public enum PoolStyle { FILL, RING, BAR }

    /** Temporary comparison selector. */
    public static PoolStyle poolStyle = PoolStyle.FILL;

    private static final float[] GREEN = {0.24f, 0.80f, 0.36f};
    private static final float[] SPENT = {0.58f, 0.62f, 0.59f};
    private static final float[] OUTLINE = {0.05f, 0.08f, 0.06f};
    private static final float[] EMPTY = {0.13f, 0.22f, 0.15f};

    /** The medic's green cross, outlined dark, with the pool drawn in the current style. */
    static void mark(DrawList out, float x, float y, float size, float fill, boolean ready, float alpha) {
        float reach = size * 0.5f;
        float arm = size * 0.19f;
        float o = Math.max(1f, size * 0.1f);
        float[] pool = ready ? GREEN : SPENT;
        crossRects(out, x, y, reach + o, arm + o, -Float.MAX_VALUE, Float.MAX_VALUE, OUTLINE, alpha);
        switch (poolStyle) {
            case FILL -> {
                crossRects(out, x, y, reach, arm, -Float.MAX_VALUE, Float.MAX_VALUE, EMPTY, alpha);
                float level = y - reach + fill * 2f * reach;
                if (fill > 0f) crossRects(out, x, y, reach, arm, -Float.MAX_VALUE, level, pool, alpha);
            }
            case RING -> {
                crossRects(out, x, y, reach, arm, -Float.MAX_VALUE, Float.MAX_VALUE, pool, alpha);
                float r = reach + o + 2.5f;
                ring(out, x, y, r, 1f, 3f, OUTLINE, alpha);
                ring(out, x, y, r, 1f, 1.6f, EMPTY, alpha);
                if (fill > 0f) ring(out, x, y, r, fill, 1.6f, pool, alpha);
            }
            case BAR -> {
                crossRects(out, x, y, reach, arm, -Float.MAX_VALUE, Float.MAX_VALUE, pool, alpha);
                float top = y - reach - o - 1.5f;
                float h = Math.max(2f, size * 0.18f);
                float left = x - reach - o;
                float right = x + reach + o;
                out.addSolidRect(RenderLayer.UNITS, left - 1f, top - h - 1f, right + 1f, top + 1f,
                        OUTLINE[0], OUTLINE[1], OUTLINE[2], alpha);
                out.addSolidRect(RenderLayer.UNITS, left, top - h, right, top,
                        EMPTY[0], EMPTY[1], EMPTY[2], alpha);
                if (fill > 0f) {
                    out.addSolidRect(RenderLayer.UNITS, left, top - h, left + (right - left) * fill, top,
                            pool[0], pool[1], pool[2], alpha);
                }
            }
        }
    }

    /** The cross's two bars, clipped to screen rows {@code [yMin, yMax]} (screen y runs up). */
    private static void crossRects(DrawList out, float x, float y, float reach, float arm,
                                   float yMin, float yMax, float[] c, float alpha) {
        rect(out, x - reach, y - arm, x + reach, y + arm, yMin, yMax, c, alpha);
        rect(out, x - arm, y - reach, x + arm, y + reach, yMin, yMax, c, alpha);
    }

    private static void rect(DrawList out, float x0, float y0, float x1, float y1,
                             float yMin, float yMax, float[] c, float alpha) {
        float lo = Math.max(y0, yMin);
        float hi = Math.min(y1, yMax);
        if (hi <= lo) return;
        out.addSolidRect(RenderLayer.UNITS, x0, lo, x1, hi, c[0], c[1], c[2], alpha);
    }

    /** An arc of {@code share} of a full turn, clockwise from the top. */
    private static void ring(DrawList out, float x, float y, float r, float share, float width,
                             float[] c, float alpha) {
        int segments = Math.max(1, Math.round(28 * share));
        double step = 2 * Math.PI * share / segments;
        for (int i = 0; i < segments; i++) {
            double a0 = i * step;
            double a1 = (i + 1) * step;
            out.addLine(RenderLayer.UNITS, x + (float) (r * Math.sin(a0)), y + (float) (r * Math.cos(a0)),
                    x + (float) (r * Math.sin(a1)), y + (float) (r * Math.cos(a1)), width,
                    c[0], c[1], c[2], alpha);
        }
    }
}
