package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Host bridge for the compact MLX-authored command-power tray. */
final class BattlePowerOverlay {

    static final String COMPONENT = "battle-power-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-power-overlay.mlx";
    static final float DECK_HEIGHT = 64f;
    static final float TARGETING_HEIGHT = 88f;
    private static final float RESOURCE_WIDTH = 96f;
    private static final float CARD_WIDTH = 112f;
    private static final float GAP = 5f;
    private static final float HORIZONTAL_PADDING = 12f;
    private static final float BOTTOM_INSET = 12f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattlePowerOverlayModel model;
    private final Supplier<String> targetingPowerId;

    private PositionAPI position;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private BattlePowerOverlayModel.Presentation presentation =
            new BattlePowerOverlayModel.Presentation(false, 0, false);

    BattlePowerOverlay(Consumer<String> targetingToggle,
                       Supplier<String> targetingPowerId) {
        model = new BattlePowerOverlayModel(reactor, targetingToggle);
        this.targetingPowerId = targetingPowerId;
    }

    void attach(PositionAPI nextPosition, BattleSimulation sim) {
        position = nextPosition;
        presentation = model.update(sim == null ? null : sim.getCommandPowerService(),
                targetingPowerId.get());
        viewport = viewport(nextPosition, presentation.powerCount(), presentation.targeting());
        if (document == null) installDocument();
        else document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(
                reactor, COMPONENT, model.props());
        UiDocument built;
        try {
            for (String id : List.of("battle-power-overlay", "battle-power-targeting",
                    "battle-power-targeting-label", "battle-power-deck",
                    "battle-power-resources", "battle-power-cp",
                    "battle-power-cp-fill", "battle-power-supplies",
                    "battle-power-cards")) {
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

    void update(float realDt, BattleSimulation sim) {
        BattlePowerOverlayModel.Presentation next = model.update(
                sim == null ? null : sim.getCommandPowerService(), targetingPowerId.get());
        if (markupInstance != null) markupInstance.flush();
        if ((next.powerCount() != presentation.powerCount()
                || next.targeting() != presentation.targeting()) && position != null) {
            viewport = viewport(position, next.powerCount(), next.targeting());
            document.layout(viewport.documentWidth(), viewport.documentHeight());
            input = new StarsectorUiInputAdapter(document, viewport);
        }
        presentation = next;
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (presentation.visible() && document != null && viewport != null) {
            document.render(viewport, alphaMult);
        }
    }

    void processInput(List<InputEventAPI> events) {
        if (!presentation.visible() || input == null || events == null) return;
        // RMB remains a world-level cancel gesture, even directly over the tray.
        List<InputEventAPI> retainedEvents = new ArrayList<>(events.size());
        for (InputEventAPI event : events) {
            if (!event.isRMBDownEvent() && !event.isRMBUpEvent()) retainedEvents.add(event);
        }
        input.process(retainedEvents);
    }

    void detach() {
        if (document != null) document.deactivateInput();
        input = null;
    }

    static float documentWidth(int powerCount) {
        int count = Math.max(1, powerCount);
        return HORIZONTAL_PADDING + RESOURCE_WIDTH + GAP
                + count * CARD_WIDTH + (count - 1) * GAP;
    }

    /** CSS has no item-alignment property yet; keep the CP fill left-anchored. */
    static void wireLayout(MarkupInstance instance) {
        instance.requireElement("battle-power-cp-fill")
                .align(UiAlign.START, UiAlign.STRETCH);
    }

    static UiViewport viewport(PositionAPI position, int powerCount, boolean targeting) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = documentWidth(powerCount) * scale;
        float documentHeight = targeting ? TARGETING_HEIGHT : DECK_HEIGHT;
        float height = documentHeight * scale;
        return new UiViewport(position.getX() + (position.getWidth() - width) * 0.5f,
                position.getY() + BOTTOM_INSET, width, height, scale);
    }
}
