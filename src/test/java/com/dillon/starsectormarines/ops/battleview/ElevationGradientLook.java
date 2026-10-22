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

    /** Three treatments of a cliff's face, its lip at the top and its apron at the foot. */
    enum EdgeStyle {
        /** Strata ledges across the face; a broken turf lip over exposed soil; a gravel apron. */
        LAYERED,
        /** Jointed rock blocks; rocks breaking through the turf at the rim; boulders at the foot. */
        BOULDER,
        /** Gullies running down an earthy face; grass draping over the lip; scree fans below. */
        ERODED
    }

    // A's grey-brown rock and C's scree, the tones the owner kept.
    private static final float[] ROCK_LIGHT = {156, 144, 127}, ROCK_MID = {116, 104, 90}, ROCK_DARK = {70, 61, 52};
    private static final float[] SOIL = {104, 80, 56}, SOIL_DARK = {66, 50, 36}, TALUS = {136, 127, 114};
    private static final float[] TURF_DARK = {52, 70, 30};

    /** The height surface sampled through a noise warp, so every edge wanders below cell scale. */
    private float warped(float wx, float wy) {
        float ox = noise(wx * 1.15f + 17.3f, wy * 1.15f) * .3f + noise(wx * 4.1f, wy * 4.1f + 5.1f) * .1f;
        float oy = noise(wx * 1.15f, wy * 1.15f + 41.7f) * .3f + noise(wx * 4.1f + 9.4f, wy * 4.1f) * .1f;
        return tiers(wx + ox, wy + oy);
    }

    private float warpedRing(float wx, float wy, float radius) {
        // Sixteen bearings at three radii: eight samples of a step quantise into
        // nine levels, and every threshold on them drew a contour line at the foot.
        float sum = 0f;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 + .2;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            sum += warped(wx + c * radius * .6f, wy + sn * radius * .6f)
                    + warped(wx + c * radius, wy + sn * radius)
                    + warped(wx + c * radius * 1.4f, wy + sn * radius * 1.4f);
        }
        return sum / 48f;
    }

    /**
     * The cliffs drawn as rock and turf over a flat ground image: a face broken
     * by its style's structure and lit by its bearing, a ragged lip where the
     * top's turf ends, an apron of talus and stones feathering into the low
     * ground, the plateau's shadow, and the ramps' floors in {@code ramp}.
     */
    BufferedImage shadeEdges(BufferedImage ground, float left, float top, int cellPx, EdgeStyle style, RampStyle ramp) {
        int w = ground.getWidth(), h = ground.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double az = Math.toRadians(SunLight.DEFAULT_AZIMUTH_DEGREES);
        double el = Math.toRadians(SunLight.DEFAULT_ELEVATION_DEGREES);
        float lx = (float) (Math.cos(az) * Math.cos(el)), ly = (float) (Math.sin(az) * Math.cos(el));
        float lz = (float) Math.sin(el);
        float tallest = Math.max(1f, layout.highestTier() * RISE_METERS);
        float e = .12f;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                float wx = left + (px + .5f) / cellPx, wy = top - (py + .5f) / cellPx;
                int rgb = ground.getRGB(px, py);
                float r = (rgb >>> 16) & 0xFF, g = (rgb >>> 8) & 0xFF, b = rgb & 0xFF;
                float hgt = warped(wx, wy);
                float gx = (warped(wx + e, wy) - warped(wx - e, wy)) / (2 * e);
                float gy = (warped(wx, wy + e) - warped(wx, wy - e)) / (2 * e);
                float slope = (float) Math.hypot(gx, gy);
                float face = smooth(.1, .38, slope);
                float around = warpedRing(wx, wy, 1.05f);
                float foot = clamp((around - hgt) * 1.8f) * (1f - face);
                float rim = clamp((hgt - around) * 2.4f);
                // Downhill, the way the face looks out.
                float dx = slope > 1e-4f ? -gx / slope : 0f, dy = slope > 1e-4f ? -gy / slope : 0f;
                float toSun = (dx * lx + dy * ly) / (float) Math.hypot(lx, ly);
                // Depth below the face's top, in tiers: 0 at the lip, 1 at the foot.
                float below = (float) (Math.ceil(hgt - .02) - hgt);
                float n1 = noise(wx * 2.3f, wy * 2.3f), n2 = noise(wx * 7.7f, wy * 7.7f), n3 = noise(wx * 19f, wy * 19f);

                // The apron: talus feathered into the grass in patches, never along a line.
                if (foot > 0f) {
                    float reach = style == EdgeStyle.ERODED ? 1.15f : style == EdgeStyle.BOULDER ? .95f : .85f;
                    float patch = smooth(.15, .55, foot * reach + n1 * .22f + n2 * .12f);
                    float[] talus = style == EdgeStyle.ERODED ? mix(TALUS, SOIL, .45f) : TALUS;
                    float tone = 1f + n2 * .12f + n3 * .06f;
                    r += (talus[0] * tone - r) * patch * .85f;
                    g += (talus[1] * tone - g) * patch * .85f;
                    b += (talus[2] * tone - b) * patch * .85f;
                    // Grass darkens where it meets the scree.
                    float ao = clamp(foot * 1.2f) * .25f;
                    r *= 1 - ao; g *= 1 - ao; b *= 1 - ao;
                }

                // The face.
                if (face > 0f) {
                    float[] c = faceColour(style, wx, wy, hgt, below, dx, dy, n1, n2, n3);
                    float light = .62f + .5f * Math.max(-.3f, toSun);
                    // Darker toward the foot, catching light toward the top.
                    light *= lerp(1.12f, .78f, smooth(0, 1, below));
                    float k = face;
                    r += (c[0] * light - r) * k; g += (c[1] * light - g) * k; b += (c[2] * light - b) * k;
                }

                // The lip: exposed soil under a ragged turf edge, the turf overhanging in tufts.
                if (face > .05f && below < .45f) {
                    float tuft = style == EdgeStyle.ERODED ? .2f : style == EdgeStyle.BOULDER ? .07f : .12f;
                    float edge = tuft + n2 * .07f + Math.max(0, n3) * .06f;
                    float soilDepth = edge + (style == EdgeStyle.BOULDER ? .06f : .12f) + n1 * .05f;
                    if (below < edge) {
                        // Turf hanging over the lip, its underside in shadow.
                        float shade = smooth(edge - .05f, edge, below);
                        float[] turf = {lerp(r, TURF_DARK[0], .1f), lerp(g, TURF_DARK[1], .1f), lerp(b, TURF_DARK[2], .1f)};
                        float base = (rgb >>> 16) & 0xFF, bg = (rgb >>> 8) & 0xFF, bb = rgb & 0xFF;
                        r = lerp(base * .95f, TURF_DARK[0], shade * .7f);
                        g = lerp(bg * .95f, TURF_DARK[1], shade * .7f);
                        b = lerp(bb * .95f, TURF_DARK[2], shade * .7f);
                    } else if (below < soilDepth) {
                        // Soil and roots.
                        float root = smooth(.9, .97, 1 - Math.abs(noise(wx * 3f, wy * 3f + 3f + below * 40f)));
                        float[] soil = mix(SOIL, SOIL_DARK, smooth(edge, soilDepth, below) * .6f + root * .4f);
                        float k = smooth(soilDepth, soilDepth - .04f, below) * face;
                        r += (soil[0] - r) * k; g += (soil[1] - g) * k; b += (soil[2] - b) * k;
                    }
                }

                // Rocks breaking through the turf just back from the rim.
                if (rim > 0f && face < .5f) {
                    float density = style == EdgeStyle.BOULDER ? .22f : style == EdgeStyle.LAYERED ? .14f : .06f;
                    float[] stone = scree(wx, wy, smooth(.3, .8, rim) * density, style == EdgeStyle.BOULDER ? 1.3f : .8f, 1f, cellPx, 101);
                    if (stone != null) {
                        // Half-sunk in the turf: grass laps over their edges.
                        float k = stone[3] * smooth(-.3, .2, n3);
                        r += (stone[0] - r) * k; g += (stone[1] - g) * k; b += (stone[2] - b) * k;
                    }
                }
                // Stones on the apron: bigger near the face, finer farther out.
                if (foot > 0f) {
                    float density = style == EdgeStyle.BOULDER ? .95f : style == EdgeStyle.ERODED ? .55f : .7f;
                    float size = style == EdgeStyle.BOULDER ? 2.2f : 1.1f;
                    float[] stone = scree(wx, wy, clamp(foot * 1.8f + n1 * .15f) * density, size, .6f + foot, cellPx, 7);
                    if (stone != null) {
                        r += (stone[0] - r) * stone[3]; g += (stone[1] - g) * stone[3]; b += (stone[2] - b) * stone[3];
                    }
                }

                if (ramp != RampStyle.NONE && ramp != RampStyle.BANDS) {
                    // The floor only, its edges wandering like the cliff's: the cut's walls are face already.
                    float ox = noise(wx * 1.6f + 3.3f, wy * 1.6f) * .22f + n2 * .06f;
                    float oy = noise(wx * 1.6f, wy * 1.6f + 8.8f) * .22f + n3 * .04f;
                    // Cut steps are masonry: square to the cut, with their walls, as the owner liked them.
                    boolean steps = ramp == RampStyle.STAIRS;
                    float[] painted = steps ? rampPaint(wx, wy, r, g, b, ramp, lx, ly, true)
                            : rampPaint(wx + ox, wy + oy, r, g, b, ramp, lx, ly, false);
                    if (painted != null) { r = painted[0]; g = painted[1]; b = painted[2]; }
                }
                float shadow = GroundSunShadowReference.shadowAt((x, y) -> warped(x, y) * RISE_METERS, wx, wy,
                        SunLight.DEFAULT_AZIMUTH_DEGREES, SunLight.DEFAULT_ELEVATION_DEGREES, tallest);
                float k = SunLight.DEFAULT_SHADOW_STRENGTH * shadow;
                r *= lerp(1f, SunLight.TINT_R, k); g *= lerp(1f, SunLight.TINT_G, k); b *= lerp(1f, SunLight.TINT_B, k);
                out.setRGB(px, py, (rgb & 0xFF000000) | channel(r) << 16 | channel(g) << 8 | channel(b));
            }
        }
        return out;
    }

    /** The face's rock at a point: structure by style, in A's grey-browns. */
    private float[] faceColour(EdgeStyle style, float wx, float wy, float hgt, float below,
                               float dx, float dy, float n1, float n2, float n3) {
        switch (style) {
            case LAYERED: {
                // Strata follow the height, so they run along the cliff; each band its own tone.
                float band = hgt * 5.5f + n1 * .35f + n2 * .12f;
                int id = (int) Math.floor(band);
                float f = band - id;
                float[] c = mix(ROCK_MID, ROCK_LIGHT, unit(hash(id, 31)) * .8f);
                c = mix(c, SOIL, unit(hash(id, 57)) * .35f);
                float ledge = f > .86f ? 1.22f : f < .14f ? .62f : 1f;
                float crack = smooth(.9, .97, 1 - Math.abs(noise(wx * 5f * Math.abs(dy) + wy * 5f * Math.abs(dx), band * .3f)));
                float grain = 1f + n3 * .08f;
                return scale(c, ledge * grain * (1f - .4f * crack));
            }
            case BOULDER: {
                // Jointed blocks: the nearest of a jittered lattice, its edges dark, its sun side lit.
                float cell = .56f;
                int gx = (int) Math.floor(wx / cell), gy = (int) Math.floor(wy / cell);
                float best = 9f, second = 9f;
                long id = 0;
                float bx = 0, by = 0;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        long hsh = hash(gx + ox, gy + oy);
                        float sx = (gx + ox + .1f + .8f * unit(hsh)) * cell, sy = (gy + oy + .1f + .8f * unit(hsh >>> 11)) * cell;
                        float d = (float) Math.hypot(wx - sx, wy - sy);
                        if (d < best) { second = best; best = d; id = hsh; bx = wx - sx; by = wy - sy; }
                        else if (d < second) second = d;
                    }
                }
                float[] c = mix(ROCK_MID, ROCK_LIGHT, unit(id >>> 22) * .9f);
                c = mix(c, ROCK_DARK, unit(id >>> 33) * .3f);
                float joint = smooth(.06, .0, second - best);
                // Each block is a rounded lump: lit on its sun side, shaded on the side that faces down the cliff.
                float lumpX = bx / (cell * .5f), lumpY = by / (cell * .5f);
                float facet = 1f + clamp(-lumpX * .7f + lumpY * .7f) * .3f - clamp(lumpX * .7f - lumpY * .7f) * .25f
                        - clamp(lumpX * dx + lumpY * dy) * .2f;
                float stain = smooth(.2, .6, noise(wx * 1.4f + 2f, wy * 1.4f)) * .25f;
                c = mix(c, SOIL_DARK, stain);
                return scale(mix(c, ROCK_DARK, joint * .85f), facet * (1f + n3 * .07f + n2 * .05f));
            }
            default: {
                // Gullies run downhill: ridges of a noise read across the face's bearing.
                float across = wx * -dy + wy * dx;
                float gully = 1 - Math.abs(noise(across * 2.6f, hgt * 1.5f) * 2 - 0) ;
                float[] c = mix(SOIL, ROCK_MID, .45f + n1 * .25f);
                c = mix(c, ROCK_LIGHT, clamp(noise(across * 1.3f, 3f) * .5f + .2f) * .4f);
                float runnel = smooth(.82, .96, gully) * smooth(.1f, .5f, below);
                float streak = 1f + noise(across * 9f, hgt * .5f) * .08f + n3 * .05f;
                return scale(mix(c, SOIL_DARK, runnel * .65f), streak);
            }
        }
    }

    /**
     * One stone of a scatter, if one covers the point: kept with probability {@code density},
     * {@code grow} swelling the stones without moving the lattice. RGB and coverage.
     */
    private float[] scree(float wx, float wy, float density, float sizeScale, float grow, int cellPx, int salt) {
        if (density <= 0f) return null;
        // The lattice is fixed by sizeScale alone: a spacing that varied per pixel read a
        // different grid at every pixel and drew hairline stripes along the foot.
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
                    float edge = clamp((1f - d2) * cellPx * size * .9f);
                    best = new float[]{base[0] * tone, base[1] * tone, base[2] * tone, edge};
                } else if (best == null && shx * shx + shy * shy <= 1f) {
                    best = new float[]{34, 30, 26, .4f};
                }
            }
        }
        return best;
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }

    private static float[] scale(float[] c, float k) { return new float[]{c[0] * k, c[1] * k, c[2] * k}; }

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
        return rampPaint(wx, wy, r, g, b, style, lx, ly, true);
    }

    private float[] rampPaint(float wx, float wy, float r, float g, float b, RampStyle style,
                              float lx, float ly, boolean walls) {
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
            float floorAt = walls ? smooth(half + .04, half - .12, across) : smooth(half + .02, half - .3, across);
            float ends = smooth(entry[i] - 1.4, entry[i] - .2, along) * smooth(length + 1.1, length + .2, along);
            if (!walls) {
                // No straight sides or ends: the floor's outline wanders a third of a cell either way.
                double ragged = Math.abs(signed) + noise(wx * 1.3f + 4f, wy * 1.3f) * .3 + noise(wx * 5.2f, wy * 5.2f) * .09;
                floorAt = smooth(half + .2, half - .35, ragged);
                double wander = along + noise(wx * 1.1f, wy * 1.1f + 6f) * .7 + noise(wx * 4.6f + 1f, wy * 4.6f) * .15;
                ends = smooth(entry[i] - 1.7, entry[i] - .1, wander) * smooth(length + 1.3, length - .1, wander);
            }
            float floor = floorAt * ends;
            float wall = !walls || wallWidth <= .02 ? 0f
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
            float lip = !walls || wallWidth <= .02 ? 0f : smooth(.07, 0, Math.abs(across - half - wallWidth));
            float or = r, og = g, ob = b;
            float fk = floor * .88f;
            if (!walls) {
                // Feathered in patches into the ground around it, as the apron is; toned toward the talus.
                float patchy = noise(wx * 2.9f + 7f, wy * 2.9f) * .3f + noise(wx * 8.3f, wy * 8.3f + 2f) * .15f;
                fk = smooth(.2, .7, floor + patchy - .1) * .82f;
                float tone = .82f + .14f * t;
                if (style != RampStyle.STAIRS) {
                    // Earth floors take the soil's tone; cut steps stay the cliff's own grey-brown stone.
                    fr = lerp(fr, SOIL[0], .3f); fg = lerp(fg, SOIL[1], .3f); fb = lerp(fb, SOIL[2], .3f);
                } else {
                    fr = lerp(fr, ROCK_MID[0] * 1.2f, .3f); fg = lerp(fg, ROCK_MID[1] * 1.2f, .3f); fb = lerp(fb, ROCK_MID[2] * 1.2f, .3f);
                }
                fr *= tone; fg *= tone; fb *= tone;
                fr = lerp(fr, TALUS[0], .2f); fg = lerp(fg, TALUS[1], .2f); fb = lerp(fb, TALUS[2], .2f);
            }
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
