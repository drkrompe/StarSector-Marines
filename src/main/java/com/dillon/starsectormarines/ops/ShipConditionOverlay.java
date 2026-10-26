package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.refit.RefitText;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;

import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.ShipViewCanvas;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
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
    private final UiElement stage, canvas, card, power, systems, tree, strip;
    private final UiElement curtain, frame, caption, captionTitle, captionEffect, captionHint;
    private RefitReveal reveal;
    private ShipViewCanvas revealCanvas;
    private boolean flown, returning;
    private ShipViewCanvas.View returnView;
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
        systems = markup.requireElement("ship-systems");
        tree = markup.requireElement("systems-tree");
        strip = markup.requireElement("systems-strip");
        markup.requireElement("survey-close").onClick(this::dismiss);
        markup.requireElement("systems-back").onClick(this::dismiss);
        curtain = markup.requireElement("reveal-curtain");
        frame = markup.requireElement("reveal-frame");
        caption = markup.requireElement("reveal-caption");
        captionTitle = markup.requireElement("reveal-title");
        captionEffect = markup.requireElement("reveal-effect");
        captionHint = markup.requireElement("reveal-hint");
        curtain.onPointerDown(event -> skipReveal());
        model.stageWith((order, commit) -> {
            reveal = new RefitReveal(order, commit);
            revealCanvas = null;
            flown = false;
            returning = false;
            returnView = null;
        });
        card.onPointerDown(event -> {
            if (event.button() == PointerButton.PRIMARY) pinned = true;
        });
        canvas.onPointerMove(event -> { pointerX = event.x(); pointerY = event.y(); });
    }

    public boolean dismiss() {
        if (reveal != null) { skipReveal(); return true; }
        if (model.systemsOpen()) {
            model.closeSystems();
            markup.flush();
            active = NONE;
            suppressed = NONE;
            pinned = false;
            style(card, HIDDEN);
            style(systems, HIDDEN);
            return true;
        }
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
            style(systems, HIDDEN);
            hideReveal();
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
        boolean inTree = model.systemsOpen() || reveal != null;
        for (var room : ship.rooms().compartments()) {
            UiElement marker = markup.requireElement("survey-room-" + room.id());
            Rect anchor = deck.markerBounds(room, metrics);
            anchors.put(room.id(), deck.roomBounds(room, metrics));
            if (marker.hasClass("healthy")) {
                style(marker, HIDDEN);
                continue;
            }
            markers.put(marker, room.id());
            marker.onClick(() -> select(room.id()));
            if (!inTree && bounds.contains(anchor.x(), anchor.y()) && anchor.right() <= bounds.right() && anchor.bottom() <= bounds.bottom()) {
                style(marker, position(anchor.x() - bounds.x(), anchor.y() - bounds.y())
                        + String.format(Locale.ROOT, "width: %.1fpx; height: %.1fpx; ", anchor.width(), anchor.height())
                        + "opacity: 1; pointer-events: auto; transition: none; background-color: transparent; background-image: none;");
            } else style(marker, HIDDEN);
        }
        power.onClick(() -> select(-1));
        anchors.put(-1, power.box().borderBox());
        if (reveal != null && playReveal(dt, ship, deck, bounds, metrics)) return;
        hideReveal();
        if (model.systemsOpen()) { showTree(bounds); deck.select(null); return; }
        style(systems, HIDDEN);
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
        fitCard();
        float height = Math.min(columnHeight(card), bounds.height() - 16f);
        Rect placement = place(bounds, anchor, width, height);
        style(card, position(placement.x() - bounds.x(), placement.y() - bounds.y())
                + String.format(Locale.ROOT, "width: %.1fpx; height: %.1fpx; opacity: 1; pointer-events: auto;",
                Math.max(0f, width - 36f), Math.max(0f, height - 36f)));
        markup.requireElement("survey-card-hint").text(pinned ? Strings.get("openingShipInspectionPinned") : Strings.get("openingShipInspectionPinHint"));
        document.advance(0f);
    }

    /**
     * The systems tree covers the deck; the inspection card docks down its right edge and reads the
     * track the tree has selected.
     */
    private void showTree(Rect bounds) {
        active = -1;
        pinned = true;
        float width = Math.min(420f, bounds.width() * 0.4f);
        float inset = 18f;
        style(systems, position(inset, inset) + String.format(Locale.ROOT,
                "width: %.1fpx; height: %.1fpx; opacity: 1; pointer-events: auto; transition: none;",
                bounds.width() - 2f * inset - 4f, bounds.height() - 2f * inset - 4f));
        style(tree, String.format(Locale.ROOT, "padding: 12px %.1fpx 12px 16px;", width + 24f));
        if (measuredWidth != width) {
            style(card, "width: " + Math.max(0f, width - 36f) + "px; height: auto; " + HIDDEN);
            document.advance(0f);
            measuredWidth = width;
        }
        fitCard();
        float height = Math.min(columnHeight(card), bounds.height() - 2f * inset - 20f);
        style(card, position(bounds.width() - width - inset - 10f, inset + 10f)
                + String.format(Locale.ROOT, "width: %.1fpx; height: %.1fpx; opacity: 1; pointer-events: auto;",
                Math.max(0f, width - 36f), Math.max(0f, height - 36f)));
        markup.requireElement("survey-card-hint").text(Strings.get("openingRefitSystemsTitle"));
        document.advance(0f);
    }

    /** True while an authorized refit is being played out on the deck. */
    public boolean revealing() { return reveal != null; }

    private void skipReveal() { if (reveal != null) reveal.skip(); }

    /** Plays one frame of the reveal; false once it has finished and the ordinary view takes over. */
    private boolean playReveal(float dt, CompanyDeck ship, ShipViewCanvas deck, Rect bounds, CanvasMetrics metrics) {
        style(systems, HIDDEN);
        style(card, HIDDEN);
        deck.advance(dt);
        reveal.advance(dt, reveal.wave ? !ship.dressingWave() : ship.ready());
        RefitReveal.Phase phase = reveal.phase();
        if (phase == RefitReveal.Phase.DONE) {
            reveal = null;
            hideReveal();
            return false;
        }
        Compartment room = RefitReveal.room(reveal.order.track, ship.rooms());
        if (!flown) { returnView = deck.view(); deck.flyTo(room, RefitReveal.FLY); flown = true; }
        if (phase == RefitReveal.Phase.RETURN && !returning) {
            if (returnView != null) deck.flyTo(returnView, RefitReveal.RETURN);
            returning = true;
        }
        // The deck may have been laid out again under the curtain, so frame the room on the one drawn now;
        // a wave keeps the deck but the screen may hand over a fresh canvas once the order is paid.
        boolean settled = reveal.wave ? reveal.paid() && ship.ready() : phase.ordinal() >= RefitReveal.Phase.OPEN.ordinal();
        if (settled && phase != RefitReveal.Phase.RETURN && revealCanvas != deck) {
            if (room != null) deck.focus(room); else deck.flyTo((Compartment) null, 0.01f);
            revealCanvas = deck;
        }
        style(curtain, position(0f, 0f) + String.format(Locale.ROOT,
                "width: %.1fpx; height: %.1fpx; opacity: %.3f; pointer-events: auto; transition: none;",
                bounds.width(), bounds.height(), reveal.curtain()));
        float shown = phase == RefitReveal.Phase.WORK ? Math.min(1f, reveal.time() / 0.3f) : reveal.caption();
        if (phase == RefitReveal.Phase.WORK) {
            captionTitle.text(Strings.get("openingRefitRevealWorking"));
            captionEffect.text(Strings.format("openingRefitRevealWorkingOn", RefitText.track(reveal.order.track),
                    RefitText.tier(reveal.order.track, reveal.order.band)));
            captionHint.text("");
        } else {
            captionTitle.text(Strings.upper(RefitText.tier(reveal.order.track, reveal.order.band)));
            captionEffect.text(RefitText.effect(reveal.order.track, reveal.order.band));
            captionHint.text(Strings.get("openingRefitRevealContinue"));
        }
        float captionWidth = Math.min(640f, bounds.width() - 32f);
        style(caption, position((bounds.width() - captionWidth) / 2f, bounds.height() - 112f - 28f)
                + String.format(Locale.ROOT, "width: %.1fpx; opacity: %.3f; pointer-events: none; transition: none;",
                captionWidth - 44f, shown));
        Rect box = room == null ? Rect.EMPTY : deck.roomBounds(room, metrics);
        boolean framed = room != null && box.width() > 0f && reveal.refusal() == null
                && (phase == RefitReveal.Phase.OPEN || phase == RefitReveal.Phase.SHOW
                    || reveal.wave && phase == RefitReveal.Phase.WORK);
        if (phase == RefitReveal.Phase.RETURN) framed = false;
        float pulse = 0.6f + 0.4f * (float) Math.sin(reveal.time() * 5f);
        style(frame, framed ? position(box.x() - bounds.x() - 4f, box.y() - bounds.y() - 4f)
                + String.format(Locale.ROOT, "width: %.1fpx; height: %.1fpx; opacity: %.3f; pointer-events: none; transition: none;",
                box.width() + 2f, box.height() + 2f, (phase == RefitReveal.Phase.WORK ? shown : reveal.caption()) * pulse) : HIDDEN);
        document.advance(0f);
        return true;
    }

    private void hideReveal() {
        style(curtain, HIDDEN);
        style(frame, HIDDEN);
        style(caption, HIDDEN);
    }

    /** The retained subset measures wrapped leaves, not auto-sized container trees, so fit columns from them. */
    private void fitCard() {
        fitColumn(markup.requireElement("survey-needs-list"));
        document.advance(0f);
        fitColumn(markup.requireElement("survey-needs"));
        document.advance(0f);
        fitColumn(markup.requireElement("ship-condition-detail"));
        fitColumn(markup.requireElement("workshop-restoration"));
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
            if (reveal != null) return true;
            if (element == card || element == power || element == strip || markers.containsKey(element)
                    || element == systems && model.systemsOpen()) return true;
            element = element.parent();
        }
        return false;
    }
}
