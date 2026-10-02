package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.Objects;

/** Generated map and the exact authored place requested by First Contract. */
public record OpeningOperationMapResult(MapResult map, PointOfInterest place) {

    public OpeningOperationMapResult {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(place, "place");
    }
}
