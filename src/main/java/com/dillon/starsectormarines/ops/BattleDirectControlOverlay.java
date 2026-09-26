package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;
import java.util.Map;

/** A bounded, retained enter/exit button and control legend above the infantry roster. */
final class BattleDirectControlOverlay {
    static final String COMPONENT = "battle-direct-control-overlay";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> label = reactor.signal("");
    private final MutableSignal<String> hint = reactor.signal("");
    private final MutableSignal<Boolean> disabled = reactor.signal(true);
    private final Selection selection;
    private final Runnable toggle;
    private UiDocument document;
    private MarkupInstance markup;
    private UiViewport viewport;
    private StarsectorUiInputAdapter input;
    private boolean visible;

    BattleDirectControlOverlay(Selection selection, Runnable toggle) {
        this.selection = selection;
        this.toggle = toggle;
    }

    void attach(PositionAPI position, BattleSimulation sim) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = Math.min(440f * scale, position.getWidth() - 24f);
        UiViewport squad = BattleSquadOverlay.viewport(position);
        viewport = new UiViewport(position.getX() + 12f,
                squad.screenY() + squad.height() + 8f * scale,
                width, 64f * scale, scale);
        if (document == null) {
            MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(COMPONENT_PATH));
            markup = loader.reloadAndBuild(reactor, COMPONENT, Map.of(
                    "label", label, "hint", hint, "disabled", disabled, "toggle", toggle));
            document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
        }
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
        update(0f, sim);
    }

    void update(float dt, BattleSimulation sim) {
        boolean active = sim != null && sim.directControl().active();
        long selected = selection.hasVehicleSelection() ? selection.getSelectedVehicleId()
                : selection.getSelectedUnitEntityId();
        boolean eligible = sim != null && sim.directControl().canEnter(selected);
        visible = sim != null && !sim.isComplete();
        disabled.set(!active && !eligible);
        label.set(Strings.get(active ? "battleDirectExit" : "battleDirectEnter"));
        if (active) {
            String name = sim.identity().name(sim.directControl().activeUnitId());
            boolean mech = sim.directControl().controlledMechId() != 0L;
            boolean vehicle = sim.directControl().controlledVehicleId() != 0L;
            hint.set((name == null ? "" : name + "  |  ")
                    + Strings.get(vehicle ? "battleDirectVehicleControls"
                            : mech ? "battleDirectMechControls" : "battleDirectControls"));
        } else {
            hint.set(Strings.get(eligible ? "battleDirectReady" : "battleDirectSelect"));
        }
        if (markup != null) markup.flush();
        if (document != null) document.advance(dt);
    }

    void processInput(List<InputEventAPI> events) {
        if (visible && input != null && events != null) input.process(events);
    }

    void render(float alpha) {
        if (visible && document != null) document.render(viewport, alpha);
    }

    boolean blocksWorldPointer(float x, float y) {
        return visible && viewport != null && x >= viewport.screenX()
                && x < viewport.screenX() + viewport.width() && y >= viewport.screenY()
                && y < viewport.screenY() + viewport.height();
    }

    void detach() {
        if (document != null) document.deactivateInput();
        input = null;
    }
}
