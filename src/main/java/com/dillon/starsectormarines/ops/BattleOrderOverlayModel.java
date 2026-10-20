package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.squad.ReactiveManningService.Status;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Selection scope and armed-order feedback, independent of the unit detail plates. */
final class BattleOrderOverlayModel {
    private final MutableSignal<String> scope;
    private final MutableSignal<String> hint;
    private final MutableSignal<String> defendClasses;
    private final MutableSignal<String> defendScope;
    private final MutableSignal<String> garrisonClasses;
    private final MutableSignal<String> garrisonDetail;
    private final Consumer<Mode> toggle;
    private final Runnable garrison;

    BattleOrderOverlayModel(Reactor reactor, Consumer<Mode> toggle) {
        this(reactor, toggle, () -> { });
    }

    BattleOrderOverlayModel(Reactor reactor, Consumer<Mode> toggle, Runnable garrison) {
        this.toggle = toggle;
        this.garrison = garrison;
        scope = reactor.signal("");
        hint = reactor.signal("");
        defendClasses = reactor.signal("order-card");
        defendScope = reactor.signal("");
        garrisonClasses = reactor.signal("order-card");
        garrisonDetail = reactor.signal("OFF");
    }

    void update(int count, boolean mech, Mode mode) {
        update(count, mech, mode, mech ? List.of() : List.of(Status.OFF));
    }

    /**
     * As {@link #update(int, boolean, Mode)}, with the GARRISON stance of each selected infantry squad: the card lights
     * when any of them stands in it, and its detail says what the stance is doing.
     */
    void update(int count, boolean mech, Mode mode, List<Status> stances) {
        boolean on = false;
        for (Status status : stances) on |= status != Status.OFF;
        garrisonClasses.set("order-card" + (on ? " selected" : ""));
        garrisonDetail.set(garrisonDetail(stances));
        String formations = count > 1 ? count + " SQUADS" : mech ? "MECH / LANCE" : "SQUAD ORDERS";
        scope.set(formations);
        defendScope.set(count > 1 ? "ALL SELECTED" : mech ? "WHOLE LANCE" : "WHOLE SQUAD");
        hint.set(mode == Mode.NONE ? "RMB: MOVE" : "LMB: PLACE  ·  RMB / ESC: CANCEL");
        defendClasses.set("order-card" + (mode == Mode.DEFEND_AREA ? " selected" : ""));
    }

    Map<String, Object> props() {
        return Map.of("scope", scope, "hint", hint, "defendClasses", defendClasses,
                "defendScope", defendScope,
                "defend", (Runnable) () -> toggle.accept(Mode.DEFEND_AREA),
                "garrisonClasses", garrisonClasses, "garrisonDetail", garrisonDetail,
                "garrison", garrison);
    }

    /** What the GARRISON card says about the selection's stances: one squad's state, or how many stand in it. */
    static String garrisonDetail(List<Status> stances) {
        if (stances.isEmpty()) return "INFANTRY ONLY";
        if (stances.size() > 1) {
            int on = 0;
            for (Status status : stances) if (status != Status.OFF) on++;
            return on == 0 ? "OFF" : on + " OF " + stances.size() + " WATCHING";
        }
        return switch (stances.get(0)) {
            case OFF -> "OFF";
            case WATCHING -> "WATCHING";
            case MANNING_TRENCH -> "MANNING TRENCH";
            case HOLDING_BUILDING -> "HOLDING BUILDING";
            case RETURNING -> "RETURNING TO POST";
            case UNDER_ORDERS -> "ON  ·  UNDER ORDERS";
        };
    }
}
