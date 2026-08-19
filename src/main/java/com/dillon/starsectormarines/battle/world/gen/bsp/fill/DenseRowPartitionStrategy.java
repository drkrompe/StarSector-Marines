package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Random;

/**
 * Guaranteed two-room plan for the narrow row buildings inside a tactical
 * dense block. The partition crosses the building's short dimension, leaving
 * two useful rooms along its long axis. The public room is seed-variable and
 * supplies the preferred coordinate for both alley and opposed perimeter
 * doors; the private/support room remains reachable through a centered
 * interior doorway.
 */
final class DenseRowPartitionStrategy implements PartitionStrategy {

    static final DenseRowPartitionStrategy TENEMENT = new DenseRowPartitionStrategy(
            RoomPurpose.APARTMENT_LIVING, RoomPurpose.BEDROOM);
    static final DenseRowPartitionStrategy MARKET = new DenseRowPartitionStrategy(
            RoomPurpose.SHOP_FLOOR, RoomPurpose.STOCKROOM);

    private final RoomPurpose publicPurpose;
    private final RoomPurpose privatePurpose;

    private DenseRowPartitionStrategy(RoomPurpose publicPurpose,
                                      RoomPurpose privatePurpose) {
        this.publicPurpose = publicPurpose;
        this.privatePurpose = privatePurpose;
    }

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
        boolean longAlongY = (bb - bt) >= (br - bl);
        boolean publicAtLow = rng.nextBoolean();
        if (longAlongY) {
            int wallY = (bt + bb) / 2;
            for (int x = bl + 1; x <= br - 1; x++) grid.setWalkable(x, wallY, false);
            BinaryPartitionStrategy.openInteriorDoorway(
                    grid, topology, (bl + br) / 2, wallY, interiorGround);
            labelHorizontalRooms(grid, topology, bl, bt, br, bb, wallY, publicAtLow);
            int preferred = publicAtLow
                    ? (bt + 1 + wallY - 1) / 2
                    : (wallY + 1 + bb - 1) / 2;
            return layout(PartitionLayout.Orient.HORIZONTAL, wallY, preferred);
        }

        int wallX = (bl + br) / 2;
        for (int y = bt + 1; y <= bb - 1; y++) grid.setWalkable(wallX, y, false);
        BinaryPartitionStrategy.openInteriorDoorway(
                grid, topology, wallX, (bt + bb) / 2, interiorGround);
        labelVerticalRooms(grid, topology, bl, bt, br, bb, wallX, publicAtLow);
        int preferred = publicAtLow
                ? (bl + 1 + wallX - 1) / 2
                : (wallX + 1 + br - 1) / 2;
        return layout(PartitionLayout.Orient.VERTICAL, wallX, preferred);
    }

    private static PartitionLayout layout(PartitionLayout.Orient orient,
                                          int wall, int preferred) {
        return new PartitionLayout(orient, new int[]{wall},
                false, false, false, true, preferred);
    }

    private void labelHorizontalRooms(NavigationGrid grid, CellTopology topology,
                                      int bl, int bt, int br, int bb,
                                      int wallY, boolean publicAtLow) {
        labelRect(grid, topology, bl + 1, bt + 1, br - 1, wallY - 1,
                publicAtLow ? publicPurpose : privatePurpose);
        labelRect(grid, topology, bl + 1, wallY + 1, br - 1, bb - 1,
                publicAtLow ? privatePurpose : publicPurpose);
    }

    private void labelVerticalRooms(NavigationGrid grid, CellTopology topology,
                                    int bl, int bt, int br, int bb,
                                    int wallX, boolean publicAtLow) {
        labelRect(grid, topology, bl + 1, bt + 1, wallX - 1, bb - 1,
                publicAtLow ? publicPurpose : privatePurpose);
        labelRect(grid, topology, wallX + 1, bt + 1, br - 1, bb - 1,
                publicAtLow ? privatePurpose : publicPurpose);
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
