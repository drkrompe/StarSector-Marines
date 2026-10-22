package com.dillon.starsectormarines.battle.fire;

/**
 * What the map offers the fire field: the fuel standing on a cell, read live
 * so a tree felled by a shell or creep that has spread is seen as it is now.
 * Called only for cells heat has reached, never swept over the whole map.
 */
public interface FireTerrain {

    /** The {@link FireFuel} mask standing on a cell, by row-major index; 0 for nothing that burns. */
    int fuelMask(int idx);

    /** A terrain with nothing to burn. */
    FireTerrain NONE = idx -> 0;
}
