package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground shaders, compiled and linked by a real driver.
 *
 * <p>This is the one piece of evidence in the project that runs GLSL rather
 * than modelling it. Everything else about surface relief — the unit tests, the
 * pixel oracle, the sun-shadow snapshot suite — evaluates the shader's
 * arithmetic on the CPU, which proves the geometry and is completely blind to
 * the ways a shader actually fails in practice: a syntax error, a construct one
 * driver takes and another rejects, a uniform the Java side sets under a name
 * the program does not have.
 *
 * <p>That last one is the quiet one. {@code glUniform*} on an unknown location
 * is defined to do nothing, so a renamed uniform produces no error, no log, and
 * no crash — only a wrong picture. {@link ShaderProgram#unresolvedUniforms()}
 * is what turns it into a sentence, and this is where it gets read.
 *
 * <p><b>Opt-in, and excluded from {@code test}.</b> It needs an accelerated
 * driver, which a test suite may not have and must never require. Run it with
 * {@code gradlew.bat shaderEvidence}. Where no context can be made it reports
 * that it skipped rather than failing, because "this machine has no GPU" and
 * "this shader is broken" are not the same finding and must not look alike.
 */
@Tag("shader-evidence")
class GroundShaderGlEvidence {

    /** Small: nothing here draws, and a context's size has no bearing on whether a program links. */
    private static final int SURFACE = 64;

    @Test
    void everyGroundShaderCompilesAndLinksOnARealDriver() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            System.out.println("[shader-evidence] context: " + gl.rendererDescription());

            List<String> failures = new ArrayList<>();
            for (Shader shader : projectShaders()) {
                ShaderProgram program = new ShaderProgram(
                        shader.label, shader.vertexSource, shader.fragmentSource);
                boolean ok = program.ensure();
                System.out.println("[shader-evidence] " + shader.label + ": "
                        + (ok ? "compiled and linked" : "FAILED (see the GLSL info log above)"));
                if (!ok) failures.add(shader.label);
                program.dispose();
            }

            assertTrue(failures.isEmpty(),
                    "these shaders did not build on this driver: " + failures);
        }
    }

    /**
     * Every uniform the ground composite sets must exist in the linked program.
     *
     * <p>Drives {@link ShaderProgram} with the same names
     * {@code GroundParallaxPipeline.composite} uses. The list is here rather
     * than shared with the pipeline on purpose: a list the pipeline handed over
     * would agree with the pipeline by construction and check nothing. Written
     * out, it disagrees the moment either side is renamed alone — which is the
     * failure being hunted.
     */
    @Test
    void theCompositeSetsOnlyUniformsTheProgramActuallyHas() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }

            ShaderProgram program = new ShaderProgram("GroundParallax",
                    sourceField("VERTEX_SRC"), sourceField("FRAGMENT_SRC"));
            assertTrue(program.ensure(), "the ground composite must build before its uniforms mean anything");

            program.use();
            program.set1i("colorTex", 0);
            program.set1i("heightTex", 1);
            program.set1i("normalTex", 2);
            program.set2f("screenCenter", 0.5f, 0.5f);
            program.set1f("eyeHeight", 1.2f);
            program.set1f("structureStrength", GroundParallaxPipeline.DEFAULT_STRENGTH);
            program.set1f("surfaceStrength", GroundParallaxPipeline.DEFAULT_SURFACE_STRENGTH);
            program.set1f("microScale", GroundHeightPass.MICRO_SCALE);
            program.set1f("waterWaveAmplitude", GroundParallaxPipeline.DEFAULT_WATER_WAVE_AMPLITUDE);
            program.set1f("waterMacroScale", 0.2f);
            program.set1f("waterMicroScale", 0.35f);
            program.set1f("waterInteriorWaveScale", 0.25f);
            program.set1f("waterFoamAmount", 0.1f);
            program.set1f("waveTime", 0f);
            program.set2f("worldCenter", 0f, 0f);
            program.set2f("visibleCells", 40f, 24f);
            program.set2f("cellUv", 0.025f, 0.04f);
            program.set2f("heightCellUv", 0.02f, 0.03f);
            program.set1f("macroDatum", GroundHeightPass.MACRO_DATUM);
            program.set1f("macroMetersSpan", GroundHeightPass.MACRO_METERS_SPAN);
            program.set2f("sunDir", -0.707f, 0.707f);
            program.set1f("sunRisePerCell", 0.78f);
            program.set1f("shadowRangeCells", 3.8f);
            program.set1f("shadowStrength", SunLight.DEFAULT_SHADOW_STRENGTH);
            program.set1f("shadowSoftnessMeters", GroundParallaxPipeline.SHADOW_SOFTNESS_METERS);
            program.set3f("shadowTint", SunLight.TINT_R,
                    SunLight.TINT_G, SunLight.TINT_B);
            program.set1f("aspect", 1.777f);
            program.set1f("lightingStrength", GroundParallaxPipeline.DEFAULT_LIGHTING_STRENGTH);
            for (int i = 0; i < GroundLightService.MAX_SHADER_LIGHTS; i++) {
                program.set4f("lightPosRadius[" + i + "]", 0f, 0f, 1f, 1f);
                program.set4f("lightColorIntensity[" + i + "]", 0f, 0f, 0f, 0f);
            }
            List<String> missing = program.unresolvedUniforms();
            System.out.println("[shader-evidence] composite uniforms: "
                    + program.requestedUniforms().size() + " uploaded, "
                    + missing.size() + " unresolved "
                    + (missing.isEmpty() ? "" : missing.toString()));

            // The instrument, checked against itself. A name that cannot exist
            // has to be reported, or an empty `missing` above means only that
            // nothing was looked at.
            program.set1f("thisUniformDoesNotExist", 1f);
            assertTrue(program.unresolvedUniforms().contains("thisUniformDoesNotExist"),
                    "a uniform the program does not have must be reported, "
                            + "or this whole check is vacuous");

            ShaderProgram.useNone();
            program.dispose();
            assertTrue(missing.isEmpty(),
                    "the composite uploads uniforms the linked program does not have, "
                            + "so those values silently do nothing: " + missing);
        }
    }


    private record Shader(String label, String vertexSource, String fragmentSource) {}

    /**
     * Every shader the mod defines, read out of the classes that own them.
     *
     * <p>Reflection rather than a copy of the GLSL: a duplicated shader source
     * would build here forever while the real one broke.
     */
    private static List<Shader> projectShaders() throws Exception {
        return List.of(
                new Shader("GroundParallax", sourceField("VERTEX_SRC"), sourceField("FRAGMENT_SRC")),
                new Shader("GroundHeightCompose",
                        sourceField(GroundHeightPass.class, "VERTEX_SRC"),
                        sourceField(GroundHeightPass.class, "FRAGMENT_SRC")));
    }

    private static String sourceField(String name) throws Exception {
        return sourceField(GroundParallaxPipeline.class, name);
    }

    private static String sourceField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
