package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Random;

/**
 * Courtyard-facing apartment plan for large residential compound members.
 * A two-cell hall connects the public courtyard entrance to an opposed rear
 * exit. A shallow shared lobby opens into that hall; two living rooms and two
 * bedrooms flank it with direct doors, preserving a clear squad route without
 * requiring one private room to be crossed to reach another.
 */
final class ResidentialPartitionStrategy implements PartitionStrategy {

    static final ResidentialPartitionStrategy DEFAULT = new ResidentialPartitionStrategy();
    static final int MIN_LONG_DIM = 12;
    static final int MIN_SHORT_DIM = 10;

    private static final int LOBBY_DEPTH = 2;

    private ResidentialPartitionStrategy() {}

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
                ? carveVertical(grid, topology, bl, bt, br, bb,
                        interiorGround, placement.frontage)
                : carveHorizontal(grid, topology, bl, bt, br, bb,
                        interiorGround, placement.frontage);
    }

    private static PartitionLayout carveVertical(NavigationGrid grid,
                                                   CellTopology topology,
                                                   int bl, int bt, int br, int bb,
                                                   GroundKind ground,
                                                   BuildingPlacement.Side frontage) {
        int hallLow = (bl + br - 1) / 2;
        int hallHigh = hallLow + 1;
        int leftWall = hallLow - 1;
        int rightWall = hallHigh + 1;
        boolean frontAtLow = frontage != BuildingPlacement.Side.BOTTOM;
        int longMin = bt + 1;
        int longMax = bb - 1;
        int lobbyMin = frontAtLow ? longMin : longMax - LOBBY_DEPTH + 1;
        int lobbyMax = frontAtLow ? longMin + LOBBY_DEPTH - 1 : longMax;
        int roomsMin = frontAtLow ? lobbyMax + 1 : longMin;
        int roomsMax = frontAtLow ? longMax : lobbyMin - 1;
        int splitWall = (roomsMin + roomsMax) / 2;

        for (int y = roomsMin; y <= roomsMax; y++) {
            grid.setWalkable(leftWall, y, false);
            grid.setWalkable(rightWall, y, false);
        }
        for (int x = bl + 1; x < leftWall; x++) grid.setWalkable(x, splitWall, false);
        for (int x = rightWall + 1; x <= br - 1; x++) grid.setWalkable(x, splitWall, false);

        int livingMin = frontAtLow ? roomsMin : splitWall + 1;
        int livingMax = frontAtLow ? splitWall - 1 : roomsMax;
        int bedroomMin = frontAtLow ? splitWall + 1 : roomsMin;
        int bedroomMax = frontAtLow ? roomsMax : splitWall - 1;
        openVerticalRoomDoors(grid, topology, leftWall, rightWall,
                livingMin, livingMax, bedroomMin, bedroomMax, ground);

        labelRect(grid, topology, bl + 1, lobbyMin, br - 1, lobbyMax,
                RoomPurpose.APARTMENT_LOBBY);
        labelRect(grid, topology, hallLow, longMin, hallHigh, longMax,
                RoomPurpose.RESIDENTIAL_HALL);
        labelVerticalWings(grid, topology, bl, br, leftWall, rightWall,
                livingMin, livingMax, RoomPurpose.APARTMENT_LIVING);
        labelVerticalWings(grid, topology, bl, br, leftWall, rightWall,
                bedroomMin, bedroomMax, RoomPurpose.BEDROOM);

        return new PartitionLayout(PartitionLayout.Orient.VERTICAL,
                new int[]{leftWall, rightWall}, false, false, true, hallLow);
    }

    private static PartitionLayout carveHorizontal(NavigationGrid grid,
                                                     CellTopology topology,
                                                     int bl, int bt, int br, int bb,
                                                     GroundKind ground,
                                                     BuildingPlacement.Side frontage) {
        int hallLow = (bt + bb - 1) / 2;
        int hallHigh = hallLow + 1;
        int topWall = hallLow - 1;
        int bottomWall = hallHigh + 1;
        boolean frontAtLow = frontage != BuildingPlacement.Side.RIGHT;
        int longMin = bl + 1;
        int longMax = br - 1;
        int lobbyMin = frontAtLow ? longMin : longMax - LOBBY_DEPTH + 1;
        int lobbyMax = frontAtLow ? longMin + LOBBY_DEPTH - 1 : longMax;
        int roomsMin = frontAtLow ? lobbyMax + 1 : longMin;
        int roomsMax = frontAtLow ? longMax : lobbyMin - 1;
        int splitWall = (roomsMin + roomsMax) / 2;

        for (int x = roomsMin; x <= roomsMax; x++) {
            grid.setWalkable(x, topWall, false);
            grid.setWalkable(x, bottomWall, false);
        }
        for (int y = bt + 1; y < topWall; y++) grid.setWalkable(splitWall, y, false);
        for (int y = bottomWall + 1; y <= bb - 1; y++) grid.setWalkable(splitWall, y, false);

        int livingMin = frontAtLow ? roomsMin : splitWall + 1;
        int livingMax = frontAtLow ? splitWall - 1 : roomsMax;
        int bedroomMin = frontAtLow ? splitWall + 1 : roomsMin;
        int bedroomMax = frontAtLow ? roomsMax : splitWall - 1;
        openHorizontalRoomDoors(grid, topology, topWall, bottomWall,
                livingMin, livingMax, bedroomMin, bedroomMax, ground);

        labelRect(grid, topology, lobbyMin, bt + 1, lobbyMax, bb - 1,
                RoomPurpose.APARTMENT_LOBBY);
        labelRect(grid, topology, longMin, hallLow, longMax, hallHigh,
                RoomPurpose.RESIDENTIAL_HALL);
        labelHorizontalWings(grid, topology, bt, bb, topWall, bottomWall,
                livingMin, livingMax, RoomPurpose.APARTMENT_LIVING);
        labelHorizontalWings(grid, topology, bt, bb, topWall, bottomWall,
                bedroomMin, bedroomMax, RoomPurpose.BEDROOM);

        return new PartitionLayout(PartitionLayout.Orient.HORIZONTAL,
                new int[]{topWall, bottomWall}, false, false, true, hallLow);
    }

    private static void openVerticalRoomDoors(NavigationGrid grid, CellTopology topology,
                                              int firstWall, int secondWall,
                                              int livingMin, int livingMax,
                                              int bedroomMin, int bedroomMax,
                                              GroundKind ground) {
        int livingDoor = (livingMin + livingMax) / 2;
        int bedroomDoor = (bedroomMin + bedroomMax) / 2;
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, firstWall, livingDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, secondWall, livingDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, firstWall, bedroomDoor, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, secondWall, bedroomDoor, ground);
    }

    private static void openHorizontalRoomDoors(NavigationGrid grid, CellTopology topology,
                                                int firstWall, int secondWall,
                                                int livingMin, int livingMax,
                                                int bedroomMin, int bedroomMax,
                                                GroundKind ground) {
        int livingDoor = (livingMin + livingMax) / 2;
        int bedroomDoor = (bedroomMin + bedroomMax) / 2;
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, livingDoor, firstWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, livingDoor, secondWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, bedroomDoor, firstWall, ground);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, bedroomDoor, secondWall, ground);
    }

    private static void labelVerticalWings(NavigationGrid grid, CellTopology topology,
                                           int bl, int br, int leftWall, int rightWall,
                                           int minY, int maxY, RoomPurpose purpose) {
        labelRect(grid, topology, bl + 1, minY, leftWall - 1, maxY, purpose);
        labelRect(grid, topology, rightWall + 1, minY, br - 1, maxY, purpose);
    }

    private static void labelHorizontalWings(NavigationGrid grid, CellTopology topology,
                                             int bt, int bb, int topWall, int bottomWall,
                                             int minX, int maxX, RoomPurpose purpose) {
        labelRect(grid, topology, minX, bt + 1, maxX, topWall - 1, purpose);
        labelRect(grid, topology, minX, bottomWall + 1, maxX, bb - 1, purpose);
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
