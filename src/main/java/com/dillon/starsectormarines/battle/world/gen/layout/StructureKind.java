package com.dillon.starsectormarines.battle.world.gen.layout;

/**
 * What a {@link Structure} raises, and the ground it takes (its footprint, a
 * yard or margin included, along x and y unturned).
 */
public enum StructureKind {
    /** A farmhouse and a barn side by side on a worked yard. */
    FARMSTEAD(StructureFit.FARM_ALONG, StructureFit.FARM_DEEP),
    /** A long store building on a yard: the barn's plan. */
    GRANARY(StructureFit.GRANARY_ALONG, StructureFit.GRANARY_DEEP),
    /** A sandbag ring round stacked crates, open on one side. */
    AMMO_DUMP(StructureFit.DUMP_SIDE, StructureFit.DUMP_SIDE),
    /** A small sandbag ring a lookout stands in. */
    OBSERVATION_POST(StructureFit.POST_SIDE, StructureFit.POST_SIDE),
    /** An open paved square with a marker in its middle, where people gather. */
    BELL_SQUARE(StructureFit.SQUARE_SIDE, StructureFit.SQUARE_SIDE),
    /**
     * An allied airbase: an {@code AirbaseLot} field (strip, hardstands, sheds, tower and fence) and the verge kept
     * round its fence. Unturned its strip runs along x on the footprint's north edge, toward a bloom to the north.
     */
    AIRFIELD(StructureFit.AIRFIELD_ALONG, StructureFit.AIRFIELD_DEEP),
    /** A long prefab bunkhouse on a yard: a field crew's quarters. */
    CREW_QUARTERS(StructureFit.QUARTERS_ALONG, StructureFit.QUARTERS_DEEP),
    /** A short prefab module on a yard: fuel and stores. */
    STORE_SHED(StructureFit.SHED_ALONG, StructureFit.SHED_DEEP),
    /** A one-room hut on a yard: a pump house or a fuel hut. */
    PUMP_HUT(StructureFit.HUT_ALONG, StructureFit.HUT_DEEP);

    public final int along, deep;

    StructureKind(int along, int deep) {
        this.along = along;
        this.deep = deep;
    }

    /**
     * Whether the structure keeps off every way, the farmland's tracks included, which the farmland paints only after
     * structures are raised: an airfield and the buildings of its hamlet ({@link AirfieldSiting}).
     */
    public boolean keepsOffWays() {
        return this == AIRFIELD || this == CREW_QUARTERS || this == STORE_SHED || this == PUMP_HUT;
    }

    /** Whether the structure may stand on road: only a square, which is paving itself. */
    public boolean takesRoad() {
        return this == BELL_SQUARE;
    }
}
