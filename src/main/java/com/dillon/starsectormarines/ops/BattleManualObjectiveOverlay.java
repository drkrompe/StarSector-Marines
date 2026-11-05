package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;

/** A bounded mission readout in the free top-left corner during takeover. */
final class BattleManualObjectiveOverlay {
    static final String COMPONENT = "battle-manual-objectives";
    static final String COMPONENT_PATH = "data/ui/components/battle/" + COMPONENT + ".mlx";
    static final float WIDTH = 360f;
    static final float HEIGHT = 390f;
    private final Reactor reactor = new Reactor();
    private final BattleManualObjectiveModel model = new BattleManualObjectiveModel(reactor);
    private MarkupInstance markup;
    private UiDocument document;
    private StarsectorUiInputAdapter input;
    private UiViewport viewport;
    private PositionAPI position;
    private boolean visible;

    void attach(PositionAPI position, BattleSimulation sim) {
        this.position = position;
        if (document == null) {
            var loader = new MarkupLoader(path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
            markup = loader.reloadAndBuild(reactor, COMPONENT, model.props());
            document = new UiDocument(markup.root());
            for (var style : markup.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.battle());
        }
        update(0f, sim);
    }

    void update(float dt, BattleSimulation sim) {
        if (sim == null || !sim.directControl().active()) {
            if (visible) detach();
            return;
        }
        var picture = MissionObjectiveProjection.capture(sim);
        boolean nextVisible = !picture.objectives().isEmpty();
        if (!nextVisible) {
            if (visible) detach();
            return;
        }
        visible = true;
        model.update(picture);
        if (markup == null || position == null) return;
        markup.flush();
        var next = viewport(position);
        if (!next.equals(viewport) || input == null) {
            document.deactivateInput();
            viewport = next;
            document.layout(next.documentWidth(), next.documentHeight());
            input = new StarsectorUiInputAdapter(document, next);
        }
        document.advance(dt);
    }

    static UiViewport viewport(PositionAPI position) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        return new UiViewport(position.getX() + 12f,
                position.getY() + position.getHeight() - 12f - HEIGHT * scale,
                WIDTH * scale, HEIGHT * scale, scale);
    }

    void processInput(List<InputEventAPI> events) {
        if (!visible || input == null || events == null) return;
        // Only pointer events belong to the scrollable readout; WASD, Tab and abilities remain manual input.
        var pointer = events.stream().filter(event -> !event.isConsumed()
                && (event.isMouseMoveEvent() || blocksWorldPointer(event.getX(), event.getY())
                && (event.isMouseScrollEvent() || event.isLMBDownEvent() || event.isLMBUpEvent()
                || event.isRMBDownEvent() || event.isRMBUpEvent()))).toList();
        input.process(pointer);
        for (var event : pointer) if (!event.isMouseMoveEvent()) event.consume();
    }

    /**
     * Only the drawn readout claims the pointer: the viewport is sized for a full list, but a short one leaves
     * most of it empty, and that empty corner must stay open to aiming and firing.
     */
    boolean blocksWorldPointer(float x, float y) {
        if (!visible || viewport == null || markup == null) return false;
        float docX = viewport.documentX(x);
        float docY = viewport.documentY(y);
        return docX >= 0f && docX < viewport.documentWidth() && docY >= 0f
                && docY < contentBottom(markup.requireElement("manual-objective-list"));
    }

    /** The bottom of the last card, in document pixels, held to the list's own bottom once it scrolls. */
    static float contentBottom(UiElement list) {
        var listBox = list.box().borderBox();
        float bottom = listBox.y();
        for (var card : list.children()) bottom = Math.max(bottom, card.box().borderBox().bottom());
        return Math.min(bottom, listBox.bottom());
    }

    void render(float alpha) {
        if (visible && document != null && viewport != null) document.render(viewport, alpha);
    }

    BattleHudSurface surface() {
        return visible && document != null && viewport != null ? new BattleHudSurface(document, viewport) : null;
    }

    void detach() {
        visible = false;
        if (document != null) document.deactivateInput();
        input = null;
    }
}
