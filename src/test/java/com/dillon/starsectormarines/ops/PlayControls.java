package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The controls on screen, found by the id they carry in their markup, so a {@code playBattle} script can press one
 * without knowing where it is. Read off the documents that took input last frame.
 */
final class PlayControls {

    private record Live(UiDocument document, UiViewport viewport) { }

    private final List<Live> filling = new ArrayList<>();
    private List<Live> live = List.of();

    PlayControls() {
        StarsectorUiInputAdapter.watch((document, viewport) -> filling.add(new Live(document, viewport)));
    }

    /** Called once a frame, after the screens have taken their input. */
    void frameDone() {
        live = List.copyOf(filling);
        filling.clear();
    }

    /** Window coordinates of the middle of the control, or null while it is absent, hidden or disabled. */
    int[] find(String id) {
        for (Live each : live) {
            UiElement control = find(each.document().root(), id);
            if (control == null) continue;
            var box = control.box().borderBox();
            float x = box.x() + box.width() / 2f;
            float y = box.y() + box.height() / 2f;
            // Under a modal, or scrolled away, it could not be pressed by hand either.
            UiElement hit = each.document().elementAt(x, y);
            while (hit != null && hit != control) hit = hit.parent();
            if (hit == null) continue;
            return new int[]{Math.round(each.viewport().screenXFor(x)), Math.round(each.viewport().screenTopFor(y))};
        }
        return null;
    }

    private static UiElement find(UiElement element, String id) {
        if (!element.displayed()) return null;
        if (id.equals(element.id()) && element.clickable()) return element;
        for (UiElement child : element.children()) {
            UiElement found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }

    /** The first of these controls that carries the class, or null with none on screen. */
    String firstWithClass(String className, String... ids) {
        for (Live each : live) {
            for (String id : ids) {
                UiElement control = find(each.document().root(), id);
                if (control != null && control.hasClass(className)) return id;
            }
        }
        return null;
    }

    /** Every control that could be pressed now, for writing a script. */
    String list() {
        TreeSet<String> ids = new TreeSet<>();
        for (Live each : live) collect(each.document().root(), ids);
        ids.removeIf(id -> find(id) == null);
        return String.join(" ", ids);
    }

    /** Every control a player could not fully reach on this frame: off the page, or cut off by a box that cannot scroll. */
    String audit() {
        TreeSet<String> found = new TreeSet<>();
        for (Live each : live) {
            var page = each.document().root().box().borderBox();
            audit(each.document().root(), page.x(), page.y(), page.right(), page.bottom(), found);
        }
        return found.isEmpty() ? "all controls reachable" : String.join("\n  ", found);
    }

    private static void audit(UiElement element, float left, float top, float right, float bottom, TreeSet<String> found) {
        if (!element.displayed()) return;
        var box = element.box().borderBox();
        if ((element.clickable() || element.disabled()) && box.width() > 0f && box.height() > 0f) {
            float over = Math.max(Math.max(left - box.x(), top - box.y()), Math.max(box.right() - right, box.bottom() - bottom));
            if (over > 1f) {
                found.add((element.id() == null ? element.classes() : element.id()) + " cut off by " + Math.round(over) + "px");
            }
        }
        // What a scrolling box hides is one wheel away.
        if (element.overflow() == Overflow.SCROLL) return;
        for (UiElement child : element.children()) {
            if (element.overflow() == Overflow.VISIBLE) {
                audit(child, left, top, right, bottom, found);
            } else {
                audit(child, Math.max(left, box.x()), Math.max(top, box.y()), Math.min(right, box.right()), Math.min(bottom, box.bottom()), found);
            }
        }
    }

    private static void collect(UiElement element, TreeSet<String> ids) {
        if (!element.displayed()) return;
        if (element.id() != null && element.clickable()) ids.add(element.id());
        for (UiElement child : element.children()) collect(child, ids);
    }
}
