package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.ArrayList;
import java.util.List;

/**
 * The machines of an open pit worked as a spacefaring colony's megaproject ({@code voids.md}, the open pit): the ore's
 * path is the read. A cutting crawler the size of a building gnaws a face on the floor and swings its ore back into a
 * crushing station, and the station feeds the spine, the covered conveyor ({@link NaturalVoid.Belt}) that climbs out
 * of the pit to the works. Ore crawlers bring ore from the upper benches to a hopper beside the spine, and climb and
 * descend the haul road; a drilling gantry readies the next blast on a bench. Scenery: drawn in the drop, never on the
 * battle's ground. GL-free; {@link NaturalVoidRenderSystem} draws them from {@link #SHEET}.
 */
final class PitMachines {

    /**
     * The sheet: four columns by two rows of frames in {@link Kind} order, each piece centred in its frame, its front
     * or its run to the east (`art-source/voids/open-pit/megaworks-guide.py`).
     */
    static final String SHEET = "graphics/battle/voids/pit-megaworks.png";
    /** One frame of the sheet in guide pixels. */
    static final float FRAME_PX_W = 480f, FRAME_PX_H = 540f;
    /**
     * Cells a metre of machine spans: an ore crawler is between three and four cells long, the cutter nine, at the
     * battle's own vehicle scale. UNTUNED.
     */
    static final float CELLS_PER_METER = .2f;
    /** How far in from the floor's edge the cutter stands, cells: its drum in the face. */
    static final float CUT_MIN = 3f, CUT_MAX = 6.5f;
    /**
     * How far from the crushing station the cutter works, cells: clear of it, and near enough to swing its ore back
     * to it. A small floor gives the cutter its farthest dry face within reach.
     */
    static final float FEED_MIN = 5f, FEED_MAX = 16f;
    /** Dry floor: the sump's water at most this much where a machine stands. */
    static final float DRY = .05f;
    /** A gantry stands this far out from its bench's face, cells, between the face and the bench's drop. */
    static final float GANTRY_MIN = .8f, GANTRY_MAX = 1.4f;
    /** Nothing works on a bench this near the haul road, cells. */
    static final float ROAD_CLEAR = 3f;
    /** How far to the side of the spine the bench hopper stands, cells, and its crawler beyond it. */
    static final float HOPPER_SIDE = 2.2f, TIPPING_SIDE = 4.6f;

    /** The pieces, in the sheet's frame order, each with the scale its frame was drawn at. */
    enum Kind {
        CUTTER(9), CRUSHER(18), ORE_LOADED(24), ORE_EMPTY(24), GANTRY(30), TOWER(28), GALLERY(36), HOPPER(36);

        /** Guide pixels a metre in this piece's frame. */
        final float pxPerMeter;

        Kind(float pxPerMeter) { this.pxPerMeter = pxPerMeter; }

        /** The frame's width on the map, cells. */
        float frameCellsW() { return FRAME_PX_W / pxPerMeter * CELLS_PER_METER; }

        /** The frame's height on the map, cells. */
        float frameCellsH() { return FRAME_PX_H / pxPerMeter * CELLS_PER_METER; }
    }

    /** One gallery module's length and width along the spine, cells: the bay drawn 12 m by 5 m, made bolder. */
    static final float BAY_CELLS_LONG = 3.2f, BAY_CELLS_WIDE = 1.35f;
    /** The bay's part of its frame, as shares of the frame: 12 m by 5 m at 36 px a metre round the frame's middle. */
    static final float BAY_U0 = (240f - 216f) / FRAME_PX_W, BAY_U1 = (240f + 216f) / FRAME_PX_W;
    static final float BAY_V0 = (270f - 90f) / FRAME_PX_H, BAY_V1 = (270f + 90f) / FRAME_PX_H;

    /**
     * One piece at {@code (x, y)} in the field's cells, {@code depth} metres down, its front toward
     * {@code (headingX, headingY)}. It rests on band {@code step}; an ore crawler on the haul road rests on the road
     * down face {@code rampWall} (the road on bench {@code rampWall + 1}), and {@code rampWall} is -1 for the rest.
     */
    record Machine(Kind kind, float x, float y, float depth, float headingX, float headingY, int step, int rampWall) { }

    private PitMachines() { }

    /**
     * The pit's machines, worked out from its field, its haul road and its spine ({@code belt}, in map cells, which
     * sit {@code margin} cells inside the field's, or null when the pit sends none). Without a spine there is nothing
     * to crush into, so no cutter, station or hopper.
     */
    static List<Machine> place(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, NaturalVoid.Belt belt, int margin,
                               int seed) {
        List<Machine> out = new ArrayList<>();
        int n = field.steps();
        if (n < 1) return out;
        if (belt != null) {
            spineFoot(field, belt, margin, n, seed, out);
            benchHopper(field, ramps, belt, margin, n, out);
        }
        gantry(field, ramps, n, seed, out);
        road(ramps, seed, out);
        return out;
    }

    /**
     * The crushing station at the spine's foot, its hopper turned from the spine, and the cutter at the face it can
     * feed the station from: on the dry floor, its drum in the face and its back to the station.
     */
    private static void spineFoot(NaturalVoidField field, NaturalVoid.Belt belt, int margin, int n, int seed,
                                  List<Machine> out) {
        float fx = belt.floorX() + margin, fy = belt.floorY() + margin;
        float dx = belt.rimX() - belt.floorX(), dy = belt.rimY() - belt.floorY();
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return;
        dx /= len; dy /= len;
        // The station's discharge, at the back of its 24 m, is the spine's foot.
        float half = 12f * CELLS_PER_METER;
        float cx = fx - dx * half, cy = fy - dy * half;
        if (!on(field, n, cx, cy)) { cx = fx; cy = fy; }
        float depth = field.depth[n];
        out.add(new Machine(Kind.CRUSHER, cx, cy, depth, -dx, -dy, n, -1));
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
            // Its back to the station: the face it cuts lies away from the station, so its boom swings back to it.
            float back = (face[0] * (x - cx) + face[1] * (y - cy)) / away;
            if (back < 0f || !dryUnder(field, n, x, y, face[0], face[1], seed)) continue;
            float score = back - away * .02f
                    + .05f * NaturalVoidField.Noise.hash(Math.round(x * 4), Math.round(y * 4), seed + 11);
            if (score > best) { best = score; at = new float[]{x, y, face[0], face[1]}; }
        }
        if (at != null) out.add(new Machine(Kind.CUTTER, at[0], at[1], depth, at[2], at[3], n, -1));
    }

    /**
     * A hopper beside the spine on a bench it climbs over, the middle one first, clear of the haul road, with a loaded ore crawler backed up to tip into
     * it: where the upper benches' ore joins the spine.
     */
    private static void benchHopper(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, NaturalVoid.Belt belt,
                                    int margin, int n, List<Machine> out) {
        for (int step = 0; step < n - 1; step++) {
            int k = 1 + Math.floorMod(n / 2 - 1 + step, n - 1);
            if (hopperOn(field, ramps, belt, margin, k, out)) return;
        }
    }

    private static boolean hopperOn(NaturalVoidField field, List<PitVoidLook.Ramp> ramps, NaturalVoid.Belt belt,
                                    int margin, int k, List<Machine> out) {
        float fx = belt.floorX() + margin, fy = belt.floorY() + margin;
        float dx = belt.rimX() - belt.floorX(), dy = belt.rimY() - belt.floorY();
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return false;
        float ux = dx / len, uy = dy / len;
        for (float t = 0f; t <= len; t += .25f) {
            float x = fx + ux * t, y = fy + uy * t;
            if (!on(field, k, x, y)) continue;
            for (int side = 1; side >= -1; side -= 2) {
                float hx = x - uy * HOPPER_SIDE * side, hy = y + ux * HOPPER_SIDE * side;
                float cx = x - uy * TIPPING_SIDE * side, cy = y + ux * TIPPING_SIDE * side;
                if (!on(field, k, hx, hy) || !on(field, k, cx, cy) || nearRoad(ramps, hx, hy)
                        || nearRoad(ramps, cx, cy)) continue;
                out.add(new Machine(Kind.HOPPER, hx, hy, field.depth[k], ux, uy, k, -1));
                // Backed up to the hopper: its front turned away from it.
                out.add(new Machine(Kind.ORE_LOADED, cx, cy, field.depth[k], -uy * side, ux * side, k, -1));
                return true;
            }
        }
        return false;
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
                if (toDrop < GANTRY_MIN || toDrop > GANTRY_MAX || nearRoad(ramps, x, y) || near(out, x, y, 6f)) continue;
                float score = NaturalVoidField.Noise.hash(Math.round(x * 4), Math.round(y * 4), seed + 23);
                if (score < best) { best = score; at = new float[]{x, y}; }
            }
            if (at == null) continue;
            float[] o0 = outward(field, field.reach[k + 1], at[0], at[1]);
            if (o0 == null) continue;
            out.add(new Machine(Kind.GANTRY, at[0], at[1], field.depth[k], -o0[1], o0[0], k, -1));
            return;
        }
    }

    /**
     * Two ore crawlers on the haul road, on different faces: the deeper one loaded and climbing, the upper one empty
     * and coming down.
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
            out.add(new Machine(loaded ? Kind.ORE_LOADED : Kind.ORE_EMPTY, x, y, z, dx * sign, dy * sign,
                    wall + 1, wall));
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

    private static boolean near(List<Machine> taken, float x, float y, float apart) {
        for (Machine m : taken) if (Math.hypot(m.x() - x, m.y() - y) < apart) return true;
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
