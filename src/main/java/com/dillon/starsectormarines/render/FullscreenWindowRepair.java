package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.IntBuffer;
import java.util.List;

import static java.lang.invoke.MethodType.methodType;

/**
 * Puts the game window back to the display mode's size after an alt-tab out of
 * fullscreen.
 *
 * <p>At a fullscreen resolution below the monitor's native one, LWJGL 2 switches
 * the display mode back when the game regains focus, but on Windows the window
 * can come back at the desktop's size. The frame is then drawn into the
 * bottom-left mode-sized corner of a larger window while the monitor shows its
 * top-left corner, so the top half of every screen lands in the bottom half with
 * black above it. Each frame while fullscreen and focused this compares the
 * window's client size with the display mode and, on a mismatch, moves and sizes
 * the window back to the mode with {@code SetWindowPos}.
 *
 * <p>LWJGL keeps the window handle and the Win32 calls private, and the game's
 * mod class loader refuses {@code java.lang.reflect}, so they are reached through
 * method handles. Anything unexpected (another platform, another LWJGL build)
 * logs once and turns the repair off; the game is never worse than without it.
 */
public final class FullscreenWindowRepair {
    private static final Logger LOG = Global.getLogger(FullscreenWindowRepair.class);

    /** SetWindowPos attempts per focus before giving up on a window Windows keeps resizing. */
    static final int MAX_ATTEMPTS = 3;
    private static final long SWP_NOZORDER = 0x0004L;
    private static final long SWP_NOACTIVATE = 0x0010L;

    enum Action { NONE, REPAIR, GIVE_UP }

    private static final Policy POLICY = new Policy();
    private static final IntBuffer RECT = BufferUtils.createIntBuffer(4);
    private static Win32 win32;
    private static boolean disabled;
    private static boolean wasActive = true;

    private FullscreenWindowRepair() {
    }

    /** Checks the window once; call every frame from the game thread. */
    public static void tick() {
        if (disabled) return;
        try {
            boolean active = Display.isActive();
            boolean fullscreen = Display.isFullscreen();
            if (!active || !fullscreen) {
                wasActive = active;
                POLICY.observe(false, 0, 0, 0, 0);
                return;
            }
            Win32 api = win32();
            if (api == null) return;
            long hwnd = api.hwnd();
            api.clientRect(hwnd, RECT);
            int clientW = RECT.get(2) - RECT.get(0);
            int clientH = RECT.get(3) - RECT.get(1);
            DisplayMode mode = Display.getDisplayMode();
            int modeW = mode.getWidth();
            int modeH = mode.getHeight();
            if (!wasActive) {
                wasActive = true;
                LOG.info("Fullscreen refocused: window client " + clientW + "x" + clientH
                        + ", display mode " + modeW + "x" + modeH);
            }
            switch (POLICY.observe(true, clientW, clientH, modeW, modeH)) {
                case REPAIR:
                    LOG.warn("Fullscreen window is " + clientW + "x" + clientH + " but the display mode is "
                            + modeW + "x" + modeH + "; resizing the window to the mode");
                    if (!api.setWindowPos(hwnd, modeW, modeH)) LOG.warn("SetWindowPos refused the resize");
                    break;
                case GIVE_UP:
                    LOG.warn("Fullscreen window still mismatches the display mode after " + MAX_ATTEMPTS
                            + " resizes; leaving it until the next refocus");
                    break;
                default:
                    break;
            }
        } catch (Throwable failure) {
            disabled = true;
            LOG.warn("Fullscreen window repair failed; disabled for this run", failure);
        }
    }

    private static Win32 win32() throws Throwable {
        if (win32 == null) {
            if (LWJGLUtil.getPlatform() != LWJGLUtil.PLATFORM_WINDOWS) {
                disabled = true;
                return null;
            }
            win32 = Win32.bind();
        }
        return win32;
    }

    /**
     * Decides, frame by frame, whether to resize the window. A mismatch is
     * repaired at most {@link #MAX_ATTEMPTS} times; the budget is refilled once
     * the window matches again or the game loses focus.
     */
    static final class Policy {
        private int attempts;

        Action observe(boolean focusedFullscreen, int clientW, int clientH, int modeW, int modeH) {
            if (!focusedFullscreen || (clientW == modeW && clientH == modeH)) {
                attempts = 0;
                return Action.NONE;
            }
            if (attempts > MAX_ATTEMPTS) return Action.NONE;
            attempts++;
            return attempts > MAX_ATTEMPTS ? Action.GIVE_UP : Action.REPAIR;
        }
    }

    /** LWJGL 2.9's private Windows window handle and Win32 natives. */
    private static final class Win32 {
        private final Object impl;
        private final MethodHandle getHwnd;
        private final MethodHandle getClientRect;
        private final MethodHandle setWindowPos;

        private Win32(Object impl, MethodHandle getHwnd, MethodHandle getClientRect, MethodHandle setWindowPos) {
            this.impl = impl;
            this.getHwnd = getHwnd;
            this.getClientRect = getClientRect;
            this.setWindowPos = setWindowPos;
        }

        static Win32 bind() throws Throwable {
            MethodHandles.Lookup lwjgl = MethodHandles.privateLookupIn(Display.class, MethodHandles.lookup());
            Class<?> implType = lwjgl.findClass("org.lwjgl.opengl.DisplayImplementation");
            Class<?> windowsDisplay = lwjgl.findClass("org.lwjgl.opengl.WindowsDisplay");
            Object impl = lwjgl.findStaticGetter(Display.class, "display_impl", implType).invoke();
            if (!windowsDisplay.isInstance(impl)) {
                throw new IllegalStateException("display implementation is " + impl.getClass().getName());
            }
            // Private members are reachable only through a lookup on their own class.
            MethodHandles.Lookup windows = MethodHandles.privateLookupIn(windowsDisplay, MethodHandles.lookup());
            MethodHandle getHwnd = windows.findVirtual(windowsDisplay, "getHwnd", methodType(long.class))
                    .asType(methodType(long.class, Object.class));
            MethodHandle getClientRect = windows.findStatic(windowsDisplay, "getClientRect",
                    methodType(void.class, long.class, IntBuffer.class));
            MethodHandle setWindowPos = windows.findStatic(windowsDisplay, "setWindowPos",
                    methodType(boolean.class, long.class, long.class, int.class, int.class, int.class, int.class,
                            long.class));
            return new Win32(impl, getHwnd, getClientRect, setWindowPos);
        }

        long hwnd() throws Throwable {
            return (long) getHwnd.invokeExact(impl);
        }

        void clientRect(long hwnd, IntBuffer rect) throws Throwable {
            rect.clear();
            getClientRect.invokeExact(hwnd, rect);
        }

        boolean setWindowPos(long hwnd, int width, int height) throws Throwable {
            return (boolean) setWindowPos.invokeExact(hwnd, 0L, 0, 0, width, height,
                    SWP_NOZORDER | SWP_NOACTIVATE);
        }
    }

    /** Runs the repair on every campaign frame, dialogs and paused screens included. Not saved. */
    public static final class CampaignHook implements EveryFrameScript {
        public static void install(SectorAPI sector) {
            sector.removeScriptsOfClass(CampaignHook.class);
            sector.addTransientScript(new CampaignHook());
        }

        @Override public boolean isDone() { return false; }
        @Override public boolean runWhilePaused() { return true; }
        @Override public void advance(float amount) { tick(); }
    }

    /** Runs the repair on every combat frame, title screen included; listed under {@code plugins} in settings.json. */
    public static final class CombatHook extends BaseEveryFrameCombatPlugin {
        @Override public void advance(float amount, List<InputEventAPI> events) { tick(); }
    }
}
