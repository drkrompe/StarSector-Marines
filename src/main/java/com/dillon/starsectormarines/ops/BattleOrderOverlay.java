package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel;
import com.dillon.starsectormarines.battle.ui.panel.SquadOrderTargetingPanel.Mode;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Compact contextual orders above the fleet-support tray. */
final class BattleOrderOverlay {
    static final String COMPONENT = "battle-order-overlay";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final float WIDTH = 420f;
    static final float HEIGHT = 70f;
    private final Reactor reactor = new Reactor();
    private final Selection selection;
    private final Supplier<Mode> targeting;
    private final Consumer<Mode> toggle;
    private final BattleOrderOverlayModel model;
    private final BattleOrderShortcuts shortcuts = new BattleOrderShortcuts();
    private UiDocument document;
    private MarkupInstance markup;
    private UiViewport viewport;
    private StarsectorUiInputAdapter input;
    private PositionAPI position;
    private boolean visible;

    BattleOrderOverlay(Selection selection, Supplier<Mode> targeting, Consumer<Mode> toggle) {
        this.selection = selection;
        this.targeting = targeting;
        this.toggle = toggle;
        model = new BattleOrderOverlayModel(reactor, toggle);
    }

    void attach(PositionAPI position, BattleSimulation sim, float supportHeight) {
        this.position = position;
        if (document == null) {
            MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
            markup = loader.reloadAndBuild(reactor, COMPONENT, model.props());
            document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
        }
        update(0f, sim, supportHeight);
    }

    void update(float dt, BattleSimulation sim, float supportHeight) {
        boolean nextVisible = SquadOrderTargetingPanel.canOrderSquad(sim, selection.getSelectedSquadId());
        if (!nextVisible && visible) detach();
        visible = nextVisible;
        if (visible) model.update(selection.squadCount(), sim.getSquad(selection.getSelectedSquadId()).isMechSquad(), targeting.get());
        if (markup == null || position == null) return;
        markup.flush();
        UiViewport next = viewport(position, supportHeight);
        if (!next.equals(viewport) || input == null) {
            document.deactivateInput();
            viewport = next;
            document.layout(next.documentWidth(), next.documentHeight());
            input = new StarsectorUiInputAdapter(document, next);
        }
        document.advance(dt);
    }

    static UiViewport viewport(PositionAPI position, float supportHeight) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        float width = WIDTH * scale;
        float centered = position.getX() + (position.getWidth() - width) / 2f;
        float besideSelection = position.getX() + 12f + (BattleSquadOverlay.PANEL_WIDTH + 8f) * scale;
        float x = Math.max(centered, besideSelection);
        return new UiViewport(x,
                position.getY() + 12f + (supportHeight > 0f ? supportHeight + 8f * scale : 0f),
                width, HEIGHT * scale, scale);
    }

    void processInput(List<InputEventAPI> events) {
        if (events == null) return;
        shortcuts.process(events, visible, toggle);
        if (!visible || input == null) return;
        List<InputEventAPI> retained = new ArrayList<>();
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (event.isRMBDownEvent() || event.isRMBUpEvent()) continue;
            if (event.isMouseMoveEvent() || blocksWorldPointer(event.getX(), event.getY())) retained.add(event);
        }
        input.process(retained);
        for (InputEventAPI event : retained) {
            if (event.isLMBDownEvent() || event.isLMBUpEvent()) event.consume();
        }
    }

    boolean blocksWorldPointer(float x, float y) {
        return visible && viewport != null && x >= viewport.screenX() && x < viewport.screenX() + viewport.width()
                && y >= viewport.screenY() && y < viewport.screenY() + viewport.height();
    }

    void render(float alpha) {
        if (visible && document != null && viewport != null) document.render(viewport, alpha);
    }

    BattleHudSurface surface() {
        return visible && document != null && viewport != null ? new BattleHudSurface(document, viewport) : null;
    }

    void detach() {
        if (document != null) document.deactivateInput();
        shortcuts.clear();
        input = null;
    }
}
