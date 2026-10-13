package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;

import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.ShipViewCanvas;
import com.dillon.starsectormarines.ui.retained.PointerButton;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Retained controls anchored to the deck camera; campaign orders stay in the survey models. */
public final class ShipConditionOverlay {
    private static final int NONE = Integer.MIN_VALUE;
    private static final String HIDDEN = "opacity: 0; pointer-events: none; transition: none;";
    private final UiDocument document;
    private final MarkupInstance markup;
    private final ShipConditionModel model;
    private final UiElement stage, canvas, card, power;
    private final Map<UiElement, String> styles = new IdentityHashMap<>();
    private final Map<UiElement, Integer> markers = new IdentityHashMap<>();
    private final Map<Integer, Rect> anchors = new HashMap<>();
    private int active = NONE;
    private int suppressed = NONE;
    private boolean pinned;
    private float leaveTime, pointerX, pointerY;
    private float measuredWidth;

    public ShipConditionOverlay(UiDocument document, MarkupInstance markup, ShipConditionModel model) {
        this.document = document;
        this.markup = markup;
        this.model = model;
        stage = markup.requireElement("ship-view-stage");
        canvas = markup.requireElement("ship-view-deck");
        card = markup.requireElement("ship-condition");
        power = markup.requireElement("survey-power");
        markup.requireElement("survey-close").onClick(this::dismiss);
        card.onPointerDown(event -> {
            if (event.button() == PointerButton.PRIMARY) pinned = true;
        });
        canvas.onPointerMove(event -> { pointerX = event.x(); pointerY = event.y(); });
    }

    public boolean dismiss() {
        if (active == NONE) return false;
        suppressed = active;
        active = NONE;
        pinned = false;
        document.requestFocus(null, false);
        style(card, HIDDEN);
        return true;
    }

    public void select(int id) { show(id, true); }

    private void show(int id, boolean pin) {
        if (active != id) {
            model.select(id);
            markup.flush();
            card.scrollTop(0f);
        }
        active = id;
        pinned = pin;
        leaveTime = 0f;
    }

    /** Call after camera movement and binding flush, before painting or dispatching input. */
    public void update(float dt, CompanyDeck ship, ShipViewCanvas deck) {
        if (ship == null || !ship.ready() || deck.camera() == null) {
            style(card, HIDDEN);
            return;
        }
        document.advance(0f);
        Rect bounds = stage.box().contentBox();
        var metrics = document.canvasMetrics(canvas, 1f);
        canvas.onPointerDown(event -> {
            if (event.button() != PointerButton.PRIMARY) return;
            var room = deck.roomAt(deck.toCameraX(metrics.toCanvasX(event.x())),
                    deck.toCameraY(metrics.toCanvasY(event.y())));
            if (room == null) dismiss(); else select(room.id());
        });
        markers.clear();
        anchors.clear();
        for (var room : ship.rooms().compartments()) {
            UiElement marker = markup.requireElement("survey-room-" + room.id());
            Rect anchor = deck.markerBounds(room, metrics);
            anchors.put(room.id(), deck.roomBounds(room, metrics));
            markers.put(marker, room.id());
            marker.onClick(() -> select(room.id()));
            if (bounds.contains(anchor.x(), anchor.y()) && anchor.right() <= bounds.right() && anchor.bottom() <= bounds.bottom()) {
                style(marker, position(anchor.x() - bounds.x(), anchor.y() - bounds.y())
                        + String.format(Locale.ROOT, "width: %.1fpx; height: %.1fpx; ", anchor.width(), anchor.height())
                        + "opacity: 1; pointer-events: auto; transition: none; background-color: transparent; background-image: none;");
            } else style(marker, HIDDEN);
        }
        power.onClick(() -> select(-1));
        anchors.put(-1, power.box().borderBox());
        int hovered = NONE;
        for (UiElement element : document.hoverChain()) {
            if (element == card) { hovered = active; break; }
            if (element == power) { hovered = -1; break; }
            if (markers.containsKey(element)) { hovered = markers.get(element); break; }
            if (element == canvas) {
                var room = deck.roomAt(deck.toCameraX(metrics.toCanvasX(pointerX)),
                        deck.toCameraY(metrics.toCanvasY(pointerY)));
                if (room != null) hovered = room.id();
                break;
            }
        }
        UiElement focused = document.focusedElement();
        if (focused != null && focused.focusVisible()) {
            if (markers.containsKey(focused)) hovered = markers.get(focused);
            else if (focused == power) hovered = -1;
        }
        if (hovered != suppressed) suppressed = NONE;
        if (hovered == suppressed) hovered = NONE;
        if (!pinned) {
            if (hovered != NONE) show(hovered, false);
            else if ((leaveTime += dt) > 0.22f) dismiss();
        }
        var selected = ship.rooms().compartments().stream().filter(r -> r.id() == active).findFirst().orElse(null);
        deck.select(selected);
        if (active == NONE) { style(card, HIDDEN); return; }
        Rect anchor = anchors.get(active);
        if (anchor == null) { dismiss(); return; }
        // Keep a selected card reachable when its room is panned outside the viewport.
        float width = Math.min(404f, bounds.width() - 16f);
        if (measuredWidth != width) {
            style(card, "width: " + Math.max(0f, width - 36f) + "px; height: auto; " + HIDDEN);
            document.advance(0f);
            measuredWidth = width;
        }
        // The retained subset measures wrapped leaves, not auto-sized container trees.
        // Fit the three content columns from those same measured leaf boxes.
        fitColumn(markup.requireElement("workshop-reclaim"));
        document.advance(0f);
        fitColumn(markup.requireElement("ship-condition-detail"));
        fitColumn(markup.requireElement("workshop-restoration"));
        document.advance(0f);
        float naturalHeight = columnHeight(card);
        float height = Math.min(naturalHeight, bounds.height() - 16f);
        Rect placement = place(bounds, anchor, width, height);
        style(card, position(placement.x() - bounds.x(), placement.y() - bounds.y())
                + String.format(Locale.ROOT, "width: %.1fpx; height: %.1fpx; opacity: 1; pointer-events: auto;",
                Math.max(0f, width - 36f), Math.max(0f, height - 36f)));
        markup.requireElement("survey-card-hint").text(pinned ? Strings.get("openingShipInspectionPinned") : Strings.get("openingShipInspectionPinHint"));
        document.advance(0f);
    }

    /** The same clamped box is used by retained painting and hit testing. */
    static Rect place(Rect bounds, Rect anchor, float width, float height) {
        float x = anchor.right() + 8f;
        if (x + width > bounds.right() - 8f) x = anchor.x() - width - 8f;
        x = Math.max(bounds.x() + 8f, Math.min(x, bounds.right() - width - 8f));
        float y = Math.max(bounds.y() + 8f, Math.min(anchor.y(), bounds.bottom() - height - 8f));
        return new Rect(x, y, width, height);
    }

    private static String position(float x, float y) {
        return String.format(Locale.ROOT, "left: %.1fpx; top: %.1fpx; ", x, y);
    }

    private void fitColumn(UiElement element) {
        if (element.opacity() == 0f) { style(element, ""); return; }
        style(element, String.format(Locale.ROOT, "height: %.1fpx;", columnHeight(element)));
    }

    private static float columnHeight(UiElement element) {
        float result = element.padding().vertical() + 2f * element.borderWidth();
        for (UiElement child : element.children()) result += child.box().borderBox().height();
        return result + Math.max(0, element.children().size() - 1) * element.gap();
    }

    private void style(UiElement element, String declaration) {
        if (!declaration.equals(styles.put(element, declaration))) element.style(declaration);
    }

    /** Chrome owns its pointer; a card's wheel must never zoom the room behind it. */
    public boolean blocksCamera(float x, float y) {
        UiElement element = document.elementAt(x, y);
        while (element != null) {
            if (element == card || element == power || markers.containsKey(element)) return true;
            element = element.parent();
        }
        return false;
    }
}
