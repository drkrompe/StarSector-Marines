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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** The same portrait and real-control spotlight across the preparation rooms. */
final class PreparationGuide {
    record Lesson(int milestone, String title, String copy, List<String> targets,
                  String actionLabel, Runnable action) { }

    private final UiDocument document;
    private final MarkupInstance markup;
    private final Supplier<Lesson> lesson;
    private final UiSpotlight spotlight;
    private Lesson current;
    private String previousTarget;
    private Rect previousViewport;

    /** Keep the overlay at viewport origin, outside the room's padded content box. */
    static UiDocument document(UiElement content) {
        content.style("height: 0px; flex-grow: 1;");
        return new UiDocument(new UiElement("preparation-host").style("flex-direction: column;").child(content));
    }

    static PreparationGuide install(UiDocument document, Supplier<Lesson> lesson, Runnable close) {
        MarkupLoader loader = new MarkupLoader(path -> Global.getSettings().loadText(path),
                List.of("data/ui/components/company/preparation-guide.mlx"));
        return new PreparationGuide(document, lesson, close, loader);
    }

    PreparationGuide(UiDocument document, Supplier<Lesson> lesson, Runnable close, MarkupLoader loader) {
        this.document = document;
        this.lesson = lesson;
        markup = loader.reloadAndBuild(new Reactor(), "preparation-guide", Map.of(
                "continueAction", (Runnable) () -> {
                    Lesson live = lesson.get();
                    if (live != null && live.action() != null) live.action().run();
                }));
        for (var style : markup.styles()) document.addStyleSheet(style);
        document.root().child(markup.root());
        spotlight = new UiSpotlight(document, markup.requireElement("preparation-shade"),
                markup.requireElement("preparation-callout"), this::visible, this::regions);
        document.onCancel(() -> { if (!visible()) close.run(); });
    }

    private boolean visible() { return current != null; }

    void update() {
        current = lesson.get();
        UiElement callout = markup.requireElement("preparation-callout");
        callout.classed("preparation-hidden", !visible());
        if (!visible()) { spotlight.update(); document.advance(0f); return; }
        markup.requireElement("preparation-title").text(current.title());
        markup.requireElement("preparation-copy").text(current.copy());
        markup.requireElement("preparation-progress").imageSource(
                "graphics/ui/officers/preparation-step-" + current.milestone() + ".svg");
        UiElement action = markup.requireElement("preparation-continue");
        action.text(current.actionLabel() == null ? "CONTINUE" : current.actionLabel());
        action.classed("preparation-hidden", current.action() == null);
        List<UiElement> targets = targets();
        UiElement target = targets.isEmpty() ? null : targets.get(0);
        if (target != null && (!target.id().equals(previousTarget) || !document.viewport().equals(previousViewport))) {
            for (UiElement parent = target.parent(); parent != null; parent = parent.parent()) {
                if (parent.overflow() != Overflow.SCROLL) continue;
                Rect box = target.box().borderBox(), window = parent.box().contentBox();
                float delta = box.y() < window.y() ? box.y() - window.y()
                        : box.bottom() > window.bottom() ? box.bottom() - window.bottom() : 0;
                parent.scrollTop(Math.min(parent.box().maxScrollTop(), Math.max(0, parent.scrollTop() + delta)));
                document.layout(document.viewport().width(), document.viewport().height());
            }
            previousTarget = target.id(); previousViewport = document.viewport();
        }
        Rect view = document.viewport();
        List<Rect> holes = regions().stream().map(r -> UiSpotlight.regionBounds(document, r)).toList();
        Rect anchor = target == null ? Rect.EMPTY : UiSpotlight.visibleBounds(document, target);
        float height = current.milestone() == 0 && current.copy().contains("Cost / Available") ? 392 : 280;
        Rect place = BarracksGuideCallout.place(anchor, holes, view,
                Math.min(528, view.width() - 16), Math.min(height, view.height() - 16));
        callout.style(String.format(Locale.ROOT, "left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx;",
                place.x(), place.y(), place.width() - 28, place.height() - 28));
        document.advance(0f);
        spotlight.update();
        document.advance(0f);
    }

    private List<UiElement> targets() {
        return current == null ? List.of() : current.targets().stream()
                .map(id -> find(document.root(), id)).filter(e -> e != null).toList();
    }

    private List<UiSpotlight.Region> regions() {
        var controls = new ArrayList<>(targets());
        UiElement exit = find(document.root(), "page-nav-return");
        if (exit != null && !controls.contains(exit)) controls.add(exit);
        return controls.stream().map(e -> new UiSpotlight.Region(List.of(e))).toList();
    }

    private static UiElement find(UiElement root, String id) {
        if (id.equals(root.id())) return root;
        for (UiElement child : root.children()) {
            UiElement found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
