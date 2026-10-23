package org.lwjgl.input;

/** LWJGL 2's {@code Keyboard} for a headless LWJGL 3 run: never created, nothing pressed. Tool classpath only. */
public final class Keyboard {

    private Keyboard() { }

    public static boolean isCreated() { return false; }

    public static boolean isKeyDown(int key) { return false; }
}
