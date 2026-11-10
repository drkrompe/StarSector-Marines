package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;

import java.util.Locale;

/**
 * Older lava flows' fragment program: {@link LavaFlowShading} in GLSL, line for line. One quad over the flows'
 * extent, texture coordinate 0 in map cells; the smoothed flow on unit 0 (R cover, G heat, B melt), sampled linearly and
 * clamped at the edge, {@link CreepNoise} on unit 1, repeating, and the lava rift's crust image on unit 2, its used
 * part {@code crustExtent} of the texture.
 *
 * <p>Drawn twice. With {@code mode} 0 it lays the dark crust for an ordinary alpha blend; with {@code mode} 1 it
 * writes the hot cracks' glow for an additive blend. Both discard off the flows.
 */
final class LavaFlowShader {

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
            + "uniform sampler2D crust;\n"
            + "uniform vec2 fieldSize;\n"
            + "uniform vec2 crustExtent;\n"
            + "uniform float time;\n"
            + "uniform float mode;\n"
            + "uniform float alpha;\n"
            + "void main() {\n"
            + "    vec2 p = vCell;\n"
            + "    vec4 f = texture2D(field, p / fieldSize);\n"
            + "    float onMelt = smoothstep(" + lit(LavaFlowShading.MELT_FADE_FROM) + ", "
            + lit(LavaFlowShading.MELT_FADE_TO) + ", f.b);\n"
            + "    if (f.r <= 0.01 || onMelt >= 1.0) discard;\n"
            + "    vec4 lobe = texture2D(noise, p * " + lit(LavaFlowShading.LOBE_SCALE) + " / " + lit(CreepNoise.CELLS) + ");\n"
            + "    vec4 fray = texture2D(noise, (p * " + lit(LavaFlowShading.FRAY_SCALE) + " + vec2(3.7, 1.3)) / "
            + lit(CreepNoise.CELLS) + ");\n"
            + "    float m = f.r + (lobe.r - 0.5) * 2.0 * " + lit(LavaFlowShading.LOBE)
            + " + (fray.g - 0.5) * 2.0 * " + lit(LavaFlowShading.FRAY) + ";\n"
            + "    float k = smoothstep(" + lit(LavaFlowShading.EDGE - LavaFlowShading.FEATHER) + ", "
            + lit(LavaFlowShading.EDGE + LavaFlowShading.FEATHER) + ", m) * (1.0 - onMelt);\n"
            + "    if (k <= 0.0) discard;\n"
            + "    vec3 c = texture2D(crust, fract(p / " + lit(LavaFlowShading.TILE_CELLS) + ") * crustExtent).rgb;\n"
            + "    if (mode < 0.5) {\n"
            + "        gl_FragColor = vec4(c * " + vec3(LavaFlowShading.TINT) + ", "
            + lit(LavaFlowShading.ALPHA) + " * k * alpha);\n"
            + "    } else {\n"
            + "        float breath = 1.0 - " + lit(LavaFlowShading.BREATH) + " * 0.5 * (1.0 + sin(time * "
            + lit(LavaFlowShading.BREATH_RATE) + " * 6.2832 + lobe.b * 6.2832));\n"
            + "        float g = " + lit(LavaFlowShading.GLOW) + " * f.g * f.g * breath * k * smoothstep("
            + lit(LavaFlowShading.EDGE) + ", 0.9, m);\n"
            + "        if (g <= 0.0) discard;\n"
            + "        gl_FragColor = vec4(c * " + vec3(LavaFlowShading.GLOW_TINT) + ", g * alpha);\n"
            + "    }\n"
            + "}\n";

    private final ShaderProgram program = new ShaderProgram("LavaFlow", VERTEX_SRC, FRAGMENT_SRC);

    boolean isBroken() { return program.isBroken(); }

    /** Builds lazily; false when the program cannot be used. Needs a current context. */
    boolean begin(int fieldW, int fieldH, float crustW, float crustH, float time, float alpha) {
        if (!program.ensure()) return false;
        program.use();
        program.set1i("field", 0);
        program.set1i("noise", 1);
        program.set1i("crust", 2);
        program.set2f("fieldSize", fieldW, fieldH);
        program.set2f("crustExtent", crustW, crustH);
        program.set1f("time", time);
        program.set1f("alpha", alpha);
        return true;
    }

    /** 0 for the crust, 1 for the glow. */
    void mode(int mode) { program.set1f("mode", mode); }

    void dispose() { program.dispose(); }

    private static String vec3(float[] c) { return "vec3(" + lit(c[0]) + ", " + lit(c[1]) + ", " + lit(c[2]) + ")"; }

    private static String lit(float v) { return String.format(Locale.ROOT, "%.4f", v); }
}
