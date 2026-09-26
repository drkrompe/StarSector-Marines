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

/** Strategic entry above selection; compact exit and pause controls while following one body. */
final class BattleDirectControlOverlay {
    static final String COMPONENT = "battle-direct-control-overlay";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final float DOCUMENT_WIDTH = 440f;
    static final float DOCUMENT_HEIGHT = 52f;
    private static final float SELECTION_GAP = 4f;
    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> label = reactor.signal("");
    private final MutableSignal<String> hint = reactor.signal("");
    private final MutableSignal<Boolean> disabled = reactor.signal(true);
    private final MutableSignal<String> pauseLabel = reactor.signal("");
    private final MutableSignal<String> pauseClasses = reactor.signal("direct-pause direct-pause-hidden");
    private final MutableSignal<Boolean> pauseDisabled = reactor.signal(true);
    private final Selection selection;
    private final Runnable toggle;
    private final Runnable togglePause;
    private PositionAPI position;
    private UiDocument document;
    private MarkupInstance markup;
    private UiViewport viewport;
    private StarsectorUiInputAdapter input;
    private boolean visible;

    private boolean activeMode;

    BattleDirectControlOverlay(Selection selection, Runnable toggle, Runnable togglePause) {
        this.selection = selection;
        this.toggle = toggle;
        this.togglePause = togglePause;
    }

    void attach(PositionAPI position, BattleSimulation sim, float speedMultiplier) {
        this.position = position;
        if (document == null) {
            MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(COMPONENT_PATH));
            markup = loader.reloadAndBuild(reactor, COMPONENT, Map.of(
                    "label", label, "hint", hint, "disabled", disabled, "toggle", toggle,
                    "pauseLabel", pauseLabel, "pauseClasses", pauseClasses,
                    "pauseDisabled", pauseDisabled, "togglePause", togglePause));
            document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
        }
        update(0f, sim, speedMultiplier);
    }

    /** Reserve the taller selection plate even when another carrier is selected, avoiding button jumps. */
    static UiViewport viewport(PositionAPI position) {
        return viewport(position, false);
    }

    static UiViewport viewport(PositionAPI position, boolean active) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = Math.max(0f, Math.min(DOCUMENT_WIDTH * scale, position.getWidth() - 24f));
        if (active) {
            return new UiViewport(position.getX() + (position.getWidth() - width) / 2f,
                    position.getY() + 12f, width, DOCUMENT_HEIGHT * scale, scale);
        }
        UiViewport squad = BattleSquadOverlay.viewport(position);
        UiViewport mech = BattleMechOverlay.viewport(position);
        float selectionTop = Math.max(squad.screenY() + squad.height(),
                mech.screenY() + mech.height());
        return new UiViewport(position.getX() + 12f,
                selectionTop + SELECTION_GAP * scale,
                width,
                DOCUMENT_HEIGHT * scale, scale);
    }

    void update(float dt, BattleSimulation sim, float speedMultiplier) {
        boolean active = sim != null && sim.directControl().active();
        if (active != activeMode && document != null) document.deactivateInput();
        activeMode = active;
        long selected = selection.hasVehicleSelection() ? selection.getSelectedVehicleId()
                : selection.getSelectedUnitEntityId();
        boolean eligible = sim != null && sim.directControl().canEnter(selected);
        visible = sim != null && !sim.isComplete();
        disabled.set(!active && !eligible);
        label.set(Strings.get(active ? "battleDirectExit" : "battleDirectEnter"));
        pauseLabel.set(Strings.get(speedMultiplier == 0f ? "battleSpeed1x" : "battleSpeedPause"));
        pauseClasses.set(active ? "direct-pause" : "direct-pause direct-pause-hidden");
        pauseDisabled.set(!active);
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
        if (position != null && document != null) {
            UiViewport next = viewport(position, active);
            if (!next.equals(viewport) || input == null) {
                document.deactivateInput();
                viewport = next;
                document.layout(viewport.documentWidth(), viewport.documentHeight());
                input = new StarsectorUiInputAdapter(document, viewport);
            }
        }
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
