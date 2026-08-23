package com.dillon.starsectormarines.ui.retained;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Application wiring between attached canvas elements and Java producers. */
public final class CanvasRegistry {

    private final UiDocument owner;
    private final Map<UiElement, CanvasProducer> producers = new IdentityHashMap<>();
    private long revision;

    CanvasRegistry(UiDocument owner) {
        this.owner = owner;
    }

    public void set(UiElement canvas, CanvasProducer producer) {
        requireAttachedCanvas(canvas);
        producers.put(canvas, Objects.requireNonNull(producer, "producer"));
        revision++;
    }

    public void clear(UiElement canvas) {
        requireCanvas(canvas);
        if (producers.remove(canvas) != null) revision++;
    }

    public CanvasProducer producerOf(UiElement canvas) {
        return owner.containsElement(canvas) ? producers.get(canvas) : null;
    }

    /** Signals changed producer state; current backend repaints each render. */
    public void invalidate(UiElement canvas) {
        requireAttachedCanvas(canvas);
        if (!producers.containsKey(canvas)) {
            throw new IllegalArgumentException("Canvas has no registered producer");
        }
        revision++;
    }

    public long revision() {
        return revision;
    }

    public int registeredCount() {
        return producers.size();
    }

    void prune() {
        producers.keySet().removeIf(canvas -> !owner.containsElement(canvas));
    }

    private void requireAttachedCanvas(UiElement canvas) {
        requireCanvas(canvas);
        if (!owner.containsElement(canvas)) {
            throw new IllegalArgumentException("Canvas must be attached before registration");
        }
    }

    private static void requireCanvas(UiElement element) {
        Objects.requireNonNull(element, "canvas");
        if (element.tag() != UiTag.CANVAS) {
            throw new IllegalArgumentException("Only a canvas can have a producer");
        }
    }
}
