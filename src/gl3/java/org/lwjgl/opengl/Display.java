package org.lwjgl.opengl;

/**
 * LWJGL 2's {@code Display}, as a headless LWJGL 3 run sees it: there is no display. Tool classpath only (the
 * {@code gl3} source set); the game supplies the real one. Carries exactly the members the mod's compiled classes
 * reference. A zero size is what {@code GroundParallaxPipeline} already reads as "no display, scale one".
 */
public final class Display {

    private Display() { }

    public static int getWidth() { return 0; }

    public static int getHeight() { return 0; }

    public static boolean isCreated() { return false; }

    public static boolean isActive() { return false; }
}
