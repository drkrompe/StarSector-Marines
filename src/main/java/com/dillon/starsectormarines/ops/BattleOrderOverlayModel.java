package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
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
        garrisonDetail = reactor.signal(Strings.get("battleGarrisonOff"));
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
        String formations = count > 1 ? Strings.format("battleOrderSquads", count) : Strings.get(mech ? "battleOrderLance" : "battleOrderSquad");
        scope.set(formations);
        defendScope.set(Strings.get(count > 1 ? "battleOrderAllSelected" : mech ? "battleOrderWholeLance" : "battleOrderWholeSquad"));
        hint.set(Strings.get(mode == Mode.NONE ? "battleOrderHintMove" : "battleOrderHintPlace"));
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
        if (stances.isEmpty()) return Strings.get("battleGarrisonInfantryOnly");
        if (stances.size() > 1) {
            int on = 0;
            for (Status status : stances) if (status != Status.OFF) on++;
            return on == 0 ? Strings.get("battleGarrisonOff") : Strings.format("battleGarrisonSomeWatching", on, stances.size());
        }
        return switch (stances.get(0)) {
            case OFF -> Strings.get("battleGarrisonOff");
            case WATCHING -> Strings.get("battleGarrisonWatching");
            case MANNING_TRENCH -> Strings.get("battleGarrisonTrench");
            case HOLDING_BUILDING -> Strings.get("battleGarrisonBuilding");
            case RETURNING -> Strings.get("battleGarrisonReturning");
            case UNDER_ORDERS -> Strings.get("battleGarrisonOrders");
        };
    }
}
