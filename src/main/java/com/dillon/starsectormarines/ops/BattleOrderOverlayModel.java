package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.Map;
import java.util.function.Consumer;

/** Selection scope and armed-order feedback, independent of the unit detail plates. */
final class BattleOrderOverlayModel {
    private final MutableSignal<String> scope;
    private final MutableSignal<String> hint;
    private final MutableSignal<String> defendClasses;
    private final MutableSignal<String> defendScope;
    private final Consumer<Mode> toggle;

    BattleOrderOverlayModel(Reactor reactor, Consumer<Mode> toggle) {
        this.toggle = toggle;
        scope = reactor.signal("");
        hint = reactor.signal("");
        defendClasses = reactor.signal("order-card");
        defendScope = reactor.signal("");
    }

    void update(int count, boolean mech, Mode mode) {
        String formations = count > 1 ? count + " SQUADS" : mech ? "MECH / LANCE" : "SQUAD ORDERS";
        scope.set(formations);
        defendScope.set(count > 1 ? "ALL SELECTED" : mech ? "WHOLE LANCE" : "WHOLE SQUAD");
        hint.set(mode == Mode.NONE ? "RMB: MOVE" : "LMB: PLACE  ·  RMB / ESC: CANCEL");
        defendClasses.set("order-card" + (mode == Mode.DEFEND_AREA ? " selected" : ""));
    }

    Map<String, Object> props() {
        return Map.of("scope", scope, "hint", hint, "defendClasses", defendClasses,
                "defendScope", defendScope,
                "defend", (Runnable) () -> toggle.accept(Mode.DEFEND_AREA));
    }
}
