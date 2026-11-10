package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.ArrayList;
import java.util.List;

/**
 * The machines of an open pit worked as a spacefaring colony's megaproject ({@code voids.md}, the open pit; the
 * owner's pick, the mass driver): the ore's path is the read. A cutting crawler the size of a building gnaws a face on
 * the floor and swings its ore back into the crusher and capsule loader, and the loader fires sealed ore capsules up
 * the mass driver ({@link NaturalVoid.Belt}), the rail that climbs out of the pit to the catch station on the rim.
 * Hover haulers ferry ore from the upper benches down to the loader and fly back empty; a drilling gantry readies the
 * next blast on a bench. Scenery: drawn in the drop, never on the battle's ground. GL-free;
 * {@link NaturalVoidRenderSystem} draws them from {@link #SHEET}.
 */
final class PitMachines {

    /**
     * The sheet: four columns by two rows of frames in {@link Kind} order, each piece centred in its frame, its front
     * or its run to the east (`art-source/voids/open-pit/massdriver-guide.py`).
     */
    static final String SHEET = "graphics/battle/voids/pit-massdriver.png";
    /** One frame of the sheet in guide pixels. */
    static final float FRAME_PX_W = 480f, FRAME_PX_H = 540f;
    /**
     * Cells a metre of machine spans: a hover hauler is about three cells long, the cutter nine, at the battle's own
     * vehicle scale. UNTUNED.
     */
    static final float CELLS_PER_METER = .2f;
    /** How far in from the floor's edge the cutter stands, cells: its drum in the face. */
    static final float CUT_MIN = 3f, CUT_MAX = 6.5f;
    /**
     * How far from the loader the cutter works, cells: clear of it, and near enough to swing its ore back to it. A small floor gives the cutter its farthest dry face within reach.
     */
    static final float FEED_MIN = 5f, FEED_MAX = 16f;
    /** Dry floor: the sump's water at most this much where a machine stands. */
    static final float DRY = .05f;
    /** A gantry stands this far out from its bench's face, cells, between the face and the bench's drop. */
    static final float GANTRY_MIN = .8f, GANTRY_MAX = 1.4f;
    /** Nothing works on a bench this near the haul road, cells. */
    static final float ROAD_CLEAR = 3f;
    /** How far from the loader a hauler comes in to tip, cells. */
    static final float TIP_REACH = 6f;
    /** Hover heights, metres over the ground beneath: coming in to tip, lifting off a bench, flying back empty. */
    static final float HOVER_TIP = 4f, HOVER_LIFT = 2f, HOVER_RETURN = 12f;
    /** Machines stand at least this far apart, cells. */
    static final float APART = 6f;
    /** The capsules on the mass driver: cells between one and the next, and cells a second they run. UNTUNED. */
    static final float CAPSULE_EVERY = 7f, CAPSULE_SPEED = 9f;

    /** The pieces, in the sheet's frame order, each with the scale its frame was drawn at. */
    enum Kind {
        CUTTER(9), LOADER(18), HAULER_LOADED(15), HAULER_EMPTY(15), GANTRY(30), CATCHER(28), RAIL(36), CAPSULE(48);

        /** Guide pixels a metre in this piece's frame. */
        final float pxPerMeter;

        Kind(float pxPerMeter) { this.pxPerMeter = pxPerMeter; }

        /** The frame's width on the map, cells. */
        float frameCellsW() { return FRAME_PX_W / pxPerMeter * CELLS_PER_METER; }

        /** The frame's height on the map, cells. */
        float frameCellsH() { return FRAME_PX_H / pxPerMeter * CELLS_PER_METER; }
    }

    /** One rail module's length and width along the driver, cells: the bay drawn 12 m by 5 m, made bolder. */
    static final float BAY_CELLS_LONG = 3.2f, BAY_CELLS_WIDE = 1.35f;
    /** The rail bay's part of its frame, as shares of the frame: 12 m by 5 m at 36 px a metre round the frame's middle. */
    static final float BAY_U0 = (240f - 216f) / FRAME_PX_W, BAY_U1 = (240f + 216f) / FRAME_PX_W;
    static final float BAY_V0 = (270f - 90f) / FRAME_PX_H, BAY_V1 = (270f + 90f) / FRAME_PX_H;

    /**
     * One piece at {@code (x, y)} in the field's cells over band {@code step}, {@code depth} metres down, its front
     * toward {@code (headingX, headingY)}; a hauler flies {@code hover} metres over the band, its shadow on the band.
     * A machine on the haul road would rest on the road down face {@code rampWall}; it is -1 for every machine now.
     */
    record Machine(Kind kind, float x, float y, float depth, float headingX, float headingY, int step, int rampWall,
                   float hover) {
        Machine(Kind kind, float x, float y, float depth, float headingX, float headingY, int step) {
            this(kind, x, y, depth, headingX, headingY, step, -1, 0f);
        }
    }

    private PitMachines() { }

    /**
     * The pit's machines, worked out from its field, its haul road and its mass driver ({@code belt}, in map cells,
     * which sit {@code margin} cells inside the field's, or null when the pit sends none). Without a driver there is
     * nothing to load, so no cutter, loader or hauler.
     */
    static List<Machine> place(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, NaturalVoid.Belt belt, int margin,
                               int seed) {
        List<Machine> out = new ArrayList<>();
        int n = field.steps();
        if (n < 1) return out;
        if (belt != null) {
            spineFoot(field, belt, margin, n, seed, out);
        }
        gantry(field, ramps, n, seed, out);
        if (!out.isEmpty() && out.get(0).kind() == Kind.LOADER) haulers(field, ramps, n, seed, out);
        return out;
    }

    /**
     * The loader at the driver's foot, its hopper turned from the rail, and the cutter at the face it can feed the
     * loader from: on the dry floor, its drum in the face and its back to the loader.
     */
    private static void spineFoot(NaturalVoidField field, NaturalVoid.Belt belt, int margin, int n, int seed,
                                  List<Machine> out) {
        float fx = belt.floorX() + margin, fy = belt.floorY() + margin;
        float dx = belt.rimX() - belt.floorX(), dy = belt.rimY() - belt.floorY();
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return;
        dx /= len; dy /= len;
        // The loader's breech, at the back of its 24 m, is the driver's foot.
        float half = 12f * CELLS_PER_METER;
        float cx = fx - dx * half, cy = fy - dy * half;
        if (!on(field, n, cx, cy)) { cx = fx; cy = fy; }
        float depth = field.depth[n];
        out.add(new Machine(Kind.LOADER, cx, cy, depth, -dx, -dy, n));
        NaturalVoidField.Band band = field.bands.get(n);
        float best = -Float.MAX_VALUE;
        float[] at = null;
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            float x = band.attrs[o], y = band.attrs[o + 1], edge = band.attrs[o + 3];
            if (edge < CUT_MIN || edge > CUT_MAX || PitVoidLook.sump(x, y, edge, seed) > DRY) continue;
            float away = (float) Math.hypot(x - cx, y - cy);
            if (away < FEED_MIN || away > FEED_MAX) continue;
            float[] face = outward(field, field.reach[n], x, y);
            if (face == null) continue;
            // Its back to the loader: the face it cuts lies away from the loader, so its boom swings back to it.
            float back = (face[0] * (x - cx) + face[1] * (y - cy)) / away;
            if (back < 0f || !dryUnder(field, n, x, y, face[0], face[1], seed)) continue;
            float score = back - away * .02f
                    + .05f * NaturalVoidField.Noise.hash(Math.round(x * 4), Math.round(y * 4), seed + 11);
            if (score > best) { best = score; at = new float[]{x, y, face[0], face[1]}; }
        }
        if (at != null) out.add(new Machine(Kind.CUTTER, at[0], at[1], depth, at[2], at[3], n));
    }

    /**
     * The hover haulers that bring the upper benches' ore to the loader: one coming in over the floor to tip into its
     * hopper, one lifting off a bench loaded, turned into the pit, and one flying back empty over another bench,
     * turned out of it. Each keeps clear of the haul road and of the machines already placed.
     */
    private static void haulers(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, int n, int seed,
                                List<Machine> out) {
        Machine loader = out.get(0);
        // Coming in: over the floor, a tip's reach out from the loader, the point nearest the front of its hopper.
        float best = -Float.MAX_VALUE;
        float[] tip = null;
        for (int a = 0; a < 24; a++) {
            double t = a * Math.PI / 12;
            float ux = (float) Math.cos(t), uy = (float) Math.sin(t);
            float x = loader.x() + ux * TIP_REACH, y = loader.y() + uy * TIP_REACH;
            if (!on(field, n, x, y) || near(out, x, y, APART, loader)) continue;
            float front = ux * loader.headingX() + uy * loader.headingY();
            if (front > best) { best = front; tip = new float[]{x, y, -ux, -uy}; }
        }
        if (tip != null) {
            out.add(new Machine(Kind.HAULER_LOADED, tip[0], tip[1], field.depth[n] - HOVER_TIP, tip[2], tip[3], n, -1,
                    HOVER_TIP));
        }
        int lifted = bench(field, ramps, n, seed + 31, 0, Kind.HAULER_LOADED, HOVER_LIFT, false, out);
        bench(field, ramps, n, seed + 47, lifted, Kind.HAULER_EMPTY, HOVER_RETURN, true, out);
    }

    /**
     * A hauler over a bench other than {@code not}, the middle benches first, out on the bench toward its drop, clear
     * of the haul road and the other machines; turned out of the pit when {@code outward}, into it otherwise. Returns
     * its bench, or 0 when none has room.
     */
    private static int bench(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, int n, int seed, int not, Kind kind,
                             float hover, boolean outward, List<Machine> out) {
        for (int step = 0; step < n - 1; step++) {
            int k = 1 + Math.floorMod(n / 2 - 1 + step, n - 1);
            if (k == not) continue;
            NaturalVoidField.Band band = field.bands.get(k);
            float best = Float.MAX_VALUE;
            float[] at = null;
            for (int q = 0; q < band.count; q++) {
                int o = q * 8;
                float x = band.attrs[o], y = band.attrs[o + 1], toDrop = band.attrs[o + 3];
                if (toDrop < GANTRY_MIN || toDrop > GANTRY_MAX + 1f) continue;
                float score = NaturalVoidField.Noise.hash(Math.round(x * 4), Math.round(y * 4), seed);
                if (score >= best || near(out, x, y, APART, null) || nearRoad(ramps, x, y)) continue;
                best = score;
                at = new float[]{x, y};
            }
            if (at == null) continue;
            float[] o0 = outward(field, field.reach[k], at[0], at[1]);
            if (o0 == null) continue;
            float sign = outward ? 1f : -1f;
            out.add(new Machine(kind, at[0], at[1], field.depth[k] - hover, o0[0] * sign, o0[1] * sign, k, -1, hover));
            return k;
        }
        return 0;
    }

    /** A drilling gantry on the first bench below the lip that has room, clear of the haul road, along the bench. */
    private static void gantry(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, int n, int seed,
                               List<Machine> out) {
        for (int k = 1; k < n; k++) {
            NaturalVoidField.Band band = field.bands.get(k);
            float best = Float.MAX_VALUE;
            float[] at = null;
            for (int q = 0; q < band.count; q++) {
                int o = q * 8;
                float x = band.attrs[o], y = band.attrs[o + 1], toDrop = band.attrs[o + 3];
                if (toDrop < GANTRY_MIN || toDrop > GANTRY_MAX) continue;
                float score = NaturalVoidField.Noise.hash(Math.round(x * 4), Math.round(y * 4), seed + 23);
                if (score >= best || near(out, x, y, APART, null) || nearRoad(ramps, x, y)) continue;
                best = score;
                at = new float[]{x, y};
            }
            if (at == null) continue;
            float[] o0 = outward(field, field.reach[k + 1], at[0], at[1]);
            if (o0 == null) continue;
            out.add(new Machine(Kind.GANTRY, at[0], at[1], field.depth[k], -o0[1], o0[0], k));
            return;
        }
    }

    /** The cutter's body, from its drum back to its discharge boom and across its tracks, in cells from its centre. */
    static final float CUTTER_REACH_FORE = 4.4f, CUTTER_REACH_AFT = 4f, CUTTER_REACH_SIDE = 2.8f;

    /** Whether the whole of a cutter at {@code (x, y)} facing {@code (hx, hy)} stands on the floor, out of the sump. */
    static boolean dryUnder(NaturalVoidField field, int n, float x, float y, float hx, float hy, int seed) {
        float[][] at = {{0f, 0f}, {CUTTER_REACH_FORE * .7f, 0f}, {-CUTTER_REACH_AFT, 0f},
                {0f, CUTTER_REACH_SIDE}, {0f, -CUTTER_REACH_SIDE}, {-CUTTER_REACH_AFT * .6f, CUTTER_REACH_SIDE},
                {-CUTTER_REACH_AFT * .6f, -CUTTER_REACH_SIDE}};
        for (float[] a : at) {
            float px = x + hx * a[0] - hy * a[1], py = y + hy * a[0] + hx * a[1];
            if (!on(field, n, px, py)) return false;
            if (PitVoidLook.sump(px, py, field.sample(field.reach[n], px, py), seed) > DRY) return false;
        }
        return true;
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

    private static boolean near(List<Machine> taken, float x, float y, float apart, Machine except) {
        for (Machine m : taken) if (m != except && Math.hypot(m.x() - x, m.y() - y) < apart) return true;
        return false;
    }

    private static boolean nearRoad(List<PitVoidLook.Ramp> ramps, float x, float y) {
        for (PitVoidLook.Ramp r : ramps) {
            float[] p = r.corners();
            float dx = p[0] - x, dy = p[1] - y;
            if (dx * dx + dy * dy < ROAD_CLEAR * ROAD_CLEAR) return true;
        }
        return false;
    }
}
