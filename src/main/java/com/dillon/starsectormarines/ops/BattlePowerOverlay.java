package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
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
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Host bridge for the compact MLX-authored command-power tray. */
final class BattlePowerOverlay {

    static final String COMPONENT = "battle-power-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-power-overlay.mlx";
    static final float DECK_HEIGHT = 64f;
    static final float TARGETING_HEIGHT = 88f;
    /** Added above the targeting strip while an armor drop's bay picker is open. */
    static final float PICKER_HEIGHT = 61f;
    private static final float CHIP_WIDTH = 128f;
    private static final float CHIP_GAP = 4f;
    private static final float PICKER_PADDING = 16f;
    private static final float RESOURCE_WIDTH = 96f;
    private static final float CARD_WIDTH = 146f;
    private static final float GAP = 5f;
    private static final float HORIZONTAL_PADDING = 12f;
    private static final float BOTTOM_INSET = 12f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattlePowerOverlayModel model;
    private final Supplier<String> targetingPowerId;
    private final Supplier<List<ArmorDropPower.PickerRow>> pickerRows;

    private PositionAPI position;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private boolean suppressed;
    private BattlePowerOverlayModel.Presentation presentation =
            new BattlePowerOverlayModel.Presentation(false, 0, false);

    BattlePowerOverlay(Consumer<String> targetingToggle,
                       Supplier<String> targetingPowerId) {
        this(targetingToggle, targetingPowerId, List::of, index -> { });
    }

    /**
     * @param pickerRows the armed armor drop's bay manifest against its pick (empty otherwise)
     * @param pickToggle toggles one manifest entry in that pick
     */
    BattlePowerOverlay(Consumer<String> targetingToggle,
                       Supplier<String> targetingPowerId,
                       Supplier<List<ArmorDropPower.PickerRow>> pickerRows,
                       IntConsumer pickToggle) {
        model = new BattlePowerOverlayModel(reactor, targetingToggle, pickToggle,
                CampaignCommodityPresentation.INSTANCE);
        this.targetingPowerId = targetingPowerId;
        this.pickerRows = pickerRows;
    }

    void attach(PositionAPI nextPosition, BattleSimulation sim) {
        position = nextPosition;
        presentation = project(sim);
        viewport = viewport(nextPosition, presentation);
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
                    "battle-power-targeting-label", "battle-power-picker", "battle-power-deck",
                    "battle-power-resources", "battle-power-cp",
                    "battle-power-cp-fill", "battle-power-supplies",
                    "battle-power-supplies-icon",
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
        BattlePowerOverlayModel.Presentation next = project(sim);
        if (markupInstance != null) markupInstance.flush();
        if ((next.powerCount() != presentation.powerCount()
                || next.targeting() != presentation.targeting()
                || next.pickerChips() != presentation.pickerChips()) && position != null) {
            viewport = viewport(position, next);
            document.layout(viewport.documentWidth(), viewport.documentHeight());
            input = new StarsectorUiInputAdapter(document, viewport);
        }
        presentation = next;
        if (document != null) document.advance(realDt);
    }

    private BattlePowerOverlayModel.Presentation project(BattleSimulation sim) {
        return model.update(sim == null ? null : sim.getCommandPowerService(),
                targetingPowerId.get(), sim != null && sim.orbitalBarrageLock() != null,
                sim == null ? List.of() : pickerRows.get());
    }

    void render(float alphaMult) {
        if (!suppressed && presentation.visible() && document != null && viewport != null) {
            document.render(viewport, alphaMult);
        }
    }

    BattleHudSurface surface() {
        return !suppressed && presentation.visible() && document != null && viewport != null ? new BattleHudSurface(document, viewport) : null;
    }

    void processInput(List<InputEventAPI> events) {
        if (suppressed || !presentation.visible() || input == null || events == null) return;
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

    /** A selected ship owns the bottom tray; hidden powers must also release focus. */
    void suppressed(boolean hidden) {
        if (suppressed == hidden) return;
        suppressed = hidden;
        if (document != null) document.deactivateInput();
    }

    static float documentWidth(int powerCount) {
        int count = Math.max(1, powerCount);
        return HORIZONTAL_PADDING + RESOURCE_WIDTH + GAP
                + count * CARD_WIDTH + (count - 1) * GAP;
    }

    /** Width of a bay picker holding {@code chips} chips (zero when there is none). */
    static float pickerWidth(int chips) {
        if (chips <= 0) return 0f;
        return PICKER_PADDING + chips * CHIP_WIDTH + (chips - 1) * CHIP_GAP;
    }

    static float documentWidth(BattlePowerOverlayModel.Presentation presentation) {
        return Math.max(documentWidth(presentation.powerCount()),
                pickerWidth(presentation.pickerChips()));
    }

    static float documentHeight(BattlePowerOverlayModel.Presentation presentation) {
        if (!presentation.targeting()) return DECK_HEIGHT;
        return presentation.picking() ? TARGETING_HEIGHT + PICKER_HEIGHT : TARGETING_HEIGHT;
    }

    /** CSS has no item-alignment property yet; keep the CP fill left-anchored. */
    static void wireLayout(MarkupInstance instance) {
        instance.requireElement("battle-power-cp-fill")
                .align(UiAlign.START, UiAlign.STRETCH);
    }

    static UiViewport viewport(PositionAPI position, int powerCount, boolean targeting) {
        return viewport(position, new BattlePowerOverlayModel.Presentation(true, powerCount, targeting));
    }

    static UiViewport viewport(PositionAPI position, BattlePowerOverlayModel.Presentation presentation) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = documentWidth(presentation) * scale;
        float height = documentHeight(presentation) * scale;
        return new UiViewport(position.getX() + (position.getWidth() - width) * 0.5f,
                position.getY() + BOTTOM_INSET, width, height, scale);
    }

    float occupiedHeight() {
        return !suppressed && presentation.visible() && viewport != null ? viewport.height() : 0f;
    }

    boolean blocksWorldPointer(float x, float y) {
        if (suppressed || !presentation.visible() || viewport == null) return false;
        return x >= viewport.screenX() && x < viewport.screenX() + viewport.width()
                && y >= viewport.screenY() && y < viewport.screenY() + viewport.height();
    }
}
