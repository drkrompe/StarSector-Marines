package com.dillon.starsectormarines.ops.battleview;

/**
 * What one point of creep looks like: the CPU model of {@link CreepShader}'s
 * fragment program, line for line, so a review frame drawn through Java2D shows
 * the creep the game draws.
 *
 * <p>Creep is a raised surface with a hard organic edge, not a film. Its
 * outline is the field's coverage crossing one half, broken into lobes by
 * noise, and it is opaque inside a one-pixel antialiased edge. Distance in
 * from that edge (coverage over its own gradient, in cells) shapes a beaded
 * lip that stands up off the ground, and past the lip the body is covered in
 * {@link CreepSurface}'s chitin plates, with veins in it carrying the pulse.
 * The creep shades its own relief against the sun, because the ground
 * composite casts shadows from its height field but does not light a
 * surface's slope, and casts its own short shadow, because the composite's
 * height channel is too coarse for a lip a fifth of a metre tall.
 *
 * <p>Beside a wall the creep tucks into a dark crease. On a wall the field's
 * coverage is how long creep has held it: first veins reach across the wall
 * top, then a crust with the same beaded edge grows over it from the side the
 * creep came from.
 */
final class CreepShading {

    /** Height of the flat body, and of the lip's crest above it, in metres. */
    static final float PLATEAU = 0.09f;
    static final float LIP_LOW = 0.07f, LIP_BLISTER = 0.22f;
    /** Where the lip crests and how wide it is, in cells in from the edge. */
    static final float LIP_AT = 0.24f, LIP_WIDTH = 0.20f;
    /** Plate relief, in metres. */
    static final float PLATE_RELIEF = 0.022f;
    /** Coverage-unit amplitude of the coarse and fine lobes in the edge. */
    static final float EDGE_NOISE = 0.30f, EDGE_FINE = 0.12f;
    /** Half-spacing of the coverage taps the gradient and softening use, in cells. */
    static final float TAP = 0.5f;
    /** Floor on the coverage gradient, so the distance estimate stays finite in the flat interior. */
    static final float GRADIENT_FLOOR = 0.35f;
    /** Pulse phase per cell of path distance and per second; positive both, so a crest walks toward the source. */
    static final float PULSE_PER_CELL = 0.9f, PULSE_PER_SECOND = 2.6f;
    /** How hard the pulse lights the veins. */
    static final float GLOW = 0.30f;
    /** Steps (in cells) of the creep's own shadow march, and the shadow's opacity at full sun strength. */
    static final float[] SHADOW_STEPS = {0.1f, 0.2f, 0.3f};
    static final float SHADOW_ALPHA = 0.45f;
    /** Colour the creep's shadow darkens the ground toward. */
    static final float[] SHADOW_TINT = {0.07f, 0.07f, 0.10f};
    /** Wall coverage over which veins reach across the wall top, and over which the crust takes it. */
    static final float VEIN_AGE = 0.5f, CRUST_AGE_LO = 0.35f, CRUST_REACH = 0.6f;
    /** Larger plates: their sampling scale, and the cloud band over which they take over. */
    static final float BIG_SCALE = 0.6f, BIG_LO = 0.45f, BIG_HI = 0.6f;
    /** Scale of the turned cloud sampling the membrane patches mix in. */
    static final float PATCH_TURN_SCALE = 1.7f;
    /** Fine-noise band over which plates give way to bare membrane. */
    static final float PATCH_LO = 0.30f, PATCH_HI = 0.50f;
    /** Scale of the second, quarter-turned vein sampling on the ground, against the first. */
    static final float VEIN_SECOND_SCALE = 0.77f;
    /** Scale of the finer vein lattice drawn over walls, against the ground's. */
    static final float WALL_VEIN_SCALE = 2.2f;
    /**
     * How an overgrown wall still reads as a wall: its crust lifted toward the rim colour, lit and shaded on the edges
     * that face toward and away from the sun as a raised block is, and outlined dark where it meets the floor (the
     * floor's own contact crease is the outline's other half).
     */
    static final float WALL_LIFT = 0.22f, WALL_BEVEL = 0.30f, WALL_OUTLINE = 0.5f;
    /**
     * Grass and crops ahead of the edge: the blight (the creep's coverage spread {@link CreepLayer#BLIGHT_CELLS}
     * cells out over vegetated ground) over which the ground turns the palette's wilt colour, how opaque the wilt
     * gets, how much its edge is broken up, and the blight over which veins reach out into it.
     */
    static final float WILT_LO = 0.05f, WILT_HI = 0.6f, WILT_ALPHA = 0.6f, WILT_BREAK = 0.3f;
    static final float BAND_VEIN_LO = 0.32f, BAND_VEIN_HI = 0.7f, BAND_VEIN_BREAK = 0.35f;

    private CreepShading() { }

    /** The field and noise lookups one point needs, from the layer's mirror on the CPU. */
    interface Field {
        /** Bilinear field at a point: coverage, vitality in 0..1, pulse in cells, wall flag in 0..1. */
        void sample(float cellX, float cellY, float[] out);

        /** Bilinear coverage alone. */
        float coverage(float cellX, float cellY);

        /** Bilinear ground under the creep: blight and vegetated in 0..1 into {@code out[0..1]}. */
        void ground(float cellX, float cellY, float[] out);
    }

    /** The scene's sun, as the creep reads it: toward the sun on the map, rise per cell, shadow strength. */
    record Sun(float dirX, float dirY, float rise, float strength) { }

    /**
     * Shades one point into straight (not premultiplied) RGBA in {@code out}.
     *
     * @param cellPx screen pixels per cell, for the antialiased edge and the surface's mip level
     * @param tmp    scratch, one per thread
     */
    static void shade(Field f, float x, float y, float cellPx, float time, CreepPalette pal, Sun sun,
                      Scratch tmp, float[] out) {
        float[] own = tmp.own, here = tmp.here, noise = tmp.noise, fine = tmp.fine, surf = tmp.surf, col = tmp.col, turned = tmp.turned;
        f.sample((float) Math.floor(x) + 0.5f, (float) Math.floor(y) + 0.5f, own);
        f.sample(x, y, here);
        float e = f.coverage(x + TAP, y), w = f.coverage(x - TAP, y);
        float n = f.coverage(x, y + TAP), s = f.coverage(x, y - TAP);
        float soft = 0.4f * here[0] + 0.15f * (e + w + n + s);
        float gx = (e - w) / (2f * TAP), gy = (n - s) / (2f * TAP);
        float g = (float) Math.sqrt(gx * gx + gy * gy);
        float gl = Math.max(g, GRADIENT_FLOOR);
        float ux = g > 1e-5f ? gx / g : 0f, uy = g > 1e-5f ? gy / g : 0f;
        CreepNoise.sample(x, y, noise);
        CreepNoise.sample(x * 2.5f + 3.1f, y * 2.5f + 1.7f, fine);
        float lobes = (noise[0] - 0.5f) * EDGE_NOISE + (fine[0] - 0.5f) * EDGE_FINE;
        // Bare-membrane patches: the fine cloud mixed with a turned, rescaled sampling of the same cloud, so the
        // patches never line up into the four-cell repeat either sampling has alone.
        CreepNoise.sample((0.8f * x + 0.6f * y) * PATCH_TURN_SCALE + 5.3f,
                (-0.6f * x + 0.8f * y) * PATCH_TURN_SCALE + 2.9f, turned);
        float patch = 0.5f * fine[0] + 0.5f * turned[0];
        float tpp = CreepSurface.SIZE / CreepSurface.CELLS / Math.max(0.01f, cellPx);
        boolean onWall = own[3] > 0.5f;

        if (!onWall) {
            // Vegetation ahead of the edge: the grass wilts, then veins reach out into it.
            float[] gnd = tmp.gnd;
            f.ground(x, y, gnd);
            float wa = 0f, wv = 0f;
            if (gnd[1] > 0f && gnd[0] > 0f) {
                wa = gnd[1] * smoothstep(WILT_LO, WILT_HI, gnd[0] + (fine[0] - 0.5f) * WILT_BREAK) * WILT_ALPHA;
                float reachV = smoothstep(BAND_VEIN_LO, BAND_VEIN_HI, gnd[0] + (noise[0] - 0.5f) * BAND_VEIN_BREAK);
                if (reachV > 0f) {
                    CreepSurface.sample(x * WALL_VEIN_SCALE, y * WALL_VEIN_SCALE, tpp * WALL_VEIN_SCALE, surf);
                    wv = 0.85f * clamp01(surf[2] * 1.6f) * reachV * gnd[1];
                }
            }
            float d = (soft - 0.5f + lobes) / gl;
            float a = clamp01(d * cellPx + 0.5f);
            float shadow = 0f;
            if (a < 1f && sun.strength() > 0f) {
                for (float t : SHADOW_STEPS) {
                    float ds = (f.coverage(x + sun.dirX() * t, y + sun.dirY() * t) - 0.5f + lobes) / gl;
                    if (ds <= 0f) continue;
                    float lip = PLATEAU + bump(Math.min(ds, 2f), LIP_AT, LIP_WIDTH) * (LIP_LOW + 0.5f * LIP_BLISTER);
                    shadow = Math.max(shadow, clamp01((lip - t * sun.rise()) / 0.04f));
                }
            }
            float sa = SHADOW_ALPHA * shadow * sun.strength();
            if (a <= 0f && sa <= 0.002f && wa <= 0.002f && wv <= 0.002f) {
                out[0] = out[1] = out[2] = out[3] = 0f;
                return;
            }
            if (a > 0f) {
                body(clamp(d, 0f, 2f), x, y, ux, uy, here[1], here[2], noise[0], patch, tpp, time, pal, sun, surf, col);
                float crease = smoothstep(0.2f, 0.5f, here[3]);
                for (int c = 0; c < 3; c++) col[c] *= 1f - 0.55f * crease;
            }
            // Layered over the ground in order: wilt, veins, the creep's shadow, the creep. Premultiplied.
            float alpha = 0f;
            for (int c = 0; c < 3; c++) out[c] = 0f;
            alpha = over(out, alpha, pal.wilt(), 1f, wa);
            alpha = over(out, alpha, pal.flesh(), pal.rim(), wv);
            alpha = over(out, alpha, SHADOW_TINT, 1f, sa);
            alpha = over(out, alpha, col, 1f, a);
            for (int c = 0; c < 3; c++) out[c] = clamp01(out[c] / Math.max(alpha, 1e-4f));
            out[3] = alpha;
            return;
        }

        // A wall: its own coverage is how long creep has held it.
        float age = own[0];
        if (age <= 0f) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        float crustT = 1f - CRUST_REACH * smoothstep(CRUST_AGE_LO, 1f, age);
        float dc = (soft - crustT + lobes * 0.6f) / gl;
        float ac = crustT < 1f ? clamp01(dc * cellPx + 0.5f) : 0f;
        float veinT = 1f - 0.95f * smoothstep(0f, VEIN_AGE, age);
        float reach = smoothstep(0f, 0.08f, soft - veinT);
        CreepSurface.sample(x * WALL_VEIN_SCALE, y * WALL_VEIN_SCALE, tpp * WALL_VEIN_SCALE, surf);
        float vein = clamp01(surf[2] * 1.6f) * reach;
        if (ac <= 0f && vein <= 0.002f) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        for (int c = 0; c < 3; c++) {
            float raised = mix(pal.flesh()[c], pal.rim()[c], 0.35f);
            out[c] = raised;
        }
        float va = 0.9f * vein;
        if (ac > 0f) {
            body(clamp(dc, 0f, 2f), x, y, ux, uy, here[1], here[2], noise[0], patch, tpp, time, pal, sun, surf, col);
            float[] tap = tmp.tap;
            f.sample(x + TAP, y, tap);
            float we = tap[3];
            f.sample(x - TAP, y, tap);
            float ww = tap[3];
            f.sample(x, y + TAP, tap);
            float wn = tap[3];
            f.sample(x, y - TAP, tap);
            float ws = tap[3];
            float gwx = (we - ww) / (2f * TAP), gwy = (wn - ws) / (2f * TAP);
            float bevel = clamp(-(gwx * sun.dirX() + gwy * sun.dirY()), -1f, 1f);
            float edge = 1f - smoothstep(0.5f, 0.8f, here[3]);
            for (int c = 0; c < 3; c++) {
                col[c] = mix(col[c], pal.rim()[c], WALL_LIFT) * (1f + WALL_BEVEL * bevel) * (1f - WALL_OUTLINE * edge);
            }
        }
        float alpha = ac + va * (1f - ac);
        for (int c = 0; c < 3; c++) out[c] = clamp01((col[c] * ac + out[c] * va * (1f - ac)) / Math.max(alpha, 1e-4f));
        out[3] = alpha;
    }

    /** Per-thread working arrays for {@link #shade}. */
    static final class Scratch {
        final float[] own = new float[4], here = new float[4], noise = new float[4], fine = new float[4],
                surf = new float[4], col = new float[4], tap = new float[4], turned = new float[4], gnd = new float[2];
    }

    /** Lays {@code rgb * scale} over the premultiplied {@code acc} at {@code a}; the accumulated alpha. */
    private static float over(float[] acc, float accAlpha, float[] rgb, float scale, float a) {
        if (a <= 0f) return accAlpha;
        for (int c = 0; c < 3; c++) acc[c] = rgb[c] * scale * a + acc[c] * (1f - a);
        return a + accAlpha * (1f - a);
    }

    /** As {@link #over}, in the raised colour a wall's veins take: the plates' colour a third of the way to the rim. */
    private static float over(float[] acc, float accAlpha, float[] flesh, float[] rim, float a) {
        if (a <= 0f) return accAlpha;
        for (int c = 0; c < 3; c++) acc[c] = mix(flesh[c], rim[c], 0.35f) * a + acc[c] * (1f - a);
        return a + accAlpha * (1f - a);
    }

    /**
     * The creep's body at {@code dd} cells in from its edge: lip profile, plates, veins and pulse, lit by the sun
     * from its own slope. RGB into {@code col}.
     */
    private static void body(float dd, float x, float y, float ux, float uy, float vit, float pulseCells,
                             float cloud, float patch, float tpp, float time, CreepPalette pal, Sun sun,
                             float[] surf, float[] col) {
        // Plates come in two sizes, the larger where the cloud is high, and only in patches: between them is bare
        // membrane studded with nodules, so the cover reads as grown rather than paved.
        float big = smoothstep(BIG_LO, BIG_HI, cloud);
        CreepSurface.sample(x * BIG_SCALE + 1.3f, y * BIG_SCALE + 0.7f, tpp * BIG_SCALE, surf);
        float bigPlate = surf[0], bigTone = surf[1];
        CreepSurface.sample(x, y, tpp, surf);
        float plate = mix(surf[0], bigPlate, big), tone = mix(surf[1], bigTone, big);
        float veinMask = surf[2], blister = surf[3];
        float step = 1f / 64f;
        float px = plateAt(x + step, y, big, tpp, col), py = plateAt(x, y + step, big, tpp, col);
        float lip = bump(dd, LIP_AT, LIP_WIDTH);
        float lipAmp = LIP_LOW + LIP_BLISTER * blister;
        float hLip = PLATEAU + lip * lipAmp;
        float dLip = lipAmp * lip * (-2f * (dd - LIP_AT) / (LIP_WIDTH * LIP_WIDTH));
        float plated = smoothstep(0.35f, 0.7f, dd) * smoothstep(PATCH_LO, PATCH_HI, patch);
        float sx = dLip * ux + PLATE_RELIEF * plated * (px - plate) / step;
        float sy = dLip * uy + PLATE_RELIEF * plated * (py - plate) / step;
        float nl = (float) Math.sqrt(sx * sx + sy * sy + 1f);
        float nx = -sx / nl, ny = -sy / nl, nz = 1f / nl;
        float cosE = 1f / (float) Math.sqrt(1f + sun.rise() * sun.rise());
        float lx = sun.dirX() * cosE, ly = sun.dirY() * cosE, lz = sun.rise() * cosE;
        float ndl = Math.max(0f, nx * lx + ny * ly + nz * lz);
        float diffuse = 0.45f + 0.55f * ndl / Math.max(lz, 0.05f);
        float hx = lx, hy = ly, hz = lz + 1f;
        float hn = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        float ndh = Math.max(0f, (nx * hx + ny * hy + nz * hz) / hn);
        float spec = (0.05f + 0.15f * plated) * (float) Math.pow(ndh, 24.0) * vit;
        float seam = smoothstep(0.14f, 0f, plate) * plated;
        float crest = clamp01((hLip - PLATEAU) / 0.15f);
        // Two samplings of the vein lattice, the second turned and rescaled, chosen between by the cloud,
        // so the network does not repeat with the surface's tile.
        CreepSurface.sample(y * VEIN_SECOND_SCALE + 2.3f, x * VEIN_SECOND_SCALE + 4.1f, tpp * VEIN_SECOND_SCALE, surf);
        float veinPick = smoothstep(0.38f, 0.62f, cloud);
        float vein = mix(veinMask, surf[2], veinPick) * smoothstep(0.25f, 0.8f, dd);
        float outline = smoothstep(0.06f, 0f, dd);
        float wave = 0.5f + 0.5f * (float) Math.sin(pulseCells * PULSE_PER_CELL + time * PULSE_PER_SECOND);
        float glow = vein * (0.25f + 0.75f * wave * wave) * vit * GLOW;
        for (int c = 0; c < 3; c++) {
            float membrane = pal.base()[c] * (0.8f + 0.25f * cloud) * (0.88f + 0.3f * blister);
            float plateCol = pal.flesh()[c] * (0.82f + 0.36f * tone);
            float alb = mix(membrane, plateCol, plated * (1f - seam));
            alb = mix(alb, pal.base()[c] * 0.6f, seam * 0.5f);
            alb = mix(alb, pal.rim()[c], 0.5f * crest);
            alb = mix(alb, pal.vein()[c], 0.7f * vein);
            alb = mix(pal.dead()[c] * (0.8f + 0.3f * cloud), alb, vit);
            col[c] = (alb * diffuse + spec + pal.glow()[c] * glow) * (1f - 0.35f * outline);
        }
    }

    /** Plate height alone, both sizes mixed as {@link #body} mixes them, sampled through {@code into}. */
    private static float plateAt(float x, float y, float big, float tpp, float[] into) {
        CreepSurface.sample(x * BIG_SCALE + 1.3f, y * BIG_SCALE + 0.7f, tpp * BIG_SCALE, into);
        float b = into[0];
        CreepSurface.sample(x, y, tpp, into);
        return mix(into[0], b, big);
    }

    static float bump(float d, float centre, float width) {
        float t = (d - centre) / width;
        return (float) Math.exp(-t * t);
    }

    static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    static float mix(float a, float b, float t) { return a + (b - a) * t; }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
