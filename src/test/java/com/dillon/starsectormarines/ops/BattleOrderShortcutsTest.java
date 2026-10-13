package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.fs.starfarer.api.input.InputEventAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BattleOrderShortcutsTest {
    @Test void heldKeyArmsOnceAndReleaseAllowsTheNextPress() {
        var shortcuts = new BattleOrderShortcuts();
        List<Mode> actions = new ArrayList<>();
        var down = event(Keyboard.KEY_F, true, false);
        var repeat = event(Keyboard.KEY_F, true, false);
        shortcuts.process(List.of(down, repeat), true, actions::add);
        assertEquals(List.of(Mode.DEFEND_AREA), actions);
        assertTrue(down.isConsumed());
        assertTrue(repeat.isConsumed());
        var up = event(Keyboard.KEY_F, false, false);
        shortcuts.process(List.of(up), false, actions::add);
        assertTrue(up.isConsumed());
        shortcuts.process(List.of(event(Keyboard.KEY_F, true, false)), true, actions::add);
        assertEquals(List.of(Mode.DEFEND_AREA, Mode.DEFEND_AREA), actions);
    }

    @Test void hiddenModifiedConsumedAndUnrelatedKeysPassThrough() {
        var shortcuts = new BattleOrderShortcuts();
        List<Mode> actions = new ArrayList<>();
        var hidden = event(Keyboard.KEY_F, true, false);
        shortcuts.process(List.of(hidden), false, actions::add);
        assertFalse(hidden.isConsumed());
        var modified = event(Keyboard.KEY_F, true, true);
        var unrelated = event(Keyboard.KEY_W, true, false);
        var consumed = event(Keyboard.KEY_F, true, false);
        consumed.consume();
        shortcuts.process(List.of(modified, unrelated, consumed), true, actions::add);
        assertFalse(modified.isConsumed());
        assertFalse(unrelated.isConsumed());
        assertTrue(actions.isEmpty());
    }

    @Test void cameraPanKeysAndTheRetiredAttackMoveKeyAreNeverClaimed() {
        var shortcuts = new BattleOrderShortcuts();
        List<Mode> actions = new ArrayList<>();
        var keys = List.of(event(Keyboard.KEY_W, true, false), event(Keyboard.KEY_A, true, false),
                event(Keyboard.KEY_S, true, false), event(Keyboard.KEY_D, true, false),
                event(Keyboard.KEY_Q, true, false));
        shortcuts.process(keys, true, actions::add);
        assertTrue(actions.isEmpty());
        keys.forEach(key -> assertFalse(key.isConsumed()));
    }

    private static InputEventAPI event(int key, boolean down, boolean modified) {
        boolean[] consumed = {false};
        return (InputEventAPI) Proxy.newProxyInstance(InputEventAPI.class.getClassLoader(),
                new Class<?>[]{InputEventAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isKeyDownEvent" -> down;
                    case "isKeyUpEvent" -> !down;
                    case "getEventValue" -> key;
                    case "isCtrlDown" -> modified;
                    case "isConsumed" -> consumed[0];
                    case "consume" -> { consumed[0] = true; yield null; }
                    default -> { if (method.getReturnType() == boolean.class) yield false;
                        throw new AssertionError(method.getName()); }
                });
    }
}
