package com.dillon.starsectormarines.testsupport;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A real OpenGL context, with no game and no window.
 *
 * <p>Every other piece of headless evidence in this project deliberately avoids
 * GL: the snapshot suites draw through Java2D and the shader oracles
 * re-implement GLSL on the CPU. That is the right trade for evidence about
 * <em>geometry</em>, and it is worth nothing for the question a shader actually
 * fails on. A composite can be arithmetically perfect and still be a black
 * screen because a uniform was renamed, a sampler was left bound to the wrong
 * unit, or a driver rejected a construct the spec allows. Nothing on the CPU
 * side can see any of that.
 *
 * <h2>Why LWJGL 2, from the game's own install</h2>
 * <p>Because the point is to run <b>our</b> classes rather than a copy of them.
 * {@code ShaderProgram}, {@code GroundParallaxPipeline} and the passes beneath
 * them are written against LWJGL 2's {@code org.lwjgl.opengl.*} bindings; under
 * LWJGL 3 they would not even link, so a LWJGL 3 harness could only exercise a
 * re-implementation of the setup — which catches a GLSL syntax error and misses
 * every binding mistake, the failures that are actually likely. The jar is
 * already on the test runtime classpath and the natives already ship beside the
 * game, so this costs no new dependency at all.
 *
 * <p><b>What it still does not cover:</b> Starsector hands its UI hooks a
 * polluted GL state — alpha masked off, scissor enabled, matrices left
 * somewhere unexpected — and a context created here is clean. Evidence from
 * this class says the shader compiles, binds and draws what it should; it does
 * not say the effect survives contact with the host's state.
 */
public final class HeadlessGl implements AutoCloseable {

    /** LWJGL 2 reads this once, when its native loader first runs, so it must be set before any GL class touches it. */
    private static final String LIBRARY_PATH_PROPERTY = "org.lwjgl.librarypath";

    private final Pbuffer buffer;

    private HeadlessGl(Pbuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * A current context of {@code width}x{@code height}, or {@code null} when
     * this machine cannot give one.
     *
     * <p>Null rather than an exception because the reasons are environmental
     * and none of them are a defect in the thing under test: no display, no
     * accelerated driver, a game install without its natives. A caller reports
     * that it skipped; it does not report a failure it cannot distinguish from
     * a real one.
     */
    public static HeadlessGl createOrNull(int width, int height) {
        if (!installNativeLibraryPath()) return null;
        try {
            if ((Pbuffer.getCapabilities() & Pbuffer.PBUFFER_SUPPORTED) == 0) return null;
            Pbuffer buffer = new Pbuffer(width, height, new PixelFormat(8, 8, 0), null);
            buffer.makeCurrent();
            // Without this LWJGL's GL entry points are unbound and every call
            // NPEs somewhere far away from the cause.
            GLContext.useContext(buffer);
            return new HeadlessGl(buffer);
        } catch (LWJGLException | UnsatisfiedLinkError | NoClassDefFoundError e) {
            return null;
        }
    }

    /** Human-readable reason a context could not be made, for a skip message that says something. */
    public static String unavailableReason() {
        Path natives = nativeDirectory();
        if (natives == null) {
            return "no LWJGL natives found (set -DstarsectorDir=<install> or "
                    + LIBRARY_PATH_PROPERTY + ")";
        }
        return "no accelerated OpenGL context available on this machine";
    }

    public String rendererDescription() {
        return GL11.glGetString(GL11.GL_RENDERER)
                + " / GL " + GL11.glGetString(GL11.GL_VERSION);
    }

    @Override
    public void close() {
        buffer.destroy();
    }

    private static boolean installNativeLibraryPath() {
        if (System.getProperty(LIBRARY_PATH_PROPERTY) != null) return true;
        Path natives = nativeDirectory();
        if (natives == null) return false;
        System.setProperty(LIBRARY_PATH_PROPERTY, natives.toAbsolutePath().toString());
        return true;
    }

    /**
     * The game's native folder, which is where the only LWJGL 2 binaries on
     * this machine live. {@code starsectorDir} is already required to build at
     * all and every evidence task forwards it, so there is nothing new to
     * configure.
     */
    private static Path nativeDirectory() {
        String install = System.getProperty("starsectorDir");
        if (install == null || install.isBlank()) return null;
        Path core = Paths.get(install, "starsector-core", "native");
        for (String platform : new String[]{"windows", "linux", "macosx"}) {
            Path candidate = core.resolve(platform);
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }
}
