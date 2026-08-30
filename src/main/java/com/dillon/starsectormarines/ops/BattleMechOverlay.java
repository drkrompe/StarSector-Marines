package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ui.retained.UiAlign;
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

/** Host bridge for the selected-mech doctrine control. */
final class BattleMechOverlay {

    static final String COMPONENT = "battle-mech-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-mech-overlay.mlx";
    static final float DOCUMENT_WIDTH = 430f;
    static final float DOCUMENT_HEIGHT = 194f;
    private static final float EDGE_INSET = 12f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final Selection selection;
    private final BattleMechOverlayModel model;

    private BattleSimulation simulation;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private BattleMechOverlayModel.Presentation presentation =
            new BattleMechOverlayModel.Presentation(false);

    BattleMechOverlay(Selection selection) {
        this.selection = selection;
        model = new BattleMechOverlayModel(
                reactor, selection::clear, this::requestDoctrine);
    }

    void attach(PositionAPI nextPosition, BattleSimulation sim) {
        simulation = sim;
        presentation = updateModel(sim);
        viewport = viewport(nextPosition);
        if (document == null) installDocument();
        else document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(
                reactor, COMPONENT, model.props());
        UiDocument built;
        try {
            for (String id : List.of("battle-mech-overlay", "battle-mech-panel",
                    "battle-mech-header", "battle-mech-back", "battle-mech-title",
                    "battle-mech-state", "battle-mech-identity",
                    "battle-mech-deployed", "battle-mech-effective",
                    "battle-mech-doctrine-cards", "battle-mech-default")) {
                candidate.requireElement(id);
            }
            wireLayout(candidate);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard());
            built.layout(viewport.documentWidth(), viewport.documentHeight());
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }
        document = built;
        markupInstance = candidate;
    }

    static void wireLayout(MarkupInstance instance) {
        instance.requireElement("battle-mech-panel")
                .align(UiAlign.START, UiAlign.START);
    }

    void update(float realDt, BattleSimulation sim) {
        simulation = sim;
        presentation = updateModel(sim);
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (presentation.visible() && document != null && viewport != null) {
            document.render(viewport, alphaMult);
        }
    }

    void processInput(List<InputEventAPI> events) {
        if (!presentation.visible() || input == null || events == null) return;
        List<InputEventAPI> retained = new ArrayList<>(events.size());
        for (InputEventAPI event : events) {
            if (event.isConsumed() || event.isRMBDownEvent() || event.isRMBUpEvent()) {
                continue;
            }
            if (event.isMouseMoveEvent() || insidePanel(event.getX(), event.getY())) {
                retained.add(event);
            }
        }
        input.process(retained);

        // The visible plate is opaque interaction chrome. Empty space inside
        // it must not also select the battlefield or zoom the camera behind it.
        for (InputEventAPI event : retained) {
            if (event.isConsumed() || event.isMouseMoveEvent()) continue;
            if (insidePanel(event.getX(), event.getY())
                    && (event.isLMBDownEvent() || event.isLMBUpEvent()
                    || event.isMouseScrollEvent())) {
                event.consume();
            }
        }
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(0f);
    }

    void detach() {
        if (document != null) document.deactivateInput();
        input = null;
        simulation = null;
    }

    static UiViewport viewport(PositionAPI position) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = DOCUMENT_WIDTH * scale;
        float height = DOCUMENT_HEIGHT * scale;
        float y = position.getY() + BattleLayout.PAD + BattleLayout.BACK_H
                + BattleLayout.CONTROLS_GAP;
        return new UiViewport(position.getX() + EDGE_INSET, y,
                width, height, scale);
    }

    private BattleMechOverlayModel.Presentation updateModel(BattleSimulation sim) {
        int squadId = selection.hasSquadSelection()
                ? selection.getSelectedSquadId() : Selection.NONE;
        return model.update(sim, squadId, selection.getSelectedUnitEntityId());
    }

    private void requestDoctrine(long mechId, MechRole role) {
        BattleSimulation sim = simulation;
        if (sim != null) {
            sim.getMechDoctrineService().requestOverride(mechId, role);
        }
    }

    private boolean insidePanel(float x, float y) {
        if (viewport == null) return false;
        return x >= viewport.screenX() && x < viewport.screenX() + viewport.width()
                && y >= viewport.screenY() && y < viewport.screenY() + viewport.height();
    }
}
