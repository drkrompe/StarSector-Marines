package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout.RampShape;

import java.util.List;

/**
 * The look of grass-topped raised ground, as the owner chose it: a layered
 * escarpment (strata ledges along the face, a broken turf lip over exposed
 * soil, a gravel apron feathering into the grass at the foot, every edge
 * wandering below cell scale) and cut stone steps up each ramp, with walls
 * that taper up the cut.
 *
 * <p>Everything is a pure function of the layout, the sun and the point, so
 * it is baked once ({@link CliffArtLayer}) rather than shaded each frame. The
 * ground under it is not known when baking, so a point's look is expressed as
 * what it does to whatever ground is there: {@code ground * (1 - a) + c}, a
 * premultiplied colour over the ground. Every step of the look is a blend
 * toward a colour or a darkening, and both keep that form. Sun shadows are
 * left out: the ground composite casts them from the same heights.
 *
 * <p>Graded ground ({@link ElevationLayout#gradedCorner}, an engineered berm) is
 * drawn as a built bank instead: a turfed slope worn in patches, with no strata,
 * ledges or scree, its edges barely wandering, a clean trodden line along the
 * crest and a drainage ditch along the toe.
 */
final class CliffShading {

    /** Step of the finite differences that measure the slope, in cells. */
    private static final float E = .12f;

    // The grey-browns and scree the owner kept from the look study.
    private static final float[] ROCK_LIGHT = {156, 144, 127}, ROCK_MID = {116, 104, 90};
    private static final float[] SOIL = {104, 80, 56}, SOIL_DARK = {66, 50, 36}, TALUS = {136, 127, 114};
    private static final float[] TURF_DARK = {52, 70, 30};
    // A graded bank: turf over packed earth.
    private static final float[] BANK_TURF = {88, 100, 56}, DITCH = {58, 52, 38};

    private final int width, height, faceWidth, southFace;
    private final double[][] fields;
    private final List<RampShape> ramps;
    /** Where along each ramp its corridor enters the plateau: the slope starts there. */
    private final double[] entry;
    private final float lx, ly;
    /** Texels per cell the look is evaluated at, which sets how soft a stone's edge is. */
    private final int texelsPerCell;
    /** 1 at each cell corner whose raised ground is graded, 0 elsewhere; null when none is. */
    private final double[] graded;

    CliffShading(ElevationLayout layout, SunLight sun, int texelsPerCell) {
        this.width = layout.width();
        this.height = layout.height();
        this.faceWidth = layout.faceWidth();
        this.southFace = layout.southFace();
        this.texelsPerCell = texelsPerCell;
        this.fields = new double[layout.highestTier()][];
        for (int k = 1; k <= layout.highestTier(); k++) fields[k - 1] = layout.plateauCorners(k);
        this.ramps = layout.ramps();
        double[] grade = new double[(width + 1) * (height + 1)];
        boolean any = false;
        for (int y = 0; y <= height; y++) {
            for (int x = 0; x <= width; x++) {
                if (!layout.gradedCorner(x, y)) continue;
                grade[y * (width + 1) + x] = 1;
                any = true;
            }
        }
        this.graded = any ? grade : null;
        double az = Math.toRadians(sun.azimuthDegrees()), el = Math.toRadians(sun.elevationDegrees());
        this.lx = (float) (Math.cos(az) * Math.cos(el));
        this.ly = (float) (Math.sin(az) * Math.cos(el));
        this.entry = new double[ramps.size()];
        for (int r = 0; r < ramps.size(); r++) {
            RampShape ramp = ramps.get(r);
            double at = 0;
            for (double a = 0; a <= ramp.length(); a += .1) {
                at = a;
                float wx = (float) (ramp.footX() + ramp.ux() * a + .5), wy = (float) (ramp.footY() + ramp.uy() * a + .5);
                if (plateauAt(wx, wy) > ramp.lowerTier() + .05) break;
            }
            entry[r] = Math.min(at, ramp.length() - 1);
        }
    }

    /**
     * The look at a world point (cells, y up, cell {@code (x, y)} spanning
     * {@code [x, x + 1]}), into {@code out} as premultiplied red, green, blue
     * and coverage, each 0 to 1. Zero coverage leaves the ground as it is.
     */
    void shade(float wx, float wy, float[] out) {
        float g = gradedAt(wx, wy);
        if (g <= 0f) {
            escarpment(wx, wy, out);
            return;
        }
        if (g >= 1f) {
            bank(wx, wy, out);
            return;
        }
        float[] built = new float[4];
        escarpment(wx, wy, out);
        bank(wx, wy, built);
        for (int i = 0; i < 4; i++) out[i] = lerp(out[i], built[i], g);
    }

    /** The natural look: the layered escarpment. */
    private void escarpment(float wx, float wy, float[] out) {
        // ground * m + (cr, cg, cb), in 0..255 colour units until the end.
        float m = 1f, cr = 0f, cg = 0f, cb = 0f;
        float hgt = warped(wx, wy);
        float gx = (warped(wx + E, wy) - warped(wx - E, wy)) / (2 * E);
        float gy = (warped(wx, wy + E) - warped(wx, wy - E)) / (2 * E);
        float slope = (float) Math.hypot(gx, gy);
        float face = smooth(.1, .38, slope);
        float around = warpedRing(wx, wy, 1.05f);
        float foot = clamp((around - hgt) * 1.8f) * (1f - face);
        float rim = clamp((hgt - around) * 2.4f);
        float dx = slope > 1e-4f ? -gx / slope : 0f, dy = slope > 1e-4f ? -gy / slope : 0f;
        float toSun = (dx * lx + dy * ly) / (float) Math.hypot(lx, ly);
        // Depth below the face's top, in tiers: 0 at the lip, 1 at the foot.
        float below = (float) (Math.ceil(hgt - .02) - hgt);
        float n1 = noise(wx * 2.3f, wy * 2.3f), n2 = noise(wx * 7.7f, wy * 7.7f), n3 = noise(wx * 19f, wy * 19f);

        // The apron: talus feathered into the grass in patches, never along a line.
        if (foot > 0f) {
            float patch = smooth(.15, .55, foot * .85f + n1 * .22f + n2 * .12f);
            float tone = 1f + n2 * .12f + n3 * .06f;
            float k = patch * .85f;
            m *= 1 - k;
            cr = cr * (1 - k) + TALUS[0] * tone * k;
            cg = cg * (1 - k) + TALUS[1] * tone * k;
            cb = cb * (1 - k) + TALUS[2] * tone * k;
            // Grass darkens where it meets the scree.
            float ao = 1f - clamp(foot * 1.2f) * .25f;
            m *= ao; cr *= ao; cg *= ao; cb *= ao;
        }

        // The face: strata that follow the height, so they run along the cliff.
        if (face > 0f) {
            float band = hgt * 5.5f + n1 * .35f + n2 * .12f;
            int id = (int) Math.floor(band);
            float f = band - id;
            float[] c = mix(ROCK_MID, ROCK_LIGHT, unit(hash(id, 31)) * .8f);
            c = mix(c, SOIL, unit(hash(id, 57)) * .35f);
            float ledge = f > .86f ? 1.22f : f < .14f ? .62f : 1f;
            float crack = smooth(.9, .97, 1 - Math.abs(noise(wx * 5f * Math.abs(dy) + wy * 5f * Math.abs(dx), band * .3f)));
            float grain = 1f + n3 * .08f;
            float light = (.62f + .5f * Math.max(-.3f, toSun)) * lerp(1.12f, .78f, smooth(0, 1, below));
            float s = ledge * grain * (1f - .4f * crack) * light;
            m *= 1 - face;
            cr = cr * (1 - face) + c[0] * s * face;
            cg = cg * (1 - face) + c[1] * s * face;
            cb = cb * (1 - face) + c[2] * s * face;
        }

        // The lip: exposed soil under a ragged turf edge, the turf overhanging in tufts.
        if (face > .05f && below < .45f) {
            float edge = .12f + n2 * .07f + Math.max(0, n3) * .06f;
            float soilDepth = edge + .12f + n1 * .05f;
            if (below < edge) {
                // Turf hanging over the lip, its underside in shadow: the ground itself, darkened.
                float shade = smooth(edge - .05f, edge, below) * .7f;
                m = .95f * (1 - shade);
                cr = TURF_DARK[0] * shade; cg = TURF_DARK[1] * shade; cb = TURF_DARK[2] * shade;
            } else if (below < soilDepth) {
                float root = smooth(.9, .97, 1 - Math.abs(noise(wx * 3f, wy * 3f + 3f + below * 40f)));
                float[] soil = mix(SOIL, SOIL_DARK, smooth(edge, soilDepth, below) * .6f + root * .4f);
                float k = smooth(soilDepth, soilDepth - .04f, below) * face;
                m *= 1 - k;
                cr = cr * (1 - k) + soil[0] * k; cg = cg * (1 - k) + soil[1] * k; cb = cb * (1 - k) + soil[2] * k;
            }
        }

        float[] stone = null;
        // A few rocks breaking through the turf just back from the rim, half sunk in it.
        if (rim > 0f && face < .5f) {
            stone = scree(wx, wy, smooth(.3, .8, rim) * .14f, .8f, 1f, 101);
            if (stone != null) stone[3] *= smooth(-.3, .2, n3);
        }
        if (stone != null) {
            float k = stone[3];
            m *= 1 - k;
            cr = cr * (1 - k) + stone[0] * k; cg = cg * (1 - k) + stone[1] * k; cb = cb * (1 - k) + stone[2] * k;
        }
        // Stones on the apron: bigger near the face, finer farther out.
        if (foot > 0f) {
            stone = scree(wx, wy, clamp(foot * 1.8f + n1 * .15f) * .7f, 1.1f, .6f + foot, 7);
            if (stone != null) {
                float k = stone[3];
                m *= 1 - k;
                cr = cr * (1 - k) + stone[0] * k; cg = cg * (1 - k) + stone[1] * k; cb = cb * (1 - k) + stone[2] * k;
            }
        }

        finish(wx, wy, m, cr, cg, cb, out);
    }

    /**
     * The built look: a graded bank. The same height surface and lighting as the escarpment, but turf over packed
     * earth worn in broad patches where the escarpment shows strata, a clean crest, and a ditch along the toe in
     * place of the talus apron.
     */
    private void bank(float wx, float wy, float[] out) {
        float m = 1f, cr = 0f, cg = 0f, cb = 0f;
        float hgt = warped(wx, wy);
        float gx = (warped(wx + E, wy) - warped(wx - E, wy)) / (2 * E);
        float gy = (warped(wx, wy + E) - warped(wx, wy - E)) / (2 * E);
        float slope = (float) Math.hypot(gx, gy);
        float face = smooth(.1, .38, slope);
        float around = warpedRing(wx, wy, 1.05f);
        float foot = clamp((around - hgt) * 1.8f) * (1f - face);
        float rim = clamp((hgt - around) * 2.4f);
        float dx = slope > 1e-4f ? -gx / slope : 0f, dy = slope > 1e-4f ? -gy / slope : 0f;
        float toSun = (dx * lx + dy * ly) / (float) Math.hypot(lx, ly);
        float below = (float) (Math.ceil(hgt - .02) - hgt);
        float n1 = noise(wx * 1.3f, wy * 1.3f), n2 = noise(wx * 5.3f, wy * 5.3f), n3 = noise(wx * 17f, wy * 17f);

        // The toe: a ditch cut along the foot of the bank, wet soil in a straight dark line, the grass trodden by it.
        if (foot > 0f) {
            float ditch = smooth(.3, .5, foot) * smooth(.95, .7, foot) * .55f;
            m *= 1 - ditch;
            cr = cr * (1 - ditch) + DITCH[0] * ditch;
            cg = cg * (1 - ditch) + DITCH[1] * ditch;
            cb = cb * (1 - ditch) + DITCH[2] * ditch;
            float ao = 1f - clamp(foot * 1.2f) * .12f;
            m *= ao; cr *= ao; cg *= ao; cb *= ao;
        }

        // The bank: graded earth turfed over, worn to soil in broad patches, lit by its bearing; no ledges.
        if (face > 0f) {
            float worn = smooth(.25, .75, n1 * .5f + .5f) * .4f + Math.max(0f, n2) * .12f;
            float[] c = mix(BANK_TURF, SOIL, worn);
            float light = (.68f + .5f * Math.max(-.3f, toSun)) * lerp(1.05f, .88f, smooth(0, 1, below));
            float s = light * (1f + n3 * .05f);
            m *= 1 - face;
            cr = cr * (1 - face) + c[0] * s * face;
            cg = cg * (1 - face) + c[1] * s * face;
            cb = cb * (1 - face) + c[2] * s * face;
        }

        // The crest: a clean graded edge, a thin trodden line of earth along the top.
        if (rim > 0f && face < .6f) {
            float line = smooth(.12, .35, rim) * smooth(.9, .55, rim) * (.3f + .08f * n2);
            m *= 1 - line;
            cr = cr * (1 - line) + SOIL[0] * line;
            cg = cg * (1 - line) + SOIL[1] * line;
            cb = cb * (1 - line) + SOIL[2] * line;
        }
        finish(wx, wy, m, cr, cg, cb, out);
    }

    /** Lays the ramps' steps over a look and writes it out as premultiplied colour and coverage. */
    private void finish(float wx, float wy, float m, float cr, float cg, float cb, float[] out) {
        float[] step = steps(wx, wy);
        if (step != null) {
            // Floor over the ground, wall over that, then the lip's line as a scale.
            float fk = step[3], wall = step[7], s = step[8];
            m *= 1 - fk;
            cr = cr * (1 - fk) + step[0] * fk; cg = cg * (1 - fk) + step[1] * fk; cb = cb * (1 - fk) + step[2] * fk;
            m *= 1 - wall;
            cr = cr * (1 - wall) + step[4] * wall; cg = cg * (1 - wall) + step[5] * wall; cb = cb * (1 - wall) + step[6] * wall;
            m *= s; cr *= s; cg *= s; cb *= s;
        }

        // A brightening (m above one) cannot be laid over the ground; it is rare and small, so it is dropped.
        float a = clamp(1f - m);
        out[0] = Math.min(a, Math.max(0f, cr / 255f));
        out[1] = Math.min(a, Math.max(0f, cg / 255f));
        out[2] = Math.min(a, Math.max(0f, cb / 255f));
        out[3] = a;
    }

    /** How graded the raised ground nearest a world point is, 0 natural to 1 built, eased across a cell. */
    private float gradedAt(float wx, float wy) {
        return graded == null ? 0f : (float) bilinear(graded, wx, wy);
    }

    /**
     * How much of a point is bare rock, 0 to 1: the face's share of what {@link #shade} lays over the ground there,
     * so the turf hanging over the lip and the apron at the foot count for nothing, and a ramp's walls count in
     * full. A ramp's floor counts for nothing however steep it is: creep runs up the steps between the bare walls,
     * which marks the way up. What creep stands back from ({@link CliffFaceMask}).
     */
    float faceCover(float wx, float wy, float[] scratch) {
        float gx = (warped(wx + E, wy) - warped(wx - E, wy)) / (2 * E);
        float gy = (warped(wx, wy + E) - warped(wx, wy - E)) / (2 * E);
        float face = smooth(.1, .38, Math.hypot(gx, gy));
        float rock = 0f;
        if (face > 0f) {
            shade(wx, wy, scratch);
            rock = face * scratch[3];
        }
        float[] step = steps(wx, wy);
        if (step == null) return rock;
        // The floor carries .88 coverage at most; under it the slope is the ramp's, not a face's.
        float floor = Math.min(1f, step[3] / .88f);
        return Math.max(rock * (1f - floor), step[7]);
    }

    /**
     * Whether a cell can show any of the look: it lies within reach of a face
     * or a ramp. Cells farther out bake to nothing and need not be evaluated.
     */
    static boolean[] reach(ElevationLayout layout, int margin) {
        int w = layout.width(), h = layout.height();
        boolean[] near = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!layout.isFace(x, y) && !layout.isRamp(x, y)) continue;
                for (int j = Math.max(0, y - margin); j <= Math.min(h - 1, y + margin); j++) {
                    for (int i = Math.max(0, x - margin); i <= Math.min(w - 1, x + margin); i++) near[j * w + i] = true;
                }
            }
        }
        return near;
    }

    // ---- the height surface --------------------------------------------------

    /** Smooth height in tiers: the plateaus eased across their faces, each ramp laid in as a slope. */
    float tiers(float wx, float wy) {
        float plateau = plateauAt(wx, wy);
        float h = plateau;
        double px = wx - .5, py = wy - .5;
        for (int r = 0; r < ramps.size(); r++) {
            RampShape ramp = ramps.get(r);
            double across = Math.abs(ramp.across(px, py));
            if (across > ramp.half() + 1.2) continue;
            float profile = ramp.lowerTier() + smooth(entry[r] - .3, ramp.length(), ramp.along(px, py));
            float inside = smooth(ramp.half() + .7, ramp.half() - .05, across);
            float cut = Math.min(plateau, profile);
            h = Math.min(h, plateau + (cut - plateau) * inside);
        }
        return h;
    }

    /** The plateaus alone; a face looking down the screen eases over its widened band. */
    private float plateauAt(float wx, float wy) {
        float sum = 0f;
        for (double[] field : fields) {
            double f = bilinear(field, wx, wy);
            double band = faceWidth;
            if (southFace > 0 && f > -1 && f < faceWidth + southFace + 1) {
                double gx = bilinear(field, wx + .5f, wy) - bilinear(field, wx - .5f, wy);
                double gy = bilinear(field, wx, wy + .5f) - bilinear(field, wx, wy - .5f);
                double length = Math.hypot(gx, gy);
                if (length > 1e-6) band += southFace * Math.max(0, gy / length);
            }
            sum += smooth(-.15, band + .25, f);
        }
        return sum;
    }

    /**
     * The height surface sampled through a noise warp, so every edge wanders below cell scale; a graded bank's
     * edges barely wander, as a built one's would not.
     */
    private float warped(float wx, float wy) {
        float ox = noise(wx * 1.15f + 17.3f, wy * 1.15f) * .3f + noise(wx * 4.1f, wy * 4.1f + 5.1f) * .1f;
        float oy = noise(wx * 1.15f, wy * 1.15f + 41.7f) * .3f + noise(wx * 4.1f + 9.4f, wy * 4.1f) * .1f;
        if (graded != null) {
            float calm = 1f - .85f * gradedAt(wx, wy);
            ox *= calm;
            oy *= calm;
        }
        return tiers(wx + ox, wy + oy);
    }

    /**
     * Mean warped height around a point: sixteen bearings at three radii. Eight
     * samples of a step quantise into nine levels, and every threshold on them
     * drew a contour line at the foot.
     */
    private float warpedRing(float wx, float wy, float radius) {
        float sum = 0f;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 + .2;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            sum += warped(wx + c * radius * .6f, wy + s * radius * .6f)
                    + warped(wx + c * radius, wy + s * radius)
                    + warped(wx + c * radius * 1.4f, wy + s * radius * 1.4f);
        }
        return sum / 48f;
    }

    private double bilinear(double[] corners, float wx, float wy) {
        int vw = width + 1;
        float fx = Math.max(0f, Math.min(width - .001f, wx)), fy = Math.max(0f, Math.min(height - .001f, wy));
        int i = (int) fx, j = (int) fy;
        float tx = fx - i, ty = fy - j;
        double a = corners[j * vw + i], b = corners[j * vw + i + 1];
        double c = corners[(j + 1) * vw + i], d = corners[(j + 1) * vw + i + 1];
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
    }

    // ---- ramps -----------------------------------------------------------------

    /**
     * Cut stone steps up a ramp, or null off every ramp: floor colour and
     * coverage, wall colour and coverage, and the scale the wall's top line
     * applies. The walls are tall at the mouth, where the cliff stands full
     * height beside the floor, and taper to nothing at the head; the wall
     * between the floor and the sun shades the floor beside it. The steps are
     * rough-hewn: each riser wanders, the spacing drifts, every step is its own
     * stone, and the nosings are worn and chipped.
     */
    private float[] steps(float wx, float wy) {
        double px = wx - .5, py = wy - .5;
        for (int i = 0; i < ramps.size(); i++) {
            RampShape ramp = ramps.get(i);
            double along = ramp.along(px, py), signed = ramp.across(px, py);
            double across = Math.abs(signed) + noise(wx * 3.7f, wy * 3.7f) * .07;
            double half = ramp.half(), length = ramp.length();
            if (across > half + 1.2 || along < entry[i] - 2 || along > length + 1) continue;
            float t = smooth(entry[i], length, along);
            float inCut = smooth(entry[i] - .4, entry[i] + .2, along) * smooth(length + .3, length - .3, along);
            double wallWidth = (.1 + .7 * (1 - t)) * inCut;
            float floorAt = smooth(half + .04, half - .12, across);
            float ends = smooth(entry[i] - 1.4, entry[i] - .2, along) * smooth(length + 1.1, length + .2, along);
            float floor = floorAt * ends;
            float wall = wallWidth <= .02 ? 0f
                    : smooth(half - .06, half + .04, across) * smooth(half + wallWidth + .05, half + wallWidth - .08, across);
            if (floor <= 0f && wall <= 0f) continue;
            double side = Math.signum(signed);
            double faceX = ramp.uy() * side, faceY = -ramp.ux() * side;
            float facing = (float) ((faceX * lx + faceY * ly) / Math.hypot(lx, ly));
            float n = noise(wx * 4.3f, wy * 4.3f) * .5f + noise(wx * 11f, wy * 11f) * .3f;
            float grade = lerp(.6f, 1.18f, smooth(entry[i] - 1.2, length, along));
            float fr = (126 + 14 * n) * grade, fg = (115 + 13 * n) * grade, fb = (101 + 12 * n) * grade;
            double bent = along + noise((float) signed * 2.2f, (float) along * 1.9f + i * 5f) * .07
                    + noise((float) signed * .8f + 4f, (float) along * 2.3f) * .05
                    + noise((float) along * 1.1f + 11f, i * 3f) * .16 + noise(wx * 9f, wy * 9f) * .015;
            double stepsUp = bent / .5;
            int id = (int) Math.floor(stepsUp);
            double phase = stepsUp - id;
            float worn = smooth(-.45, .05, noise((float) signed * 2.4f + id * 1.7f, id * 2.3f) + noise(wx * 7f, wy * 7f) * .25f);
            float riser = smooth(.0, .05, phase) * smooth(.24, .14, phase);
            float nosing = smooth(.22, .28, phase) * smooth(.4, .3, phase);
            float k = 1f - .38f * riser * (.12f + .88f * worn) + .14f * nosing * worn;
            k *= 1f + (unit(hash(id, 97 + i)) - .5f) * .14f + noise(wx * 6f, wy * 6f) * .04f;
            k = lerp(1f, k, smooth(half + .02, half - .2, across) * inCut);
            fr *= k; fg *= k; fb *= k;
            if (facing < 0f) {
                double reach = (1 - t) * 1.1 * inCut;
                double fromWall = half - across;
                float shade = reach <= .05 ? 0f : smooth(reach, reach * .4, fromWall) * floorAt;
                float s = 1f - .42f * shade * -facing;
                fr *= s; fg *= s; fb *= s;
            }
            float wallLight = facing >= 0f ? .95f + .3f * facing : .55f + .25f * (1f + facing);
            float wn = noise(wx * 6.1f, wy * 6.1f) * .6f;
            float lip = wallWidth <= .02 ? 0f : smooth(.07, 0, Math.abs(across - half - wallWidth));
            // A darkening line where the up-sun wall's top meets the cliff top; the lit wall's line is dropped.
            float scale = facing >= 0f ? 1f : 1f - .25f * lip;
            return new float[]{fr, fg, fb, floor * .88f,
                    (124 + 26 * wn) * wallLight, (115 + 24 * wn) * wallLight, (103 + 20 * wn) * wallLight, wall, scale};
        }
        return null;
    }

    // ---- scree -----------------------------------------------------------------

    /**
     * One stone of a scatter, if one covers the point: kept with probability
     * {@code density}, {@code grow} swelling the stones without moving the
     * lattice (a spacing that varied per point read a different grid at every
     * point and drew hairline stripes). RGB and coverage.
     */
    private float[] scree(float wx, float wy, float density, float sizeScale, float grow, int salt) {
        if (density <= 0f) return null;
        float spacing = .24f * Math.max(1f, sizeScale * .8f);
        int gx = (int) Math.floor(wx / spacing), gy = (int) Math.floor(wy / spacing);
        float[] best = null;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                long hsh = hash(gx + ox + salt * 7919, gy + oy - salt * 104729);
                if (unit(hsh >>> 22) > density) continue;
                float sx = (gx + ox + .15f + .7f * unit(hsh)) * spacing, sy = (gy + oy + .15f + .7f * unit(hsh >>> 11)) * spacing;
                float size = (.04f + .07f * unit(hsh >>> 33)) * sizeScale * Math.min(grow, 1.3f);
                float ax = 1f + .5f * unit(hsh >>> 44);
                float ddx = (wx - sx) / (size * ax), ddy = (wy - sy) / size;
                float d2 = ddx * ddx + ddy * ddy;
                float shx = (wx - sx + size * .35f) / (size * ax), shy = (wy - sy - size * .35f) / size;
                if (d2 <= 1f) {
                    float lit = clamp(.55f - .45f * (ddx * .7f - ddy * .7f));
                    float[] base = mix(ROCK_MID, ROCK_LIGHT, unit(hsh >>> 50));
                    float tone = .7f + .55f * lit;
                    float edge = clamp((1f - d2) * texelsPerCell * size * .9f);
                    best = new float[]{base[0] * tone, base[1] * tone, base[2] * tone, edge};
                } else if (best == null && shx * shx + shy * shy <= 1f) {
                    best = new float[]{34, 30, 26, .4f};
                }
            }
        }
        return best;
    }

    // ---- arithmetic --------------------------------------------------------------

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }

    private static float smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return (float) (t * t * (3 - 2 * t));
    }

    private static float noise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float tx = x - ix, ty = y - iy;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        float a = unit(hash(ix, iy)), b = unit(hash(ix + 1, iy));
        float c = unit(hash(ix, iy + 1)), d = unit(hash(ix + 1, iy + 1));
        return ((a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty) * 2f - 1f;
    }

    private static long hash(int x, int y) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    private static float unit(long bits) { return (bits & 0x7FF) / 2047f; }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
