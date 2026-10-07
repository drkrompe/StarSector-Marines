package com.dillon.starsectormarines.ui.retained;

/** Immutable triangle geometry in a canvas producer's local coordinates. */
public final class CanvasMesh {

    private final float[] coordinates;

    public CanvasMesh(float[] coordinates) {
        if (coordinates == null || coordinates.length % 6 != 0) {
            throw new IllegalArgumentException("mesh requires three XY vertices per triangle");
        }
        this.coordinates = coordinates.clone();
        for (float coordinate : this.coordinates) {
            if (!Float.isFinite(coordinate)) {
                throw new IllegalArgumentException("mesh coordinates must be finite");
            }
        }
    }

    public int vertexCount() {
        return coordinates.length / 2;
    }

    public float x(int vertex) {
        return coordinates[vertex * 2];
    }

    public float y(int vertex) {
        return coordinates[vertex * 2 + 1];
    }
}
