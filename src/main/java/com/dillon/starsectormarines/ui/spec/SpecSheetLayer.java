package com.dillon.starsectormarines.ui.spec;

import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * The one floating overlay a document shows a {@link SpecSheet} in
 * ({@code ui-nouns.md}).
 *
 * <p>One layer per document, installed once at the document root and re-filled
 * from whichever subject is hovered, rather than one popup element per card.
 * It is <strong>transparent to the pointer</strong> — {@code pointer-events:
 * none}, the documented exception to design law 6 — because an overlay that
 * could take the hover that opened it would flicker between open and closed
 * wherever it covered its own anchor.
 *
 * <p>Placement is CSS's own vocabulary: the layer is a {@code position:
 * absolute} child of the root placed by {@code left} and {@code top}, so it is
 * out of the root's flow and cannot shift a single screen element. It is added
 * last, so paint order puts it above everything (law 6).
 *
 * <p>Its height is <strong>measured, never estimated</strong>. Filling the
 * sheet lays the document out once with the layer running to the bottom of the
 * root, reads the bottom of its own last child — a real wrapped-text
 * measurement through the production {@code UiTextMeasurer} — and lays out
 * again at that height. A character-count guess disagrees with the font the
 * moment either changes.
 *
 * <p>Show and hide toggle {@link #HIDDEN_CLASS} rather than mutating geometry
 * directly, so a stylesheet may transition the overlay in. The shipped sheet
 * deliberately declares no transition: headless evidence renders one frame, and
 * a fade would photograph as an empty rectangle.
 */
public final class SpecSheetLayer {

    /** Retained id of the overlay element. */
    public static final String ELEMENT_ID = "spec-sheet-layer";

    /** Class present exactly while no sheet is shown. */
    public static final String HIDDEN_CLASS = "spec-sheet-hidden";

    /** Name of the stylesheet {@link #install} adds; replaceable like any other. */
    public static final String SHEET_NAME = "spec-sheet";

    /** Gap between the anchor's border box and the overlay, in document pixels. */
    private static final float ANCHOR_GAP = 10f;

    private static final String ACCENT_PREFIX = "spec-sheet-";

    /**
     * The shared fragment. It lives here rather than in a screen's own MLX
     * because the overlay is one element for every screen: a per-screen copy is
     * the duplication this story exists to remove. Colours are the Fleet
     * Armory's own, which is where the first per-card popups lived.
     */
    private static final String CSS = """
            .spec-sheet {
                position: absolute;
                pointer-events: none;
                flex-direction: column;
                width: 360px;
                padding: 10px;
                gap: 6px;
                border-width: 2px;
                overflow: hidden;
                font-family: body;
                background-color: #09131d;
                border-color: #53c6e8;
                color: #d5deea;
            }
            .spec-sheet.spec-sheet-weapon { border-color: #53c6e8; }
            .spec-sheet.spec-sheet-armor { border-color: #dfb84d; }
            .spec-sheet.spec-sheet-special { border-color: #71d6c1; }
            .spec-sheet.spec-sheet-system { border-color: #c79ce8; }
            .spec-sheet.spec-sheet-mech { border-color: #e2965c; }
            .spec-sheet-heading { flex-direction: row; height: 34px; gap: 8px; }
            .spec-sheet-crest { width: 32px; height: 32px; }
            .spec-sheet-crest.spec-sheet-crest-absent { width: 0px; height: 0px; }
            .spec-sheet-heading-copy { flex-direction: column; width: 0px; flex-grow: 1; gap: 3px; }
            .spec-sheet-title { height: 17px; font-family: heading; color: #eaf3ff; }
            .spec-sheet-subtitle { height: 14px; color: #8b9aaf; }
            .spec-sheet-stat { flex-direction: column; height: 16px; gap: 3px; }
            .spec-sheet-stat.spec-sheet-stat-metered { height: 23px; }
            .spec-sheet-stat-line { flex-direction: row; height: 16px; gap: 6px; }
            .spec-sheet-stat-label { width: 0px; flex-grow: 1; color: #8b9aaf; }
            .spec-sheet-stat-value { width: 120px; text-align: right; }
            .spec-sheet-stat-track { flex-direction: row; height: 4px; background-color: #1a2836; }
            .spec-sheet-stat-fill { height: 4px; background-color: #53c6e8; }
            .spec-sheet-armor .spec-sheet-stat-fill { background-color: #dfb84d; }
            .spec-sheet-special .spec-sheet-stat-fill { background-color: #71d6c1; }
            .spec-sheet-system .spec-sheet-stat-fill { background-color: #c79ce8; }
            .spec-sheet-mech .spec-sheet-stat-fill { background-color: #e2965c; }
            .spec-sheet-note { white-space: normal; }
            .spec-sheet.spec-sheet-hidden {
                width: 0px;
                height: 0px;
                padding: 0px;
                gap: 0px;
                border-width: 0px;
                opacity: 0;
            }
            """;

    private static final Map<UiDocument, SpecSheetLayer> INSTALLED = new WeakHashMap<>();

    private final UiDocument document;
    private final UiElement root;
    private final UiElement heading;
    private final UiElement crest;
    private final UiElement title;
    private final UiElement subtitle;
    private final List<StatRow> statRows = new ArrayList<>();
    private final List<UiElement> notes = new ArrayList<>();

    private String accentClass;
    private int usedStats;
    private int usedNotes;

    private SpecSheetLayer(UiDocument document) {
        this.document = document;
        crest = new UiElement(ELEMENT_ID + "-crest").tag(UiTag.IMAGE)
                .addClass("spec-sheet-crest");
        title = new UiElement(ELEMENT_ID + "-title").addClass("spec-sheet-title");
        subtitle = new UiElement(ELEMENT_ID + "-subtitle").addClass("spec-sheet-subtitle");
        heading = new UiElement(ELEMENT_ID + "-heading")
                .addClass("spec-sheet-heading")
                .child(crest)
                .child(new UiElement(ELEMENT_ID + "-heading-copy")
                        .addClass("spec-sheet-heading-copy")
                        .child(title)
                        .child(subtitle));
        root = new UiElement(ELEMENT_ID)
                .addClass("spec-sheet")
                .addClass(HIDDEN_CLASS)
                .child(heading);
    }

    /**
     * Installs the layer on this document, or returns the one already there.
     *
     * <p>Idempotent because a screen family installs from its own rebuild path
     * and a snapshot route may install again around it; two overlays on one
     * document would be two answers to the same hover.
     */
    public static SpecSheetLayer install(UiDocument document) {
        Objects.requireNonNull(document, "document");
        synchronized (INSTALLED) {
            SpecSheetLayer existing = INSTALLED.get(document);
            if (existing != null) return existing;
            SpecSheetLayer layer = new SpecSheetLayer(document);
            document.addStyleSheet(StyleSheet.parse(SHEET_NAME, CSS));
            document.root().child(layer.root);
            INSTALLED.put(document, layer);
            return layer;
        }
    }

    /** The overlay element itself, for tests and for hit-test assertions. */
    public UiElement element() {
        return root;
    }

    public boolean visible() {
        return !root.hasClass(HIDDEN_CLASS);
    }

    /**
     * Fills the overlay with one sheet and places it beside its subject: to the
     * right of the anchor when there is room, otherwise to its left, otherwise
     * beneath it — and clamped to the root's content box either way, so the
     * overlay never leaves the panel it belongs to.
     */
    public void show(SpecSheet sheet, UiElement anchor) {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(anchor, "anchor");
        fill(sheet);
        root.removeClass(HIDDEN_CLASS);
        root.style("left: 0px; top: 0px");
        relayout();
        place(anchor.box().borderBox(), measuredHeight());
        relayout();
    }

    /** Closes the overlay, leaving its filled content retained for reuse. */
    public void hide() {
        if (!visible()) return;
        root.addClass(HIDDEN_CLASS);
        root.style("");
    }

    private void fill(SpecSheet sheet) {
        if (accentClass != null) root.removeClass(accentClass);
        accentClass = ACCENT_PREFIX + token(sheet.accent());
        root.addClass(accentClass);

        title.text(sheet.title());
        subtitle.text(sheet.subtitle());
        crest.imageSource(sheet.crestPath());
        crest.classed("spec-sheet-crest-absent",
                sheet.crestPath() == null || sheet.crestPath().isBlank());

        for (int index = usedStats - 1; index >= 0; index--) root.remove(statRows.get(index).row);
        for (int index = usedNotes - 1; index >= 0; index--) root.remove(notes.get(index));
        usedStats = sheet.stats().size();
        usedNotes = sheet.notes().size();

        for (int index = 0; index < usedStats; index++) {
            StatRow row = statRow(index);
            row.apply(sheet.stats().get(index));
            root.child(row.row);
        }
        for (int index = 0; index < usedNotes; index++) {
            UiElement note = note(index);
            note.text(sheet.notes().get(index));
            root.child(note);
        }
    }

    private StatRow statRow(int index) {
        while (statRows.size() <= index) statRows.add(new StatRow(statRows.size()));
        return statRows.get(index);
    }

    private UiElement note(int index) {
        while (notes.size() <= index) {
            notes.add(new UiElement(ELEMENT_ID + "-note-" + notes.size())
                    .addClass("spec-sheet-note"));
        }
        return notes.get(index);
    }

    /**
     * The overlay's own border-box height after a layout pass, taken from the
     * bottom of its last laid-out child rather than from a text-length guess.
     */
    private float measuredHeight() {
        float bottom = root.box().contentBox().y();
        for (UiElement child : root.children()) {
            bottom = Math.max(bottom, child.box().borderBox().bottom());
        }
        return bottom - root.box().borderBox().y() + frame();
    }

    /** Padding and border between the overlay's border box and its content box. */
    private float frame() {
        return root.padding().bottom() + root.borderWidth();
    }

    private void place(Rect anchor, float height) {
        UiElement parent = root.parent();
        Rect bounds = parent == null ? document.viewport() : parent.box().contentBox();
        float width = root.box().borderBox().width();

        float x;
        float y = anchor.y();
        if (anchor.right() + ANCHOR_GAP + width <= bounds.right()) {
            x = anchor.right() + ANCHOR_GAP;
        } else if (anchor.x() - ANCHOR_GAP - width >= bounds.x()) {
            x = anchor.x() - ANCHOR_GAP - width;
        } else {
            x = anchor.x();
            y = anchor.bottom() + ANCHOR_GAP;
        }
        x = clamp(x, bounds.x(), bounds.right() - width);
        y = clamp(y, bounds.y(), bounds.bottom() - height);
        // CSS height here is the content box, as everywhere else in this toolkit.
        float content = height - root.padding().vertical() - root.borderWidth() * 2f;
        root.style(String.format(Locale.ROOT, "left: %.2fpx; top: %.2fpx; height: %.2fpx",
                x - bounds.x(), y - bounds.y(), Math.max(0f, content)));
    }

    private void relayout() {
        Rect viewport = document.viewport();
        if (viewport.width() <= 0f || viewport.height() <= 0f) return;
        document.layout(viewport.width(), viewport.height());
    }

    private static float clamp(float value, float low, float high) {
        if (high < low) return low;
        return Math.max(low, Math.min(high, value));
    }

    /** One accent token, reduced to something that can be a class name. */
    private static String token(String accent) {
        StringBuilder result = new StringBuilder(accent.length());
        for (char character : accent.toLowerCase(Locale.ROOT).toCharArray()) {
            result.append(character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9' ? character : '-');
        }
        String value = result.toString();
        return value.equals("hidden") ? "item" : value;
    }

    /** One stat row: a label and value line, plus a meter track when it has one. */
    private static final class StatRow {

        private final UiElement row;
        private final UiElement label;
        private final UiElement value;
        private final UiElement track;
        private final UiElement fill;

        private StatRow(int index) {
            String id = ELEMENT_ID + "-stat-" + index;
            label = new UiElement(id + "-label").addClass("spec-sheet-stat-label");
            value = new UiElement(id + "-value").addClass("spec-sheet-stat-value");
            fill = new UiElement(id + "-fill").addClass("spec-sheet-stat-fill");
            track = new UiElement(id + "-track")
                    .addClass("spec-sheet-stat-track")
                    .child(fill);
            row = new UiElement(id)
                    .addClass("spec-sheet-stat")
                    .child(new UiElement(id + "-line")
                            .addClass("spec-sheet-stat-line")
                            .child(label)
                            .child(value));
        }

        private void apply(SpecSheet.Stat stat) {
            label.text(stat.label());
            value.text(stat.value());
            boolean metered = stat.hasMeter();
            row.classed("spec-sheet-stat-metered", metered);
            boolean attached = track.parent() == row;
            if (metered && !attached) row.child(track);
            if (!metered && attached) row.remove(track);
            if (metered) {
                fill.style(String.format(Locale.ROOT, "width: %.2f%%",
                        Math.max(0f, Math.min(1f, stat.fill())) * 100f));
            }
        }
    }
}
