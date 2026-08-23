package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.PointerButton;
import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiPointerEvent;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Developer-facing vertical proof for the retained Marine Ops UI.
 *
 * <p>The element tree is built once for this screen instance. Selection and
 * hover mutate retained nodes; {@link #attach} only re-lays the same tree into
 * the host's latest granted viewport.
 */
public final class UiWorkbenchScreen implements Screen {

    private static final Color ROOT = new Color(0x08, 0x0D, 0x15);
    private static final Color PANEL = new Color(0x15, 0x20, 0x2E);
    private static final Color PANEL_DARK = new Color(0x0E, 0x16, 0x21);
    private static final Color EDGE = new Color(0x6E, 0xD7, 0xFF);
    private static final Color TEXT = new Color(0xE4, 0xEE, 0xFA);
    private static final Color MUTED = new Color(0x8B, 0x9A, 0xAF);
    private static final Color ACCENT = new Color(0xFF, 0xD4, 0x64);
    private static final Color GOOD = new Color(0x78, 0xD4, 0x94);

    private final List<UiElement> teamButtons = new ArrayList<>();
    private final List<UiElement> templateButtons = new ArrayList<>();
    private final List<UiElement> themeButtons = new ArrayList<>();

    private MarineOpsContext context;
    private UiViewport viewport;
    private UiDocument document;
    private StarsectorUiInputAdapter input;
    private UiElement viewportReadout;
    private UiElement selectedTeamReadout;
    private UiElement selectedTemplateReadout;
    private UiElement transactionReadout;
    private UiElement transactionCanvas;
    private float issueMarkerX = 610f;
    private int selectedTeam;
    private int selectedTemplate;
    private boolean highContrast;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.context = ctx;
        this.viewport = new UiViewport(position.getX(), position.getY(),
                position.getWidth(), position.getHeight());
        if (document == null) document = buildDocument();
        updateViewportReadout();
        updateSelectionReadouts();
        document.layout(viewport.width(), viewport.height());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private UiDocument buildDocument() {
        UiElement root = panel("workbench-root", ROOT)
                .layout(UiLayout.COLUMN)
                .addClass("workbench-root");

        UiElement header = panel("header", PANEL)
                .layout(UiLayout.ROW)
                .preferredHeight(70f)
                .padding(12f)
                .gap(12f);
        header.child(label("title", "RETAINED UI WORKBENCH", ACCENT)
                .grow(1f));
        viewportReadout = label("viewport-readout", "", EDGE)
                .preferredWidth(520f)
                .align(UiAlign.END, UiAlign.CENTER);
        header.child(viewportReadout);
        root.child(header);

        UiElement body = new UiElement("body")
                .layout(UiLayout.ROW)
                .grow(1f)
                .gap(10f);
        body.child(buildFormationPane().grow(0.85f));
        body.child(buildTemplatePane().grow(1.15f));
        body.child(buildWorkspacePane().grow(1.45f));
        root.child(body);

        UiElement footer = panel("footer", PANEL_DARK)
                .layout(UiLayout.ROW)
                .preferredHeight(48f)
                .padding(7f)
                .gap(10f);
        footer.child(button("back", "BACK TO COMPANY HQ", 188f,
                () -> context.goTo(ScreenId.COMPANY_HQ)));
        UiElement standardTheme = button("theme-standard", "STANDARD", 116f,
                () -> selectTheme(false));
        UiElement contrastTheme = button("theme-contrast", "HIGH CONTRAST", 154f,
                () -> selectTheme(true));
        themeButtons.add(standardTheme);
        themeButtons.add(contrastTheme);
        footer.child(standardTheme);
        footer.child(contrastTheme);
        footer.child(label("retained-status",
                "RETAINED TREE  |  CASCADE + REAL-TIME MOTION  |  U3 PROOF",
                GOOD).grow(1f).align(UiAlign.STRETCH, UiAlign.CENTER));
        root.child(footer);

        stampScope(root, "ui-workbench");
        UiDocument built = new UiDocument(root)
                .addStyleSheet(MarineOpsThemes.WORKBENCH_COMPONENTS)
                .theme(highContrast ? MarineOpsThemes.highContrast() : MarineOpsThemes.standard())
                .onCancel(() -> context.goTo(ScreenId.COMPANY_HQ));
        built.canvases().set(transactionCanvas, this::paintTransactionCanvas);
        updateSelectionReadouts();
        return built;
    }

    private UiElement buildFormationPane() {
        UiElement pane = panel("formation-pane", PANEL)
                .layout(UiLayout.COLUMN)
                .padding(10f)
                .gap(7f);
        pane.child(label("formation-heading", "COMPANY / SQUAD / FIRE TEAM", EDGE)
                .preferredHeight(34f));
        pane.child(label("squad", "1ST SQUAD  ·  12 / 12 RTD", TEXT)
                .preferredHeight(34f)
                .addClass("surface-dark")
                .padding(7f));

        String[] teams = {"ALPHA", "BRAVO", "CHARLIE"};
        String[] issues = {"LINE TEMPLATE", "RECON TEMPLATE", "FIRE SUPPORT"};
        for (int i = 0; i < teams.length; i++) {
            final int index = i;
            UiElement team = button("team-" + i,
                    teams[i] + "  ·  4 RTD  ·  " + issues[i], Float.NaN,
                    () -> selectTeam(index)).preferredHeight(56f);
            teamButtons.add(team);
            pane.child(team);
        }
        pane.child(label("formation-note",
                "Selection stays on these retained elements; no screen rebuild.", MUTED)
                .grow(1f)
                .align(UiAlign.STRETCH, UiAlign.END));
        return pane;
    }

    private UiElement buildTemplatePane() {
        UiElement pane = panel("template-pane", PANEL)
                .layout(UiLayout.COLUMN)
                .padding(10f)
                .gap(7f);
        pane.child(label("template-heading", "TEMPLATE LIBRARY", EDGE)
                .preferredHeight(34f));
        UiElement list = new UiElement("template-list")
                .layout(UiLayout.COLUMN)
                .grow(1f)
                .gap(7f)
                .padding(2f)
                .overflow(Overflow.SCROLL);
        String[] templates = {
                "LINE  ·  FIELDED 2  ·  READY 3",
                "RECON  ·  FIELDED 1  ·  READY 1",
                "FIRE SUPPORT  ·  FIELDED 1  ·  READY 0",
                "BREACH  ·  FIELDED 0  ·  READY 1",
                "BOARDING  ·  FIELDED 0  ·  READY 2",
                "ANTI-ARMOR  ·  FIELDED 0  ·  READY 0",
                "SECURITY  ·  FIELDED 0  ·  READY 4",
                "HAZARD RESPONSE  ·  FIELDED 0  ·  READY 1"
        };
        for (int i = 0; i < templates.length; i++) {
            final int index = i;
            UiElement template = button("template-" + i, templates[i], Float.NaN,
                    () -> selectTemplate(index)).preferredHeight(50f);
            templateButtons.add(template);
            list.child(template);
        }
        pane.child(list);
        pane.child(label("library-note",
                "Plans are reusable. Finite equipment gates assignment, not design.", MUTED)
                .preferredHeight(52f)
                .align(UiAlign.STRETCH, UiAlign.END));
        return pane;
    }

    private UiElement buildWorkspacePane() {
        UiElement stack = panel("workspace-stack", PANEL)
                .layout(UiLayout.STACK);
        UiElement content = new UiElement("workspace-content")
                .layout(UiLayout.COLUMN)
                .padding(12f)
                .gap(8f)
                .overflow(Overflow.SCROLL)
                .align(UiAlign.STRETCH, UiAlign.STRETCH);
        content.child(label("workspace-heading", "REFIT TRANSACTION", EDGE)
                .preferredHeight(34f));
        selectedTeamReadout = label("selected-team", "", ACCENT)
                .preferredHeight(38f)
                .addClass("surface-dark")
                .padding(8f);
        selectedTemplateReadout = label("selected-template", "", TEXT)
                .preferredHeight(38f)
                .addClass("surface-dark")
                .padding(8f);
        content.child(selectedTeamReadout);
        content.child(selectedTemplateReadout);

        UiElement states = new UiElement("state-gallery")
                .layout(UiLayout.ROW)
                .preferredHeight(48f)
                .gap(6f);
        states.child(button("state-focus", "TAB: FOCUS", Float.NaN, () -> { }).grow(1f));
        states.child(button("state-live", "HOVER / PRESS", Float.NaN, () -> { }).grow(1f));
        states.child(button("state-selected", "SELECTED", Float.NaN, () -> { })
                .grow(1f).selected(true));
        states.child(button("state-disabled", "DISABLED", Float.NaN, () -> { })
                .grow(1f).disabled(true));
        content.child(states);

        transactionCanvas = new UiElement("transaction-canvas")
                .tag(UiTag.CANVAS)
                .canvasSize(900, 150)
                .preferredHeight(150f)
                .addClass("panel")
                .addClass("surface-dark")
                .overflow(Overflow.HIDDEN)
                .onPointerDown(event -> {
                    if (event.button() == PointerButton.PRIMARY) {
                        event.capturePointer();
                        moveIssueMarker(event);
                    }
                })
                .onPointerMove(event -> {
                    if (document != null && document.pointerCapture() == transactionCanvas) {
                        moveIssueMarker(event);
                    }
                })
                .onPointerUp(event -> {
                    if (event.button() == PointerButton.PRIMARY) moveIssueMarker(event);
                });
        content.child(transactionCanvas);

        content.child(issueRow("free", "FREE STOCK", "18 rifles  ·  4 armor  ·  1 support"));
        content.child(issueRow("returns", "RETURNED ISSUE", "+4 rifles  ·  +4 armor"));
        content.child(issueRow("required", "REQUIRED ISSUE", "-3 rifles  ·  -4 armor  ·  -1 support"));
        transactionReadout = label("transaction-result", "", GOOD)
                .preferredHeight(46f)
                .addClass("good-surface")
                .padding(10f)
                .addClass("panel");
        content.child(transactionReadout);
        content.child(label("workspace-note",
                "This pane is a stack: the badge below overlays without moving content.", MUTED)
                .grow(1f)
                .align(UiAlign.STRETCH, UiAlign.END));
        stack.child(content);

        UiElement overlay = label("stack-proof", "STACK OVERLAY", EDGE)
                .preferredSize(178f, 34f)
                .align(UiAlign.END, UiAlign.END)
                .addClass("edge-surface")
                .padding(7f)
                .addClass("panel");
        stack.child(overlay);
        return stack;
    }

    private void paintTransactionCanvas(CanvasContext canvas) {
        MarineOpsThemes.CanvasPalette palette = MarineOpsThemes.canvasPalette(highContrast);
        BitmapFont font = document.styles().fontFor(transactionCanvas);
        float width = canvas.metrics().surfaceWidth();
        float nodeWidth = 190f;
        float nodeHeight = 62f;
        float top = 42f;
        float[] x = {28f, width * 0.28f, width * 0.54f, width - nodeWidth - 28f};
        String[] labels = {"FREE STOCK", "RETURNS", "REQUIRED", "VALID ISSUE"};
        Color[] colors = {palette.button(), palette.selected(), palette.danger(), palette.valid()};

        canvas.text(font, "CAPTURED CANVAS DRAG  ·  MOVE ISSUE MARKER",
                24f, 12f, palette.muted());
        for (int index = 0; index < x.length; index++) {
            if (index > 0) {
                canvas.line(x[index - 1] + nodeWidth, top + nodeHeight * 0.5f,
                        x[index], top + nodeHeight * 0.5f, palette.edge(), 2f);
            }
            canvas.fillRect(x[index], top, nodeWidth, nodeHeight, colors[index]);
            canvas.strokeRect(x[index], top, nodeWidth, nodeHeight,
                    index == x.length - 1 ? palette.good() : palette.border(), 2f);
            canvas.text(font, labels[index], x[index] + 13f,
                    top + 18f, index == x.length - 1 ? palette.good() : palette.text());
        }
        canvas.line(issueMarkerX, 34f, issueMarkerX, 126f, palette.accent(), 3f);
        canvas.fillRect(issueMarkerX - 7f, 30f, 14f, 14f, palette.accent());
    }

    private void moveIssueMarker(UiPointerEvent event) {
        if (document == null || transactionCanvas == null) return;
        CanvasMetrics metrics = document.canvasMetrics(transactionCanvas, 1f);
        float canvasX = metrics.toCanvasX(event.x());
        if (!Float.isFinite(canvasX)) return;
        issueMarkerX = Math.max(8f,
                Math.min(metrics.surfaceWidth() - 8f, canvasX));
        document.canvases().invalidate(transactionCanvas);
    }

    private static UiElement issueRow(String id, String label, String value) {
        UiElement row = new UiElement(id)
                .layout(UiLayout.ROW)
                .preferredHeight(42f)
                .gap(8f);
        row.child(label(id + "-label", label, MUTED).preferredWidth(170f));
        row.child(label(id + "-value", value, TEXT).grow(1f));
        return row;
    }

    private static UiElement panel(String id, Color color) {
        UiElement panel = new UiElement(id).overflow(Overflow.HIDDEN);
        if (!ROOT.equals(color)) panel.addClass("panel");
        if (PANEL_DARK.equals(color)) panel.addClass("surface-dark");
        return panel;
    }

    private static UiElement label(String id, String text, Color color) {
        return new UiElement(id)
                .addClass("label")
                .addClass(toneClass(color))
                .text(text)
                .overflow(Overflow.HIDDEN);
    }

    private static UiElement button(String id, String text, float width, Runnable action) {
        return new UiElement(id)
                .tag(UiTag.BUTTON)
                .preferredWidth(width)
                .text(text)
                .onClick(action);
    }

    private static String toneClass(Color color) {
        if (EDGE.equals(color)) return "tone-edge";
        if (MUTED.equals(color)) return "tone-muted";
        if (ACCENT.equals(color)) return "tone-accent";
        if (GOOD.equals(color)) return "tone-good";
        return "tone-text";
    }

    private static void stampScope(UiElement element, String scopeClass) {
        element.addClass(scopeClass);
        for (UiElement child : element.children()) stampScope(child, scopeClass);
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
            document.theme(highContrast
                    ? MarineOpsThemes.highContrast() : MarineOpsThemes.standard());
        }
        updateSelectionReadouts();
    }

    private void updateSelectionReadouts() {
        String[] teams = {"ALPHA", "BRAVO", "CHARLIE"};
        String[] templates = {
                "LINE", "RECON", "FIRE SUPPORT", "BREACH",
                "BOARDING", "ANTI-ARMOR", "SECURITY", "HAZARD RESPONSE"
        };
        for (int i = 0; i < teamButtons.size(); i++) {
            teamButtons.get(i).selected(i == selectedTeam);
        }
        for (int i = 0; i < templateButtons.size(); i++) {
            templateButtons.get(i).selected(i == selectedTemplate);
        }
        for (int i = 0; i < themeButtons.size(); i++) {
            themeButtons.get(i).selected(highContrast == (i == 1));
        }
        if (selectedTeamReadout != null) {
            selectedTeamReadout.text("TARGET  ·  1ST SQUAD / " + teams[selectedTeam]);
        }
        if (selectedTemplateReadout != null) {
            selectedTemplateReadout.text("CANDIDATE  ·  " + templates[selectedTemplate] + " TEMPLATE");
        }
        if (transactionReadout != null) {
            transactionReadout.text("VALID  ·  ISSUE " + templates[selectedTemplate]
                    + " TO " + teams[selectedTeam] + " IN ONE ATOMIC TRANSACTION");
        }
    }

    private void updateViewportReadout() {
        if (viewportReadout == null || viewport == null) return;
        viewportReadout.text(String.format(Locale.ROOT,
                "GRANTED %.1f x %.1f  ·  DOCUMENT ORIGIN TOP-LEFT",
                viewport.width(), viewport.height()));
    }

    @Override
    public void advance(float dt) {
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
