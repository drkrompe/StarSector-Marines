package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * Minimal GLSL 1.20 compile/link helper over LWJGL 2's {@code GL20} core entry
 * points (not {@code ARBShaderObjects} — this mod's existing shader code,
 * {@code render.PlanetSphereDrawable} / {@code ProceduralCubeDrawable} /
 * {@code PlanetBillboardDrawable}, all call {@code GL20} core directly, so S2's
 * shader follows the same proven path rather than the ARB extension). This
 * class generalizes the compile/link/uniform-location boilerplate those three
 * duplicate into one reusable helper.
 *
 * <p><strong>Fail-soft by design.</strong> {@link #ensure()} compiles + links
 * once, lazily, and never throws out of it — a compile or link failure logs
 * once (with the GLSL info log) and flips {@link #isBroken()} permanently for
 * this instance. Callers must check the return value and skip the effect
 * entirely rather than let a bad shader crash the battle — the same
 * {@code broken}-flag idiom {@link DecalAccumulator} and
 * {@link com.dillon.starsectormarines.render.BridgeRenderer} use for FBO
 * failures.
 */
public final class ShaderProgram {

    private static final Logger LOG = Global.getLogger(ShaderProgram.class);

    private final String label;
    private final String vertexSource;
    private final String fragmentSource;

    private boolean attempted;
    private boolean broken;
    private int program;
    private final Map<String, Integer> uniformLocs = new LinkedHashMap<>();

    public ShaderProgram(String label, String vertexSource, String fragmentSource) {
        this.label = label;
        this.vertexSource = vertexSource;
        this.fragmentSource = fragmentSource;
    }

    /**
     * Compiles + links on first call (idempotent after — cheap boolean check
     * once attempted). Returns {@code true} if the program is usable this
     * frame; {@code false} means the caller must skip the effect.
     */
    public boolean ensure() {
        if (attempted) return !broken;
        attempted = true;
        int vs = 0, fs = 0;
        try {
            vs = compile(GL_VERTEX_SHADER, vertexSource);
            fs = compile(GL_FRAGMENT_SHADER, fragmentSource);
            program = glCreateProgram();
            glAttachShader(program, vs);
            glAttachShader(program, fs);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new RuntimeException("link failed: " + glGetProgramInfoLog(program, 4096));
            }
            return true;
        } catch (RuntimeException e) {
            LOG.error("ShaderProgram '" + label + "' failed to build; effect disabled", e);
            broken = true;
            return false;
        } finally {
            if (vs != 0) glDeleteShader(vs);
            if (fs != 0) glDeleteShader(fs);
        }
    }

    public boolean isBroken() { return broken; }

    public void use() { glUseProgram(program); }

    public static void useNone() { glUseProgram(0); }

    public void set1i(String name, int v) { glUniform1i(loc(name), v); }
    public void set1f(String name, float v) { glUniform1f(loc(name), v); }
    public void set2f(String name, float x, float y) { glUniform2f(loc(name), x, y); }
    public void set3f(String name, float x, float y, float z) { glUniform3f(loc(name), x, y, z); }
    public void set4f(String name, float x, float y, float z, float w) {
        glUniform4f(loc(name), x, y, z, w);
    }

    private int loc(String name) {
        return uniformLocs.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    /**
     * Every uniform name this program has been asked to set, in first-asked
     * order. Paired with {@link #unresolvedUniforms()} it says how much was
     * actually checked — an empty missing-list means nothing when nothing was
     * looked up.
     */
    /**
     * Names this program was asked to set that the linked program does not
     * have, in the order they were first asked for.
     *
     * <p>Setting an unknown uniform is silent: {@code glGetUniformLocation}
     * answers -1 and {@code glUniform*} on -1 is defined to do nothing. So a
     * renamed or mistyped uniform is not an error anywhere — the effect simply
     * comes out wrong, which is the hardest kind of render bug to trace back.
     * This is the record that makes it sayable, for evidence that runs against
     * a real context.
     *
     * <p>A name can also land here legitimately: GLSL strips a uniform the
     * shader never reads, so a value the Java side still bothers to upload
     * reports missing. That is worth knowing too — it is dead work.
     */
    public List<String> requestedUniforms() {
        return new ArrayList<>(uniformLocs.keySet());
    }

    /** @see #requestedUniforms() */
    public List<String> unresolvedUniforms() {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : uniformLocs.entrySet()) {
            if (entry.getValue() < 0) missing.add(entry.getKey());
        }
        return missing;
    }

    /** Releases the GL program object. Safe to call whether or not {@link #ensure()} ever succeeded. */
    public void dispose() {
        if (program != 0) { glDeleteProgram(program); program = 0; }
        uniformLocs.clear();
        attempted = false;
        broken = false;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader, 4096);
            glDeleteShader(shader);
            throw new RuntimeException("compile failed ("
                    + (type == GL_VERTEX_SHADER ? "vertex" : "fragment") + "): " + log);
        }
        return shader;
    }
}
