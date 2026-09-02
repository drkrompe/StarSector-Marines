package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;
import java.util.Map;

/** Shared retained-document lifecycle for the mission flow's MLX screens. */
abstract class MissionFlowMlxScreen implements Screen {

    private final String rootComponent;
    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup;

    protected MarineOpsContext context;
    protected Runnable dismissDialog;

    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private SpecSheetBinder specSheets;

    MissionFlowMlxScreen(String rootComponent, List<String> componentPaths) {
        this.rootComponent = rootComponent;
        markup = new MarkupLoader(path -> Global.getSettings().loadText(path), componentPaths);
    }

    @Override
    public final void attach(PositionAPI position, MarineOpsContext context,
                             Runnable dismissDialog) {
        this.context = context;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        onAttach();
        rebuildDocument();
    }

    /** Lets a screen reset transient state before its document is projected. */
    protected void onAttach() {
    }

    protected abstract Map<String, Object> props();

    protected abstract List<String> requiredElementIds();

    /**
     * Lets a screen bind retained elements that are repeated from its projected
     * rows. {@link #specSheets()} is already installed on {@code built} when
     * this runs, so a screen binds its hover overlays here.
     */
    protected void onDocumentBuilt(MarkupInstance instance, UiDocument built) {
    }

    /**
     * This screen's spec-sheet bindings, valid from {@link #onDocumentBuilt}
     * until the next rebuild. A screen that binds nothing costs nothing.
     */
    protected final SpecSheetBinder specSheets() {
        return specSheets;
    }

    /**
     * Lets a screen project hover-only presentation after retained input is
     * resolved. An override owes nothing to the spec sheets: the base updates
     * the binder itself, after this returns, so a screen cannot silently drop
     * its overlays by forgetting to call {@code super}.
     */
    protected void onInputProcessed() {
    }

    protected void onCancel() {
    }

    protected final void rebuildDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, rootComponent, props());
        SpecSheetBinder previousSheets = specSheets;
        UiDocument built;
        try {
            for (String id : requiredElementIds()) candidate.requireElement(id);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::onCancel);
            if (viewport != null) built.layout(viewport.documentWidth(), viewport.documentHeight());
            specSheets = new SpecSheetBinder(built, SpecSheetLayer.install(built));
            onDocumentBuilt(candidate, built);
        } catch (RuntimeException failure) {
            specSheets = previousSheets;
            candidate.close();
            throw failure;
        }
        if (previousSheets != null) previousSheets.clear();

        UiDocument previousDocument = document;
        MarkupInstance previousInstance = markupInstance;
        document = built;
        markupInstance = candidate;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
        if (viewport != null) input = new StarsectorUiInputAdapter(document, viewport);
    }

    @Override
    public final void advance(float dt) {
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(dt);
    }

    @Override
    public final void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    @Override
    public final void processInput(List<InputEventAPI> events) {
        if (input == null) return;
        input.process(events);
        onInputProcessed();
        if (specSheets != null) specSheets.update();
        if (document != null) document.advance(0f);
    }

    @Override
    public final void detach() {
        if (document != null) document.deactivateInput();
        if (specSheets != null) specSheets.clear();
        input = null;
    }
}
