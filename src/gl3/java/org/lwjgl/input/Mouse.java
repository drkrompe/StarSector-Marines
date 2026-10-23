package org.lwjgl.input;

/** LWJGL 2's {@code Mouse} for a headless LWJGL 3 run: never created, never in the window. Tool classpath only. */
public final class Mouse {

    private Mouse() { }

    public static boolean isCreated() { return false; }

    public static boolean isInsideWindow() { return false; }

    public static boolean isButtonDown(int button) { return false; }

    public static int getX() { return 0; }

    public static int getY() { return 0; }
}
