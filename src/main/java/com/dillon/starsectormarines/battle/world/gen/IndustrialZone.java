package com.dillon.starsectormarines.battle.world.gen;

import java.util.List;

/**
 * The industrial zone district ({@code set-piece-districts.md}): one working service street with walled
 * yards, warehouses and a factory row gated onto it. The street is its centre line in world cells, from one
 * end of the claim to the other, and {@code width} its carriageway. {@code bays} are the loading bays of the
 * freight yards it hosts, {@code lanes} the service lane of its factory row (empty where it hosts none), and
 * {@code docks} each warehouse's loading door onto the street with the street cell straight out from it,
 * where a hauler backs up to be loaded.
 */
public record IndustrialZone(float startX, float startY, float endX, float endY, int width,
                             List<LoadingBay> bays, List<ServiceLane> lanes, List<Dock> docks) {

    public IndustrialZone {
        bays = List.copyOf(bays);
        lanes = List.copyOf(lanes);
        docks = List.copyOf(docks);
    }

    /** A warehouse's loading door and the street cell straight out from it, past the dock apron. */
    public record Dock(int doorX, int doorY, int streetX, int streetY) {
    }
}
