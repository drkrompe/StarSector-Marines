package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.Random;

/**
 * Large-store floor plan: a broad sales floor plus a two- or three-cell-deep
 * stockroom across the rear. The activation threshold leaves enough space
 * for two-cell infantry lanes around real (non-walkable) shelf footprints:
 * a 0.3-cell-radius marine fits single-file in one cell, while two cells let
 * a pair maneuver without constant separation pressure.
 *
 * <p>Smaller shops retain the established optional binary partition and
 * non-blocking furniture recipe. This keeps neighborhood storefronts compact
 * while reserving the tactical aisle treatment for lots that can support it.
 */
final class CommercialPartitionStrategy implements PartitionStrategy {

    static final CommercialPartitionStrategy DEFAULT = new CommercialPartitionStrategy();

    static final int MIN_LONG_DIM = 10;
    static final int MIN_SHORT_DIM = 9;
    private static final int FULL_AISLE_LONG_DIM = 13;
    private static final int FULL_AISLE_SHORT_DIM = 10;

    private CommercialPartitionStrategy() {}

    @Override
    public PartitionLayout partition(NavigationGrid grid, CellTopology topology,
                                     int bl, int bt, int br, int bb,
                                     Random rng, GroundKind interiorGround) {
        return partition(grid, topology, bl, bt, br, bb, rng, interiorGround,
                BuildingPlacement.DEFAULT);
    }

    @Override
    public PartitionLayout partition(NavigationGrid grid, CellTopology topology,
                                     int bl, int bt, int br, int bb,
                                     Random rng, GroundKind interiorGround,
                                     BuildingPlacement placement) {
        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean vertical = w >= MIN_LONG_DIM && h >= MIN_SHORT_DIM;
        boolean horizontal = h >= MIN_LONG_DIM && w >= MIN_SHORT_DIM;
        if (!vertical && !horizontal) {
            return BinaryPartitionStrategy.DEFAULT.partition(
                    grid, topology, bl, bt, br, bb, rng, interiorGround);
        }
        if (vertical && horizontal) {
            if (placement.frontage == BuildingPlacement.Side.LEFT
                    || placement.frontage == BuildingPlacement.Side.RIGHT) {
                vertical = true;
            } else if (placement.frontage == BuildingPlacement.Side.TOP
                    || placement.frontage == BuildingPlacement.Side.BOTTOM) {
                vertical = false;
            } else if (w == h) {
                vertical = rng.nextBoolean();
            } else {
                vertical = w > h;
            }
        }

        boolean stockAtLowEnd;
        if (vertical && placement.frontage == BuildingPlacement.Side.LEFT) {
            stockAtLowEnd = false;
        } else if (vertical && placement.frontage == BuildingPlacement.Side.RIGHT) {
            stockAtLowEnd = true;
        } else if (!vertical && placement.frontage == BuildingPlacement.Side.TOP) {
            stockAtLowEnd = false;
        } else if (!vertical && placement.frontage == BuildingPlacement.Side.BOTTOM) {
            stockAtLowEnd = true;
        } else {
            stockAtLowEnd = rng.nextBoolean();
        }
        int stockroomDepth = Math.max(w, h) >= FULL_AISLE_LONG_DIM
                && Math.min(w, h) >= FULL_AISLE_SHORT_DIM ? 3 : 2;
        if (vertical) {
            int wallX = stockAtLowEnd
                    ? bl + stockroomDepth + 1
                    : br - stockroomDepth - 1;
            for (int y = bt + 1; y <= bb - 1; y++) grid.setWalkable(wallX, y, false);
            int doorY = centeredDoor(bt + 1, bb - 1, rng);
            BinaryPartitionStrategy.openInteriorDoorway(grid, topology, wallX, doorY, interiorGround);
            return tactical(PartitionLayout.Orient.VERTICAL, wallX);
        }

        int wallY = stockAtLowEnd
                ? bt + stockroomDepth + 1
                : bb - stockroomDepth - 1;
        for (int x = bl + 1; x <= br - 1; x++) grid.setWalkable(x, wallY, false);
        int doorX = centeredDoor(bl + 1, br - 1, rng);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, doorX, wallY, interiorGround);
        return tactical(PartitionLayout.Orient.HORIZONTAL, wallY);
    }

    private static PartitionLayout tactical(PartitionLayout.Orient orient, int axis) {
        return new PartitionLayout(orient, new int[]{axis}, true, true);
    }

    /** Center-biased but seed-variable, leaving at least two clear cells at either end. */
    private static int centeredDoor(int min, int max, Random rng) {
        int center = (min + max) / 2;
        int jitter = rng.nextInt(3) - 1;
        return Math.max(min + 2, Math.min(max - 2, center + jitter));
    }
}
