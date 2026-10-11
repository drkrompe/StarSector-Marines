package com.dillon.starsectormarines.ui.retained;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** A dimmed tutorial surface with live, clipped apertures over declared element subtrees.
 * Place the layer after the screen content and before the dialogue in paint order. */
public final class UiSpotlight {
    private final UiDocument document;
    private final UiElement layer;
    private final UiElement dialogue;
    private final BooleanSupplier visible;
    private final Supplier<List<Region>> regions;
    private List<Rect> previousHoles = List.of();
    private Rect previousViewport = Rect.EMPTY;
    private boolean painted;

    public UiSpotlight(UiDocument document, UiElement layer, UiElement dialogue,
                       BooleanSupplier visible, Supplier<List<Region>> regions) {
        this.document = document;
        this.layer = layer;
        this.dialogue = dialogue;
        this.visible = visible;
        this.regions = regions;
        document.inputRegions(() -> {
            if (!visible.getAsBoolean()) return null;
            var allowed = new ArrayList<>(regions.get().stream().flatMap(region -> region.elements().stream()).toList());
            allowed.add(dialogue);
            return List.copyOf(allowed);
        });
    }

    /** Call after layout and dialogue placement. Input uses the live elements immediately. */
    public void update() {
        if (!visible.getAsBoolean()) {
            if (painted) clear();
            painted = false;
            return;
        }
        List<Rect> holes = regions.get().stream().map(region -> regionBounds(document, region))
                .filter(rect -> rect.width() > 0 && rect.height() > 0).distinct().toList();
        Rect viewport = document.viewport();
        if (painted && holes.equals(previousHoles) && viewport.equals(previousViewport)) return;
        clear();
        int index = 0;
        for (Rect rect : shadeRectangles(viewport, holes)) {
            layer.child(new UiElement("spotlight-shade-" + index++).style(geometry(rect, 0)
                    + "background-color: #080e12b8; pointer-events: none;"));
        }
        index = 0;
        for (Rect rect : holes) {
            if (rect.width() < 2 || rect.height() < 2) continue;
            layer.child(new UiElement("spotlight-region-" + index++).style(geometry(rect, 2)
                    + "border-width: 1px; border-color: #dfc588; pointer-events: none;"));
        }
        painted = true;
        previousHoles = holes;
        previousViewport = viewport;
    }

    private void clear() {
        for (UiElement child : List.copyOf(layer.children())) layer.remove(child);
    }

    /** A group of related controls and readouts can share one rectangular opening. */
    public record Region(List<UiElement> elements) {
        public Region { elements = List.copyOf(elements); }
    }

    public static Rect regionBounds(UiDocument document, Region region) {
        Rect union = null;
        for (UiElement element : region.elements()) {
            Rect bounds = visibleBounds(document, element);
            if (bounds.width() <= 0 || bounds.height() <= 0) continue;
            if (union == null) union = bounds;
            else {
                float left = Math.min(union.x(), bounds.x()), top = Math.min(union.y(), bounds.y());
                union = new Rect(left, top, Math.max(union.right(), bounds.right()) - left,
                        Math.max(union.bottom(), bounds.bottom()) - top);
            }
        }
        return union == null ? Rect.EMPTY : union;
    }

    /** Uses the same clipping boundary as paint and hit testing, including scrolled ancestors. */
    public static Rect visibleBounds(UiDocument document, UiElement element) {
        Rect bounds = element.box().borderBox().intersect(document.viewport());
        for (UiElement parent = element.parent(); parent != null; parent = parent.parent()) {
            bounds = UiLayoutEngine.clipForChildren(parent.overflow(), parent.box(), bounds);
        }
        return bounds;
    }

    /** Disjoint shade tiles: overlapping apertures never double-darken the remaining screen. */
    static List<Rect> shadeRectangles(Rect viewport, List<Rect> holes) {
        List<Rect> remaining = List.of(viewport);
        for (Rect hole : holes) {
            List<Rect> next = new ArrayList<>();
            for (Rect tile : remaining) {
                Rect cut = tile.intersect(hole);
                if (cut.width() <= 0 || cut.height() <= 0) { next.add(tile); continue; }
                add(next, new Rect(tile.x(), tile.y(), tile.width(), cut.y() - tile.y()));
                add(next, new Rect(tile.x(), cut.bottom(), tile.width(), tile.bottom() - cut.bottom()));
                add(next, new Rect(tile.x(), cut.y(), cut.x() - tile.x(), cut.height()));
                add(next, new Rect(cut.right(), cut.y(), tile.right() - cut.right(), cut.height()));
            }
            remaining = next;
        }
        return List.copyOf(remaining);
    }

    private static void add(List<Rect> tiles, Rect rect) {
        if (rect.width() > 0 && rect.height() > 0) tiles.add(rect);
    }

    private String geometry(Rect rect, float border) {
        Rect origin = layer.box().contentBox();
        return String.format(Locale.ROOT,
                "position: absolute; left: %.2fpx; top: %.2fpx; width: %.2fpx; height: %.2fpx;",
                rect.x() - origin.x(), rect.y() - origin.y(), rect.width() - border, rect.height() - border);
    }
}
