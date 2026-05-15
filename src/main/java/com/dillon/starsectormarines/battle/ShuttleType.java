package com.dillon.starsectormarines.battle;

/**
 * Static config for each shuttle variant — sprite path, capacity, flight feel.
 * Phase 1 ships only {@link #BASIC_SHUTTLE}; Valkyrie and any heavier dropships
 * land in Phase 2 once fleet-driven drop counts come online.
 */
public enum ShuttleType {

    BASIC_SHUTTLE(
            "graphics/ships/aeroshuttle/aeroshuttle_base.png",
            4,    // marines per drop
            3.0f, // sprite length in cells
            10f,  // flight speed (cells/sec)
            0.6f  // sim-seconds between deboards
    );

    public final String spritePath;
    public final int capacity;
    public final float visualLengthCells;
    public final float flightSpeed;
    public final float deboardInterval;

    ShuttleType(String spritePath, int capacity, float visualLengthCells,
                float flightSpeed, float deboardInterval) {
        this.spritePath = spritePath;
        this.capacity = capacity;
        this.visualLengthCells = visualLengthCells;
        this.flightSpeed = flightSpeed;
        this.deboardInterval = deboardInterval;
    }
}
