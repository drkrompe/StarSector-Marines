package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleDirectControlHelp.Layout;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
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
import java.util.function.IntConsumer;

/** Strategic entry above selection; equipment below and separate session controls above during takeover. */
final class BattleDirectControlOverlay {
    static final String COMPONENT = "battle-direct-control-overlay";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final String ACTION_COMPONENT = "battle-direct-control-hud";
    static final String ACTION_COMPONENT_PATH = "data/ui/components/battle/" + ACTION_COMPONENT + ".mlx";
    static final float ACTION_WIDTH = 640f;
    static final float ACTION_HEIGHT = 70f;
    static final String ACTIONS_COMPONENT = "battle-direct-control-actions";
    static final String ACTIONS_COMPONENT_PATH = "data/ui/components/battle/" + ACTIONS_COMPONENT + ".mlx";
    static final float ACTIONS_WIDTH = 154f;
    static final float ACTIONS_HEIGHT = 26f;
    static final float DOCUMENT_WIDTH = 170f;
    static final float DOCUMENT_HEIGHT = 26f;
    private static final float SELECTION_GAP = 4f;
    private final Reactor reactor = new Reactor();
    private final MutableSignal<String> label = reactor.signal("");
    private final MutableSignal<Boolean> disabled = reactor.signal(true);
    private final Selection selection;
    private final Runnable toggle;
    private final BattleDirectControlHudModel actionModel;
    private PositionAPI position;
    private UiDocument document;
    private MarkupInstance markup;
    private UiDocument actionDocument;
    private MarkupInstance actionMarkup;
    private UiDocument actionsDocument;
    private MarkupInstance actionsMarkup;
    private UiViewport actionsViewport;
    private StarsectorUiInputAdapter actionsInput;
    private UiViewport viewport;
    private StarsectorUiInputAdapter input;
    private final BattleDirectControlHelpPlate helpPlate = new BattleDirectControlHelpPlate(reactor);
    private final BattleDirectControlHelp.Hover helpHover = new BattleDirectControlHelp.Hover();
    private MarkupInstance helpMarkup;
    private UiDocument helpDocument;
    private UiViewport helpViewport;
    private Layout helpLayout;
    private Carrier helpCarrier = Carrier.NONE;
    private boolean helpPointerKnown;
    private float helpPointerX, helpPointerY;
    private boolean visible;

    private boolean activeMode;
    private boolean dockTop;

    BattleDirectControlOverlay(Selection selection, Runnable toggle, Runnable togglePause) {
        this(selection, toggle, togglePause, ignored -> {});
    }

    BattleDirectControlOverlay(Selection selection, Runnable toggle, Runnable togglePause,
                              IntConsumer selectWeapon) {
        this.selection = selection;
        this.toggle = toggle;
        actionModel = new BattleDirectControlHudModel(reactor, selectWeapon, toggle, togglePause);
    }

    void setExitLabelKey(String key) { actionModel.setExitLabelKey(key); }

    void attach(PositionAPI position, BattleSimulation sim, float speedMultiplier) {
        this.position = position;
        if (document == null) {
            MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(COMPONENT_PATH));
            markup = loader.reloadAndBuild(reactor, COMPONENT, Map.of(
                    "label", label, "disabled", disabled, "toggle", toggle));
            document = document(markup);
            MarkupLoader actionLoader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(ACTION_COMPONENT_PATH));
            actionMarkup = actionLoader.reloadAndBuild(reactor, ACTION_COMPONENT, actionModel.props());
            actionDocument = document(actionMarkup);
            MarkupLoader actionsLoader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(ACTIONS_COMPONENT_PATH));
            actionsMarkup = actionsLoader.reloadAndBuild(reactor, ACTIONS_COMPONENT, actionModel.actionsProps());
            actionsDocument = document(actionsMarkup);
            MarkupLoader helpLoader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                    List.of(BattleDirectControlHelpPlate.COMPONENT_PATH));
            helpMarkup = helpLoader.reloadAndBuild(reactor, BattleDirectControlHelpPlate.COMPONENT,
                    helpPlate.props());
            helpDocument = document(helpMarkup);
        }
        update(0f, sim, speedMultiplier);
    }

    /** Default bounds for the taller mech detail panel. */
    static UiViewport viewport(PositionAPI position) {
        return viewport(position, false);
    }

    static UiViewport viewport(PositionAPI position, boolean active) {
        return viewport(position, active, false);
    }

    private static UiViewport viewport(PositionAPI position, boolean active, boolean dockTop) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = Math.max(0f, Math.min((active ? ACTION_WIDTH : DOCUMENT_WIDTH) * scale,
                position.getWidth() - 24f));
        if (active) {
            return new UiViewport(position.getX() + (position.getWidth() - width) / 2f,
                    dockTop ? position.getY() + position.getHeight() - 12f
                            - (ACTIONS_HEIGHT + 6f + ACTION_HEIGHT) * scale
                            : position.getY() + 12f,
                    width, ACTION_HEIGHT * scale, scale);
        }
        return entryViewport(position, true);
    }

    /** The shortcut sits just above the selected unit's detail card, aligned to its right edge. */
    static UiViewport entryViewport(PositionAPI position, boolean mech) {
        UiViewport panel = mech ? BattleMechOverlay.viewport(position) : BattleSquadOverlay.viewport(position);
        float scale = panel.documentScale();
        float panelWidth = Math.min(BattleSquadOverlay.PANEL_WIDTH * scale, position.getWidth() - 24f);
        float width = Math.min(DOCUMENT_WIDTH * scale, panelWidth);
        return new UiViewport(panel.screenX() + panelWidth - width,
                panel.screenY() + panel.height() + SELECTION_GAP * scale,
                width, DOCUMENT_HEIGHT * scale, scale);
    }

    static UiViewport actionsViewport(PositionAPI position) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        return new UiViewport(position.getX() + position.getWidth() - 12f - ACTIONS_WIDTH * scale,
                position.getY() + position.getHeight() - 12f - ACTIONS_HEIGHT * scale,
                ACTIONS_WIDTH * scale, ACTIONS_HEIGHT * scale, scale);
    }

    void update(float dt, BattleSimulation sim, float speedMultiplier) {
        boolean active = sim != null && sim.directControl().active();
        if (active != activeMode) {
            deactivateDocuments();
            input = null;
            actionsInput = null;
            helpViewport = null;
            helpHover.collapse();
        }
        activeMode = active;
        if (!active) dockTop = false;
        long selected = selection.hasVehicleSelection() ? selection.getSelectedVehicleId()
                : selection.getSelectedUnitEntityId();
        boolean eligible = sim != null && (sim.directControl().canEnter(selected)
                || selection.hasWarshipSelection()
                && sim.directControl().canEnterWarship(selection.getSelectedWarship()));
        boolean nextVisible = sim != null && !sim.isComplete() && (active || eligible);
        if (visible && !nextVisible) deactivateDocuments();
        visible = nextVisible;
        disabled.set(!active && !eligible);
        label.set(Strings.get("battleDirectEnter"));
        if (active) {
            var status = BattleDirectControlStatus.snapshot(sim);
            helpCarrier = status.carrier();
            actionModel.update(status, sim.directControl().selectedWeapon(), speedMultiplier == 0f);
        }
        updateHelp(dt, active);
        if (markup != null) markup.flush();
        if (actionMarkup != null) actionMarkup.flush();
        if (actionsMarkup != null) actionsMarkup.flush();
        if (helpMarkup != null) helpMarkup.flush();
        UiDocument current = currentDocument();
        if (position != null && current != null) {
            UiViewport next = active ? viewport(position, true, dockTop)
                    : entryViewport(position, eligible && selected != 0L && sim.world().hasMechLoadout(selected));
            if (!next.equals(viewport) || input == null) {
                deactivateDocuments();
                viewport = next;
                current.layout(viewport.documentWidth(), viewport.documentHeight());
                input = new StarsectorUiInputAdapter(current, viewport);
            }
        }
        if (current != null) current.advance(dt);
        if (active && position != null && actionsDocument != null) {
            UiViewport next = actionsViewport(position);
            if (!next.equals(actionsViewport) || actionsInput == null) {
                actionsDocument.deactivateInput();
                actionsViewport = next;
                actionsDocument.layout(next.documentWidth(), next.documentHeight());
                actionsInput = new StarsectorUiInputAdapter(actionsDocument, next);
            }
            actionsDocument.advance(dt);
        }
    }

    /** The help tab is hover-only: pointer events are observed here, never consumed or acted on. */
    private void trackPointer(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            // An earlier handler may already have consumed the event; its coordinates are then unreadable.
            if (event.isConsumed()) continue;
            if (event.isMouseMoveEvent() || event.isLMBDownEvent() || event.isLMBUpEvent()
                    || event.isRMBDownEvent() || event.isRMBUpEvent()) {
                helpPointerKnown = true;
                helpPointerX = event.getX();
                helpPointerY = event.getY();
            }
        }
    }

    private void updateHelp(float dt, boolean active) {
        if (helpDocument == null || position == null || !active) {
            helpLayout = null;
            helpHover.collapse();
            return;
        }
        UiViewport strip = actionsViewport(position);
        float top = strip.screenY() + strip.height();
        Layout layout = BattleDirectControlHelp.layout(strip.screenX(), top, strip.documentScale(),
                BattleDirectControlHelpPlate.rowCount(helpCarrier));
        helpLayout = layout;
        boolean expanded = helpHover.update(true, helpPointerKnown, helpPointerX, helpPointerY, layout);
        helpPlate.update(expanded, helpCarrier);
        helpMarkup.flush();
        var bounds = expanded ? layout.union() : layout.tab();
        float scale = strip.documentScale();
        UiViewport next = new UiViewport(bounds.x(), bounds.y(), bounds.width(), bounds.height(), scale);
        if (!next.equals(helpViewport)) {
            helpViewport = next;
            helpDocument.layout(next.documentWidth(), next.documentHeight());
        }
        helpDocument.advance(dt);
    }

    void processInput(List<InputEventAPI> events) {
        if (events != null) trackPointer(events);
        if (visible && activeMode && actionsInput != null && events != null) actionsInput.process(events);
        if (visible && input != null && events != null) input.process(events);
    }

    void render(float alpha) {
        UiDocument current = currentDocument();
        if (visible && current != null) current.render(viewport, alpha);
        if (visible && activeMode && actionsDocument != null) actionsDocument.render(actionsViewport, alpha);
        if (visible && activeMode && helpDocument != null && helpViewport != null) helpDocument.render(helpViewport, alpha);
    }

    boolean blocksWorldPointer(float x, float y) {
        return visible && (contains(viewport, x, y) || activeMode && contains(actionsViewport, x, y)
                || activeMode && helpLayout != null && helpHover.blocks(true, x, y, helpLayout));
    }

    private static boolean contains(UiViewport bounds, float x, float y) {
        return bounds != null && x >= bounds.screenX() && x < bounds.screenX() + bounds.width()
                && y >= bounds.screenY() && y < bounds.screenY() + bounds.height();
    }

    void detach() {
        deactivateDocuments();
        input = null;
        actionsInput = null;
        helpViewport = null;
        helpHover.collapse();
    }

    /** Map clamping can put the body at the bottom edge; move chrome instead of revealing off-map ground. */
    void avoidControlledBody(float screenX, float screenY, float screenRadius) {
        if (!activeMode || position == null || actionDocument == null) return;
        UiViewport next = actionViewport(position, screenX, screenY, screenRadius);
        dockTop = next.screenY() != viewport(position, true).screenY();
        if (next.equals(viewport)) return;
        deactivateDocuments();
        viewport = next;
        actionDocument.layout(next.documentWidth(), next.documentHeight());
        input = new StarsectorUiInputAdapter(actionDocument, next);
    }

    static UiViewport actionViewport(PositionAPI position, float screenX, float screenY, float screenRadius) {
        UiViewport bottom = viewport(position, true);
        float radius = Math.max(0f, screenRadius) + 8f;
        boolean overlaps = screenX + radius > bottom.screenX()
                && screenX - radius < bottom.screenX() + bottom.width()
                && screenY + radius > bottom.screenY()
                && screenY - radius < bottom.screenY() + bottom.height();
        return overlaps ? viewport(position, true, true) : bottom;
    }

    private UiDocument currentDocument() { return activeMode ? actionDocument : document; }

    private void deactivateDocuments() {
        if (document != null) document.deactivateInput();
        if (actionDocument != null) actionDocument.deactivateInput();
        if (actionsDocument != null) actionsDocument.deactivateInput();
    }

    static UiDocument document(MarkupInstance markup) {
        UiDocument result = new UiDocument(markup.root());
        for (var style : markup.styles()) result.addStyleSheet(style);
        result.theme(MarineOpsThemes.standard());
        return result;
    }
}
