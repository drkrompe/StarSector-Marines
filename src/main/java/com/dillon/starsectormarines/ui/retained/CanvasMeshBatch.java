package com.dillon.starsectormarines.ui.retained;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/** Immutable meshes and colors in paint order, prepared outside the render loop. */
public final class CanvasMeshBatch {

    private final List<Layer> layers;

    public CanvasMeshBatch(List<Layer> layers) {
        this.layers = List.copyOf(layers);
    }

    public int layerCount() {
        return layers.size();
    }

    public Layer layer(int index) {
        return layers.get(index);
    }

    public record Layer(CanvasMesh mesh, Color color) {
        public Layer {
            Objects.requireNonNull(mesh, "mesh");
            Objects.requireNonNull(color, "color");
        }
    }
}
