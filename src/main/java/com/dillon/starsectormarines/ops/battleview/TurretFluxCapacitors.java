package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.turret.FluxShield;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;

/**
 * A shielded hardpoint's capacitors, drawn on its plinth so a player reads how close its shield is to breaking
 * without any interface ({@code ground-turret-tiers.md}). Presentation only, read off the simulation's
 * {@link FluxShield}; its flicker and arcs are hashed from the turret and the simulation tick, so the same battle
 * draws the same capacitors every time and nothing in it is drawn from a random stream the battle uses.
 *
 * <ul>
 *   <li><b>Charge.</b> The cells fill with the capacitors' flux: dark when empty, field blue as they fill,
 *   whitening over the last quarter.</li>
 *   <li><b>Strain.</b> Past {@link #STRAIN} of capacity the lit cells flicker and arcs crackle across the plinth,
 *   more of them the nearer the shield is to breaking.</li>
 *   <li><b>Dump.</b> At a break the cells flash white and drain in {@link #DUMP_SECONDS}, throwing a discharge ring.</li>
 *   <li><b>Recharge.</b> For the rest of the overload the cells refill in a dim charging tint as the cooldown runs;
 *   full means the shield may rise again.</li>
 * </ul>
 */
final class TurretFluxCapacitors {

    /** Which capacitor motif a hardpoint wears. */
    enum Motif {
        /** A strip of four cells along each side of the plinth's top. */
        BANKS,
        /** A ring of sixteen cells round the gun's head: the default, the plainest gauge of the three. */
        RING,
        /** No cells: the projector ring under the head glows and pulses with the charge. */
        CORE
    }

    /** The motif drawn; the look study sets it to compare them. */
    static Motif motif = Motif.RING;

    /** Share of capacity past which the capacitors show strain. */
    static final float STRAIN = 0.75f;
    /** How long a break's dump takes, in seconds. */
    static final float DUMP_SECONDS = 0.4f;

    /** What the capacitors show: how full, and in which state. */
    enum Phase { CHARGE, DUMP, RECHARGE }

    private static final float[] HOUSING = {0.04f, 0.05f, 0.07f};
    private static final float[] EMPTY = {0.09f, 0.12f, 0.17f};
    private static final float[] COLD = {0.12f, 0.58f, 1.00f};
    private static final float[] HOT = {0.80f, 0.96f, 1.00f};
    private static final float[] CHARGING = {0.30f, 0.42f, 0.58f};

    private TurretFluxCapacitors() {}

    /** The phase {@code shield}'s capacitors are drawn in. */
    static Phase phase(FluxShield shield) {
        if (!shield.overloaded()) return Phase.CHARGE;
        return shield.sinceBreak() < DUMP_SECONDS ? Phase.DUMP : Phase.RECHARGE;
    }

    /** How full the capacitors are drawn, {@code [0, 1]}, in their {@link #phase}. */
    static float gauge(FluxShield shield) {
        switch (phase(shield)) {
            case DUMP: return 1f - shield.sinceBreak() / DUMP_SECONDS;
            case RECHARGE: return 1f - shield.overloadShare();
            default: return shield.fluxShare();
        }
    }

    /**
     * How strained the capacitors look, {@code [0, 1]}: zero below {@link #STRAIN}, one at full. Charging after a
     * break shows none.
     */
    static float strain(FluxShield shield) {
        if (phase(shield) != Phase.CHARGE) return 0f;
        return Math.max(0f, (shield.fluxShare() - STRAIN) / (1f - STRAIN));
    }

    /** Appends the capacitors of turret {@code id}, standing at ({@code x}, {@code y}), to {@code mesh}. */
    static void append(PolyMesh mesh, long id, float x, float y, FluxShield shield, BattleCamera camera,
                       float cellPx, long tick, float alpha) {
        Phase phase = phase(shield);
        float gauge = gauge(shield);
        float strain = strain(shield);
        float flicker = strain > 0f ? 1f - 0.45f * strain * hash01(id, tick, 1) : 1f;
        float sx = camera.cellToScreenX(x);
        float sy = camera.cellToScreenY(y);
        float[] rgb = new float[3];
        switch (motif) {
            case CORE: core(mesh, sx, sy, cellPx, phase, gauge, strain, flicker, tick, alpha, rgb); break;
            case BANKS: banks(mesh, x, y, camera, cellPx, phase, gauge, flicker, alpha, rgb); break;
            default: ring(mesh, sx, sy, cellPx, phase, gauge, flicker, alpha, rgb); break;
        }
        if (strain > 0f) arcs(mesh, id, sx, sy, cellPx, strain, tick, alpha);
        if (phase == Phase.DUMP) {
            float spent = shield.sinceBreak() / DUMP_SECONDS;
            float r = cellPx * (1.0f + 1.6f * spent);
            PolyTess.appendAnnulus(mesh, sx, sy, r - 3f, r, 48, HOT[0], HOT[1], HOT[2], 0.8f * (1f - spent) * alpha);
            PolyTess.appendAnnulus(mesh, sx, sy, 0f, cellPx * 1.4f, 32, HOT[0], HOT[1], HOT[2],
                    0.35f * (1f - spent) * alpha);
        }
    }

    /** A strip of four cells along each side of the plinth's top, every side filling clockwise together. */
    private static void banks(PolyMesh mesh, float x, float y, BattleCamera camera, float cellPx, Phase phase,
                              float gauge, float flicker, float alpha, float[] rgb) {
        int cells = 4;
        float span = 1.7f;
        float inset = 1.24f;
        float thick = 0.11f;
        for (int side = 0; side < 4; side++) {
            boolean vertical = side < 2;
            float sign = (side & 1) == 0 ? -1f : 1f;
            for (int k = 0; k < cells; k++) {
                float a0 = -span * 0.5f + span * k / cells + 0.04f;
                float a1 = -span * 0.5f + span * (k + 1) / cells - 0.04f;
                // Clockwise round the plinth: the top and right run with the axis, the bottom and left against it.
                int order = side == 1 || side == 2 ? k : cells - 1 - k;
                float lit = clamp01(gauge * cells - order);
                float x0 = vertical ? x + sign * inset - thick : x + a0;
                float x1 = vertical ? x + sign * inset + thick : x + a1;
                float y0 = vertical ? y + a0 : y + sign * inset - thick;
                float y1 = vertical ? y + a1 : y + sign * inset + thick;
                cell(mesh, camera.cellToScreenX(x0), camera.cellToScreenY(y0), camera.cellToScreenX(x1),
                        camera.cellToScreenY(y1), phase, gauge, lit, flicker, alpha, rgb);
            }
        }
    }

    /** Sixteen cells round the head, filling clockwise from the top. */
    private static void ring(PolyMesh mesh, float sx, float sy, float cellPx, Phase phase, float gauge,
                             float flicker, float alpha, float[] rgb) {
        int cells = 16;
        float inner = 1.06f * cellPx;
        float outer = 1.30f * cellPx;
        float sweep = 360f / cells - 5f;
        for (int k = 0; k < cells; k++) {
            float centre = -90f + (k + 0.5f) * 360f / cells;
            float lit = clamp01(gauge * cells - k);
            colour(phase, gauge, rgb);
            PolyTess.appendSector(mesh, sx, sy, inner - 1.5f, outer + 1.5f, centre, sweep + 2f, 3,
                    HOUSING[0], HOUSING[1], HOUSING[2], 0.95f * alpha);
            PolyTess.appendSector(mesh, sx, sy, inner, outer, centre, sweep, 3,
                    EMPTY[0], EMPTY[1], EMPTY[2], alpha);
            if (lit <= 0f) continue;
            float a = cellAlpha(phase, gauge, lit, flicker);
            PolyTess.appendSector(mesh, sx, sy, inner + 1f, outer - 1f, centre, sweep, 3,
                    rgb[0], rgb[1], rgb[2], a * alpha);
            PolyTess.appendSector(mesh, sx, sy, outer - 1f, outer + 0.25f * cellPx * gauge, centre, sweep, 3,
                    rgb[0], rgb[1], rgb[2], 0.25f * a * alpha);
        }
    }

    /** The projector ring under the head: brighter, wider and faster-pulsing as the charge climbs. */
    private static void core(PolyMesh mesh, float sx, float sy, float cellPx, Phase phase, float gauge,
                             float strain, float flicker, long tick, float alpha, float[] rgb) {
        colour(phase, gauge, rgb);
        float rate = 0.10f + 0.5f * gauge * gauge;
        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * rate);
        float a = (phase == Phase.RECHARGE ? 0.35f : 0.35f + 0.6f * gauge) * (0.7f + 0.3f * pulse) * flicker;
        float inner = 0.78f * cellPx;
        float outer = (0.92f + 0.18f * gauge + 0.08f * strain * pulse) * cellPx;
        PolyTess.appendAnnulus(mesh, sx, sy, inner, outer, 48, rgb[0], rgb[1], rgb[2], a * alpha);
        PolyTess.appendAnnulus(mesh, sx, sy, outer, outer + (0.15f + 0.45f * gauge) * cellPx, 48,
                rgb[0], rgb[1], rgb[2], 0.25f * a * alpha);
        if (phase == Phase.RECHARGE) {
            PolyTess.appendArc(mesh, sx, sy, outer + 1f, outer + 4f, gauge, 48,
                    CHARGING[0], CHARGING[1], CHARGING[2], 0.7f * alpha);
        }
    }

    /** One rectangular cell between two screen corners: its dark housing, its charge and, when lit, a glow. */
    private static void cell(PolyMesh mesh, float ax, float ay, float bx, float by, Phase phase, float gauge,
                             float lit, float flicker, float alpha, float[] rgb) {
        float x0 = Math.min(ax, bx);
        float x1 = Math.max(ax, bx);
        float y0 = Math.min(ay, by);
        float y1 = Math.max(ay, by);
        rect(mesh, x0 - 1.5f, y0 - 1.5f, x1 + 1.5f, y1 + 1.5f, HOUSING[0], HOUSING[1], HOUSING[2], 0.95f * alpha);
        rect(mesh, x0, y0, x1, y1, EMPTY[0], EMPTY[1], EMPTY[2], alpha);
        if (lit <= 0f) return;
        colour(phase, gauge, rgb);
        float a = cellAlpha(phase, gauge, lit, flicker);
        rect(mesh, x0 + 1f, y0 + 1f, x1 - 1f, y1 - 1f, rgb[0], rgb[1], rgb[2], a * alpha);
        float glow = (2f + 5f * gauge) * lit;
        rect(mesh, x0 - glow, y0 - glow, x1 + glow, y1 + glow, rgb[0], rgb[1], rgb[2], 0.18f * a * alpha);
    }

    /** Arcs crackling between points round the head: more, longer and brighter the nearer the break. */
    private static void arcs(PolyMesh mesh, long id, float sx, float sy, float cellPx, float strain, long tick,
                             float alpha) {
        // A new arc pattern every other tick, and a share of ticks dark, so they crackle rather than glow.
        long beat = tick / 2;
        int count = 1 + (int) (strain * 4f);
        for (int i = 0; i < count; i++) {
            if (hash01(id, beat, 10 + i) > 0.35f + 0.6f * strain) continue;
            float a0 = hash01(id, beat, 20 + i) * 6.2831853f;
            float a1 = a0 + (0.5f + hash01(id, beat, 30 + i)) * (hash01(id, beat, 40 + i) < 0.5f ? -1f : 1f);
            float r0 = (0.85f + 0.5f * hash01(id, beat, 50 + i)) * cellPx;
            float r1 = (0.85f + 0.5f * hash01(id, beat, 60 + i)) * cellPx;
            float px = sx + (float) Math.cos(a0) * r0;
            float py = sy + (float) Math.sin(a0) * r0;
            float qx = sx + (float) Math.cos(a1) * r1;
            float qy = sy + (float) Math.sin(a1) * r1;
            bolt(mesh, px, py, qx, qy, cellPx * 0.18f, id, beat, i, (0.6f + 0.4f * strain) * alpha);
        }
    }

    /** A jagged bolt from P to Q in four kinks, a bright core over a blue glow. */
    private static void bolt(PolyMesh mesh, float px, float py, float qx, float qy, float jag, long id, long beat,
                             int salt, float alpha) {
        float dx = qx - px;
        float dy = qy - py;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float nx = -dy / len;
        float ny = dx / len;
        float lastX = px;
        float lastY = py;
        for (int k = 1; k <= 4; k++) {
            float t = k / 4f;
            float off = k == 4 ? 0f : (hash01(id, beat, 100 + salt * 8 + k) - 0.5f) * 2f * jag;
            float x = px + dx * t + nx * off;
            float y = py + dy * t + ny * off;
            segment(mesh, lastX, lastY, x, y, 4f, COLD[0], COLD[1], COLD[2], 0.35f * alpha);
            segment(mesh, lastX, lastY, x, y, 1.4f, HOT[0], HOT[1], HOT[2], alpha);
            lastX = x;
            lastY = y;
        }
    }

    private static void colour(Phase phase, float gauge, float[] rgb) {
        float[] from = phase == Phase.RECHARGE ? CHARGING : COLD;
        float heat = phase == Phase.DUMP ? 1f : phase == Phase.RECHARGE ? 0f : smoothstep(0.7f, 1f, gauge);
        for (int c = 0; c < 3; c++) rgb[c] = from[c] + (HOT[c] - from[c]) * heat;
    }

    private static float cellAlpha(Phase phase, float gauge, float lit, float flicker) {
        float base = phase == Phase.RECHARGE ? 0.55f : 0.75f + 0.25f * gauge;
        return base * (0.35f + 0.65f * lit) * flicker;
    }

    private static void rect(PolyMesh mesh, float x0, float y0, float x1, float y1, float r, float g, float b,
                             float a) {
        mesh.appendQuad(x0, y0, x1, y0, x1, y1, x0, y1, r, g, b, a);
    }

    private static void segment(PolyMesh mesh, float x0, float y0, float x1, float y1, float width,
                                float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return;
        float hx = -dy / len * width * 0.5f;
        float hy = dx / len * width * 0.5f;
        mesh.appendQuad(x0 - hx, y0 - hy, x0 + hx, y0 + hy, x1 + hx, y1 + hy, x1 - hx, y1 - hy, r, g, b, a);
    }

    /** A value in {@code [0, 1)} hashed from a turret, a tick and a salt: the same inputs, the same value. */
    static float hash01(long id, long tick, int salt) {
        long h = id * 0x9E3779B97F4A7C15L + tick * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }
}
