package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.LoadingProgress;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;

/** Host bridge for the MLX-authored screen a battle shows while its textures load. */
final class BattleLoadingOverlay {

    static final String COMPONENT = "battle-loading-overlay";
    static final String COMPONENT_PATH =
            "data/ui/components/battle/battle-loading-overlay.mlx";

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattleLoadingOverlayModel model = new BattleLoadingOverlayModel(reactor);

    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;

    void attach(PositionAPI position, String operation, String where) {
        model.heading(operation, where);
        viewport = viewport(position);
        if (document == null) installDocument();
        else document.layout(viewport.documentWidth(), viewport.documentHeight());
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, COMPONENT, model.props());
        UiDocument built;
        try {
            for (String id : List.of("battle-loading-overlay", "battle-loading-progress")) {
                candidate.requireElement(id);
            }
            // CSS has no item alignment yet; keep the fill anchored at the bar's left end.
            candidate.requireElement("battle-loading-progress").align(UiAlign.START, UiAlign.STRETCH);
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

    void update(float realDt, LoadingProgress progress) {
        model.update(realDt, progress);
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    static UiViewport viewport(PositionAPI position) {
        float scale = MarineOpsUiViewport.from(position).documentScale();
        return new UiViewport(position.getX(), position.getY(),
                position.getWidth(), position.getHeight(), scale);
    }
}
