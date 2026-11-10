package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.ArrayList;
import java.util.List;

/**
 * The open pit's palette and its works ({@code voids.md}, the open pit): dusty spoil at the lip, gravel benches,
 * a sump on the floor, and the haul road spiralling down the benches, worked out from the drop's own field.
 * GL-free; {@link NaturalVoidRenderSystem} draws with it.
 */
final class PitVoidLook {

    /** How far round the pit one haul ramp runs from one bench to the next, in radians. UNTUNED. */
    static final float RAMP_SWEEP = 1.15f;
    /** How far out from the face the haul road reaches across the bench below, in cells: a haul truck's width. */
    static final float RAMP_CELLS = 1.9f;
    /** The bearing round the pit the haul road leaves the lip at, in radians from the map's east. */
    static final float RAMP_START = 2.4f;
    /** How high over the ground the spine's gallery runs on its trestles, in metres. */
    static final float BELT_METERS = 3.2f;
    /** The gap between the spine's trestles, in cells. */
    static final float TRESTLE_EVERY = 5f;

    private PitVoidLook() { }

    static boolean pit(NaturalVoid look) {
        return look != null && look.style() == NaturalVoid.Style.OPEN_PIT;
    }

    /** A lip or bench piece's colour: dusty spoil at the lip, worked gravel and scree on the benches. */
    static void rock(NaturalVoidField.Band band, int q, int k, int seed, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1], light = a[o + 2], edge = a[o + 3];
        float n1 = NaturalVoidField.Noise.fbm(x * .35f, y * .35f, seed * 7 + 29);
        float n2 = NaturalVoidField.Noise.value(x * 2.7f, y * 2.7f, seed * 7 + 31);
        float iron = clamp01(.45f + n1 * 1.4f);
        float r, g, b;
        if (k == 0) {
            // Spoil and dust trodden flat at the edge of the cut.
            r = lerp(.55f, .52f, iron); g = lerp(.51f, .42f, iron); b = lerp(.44f, .33f, iron);
        } else {
            // A working bench: crushed rock rolled flat, scree spilling where it breaks over the face below.
            r = lerp(.50f, .49f, iron); g = lerp(.48f, .41f, iron); b = lerp(.44f, .34f, iron);
            float scree = clamp01(1f - edge * 1.8f) * clamp01(.55f + n2);
            r = lerp(r, .62f, scree * .45f); g = lerp(g, .59f, scree * .45f); b = lerp(b, .54f, scree * .45f);
        }
        float grain = .88f + .24f * (n2 * .5f + .5f);
        float lit = Math.min(1.12f, light);
        into[q * 3] = r * grain * lit;
        into[q * 3 + 1] = g * grain * lit;
        into[q * 3 + 2] = b * grain * lit;
    }

    /** The floor: wet ore rubble, and a still sump of murky water where the floor lies farthest from its walls. */
    static float[] floor(float x, float y, float light, float edge, float sky, int seed) {
        float grain = NaturalVoidField.Noise.value(x * 2.3f, y * 2.3f, seed * 7 + 41) * .5f + .5f;
        float sump = sump(x, y, edge, seed);
        float bright = .62f + .45f * light;
        float r = .36f * bright * (.85f + .3f * grain), g = .31f * bright * (.85f + .3f * grain),
                b = .26f * bright * (.85f + .3f * grain);
        // Mud at the water's edge, then water taking the sky.
        float mud = clamp01(sump * 3f) * (1f - sump);
        r = lerp(r, .24f, mud); g = lerp(g, .21f, mud); b = lerp(b, .17f, mud);
        float wr = .15f + .10f * sky, wg = .19f + .12f * sky, wb = .18f + .12f * sky;
        return new float[]{lerp(r, wr, sump), lerp(g, wg, sump), lerp(b, wb, sump)};
    }

    /** How much of the floor at a point, {@code edge} cells in from its walls, is the sump's water: 0 dry, 1 water. */
    static float sump(float x, float y, float edge, int seed) {
        float n = NaturalVoidField.Noise.fbm(x * .11f, y * .11f, seed * 7 + 37);
        return clamp01((n + (edge - 5f) * .12f - .2f) * 4f);
    }

    /**
     * A piece of the haul road: four corners in field cells, each with its depth in metres, and the road's shade.
     * {@code side} pieces are the road's inner edge falling to the bench below; the rest are its surface, in
     * {@code lane} 0 (verge), 1 (rut) or 2 (crown).
     */
    record Ramp(int wall, float[] corners, boolean side, int lane) { }

    /**
     * The haul road down the benches: on each face, between the bearings {@link #RAMP_START} plus the face's turn
     * and one {@link #RAMP_SWEEP} further, a strip on the bench below running from the bench above's level down to
     * its own, so the road spirals from the lip to the floor. Bearings are taken round the middle of the pit.
     */
    static List<Ramp> ramps(NaturalVoidField field) {
        List<Ramp> out = new ArrayList<>();
        float sx = 0f, sy = 0f;
        int count = 0;
        for (int i = 0; i < field.w * field.h; i++) {
            if (!field.open[i] || field.bridge[i]) continue;
            sx += i % field.w + .5f;
            sy += i / field.w + .5f;
            count++;
        }
        if (count == 0) return out;
        float cx = sx / count, cy = sy / count;
        // Lanes across the road, outer edge (the face) to inner: verge, rut, crown, rut, verge.
        float[] lanes = {0f, .14f, .30f, .70f, .86f, 1f};
        int[] laneKind = {0, 1, 2, 1, 0};
        for (int k = 0; k < field.walls.size(); k++) {
            float start = RAMP_START + k * RAMP_SWEEP;
            for (NaturalVoidField.Wall wall : field.walls.get(k)) {
                float ta = turn(wall.x0() - cx, wall.y0() - cy, start), tb = turn(wall.x1() - cx, wall.y1() - cy, start);
                if (ta < 0f || ta > 1f || tb < 0f || tb > 1f || Math.abs(ta - tb) > .5f) continue;
                float za = wall.zTop() + ta * (wall.zFoot() - wall.zTop());
                float zb = wall.zTop() + tb * (wall.zFoot() - wall.zTop());
                float nx = wall.nx() * RAMP_CELLS, ny = wall.ny() * RAMP_CELLS;
                for (int l = 0; l < laneKind.length; l++) {
                    float u0 = lanes[l], u1 = lanes[l + 1];
                    out.add(new Ramp(k, new float[]{
                            wall.x0() + nx * u0, wall.y0() + ny * u0, za,
                            wall.x1() + nx * u0, wall.y1() + ny * u0, zb,
                            wall.x1() + nx * u1, wall.y1() + ny * u1, zb,
                            wall.x0() + nx * u1, wall.y0() + ny * u1, za}, false, laneKind[l]));
                }
                out.add(new Ramp(k, new float[]{
                        wall.x0() + nx, wall.y0() + ny, za,
                        wall.x1() + nx, wall.y1() + ny, zb,
                        wall.x1() + nx, wall.y1() + ny, wall.zFoot(),
                        wall.x0() + nx, wall.y0() + ny, wall.zFoot()}, true, 0));
            }
        }
        return out;
    }

    /** How far round from {@code start} the bearing of {@code (dx, dy)} lies, as a share of a ramp's sweep. */
    static float turn(float dx, float dy, float start) {
        double a = Math.atan2(dy, dx) - start;
        a -= 2 * Math.PI * Math.floor(a / (2 * Math.PI));
        return (float) (a / RAMP_SWEEP);
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
