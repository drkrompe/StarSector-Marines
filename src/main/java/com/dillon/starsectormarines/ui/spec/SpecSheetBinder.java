package com.dillon.starsectormarines.ui.spec;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What ties one retained element to one {@link SpecSheet} ({@code ui-nouns.md}).
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
 * <p>Ordinary bindings do no work while the hovered element is unchanged; live
 * bindings recheck their value and redraw only on a change. A screen that
 * binds nothing pays one walk of a short list per frame and never touches the
 * layer.
 */
public final class SpecSheetBinder {

    private final UiDocument document;
    private final SpecSheetLayer layer;
    private final boolean keyboardHints;
    private final Map<UiElement, Supplier<SpecSheet>> bindings = new IdentityHashMap<>();

    private final Set<UiElement> liveBindings = Collections.newSetFromMap(new IdentityHashMap<>());
    private UiElement open;
    private SpecSheet shown;

    public SpecSheetBinder(UiDocument document, SpecSheetLayer layer) {
        this(document, layer, false);
    }

    /** Optionally exposes compact controls' details to keyboard users as well. */
    public SpecSheetBinder(UiDocument document, SpecSheetLayer layer, boolean keyboardHints) {
        this.document = Objects.requireNonNull(document, "document");
        this.layer = Objects.requireNonNull(layer, "layer");
        this.keyboardHints = keyboardHints;
    }

    /**
     * Binds one element to a sheet produced on demand. The supplier is asked
     * when the element is hovered, so a subject whose numbers move is described
     * as it is now rather than as it was when the row was built.
     *
     * <p>A supplier may answer null, which opens nothing. That is what a fixed
     * element whose subject changes underneath it needs — the squad-deployment
     * inspector's four equipment lines are bound once and describe whichever
     * marine is hovered, and there are moments (no marine, a suit with no
     * integral system) when the line names nothing to describe.
     */
    public SpecSheetBinder bind(UiElement target, Supplier<SpecSheet> sheet) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sheet, "sheet");
        liveBindings.remove(target);
        bindings.put(target, sheet);
        return this;
    }

    /** Rechecks the hovered value each pass; redraws only when its content changes. */
    public SpecSheetBinder bindLive(UiElement target, Supplier<SpecSheet> sheet) {
        bind(target, sheet);
        liveBindings.add(target);
        return this;
    }

    public SpecSheetBinder bind(UiElement target, SpecSheet sheet) {
        Objects.requireNonNull(sheet, "sheet");
        return bind(target, () -> sheet);
    }

    public void unbind(UiElement target) {
        liveBindings.remove(target);
        if (bindings.remove(target) != null && open == target) closeOpen();
    }

    /** Drops every binding and closes the overlay. Called when a document is replaced. */
    public void clear() {
        bindings.clear();
        liveBindings.clear();
        closeOpen();
    }

    public int size() {
        return bindings.size();
    }

    /**
     * Whether this element carries a binding. Screens that rebind every frame
     * as rows arrive ask before rebinding; tests ask to state what a screen
     * made hoverable.
     */
    public boolean isBound(UiElement target) {
        return target != null && bindings.containsKey(target);
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
        UiElement focused = document.focusedElement();
        if (next == null && keyboardHints && focused != null && focused.focusVisible()) {
            for (UiElement element = focused; element != null; element = element.parent()) {
                if (bindings.containsKey(element)) {
                    next = element;
                    break;
                }
            }
        }
        if (next == open && !liveBindings.contains(next)) return;
        SpecSheet sheet = next == null ? null : bindings.get(next).get();
        if (next == open && Objects.equals(sheet, shown)) return;
        open = next;
        shown = sheet;
        if (sheet == null) layer.hide();
        else layer.show(sheet, next);
    }

    private void closeOpen() {
        if (open == null) return;
        open = null;
        shown = null;
        layer.hide();
    }
}
