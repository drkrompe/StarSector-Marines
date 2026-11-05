package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiSpotlight;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The one tutorial callout: a speaker's portrait beside live spotlight regions over real controls. A {@link Callout}
 * says which lines and buttons show; lessons and the Barracks induction and walkthrough all render through it.
 */
final class PreparationGuide {
    /** A callout's button; a null button is not shown. */
    record Button(String label, Runnable action) { }

    /**
     * What one callout shows. Null drops a line or button: {@code progressImage} and {@code progressLabel} are the
     * progress line's two forms, {@code hint} the line under the copy, {@code corner} the set-aside in the heading
     * corner, {@code back} and {@code action} the action row. {@code escape} is what Escape does, nothing when null.
     * {@code regions} are the groups of element ids that share one lit opening; {@code target} is the control the
     * callout sits beside and, when {@code markTarget}, outlines; null means the first region's first element.
     * {@code height} and {@code dock} are as on {@link Lesson}.
     */
    record Callout(String portrait, String speaker, String title, String progressImage, String progressLabel,
                   String copy, String hint, Button corner, Runnable escape, Button back, Button action,
                   String target, boolean markTarget, List<List<String>> regions, int height, String dock) {
    }

    /**
     * {@code later} is an optional set-aside for guidance that must not hold the player behind work they cannot yet
     * afford; {@code laterLabel} names it, "Not now" when null. {@code height} is the most the callout may take: it
     * shrinks to its words. {@code dock} names an element whose empty top-left corner the callout sits in, when the
     * room has a quiet spot better than any clear of the lesson's controls; null searches for one.
     */
    record Lesson(int milestone, String title, String copy, List<String> targets,
                  String actionLabel, Runnable action, int height, Runnable later, String laterLabel, String dock) {
        Lesson(int milestone, String title, String copy, List<String> targets,
               String actionLabel, Runnable action, int height, Runnable later, String laterLabel) {
            this(milestone, title, copy, targets, actionLabel, action, height, later, laterLabel, null);
        }
        Lesson(int milestone, String title, String copy, List<String> targets,
               String actionLabel, Runnable action, int height, Runnable later) {
            this(milestone, title, copy, targets, actionLabel, action, height, later, null);
        }
        Lesson(int milestone, String title, String copy, List<String> targets, String actionLabel, Runnable action,
               int height) {
            this(milestone, title, copy, targets, actionLabel, action, height, null);
        }
        Lesson(int milestone, String title, String copy, List<String> targets, String actionLabel, Runnable action) {
            this(milestone, title, copy, targets, actionLabel, action,
                    310);
        }

        Callout callout() {
            Button main = action == null ? null
                    : new Button(actionLabel == null ? LessonCopy.text("lessonContinue") : actionLabel, action);
            Button corner = later == null ? null
                    : new Button(laterLabel == null ? LessonCopy.text("lessonLater") : laterLabel, later);
            return new Callout(CompanyManagementGuide.portrait(), LessonCopy.text("lessonSpeaker"), title,
                    "graphics/ui/officers/preparation-step-" + milestone + ".svg", null, copy, null, corner, null,
                    null, main, null, false, targets.stream().map(List::of).toList(), height, dock);
        }
    }

    private final UiDocument document;
    private final MarkupInstance markup;
    private final Supplier<Callout> callout;
    private final UiSpotlight spotlight;
    private Callout current;
    private String previousTarget;
    private Rect previousViewport;
    private String previousOutline = "";
    private List<Object> fittedFor;
    private float fitted;

    /** Keep the overlay at viewport origin, outside the room's padded content box. */
    static UiDocument document(UiElement content) {
        content.style("height: 0px; flex-grow: 1;");
        return new UiDocument(new UiElement("preparation-host").style("flex-direction: column;").child(content));
    }

    static PreparationGuide install(UiDocument document, Supplier<Lesson> lesson, Runnable close) {
        return new PreparationGuide(document, lesson, close, loader());
    }

    static PreparationGuide installCallouts(UiDocument document, Supplier<Callout> callout, Runnable close) {
        return callouts(document, callout, close, loader());
    }

    static PreparationGuide callouts(UiDocument document, Supplier<Callout> callout, Runnable close, MarkupLoader loader) {
        return new PreparationGuide(loader, document, callout, close);
    }

    private static MarkupLoader loader() {
        return new MarkupLoader(path -> Global.getSettings().loadText(path),
                List.of("data/ui/components/company/preparation-guide.mlx"));
    }

    PreparationGuide(UiDocument document, Supplier<Lesson> lesson, Runnable close, MarkupLoader loader) {
        this(loader, document, () -> {
            Lesson live = lesson.get();
            return live == null ? null : live.callout();
        }, close);
    }

    private PreparationGuide(MarkupLoader loader, UiDocument document, Supplier<Callout> callout, Runnable close) {
        this.document = document;
        this.callout = callout;
        markup = loader.reloadAndBuild(new Reactor(), "preparation-guide", Map.of(
                "continueAction", (Runnable) () -> press(Callout::action),
                "backAction", (Runnable) () -> press(Callout::back),
                "laterAction", (Runnable) () -> press(Callout::corner)));
        for (var style : markup.styles()) document.addStyleSheet(style);
        // Mount as siblings: pointer-events:none on a fullscreen wrapper also
        // suppresses its dialogue children in the retained hit tester.
        document.root().child(markup.requireElement("preparation-shade"));
        document.root().child(markup.requireElement("preparation-target"));
        document.root().child(markup.requireElement("preparation-callout"));
        spotlight = new UiSpotlight(document, markup.requireElement("preparation-shade"),
                markup.requireElement("preparation-callout"), this::visible, this::regions);
        document.onCancel(() -> {
            if (!visible()) close.run();
            else if (current.escape() != null) current.escape().run();
        });
    }

    private void press(Function<Callout, Button> button) {
        Callout live = callout.get();
        if (live != null && button.apply(live) != null) button.apply(live).action().run();
    }

    Rect calloutBounds() { return visible() ? markup.requireElement("preparation-callout").box().borderBox() : Rect.EMPTY; }

    boolean visible() { return current != null; }

    void update() {
        current = callout.get();
        UiElement box = markup.requireElement("preparation-callout");
        box.classed("preparation-hidden", !visible());
        if (!visible()) {
            outline(Rect.EMPTY);
            previousTarget = null;
            spotlight.update();
            document.advance(0f);
            return;
        }
        markup.requireElement("preparation-portrait").imageSource(current.portrait());
        markup.requireElement("preparation-speaker").text(current.speaker());
        markup.requireElement("preparation-title").text(current.title());
        markup.requireElement("preparation-copy").text(current.copy());
        UiElement image = markup.requireElement("preparation-progress");
        image.classed("preparation-hidden", current.progressImage() == null);
        if (current.progressImage() != null) image.imageSource(current.progressImage());
        line("preparation-progress-label", current.progressLabel());
        line("preparation-hint", current.hint());
        button("preparation-later", current.corner());
        button("preparation-back", current.back());
        button("preparation-continue", current.action());
        // A callout with nothing to press gives the row's height to its words; set-aside sits in the heading corner.
        markup.requireElement("preparation-actions").classed("preparation-hidden",
                current.back() == null && current.action() == null);
        UiElement target = target();
        if (target != null && (!target.id().equals(previousTarget) || !document.viewport().equals(previousViewport))) {
            for (UiElement parent = target.parent(); parent != null; parent = parent.parent()) {
                if (parent.overflow() != Overflow.SCROLL) continue;
                Rect bounds = target.box().borderBox(), window = parent.box().contentBox();
                float delta = bounds.y() < window.y() ? bounds.y() - window.y()
                        : bounds.bottom() > window.bottom() ? bounds.bottom() - window.bottom() : 0;
                parent.scrollTop(Math.min(parent.box().maxScrollTop(), Math.max(0, parent.scrollTop() + delta)));
                document.layout(document.viewport().width(), document.viewport().height());
            }
            previousTarget = target.id(); previousViewport = document.viewport();
        }
        Rect view = document.viewport();
        List<Rect> holes = regions().stream().map(r -> UiSpotlight.regionBounds(document, r)).toList();
        Rect anchor = target == null ? Rect.EMPTY : UiSpotlight.visibleBounds(document, target);
        float width = Math.min(528, view.width() - 16);
        float height = fittedHeight(box, width, Math.min(current.height(), view.height() - 16));
        UiElement dock = current.dock() == null ? null : find(document.root(), current.dock());
        Rect corner = dock == null ? Rect.EMPTY : UiSpotlight.visibleBounds(document, dock);
        boolean docked = corner.width() >= width + 24 && corner.height() >= height + 16;
        Rect place = docked ? new Rect(corner.x() + 12, corner.y() + 8, width, height)
                : place(anchor, holes, view, width, height);
        UiElement navigation = find(document.root(), "marine-ops-page-nav");
        if (navigation != null && !docked) {
            float top = UiSpotlight.visibleBounds(document, navigation).bottom() + 8;
            if (top + place.height() <= view.height() - 8 && place.y() < top)
                place = new Rect(place.x(), top, place.width(), place.height());
        }
        box.style(String.format(Locale.ROOT, "left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx;",
                place.x(), place.y(), place.width() - 28, place.height() - 28));
        outline(current.markTarget() ? anchor : Rect.EMPTY);
        document.advance(0f);
        spotlight.update();
        document.advance(0f);
    }

    private void line(String id, String text) {
        UiElement element = markup.requireElement(id);
        element.classed("preparation-hidden", text == null);
        if (text != null) element.text(text);
    }

    private void button(String id, Button button) {
        line(id, button == null ? null : button.label());
    }

    /** The gold outline that tells the next real action from the rest of a wider opening. */
    private void outline(Rect bounds) {
        String style = bounds.width() <= 0 || bounds.height() <= 0
                ? "width: 0px; height: 0px; border-width: 0px;"
                : String.format(Locale.ROOT,
                        "left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx; border-width: 3px; border-color: #ffd979;",
                        bounds.x(), bounds.y(), Math.max(0, bounds.width() - 6), Math.max(0, bounds.height() - 6));
        if (style.equals(previousOutline)) return;
        markup.requireElement("preparation-target").style(style);
        previousOutline = style;
    }

    /**
     * The callout's border-box height for its words, at most {@code limit}: laid out once with the copy at its
     * natural height, so a short lesson does not cover the screen with an empty card. Copy that would not fit
     * scrolls inside the limit instead.
     */
    private float fittedHeight(UiElement box, float width, float limit) {
        // Only when the words or room change: this runs every frame, and measuring carries the callout out from under
        // the pointer, which would hover its button afresh each time.
        List<Object> asked = Arrays.asList(width, limit, current.title(), current.progressImage(), current.progressLabel(),
                current.copy(), current.hint(), label(current.corner()), label(current.back()), label(current.action()));
        if (asked.equals(fittedFor)) return fitted;
        UiElement copy = markup.requireElement("preparation-copy");
        copy.classed("preparation-copy-fill", false);
        box.style(String.format(Locale.ROOT, "left: 0px; top: 0px; width: %.1fpx; height: %.1fpx;",
                width - 28, limit - 28));
        document.advance(0f);
        UiElement actions = markup.requireElement("preparation-actions");
        UiElement hint = markup.requireElement("preparation-hint");
        UiElement last = actions.displayed() ? actions : hint.displayed() ? hint : copy;
        float needed = last.box().borderBox().bottom() - box.box().borderBox().y() + 14;
        copy.classed("preparation-copy-fill", true);
        fittedFor = asked;
        fitted = Math.min(limit, needed);
        return fitted;
    }

    private static String label(Button button) {
        return button == null ? null : button.label();
    }

    private UiElement target() {
        if (current == null) return null;
        UiElement named = current.target() == null ? null : find(document.root(), current.target());
        if (named != null) return named;
        return current.regions().stream().flatMap(List::stream).map(id -> find(document.root(), id))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private List<UiSpotlight.Region> regions() {
        var regions = new ArrayList<UiSpotlight.Region>();
        if (current != null) for (List<String> ids : current.regions()) {
            List<UiElement> elements = ids.stream().map(id -> find(document.root(), id)).filter(Objects::nonNull).toList();
            if (!elements.isEmpty()) regions.add(new UiSpotlight.Region(elements));
        }
        // The way out is always lit: travel and resupply stay reachable, and leaving never completes the work.
        UiElement exit = find(document.root(), "page-nav-return");
        if (exit != null && regions.stream().noneMatch(region -> region.elements().contains(exit)))
            regions.add(new UiSpotlight.Region(List.of(exit)));
        return regions;
    }

    static Rect place(Rect target, List<Rect> regions, Rect view, float width, float height) {
        List<Rect> candidates = new ArrayList<>(List.of(new Rect(target.right() + 12, target.y(), width, height),
                new Rect(target.x() - width - 12, target.y(), width, height),
                new Rect(target.x(), target.bottom() + 12, width, height),
                new Rect(target.x(), target.y() - height - 12, width, height),
                new Rect(8, 8, width, height), new Rect(view.width() - width - 8, 8, width, height),
                new Rect(8, view.height() - height - 8, width, height),
                new Rect(view.width() - width - 8, view.height() - height - 8, width, height)));
        for (Rect region : regions) {
            candidates.add(new Rect(region.x() - width - 12, region.y(), width, height));
            candidates.add(new Rect(region.right() + 12, region.y(), width, height));
        }
        Rect best = null;
        float leastOverlap = Float.MAX_VALUE;
        float leastRegionOverlap = Float.MAX_VALUE;
        for (Rect candidate : candidates) {
            Rect clamped = new Rect(Math.max(8, Math.min(candidate.x(), view.width() - width - 8)),
                    Math.max(8, Math.min(candidate.y(), view.height() - height - 8)), width, height);
            Rect overlap = clamped.intersect(target);
            float area = overlap.width() * overlap.height();
            float regionArea = 0;
            for (Rect region : regions) {
                Rect covered = clamped.intersect(region);
                regionArea += covered.width() * covered.height();
            }
            if (area < leastOverlap || area == leastOverlap && regionArea < leastRegionOverlap) {
                best = clamped; leastOverlap = area; leastRegionOverlap = regionArea;
            }
            if (area == 0 && regionArea == 0) break;
        }
        return best;
    }

    static UiElement find(UiElement root, String id) {
        if (id.equals(root.id())) return root;
        for (UiElement child : root.children()) {
            UiElement found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
