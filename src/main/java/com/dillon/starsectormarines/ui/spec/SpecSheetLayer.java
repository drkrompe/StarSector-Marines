package com.dillon.starsectormarines.ui.spec;

import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

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
 *
 * <p>A sheet that names a {@link SpecSheet.Scene} gets a canvas under its
 * heading, painted by the {@link SceneSource} registered for the scene's kind.
 * The layer knows kinds only by name, so the code that paints a weapon on the
 * range stays out of this package; a kind with no source shows no canvas.
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
            .spec-sheet, .spec-card {
                flex-direction: column;
                padding: 10px;
                gap: 6px;
                border-width: 2px;
                overflow: hidden;
                font-family: body;
                text-align: left;
                background-color: #09131d;
                border-color: #53c6e8;
                color: #d5deea;
            }
            .spec-sheet { position: absolute; pointer-events: none; width: 360px; }
            .spec-card { width: 504px; }
            .spec-sheet.spec-sheet-weapon { border-color: #53c6e8; }
            .spec-sheet.spec-sheet-armor { border-color: #dfb84d; }
            .spec-sheet.spec-sheet-special { border-color: #71d6c1; }
            .spec-sheet.spec-sheet-system { border-color: #c79ce8; }
            .spec-sheet.spec-sheet-mech { border-color: #e2965c; }
            .spec-sheet-heading { flex-direction: row; height: 34px; gap: 8px; }
            .spec-sheet-crest { width: 32px; height: 32px; }
            .spec-sheet-crest.spec-sheet-crest-absent { width: 0px; height: 0px; }
            .spec-sheet-heading-copy { flex-direction: column; width: 0px; flex-grow: 1; gap: 3px; }
            .spec-sheet-title { height: 17px; white-space: normal; font-family: heading; color: #eaf3ff; }
            .spec-sheet-subtitle { height: 14px; white-space: normal; color: #8b9aaf; }
            .spec-sheet-stat { flex-direction: column; height: 16px; gap: 3px; }
            .spec-sheet-stat.spec-sheet-stat-metered { height: 23px; }
            .spec-sheet-stat-line { flex-direction: row; height: 16px; gap: 6px; }
            .spec-sheet-stat-label { width: 0px; flex-grow: 1; white-space: normal; color: #8b9aaf; }
            .spec-sheet-stat-value { width: 120px; white-space: normal; text-align: right; }
            .spec-sheet-stat-track { flex-direction: row; height: 4px; background-color: #1a2836; }
            .spec-sheet-stat-fill { height: 4px; background-color: #53c6e8; }
            .spec-sheet-rating { flex-direction: row; align-items: center; gap: 2px; pointer-events: none; }
            .spec-sheet-rating-slot { width: 16px; height: 16px; pointer-events: none; }
            .spec-sheet-rating-empty { position: absolute; left: 0px; top: 0px; width: 16px; height: 16px; pointer-events: none; }
            .spec-sheet-rating-lit { position: absolute; left: 0px; top: 0px; height: 16px; flex-direction: row; overflow: hidden; pointer-events: none; }
            .spec-sheet-rating-lit-image { width: 16px; height: 16px; pointer-events: none; }
            .spec-sheet-armor .spec-sheet-stat-fill { background-color: #dfb84d; }
            .spec-card .spec-sheet-stamp-badge { width: 466px; }
            .spec-sheet-crest.spec-card-crest { width: 48px; height: 32px; }
            .spec-sheet-special .spec-sheet-stat-fill { background-color: #71d6c1; }
            .spec-sheet-system .spec-sheet-stat-fill { background-color: #c79ce8; }
            .spec-sheet-mech .spec-sheet-stat-fill { background-color: #e2965c; }
            .spec-sheet-note { white-space: normal; }
            .spec-sheet-scene { width: 360px; height: 168px; }
            .spec-sheet-stamp {
                flex-direction: column;
                height: 24px;
                gap: 2px;
                padding: 3px 6px 3px 6px;
                border-width: 1px;
                border-color: #1f3346;
                background-color: #0e1a26;
            }
            .spec-sheet-stamp.spec-sheet-stamp-restricted { height: 46px; }
            .spec-sheet-stamp-issued { height: 16px; color: #8b9aaf; }
            .spec-sheet-stamp-badge {
                flex-direction: row;
                align-items: center;
                width: 300px;
                height: 20px;
                gap: 4px;
                padding: 0px 6px 0px 2px;
                background-color: #3a1512;
            }
            .spec-sheet-stamp-badge.spec-sheet-stamp-badge-absent { width: 0px; height: 0px; padding: 0px; gap: 0px; }
            .spec-sheet-stamp-seal { width: 18px; height: 18px; }
            .spec-sheet-stamp-restriction { width: 0px; flex-grow: 1; font-family: heading; color: #f08a74; }
            .spec-sheet.spec-sheet-wide { width: 504px; }
            .spec-sheet-seats { flex-direction: row; height: 146px; gap: 9px; }
            .spec-sheet-team {
                flex-direction: column;
                width: 152px;
                height: 136px;
                padding: 4px;
                gap: 4px;
                border-width: 1px;
                border-color: #1f3346;
                background-color: #0e1a26;
            }
            .spec-sheet-team-label { height: 12px; text-align: center; color: #8b9aaf; }
            .spec-sheet-seat-row { flex-direction: row; height: 58px; gap: 4px; }
            .spec-sheet-seat {
                width: 64px;
                height: 48px;
                padding: 4px;
                border-width: 1px;
                border-color: #2a4258;
                background-color: #09131d;
            }
            .spec-sheet-seat-image { width: 64px; height: 48px; }
            .spec-sheet-seat-badge {
                position: absolute;
                left: 38px;
                top: 22px;
                width: 28px;
                height: 28px;
                border-width: 1px;
                border-color: #2c4a63;
                background-color: #09131d;
            }
            .spec-sheet-seat-badge.spec-sheet-seat-badge-absent { width: 0px; height: 0px; border-width: 0px; }
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
    private static final Map<String, Supplier<SceneSource>> SCENE_SOURCES = new HashMap<>();

    /** Width of a scene's canvas: the overlay's content width. */
    public static final int SCENE_WIDTH = 360;
    /** Height of a scene's canvas. */
    public static final int SCENE_HEIGHT = 168;
    /** The seal a Restricted card's badge carries, the same one its face shows. */
    public static final String RESTRICTED_SEAL = "graphics/ui/company/restricted-seal.svg";

    private final UiDocument document;
    private final UiElement root;
    private final UiElement heading;
    private final UiElement crest;
    private final UiElement title;
    private final UiElement subtitle;
    private final UiElement stamp;
    private final UiElement stampIssued;
    private final UiElement stampBadge;
    private final UiElement stampRestriction;
    private final List<StatRow> statRows = new ArrayList<>();
    private final List<UiElement> notes = new ArrayList<>();
    private final UiElement seats;
    private final List<UiElement> seatRows = new ArrayList<>();
    private final List<UiElement> teamBlocks = new ArrayList<>();
    private final List<UiElement> teamLabels = new ArrayList<>();
    private final List<SeatTile> seatTiles = new ArrayList<>();

    private final Map<String, SceneSource> sceneSources = new HashMap<>();
    private UiElement sceneCanvas;
    private SpecSheet.Scene shownScene;
    private long sceneShownNanos;
    private DoubleSupplier sceneClock;

    private String accentClass;
    private int usedStats;
    private int usedNotes;
    private int usedSeatRows;
    private int usedTeams;
    private boolean seatsAttached;
    private boolean stampAttached;
    private boolean sceneAttached;

    private SpecSheetLayer(UiDocument document) {
        this.document = document;
        crest = new UiElement(ELEMENT_ID + "-crest").tag(UiTag.IMAGE)
                .addClass("spec-sheet-crest");
        seats = new UiElement(ELEMENT_ID + "-seats").addClass("spec-sheet-seats");
        title = new UiElement(ELEMENT_ID + "-title").addClass("spec-sheet-title");
        subtitle = new UiElement(ELEMENT_ID + "-subtitle").addClass("spec-sheet-subtitle");
        heading = new UiElement(ELEMENT_ID + "-heading")
                .addClass("spec-sheet-heading")
                .child(crest)
                .child(new UiElement(ELEMENT_ID + "-heading-copy")
                        .addClass("spec-sheet-heading-copy")
                        .child(title)
                        .child(subtitle));
        stampIssued = new UiElement(ELEMENT_ID + "-stamp-issued").addClass("spec-sheet-stamp-issued");
        stampRestriction = new UiElement(ELEMENT_ID + "-stamp-restriction")
                .addClass("spec-sheet-stamp-restriction");
        stampBadge = new UiElement(ELEMENT_ID + "-stamp-badge")
                .addClass("spec-sheet-stamp-badge")
                .child(new UiElement(ELEMENT_ID + "-stamp-seal").tag(UiTag.IMAGE)
                        .addClass("spec-sheet-stamp-seal").imageSource(RESTRICTED_SEAL))
                .child(stampRestriction);
        stamp = new UiElement(ELEMENT_ID + "-stamp")
                .addClass("spec-sheet-stamp")
                .child(stampBadge)
                .child(stampIssued);
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

    /**
     * Registers what paints scenes of one kind. Each layer makes its own
     * source the first time it shows that kind, so a source's textures belong
     * to one document.
     */
    public static void registerSceneSource(String kind, Supplier<SceneSource> source) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        synchronized (SCENE_SOURCES) {
            SCENE_SOURCES.put(kind, source);
        }
    }

    /**
     * Replaces the wall clock a scene plays to with a fixed one, for headless
     * evidence; null restores the wall clock.
     */
    public void sceneClock(DoubleSupplier seconds) {
        sceneClock = seconds;
    }

    /**
     * Gives every scene source's resources back, when the document's screen is
     * put away; a later sheet loads what it shows afresh.
     */
    public void releaseScenes() {
        clearScene();
        for (SceneSource source : sceneSources.values()) source.release();
    }

    /** The scene canvas, for tests; null until a sheet with a scene is shown. */
    public UiElement sceneCanvas() {
        return sceneCanvas;
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
        Objects.requireNonNull(anchor, "anchor");
        show(sheet, anchor.box().borderBox());
    }

    /** As {@link #show(SpecSheet, UiElement)}, beside a box in document pixels. */
    public void show(SpecSheet sheet, Rect anchor) {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(anchor, "anchor");
        fill(sheet);
        raiseToTop();
        root.removeClass(HIDDEN_CLASS);
        root.style("left: 0px; top: 0px");
        relayout();
        fitWrappedRows();
        relayout();
        place(anchor, measuredHeight());
        relayout();
    }

    /**
     * Keeps the overlay last in paint order. Being added last at install is not
     * enough: a screen mounts its tutorial shade and callout on the same root
     * afterwards ({@code PreparationGuide}), and the shade then dimmed every part
     * of an open sheet that lay outside the spotlit region.
     */
    private void raiseToTop() {
        UiElement parent = root.parent();
        if (parent == null) return;
        List<UiElement> siblings = parent.children();
        if (siblings.get(siblings.size() - 1) != root) parent.child(root);
    }

    /** Closes the overlay, leaving its filled content retained for reuse. */
    public void hide() {
        clearScene();
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

        if (stampAttached) root.remove(stamp);
        stampAttached = sheet.stamp() != null;
        if (stampAttached) {
            stampIssued.text(sheet.stamp().issued());
            stampRestriction.text(sheet.stamp().restricted() ? sheet.stamp().restriction() : "");
            stampBadge.classed("spec-sheet-stamp-badge-absent", !sheet.stamp().restricted());
            stamp.classed("spec-sheet-stamp-restricted", sheet.stamp().restricted());
        }
        if (sceneAttached) root.remove(sceneCanvas);
        for (int index = usedStats - 1; index >= 0; index--) root.remove(statRows.get(index).row);
        for (int index = usedNotes - 1; index >= 0; index--) root.remove(notes.get(index));
        if (seatsAttached) root.remove(seats);
        for (int index = usedTeams - 1; index >= 0; index--) seats.remove(teamBlocks.get(index));
        usedStats = sheet.stats().size();
        usedNotes = sheet.notes().size();

        if (stampAttached) root.child(stamp);
        fillScene(sheet.scene());
        for (int index = 0; index < usedStats; index++) {
            StatRow row = statRow(index);
            row.apply(sheet.stats().get(index));
            root.child(row.row);
        }
        fillSeats(sheet.seats(), sheet.teams());
        for (int index = 0; index < usedNotes; index++) {
            UiElement note = note(index);
            note.text(sheet.notes().get(index));
            root.child(note);
        }
    }

    /**
     * Paints a squad-wide sheet's seats as fire teams side by side, each a
     * labelled two-by-two block of {@link SpecSheet#TEAM_SIZE} tiles — the
     * grouping the squad card's muster already uses — and widens the overlay to
     * hold them. A single-item sheet has none and keeps the ordinary width.
     */
    private void fillSeats(List<SpecSheet.Seat> sheetSeats, List<String> teams) {
        seatsAttached = !sheetSeats.isEmpty();
        root.classed("spec-sheet-wide", seatsAttached);
        usedTeams = 0;
        if (!seatsAttached) return;
        int perRow = SpecSheet.TEAM_SIZE / 2;
        usedTeams = (sheetSeats.size() + SpecSheet.TEAM_SIZE - 1) / SpecSheet.TEAM_SIZE;
        for (int team = 0; team < usedTeams; team++) {
            UiElement block = teamBlock(team);
            UiElement label = teamLabels.get(team);
            label.text(team < teams.size() ? teams.get(team) : "");
            for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
                UiElement row = seatRow(team * 2 + rowIndex);
                for (int index = row.children().size() - 1; index >= 0; index--) row.remove(row.children().get(index));
                for (int column = 0; column < perRow; column++) {
                    int seat = team * SpecSheet.TEAM_SIZE + rowIndex * perRow + column;
                    if (seat >= sheetSeats.size()) break;
                    SeatTile tile = seatTile(seat);
                    tile.apply(sheetSeats.get(seat));
                    row.child(tile.tile);
                }
            }
            seats.child(block);
        }
        root.child(seats);
    }

    private UiElement teamBlock(int index) {
        while (teamBlocks.size() <= index) {
            int team = teamBlocks.size();
            UiElement label = new UiElement(ELEMENT_ID + "-team-" + team + "-label")
                    .addClass("spec-sheet-team-label");
            teamLabels.add(label);
            teamBlocks.add(new UiElement(ELEMENT_ID + "-team-" + team)
                    .addClass("spec-sheet-team")
                    .child(label)
                    .child(seatRow(team * 2))
                    .child(seatRow(team * 2 + 1)));
        }
        return teamBlocks.get(index);
    }

    private UiElement seatRow(int index) {
        while (seatRows.size() <= index) {
            seatRows.add(new UiElement(ELEMENT_ID + "-seat-row-" + seatRows.size())
                    .addClass("spec-sheet-seat-row"));
        }
        return seatRows.get(index);
    }

    private SeatTile seatTile(int index) {
        while (seatTiles.size() <= index) seatTiles.add(new SeatTile(seatTiles.size()));
        return seatTiles.get(index);
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

    /** Row containers need explicit heights; derive them from the resolved font and allocated width. */
    private void fitWrappedRows() {
        float titleHeight = textHeight(title, 17f);
        float subtitleHeight = textHeight(subtitle, 14f);
        height(title, titleHeight);
        height(subtitle, subtitleHeight);
        height(heading, Math.max(34f, titleHeight + 3f + subtitleHeight));
        for (int index = 0; index < usedStats; index++) {
            StatRow stat = statRows.get(index);
            float lineHeight = Math.max(textHeight(stat.label, 16f), textHeight(stat.value, 16f));
            height(stat.line, lineHeight);
            height(stat.row, lineHeight + (stat.track.parent() == stat.row ? 7f : 0f));
        }
    }

    private float textHeight(UiElement element, float minimum) {
        var font = document.styles().fontFor(element);
        if (font == null || element.text() == null) return minimum;
        return Math.max(minimum, font.getLineHeight()
                * font.wrapLines(element.text(), element.box().contentBox().width()).size());
    }

    private static void height(UiElement element, float height) {
        element.style(String.format(Locale.ROOT, "height: %.2fpx", height));
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
    /**
     * Shows the sheet's scene under its heading, restarting its clock only when
     * the scene itself changes, so a live sheet re-filled every frame keeps
     * playing.
     */
    private void fillScene(SpecSheet.Scene scene) {
        sceneAttached = false;
        SceneSource source = scene == null ? null : sceneSource(scene.kind());
        if (source == null) {
            clearScene();
            return;
        }
        if (sceneCanvas == null) {
            sceneCanvas = new UiElement(ELEMENT_ID + "-scene").tag(UiTag.CANVAS)
                    .addClass("spec-sheet-scene")
                    .canvasSize(SCENE_WIDTH, SCENE_HEIGHT);
        }
        root.child(sceneCanvas);
        sceneAttached = true;
        if (scene.equals(shownScene) && document.canvases().producerOf(sceneCanvas) != null) return;
        CanvasProducer producer = source.open(scene, this::sceneSeconds);
        if (producer == null) {
            root.remove(sceneCanvas);
            sceneAttached = false;
            clearScene();
            return;
        }
        shownScene = scene;
        sceneShownNanos = System.nanoTime();
        document.canvases().set(sceneCanvas, producer);
    }

    private SceneSource sceneSource(String kind) {
        SceneSource source = sceneSources.get(kind);
        if (source != null) return source;
        Supplier<SceneSource> factory;
        synchronized (SCENE_SOURCES) {
            factory = SCENE_SOURCES.get(kind);
        }
        if (factory == null) return null;
        source = factory.get();
        sceneSources.put(kind, source);
        return source;
    }

    private double sceneSeconds() {
        if (sceneClock != null) return sceneClock.getAsDouble();
        return (System.nanoTime() - sceneShownNanos) / 1e9;
    }

    /** Takes the producer off the canvas, so the next scene shown starts from its first round. */
    private void clearScene() {
        shownScene = null;
        if (sceneCanvas != null && document.canvases().producerOf(sceneCanvas) != null) {
            document.canvases().clear(sceneCanvas);
        }
    }

    /**
     * Paints one kind of scene. {@link #open} is asked each time a sheet with a
     * new scene is shown, and may return null for a subject it cannot show;
     * the canvas is then left out.
     */
    public interface SceneSource {
        /**
         * @param seconds time since the scene was first shown
         */
        CanvasProducer open(SpecSheet.Scene scene, DoubleSupplier seconds);

        /** Gives back whatever the source loaded. */
        default void release() {}
    }

    private static String token(String accent) {
        StringBuilder result = new StringBuilder(accent.length());
        for (char character : accent.toLowerCase(Locale.ROOT).toCharArray()) {
            result.append(character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9' ? character : '-');
        }
        String value = result.toString();
        return value.equals("hidden") ? "item" : value;
    }

    /** One seat's tile: its main item, with a second item badged on the corner. */
    private static final class SeatTile {

        private final UiElement tile;
        private final UiElement image;
        private final UiElement badge;

        private SeatTile(int index) {
            String id = ELEMENT_ID + "-seat-" + index;
            image = new UiElement(id + "-image").tag(UiTag.IMAGE).addClass("spec-sheet-seat-image");
            badge = new UiElement(id + "-badge").tag(UiTag.IMAGE).addClass("spec-sheet-seat-badge");
            tile = new UiElement(id).addClass("spec-sheet-seat").child(image).child(badge);
        }

        private void apply(SpecSheet.Seat seat) {
            image.imageSource(seat.image());
            badge.imageSource(seat.badge());
            badge.classed("spec-sheet-seat-badge-absent", seat.badge() == null || seat.badge().isBlank());
        }
    }

    /**
     * One stat row: a label and value line, plus a meter when it has one, drawn as a
     * track under the line or as a row of rating symbols within it.
     */
    private static final class StatRow {

        private final UiElement row;
        private final UiElement line;
        private final UiElement label;
        private final UiElement value;
        private final UiElement track;
        private final UiElement fill;
        private final UiElement rating;
        private final UiElement[] emptySymbols = new UiElement[RatingSymbols.SLOTS];
        private final UiElement[] litSymbols = new UiElement[RatingSymbols.SLOTS];
        private final UiElement[] litClips = new UiElement[RatingSymbols.SLOTS];

        private StatRow(int index) {
            String id = ELEMENT_ID + "-stat-" + index;
            label = new UiElement(id + "-label").addClass("spec-sheet-stat-label");
            value = new UiElement(id + "-value").addClass("spec-sheet-stat-value");
            fill = new UiElement(id + "-fill").addClass("spec-sheet-stat-fill");
            track = new UiElement(id + "-track")
                    .addClass("spec-sheet-stat-track")
                    .child(fill);
            line = new UiElement(id + "-line")
                            .addClass("spec-sheet-stat-line")
                            .child(label)
                            .child(value);
            row = new UiElement(id).addClass("spec-sheet-stat").child(line);
            rating = new UiElement(id + "-rating").addClass("spec-sheet-rating");
            for (int slot = 0; slot < RatingSymbols.SLOTS; slot++) {
                String slotId = id + "-rating-" + slot;
                emptySymbols[slot] = new UiElement(slotId + "-empty").tag(UiTag.IMAGE)
                        .addClass("spec-sheet-rating-empty");
                litSymbols[slot] = new UiElement(slotId + "-lit-image").tag(UiTag.IMAGE)
                        .addClass("spec-sheet-rating-lit-image");
                litClips[slot] = new UiElement(slotId + "-lit").addClass("spec-sheet-rating-lit")
                        .child(litSymbols[slot]);
                rating.child(new UiElement(slotId).addClass("spec-sheet-rating-slot")
                        .child(emptySymbols[slot])
                        .child(litClips[slot]));
            }
        }

        private void apply(SpecSheet.Stat stat) {
            label.text(stat.label());
            value.text(stat.value());
            boolean symbolled = stat.hasMeter() && stat.symbol() != null;
            boolean metered = stat.hasMeter() && !symbolled;
            row.classed("spec-sheet-stat-metered", metered);
            boolean attached = track.parent() == row;
            if (metered && !attached) row.child(track);
            if (!metered && attached) row.remove(track);
            if (metered) {
                fill.style(String.format(Locale.ROOT, "width: %.2f%%",
                        Math.max(0f, Math.min(1f, stat.fill())) * 100f));
            }
            // Between the label and the value, so the row stays one line high.
            boolean rated = rating.parent() == line;
            if (symbolled && !rated) line.insert(1, rating);
            if (!symbolled && rated) line.remove(rating);
            if (symbolled) {
                float[] fills = RatingSymbols.fills(stat.fill());
                for (int slot = 0; slot < RatingSymbols.SLOTS; slot++) {
                    emptySymbols[slot].imageSource(RatingSymbols.emptyImage(stat.symbol()));
                    litSymbols[slot].imageSource(RatingSymbols.litImage(stat.symbol()));
                    litClips[slot].style(RatingSymbols.litStyle(fills[slot]));
                }
            }
        }
    }
}
