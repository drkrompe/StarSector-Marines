package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout.RampShape;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A look study for raised ground drawn by gradients instead of per-cell tiles:
 * the layout's signed fields become one smooth height surface, and the ground
 * already drawn under it is shaded by that surface's slope against the sun,
 * darkened where it lies at a cliff's foot, lit along a rim, and shadowed by
 * the cast of what stands up-sun of it. Optional scree (stones whose density
 * follows the slope and the foot) and ramp steps (compacted bands across a
 * ramp) carry the grade in texture as well as in tone.
 *
 * <p>CPU only, for picture options: whatever the owner picks would be carried
 * into the ground composite's shader. Everything here is a pure function of
 * the layout and the pixel, so a frame repeats.
 */
final class ElevationGradientLook {

    /** What to draw besides the shaded surface. */
    record Options(boolean rockOnFaces, boolean scree, RampStyle ramp, float rockBlend) { }

    /** How a ramp's floor and the walls of its cut are drawn. */
    enum RampStyle {
        /** The ground's own surface, shaded by the slope alone. */
        NONE,
        /** Compacted bands across the floor. */
        BANDS,
        /**
         * Packed earth graded from dark at the foot to light at the head, with
         * stone walls where the cut meets the cliff, lit and shaded as faces are.
         */
        GRADED,
        /** As {@link #GRADED}, with a worn track of two ruts running up it. */
        TRACK,
        /** Cut stone steps: a shadowed riser and a lit tread every half cell. */
        STAIRS
    }

    private static final float RISE_CELLS = 1.6f;    // tier height in cell units, for slope shading
    private static final float RISE_METERS = MacroReliefField.TIER_RISE_METERS;

    private final ElevationLayout layout;
    private final int width, height, faceWidth;
    private final double[][] fields;
    private final List<RampShape> ramps;
    /** Where along each ramp its corridor enters the plateau: the slope starts there. */
    private final double[] entry;
    private final int southFace;

    ElevationGradientLook(ElevationLayout layout, int faceWidth) {
        this.layout = layout;
        this.width = layout.width();
        this.height = layout.height();
        this.faceWidth = faceWidth;
        this.fields = new double[layout.highestTier()][];
        for (int k = 1; k <= layout.highestTier(); k++) fields[k - 1] = layout.plateauCorners(k);
        this.ramps = layout.ramps();
        this.southFace = layout.southFace();
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
     * Smooth height in tiers at a world point (cells, y up): the plateaus'
     * own fields eased across their faces, with each ramp laid in as a slope
     * that eases from its foot to its head and out through its side walls.
     */
    float tiers(float wx, float wy) {
        float plateau = plateauAt(wx, wy);
        float height = plateau;
        double px = wx - .5, py = wy - .5;
        for (int r = 0; r < ramps.size(); r++) {
            RampShape ramp = ramps.get(r);
            double across = Math.abs(ramp.across(px, py));
            if (across > ramp.half() + 1.2) continue;
            float profile = ramp.lowerTier() + smooth(entry[r] - .3, ramp.length(), ramp.along(px, py));
            float inside = smooth(ramp.half() + .7, ramp.half() - .05, across);
            float cut = Math.min(plateau, profile);
            height = Math.min(height, plateau + (cut - plateau) * inside);
        }
        return height;
    }

    /** How strongly a point lies on a ramp's climbing floor, 0 to 1. */
    private float onRamp(RampShape ramp, double px, double py, float plateau) {
        double along = ramp.along(px, py), across = Math.abs(ramp.across(px, py));
        return smooth(ramp.half() + .2, ramp.half() - .5, across)
                * smooth(ramp.lowerTier() + .1, ramp.lowerTier() + .6, plateau)
                * smooth(-.2, .6, along) * smooth(ramp.length() + .2, ramp.length() - .6, along);
    }

    /**
     * The plateaus alone, eased across their faces; a face looking down the
     * screen eases over its widened band, as the cells that are face there do.
     */
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

    float meters(float wx, float wy) { return tiers(wx, wy) * RISE_METERS; }

    BufferedImage shade(BufferedImage ground, float left, float top, int cellPx, Options options) {
        int w = ground.getWidth(), h = ground.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double az = Math.toRadians(SunLight.DEFAULT_AZIMUTH_DEGREES);
        double el = Math.toRadians(SunLight.DEFAULT_ELEVATION_DEGREES);
        float lx = (float) (Math.cos(az) * Math.cos(el)), ly = (float) (Math.sin(az) * Math.cos(el));
        float lz = (float) Math.sin(el);
        float e = .5f / cellPx;
        float tallest = layout.highestTier() * RISE_METERS;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                float wx = left + (px + .5f) / cellPx, wy = top - (py + .5f) / cellPx;
                float t0 = tiers(wx, wy);
                float gx = (tiers(wx + e * 4, wy) - tiers(wx - e * 4, wy)) / (e * 8) * RISE_CELLS;
                float gy = (tiers(wx, wy + e * 4) - tiers(wx, wy - e * 4)) / (e * 8) * RISE_CELLS;
                float slope = (float) Math.hypot(gx, gy);
                float nl = (float) Math.sqrt(gx * gx + gy * gy + 1);
                float lambert = Math.max(0f, (-gx * lx - gy * ly + lz) / nl) / lz;
                float occlusion = 0f, rim = 0f;
                if (fields.length > 0) {
                    float around = ring(wx, wy, .9f) * .5f + ring(wx, wy, 1.8f) * .5f;
                    occlusion = clamp((around - t0) * 1.6f);
                    rim = clamp((t0 - around) * 2.2f);
                }
                float shadow = GroundSunShadowReference.shadowAt(this::meters, wx, wy,
                        SunLight.DEFAULT_AZIMUTH_DEGREES, SunLight.DEFAULT_ELEVATION_DEGREES,
                        Math.max(tallest, 1f));
                int rgb = ground.getRGB(px, py);
                float r = (rgb >>> 16) & 0xFF, g = (rgb >>> 8) & 0xFF, b = rgb & 0xFF;
                // Steep ground shows its rock.
                float steep = smooth(.5, 1.6, slope);
                if (options.rockOnFaces() && steep > 0f) {
                    float n = noise(wx * 3.1f, wy * 3.1f) * .5f + noise(wx * 9.3f, wy * 9.3f) * .25f;
                    float[] rock = {118 + 30 * n, 106 + 26 * n, 92 + 22 * n};
                    float k = steep * options.rockBlend();
                    r += (rock[0] - r) * k; g += (rock[1] - g) * k; b += (rock[2] - b) * k;
                }
                float light = Math.min(1.25f, .6f + .4f * lambert) * (1f - .38f * occlusion) * (1f + .14f * rim);
                r *= light; g *= light; b *= light;
                if (options.ramp() == RampStyle.BANDS) {
                    float[] step = rampStep(wx, wy);
                    if (step != null) {
                        r = r * step[0] + step[1]; g = g * step[0] + step[1]; b = b * step[0] + step[1];
                    }
                } else if (options.ramp() != RampStyle.NONE) {
                    float[] painted = rampPaint(wx, wy, r, g, b, options.ramp(), lx, ly);
                    if (painted != null) { r = painted[0]; g = painted[1]; b = painted[2]; }
                }
                if (options.scree()) {
                    float[] stone = stone(wx, wy, slope, occlusion, cellPx);
                    if (stone != null) {
                        r = r * (1 - stone[3]) + stone[0] * stone[3];
                        g = g * (1 - stone[3]) + stone[1] * stone[3];
                        b = b * (1 - stone[3]) + stone[2] * stone[3];
                    }
                }
                float k = SunLight.DEFAULT_SHADOW_STRENGTH * shadow;
                r *= lerp(1f, SunLight.TINT_R, k); g *= lerp(1f, SunLight.TINT_G, k); b *= lerp(1f, SunLight.TINT_B, k);
                out.setRGB(px, py, (rgb & 0xFF000000) | channel(r) << 16 | channel(g) << 8 | channel(b));
            }
        }
        return out;
    }

    /**
     * Only the ramps, painted over an image whose cliffs are already drawn
     * (the shipped cliff tiles, sunlit): each ramp's floor and the tapering
     * walls of its cut, nothing else touched.
     */
    BufferedImage paintRamps(BufferedImage image, float left, float top, int cellPx, RampStyle style) {
        double az = Math.toRadians(SunLight.DEFAULT_AZIMUTH_DEGREES);
        double el = Math.toRadians(SunLight.DEFAULT_ELEVATION_DEGREES);
        float lx = (float) (Math.cos(az) * Math.cos(el)), ly = (float) (Math.sin(az) * Math.cos(el));
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int rgb = image.getRGB(px, py);
                float wx = left + (px + .5f) / cellPx, wy = top - (py + .5f) / cellPx;
                float[] painted = rampPaint(wx, wy, (rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, style, lx, ly);
                out.setRGB(px, py, painted == null ? rgb
                        : (rgb & 0xFF000000) | channel(painted[0]) << 16 | channel(painted[1]) << 8 | channel(painted[2]));
            }
        }
        return out;
    }

    /** Mean height on a ring around a point: what the ground nearby stands at. */
    private float ring(float wx, float wy, float radius) {
        float sum = 0f;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            sum += tiers(wx + (float) Math.cos(a) * radius, wy + (float) Math.sin(a) * radius);
        }
        return sum / 8f;
    }

    /**
     * A ramp's grade as compacted bands across it: a dark seam and a lit lip
     * every half cell, fading out at the corridor's sides. Multiply and add.
     */
    private float[] rampStep(float wx, float wy) {
        double px = wx - .5, py = wy - .5;
        float plateau = plateauAt(wx, wy);
        for (RampShape ramp : ramps) {
            float edge = onRamp(ramp, px, py, plateau);
            if (edge <= 0f) continue;
            double along = ramp.along(px, py), across = ramp.across(px, py);
            double phase = along / .45 - Math.floor(along / .45);
            float jitter = noise((float) across * 2.3f, (float) along * .7f) * .1f;
            double seam = phase + jitter < .16 ? -1 : phase + jitter < .32 ? .7 : 0;
            float k = (float) (edge * seam);
            return new float[]{1f + .24f * Math.min(0f, k) + .08f * Math.max(0f, k), 12f * Math.max(0f, k)};
        }
        return null;
    }

    /**
     * A ramp drawn as a slope cut into the cliff. What makes it read as one is
     * the walls: tall at the mouth, where the floor is still low ground and
     * the cliff stands full height beside it, and tapering to nothing at the
     * head, where the floor has climbed to the top. The wall between the floor
     * and the sun throws a shadow across the floor that narrows the same way;
     * the other wall is lit. The floor is a material of its own (packed earth,
     * or cut stone for stairs), darker deep in the cut and lighter as it climbs,
     * fading into the ground at both ends. Null off every ramp.
     */
    private float[] rampPaint(float wx, float wy, float r, float g, float b, RampStyle style,
                              float lx, float ly) {
        double px = wx - .5, py = wy - .5;
        for (int i = 0; i < ramps.size(); i++) {
            RampShape ramp = ramps.get(i);
            double along = ramp.along(px, py), signed = ramp.across(px, py);
            // A ragged edge, so the floor reads as worn ground rather than a laid slab.
            double across = Math.abs(signed) + noise(wx * 3.7f, wy * 3.7f) * .07;
            double half = ramp.half(), length = ramp.length();
            if (across > half + 1.2 || along < entry[i] - 2 || along > length + 1) continue;
            // How far up the cut: 0 at the plateau's edge, 1 at the head.
            float t = smooth(entry[i], length, along);
            float inCut = smooth(entry[i] - .4, entry[i] + .2, along) * smooth(length + .3, length - .3, along);
            // Wall width shrinks as the floor climbs toward the top.
            double wallWidth = (.1 + .7 * (1 - t)) * inCut;
            float floorAt = smooth(half + .04, half - .12, across);
            float ends = smooth(entry[i] - 1.4, entry[i] - .2, along) * smooth(length + 1.1, length + .2, along);
            float floor = floorAt * ends;
            float wall = wallWidth <= .02 ? 0f
                    : smooth(half - .06, half + .04, across) * smooth(half + wallWidth + .05, half + wallWidth - .08, across);
            if (floor <= 0f && wall <= 0f) continue;
            // Which wall faces the sun: the side whose face looks back along the light.
            double side = Math.signum(signed);
            double faceX = ramp.uy() * side, faceY = -ramp.ux() * side;   // from the wall into the floor
            float facing = (float) ((faceX * lx + faceY * ly) / Math.hypot(lx, ly));
            float n = noise(wx * 4.3f, wy * 4.3f) * .5f + noise(wx * 11f, wy * 11f) * .3f;
            float[] mat = style == RampStyle.STAIRS ? new float[]{150 + 14 * n, 141 + 13 * n, 126 + 12 * n}
                    : new float[]{142 + 22 * n, 114 + 18 * n, 82 + 14 * n};
            float grade = lerp(.6f, 1.18f, smooth(entry[i] - 1.2, length, along));
            float fr = mat[0] * grade, fg = mat[1] * grade, fb = mat[2] * grade;
            if (style == RampStyle.TRACK) {
                double rut = Math.min(Math.abs(signed - half * .42), Math.abs(signed + half * .42));
                double wobble = noise((float) along * 1.7f, (float) signed) * .05;
                float groove = smooth(.15, .05, rut + wobble);
                float lip = smooth(.25, .16, rut + wobble) - groove;
                float k = 1f - .32f * groove + .12f * lip;
                fr *= k; fg *= k; fb *= k;
            } else if (style == RampStyle.STAIRS) {
                double pitch = .5, phase = along / pitch - Math.floor(along / pitch);
                float k = phase < .2 ? .6f : phase < .3 ? 1.2f : 1f;
                k = lerp(1f, k, smooth(half + .02, half - .2, across) * inCut);
                fr *= k; fg *= k; fb *= k;
            }
            // The up-sun wall shades the floor beside it, as deep as the wall is tall.
            if (facing < 0f) {
                double reach = (1 - t) * 1.1 * inCut;
                double fromWall = half - across;
                float shade = reach <= .05 ? 0f : smooth(reach, reach * .4, fromWall) * floorAt;
                float k = 1f - .42f * shade * -facing;
                fr *= k; fg *= k; fb *= k;
            }
            float wallLight = facing >= 0f ? .95f + .3f * facing : .55f + .25f * (1f + facing);
            float wn = noise(wx * 6.1f, wy * 6.1f) * .6f;
            float wr = (124 + 26 * wn) * wallLight, wg = (115 + 24 * wn) * wallLight, wb = (103 + 20 * wn) * wallLight;
            // A crisp line where the wall's top meets the cliff top.
            float lip = wallWidth <= .02 ? 0f : smooth(.07, 0, Math.abs(across - half - wallWidth));
            float or = r, og = g, ob = b;
            float fk = floor * .88f;
            or += (fr - or) * fk; og += (fg - og) * fk; ob += (fb - ob) * fk;
            or += (wr - or) * wall; og += (wg - og) * wall; ob += (wb - ob) * wall;
            float k = 1f + (facing >= 0f ? .18f : -.25f) * lip;
            return new float[]{or * k, og * k, ob * k};
        }
        return null;
    }

    /**
     * One stone of the scree, if a stone covers this point: stones sit on a
     * jittered lattice, are kept with a probability that grows with slope and
     * at a cliff's foot, and are lit from the sun's side. RGB and coverage.
     */
    private float[] stone(float wx, float wy, float slope, float occlusion, int cellPx) {
        float spacing = .26f;
        int gx = (int) Math.floor(wx / spacing), gy = (int) Math.floor(wy / spacing);
        float[] best = null;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                int cx = gx + ox, cy = gy + oy;
                long hsh = hash(cx, cy);
                float sx = (cx + .15f + .7f * unit(hsh)) * spacing;
                float sy = (cy + .15f + .7f * unit(hsh >>> 11)) * spacing;
                float keep = unit(hsh >>> 22);
                float hereSlope = slope, hereOcc = occlusion;
                float density = clamp(smooth(.45, 1.5, hereSlope) * .55f + hereOcc * .6f);
                if (keep > density) continue;
                float size = (.045f + .07f * unit(hsh >>> 33)) * (1f + .6f * hereOcc);
                float ax = 1f + .4f * unit(hsh >>> 44);
                float dx = (wx - sx) / (size * ax), dy = (wy - sy) / size;
                float d2 = dx * dx + dy * dy;
                // A short shadow down-sun of each stone.
                float shx = (wx - sx + .035f) / (size * ax), shy = (wy - sy - .035f) / size;
                if (d2 <= 1f) {
                    float lit = clamp(.55f - .45f * (dx * .7f - dy * .7f));
                    float tone = 78 + 52 * lit + 26 * unit(hsh >>> 50);
                    float warm = .86f + .1f * unit(hsh >>> 55);
                    float edge = clamp((1f - d2) * cellPx * size * .9f);
                    best = new float[]{tone, tone * warm, tone * warm * .9f, edge};
                } else if (best == null && shx * shx + shy * shy <= 1f) {
                    best = new float[]{30, 28, 24, .35f};
                }
            }
        }
        return best;
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

    private static int channel(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
}
