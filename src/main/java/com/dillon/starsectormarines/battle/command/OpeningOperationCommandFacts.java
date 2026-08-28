package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.ops.OpeningOperationKind;

import java.util.Objects;

/** Immutable authored place disclosed to both sides of an opening operation. */
public record OpeningOperationCommandFacts(
        OpeningOperationKind kind,
        String placeId,
        String placeName,
        int cellX,
        int cellY) {

    public OpeningOperationCommandFacts {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(placeId, "placeId");
        Objects.requireNonNull(placeName, "placeName");
        if (cellX < 0 || cellY < 0) {
            throw new IllegalArgumentException(
                    "opening-operation place requires a map cell");
        }
    }
}
