package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import java.util.List;
import java.util.Locale;

/** Non-blocking portrait guidance anchored to the next real control. */
final class BarracksGuideCallout {
    private final UiDocument document;
    private final MarkupInstance markup;
    private final BarracksCommandModel command;
    private String previousTarget;
    private Rect previousViewport;
    private String previousPlacement = "";
    private String previousHighlight = "";

    private BarracksGuideCallout(UiDocument document, MarkupInstance markup, BarracksCommandModel command) {
        this.document = document;
        this.markup = markup;
        this.command = command;
    }

    static BarracksGuideCallout bind(UiDocument document, MarkupInstance markup, BarracksCommandModel command, Runnable cancel) {
        document.inputScope(null);
        document.onCancel(() -> {
            if (command.guideVisible()) command.dismissGuide();
            else cancel.run();
        });
        return new BarracksGuideCallout(document, markup, command);
    }

    /** Run after the document has its live layout, including viewport scale and scroll. */
    void update() {
        UiElement highlight = markup.requireElement("guide-target");
        if (!command.guideVisible()) {
            highlight("width: 0px; height: 0px; border-width: 0px;", highlight);
            previousTarget = null;
            document.advance(0f);
            return;
        }
        String id = command.guideTargetId();
        UiElement target = markup.requireElement(id);
        if (!id.equals(previousTarget) || !document.viewport().equals(previousViewport)) {
            reveal(target);
            previousTarget = id;
            previousViewport = document.viewport();
        }
        Rect visible = target.box().borderBox().intersect(document.viewport());
        for (UiElement parent = target.parent(); parent != null; parent = parent.parent()) {
            if (parent.overflow() != Overflow.VISIBLE) visible = visible.intersect(parent.box().contentBox());
        }
        Rect view = document.viewport();
        float width = Math.min(528, view.width() - 16);
        float height = Math.min(300, view.height() - 16);
        Rect position = place(visible, view, width, height);
        // CSS dimensions describe the content box; placement uses the complete outer box.
        String placement = geometry(position, 28);
        if (!placement.equals(previousPlacement)) {
            markup.requireElement("barracks-guide").style(placement);
            previousPlacement = placement;
        }
        highlight(visible.width() <= 0 || visible.height() <= 0 ? "width: 0px; height: 0px; border-width: 0px;"
                : geometry(visible, 6) + "border-width: 3px; border-color: #ffd979;", highlight);
        document.advance(0f);
    }

    private void highlight(String style, UiElement highlight) {
        if (style.equals(previousHighlight)) return;
        highlight.style(style);
        previousHighlight = style;
    }

    private void reveal(UiElement target) {
        for (UiElement parent = target.parent(); parent != null; parent = parent.parent()) {
            if (parent.overflow() != Overflow.SCROLL) continue;
            Rect box = target.box().borderBox();
            Rect window = parent.box().contentBox();
            float delta = box.y() < window.y() ? box.y() - window.y()
                    : box.bottom() > window.bottom() ? box.bottom() - window.bottom() : 0;
            if (delta != 0) {
                parent.scrollTop(Math.min(parent.box().maxScrollTop(), Math.max(0, parent.scrollTop() + delta)));
                document.layout(document.viewport().width(), document.viewport().height());
            }
        }
    }

    static Rect place(Rect target, Rect view, float width, float height) {
        List<Rect> candidates = List.of(new Rect(target.right() + 12, target.y(), width, height),
                new Rect(target.x() - width - 12, target.y(), width, height),
                new Rect(target.x(), target.bottom() + 12, width, height),
                new Rect(target.x(), target.y() - height - 12, width, height));
        Rect best = null;
        float leastOverlap = Float.MAX_VALUE;
        for (Rect candidate : candidates) {
            Rect clamped = new Rect(Math.max(8, Math.min(candidate.x(), view.width() - width - 8)),
                    Math.max(8, Math.min(candidate.y(), view.height() - height - 8)), width, height);
            Rect overlap = clamped.intersect(target);
            float area = overlap.width() * overlap.height();
            if (area < leastOverlap) { best = clamped; leastOverlap = area; }
            if (area == 0) break;
        }
        return best;
    }

    private static String geometry(Rect r, float inset) {
        return String.format(Locale.ROOT, "left: %.1fpx; top: %.1fpx; width: %.1fpx; height: %.1fpx;", r.x(), r.y(), Math.max(0, r.width() - inset), Math.max(0, r.height() - inset));
    }
}
