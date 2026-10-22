package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;

import java.util.Locale;

/**
 * Burnt ground's fragment program: {@link ScorchShading} in GLSL, line for line.
 * One quad over the burn's extent, texture coordinate 0 in map cells; the burn
 * picture on unit 0 (R amount, G heaviness times amount, B what lies underfoot),
 * sampled linearly so the amount blends between cell centres and clamped at the
 * edge; {@link CreepNoise} on unit 1, repeating.
 *
 * <p>Drawn twice. With {@code mode} 0 it writes the albedo multiplier for a
 * {@code DST_COLOR, ZERO} blend, which darkens and browns the ground already in
 * the target; with {@code mode} 1 it writes the overlay for an ordinary alpha
 * blend. Both discard where nothing near has burnt.
 */
final class ScorchShader {

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
            + "uniform vec2 fieldSize;\n"
            + "uniform float mode;\n"
            + "uniform float alpha;\n"
            + "vec4 N(vec2 p) { return texture2D(noise, p / " + lit(CreepNoise.CELLS) + "); }\n"
            + "float stretch(float n, float k) { return clamp((n - 0.5) * k + 0.5, 0.0, 1.0); }\n"
            + "vec4 over(vec4 acc, vec3 c, float a) {\n"
            + "    a = clamp(a, 0.0, 1.0);\n"
            + "    if (a <= 0.0) return acc;\n"
            + "    float outA = a + acc.a * (1.0 - a);\n"
            + "    return vec4((c * a + acc.rgb * acc.a * (1.0 - a)) / outA, outA);\n"
            + "}\n"
            + "void main() {\n"
            + "    vec2 p = vCell;\n"
            + "    vec4 here = texture2D(field, p / fieldSize);\n"
            + "    float a = here.r;\n"
            + "    if (a <= 0.002) discard;\n"
            + "    float h = clamp(here.g / a, 0.0, 1.0);\n"
            + "    float code = texture2D(field, (floor(p) + 0.5) / fieldSize).b;\n"
            + "    float ground = floor(code * 255.0 / " + lit(ScorchShading.GROUND_STEP) + " + 0.5);\n"
            + "    if (ground > 2.5) discard;\n"
            + "    vec4 n1 = N(p * " + lit(ScorchShading.LARGE) + ");\n"
            + "    vec4 n2 = N(p * " + lit(ScorchShading.MID) + " + vec2(3.1, 1.9));\n"
            + "    vec4 n3 = N(p * " + lit(ScorchShading.FINE) + " + vec2(7.7, 5.3));\n"
            + "    vec2 r = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8);\n"
            + "    vec4 t2 = N(r * " + lit(ScorchShading.MID * ScorchShading.TURN_MID) + " + vec2(5.7, 2.3));\n"
            + "    vec4 t3 = N(r * " + lit(ScorchShading.FINE * ScorchShading.TURN_FINE) + " + vec2(1.3, 8.9));\n"
            + "    n2.rgb = (n2.rgb + t2.rgb) * 0.5;\n"
            + "    n3.rgb = (n3.rgb + t3.rgb) * 0.5;\n"
            + "    float e = a + (n1.r - 0.5) * " + lit(ScorchShading.EDGE_LARGE) + " + (n2.r - 0.5) * " + lit(ScorchShading.EDGE_MID) + ";\n"
            + "    float burnt = smoothstep(" + lit(ScorchShading.EDGE - ScorchShading.EDGE_SOFT) + ", "
            + lit(ScorchShading.EDGE + ScorchShading.EDGE_SOFT) + ", e);\n"
            + "    float singe = smoothstep(" + lit(ScorchShading.SINGE_FROM) + ", " + lit(ScorchShading.EDGE) + ", e) * (1.0 - burnt);\n"
            + "    vec3 mult;\n"
            + "    vec4 o = vec4(0.0);\n"
            + "    if (ground > 0.5 && ground < 1.5) {\n"
            + "        float smudge = smoothstep(0.15, 0.65, e) * smoothstep(0.35, 0.75, n2.b);\n"
            + "        if (smudge <= 0.002) discard;\n"
            + "        float m = 1.0 - " + lit(ScorchShading.SOOT_DARKEN) + " * smudge;\n"
            + "        mult = vec3(m, m, m * 0.98);\n"
            + "    } else {\n"
            + "        float sev = stretch(n1.r, 2.6), mot = stretch(n2.r, 3.4), clump = stretch(n3.r, 3.1), grain = n3.a;\n"
            + "        float tuft = ground < 0.5 ? smoothstep(0.78, 0.86, clump * 0.6 + n2.b * 0.4) * (1.0 - h) : 0.0;\n"
            + "        float survived = burnt * tuft * 0.7;\n"
            + "        burnt -= survived;\n"
            + "        singe = max(singe, survived);\n"
            + "        float charLevel = clamp(" + lit(ScorchShading.CHAR_BASE) + " + " + lit(ScorchShading.CHAR_HEAVY) + " * h\n"
            + "                + (sev - 0.5) * 0.7 + (mot - 0.5) * 0.45 + (clump - 0.5) * 0.25 + (grain - 0.5) * 0.08, 0.0, 1.0);\n"
            + "        mult = mix(vec3(1.0), " + vec3(ScorchShading.SINGE_MULT) + ", singe * 0.85);\n"
            + "        mult = mix(mult, mix(" + vec3(ScorchShading.SCORCH_MULT) + ", " + vec3(ScorchShading.CHAR_MULT) + ", charLevel), burnt);\n"
            + "        o = over(o, " + vec3(ScorchShading.STRAW) + ", singe * 0.32 + burnt * (1.0 - charLevel) * (0.22 + 0.3 * mot));\n"
            + "        o = over(o, " + vec3(ScorchShading.CHARCOAL) + ", burnt * charLevel * 0.62);\n"
            + "        if (ground > 1.5) {\n"
            + "            float crack = smoothstep(0.78, 0.86, n3.g);\n"
            + "            o = over(o, mix(" + vec3(ScorchShading.CRUST) + ", " + vec3(ScorchShading.CRUST_CRACK) + ", crack), burnt * (0.55 + 0.35 * mot));\n"
            + "        } else {\n"
            + "            float piles = smoothstep(0.55, 0.85, stretch(n1.b, 2.0) * 0.45 + clump * 0.55 + h * 0.3 - 0.15)\n"
            + "                    * (0.25 + 0.75 * h) * (0.8 + 0.2 * grain);\n"
            + "            float sprinkle = smoothstep(0.80, 0.92, clump) * smoothstep(0.4, 0.8, mot) * 0.45 * (1.0 - h);\n"
            + "            o = over(o, " + vec3(ScorchShading.ASH) + ", clamp((piles + sprinkle) * " + lit(ScorchShading.ASH_WEIGHT)
            + ", 0.0, 1.0) * burnt * 0.8);\n"
            + "        }\n"
            + "    }\n"
            + "    if (mode < 0.5) {\n"
            + "        gl_FragColor = vec4(mix(vec3(1.0), mult, alpha), 1.0);\n"
            + "    } else {\n"
            + "        if (o.a <= 0.0) discard;\n"
            + "        gl_FragColor = vec4(o.rgb, clamp(o.a, 0.0, 1.0) * alpha);\n"
            + "    }\n"
            + "}\n";

    private final ShaderProgram program = new ShaderProgram("Scorch", VERTEX_SRC, FRAGMENT_SRC);

    boolean isBroken() { return program.isBroken(); }

    /** Builds lazily; false when the program cannot be used. Needs a current context. */
    boolean begin(int fieldW, int fieldH, float alpha) {
        if (!program.ensure()) return false;
        program.use();
        program.set1i("field", 0);
        program.set1i("noise", 1);
        program.set2f("fieldSize", fieldW, fieldH);
        program.set1f("alpha", alpha);
        return true;
    }

    /** 0 for the multiply pass, 1 for the overlay pass. */
    void mode(int mode) { program.set1f("mode", mode); }

    void dispose() { program.dispose(); }

    private static String vec3(float[] c) { return "vec3(" + lit(c[0]) + ", " + lit(c[1]) + ", " + lit(c[2]) + ")"; }

    private static String lit(float v) { return String.format(Locale.ROOT, "%.4f", v); }
}
