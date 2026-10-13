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
        var down = event(Keyboard.KEY_A, true, false);
        var repeat = event(Keyboard.KEY_A, true, false);
        shortcuts.process(List.of(down, repeat), true, actions::add);
        assertEquals(List.of(Mode.ATTACK_MOVE), actions);
        assertTrue(down.isConsumed());
        assertTrue(repeat.isConsumed());
        var up = event(Keyboard.KEY_A, false, false);
        shortcuts.process(List.of(up), false, actions::add);
        assertTrue(up.isConsumed());
        shortcuts.process(List.of(event(Keyboard.KEY_A, true, false),
                event(Keyboard.KEY_F, true, false)), true, actions::add);
        assertEquals(List.of(Mode.ATTACK_MOVE, Mode.ATTACK_MOVE, Mode.DEFEND_AREA), actions);
    }

    @Test void hiddenModifiedConsumedAndUnrelatedKeysPassThrough() {
        var shortcuts = new BattleOrderShortcuts();
        List<Mode> actions = new ArrayList<>();
        var hidden = event(Keyboard.KEY_A, true, false);
        shortcuts.process(List.of(hidden), false, actions::add);
        assertFalse(hidden.isConsumed());
        var modified = event(Keyboard.KEY_F, true, true);
        var unrelated = event(Keyboard.KEY_W, true, false);
        var consumed = event(Keyboard.KEY_A, true, false);
        consumed.consume();
        shortcuts.process(List.of(modified, unrelated, consumed), true, actions::add);
        assertFalse(modified.isConsumed());
        assertFalse(unrelated.isConsumed());
        assertTrue(actions.isEmpty());
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
