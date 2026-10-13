package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * The planetary shield dome shader, built and run by a real driver.
 *
 * <p>{@link ShieldDomeShadingTest} holds the look to the CPU model, which proves
 * the arithmetic and is blind to a syntax error, a construct one driver
 * rejects, or a uniform the Java side sets under a name the program does not
 * have (a silent no-op by specification). This is where those are caught, and
 * where the model is checked against what the driver actually paints.
 *
 * <p>The comparison is deliberately limited to what the two should agree on. The
 * shimmer and wobble are {@code sin}, which a GPU evaluates at lower precision
 * than {@link Math#sin}, and the per-cell hash is trig-free for the same reason.
 * Every scenario, the rim, the hexagonal field, the impacts and the collapse, is
 * compared pixel for pixel against the model with a tolerance for 8-bit output
 * and single-precision trig. Most run with an empty pool; the twinkling lit
 * cells are exercised by the full-pool unit test and a live check.
 *
 * <p>Opt-in and excluded from {@code test}; run {@code gradlew.bat shaderEvidence}.
 * Where no context can be made it reports that it skipped.
 */
@Tag("shader-evidence")
class ShieldDomeShaderGlEvidence {

    private static final int SURFACE = 256;
    private static final float TIME = 1.3f;
    private static final float[] CLEAR = {0.75f, 0.60f, 0.45f};
    /** Mean absolute channel error allowed between driver and model, out of 1. */
    private static final float MEAN_TOLERANCE = 0.012f;
    /** Largest single-channel error allowed anywhere, out of 1: a hex edge a pixel out can differ a lot. */
    private static final float PEAK_TOLERANCE = 0.30f;

    @Test
    void theDomeShaderCompilesAndLinksOnARealDriver() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            System.out.println("[shader-evidence] context: " + gl.rendererDescription());
            ShaderProgram program = new ShaderProgram("ShieldDome",
                    ShieldDomeShader.VERTEX_SRC, ShieldDomeShader.FRAGMENT_SRC);
            boolean ok = program.ensure();
            System.out.println("[shader-evidence] ShieldDome: "
                    + (ok ? "compiled and linked" : "FAILED (see the GLSL info log above)"));
            program.dispose();
            assertTrue(ok, "the dome shader did not build on this driver");
        }
    }

    @Test
    void theDomeUploadsOnlyUniformsTheProgramHasAndDrawsWhatTheModelDraws() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            ShieldDomeShader shader = new ShieldDomeShader();
            List<Scenario> scenarios = List.of(
                    new Scenario("steady", steady()),
                    new Scenario("steady hostile", hostile(steady())),
                    new Scenario("full pool", fullPool(steady())),
                    new Scenario("struck", struck(steady(), 0.05f, 1f)),
                    new Scenario("rippling", struck(steady(), 0.7f, 0.8f)),
                    new Scenario("flashing", flashing(steady())),
                    new Scenario("down, half recovered", down(0.5f, 0f)),
                    new Scenario("collapsing", down(1f, 0.8f)));
            for (Scenario scenario : scenarios) {
                float[] expected = model(scenario.draw);
                float[] drawn = drawWithShader(shader, scenario.draw);
                float mean = 0f;
                float peak = 0f;
                for (int i = 0; i < drawn.length; i++) {
                    float err = Math.abs(drawn[i] - expected[i]);
                    mean += err;
                    peak = Math.max(peak, err);
                }
                mean /= drawn.length;
                System.out.println(String.format("[shader-evidence] ShieldDome %-22s mean error %.4f, peak %.3f",
                        scenario.name, mean, peak));
                assertTrue(mean < MEAN_TOLERANCE,
                        scenario.name + ": the driver and the CPU model disagree, mean error " + mean);
                assertTrue(peak < PEAK_TOLERANCE,
                        scenario.name + ": a pixel is far from the model, peak error " + peak);
            }

            List<String> missing = shader.program().unresolvedUniforms();
            System.out.println("[shader-evidence] dome uniforms: "
                    + shader.program().requestedUniforms().size() + " uploaded, "
                    + missing.size() + " unresolved " + (missing.isEmpty() ? "" : missing.toString()));
            // The instrument, checked against itself.
            shader.program().use();
            shader.program().set1f("thisUniformDoesNotExist", 1f);
            ShaderProgram.useNone();
            assertTrue(shader.program().unresolvedUniforms().contains("thisUniformDoesNotExist"),
                    "an unknown uniform must be reported or this check is vacuous");
            missing.remove("thisUniformDoesNotExist");
            shader.dispose();
            assertTrue(missing.isEmpty(), "the dome uploads uniforms the program does not have: " + missing);
        }
    }

    @Test
    void anImpactActuallyChangesWhatTheDriverPaints() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE, SURFACE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            ShieldDomeShader shader = new ShieldDomeShader();
            float[] quiet = drawWithShader(shader, steady());
            float[] hit = drawWithShader(shader, struck(steady(), 0.05f, 1f));
            float lit = 0f;
            for (int i = 0; i < quiet.length; i++) lit = Math.max(lit, hit[i] - quiet[i]);
            System.out.println("[shader-evidence] ShieldDome impact brightens the picture by up to "
                    + String.format("%.2f", lit));
            shader.dispose();
            assertTrue(lit > 0.15f, "an impact must visibly light the dome on the driver, got " + lit);
        }
    }

    // ---- drawing -------------------------------------------------------------

    private record Scenario(String name, ShieldDomeDraw draw) {}

    /** Draws one dome through the shipping shader over the clear colour and returns RGB, row by row. */
    private static float[] drawWithShader(ShieldDomeShader shader, ShieldDomeDraw draw) {
        glViewport(0, 0, SURFACE, SURFACE);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE, 0, SURFACE, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glClearColor(CLEAR[0], CLEAR[1], CLEAR[2], 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        ShieldDomePass pass = new ShieldDomePass(shader);
        pass.reset(TIME);
        pass.add(draw);
        pass.run();
        assertTrue(!shader.isBroken(), "the dome shader must build and draw");

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE * SURFACE * 4);
        glReadPixels(0, 0, SURFACE, SURFACE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        float[] rgb = new float[SURFACE * SURFACE * 3];
        for (int i = 0; i < SURFACE * SURFACE; i++) {
            rgb[i * 3] = (pixels.get(i * 4) & 0xFF) / 255f;
            rgb[i * 3 + 1] = (pixels.get(i * 4 + 1) & 0xFF) / 255f;
            rgb[i * 3 + 2] = (pixels.get(i * 4 + 2) & 0xFF) / 255f;
        }
        return rgb;
    }

    /** The same frame from the CPU model: premultiplied source over the clear colour. */
    private static float[] model(ShieldDomeDraw draw) {
        float[] rgb = new float[SURFACE * SURFACE * 3];
        float[] out = new float[4];
        for (int y = 0; y < SURFACE; y++) {
            for (int x = 0; x < SURFACE; x++) {
                float lx = (x + 0.5f - draw.sx) / draw.radiusPx;
                float ly = (y + 0.5f - draw.sy) / draw.radiusPx;
                int at = (y * SURFACE + x) * 3;
                if (Math.abs(lx) <= ShieldDomeShading.EXTENT && Math.abs(ly) <= ShieldDomeShading.EXTENT) {
                    ShieldDomeShading.shade(draw, lx, ly, TIME, out);
                } else {
                    out[0] = out[1] = out[2] = out[3] = 0f;
                }
                for (int c = 0; c < 3; c++) {
                    rgb[at + c] = Math.min(1f, out[c] + CLEAR[c] * (1f - out[3]));
                }
            }
        }
        return rgb;
    }

    // ---- scenarios -----------------------------------------------------------

    private static ShieldDomeDraw steady() {
        ShieldDomeDraw d = new ShieldDomeDraw();
        d.sx = SURFACE / 2f;
        d.sy = SURFACE / 2f;
        d.radiusPx = 100f;
        d.radiusCells = 16f;
        d.tint[0] = 0.55f;
        d.tint[1] = 0.86f;
        d.tint[2] = 1.0f;
        d.level = 0f;
        return d;
    }

    private static ShieldDomeDraw hostile(ShieldDomeDraw d) {
        d.tint[0] = 1.0f;
        d.tint[1] = 0.55f;
        d.tint[2] = 0.50f;
        return d;
    }

    private static ShieldDomeDraw fullPool(ShieldDomeDraw d) {
        d.level = 0.9f;
        return d;
    }

    private static ShieldDomeDraw struck(ShieldDomeDraw d, float age, float intensity) {
        d.impactCount = 2;
        d.impacts[0] = 0.35f;
        d.impacts[1] = 0.1f;
        d.impacts[2] = age;
        d.impacts[3] = intensity;
        d.impacts[4] = -0.4f;
        d.impacts[5] = -0.3f;
        d.impacts[6] = age * 0.5f;
        d.impacts[7] = 0.6f;
        return d;
    }

    private static ShieldDomeDraw flashing(ShieldDomeDraw d) {
        d.hitFlash = 0.8f;
        return d;
    }

    private static ShieldDomeDraw down(float recovery, float dropFlash) {
        ShieldDomeDraw d = steady();
        d.overloaded = true;
        d.recovery = recovery;
        d.dropFlash = dropFlash;
        return d;
    }
}
