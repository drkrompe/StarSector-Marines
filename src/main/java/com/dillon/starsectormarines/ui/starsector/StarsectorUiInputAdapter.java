package com.dillon.starsectormarines.ui.starsector;

import com.dillon.starsectormarines.ui.retained.KeyModifier;
import com.dillon.starsectormarines.ui.retained.PointerButton;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiKey;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The only retained-UI seam that knows Starsector input objects or LWJGL key
 * codes. Every event crosses into document coordinates and semantic names once.
 */
public final class StarsectorUiInputAdapter {

    private static final float WHEEL_PIXELS_PER_NOTCH = 53f;

    private final UiDocument document;
    private final UiViewport viewport;

    public StarsectorUiInputAdapter(UiDocument document, UiViewport viewport) {
        if (document == null || viewport == null) {
            throw new IllegalArgumentException("document and viewport must not be null");
        }
        this.document = document;
        this.viewport = viewport;
    }

    public void process(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI event : events) {
            // Starsector invalidates access to portions of an event after consume().
            if (event.isConsumed()) continue;
            if (event.isMouseMoveEvent()) {
                document.pointerMoved(documentX(event), documentY(event));
            } else if (event.isMouseScrollEvent()) {
                int raw = event.getEventValue();
                float delta = raw > 0f ? -WHEEL_PIXELS_PER_NOTCH
                        : raw < 0f ? WHEEL_PIXELS_PER_NOTCH : 0f;
                if (document.pointerScrolled(documentX(event), documentY(event), delta)) {
                    event.consume();
                }
            } else if (event.isLMBDownEvent()) {
                if (document.pointerDown(documentX(event), documentY(event),
                        PointerButton.PRIMARY)) event.consume();
            } else if (event.isLMBUpEvent()) {
                if (document.pointerUp(documentX(event), documentY(event),
                        PointerButton.PRIMARY)) event.consume();
            } else if (event.isRMBDownEvent()) {
                if (document.pointerDown(documentX(event), documentY(event),
                        PointerButton.SECONDARY)) event.consume();
            } else if (event.isRMBUpEvent()) {
                if (document.pointerUp(documentX(event), documentY(event),
                        PointerButton.SECONDARY)) event.consume();
            } else {
                processKey(event);
            }
        }
    }

    private void processKey(InputEventAPI event) {
        boolean repeat = event.isRepeat();
        boolean down = event.isKeyDownEvent() || repeat;
        boolean up = event.isKeyUpEvent();
        if (!down && !up) return;
        UiKey key = keyFor(event.getEventValue());
        if (key == null) return;
        Set<KeyModifier> modifiers = modifiers(event);
        boolean handled = down
                ? document.keyPressed(key, modifiers, repeat)
                : document.keyReleased(key, modifiers);
        if (handled) event.consume();
    }

    private float documentX(InputEventAPI event) {
        return viewport.documentX(event.getX());
    }

    private float documentY(InputEventAPI event) {
        return viewport.documentY(event.getY());
    }

    static Set<KeyModifier> modifiers(InputEventAPI event) {
        EnumSet<KeyModifier> modifiers = EnumSet.noneOf(KeyModifier.class);
        if (event.isShiftDown()) modifiers.add(KeyModifier.SHIFT);
        if (event.isCtrlDown()) modifiers.add(KeyModifier.CONTROL);
        if (event.isAltDown()) modifiers.add(KeyModifier.ALT);
        return modifiers;
    }

    static UiKey keyFor(int key) {
        return switch (key) {
            case Keyboard.KEY_TAB -> UiKey.TAB;
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> UiKey.ENTER;
            case Keyboard.KEY_ESCAPE -> UiKey.ESCAPE;
            case Keyboard.KEY_SPACE -> UiKey.SPACE;
            case Keyboard.KEY_UP -> UiKey.ARROW_UP;
            case Keyboard.KEY_DOWN -> UiKey.ARROW_DOWN;
            case Keyboard.KEY_LEFT -> UiKey.ARROW_LEFT;
            case Keyboard.KEY_RIGHT -> UiKey.ARROW_RIGHT;
            case Keyboard.KEY_HOME -> UiKey.HOME;
            case Keyboard.KEY_END -> UiKey.END;
            case Keyboard.KEY_PRIOR -> UiKey.PAGE_UP;
            case Keyboard.KEY_NEXT -> UiKey.PAGE_DOWN;
            case Keyboard.KEY_BACK -> UiKey.BACKSPACE;
            case Keyboard.KEY_DELETE -> UiKey.DELETE;
            case Keyboard.KEY_A -> UiKey.A;
            case Keyboard.KEY_C -> UiKey.C;
            case Keyboard.KEY_V -> UiKey.V;
            case Keyboard.KEY_X -> UiKey.X;
            default -> null;
        };
    }
}
