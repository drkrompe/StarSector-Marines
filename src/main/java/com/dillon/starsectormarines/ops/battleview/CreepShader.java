package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;

import java.util.Locale;

/**
 * The creep's fragment program: {@link CreepShading} in GLSL. One quad over the
 * creep's extent, texture coordinate 0 in map cells; the field texture (R
 * coverage, G vitality, B pulse distance, A climbed wall) on unit 0, sampled
 * linearly so coverage blends between cell centres, {@link CreepNoise} on
 * unit 1 and {@link CreepMaterial} on unit 2, both repeating and mipmapped.
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
            + "uniform sampler2D material;\n"
            + "uniform vec2 fieldSize;\n"
            + "uniform float time;\n"
            + "uniform float alpha;\n"
            + "uniform vec3 base;\n"
            + "uniform vec3 flesh;\n"
            + "uniform vec3 vein;\n"
            + "uniform vec3 glow;\n"
            + "uniform vec3 rim;\n"
            + "uniform vec3 dead;\n"
            + "void main() {\n"
            + "    vec4 f = texture2D(field, vCell / fieldSize);\n"
            + "    float nr = texture2D(noise, vCell / " + lit(CreepNoise.CELLS) + ").r;\n"
            + "    float la = texture2D(material, vCell / " + lit(CreepMaterial.CELLS) + ").r;\n"
            + "    float lb = texture2D(material, vCell.yx / " + lit(CreepMaterial.CELLS * CreepMaterial.SECOND_SCALE)
            + " + " + lit(CreepMaterial.SECOND_OFFSET) + ").r;\n"
            + "    float l = clamp((mix(la, lb, " + lit(CreepMaterial.SECOND_SHARE) + ") - 0.5) * "
            + lit(CreepMaterial.CONTRAST) + " + 0.5, 0.0, 1.0);\n"
            + "    float tap = " + lit(CreepShading.TAP) + ";\n"
            + "    float cov = 0.4 * f.r + 0.15 * (texture2D(field, (vCell + vec2(tap, 0.0)) / fieldSize).r\n"
            + "        + texture2D(field, (vCell - vec2(tap, 0.0)) / fieldSize).r\n"
            + "        + texture2D(field, (vCell + vec2(0.0, tap)) / fieldSize).r\n"
            + "        + texture2D(field, (vCell - vec2(0.0, tap)) / fieldSize).r);\n"
            + "    float m = smoothstep(" + lit(CreepShading.EDGE_LO) + ", " + lit(CreepShading.EDGE_HI) + ", cov + (nr - 0.5) * " + lit(CreepShading.EDGE_NOISE) + " + (l - 0.5) * " + lit(CreepShading.EDGE_RELIEF) + ");\n"
            + "    float climb = smoothstep(0.35, 0.60, l * 0.8 + nr * 0.4);\n"
            + "    m *= 1.0 + (climb - 1.0) * f.a;\n"
            + "    if (m <= 0.002) discard;\n"
            + "    float vit = f.g;\n"
            + "    float rimT = smoothstep(0.0, 0.35, m) * (1.0 - smoothstep(0.35, 1.0, m));\n"
            + "    float v = 1.0 - smoothstep(" + lit(CreepShading.VEIN_LO) + ", " + lit(CreepShading.VEIN_HI) + ", l);\n"
            + "    float fleshy = smoothstep(" + lit(CreepShading.NODULE_LO) + ", " + lit(CreepShading.NODULE_HI) + ", l);\n"
            + "    float thick = smoothstep(0.3, 0.7, nr);\n"
            + "    float value = (0.62 + 0.8 * (l - 0.46)) * (0.85 + 0.3 * thick);\n"
            + "    float wave = 0.5 + 0.5 * sin(f.b * 255.0 * " + lit(CreepShading.PULSE_PER_CELL) + " + time * " + lit(CreepShading.PULSE_PER_SECOND) + ");\n"
            + "    float g = v * (0.25 + 0.75 * wave * wave) * vit * " + lit(CreepShading.GLOW) + ";\n"
            + "    float speck = smoothstep(0.92, 0.99, l) * vit;\n"
            + "    float mottle = 0.7 + 0.5 * nr;\n"
            + "    vec3 body = mix(base, flesh, fleshy) * value;\n"
            + "    vec3 col = mix(body, vein, v * 0.8) + glow * g;\n"
            + "    col = mix(col, rim, rimT * 0.35) + glow * speck * 0.6;\n"
            + "    vec3 ridge = vein * 1.3 + flesh * 0.4 + glow * g;\n"
            + "    col = mix(col, ridge, f.a * 0.7);\n"
            + "    col = mix(dead * mottle * value, col, vit);\n"
            + "    float film = mix(" + lit(CreepShading.FILM_ALPHA) + " * mix(0.8, 1.15, thick), 1.0, max(fleshy, v));\n"
            + "    float a = m * film * mix(0.75, 1.0, vit);\n"
            + "    a = clamp(mix(a, m * 0.95, f.a), 0.0, 1.0);\n"
            + "    gl_FragColor = vec4(clamp(col, 0.0, 1.0), a * alpha);\n"
            + "}\n";

    private final ShaderProgram program = new ShaderProgram("Creep", VERTEX_SRC, FRAGMENT_SRC);

    ShaderProgram program() { return program; }

    boolean isBroken() { return program.isBroken(); }

    /** Builds lazily; false when the program cannot be used. Needs a current context. */
    boolean begin(CreepPalette pal, float time, float alpha, int fieldW, int fieldH) {
        if (!program.ensure()) return false;
        program.use();
        program.set1i("field", 0);
        program.set1i("noise", 1);
        program.set1i("material", 2);
        program.set2f("fieldSize", fieldW, fieldH);
        program.set1f("time", time);
        program.set1f("alpha", alpha);
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
