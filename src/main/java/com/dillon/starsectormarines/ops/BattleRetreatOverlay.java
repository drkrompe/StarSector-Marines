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

/**
 * Host bridge for the MLX-authored battle exit control: Retreat in the corner
 * while the battle runs, then the end panel centred under the Victory/Defeat banner.
 */
final class BattleRetreatOverlay {

    static final String COMPONENT = "battle-retreat-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-retreat-overlay.mlx";
    private static final float EDGE_INSET = 12f;
    /** Gap between the battle centre and the end panel's top: clears the banner drawn there. */
    private static final float BANNER_CLEARANCE = 40f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattleRetreatOverlayModel model;

    private PositionAPI position;
    private float centreX;
    private float centreY;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private BattleRetreatOverlayModel.Presentation presentation =
            BattleRetreatOverlayModel.Presentation.RETREAT;

    BattleRetreatOverlay(BattleRetreatOverlayModel.Actions actions) {
        model = new BattleRetreatOverlayModel(reactor, actions, new BattleRetreatOverlayModel.Labels(
                Strings.get("battleRetreat"), Strings.get("battleContinue"),
                Strings.get("battleAcceptDefeat"), Strings.get("battleRetreatConfirm"),
                Strings.get("battleRetreatConfirmDetail"), Strings.get("battleRetreatCancel"),
                Strings.get("battleRetreatConfirmAction")));
    }

    /** {@code centreX/Y} is where the Victory/Defeat banner is centred: the end panel hangs below it. */
    void attach(PositionAPI nextPosition, float nextCentreX, float nextCentreY,
                BattleRetreatOverlayModel.Phase phase, BattleRetreatOverlayModel.RetryOffer offer) {
        position = nextPosition;
        centreX = nextCentreX;
        centreY = nextCentreY;
        presentation = model.update(phase, offer);
        viewport = viewport(nextPosition, centreX, centreY, presentation);
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
                "battle-retry-action", "battle-retreat-confirm", "battle-retreat-restart",
                "battle-retreat-cancel", "battle-retreat-confirm-action")) {
            instance.requireElement(id);
        }
    }

    void update(float realDt, BattleRetreatOverlayModel.Phase phase,
                BattleRetreatOverlayModel.RetryOffer offer) {
        model.update(phase, offer);
        if (markupInstance != null) markupInstance.flush();
        syncPresentation();
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    /** The corner control the bottom HUD stacks above; a centred end panel takes no part. */
    BattleHudSurface surface() {
        return document != null && viewport != null && !presentation.centred()
                ? new BattleHudSurface(document, viewport) : null;
    }

    void processInput(List<InputEventAPI> events) {
        if (input == null || events == null) return;
        input.process(events);
        if (markupInstance != null) markupInstance.flush();
        syncPresentation();
        if (document != null) document.advance(0f);
    }

    void cancelConfirmation() {
        model.cancelRetreat();
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
        viewport = viewport(position, centreX, centreY, next);
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    static UiViewport viewport(PositionAPI position, float centreX, float centreY,
                               BattleRetreatOverlayModel.Presentation presentation) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = presentation.documentWidth() * scale;
        float height = presentation.documentHeight() * scale;
        if (presentation.centred()) {
            // Y-up: the panel's bottom edge sits its own height below the banner clearance.
            return new UiViewport(centreX - width / 2f,
                    centreY - BANNER_CLEARANCE * scale - height, width, height, scale);
        }
        return new UiViewport(position.getX() + EDGE_INSET,
                position.getY() + EDGE_INSET, width, height, scale);
    }

    boolean confirmingRetreat() {
        return presentation == BattleRetreatOverlayModel.Presentation.CONFIRM;
    }

    boolean blocksWorldPointer(float x, float y) {
        if (viewport == null) return false;
        return x >= viewport.screenX() && x < viewport.screenX() + viewport.width()
                && y >= viewport.screenY() && y < viewport.screenY() + viewport.height();
    }
}
