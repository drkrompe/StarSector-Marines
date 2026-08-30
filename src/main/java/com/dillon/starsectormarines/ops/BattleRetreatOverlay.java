package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;

/** Host bridge for the compact MLX-authored Retreat/Continue control. */
final class BattleRetreatOverlay {

    static final String COMPONENT = "battle-retreat-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-retreat-overlay.mlx";
    private static final float EDGE_INSET = 12f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattleRetreatOverlayModel model;

    private PositionAPI position;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private BattleRetreatOverlayModel.Presentation presentation =
            BattleRetreatOverlayModel.Presentation.RETREAT;

    BattleRetreatOverlay(Runnable retreatAction, Runnable continueAction) {
        model = new BattleRetreatOverlayModel(reactor, retreatAction, continueAction,
                Strings.get("battleRetreat"), Strings.get("battleContinue"),
                Strings.get("battleRetreatConfirm"), Strings.get("battleRetreatCancel"),
                Strings.get("battleRetreatConfirmAction"));
    }

    void attach(PositionAPI nextPosition, boolean battleComplete) {
        position = nextPosition;
        presentation = model.update(battleComplete);
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
            requireWiredElements(candidate);
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
        for (String id : List.of("battle-retreat-overlay", "battle-exit-action",
                "battle-retreat-confirm", "battle-retreat-cancel",
                "battle-retreat-confirm-action")) {
            instance.requireElement(id);
        }
    }

    void update(float realDt, boolean battleComplete) {
        model.update(battleComplete);
        if (markupInstance != null) markupInstance.flush();
        syncPresentation();
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    void processInput(List<InputEventAPI> events) {
        if (input == null || events == null) return;
        input.process(events);
        if (markupInstance != null) markupInstance.flush();
        syncPresentation();
        if (document != null) document.advance(0f);
    }

    void detach() {
        if (document != null) document.deactivateInput();
        input = null;
    }

    private void syncPresentation() {
        BattleRetreatOverlayModel.Presentation next = model.presentation();
        if (next == presentation || position == null || document == null) return;
        presentation = next;
        viewport = viewport(position, next);
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    static UiViewport viewport(PositionAPI position,
                               BattleRetreatOverlayModel.Presentation presentation) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = presentation.documentWidth() * scale;
        float height = presentation.documentHeight() * scale;
        return new UiViewport(position.getX() + EDGE_INSET,
                position.getY() + EDGE_INSET, width, height, scale);
    }
}
