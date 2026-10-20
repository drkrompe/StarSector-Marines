package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Strategic-only hotkeys; a held key never repeatedly toggles placement. */
final class BattleOrderShortcuts {
    private final Set<Integer> held = new HashSet<>();

    void process(List<InputEventAPI> events, boolean enabled, Consumer<Mode> toggle) {
        process(events, enabled, toggle, null);
    }

    /** As above, with {@code G} toggling the selection's GARRISON stance when {@code garrison} is given. */
    void process(List<InputEventAPI> events, boolean enabled, Consumer<Mode> toggle, Runnable garrison) {
        for (InputEventAPI event : events) {
            if (event.isConsumed() || (!event.isKeyDownEvent() && !event.isKeyUpEvent())) continue;
            int key = event.getEventValue();
            boolean stance = key == Keyboard.KEY_G && garrison != null;
            Mode mode = key == Keyboard.KEY_F ? Mode.DEFEND_AREA : Mode.NONE;
            if (mode == Mode.NONE && !stance) continue;
            if (event.isKeyUpEvent()) {
                if (held.remove(key)) event.consume();
                continue;
            }
            if (!enabled || event.isCtrlDown() || event.isAltDown() || event.isShiftDown()) continue;
            if (held.add(key)) {
                if (stance) garrison.run();
                else toggle.accept(mode);
            }
            event.consume();
        }
    }

    void clear() { held.clear(); }
}
