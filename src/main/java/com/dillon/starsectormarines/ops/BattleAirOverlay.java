package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.airsupport.AirPoolView;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;

/**
 * Host bridge for the MLX-authored air support plate: the player side's pool at a glance, under the top-right command
 * rail. Read-only and not interactive: it takes no input and never blocks the world pointer. It is absent in a battle
 * whose player side holds no pool.
 */
final class BattleAirOverlay {

    static final String COMPONENT = "battle-air-overlay";
    static final String COMPONENT_PATH = "data/ui/components/battle/battle-air-overlay.mlx";
    static final float DOCUMENT_WIDTH = BattleHudOverlay.RAIL_WIDTH;
    private static final float EDGE_INSET = 12f;
    private static final float RAIL_GAP = 8f;
    private static final float CHROME = 12f;
    private static final float HEADER = 14f;
    private static final float ROW = 14f;
    private static final float GAP = 3f;
    private static final float HEADER_GAP = 4f;

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));
    private final BattleAirOverlayModel model = new BattleAirOverlayModel(reactor);

    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private BattleAirOverlayModel.Presentation presentation =
            new BattleAirOverlayModel.Presentation(false, 0, 0);

    /** Reads the player side's pool, lays the plate out below {@code railBottom} and installs it on first use. */
    void attach(PositionAPI position, BattleSimulation sim, float railBottom) {
        presentation = model.update(view(sim));
        if (markupInstance != null) markupInstance.flush();
        viewport = viewport(position, presentation, railBottom);
        if (!presentation.visible()) return;
        if (document == null) installDocument();
        else document.layout(viewport.documentWidth(), viewport.documentHeight());
    }

    private static AirPoolView view(BattleSimulation sim) {
        return sim == null ? AirPoolView.NONE : sim.airPool().view(Faction.MARINE, sim.getSimTickIndex());
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, COMPONENT, model.props());
        UiDocument built;
        try {
            for (String id : List.of("battle-air-overlay", "battle-air-plate", "battle-air-status",
                    "battle-air-kinds")) {
                candidate.requireElement(id);
            }
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

    void update(float realDt, BattleSimulation sim, PositionAPI position, float railBottom) {
        BattleAirOverlayModel.Presentation next = model.update(view(sim));
        if (markupInstance != null) markupInstance.flush();
        boolean resized = !next.equals(presentation);
        presentation = next;
        if (position != null) viewport = viewport(position, next, railBottom);
        if (next.visible() && document == null && viewport != null) installDocument();
        else if (resized && document != null && viewport != null) {
            document.layout(viewport.documentWidth(), viewport.documentHeight());
        }
        if (document != null) document.advance(realDt);
    }

    void render(float alphaMult) {
        if (presentation.visible() && document != null && viewport != null) document.render(viewport, alphaMult);
    }

    BattleHudSurface surface() {
        return presentation.visible() && document != null && viewport != null
                ? new BattleHudSurface(document, viewport) : null;
    }

    /** The plate's height in document pixels for a given number of rows (kinds plus caption lines). */
    static float documentHeight(int kindCount, int lineCount) {
        int rows = Math.max(1, kindCount + lineCount);
        return CHROME + HEADER + HEADER_GAP + rows * ROW + (rows - 1) * GAP;
    }

    static UiViewport viewport(PositionAPI position, BattleAirOverlayModel.Presentation presentation,
                               float railBottom) {
        UiViewport host = MarineOpsUiViewport.from(position);
        float scale = host.documentScale();
        float width = DOCUMENT_WIDTH * scale;
        float height = documentHeight(presentation.kindCount(), presentation.lineCount()) * scale;
        float top = Float.isInfinite(railBottom)
                ? position.getY() + position.getHeight() - EDGE_INSET : railBottom - RAIL_GAP * scale;
        return new UiViewport(position.getX() + position.getWidth() - EDGE_INSET - width,
                top - height, width, height, scale);
    }
}
