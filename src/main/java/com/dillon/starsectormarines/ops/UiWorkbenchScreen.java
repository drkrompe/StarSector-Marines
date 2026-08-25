package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.PointerButton;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiPointerEvent;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader.PreparedReload;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Developer proof for the retained tree, cascade, input, canvas, and MLX authoring path. */
public final class UiWorkbenchScreen implements Screen {
    private static final Logger LOG = Global.getLogger(UiWorkbenchScreen.class);
    private static final String COMPONENT_PATH = "data/ui/components/dev/ui-workbench.mlx";
    private static final String COMPONENT_NAME = "ui-workbench";

    private final List<UiElement> teamButtons = new ArrayList<>();
    private final List<UiElement> templateButtons = new ArrayList<>();
    private final List<UiElement> themeButtons = new ArrayList<>();
    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), List.of(COMPONENT_PATH));

    private MarineOpsContext context;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;
    private UiElement viewportReadout;
    private UiElement selectedTeamReadout;
    private UiElement selectedTemplateReadout;
    private UiElement transactionReadout;
    private UiElement transactionCanvas;
    private UiElement retainedStatus;
    private float issueMarkerX = 610f;
    private int selectedTeam;
    private int selectedTemplate;
    private boolean highContrast;
    private boolean reloadRequested;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        context = ctx;
        viewport = MarineOpsUiViewport.from(position);
        if (document == null) installDocument(true);
        updateViewportReadout();
        updateSelectionReadouts();
        document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument(boolean reloadSource) {
        PreparedReload prepared = reloadSource
                ? markup.prepareReload(reactor, COMPONENT_NAME, Map.of()) : null;
        MarkupInstance candidate = prepared == null
                ? markup.build(reactor, COMPONENT_NAME, Map.of()) : prepared.instance();
        UiDocument built;
        Wiring wiring;
        try {
            wiring = bindElements(candidate);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(highContrast ? MarineOpsThemes.highContrast() : MarineOpsThemes.standard())
                    .onCancel(() -> context.goTo(ScreenId.COMPANY_HQ));
            built.canvases().set(wiring.transactionCanvas(), this::paintTransactionCanvas);
            if (viewport != null) {
                built.layout(viewport.documentWidth(), viewport.documentHeight());
            }
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }

        UiDocument previousDocument = document;
        MarkupInstance previousInstance = markupInstance;
        if (prepared != null) prepared.commit();
        document = built;
        markupInstance = candidate;
        installWiring(wiring);
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
        updateViewportReadout();
        updateSelectionReadouts();
        if (viewport != null) input = new StarsectorUiInputAdapter(document, viewport);
    }

    private Wiring bindElements(MarkupInstance component) {
        requireWiredElements(component);
        List<UiElement> candidateTeams = new ArrayList<>();
        List<UiElement> candidateTemplates = new ArrayList<>();
        List<UiElement> candidateThemes = new ArrayList<>();

        UiElement candidateViewport = component.requireElement("viewport-readout")
                .align(UiAlign.END, UiAlign.CENTER);
        component.requireElement("formation-note").align(UiAlign.STRETCH, UiAlign.END);
        component.requireElement("library-note").align(UiAlign.STRETCH, UiAlign.END);
        component.requireElement("workspace-stack").layout(UiLayout.STACK);
        component.requireElement("workspace-content").align(UiAlign.STRETCH, UiAlign.STRETCH);
        component.requireElement("workspace-note").align(UiAlign.STRETCH, UiAlign.END);
        component.requireElement("stack-proof").align(UiAlign.END, UiAlign.END);
        UiElement candidateStatus = component.requireElement("retained-status")
                .align(UiAlign.STRETCH, UiAlign.CENTER);

        component.requireElement("back").onClick(() -> context.goTo(ScreenId.COMPANY_HQ));
        for (int index = 0; index < 3; index++) {
            int selected = index;
            UiElement team = component.requireElement("team-" + index)
                    .onClick(() -> selectTeam(selected));
            candidateTeams.add(team);
        }
        for (int index = 0; index < 8; index++) {
            int selected = index;
            UiElement template = component.requireElement("template-" + index)
                    .onClick(() -> selectTemplate(selected));
            candidateTemplates.add(template);
        }
        component.requireElement("state-focus").onClick(() -> { });
        component.requireElement("state-live").onClick(() -> { });
        component.requireElement("state-selected").onClick(() -> { });
        component.requireElement("state-disabled").onClick(() -> { });

        UiElement standard = component.requireElement("theme-standard")
                .onClick(() -> selectTheme(false));
        UiElement contrast = component.requireElement("theme-contrast")
                .onClick(() -> selectTheme(true));
        candidateThemes.add(standard);
        candidateThemes.add(contrast);
        component.requireElement("reload-ui").onClick(() -> reloadRequested = true);

        UiElement candidateTeamReadout = component.requireElement("selected-team");
        UiElement candidateTemplateReadout = component.requireElement("selected-template");
        UiElement candidateTransactionReadout = component.requireElement("transaction-result");
        UiElement candidateCanvas = component.requireElement("transaction-canvas")
                .onPointerDown(event -> {
                    if (event.button() == PointerButton.PRIMARY) {
                        event.capturePointer();
                        moveIssueMarker(event);
                    }
                })
                .onPointerMove(event -> {
                    if (document != null && document.pointerCapture() == component.requireElement("transaction-canvas")) {
                        moveIssueMarker(event);
                    }
                })
                .onPointerUp(event -> {
                    if (event.button() == PointerButton.PRIMARY) moveIssueMarker(event);
                });
        return new Wiring(candidateViewport, candidateTeamReadout, candidateTemplateReadout,
                candidateTransactionReadout, candidateCanvas, candidateStatus,
                candidateTeams, candidateTemplates, candidateThemes);
    }

    private void installWiring(Wiring wiring) {
        viewportReadout = wiring.viewportReadout();
        selectedTeamReadout = wiring.selectedTeamReadout();
        selectedTemplateReadout = wiring.selectedTemplateReadout();
        transactionReadout = wiring.transactionReadout();
        transactionCanvas = wiring.transactionCanvas();
        retainedStatus = wiring.retainedStatus();
        teamButtons.clear();
        teamButtons.addAll(wiring.teamButtons());
        templateButtons.clear();
        templateButtons.addAll(wiring.templateButtons());
        themeButtons.clear();
        themeButtons.addAll(wiring.themeButtons());
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "viewport-readout", "formation-note", "library-note", "workspace-stack",
                "workspace-content", "workspace-note", "stack-proof", "retained-status",
                "back", "team-0", "team-1", "team-2",
                "template-0", "template-1", "template-2", "template-3",
                "template-4", "template-5", "template-6", "template-7",
                "state-focus", "state-live", "state-selected", "state-disabled",
                "theme-standard", "theme-contrast", "reload-ui", "selected-team",
                "selected-template", "transaction-result", "transaction-canvas")) {
            component.requireElement(id);
        }
    }

    private void reloadDocument() {
        try {
            installDocument(true);
            retainedStatus.text("MLX reloaded  |  View-model state preserved  |  U4 proof");
            LOG.info("Reloaded " + COMPONENT_PATH);
        } catch (RuntimeException failure) {
            LOG.error("MLX reload refused; keeping the previous document", failure);
            if (retainedStatus != null) retainedStatus.text("MLX reload refused  |  Previous document retained");
        }
    }

    private void paintTransactionCanvas(CanvasContext canvas) {
        MarineOpsThemes.CanvasPalette palette = MarineOpsThemes.canvasPalette(highContrast);
        BitmapFont font = document.styles().fontFor(transactionCanvas);
        float width = canvas.metrics().surfaceWidth();
        float nodeWidth = 190f;
        float nodeHeight = 62f;
        float top = 42f;
        float[] x = {28f, width * 0.28f, width * 0.54f, width - nodeWidth - 28f};
        String[] labels = {"Free Stock", "Returns", "Required", "Valid Issue"};
        Color[] colors = {palette.button(), palette.selected(), palette.danger(), palette.valid()};

        canvas.text(font, "Captured canvas drag  ·  Move issue marker", 24f, 12f, palette.muted());
        for (int index = 0; index < x.length; index++) {
            if (index > 0) {
                canvas.line(x[index - 1] + nodeWidth, top + nodeHeight * 0.5f,
                        x[index], top + nodeHeight * 0.5f, palette.edge(), 2f);
            }
            canvas.fillRect(x[index], top, nodeWidth, nodeHeight, colors[index]);
            canvas.strokeRect(x[index], top, nodeWidth, nodeHeight,
                    index == x.length - 1 ? palette.good() : palette.border(), 2f);
            canvas.text(font, labels[index], x[index] + 13f, top + 18f,
                    index == x.length - 1 ? palette.good() : palette.text());
        }
        canvas.line(issueMarkerX, 34f, issueMarkerX, 126f, palette.accent(), 3f);
        canvas.fillRect(issueMarkerX - 7f, 30f, 14f, 14f, palette.accent());
    }

    private void moveIssueMarker(UiPointerEvent event) {
        if (document == null || transactionCanvas == null) return;
        CanvasMetrics metrics = document.canvasMetrics(transactionCanvas, 1f);
        float canvasX = metrics.toCanvasX(event.x());
        if (!Float.isFinite(canvasX)) return;
        issueMarkerX = Math.max(8f, Math.min(metrics.surfaceWidth() - 8f, canvasX));
        document.canvases().invalidate(transactionCanvas);
    }

    private void selectTeam(int index) {
        selectedTeam = index;
        updateSelectionReadouts();
    }

    private void selectTemplate(int index) {
        selectedTemplate = index;
        updateSelectionReadouts();
    }

    private void selectTheme(boolean useHighContrast) {
        highContrast = useHighContrast;
        if (document != null) {
            document.theme(highContrast ? MarineOpsThemes.highContrast() : MarineOpsThemes.standard());
        }
        updateSelectionReadouts();
    }

    private void updateSelectionReadouts() {
        String[] teams = {"Alpha", "Bravo", "Charlie"};
        String[] templates = {"Line", "Recon", "Fire Support", "Breach",
                "Boarding", "Anti-Armor", "Security", "Hazard Response"};
        for (int index = 0; index < teamButtons.size(); index++) {
            teamButtons.get(index).selected(index == selectedTeam);
        }
        for (int index = 0; index < templateButtons.size(); index++) {
            templateButtons.get(index).selected(index == selectedTemplate);
        }
        for (int index = 0; index < themeButtons.size(); index++) {
            themeButtons.get(index).selected(highContrast == (index == 1));
        }
        if (selectedTeamReadout != null) {
            selectedTeamReadout.text("Target  ·  1st Squad / " + teams[selectedTeam]);
        }
        if (selectedTemplateReadout != null) {
            selectedTemplateReadout.text("Candidate  ·  " + templates[selectedTemplate] + " template");
        }
        if (transactionReadout != null) {
            transactionReadout.text("Valid  ·  Issue " + templates[selectedTemplate]
                    + " to " + teams[selectedTeam] + " in one atomic transaction");
        }
    }

    private void updateViewportReadout() {
        if (viewportReadout == null || viewport == null) return;
        viewportReadout.text(String.format(Locale.ROOT,
                "Granted %.1f x %.1f  ·  Document %.1f x %.1f @ %.2fx",
                viewport.width(), viewport.height(),
                viewport.documentWidth(), viewport.documentHeight(),
                viewport.documentScale()));
    }

    private record Wiring(UiElement viewportReadout,
                          UiElement selectedTeamReadout,
                          UiElement selectedTemplateReadout,
                          UiElement transactionReadout,
                          UiElement transactionCanvas,
                          UiElement retainedStatus,
                          List<UiElement> teamButtons,
                          List<UiElement> templateButtons,
                          List<UiElement> themeButtons) {
        private Wiring {
            teamButtons = List.copyOf(teamButtons);
            templateButtons = List.copyOf(templateButtons);
            themeButtons = List.copyOf(themeButtons);
        }
    }

    @Override
    public void advance(float dt) {
        if (reloadRequested) {
            reloadRequested = false;
            reloadDocument();
        }
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(dt);
    }

    @Override
    public void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (input != null) input.process(events);
    }

    @Override
    public void detach() {
        if (document != null) document.deactivateInput();
        input = null;
    }
}
