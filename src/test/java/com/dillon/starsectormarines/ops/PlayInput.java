package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * LWJGL's mouse and keyboard queues as the game's input events, for {@code playBattle}. The screen also polls
 * {@link Keyboard} and {@link Mouse} directly, and those answer as they do in the game because the window is real.
 */
final class PlayInput {

    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;
    private static final int DOUBLE_CLICK_SLOP = 4;

    private long lastClickNanos;
    private int lastClickX;
    private int lastClickY;
    /** {@code -PplayUiScale=1.5}: what the game's UI scale setting would be. */
    static final float UI_SCALE = Float.parseFloat(System.getProperty("play.uiScale", "1"));

    private int pointerX;
    private int pointerY;

    int pointerX() {
        return pointerX;
    }

    int pointerY() {
        return pointerY;
    }

    /** Everything queued since the last call, mouse first. */
    List<InputEventAPI> poll() {
        List<InputEventAPI> events = new ArrayList<>();
        while (Mouse.next()) {
            int x = Math.round(Mouse.getEventX() / UI_SCALE);
            int y = Math.round(Mouse.getEventY() / UI_SCALE);
            int dx = Math.round(Mouse.getEventDX() / UI_SCALE);
            int dy = Math.round(Mouse.getEventDY() / UI_SCALE);
            if (dx != 0 || dy != 0) events.add(move(x, y, dx, dy));
            if (Mouse.getEventDWheel() != 0) {
                events.add(new Event(InputEventType.MOUSE_SCROLL, Mouse.getEventDWheel(), '\0', x, y, 0, 0, false, false));
            }
            if (Mouse.getEventButton() >= 0) events.add(button(Mouse.getEventButton(), Mouse.getEventButtonState(), x, y));
        }
        while (Keyboard.next()) {
            events.add(key(Keyboard.getEventKey(), Keyboard.getEventKeyState(), Keyboard.getEventCharacter(),
                    Keyboard.isRepeatEvent()));
        }
        return events;
    }

    /** A pointer move, moving the pointer the settings report. */
    InputEventAPI move(int x, int y, int dx, int dy) {
        pointerX = x;
        pointerY = y;
        return new Event(InputEventType.MOUSE_MOVE, 0, '\0', x, y, dx, dy, false, false);
    }

    InputEventAPI button(int button, boolean down, int x, int y) {
        pointerX = x;
        pointerY = y;
        boolean doubleClick = false;
        if (down && button == 0) {
            long now = System.nanoTime();
            doubleClick = now - lastClickNanos < DOUBLE_CLICK_NANOS && Math.abs(x - lastClickX) <= DOUBLE_CLICK_SLOP
                    && Math.abs(y - lastClickY) <= DOUBLE_CLICK_SLOP;
            lastClickNanos = doubleClick ? 0L : now;
            lastClickX = x;
            lastClickY = y;
        }
        return new Event(down ? InputEventType.MOUSE_DOWN : InputEventType.MOUSE_UP, button, '\0', x, y, 0, 0,
                false, doubleClick);
    }

    InputEventAPI key(int key, boolean down, char character, boolean repeat) {
        return new Event(down ? InputEventType.KEY_DOWN : InputEventType.KEY_UP, key, character, pointerX, pointerY,
                0, 0, repeat, false);
    }

    private static final class Event implements InputEventAPI {
        private final InputEventType type;
        private final int value;
        private final char character;
        private final int x;
        private final int y;
        private final int dx;
        private final int dy;
        private final boolean repeat;
        private final boolean doubleClick;
        private final boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        private final boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        private final boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        private boolean consumed;

        Event(InputEventType type, int value, char character, int x, int y, int dx, int dy,
              boolean repeat, boolean doubleClick) {
            this.type = type;
            this.value = value;
            this.character = character;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.repeat = repeat;
            this.doubleClick = doubleClick;
        }

        @Override public int getEventValue() { return value; }
        @Override public char getEventChar() { return character; }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int getDX() { return dx; }
        @Override public int getDY() { return dy; }
        @Override public InputEventType getEventType() { return type; }
        @Override public InputEventClass getEventClass() {
            return isKeyboardEvent() ? InputEventClass.KEYBOARD_EVENT : InputEventClass.MOUSE_EVENT;
        }
        @Override public void logEvent() { }
        @Override public boolean isConsumed() { return consumed; }
        @Override public void consume() { consumed = true; }
        @Override public boolean isRepeat() { return repeat; }
        @Override public boolean isKeyboardEvent() { return type == InputEventType.KEY_DOWN || type == InputEventType.KEY_UP; }
        @Override public boolean isMouseEvent() { return !isKeyboardEvent(); }
        @Override public boolean isKeyUpEvent() { return type == InputEventType.KEY_UP; }
        @Override public boolean isKeyDownEvent() { return type == InputEventType.KEY_DOWN; }
        @Override public boolean isMouseUpEvent() { return type == InputEventType.MOUSE_UP; }
        @Override public boolean isMouseDownEvent() { return type == InputEventType.MOUSE_DOWN; }
        @Override public boolean isLMBEvent() { return isLMBDownEvent() || isLMBUpEvent(); }
        @Override public boolean isRMBEvent() { return isRMBDownEvent() || isRMBUpEvent(); }
        @Override public boolean isLMBDownEvent() { return isMouseDownEvent() && value == 0; }
        @Override public boolean isLMBUpEvent() { return isMouseUpEvent() && value == 0; }
        @Override public boolean isRMBDownEvent() { return isMouseDownEvent() && value == 1; }
        @Override public boolean isRMBUpEvent() { return isMouseUpEvent() && value == 1; }
        @Override public boolean isMouseMoveEvent() { return type == InputEventType.MOUSE_MOVE; }
        @Override public boolean isMouseScrollEvent() { return type == InputEventType.MOUSE_SCROLL; }
        @Override public boolean isAltDown() { return alt; }
        @Override public boolean isCtrlDown() { return ctrl; }
        @Override public boolean isShiftDown() { return shift; }
        @Override public boolean isUnmodified() { return !alt && !ctrl && !shift; }
        @Override public boolean isDoubleClick() { return doubleClick; }
        @Override public boolean isModifierKey() {
            return isKeyboardEvent() && switch (value) {
                case Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT, Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL,
                     Keyboard.KEY_LMENU, Keyboard.KEY_RMENU -> true;
                default -> false;
            };
        }
        @Override public boolean isControlDownEvent(String controlEnumName) { return false; }
        @Override public boolean isControlUpEvent(String controlEnumName) { return false; }
        @Override public boolean isControlActivated(String enumName) { return false; }
    }
}
