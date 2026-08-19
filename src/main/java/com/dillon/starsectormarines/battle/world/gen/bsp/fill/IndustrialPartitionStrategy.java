package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Random;

/**
 * Frontage-aware factory plan for genuinely large industrial lots. A clear
 * two-cell service spine connects opposed entrances. One side opens onto a
 * broad loading/production floor while the other holds directly accessible
 * control and parts rooms; no room must be crossed to reach another.
 *
 * <p>Smaller lots retain the legacy binary warehouse plan. The minimum is
 * sized so the production side has four cells of depth after accounting for
 * two separator walls, a two-cell spine, and a two-cell support wing.
 */
final class IndustrialPartitionStrategy implements PartitionStrategy {

    static final IndustrialPartitionStrategy DEFAULT = new IndustrialPartitionStrategy();
    static final int MIN_LONG_DIM = 15;
    static final int MIN_SHORT_DIM = 12;

    private static final int LOADING_DEPTH = 3;
    private static final int SUPPORT_DEPTH = 2;

    private IndustrialPartitionStrategy() {}

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
        int width = br - bl + 1;
        int height = bb - bt + 1;
        if (Math.max(width, height) < MIN_LONG_DIM
                || Math.min(width, height) < MIN_SHORT_DIM) {
            return BinaryPartitionStrategy.DEFAULT.partition(
                    grid, topology, bl, bt, br, bb, rng, interiorGround, placement);
        }

        boolean vertical = placement.frontage == BuildingPlacement.Side.TOP
                || placement.frontage == BuildingPlacement.Side.BOTTOM
                || (placement.frontage == null && height >= width);
        return vertical
                ? carveVertical(grid, topology, bl, bt, br, bb, interiorGround,
                        placement.frontage, rng.nextBoolean())
                : carveHorizontal(grid, topology, bl, bt, br, bb, interiorGround,
                        placement.frontage, rng.nextBoolean());
    }

    private static PartitionLayout carveVertical(NavigationGrid grid, CellTopology topology,
                                                   int bl, int bt, int br, int bb,
                                                   GroundKind ground,
                                                   BuildingPlacement.Side frontage,
                                                   boolean productionFirst) {
        int interiorAcross = br - bl - 1;
        int productionDepth = interiorAcross - SUPPORT_DEPTH - 4;
        int productionMin;
        int productionMax;
        int supportMin;
        int supportMax;
        int firstWall;
        int secondWall;
        int spineLow;

        if (productionFirst) {
            productionMin = bl + 1;
            productionMax = productionMin + productionDepth - 1;
            firstWall = productionMax + 1;
            spineLow = firstWall + 1;
            secondWall = spineLow + 2;
            supportMin = secondWall + 1;
            supportMax = br - 1;
        } else {
            supportMin = bl + 1;
            supportMax = supportMin + SUPPORT_DEPTH - 1;
            firstWall = supportMax + 1;
            spineLow = firstWall + 1;
            secondWall = spineLow + 2;
            productionMin = secondWall + 1;
            productionMax = br - 1;
        }

        int longMin = bt + 1;
        int longMax = bb - 1;
        int productionWall = productionFirst ? firstWall : secondWall;
        int supportWall = productionFirst ? secondWall : firstWall;
        for (int y = longMin; y <= longMax; y++) {
            grid.setWalkable(firstWall, y, false);
            grid.setWalkable(secondWall, y, false);
        }

        boolean frontAtLow = frontage != BuildingPlacement.Side.BOTTOM;
        int loadingMin = frontAtLow ? longMin : longMax - LOADING_DEPTH + 1;
        int loadingMax = frontAtLow ? longMin + LOADING_DEPTH - 1 : longMax;
        int productionLongMin = frontAtLow ? loadingMax + 1 : longMin;
        int productionLongMax = frontAtLow ? longMax : loadingMin - 1;
        int splitWall = (longMin + longMax) / 2;
        for (int x = supportMin; x <= supportMax; x++) {
            grid.setWalkable(x, splitWall, false);
        }

        int controlMin = frontAtLow ? longMin : splitWall + 1;
        int controlMax = frontAtLow ? splitWall - 1 : longMax;
        int partsMin = frontAtLow ? splitWall + 1 : longMin;
        int partsMax = frontAtLow ? longMax : splitWall - 1;
        openVerticalDoor(grid, topology, productionWall,
                (loadingMin + loadingMax) / 2, ground);
        openVerticalDoor(grid, topology, productionWall,
                (productionLongMin + productionLongMax) / 2, ground);
        openVerticalDoor(grid, topology, supportWall,
                (controlMin + controlMax) / 2, ground);
        openVerticalDoor(grid, topology, supportWall,
                (partsMin + partsMax) / 2, ground);

        labelRect(grid, topology, productionMin, loadingMin, productionMax, loadingMax,
                RoomPurpose.LOADING_BAY);
        labelRect(grid, topology, productionMin, productionLongMin,
                productionMax, productionLongMax, RoomPurpose.PRODUCTION_FLOOR);
        labelRect(grid, topology, spineLow, longMin, spineLow + 1, longMax,
                RoomPurpose.INDUSTRIAL_SPINE);
        labelRect(grid, topology, supportMin, controlMin, supportMax, controlMax,
                RoomPurpose.CONTROL_ROOM);
        labelRect(grid, topology, supportMin, partsMin, supportMax, partsMax,
                RoomPurpose.PARTS_CAGE);

        return new PartitionLayout(PartitionLayout.Orient.VERTICAL,
                new int[]{firstWall, secondWall}, false, false, true, true, spineLow);
    }

    private static PartitionLayout carveHorizontal(NavigationGrid grid, CellTopology topology,
                                                     int bl, int bt, int br, int bb,
                                                     GroundKind ground,
                                                     BuildingPlacement.Side frontage,
                                                     boolean productionFirst) {
        int interiorAcross = bb - bt - 1;
        int productionDepth = interiorAcross - SUPPORT_DEPTH - 4;
        int productionMin;
        int productionMax;
        int supportMin;
        int supportMax;
        int firstWall;
        int secondWall;
        int spineLow;

        if (productionFirst) {
            productionMin = bt + 1;
            productionMax = productionMin + productionDepth - 1;
            firstWall = productionMax + 1;
            spineLow = firstWall + 1;
            secondWall = spineLow + 2;
            supportMin = secondWall + 1;
            supportMax = bb - 1;
        } else {
            supportMin = bt + 1;
            supportMax = supportMin + SUPPORT_DEPTH - 1;
            firstWall = supportMax + 1;
            spineLow = firstWall + 1;
            secondWall = spineLow + 2;
            productionMin = secondWall + 1;
            productionMax = bb - 1;
        }

        int longMin = bl + 1;
        int longMax = br - 1;
        int productionWall = productionFirst ? firstWall : secondWall;
        int supportWall = productionFirst ? secondWall : firstWall;
        for (int x = longMin; x <= longMax; x++) {
            grid.setWalkable(x, firstWall, false);
            grid.setWalkable(x, secondWall, false);
        }

        boolean frontAtLow = frontage != BuildingPlacement.Side.RIGHT;
        int loadingMin = frontAtLow ? longMin : longMax - LOADING_DEPTH + 1;
        int loadingMax = frontAtLow ? longMin + LOADING_DEPTH - 1 : longMax;
        int productionLongMin = frontAtLow ? loadingMax + 1 : longMin;
        int productionLongMax = frontAtLow ? longMax : loadingMin - 1;
        int splitWall = (longMin + longMax) / 2;
        for (int y = supportMin; y <= supportMax; y++) {
            grid.setWalkable(splitWall, y, false);
        }

        int controlMin = frontAtLow ? longMin : splitWall + 1;
        int controlMax = frontAtLow ? splitWall - 1 : longMax;
        int partsMin = frontAtLow ? splitWall + 1 : longMin;
        int partsMax = frontAtLow ? longMax : splitWall - 1;
        openHorizontalDoor(grid, topology,
                (loadingMin + loadingMax) / 2, productionWall, ground);
        openHorizontalDoor(grid, topology,
                (productionLongMin + productionLongMax) / 2, productionWall, ground);
        openHorizontalDoor(grid, topology,
                (controlMin + controlMax) / 2, supportWall, ground);
        openHorizontalDoor(grid, topology,
                (partsMin + partsMax) / 2, supportWall, ground);

        labelRect(grid, topology, loadingMin, productionMin, loadingMax, productionMax,
                RoomPurpose.LOADING_BAY);
        labelRect(grid, topology, productionLongMin, productionMin,
                productionLongMax, productionMax, RoomPurpose.PRODUCTION_FLOOR);
        labelRect(grid, topology, longMin, spineLow, longMax, spineLow + 1,
                RoomPurpose.INDUSTRIAL_SPINE);
        labelRect(grid, topology, controlMin, supportMin, controlMax, supportMax,
                RoomPurpose.CONTROL_ROOM);
        labelRect(grid, topology, partsMin, supportMin, partsMax, supportMax,
                RoomPurpose.PARTS_CAGE);

        return new PartitionLayout(PartitionLayout.Orient.HORIZONTAL,
                new int[]{firstWall, secondWall}, false, false, true, true, spineLow);
    }

    private static void openVerticalDoor(NavigationGrid grid, CellTopology topology,
                                         int x, int y, GroundKind ground) {
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, x, y, ground);
    }

    private static void openHorizontalDoor(NavigationGrid grid, CellTopology topology,
                                           int x, int y, GroundKind ground) {
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, x, y, ground);
    }

    private static void labelRect(NavigationGrid grid, CellTopology topology,
                                  int minX, int minY, int maxX, int maxY,
                                  RoomPurpose purpose) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (grid.isWalkable(x, y) && !grid.isDoorway(x, y)) {
                    topology.setRoomPurpose(x, y, purpose);
                }
            }
        }
    }
}
