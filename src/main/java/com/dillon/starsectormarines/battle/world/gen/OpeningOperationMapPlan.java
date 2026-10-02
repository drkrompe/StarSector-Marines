package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.List;

/**
 * Per-call authored-place request for one of the two First Contract operations.
 *
 * <p>The caller resolves landing and force staging on the ordinary map first.
 * Those cells are carried here so the authored facility can fit around the
 * already-resolved operation instead of moving its landing or starting forces.
 */
public record OpeningOperationMapPlan(
        PointOfInterest.Kind facilityKind,
        int marineSpawnX,
        int marineSpawnY,
        int defenderSpawnX,
        int defenderSpawnY,
        int targetAnchorX,
        int targetAnchorY,
        List<Cell> reservedCells) {

    public OpeningOperationMapPlan {
        if (facilityKind != PointOfInterest.Kind.COMMS
                && facilityKind != PointOfInterest.Kind.DEPOT) {
            throw new IllegalArgumentException(
                    "opening operation facility must be COMMS or DEPOT");
        }
        reservedCells = List.copyOf(reservedCells == null ? List.of() : reservedCells);
    }

    /** One immutable map cell reserved for the current operation staging. */
    public record Cell(int x, int y) {}
}
