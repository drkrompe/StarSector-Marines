package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.i18n.Strings;
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

/** Host bridge for the MLX-authored battle time, objective, and command overlay. */
final class BattleHudOverlay {

    static final String COMPONENT = "battle-hud-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-hud-overlay.mlx";
    static final float RAIL_WIDTH = 360f;
    static final float TIME_ONLY_HEIGHT = 54f;
    static final float OBJECTIVE_HEIGHT = BattleLayout.COMMAND_RAIL_H;
    static final float COMMAND_ONLY_HEIGHT = 166f;
    static final float CONQUEST_COMMAND_HEIGHT = BattleLayout.COMMAND_RAIL_H;
    private static final float EDGE_INSET = 12f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattleHudOverlayModel model;

    private PositionAPI position;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private boolean primaryPointerActive;
    private boolean secondaryPointerActive;
    private BattleHudOverlayModel.Presentation presentation =
            new BattleHudOverlayModel.Presentation(false, false);

    BattleHudOverlay(Consumer<Float> speedSetter) {
        model = new BattleHudOverlayModel(reactor, speedSetter,
                Strings.get("battleSpeedPause"), Strings.get("battleSpeed1x"),
                Strings.get("battleSpeed2x"), Strings.get("battleSpeed4x"));
    }

    void attach(PositionAPI nextPosition, BattleSimulation sim, float speedMultiplier) {
        position = nextPosition;
        presentation = updateModel(sim, speedMultiplier);
        viewport = viewport(nextPosition, presentation);
        if (document == null) installDocument();
        else {
            markupInstance.flush();
            document.layout(viewport.documentWidth(), viewport.documentHeight());
        }
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(
                reactor, COMPONENT, model.props());
        UiDocument built;
        try {
            requireWiredElements(candidate);
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

    private static void requireWiredElements(MarkupInstance instance) {
        for (String id : List.of(
                "battle-hud-overlay", "battle-time-control", "battle-time-pause",
                "battle-time-normal", "battle-time-double", "battle-time-quad",
                "battle-command-rail", "battle-hud-spacer",
                "battle-objectives", "battle-objective-chips",
                "battle-objective-score", "battle-objective-tally",
                "battle-objective-focus", "battle-objective-progress-fill",
                "battle-conquest-command", "battle-conquest-lanes")) {
            instance.requireElement(id);
        }
    }

    /** CSS has no item-alignment property yet; keep the progress fill left-anchored. */
    static void wireLayout(MarkupInstance instance) {
        instance.requireElement("battle-objective-progress-fill")
                .align(UiAlign.START, UiAlign.STRETCH);
    }

    void update(float realDt, BattleSimulation sim, float speedMultiplier) {
        BattleHudOverlayModel.Presentation next = updateModel(sim, speedMultiplier);
        if (markupInstance != null) markupInstance.flush();
        if (!next.equals(presentation) && position != null) {
            presentation = next;
            viewport = viewport(position, presentation);
            document.layout(viewport.documentWidth(), viewport.documentHeight());
            input = new StarsectorUiInputAdapter(document, viewport);
        }
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    void processInput(List<InputEventAPI> events) {
        if (input == null || events == null || viewport == null
                || markupInstance == null) return;
        List<InputEventAPI> retained = new ArrayList<>(events.size());
        for (InputEventAPI event : events) {
            if (event.isConsumed()) {
                clearReleasedPointer(event);
                continue;
            }
            boolean pointerEvent = event.isLMBDownEvent() || event.isLMBUpEvent()
                    || event.isRMBDownEvent() || event.isRMBUpEvent()
                    || event.isMouseScrollEvent();
            boolean pointerMovement = event.isMouseMoveEvent();
            boolean inside = (pointerEvent || pointerMovement)
                    && insideInteractiveSurface(
                    viewport.documentX(event.getX()),
                    viewport.documentY(event.getY()));
            boolean activeRelease = (event.isLMBUpEvent() && primaryPointerActive)
                    || (event.isRMBUpEvent() && secondaryPointerActive);
            if (pointerMovement || !pointerEvent || inside || activeRelease) {
                retained.add(event);
            }
            if (event.isLMBDownEvent() && inside) primaryPointerActive = true;
            if (event.isRMBDownEvent() && inside) secondaryPointerActive = true;
            clearReleasedPointer(event);
        }
        input.process(retained);
    }

    private void clearReleasedPointer(InputEventAPI event) {
        if (event.isLMBUpEvent()) primaryPointerActive = false;
        if (event.isRMBUpEvent()) secondaryPointerActive = false;
    }

    private boolean insideInteractiveSurface(float documentX, float documentY) {
        return insideInteractiveSurface(markupInstance, documentX, documentY);
    }

    /** The wide retained document uses a transparent spacer between its two rails. */
    static boolean insideInteractiveSurface(MarkupInstance instance,
                                            float documentX, float documentY) {
        if (instance == null) return false;
        return instance.requireElement("battle-conquest-command")
                .box().borderBox().contains(documentX, documentY)
                || instance.requireElement("battle-command-rail")
                .box().borderBox().contains(documentX, documentY);
    }

    void detach() {
        if (document != null) document.deactivateInput();
        input = null;
        primaryPointerActive = false;
        secondaryPointerActive = false;
    }

    static UiViewport viewport(PositionAPI position,
                               BattleHudOverlayModel.Presentation presentation) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float documentHeight = documentHeight(presentation);
        float physicalWidth = Math.max(0f, position.getWidth() - 2f * EDGE_INSET);
        float physicalHeight = documentHeight * scale;
        return new UiViewport(
                position.getX() + EDGE_INSET,
                position.getY() + position.getHeight() - EDGE_INSET - physicalHeight,
                physicalWidth, physicalHeight, scale);
    }

    static float documentHeight(BattleHudOverlayModel.Presentation presentation) {
        if (presentation.objectivesVisible() && presentation.commandVisible()) {
            return CONQUEST_COMMAND_HEIGHT;
        }
        if (presentation.objectivesVisible()) return OBJECTIVE_HEIGHT;
        if (presentation.commandVisible()) return COMMAND_ONLY_HEIGHT;
        return TIME_ONLY_HEIGHT;
    }

    private BattleHudOverlayModel.Presentation updateModel(
            BattleSimulation sim, float speedMultiplier) {
        return model.update(speedMultiplier,
                sim == null ? List.of() : sim.getCompoundService().getRecords(),
                sim == null ? null : sim.getCommanderSnapshot(Faction.MARINE));
    }
}
