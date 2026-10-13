package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.drone.DroneHash;
import com.dillon.starsectormarines.battle.tender.BuilderStations;
import com.dillon.starsectormarines.battle.tender.ConstructionOrder;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderCues;
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
    private static final int UNDER_FIRE_PULSE_TICKS = 15;
    private static final int STUN_ARCS = 5;
    private static final int FLOURISH_SPARKS = 8;

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
                if (order.underFire()) appendUnderFire(ctx, sx, sy, marker);
                if (working) appendTorches(ctx, tender, order, sx, sy);
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
        appendEffects(ctx);
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.HAZARDS, mesh);
    }

    /**
     * Stun bursts on builders that were just hit and a flourish where a turret just stood: pure
     * functions of the recorded event and the tick, from ids and ticks alone, with no state of
     * their own and nothing written back to the simulation.
     */
    private void appendEffects(RenderContext ctx) {
        int now = ctx.sim.getSimTickIndex();
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        for (TenderCues.Cue cue : ctx.sim.tenders().cues().recent()) {
            if (!isKnown(ctx, cue.faction(), cue.x(), cue.y())) continue;
            int age = now - cue.tick();
            if (age < 0 || age >= TenderCues.EFFECT_TICKS) continue;
            float u = age / (float) TenderCues.EFFECT_TICKS;
            float cx = camera.cellToScreenX(cue.x());
            float cy = camera.cellToScreenY(cue.y());
            if (cue.kind() == TenderCues.Kind.STUN) appendStun(ctx, cue, cx, cy, u, cellPx);
            else if (cue.kind() == TenderCues.Kind.COMPLETE) appendFlourish(ctx, cue, cx, cy, u, cellPx);
        }
    }

    /** Jagged electric arcs flung from a shocked builder, flickering for a few frames, and a pale ring. */
    private void appendStun(RenderContext ctx, TenderCues.Cue cue, float cx, float cy, float u, float cellPx) {
        float fade = (1f - u) * ctx.alphaMult;
        for (int k = 0; k < STUN_ARCS; k++) {
            float a = DroneHash.unit(cue.id(), 400 + k + 11 * cue.tick()) * (float) (Math.PI * 2.0);
            float len = (0.35f + 0.45f * DroneHash.unit(cue.id(), 500 + k + 11 * cue.tick())) * cellPx * (0.4f + u);
            float px = cx;
            float py = cy;
            for (int s = 1; s <= 3; s++) {
                float j = (DroneHash.unit(cue.id(), 600 + 3 * k + s + 13 * cue.tick()) - 0.5f) * 0.9f;
                float ang = a + j;
                float nx = cx + (float) Math.cos(ang) * len * s / 3f;
                float ny = cy + (float) Math.sin(ang) * len * s / 3f;
                segment(px, py, nx, ny, Math.max(1f, 0.03f * cellPx), 0.7f, 0.9f, 1f, 0.95f * fade);
                px = nx;
                py = ny;
            }
        }
        float r = (0.25f + 0.5f * u) * cellPx;
        PolyTess.appendAnnulus(mesh, cx, cy, r - 1.5f, r, 12, 0.6f, 0.85f, 1f, 0.7f * fade);
    }

    /** A green ring and a handful of sparks thrown outward where a turret has just been finished. */
    private void appendFlourish(RenderContext ctx, TenderCues.Cue cue, float cx, float cy, float u, float cellPx) {
        float fade = (1f - u) * ctx.alphaMult;
        float r = (0.5f + 1.1f * u) * cellPx;
        PolyTess.appendAnnulus(mesh, cx, cy, r - 2.5f, r, 24, 0.5f, 1f, 0.55f, 0.85f * fade);
        long seed = Float.floatToIntBits(cue.x()) * 31L + Float.floatToIntBits(cue.y());
        for (int k = 0; k < FLOURISH_SPARKS; k++) {
            float a = DroneHash.unit(seed, 700 + k) * (float) (Math.PI * 2.0);
            float d = (0.3f + 0.9f * u) * cellPx * (0.6f + 0.6f * DroneHash.unit(seed, 800 + k));
            PolyTess.appendAnnulus(mesh, cx + d * (float) Math.cos(a), cy + d * (float) Math.sin(a), 0f,
                    Math.max(1.3f, 0.04f * cellPx), 6, 1f, 0.95f, 0.6f, 0.9f * fade);
        }
    }

    private void segment(float x0, float y0, float x1, float y1, float half,
                         float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.01f) return;
        float nx = -dy / len * half;
        float ny = dx / len * half;
        mesh.appendQuad(x0 - nx, y0 - ny, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, r, g, b, a);
    }

    /**
     * A site whose builders were hit: a red ring that pulses out from the marker twice a second
     * for as long as the site is blocked, so the reason the work stopped reads at a glance.
     */
    private void appendUnderFire(RenderContext ctx, float sx, float sy, float marker) {
        int tick = ctx.sim.getSimTickIndex();
        float phase = Math.floorMod(tick, UNDER_FIRE_PULSE_TICKS) / (float) UNDER_FIRE_PULSE_TICKS;
        float radius = marker * (1.1f + 0.9f * phase);
        PolyTess.appendAnnulus(mesh, sx, sy, radius - 2f, radius, RING_SEGMENTS * 4,
                1f, 0.15f, 0.1f, (1f - phase) * 0.9f * ctx.alphaMult);
    }

    /**
     * A welding arc from each builder at its station to the site, struck and broken in
     * short bursts of its own, with a few sparks scattering from the point it lands on.
     * Drawn from nothing but the drones' positions and the tick, so it keeps no state.
     */
    private void appendTorches(RenderContext ctx, Tender tender, ConstructionOrder order, float sx, float sy) {
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        int tick = ctx.sim.getSimTickIndex();
        for (Long id : tender.builderIds()) {
            float bx = ctx.sim.world().x(id);
            float by = ctx.sim.world().y(id);
            if (!tender.welding(id, order, bx, by) || !BuilderStations.torchLit(id, tick)) continue;
            float px = camera.cellToScreenX(bx);
            float py = camera.cellToScreenY(by);
            float dx = sx - px;
            float dy = sy - py;
            float len = (float) Math.hypot(dx, dy);
            if (len < 1f) continue;
            float half = Math.max(1f, 0.04f * cellPx);
            float nx = -dy / len * half;
            float ny = dx / len * half;
            mesh.appendQuad(px - nx, py - ny, px + nx, py + ny, sx + nx, sy + ny, sx - nx, sy - ny,
                    1f, 0.93f, 0.6f, 0.85f * ctx.alphaMult);
            for (int k = 0; k < 3; k++) {
                float a = DroneHash.unit(id, 200 + k + 7 * Math.floorDiv(tick, BuilderStations.FLICKER_TICKS))
                        * (float) (Math.PI * 2.0);
                float r = (0.12f + 0.3f * DroneHash.unit(id, 300 + k + Math.floorDiv(tick, 2))) * cellPx;
                PolyTess.appendAnnulus(mesh, sx + r * (float) Math.cos(a), sy + r * (float) Math.sin(a), 0f,
                        Math.max(1.2f, 0.035f * cellPx), 6, 1f, 0.8f, 0.35f, 0.9f * ctx.alphaMult);
            }
        }
    }

    /** The player's own sites are always drawn; a hostile tender's only where the map has been seen. */
    private static boolean isKnown(RenderContext ctx, Faction faction, float x, float y) {
        return Allegiance.of(faction).friendly()
                || ctx.sim.getFogOfWar().isCellRevealed((int) Math.floor(x), (int) Math.floor(y));
    }
}
