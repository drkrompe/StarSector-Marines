package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.util.List;
import java.util.Map;

/** Presentation only: the host supplies permitted ships and simulation-owned commands. */
final class BattleShipCommandOverlayModel {
    private final MutableSignal<List<ShipCard>> ships;
    private final MutableSignal<List<ActionCard>> actions;
    private final MutableSignal<String> title;
    private final MutableSignal<String> hint;
    private final MutableSignal<String> heroStyle;
    private final MutableSignal<String> actionStyle;
    private State state = State.EMPTY;

    BattleShipCommandOverlayModel(Reactor reactor) {
        ships = reactor.signal(List.of());
        actions = reactor.signal(List.of());
        title = reactor.signal("");
        hint = reactor.signal("");
        heroStyle = reactor.signal("width: 112px;");
        actionStyle = reactor.signal("width: 488px;");
    }

    Map<String, Object> props() {
        return Map.of("ships", ships, "actions", actions, "title", title, "hint", hint, "heroStyle", heroStyle, "actionStyle", actionStyle);
    }

    State update(State next) {
        state = next == null ? State.EMPTY : next;
        ships.set(state.ships().stream().map(ShipCard::new).toList());
        actions.set(state.actions().stream().map(ActionCard::new).toList());
        heroStyle.set("width: " + (BattleShipCommandOverlay.heroWidth(state.ships().size()) - 12f) + "px;");
        actionStyle.set("width: " + (BattleShipCommandOverlay.actionWidth(state.actions().size()) - 12f) + "px;");
        title.set(state.selectedName());
        hint.set(state.hint());
        return state;
    }

    void hover(String id) {
        String text = state.hint();
        for (ActionState action : state.actions()) {
            if (("ship-action-" + action.id()).equals(id)) text = action.hint();
        }
        for (ShipState ship : state.ships()) {
            if (("ship-hero-" + ship.id()).equals(id)) text = Strings.format("battleShipCommandSelectHint", ship.name(), ship.status());
        }
        hint.set(text);
    }

    record State(List<ShipState> ships, String selectedName, String hint, List<ActionState> actions) {
        static final State EMPTY = new State(List.of(), "", "", List.of());
        State {
            ships = ships == null ? List.of() : List.copyOf(ships);
            actions = actions == null ? List.of() : List.copyOf(actions);
            selectedName = selectedName == null ? "" : selectedName;
            hint = hint == null ? "" : hint;
        }
        boolean visible() { return !ships.isEmpty(); }
        boolean selected() { return !selectedName.isEmpty(); }
    }
    record ShipState(String id, String name, String icon, String status,
                     float hullFraction, boolean selected, Runnable select) {}
    record ActionState(String id, String label, String icon, String hint, String status,
                       boolean enabled, boolean armed, Runnable action) {}

    record ShipCard(ShipState ship) implements MarkupPropertySource {
        public Object markupProperty(String property) {
            String id = "ship-hero-" + ship.id();
            return switch (property) {
                case "id" -> id;
                case "imageId" -> id + "-image";
                case "nameId" -> id + "-name";
                case "statusId" -> id + "-status";
                case "fillId" -> id + "-fill";
                case "trackId" -> id + "-track";
                case "name" -> ship.name();
                case "icon" -> ship.icon();
                case "status" -> ship.status();
                case "classes" -> ship.selected() ? "ship-hero ship-selected" : "ship-hero";
                case "fillStyle" -> "width: " + Math.round(Math.max(0f, Math.min(1f, ship.hullFraction())) * 100f) + "%;";
                case "action" -> ship.select();
                default -> throw new IllegalArgumentException(property);
            };
        }
    }
    record ActionCard(ActionState action) implements MarkupPropertySource {
        public Object markupProperty(String property) {
            String id = "ship-action-" + action.id();
            return switch (property) {
                case "id" -> id;
                case "imageId" -> id + "-image";
                case "labelId" -> id + "-label";
                case "statusId" -> id + "-status";
                case "label" -> action.label();
                case "icon" -> action.icon();
                case "status" -> action.status();
                case "disabled" -> !action.enabled();
                case "classes" -> !action.enabled() ? "ship-action ship-disabled" : action.armed() ? "ship-action ship-selected" : "ship-action";
                case "action" -> action.action();
                default -> throw new IllegalArgumentException(property);
            };
        }
    }
}
