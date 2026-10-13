package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.shield.ShieldDomeService;

/**
 * A CPU model of the dome fragment shader in {@link ShieldDomeShader}, and the
 * constants both share.
 *
 * <p><b>This is the model, not the shader.</b> It follows the GLSL line for line
 * so the look can be reviewed in a deterministic Java2D frame (the snapshot
 * suites cannot run GLSL) and so a headless scene shows a dome at all. It proves
 * the look and the arithmetic; it does not prove the GLSL compiles, binds its
 * uniforms or renders the same on a driver (trig precision differs), which
 * is what {@code gradlew.bat shaderEvidence} and a live check are for. A change to
 * one is a change to the other.
 *
 * <p>The result is premultiplied RGBA: colour already weighted by coverage, with
 * glow terms adding colour beyond their alpha so energy brightens what is under it
 * rather than merely tinting it. Composite it with {@code ONE, ONE_MINUS_SRC_ALPHA}.
 */
final class ShieldDomeShading {

    /** Half-extent, in dome radii, of the quad a dome is drawn into, leaving room for the outer bloom. */
    static final float EXTENT = 1.14f;
    /** Width of one hexagonal cell of the field, in map cells. */
    static final float HEX_CELLS = 1.7f;
    /** How fast an impact's ring travels across the dome, in map cells a second. */
    static final float RING_SPEED = 9.5f;
    /** Rim thickness on screen, in pixels. */
    static final float RIM_PX = 2.6f;

    static final float[] AMBER = {1.00f, 0.75f, 0.47f};
    private static final float TWO_PI = 6.2831853f;

    private ShieldDomeShading() {}

    /**
     * Shades one point of one dome.
     *
     * @param lx   local x, the dome radius being {@code 1}
     * @param ly   local y
     * @param time seconds of presentation clock, for the shimmer
     * @param out  receives premultiplied r, g, b, a
     */
    static void shade(ShieldDomeDraw d, float lx, float ly, float time, float[] out) {
        float r = (float) Math.sqrt(lx * lx + ly * ly);
        float edge = 1f - r;
        float rimW = RIM_PX / d.radiusPx;
        float inside = edge >= 0f ? 1f : 0f;
        float rimIn = (1f - smoothstep(0f, rimW, edge)) * inside;
        float rimOut = (1f - smoothstep(0f, rimW * 2.2f, -edge)) * (1f - inside);
        float glowIn = (1f - smoothstep(0f, rimW * 6f, edge)) * inside;
        float fres = (float) Math.pow(clamp01(r), 3.2) * inside;

        float p0x = lx * d.radiusCells;
        float p0y = ly * d.radiusCells;
        float px = p0x + 0.12f * (float) Math.sin(p0y * 0.6f + time * 0.9f);
        float py = p0y + 0.12f * (float) Math.cos(p0x * 0.55f - time * 0.75f);
        float hpx = px / HEX_CELLS;
        float hpy = py / HEX_CELLS;
        final float hrx = 1f;
        final float hry = 1.7320508f;
        float ax = mod(hpx, hrx) - hrx * 0.5f;
        float ay = mod(hpy, hry) - hry * 0.5f;
        float bx = mod(hpx - hrx * 0.5f, hrx) - hrx * 0.5f;
        float by = mod(hpy - hry * 0.5f, hry) - hry * 0.5f;
        float gx;
        float gy;
        if (ax * ax + ay * ay < bx * bx + by * by) { gx = ax; gy = ay; } else { gx = bx; gy = by; }
        float cidx = hpx - gx;
        float cidy = hpy - gy;
        float hd = Math.max(Math.abs(gx) * 0.5f + Math.abs(gy) * 0.8660254f, Math.abs(gx));
        float hexEdge = 1f - smoothstep(0f, 0.085f, 0.5f - hd);
        float ch = hash(cidx, cidy);
        float ccx = cidx * HEX_CELLS;
        float ccy = cidy * HEX_CELLS;

        float flow = 0.5f + 0.5f * (float) Math.sin((px * 0.62f + py * 0.78f) * 0.42f - time * 1.15f
                + 1.6f * (float) Math.sin(px * 0.21f + time * 0.31f));
        float shimmer = 0.78f + 0.44f * flow;

        float[] tint = d.tint;
        float level = d.level;
        float amberMix = smoothstep(0.55f, 1f, level) * 0.65f;
        float baseR = mix(tint[0], AMBER[0], amberMix);
        float baseG = mix(tint[1], AMBER[1], amberMix);
        float baseB = mix(tint[2], AMBER[2], amberMix);
        float brightR = mix(baseR, 1f, 0.45f);
        float brightG = mix(baseG, 1f, 0.45f);
        float brightB = mix(baseB, 1f, 0.45f);

        float u = d.overloaded ? 0f : 1f;
        float down = 1f - u;
        float uIn = u * inside;
        float hf = d.hitFlash;
        float df = d.dropFlash;

        float cr = 0f;
        float cg = 0f;
        float cb = 0f;
        float ca = 0f;

        // Interior: a faint cover tint, hex outlines that strengthen toward the rim,
        // and a share of cells glowing that tracks how full the pool is.
        float fillA = (0.034f + 0.045f * level + 0.17f * fres + 0.10f * glowIn) * shimmer * uIn;
        cr += baseR * fillA; cg += baseG * fillA; cb += baseB * fillA; ca += fillA;
        float lineA = hexEdge * (0.004f + 0.20f * fres * fres * fres + 0.010f * level) * shimmer * uIn;
        float twinkle = 0.5f + 0.5f * (float) Math.sin(time * 2.3f + ch * 40f);
        float litA = (ch <= level * 0.55f ? 1f : 0f) * twinkle * (1f - hexEdge) * 0.016f * uIn;
        float cellGlow = lineA + litA;
        cr += brightR * cellGlow; cg += brightG * cellGlow; cb += brightB * cellGlow; ca += cellGlow * 0.4f;

        // Rim, brightening and whitening when struck.
        float rimA = (rimIn * (0.62f + 0.28f * hf) + rimOut * 0.30f) * u;
        float rimR = mix(brightR, 1f, hf);
        float rimG = mix(brightG, 1f, hf);
        float rimB = mix(brightB, 1f, hf);
        cr += rimR * rimA; cg += rimG * rimA; cb += rimB * rimA; ca += rimA * 0.5f;

        // Down: a ghost rim, the part recovered climbing back round it, and cells
        // re-forming; on the instant of collapse the cells go out one by one.
        if (down > 0f) {
            float recovered = 1f - d.recovery;
            float ang = (float) Math.atan2(lx, -ly) / TWO_PI + 0.5f;
            float arc = ang <= recovered ? 1f : 0f;
            float mixBack = smoothstep(0.6f, 1f, recovered);
            float dr = mix(AMBER[0], tint[0], mixBack);
            float dg = mix(AMBER[1], tint[1], mixBack);
            float db = mix(AMBER[2], tint[2], mixBack);
            float dd = (ang - recovered) * TWO_PI * d.radiusCells;
            float head = (float) Math.exp(-dd * dd / 1.5f);
            float downRim = (rimIn * (0.10f + 0.55f * arc) + rimOut * 0.10f * arc
                    + glowIn * head * 0.35f) * down;
            cr += dr * downRim; cg += dg * downRim; cb += db * downRim; ca += downRim * 0.5f;
            float reform = (ch <= recovered * 0.5f ? 1f : 0f) * hexEdge * 0.07f * inside * down;
            cr += dr * reform; cg += dg * reform; cb += db * reform; ca += reform * 0.4f;
            float shatter = (ch <= df ? 1f : 0f) * (hexEdge * 0.40f + 0.06f) * inside * df * down;
            cr += AMBER[0] * shatter; cg += AMBER[1] * shatter; cb += AMBER[2] * shatter; ca += shatter * 0.4f;
        }
        if (df > 0f) {
            float spent = 1f - df;
            float ringR = 1f + 0.12f * spent;
            float dRing = (r - ringR) / (3.5f / d.radiusPx);
            float collapse = (float) Math.exp(-dRing * dRing) * df * 0.8f;
            cr += AMBER[0] * collapse; cg += AMBER[1] * collapse; cb += AMBER[2] * collapse; ca += collapse * 0.5f;
        }

        // Impacts: a bright flash where the round landed, a ripple ring crossing
        // the dome, and the hex cells lighting as it passes.
        float flashGlow = 0f;
        float cellFill = 0f;
        float lineBoost = 0f;
        for (int i = 0; i < d.impactCount; i++) {
            int at = i * 4;
            float ipx = d.impacts[at] * d.radiusCells;
            float ipy = d.impacts[at + 1] * d.radiusCells;
            float age = d.impacts[at + 2];
            float s = d.impacts[at + 3];
            float life = clamp01(1f - age / ShieldDomeService.IMPACT_SECONDS);
            float dx = p0x - ipx;
            float dy = p0y - ipy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float cx = ccx - ipx;
            float cy = ccy - ipy;
            float dc = (float) Math.sqrt(cx * cx + cy * cy);
            float ringRadius = age * RING_SPEED;
            float ringW = 0.55f + 0.9f * age;
            float atten = life * life / (1f + 0.10f * ringRadius);
            float ring = (float) Math.exp(-sq((dist - ringRadius) / ringW)) * s * atten;
            float ringCell = (float) Math.exp(-sq((dc - ringRadius) / (ringW * 1.2f))) * s * atten;
            float flash = s * (float) Math.exp(-dist * dist / (1f + 2.4f * s)) * (float) Math.exp(-age * 5.5f);
            float cellFlash = s * (float) Math.exp(-dc * dc / (2f + 4f * s)) * (float) Math.exp(-age * 3.2f);
            flashGlow = screen(flashGlow, ring * 0.35f + flash * 0.80f);
            cellFill = screen(cellFill, (ringCell * 0.7f + cellFlash * 0.7f) * (1f - hexEdge * 0.6f) * 0.8f);
            lineBoost = screen(lineBoost, ring + ringCell + cellFlash);
        }
        if (d.impactCount > 0) {
            float within = 1f - smoothstep(0.97f, 1.0f, r);
            float wr = mix(tint[0], 1f, 0.7f);
            float wg = mix(tint[1], 1f, 0.7f);
            float wb = mix(tint[2], 1f, 0.7f);
            float g = flashGlow * within;
            cr += wr * g; cg += wg * g; cb += wb * g; ca += g * 0.4f;
            float cf = cellFill * 0.30f * within;
            float tr = mix(tint[0], 1f, 0.35f);
            float tg = mix(tint[1], 1f, 0.35f);
            float tb = mix(tint[2], 1f, 0.35f);
            cr += tr * cf; cg += tg * cf; cb += tb * cf; ca += cf * 0.4f;
            float lb = lineBoost * hexEdge * 0.5f * within;
            cr += lb; cg += lb; cb += lb; ca += lb * 0.4f;
        }

        float a = d.alpha;
        out[0] = clamp01(cr) * a;
        out[1] = clamp01(cg) * a;
        out[2] = clamp01(cb) * a;
        out[3] = clamp01(ca) * a;
    }

    /** A hash with no trigonometry in it, so a driver and the JVM agree on which cells it picks. */
    static float hash(float x, float y) {
        float ax = fract(x * 0.1031f);
        float ay = fract(y * 0.1031f);
        float az = ax;
        float d = ax * (ay + 33.33f) + ay * (az + 33.33f) + az * (ax + 33.33f);
        ax += d;
        ay += d;
        az += d;
        return fract((ax + ay) * az);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float mod(float x, float y) {
        return x - y * (float) Math.floor(x / y);
    }

    /** Soft-saturating union of two light amounts in 0..1, so overlapping ripples combine instead of piling up. */
    private static float screen(float acc, float v) {
        float c = clamp01(v);
        return acc + c - acc * c;
    }

    private static float sq(float v) { return v * v; }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }
}
