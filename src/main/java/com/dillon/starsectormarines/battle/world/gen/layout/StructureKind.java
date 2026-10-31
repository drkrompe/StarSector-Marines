package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;

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
    /** A town's buildings, one authored plan each, with no yard: the blocks of a built-up street. */
    TOWN_HOUSE(BuildingStamp.CORNER_HOME),
    TOWN_COURT(BuildingStamp.COURT_HOUSE),
    TOWN_SHOP(BuildingStamp.CORNER_SHOP),
    TOWN_HALL(BuildingStamp.MARKET_HALL),
    TOWN_WORKSHOP(BuildingStamp.T_WORKSHOP),
    TOWN_DEPOT(BuildingStamp.OFFSET_DEPOT);

    public final int along, deep;
    /** The one authored plan the structure is, or {@code null} for a structure raised its own way. */
    public final BuildingStamp stamp;

    StructureKind(int along, int deep) {
        this.along = along;
        this.deep = deep;
        this.stamp = null;
    }

    StructureKind(BuildingStamp stamp) {
        this.along = stamp.footprintWidth(0);
        this.deep = stamp.footprintHeight(0);
        this.stamp = stamp;
    }

    /** Whether the structure may stand on road: only a square, which is paving itself. */
    public boolean takesRoad() {
        return this == BELL_SQUARE;
    }
}
