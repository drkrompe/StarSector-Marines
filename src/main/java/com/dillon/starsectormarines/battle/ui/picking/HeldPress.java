package com.dillon.starsectormarines.battle.ui.picking;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;

/**
 * A primary press the {@link WorldPicker} held back from see-through chrome,
 * copied before the original was consumed (Starsector refuses reads of a
 * consumed event). When the gesture turns out to be a click, this copy is what
 * the chrome receives, just ahead of the release.
 */
final class HeldPress implements InputEventAPI {

    private final int x;
    private final int y;
    private final int eventValue;
    private final InputEventClass eventClass;
    private final InputEventType eventType;
    private final boolean shift;
    private final boolean ctrl;
    private final boolean alt;
    private final boolean doubleClick;
    private boolean consumed;

    private HeldPress(InputEventAPI press) {
        x = press.getX();
        y = press.getY();
        eventValue = press.getEventValue();
        eventClass = press.getEventClass();
        eventType = press.getEventType();
        shift = press.isShiftDown();
        ctrl = press.isCtrlDown();
        alt = press.isAltDown();
        doubleClick = press.isDoubleClick();
    }

    /** Copies an unconsumed primary press. */
    static HeldPress of(InputEventAPI press) {
        return new HeldPress(press);
    }

    @Override public int getEventValue() { return eventValue; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getDX() { return 0; }
    @Override public int getDY() { return 0; }
    @Override public InputEventClass getEventClass() { return eventClass; }
    @Override public void logEvent() {}
    @Override public boolean isConsumed() { return consumed; }
    @Override public void consume() { consumed = true; }
    @Override public boolean isRepeat() { return false; }
    @Override public InputEventType getEventType() { return eventType; }
    @Override public boolean isMouseEvent() { return true; }
    @Override public boolean isKeyboardEvent() { return false; }
    @Override public boolean isKeyUpEvent() { return false; }
    @Override public boolean isKeyDownEvent() { return false; }
    @Override public boolean isMouseUpEvent() { return false; }
    @Override public boolean isMouseDownEvent() { return true; }
    @Override public boolean isLMBDownEvent() { return true; }
    @Override public boolean isLMBEvent() { return true; }
    @Override public boolean isRMBEvent() { return false; }
    @Override public boolean isLMBUpEvent() { return false; }
    @Override public boolean isRMBDownEvent() { return false; }
    @Override public boolean isRMBUpEvent() { return false; }
    @Override public boolean isMouseMoveEvent() { return false; }
    @Override public boolean isMouseScrollEvent() { return false; }
    @Override public char getEventChar() { return '\0'; }
    @Override public boolean isAltDown() { return alt; }
    @Override public boolean isCtrlDown() { return ctrl; }
    @Override public boolean isShiftDown() { return shift; }
    @Override public boolean isUnmodified() { return !shift && !ctrl && !alt; }
    @Override public boolean isDoubleClick() { return doubleClick; }
    @Override public boolean isModifierKey() { return false; }
    @Override public boolean isControlDownEvent(String controlEnumName) { return false; }
    @Override public boolean isControlUpEvent(String controlEnumName) { return false; }
    @Override public boolean isControlActivated(String enumName) { return false; }
}
