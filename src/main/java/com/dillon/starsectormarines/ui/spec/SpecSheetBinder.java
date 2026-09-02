package com.dillon.starsectormarines.ui.spec;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * What ties one retained element to one {@link SpecSheet} ({@code spec-sheet.md}).
 *
 * <p>A binding is made from Java, by element, where the screen builds the
 * element's row. There is no markup attribute for it: the sheet is data the
 * screen already holds, and an attribute would need a second way to name the
 * subject.
 *
 * <p>{@link #update()} reads the document's hover chain rather than asking every
 * binding whether it is hovered. Hover is an ancestor chain, so the first bound
 * element in that chain is the deepest one — a bound row inside a bound panel
 * shows the row's sheet, which is what a reader pointing at the row means.
 *
 * <p>Nothing happens while the hovered binding is unchanged, so a screen that
 * binds nothing pays one walk of a short list per frame and never touches the
 * layer.
 */
public final class SpecSheetBinder {

    private final UiDocument document;
    private final SpecSheetLayer layer;
    private final Map<UiElement, Supplier<SpecSheet>> bindings = new IdentityHashMap<>();

    private UiElement open;

    public SpecSheetBinder(UiDocument document, SpecSheetLayer layer) {
        this.document = Objects.requireNonNull(document, "document");
        this.layer = Objects.requireNonNull(layer, "layer");
    }

    /**
     * Binds one element to a sheet produced on demand. The supplier is asked
     * when the element is hovered, so a subject whose numbers move is described
     * as it is now rather than as it was when the row was built.
     */
    public SpecSheetBinder bind(UiElement target, Supplier<SpecSheet> sheet) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sheet, "sheet");
        bindings.put(target, sheet);
        return this;
    }

    public SpecSheetBinder bind(UiElement target, SpecSheet sheet) {
        Objects.requireNonNull(sheet, "sheet");
        return bind(target, () -> sheet);
    }

    public void unbind(UiElement target) {
        if (bindings.remove(target) != null && open == target) closeOpen();
    }

    /** Drops every binding and closes the overlay. Called when a document is replaced. */
    public void clear() {
        bindings.clear();
        closeOpen();
    }

    public int size() {
        return bindings.size();
    }

    /** Whether an overlay is currently open for a bound element. */
    public UiElement openTarget() {
        return open;
    }

    /**
     * Opens, closes, or leaves the overlay alone for the current hover. Cheap
     * and idempotent: call it once per resolved input pass.
     */
    public void update() {
        UiElement next = null;
        if (!bindings.isEmpty()) {
            for (UiElement element : document.hoverChain()) {
                if (bindings.containsKey(element)) {
                    next = element;
                    break;
                }
            }
        }
        if (next == open) return;
        open = next;
        if (next == null) layer.hide();
        else layer.show(bindings.get(next).get(), next);
    }

    private void closeOpen() {
        if (open == null) return;
        open = null;
        layer.hide();
    }
}
