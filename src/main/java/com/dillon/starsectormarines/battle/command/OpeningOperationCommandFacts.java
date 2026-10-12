package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.ops.OpeningOperationKind;

import java.util.Objects;
import java.util.List;

/** Immutable authored place disclosed to both sides of an opening operation. */
public record OpeningOperationCommandFacts(
        OpeningOperationKind kind,
        String placeId,
        String placeName,
        int cellX,
        int cellY,
        List<Place> depotStages, boolean warehouseCaptured) {

    public OpeningOperationCommandFacts(OpeningOperationKind kind, String placeId, String placeName,
                                        int cellX, int cellY, List<Place> depotStages) {
        this(kind, placeId, placeName, cellX, cellY, depotStages, false);
    }
    public OpeningOperationCommandFacts withWarehouseCaptured(boolean captured) {
        return new OpeningOperationCommandFacts(kind, placeId, placeName, cellX, cellY, depotStages, captured);
    }
    public record Place(String name, int x, int y) {}

    public OpeningOperationCommandFacts(OpeningOperationKind kind, String placeId,
                                        String placeName, int cellX, int cellY) {
        this(kind, placeId, placeName, cellX, cellY, List.of());
    }

    public OpeningOperationCommandFacts {
        Objects.requireNonNull(kind, "kind");
        depotStages = List.copyOf(depotStages);
        Objects.requireNonNull(placeId, "placeId");
        Objects.requireNonNull(placeName, "placeName");
        if (cellX < 0 || cellY < 0) {
            throw new IllegalArgumentException(
                    "opening-operation place requires a map cell");
        }
    }
}
