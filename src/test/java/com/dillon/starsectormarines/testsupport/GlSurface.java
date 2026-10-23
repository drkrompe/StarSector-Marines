package com.dillon.starsectormarines.testsupport;

import java.lang.reflect.Method;

/**
 * A current OpenGL context with a drawable of a fixed size, from whichever binding this process was launched with.
 *
 * <p>Two exist. {@link HeadlessGl} is LWJGL 2 from the game's install, a pbuffer: what Windows uses, and what
 * {@code shaderEvidence} and {@code renderEvidence} run on. The other is LWJGL 3, for a machine whose game install
 * has no LWJGL 2 native this JVM can load (the Mac: the game's library is x86_64 and the JDK is arm64). It lives in
 * the {@code gl3} source set, compiled against LWJGL 3, and only a launcher that swaps the game's {@code lwjgl.jar}
 * for LWJGL 3 puts it on the classpath; {@code -Dsnapshot.gl.context=<class>} names it, and its static
 * {@code create(int, int)} is called by reflection so nothing here links against either binding's own classes.
 */
public interface GlSurface extends AutoCloseable {

    /** System property naming a class with a static {@code GlSurface create(int width, int height)}. */
    String CONTEXT_PROPERTY = "snapshot.gl.context";

    String rendererDescription();

    @Override
    void close();

    /** A current surface of {@code width}x{@code height}, or null when this machine cannot give one. */
    static GlSurface createOrNull(int width, int height) {
        String named = System.getProperty(CONTEXT_PROPERTY);
        if (named == null || named.isBlank()) return HeadlessGl.createOrNull(width, height);
        try {
            Method create = Class.forName(named.trim()).getMethod("create", int.class, int.class);
            return (GlSurface) create.invoke(null, width, height);
        } catch (ReflectiveOperationException | LinkageError failure) {
            System.out.println("[gl] " + named + " could not make a context: " + failure);
            return null;
        }
    }

    /** Why {@link #createOrNull} came back empty, for a fallback message that says something. */
    static String unavailableReason() {
        String named = System.getProperty(CONTEXT_PROPERTY);
        return named == null || named.isBlank() ? HeadlessGl.unavailableReason()
                : named + " could not make an accelerated context";
    }
}
