package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Host bridge for the compact MLX-authored selected-squad roster. */
final class BattleSquadOverlay {

    static final String COMPONENT = "battle-squad-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-squad-overlay.mlx";
    static final float DOCUMENT_WIDTH = 720f;
    static final float DOCUMENT_HEIGHT = 230f;
    static final float PANEL_WIDTH = 430f;
    private static final float TOOLTIP_WIDTH = 266f;
    private static final float TOOLTIP_HEIGHT = 126f;
    private static final float EDGE_INSET = 12f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final Selection selection;
    private final IntSupplier targetingSquadId;
    private final BattleSquadOverlayModel model;
    private final List<MemberElement> memberElements = new ArrayList<>();

    private PositionAPI position;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private BattleSquadOverlayModel.Presentation presentation =
            new BattleSquadOverlayModel.Presentation(false);

    BattleSquadOverlay(Selection selection, IntConsumer defendAreaAction,
                       IntSupplier targetingSquadId) {
        this.selection = selection;
        this.targetingSquadId = targetingSquadId;
        model = new BattleSquadOverlayModel(reactor, selection::clear,
                () -> defendAreaAction.accept(selection.getSelectedSquadId()));
    }

    void attach(PositionAPI nextPosition, BattleSimulation sim) {
        position = nextPosition;
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
        refreshMemberElements();
    }

    private static void requireWiredElements(MarkupInstance instance) {
        for (String id : List.of(
                "battle-squad-overlay", "battle-squad-panel", "battle-squad-header",
                "battle-squad-summary", "battle-squad-orders",
                "battle-squad-back", "battle-squad-title", "battle-squad-strength",
                "battle-squad-defend-area",
                "battle-squad-morale", "battle-squad-morale-fill",
                "battle-squad-fireteams", "battle-squad-tooltip",
                "battle-squad-tooltip-title", "battle-squad-tooltip-team",
                "battle-squad-tooltip-primary", "battle-squad-tooltip-special",
                "battle-squad-tooltip-system", "battle-squad-tooltip-profile")) {
            instance.requireElement(id);
        }
    }

    static void wireLayout(MarkupInstance instance) {
        instance.requireElement("battle-squad-overlay").layout(UiLayout.STACK);
        instance.requireElement("battle-squad-panel")
                .align(UiAlign.START, UiAlign.START);
        instance.requireElement("battle-squad-tooltip")
                .align(UiAlign.END, UiAlign.CENTER);
        instance.requireElement("battle-squad-morale-fill")
                .align(UiAlign.START, UiAlign.STRETCH);
    }

    private void refreshMemberElements() {
        memberElements.clear();
        for (int team = 0; team < BattleSquadOverlayModel.FIRE_TEAM_COUNT; team++) {
            for (int slot = 0; slot < BattleSquadOverlayModel.MEMBERS_PER_TEAM; slot++) {
                String id = "battle-squad-member-" + team + "-" + slot;
                memberElements.add(new MemberElement(id, markupInstance.requireElement(id)));
            }
        }
    }

    void update(float realDt, BattleSimulation sim) {
        presentation = updateModel(sim);
        if (markupInstance != null) {
            markupInstance.flush();
            refreshMemberElements();
        }
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
            // A retained overlay dispatched earlier (the HUD rail owns pause and
            // the speed control) may already have claimed this click, and the game
            // throws from getX/getY once an event is consumed.
            if (event.isConsumed()) continue;
            if (event.isMouseMoveEvent() || insidePanel(event.getX(), event.getY())
                    || insideVisibleTooltip(event.getX(), event.getY())) {
                retained.add(event);
            }
        }
        input.process(retained);

        String hovered = null;
        for (MemberElement member : memberElements) {
            if (member.element().hovered()) {
                hovered = member.id();
                break;
            }
        }
        model.hover(hovered);
        markupInstance.flush();
        document.advance(0f);
    }

    void detach() {
        if (document != null) document.deactivateInput();
        input = null;
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

    private BattleSquadOverlayModel.Presentation updateModel(BattleSimulation sim) {
        int selected = selection.hasSquadSelection()
                ? selection.getSelectedSquadId() : Selection.NONE;
        if (sim != null && selected != Selection.NONE) {
            Squad squad = sim.getSquad(selected);
            if (squad == null || squad.aliveMembers <= 0) {
                selection.clear();
                selected = Selection.NONE;
            }
        }
        return model.update(sim, selected, targetingSquadId.getAsInt());
    }

    private boolean insidePanel(float x, float y) {
        if (viewport == null) return false;
        return inside(x, y, viewport.screenX(), viewport.screenY(),
                PANEL_WIDTH * viewport.documentScale(), viewport.height());
    }

    boolean blocksWorldPointer(float x, float y) {
        return presentation.visible()
                && (insidePanel(x, y) || insideVisibleTooltip(x, y));
    }

    private boolean insideVisibleTooltip(float x, float y) {
        if (viewport == null || markupInstance == null
                || markupInstance.requireElement("battle-squad-tooltip")
                .hasClass("squad-tooltip-hidden")) return false;
        float scale = viewport.documentScale();
        float left = viewport.screenX() + (DOCUMENT_WIDTH - TOOLTIP_WIDTH) * scale;
        float bottom = viewport.screenY()
                + (DOCUMENT_HEIGHT - TOOLTIP_HEIGHT) * 0.5f * scale;
        return inside(x, y, left, bottom, TOOLTIP_WIDTH * scale, TOOLTIP_HEIGHT * scale);
    }

    private static boolean inside(float x, float y, float left, float bottom,
                                  float width, float height) {
        return x >= left && x < left + width && y >= bottom && y < bottom + height;
    }

    private record MemberElement(String id, UiElement element) { }
}
