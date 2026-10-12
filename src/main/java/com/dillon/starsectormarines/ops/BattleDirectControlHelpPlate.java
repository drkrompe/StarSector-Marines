package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Reactive props for the help tab's markup. Display only: it owns no input and no game state. */
final class BattleDirectControlHelpPlate {
    static final String COMPONENT = "battle-direct-control-help";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";

    private final Reactor reactor;
    private final Function<String, String> copy;
    private final Map<String, Object> props = new LinkedHashMap<>();
    private final MutableSignal<String> tabClasses;
    private final MutableSignal<String> panelClasses;
    private final MutableSignal<String> title;
    private final MutableSignal<List<Row>> rows;
    private boolean lastExpanded;
    private Carrier lastCarrier = Carrier.NONE;
    private boolean primed;

    BattleDirectControlHelpPlate(Reactor reactor) {
        this(reactor, Strings::get);
    }

    BattleDirectControlHelpPlate(Reactor reactor, Function<String, String> copy) {
        this.reactor = reactor;
        this.copy = copy;
        tabClasses = reactor.signal("help-tab");
        panelClasses = reactor.signal("help-panel help-panel-hidden");
        title = reactor.signal("");
        rows = reactor.signal(List.of());
        props.put("tabMark", copy.apply("battleDirectHelpTabMark"));
        props.put("tabLabel", copy.apply("battleDirectHelpTab"));
        props.put("tabClasses", tabClasses);
        props.put("panelClasses", panelClasses);
        props.put("title", title);
        props.put("rows", rows);
    }

    Map<String, Object> props() { return props; }

    /** Row count the expanded panel will show for a carrier. */
    static int rowCount(Carrier carrier) { return BattleDirectControlHelp.lines(carrier).size(); }

    void update(boolean expanded, Carrier carrier) {
        if (primed && expanded == lastExpanded && carrier == lastCarrier) return;
        primed = true;
        lastExpanded = expanded;
        lastCarrier = carrier;
        reactor.untracked(() -> {
            tabClasses.set(expanded ? "help-tab help-tab-open" : "help-tab");
            panelClasses.set(expanded ? "help-panel" : "help-panel help-panel-hidden");
            List<Row> built = new ArrayList<>();
            if (expanded) {
                title.set(copy.apply(BattleDirectControlHelp.titleKey(carrier)));
                int index = 0;
                for (var line : BattleDirectControlHelp.lines(carrier)) {
                    built.add(new Row("direct-help-row-" + index++, line.keys(), copy.apply(line.textKey())));
                }
            } else {
                title.set("");
            }
            rows.set(List.copyOf(built));
        });
    }

    record Row(String id, String keys, String text) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> id;
                case "keys" -> keys;
                case "text" -> text;
                case "keysId" -> id + "-keys";
                case "textId" -> id + "-text";
                default -> throw new IllegalArgumentException("Unknown help row property: " + key);
            };
        }
    }
}
