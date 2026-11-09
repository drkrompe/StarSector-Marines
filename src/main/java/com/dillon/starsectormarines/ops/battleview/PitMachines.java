package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;

/**
 * The machines working an open pit ({@code voids.md}, the open pit): the evidence that the cut is a working mine on a
 * planet's scale. A face shovel loads a haul truck at the toe of a face on the floor, a dozer and a loader work the
 * floor clear of the sump, drill rigs stand on an upper bench drilling the next blast, and haul trucks climb the
 * spiral road loaded and come down it empty. Scenery: drawn in the drop, never on the battle's ground. GL-free;
 * {@link NaturalVoidRenderSystem} draws them from {@link #SHEET}.
 */
final class PitMachines {

    /**
     * The machines' sheet: three columns by two rows of frames in {@link Kind} order, each machine front to the east
     * and centred in its frame (`art-source/voids/open-pit/machines-guide.py`).
     */
    static final String SHEET = "graphics/battle/voids/pit-machines.png";
    /** One frame of the sheet in metres: 640 x 540 guide pixels at 30 pixels a metre. */
    static final float FRAME_W_METERS = 640f / 30f, FRAME_H_METERS = 540f / 30f;
    /**
     * Cells a metre of machine spans: a haul truck three cells long and two wide, the battle's own parked trucks'
     * size, as wide as the haul road it drives. UNTUNED.
     */
    static final float CELLS_PER_METER = .2f;
    /** How far in from the floor's edge the shovel stands, cells: its bucket in the face's toe. */
    static final float TOE_MIN = 2.2f, TOE_MAX = 3.8f;
    /** Dry floor: the sump's water at most this much where a machine stands. */
    static final float DRY = .05f;
    /** How far behind the truck being loaded the next one waits, cells. */
    static final float QUEUE = 3.8f;
    /** Floor machines stand at least this far apart, cells. */
    static final float APART = 8f;
    /** A drill stands this far out from its bench's face, cells, between the face and the bench's drop. */
    static final float DRILL_MIN = .8f, DRILL_MAX = 1.4f;
    /** The second drill of a pattern stands this far along the bench from the first, cells. */
    static final float DRILL_SPACING = 3.6f;
    /** Nothing works on a bench this near the haul road, cells. */
    static final float ROAD_CLEAR = 3f;

    /** The machines, in the sheet's frame order. */
    enum Kind { HAUL_LOADED, HAUL_EMPTY, SHOVEL, DRILL, DOZER, LOADER }

    /**
     * One machine at {@code (x, y)} in the field's cells, {@code depth} metres down, its front toward
     * {@code (headingX, headingY)}. It rests on band {@code step}; a truck on the haul road rests on the road down
     * face {@code rampWall} (the road on bench {@code rampWall + 1}), and {@code rampWall} is -1 for the rest.
     */
    record Machine(Kind kind, float x, float y, float depth, float headingX, float headingY, int step, int rampWall) { }

    private PitMachines() { }

    /** The pit's machines, worked out from its field and its haul road; none where the floor has no dry toe. */
    static List<Machine> place(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, int seed) {
        List<Machine> out = new ArrayList<>();
        int n = field.steps();
        if (n < 1) return out;
        floor(field, n, seed, out);
        benches(field, ramps, n, seed, out);
        road(ramps, seed, out);
        return out;
    }

    /** The shovel and the truck it loads at a face's toe, then a dozer and a loader elsewhere on the dry floor. */
    private static void floor(NaturalVoidField field, int n, int seed, List<Machine> out) {
        NaturalVoidField.Band band = field.bands.get(n);
        float depth = field.depth[n];
        List<float[]> toes = new ArrayList<>();
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            float x = band.attrs[o], y = band.attrs[o + 1], edge = band.attrs[o + 3];
            if (edge < TOE_MIN || edge > TOE_MAX || PitVoidLook.sump(x, y, edge, seed) > DRY) continue;
            toes.add(new float[]{x, y, NaturalVoidField.Noise.hash(Math.round(x * 4), Math.round(y * 4), seed + 11)});
        }
        toes.sort((a, b) -> Float.compare(a[2], b[2]));
        List<float[]> taken = new ArrayList<>();
        Kind[] floorKinds = {Kind.SHOVEL, Kind.DOZER, Kind.LOADER};
        int next = 0;
        for (float[] toe : toes) {
            if (next == floorKinds.length) break;
            if (near(taken, toe[0], toe[1], APART)) continue;
            float[] out0 = outward(field, field.reach[n], toe[0], toe[1]);
            if (out0 == null) continue;
            Kind kind = floorKinds[next];
            if (kind == Kind.SHOVEL) {
                // The truck stands beside the shovel, along the face, wherever the floor has room for it.
                Machine truck = null;
                for (int sideSign = 1; sideSign >= -1 && truck == null; sideSign -= 2) {
                    float tx = toe[0] - out0[1] * 2.7f * sideSign - out0[0] * .4f;
                    float ty = toe[1] + out0[0] * 2.7f * sideSign - out0[1] * .4f;
                    if (on(field, n, tx, ty)) truck = new Machine(Kind.HAUL_LOADED, tx, ty, depth, -out0[1] * sideSign,
                            out0[0] * sideSign, n, -1);
                }
                if (truck == null) continue;
                out.add(new Machine(Kind.SHOVEL, toe[0], toe[1], depth, out0[0], out0[1], n, -1));
                out.add(truck);
                // The next truck waits its turn behind the one being loaded.
                float qx = truck.x() - truck.headingX() * QUEUE, qy = truck.y() - truck.headingY() * QUEUE;
                if (on(field, n, qx, qy) && Math.hypot(qx - toe[0], qy - toe[1]) >= QUEUE) {
                    out.add(new Machine(Kind.HAUL_EMPTY, qx, qy, depth, truck.headingX(), truck.headingY(), n, -1));
                }
            } else {
                // The dozer pushes toward the face; the loader works along it.
                float hx = kind == Kind.DOZER ? out0[0] : -out0[1], hy = kind == Kind.DOZER ? out0[1] : out0[0];
                out.add(new Machine(kind, toe[0], toe[1], depth, hx, hy, n, -1));
            }
            taken.add(toe);
            next++;
        }
    }

    /** Two drill rigs a pattern apart on the first bench below the lip that has room, clear of the haul road. */
    private static void benches(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, int n, int seed,
                                List<Machine> out) {
        for (int k = 1; k < n; k++) {
            NaturalVoidField.Band band = field.bands.get(k);
            float best = Float.MAX_VALUE;
            float[] at = null;
            for (int q = 0; q < band.count; q++) {
                int o = q * 8;
                float x = band.attrs[o], y = band.attrs[o + 1], toDrop = band.attrs[o + 3];
                if (toDrop < DRILL_MIN || toDrop > DRILL_MAX || nearRoad(ramps, x, y)) continue;
                float score = NaturalVoidField.Noise.hash(Math.round(x * 4), Math.round(y * 4), seed + 23);
                if (score < best) { best = score; at = new float[]{x, y}; }
            }
            if (at == null) continue;
            float[] o0 = outward(field, field.reach[k + 1], at[0], at[1]);
            if (o0 == null) continue;
            float tx = -o0[1], ty = o0[0];
            float bx = at[0] + tx * DRILL_SPACING, by = at[1] + ty * DRILL_SPACING;
            out.add(new Machine(Kind.DRILL, at[0], at[1], field.depth[k], tx, ty, k, -1));
            if (on(field, k, bx, by) && !nearRoad(ramps, bx, by)) {
                out.add(new Machine(Kind.DRILL, bx, by, field.depth[k], tx, ty, k, -1));
            }
            return;
        }
    }

    /**
     * Two haul trucks on the spiral road, on different faces: the deeper one loaded and climbing, the upper one
     * empty and coming down.
     */
    private static void road(List<PitVoidLook.Ramp> ramps, int seed, List<Machine> out) {
        int walls = -1;
        for (PitVoidLook.Ramp r : ramps) if (!r.side() && r.lane() == 2) walls = Math.max(walls, r.wall());
        if (walls < 1) return;
        int up = Math.max(1, walls - 1 - Math.floorMod(seed, 2)), down = Math.max(0, up - 2);
        for (int wall : new int[]{up, down}) {
            List<PitVoidLook.Ramp> crown = new ArrayList<>();
            for (PitVoidLook.Ramp r : ramps) if (!r.side() && r.lane() == 2 && r.wall() == wall) crown.add(r);
            if (crown.isEmpty()) continue;
            float[] p = crown.get(crown.size() / 2).corners();
            float x = (p[0] + p[3] + p[6] + p[9]) / 4f, y = (p[1] + p[4] + p[7] + p[10]) / 4f;
            float z = (p[2] + p[5] + p[8] + p[11]) / 4f;
            float dx = p[3] - p[0], dy = p[4] - p[1];
            float len = (float) Math.hypot(dx, dy);
            if (len < 1e-4f) continue;
            dx /= len; dy /= len;
            // Along the road from x0 toward x1 the road falls when the depth grows.
            boolean falls = p[5] > p[2];
            boolean loaded = wall == up;
            float sign = loaded == falls ? -1f : 1f;
            out.add(new Machine(loaded ? Kind.HAUL_LOADED : Kind.HAUL_EMPTY, x, y, z, dx * sign, dy * sign,
                    wall + 1, wall));
        }
    }

    /** The unit direction out of the cut at a point: down the distance-in field {@code reach}, toward its edge. */
    static float[] outward(NaturalVoidField field, float[] reach, float x, float y) {
        float gx = field.sample(reach, x + .5f, y) - field.sample(reach, x - .5f, y);
        float gy = field.sample(reach, x, y + .5f) - field.sample(reach, x, y - .5f);
        float len = (float) Math.hypot(gx, gy);
        if (len < 1e-4f) return null;
        return new float[]{-gx / len, -gy / len};
    }

    /** Whether a point stands on band {@code k}: no shallower, and no deeper. */
    static boolean on(NaturalVoidField field, int k, float x, float y) {
        return field.depthAt(x, y) == field.depth[k];
    }

    private static boolean near(List<float[]> taken, float x, float y, float apart) {
        for (float[] t : taken) if (Math.hypot(t[0] - x, t[1] - y) < apart) return true;
        return false;
    }

    private static boolean nearRoad(List<PitVoidLook.Ramp> ramps, float x, float y) {
        for (PitVoidLook.Ramp r : ramps) {
            float[] p = r.corners();
            if (Math.hypot(p[0] - x, p[1] - y) < ROAD_CLEAR) return true;
        }
        return false;
    }
}
