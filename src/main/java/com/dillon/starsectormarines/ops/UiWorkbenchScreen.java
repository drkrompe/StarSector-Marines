package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.Fonts;
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
    private static final Color BORDER = new Color(0x62, 0x82, 0xA8);
    private static final Color EDGE = new Color(0x6E, 0xD7, 0xFF);
    private static final Color TEXT = new Color(0xE4, 0xEE, 0xFA);
    private static final Color MUTED = new Color(0x8B, 0x9A, 0xAF);
    private static final Color ACCENT = new Color(0xFF, 0xD4, 0x64);
    private static final Color SELECTED = new Color(0x31, 0x50, 0x70);
    private static final Color BUTTON = new Color(0x1E, 0x30, 0x45);
    private static final Color BUTTON_HOVER = new Color(0x2A, 0x49, 0x68);
    private static final Color BUTTON_ARMED = new Color(0x46, 0x6F, 0x92);
    private static final Color GOOD = new Color(0x78, 0xD4, 0x94);

    private final List<UiElement> teamButtons = new ArrayList<>();
    private final List<UiElement> templateButtons = new ArrayList<>();

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
                .padding(14f)
                .gap(10f)
                .border(3f, EDGE);

        UiElement header = panel("header", PANEL)
                .layout(UiLayout.ROW)
                .preferredHeight(70f)
                .padding(12f)
                .gap(12f)
                .border(1f, BORDER);
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
                .gap(10f)
                .border(1f, BORDER);
        footer.child(button("back", "BACK TO COMPANY HQ", 188f,
                () -> context.goTo(ScreenId.COMPANY_HQ)));
        footer.child(label("retained-status",
                "RETAINED TREE  |  CLIPPED SCROLL + HIT TEST  |  U2 PROOF",
                GOOD).grow(1f).align(UiAlign.STRETCH, UiAlign.CENTER));
        root.child(footer);

        UiDocument built = new UiDocument(root)
                .onCancel(() -> context.goTo(ScreenId.COMPANY_HQ));
        built.canvases().set(transactionCanvas, this::paintTransactionCanvas);
        updateSelectionReadouts();
        return built;
    }

    private UiElement buildFormationPane() {
        UiElement pane = panel("formation-pane", PANEL)
                .layout(UiLayout.COLUMN)
                .padding(10f)
                .gap(7f)
                .border(1f, BORDER);
        pane.child(label("formation-heading", "COMPANY / SQUAD / FIRE TEAM", EDGE)
                .preferredHeight(34f));
        pane.child(label("squad", "1ST SQUAD  ·  12 / 12 RTD", TEXT)
                .preferredHeight(34f)
                .background(PANEL_DARK)
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
                .gap(7f)
                .border(1f, BORDER);
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
                .layout(UiLayout.STACK)
                .border(1f, BORDER);
        UiElement content = new UiElement("workspace-content")
                .layout(UiLayout.COLUMN)
                .padding(12f)
                .gap(8f)
                .align(UiAlign.STRETCH, UiAlign.STRETCH);
        content.child(label("workspace-heading", "REFIT TRANSACTION", EDGE)
                .preferredHeight(34f));
        selectedTeamReadout = label("selected-team", "", ACCENT)
                .preferredHeight(38f)
                .background(PANEL_DARK)
                .padding(8f);
        selectedTemplateReadout = label("selected-template", "", TEXT)
                .preferredHeight(38f)
                .background(PANEL_DARK)
                .padding(8f);
        content.child(selectedTeamReadout);
        content.child(selectedTemplateReadout);

        transactionCanvas = new UiElement("transaction-canvas")
                .tag(UiTag.CANVAS)
                .canvasSize(900, 150)
                .preferredHeight(150f)
                .background(PANEL_DARK)
                .border(1f, BORDER)
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
                .background(new Color(0x13, 0x2B, 0x22))
                .padding(10f)
                .border(1f, GOOD);
        content.child(transactionReadout);
        content.child(label("workspace-note",
                "This pane is a stack: the badge below overlays without moving content.", MUTED)
                .grow(1f)
                .align(UiAlign.STRETCH, UiAlign.END));
        stack.child(content);

        UiElement overlay = label("stack-proof", "STACK OVERLAY", EDGE)
                .preferredSize(178f, 34f)
                .align(UiAlign.END, UiAlign.END)
                .background(new Color(0x18, 0x3B, 0x50))
                .padding(7f)
                .border(1f, EDGE);
        stack.child(overlay);
        return stack;
    }

    private void paintTransactionCanvas(CanvasContext canvas) {
        float width = canvas.metrics().surfaceWidth();
        float nodeWidth = 190f;
        float nodeHeight = 62f;
        float top = 42f;
        float[] x = {28f, width * 0.28f, width * 0.54f, width - nodeWidth - 28f};
        String[] labels = {"FREE STOCK", "RETURNS", "REQUIRED", "VALID ISSUE"};
        Color[] colors = {BUTTON, SELECTED, new Color(0x3D, 0x2A, 0x26),
                new Color(0x13, 0x2B, 0x22)};

        canvas.text(Fonts.ORBITRON_20, "CAPTURED CANVAS DRAG  ·  MOVE ISSUE MARKER",
                24f, 12f, MUTED);
        for (int index = 0; index < x.length; index++) {
            if (index > 0) {
                canvas.line(x[index - 1] + nodeWidth, top + nodeHeight * 0.5f,
                        x[index], top + nodeHeight * 0.5f, EDGE, 2f);
            }
            canvas.fillRect(x[index], top, nodeWidth, nodeHeight, colors[index]);
            canvas.strokeRect(x[index], top, nodeWidth, nodeHeight,
                    index == x.length - 1 ? GOOD : BORDER, 2f);
            canvas.text(Fonts.ORBITRON_20, labels[index], x[index] + 13f,
                    top + 18f, index == x.length - 1 ? GOOD : TEXT);
        }
        canvas.line(issueMarkerX, 34f, issueMarkerX, 126f, ACCENT, 3f);
        canvas.fillRect(issueMarkerX - 7f, 30f, 14f, 14f, ACCENT);
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
        return new UiElement(id)
                .background(color)
                .overflow(Overflow.HIDDEN);
    }

    private static UiElement label(String id, String text, Color color) {
        return new UiElement(id)
                .text(Fonts.ORBITRON_20, text, color)
                .overflow(Overflow.HIDDEN);
    }

    private static UiElement button(String id, String text, float width, Runnable action) {
        UiElement button = new UiElement(id)
                .tag(UiTag.BUTTON)
                .preferredWidth(width)
                .background(BUTTON)
                .hoverBackground(BUTTON_HOVER)
                .armedBackground(BUTTON_ARMED)
                .border(1f, BORDER)
                .focusOutline(2f, ACCENT)
                .padding(9f)
                .overflow(Overflow.HIDDEN)
                .text(Fonts.ORBITRON_20, text, TEXT)
                .onClick(action);
        return button;
    }

    private void selectTeam(int index) {
        selectedTeam = index;
        updateSelectionReadouts();
    }

    private void selectTemplate(int index) {
        selectedTemplate = index;
        updateSelectionReadouts();
    }

    private void updateSelectionReadouts() {
        String[] teams = {"ALPHA", "BRAVO", "CHARLIE"};
        String[] templates = {
                "LINE", "RECON", "FIRE SUPPORT", "BREACH",
                "BOARDING", "ANTI-ARMOR", "SECURITY", "HAZARD RESPONSE"
        };
        for (int i = 0; i < teamButtons.size(); i++) {
            teamButtons.get(i).background(i == selectedTeam ? SELECTED : BUTTON);
        }
        for (int i = 0; i < templateButtons.size(); i++) {
            templateButtons.get(i).background(i == selectedTemplate ? SELECTED : BUTTON);
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
