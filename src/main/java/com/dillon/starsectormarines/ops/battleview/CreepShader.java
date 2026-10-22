package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;

import java.util.Locale;

/**
 * The creep's fragment program: {@link CreepShading} in GLSL, line for line.
 * One quad over the creep's extent, texture coordinate 0 in map cells; the
 * field texture (R coverage, G vitality, B pulse distance, A wall) on unit 0,
 * sampled linearly so coverage blends between cell centres, {@link CreepNoise}
 * on unit 1 and {@link CreepSurface} on unit 2, both repeating, the surface
 * mipmapped.
 */
final class CreepShader {

    static final String VERTEX_SRC = ""
            + "#version 120\n"
            + "varying vec2 vCell;\n"
            + "void main() {\n"
            + "    vCell = gl_MultiTexCoord0.xy;\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

    static final String FRAGMENT_SRC = ""
            + "#version 120\n"
            + "varying vec2 vCell;\n"
            + "uniform sampler2D field;\n"
            + "uniform sampler2D noise;\n"
            + "uniform sampler2D surface;\n"
            + "uniform vec2 fieldSize;\n"
            + "uniform float time;\n"
            + "uniform float alpha;\n"
            + "uniform float cellPx;\n"
            + "uniform vec2 sunDir;\n"
            + "uniform float sunRise;\n"
            + "uniform float sunStrength;\n"
            + "uniform vec3 base;\n"
            + "uniform vec3 flesh;\n"
            + "uniform vec3 vein;\n"
            + "uniform vec3 glow;\n"
            + "uniform vec3 rim;\n"
            + "uniform vec3 dead;\n"
            + "vec4 F(vec2 p) { return texture2D(field, p / fieldSize); }\n"
            + "float C(vec2 p) { return texture2D(field, p / fieldSize).r; }\n"
            + "vec4 S(vec2 p) { return texture2D(surface, p / " + lit(CreepSurface.CELLS) + "); }\n"
            + "float bump(float d, float c, float w) { float t = (d - c) / w; return exp(-t * t); }\n"
            + "vec2 BIG(vec2 p) { return p * " + lit(CreepShading.BIG_SCALE) + " + vec2(1.3, 0.7); }\n"
            + "float plateAt(vec2 p, float big) { return mix(S(p).r, S(BIG(p)).r, big); }\n"
            + "vec3 body(float dd, vec2 p, vec2 u, float vit, float pulseCells, float cloud, float patch) {\n"
            + "    float big = smoothstep(" + lit(CreepShading.BIG_LO) + ", " + lit(CreepShading.BIG_HI) + ", cloud);\n"
            + "    vec4 sb = S(BIG(p));\n"
            + "    vec4 s = S(p);\n"
            + "    float plate = mix(s.r, sb.r, big), tone = mix(s.g, sb.g, big);\n"
            + "    float stp = 1.0 / 64.0;\n"
            + "    float px = plateAt(p + vec2(stp, 0.0), big), py = plateAt(p + vec2(0.0, stp), big);\n"
            + "    float lip = bump(dd, " + lit(CreepShading.LIP_AT) + ", " + lit(CreepShading.LIP_WIDTH) + ");\n"
            + "    float lipAmp = " + lit(CreepShading.LIP_LOW) + " + " + lit(CreepShading.LIP_BLISTER) + " * s.a;\n"
            + "    float hLip = " + lit(CreepShading.PLATEAU) + " + lip * lipAmp;\n"
            + "    float dLip = lipAmp * lip * (-2.0 * (dd - " + lit(CreepShading.LIP_AT) + ") / "
            + lit(CreepShading.LIP_WIDTH * CreepShading.LIP_WIDTH) + ");\n"
            + "    float plated = smoothstep(0.35, 0.7, dd) * smoothstep(" + lit(CreepShading.PATCH_LO) + ", " + lit(CreepShading.PATCH_HI) + ", patch);\n"
            + "    vec2 sl = dLip * u + " + lit(CreepShading.PLATE_RELIEF) + " * plated * (vec2(px, py) - plate) / stp;\n"
            + "    vec3 n = normalize(vec3(-sl, 1.0));\n"
            + "    float cosE = 1.0 / sqrt(1.0 + sunRise * sunRise);\n"
            + "    vec3 l = vec3(sunDir * cosE, sunRise * cosE);\n"
            + "    float diffuse = 0.45 + 0.55 * max(dot(n, l), 0.0) / max(l.z, 0.05);\n"
            + "    vec3 h = normalize(l + vec3(0.0, 0.0, 1.0));\n"
            + "    float spec = (0.05 + 0.15 * plated) * pow(max(dot(n, h), 0.0), 24.0) * vit;\n"
            + "    float seam = smoothstep(0.14, 0.0, plate) * plated;\n"
            + "    float crest = clamp((hLip - " + lit(CreepShading.PLATEAU) + ") / 0.15, 0.0, 1.0);\n"
            + "    float v2 = S(p.yx * " + lit(CreepShading.VEIN_SECOND_SCALE) + " + vec2(2.3, 4.1)).b;\n"
            + "    float vn = mix(s.b, v2, smoothstep(0.38, 0.62, cloud)) * smoothstep(0.25, 0.8, dd);\n"
            + "    float outline = smoothstep(0.06, 0.0, dd);\n"
            + "    float wave = 0.5 + 0.5 * sin(pulseCells * " + lit(CreepShading.PULSE_PER_CELL)
            + " + time * " + lit(CreepShading.PULSE_PER_SECOND) + ");\n"
            + "    float g = vn * (0.25 + 0.75 * wave * wave) * vit * " + lit(CreepShading.GLOW) + ";\n"
            + "    vec3 membrane = base * (0.8 + 0.25 * cloud) * (0.88 + 0.3 * s.a);\n"
            + "    vec3 plateCol = flesh * (0.82 + 0.36 * tone);\n"
            + "    vec3 alb = mix(membrane, plateCol, plated * (1.0 - seam));\n"
            + "    alb = mix(alb, base * 0.6, seam * 0.5);\n"
            + "    alb = mix(alb, rim, 0.5 * crest);\n"
            + "    alb = mix(alb, vein, 0.7 * vn);\n"
            + "    alb = mix(dead * (0.8 + 0.3 * cloud), alb, vit);\n"
            + "    return (alb * diffuse + vec3(spec) + glow * g) * (1.0 - 0.35 * outline);\n"
            + "}\n"
            + "void main() {\n"
            + "    vec2 p = vCell;\n"
            + "    vec4 own = F(floor(p) + vec2(0.5));\n"
            + "    vec4 here = F(p);\n"
            + "    float tap = " + lit(CreepShading.TAP) + ";\n"
            + "    float e = C(p + vec2(tap, 0.0)), w = C(p - vec2(tap, 0.0));\n"
            + "    float nn = C(p + vec2(0.0, tap)), ss = C(p - vec2(0.0, tap));\n"
            + "    float soft = 0.4 * here.r + 0.15 * (e + w + nn + ss);\n"
            + "    vec2 gr = vec2(e - w, nn - ss) / (2.0 * tap);\n"
            + "    float glen = length(gr);\n"
            + "    float gl = max(glen, " + lit(CreepShading.GRADIENT_FLOOR) + ");\n"
            + "    vec2 u = glen > 0.00001 ? gr / glen : vec2(0.0);\n"
            + "    vec4 coarse = texture2D(noise, p / " + lit(CreepNoise.CELLS) + ");\n"
            + "    float cloud = coarse.r;\n"
            + "    float fine = texture2D(noise, (p * 2.5 + vec2(3.1, 1.7)) / " + lit(CreepNoise.CELLS) + ").r;\n"
            + "    float lobes = (cloud - 0.5) * " + lit(CreepShading.EDGE_NOISE) + " + (fine - 0.5) * "
            + lit(CreepShading.EDGE_FINE) + ";\n"
            + "    vec2 pt = vec2(0.8 * p.x + 0.6 * p.y, -0.6 * p.x + 0.8 * p.y) * " + lit(CreepShading.PATCH_TURN_SCALE)
            + " + vec2(5.3, 2.9);\n"
            + "    float patch = 0.5 * fine + 0.5 * texture2D(noise, pt / " + lit(CreepNoise.CELLS) + ").r;\n"
            + "    float pulseCells = here.b * 255.0;\n"
            + "    if (own.a <= 0.5) {\n"
            + "        float d = (soft - 0.5 + lobes) / gl;\n"
            + "        float a = clamp(d * cellPx + 0.5, 0.0, 1.0);\n"
            + "        float shadow = 0.0;\n"
            + "        if (a < 1.0 && sunStrength > 0.0) {\n"
            + shadowStep(CreepShading.SHADOW_STEPS[0])
            + shadowStep(CreepShading.SHADOW_STEPS[1])
            + shadowStep(CreepShading.SHADOW_STEPS[2])
            + "        }\n"
            + "        float sa = " + lit(CreepShading.SHADOW_ALPHA) + " * shadow * sunStrength;\n"
            + "        if (a <= 0.0 && sa <= 0.002) discard;\n"
            + "        vec3 col = vec3(0.0);\n"
            + "        if (a > 0.0) {\n"
            + "            col = body(clamp(d, 0.0, 2.0), p, u, here.g, pulseCells, cloud, patch);\n"
            + "            col *= 1.0 - 0.55 * smoothstep(0.2, 0.5, here.a);\n"
            + "        }\n"
            + "        float outA = a + sa * (1.0 - a);\n"
            + "        vec3 tint = vec3(" + lit(CreepShading.SHADOW_TINT[0]) + ", " + lit(CreepShading.SHADOW_TINT[1])
            + ", " + lit(CreepShading.SHADOW_TINT[2]) + ");\n"
            + "        vec3 rgb = (col * a + tint * sa * (1.0 - a)) / max(outA, 0.0001);\n"
            + "        gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), outA * alpha);\n"
            + "        return;\n"
            + "    }\n"
            + "    float age = own.r;\n"
            + "    if (age <= 0.0) discard;\n"
            + "    float crustT = 1.0 - " + lit(CreepShading.CRUST_REACH) + " * smoothstep(" + lit(CreepShading.CRUST_AGE_LO)
            + ", 1.0, age);\n"
            + "    float dc = (soft - crustT + lobes * 0.6) / gl;\n"
            + "    float ac = crustT < 1.0 ? clamp(dc * cellPx + 0.5, 0.0, 1.0) : 0.0;\n"
            + "    float veinT = 1.0 - 0.95 * smoothstep(0.0, " + lit(CreepShading.VEIN_AGE) + ", age);\n"
            + "    float reach = smoothstep(0.0, 0.08, soft - veinT);\n"
            + "    float wv = clamp(S(p * " + lit(CreepShading.WALL_VEIN_SCALE) + ").b * 1.6, 0.0, 1.0) * reach;\n"
            + "    if (ac <= 0.0 && wv <= 0.002) discard;\n"
            + "    vec3 raised = mix(flesh, rim, 0.35);\n"
            + "    float va = 0.9 * wv;\n"
            + "    vec3 col = ac > 0.0 ? body(clamp(dc, 0.0, 2.0), p, u, here.g, pulseCells, cloud, patch) : vec3(0.0);\n"
            + "    if (ac > 0.0) {\n"
            + "        vec2 gw = vec2(F(p + vec2(tap, 0.0)).a - F(p - vec2(tap, 0.0)).a,\n"
            + "                       F(p + vec2(0.0, tap)).a - F(p - vec2(0.0, tap)).a) / (2.0 * tap);\n"
            + "        float bevel = clamp(-dot(gw, sunDir), -1.0, 1.0);\n"
            + "        float edge = 1.0 - smoothstep(0.5, 0.8, here.a);\n"
            + "        col = mix(col, rim, " + lit(CreepShading.WALL_LIFT) + ") * (1.0 + " + lit(CreepShading.WALL_BEVEL)
            + " * bevel) * (1.0 - " + lit(CreepShading.WALL_OUTLINE) + " * edge);\n"
            + "    }\n"
            + "    float outA = ac + va * (1.0 - ac);\n"
            + "    vec3 rgb = (col * ac + raised * va * (1.0 - ac)) / max(outA, 0.0001);\n"
            + "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), outA * alpha);\n"
            + "}\n";

    private static String shadowStep(float t) {
        String tl = lit(t);
        return "            {\n"
                + "                float ds = (C(p + sunDir * " + tl + ") - 0.5 + lobes) / gl;\n"
                + "                if (ds > 0.0) {\n"
                + "                    float lipH = " + lit(CreepShading.PLATEAU) + " + bump(min(ds, 2.0), "
                + lit(CreepShading.LIP_AT) + ", " + lit(CreepShading.LIP_WIDTH) + ") * "
                + lit(CreepShading.LIP_LOW + 0.5f * CreepShading.LIP_BLISTER) + ";\n"
                + "                    shadow = max(shadow, clamp((lipH - " + tl + " * sunRise) / 0.04, 0.0, 1.0));\n"
                + "                }\n"
                + "            }\n";
    }

    private final ShaderProgram program = new ShaderProgram("Creep", VERTEX_SRC, FRAGMENT_SRC);

    ShaderProgram program() { return program; }

    boolean isBroken() { return program.isBroken(); }

    /** Builds lazily; false when the program cannot be used. Needs a current context. */
    boolean begin(CreepPass pass, int fieldW, int fieldH) {
        if (!program.ensure()) return false;
        program.use();
        program.set1i("field", 0);
        program.set1i("noise", 1);
        program.set1i("surface", 2);
        program.set2f("fieldSize", fieldW, fieldH);
        program.set1f("time", pass.timeSeconds);
        program.set1f("alpha", pass.alpha);
        program.set1f("cellPx", pass.cellPx);
        program.set2f("sunDir", pass.sun.dirX(), pass.sun.dirY());
        program.set1f("sunRise", pass.sun.rise());
        program.set1f("sunStrength", pass.sun.strength());
        CreepPalette pal = pass.palette;
        set(program, "base", pal.base());
        set(program, "flesh", pal.flesh());
        set(program, "vein", pal.vein());
        set(program, "glow", pal.glow());
        set(program, "rim", pal.rim());
        set(program, "dead", pal.dead());
        return true;
    }

    void dispose() { program.dispose(); }

    private static void set(ShaderProgram p, String name, float[] c) { p.set3f(name, c[0], c[1], c[2]); }

    private static String lit(float v) { return String.format(Locale.ROOT, "%.4f", v); }
}
